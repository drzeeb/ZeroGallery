package de.zerogallery.ui.gallery

import de.zerogallery.domain.model.MediaItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** How [MediaGrid] visually groups items into labelled, sticky-header sections. */
enum class MediaGroupingMode {
    /** Flat grid, no headers - the original, purely reverse-chronological order. */
    NONE,

    /** One section per calendar month (e.g. "August 2026"), newest first. */
    DATE,

    /** One section per device folder (`MediaStore`'s bucket), most recently active first. */
    FOLDER,
}

/** A labelled, contiguous run of items within [MediaGrid] - rendered as a sticky header + tiles. */
data class MediaGroup(val label: String, val items: List<MediaItem>)

/**
 * Splits [items] (expected to already be sorted newest-first, as [de.zerogallery.data.mediastore
 * .MediaStoreRepository] does) into [MediaGroup]s according to [mode].
 *
 * [unknownFolderLabel] is used as [MediaGroupingMode.FOLDER]'s group label whenever an item's
 * [MediaItem.bucketName] is blank (missing from `MediaStore` for some file managers/cloud sync
 * clients) - passed in rather than hard-coded so the caller can supply a localized string
 * resource; this function stays a plain, unit-testable function with no Android/Compose
 * dependency.
 *
 * For [MediaGroupingMode.NONE], a single, unlabelled group containing all of [items] is returned
 * - [MediaGrid] treats a blank [MediaGroup.label] as "don't render a header for this section".
 *
 * For [MediaGroupingMode.DATE], grouping by [monthYearLabel] on an already-sorted list naturally
 * yields contiguous, newest-first sections - no separate re-sort needed.
 *
 * For [MediaGroupingMode.FOLDER], the *items within* each folder stay newest-first (same reason
 * as above), but the *sections themselves* are additionally sorted by each folder's own newest
 * item, so the most recently active folder still appears first - simply grouping by folder name
 * would otherwise leave sections in whatever (essentially arbitrary) order their first item
 * happened to appear in the input.
 */
fun groupMedia(
    items: List<MediaItem>,
    mode: MediaGroupingMode,
    unknownFolderLabel: String = "",
): List<MediaGroup> = when (mode) {
    MediaGroupingMode.NONE -> listOf(MediaGroup(label = "", items = items))

    MediaGroupingMode.DATE -> items
        .groupBy { monthYearLabel(it.dateAddedSeconds) }
        .map { (label, groupItems) -> MediaGroup(label, groupItems) }

    MediaGroupingMode.FOLDER -> items
        .groupBy { it.bucketName.ifBlank { unknownFolderLabel } }
        .map { (label, groupItems) -> MediaGroup(label, groupItems) }
        .sortedByDescending { group -> group.items.maxOf { it.dateAddedSeconds } }
}

/** Formats a Unix epoch (seconds) as a localized "Month Year" label, e.g. "August 2026". */
internal fun monthYearLabel(epochSeconds: Long, locale: Locale = Locale.getDefault()): String {
    val date = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault())
    val month = date.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    return "$month ${date.year}"
}

/**
 * Whether [MediaItem.bucketName] follows the common Unix convention for a hidden folder/file (a
 * leading dot, e.g. ".hidden_gallery" - used by several messaging/vault apps to keep their media
 * out of standard gallery apps' folder listings). [MediaItem.displayName] is checked too, in case
 * an otherwise-ordinary folder contains an individually dot-prefixed file.
 */
internal fun MediaItem.isHidden(): Boolean =
    bucketName.startsWith(".") || displayName.startsWith(".")

/**
 * Filters out items in hidden folders/files (see [isHidden]) unless [showHidden] is `true` - used
 * to drive the folder view's "show hidden folders" toggle. A plain, unit-testable function kept
 * separate from [groupMedia] since it's an orthogonal concern (which items are eligible at all,
 * vs. how the eligible ones get grouped).
 */
fun filterHidden(items: List<MediaItem>, showHidden: Boolean): List<MediaItem> =
    if (showHidden) items else items.filterNot { it.isHidden() }

/**
 * Maps each of [groups] to the index its section starts at within [MediaGrid]'s flattened
 * `LazyVerticalGrid` item list - i.e. counting the (optional) header row itself as well as every
 * preceding group's items, not just [MediaGroup.items]' own flat index. Used by [currentGroupLabel]
 * to look up which section a given scroll position currently falls within, driving the live
 * "August 2026"-style indicator [MediaGrid] shows while the fast scroll thumb is being dragged.
 */
internal fun groupBoundaries(groups: List<MediaGroup>): List<Pair<Int, String>> {
    var gridItemIndex = 0
    return groups.map { group ->
        val boundary = gridItemIndex to group.label
        if (group.label.isNotBlank()) gridItemIndex += 1
        gridItemIndex += group.items.size
        boundary
    }
}

/**
 * Which group's label [gridItemIndex] (a `LazyVerticalGrid` item index, as produced by
 * [groupBoundaries] - header rows included) currently falls within - blank if [boundaries] is
 * empty or [gridItemIndex] precedes every boundary.
 */
internal fun currentGroupLabel(boundaries: List<Pair<Int, String>>, gridItemIndex: Int): String =
    boundaries.lastOrNull { it.first <= gridItemIndex }?.second ?: ""



