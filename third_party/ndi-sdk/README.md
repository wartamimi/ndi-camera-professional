# Place the official NDI SDK for Android here (never commit it)

This folder is git-ignored except for this README. After installing the official
NDI SDK for Android (registration required at https://ndi.video/for-developers/ndi-sdk/download/),
copy its `include/` and `lib/` folders here so that the layout is:

```
third_party/ndi-sdk/include/Processing.NDI.Lib.h   (+ the other Processing.NDI.*.h headers)
third_party/ndi-sdk/lib/arm64-v8a/libndi.so
third_party/ndi-sdk/lib/armeabi-v7a/libndi.so       (optional, see -Pndi.abis)
```

Also keep the SDK's license files (the NDI SDK EULA and `libndi_licenses.txt`) with your copy.
See `docs/NDI_SDK_SETUP.md` for the full procedure and the CI secret setup.
