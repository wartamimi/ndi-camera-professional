package id.lcscam.app.streaming

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Decouples microphone capture from NDI audio sending.
 *
 * Before, `NDIlib_send_send_audio_v3` was called directly on the AudioRecord thread. When the
 * network stalls (weak Wi-Fi, retransmissions) that call can take longer than one 20 ms buffer,
 * AudioRecord overruns and the receiver hears clicks or gaps even after the network recovers.
 * Now capture only copies the buffer into a small pool and returns; a separate thread sends.
 *
 * The queue holds at most [capacity] buffers (16 x 20 ms = 320 ms). If the network is stalled
 * longer than that, the oldest buffer is dropped (counted in [dropped]) so latency cannot grow
 * without bound. Every buffer keeps its capture timecode, so receivers stay in A/V sync.
 * Buffers are recycled: no allocation per buffer after warm-up.
 */
class AudioSendQueue(
    private val capacity: Int = 16,
    private val onThreadStart: () -> Unit = {},
    private val send: (data: FloatArray, frames: Int, channels: Int, sampleRate: Int, timecode: Long) -> Unit,
) {
    private class Buf(var data: FloatArray) {
        var frames = 0; var channels = 0; var rate = 0; var timecode = 0L
    }

    private val free = ArrayBlockingQueue<Buf>(capacity + 1)
    private val filled = ArrayBlockingQueue<Buf>(capacity + 1)
    val dropped = AtomicLong()
    private var allocated = 0  // capture thread only
    @Volatile private var running = true

    private val thread = Thread({
        onThreadStart()
        while (running) {
            val b = try { filled.poll(100, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { null } ?: continue
            try { send(b.data, b.frames, b.channels, b.rate, b.timecode) } finally { free.offer(b) }
        }
    }, "ndi-audio").apply { isDaemon = true; start() }

    /** Called on the capture thread. Copies [src] and returns immediately. */
    fun offer(src: FloatArray, frames: Int, channels: Int, sampleRate: Int, timecode: Long) {
        val n = frames * channels
        val b = free.poll() ?: if (allocated < capacity) {
            allocated++
            Buf(FloatArray(n))
        } else {
            // Every buffer is pending or being sent: drop the oldest pending one and reuse it.
            // (null only if the sender thread grabbed it this instant; then wait for it briefly.)
            filled.poll()?.also { dropped.incrementAndGet() }
                ?: free.poll(5, TimeUnit.MILLISECONDS)
                ?: run { dropped.incrementAndGet(); return }
        }
        if (b.data.size < n) b.data = FloatArray(n)
        System.arraycopy(src, 0, b.data, 0, n)
        b.frames = frames; b.channels = channels; b.rate = sampleRate; b.timecode = timecode
        filled.offer(b)
    }

    /** Discards pending audio (e.g. when streaming stops or the sender is replaced). */
    fun clear() {
        while (true) free.offer(filled.poll() ?: break)
    }

    val pending: Int get() = filled.size

    fun close() {
        running = false
        thread.interrupt()
    }
}
