#include "camera/watermark.h"

#include <cstring>

namespace ndicam {
namespace {

// 5x7 glyphs, one byte per row, low 5 bits used (bit 4 = leftmost column).
struct Glyph { char c; uint8_t rows[7]; };
constexpr Glyph kFont[] = {
    {'A', {0x0E, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11}},
    {'B', {0x1E, 0x11, 0x11, 0x1E, 0x11, 0x11, 0x1E}},
    {'C', {0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E}},
    {'D', {0x1E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x1E}},
    {'E', {0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x1F}},
    {'F', {0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x10}},
    {'G', {0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0F}},
    {'H', {0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11}},
    {'I', {0x0E, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0E}},
    {'K', {0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11}},
    {'L', {0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F}},
    {'M', {0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11}},
    {'N', {0x11, 0x19, 0x15, 0x13, 0x11, 0x11, 0x11}},
    {'O', {0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E}},
    {'P', {0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10}},
    {'R', {0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11}},
    {'S', {0x0F, 0x10, 0x10, 0x0E, 0x01, 0x01, 0x1E}},
    {'T', {0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04}},
    {'U', {0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E}},
    {'V', {0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04}},
    {'W', {0x11, 0x11, 0x11, 0x15, 0x15, 0x15, 0x0A}},
    {'Y', {0x11, 0x11, 0x0A, 0x04, 0x04, 0x04, 0x04}},
    {'-', {0x00, 0x00, 0x00, 0x1F, 0x00, 0x00, 0x00}},
    {'.', {0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C}},
    {' ', {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}},
};

const uint8_t* glyph(char c) {
    if (c >= 'a' && c <= 'z') c = static_cast<char>(c - 'a' + 'A');
    for (const auto& g : kFont) if (g.c == c) return g.rows;
    return kFont[sizeof(kFont) / sizeof(kFont[0]) - 1].rows;  // unknown -> space
}

}  // namespace

void burn_watermark_nv12(uint8_t* nv12, int w, int h, const char* text) {
    if (!nv12 || !text || w < 64 || h < 64) return;
    const int len = static_cast<int>(std::strlen(text));
    if (len == 0) return;
    int scale = (w < h ? w : h) / 180;
    if (scale < 2) scale = 2;
    const int pad = scale * 3;
    const int text_w = len * 6 * scale - scale;
    const int text_h = 7 * scale;
    int box_w = text_w + 2 * pad;
    int box_h = text_h + 2 * pad;
    if (box_w > w - 8) box_w = w - 8;
    const int margin = scale * 4;
    int x0 = (w - box_w - margin) & ~1;
    int y0 = (h - box_h - margin) & ~1;
    if (x0 < 0) x0 = 0;
    if (y0 < 0) y0 = 0;
    const int x1 = (x0 + box_w) < w ? x0 + box_w : w;
    const int y1 = (y0 + box_h) < h ? y0 + box_h : h;

    // Darken the box (luma) and neutralise its chroma.
    for (int y = y0; y < y1; ++y) {
        uint8_t* row = nv12 + static_cast<size_t>(y) * w;
        for (int x = x0; x < x1; ++x) row[x] = static_cast<uint8_t>(16 + (row[x] - 16 > 0 ? (row[x] - 16) / 4 : 0));
    }
    uint8_t* uv = nv12 + static_cast<size_t>(w) * h;
    for (int y = y0 / 2; y < y1 / 2; ++y) {
        uint8_t* row = uv + static_cast<size_t>(y) * w;
        for (int x = x0 & ~1; x < x1; ++x) row[x] = 128;
    }
    // Text in near-white luma.
    const int tx = x0 + pad;
    const int ty = y0 + pad;
    for (int i = 0; i < len; ++i) {
        const uint8_t* g = glyph(text[i]);
        const int gx = tx + i * 6 * scale;
        for (int r = 0; r < 7; ++r) {
            for (int c = 0; c < 5; ++c) {
                if (!(g[r] & (0x10 >> c))) continue;
                for (int sy = 0; sy < scale; ++sy) {
                    const int py = ty + r * scale + sy;
                    if (py >= y1) break;
                    uint8_t* row = nv12 + static_cast<size_t>(py) * w;
                    for (int sx = 0; sx < scale; ++sx) {
                        const int px = gx + c * scale + sx;
                        if (px < x1) row[px] = 235;
                    }
                }
            }
        }
    }
}

}  // namespace ndicam
