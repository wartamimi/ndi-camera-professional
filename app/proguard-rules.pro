# JNI entry points are looked up by name from native code.
-keepclasseswithmembernames class * { native <methods>; }
-keep class id.lcscam.app.ndi.NdiNative { *; }
-keep class id.lcscam.app.camera.NativeFrameAnalyzer { *; }
-keep class id.lcscam.app.audio.NativeAudio { *; }
