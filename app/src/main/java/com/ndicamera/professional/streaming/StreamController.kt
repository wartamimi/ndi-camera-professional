package com.ndicamera.professional.streaming

import android.os.SystemClock
import android.util.Log
import com.ndicamera.professional.audio.AudioConfig
import com.ndicamera.professional.audio.AudioEngine
import com.ndicamera.professional.audio.AudioSink
import com.ndicamera.professional.billing.Feature
import com.ndicamera.professional.billing.PlanLimits
import com.ndicamera.professional.billing.Tier
import com.ndicamera.professional.domain.model.FocusMode
import com.ndicamera.professional.domain.model.LensFacing
import com.ndicamera.professional.domain.model.WhiteBalanceMode
import com.ndicamera.professional.camera.AnalyzerSettings
import com.ndicamera.professional.camera.CameraEngine
import com.ndicamera.professional.camera.NativeFrameAnalyzer
import com.ndicamera.professional.domain.model.CameraState
import com.ndicamera.professional.domain.model.LensDescriptor
import com.ndicamera.professional.domain.model.NdiStatus
import com.ndicamera.professional.domain.model.NetworkWarning
import com.ndicamera.professional.domain.model.Resolution
import com.ndicamera.professional.domain.model.StreamStatistics
import com.ndicamera.professional.domain.model.Transport
import com.ndicamera.professional.domain.model.VideoMode
import com.ndicamera.professional.domain.ndi.NdiInitResult
import com.ndicamera.professional.domain.ndi.NdiRuntime
import com.ndicamera.professional.domain.ndi.NdiSender
import com.ndicamera.professional.domain.ndi.SenderConfig
import com.ndicamera.professional.network.NetworkMonitor
import com.ndicamera.professional.settings.AppSettings
import com.ndicamera.professional.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Starts/stops the Android foreground service; implemented by the service layer. */
interface ForegroundServiceControl {
    fun startStreamingService(sourceName: String, withMicrophone: Boolean)
    fun stopStreamingService()
}

/**
 * Orchestrates camera -> NDI video, audio -> NDI audio, NDI sender lifecycle, statistics,
 * network-change recovery and UI-visibility-driven camera lifetime.
 */
class StreamController(
    private val ndi: NdiRuntime,
    private val camera: CameraEngine,
    private val audio: AudioEngine,
    private val network: NetworkMonitor,
    private val settingsRepo: SettingsRepository,
    private val analyzer: NativeFrameAnalyzer,
    private val scope: CoroutineScope,
    private val ndiDispatcher: CoroutineDispatcher,
    /** Active Google Play plan; drives every feature limit. */
    val tier: StateFlow<Tier>,
) {
    val clock = MediaClock()
    val pipeline = FramePipeline(clock, analyzer)

    private val _ndiStatus = MutableStateFlow<NdiStatus>(NdiStatus.TransmissionStopped)
    val ndiStatus: StateFlow<NdiStatus> = _ndiStatus.asStateFlow()
    private val _stats = MutableStateFlow(StreamStatistics())
    val stats: StateFlow<StreamStatistics> = _stats.asStateFlow()
    private val _warnings = MutableStateFlow<Set<NetworkWarning>>(emptySet())
    val warnings: StateFlow<Set<NetworkWarning>> = _warnings.asStateFlow()
    private val _discoveredSources = MutableStateFlow<List<String>?>(null)
    val discoveredSources: StateFlow<List<String>?> = _discoveredSources.asStateFlow()
    private val _ndiVersion = MutableStateFlow<String?>(null)
    val ndiVersion: StateFlow<String?> = _ndiVersion.asStateFlow()

    var serviceControl: ForegroundServiceControl? = null

    /** Set when a Free session was stopped by the time limit; cleared on the next start. */
    private val _sessionLimitReached = MutableStateFlow(false)
    val sessionLimitReached: StateFlow<Boolean> = _sessionLimitReached.asStateFlow()

    fun has(f: Feature): Boolean = f.availableIn(tier.value)
    fun dismissSessionLimit() { _sessionLimitReached.value = false }

    @Volatile private var sender: NdiSender? = null
    private val audioBuffersSent = java.util.concurrent.atomic.AtomicLong()
    private val audioErrors = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var streaming = false
    private var statsJob: Job? = null
    private var uiVisible = false
    private var startedAt = 0L
    private var restarts = 0
    private var deviceOrientation = 0
    private var lastLandscape = 270
    private var lastPortrait = 0
    private var libraryReady = false

    val isStreaming: Boolean get() = streaming

    init {
        camera.frameSink = pipeline
        camera.modeFilter = { m -> PlanLimits.modeAllowed(tier.value, m.resolution, m.fps) }
        pipeline.deviceOrientationProvider = {
            com.ndicamera.professional.camera.OrientationMath.constrainDeviceOrientation(
                deviceOrientation, lastLandscape, lastPortrait, settingsRepo.settings.value.outputOrientation)
        }
        audio.sink = AudioSink { buf, frames, ch, rate, startNs ->
            if (streaming) {
                val rc = sender?.sendAudio(buf, frames, ch, rate, clock.audioTimecode(startNs)) ?: -1
                if (rc == 0) audioBuffersSent.incrementAndGet() else audioErrors.incrementAndGet()
            }
        }
        scope.launch { initializeNdi() }
        scope.launch { tier.collect { t -> enforcePlan(t) } }
        scope.launch {
            settingsRepo.settings.collect { s -> applySettings(s) }
        }
        scope.launch {
            network.interfaceChanges.collect { st ->
                if (streaming) {
                    Log.i(TAG, "Network interface changed (${st.interfaceName} ${st.ipv4Addresses}); recreating NDI sender")
                    addWarning(NetworkWarning.INTERFACE_CHANGED)
                    recreateSender()
                }
            }
        }
        scope.launch {
            network.status.map { it.connected to it.transport }.distinctUntilChanged().collect { (connected, transport) ->
                setWarning(NetworkWarning.DISCONNECTED, !connected && transport != Transport.CELLULAR)
                setWarning(NetworkWarning.CELLULAR_ONLY, transport == Transport.CELLULAR)
            }
        }
    }

    private suspend fun initializeNdi() = withContext(ndiDispatcher) {
        when (val r = ndi.initialize()) {
            is NdiInitResult.Ready -> {
                libraryReady = true
                _ndiVersion.value = r.version
                _ndiStatus.value = NdiStatus.LibraryReady(r.version)
            }
            is NdiInitResult.SdkNotInstalled -> _ndiStatus.value = NdiStatus.SdkNotInstalled(r.detail)
            is NdiInitResult.Failed -> _ndiStatus.value = NdiStatus.InitFailed(r.detail)
        }
    }

    private var lastAudioConfig: AudioConfig? = null
    private fun applySettings(s: AppSettings) {
        val o = s.overlays
        val t = tier.value
        val pro = Feature.PRO_OVERLAYS.availableIn(t)
        analyzer.settings = AnalyzerSettings(o.zebra && pro, o.zebraThresholdCode, o.peaking && pro, o.peakingThreshold, o.peakingColor)
        pipeline.mirrorFrontCamera = s.mirrorFrontOutput
        pipeline.watermark = PlanLimits.watermark(t)
        clock.audioOffsetNs = if (Feature.AV_SYNC_OFFSET.availableIn(t)) s.avOffsetMs * 1_000_000L else 0L
        val cfg = AudioConfig(
            enabled = s.audioEnabled,
            deviceId = if (Feature.EXTERNAL_AUDIO.availableIn(t)) s.audioDeviceId else 0,
            sampleRate = s.audioSampleRate,
            channels = minOf(s.audioChannels, PlanLimits.maxAudioChannels(t)),
            gainDb = s.audioGainDb, muted = audio.currentConfig().muted, source = s.audioSource,
        )
        if (cfg != lastAudioConfig) {
            lastAudioConfig = cfg
            audio.configure(cfg)
            if (cfg.enabled && (uiVisible || streaming)) audio.start()
        }
    }

    fun setMuted(muted: Boolean) {
        val c = audio.currentConfig().copy(muted = muted)
        lastAudioConfig = c
        audio.configure(c)
    }

    fun onDeviceOrientation(snapped: Int) {
        deviceOrientation = snapped
        if (snapped == 90 || snapped == 270) lastLandscape = snapped else lastPortrait = snapped
    }

    // ---- camera lifetime -------------------------------------------------------------------

    /** Called by the camera screen when it becomes visible or hidden. */
    fun onUiVisibilityChanged(visible: Boolean) {
        uiVisible = visible
        if (visible) {
            ensureCameraOpen()
            if (settingsRepo.settings.value.audioEnabled) audio.start()
        } else if (!streaming) {
            camera.close()
            audio.stop()
        } else if (!has(Feature.BACKGROUND_STREAMING)) {
            // Free plan: streaming stops when the app leaves the screen.
            stopStreaming()
        }
    }

    /** Applies plan limits immediately, e.g. after a purchase, restore or expired subscription. */
    private fun enforcePlan(t: Tier) {
        applySettings(settingsRepo.settings.value)
        camera.setControls { c ->
            var n = c
            if (!Feature.MANUAL_EXPOSURE.availableIn(t)) n = n.copy(autoExposure = true, aeLock = false, exposureCompensation = 0)
            if (!Feature.MANUAL_FOCUS.availableIn(t) && n.focusMode != FocusMode.CONTINUOUS) n = n.copy(focusMode = FocusMode.CONTINUOUS)
            if (!Feature.MANUAL_WB.availableIn(t)) n = n.copy(whiteBalanceMode = WhiteBalanceMode.PRESET, awbLock = false)
            n
        }
        val mode = camera.currentMode
        val lens = camera.capabilities.value?.lens
        if (lens != null && ((mode != null && !PlanLimits.modeAllowed(t, mode.resolution, mode.fps)) || !lensAllowed(lens, t))) {
            val target = if (lensAllowed(lens, t)) lens else camera.lenses.value.firstOrNull { lensAllowed(it, t) } ?: lens
            camera.open(target, mode)
        }
    }

    /** Free/Basic: the primary rear and primary front camera only. */
    fun lensAllowed(l: LensDescriptor, t: Tier = tier.value): Boolean {
        if (Feature.ALL_LENSES.availableIn(t)) return true
        if (l.physicalId != null) return false
        val lenses = camera.lenses.value
        val primaryBack = lenses.firstOrNull { it.facing == LensFacing.BACK && it.physicalId == null }
        val primaryFront = lenses.firstOrNull { it.facing == LensFacing.FRONT && it.physicalId == null }
        return l.key == primaryBack?.key || l.key == primaryFront?.key
    }

    fun ensureCameraOpen() {
        val st = camera.state.value
        if (st is CameraState.Running || st is CameraState.Opening) return
        openCameraFromSettings()
    }

    private fun openCameraFromSettings() {
        camera.refreshLenses()
        scope.launch {
            // Lens enumeration happens on the camera thread; wait briefly for the list.
            var lenses = camera.lenses.value
            var tries = 0
            while (lenses.isEmpty() && tries++ < 20) { delay(50); lenses = camera.lenses.value }
            val s = settingsRepo.settings.value
            val lens = lenses.firstOrNull { it.key == s.lensKey && lensAllowed(it) } ?: lenses.firstOrNull() ?: return@launch
            camera.open(lens, VideoMode(Resolution(s.width, s.height), s.fps))
        }
    }

    fun selectLens(lens: LensDescriptor) {
        if (!lensAllowed(lens)) return
        val s = settingsRepo.settings.value
        settingsRepo.update { it.copy(lensKey = lens.key) }
        camera.open(lens, VideoMode(Resolution(s.width, s.height), s.fps))
    }

    fun selectMode(mode: VideoMode) {
        if (!PlanLimits.modeAllowed(tier.value, mode.resolution, mode.fps)) return
        settingsRepo.update { it.copy(width = mode.resolution.width, height = mode.resolution.height, fps = mode.fps) }
        val lens = camera.capabilities.value?.lens ?: return
        camera.open(lens, mode)
    }

    // ---- NDI transmission --------------------------------------------------------------------

    fun startStreaming() {
        if (streaming) return
        when (val st = _ndiStatus.value) {
            is NdiStatus.SdkNotInstalled, is NdiStatus.InitFailed -> {
                Log.w(TAG, "Cannot start: $st")
                return
            }
            else -> Unit
        }
        scope.launch(ndiDispatcher) {
            if (!libraryReady) initializeNdi()
            if (!libraryReady) return@launch
            val s = settingsRepo.settings.value
            network.setMulticastLock(true)
            clock.resync()
            val created = createSenderWithRetry(s)
            if (created == null) {
                network.setMulticastLock(false)
                return@launch
            }
            _sessionLimitReached.value = false
            pipeline.resetCounters()
            audioBuffersSent.set(0)
            audioErrors.set(0)
            restarts = 0
            startedAt = SystemClock.elapsedRealtime()
            pipeline.sender = created
            streaming = true
            ensureCameraOpen()
            if (s.audioEnabled) audio.start()
            serviceControl?.startStreamingService(created.sourceName() ?: s.sourceName, s.audioEnabled)
            startStatsLoop()
        }
    }

    fun stopStreaming() {
        if (!streaming && sender == null) return
        streaming = false
        statsJob?.cancel()
        scope.launch(ndiDispatcher) {
            pipeline.sender = null
            pipeline.drain()
            sender?.close()
            sender = null
            network.setMulticastLock(false)
            _ndiStatus.value = NdiStatus.TransmissionStopped
            _stats.value = _stats.value.copy(running = false)
            serviceControl?.stopStreamingService()
            if (!uiVisible) {
                camera.close()
                audio.stop()
            }
        }
    }

    private suspend fun createSenderWithRetry(s: AppSettings): NdiSender? {
        var attempt = 0
        while (scope.isActive && attempt < 5) {
            val name = if (has(Feature.CUSTOM_SOURCE_NAME)) AppSettings.sanitizeSourceName(s.sourceName) else AppSettings.DEFAULT_SOURCE_NAME
            val groups = if (has(Feature.NDI_GROUPS)) s.ndiGroups.ifBlank { null } else null
            val created = ndi.createSender(SenderConfig(name, groups, s.clockVideo))
            if (created != null) {
                sender = created
                _ndiStatus.value = NdiStatus.SenderInitialized(created.sourceName() ?: s.sourceName)
                return created
            }
            attempt++
            _ndiStatus.value = NdiStatus.SenderError("NDIlib_send_create failed (attempt $attempt/5)", retrying = attempt < 5)
            delay(1000L * attempt)
        }
        return null
    }

    private fun recreateSender() {
        scope.launch(ndiDispatcher) {
            if (!streaming) return@launch
            pipeline.sender = null
            pipeline.drain()
            sender?.close()
            sender = null
            delay(500) // let the new interface settle before re-advertising via mDNS
            val created = createSenderWithRetry(settingsRepo.settings.value)
            if (created != null && streaming) {
                restarts++
                pipeline.sender = created
            } else if (created == null) {
                _ndiStatus.value = NdiStatus.SenderError("Could not recreate NDI sender after network change", retrying = false)
            }
        }
    }

    /** Restarts the sender, e.g. after the user changed the source name while live. */
    fun restartSender() = recreateSender()

    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = scope.launch(ndiDispatcher) {
            var lastFrames = 0L
            var lastT = SystemClock.elapsedRealtime()
            var lastDropped = 0L
            var unstableSeconds = 0
            while (isActive && streaming) {
                delay(1000)
                val s = sender ?: continue
                val now = SystemClock.elapsedRealtime()
                val frames = pipeline.framesSent.get()
                val fps = (frames - lastFrames) * 1000f / (now - lastT).coerceAtLeast(1)
                lastFrames = frames; lastT = now
                val connections = s.connections()
                val tally = if (has(Feature.TALLY)) s.tally() else com.ndicamera.professional.domain.ndi.Tally(false, false)
                PlanLimits.sessionLimitMs(tier.value)?.let { limit ->
                    if (now - startedAt >= limit) {
                        _sessionLimitReached.value = true
                        stopStreaming()
                        return@launch
                    }
                }
                val name = s.sourceName() ?: settingsRepo.settings.value.sourceName
                _ndiStatus.value = NdiStatus.SourceActive(name, connections.coerceAtLeast(0), tally.onProgram, tally.onPreview)
                val dropped = pipeline.framesDroppedPipeline.get() + pipeline.framesDroppedCamera.get()
                unstableSeconds = if (dropped - lastDropped > 2) unstableSeconds + 1 else 0
                lastDropped = dropped
                setWarning(NetworkWarning.UNSTABLE_TRANSMISSION, unstableSeconds >= 3)
                _stats.value = StreamStatistics(
                    running = true,
                    startedAtElapsedMs = startedAt,
                    videoFramesSent = frames,
                    audioBuffersSent = audioBuffersSent.get(),
                    framesDroppedPipeline = pipeline.framesDroppedPipeline.get(),
                    framesDroppedCamera = pipeline.framesDroppedCamera.get(),
                    currentFps = fps,
                    lastConvertMs = pipeline.lastConvertMs,
                    sendErrors = pipeline.sendErrors.get() + audioErrors.get(),
                    senderRestarts = restarts,
                    txBitsPerSecond = network.txBitsPerSecond.value,
                )
                val st = network.status.value
                setWarning(NetworkWarning.WEAK_WIFI, st.transport == Transport.WIFI && (st.wifiRssiDbm ?: 0) < -72)
                val res = camera.state.value.let { (it as? CameraState.Running)?.mode?.resolution }
                val needMbps = if (res != null && res.pixels >= 3840L * 2160) 250 else if (res != null && res.pixels >= 1920L * 1080) 130 else 60
                setWarning(NetworkWarning.LOW_LINK_SPEED, st.transport == Transport.WIFI && (st.wifiTxLinkSpeedMbps ?: st.wifiLinkSpeedMbps ?: Int.MAX_VALUE) < needMbps)
            }
        }
    }

    /**
     * Discovery self-test: runs an NDI finder on this device and checks that our own source is
     * listed. This proves the sender is advertised via mDNS on this device; it does not prove
     * that a remote receiver can see it (client isolation / firewalls can still block it).
     */
    fun runDiscoveryTest(onDone: (found: Boolean, sources: List<String>) -> Unit = { _, _ -> }) {
        scope.launch(ndiDispatcher) {
            if (!libraryReady) { onDone(false, emptyList()); return@launch }
            val extra = settingsRepo.settings.value.ndiExtraIps.ifBlank { null }
            val sources = ndi.findSources(4000, extra)
            _discoveredSources.value = sources
            val own = sender?.sourceName()
            val found = own != null && sources.any { it == own }
            if (streaming) setWarning(NetworkWarning.DISCOVERY_FAILED, !found)
            onDone(found, sources)
        }
    }

    private fun setWarning(w: NetworkWarning, on: Boolean) {
        val cur = _warnings.value
        _warnings.value = if (on) cur + w else cur - w
    }

    private fun addWarning(w: NetworkWarning) {
        setWarning(w, true)
        scope.launch { delay(8000); setWarning(w, false) }
    }

    private companion object { const val TAG = "StreamController" }
}
