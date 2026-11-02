#include "audio/audio_utils.h"

#include <cmath>
#include <cstring>

namespace ndicam {

void process_interleaved(float* s, int frames, int channels, float gain, bool mute, AudioLevels* lv) {
    if (channels > 8) channels = 8;
    double sq[8] = {0};
    float pk[8] = {0};
    const int n = frames * channels;
    if (mute) {
        std::memset(s, 0, sizeof(float) * static_cast<size_t>(n));
    } else {
        for (int i = 0; i < n; ++i) {
            const float v = s[i] * gain;
            s[i] = v;
            const int c = i % channels;
            const float a = std::fabs(v);
            if (a > pk[c]) pk[c] = a;
            sq[c] += static_cast<double>(v) * v;
        }
    }
    if (lv) {
        for (int c = 0; c < 8; ++c) {
            lv->peak[c] = c < channels ? pk[c] : 0.f;
            lv->rms[c] = (c < channels && frames > 0) ? static_cast<float>(std::sqrt(sq[c] / frames)) : 0.f;
        }
    }
}

void deinterleave(const float* src, int frames, int channels, float* dst) {
    for (int c = 0; c < channels; ++c) {
        float* d = dst + static_cast<size_t>(c) * frames;
        const float* p = src + c;
        for (int i = 0; i < frames; ++i) d[i] = p[static_cast<size_t>(i) * channels];
    }
}

}  // namespace ndicam
