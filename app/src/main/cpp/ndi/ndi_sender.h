#pragma once
#include <Processing.NDI.Lib.h>

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "camera/frame_converter.h"

namespace ndicam {

// Owns one NDIlib_send_instance_t. Video is sent asynchronously from a pair of alternating
// NV12 buffers, following the SDK rule that an async buffer stays in use until the next
// synchronising call. Audio is sent synchronously (the SDK copies it).
class NdiSender {
public:
    static NdiSender* create(const char* name, const char* groups, bool clock_video);
    ~NdiSender();

    // Returns 0 on success, negative on error.
    int send_video(const Yuv420Planes& src, const Transform& t, int fps_n, int fps_d, int64_t timecode);
    int send_audio_interleaved(const float* interleaved, int frames, int channels, int sample_rate, int64_t timecode);

    int connections(uint32_t timeout_ms);
    bool tally(bool* on_program, bool* on_preview);
    std::string source_name();

    uint64_t video_frames_sent() const { return video_frames_.load(); }
    uint64_t audio_frames_sent() const { return audio_frames_.load(); }

private:
    explicit NdiSender(NDIlib_send_instance_t inst) : inst_(inst) {}
    NDIlib_send_instance_t inst_;
    std::mutex video_mtx_;
    std::mutex audio_mtx_;
    std::vector<uint8_t> buffers_[2];
    int next_buffer_ = 0;
    int cur_w_ = 0, cur_h_ = 0;
    bool async_pending_ = false;
    std::vector<float> planar_;
    std::atomic<uint64_t> video_frames_{0};
    std::atomic<uint64_t> audio_frames_{0};
};

}  // namespace ndicam
