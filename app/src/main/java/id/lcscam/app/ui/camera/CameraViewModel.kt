package id.lcscam.app.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import id.lcscam.app.billing.Feature
import id.lcscam.app.billing.PlanLimits
import id.lcscam.app.di.AppContainer
import id.lcscam.app.domain.model.AntibandingMode
import id.lcscam.app.domain.model.AwbPreset
import id.lcscam.app.domain.model.CameraControlState
import id.lcscam.app.domain.model.FocusMode
import id.lcscam.app.domain.model.LensDescriptor
import id.lcscam.app.domain.model.VideoMode
import id.lcscam.app.domain.model.WhiteBalanceMode
import id.lcscam.app.settings.OverlaySettings

/** Thin presentation layer over the process-scoped engines; holds no camera resources itself. */
class CameraViewModel(val c: AppContainer) : ViewModel() {
    val cameraState = c.cameraEngine.state
    val capabilities = c.cameraEngine.capabilities
    val telemetry = c.cameraEngine.telemetry
    val controls = c.cameraEngine.controls
    val lenses = c.cameraEngine.lenses
    val previewSize = c.cameraEngine.previewSize
    val ndiStatus = c.streamController.ndiStatus
    val stats = c.streamController.stats
    val warnings = c.streamController.warnings
    val network = c.networkMonitor.status
    val health = c.systemMonitor.health
    val audioLevels = c.audioEngine.levels
    val audioState = c.audioEngine.state
    val settings = c.settings.settings
    val overlay = c.analyzer.output
    val tier = c.billing.tier
    val sessionLimitReached = c.streamController.sessionLimitReached

    fun has(f: Feature) = f.availableIn(tier.value)
    fun lensAllowed(l: LensDescriptor) = c.streamController.lensAllowed(l)
    fun modeAllowed(m: VideoMode) = PlanLimits.modeAllowed(tier.value, m.resolution, m.fps)
    fun dismissSessionLimit() = c.streamController.dismissSessionLimit()

    fun onVisible(visible: Boolean) = c.streamController.onUiVisibilityChanged(visible)
    fun start() = c.streamController.startStreaming()
    fun stop() = c.streamController.stopStreaming()
    fun selectLens(l: LensDescriptor) = c.streamController.selectLens(l)
    fun selectMode(m: VideoMode) = c.streamController.selectMode(m)
    fun setMuted(m: Boolean) = c.streamController.setMuted(m)
    fun focusAt(x: Float, y: Float, lock: Boolean) = c.cameraEngine.focusAt(x, y, lock && has(Feature.MANUAL_FOCUS))

    private fun update(f: (CameraControlState) -> CameraControlState) = c.cameraEngine.setControls(f)

    /** Switching to manual exposure starts from the values the camera is currently using. */
    fun setManualExposure(manual: Boolean) {
        if (manual && !has(Feature.MANUAL_EXPOSURE)) return
        val t = telemetry.value
        update { s ->
            if (manual) s.copy(autoExposure = false, iso = t.iso ?: s.iso, exposureTimeNs = t.exposureTimeNs ?: s.exposureTimeNs)
            else s.copy(autoExposure = true)
        }
    }
    fun setIso(iso: Int) = if (!has(Feature.MANUAL_EXPOSURE)) Unit else update { it.copy(autoExposure = false, iso = iso) }
    fun setShutter(ns: Long) = if (!has(Feature.MANUAL_EXPOSURE)) Unit else update { it.copy(autoExposure = false, exposureTimeNs = ns) }
    fun setEv(ev: Int) = if (!has(Feature.MANUAL_EXPOSURE)) Unit else update { it.copy(exposureCompensation = ev) }
    fun setAeLock(on: Boolean) = if (!has(Feature.MANUAL_EXPOSURE)) Unit else update { it.copy(aeLock = on) }
    fun setFocusMode(m: FocusMode) = if (m != FocusMode.CONTINUOUS && !has(Feature.MANUAL_FOCUS)) Unit else update { s ->
        val d = if (m == FocusMode.MANUAL) telemetry.value.focusDistanceDiopters ?: s.focusDistanceDiopters else s.focusDistanceDiopters
        s.copy(focusMode = m, focusDistanceDiopters = d, focusPoint = if (m == FocusMode.MANUAL) null else s.focusPoint)
    }
    fun setFocusDistance(d: Float) = if (!has(Feature.MANUAL_FOCUS)) Unit else update { it.copy(focusMode = FocusMode.MANUAL, focusDistanceDiopters = d) }
    fun setAwbPreset(p: AwbPreset) = update { it.copy(whiteBalanceMode = WhiteBalanceMode.PRESET, awbPreset = p) }
    fun setKelvin(k: Int) = if (!has(Feature.MANUAL_WB)) Unit else update { it.copy(whiteBalanceMode = WhiteBalanceMode.KELVIN, kelvin = k) }
    fun setTint(t: Float) = if (!has(Feature.MANUAL_WB)) Unit else update { it.copy(tint = t) }
    fun setAwbLock(on: Boolean) = if (!has(Feature.MANUAL_WB)) Unit else update { it.copy(awbLock = on) }
    fun setZoom(z: Float) = update { it.copy(zoom = z) }
    fun setTorch(on: Boolean) = update { it.copy(torch = on) }
    fun setOis(on: Boolean) = update { it.copy(ois = on) }
    fun setEis(on: Boolean) = update { it.copy(eis = on) }
    fun setAntibanding(m: AntibandingMode) = update { it.copy(antibanding = m) }
    fun updateOverlays(f: (OverlaySettings) -> OverlaySettings) = c.settings.update { it.copy(overlays = f(it.overlays)) }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CameraViewModel(c) as T
    }
}
