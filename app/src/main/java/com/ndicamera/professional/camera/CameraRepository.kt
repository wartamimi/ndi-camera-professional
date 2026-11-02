package com.ndicamera.professional.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.view.SurfaceHolder
import android.util.Log
import com.ndicamera.professional.domain.model.AntibandingMode
import com.ndicamera.professional.domain.model.AwbPreset
import com.ndicamera.professional.domain.model.CameraCapabilities
import com.ndicamera.professional.domain.model.FloatRangeCap
import com.ndicamera.professional.domain.model.IntRangeCap
import com.ndicamera.professional.domain.model.LensDescriptor
import com.ndicamera.professional.domain.model.LensFacing
import com.ndicamera.professional.domain.model.LongRangeCap
import com.ndicamera.professional.domain.model.Resolution
import kotlin.math.sqrt

/** Enumerates cameras/lenses and reads their real hardware capabilities. */
class CameraRepository(private val cameraManager: CameraManager) {

    fun characteristics(id: String): CameraCharacteristics = cameraManager.getCameraCharacteristics(id)

    fun enumerateLenses(): List<LensDescriptor> {
        val result = mutableListOf<LensDescriptor>()
        val ids = try { cameraManager.cameraIdList.toList() } catch (e: Exception) {
            Log.e(TAG, "cameraIdList failed", e); emptyList()
        }
        val seen = ids.toMutableSet()
        for (id in ids) {
            val chars = try { characteristics(id) } catch (e: Exception) { continue }
            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE !in caps) continue
            val facing = facingOf(chars)
            result += LensDescriptor(id, null, facing, focalLengths(chars), label(facing, chars, id, null))
            if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in caps) {
                for (pid in chars.physicalCameraIds) {
                    if (pid in seen) continue
                    val pchars = try { characteristics(pid) } catch (e: Exception) { continue }
                    result += LensDescriptor(id, pid, facing, focalLengths(pchars), label(facing, pchars, id, pid))
                    seen += pid
                }
            }
        }
        return result
    }

    fun capabilities(lens: LensDescriptor): CameraCapabilities {
        val chars = characteristics(lens.cameraId)
        val streamChars = lens.physicalId?.let { characteristics(it) } ?: chars
        val map = streamChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.map { Resolution(it.width, it.height) } ?: emptyList()
        val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { it.lower to it.upper } ?: emptyList()
        val modes = ModeSelection.supportedModes(yuvSizes, { res ->
            try { map?.getOutputMinFrameDuration(ImageFormat.YUV_420_888, android.util.Size(res.width, res.height)) ?: 0L }
            catch (e: IllegalArgumentException) { 0L }
        }, fpsRanges)

        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val manualSensor = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps
        val manualPost = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in caps
        val iso = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { IntRangeCap(it.lower, it.upper) }
        val exp = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { LongRangeCap(it.lower, it.upper) }
        val ev = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let { IntRangeCap(it.lower, it.upper) }
        val evStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f
        val minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList() ?: emptyList()
        val awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toSet() ?: emptySet()
        val presets = AwbPreset.entries.filter { awbConstant(it) in awbModes }
        val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { FloatRangeCap(it.lower, it.upper) }
        } else null
        val maxDigital = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        val ois = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
        val eis = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true
        val antibanding = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)?.toList()?.mapNotNull {
            when (it) {
                CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_OFF -> AntibandingMode.OFF
                CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ -> AntibandingMode.HZ50
                CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_60HZ -> AntibandingMode.HZ60
                CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO -> AntibandingMode.AUTO
                else -> null
            }
        } ?: emptyList()
        val level = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
        return CameraCapabilities(
            lens = lens,
            hardwareLevel = level,
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
            modes = modes,
            manualSensor = manualSensor,
            manualPostProcessing = manualPost,
            isoRange = iso,
            exposureTimeRangeNs = exp,
            maxFrameDurationNs = chars.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION),
            exposureCompensationRange = ev,
            exposureCompensationStep = evStep,
            minFocusDistanceDiopters = minFocus,
            afModes = afModes,
            awbPresets = presets,
            awbLockAvailable = chars.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true,
            aeLockAvailable = chars.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true,
            zoomRange = zoomRange ?: FloatRangeCap(1f, maxDigital),
            usesZoomRatio = zoomRange != null,
            flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            oisAvailable = ois,
            eisAvailable = eis,
            antibandingModes = antibanding,
            maxAfRegions = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0,
            maxAeRegions = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0,
        )
    }

    fun previewSizes(lens: LensDescriptor): List<Resolution> {
        val chars = characteristics(lens.physicalId ?: lens.cameraId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyList()
        return map.getOutputSizes(SurfaceHolder::class.java)?.map { Resolution(it.width, it.height) } ?: emptyList()
    }

    private fun facingOf(chars: CameraCharacteristics) = when (chars.get(CameraCharacteristics.LENS_FACING)) {
        CameraMetadata.LENS_FACING_FRONT -> LensFacing.FRONT
        CameraMetadata.LENS_FACING_BACK -> LensFacing.BACK
        else -> LensFacing.EXTERNAL
    }

    private fun focalLengths(chars: CameraCharacteristics) =
        chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList()

    private fun label(facing: LensFacing, chars: CameraCharacteristics, id: String, physicalId: String?): String {
        val f = focalLengths(chars).firstOrNull()
        val size = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val eq = if (f != null && size != null) {
            val diag = sqrt(size.width * size.width + size.height * size.height)
            if (diag > 0f) Math.round(f * 43.27f / diag) else null
        } else null
        val base = when (facing) { LensFacing.BACK -> "REAR"; LensFacing.FRONT -> "FRONT"; LensFacing.EXTERNAL -> "EXT" }
        val focal = eq?.let { " ${it}mm" } ?: f?.let { String.format(java.util.Locale.US, " f%.1fmm", it) } ?: ""
        val idPart = if (physicalId != null) "$id:$physicalId" else id
        return "$base$focal (#$idPart)"
    }

    companion object {
        private const val TAG = "CameraRepository"

        fun awbConstant(p: AwbPreset): Int = when (p) {
            AwbPreset.AUTO -> CameraMetadata.CONTROL_AWB_MODE_AUTO
            AwbPreset.INCANDESCENT -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
            AwbPreset.FLUORESCENT -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
            AwbPreset.WARM_FLUORESCENT -> CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT
            AwbPreset.DAYLIGHT -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
            AwbPreset.CLOUDY_DAYLIGHT -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            AwbPreset.TWILIGHT -> CameraMetadata.CONTROL_AWB_MODE_TWILIGHT
            AwbPreset.SHADE -> CameraMetadata.CONTROL_AWB_MODE_SHADE
        }

        fun antibandingConstant(m: AntibandingMode): Int = when (m) {
            AntibandingMode.OFF -> CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_OFF
            AntibandingMode.HZ50 -> CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ
            AntibandingMode.HZ60 -> CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_60HZ
            AntibandingMode.AUTO -> CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO
        }
    }
}
