package de.zerogallery.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSizeClassTest {

    @Test
    fun `narrow phone width is compact`() {
        assertEquals(WindowWidthSizeClass.COMPACT, windowWidthSizeClassOf(360))
    }

    @Test
    fun `599dp is still compact`() {
        assertEquals(WindowWidthSizeClass.COMPACT, windowWidthSizeClassOf(599))
    }

    @Test
    fun `600dp is the medium lower bound`() {
        assertEquals(WindowWidthSizeClass.MEDIUM, windowWidthSizeClassOf(600))
    }

    @Test
    fun `839dp is still medium`() {
        assertEquals(WindowWidthSizeClass.MEDIUM, windowWidthSizeClassOf(839))
    }

    @Test
    fun `840dp is the expanded lower bound`() {
        assertEquals(WindowWidthSizeClass.EXPANDED, windowWidthSizeClassOf(840))
    }

    @Test
    fun `large tablet width is expanded`() {
        assertEquals(WindowWidthSizeClass.EXPANDED, windowWidthSizeClassOf(1280))
    }
}

