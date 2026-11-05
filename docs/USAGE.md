# Usage

## Install

Install the NDI-enabled APK (`LCSCam-NDI-debug.apk` or the signed release) with
`adb install -r <apk>` or by opening it on the phone (allow installs from unknown sources).
The `VALIDATION-no-ndi` APK from CI is for compile checks only: it shows "NDI SDK MISSING" and cannot stream.

## First start

1. An animated LCS Cam logo plays, then the onboarding pages. Grant camera, microphone and notification
   permissions; the camera starts right away (no app restart needed).
2. The camera screen opens in the vertical layout (change it in Settings › Camera › Interface orientation). Top bar: NDI state, network, battery, temperature, settings.
3. Connect the phone and the receiver computer to the **same** network (5 GHz/6 GHz Wi-Fi or USB-C Ethernet).

## Streaming

1. Tap **START STREAM**. The pill changes to *NDI SENDER UP* then *LIVE · n RX* when receivers connect,
   *PREVIEW* (green) / *ON AIR* (red) when the receiver sets tally.
2. In the receiver choose the source `PHONE-HOSTNAME (LCS Cam 01)`.
3. Tap **STOP STREAM** or use the notification action. The stream keeps running with the screen off or the
   app in the background while the notification is shown (OEM battery managers can still interfere; see limitations).

## Camera controls (bottom bar)

| Control | Behaviour |
|---|---|
| LENS | Every camera and physical lens the device exposes; plus verified resolution@fps modes |
| FOCUS | AF-C, AF lock, MF slider (dioptres shown as metres). Tap preview = focus point, long-press = focus lock |
| ISO / SHUTTER | AUTO or manual; selecting a value switches the camera to manual exposure (both ISO and shutter). Shutter angle is shown |
| WB | Hardware presets, AWB lock, manual Kelvin + tint (approximate) when the camera supports manual post-processing |
| ZOOM | Optical/digital zoom ratio (API 30+) or crop zoom. **DURATION** chips (Instant, 1 s … 8 s) set how long presets, ZOOM IN and ZOOM OUT take; moves are eased and log-scaled like a servo zoom. STOP holds the current zoom. The slider is always immediate |
| EV | Exposure compensation and AE lock (auto exposure only) |
| TOOLS | Zebra, focus peaking, thirds, crosshair, safe areas, histogram |
| STAB | OIS, EIS, anti-banding 50/60 Hz/auto |
| TORCH | Flashlight |

Greyed controls are not supported by the current lens; they do nothing by design.

## Settings

- **Language / Bahasa:** *Device (region)* (default: Indonesian when the phone language or region is Indonesian, English otherwise), *Bahasa Indonesia* or *English*. Applies immediately.
- **Camera:** lens, mode, NDI output orientation (*Follow screen* (default): output has exactly the orientation shown on screen, e.g. 1080×1920 in the vertical layout and 1920×1080 in landscape; or forced *Portrait* / *Landscape*), front-camera output mirroring, keep screen awake, overlay levels.
- **NDI:** source name (default `LCS Cam 01`), groups, SDK clocking, discovery self-test.
- **Audio:** enable, input device (built-in, USB mic/interface, headset), 48/44.1 kHz, mono/stereo, capture source, digital gain, A/V offset.
- **Network diagnostics:** interface, IPs, Wi-Fi RSSI/band/link rate, measured TX rate, drops, warnings, troubleshooting.
- **Performance:** fps, drops, conversion time, thermal, battery, memory, camera capabilities.
