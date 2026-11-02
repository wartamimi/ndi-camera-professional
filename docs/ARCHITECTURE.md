# Architecture

## Modules (packages) and responsibilities

```
app/src/main/java/com/ndicamera/professional/
  NdiCameraApp.kt         Application; creates the DI container
  di/                     AppContainer: manual constructor injection, process scope
  domain/model/           Pure data models: camera, stream, audio, network, system
  domain/ndi/             NdiRuntime / NdiSender interfaces: the NDI abstraction layer
  ndi/                    NativeNdiRuntime (JNI to libndibridge), NsdManager holder
  camera/                 CameraRepository (enumeration + capabilities), CameraEngine (Camera2),
                          ModeSelection, OrientationMath, WhiteBalanceMath, NativeFrameAnalyzer
  audio/                  AudioEngine (AudioRecord, device routing, USB), NativeAudio, AudioMath
  streaming/              StreamController (orchestration), FramePipeline (camera -> NDI), MediaClock
  network/                NetworkMonitor (all local networks, Wi-Fi link, TX rate, multicast lock)
  service/                StreamingService (foreground service: camera|microphone, wake/Wi-Fi locks)
  settings/               AppSettings + SettingsRepository (DataStore)
  diagnostics/            SystemMonitor (battery, thermal, memory)
  ui/                     Compose: camera screen, settings, diagnostics, about, theme, components

app/src/main/cpp/
  camera/                 YUV_420_888 -> NV12 conversion (rotation/mirror), luma analysis (zebra, peaking, histogram)
  audio/                  gain, mute, peak/RMS metering, interleaved -> planar (FLTP)
  ndi/                    NdiSender (double-buffered async video, FLTP audio, tally, connections), finder
  jni/                    jni_core.cpp (libndicam.so), jni_ndi.cpp (libndibridge.so)
native-tests/             host unit tests for the native media core
```

The requested `app/ camera/ ndi/ audio/ network/ service/ settings/ diagnostics/ ui/ data/ domain/ di/`
layout is realised as packages inside one Gradle module. Splitting into Gradle modules was not done
because every layer shares one JNI library set and one APK; packages keep boundaries without
multiplying build configuration. Persistent data lives in `settings/` (the only data source).

## Dependency direction

```
ui  ->  streaming / camera / audio / network / settings   (via AppContainer)
streaming -> domain.ndi.NdiRuntime (interface)  <- ndi.NativeNdiRuntime (only class that touches JNI/NDI)
```

`StreamController` never references the NDI SDK; it uses `NdiRuntime`/`NdiSender`, so the streaming
logic can run against a fake sender in tests and the validation build.

## Threads

| Thread | Owner | Work |
|---|---|---|
| main | UI | Compose only |
| `camera-control` | CameraEngine | all Camera2 calls, capture results |
| `camera-frames` | CameraEngine | ImageReader callbacks, drop detection |
| `ndi-video` | FramePipeline | YUV->NV12 + `NDIlib_send_send_video_async_v2`, overlay analysis |
| `audio-capture` (URGENT_AUDIO) | AudioEngine | AudioRecord read, gain/meter, `NDIlib_send_send_audio_v3` |
| `ndi-control` | StreamController | sender create/destroy, stats polling, discovery |
| `network-monitor` | NetworkMonitor | connectivity callbacks, TX sampling |
| `system-monitor` | SystemMonitor | battery/thermal/memory |

## Video path

```
Camera2 session ──► ImageReader (YUV_420_888, 4 buffers) ──► camera-frames thread
                 └► SurfaceView preview (optional; removed when UI hidden)
camera-frames: timestamp gap check -> single-slot mailbox (older pending frame dropped + counted)
ndi-video:     native yuv420_to_nv12 directly into NDI buffer[n] (memcpy fast path for NV12-layout chroma)
               NDIlib_send_send_video_async_v2(buffer[n]); n ^= 1  (SDK releases the other buffer)
               every ~100 ms: native luma analysis -> ARGB mask bitmap (triple buffered) + histogram
               Image.close()
```

One pixel copy per frame (Camera HAL buffer -> NDI buffer); it is unavoidable because NDI needs the
buffer after the camera Image is returned. No Java-heap pixel copies and no per-frame allocations.

## Audio path

AudioRecord (`ENCODING_PCM_FLOAT`, 48 kHz preferred, mono/stereo, preferred device) -> 20 ms buffers ->
native gain/mute/meter -> native deinterleave to FLTP -> `NDIlib_send_send_audio_v3`.

## A/V sync

Both streams carry explicit NDI timecodes from one clock (`MediaClock`):
video uses the sensor timestamp (BOOTTIME converted to MONOTONIC when the camera reports REALTIME source),
audio uses `AudioRecord.getTimestamp(TIMEBASE_MONOTONIC)` extrapolated to the first sample of each buffer.
Both are mapped to 100 ns units since the Unix epoch. A user offset (±300 ms) compensates device-specific
audio latency.

## NDI states

`SdkNotInstalled` (bridge or libndi.so missing, or validation build) · `InitFailed` (CPU unsupported or
`NDIlib_initialize` false) · `LibraryReady` · `SenderInitialized` · `SourceActive(connections, tally)` ·
`TransmissionStopped` · `SenderError(retrying)`.

## Recovery

- Camera `onDisconnected`/`onError`: state shown, exponential reopen (5 tries) and reopen on `onCameraAvailable`.
- Session configuration rejected: one fallback to 1280x720@30, reported in the UI.
- Network interface/IP change while live: sender destroyed and recreated (mDNS re-advertised), counted as restart.
- `NDIlib_send_create` failure: 5 retries with back-off, then `SenderError`.
- Audio read error / USB device removed: AudioRecord recreated, falls back to default input.
