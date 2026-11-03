// Host-side unit tests for the NDI-independent native media core.
// Build: see native-tests/CMakeLists.txt (run in CI with the host compiler).
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>

#include "audio/audio_utils.h"
#include "camera/frame_analyzer.h"
#include "camera/frame_converter.h"
#include "camera/watermark.h"
#include <chrono>

using namespace ndicam;

static int failures = 0;
#define CHECK(cond) do { if (!(cond)) { std::printf("FAIL %s:%d %s\n", __FILE__, __LINE__, #cond); ++failures; } } while (0)

// Builds a synthetic W x H YUV_420_888 image where Y = x + 10*y, U = 100 + cx, V = 200 + cy.
struct Img {
    int w, h, ys, uvs, ps;
    std::vector<uint8_t> y, uv;  // uv holds either interleaved (ps=2) or U then V planes
    const uint8_t *u, *v;
};

static Img make(int w, int h, int ps, bool nv21 = false) {
    Img im{w, h, w + 8, 0, ps, {}, {}, nullptr, nullptr};
    im.y.resize(static_cast<size_t>(im.ys) * h);
    for (int r = 0; r < h; ++r) for (int c = 0; c < w; ++c) im.y[r * im.ys + c] = static_cast<uint8_t>(c + 10 * r);
    const int cw = w / 2, ch = h / 2;
    if (ps == 2) {
        im.uvs = w + 8;
        im.uv.resize(static_cast<size_t>(im.uvs) * ch + 1);
        for (int r = 0; r < ch; ++r) for (int c = 0; c < cw; ++c) {
            im.uv[r * im.uvs + 2 * c + (nv21 ? 1 : 0)] = static_cast<uint8_t>(100 + c);
            im.uv[r * im.uvs + 2 * c + (nv21 ? 0 : 1)] = static_cast<uint8_t>(200 + r);
        }
        im.u = im.uv.data() + (nv21 ? 1 : 0);
        im.v = im.uv.data() + (nv21 ? 0 : 1);
    } else {
        im.uvs = cw + 4;
        im.uv.resize(static_cast<size_t>(im.uvs) * ch * 2);
        for (int r = 0; r < ch; ++r) for (int c = 0; c < cw; ++c) {
            im.uv[r * im.uvs + c] = static_cast<uint8_t>(100 + c);
            im.uv[im.uvs * ch + r * im.uvs + c] = static_cast<uint8_t>(200 + r);
        }
        im.u = im.uv.data();
        im.v = im.uv.data() + im.uvs * ch;
    }
    return im;
}

static Yuv420Planes planes(const Img& im) {
    return Yuv420Planes{im.y.data(), im.u, im.v, im.ys, im.uvs, im.ps, im.w, im.h};
}

static void test_identity(int ps, bool nv21) {
    Img im = make(8, 4, ps, nv21);
    std::vector<uint8_t> out(nv12_size(8, 4));
    CHECK(yuv420_to_nv12(planes(im), Transform{0, false}, out.data(), 8, 4));
    for (int r = 0; r < 4; ++r) for (int c = 0; c < 8; ++c) CHECK(out[r * 8 + c] == c + 10 * r);
    const uint8_t* uv = out.data() + 32;
    for (int r = 0; r < 2; ++r) for (int c = 0; c < 4; ++c) {
        CHECK(uv[r * 8 + 2 * c] == 100 + c);
        CHECK(uv[r * 8 + 2 * c + 1] == 200 + r);
    }
}

static void test_rotations() {
    Img im = make(8, 4, 2);
    std::vector<uint8_t> out(nv12_size(8, 4));
    // 180: out(x,y) = src(W-1-x, H-1-y)
    CHECK(yuv420_to_nv12(planes(im), Transform{180, false}, out.data(), 8, 4));
    CHECK(out[0] == 7 + 30);
    CHECK(out[3 * 8 + 7] == 0);
    // 90 clockwise: output is 4 x 8; out(x,y) = src(y, H-1-x)
    CHECK(yuv420_to_nv12(planes(im), Transform{90, false}, out.data(), 4, 8));
    CHECK(out[0] == 0 + 10 * 3);          // top-left comes from bottom-left of source
    CHECK(out[0 * 4 + 3] == 0);           // top-right comes from top-left of source
    CHECK(out[7 * 4 + 3] == 7);           // bottom-right comes from top-right
    // 270: out(x,y) = src(W-1-y, x)
    CHECK(yuv420_to_nv12(planes(im), Transform{270, false}, out.data(), 4, 8));
    CHECK(out[0] == 7);                   // top-left comes from top-right of source
    // mirror: out(x,y) = src(W-1-x, y)
    CHECK(yuv420_to_nv12(planes(im), Transform{0, true}, out.data(), 8, 4));
    CHECK(out[0] == 7);
    CHECK(out[8 + 7] == 10);
    // size mismatch rejected
    CHECK(!yuv420_to_nv12(planes(im), Transform{90, false}, out.data(), 8, 4));
}

static void test_analyzer() {
    const int w = 64, h = 32;
    std::vector<uint8_t> y(w * h, 128);
    for (int i = 0; i < w * h / 2; ++i) y[i] = 255;  // top half clipped
    std::vector<uint32_t> mask(32 * 16, 0);
    AnalyzerConfig cfg{true, 240, false, 40, 0xFFFF0000u, 0};
    AnalyzerResult r;
    analyze_luma(y.data(), w, w, h, cfg, mask.data(), 32, 16, 32, &r);
    uint32_t total = 0;
    for (int i = 0; i < 256; ++i) total += r.histogram[i];
    CHECK(total == 32u * 16u);
    CHECK(r.histogram[255] > 0 && r.histogram[128] > 0);
    CHECK(r.clipped_fraction > 0.3f && r.clipped_fraction < 0.7f);
    CHECK(mask[0] != 0);               // zebra drawn in clipped area
    CHECK(mask[15 * 32 + 5] == 0);     // nothing in mid-grey area
}

static void test_audio() {
    std::vector<float> s = {0.5f, -0.25f, -1.0f, 0.25f};  // 2 frames, stereo
    AudioLevels lv;
    process_interleaved(s.data(), 2, 2, 0.5f, false, &lv);
    CHECK(std::fabs(lv.peak[0] - 0.5f) < 1e-6f);
    CHECK(std::fabs(lv.peak[1] - 0.125f) < 1e-6f);
    std::vector<float> planar(4);
    deinterleave(s.data(), 2, 2, planar.data());
    CHECK(planar[0] == 0.25f && planar[1] == -0.5f && planar[2] == -0.125f && planar[3] == 0.125f);
    process_interleaved(s.data(), 2, 2, 1.0f, true, &lv);
    CHECK(s[0] == 0.f && lv.peak[0] == 0.f);
}

static void test_watermark() {
    const int w = 640, h = 360;
    std::vector<uint8_t> img(nv12_size(w, h), 128);
    burn_watermark_nv12(img.data(), w, h, "LCS CAM - FREE");
    int white = 0, dark = 0;
    for (int i = 0; i < w * h; ++i) { if (img[i] == 235) ++white; if (img[i] < 60) ++dark; }
    CHECK(white > 100);
    CHECK(dark > 100);
    CHECK(img[0] == 128);                       // top-left untouched
    CHECK(img[(h - 1) * w + (w / 2)] == 128);   // outside the label box (bottom centre-left)
    burn_watermark_nv12(nullptr, w, h, "X");    // must not crash
}

static void test_tiled_rotation_matches_reference() {
    Img im = make(64, 34, 2);
    std::vector<uint8_t> out(nv12_size(34, 64));
    CHECK(yuv420_to_nv12(planes(im), Transform{90, false}, out.data(), 34, 64));
    for (int y = 0; y < 64; ++y) for (int x = 0; x < 34; ++x) {
        const int sx = y, sy = 34 - 1 - x;
        if (out[y * 34 + x] != static_cast<uint8_t>(sx + 10 * sy)) { CHECK(false); return; }
    }
    // Chroma: output chroma (cx, cy) of the 17x32 chroma plane comes from source chroma (cy, 17-1-cx).
    const uint8_t* uv = out.data() + 34 * 64;
    for (int cy = 0; cy < 32; ++cy) for (int cx = 0; cx < 17; ++cx) {
        const int scx = cy, scy = 17 - 1 - cx;
        if (uv[cy * 34 + 2 * cx] != 100 + scx || uv[cy * 34 + 2 * cx + 1] != 200 + scy) { CHECK(false); return; }
    }
    // 270 chroma: output (cx, cy) comes from source (32-1-cy, cx).
    CHECK(yuv420_to_nv12(planes(im), Transform{270, false}, out.data(), 34, 64));
    for (int cy = 0; cy < 32; ++cy) for (int cx = 0; cx < 17; ++cx) {
        const int scx = 32 - 1 - cy, scy = cx;
        if (uv[cy * 34 + 2 * cx] != 100 + scx || uv[cy * 34 + 2 * cx + 1] != 200 + scy) { CHECK(false); return; }
    }
}

static void bench() {
    Img im = make(1920, 1080, 2);
    std::vector<uint8_t> out(nv12_size(1920, 1080));
    for (int rot : {0, 90}) {
        int ow, oh; output_size(1920, 1080, rot, &ow, &oh);
        auto t0 = std::chrono::steady_clock::now();
        for (int i = 0; i < 20; ++i) yuv420_to_nv12(planes(im), Transform{rot, false}, out.data(), ow, oh);
        auto ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count() / 20;
        std::printf("bench 1080p rotation %d: %.2f ms/frame (host)\n", rot, ms);
    }
}

int main() {
    test_watermark();
    test_tiled_rotation_matches_reference();
    bench();
    test_identity(2, false);
    test_identity(2, true);
    test_identity(1, false);
    test_rotations();
    test_analyzer();
    test_audio();
    if (failures) { std::printf("%d failure(s)\n", failures); return 1; }
    std::printf("mediacore tests passed\n");
    return 0;
}
