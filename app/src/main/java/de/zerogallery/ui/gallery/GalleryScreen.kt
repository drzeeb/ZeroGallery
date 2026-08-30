package de.zerogallery.ui.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.zerogallery.R
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType
import de.zerogallery.ui.permission.MediaPermissions

/**
 * Stateful entry point: wires up the runtime permission request to [GalleryViewModel] and
 * forwards the resulting [GalleryUiState] to the stateless [GalleryScreen].
 */
@Composable
fun GalleryRoute(viewModel: GalleryViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> viewModel.onPermissionResult(result.values.all { it }) }

    GalleryScreen(
        uiState = uiState,
        onRequestPermission = { permissionLauncher.launch(MediaPermissions.required) },
    )
}

/**
 * Stateless gallery screen.
 *
 * NOTE: Phase 1 renders a simple, single-column list purely to verify that the MediaStore data
 * layer and the permission flow work end to end. Phase 2 replaces [MediaContentList] with an
 * adaptive grid ([androidx.compose.foundation.lazy.grid.LazyVerticalGrid]) with thumbnails.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryScreen(
    uiState: GalleryUiState,
    onRequestPermission: () -> Unit,
) {
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
                is GalleryUiState.Content -> MediaContentList(uiState.items)
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequestPermission: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp),
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
        modifier = Modifier.padding(24.dp),
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

@Composable
private fun MediaContentList(items: List<MediaItem>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.id }) { item ->
            ListItem(
                headlineContent = { Text(item.displayName) },
                leadingContent = {
                    Icon(
                        imageVector = if (item.mediaType == MediaType.VIDEO) {
                            Icons.Filled.Videocam
                        } else {
                            Icons.Filled.PhotoLibrary
                        },
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

