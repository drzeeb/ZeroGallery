package de.zerogallery.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import de.zerogallery.data.filesystem.HiddenMediaScanner
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType
import de.zerogallery.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [MediaRepository] backed directly by the platform [MediaStore] via [android.content.ContentResolver].
 *
 * Images and videos are queried from their respective MediaStore collections and merged into a
 * single, date-descending list. A [ContentObserver] is registered on both collections so that the
 * emitted list automatically refreshes when media is added, changed or removed on the device -
 * no polling required.
 *
 * Also merges in [HiddenMediaScanner]'s results: dot-prefixed "hidden" folders are never scanned
 * into MediaStore by the platform at all, so no ContentResolver query can ever surface them - a
 * raw filesystem walk is the only way. That's a no-op unless "All files access" has been granted
 * (see [de.zerogallery.ui.permission.AllFilesAccessPermission]), so this is always safe to call.
 */
class MediaStoreRepository(
    private val context: Context,
) : MediaRepository {

    override fun observeMedia(): Flow<List<MediaItem>> = callbackFlow {
        val producerScope = this

        suspend fun refresh() {
            trySend(queryAllMedia())
        }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                producerScope.launch { refresh() }
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer,
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer,
        )

        refresh()

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private suspend fun queryAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val images = queryCollection(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaType = MediaType.IMAGE,
        )
        val videos = queryCollection(
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            mediaType = MediaType.VIDEO,
        )
        val hidden = HiddenMediaScanner.scan(context)
        (images + videos + hidden).sortedByDescending { it.dateAddedSeconds }
    }

    private fun queryCollection(collection: Uri, mediaType: MediaType): List<MediaItem> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (mediaType == MediaType.VIDEO) {
                add(MediaStore.Video.Media.DURATION)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        val items = mutableListOf<MediaItem>()

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val bucketColumn =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val durationColumn = if (mediaType == MediaType.VIDEO) {
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(nameColumn) ?: "",
                    mediaType = mediaType,
                    dateAddedSeconds = cursor.getLong(dateAddedColumn),
                    sizeBytes = cursor.getLong(sizeColumn),
                    durationMillis = if (durationColumn >= 0) cursor.getLong(durationColumn) else null,
                    bucketName = cursor.getString(bucketColumn) ?: "",
                )
            }
        }
        return items
    }
}

