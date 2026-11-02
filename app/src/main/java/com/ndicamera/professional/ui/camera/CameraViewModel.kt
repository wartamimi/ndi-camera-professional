package com.ndicamera.professional.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ndicamera.professional.di.AppContainer
import com.ndicamera.professional.domain.model.AntibandingMode
import com.ndicamera.professional.domain.model.AwbPreset
import com.ndicamera.professional.domain.model.CameraControlState
import com.ndicamera.professional.domain.model.FocusMode
import com.ndicamera.professional.domain.model.LensDescriptor
import com.ndicamera.professional.domain.model.VideoMode
import com.ndicamera.professional.domain.model.WhiteBalanceMode
import com.ndicamera.professional.settings.OverlaySettings

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

    fun onVisible(visible: Boolean) = c.streamController.onUiVisibilityChanged(visible)
    fun start() = c.streamController.startStreaming()
    fun stop() = c.streamController.stopStreaming()
    fun selectLens(l: LensDescriptor) = c.streamController.selectLens(l)
    fun selectMode(m: VideoMode) = c.streamController.selectMode(m)
    fun setMuted(m: Boolean) = c.streamController.setMuted(m)
    fun focusAt(x: Float, y: Float, lock: Boolean) = c.cameraEngine.focusAt(x, y, lock)

    private fun update(f: (CameraControlState) -> CameraControlState) = c.cameraEngine.setControls(f)

    /** Switching to manual exposure starts from the values the camera is currently using. */
    fun setManualExposure(manual: Boolean) {
        val t = telemetry.value
        update { s ->
            if (manual) s.copy(autoExposure = false, iso = t.iso ?: s.iso, exposureTimeNs = t.exposureTimeNs ?: s.exposureTimeNs)
            else s.copy(autoExposure = true)
        }
    }
    fun setIso(iso: Int) = update { it.copy(autoExposure = false, iso = iso) }
    fun setShutter(ns: Long) = update { it.copy(autoExposure = false, exposureTimeNs = ns) }
    fun setEv(ev: Int) = update { it.copy(exposureCompensation = ev) }
    fun setAeLock(on: Boolean) = update { it.copy(aeLock = on) }
    fun setFocusMode(m: FocusMode) = update { s ->
        val d = if (m == FocusMode.MANUAL) telemetry.value.focusDistanceDiopters ?: s.focusDistanceDiopters else s.focusDistanceDiopters
        s.copy(focusMode = m, focusDistanceDiopters = d, focusPoint = if (m == FocusMode.MANUAL) null else s.focusPoint)
    }
    fun setFocusDistance(d: Float) = update { it.copy(focusMode = FocusMode.MANUAL, focusDistanceDiopters = d) }
    fun setAwbPreset(p: AwbPreset) = update { it.copy(whiteBalanceMode = WhiteBalanceMode.PRESET, awbPreset = p) }
    fun setKelvin(k: Int) = update { it.copy(whiteBalanceMode = WhiteBalanceMode.KELVIN, kelvin = k) }
    fun setTint(t: Float) = update { it.copy(tint = t) }
    fun setAwbLock(on: Boolean) = update { it.copy(awbLock = on) }
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
