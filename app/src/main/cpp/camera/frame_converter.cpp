#include "camera/frame_converter.h"

#include <cstring>

namespace ndicam {

void output_size(int src_w, int src_h, int rotation, int* out_w, int* out_h) {
    if (rotation == 90 || rotation == 270) {
        *out_w = src_h;
        *out_h = src_w;
    } else {
        *out_w = src_w;
        *out_h = src_h;
    }
}

namespace {

// Maps an output coordinate (x, y) to source coordinates for a plane of size (sw, sh).
inline void map_coord(int x, int y, int ow, int sw, int sh, const Transform& t, int* sx, int* sy) {
    if (t.mirror) x = ow - 1 - x;
    switch (t.rotation) {
        case 90:  *sx = y;          *sy = sh - 1 - x; break;
        case 180: *sx = sw - 1 - x; *sy = sh - 1 - y; break;
        case 270: *sx = sw - 1 - y; *sy = x;          break;
        default:  *sx = x;          *sy = y;          break;
    }
}

void copy_identity(const Yuv420Planes& s, uint8_t* dst) {
    const int w = s.width, h = s.height;
    uint8_t* dy = dst;
    for (int row = 0; row < h; ++row) {
        std::memcpy(dy + static_cast<size_t>(row) * w, s.y + static_cast<size_t>(row) * s.y_row_stride, w);
    }
    uint8_t* duv = dst + static_cast<size_t>(w) * h;
    const int cw = w / 2, ch = h / 2;
    const bool nv12_layout = s.uv_pixel_stride == 2 && s.v == s.u + 1;
    if (nv12_layout) {
        // Chroma is already interleaved U,V in memory: one memcpy per row.
        for (int row = 0; row < ch; ++row) {
            const uint8_t* src_row = s.u + static_cast<size_t>(row) * s.uv_row_stride;
            uint8_t* dst_row = duv + static_cast<size_t>(row) * w;
            if (row < ch - 1) {
                std::memcpy(dst_row, src_row, w);
            } else {
                // Last row: the U buffer is one byte shorter; copy U/V separately to stay in bounds.
                std::memcpy(dst_row, src_row, w - 1);
                dst_row[w - 1] = s.v[static_cast<size_t>(row) * s.uv_row_stride + (cw - 1) * 2];
            }
        }
        return;
    }
    const int ps = s.uv_pixel_stride;
    for (int row = 0; row < ch; ++row) {
        const uint8_t* ur = s.u + static_cast<size_t>(row) * s.uv_row_stride;
        const uint8_t* vr = s.v + static_cast<size_t>(row) * s.uv_row_stride;
        uint8_t* d = duv + static_cast<size_t>(row) * w;
        for (int x = 0; x < cw; ++x) {
            d[2 * x] = ur[x * ps];
            d[2 * x + 1] = vr[x * ps];
        }
    }
}

// Rotates one 8-bit plane (element size 1 or interleaved pairs of 2) by 90/270 without mirroring,
// in tiles. Source pixel (sx, sy) of an sw x sh plane goes to:
//   90:  out(sh-1-sy, sx)      270: out(sy, sw-1-sx)
template <int kElem>
void rotate_plane(const uint8_t* src, int src_stride, int src_pixel_stride, const uint8_t* src2,
                  int sw, int sh, uint8_t* out, int out_stride, int rotation) {
    constexpr int T = 32;
    for (int ty = 0; ty < sh; ty += T) {
        const int ey = ty + T < sh ? ty + T : sh;
        for (int tx = 0; tx < sw; tx += T) {
            const int ex = tx + T < sw ? tx + T : sw;
            for (int sy = ty; sy < ey; ++sy) {
                const uint8_t* r1 = src + static_cast<size_t>(sy) * src_stride;
                const uint8_t* r2 = kElem == 2 ? src2 + static_cast<size_t>(sy) * src_stride : nullptr;
                for (int sx = tx; sx < ex; ++sx) {
                    int ox, oy;
                    if (rotation == 90) { ox = sh - 1 - sy; oy = sx; } else { ox = sy; oy = sw - 1 - sx; }
                    uint8_t* d = out + static_cast<size_t>(oy) * out_stride + static_cast<size_t>(ox) * kElem;
                    d[0] = r1[static_cast<size_t>(sx) * src_pixel_stride];
                    if (kElem == 2) d[1] = r2[static_cast<size_t>(sx) * src_pixel_stride];
                }
            }
        }
    }
}

void copy_transformed(const Yuv420Planes& s, const Transform& t, uint8_t* dst, int ow, int oh) {
    if (!t.mirror && (t.rotation == 90 || t.rotation == 270)) {
        rotate_plane<1>(s.y, s.y_row_stride, 1, nullptr, s.width, s.height, dst, ow, t.rotation);
        rotate_plane<2>(s.u, s.uv_row_stride, s.uv_pixel_stride, s.v, s.width / 2, s.height / 2,
                        dst + static_cast<size_t>(ow) * oh, ow, t.rotation);
        return;
    }
    const int sw = s.width, sh = s.height;
    int sx, sy;
    // Luma in 32x32 tiles: rotations by 90/270 read source columns, and tiling keeps both the
    // source and destination working sets inside the L1 cache.
    constexpr int T = 32;
    for (int ty = 0; ty < oh; ty += T) {
        const int ey = ty + T < oh ? ty + T : oh;
        for (int tx = 0; tx < ow; tx += T) {
            const int ex = tx + T < ow ? tx + T : ow;
            for (int y = ty; y < ey; ++y) {
                uint8_t* d = dst + static_cast<size_t>(y) * ow;
                for (int x = tx; x < ex; ++x) {
                    map_coord(x, y, ow, sw, sh, t, &sx, &sy);
                    d[x] = s.y[static_cast<size_t>(sy) * s.y_row_stride + sx];
                }
            }
        }
    }
    uint8_t* duv = dst + static_cast<size_t>(ow) * oh;
    const int ocw = ow / 2, och = oh / 2, scw = sw / 2, sch = sh / 2;
    const int ps = s.uv_pixel_stride;
    for (int y = 0; y < och; ++y) {
        uint8_t* d = duv + static_cast<size_t>(y) * ow;
        for (int x = 0; x < ocw; ++x) {
            map_coord(x, y, ocw, scw, sch, t, &sx, &sy);
            const size_t off = static_cast<size_t>(sy) * s.uv_row_stride + static_cast<size_t>(sx) * ps;
            d[2 * x] = s.u[off];
            d[2 * x + 1] = s.v[off];
        }
    }
}

}  // namespace

bool yuv420_to_nv12(const Yuv420Planes& s, const Transform& t, uint8_t* dst, int dst_w, int dst_h) {
    if (!s.y || !s.u || !s.v || !dst) return false;
    if (s.width <= 0 || s.height <= 0 || (s.width & 1) || (s.height & 1)) return false;
    if (s.uv_pixel_stride != 1 && s.uv_pixel_stride != 2) return false;
    int ow, oh;
    output_size(s.width, s.height, t.rotation, &ow, &oh);
    if (ow != dst_w || oh != dst_h) return false;
    if (t.rotation == 0 && !t.mirror) {
        copy_identity(s, dst);
    } else {
        copy_transformed(s, t, dst, ow, oh);
    }
    return true;
}

}  // namespace ndicam
