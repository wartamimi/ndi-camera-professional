#pragma once
#include <cstdint>

namespace ndicam {

struct AnalyzerConfig {
    bool zebra;
    int zebra_threshold;   // luma code value 0..255 at/above which zebra stripes are drawn
    bool peaking;
    int peaking_threshold; // gradient magnitude threshold 0..255
    uint32_t peaking_color;  // ARGB
    int stripe_phase;      // animates zebra stripes
};

struct AnalyzerResult {
    uint32_t histogram[256];
    float mean_luma;        // 0..255
    float clipped_fraction; // fraction of samples >= 250
    float crushed_fraction; // fraction of samples <= 5
};

// Analyzes a luma plane and renders zebra/peaking into an ARGB_8888 mask (out may be null).
// The mask is (out_w x out_h) and covers the whole source frame.
void analyze_luma(const uint8_t* y, int y_stride, int w, int h, const AnalyzerConfig& cfg,
                  uint32_t* out, int out_w, int out_h, int out_stride_px, AnalyzerResult* result);

}  // namespace ndicam
