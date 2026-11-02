package com.ndicamera.professional.ndi

import java.nio.ByteBuffer

/** JNI declarations implemented in app/src/main/cpp/jni/jni_ndi.cpp (libndibridge.so). */
object NdiNative {
    @JvmStatic external fun nativeVersion(): String
    @JvmStatic external fun nativeInitialize(): Int
    @JvmStatic external fun nativeShutdown()
    @JvmStatic external fun nativeCreateSender(name: String, groups: String?, clockVideo: Boolean): Long
    @JvmStatic external fun nativeDestroySender(handle: Long)
    @JvmStatic external fun nativeSendVideo(
        handle: Long,
        y: ByteBuffer, yRowStride: Int, u: ByteBuffer, v: ByteBuffer, uvRowStride: Int, uvPixelStride: Int,
        width: Int, height: Int, rotation: Int, mirror: Boolean, fpsN: Int, fpsD: Int, timecode: Long, watermark: Boolean,
    ): Int
    @JvmStatic external fun nativeSendAudio(handle: Long, interleaved: FloatArray, frames: Int, channels: Int, sampleRate: Int, timecode: Long): Int
    @JvmStatic external fun nativeGetConnections(handle: Long): Int
    @JvmStatic external fun nativeGetTally(handle: Long): Int
    @JvmStatic external fun nativeGetSourceName(handle: Long): String?
    @JvmStatic external fun nativeGetCounters(handle: Long): LongArray
    @JvmStatic external fun nativeFindSources(timeoutMs: Int, extraIps: String?): Array<String>
}
