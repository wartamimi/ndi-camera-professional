#pragma once
#include <cstdint>

namespace ndicam {

// Burns a "LCS CAM · FREE" style label into the bottom-right corner of a tightly packed
// NV12 image (stride == width). Used for the free plan; text uses a built-in 5x7 font.
void burn_watermark_nv12(uint8_t* nv12, int width, int height, const char* text);

}  // namespace ndicam
