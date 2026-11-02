package com.ndicamera.professional.ui.diagnostics

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndicamera.professional.di.AppContainer
import com.ndicamera.professional.domain.model.CameraState
import com.ndicamera.professional.domain.model.NdiStatus
import com.ndicamera.professional.ui.camera.ndiPill
import com.ndicamera.professional.ui.components.KeyValue
import com.ndicamera.professional.ui.components.SectionTitle
import com.ndicamera.professional.ui.components.SettingsPage
import com.ndicamera.professional.ui.components.StatusPill
import com.ndicamera.professional.ui.theme.Cine
import java.util.Locale

@Composable
fun NetworkDiagnosticsScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage("Network diagnostics", onBack) {
    val n by c.networkMonitor.status.collectAsStateWithLifecycle()
    val tx by c.networkMonitor.txBitsPerSecond.collectAsStateWithLifecycle()
    val ndi by c.streamController.ndiStatus.collectAsStateWithLifecycle()
    val stats by c.streamController.stats.collectAsStateWithLifecycle()
    val warnings by c.streamController.warnings.collectAsStateWithLifecycle()
    var test by remember { mutableStateOf<String?>(null) }

    SectionTitle("Connection")
    KeyValue("State", if (n.connected) "CONNECTED" else "DISCONNECTED", warn = !n.connected)
    KeyValue("Transport", n.transport.name)
    KeyValue("Interface", n.interfaceName ?: "--")
    KeyValue("IPv4", n.ipv4Addresses.joinToString().ifEmpty { "--" })
    KeyValue("IPv6", n.ipv6Addresses.firstOrNull() ?: "--")
    KeyValue("Internet validated", if (n.validated) "yes" else "no (fine for NDI on a local LAN)")
    KeyValue("Interface changes", n.interfaceChangeCount.toString())
    SectionTitle("Wi-Fi link")
    KeyValue("RSSI", n.wifiRssiDbm?.let { "$it dBm" } ?: "--", warn = (n.wifiRssiDbm ?: 0) < -72)
    KeyValue("Band", n.wifiFrequencyMhz?.let { if (it > 5900) "6 GHz ($it MHz)" else if (it > 4900) "5 GHz ($it MHz)" else "2.4 GHz ($it MHz)" } ?: "--")
    KeyValue("Link rate RX / TX", "${n.wifiLinkSpeedMbps ?: "--"} / ${n.wifiTxLinkSpeedMbps ?: "--"} Mbps")
    Text("Link rate is the negotiated PHY rate, not available bandwidth. Real throughput is typically 40–60% of it.",
        color = Cine.Dim, fontSize = 12.sp)
    KeyValue("Est. up/down (Android)", "${n.estimatedUpstreamKbps?.let { it / 1000 } ?: "--"} / ${n.estimatedDownstreamKbps?.let { it / 1000 } ?: "--"} Mbps")
    Text("Android's estimate is coarse and not measured on this LAN.", color = Cine.Dim, fontSize = 12.sp)
    SectionTitle("Transmission (measured)")
    KeyValue("App TX rate", String.format(Locale.US, "%.1f Mbit/s", tx / 1e6))
    val (pill, col) = ndiPill(ndi)
    StatusPill(pill, col)
    KeyValue("Receivers connected", (ndi as? NdiStatus.SourceActive)?.connections?.toString() ?: "0")
    KeyValue("Frames sent", stats.videoFramesSent.toString())
    KeyValue("Dropped (pipeline / camera)", "${stats.framesDroppedPipeline} / ${stats.framesDroppedCamera}", warn = stats.totalDropped > 0)
    KeyValue("Send errors", stats.sendErrors.toString(), warn = stats.sendErrors > 0)
    KeyValue("Sender restarts", stats.senderRestarts.toString())
    KeyValue("Multicast lock", if (c.networkMonitor.multicastLockHeld) "held" else "not held")
    SectionTitle("Warnings")
    if (warnings.isEmpty()) Text("None", color = Cine.Ok, fontSize = 13.sp)
    warnings.forEach { Text("⚠ ${it.message}", color = Cine.Warn, fontSize = 13.sp) }
    SectionTitle("Discovery")
    Button(onClick = {
        test = "Searching…"
        c.streamController.runDiscoveryTest { found, s ->
            test = if (c.streamController.isStreaming) (if (found) "PASS: own source advertised (${s.size} found)" else "FAIL: own source not seen locally")
            else "${s.size} NDI source(s) visible: ${s.joinToString()}"
        }
    }, colors = ButtonDefaults.buttonColors(containerColor = Cine.PanelRaised)) { Text("RUN NDI DISCOVERY", color = Cine.Text) }
    test?.let { Text(it, color = Cine.Text, fontSize = 13.sp) }
    SectionTitle("If receivers cannot see this camera")
    listOf(
        "Phone and receiver must be on the same subnet (no guest network / AP client isolation).",
        "mDNS (UDP 5353 multicast) must pass; some routers filter multicast on Wi-Fi.",
        "NDI uses TCP 5959-5960 and TCP/UDP from 5961 upward; allow them in the receiver PC firewall.",
        "On the receiver, NDI Access Manager can add this phone's IP (${n.ipv4Addresses.firstOrNull() ?: "?"}) as an extra IP if mDNS is blocked.",
        "Prefer 5 GHz / 6 GHz Wi-Fi or USB Ethernet for 1080p and above.",
    ).forEach { Text("• $it", color = Cine.Dim, fontSize = 12.sp) }
}

@Composable
fun PerformanceDiagnosticsScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage("Performance", onBack) {
    val stats by c.streamController.stats.collectAsStateWithLifecycle()
    val h by c.systemMonitor.health.collectAsStateWithLifecycle()
    val caps by c.cameraEngine.capabilities.collectAsStateWithLifecycle()
    val cam by c.cameraEngine.state.collectAsStateWithLifecycle()
    SectionTitle("Frame delivery")
    KeyValue("Streaming", if (stats.running) "yes" else "no")
    KeyValue("Mode", (cam as? CameraState.Running)?.mode?.label ?: cam::class.simpleName.orEmpty())
    KeyValue("Current FPS", String.format(Locale.US, "%.2f", stats.currentFps))
    KeyValue("Video frames sent", stats.videoFramesSent.toString())
    KeyValue("Audio buffers sent", stats.audioBuffersSent.toString())
    KeyValue("Dropped: NDI thread busy", stats.framesDroppedPipeline.toString(), warn = stats.framesDroppedPipeline > 0)
    KeyValue("Dropped: camera gaps", stats.framesDroppedCamera.toString(), warn = stats.framesDroppedCamera > 0)
    KeyValue("YUV→NV12 + send", String.format(Locale.US, "%.2f ms", stats.lastConvertMs))
    SectionTitle("Device health")
    KeyValue("Thermal", h.thermalLabel + (h.thermalHeadroom?.let { String.format(Locale.US, " · headroom %.2f", it) } ?: ""), warn = h.throttling)
    KeyValue("Battery", "${h.batteryPercent ?: "--"}% ${if (h.charging) "(charging)" else ""} · ${h.batteryTempC ?: "--"} °C")
    KeyValue("Java heap", "${h.javaHeapUsedMb} MB")
    KeyValue("Native heap", "${h.nativeHeapUsedMb} MB")
    if (h.throttling) Text("Device is thermally throttling: consider lower resolution/fps, removing the case, or active cooling.",
        color = Cine.Rec, fontSize = 12.sp)
    SectionTitle("Camera capabilities")
    val cp = caps
    if (cp == null) Text("Camera not open", color = Cine.Dim) else {
        KeyValue("Lens", cp.lens.label)
        KeyValue("Hardware level", cp.hardwareLevel)
        KeyValue("Sensor orientation", "${cp.sensorOrientation}°")
        KeyValue("MANUAL_SENSOR", cp.manualSensor.toString())
        KeyValue("MANUAL_POST_PROCESSING", cp.manualPostProcessing.toString())
        KeyValue("ISO range", cp.isoRange?.let { "${it.lower}–${it.upper}" } ?: "n/a")
        KeyValue("Exposure range", cp.exposureTimeRangeNs?.let { "${it.lower / 1000} µs – ${it.upper / 1_000_000} ms" } ?: "n/a")
        KeyValue("Min focus", if (cp.minFocusDistanceDiopters > 0) "${cp.minFocusDistanceDiopters} D" else "fixed focus")
        KeyValue("Zoom", "${cp.zoomRange.lower}–${cp.zoomRange.upper}x ${if (cp.usesZoomRatio) "(zoom ratio)" else "(crop)"}")
        KeyValue("OIS / EIS", "${cp.oisAvailable} / ${cp.eisAvailable}")
        KeyValue("Torch", cp.flashAvailable.toString())
        KeyValue("AF / AE regions", "${cp.maxAfRegions} / ${cp.maxAeRegions}")
        KeyValue("Modes", cp.modes.joinToString { it.label })
    }
}
