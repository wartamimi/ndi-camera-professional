// JNI for the NDI-independent media core: overlay analysis and audio DSP.
#include <jni.h>
#include <android/bitmap.h>

#include "audio/audio_utils.h"
#include "camera/frame_analyzer.h"

extern "C" {

// stats (FloatArray, size >= 3): [mean luma, clipped fraction, crushed fraction]
JNIEXPORT jint JNICALL
Java_com_ndicamera_professional_camera_NativeFrameAnalyzer_nativeAnalyze(
        JNIEnv* env, jclass, jobject yBuf, jint yRowStride, jint width, jint height,
        jobject bitmap, jboolean zebra, jint zebraThreshold, jboolean peaking, jint peakingThreshold,
        jint peakingColor, jint stripePhase, jintArray histogramOut, jfloatArray statsOut) {
    const uint8_t* y = static_cast<const uint8_t*>(env->GetDirectBufferAddress(yBuf));
    if (!y) return -1;
    ndicam::AnalyzerConfig cfg{};
    cfg.zebra = zebra == JNI_TRUE;
    cfg.zebra_threshold = zebraThreshold;
    cfg.peaking = peaking == JNI_TRUE;
    cfg.peaking_threshold = peakingThreshold;
    cfg.peaking_color = static_cast<uint32_t>(peakingColor);
    cfg.stripe_phase = stripePhase;

    uint32_t* pixels = nullptr;
    int bw = 64, bh = 36, stride_px = 64;
    bool locked = false;
    if (bitmap) {
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return -2;
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return -3;
        void* addr = nullptr;
        if (AndroidBitmap_lockPixels(env, bitmap, &addr) != ANDROID_BITMAP_RESULT_SUCCESS) return -4;
        locked = true;
        pixels = static_cast<uint32_t*>(addr);
        bw = static_cast<int>(info.width);
        bh = static_cast<int>(info.height);
        stride_px = static_cast<int>(info.stride / 4);
    }
    // Android RGBA_8888 is stored as R,G,B,A bytes (little-endian ABGR word). Convert ARGB input.
    const uint32_t c = cfg.peaking_color;
    cfg.peaking_color = (c & 0xFF00FF00u) | ((c >> 16) & 0xFFu) | ((c & 0xFFu) << 16);

    ndicam::AnalyzerResult res;
    ndicam::analyze_luma(y, yRowStride, width, height, cfg, pixels, bw, bh, stride_px, &res);
    if (locked) AndroidBitmap_unlockPixels(env, bitmap);

    if (histogramOut && env->GetArrayLength(histogramOut) >= 256) {
        env->SetIntArrayRegion(histogramOut, 0, 256, reinterpret_cast<const jint*>(res.histogram));
    }
    if (statsOut && env->GetArrayLength(statsOut) >= 3) {
        const jfloat s[3] = {res.mean_luma, res.clipped_fraction, res.crushed_fraction};
        env->SetFloatArrayRegion(statsOut, 0, 3, s);
    }
    return 0;
}

// levelsOut (FloatArray, size >= 2*channels): [peak0..peakN-1, rms0..rmsN-1]
JNIEXPORT void JNICALL
Java_com_ndicamera_professional_audio_NativeAudio_nativeProcess(
        JNIEnv* env, jclass, jfloatArray samples, jint frames, jint channels, jfloat gain, jboolean mute, jfloatArray levelsOut) {
    if (!samples || channels <= 0 || channels > 8) return;
    if (env->GetArrayLength(samples) < frames * channels) return;
    jfloat* s = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(samples, nullptr));
    if (!s) return;
    ndicam::AudioLevels lv;
    ndicam::process_interleaved(s, frames, channels, gain, mute == JNI_TRUE, &lv);
    env->ReleasePrimitiveArrayCritical(samples, s, 0);
    if (levelsOut && env->GetArrayLength(levelsOut) >= 2 * channels) {
        env->SetFloatArrayRegion(levelsOut, 0, channels, lv.peak);
        env->SetFloatArrayRegion(levelsOut, channels, channels, lv.rms);
    }
}

}  // extern "C"
