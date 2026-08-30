package de.zerogallery.ui.gallery

import android.net.Uri
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

class MediaGroupingTest {

    // A real Uri instance is never actually read (no `.toString()`/`.getScheme()` etc. below) -
    // MediaItem just needs *some* non-null Uri to construct, and Uri is abstract with no usable
    // public constructor, so a bare Mockito mock is the simplest stand-in.
    private val fakeUri: Uri = mock(Uri::class.java)

    private fun item(
        id: Long,
        dateAddedSeconds: Long,
        bucketName: String = "",
        displayName: String = "item-$id",
    ) = MediaItem(
        id = id,
        uri = fakeUri,
        displayName = displayName,
        mediaType = MediaType.IMAGE,
        dateAddedSeconds = dateAddedSeconds,
        sizeBytes = 0,
        bucketName = bucketName,
    )

    private fun epochSecondsFor(year: Int, month: Int, day: Int): Long =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, ZoneOffset.UTC)
            .withZoneSameInstant(java.time.ZoneId.systemDefault())
            .toEpochSecond()

    @Test
    fun `NONE mode returns a single unlabelled group with all items in original order`() {
        val items = listOf(item(1, 300), item(2, 200), item(3, 100))

        val groups = groupMedia(items, MediaGroupingMode.NONE)

        assertEquals(1, groups.size)
        assertEquals("", groups.single().label)
        assertEquals(items, groups.single().items)
    }

    @Test
    fun `DATE mode groups newest-first items into contiguous month sections`() {
        val aug1 = epochSecondsFor(2026, 8, 20)
        val aug2 = epochSecondsFor(2026, 8, 5)
        val jul = epochSecondsFor(2026, 7, 15)
        val items = listOf(item(1, aug1), item(2, aug2), item(3, jul))

        val groups = groupMedia(items, MediaGroupingMode.DATE)

        assertEquals(2, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].items.map { it.id })
        assertEquals(listOf(3L), groups[1].items.map { it.id })
    }

    @Test
    fun `FOLDER mode sorts sections by each folder's newest item`() {
        val newest = 300L
        val middle = 200L
        val oldest = 100L
        val items = listOf(
            item(1, oldest, bucketName = "Screenshots"),
            item(2, newest, bucketName = "Camera"),
            item(3, middle, bucketName = "Screenshots"),
        )

        val groups = groupMedia(items, MediaGroupingMode.FOLDER)

        assertEquals(listOf("Camera", "Screenshots"), groups.map { it.label })
        assertEquals(listOf(2L), groups[0].items.map { it.id })
        assertEquals(listOf(1L, 3L), groups[1].items.map { it.id })
    }

    @Test
    fun `FOLDER mode falls back to the given label for blank bucket names`() {
        val items = listOf(item(1, 100, bucketName = ""))

        val groups = groupMedia(items, MediaGroupingMode.FOLDER, unknownFolderLabel = "Other")

        assertEquals("Other", groups.single().label)
    }

    @Test
    fun `monthYearLabel formats month and year`() {
        val epochSeconds = epochSecondsFor(2026, 8, 15)

        assertEquals("August 2026", monthYearLabel(epochSeconds, Locale.US))
    }

    @Test
    fun `filterHidden excludes items in dot-prefixed folders by default`() {
        val visible = item(1, 100, bucketName = "Camera")
        val hidden = item(2, 100, bucketName = ".hidden_gallery")
        val items = listOf(visible, hidden)

        assertEquals(listOf(visible), filterHidden(items, showHidden = false))
        assertEquals(items, filterHidden(items, showHidden = true))
    }

    @Test
    fun `filterHidden also excludes individually dot-prefixed file names`() {
        val visible = item(1, 100, bucketName = "Camera", displayName = "IMG_1.jpg")
        val hidden = item(2, 100, bucketName = "Camera", displayName = ".IMG_2.jpg")
        val items = listOf(visible, hidden)

        assertEquals(listOf(visible), filterHidden(items, showHidden = false))
    }

    @Test
    fun `groupBoundaries accounts for each group's own header row`() {
        val groups = listOf(
            MediaGroup(label = "August 2026", items = listOf(item(1, 300), item(2, 200))),
            MediaGroup(label = "July 2026", items = listOf(item(3, 100))),
        )

        val boundaries = groupBoundaries(groups)

        // First group's header is grid item 0, its 2 items are 1-2; second group's header is
        // therefore grid item 3 (1 header + 2 items in), not 2 (which would ignore its own header).
        assertEquals(listOf(0 to "August 2026", 3 to "July 2026"), boundaries)
    }

    @Test
    fun `groupBoundaries omits a header slot entirely for blank labels`() {
        val groups = listOf(MediaGroup(label = "", items = listOf(item(1, 100), item(2, 100))))

        val boundaries = groupBoundaries(groups)

        assertEquals(listOf(0 to ""), boundaries)
    }

    @Test
    fun `currentGroupLabel returns the label of the last boundary at or before the given index`() {
        val boundaries = listOf(0 to "August 2026", 3 to "July 2026", 5 to "June 2026")

        assertEquals("August 2026", currentGroupLabel(boundaries, gridItemIndex = 0))
        assertEquals("August 2026", currentGroupLabel(boundaries, gridItemIndex = 2))
        assertEquals("July 2026", currentGroupLabel(boundaries, gridItemIndex = 3))
        assertEquals("June 2026", currentGroupLabel(boundaries, gridItemIndex = 99))
    }

    @Test
    fun `currentGroupLabel returns blank when there are no boundaries at all`() {
        assertEquals("", currentGroupLabel(emptyList(), gridItemIndex = 0))
    }
}


