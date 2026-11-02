# Phase 1 — NDI SDK for Android: verified facts

Researched 2026-11-02 from official NDI sources. Items marked *unverified* could not be checked
against an official page from the build environment and must be confirmed against the SDK you download.

| Topic | Finding | Source |
|---|---|---|
| Android support | Supported since the NDI 5 SDK ("Android (ARM) is now supported"). | [NDI FAQ: Can I develop NDI for Android?](https://ndi.video/?p=1150) |
| Current SDK | NDI 6.3.2, released 2026-04-13. No Android-specific changes listed. | [SDK release notes](https://docs.ndi.video/all/developing-with-ndi/sdk/release-notes) |
| Download | From the NDI developer page; requires a registration form. No public direct link. | [NDI SDK download](https://ndi.video/for-developers/ndi-sdk/download/) |
| Language | C API (C++ samples only); Java/Kotlin need a translation layer (JNI). NDI does not endorse a specific one. | [NDI FAQ: Android examples](https://ndi.video/?p=1159) |
| Library / ABIs | `libndi.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, shipped with `libndi_licenses.txt` and `libndi_bonjour_license.txt`. *Folder layout inside the SDK is not documented on the web; the build expects `include/` and `lib/<abi>/libndi.so` and the CI step searches the archive for these files.* | [KlakNDI issue #106](https://github.com/keijiro/KlakNDI/issues/106), [flutter_ndi README](https://github.com/featherbear/flutter_ndi) |
| Android discovery | NDI uses Android's NSD service. An `NsdManager` instance must exist before any NDI sender/finder/receiver is created, and for as long as they exist: `getSystemService(Context.NSD_SERVICE)`. | [Platform considerations](https://docs.ndi.video/all/developing-with-ndi/sdk/platform-considerations) |
| Sender API | `NDIlib_initialize`, `NDIlib_send_create(NDIlib_send_create_t{p_ndi_name, p_groups, clock_video, clock_audio})`, `NDIlib_send_send_video_v2`, `NDIlib_send_send_video_async_v2`, `NDIlib_send_send_audio_v3`, `NDIlib_send_get_tally`, `NDIlib_send_destroy`, `NDIlib_send_timecode_synthesize`. | [NDI-SEND](https://docs.ndi.video/all/developing-with-ndi/sdk/ndi-send) |
| Async buffer rule | An async video buffer is owned by the SDK until the next async/sync send, destroy, or an async call with NULL. NDI recommends alternating two buffers. | [NDI-SEND](https://docs.ndi.video/all/developing-with-ndi/sdk/ndi-send) |
| Video formats | `NDIlib_video_frame_v2_t` (xres, yres, FourCC, frame_rate_N/D, picture_aspect_ratio, frame_format_type, timecode, p_data, line_stride_in_bytes, p_metadata, timestamp). FourCCs include UYVY, UYVA, P216, PA16, YV12, I420, **NV12**, BGRA/BGRX/RGBA/RGBX. | [Frame types](https://docs.ndi.video/all/developing-with-ndi/sdk/frame-types) |
| Audio format | `NDIlib_audio_frame_v3_t` with `NDIlib_FourCC_audio_type_FLTP` (32-bit float planar, only supported format); `channel_stride_in_bytes` steps between channels. | [Frame types](https://docs.ndi.video/all/developing-with-ndi/sdk/frame-types) |
| Licensing | Royalty-free. Ship the runtime library inside your app (not system-wide). Link to ndi.video near where NDI is used and in docs; show "NDI® is a registered trademark of Vizrt NDI AB" (About box). **Using "NDI" in a product name requires contacting NDI.** NDI Tools may not be redistributed. | [SDK licensing](https://docs.ndi.video/all/developing-with-ndi/sdk/licensing) |
| Redistribution | Headers may be distributed with open-source projects under MIT. Binaries may be distributed inside your application if your EULA covers the NDI SDK EULA terms. Nothing permits putting the SDK in a public repository, so this repo never contains it. | [Software distribution](https://docs.ndi.video/all/developing-with-ndi/sdk/software-distribution) |
| Advanced SDK trial | Advanced SDK trials on Android are limited to 5 minutes. This app uses the standard SDK only. | [SDK licensing](https://docs.ndi.video/all/developing-with-ndi/sdk/licensing) |

## Decisions taken from these facts

1. **Pixel format NV12.** Camera2 delivers YUV_420_888; converting to NV12 is one pass with
   memcpy fast paths for the common semi-planar layout, and NDI accepts NV12 natively.
2. **Async video send with two alternating native buffers**, per the NDI buffer-lifetime rule.
3. **Audio as FLTP** from AudioRecord `ENCODING_PCM_FLOAT`, deinterleaved natively.
4. **`NsdManager` created in `NativeNdiRuntime.initialize()` before `NDIlib_initialize()`** and held for the process lifetime.
5. **Two native libraries**: `libndicam.so` (always built, no NDI) and `libndibridge.so` (links `libndi.so`).
   If the bridge cannot load, the app reports *SDK not installed* instead of failing silently.
6. **The SDK is never committed**; CI downloads it from a secret URL and the build fails explicitly without it.
7. **Product name:** the requested name "NDI Camera Professional" contains "NDI". Per the licensing page the
   owner must contact NDI before publishing under that name. The launcher label is "NDI Camera Pro" for now;
   this is a business decision for the project owner.

## Risks

| Risk | Mitigation |
|---|---|
| SDK folder layout differs from the expected `include/` + `lib/<abi>/` | Gradle prints the exact missing paths; CI searches the archive for the header and each `libndi.so`. |
| Header struct changes between SDK versions | Only long-standing v2/v3 frame structs and functions are used; all fields are set explicitly. |
| mDNS blocked on the Wi-Fi network (client isolation, multicast filtering) | Multicast lock, on-device discovery self-test, NDI Access Manager / extra-IP guidance. |
| Wi-Fi bandwidth for full-bandwidth NDI (≈100–150 Mbit/s at 1080p30) | Measured TX rate shown; link-speed warning; Ethernet supported. |
| Background camera restrictions differ per OEM | Foreground service of type camera+microphone started from a user action; documented as best effort. |
| Thermal throttling during long sessions | Thermal status/headroom shown and warned. |
