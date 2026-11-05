# Test report

Legend: **Impl** = code written · **Compiled** = compiled in CI · **Unit** = covered by an automated test that passed ·
**Device** = tested on a phone · **External** = verified with an external NDI receiver.

Last CI run: [Android CI #41397047316](https://github.com/wartamimi/ndi-camera-professional/actions/runs/41397047316) (2026-11-02), with the official NDI SDK for Android (v6, Linux-hosted installer).

## Build results

| Check | Result |
|---|---|
| Native media core host tests (converter, rotation, NV21/planar input, analyzer, audio DSP) under ASan/UBSan | PASS |
| Kotlin unit tests (`testDebugUnitTest`) | PASS |
| Validation APK (`-Pndi.validationBuild=true assembleDebug`): Kotlin + `libndicam.so` for arm64-v8a and armeabi-v7a | PASS |
| Android lint (`lintDebug`) | PASS |
| Production NDI build: `libndibridge.so` compiled against the official headers and linked to `libndi.so` (arm64-v8a, armeabi-v7a) | PASS |
| APK content check: `lib/<abi>/libndi.so` and `lib/<abi>/libndibridge.so` present for both ABIs | PASS (debug APK, artifact `ndi-apk`) |
| Release APK | not built: signing secrets not configured |

The NDI bridge compiles and links, but **has not been executed**: whether `NDIlib_initialize` succeeds, the
source is advertised, and frames arrive at a receiver is only known after the device/receiver tests below.

## Feature status

| Feature | Impl | Compiled | Unit | Device | External |
|---|---|---|---|---|---|
| Camera enumeration incl. physical lenses | ✓ | ✓ | – | – | – |
| Supported resolution/fps detection | ✓ | ✓ | ✓ (mode filter logic) | – | – |
| Preview, session management, camera switching | ✓ | ✓ | – | – | – |
| Orientation correction (output rotation, tap mapping) | ✓ | ✓ | ✓ | – | – |
| NDI output follows the interface orientation ("Follow screen") | ✓ | see CI | ✓ (orientation math) | – | – |
| Camera opens once after first-run permission grant (no restart) | ✓ | see CI | – | – | – |
| Timed zoom moves 1–8 s (eased, log-scale) | ✓ | see CI | ✓ (ramp math) | – | – |
| Indonesian / English interface, language picker, region default | ✓ | see CI | ✓ (region rule) | – | – |
| Animated splash logo | ✓ | see CI | – | – | – |
| Camera interruption recovery | ✓ | ✓ | – | – | – |
| Manual ISO / shutter / EV / AE lock | ✓ | ✓ | ✓ (stops, labels) | – | – |
| Focus: AF-C, tap focus, AF lock, MF | ✓ | ✓ | – | – | – |
| White balance presets, AWB lock, Kelvin (approx.) | ✓ | ✓ | ✓ (Kelvin math) | – | – |
| Zoom, torch, OIS, EIS, anti-banding | ✓ | ✓ | – | – | – |
| Zebra, focus peaking, histogram (native) | ✓ | ✓ | ✓ (native analyzer) | – | – |
| Thirds, crosshair, safe areas | ✓ | ✓ | – | – | – |
| YUV_420_888 → NV12 conversion with rotation/mirror | ✓ | ✓ | ✓ (native, ASan) | – | – |
| NDI init / SDK-missing / init-failed states | ✓ | ✓ | – | – | – |
| NDI sender create/destroy, async video, FLTP audio | ✓ | ✓ | – | – | – |
| Tally, connection count, source name | ✓ | ✓ | – | – | – |
| Discovery self-test (NDI finder) | ✓ | ✓ | – | – | – |
| Sender recreation on network change, retry on failure | ✓ | ✓ | – | – | – |
| Audio capture, device selection, USB attach/detach | ✓ | ✓ | – | – | – |
| Gain, mute, meters (native) | ✓ | ✓ | ✓ (native) | – | – |
| A/V timestamping (common monotonic clock) | ✓ | ✓ | ✓ (buffer timestamp math) | – | – |
| Network monitor, TX rate, warnings, multicast lock | ✓ | ✓ | – | – | – |
| Foreground service, wake/Wi-Fi locks, notification stop | ✓ | ✓ | – | – | – |
| Battery / thermal / memory diagnostics | ✓ | ✓ | – | – | – |
| Settings persistence (DataStore) | ✓ | ✓ | ✓ (sanitising) | – | – |
| Release signing in CI | ✓ | not run (no secrets) | – | – | – |

## Not performed yet

All device tests (launch, permissions, preview, switching, manual controls, audio) and all external receiver
tests E1–E15 in [RECEIVER_TESTING.md](RECEIVER_TESTING.md), plus long-duration, memory and thermal runs.
None of these may be reported as passed until performed on hardware.

## 2026-11-02 · Google Play plans, vertical layout, splash, onboarding (CI run 41424463237)

| Item | Implemented | Compiled (CI) | Unit-tested | Device-tested |
|---|---|---|---|---|
| Plans Free/Basic/Pro/Ultra/Max, feature gating | ✓ | ✓ | ✓ (PlansTest) | – |
| Google Play Billing 8 (subscriptions, lifetime, restore) | ✓ | ✓ | – | – (needs a Play test track) |
| Free watermark burned into NDI output | ✓ | ✓ | ✓ (native host test) | – |
| Free 15-minute session limit + dialog | ✓ | ✓ | – | – |
| Vertical layout (default), landscape, auto-rotate | ✓ | ✓ | – | – |
| AndroidX splash screen, first-launch onboarding | ✓ | ✓ | – | – |
| About "Dibuat oleh Munawar Tamimi" | ✓ | ✓ | – | – |
| Target API 36, AGP 8.11.1 / Gradle 8.13 | ✓ | ✓ | – | – |
| 16 KB page alignment (arm64 libndi.so, libndibridge.so, libndicam.so, libc++_shared.so) | ✓ | ✓ readelf: 0x4000 | – | – |
| Signed release AAB | ✓ (workflow) | not run: signing secrets not set | – | – |
