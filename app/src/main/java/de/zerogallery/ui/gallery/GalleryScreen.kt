package de.zerogallery.ui.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.zerogallery.R
import de.zerogallery.ui.detail.MediaDetailScreen
import de.zerogallery.ui.permission.MediaPermissions
import de.zerogallery.ui.util.rememberWindowWidthSizeClass

/**
 * Stateful entry point: wires up the runtime permission request to [GalleryViewModel], tracks
 * which item (if any) is open in the full-screen detail viewer, and forwards everything to the
 * stateless [GalleryScreen].
 *
 * There's intentionally no navigation library here yet: with just these two destinations, a
 * single `selectedIndex` is simpler than a `NavHost`. Should more top-level screens appear later
 * (e.g. albums, settings), migrating to `androidx.navigation.compose` becomes worthwhile.
 *
 * Permission state is *remembered* across launches: rather than always starting out in
 * [GalleryUiState.PermissionRequired] and forcing the user to tap "Grant access" again on every
 * cold start, [MediaPermissions.hasAll] is checked directly against the system on `ON_START`
 * (covers both the initial launch and the user returning to the app, e.g. after granting the
 * permission from Settings) so an already-granted permission is picked up immediately.
 */
@Composable
fun GalleryRoute(viewModel: GalleryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> viewModel.onPermissionResult(result.values.all { it }) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onPermissionResult(MediaPermissions.hasAll(context))
    }

    val index = selectedIndex
    val contentState = uiState
    if (index != null && contentState is GalleryUiState.Content) {
        MediaDetailScreen(
            items = contentState.items,
            initialIndex = index,
            onClose = { selectedIndex = null },
        )
    } else {
        GalleryScreen(
            uiState = uiState,
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryScreen(
    uiState: GalleryUiState,
    onRequestPermission: () -> Unit,
    onItemClick: (index: Int) -> Unit,
) {
    val windowWidthSizeClass = rememberWindowWidthSizeClass()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
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
                is GalleryUiState.Content -> MediaGrid(
                    items = uiState.items,
                    onItemClick = onItemClick,
                    windowWidthSizeClass = windowWidthSizeClass,
                )
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

