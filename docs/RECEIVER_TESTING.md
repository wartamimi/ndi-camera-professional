# External NDI receiver testing

Do these on real hardware and record results in [TEST_REPORT.md](TEST_REPORT.md). Mark a test as passed
only after doing it.

## Setup

- Phone with the **NDI-enabled** APK (About screen must show an NDI SDK version, not "not loaded").
- Computer on the same subnet with one of:
  - **NDI Studio Monitor** (NDI Tools, https://ndi.video/tools/) — reference receiver.
  - OBS Studio + an NDI plugin (e.g. DistroAV), vMix, TriCaster.
- Router: client/AP isolation **off**, multicast allowed. Prefer 5/6 GHz or Ethernet.
- Receiver PC firewall: allow NDI (TCP 5959–5960, TCP/UDP 5961+, UDP 5353 mDNS).

## Procedure

| # | Test | How | Pass criterion |
|---|---|---|---|
| E1 | Discovery | Start stream; open Studio Monitor source list | `HOST (NDI Camera - Android 01)` listed within 5 s |
| E2 | Video | Select source | Live, correct orientation, no colour swap (skin tones natural, not blue/green tinted) |
| E3 | Resolution | Studio Monitor overlay / vMix input properties | Matches selected mode (e.g. 1920x1080) |
| E4 | Frame rate | Receiver stats; app FPS readout | Within ±0.5 fps of the mode for 10 min |
| E5 | Audio | Speak / play tone; receiver audio meter | Audio present, no crackle, correct channel count |
| E6 | A/V sync | Clap on camera; inspect in vMix/OBS recording | Offset ≤ 1 frame after adjusting the A/V offset setting |
| E7 | Drops | 30 min stream at 1080p30 on 5 GHz | App drop counter < 0.1% of frames |
| E8 | Tally | Put input on preview/program in vMix | App pill shows PREVIEW / ON AIR |
| E9 | Rename | Change source name while live | New name appears; receiver can reconnect |
| E10 | Network loss | Turn Wi-Fi off 10 s, then on | App warns; sender recreated; receiver reconnects automatically or after reselecting |
| E11 | Interface change | Switch Wi-Fi network / plug USB Ethernet | Sender restart counter increments; source reappears |
| E12 | Background | Lock screen 10 min while live | Receiver keeps getting video and audio |
| E13 | Camera interruption | Open another camera app while live, return | App shows interruption and recovers when camera is free |
| E14 | Long run | 2 h at 1080p30 | No crash; thermal and memory stable (record Performance screen) |
| E15 | Self-test | NDI settings › Run discovery test while live | PASS (proves local mDNS advertisement) |

## Collecting evidence

- `adb logcat -s NdiSender NdiBridge StreamController CameraEngine AudioEngine FramePipeline NativeNdiRuntime`
- Screenshots of the receiver with its stats overlay.
- Performance screen screenshots at start and end of long runs.
