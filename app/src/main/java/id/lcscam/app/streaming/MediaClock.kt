package id.lcscam.app.streaming

import android.os.SystemClock

/**
 * Maps capture timestamps from camera (CLOCK_MONOTONIC or CLOCK_BOOTTIME) and audio
 * (AudioTimestamp, CLOCK_MONOTONIC) to NDI timecodes (100 ns units since the Unix epoch),
 * so that receivers can align audio and video from a common clock.
 */
class MediaClock {
    @Volatile private var epochOffsetNs: Long = computeEpochOffset()
    @Volatile var audioOffsetNs: Long = 0L

    fun resync() { epochOffsetNs = computeEpochOffset() }

    fun videoTimecode(sensorTimestampNs: Long, isBoottime: Boolean): Long {
        val mono = if (isBoottime) sensorTimestampNs - (SystemClock.elapsedRealtimeNanos() - System.nanoTime()) else sensorTimestampNs
        return (mono + epochOffsetNs) / 100L
    }

    fun audioTimecode(monotonicNs: Long): Long = (monotonicNs + audioOffsetNs + epochOffsetNs) / 100L

    private fun computeEpochOffset(): Long = System.currentTimeMillis() * 1_000_000L - System.nanoTime()
}
