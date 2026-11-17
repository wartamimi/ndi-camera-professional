package id.lcscam.app

import id.lcscam.app.streaming.LagDiagnosis
import id.lcscam.app.streaming.LagDiagnosis.Cause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LagDiagnosisTest {
    private val healthy = LagDiagnosis.Input(
        targetFps = 30, cameraFps = 30f, pipelineDropsPerSec = 0, convertMs = 4f, pixels = 1920L * 1080,
        thermalThrottling = false, onWifi = true, wifiRssiDbm = -50, wifiLinkMbps = 866, wifiFrequencyMhz = 5180,
        txBitsPerSecond = 120_000_000, receivers = 1,
    )

    @Test fun healthyStreamHasNoCause() = assertEquals(emptyList<Cause>(), LagDiagnosis.diagnose(healthy))

    @Test fun detectsDarkSceneSlowCamera() =
        assertEquals(listOf(Cause.CAMERA_SLOW), LagDiagnosis.diagnose(healthy.copy(cameraFps = 20f)))

    @Test fun detectsPhoneTooSlow() {
        assertEquals(listOf(Cause.PHONE_TOO_SLOW), LagDiagnosis.diagnose(healthy.copy(pipelineDropsPerSec = 5)))
        assertEquals(listOf(Cause.PHONE_TOO_SLOW), LagDiagnosis.diagnose(healthy.copy(targetFps = 60, cameraFps = 60f, convertMs = 15f)))
    }

    @Test fun detectsWifiProblems() {
        val c = LagDiagnosis.diagnose(healthy.copy(wifiFrequencyMhz = 2437, wifiRssiDbm = -75, wifiLinkMbps = 72))
        assertTrue(Cause.WIFI_2_4_GHZ in c && Cause.WIFI_WEAK in c && Cause.WIFI_LINK_TOO_SLOW in c)
    }

    @Test fun bandwidthEstimate() {
        assertEquals(125, LagDiagnosis.requiredMbps(1920L * 1080, 30))
        assertTrue(LagDiagnosis.requiredMbps(1920L * 1080, 60) in 150..180)
        assertTrue(LagDiagnosis.requiredMbps(3840L * 2160, 30) in 200..240)
    }
}
