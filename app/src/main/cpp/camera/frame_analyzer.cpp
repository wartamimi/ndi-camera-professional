#include "camera/frame_analyzer.h"

#include <cstdlib>
#include <cstring>

namespace ndicam {

void analyze_luma(const uint8_t* y, int y_stride, int w, int h, const AnalyzerConfig& cfg,
                  uint32_t* out, int out_w, int out_h, int out_stride_px, AnalyzerResult* result) {
    AnalyzerResult r;
    std::memset(&r, 0, sizeof(r));
    if (!y || w < 4 || h < 4 || out_w <= 0 || out_h <= 0) {
        if (result) *result = r;
        return;
    }
    uint64_t sum = 0;
    uint32_t clipped = 0, crushed = 0, samples = 0;
    const uint32_t zebra_color = 0xC0C0C0C0u;   // translucent white (premultiplied)
    const uint32_t zebra_dark = 0x80000000u;
    for (int oy = 0; oy < out_h; ++oy) {
        int sy = static_cast<int>((static_cast<int64_t>(oy) * h) / out_h);
        if (sy < 1) sy = 1;
        if (sy > h - 2) sy = h - 2;
        const uint8_t* row = y + static_cast<size_t>(sy) * y_stride;
        const uint8_t* up = row - y_stride;
        const uint8_t* dn = row + y_stride;
        uint32_t* o = out ? out + static_cast<size_t>(oy) * out_stride_px : nullptr;
        for (int ox = 0; ox < out_w; ++ox) {
            int sx = static_cast<int>((static_cast<int64_t>(ox) * w) / out_w);
            if (sx < 1) sx = 1;
            if (sx > w - 2) sx = w - 2;
            const int l = row[sx];
            r.histogram[l]++;
            sum += l;
            samples++;
            if (l >= 250) clipped++;
            if (l <= 5) crushed++;
            if (!o) continue;
            uint32_t px = 0;
            if (cfg.peaking) {
                const int gx = std::abs(static_cast<int>(row[sx + 1]) - static_cast<int>(row[sx - 1]));
                const int gy = std::abs(static_cast<int>(dn[sx]) - static_cast<int>(up[sx]));
                if (gx + gy >= cfg.peaking_threshold) px = cfg.peaking_color;
            }
            if (cfg.zebra && l >= cfg.zebra_threshold) {
                px = (((ox + oy + cfg.stripe_phase) >> 2) & 1) ? zebra_color : zebra_dark;
            }
            o[ox] = px;
        }
    }
    if (samples) {
        r.mean_luma = static_cast<float>(sum) / static_cast<float>(samples);
        r.clipped_fraction = static_cast<float>(clipped) / static_cast<float>(samples);
        r.crushed_fraction = static_cast<float>(crushed) / static_cast<float>(samples);
    }
    if (result) *result = r;
}

}  // namespace ndicam
