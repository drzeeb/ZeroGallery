package de.zerogallery.ui.gallery

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.zerogallery.R
import de.zerogallery.ui.detail.MediaDetailScreen
import de.zerogallery.ui.permission.AllFilesAccessPermission
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
 * [groupingMode] (see [MediaGroupingMode]) is also owned here rather than inside [GalleryScreen].
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
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var groupingMode by rememberSaveable { mutableStateOf(MediaGroupingMode.NONE) }
    var selectedFolderLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var showAllFilesAccessRationale by rememberSaveable { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> viewModel.onPermissionResult(result.values.all { it }) }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (AllFilesAccessPermission.isGranted()) {
            showHidden = true
        }
        viewModel.refresh()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onPermissionResult(MediaPermissions.hasAll(context))
        viewModel.refresh()
    }

    if (showAllFilesAccessRationale) {
        AllFilesAccessRationaleDialog(
            onConfirm = {
                showAllFilesAccessRationale = false
                // Only ever shown once !AllFilesAccessPermission.isGranted(), which implies API
                // 30+ - the explicit check here just satisfies lint's static analysis, which can't
                // follow that implication across the showAllFilesAccessRationale state indirection.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    allFilesAccessLauncher.launch(AllFilesAccessPermission.requestIntent(context))
                }
            },
            onDismiss = { showAllFilesAccessRationale = false },
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

    // Step back out of an opened folder to the folder picker, rather than falling through to the
    // system's default back behaviour (closing the app). Only active while the folder picker's
    // *contents* screen is actually showing - i.e. not while the detail viewer is open on top of
    // it, which handles back for itself (see MediaDetailScreen's own BackHandler).
    BackHandler(enabled = selectedIndex == null && selectedFolderLabel != null) {
        selectedFolderLabel = null
    }

    val index = selectedIndex
    if (index != null && displayedItems.isNotEmpty()) {
        MediaDetailScreen(
            items = displayedItems,
            initialIndex = index,
            onClose = { selectedIndex = null },
        )
    } else {
        GalleryScreen(
            uiState = uiState,
            groups = groups,
            groupingMode = groupingMode,
            selectedFolderLabel = selectedFolderLabel,
            showHidden = showHidden,
            saveableStateHolder = saveableStateHolder,
            onGroupingModeChange = {
                groupingMode = it
                selectedFolderLabel = null
            },
            onShowHiddenChange = { wantShown ->
                if (wantShown && !AllFilesAccessPermission.isGranted()) {
                    showAllFilesAccessRationale = true
                } else {
                    showHidden = wantShown
                }
            },
            onFolderOpen = { selectedFolderLabel = it },
            onFolderBack = { selectedFolderLabel = null },
            onRequestPermission = { permissionLauncher.launch(MediaPermissions.required) },
            onItemClick = { selectedIndex = it },
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
    saveableStateHolder: SaveableStateHolder,
    onGroupingModeChange: (MediaGroupingMode) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onFolderOpen: (String) -> Unit,
    onFolderBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onItemClick: (index: Int) -> Unit,
) {
    val windowWidthSizeClass = rememberWindowWidthSizeClass()
    val isFolderPicker = groupingMode == MediaGroupingMode.FOLDER && selectedFolderLabel == null
    val openedFolder = groupingMode == MediaGroupingMode.FOLDER && selectedFolderLabel != null
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    var showVideoGesturesHelp by rememberSaveable { mutableStateOf(false) }

    if (showVideoGesturesHelp) {
        VideoGesturesHelpDialog(onDismiss = { showVideoGesturesHelp = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (openedFolder) {
                        Text(
                            text = selectedFolderLabel.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        ZeroGalleryWordmark()
                    }
                },
                navigationIcon = {
                    if (openedFolder) {
                        IconButton(onClick = onFolderBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
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
 * Explains *why* showing hidden folders needs the special, powerful "All files access" permission
 * (see [AllFilesAccessPermission]) - unlike the initial media permission, this can't just be
 * requested via a system dialog, so the user needs to understand what they're about to be sent to
 * a Settings screen for and why a normal permission wasn't enough, before being sent there.
 */
@Composable
private fun AllFilesAccessRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
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

