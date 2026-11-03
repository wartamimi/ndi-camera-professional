package id.lcscam.app.domain.ndi

import java.nio.ByteBuffer

/** Plane view of a YUV_420_888 frame. Buffers must be direct and stay valid for the call. */
class YuvFrame(
    val y: ByteBuffer,
    val yRowStride: Int,
    val u: ByteBuffer,
    val v: ByteBuffer,
    val uvRowStride: Int,
    val uvPixelStride: Int,
    val width: Int,
    val height: Int,
)

data class SenderConfig(
    val sourceName: String,
    val groups: String?,
    val clockVideo: Boolean,
)

data class Tally(val onProgram: Boolean, val onPreview: Boolean)

sealed interface NdiInitResult {
    data class Ready(val version: String) : NdiInitResult
    data class SdkNotInstalled(val detail: String) : NdiInitResult
    data class Failed(val detail: String) : NdiInitResult
}

/**
 * Abstraction over the NDI SDK. Application logic depends only on this interface,
 * which keeps the proprietary SDK isolated and makes the streaming logic unit-testable.
 */
interface NdiRuntime {
    fun initialize(): NdiInitResult
    fun createSender(config: SenderConfig): NdiSender?
    /** Runs NDI discovery on this device (includes local sources). Blocking. */
    fun findSources(timeoutMs: Int, extraIps: String?): List<String>
    fun shutdown()
}

interface NdiSender : AutoCloseable {
    /** Converts the frame to NV12 natively and sends it asynchronously. 0 = success. */
    fun sendVideo(frame: YuvFrame, rotation: Int, mirror: Boolean, fpsN: Int, fpsD: Int, timecode100ns: Long, watermark: Boolean): Int
    /** Sends interleaved float PCM (converted to planar FLTP natively). 0 = success. */
    fun sendAudio(interleaved: FloatArray, frames: Int, channels: Int, sampleRate: Int, timecode100ns: Long): Int
    fun connections(): Int
    fun tally(): Tally
    fun sourceName(): String?
    override fun close()
}
