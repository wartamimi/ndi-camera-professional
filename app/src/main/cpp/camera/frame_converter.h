#pragma once
#include <cstddef>
#include <cstdint>

namespace ndicam {

// Description of an Android YUV_420_888 image as delivered by ImageReader.
// The luma plane always has pixel stride 1. Chroma planes may be planar (pixel stride 1)
// or semi-planar (pixel stride 2, interleaved, NV12- or NV21-like in memory).
struct Yuv420Planes {
    const uint8_t* y;
    const uint8_t* u;
    const uint8_t* v;
    int y_row_stride;
    int uv_row_stride;
    int uv_pixel_stride;
    int width;   // source width in pixels (even)
    int height;  // source height in pixels (even)
};

// Rotation applied clockwise in degrees: 0, 90, 180, 270.
// Mirror flips the OUTPUT horizontally (used for front cameras).
struct Transform {
    int rotation;
    bool mirror;
};

// Output dimensions for a given source and transform.
void output_size(int src_w, int src_h, int rotation, int* out_w, int* out_h);

// Number of bytes for an NV12 image with stride == width.
inline size_t nv12_size(int w, int h) { return static_cast<size_t>(w) * h * 3 / 2; }

// Converts a YUV_420_888 image into a tightly packed NV12 buffer (stride == out width).
// Returns false on invalid arguments.
bool yuv420_to_nv12(const Yuv420Planes& src, const Transform& t, uint8_t* dst, int dst_w, int dst_h);

}  // namespace ndicam
