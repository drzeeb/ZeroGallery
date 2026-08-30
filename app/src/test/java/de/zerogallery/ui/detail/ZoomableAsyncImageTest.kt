package de.zerogallery.ui.detail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomableAsyncImageTest {

    private val containerSize = IntSize(width = 200, height = 200)

    @Test
    fun `tapping the exact center produces no offset`() {
        val offset = computeDoubleTapOffset(
            containerSize = containerSize,
            tapPosition = Offset(100f, 100f),
            zoom = 3f,
        )

        assertEquals(Offset.Zero, offset)
    }

    @Test
    fun `tapping the top-left corner shifts the image toward the bottom-right`() {
        val offset = computeDoubleTapOffset(
            containerSize = containerSize,
            tapPosition = Offset(0f, 0f),
            zoom = 3f,
        )

        // center (100,100) - tap (0,0) = (100,100), scaled by zoom 3 -> (300,300)
        assertEquals(Offset(300f, 300f), offset)
    }

    @Test
    fun `tapping the bottom-right corner shifts the image toward the top-left`() {
        val offset = computeDoubleTapOffset(
            containerSize = containerSize,
            tapPosition = Offset(200f, 200f),
            zoom = 3f,
        )

        // center (100,100) - tap (200,200) = (-100,-100), scaled by zoom 3 -> (-300,-300)
        assertEquals(Offset(-300f, -300f), offset)
    }

    @Test
    fun `higher zoom factor scales the offset proportionally`() {
        val offset = computeDoubleTapOffset(
            containerSize = containerSize,
            tapPosition = Offset(50f, 50f),
            zoom = 5f,
        )

        // center (100,100) - tap (50,50) = (50,50), scaled by zoom 5 -> (250,250)
        assertEquals(Offset(250f, 250f), offset)
    }
}

