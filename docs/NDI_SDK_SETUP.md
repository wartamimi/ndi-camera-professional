# Installing the official NDI SDK for Android

The NDI SDK is proprietary. It is **not** in this repository and must never be committed to it.

## 1. Get the SDK

1. Open https://ndi.video/for-developers/ndi-sdk/download/ and fill in the registration form.
2. Download **NDI SDK for Android** (Windows, macOS and Linux installers exist; any of them gives the same Android files).
3. Run the installer and accept the NDI SDK License Agreement.

## 2. Put it where the build looks

Copy the SDK's `include` and `lib` folders into `third_party/ndi-sdk/` so you have:

```
third_party/ndi-sdk/include/Processing.NDI.Lib.h      (plus the other Processing.NDI.*.h)
third_party/ndi-sdk/lib/arm64-v8a/libndi.so
third_party/ndi-sdk/lib/armeabi-v7a/libndi.so
```

`third_party/ndi-sdk/` is git-ignored. Alternatives:

- `-Pndi.sdkDir=/absolute/path` or the `NDI_SDK_DIR` environment variable pointing at a folder with that layout.
- `-Pndi.abis=arm64-v8a` if you only have the 64-bit library.

If anything is missing, Gradle stops with the list of missing files. It never builds a fake NDI app.

> If your SDK version uses a different folder name for the libraries, keep the files but arrange
> them into the layout above. Do not rename `libndi.so`.

## 3. CI (GitHub Actions)

The production job downloads your copy of the SDK at build time:

1. Either use the download link NDI emails you for **NDI SDK for Android (Linux)** (a `.tar.gz` holding a
   self-extracting installer; CI extracts its payload, verified with SDK v6), or zip the SDK folder you installed (it must contain `include/Processing.NDI.Lib.h` and `libndi.so` per ABI somewhere inside).
2. Host it where only you can reach it, e.g. a private cloud-storage link or a release asset in a **private** repository with a token-bearing URL.
3. In GitHub: *Settings › Secrets and variables › Actions*:
   - `NDI_SDK_URL`: the download URL (secret).
   - `NDI_SDK_SHA256`: optional, `sha256sum` of the archive.
   - Variable `NDI_ABIS`: optional, e.g. `arm64-v8a`.

The SDK is extracted to the runner's temp folder, used for the build, and deleted at the end. Built APKs
(which legitimately contain `libndi.so`, as the NDI licence allows inside an application) are uploaded as
private workflow artifacts.

### Release signing

Create a keystore locally (never commit it):

```bash
keytool -genkeypair -v -keystore release.jks -alias ndicam -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks > release.jks.b64
```

Add secrets `SIGNING_KEYSTORE_BASE64` (contents of `release.jks.b64`), `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Locally you can instead create `keystore.properties`
(git-ignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

## 4. Licence obligations when you distribute the APK

From the NDI SDK licensing page (re-check against the EULA in your SDK download):

- The About screen shows "NDI® is a registered trademark of Vizrt NDI AB" and links to https://ndi.video/ (implemented).
- Link to ndi.video wherever NDI is used/selected, on your website and in your documentation.
- Your EULA must cover the NDI SDK EULA requirements.
- Do not redistribute NDI Tools; link to https://ndi.video/tools/ instead.
- Contact NDI before using "NDI" in your product name.
- NDI asks to be told about commercial applications that use NDI.
