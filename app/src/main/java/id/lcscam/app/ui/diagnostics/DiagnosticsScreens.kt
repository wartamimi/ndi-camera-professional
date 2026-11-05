package id.lcscam.app.ui.diagnostics

import id.lcscam.app.i18n.tr
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
import id.lcscam.app.di.AppContainer
import id.lcscam.app.domain.model.CameraState
import id.lcscam.app.domain.model.NdiStatus
import id.lcscam.app.ui.camera.ndiPill
import id.lcscam.app.ui.components.KeyValue
import id.lcscam.app.ui.components.SectionTitle
import id.lcscam.app.ui.components.SettingsPage
import id.lcscam.app.ui.components.StatusPill
import id.lcscam.app.ui.theme.Cine
import java.util.Locale

@Composable
fun NetworkDiagnosticsScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage(tr("Network diagnostics", "Diagnostik jaringan"), onBack) {
    val n by c.networkMonitor.status.collectAsStateWithLifecycle()
    val tx by c.networkMonitor.txBitsPerSecond.collectAsStateWithLifecycle()
    val ndi by c.streamController.ndiStatus.collectAsStateWithLifecycle()
    val stats by c.streamController.stats.collectAsStateWithLifecycle()
    val warnings by c.streamController.warnings.collectAsStateWithLifecycle()
    var test by remember { mutableStateOf<String?>(null) }

    SectionTitle(tr("Connection", "Koneksi"))
    KeyValue("State", if (n.connected) tr("CONNECTED", "TERHUBUNG") else tr("DISCONNECTED", "TERPUTUS"), warn = !n.connected)
    KeyValue("Transport", n.transport.name)
    KeyValue("Interface", n.interfaceName ?: "--")
    KeyValue("IPv4", n.ipv4Addresses.joinToString().ifEmpty { "--" })
    KeyValue("IPv6", n.ipv6Addresses.firstOrNull() ?: "--")
    KeyValue(tr("Internet validated", "Internet tervalidasi"), if (n.validated) tr("yes", "ya") else tr("no (fine for NDI on a local LAN)", "tidak (tidak masalah untuk NDI di LAN lokal)"))
    KeyValue(tr("Interface changes", "Perubahan antarmuka"), n.interfaceChangeCount.toString())
    SectionTitle("Wi-Fi link")
    KeyValue("RSSI", n.wifiRssiDbm?.let { "$it dBm" } ?: "--", warn = (n.wifiRssiDbm ?: 0) < -72)
    KeyValue("Band", n.wifiFrequencyMhz?.let { if (it > 5900) "6 GHz ($it MHz)" else if (it > 4900) "5 GHz ($it MHz)" else "2.4 GHz ($it MHz)" } ?: "--")
    KeyValue("Link rate RX / TX", "${n.wifiLinkSpeedMbps ?: "--"} / ${n.wifiTxLinkSpeedMbps ?: "--"} Mbps")
    Text(tr("Link rate is the negotiated PHY rate, not available bandwidth. Real throughput is typically 40–60% of it.",
        "Link rate adalah laju PHY hasil negosiasi, bukan bandwidth tersedia. Throughput nyata biasanya 40–60%-nya."),
        color = Cine.Dim, fontSize = 12.sp)
    KeyValue("Est. up/down (Android)", "${n.estimatedUpstreamKbps?.let { it / 1000 } ?: "--"} / ${n.estimatedDownstreamKbps?.let { it / 1000 } ?: "--"} Mbps")
    Text(tr("Android's estimate is coarse and not measured on this LAN.", "Perkiraan Android kasar dan tidak diukur di LAN ini."), color = Cine.Dim, fontSize = 12.sp)
    SectionTitle(tr("Transmission (measured)", "Transmisi (terukur)"))
    KeyValue(tr("App TX rate", "Laju kirim aplikasi"), String.format(Locale.US, "%.1f Mbit/s", tx / 1e6))
    val (pill, col) = ndiPill(ndi)
    StatusPill(pill, col)
    KeyValue(tr("Receivers connected", "Receiver terhubung"), (ndi as? NdiStatus.SourceActive)?.connections?.toString() ?: "0")
    KeyValue(tr("Frames sent", "Frame terkirim"), stats.videoFramesSent.toString())
    KeyValue(tr("Dropped (pipeline / camera)", "Terbuang (pipeline / kamera)"), "${stats.framesDroppedPipeline} / ${stats.framesDroppedCamera}", warn = stats.totalDropped > 0)
    KeyValue(tr("Send errors", "Gagal kirim"), stats.sendErrors.toString(), warn = stats.sendErrors > 0)
    KeyValue(tr("Sender restarts", "Restart sender"), stats.senderRestarts.toString())
    KeyValue("Multicast lock", if (c.networkMonitor.multicastLockHeld) tr("held", "aktif") else tr("not held", "tidak aktif"))
    SectionTitle(tr("Warnings", "Peringatan"))
    if (warnings.isEmpty()) Text(tr("None", "Tidak ada"), color = Cine.Ok, fontSize = 13.sp)
    warnings.forEach { Text("⚠ ${it.message}", color = Cine.Warn, fontSize = 13.sp) }
    SectionTitle("Discovery")
    Button(onClick = {
        test = tr("Searching…", "Mencari…")
        c.streamController.runDiscoveryTest { found, s ->
            test = if (c.streamController.isStreaming) (if (found) tr("PASS: own source advertised (${s.size} found)", "LULUS: source sendiri diumumkan (${s.size} ditemukan)") else tr("FAIL: own source not seen locally", "GAGAL: source sendiri tidak terlihat secara lokal"))
            else tr("${s.size} NDI source(s) visible: ${s.joinToString()}", "${s.size} source NDI terlihat: ${s.joinToString()}")
        }
    }, colors = ButtonDefaults.buttonColors(containerColor = Cine.PanelRaised)) { Text(tr("RUN NDI DISCOVERY", "JALANKAN DISCOVERY NDI"), color = Cine.Text) }
    test?.let { Text(it, color = Cine.Text, fontSize = 13.sp) }
    SectionTitle(tr("If receivers cannot see this camera", "Jika receiver tidak melihat kamera ini"))
    listOf(
        tr("Phone and receiver must be on the same subnet (no guest network / AP client isolation).", "Ponsel dan receiver harus di subnet yang sama (bukan jaringan tamu / tanpa client isolation AP)."),
        tr("mDNS (UDP 5353 multicast) must pass; some routers filter multicast on Wi-Fi.", "mDNS (multicast UDP 5353) harus lolos; sebagian router memfilter multicast di Wi-Fi."),
        tr("NDI uses TCP 5959-5960 and TCP/UDP from 5961 upward; allow them in the receiver PC firewall.", "NDI memakai TCP 5959-5960 dan TCP/UDP mulai 5961; izinkan di firewall PC receiver."),
        tr("On the receiver, NDI Access Manager can add this phone's IP (${n.ipv4Addresses.firstOrNull() ?: "?"}) as an extra IP if mDNS is blocked.",
            "Di receiver, NDI Access Manager bisa menambahkan IP ponsel ini (${n.ipv4Addresses.firstOrNull() ?: "?"}) sebagai IP tambahan jika mDNS diblokir."),
        tr("Prefer 5 GHz / 6 GHz Wi-Fi or USB Ethernet for 1080p and above.", "Gunakan Wi-Fi 5 GHz / 6 GHz atau Ethernet USB untuk 1080p ke atas."),
    ).forEach { Text("• $it", color = Cine.Dim, fontSize = 12.sp) }
}

@Composable
fun PerformanceDiagnosticsScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage(tr("Performance", "Performa"), onBack) {
    val stats by c.streamController.stats.collectAsStateWithLifecycle()
    val h by c.systemMonitor.health.collectAsStateWithLifecycle()
    val caps by c.cameraEngine.capabilities.collectAsStateWithLifecycle()
    val cam by c.cameraEngine.state.collectAsStateWithLifecycle()
    SectionTitle(tr("Frame delivery", "Pengiriman frame"))
    KeyValue("Streaming", if (stats.running) tr("yes", "ya") else tr("no", "tidak"))
    KeyValue("Mode", (cam as? CameraState.Running)?.mode?.label ?: cam::class.simpleName.orEmpty())
    KeyValue("Current FPS", String.format(Locale.US, "%.2f", stats.currentFps))
    KeyValue(tr("Video frames sent", "Frame video terkirim"), stats.videoFramesSent.toString())
    KeyValue(tr("Audio buffers sent", "Buffer audio terkirim"), stats.audioBuffersSent.toString())
    KeyValue(tr("Dropped: NDI thread busy", "Terbuang: thread NDI sibuk"), stats.framesDroppedPipeline.toString(), warn = stats.framesDroppedPipeline > 0)
    KeyValue(tr("Dropped: camera gaps", "Terbuang: jeda kamera"), stats.framesDroppedCamera.toString(), warn = stats.framesDroppedCamera > 0)
    KeyValue("YUV→NV12 + send", String.format(Locale.US, "%.2f ms", stats.lastConvertMs))
    SectionTitle(tr("Device health", "Kesehatan perangkat"))
    KeyValue(tr("Thermal", "Suhu"), h.thermalLabel + (h.thermalHeadroom?.let { String.format(Locale.US, " · headroom %.2f", it) } ?: ""), warn = h.throttling)
    KeyValue(tr("Battery", "Baterai"), "${h.batteryPercent ?: "--"}% ${if (h.charging) tr("(charging)", "(mengisi)") else ""} · ${h.batteryTempC ?: "--"} °C")
    KeyValue("Java heap", "${h.javaHeapUsedMb} MB")
    KeyValue("Native heap", "${h.nativeHeapUsedMb} MB")
    if (h.throttling) Text(tr("Device is thermally throttling: consider lower resolution/fps, removing the case, or active cooling.",
        "Perangkat mengalami thermal throttling: turunkan resolusi/fps, lepas casing, atau gunakan pendingin aktif."),
        color = Cine.Rec, fontSize = 12.sp)
    SectionTitle(tr("Camera capabilities", "Kemampuan kamera"))
    val cp = caps
    if (cp == null) Text(tr("Camera not open", "Kamera belum terbuka"), color = Cine.Dim) else {
        KeyValue(tr("Lens", "Lensa"), cp.lens.label)
        KeyValue("Hardware level", cp.hardwareLevel)
        KeyValue("Sensor orientation", "${cp.sensorOrientation}°")
        KeyValue("MANUAL_SENSOR", cp.manualSensor.toString())
        KeyValue("MANUAL_POST_PROCESSING", cp.manualPostProcessing.toString())
        KeyValue("ISO range", cp.isoRange?.let { "${it.lower}–${it.upper}" } ?: "n/a")
        KeyValue("Exposure range", cp.exposureTimeRangeNs?.let { "${it.lower / 1000} µs – ${it.upper / 1_000_000} ms" } ?: "n/a")
        KeyValue("Min focus", if (cp.minFocusDistanceDiopters > 0) "${cp.minFocusDistanceDiopters} D" else tr("fixed focus", "fokus tetap"))
        KeyValue("Zoom", "${cp.zoomRange.lower}–${cp.zoomRange.upper}x ${if (cp.usesZoomRatio) "(zoom ratio)" else "(crop)"}")
        KeyValue("OIS / EIS", "${cp.oisAvailable} / ${cp.eisAvailable}")
        KeyValue("Torch", cp.flashAvailable.toString())
        KeyValue("AF / AE regions", "${cp.maxAfRegions} / ${cp.maxAeRegions}")
        KeyValue("Modes", cp.modes.joinToString { it.label })
    }
}
