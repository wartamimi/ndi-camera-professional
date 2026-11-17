# Performance and connection stability

This page records what was changed to make LCS Cam lighter and its NDI stream more stable,
how each change was measured, and what has **not** been measured yet. Video quality is not
touched by any of these changes: resolution, frame rate, NDI compression and audio format are
exactly the same, and the converted frames are verified to be bit-identical to the previous
implementation.

## 1. Faster YUV → NV12 rotation/mirroring (biggest CPU cost in our code)

Every camera frame is converted from Android's `YUV_420_888` into NV12 for the NDI SDK. When the
output is rotated (portrait output — the app's default — or landscape-left) or mirrored (front
camera), the old converter computed a coordinate mapping for every single pixel.

The new converter (`app/src/main/cpp/camera/frame_converter.cpp`) splits the eight
rotation/mirror combinations into two families:

* rotation 0/180: whole rows copied with `memcpy` or reversed 8 bytes at a time;
* rotation 90/270: 8×8 luma tiles / 4×4 chroma-pair tiles loaded as 64-bit words and transposed
  in registers.

Correctness: `native-tests/mediacore_test.cpp` compares the converter against an independent
per-pixel reference for all 4 rotations × mirror on/off × NV12/NV21/planar chroma on 10 frame
sizes (including sizes that are not multiples of the tile), with random pixel data, under
ASan/UBSan. A deliberately broken mapping makes this test fail (182 failures), so it does catch
misplaced bytes.

Host benchmark (Intel Xeon 2.1 GHz, `g++ -O3`, average of 50 frames, output compared byte for
byte with the old implementation — identical in every case):

| Frame | Transform | Old | New |
|---|---|---|---|
| 1080p | rotation 0 (no mirror) | 0.21 ms | 0.22 ms |
| 1080p | rotation 0 + mirror | 2.70 ms | 0.31 ms |
| 1080p | rotation 90 (portrait) | 1.66 ms | 0.74 ms |
| 1080p | rotation 90 + mirror | 3.49 ms | 0.71 ms |
| 1080p | rotation 180 | 2.78 ms | 0.23 ms |
| 1080p | rotation 270 + mirror | 2.89 ms | 0.84 ms |
| 2160p | rotation 0 + mirror | 14.35 ms | 1.42 ms |
| 2160p | rotation 90 (portrait) | 7.09 ms | 4.14 ms |
| 2160p | rotation 90 + mirror | 17.19 ms | 3.31 ms |
| 2160p | rotation 180 | 11.17 ms | 0.99 ms |

At 4K30 the frame budget is 33 ms; on a phone CPU the old mirrored/180° path could take a large
part of it. **Not yet measured on a phone**: open *Diagnostics → Performance* while streaming
and read "YUV→NV12 + send" to see the real number on your device.

## 2. No encoding while nobody is watching

The NDI SDK documentation recommends skipping work when `NDIlib_send_get_no_connections()`
returns 0. The sender now re-reads the receiver count every 100 ms and, while it is 0, skips the
colour conversion and the SpeedHQ compression completely. The camera keeps running and the
source stays advertised; a receiver that connects gets its first frame at most ~100 ms later.
Skipped frames are shown in *Diagnostics → Performance* as "Skipped: no receiver" and are not
counted as drops. The FPS read-out shows the camera's delivered rate either way.

## 3. Overlay analysis only when it is visible

Zebra/peaking/histogram analysis ran on the video thread several times per second even when the
camera screen was not visible (background streaming) and on plans that cannot show overlays. It
now runs only when the camera screen is visible and at least one of those overlays is enabled
on a plan that has them.

## 4. Connection stability

* **Debounced network recovery.** Wi-Fi roaming, DHCP renewals and Wi-Fi/Ethernet handovers come
  as bursts of callbacks. Previously every burst recreated the NDI sender (often twice: once when
  the network dropped, once when it came back), disconnecting all receivers each time. Now the
  decision is taken once the network has been quiet for 1.2 s: same interface and address → keep
  the sender (receivers stay connected); new interface/address, or the LAN dropped in between →
  recreate once; no LAN at all → wait for it to come back. Logic in `SenderRecovery.kt`, covered
  by `SenderRecoveryTest`.
* **No duplicate or orphan senders.** Sender create/replace/close is serialised with a mutex.
  Before, overlapping network events could create two senders with the same name (duplicate
  sources on the network), and pressing Stop during a re-creation could leave a sender running.
* **Recovery never gives up while live.** After a network change the sender is re-created with
  backoff (1 s … 5 s) for as long as streaming is on, instead of giving up after 5 attempts.
* **Camera stall watchdog.** If the camera reports "running" but delivers no frames for 5 s, the
  camera session is reopened (at most once every 15 s).

## Not changed / still to verify

* APK size: release builds were already minified/resource-shrunk; the Play bundle is split per
  ABI by Google Play, so a phone only downloads its own native libraries. Nothing further was
  removed, because the remaining size is mostly the NDI SDK's own `libndi.so`.
* No change was measured on a real phone or with a real receiver yet (no device in CI). The
  checks to run are listed in `docs/RECEIVER_TESTING.md`; for this change in particular: CPU /
  thermal over 30 min in portrait 1080p30, first-frame time when OBS connects, and turning
  Wi-Fi off/on while OBS is connected.
