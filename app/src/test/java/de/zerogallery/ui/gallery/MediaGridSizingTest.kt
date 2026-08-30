package de.zerogallery.ui.gallery

import de.zerogallery.ui.util.WindowWidthSizeClass
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaGridSizingTest {

    @Test
    fun `thumbnail size grows with the window width size class`() {
        val compact = gridMinThumbnailSize(WindowWidthSizeClass.COMPACT)
        val medium = gridMinThumbnailSize(WindowWidthSizeClass.MEDIUM)
        val expanded = gridMinThumbnailSize(WindowWidthSizeClass.EXPANDED)

        assertTrue(compact < medium)
        assertTrue(medium < expanded)
    }

    @Test
    fun `spacing grows with the window width size class`() {
        val compact = gridSpacing(WindowWidthSizeClass.COMPACT)
        val medium = gridSpacing(WindowWidthSizeClass.MEDIUM)
        val expanded = gridSpacing(WindowWidthSizeClass.EXPANDED)

        assertTrue(compact < medium)
        assertTrue(medium < expanded)
    }
}

