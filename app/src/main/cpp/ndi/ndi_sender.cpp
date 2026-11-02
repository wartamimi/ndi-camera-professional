#include "ndi/ndi_sender.h"

#include <android/log.h>

#include "audio/audio_utils.h"

#define LOG_TAG "NdiSender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace ndicam {

NdiSender* NdiSender::create(const char* name, const char* groups, bool clock_video) {
    NDIlib_send_create_t desc;
    desc.p_ndi_name = name;
    desc.p_groups = (groups && groups[0]) ? groups : nullptr;
    // The camera is the clock: frames are pushed as they arrive from the sensor, so the SDK
    // must not additionally rate-limit unless the user asks for it.
    desc.clock_video = clock_video;
    desc.clock_audio = false;
    NDIlib_send_instance_t inst = NDIlib_send_create(&desc);
    if (!inst) {
        LOGE("NDIlib_send_create failed for '%s'", name ? name : "(null)");
        return nullptr;
    }
    LOGI("NDI sender created: '%s'", name ? name : "(null)");
    return new NdiSender(inst);
}

NdiSender::~NdiSender() {
    std::lock_guard<std::mutex> lv(video_mtx_);
    std::lock_guard<std::mutex> la(audio_mtx_);
    if (inst_) {
        if (async_pending_) NDIlib_send_send_video_async_v2(inst_, nullptr);  // flush async buffer
        NDIlib_send_destroy(inst_);
        inst_ = nullptr;
        LOGI("NDI sender destroyed");
    }
}

int NdiSender::send_video(const Yuv420Planes& src, const Transform& t, int fps_n, int fps_d, int64_t timecode) {
    std::lock_guard<std::mutex> lock(video_mtx_);
    if (!inst_) return -1;
    int ow, oh;
    output_size(src.width, src.height, t.rotation, &ow, &oh);
    if (ow != cur_w_ || oh != cur_h_) {
        // Resolution change: make sure the SDK no longer references the old buffers.
        if (async_pending_) {
            NDIlib_send_send_video_async_v2(inst_, nullptr);
            async_pending_ = false;
        }
        const size_t sz = nv12_size(ow, oh);
        buffers_[0].assign(sz, 0);
        buffers_[1].assign(sz, 0);
        cur_w_ = ow;
        cur_h_ = oh;
        next_buffer_ = 0;
    }
    uint8_t* dst = buffers_[next_buffer_].data();
    if (!yuv420_to_nv12(src, t, dst, ow, oh)) return -2;

    NDIlib_video_frame_v2_t f;
    f.xres = ow;
    f.yres = oh;
    f.FourCC = NDIlib_FourCC_video_type_NV12;
    f.frame_rate_N = fps_n;
    f.frame_rate_D = fps_d;
    f.picture_aspect_ratio = static_cast<float>(ow) / static_cast<float>(oh);
    f.frame_format_type = NDIlib_frame_format_type_progressive;
    f.timecode = timecode != 0 ? timecode : NDIlib_send_timecode_synthesize;
    f.p_data = dst;
    f.line_stride_in_bytes = ow;
    f.p_metadata = nullptr;
    f.timestamp = 0;
    // Async: returns immediately; this buffer stays owned by the SDK until the next call.
    NDIlib_send_send_video_async_v2(inst_, &f);
    async_pending_ = true;
    next_buffer_ ^= 1;
    video_frames_.fetch_add(1);
    return 0;
}

int NdiSender::send_audio_interleaved(const float* interleaved, int frames, int channels, int sample_rate, int64_t timecode) {
    std::lock_guard<std::mutex> lock(audio_mtx_);
    if (!inst_) return -1;
    if (frames <= 0 || channels <= 0 || channels > 8) return -2;
    planar_.resize(static_cast<size_t>(frames) * channels);
    deinterleave(interleaved, frames, channels, planar_.data());

    NDIlib_audio_frame_v3_t a;
    a.sample_rate = sample_rate;
    a.no_channels = channels;
    a.no_samples = frames;
    a.timecode = timecode != 0 ? timecode : NDIlib_send_timecode_synthesize;
    a.FourCC = NDIlib_FourCC_audio_type_FLTP;
    a.p_data = reinterpret_cast<uint8_t*>(planar_.data());
    a.channel_stride_in_bytes = frames * static_cast<int>(sizeof(float));
    a.p_metadata = nullptr;
    a.timestamp = 0;
    NDIlib_send_send_audio_v3(inst_, &a);
    audio_frames_.fetch_add(1);
    return 0;
}

int NdiSender::connections(uint32_t timeout_ms) {
    if (!inst_) return -1;
    return NDIlib_send_get_no_connections(inst_, timeout_ms);
}

bool NdiSender::tally(bool* on_program, bool* on_preview) {
    if (!inst_) return false;
    NDIlib_tally_t t;
    t.on_program = false;
    t.on_preview = false;
    const bool changed = NDIlib_send_get_tally(inst_, &t, 0);
    *on_program = t.on_program;
    *on_preview = t.on_preview;
    return changed;
}

std::string NdiSender::source_name() {
    if (!inst_) return {};
    const NDIlib_source_t* s = NDIlib_send_get_source_name(inst_);
    return (s && s->p_ndi_name) ? std::string(s->p_ndi_name) : std::string();
}

}  // namespace ndicam
