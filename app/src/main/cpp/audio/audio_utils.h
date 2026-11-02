#pragma once
#include <cstdint>

namespace ndicam {

struct AudioLevels {
    float peak[8];  // linear 0..1+ per channel
    float rms[8];   // linear per channel
};

// Applies gain (or silence when muted) in place to interleaved float samples and measures
// per-channel peak/RMS after gain. channels is clamped to 8.
void process_interleaved(float* samples, int frames, int channels, float gain, bool mute, AudioLevels* levels);

// Converts interleaved float samples to planar (FLTP) layout: dst[c * frames + i].
void deinterleave(const float* src, int frames, int channels, float* dst);

}  // namespace ndicam
