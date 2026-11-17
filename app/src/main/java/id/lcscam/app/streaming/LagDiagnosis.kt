package id.lcscam.app.streaming

import id.lcscam.app.i18n.tr
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Turns live stream statistics into the most likely reasons for visible lag on the receiver,
 * so the cause can be fixed at its source (camera, phone, or network) instead of guessed.
 * Pure logic, unit-tested.
 */
object LagDiagnosis {
    data class Input(
        val targetFps: Int,
        /** Frames per second actually arriving from the camera. */
        val cameraFps: Float,
        /** Frames dropped in the last second because the NDI thread was still busy. */
        val pipelineDropsPerSec: Long,
        /** Time to convert + hand one frame to the SDK. */
        val convertMs: Float,
        val pixels: Long,
        val thermalThrottling: Boolean,
        val onWifi: Boolean,
        val wifiRssiDbm: Int?,
        val wifiLinkMbps: Int?,
        val wifiFrequencyMhz: Int?,
        /** Measured app transmit rate. */
        val txBitsPerSecond: Long,
        val receivers: Int,
    )

    enum class Cause { CAMERA_SLOW, PHONE_TOO_SLOW, THERMAL, WIFI_2_4_GHZ, WIFI_WEAK, WIFI_LINK_TOO_SLOW }

    /**
     * Approximate NDI (SpeedHQ) bandwidth in Mbit/s for a mode. Anchored at the NDI figure of
     * ~125 Mbit/s for 1080p30; grows sub-linearly with pixel rate (1080p60 ≈ 165, 4K30 ≈ 220).
     */
    fun requiredMbps(pixels: Long, fps: Int): Int {
        val ratio = (pixels.toDouble() * fps) / (1920.0 * 1080.0 * 30.0)
        return (125.0 * ratio.coerceAtLeast(0.05).pow(0.4)).roundToInt()
    }

    fun diagnose(i: Input): List<Cause> {
        val out = mutableListOf<Cause>()
        if (i.thermalThrottling) out += Cause.THERMAL
        // Camera itself is slow (typically auto exposure lengthening frames in dim light).
        if (i.targetFps > 0 && i.cameraFps > 0f && i.cameraFps < i.targetFps * 0.9f) out += Cause.CAMERA_SLOW
        // Phone cannot keep up: frames are being dropped before NDI, or one frame takes longer
        // than the frame interval.
        val frameMs = if (i.targetFps > 0) 1000f / i.targetFps else 33f
        if (i.receivers > 0 && (i.pipelineDropsPerSec >= 2 || i.convertMs > frameMs * 0.8f)) out += Cause.PHONE_TOO_SLOW
        if (i.onWifi) {
            val need = requiredMbps(i.pixels, i.targetFps)
            if (i.wifiFrequencyMhz != null && i.wifiFrequencyMhz in 2400..2500) out += Cause.WIFI_2_4_GHZ
            if (i.wifiRssiDbm != null && i.wifiRssiDbm < -67) out += Cause.WIFI_WEAK
            // A Wi-Fi link needs roughly twice the payload rate to carry it steadily.
            if (i.wifiLinkMbps != null && i.wifiLinkMbps < need * 2) out += Cause.WIFI_LINK_TOO_SLOW
        }
        return out
    }

    fun message(c: Cause, i: Input): String = when (c) {
        Cause.THERMAL -> tr("Phone is hot and throttling the CPU. Remove the case, use a fan/cooler, keep it out of the sun.",
            "HP panas dan CPU diperlambat. Lepas casing, pakai kipas/pendingin, jauhkan dari matahari.")
        Cause.CAMERA_SLOW -> tr("The camera delivers only ${i.cameraFps.roundToInt()} of ${i.targetFps} fps, usually because the scene is too dark for auto exposure. Add light, or set a manual shutter of 1/${i.targetFps * 2} or faster.",
            "Kamera hanya memberi ${i.cameraFps.roundToInt()} dari ${i.targetFps} fps, biasanya karena terlalu gelap untuk auto exposure. Tambah cahaya, atau set shutter manual 1/${i.targetFps * 2} atau lebih cepat.")
        Cause.PHONE_TOO_SLOW -> tr("This phone cannot convert/compress every frame in time (${String.format(java.util.Locale.US, "%.1f", i.convertMs)} ms per frame). Close other apps, or choose a lower frame rate.",
            "HP tidak sempat mengonversi/mengompres setiap frame (${String.format(java.util.Locale.US, "%.1f", i.convertMs)} ms per frame). Tutup aplikasi lain, atau pilih frame rate lebih rendah.")
        Cause.WIFI_2_4_GHZ -> tr("Connected on 2.4 GHz Wi-Fi, which is too slow and too busy for full-quality NDI. Use 5 GHz / 6 GHz or USB Ethernet.",
            "Terhubung ke Wi-Fi 2.4 GHz, terlalu lambat dan padat untuk NDI kualitas penuh. Pakai 5 GHz / 6 GHz atau Ethernet USB.")
        Cause.WIFI_WEAK -> tr("Weak Wi-Fi signal (${i.wifiRssiDbm} dBm). Move closer to the router/access point (aim for -60 dBm or better).",
            "Sinyal Wi-Fi lemah (${i.wifiRssiDbm} dBm). Dekatkan ke router/access point (targetkan -60 dBm atau lebih baik).")
        Cause.WIFI_LINK_TOO_SLOW -> tr("Wi-Fi link is ${i.wifiLinkMbps} Mbit/s; this mode needs about ${requiredMbps(i.pixels, i.targetFps)} Mbit/s of NDI data, so the link should be ${requiredMbps(i.pixels, i.targetFps) * 2}+ Mbit/s. Connect the receiver PC by cable and use 5 GHz.",
            "Link Wi-Fi ${i.wifiLinkMbps} Mbit/s; mode ini butuh sekitar ${requiredMbps(i.pixels, i.targetFps)} Mbit/s data NDI, jadi link sebaiknya ${requiredMbps(i.pixels, i.targetFps) * 2}+ Mbit/s. Sambungkan PC receiver dengan kabel dan pakai 5 GHz.")
    }
}
