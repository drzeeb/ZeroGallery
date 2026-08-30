package de.zerogallery.ui.gallery

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.zerogallery.R
import de.zerogallery.data.filesystem.HiddenFolderAccess
import de.zerogallery.data.mediastore.MediaDeleter
import de.zerogallery.data.mediastore.MediaSharer
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaSource
import de.zerogallery.ui.detail.MediaDetailScreen
import de.zerogallery.ui.permission.MediaPermissions
import de.zerogallery.ui.theme.ZeroGalleryWordmark
import de.zerogallery.ui.util.rememberWindowWidthSizeClass

/**
 * Stateful entry point: wires up the runtime permission request to [GalleryViewModel], tracks
 * which item (if any) is open in the full-screen detail viewer, and forwards everything to the
 * stateless [GalleryScreen].
 *
 * There's intentionally no navigation library here yet: with just these two destinations, a
 * single `selectedIndex` is simpler than a `NavHost`. Should more top-level screens appear later
 * (e.g. settings), migrating to `androidx.navigation.compose` becomes worthwhile.
 *
 * Permission state is *remembered* across launches: rather than always starting out in
 * [GalleryUiState.PermissionRequired] and forcing the user to tap "Grant access" again on every
 * cold start, [MediaPermissions.hasAll] is checked directly against the system on `ON_START`
 * (covers both the initial launch and the user returning to the app, e.g. after granting the
 * permission from Settings) so an already-granted permission is picked up immediately.
 *
 * [groupingMode] (see [MediaGroupingMode]) is also owned here rather than inside [GalleryScreen],
 * and is the one piece of this screen's state that survives beyond just [rememberSaveable] -
 * [GallerySettings] persists it across full app restarts too (see its class doc), so switching to
 * date/folder grouping sticks around for good instead of quietly resetting back to the flat grid
 * every time the app is relaunched from a cold start.
 * [MediaGroupingMode.FOLDER] is a genuine two-level drill-down, not just a section header: tapping
 * the grouping button first shows a *folder picker* ([FolderGrid], one tile per folder with a
 * cover photo), and only opening one of those (setting [selectedFolderLabel]) then shows that
 * folder's individual items - dumping every item from every folder into one giant scrolling grid
 * (just with header rows in between) would be exactly as unwieldy as no grouping at all whenever
 * one folder happens to contain hundreds of items and the folder actually wanted is further down.
 *
 * [displayedItems] - whichever section's items are actually visible on screen right now - is
 * computed once here and threaded through to both [MediaGrid] and [MediaDetailScreen], so swiping
 * through the detail viewer always matches whatever the grid was just showing when a tile was
 * tapped (the flat list for [MediaGroupingMode.NONE]/[MediaGroupingMode.DATE], or just the opened
 * folder's items for [MediaGroupingMode.FOLDER] - never *all* items regardless of the folder).
 *
 * Opening [MediaDetailScreen] replaces [GalleryScreen] entirely in the composition (there's no
 * navigation backstack keeping both around), which would normally reset the grid's scroll
 * position back to the top the moment it's recomposed after closing the viewer. [saveableStateHolder]
 * is hoisted all the way up here - above that swap - specifically so it doesn't get thrown away
 * along with it; [GalleryScreen] wraps the actual grid in [SaveableStateHolder.SaveableStateProvider]
 * keyed by the current grouping mode/folder, so scrolling down, opening an item and closing it
 * again lands right back where you were, and switching between grouping modes or folders each
 * keeps its own remembered scroll position rather than sharing one.
 */
@Composable
fun GalleryRoute(viewModel: GalleryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    // Seeded from GallerySettings (persisted across full app restarts, unlike rememberSaveable
    // alone - see its class doc) rather than always starting at MediaGroupingMode.NONE.
    var groupingMode by rememberSaveable { mutableStateOf(GallerySettings.loadGroupingMode(context)) }
    var selectedFolderLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var showHiddenFolderRationale by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable(stateSaver = listSaver(save = { it.toList() }, restore = { it.toSet() })) {
        mutableStateOf(emptySet<Long>())
    }
    var pendingDeleteItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    val saveableStateHolder = rememberSaveableStateHolder()


    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> viewModel.onPermissionResult(result.values.all { it }) }

    // Lets the user pick one or more folders HiddenMediaScanner should read (Storage Access
    // Framework), rather than requesting the broad "All files access" special permission for the
    // sake of this one narrow feature - see HiddenFolderAccess's class doc for why.
    // Reflects HiddenFolderAccess.isConfigured, but as its own state rather than re-reading it on
    // every recomposition, since the underlying SharedPreferences write only happens as a launcher
    // callback side effect below - Compose has no way to observe that on its own.
    var hiddenFolderConfigured by remember { mutableStateOf(HiddenFolderAccess.isConfigured(context)) }
    // Only populated (and kept in sync) while the "Manage hidden folders" dialog is actually
    // showing - see the LaunchedEffect below - so opening it never blocks on resolving every
    // folder's display name (a DocumentFile query) for no reason on every other recomposition.
    var showManageHiddenFolders by remember { mutableStateOf(false) }
    var hiddenFolderEntries by remember { mutableStateOf<List<HiddenFolderEntry>>(emptyList()) }
    LaunchedEffect(showManageHiddenFolders) {
        if (showManageHiddenFolders) {
            hiddenFolderEntries = HiddenFolderAccess.treeUris(context).map { uri ->
                HiddenFolderEntry(uri, DocumentFile.fromTreeUri(context, uri)?.name ?: uri.toString())
            }
        }
    }

    val hiddenFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            HiddenFolderAccess.add(context, treeUri)
            hiddenFolderConfigured = true
            showHidden = true
        }
        viewModel.refresh()
    }

    // Fires after the user confirms/cancels the system delete-confirmation dialog for regular
    // MediaStore items (API 30+, see MediaDeleter.createDeleteRequest) - any HIDDEN_FOLDER items
    // in the same batch were already deleted directly beforehand (see performDelete below), since
    // there's no equivalent confirmation flow for those regardless of API level.
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refresh()
            selectedIds = emptySet()
            selectedIndex = null
        }
    }

    fun performDelete(items: List<MediaItem>) {
        val (hiddenFolderItems, mediaStoreItems) = items.partition { it.source == MediaSource.HIDDEN_FOLDER }
        if (hiddenFolderItems.isNotEmpty()) {
            MediaDeleter.delete(context, hiddenFolderItems)
        }
        val deleteRequest = MediaDeleter.createDeleteRequest(context, mediaStoreItems.map { it.uri })
        if (deleteRequest != null) {
            deleteRequestLauncher.launch(IntentSenderRequest.Builder(deleteRequest).build())
        } else {
            if (mediaStoreItems.isNotEmpty()) {
                MediaDeleter.delete(context, mediaStoreItems)
            }
            viewModel.refresh()
            selectedIds = emptySet()
            selectedIndex = null
        }
    }

    // Skips the app's own confirmation dialog whenever Android will already show its own
    // scoped-storage confirmation for the items being deleted (MediaDeleter.needsSystemConfirmation)
    // - asking the user twice in a row to confirm the same deletion is redundant and confusing.
    // That's only true for MediaSource.MEDIA_STORE items on API 30+ though: below that, or for
    // MediaSource.HIDDEN_FOLDER items on any API level, there's no such system dialog at all - so
    // the app's own dialog is shown instead, as the only safety net left.
    fun requestDelete(items: List<MediaItem>) {
        if (MediaDeleter.needsSystemConfirmation(context)) {
            performDelete(items)
        } else {
            pendingDeleteItems = items
        }
    }


    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onPermissionResult(MediaPermissions.hasAll(context))
        viewModel.refresh()
    }

    if (showHiddenFolderRationale) {
        HiddenFolderRationaleDialog(
            onConfirm = {
                showHiddenFolderRationale = false
                hiddenFolderPickerLauncher.launch(null)
            },
            onDismiss = { showHiddenFolderRationale = false },
        )
    }

    if (showManageHiddenFolders) {
        HiddenFoldersManageDialog(
            folders = hiddenFolderEntries,
            onAddFolder = {
                showManageHiddenFolders = false
                hiddenFolderPickerLauncher.launch(null)
            },
            onRemoveFolder = { entry ->
                HiddenFolderAccess.remove(context, entry.uri)
                hiddenFolderEntries = hiddenFolderEntries - entry
                hiddenFolderConfigured = HiddenFolderAccess.isConfigured(context)
                if (!hiddenFolderConfigured) showHidden = false
                viewModel.refresh()
            },
            onDismiss = { showManageHiddenFolders = false },
        )
    }

    val itemsPendingDelete = pendingDeleteItems
    if (itemsPendingDelete != null) {
        DeleteConfirmationDialog(
            count = itemsPendingDelete.size,
            onConfirm = {
                pendingDeleteItems = null
                performDelete(itemsPendingDelete)
            },
            onDismiss = { pendingDeleteItems = null },
        )
    }

    val contentState = uiState
    val unknownFolderLabel = stringResource(R.string.media_grouping_unknown_folder)
    val groups = remember(contentState, groupingMode, unknownFolderLabel, showHidden) {
        if (contentState is GalleryUiState.Content) {
            // The "show hidden" toggle only makes sense for MediaGroupingMode.FOLDER: hidden
            // items are keyed off their *folder* (see MediaGrouping.isHidden), so folding them
            // into a flat NONE/DATE grid would just silently drop items with no way to tell why -
            // whereas here they cleanly disappear as whole folder tiles/sections. That also means
            // hidden items must never leak into NONE/DATE regardless of the toggle's state - it
            // only has any effect while actually grouping by folder.
            val effectiveShowHidden = showHidden && groupingMode == MediaGroupingMode.FOLDER
            val items = filterHidden(contentState.items, effectiveShowHidden)
            groupMedia(items, groupingMode, unknownFolderLabel)
        } else {
            emptyList()
        }
    }
    val isFolderPicker = groupingMode == MediaGroupingMode.FOLDER && selectedFolderLabel == null
    val displayedItems = remember(groups, groupingMode, selectedFolderLabel) {
        when {
            isFolderPicker -> emptyList()
            groupingMode == MediaGroupingMode.FOLDER ->
                groups.firstOrNull { it.label == selectedFolderLabel }?.items.orEmpty()

            else -> groups.flatMap { it.items }
        }
    }
    val selectedItems = remember(selectedIds, displayedItems) {
        displayedItems.filter { it.id in selectedIds }
    }

    // Exits selection mode, then steps back out of an opened folder to the folder picker, rather
    // than falling through to the system's default back behaviour (closing the app) - only active
    // while the folder picker's *contents* screen is actually showing, i.e. not while the detail
    // viewer is open on top of it, which handles back for itself (see MediaDetailScreen's own
    // BackHandler).
    BackHandler(enabled = selectedIndex == null && (selectedIds.isNotEmpty() || selectedFolderLabel != null)) {
        if (selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        } else {
            selectedFolderLabel = null
        }
    }

    val index = selectedIndex
    if (index != null && displayedItems.isNotEmpty()) {
        MediaDetailScreen(
            items = displayedItems,
            initialIndex = index,
            onClose = { selectedIndex = null },
            onShareItem = { item ->
                context.startActivity(Intent.createChooser(MediaSharer.shareIntent(context, listOf(item)), null))
            },
            onDeleteItem = { item -> requestDelete(listOf(item)) },
        )
    } else {
        GalleryScreen(
            uiState = uiState,
            groups = groups,
            groupingMode = groupingMode,
            selectedFolderLabel = selectedFolderLabel,
            showHidden = showHidden,
            selectedIds = selectedIds,
            saveableStateHolder = saveableStateHolder,
            hiddenFolderConfigured = hiddenFolderConfigured,
            onChooseHiddenFolder = { hiddenFolderPickerLauncher.launch(null) },
            onManageHiddenFolders = { showManageHiddenFolders = true },
            onGroupingModeChange = {
                groupingMode = it
                selectedFolderLabel = null
                GallerySettings.saveGroupingMode(context, it)
            },
            onShowHiddenChange = { wantShown ->
                if (wantShown && !HiddenFolderAccess.isConfigured(context)) {
                    showHiddenFolderRationale = true
                } else {
                    showHidden = wantShown
                }
            },
            onFolderOpen = { selectedFolderLabel = it },
            onFolderBack = { selectedFolderLabel = null },
            onRequestPermission = { permissionLauncher.launch(MediaPermissions.required) },
            onItemClick = { index ->
                val item = displayedItems.getOrNull(index)
                if (item != null && selectedIds.isNotEmpty()) {
                    selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                } else {
                    selectedIndex = index
                }
            },
            onItemLongClick = { item ->
                selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
            },
            onClearSelection = { selectedIds = emptySet() },
            onShareSelected = {
                context.startActivity(Intent.createChooser(MediaSharer.shareIntent(context, selectedItems), null))
            },
            onDeleteSelected = { requestDelete(selectedItems) },
        )
    }
}

/**
 * Stateless gallery screen.
 *
 * Renders [MediaGrid], an adaptive thumbnail grid (see [MediaGrid.kt]) that already grows its
 * column count with the available width. On top of that, [windowWidthSizeClass] (Compact/Medium/
 * Expanded, see [de.zerogallery.ui.util.WindowWidthSizeClass]) drives finer layout decisions:
 * larger thumbnails and roomier padding on tablets, and a readable, non-edge-to-edge max width
 * for the permission/empty messages.
 *
 * The app bar's grouping button cycles [MediaGroupingMode] (no grouping → by date → by folder →
 * back to no grouping); its icon reflects the current mode so the button itself always shows
 * what tapping it will switch *away* from, and section headers/the folder picker appearing in the
 * grid below give immediate, self-explanatory feedback for the change - no separate confirmation
 * toast needed. While a folder is open, the grouping button is replaced by a back arrow (see
 * [selectedFolderLabel]) and the app bar's title becomes that folder's name.
 *
 * See [GalleryRoute] for why [saveableStateHolder] is hoisted rather than just created here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryScreen(
    uiState: GalleryUiState,
    groups: List<MediaGroup>,
    groupingMode: MediaGroupingMode,
    selectedFolderLabel: String?,
    showHidden: Boolean,
    selectedIds: Set<Long>,
    saveableStateHolder: SaveableStateHolder,
    hiddenFolderConfigured: Boolean,
    onChooseHiddenFolder: () -> Unit,
    onManageHiddenFolders: () -> Unit,
    onGroupingModeChange: (MediaGroupingMode) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onFolderOpen: (String) -> Unit,
    onFolderBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onItemClick: (index: Int) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
    onClearSelection: () -> Unit,
    onShareSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    val windowWidthSizeClass = rememberWindowWidthSizeClass()
    val isFolderPicker = groupingMode == MediaGroupingMode.FOLDER && selectedFolderLabel == null
    val openedFolder = groupingMode == MediaGroupingMode.FOLDER && selectedFolderLabel != null
    val isSelectionMode = selectedIds.isNotEmpty()
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    var showVideoGesturesHelp by rememberSaveable { mutableStateOf(false) }

    if (showVideoGesturesHelp) {
        VideoGesturesHelpDialog(onDismiss = { showVideoGesturesHelp = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when {
                        isSelectionMode -> Text(
                            pluralStringResource(R.plurals.selection_count, selectedIds.size, selectedIds.size),
                        )

                        openedFolder -> Text(
                            text = selectedFolderLabel.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        else -> ZeroGalleryWordmark()
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = onClearSelection) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.selection_clear_action),
                            )
                        }
                    } else if (openedFolder) {
                        IconButton(onClick = onFolderBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = onShareSelected) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(R.string.share_action),
                            )
                        }
                        IconButton(onClick = onDeleteSelected) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete_action),
                            )
                        }
                    } else {
                        if (uiState is GalleryUiState.Content && groupingMode == MediaGroupingMode.FOLDER) {
                            IconButton(onClick = { onShowHiddenChange(!showHidden) }) {
                                Icon(
                                    imageVector = if (showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (showHidden) {
                                            R.string.hidden_folders_hide_action
                                        } else {
                                            R.string.hidden_folders_show_action
                                        },
                                    ),
                                )
                            }
                        }
                        if (uiState is GalleryUiState.Content && !openedFolder) {
                            IconButton(
                                onClick = {
                                    val next = MediaGroupingMode.entries[
                                        (groupingMode.ordinal + 1) % MediaGroupingMode.entries.size
                                    ]
                                    onGroupingModeChange(next)
                                },
                            ) {
                                Icon(
                                    imageVector = when (groupingMode) {
                                        MediaGroupingMode.NONE -> Icons.Filled.GridView
                                        MediaGroupingMode.DATE -> Icons.Filled.CalendarMonth
                                        MediaGroupingMode.FOLDER -> Icons.Filled.Folder
                                    },
                                    contentDescription = stringResource(R.string.media_grouping_action),
                                )
                            }
                        }
                        if (uiState is GalleryUiState.Content) {
                            Box {
                                IconButton(onClick = { isOverflowMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.overflow_menu_action),
                                    )
                                }
                                DropdownMenu(
                                    expanded = isOverflowMenuExpanded,
                                    onDismissRequest = { isOverflowMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_item_video_gestures)) },
                                        onClick = {
                                            isOverflowMenuExpanded = false
                                            showVideoGesturesHelp = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (hiddenFolderConfigured) {
                                                        R.string.menu_item_manage_hidden_folders
                                                    } else {
                                                        R.string.menu_item_choose_hidden_folder
                                                    },
                                                ),
                                            )
                                        },
                                        onClick = {
                                            isOverflowMenuExpanded = false
                                            if (hiddenFolderConfigured) {
                                                onManageHiddenFolders()
                                            } else {
                                                onChooseHiddenFolder()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState) {
                is GalleryUiState.Loading -> CircularProgressIndicator()
                is GalleryUiState.PermissionRequired -> PermissionRationale(onRequestPermission)
                is GalleryUiState.Empty -> EmptyGalleryMessage()
                is GalleryUiState.Content -> {
                    // Keyed so NONE/DATE, the folder picker and each individual folder all keep
                    // their own remembered scroll position independently of one another, rather
                    // than one shared position that would otherwise look wrong when switching
                    // between views with very different content lengths.
                    val gridStateKey = when {
                        isFolderPicker -> "grouping:folder-picker"
                        openedFolder -> "grouping:folder:$selectedFolderLabel"
                        else -> "grouping:$groupingMode"
                    }
                    saveableStateHolder.SaveableStateProvider(gridStateKey) {
                        if (isFolderPicker) {
                            FolderGrid(
                                folders = groups,
                                onFolderClick = { onFolderOpen(it.label) },
                                windowWidthSizeClass = windowWidthSizeClass,
                            )
                        } else {
                            val displayedGroups = if (openedFolder) {
                                listOfNotNull(groups.firstOrNull { it.label == selectedFolderLabel })
                                    .map { it.copy(label = "") }
                            } else {
                                groups
                            }
                            MediaGrid(
                                groups = displayedGroups,
                                onItemClick = onItemClick,
                                windowWidthSizeClass = windowWidthSizeClass,
                                selectedIds = selectedIds,
                                onItemLongClick = onItemLongClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequestPermission: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .widthIn(max = 480.dp)
            .padding(24.dp),
    ) {
        Icon(imageVector = Icons.Filled.PhotoLibrary, contentDescription = null)
        Text(
            text = stringResource(R.string.permission_rationale_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.permission_rationale_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRequestPermission) {
            Text(stringResource(R.string.grant_access_action))
        }
    }
}

@Composable
private fun EmptyGalleryMessage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .widthIn(max = 480.dp)
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.empty_gallery_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.empty_gallery_body),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * One entry in [HiddenFoldersManageDialog]'s list: a previously picked hidden folder's uri
 * together with its display name (its own folder name, resolved once via [DocumentFile] while the
 * dialog is open - see [GalleryRoute]'s `LaunchedEffect(showManageHiddenFolders)`).
 */
private data class HiddenFolderEntry(val uri: Uri, val displayName: String)

/**
 * Explains *why* showing hidden folders is about to open the system's folder picker (Storage
 * Access Framework, see [HiddenFolderAccess]) instead of just toggling on immediately like the
 * initial media permission does - the user needs to understand what they're about to be sent to
 * pick a folder for, and that they should pick the specific hidden folder they want surfaced.
 */
@Composable
private fun HiddenFolderRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hidden_folders_permission_title)) },
        text = { Text(stringResource(R.string.hidden_folders_permission_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.hidden_folders_permission_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.hidden_folders_permission_dismiss))
            }
        },
    )
}

/**
 * Lists every hidden folder currently picked (see [HiddenFolderAccess]), each removable
 * individually, plus a way to pick yet another one - the "show hidden folders" toggle itself only
 * ever offers the picker once, while unconfigured (see [HiddenFolderRationaleDialog]), so this is
 * the only place to add more than the first folder, or to drop one that's no longer wanted, again
 * without clearing app data.
 */
@Composable
private fun HiddenFoldersManageDialog(
    folders: List<HiddenFolderEntry>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (HiddenFolderEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hidden_folders_manage_title)) },
        text = {
            if (folders.isEmpty()) {
                Text(stringResource(R.string.hidden_folders_manage_empty))
            } else {
                Column {
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = folder.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                            IconButton(onClick = { onRemoveFolder(folder) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(
                                        R.string.hidden_folders_manage_remove_action,
                                        folder.displayName,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddFolder) {
                Text(stringResource(R.string.hidden_folders_manage_add_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.hidden_folders_manage_dismiss))
            }
        },
    )
}

/**
 * Confirms a permanent delete before it happens - the only safety net for
 * [de.zerogallery.data.filesystem.HiddenMediaScanner]'s `file://` items (deleted directly, with no
 * OS-level confirmation available at all) and for regular `MediaStore` items below API 30 (no
 * [de.zerogallery.data.mediastore.MediaDeleter.createDeleteRequest] system dialog either). Shown
 * unconditionally regardless of API level/item source, so the confirmation step is always
 * consistent no matter what's selected.
 */
@Composable
private fun DeleteConfirmationDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(pluralStringResource(R.plurals.delete_confirm_body, count, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(pluralStringResource(R.plurals.delete_confirm_action, count, count))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_confirm_dismiss))
            }
        },
    )
}

/**
 * Explains the three swipe gestures available in the video detail viewer while its overlay chrome
 * is hidden (see [de.zerogallery.ui.detail.VideoPlayer]'s class doc) - they're otherwise completely
 * undiscoverable, since hiding the chrome is itself just a single tap with no visual hint that
 * anything about the available gestures then changes underneath it.
 */
@Composable
private fun VideoGesturesHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_gestures_help_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.video_gestures_help_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                VideoGestureHelpRow(Icons.Filled.SwapHoriz, R.string.video_gestures_help_seek)
                VideoGestureHelpRow(Icons.Filled.BrightnessMedium, R.string.video_gestures_help_brightness)
                VideoGestureHelpRow(Icons.AutoMirrored.Filled.VolumeUp, R.string.video_gestures_help_volume)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.video_gestures_help_dismiss))
            }
        },
    )
}

@Composable
private fun VideoGestureHelpRow(icon: ImageVector, textRes: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Text(text = stringResource(textRes), style = MaterialTheme.typography.bodyMedium)
    }
}

