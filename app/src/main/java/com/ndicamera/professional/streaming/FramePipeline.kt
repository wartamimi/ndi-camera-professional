package com.ndicamera.professional.streaming

import android.media.Image
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.ndicamera.professional.camera.FrameSink
import com.ndicamera.professional.camera.NativeFrameAnalyzer
import com.ndicamera.professional.domain.model.LensDescriptor
import com.ndicamera.professional.domain.model.LensFacing
import com.ndicamera.professional.domain.model.VideoMode
import com.ndicamera.professional.domain.ndi.NdiSender
import com.ndicamera.professional.domain.ndi.YuvFrame
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Camera-to-NDI video path. Frames arrive on the camera-frames thread and are handed to a
 * dedicated "ndi-video" thread through a single-slot mailbox: if the NDI thread is still busy
 * when a newer frame arrives, the older pending frame is dropped (and counted) instead of
 * building latency. The only pixel copy is the native YUV_420_888 -> NV12 conversion straight
 * into the SDK's async send buffer.
 */
class FramePipeline(
    private val clock: MediaClock,
    private val analyzer: NativeFrameAnalyzer,
) : FrameSink {

    private class Pending(
        val image: Image, val ts: Long, val boottime: Boolean, val mode: VideoMode,
        val lens: LensDescriptor, val sensorOrientation: Int,
    )

    private val thread = HandlerThread("ndi-video", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)
    private val slot = AtomicReference<Pending?>(null)

    @Volatile var sender: NdiSender? = null
    @Volatile var deviceOrientationProvider: () -> Int = { 270 }
    @Volatile var mirrorFrontCamera: Boolean = false
    @Volatile var analysisEnabled: Boolean = true
    /** Free plan: burn the watermark into the NDI output. */
    @Volatile var watermark: Boolean = true

    val framesSent = AtomicLong()
    val framesDroppedPipeline = AtomicLong()
    val framesDroppedCamera = AtomicLong()
    val sendErrors = AtomicLong()
    @Volatile var lastConvertMs: Float = 0f
        private set

    private var lastSensorTs = 0L
    private var frameCounter = 0L

    fun resetCounters() {
        framesSent.set(0); framesDroppedPipeline.set(0); framesDroppedCamera.set(0); sendErrors.set(0)
        lastSensorTs = 0L
    }

    override fun onFrame(image: Image, sensorTimestampNs: Long, timestampIsBoottime: Boolean, mode: VideoMode, lens: LensDescriptor, sensorOrientation: Int) {
        // Camera-side drop detection from sensor timestamp gaps (camera-frames thread only).
        val expected = 1_000_000_000L / mode.fps
        if (lastSensorTs != 0L && sender != null) {
            val gap = sensorTimestampNs - lastSensorTs
            if (gap > expected * 3 / 2) framesDroppedCamera.addAndGet(((gap + expected / 2) / expected - 1).coerceAtLeast(1))
        }
        lastSensorTs = sensorTimestampNs
        val previous = slot.getAndSet(Pending(image, sensorTimestampNs, timestampIsBoottime, mode, lens, sensorOrientation))
        if (previous != null) {
            previous.image.close()
            if (sender != null) framesDroppedPipeline.incrementAndGet()
        }
        handler.post(processRunnable)
    }

    private val processRunnable = Runnable {
        val p = slot.getAndSet(null) ?: return@Runnable
        try {
            process(p)
        } catch (e: IllegalStateException) {
            // Image was closed underneath us (reader torn down): drop silently.
            Log.w(TAG, "Frame skipped: ${e.message}")
        } finally {
            p.image.close()
        }
    }

    private fun process(p: Pending) {
        val planes = p.image.planes
        val y = planes[0]
        val u = planes[1]
        val v = planes[2]
        val s = sender
        if (s != null) {
            val front = p.lens.facing == LensFacing.FRONT
            val rotation = com.ndicamera.professional.camera.OrientationMath.outputRotation(
                p.sensorOrientation, deviceOrientationProvider(), front)
            val frame = YuvFrame(y.buffer, y.rowStride, u.buffer, v.buffer, u.rowStride, u.pixelStride, p.image.width, p.image.height)
            val t0 = SystemClock.elapsedRealtimeNanos()
            val rc = s.sendVideo(frame, rotation, front && mirrorFrontCamera, p.mode.fps * 1000, 1000,
                clock.videoTimecode(p.ts, p.boottime), watermark)
            lastConvertMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6f
            if (rc == 0) framesSent.incrementAndGet() else sendErrors.incrementAndGet()
        }
        frameCounter++
        // Overlay analysis at roughly 10 Hz on a downsampled grid.
        if (analysisEnabled && frameCounter % maxOf(1, p.mode.fps / 10) == 0L) {
            analyzer.analyze(y.buffer, y.rowStride, p.image.width, p.image.height)
        }
    }

    override fun drain() {
        slot.getAndSet(null)?.image?.close()
        if (Thread.currentThread() === thread) return
        val latch = CountDownLatch(1)
        handler.post { slot.getAndSet(null)?.image?.close(); latch.countDown() }
        latch.await(1, TimeUnit.SECONDS)
    }

    fun release() {
        drain()
        thread.quitSafely()
    }

    private companion object { const val TAG = "FramePipeline" }
}
