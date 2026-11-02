# Device compatibility

## Requirements

| Item | Requirement | Why |
|---|---|---|
| Android | 10 (API 29) or newer | `SessionConfiguration`, thermal API, `OutputConfiguration.setPhysicalCameraId` (28), float audio, NDI Android support |
| ABI | arm64-v8a (recommended) or armeabi-v7a | NDI SDK ships these; x86/x86_64 exist in the SDK but are not packaged by default |
| Camera | Camera2 with BACKWARD_COMPATIBLE; YUV_420_888 output | Required for the ImageReader pipeline |
| Network | Wi-Fi 5/6 or USB Ethernet adapter | Full-bandwidth NDI 1080p30 needs roughly 100–150 Mbit/s (estimate; measure with the TX readout) |

## What adapts per device

Everything shown in the UI is read from `CameraCharacteristics`:

- Modes: only resolution/fps pairs where YUV_420_888 is supported at that size, the minimum frame duration allows the rate, and an AE target fps range ends at that rate.
- Manual ISO/shutter: `MANUAL_SENSOR` capability. Many LIMITED/LEGACY devices do not have it.
- Manual Kelvin: `MANUAL_POST_PROCESSING`.
- Manual focus: `LENS_INFO_MINIMUM_FOCUS_DISTANCE > 0` and `MANUAL_SENSOR`.
- Zoom: `CONTROL_ZOOM_RATIO_RANGE` (API 30+) or max digital zoom.
- OIS/EIS/anti-banding/torch/AF/AE regions: their respective characteristics.
- Lenses: every camera id plus physical cameras of logical multi-cameras. Some OEMs hide auxiliary lenses from third-party apps; those cannot be offered.

## Known per-device behaviour (expected, not yet measured)

- 4K YUV at 30 fps is often available only on flagship SoCs; 4K60 YUV is rare. The app will not list unsupported modes.
- 25/50 fps need an AE range ending at 25/50; many phones only provide 15–30/30/60, so 25/50 may be absent.
- Front cameras often lack manual sensor control.
- Some OEM skins (aggressive battery managers) may stop background camera use despite the foreground service.

## Tested devices

None yet. Add rows here as devices are tested:

| Device | Android | SoC | Modes listed | Manual controls | NDI result | Notes |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — |
