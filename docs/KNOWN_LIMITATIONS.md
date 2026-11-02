# Known limitations

1. **Not yet device-tested or receiver-validated.** See [TEST_REPORT.md](TEST_REPORT.md).
2. **NDI SDK must be supplied by the developer** (registration required); CI needs the `NDI_SDK_URL` secret.
3. **Full-bandwidth NDI only (SpeedHQ, encoded by the SDK).** NDI|HX (H.264/H.265) needs the NDI Advanced SDK and licence; not implemented.
4. **Rotated output costs CPU.** Landscape output with the usual sensor orientation uses the memcpy fast path. Portrait output, reverse landscape and mirrored front camera use a per-pixel path (estimated 5–10 ms per 1080p frame; unmeasured; likely too slow for 4K).
5. **Manual Kelvin is approximate** (black-body approximation applied to WB gains, keeping the HAL colour matrix from the last auto-WB frame).
6. **Audio gain is digital.** Android has no standard API for hardware input gain of built-in or USB microphones.
7. **External audio** works for USB Audio Class devices and wired headsets the OS exposes as inputs; vendor-specific interfaces may not be exposed.
8. **Background streaming is best effort.** A camera/microphone foreground service is used, but OEM power management can still kill or restrict it.
9. **Bandwidth.** Wi-Fi link rate is shown as a PHY rate, not available bandwidth. The TX readout measures what the app sends (all sockets of the app's UID), not what the receiver gets.
10. **Discovery self-test** proves local mDNS advertisement only. Wi-Fi client isolation or receiver firewalls can still block remote discovery.
11. **Product name.** NDI's licensing page requires contacting NDI before using "NDI" in a product name. The launcher label is "NDI Camera Pro" pending that decision.
12. **No local recording**; the app is a live NDI source only.
13. **Instrumented (on-device) tests are not included**; unit tests cover pure logic and the native media core.
