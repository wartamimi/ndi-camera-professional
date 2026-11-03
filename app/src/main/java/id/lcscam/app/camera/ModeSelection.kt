package id.lcscam.app.camera

import id.lcscam.app.domain.model.Resolution
import id.lcscam.app.domain.model.VideoMode

/** Pure logic deciding which resolution/fps modes a camera can deliver (unit-tested). */
object ModeSelection {
    val FPS_CANDIDATES = listOf(24, 25, 30, 50, 60)

    /**
     * @param yuvSizes sizes supported for ImageFormat.YUV_420_888
     * @param minFrameDurationNs minimum frame duration for a YUV size (0 if unknown)
     * @param fpsRanges CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES as (lower, upper)
     */
    fun supportedModes(
        yuvSizes: List<Resolution>,
        minFrameDurationNs: (Resolution) -> Long,
        fpsRanges: List<Pair<Int, Int>>,
    ): List<VideoMode> {
        val modes = mutableListOf<VideoMode>()
        for (res in Resolution.CANDIDATES) {
            if (yuvSizes.none { it.width == res.width && it.height == res.height }) continue
            val minDur = minFrameDurationNs(res)
            for (fps in FPS_CANDIDATES) {
                if (fpsRangeFor(fps, fpsRanges) == null) continue
                val frameNs = 1_000_000_000L / fps
                if (minDur > 0 && minDur > frameNs + frameNs / 100) continue
                modes += VideoMode(res, fps)
            }
        }
        return modes
    }

    /** Prefers a fixed [fps, fps] range; otherwise a range whose upper bound is fps. */
    fun fpsRangeFor(fps: Int, ranges: List<Pair<Int, Int>>): Pair<Int, Int>? =
        ranges.firstOrNull { it.first == fps && it.second == fps }
            ?: ranges.filter { it.second == fps }.maxByOrNull { it.first }

    /** Picks a preview size with the same aspect ratio, no larger than [maxPixels]. */
    fun previewSizeFor(output: Resolution, candidates: List<Resolution>, maxPixels: Long = 1920L * 1080L): Resolution {
        val aspect = output.width.toFloat() / output.height
        val sameAspect = candidates.filter { kotlin.math.abs(it.width.toFloat() / it.height - aspect) < 0.01f && it.pixels <= maxPixels }
        return sameAspect.maxByOrNull { it.pixels }
            ?: candidates.filter { it.pixels <= maxPixels }.maxByOrNull { it.pixels }
            ?: output
    }

    fun defaultMode(modes: List<VideoMode>): VideoMode? =
        modes.firstOrNull { it.resolution == Resolution(1920, 1080) && it.fps == 30 }
            ?: modes.firstOrNull { it.resolution == Resolution(1280, 720) && it.fps == 30 }
            ?: modes.firstOrNull()

    /** Formats an exposure time as a shutter value, e.g. 1/50 or 0.5". */
    fun shutterLabel(ns: Long): String {
        if (ns <= 0) return "--"
        return if (ns >= 500_000_000L) String.format(java.util.Locale.US, "%.1f\"", ns / 1e9)
        else "1/${Math.round(1e9 / ns)}"
    }

    /** Shutter angle in degrees for an exposure time at a frame rate. */
    fun shutterAngle(ns: Long, fps: Int): Int = Math.round(ns.toDouble() * fps / 1e9 * 360.0).toInt()

    /** Standard shutter stops for a slider, filtered to the supported range. */
    fun shutterStops(minNs: Long, maxNs: Long): List<Long> {
        val denominators = listOf(8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000, 800, 640, 500, 400, 320,
            250, 200, 160, 125, 120, 100, 96, 60, 50, 48, 30, 25, 24, 15, 8, 4, 2)
        return denominators.map { 1_000_000_000L / it }.filter { it in minNs..maxNs }.sorted()
    }

    fun isoStops(min: Int, max: Int): List<Int> {
        val stops = listOf(25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600,
            2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800)
        val inRange = stops.filter { it in min..max }.toMutableList()
        if (inRange.isEmpty() || inRange.first() != min) inRange.add(0, min)
        if (inRange.last() != max) inRange.add(max)
        return inRange.distinct().sorted()
    }
}
