package com.ndicamera.professional.settings

import com.ndicamera.professional.audio.AudioSourceType
import com.ndicamera.professional.domain.model.OutputOrientation

data class OverlaySettings(
    val zebra: Boolean = false,
    val zebraLevel: Int = 95,          // percent of full scale (maps to luma code value)
    val peaking: Boolean = false,
    val peakingSensitivity: Int = 50,  // 0..100
    val peakingColor: Int = 0xFFFF3B30.toInt(),
    val thirds: Boolean = true,
    val crosshair: Boolean = false,
    val safeAreas: Boolean = false,
    val histogram: Boolean = true,
) {
    val zebraThresholdCode: Int get() = (zebraLevel.coerceIn(50, 109) * 255 / 100).coerceAtMost(255)
    val peakingThreshold: Int get() = (110 - peakingSensitivity.coerceIn(0, 100)).coerceAtLeast(8)
}

/** Orientation of the app interface on the camera screen. */
enum class UiOrientation(val label: String) { PORTRAIT("Vertical"), LANDSCAPE("Landscape"), AUTO("Auto-rotate") }

data class AppSettings(
    val sourceName: String = DEFAULT_SOURCE_NAME,
    val ndiGroups: String = "",
    val ndiExtraIps: String = "",
    val clockVideo: Boolean = false,
    val lensKey: String? = null,
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val outputOrientation: OutputOrientation = OutputOrientation.PORTRAIT,
    val uiOrientation: UiOrientation = UiOrientation.PORTRAIT,
    val onboardingDone: Boolean = false,
    val mirrorFrontOutput: Boolean = false,
    val audioEnabled: Boolean = true,
    val audioDeviceId: Int = 0,
    val audioSampleRate: Int = 48_000,
    val audioChannels: Int = 2,
    val audioGainDb: Float = 0f,
    val audioSource: AudioSourceType = AudioSourceType.UNPROCESSED,
    val avOffsetMs: Int = 0,
    val keepScreenOn: Boolean = true,
    val overlays: OverlaySettings = OverlaySettings(),
) {
    companion object {
        const val DEFAULT_SOURCE_NAME = "LCS Cam 01"

        /** NDI source names are shown in receivers; keep them printable and reasonably short. */
        fun sanitizeSourceName(raw: String): String {
            val cleaned = raw.filter { it >= ' ' && it != '\u007f' }.trim().take(63)
            return cleaned.ifEmpty { DEFAULT_SOURCE_NAME }
        }
    }
}
