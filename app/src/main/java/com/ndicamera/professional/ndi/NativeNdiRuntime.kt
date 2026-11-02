package com.ndicamera.professional.ndi

import android.content.Context
import android.net.nsd.NsdManager
import android.util.Log
import com.ndicamera.professional.BuildConfig
import com.ndicamera.professional.domain.ndi.NdiInitResult
import com.ndicamera.professional.domain.ndi.NdiRuntime
import com.ndicamera.professional.domain.ndi.NdiSender
import com.ndicamera.professional.domain.ndi.SenderConfig
import com.ndicamera.professional.domain.ndi.Tally
import com.ndicamera.professional.domain.ndi.YuvFrame
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * NdiRuntime backed by the official NDI SDK through libndibridge.so.
 *
 * NDI on Android relies on the platform NSD service: per the NDI SDK documentation an
 * NsdManager instance must exist before any sender/finder/receiver is created and for as long
 * as they live. This class holds that instance for the whole process lifetime.
 */
class NativeNdiRuntime(context: Context) : NdiRuntime {
    private val appContext = context.applicationContext
    @Volatile private var nsdManager: NsdManager? = null
    @Volatile private var initialized = false
    @Volatile private var version: String = ""

    override fun initialize(): NdiInitResult = synchronized(this) {
        if (initialized) return NdiInitResult.Ready(version)
        if (BuildConfig.NDI_VALIDATION_BUILD) {
            return NdiInitResult.SdkNotInstalled(
                "CI validation build: the NDI SDK was not bundled, so this APK cannot send NDI."
            )
        }
        // Must be created before any NDI object (NDI SDK "Platform Considerations: Android").
        nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        try {
            System.loadLibrary("ndibridge")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "libndibridge/libndi not loadable", e)
            return NdiInitResult.SdkNotInstalled("libndi.so could not be loaded: ${e.message}")
        }
        val v = try { NdiNative.nativeVersion() } catch (e: UnsatisfiedLinkError) {
            return NdiInitResult.SdkNotInstalled("NDI bridge symbols missing: ${e.message}")
        }
        return when (val rc = NdiNative.nativeInitialize()) {
            0 -> {
                initialized = true
                version = v
                NdiInitResult.Ready(v)
            }
            1 -> NdiInitResult.Failed("This CPU is not supported by the NDI SDK ($v)")
            else -> NdiInitResult.Failed("NDIlib_initialize() failed (code $rc, $v)")
        }
    }

    override fun createSender(config: SenderConfig): NdiSender? {
        if (!initialized) return null
        val handle = NdiNative.nativeCreateSender(config.sourceName, config.groups, config.clockVideo)
        if (handle == 0L) return null
        return NativeNdiSender(handle)
    }

    override fun findSources(timeoutMs: Int, extraIps: String?): List<String> {
        if (!initialized) return emptyList()
        return NdiNative.nativeFindSources(timeoutMs, extraIps).toList()
    }

    override fun shutdown() = synchronized(this) {
        if (initialized) {
            NdiNative.nativeShutdown()
            initialized = false
        }
    }

    private companion object { const val TAG = "NativeNdiRuntime" }
}

private class NativeNdiSender(private var handle: Long) : NdiSender {
    // Sends (video thread, audio thread, stats poller) share the read lock; close() takes the
    // write lock so the native sender can never be destroyed while a call is in flight.
    private val rw = ReentrantReadWriteLock()

    private inline fun <T> withHandle(default: T, block: (Long) -> T): T {
        rw.read {
            val h = handle
            return if (h == 0L) default else block(h)
        }
    }

    override fun sendVideo(frame: YuvFrame, rotation: Int, mirror: Boolean, fpsN: Int, fpsD: Int, timecode100ns: Long): Int =
        withHandle(-1) { h ->
            NdiNative.nativeSendVideo(
                h, frame.y, frame.yRowStride, frame.u, frame.v, frame.uvRowStride, frame.uvPixelStride,
                frame.width, frame.height, rotation, mirror, fpsN, fpsD, timecode100ns,
            )
        }

    override fun sendAudio(interleaved: FloatArray, frames: Int, channels: Int, sampleRate: Int, timecode100ns: Long): Int =
        withHandle(-1) { h -> NdiNative.nativeSendAudio(h, interleaved, frames, channels, sampleRate, timecode100ns) }

    override fun connections(): Int = withHandle(0) { NdiNative.nativeGetConnections(it) }

    override fun tally(): Tally = withHandle(Tally(false, false)) { h ->
        val bits = NdiNative.nativeGetTally(h)
        Tally(bits and 1 != 0, bits and 2 != 0)
    }

    override fun sourceName(): String? = withHandle(null) { NdiNative.nativeGetSourceName(it) }

    override fun close() {
        rw.write {
            val h = handle
            handle = 0L
            if (h != 0L) NdiNative.nativeDestroySender(h)
        }
    }
}
