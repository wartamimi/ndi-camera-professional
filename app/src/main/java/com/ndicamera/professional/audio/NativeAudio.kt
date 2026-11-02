package com.ndicamera.professional.audio

/** Gain, mute and metering for interleaved float PCM (libndicam.so). */
object NativeAudio {
    init { System.loadLibrary("ndicam") }

    /** levelsOut must hold 2 * channels floats: peaks then RMS, linear. */
    @JvmStatic external fun nativeProcess(samples: FloatArray, frames: Int, channels: Int, gain: Float, mute: Boolean, levelsOut: FloatArray)
}
