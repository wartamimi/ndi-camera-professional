package com.ndicamera.professional

import com.ndicamera.professional.audio.AudioMath
import com.ndicamera.professional.settings.AppSettings
import com.ndicamera.professional.settings.OverlaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test fun defaultSourceName() {
        assertEquals("NDI Camera - Android 01", AppSettings().sourceName)
        assertEquals("NDI Camera - Android 01", AppSettings.sanitizeSourceName("   "))
        assertEquals("Stage Left", AppSettings.sanitizeSourceName(" Stage\u0007 Left "))
        assertEquals(63, AppSettings.sanitizeSourceName("x".repeat(200)).length)
    }

    @Test fun overlayThresholds() {
        assertEquals(242, OverlaySettings(zebraLevel = 95).zebraThresholdCode)
        assertEquals(255, OverlaySettings(zebraLevel = 100).zebraThresholdCode)
        assertEquals(60, OverlaySettings(peakingSensitivity = 50).peakingThreshold)
    }

    @Test fun audioMath() {
        assertEquals(960, AudioMath.framesPerBuffer(48_000))
        assertEquals(0f, AudioMath.linearToDb(1f), 1e-4f)
        assertEquals(-6.0206f, AudioMath.linearToDb(0.5f), 1e-3f)
        assertEquals(AudioMath.FLOOR_DB, AudioMath.linearToDb(0f), 0f)
        // 480 frames after the anchor at 48 kHz = +10 ms
        assertEquals(1_010_000_000L, AudioMath.bufferStartNs(1000, 1_000_000_000L, 1480, 48_000))
    }
}
