package com.ndicamera.professional.audio

import kotlin.math.log10
import kotlin.math.pow

object AudioMath {
    const val FLOOR_DB = -120f
    fun linearToDb(v: Float): Float = if (v <= 1e-6f) FLOOR_DB else (20f * log10(v)).coerceAtLeast(FLOOR_DB)
    fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    /** Frames per NDI audio buffer: 20 ms keeps latency low with modest per-call overhead. */
    fun framesPerBuffer(sampleRate: Int): Int = (sampleRate / 50).coerceAtLeast(64)

    /** Monotonic time of the first frame in a buffer, from an AudioTimestamp anchor. */
    fun bufferStartNs(anchorFramePos: Long, anchorNanoTime: Long, bufferStartFramePos: Long, sampleRate: Int): Long =
        anchorNanoTime + (bufferStartFramePos - anchorFramePos) * 1_000_000_000L / sampleRate
}
