package id.lcscam.app

import id.lcscam.app.camera.ZoomRamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomRampTest {
    @Test fun durationsAreInstantThroughEightSeconds() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8), ZoomRamp.DURATIONS_SEC)
    }

    @Test fun endpointsAndEasing() {
        assertEquals(1f, ZoomRamp.valueAt(1f, 4f, 0f), 1e-6f)
        assertEquals(4f, ZoomRamp.valueAt(1f, 4f, 1f), 1e-6f)
        // Log-space midpoint of 1x->4x is 2x.
        assertEquals(2f, ZoomRamp.valueAt(1f, 4f, 0.5f), 1e-4f)
        // Zoom out is symmetric.
        assertEquals(2f, ZoomRamp.valueAt(4f, 1f, 0.5f), 1e-4f)
        assertEquals(0f, ZoomRamp.ease(-1f), 0f)
        assertEquals(1f, ZoomRamp.ease(2f), 0f)
    }

    @Test fun monotonicAndGentleStart() {
        var prev = 1f
        for (i in 1..100) {
            val v = ZoomRamp.valueAt(1f, 8f, i / 100f)
            assertTrue(v >= prev)
            prev = v
        }
        // Eased: the first 10% of the time covers far less than 10% of the move.
        assertTrue(ZoomRamp.valueAt(1f, 8f, 0.1f) < 1.15f)
    }

    @Test fun ultraWideBelowOne() {
        assertEquals(0.6f, ZoomRamp.valueAt(0.6f, 2f, 0f), 1e-6f)
        assertEquals(2f, ZoomRamp.valueAt(0.6f, 2f, 1f), 1e-6f)
    }
}
