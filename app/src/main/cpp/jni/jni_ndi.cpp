// JNI bridge between Kotlin (com.ndicamera.professional.ndi.NdiNative) and the official NDI SDK.
#include <jni.h>
#include <android/log.h>
#include <Processing.NDI.Lib.h>

#include <string>
#include <vector>

#include "ndi/ndi_finder.h"
#include "ndi/ndi_sender.h"

#define LOG_TAG "NdiBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using ndicam::NdiSender;

namespace {
inline NdiSender* from_handle(jlong h) { return reinterpret_cast<NdiSender*>(h); }

std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}
}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeVersion(JNIEnv* env, jclass) {
    const char* v = NDIlib_version();
    return env->NewStringUTF(v ? v : "unknown");
}

// 0 = initialised, 1 = CPU not supported, 2 = NDIlib_initialize() failed
JNIEXPORT jint JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeInitialize(JNIEnv*, jclass) {
    if (!NDIlib_is_supported_CPU()) return 1;
    return NDIlib_initialize() ? 0 : 2;
}

JNIEXPORT void JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeShutdown(JNIEnv*, jclass) {
    NDIlib_destroy();
}

JNIEXPORT jlong JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeCreateSender(JNIEnv* env, jclass, jstring name, jstring groups, jboolean clockVideo) {
    const std::string n = jstr(env, name);
    const std::string g = jstr(env, groups);
    NdiSender* s = NdiSender::create(n.c_str(), g.c_str(), clockVideo == JNI_TRUE);
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT void JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeDestroySender(JNIEnv*, jclass, jlong handle) {
    delete from_handle(handle);
}

JNIEXPORT jint JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeSendVideo(
        JNIEnv* env, jclass, jlong handle,
        jobject yBuf, jint yRowStride, jobject uBuf, jobject vBuf, jint uvRowStride, jint uvPixelStride,
        jint width, jint height, jint rotation, jboolean mirror, jint fpsN, jint fpsD, jlong timecode) {
    NdiSender* s = from_handle(handle);
    if (!s) return -1;
    ndicam::Yuv420Planes p{};
    p.y = static_cast<const uint8_t*>(env->GetDirectBufferAddress(yBuf));
    p.u = static_cast<const uint8_t*>(env->GetDirectBufferAddress(uBuf));
    p.v = static_cast<const uint8_t*>(env->GetDirectBufferAddress(vBuf));
    if (!p.y || !p.u || !p.v) return -3;
    p.y_row_stride = yRowStride;
    p.uv_row_stride = uvRowStride;
    p.uv_pixel_stride = uvPixelStride;
    p.width = width;
    p.height = height;
    ndicam::Transform t{rotation, mirror == JNI_TRUE};
    return s->send_video(p, t, fpsN, fpsD, timecode);
}

JNIEXPORT jint JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeSendAudio(
        JNIEnv* env, jclass, jlong handle, jfloatArray interleaved, jint frames, jint channels, jint sampleRate, jlong timecode) {
    NdiSender* s = from_handle(handle);
    if (!s || !interleaved) return -1;
    if (env->GetArrayLength(interleaved) < frames * channels) return -2;
    jfloat* data = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(interleaved, nullptr));
    if (!data) return -3;
    const int r = s->send_audio_interleaved(data, frames, channels, sampleRate, timecode);
    env->ReleasePrimitiveArrayCritical(interleaved, data, JNI_ABORT);
    return r;
}

JNIEXPORT jint JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeGetConnections(JNIEnv*, jclass, jlong handle) {
    NdiSender* s = from_handle(handle);
    return s ? s->connections(0) : -1;
}

// bit0 = on program, bit1 = on preview
JNIEXPORT jint JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeGetTally(JNIEnv*, jclass, jlong handle) {
    NdiSender* s = from_handle(handle);
    if (!s) return 0;
    bool program = false, preview = false;
    s->tally(&program, &preview);
    return (program ? 1 : 0) | (preview ? 2 : 0);
}

JNIEXPORT jstring JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeGetSourceName(JNIEnv* env, jclass, jlong handle) {
    NdiSender* s = from_handle(handle);
    if (!s) return nullptr;
    const std::string n = s->source_name();
    return n.empty() ? nullptr : env->NewStringUTF(n.c_str());
}

JNIEXPORT jlongArray JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeGetCounters(JNIEnv* env, jclass, jlong handle) {
    NdiSender* s = from_handle(handle);
    jlong vals[2] = {0, 0};
    if (s) {
        vals[0] = static_cast<jlong>(s->video_frames_sent());
        vals[1] = static_cast<jlong>(s->audio_frames_sent());
    }
    jlongArray arr = env->NewLongArray(2);
    if (arr) env->SetLongArrayRegion(arr, 0, 2, vals);
    return arr;
}

JNIEXPORT jobjectArray JNICALL
Java_com_ndicamera_professional_ndi_NdiNative_nativeFindSources(JNIEnv* env, jclass, jint timeoutMs, jstring extraIps) {
    const std::string extra = jstr(env, extraIps);
    const std::vector<std::string> names = ndicam::find_sources(timeoutMs, extra.c_str());
    jclass strCls = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(names.size()), strCls, nullptr);
    for (size_t i = 0; i < names.size(); ++i) {
        jstring js = env->NewStringUTF(names[i].c_str());
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), js);
        env->DeleteLocalRef(js);
    }
    return arr;
}

}  // extern "C"
