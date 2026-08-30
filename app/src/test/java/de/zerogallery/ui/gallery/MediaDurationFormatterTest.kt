package de.zerogallery.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaDurationFormatterTest {

    @Test
    fun `formats seconds under a minute`() {
        assertEquals("0:09", formatDuration(9_000))
    }

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("3:07", formatDuration(187_000))
    }

    @Test
    fun `formats hours minutes and seconds`() {
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun `clamps negative durations to zero`() {
        assertEquals("0:00", formatDuration(-500))
    }
}

