package de.zerogallery.ui.gallery

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "gallery_settings"
private const val KEY_GROUPING_MODE = "grouping_mode"

/**
 * Persists the gallery grid's last-used [MediaGroupingMode] across full app restarts.
 *
 * [androidx.compose.runtime.saveable.rememberSaveable] (used for [GalleryRoute]'s other transient
 * UI state) only survives configuration changes and process death while the system still keeps
 * some trace of the activity around to restore - not a genuinely fresh cold start after the app
 * was fully swiped away/force-stopped, which just re-runs [GalleryRoute] from scratch. Without
 * this, the grid would silently reset back to no grouping on every such restart even if the user
 * always browses by date or by folder instead.
 *
 * A single value in a plain `SharedPreferences` file is deliberately used over a heavier Proto/
 * Preferences DataStore dependency - there's exactly one small setting to persist here.
 */
object GallerySettings {

    fun loadGroupingMode(context: Context): MediaGroupingMode {
        val name = prefs(context).getString(KEY_GROUPING_MODE, null) ?: return MediaGroupingMode.NONE
        // Guards against a future release renaming/removing an enum constant while an old value is
        // still stored on disk - falls back to the default rather than crashing on valueOf().
        return runCatching { MediaGroupingMode.valueOf(name) }.getOrDefault(MediaGroupingMode.NONE)
    }

    fun saveGroupingMode(context: Context, mode: MediaGroupingMode) {
        prefs(context).edit { putString(KEY_GROUPING_MODE, mode.name) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}



