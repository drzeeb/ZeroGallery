package de.zerogallery.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class FastScrollbarTest {

    @Test
    fun `returns 0 when there is no content at all`() {
        val progress = computeScrollProgress(
            totalItemCount = 0,
            visibleItemCount = 0,
            firstVisibleItemIndex = 0,
            firstVisibleItemOffsetY = 0,
            firstVisibleItemHeight = 100,
        )

        assertEquals(0f, progress)
    }

    @Test
    fun `returns 0 when scrolled all the way to the top`() {
        val progress = computeScrollProgress(
            totalItemCount = 100,
            visibleItemCount = 10,
            firstVisibleItemIndex = 0,
            firstVisibleItemOffsetY = 0,
            firstVisibleItemHeight = 100,
        )

        assertEquals(0f, progress)
    }

    @Test
    fun `returns 1 when scrolled to the last possible position`() {
        val progress = computeScrollProgress(
            totalItemCount = 100,
            visibleItemCount = 10,
            firstVisibleItemIndex = 90,
            firstVisibleItemOffsetY = 0,
            firstVisibleItemHeight = 100,
        )

        assertEquals(1f, progress)
    }

    @Test
    fun `accounts for partial scroll within the first visible item, not just its index`() {
        val atRowStart = computeScrollProgress(
            totalItemCount = 100,
            visibleItemCount = 10,
            firstVisibleItemIndex = 10,
            firstVisibleItemOffsetY = 0,
            firstVisibleItemHeight = 100,
        )
        val halfScrolledPastRow = computeScrollProgress(
            totalItemCount = 100,
            visibleItemCount = 10,
            firstVisibleItemIndex = 10,
            firstVisibleItemOffsetY = -50,
            firstVisibleItemHeight = 100,
        )

        assertEquals(10f / 90f, atRowStart)
        assertEquals(10.5f / 90f, halfScrolledPastRow)
    }

    @Test
    fun `never exceeds the 0 to 1 range even with an out-of-bounds index`() {
        val progress = computeScrollProgress(
            totalItemCount = 100,
            visibleItemCount = 10,
            firstVisibleItemIndex = 99,
            firstVisibleItemOffsetY = -500,
            firstVisibleItemHeight = 100,
        )

        assertEquals(1f, progress)
    }

    @Test
    fun `returns 0 when the first visible item has no measured height yet`() {
        val progress = computeScrollProgress(
            totalItemCount = 100,
            visibleItemCount = 10,
            firstVisibleItemIndex = 5,
            firstVisibleItemOffsetY = 0,
            firstVisibleItemHeight = 0,
        )

        assertEquals(0f, progress)
    }
}

