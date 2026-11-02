package com.ndicamera.professional

import com.ndicamera.professional.camera.WhiteBalanceMath
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WhiteBalanceMathTest {
    @Test fun tungstenNeedsMoreBlueGainThanDaylight() {
        val t = WhiteBalanceMath.gainsFor(3200)
        val d = WhiteBalanceMath.gainsFor(5600)
        assertTrue(t.b > d.b)
        assertTrue(t.r <= d.r + 1e-3f)
    }

    @Test fun gainsAreAtLeastOne() {
        for (k in listOf(2000, 3200, 5600, 6500, 10000)) {
            val g = WhiteBalanceMath.gainsFor(k)
            assertTrue(g.r >= 1f - 1e-4f && g.gEven >= 1f - 1e-4f && g.b >= 1f - 1e-4f)
        }
    }

    @Test fun estimateRoundTrips() {
        for (k in listOf(3000, 4500, 6500)) {
            val g = WhiteBalanceMath.gainsFor(k)
            val est = WhiteBalanceMath.estimateKelvin(g.r, g.b)
            assertTrue("k=$k est=$est", abs(est - k) <= 150)
        }
    }
}
