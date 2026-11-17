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

    private val modes = listOf(
        VideoMode(Resolution(1280, 720), 30), VideoMode(Resolution(1280, 720), 60),
        VideoMode(Resolution(1920, 1080), 25), VideoMode(Resolution(1920, 1080), 30), VideoMode(Resolution(1920, 1080), 60),
        VideoMode(Resolution(3840, 2160), 30),
    )

    @Test fun listsResolutionsAndFrameRates() {
        assertEquals(listOf(Resolution(1280, 720), Resolution(1920, 1080), Resolution(3840, 2160)), ModeSelection.resolutions(modes))
        assertEquals(listOf(25, 30, 60), ModeSelection.frameRates(modes))
    }

    @Test fun frameRateKeepsResolutionWhenPossible() {
        val cur = VideoMode(Resolution(1920, 1080), 30)
        assertEquals(VideoMode(Resolution(1920, 1080), 60), ModeSelection.modeForFps(modes, cur, 60))
        assertEquals(VideoMode(Resolution(1920, 1080), 25), ModeSelection.modeForFps(modes, cur, 25))
    }

    @Test fun frameRateFallsBackToLargestResolutionNotAboveCurrent() {
        val cur4k = VideoMode(Resolution(3840, 2160), 30)
        assertEquals(VideoMode(Resolution(1920, 1080), 60), ModeSelection.modeForFps(modes, cur4k, 60))
        val cur720 = VideoMode(Resolution(1280, 720), 30)
        assertEquals(VideoMode(Resolution(1920, 1080), 25), ModeSelection.modeForFps(modes, cur720, 25))
        assertEquals(null, ModeSelection.modeForFps(modes, cur720, 50))
    }

    @Test fun resolutionKeepsFrameRateWhenPossible() {
        val cur = VideoMode(Resolution(1920, 1080), 60)
        assertEquals(VideoMode(Resolution(1280, 720), 60), ModeSelection.modeForResolution(modes, cur, Resolution(1280, 720)))
        assertEquals(VideoMode(Resolution(3840, 2160), 30), ModeSelection.modeForResolution(modes, cur, Resolution(3840, 2160)))
    }

    @Test fun maxFpsFromFrameDuration() {
        assertEquals(60, ModeSelection.maxFps(16_666_666L))
        assertEquals(30, ModeSelection.maxFps(33_333_333L))
        assertEquals(0, ModeSelection.maxFps(0L))
    }

    @Test fun explainsWhy60IsMissing() {
        val fhd = Resolution(1920, 1080)
        val hd = Resolution(1280, 720)
        val noRange = id.lcscam.app.domain.model.FrameRateReport(listOf(15 to 30, 30 to 30), mapOf(fhd to 60, hd to 60), mapOf(fhd to 60), emptyList())
        assertEquals(ModeSelection.FpsBlocker.NO_AE_RANGE, ModeSelection.whyNot(60, fhd, noRange))
        val slowYuv = noRange.copy(aeFpsRanges = listOf(30 to 30, 60 to 60), yuvMaxFps = mapOf(fhd to 30, hd to 60))
        assertEquals(ModeSelection.FpsBlocker.YUV_TOO_SLOW, ModeSelection.whyNot(60, fhd, slowYuv))
        assertEquals(ModeSelection.FpsBlocker.NONE, ModeSelection.whyNot(60, hd, slowYuv))
        assertEquals(ModeSelection.FpsBlocker.NO_SIZE, ModeSelection.whyNot(60, Resolution(3840, 2160), slowYuv))
    }
}
