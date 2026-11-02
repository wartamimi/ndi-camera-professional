# JNI entry points are looked up by name from native code.
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.ndicamera.professional.ndi.NdiNative { *; }
-keep class com.ndicamera.professional.camera.NativeFrameAnalyzer { *; }
-keep class com.ndicamera.professional.audio.NativeAudio { *; }
