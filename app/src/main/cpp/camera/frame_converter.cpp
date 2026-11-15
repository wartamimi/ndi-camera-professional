#include "camera/frame_converter.h"

#include <cstring>

// YUV_420_888 -> NV12 with rotation/mirroring.
//
// Every rotation/mirror combination is one of eight pixel mappings. They are split into two
// families and each family has a block-based fast path that works on 8-byte words:
//
//  * Straight (rotation 0/180): out(x, y) = src(flipH ? w-1-x : x, flipV ? h-1-y : y).
//    Rows are copied with memcpy, or reversed 8 bytes at a time.
//  * Transposed (rotation 90/270): out(x, y) = src(flipY ? sw-1-y : y, flipX ? sh-1-x : x).
//    The plane is processed in 8x8 byte tiles (luma) or 4x4 tiles of U/V byte pairs (chroma),
//    loaded as eight/four 64-bit words and transposed with SWAR shifts in registers.
//
// This replaces a per-pixel loop that recomputed the coordinate mapping for every pixel, which
// was the dominant CPU cost of the video path whenever the output was rotated (portrait output,
// landscape-left) or mirrored (front camera).
//
// Only plain C++ is used (no NEON intrinsics), so the exact same code is verified bit-for-bit
// against a reference implementation by the host unit tests. All Android ABIs are little endian.

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

inline uint64_t load64(const uint8_t* p) { uint64_t v; std::memcpy(&v, p, 8); return v; }
inline void store64(uint8_t* p, uint64_t v) { std::memcpy(p, &v, 8); }

// Swaps the two bytes of every 16-bit lane (V,U pairs -> U,V pairs).
inline uint64_t swap_pairs(uint64_t v) {
    return ((v & 0x00FF00FF00FF00FFull) << 8) | ((v >> 8) & 0x00FF00FF00FF00FFull);
}

// Reverses the order of the 8 bytes.
inline uint64_t reverse_bytes(uint64_t v) {
#if defined(__GNUC__) || defined(__clang__)
    return __builtin_bswap64(v);
#else
    v = ((v & 0x00FF00FF00FF00FFull) << 8) | ((v >> 8) & 0x00FF00FF00FF00FFull);
    v = ((v & 0x0000FFFF0000FFFFull) << 16) | ((v >> 16) & 0x0000FFFF0000FFFFull);
    return (v << 32) | (v >> 32);
#endif
}

// Reverses the order of the four 16-bit lanes.
inline uint64_t reverse_pairs(uint64_t v) {
    v = (v << 32) | (v >> 32);
    return ((v & 0x0000FFFF0000FFFFull) << 16) | ((v >> 16) & 0x0000FFFF0000FFFFull);
}

// In-register transpose: after the call, byte b of r[a] is the former byte a of r[b].
inline void transpose8x8(uint64_t r[8]) {
    for (int i = 0; i < 8; i += 2) {
        const uint64_t t = ((r[i] >> 8) ^ r[i + 1]) & 0x00FF00FF00FF00FFull;
        r[i + 1] ^= t; r[i] ^= t << 8;
    }
    for (int k = 0; k < 4; ++k) {
        const int i = k + (k & 2);  // rows 0,1,4,5 pair with 2,3,6,7
        const uint64_t t = ((r[i] >> 16) ^ r[i + 2]) & 0x0000FFFF0000FFFFull;
        r[i + 2] ^= t; r[i] ^= t << 16;
    }
    for (int i = 0; i < 4; ++i) {
        const uint64_t t = ((r[i] >> 32) ^ r[i + 4]) & 0x00000000FFFFFFFFull;
        r[i + 4] ^= t; r[i] ^= t << 32;
    }
}

// Same for a 4x4 matrix of 16-bit lanes.
inline void transpose4x4_16(uint64_t r[4]) {
    for (int i = 0; i < 4; i += 2) {
        const uint64_t t = ((r[i] >> 16) ^ r[i + 1]) & 0x0000FFFF0000FFFFull;
        r[i + 1] ^= t; r[i] ^= t << 16;
    }
    for (int i = 0; i < 2; ++i) {
        const uint64_t t = ((r[i] >> 32) ^ r[i + 2]) & 0x00000000FFFFFFFFull;
        r[i + 2] ^= t; r[i] ^= t << 32;
    }
}

// A plane of elements of kBytes bytes (1 = luma, 2 = interleaved chroma pair). For chroma,
// `base` points at whichever of U/V comes first in memory and `swap` says it is V.
template <int kBytes>
struct Plane {
    const uint8_t* base;
    int stride;
    int w, h;       // in elements
    bool swap;      // kBytes == 2 only: memory order is V,U
};

template <int kBytes>
inline void put_elem(uint8_t* d, const uint8_t* s, bool swap) {
    if (kBytes == 1) {
        d[0] = s[0];
    } else if (swap) {
        d[0] = s[1]; d[1] = s[0];
    } else {
        d[0] = s[0]; d[1] = s[1];
    }
}

// Straight family: rotation 0/180 with any mirror.
template <int kBytes>
void convert_straight(const Plane<kBytes>& p, bool flip_h, bool flip_v, uint8_t* dst, int dst_stride) {
    constexpr int kPerWord = 8 / kBytes;
    const int row_bytes = p.w * kBytes;
    for (int y = 0; y < p.h; ++y) {
        const int sy = flip_v ? p.h - 1 - y : y;
        const uint8_t* s = p.base + static_cast<size_t>(sy) * p.stride;
        uint8_t* d = dst + static_cast<size_t>(y) * dst_stride;
        if (!flip_h) {
            if (kBytes == 1 || !p.swap) {
                // NV12 chroma: the last V byte lies one past the U plane's own end but inside the
                // V plane, which shares the same memory; that is why one memcpy per row is safe.
                std::memcpy(d, s, static_cast<size_t>(row_bytes));
            } else {
                int x = 0;
                for (; x + kPerWord <= p.w; x += kPerWord) store64(d + x * kBytes, swap_pairs(load64(s + x * kBytes)));
                for (; x < p.w; ++x) put_elem<kBytes>(d + x * kBytes, s + x * kBytes, true);
            }
            continue;
        }
        // Reversed row: output element x comes from source element w-1-x.
        int x = 0;
        for (; x + kPerWord <= p.w; x += kPerWord) {
            uint64_t v = load64(s + static_cast<size_t>(p.w - x - kPerWord) * kBytes);
            v = kBytes == 1 ? reverse_bytes(v) : reverse_pairs(v);
            if (kBytes == 2 && p.swap) v = swap_pairs(v);
            store64(d + x * kBytes, v);
        }
        for (; x < p.w; ++x) put_elem<kBytes>(d + x * kBytes, s + static_cast<size_t>(p.w - 1 - x) * kBytes, p.swap);
    }
}

// Transposed family: rotation 90/270 with any mirror. Output is p.h wide and p.w tall.
//   out(x, y) = src(flip_y ? p.w-1-y : y, flip_x ? p.h-1-x : x)
template <int kBytes>
void convert_transposed(const Plane<kBytes>& p, bool flip_x, bool flip_y, uint8_t* dst, int dst_stride) {
    constexpr int B = 8 / kBytes;  // tile edge in elements; one tile row = one 64-bit word
    const int ow = p.h, oh = p.w;
    const int full_w = ow - ow % B, full_h = oh - oh % B;
    // Super-tiles keep the source rows touched by consecutive tiles resident in L1.
    constexpr int S = 64;
    for (int sy0 = 0; sy0 < full_h; sy0 += S) {
        const int sy1 = sy0 + S < full_h ? sy0 + S : full_h;
        for (int sx0 = 0; sx0 < full_w; sx0 += S) {
            const int sx1 = sx0 + S < full_w ? sx0 + S : full_w;
            for (int by = sy0; by < sy1; by += B) {
                const int c0 = flip_y ? p.w - B - by : by;  // first source column (elements)
                for (int bx = sx0; bx < sx1; bx += B) {
                    const int r0 = flip_x ? p.h - B - bx : bx;  // first source row
                    uint64_t r[8];
                    const uint8_t* s = p.base + static_cast<size_t>(r0) * p.stride + static_cast<size_t>(c0) * kBytes;
                    for (int i = 0; i < B; ++i) {
                        // Tile row b of the output must hold source row (flip_x ? B-1-b : b).
                        r[flip_x ? B - 1 - i : i] = load64(s + static_cast<size_t>(i) * p.stride);
                    }
                    if (kBytes == 1) transpose8x8(r); else transpose4x4_16(r);
                    uint8_t* d = dst + static_cast<size_t>(by) * dst_stride + static_cast<size_t>(bx) * kBytes;
                    for (int a = 0; a < B; ++a) {
                        uint64_t v = r[flip_y ? B - 1 - a : a];
                        if (kBytes == 2 && p.swap) v = swap_pairs(v);
                        store64(d + static_cast<size_t>(a) * dst_stride, v);
                    }
                }
            }
        }
    }
    // Ragged right column and bottom row of tiles (sizes that are not multiples of the tile).
    auto edge = [&](int x0, int x1, int y0, int y1) {
        for (int y = y0; y < y1; ++y) {
            const int sx = flip_y ? p.w - 1 - y : y;
            uint8_t* d = dst + static_cast<size_t>(y) * dst_stride;
            for (int x = x0; x < x1; ++x) {
                const int sy = flip_x ? p.h - 1 - x : x;
                put_elem<kBytes>(d + static_cast<size_t>(x) * kBytes,
                                 p.base + static_cast<size_t>(sy) * p.stride + static_cast<size_t>(sx) * kBytes, p.swap);
            }
        }
    };
    if (full_w < ow) edge(full_w, ow, 0, oh);
    if (full_h < oh) edge(0, full_w, full_h, oh);
}

// Fallback for planar chroma (pixel stride 1) or unusual layouts: per-pixel mapping.
void chroma_generic(const Yuv420Planes& s, bool transposed, bool fa, bool fb, uint8_t* duv, int ow) {
    const int scw = s.width / 2, sch = s.height / 2;
    const int ocw = transposed ? sch : scw, och = transposed ? scw : sch;
    const int ps = s.uv_pixel_stride;
    for (int y = 0; y < och; ++y) {
        uint8_t* d = duv + static_cast<size_t>(y) * ow;
        for (int x = 0; x < ocw; ++x) {
            int sx, sy;
            if (transposed) { sx = fb ? scw - 1 - y : y; sy = fa ? sch - 1 - x : x; }
            else            { sx = fa ? scw - 1 - x : x; sy = fb ? sch - 1 - y : y; }
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
    if (t.rotation != 0 && t.rotation != 90 && t.rotation != 180 && t.rotation != 270) return false;
    int ow, oh;
    output_size(s.width, s.height, t.rotation, &ow, &oh);
    if (ow != dst_w || oh != dst_h) return false;

    // Map (rotation, mirror) to the two families. See the header comment for the derivation:
    // mirror flips the output x before the rotation is applied.
    const bool transposed = t.rotation == 90 || t.rotation == 270;
    bool fa, fb;  // straight: flip_h, flip_v   transposed: flip_x, flip_y
    switch (t.rotation) {
        case 0:   fa = t.mirror;  fb = false;     break;
        case 180: fa = !t.mirror; fb = true;      break;
        case 90:  fa = !t.mirror; fb = false;     break;
        default:  fa = t.mirror;  fb = true;      break;  // 270
    }

    const Plane<1> luma{s.y, s.y_row_stride, s.width, s.height, false};
    if (transposed) convert_transposed<1>(luma, fa, fb, dst, ow);
    else convert_straight<1>(luma, fa, fb, dst, ow);

    uint8_t* duv = dst + static_cast<size_t>(ow) * oh;
    const bool nv12 = s.uv_pixel_stride == 2 && s.v == s.u + 1;
    const bool nv21 = s.uv_pixel_stride == 2 && s.u == s.v + 1;
    if (nv12 || nv21) {
        const Plane<2> chroma{nv12 ? s.u : s.v, s.uv_row_stride, s.width / 2, s.height / 2, nv21};
        if (transposed) convert_transposed<2>(chroma, fa, fb, duv, ow);
        else convert_straight<2>(chroma, fa, fb, duv, ow);
    } else {
        chroma_generic(s, transposed, fa, fb, duv, ow);
    }
    return true;
}

}  // namespace ndicam
