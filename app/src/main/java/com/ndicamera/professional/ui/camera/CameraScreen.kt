package com.ndicamera.professional.ui.camera

import android.os.Build
import android.os.SystemClock
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndicamera.professional.BuildConfig
import com.ndicamera.professional.camera.ModeSelection
import com.ndicamera.professional.camera.OrientationMath
import com.ndicamera.professional.domain.model.AntibandingMode
import com.ndicamera.professional.domain.model.CameraCapabilities
import com.ndicamera.professional.domain.model.CameraControlState
import com.ndicamera.professional.domain.model.CameraState
import com.ndicamera.professional.domain.model.CaptureTelemetry
import com.ndicamera.professional.domain.model.FocusMode
import com.ndicamera.professional.domain.model.LensFacing
import com.ndicamera.professional.domain.model.NdiStatus
import com.ndicamera.professional.domain.model.Transport
import com.ndicamera.professional.domain.model.WhiteBalanceMode
import com.ndicamera.professional.ui.components.AnalysisMask
import com.ndicamera.professional.ui.components.AudioMeter
import com.ndicamera.professional.ui.components.CineChip
import com.ndicamera.professional.ui.components.ControlButton
import com.ndicamera.professional.ui.components.GuideOverlay
import com.ndicamera.professional.ui.components.Histogram
import com.ndicamera.professional.ui.components.InfoCell
import com.ndicamera.professional.ui.components.StatusPill
import com.ndicamera.professional.ui.theme.Cine
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Panel { LENS, FOCUS, ISO, SHUTTER, WB, ZOOM, EV, TOOLS, STAB }

@Composable
fun CameraScreen(vm: CameraViewModel, onOpenSettings: () -> Unit) {
    val camState by vm.cameraState.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val tel by vm.telemetry.collectAsStateWithLifecycle()
    val ctl by vm.controls.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf<Panel?>(null) }

    DisposableEffect(Unit) {
        vm.onVisible(true)
        onDispose { vm.onVisible(false) }
    }

    Column(Modifier.fillMaxSize().background(Cine.Black)) {
        TopStatusBar(vm, caps, onOpenSettings)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            InfoColumn(camState, caps, tel, ctl)
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                PreviewArea(vm, caps)
                if (panel != null) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        ControlPanel(panel!!, vm, caps, tel, ctl, settings.overlays)
                    }
                }
                CameraStateBanner(camState, Modifier.align(Alignment.TopCenter))
            }
            NdiPanel(vm)
        }
        BottomBar(caps, tel, ctl, panel, onSelect = { panel = if (panel == it) null else it }, onTorch = { vm.setTorch(!ctl.torch) })
    }
}

@Composable
private fun TopStatusBar(vm: CameraViewModel, caps: CameraCapabilities?, onOpenSettings: () -> Unit) {
    val ndi by vm.ndiStatus.collectAsStateWithLifecycle()
    val net by vm.network.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().height(34.dp).background(Cine.Panel).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("NDI", color = Cine.Accent, fontWeight = FontWeight.Black, fontSize = 13.sp, fontFamily = Cine.Mono)
        Text(" CAM PRO", color = Cine.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = Cine.Mono)
        Spacer(Modifier.width(14.dp))
        Text("${Build.MODEL} · ${caps?.lens?.label ?: "--"}", style = Cine.label.copy(fontSize = 10.sp, color = Cine.Text), maxLines = 1,
            modifier = Modifier.weight(1f))
        val (txt, col) = ndiPill(ndi)
        StatusPill(txt, col)
        Spacer(Modifier.width(10.dp))
        val netText = when (net.transport) {
            Transport.WIFI -> "WIFI ${net.wifiRssiDbm?.let { "${it}dBm" } ?: ""}"
            Transport.ETHERNET -> "ETH"
            Transport.NONE -> "NO NET"
            else -> net.transport.name
        }
        StatusPill(netText.trim(), if (net.connected) Cine.Ok else Cine.Rec)
        Spacer(Modifier.width(10.dp))
        Text("${health.batteryPercent ?: "--"}%${if (health.charging) "+" else ""}", style = Cine.value.copy(fontSize = 11.sp))
        Spacer(Modifier.width(10.dp))
        Text(health.batteryTempC?.let { String.format(Locale.US, "%.0f°C", it) } ?: "--°C",
            style = Cine.value.copy(fontSize = 11.sp, color = if (health.throttling) Cine.Rec else Cine.Text))
        if (health.thermalStatus > 0) {
            Spacer(Modifier.width(6.dp))
            StatusPill(health.thermalLabel, if (health.throttling) Cine.Rec else Cine.Warn)
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Cine.Text)
        }
    }
}

fun ndiPill(s: NdiStatus): Pair<String, Color> = when (s) {
    is NdiStatus.SdkNotInstalled -> "NDI SDK MISSING" to Cine.Rec
    is NdiStatus.InitFailed -> "NDI INIT FAILED" to Cine.Rec
    is NdiStatus.LibraryReady -> "NDI READY" to Cine.Dim
    is NdiStatus.SenderInitialized -> "NDI SENDER UP" to Cine.Warn
    is NdiStatus.SourceActive -> when {
        s.onProgram -> "ON AIR" to Cine.Rec
        s.onPreview -> "PREVIEW" to Cine.Ok
        else -> "LIVE · ${s.connections} RX" to Cine.Accent
    }
    NdiStatus.TransmissionStopped -> "NDI STOPPED" to Cine.Dim
    is NdiStatus.SenderError -> "NDI ERROR" to Cine.Rec
}

@Composable
private fun InfoColumn(state: CameraState, caps: CameraCapabilities?, t: CaptureTelemetry, c: CameraControlState) {
    val mode = (state as? CameraState.Running)?.mode
    Column(
        Modifier.width(96.dp).fillMaxHeight().background(Cine.Panel).padding(horizontal = 8.dp, vertical = 4.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        InfoCell("RES", mode?.resolution?.label ?: "--")
        InfoCell("FPS", mode?.fps?.toString() ?: "--")
        InfoCell("ISO", (t.iso ?: c.iso).toString() + if (c.autoExposure) " A" else "",
            valueColor = if (c.autoExposure) Cine.Text else Cine.Accent)
        val sh = t.exposureTimeNs
        InfoCell("SHUTTER", (sh?.let { ModeSelection.shutterLabel(it) } ?: "--") +
            (if (sh != null && mode != null) " ${ModeSelection.shutterAngle(sh, mode.fps)}°" else ""),
            valueColor = if (c.autoExposure) Cine.Text else Cine.Accent)
        InfoCell("WB", when (c.whiteBalanceMode) {
            WhiteBalanceMode.KELVIN -> "${c.kelvin}K"
            WhiteBalanceMode.PRESET -> if (c.awbPreset.name == "AUTO") "A ${t.colorTemperatureEstimate?.let { "~${it}K" } ?: ""}" else c.awbPreset.label
        } + if (c.awbLock) " L" else "")
        InfoCell("FOCUS", t.focusDistanceDiopters?.let { d -> if (d <= 0.01f) "∞" else String.format(Locale.US, "%.2fm", 1f / d) } ?: "--",
            valueColor = if (c.focusMode == FocusMode.MANUAL) Cine.Accent else Cine.Text)
        InfoCell("ZOOM", String.format(Locale.US, "%.1fx", t.zoom ?: c.zoom))
        InfoCell("EV", if (caps != null && caps.exposureCompensationStep > 0f)
            String.format(Locale.US, "%+.1f", c.exposureCompensation * caps.exposureCompensationStep) else "--")
        InfoCell("AE/AF", "${aeLabel(t.aeState, c)}/${afLabel(t.afState)}")
        InfoCell("HW", caps?.hardwareLevel ?: "--")
    }
}

private fun aeLabel(s: Int?, c: CameraControlState) = when {
    !c.autoExposure -> "MAN"
    c.aeLock -> "LCK"
    s == 2 -> "OK"
    s == 1 -> "SRCH"
    s == 4 -> "FLSH"
    else -> "--"
}

private fun afLabel(s: Int?) = when (s) {
    0 -> "IDLE"; 1, 3 -> "SCAN"; 2, 4 -> "LCK"; 5 -> "FAIL"; 6 -> "MISS"; else -> "--"
}

@Composable
private fun PreviewArea(vm: CameraViewModel, caps: CameraCapabilities?) {
    val previewSize by vm.previewSize.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val tel by vm.telemetry.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val displayRotation = remember(config) {
        val r = displayRotationOf(context)
        when (r) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
    }
    val front = caps?.lens?.facing == LensFacing.FRONT
    val rotation = OrientationMath.previewRotation(caps?.sensorOrientation ?: 90, displayRotation, front)
    val swap = rotation == 90 || rotation == 270
    val aspect = if (swap) previewSize.height.toFloat() / previewSize.width else previewSize.width.toFloat() / previewSize.height
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(focusAt) { if (focusAt != 0L) { delay(2500); focusPoint = null } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(aspect).background(Color.Black)) {
            androidx.compose.runtime.key(previewSize) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.setFixedSize(previewSize.width, previewSize.height)
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(h: SurfaceHolder) {}
                                override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hgt: Int) {
                                    if (w == previewSize.width && hgt == previewSize.height) vm.c.cameraEngine.setPreviewSurface(h.surface)
                                }
                                override fun surfaceDestroyed(h: SurfaceHolder) { vm.c.cameraEngine.detachPreviewBlocking(h.surface) }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (settings.overlays.zebra || settings.overlays.peaking) AnalysisMask(overlay, rotation, front)
            val afColor = when (tel.afState) { 2, 4 -> Cine.Ok; 5, 6 -> Cine.Rec; else -> Color.White }
            GuideOverlay(settings.overlays, focusPoint, afColor,
                Modifier.pointerInput(rotation, front, caps) {
                    detectTapGestures(
                        onTap = { o ->
                            if (caps?.autofocusAvailable == true) {
                                val (sx, sy) = OrientationMath.displayToSensor(o.x / size.width, o.y / size.height, rotation, front)
                                vm.focusAt(sx, sy, lock = false)
                                focusPoint = o; focusAt = SystemClock.elapsedRealtime()
                            }
                        },
                        onLongPress = { o ->
                            if (caps?.autofocusAvailable == true) {
                                val (sx, sy) = OrientationMath.displayToSensor(o.x / size.width, o.y / size.height, rotation, front)
                                vm.focusAt(sx, sy, lock = true)
                                focusPoint = o; focusAt = SystemClock.elapsedRealtime()
                            }
                        },
                    )
                })
            if (settings.overlays.histogram) {
                Histogram(overlay, Modifier.align(Alignment.TopEnd).padding(6.dp).size(width = 128.dp, height = 56.dp))
            }
            if (BuildConfig.NDI_VALIDATION_BUILD) {
                Text("CI VALIDATION BUILD · NO NDI SDK", color = Cine.Rec, fontFamily = Cine.Mono, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.6f)).padding(4.dp))
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun displayRotationOf(context: android.content.Context): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display?.rotation ?: Surface.ROTATION_0
    else context.getSystemService(android.view.WindowManager::class.java).defaultDisplay.rotation

@Composable
private fun CameraStateBanner(state: CameraState, modifier: Modifier) {
    val text = when (state) {
        is CameraState.Error -> state.message
        is CameraState.Disconnected -> state.reason
        CameraState.PermissionRequired -> "Camera permission required"
        CameraState.Opening -> "Opening camera…"
        else -> null
    } ?: return
    Text(text, color = Color.White, fontSize = 12.sp,
        modifier = modifier.padding(8.dp).background(Cine.Rec.copy(alpha = 0.8f), RoundedCornerShape(3.dp)).padding(8.dp))
}

@Composable
private fun NdiPanel(vm: CameraViewModel) {
    val ndi by vm.ndiStatus.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val warnings by vm.warnings.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val levels by vm.audioLevels.collectAsStateWithLifecycle()
    val audioState by vm.audioState.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(stats.running) { while (stats.running) { now = SystemClock.elapsedRealtime(); delay(500) } }
    val live = ndi is NdiStatus.SourceActive || ndi is NdiStatus.SenderInitialized
    val canStart = ndi is NdiStatus.LibraryReady || ndi is NdiStatus.TransmissionStopped || ndi is NdiStatus.SenderError

    Row(Modifier.width(212.dp).fillMaxHeight().background(Cine.Panel)) {
        Column(Modifier.weight(1f).padding(8.dp).verticalScroll(rememberScrollState())) {
            Text("NDI OUTPUT", style = Cine.label.copy(color = Cine.Accent))
            val name = when (val s = ndi) {
                is NdiStatus.SourceActive -> s.sourceName
                is NdiStatus.SenderInitialized -> s.sourceName
                else -> settings.sourceName
            }
            Text(name, style = Cine.value.copy(fontSize = 11.sp), maxLines = 2)
            val (pill, col) = ndiPill(ndi)
            StatusPill(pill, col, Modifier.padding(vertical = 4.dp))
            val detail = when (val s = ndi) {
                is NdiStatus.SdkNotInstalled -> s.detail
                is NdiStatus.InitFailed -> s.detail
                is NdiStatus.SenderError -> s.detail
                else -> null
            }
            detail?.let { Text(it, color = Cine.Rec, fontSize = 10.sp) }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(42.dp)
                    .background(if (live) Cine.Rec else if (canStart) Cine.Accent else Cine.Faint, RoundedCornerShape(3.dp))
                    .clickable(enabled = live || canStart) { if (live) vm.stop() else vm.start() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (live) "STOP STREAM" else "START STREAM", color = Color.Black, fontWeight = FontWeight.Black,
                    fontFamily = Cine.Mono, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            val dur = if (stats.running) (now - stats.startedAtElapsedMs) / 1000 else 0
            InfoCell("DURATION", String.format(Locale.US, "%02d:%02d:%02d", dur / 3600, (dur / 60) % 60, dur % 60))
            Row {
                InfoCell("FPS", String.format(Locale.US, "%.1f", stats.currentFps), Modifier.weight(1f))
                InfoCell("RX", (ndi as? NdiStatus.SourceActive)?.connections?.toString() ?: "0", Modifier.weight(1f))
            }
            Row {
                InfoCell("DROPPED", stats.totalDropped.toString(), Modifier.weight(1f),
                    valueColor = if (stats.totalDropped > 0) Cine.Warn else Cine.Text)
                InfoCell("TX", String.format(Locale.US, "%.0fM", stats.txBitsPerSecond / 1e6), Modifier.weight(1f))
            }
            InfoCell("CONVERT", String.format(Locale.US, "%.1f ms", stats.lastConvertMs))
            warnings.forEach { w -> Text("⚠ ${w.message}", color = Cine.Warn, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
        // Audio meter + mute
        Column(Modifier.width(34.dp).fillMaxHeight().padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("dB", style = Cine.label)
            AudioMeter(levels, Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp))
            val muted = vm.c.audioEngine.currentConfig().muted
            var m by remember { mutableStateOf(muted) }
            Box(Modifier.fillMaxWidth().height(24.dp)
                .border(1.dp, if (m) Cine.Rec else Cine.Line, RoundedCornerShape(2.dp))
                .clickable(enabled = settings.audioEnabled) { m = !m; vm.setMuted(m) },
                contentAlignment = Alignment.Center) {
                Text(if (m) "MUTE" else "MIC", fontSize = 8.sp, color = if (m) Cine.Rec else if (audioState is com.ndicamera.professional.domain.model.AudioState.Capturing) Cine.Ok else Cine.Dim,
                    fontFamily = Cine.Mono)
            }
        }
    }
}

@Composable
private fun BottomBar(
    caps: CameraCapabilities?, t: CaptureTelemetry, c: CameraControlState, active: Panel?,
    onSelect: (Panel) -> Unit, onTorch: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(52.dp).background(Cine.Panel).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically) {
        ControlButton("LENS", caps?.lens?.label?.substringBefore(" (") ?: "--", active == Panel.LENS, caps != null) { onSelect(Panel.LENS) }
        ControlButton("FOCUS", when (c.focusMode) { FocusMode.CONTINUOUS -> "AF-C"; FocusMode.AUTO_LOCKED -> "AF-L"; FocusMode.MANUAL -> "MF" },
            active == Panel.FOCUS, caps != null && (caps.autofocusAvailable || caps.manualFocusAvailable)) { onSelect(Panel.FOCUS) }
        ControlButton("ISO", if (c.autoExposure) "AUTO" else c.iso.toString(), active == Panel.ISO, caps?.manualIsoAvailable == true) { onSelect(Panel.ISO) }
        ControlButton("SHUTTER", if (c.autoExposure) "AUTO" else ModeSelection.shutterLabel(c.exposureTimeNs), active == Panel.SHUTTER,
            caps?.manualShutterAvailable == true) { onSelect(Panel.SHUTTER) }
        ControlButton("WB", if (c.whiteBalanceMode == WhiteBalanceMode.KELVIN) "${c.kelvin}K" else c.awbPreset.label, active == Panel.WB,
            caps != null && (caps.awbPresets.size > 1 || caps.manualPostProcessing)) { onSelect(Panel.WB) }
        ControlButton("ZOOM", String.format(Locale.US, "%.1fx", c.zoom), active == Panel.ZOOM,
            caps != null && caps.zoomRange.upper > caps.zoomRange.lower) { onSelect(Panel.ZOOM) }
        ControlButton("EV", String.format(Locale.US, "%+d", c.exposureCompensation), active == Panel.EV,
            caps?.exposureCompensationRange != null && c.autoExposure) { onSelect(Panel.EV) }
        ControlButton("TOOLS", "VIEW", active == Panel.TOOLS, true) { onSelect(Panel.TOOLS) }
        ControlButton("STAB", listOfNotNull(if (c.ois && caps?.oisAvailable == true) "OIS" else null,
            if (c.eis && caps?.eisAvailable == true) "EIS" else null).joinToString("+").ifEmpty { "OFF" },
            active == Panel.STAB, caps != null) { onSelect(Panel.STAB) }
        ControlButton("TORCH", if (c.torch) "ON" else "OFF", c.torch, caps?.flashAvailable == true) { onTorch() }
    }
}

@Composable
private fun ControlPanel(
    panel: Panel, vm: CameraViewModel, caps: CameraCapabilities?, t: CaptureTelemetry, c: CameraControlState,
    overlays: com.ndicamera.professional.settings.OverlaySettings,
) {
    val lenses by vm.lenses.collectAsStateWithLifecycle()
    if (caps == null) return
    Column(Modifier.fillMaxWidth().background(Cine.Black.copy(alpha = 0.85f)).padding(8.dp)) {
        val chipRow: @Composable (content: @Composable () -> Unit) -> Unit = { content ->
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { content() }
        }
        when (panel) {
            Panel.LENS -> {
                chipRow { lenses.forEach { l -> CineChip(l.label, caps.lens?.key == l.key) { vm.selectLens(l) } } }
                chipRow {
                    caps.modes?.forEach { m ->
                        val cur = (vm.cameraState.value as? CameraState.Running)?.mode
                        CineChip(m.label, cur == m) { vm.selectMode(m) }
                    }
                }
            }
            Panel.FOCUS -> {
                chipRow {
                    CineChip("AF-C", c.focusMode == FocusMode.CONTINUOUS, caps.autofocusAvailable == true) { vm.setFocusMode(FocusMode.CONTINUOUS) }
                    CineChip("AF LOCK", c.focusMode == FocusMode.AUTO_LOCKED, caps.autofocusAvailable == true) { vm.setFocusMode(FocusMode.AUTO_LOCKED) }
                    CineChip("MF", c.focusMode == FocusMode.MANUAL, caps.manualFocusAvailable == true) { vm.setFocusMode(FocusMode.MANUAL) }
                    Text("  Tap preview: focus · Long-press: focus lock", style = Cine.label)
                }
                if (caps.manualFocusAvailable == true) {
                    LabeledSlider("FOCUS ${if (c.focusDistanceDiopters <= 0.01f) "∞" else String.format(Locale.US, "%.2fm", 1f / c.focusDistanceDiopters)}",
                        c.focusDistanceDiopters, 0f..caps.minFocusDistanceDiopters, enabled = c.focusMode == FocusMode.MANUAL) { vm.setFocusDistance(it) }
                }
            }
            Panel.ISO -> {
                chipRow {
                    CineChip("AUTO", c.autoExposure) { vm.setManualExposure(!c.autoExposure) }
                    val r = caps.isoRange
                    if (r != null) ModeSelection.isoStops(r.lower, r.upper).forEach { iso ->
                        CineChip(iso.toString(), !c.autoExposure && c.iso == iso, caps.manualIsoAvailable) { vm.setManualExposure(true); vm.setIso(iso) }
                    }
                }
            }
            Panel.SHUTTER -> {
                val fps = (vm.cameraState.value as? CameraState.Running)?.mode?.fps ?: 30
                chipRow {
                    CineChip("AUTO", c.autoExposure) { vm.setManualExposure(!c.autoExposure) }
                    val r = caps.exposureTimeRangeNs
                    if (r != null) ModeSelection.shutterStops(r.lower, minOf(r.upper, 1_000_000_000L / fps)).forEach { ns ->
                        CineChip("${ModeSelection.shutterLabel(ns)} ${ModeSelection.shutterAngle(ns, fps)}°", !c.autoExposure && c.exposureTimeNs == ns,
                            caps.manualShutterAvailable) { vm.setManualExposure(true); vm.setShutter(ns) }
                    }
                }
            }
            Panel.WB -> {
                chipRow {
                    caps.awbPresets?.forEach { p ->
                        CineChip(p.label, c.whiteBalanceMode == WhiteBalanceMode.PRESET && c.awbPreset == p) { vm.setAwbPreset(p) }
                    }
                    CineChip("AWB LOCK", c.awbLock, caps.awbLockAvailable == true && c.whiteBalanceMode == WhiteBalanceMode.PRESET) { vm.setAwbLock(!c.awbLock) }
                }
                if (caps.manualWhiteBalanceKelvinAvailable == true) {
                    LabeledSlider("KELVIN ~${c.kelvin}K (approx.)", c.kelvin.toFloat(), 2000f..10000f) { vm.setKelvin((it / 50).toInt() * 50) }
                    LabeledSlider(String.format(Locale.US, "TINT %+.2f", c.tint), c.tint, -1f..1f,
                        enabled = c.whiteBalanceMode == WhiteBalanceMode.KELVIN) { vm.setTint(it) }
                } else {
                    Text("Manual Kelvin needs MANUAL_POST_PROCESSING, which this camera does not report.", style = Cine.label)
                }
            }
            Panel.ZOOM -> {
                val r = caps.zoomRange
                if (r != null) {
                    chipRow {
                        listOf(r.lower, 1f, 2f, 3f, 5f).filter { it in r.lower..r.upper }.distinct().forEach { z ->
                            CineChip(String.format(Locale.US, "%.1fx", z), kotlin.math.abs(c.zoom - z) < 0.05f) { vm.setZoom(z) }
                        }
                    }
                    LabeledSlider(String.format(Locale.US, "ZOOM %.2fx", c.zoom), c.zoom, r.lower..r.upper) { vm.setZoom(it) }
                }
            }
            Panel.EV -> {
                val r = caps.exposureCompensationRange
                if (r != null) {
                    LabeledSlider(String.format(Locale.US, "EV %+.1f", c.exposureCompensation * caps.exposureCompensationStep),
                        c.exposureCompensation.toFloat(), r.lower.toFloat()..r.upper.toFloat(), enabled = c.autoExposure,
                        steps = (r.upper - r.lower - 1).coerceAtLeast(0)) { vm.setEv(Math.round(it)) }
                    chipRow { CineChip("AE LOCK", c.aeLock, caps.aeLockAvailable && c.autoExposure) { vm.setAeLock(!c.aeLock) } }
                }
            }
            Panel.TOOLS -> chipRow {
                CineChip("ZEBRA ${overlays.zebraLevel}%", overlays.zebra) { vm.updateOverlays { it.copy(zebra = !it.zebra) } }
                CineChip("PEAKING", overlays.peaking) { vm.updateOverlays { it.copy(peaking = !it.peaking) } }
                CineChip("THIRDS", overlays.thirds) { vm.updateOverlays { it.copy(thirds = !it.thirds) } }
                CineChip("CROSS", overlays.crosshair) { vm.updateOverlays { it.copy(crosshair = !it.crosshair) } }
                CineChip("SAFE", overlays.safeAreas) { vm.updateOverlays { it.copy(safeAreas = !it.safeAreas) } }
                CineChip("HISTO", overlays.histogram) { vm.updateOverlays { it.copy(histogram = !it.histogram) } }
            }
            Panel.STAB -> chipRow {
                CineChip("OIS", c.ois, caps.oisAvailable == true) { vm.setOis(!c.ois) }
                CineChip("EIS", c.eis, caps.eisAvailable == true) { vm.setEis(!c.eis) }
                Text("  ANTI-BANDING ", style = Cine.label)
                AntibandingMode.entries.forEach { m ->
                    CineChip(m.label, c.antibanding == m, caps.antibandingModes?.contains(m) == true) { vm.setAntibanding(m) }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean = true, steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Cine.value.copy(fontSize = 11.sp), modifier = Modifier.width(170.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range,
            enabled = enabled, steps = steps, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Cine.Accent, activeTrackColor = Cine.Accent, inactiveTrackColor = Cine.Line),
        )
    }
}
