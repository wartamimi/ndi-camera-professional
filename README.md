# NDI Camera Professional (Android)

Turns an Android phone or tablet into a broadcast camera that sends **real NDI®** video and audio
over Wi-Fi or Ethernet, using the **official NDI SDK for Android** through a JNI bridge.
Receivers: OBS Studio (with an NDI plugin), vMix, TriCaster, NDI Studio Monitor, and any other NDI receiver.

> NDI® is a registered trademark of Vizrt NDI AB. https://ndi.video/

| | |
|---|---|
| Package | `com.ndicamera.professional` |
| Min / target SDK | Android 10 (API 29) / API 36 (Google Play requirement) |
| Plans | Free, Basic, Pro, Ultra (subscriptions), Max (lifetime) via Google Play Billing |
| Developer | Munawar Tamimi |
| ABIs | `arm64-v8a` (primary), `armeabi-v7a` |
| Stack | Kotlin, Jetpack Compose, Camera2, AudioRecord, C++17, JNI, NDI SDK |

## Status

Honest status of every feature (implemented / compiled / device-tested / externally validated) is
kept in [`docs/TEST_REPORT.md`](docs/TEST_REPORT.md). In short: everything is implemented and
compiled in CI; **nothing has been device-tested or validated against an external NDI receiver
yet**, because that needs a physical phone, the NDI SDK and a receiver on the same network.

## Build

The NDI SDK is proprietary and needs registration, so it is **not** in this repository.

1. Download the *NDI SDK for Android* from https://ndi.video/for-developers/ndi-sdk/download/ and install it.
2. Copy its `include/` and `lib/` folders into `third_party/ndi-sdk/` (details: [`docs/NDI_SDK_SETUP.md`](docs/NDI_SDK_SETUP.md)).
3. Build:

```bash
./gradlew assembleDebug                       # NDI-enabled debug APK
./gradlew -Pndi.abis=arm64-v8a assembleDebug  # if your SDK copy only has arm64
./gradlew -Pndi.sdkDir=/path/to/sdk assembleDebug
```

Without the SDK the build **fails with an explicit error**. For CI compile checks only:
`./gradlew -Pndi.validationBuild=true assembleDebug` produces an APK labelled
"CI VALIDATION BUILD · NO NDI SDK" that cannot send NDI.

## CI

`.github/workflows/android.yml` has two jobs:

- **Validation build (no NDI SDK)**: native host unit tests (ASan/UBSan), Kotlin unit tests, a full Kotlin + C++ compile, lint, and the validation APK as an artifact.
- **Production build (official NDI SDK)**: downloads your SDK copy from the `NDI_SDK_URL` secret, builds debug (and, when signing secrets exist, a release APK plus the Google Play AAB), checks 16 KB page alignment, verifies `libndi.so` and `libndibridge.so` are inside the APK, and uploads `ndi-apk`. It fails clearly if the secret is missing.

Secrets: `NDI_SDK_URL`, optional `NDI_SDK_SHA256`, and for release signing `SIGNING_KEYSTORE_BASE64`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Optional variable `NDI_ABIS`.

## Documentation

- [NDI SDK setup](docs/NDI_SDK_SETUP.md) — SDK download, placement, CI secret, licensing duties
- [Phase 1 research](docs/PHASE1_RESEARCH.md) — verified SDK facts and sources
- [Architecture](docs/ARCHITECTURE.md)
- [Usage](docs/USAGE.md)
- [Receiver testing](docs/RECEIVER_TESTING.md)
- [Device compatibility](docs/DEVICE_COMPATIBILITY.md)
- [Known limitations](docs/KNOWN_LIMITATIONS.md)
- [Test report](docs/TEST_REPORT.md)
- [Google Play publishing, plans & pricing](docs/GOOGLE_PLAY.md)
- [Privacy policy](docs/PRIVACY_POLICY.md)
