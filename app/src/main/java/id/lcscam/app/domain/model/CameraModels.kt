package id.lcscam.app.domain.model

/** Lens facing as reported by Camera2. */
enum class LensFacing { BACK, FRONT, EXTERNAL }

/**
 * One selectable lens. Either a camera id from CameraManager.cameraIdList, or a physical camera
 * belonging to a logical multi-camera (physicalId != null), streamed via
 * OutputConfiguration.setPhysicalCameraId.
 */
data class LensDescriptor(
    val cameraId: String,
    val physicalId: String?,
    val facing: LensFacing,
    val focalLengthsMm: List<Float>,
    val label: String,
) {
    val key: String get() = if (physicalId != null) "$cameraId/$physicalId" else cameraId
}

data class Resolution(val width: Int, val height: Int) {
    val label: String get() = "${width}x$height"
    val pixels: Long get() = width.toLong() * height
    companion object {
        val CANDIDATES = listOf(
            Resolution(640, 480),
            Resolution(1280, 720),
            Resolution(1920, 1080),
            Resolution(3840, 2160),
        )
    }
}

/** A resolution + frame-rate combination verified against the camera's stream configuration. */
data class VideoMode(val resolution: Resolution, val fps: Int) {
    val label: String get() = "${resolution.label} @ $fps"
}

data class IntRangeCap(val lower: Int, val upper: Int)
data class LongRangeCap(val lower: Long, val upper: Long)
data class FloatRangeCap(val lower: Float, val upper: Float)

enum class AwbPreset(val label: String) {
    AUTO("AUTO"), INCANDESCENT("TUNGSTEN"), FLUORESCENT("FLUOR"), WARM_FLUORESCENT("WARM FL"),
    DAYLIGHT("DAYLIGHT"), CLOUDY_DAYLIGHT("CLOUDY"), TWILIGHT("TWILIGHT"), SHADE("SHADE"),
}

enum class AntibandingMode(val label: String) { OFF("OFF"), HZ50("50 Hz"), HZ60("60 Hz"), AUTO("AUTO") }

/** Hardware capabilities of the active lens. Everything the UI enables comes from here. */
data class CameraCapabilities(
    val lens: LensDescriptor,
    val hardwareLevel: String,
    val sensorOrientation: Int,
    val modes: List<VideoMode>,
    val manualSensor: Boolean,
    val manualPostProcessing: Boolean,
    val isoRange: IntRangeCap?,
    val exposureTimeRangeNs: LongRangeCap?,
    val maxFrameDurationNs: Long?,
    val exposureCompensationRange: IntRangeCap?,
    val exposureCompensationStep: Float,
    val minFocusDistanceDiopters: Float, // 0 = fixed focus
    val afModes: List<Int>,
    val awbPresets: List<AwbPreset>,
    val awbLockAvailable: Boolean,
    val aeLockAvailable: Boolean,
    val zoomRange: FloatRangeCap,
    val usesZoomRatio: Boolean,
    val flashAvailable: Boolean,
    val oisAvailable: Boolean,
    val eisAvailable: Boolean,
    val antibandingModes: List<AntibandingMode>,
    val maxAfRegions: Int,
    val maxAeRegions: Int,
) {
    val manualFocusAvailable: Boolean get() = minFocusDistanceDiopters > 0f && manualSensor
    val autofocusAvailable: Boolean get() = afModes.any { it != 0 }
    val manualIsoAvailable: Boolean get() = manualSensor && isoRange != null
    val manualShutterAvailable: Boolean get() = manualSensor && exposureTimeRangeNs != null
    val manualWhiteBalanceKelvinAvailable: Boolean get() = manualPostProcessing
}

enum class FocusMode { CONTINUOUS, AUTO_LOCKED, MANUAL }
enum class WhiteBalanceMode { PRESET, KELVIN }

/** User-requested camera control state. Applied to every repeating request. */
data class CameraControlState(
    val autoExposure: Boolean = true,
    val iso: Int = 400,
    val exposureTimeNs: Long = 1_000_000_000L / 50,
    val exposureCompensation: Int = 0,
    val aeLock: Boolean = false,
    val focusMode: FocusMode = FocusMode.CONTINUOUS,
    val focusDistanceDiopters: Float = 0f,
    val focusPoint: Pair<Float, Float>? = null, // normalised 0..1 in sensor-oriented frame
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.PRESET,
    val awbPreset: AwbPreset = AwbPreset.AUTO,
    val kelvin: Int = 5600,
    val tint: Float = 0f,
    val awbLock: Boolean = false,
    val zoom: Float = 1f,
    val torch: Boolean = false,
    val ois: Boolean = true,
    val eis: Boolean = false,
    val antibanding: AntibandingMode = AntibandingMode.AUTO,
)

/** Values actually reported by the camera in the latest TotalCaptureResult. */
data class CaptureTelemetry(
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val frameDurationNs: Long? = null,
    val focusDistanceDiopters: Float? = null,
    val afState: Int? = null,
    val aeState: Int? = null,
    val awbState: Int? = null,
    val colorTemperatureEstimate: Int? = null,
    val zoom: Float? = null,
    val oisActive: Boolean? = null,
)

sealed interface CameraState {
    data object Idle : CameraState
    data object PermissionRequired : CameraState
    data object Opening : CameraState
    data class Running(val mode: VideoMode, val previewAttached: Boolean) : CameraState
    data class Error(val message: String, val recoverable: Boolean) : CameraState
    data class Disconnected(val reason: String) : CameraState
}
