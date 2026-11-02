package com.ndicamera.professional.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.view.Surface
import com.ndicamera.professional.domain.model.CameraCapabilities
import com.ndicamera.professional.domain.model.CameraControlState
import com.ndicamera.professional.domain.model.CameraState
import com.ndicamera.professional.domain.model.CaptureTelemetry
import com.ndicamera.professional.domain.model.FocusMode
import com.ndicamera.professional.domain.model.LensDescriptor
import com.ndicamera.professional.domain.model.Resolution
import com.ndicamera.professional.domain.model.VideoMode
import com.ndicamera.professional.domain.model.WhiteBalanceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Receives every camera frame. The sink takes ownership of the Image and must close it. */
interface FrameSink {
    fun onFrame(image: Image, sensorTimestampNs: Long, timestampIsBoottime: Boolean, mode: VideoMode, lens: LensDescriptor, sensorOrientation: Int)
    /** Blocks until no Image from the current reader is held anymore. */
    fun drain()
}

/**
 * Camera2 engine. All camera calls run on a dedicated "camera-control" thread; frames are
 * delivered on a separate "camera-frames" thread. Preview and the YUV ImageReader that feeds
 * NDI are outputs of the same session, so streaming continues without a preview surface
 * (e.g. when the app is backgrounded under the foreground service).
 */
class CameraEngine(private val context: Context, private val repository: CameraRepository) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraThread = HandlerThread("camera-control").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val cameraExecutor = Executor { cameraHandler.post(it) }
    private val frameThread = HandlerThread("camera-frames", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val frameHandler = Handler(frameThread.looper)

    private val _state = MutableStateFlow<CameraState>(CameraState.Idle)
    val state: StateFlow<CameraState> = _state.asStateFlow()
    private val _capabilities = MutableStateFlow<CameraCapabilities?>(null)
    val capabilities: StateFlow<CameraCapabilities?> = _capabilities.asStateFlow()
    private val _telemetry = MutableStateFlow(CaptureTelemetry())
    val telemetry: StateFlow<CaptureTelemetry> = _telemetry.asStateFlow()
    private val _lenses = MutableStateFlow<List<LensDescriptor>>(emptyList())
    val lenses: StateFlow<List<LensDescriptor>> = _lenses.asStateFlow()
    private val _previewSize = MutableStateFlow(Resolution(1920, 1080))
    val previewSize: StateFlow<Resolution> = _previewSize.asStateFlow()
    private val _controls = MutableStateFlow(CameraControlState())
    val controls: StateFlow<CameraControlState> = _controls.asStateFlow()

    @Volatile var frameSink: FrameSink? = null

    // ---- camera-thread-confined state ----
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var lens: LensDescriptor? = null
    private var mode: VideoMode? = null
    private var caps: CameraCapabilities? = null
    private var chars: CameraCharacteristics? = null
    private var wantOpen = false
    private var reopenAttempts = 0
    private var fallbackTried = false
    private var lastTransform: ColorSpaceTransform? = null
    private var timestampBoottime = false
    private var lastTelemetryMs = 0L
    private var availabilityRegistered = false

    fun refreshLenses() = cameraHandler.post { _lenses.value = repository.enumerateLenses() }

    fun open(lens: LensDescriptor, requested: VideoMode?) = cameraHandler.post {
        wantOpen = true
        reopenAttempts = 0
        fallbackTried = false
        openInternal(lens, requested)
    }

    fun close() = cameraHandler.post {
        wantOpen = false
        closeInternal()
        _state.value = CameraState.Idle
    }

    /** Attach or detach the preview surface. The session is rebuilt; NDI frames keep flowing. */
    fun setPreviewSurface(surface: Surface?) = cameraHandler.post {
        if (surface === previewSurface) return@post
        previewSurface = surface
        if (device != null) createSession()
    }

    /** Detaches the preview synchronously; call from SurfaceHolder.Callback.surfaceDestroyed. */
    fun detachPreviewBlocking(surface: Surface) {
        val latch = CountDownLatch(1)
        cameraHandler.post {
            if (previewSurface === surface) {
                previewSurface = null
                if (device != null) createSession()
            }
            latch.countDown()
        }
        latch.await(1, TimeUnit.SECONDS)
    }

    fun setControls(update: (CameraControlState) -> CameraControlState) = cameraHandler.post {
        _controls.value = update(_controls.value)
        applyRepeating()
    }

    /**
     * Focus/expose at a normalised sensor point. When [lock] is true an AF trigger is sent and the
     * lens stays at the result (focus lock); otherwise continuous AF is biased toward the point.
     */
    fun focusAt(sensorX: Float, sensorY: Float, lock: Boolean) = cameraHandler.post {
        val c = caps ?: return@post
        if (!c.autofocusAvailable) return@post
        _controls.value = _controls.value.copy(
            focusPoint = Pair(sensorX.coerceIn(0f, 1f), sensorY.coerceIn(0f, 1f)),
            focusMode = if (lock) FocusMode.AUTO_LOCKED else _controls.value.focusMode.let {
                if (it == FocusMode.MANUAL) FocusMode.CONTINUOUS else it
            },
        )
        applyRepeating()
        if (lock) triggerAf()
    }

    fun release() {
        cameraHandler.post {
            wantOpen = false
            closeInternal()
            if (availabilityRegistered) cameraManager.unregisterAvailabilityCallback(availabilityCallback)
        }
        cameraThread.quitSafely()
        frameThread.quitSafely()
    }

    // ------------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun openInternal(target: LensDescriptor, requested: VideoMode?) {
        closeInternal()
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            _state.value = CameraState.PermissionRequired
            return
        }
        val c = try { repository.capabilities(target) } catch (e: Exception) {
            _state.value = CameraState.Error("Cannot read camera ${target.label}: ${e.message}", true)
            return
        }
        val selected = requested?.takeIf { it in c.modes } ?: ModeSelection.defaultMode(c.modes)
        if (selected == null) {
            _state.value = CameraState.Error("No supported YUV video mode on ${target.label}", false)
            return
        }
        lens = target
        caps = c
        chars = repository.characteristics(target.cameraId)
        timestampBoottime = chars?.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ==
            CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
        mode = selected
        _capabilities.value = c
        _previewSize.value = ModeSelection.previewSizeFor(selected.resolution, repository.previewSizes(target))
        _controls.value = sanitize(_controls.value, c, selected)
        _state.value = CameraState.Opening
        if (!availabilityRegistered) {
            cameraManager.registerAvailabilityCallback(cameraExecutor, availabilityCallback)
            availabilityRegistered = true
        }
        try {
            cameraManager.openCamera(target.cameraId, cameraExecutor, deviceCallback)
        } catch (e: Exception) {
            Log.e(TAG, "openCamera failed", e)
            _state.value = CameraState.Error("openCamera failed: ${e.message}", true)
            scheduleReopen()
        }
    }

    private fun closeInternal() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { device?.close() } catch (_: Exception) {}
        device = null
        closeReader()
    }

    private fun closeReader() {
        val r = reader ?: return
        r.setOnImageAvailableListener(null, null)
        // Wait until the frame pipeline has released all Images: reading a buffer of a closed
        // ImageReader is undefined behaviour in native code.
        val latch = CountDownLatch(1)
        frameHandler.post { latch.countDown() }
        latch.await(500, TimeUnit.MILLISECONDS)
        frameSink?.drain()
        r.close()
        reader = null
    }

    private val deviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            device = camera
            reopenAttempts = 0
            createSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "Camera disconnected (taken by another client or hardware event)")
            camera.close()
            if (device === camera) device = null
            session = null
            closeReader()
            _state.value = CameraState.Disconnected("Camera in use by another app or disconnected. Will reconnect when available.")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            val msg = when (error) {
                ERROR_CAMERA_IN_USE -> "Camera in use by another app"
                ERROR_MAX_CAMERAS_IN_USE -> "Too many cameras open"
                ERROR_CAMERA_DISABLED -> "Camera disabled by device policy"
                ERROR_CAMERA_DEVICE -> "Camera device error"
                ERROR_CAMERA_SERVICE -> "Camera service error"
                else -> "Camera error $error"
            }
            Log.e(TAG, msg)
            camera.close()
            if (device === camera) device = null
            session = null
            closeReader()
            _state.value = CameraState.Error(msg, error != ERROR_CAMERA_DISABLED)
            if (error != ERROR_CAMERA_DISABLED) scheduleReopen()
        }
    }

    private val availabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            val l = lens ?: return
            if (wantOpen && device == null && cameraId == l.cameraId && _state.value !is CameraState.Opening) {
                Log.i(TAG, "Camera $cameraId available again, reconnecting")
                openInternal(l, mode)
            }
        }
    }

    private fun scheduleReopen() {
        val l = lens ?: return
        if (!wantOpen || reopenAttempts >= 5) return
        reopenAttempts++
        val delay = 500L * (1 shl (reopenAttempts - 1))
        cameraHandler.postDelayed({ if (wantOpen && device == null) openInternal(l, mode) }, delay)
    }

    private fun createSession() {
        val dev = device ?: return
        val m = mode ?: return
        try { session?.close() } catch (_: Exception) {}
        session = null
        val r = reader?.takeIf { it.width == m.resolution.width && it.height == m.resolution.height }
            ?: run {
                closeReader()
                ImageReader.newInstance(m.resolution.width, m.resolution.height, ImageFormat.YUV_420_888, 4).also { reader = it }
            }
        r.setOnImageAvailableListener(imageListener, frameHandler)
        val outputs = mutableListOf<OutputConfiguration>()
        val physical = lens?.physicalId
        outputs += OutputConfiguration(r.surface).apply { if (physical != null) setPhysicalCameraId(physical) }
        previewSurface?.takeIf { it.isValid }?.let { s ->
            outputs += OutputConfiguration(s).apply { if (physical != null) setPhysicalCameraId(physical) }
        }
        val config = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, cameraExecutor, sessionCallback)
        try {
            dev.createCaptureSession(config)
        } catch (e: Exception) {
            Log.e(TAG, "createCaptureSession failed", e)
            _state.value = CameraState.Error("Session failed: ${e.message}", true)
        }
    }

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(s: CameraCaptureSession) {
            session = s
            applyRepeating()
            _state.value = CameraState.Running(mode!!, previewSurface != null)
        }

        override fun onConfigureFailed(s: CameraCaptureSession) {
            val m = mode
            Log.e(TAG, "Session configuration failed for $m")
            val c = caps
            val fallback = c?.modes?.firstOrNull { it.resolution == Resolution(1280, 720) && it.fps == 30 }
            if (!fallbackTried && fallback != null && fallback != m) {
                fallbackTried = true
                mode = fallback
                _state.value = CameraState.Error("${m?.label} rejected by the camera session; falling back to ${fallback.label}", true)
                createSession()
            } else {
                _state.value = CameraState.Error("Camera session configuration failed for ${m?.label}", false)
            }
        }

        override fun onClosed(s: CameraCaptureSession) {
            if (session === s) session = null
        }
    }

    private val imageListener = ImageReader.OnImageAvailableListener { r ->
        val image = try { r.acquireNextImage() } catch (e: IllegalStateException) { null } ?: return@OnImageAvailableListener
        val sink = frameSink
        val m = mode
        val l = lens
        val c = caps
        if (sink == null || m == null || l == null || c == null) {
            image.close()
            return@OnImageAvailableListener
        }
        sink.onFrame(image, image.timestamp, timestampBoottime, m, l, c.sensorOrientation)
    }

    private fun buildRequest(s: CameraCaptureSession): CaptureRequest.Builder? {
        val dev = device ?: return null
        val r = reader ?: return null
        val c = caps ?: return null
        val m = mode ?: return null
        val b = dev.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        b.addTarget(r.surface)
        previewSurface?.takeIf { it.isValid && s.device == dev }?.let { b.addTarget(it) }
        applyControls(b, _controls.value, c, m)
        return b
    }

    private fun applyRepeating() {
        val s = session ?: return
        try {
            val b = buildRequest(s) ?: return
            s.setRepeatingRequest(b.build(), captureCallback, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "setRepeatingRequest failed", e)
        }
    }

    private fun triggerAf() {
        val s = session ?: return
        try {
            val b = buildRequest(s) ?: return
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            s.capture(b.build(), captureCallback, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "AF trigger failed", e)
        }
    }

    private fun applyControls(b: CaptureRequest.Builder, st: CameraControlState, c: CameraCapabilities, m: VideoMode) {
        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        val fpsRange = ModeSelection.fpsRangeFor(m.fps, chars?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { it.lower to it.upper } ?: emptyList())
        fpsRange?.let { b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(it.first, it.second)) }

        // Exposure
        if (!st.autoExposure && c.manualSensor && c.isoRange != null && c.exposureTimeRangeNs != null) {
            val frameNs = 1_000_000_000L / m.fps
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.SENSOR_SENSITIVITY, st.iso.coerceIn(c.isoRange.lower, c.isoRange.upper))
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                st.exposureTimeNs.coerceIn(c.exposureTimeRangeNs.lower, minOf(c.exposureTimeRangeNs.upper, frameNs)))
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, frameNs)
        } else {
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            c.exposureCompensationRange?.let {
                b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, st.exposureCompensation.coerceIn(it.lower, it.upper))
            }
            if (c.aeLockAvailable) b.set(CaptureRequest.CONTROL_AE_LOCK, st.aeLock)
        }
        if (c.antibandingModes.contains(st.antibanding)) {
            b.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraRepository.antibandingConstant(st.antibanding))
        }

        // Focus
        val region = st.focusPoint?.let { meteringRect(it.first, it.second, st.zoom) }
        when {
            st.focusMode == FocusMode.MANUAL && c.manualFocusAvailable -> {
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                b.set(CaptureRequest.LENS_FOCUS_DISTANCE, st.focusDistanceDiopters.coerceIn(0f, c.minFocusDistanceDiopters))
            }
            st.focusMode == FocusMode.AUTO_LOCKED && CameraMetadata.CONTROL_AF_MODE_AUTO in c.afModes -> {
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
            }
            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO in c.afModes ->
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE in c.afModes ->
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            else -> b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
        }
        if (region != null) {
            if (c.maxAfRegions > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
            if (c.maxAeRegions > 0 && st.autoExposure) b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
        }

        // White balance
        if (st.whiteBalanceMode == WhiteBalanceMode.KELVIN && c.manualPostProcessing) {
            val g = WhiteBalanceMath.gainsFor(st.kelvin, st.tint)
            b.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
            b.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            b.set(CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector(g.r, g.gEven, g.gOdd, g.b))
            b.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, lastTransform ?: IDENTITY)
        } else {
            val preset = st.awbPreset.takeIf { it in c.awbPresets } ?: com.ndicamera.professional.domain.model.AwbPreset.AUTO
            b.set(CaptureRequest.CONTROL_AWB_MODE, CameraRepository.awbConstant(preset))
            if (c.awbLockAvailable) b.set(CaptureRequest.CONTROL_AWB_LOCK, st.awbLock)
        }

        // Zoom
        val zoom = st.zoom.coerceIn(c.zoomRange.lower, c.zoomRange.upper)
        if (c.usesZoomRatio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
        } else {
            chars?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { a ->
                val z = zoom.coerceAtLeast(1f)
                val w = (a.width() / z).toInt()
                val h = (a.height() / z).toInt()
                val l = (a.width() - w) / 2
                val t = (a.height() - h) / 2
                b.set(CaptureRequest.SCALER_CROP_REGION, Rect(l, t, l + w, t + h))
            }
        }

        // Torch, stabilisation
        b.set(CaptureRequest.FLASH_MODE,
            if (st.torch && c.flashAvailable) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
        if (c.oisAvailable) {
            b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                if (st.ois) CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON else CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF)
        }
        if (c.eisAvailable) {
            b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                if (st.eis) CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON else CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        }
    }

    private fun meteringRect(x: Float, y: Float, zoom: Float): MeteringRectangle? {
        val a = chars?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
        val z = zoom.coerceAtLeast(1f)
        val cx = a.width() / 2f + (x - 0.5f) * a.width() / z
        val cy = a.height() / 2f + (y - 0.5f) * a.height() / z
        val half = (minOf(a.width(), a.height()) * 0.08f / z).toInt().coerceAtLeast(16)
        val l = (cx.toInt() - half).coerceIn(0, a.width() - 1)
        val t = (cy.toInt() - half).coerceIn(0, a.height() - 1)
        val w = (2 * half).coerceAtMost(a.width() - l)
        val h = (2 * half).coerceAtMost(a.height() - t)
        return MeteringRectangle(l, t, w, h, MeteringRectangle.METERING_WEIGHT_MAX - 1)
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            // Keep the HAL's colour transform from auto-WB so manual Kelvin only changes gains.
            if (_controls.value.whiteBalanceMode != WhiteBalanceMode.KELVIN) {
                result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)?.let { lastTransform = it }
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastTelemetryMs < 150) return
            lastTelemetryMs = now
            val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
            _telemetry.value = CaptureTelemetry(
                iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
                exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                frameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION),
                focusDistanceDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
                afState = result.get(CaptureResult.CONTROL_AF_STATE),
                aeState = result.get(CaptureResult.CONTROL_AE_STATE),
                awbState = result.get(CaptureResult.CONTROL_AWB_STATE),
                colorTemperatureEstimate = gains?.let { WhiteBalanceMath.estimateKelvin(it.red, it.blue) },
                zoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) result.get(CaptureResult.CONTROL_ZOOM_RATIO) else _controls.value.zoom,
                oisActive = result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)?.let { it == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON },
            )
        }
    }

    private fun sanitize(st: CameraControlState, c: CameraCapabilities, m: VideoMode): CameraControlState {
        val frameNs = 1_000_000_000L / m.fps
        return st.copy(
            autoExposure = st.autoExposure || !c.manualIsoAvailable || !c.manualShutterAvailable,
            iso = c.isoRange?.let { st.iso.coerceIn(it.lower, it.upper) } ?: st.iso,
            exposureTimeNs = c.exposureTimeRangeNs?.let { st.exposureTimeNs.coerceIn(it.lower, minOf(it.upper, frameNs)) } ?: st.exposureTimeNs,
            focusMode = when {
                st.focusMode == FocusMode.MANUAL && !c.manualFocusAvailable -> FocusMode.CONTINUOUS
                !c.autofocusAvailable && st.focusMode != FocusMode.MANUAL -> if (c.manualFocusAvailable) FocusMode.MANUAL else FocusMode.CONTINUOUS
                else -> st.focusMode
            },
            focusPoint = null,
            whiteBalanceMode = if (c.manualPostProcessing) st.whiteBalanceMode else WhiteBalanceMode.PRESET,
            zoom = st.zoom.coerceIn(c.zoomRange.lower, c.zoomRange.upper),
            torch = st.torch && c.flashAvailable,
            eis = st.eis && c.eisAvailable,
            ois = st.ois && c.oisAvailable,
        )
    }

    private companion object {
        const val TAG = "CameraEngine"
        val IDENTITY = ColorSpaceTransform(intArrayOf(1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
    }
}
