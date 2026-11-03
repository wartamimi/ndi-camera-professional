package id.lcscam.app

import id.lcscam.app.camera.ModeSelection
import id.lcscam.app.domain.model.Resolution
import id.lcscam.app.domain.model.VideoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeSelectionTest {
    private val sizes = listOf(Resolution(3840, 2160), Resolution(1920, 1080), Resolution(1280, 720), Resolution(640, 480), Resolution(1440, 1080))
    private val ranges = listOf(15 to 30, 30 to 30, 24 to 24, 60 to 60, 15 to 60)

    @Test fun filtersByFrameDurationAndFpsRanges() {
        val modes = ModeSelection.supportedModes(sizes, { r -> if (r.width == 3840) 33_333_333L else 16_666_666L }, ranges)
        assertTrue(VideoMode(Resolution(1920, 1080), 60) in modes)
        assertTrue(VideoMode(Resolution(3840, 2160), 30) in modes)
        assertFalse(VideoMode(Resolution(3840, 2160), 60) in modes)   // sensor too slow for 4K60
        assertFalse(modes.any { it.fps == 25 || it.fps == 50 })        // no AE range ends at 25/50
        assertFalse(modes.any { it.resolution == Resolution(1440, 1080) }) // not a candidate
    }

    @Test fun prefersFixedFpsRange() {
        assertEquals(30 to 30, ModeSelection.fpsRangeFor(30, ranges))
        assertEquals(15 to 60, ModeSelection.fpsRangeFor(60, listOf(15 to 60)))
        assertNull(ModeSelection.fpsRangeFor(25, ranges))
    }

    @Test fun defaultModeIs1080p30() {
        val modes = ModeSelection.supportedModes(sizes, { 0L }, ranges)
        assertEquals(VideoMode(Resolution(1920, 1080), 30), ModeSelection.defaultMode(modes))
    }

    @Test fun previewSizeKeepsAspect() {
        assertEquals(Resolution(1920, 1080), ModeSelection.previewSizeFor(Resolution(3840, 2160), sizes))
        assertEquals(Resolution(1440, 1080), ModeSelection.previewSizeFor(Resolution(640, 480), sizes))
    }

    @Test fun shutterFormatting() {
        assertEquals("1/50", ModeSelection.shutterLabel(20_000_000L))
        assertEquals(180, ModeSelection.shutterAngle(20_000_000L, 25))
        assertTrue(ModeSelection.shutterStops(100_000L, 33_333_333L).all { it in 100_000L..33_333_333L })
        val iso = ModeSelection.isoStops(55, 7000)
        assertEquals(55, iso.first()); assertEquals(7000, iso.last())
    }
}
