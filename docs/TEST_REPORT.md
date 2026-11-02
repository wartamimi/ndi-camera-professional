# Test report

Legend: **Impl** = code written · **Compiled** = compiled in CI · **Unit** = covered by an automated test that passed ·
**Device** = tested on a phone · **External** = verified with an external NDI receiver.

Last CI run: [Android CI #41394484058](https://github.com/wartamimi/ndi-camera-professional/actions/runs/41394484058) (2026-11-02).

## Build results

| Check | Result |
|---|---|
| Native media core host tests (converter, rotation, NV21/planar input, analyzer, audio DSP) under ASan/UBSan | PASS |
| Kotlin unit tests (`testDebugUnitTest`) | PASS |
| Validation APK (`-Pndi.validationBuild=true assembleDebug`): Kotlin + `libndicam.so` for arm64-v8a and armeabi-v7a | PASS |
| Android lint (`lintDebug`) | PASS |
| Production NDI build (`libndibridge.so` linked against `libndi.so`) | **NOT RUN**: `NDI_SDK_URL` secret not configured, job failed with the intended explicit error |

`libndibridge.so` (`ndi_sender.cpp`, `ndi_finder.cpp`, `jni_ndi.cpp`) has **never been compiled**, because no copy
of the NDI SDK headers was available to the build. Expect possibly small fixes the first time it builds.

## Feature status

| Feature | Impl | Compiled | Unit | Device | External |
|---|---|---|---|---|---|
| Camera enumeration incl. physical lenses | ✓ | ✓ | – | – | – |
| Supported resolution/fps detection | ✓ | ✓ | ✓ (mode filter logic) | – | – |
| Preview, session management, camera switching | ✓ | ✓ | – | – | – |
| Orientation correction (output rotation, tap mapping) | ✓ | ✓ | ✓ | – | – |
| Camera interruption recovery | ✓ | ✓ | – | – | – |
| Manual ISO / shutter / EV / AE lock | ✓ | ✓ | ✓ (stops, labels) | – | – |
| Focus: AF-C, tap focus, AF lock, MF | ✓ | ✓ | – | – | – |
| White balance presets, AWB lock, Kelvin (approx.) | ✓ | ✓ | ✓ (Kelvin math) | – | – |
| Zoom, torch, OIS, EIS, anti-banding | ✓ | ✓ | – | – | – |
| Zebra, focus peaking, histogram (native) | ✓ | ✓ | ✓ (native analyzer) | – | – |
| Thirds, crosshair, safe areas | ✓ | ✓ | – | – | – |
| YUV_420_888 → NV12 conversion with rotation/mirror | ✓ | ✓ | ✓ (native, ASan) | – | – |
| NDI init / SDK-missing / init-failed states | ✓ | ✓ (Kotlin) / ✗ (bridge) | – | – | – |
| NDI sender create/destroy, async video, FLTP audio | ✓ | ✗ | – | – | – |
| Tally, connection count, source name | ✓ | ✗ | – | – | – |
| Discovery self-test (NDI finder) | ✓ | ✗ | – | – | – |
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
