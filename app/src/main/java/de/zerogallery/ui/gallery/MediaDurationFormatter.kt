package de.zerogallery.ui.gallery

import java.util.Locale

/**
 * Formats a video duration for the grid's duration badge: `m:ss` for durations under an hour,
 * `h:mm:ss` otherwise. Negative or unknown durations are clamped to `0:00`.
 */
fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

