package id.lcscam.app.domain.model

/**
 * NDI lifecycle states. These are deliberately distinct so the UI never shows
 * "streaming" when the SDK is missing or failed.
 */
sealed interface NdiStatus {
    /** libndibridge / libndi.so are not present (validation build or SDK not bundled). */
    data class SdkNotInstalled(val detail: String) : NdiStatus
    /** The SDK library loaded but NDIlib_initialize() or the CPU check failed. */
    data class InitFailed(val detail: String) : NdiStatus
    /** NDIlib_initialize() succeeded; no sender exists. */
    data class LibraryReady(val version: String) : NdiStatus
    /** NDIlib_send_create() succeeded; the source is advertised on the network. */
    data class SenderInitialized(val sourceName: String) : NdiStatus
    /** Frames are being pushed into the sender. */
    data class SourceActive(val sourceName: String, val connections: Int, val onProgram: Boolean, val onPreview: Boolean) : NdiStatus
    /** Transmission stopped by the user; library still initialised. */
    data object TransmissionStopped : NdiStatus
    /** Sender creation failed or must be re-created (e.g. after a network change). */
    data class SenderError(val detail: String, val retrying: Boolean) : NdiStatus
}

data class StreamStatistics(
    val running: Boolean = false,
    val startedAtElapsedMs: Long = 0L,
    val videoFramesSent: Long = 0L,
    val audioBuffersSent: Long = 0L,
    val framesDroppedPipeline: Long = 0L,
    val framesDroppedCamera: Long = 0L,
    val currentFps: Float = 0f,
    val lastConvertMs: Float = 0f,
    val sendErrors: Long = 0L,
    val senderRestarts: Int = 0,
    val txBitsPerSecond: Long = 0L,
) {
    val totalDropped: Long get() = framesDroppedPipeline + framesDroppedCamera
}

enum class OutputOrientation(val label: String) { LANDSCAPE("Landscape"), PORTRAIT("Portrait") }
