package id.lcscam.app.ui.settings

import id.lcscam.app.i18n.tr
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.lcscam.app.audio.AudioSourceType
import id.lcscam.app.di.AppContainer
import id.lcscam.app.domain.model.CameraState
import id.lcscam.app.domain.model.NdiStatus
import id.lcscam.app.domain.model.OutputOrientation
import id.lcscam.app.i18n.AppLanguage
import id.lcscam.app.i18n.I18n
import id.lcscam.app.settings.UiOrientation
import id.lcscam.app.ui.camera.ndiPill
import id.lcscam.app.ui.components.AudioMeter
import id.lcscam.app.ui.components.CineChip
import id.lcscam.app.ui.components.KeyValue
import id.lcscam.app.ui.components.NavRow
import id.lcscam.app.ui.components.SectionTitle
import id.lcscam.app.ui.components.SettingsPage
import id.lcscam.app.ui.components.StatusPill
import id.lcscam.app.ui.components.SwitchRow
import id.lcscam.app.ui.theme.Cine
import java.util.Locale

@Composable
fun SettingsHome(c: AppContainer, onBack: () -> Unit, navigate: (String) -> Unit) = SettingsPage(tr("Settings", "Pengaturan"), onBack) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    // Always bilingual so the picker can be found whatever language is active.
    SectionTitle("Language / Bahasa")
    ChipRow {
        AppLanguage.entries.forEach { l ->
            val label = when (l) {
                AppLanguage.SYSTEM -> tr("Device (region)", "Ikuti perangkat (region)")
                AppLanguage.INDONESIAN -> "Bahasa Indonesia"
                AppLanguage.ENGLISH -> "English"
            }
            CineChip(label, s.language == l) {
                I18n.apply(l) // switch immediately; the saved setting follows
                c.settings.update { it.copy(language = l) }
            }
        }
    }
    Text(tr("Device follows the phone's language and region: Indonesian for Indonesia, English elsewhere.",
        "Ikuti perangkat memakai bahasa dan region ponsel: Indonesia untuk region Indonesia, Inggris untuk lainnya."),
        color = Cine.Dim, fontSize = 12.sp)
    SectionTitle(tr("Menu", "Menu"))
    NavRow(tr("Camera", "Kamera"), tr("Lens, resolution, frame rate, output orientation, overlays",
        "Lensa, resolusi, frame rate, orientasi output, overlay")) { navigate("settings/camera") }
    NavRow("NDI", tr("Source name, groups, clocking, discovery self-test", "Nama source, grup, clocking, uji discovery")) { navigate("settings/ndi") }
    NavRow("Audio", tr("Input device, sample rate, channels, gain, A/V offset",
        "Perangkat input, sample rate, kanal, gain, offset A/V")) { navigate("settings/audio") }
    NavRow(tr("Network diagnostics", "Diagnostik jaringan"), tr("Interface, IP, link, transmit rate, warnings",
        "Antarmuka, IP, link, laju kirim, peringatan")) { navigate("network") }
    NavRow(tr("Performance diagnostics", "Diagnostik performa"), tr("Frame delivery, drops, thermal, memory, capabilities",
        "Pengiriman frame, drop, suhu, memori, kemampuan")) { navigate("diagnostics") }
    NavRow(tr("Plans & Pricing", "Paket & Harga"), tr("Free, Basic, Pro, Ultra, Max · restore purchases",
        "Free, Basic, Pro, Ultra, Max · pulihkan pembelian")) { navigate("plans") }
    NavRow(tr("About", "Tentang"), tr("Version, NDI® SDK, licences, developer", "Versi, NDI® SDK, lisensi, pengembang")) { navigate("about") }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { content() }
}

@Composable
fun CameraSettingsScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage(tr("Camera", "Kamera"), onBack) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val lenses by c.cameraEngine.lenses.collectAsStateWithLifecycle()
    val caps by c.cameraEngine.capabilities.collectAsStateWithLifecycle()
    val state by c.cameraEngine.state.collectAsStateWithLifecycle()
    SectionTitle(tr("Lens", "Lensa"))
    ChipRow { lenses.forEach { l -> CineChip(l.label, caps?.lens?.key == l.key) { c.streamController.selectLens(l) } } }
    if (lenses.isEmpty()) Text(tr("Open the camera screen once to enumerate lenses.", "Buka layar kamera sekali untuk memuat daftar lensa."), color = Cine.Dim, fontSize = 12.sp)
    SectionTitle(tr("Video mode (verified for this lens)", "Mode video (terverifikasi untuk lensa ini)"))
    val running = (state as? CameraState.Running)?.mode
    ChipRow { caps?.modes?.forEach { m -> CineChip(m.label, running == m) { c.streamController.selectMode(m) } } }
    Text(tr("Only resolution/fps pairs whose YUV stream duration and AE fps range are supported are listed. " +
        "4K NDI output is listed only if the sensor delivers YUV at that size and rate.",
        "Hanya pasangan resolusi/fps yang didukung durasi stream YUV dan rentang fps AE yang ditampilkan. " +
        "Output NDI 4K hanya muncul jika sensor mengirim YUV pada ukuran dan laju tersebut."), color = Cine.Dim, fontSize = 12.sp)
    SectionTitle(tr("Interface orientation", "Orientasi tampilan"))
    ChipRow {
        UiOrientation.entries.forEach { o -> CineChip(o.label, s.uiOrientation == o) { c.settings.update { it.copy(uiOrientation = o) } } }
    }
    Text(tr("Vertical is the default. Auto-rotate follows the device, including landscape.", "Vertikal adalah bawaan. Putar otomatis mengikuti perangkat, termasuk lanskap."), color = Cine.Dim, fontSize = 12.sp)
    SectionTitle(tr("NDI output orientation", "Orientasi output NDI"))
    ChipRow {
        OutputOrientation.entries.forEach { o -> CineChip(o.label, s.outputOrientation == o) { c.settings.update { it.copy(outputOrientation = o) } } }
    }
    Text(when (s.outputOrientation) {
        OutputOrientation.FOLLOW_SCREEN -> tr("Receivers get exactly the orientation shown on screen: vertical interface = portrait output (e.g. 1080×1920), landscape interface = landscape output (1920×1080).",
            "Receiver menerima orientasi persis seperti di layar: tampilan vertikal = output potret (mis. 1080×1920), tampilan lanskap = output lanskap (1920×1080).")
        OutputOrientation.PORTRAIT -> tr("Always portrait, whichever way the interface is shown.", "Selalu potret, apa pun orientasi tampilan.")
        OutputOrientation.LANDSCAPE -> tr("Always landscape, whichever way the interface is shown.", "Selalu lanskap, apa pun orientasi tampilan.")
    }, color = Cine.Dim, fontSize = 12.sp)
    SwitchRow(tr("Mirror front camera output", "Cerminkan output kamera depan"), tr("Receivers see a mirrored image (preview is always mirrored)", "Receiver melihat gambar tercermin (preview selalu tercermin)"), s.mirrorFrontOutput) { v ->
        c.settings.update { it.copy(mirrorFrontOutput = v) }
    }
    SwitchRow(tr("Keep screen awake", "Layar tetap menyala"), tr("Prevents the display from sleeping on the camera screen", "Mencegah layar mati di layar kamera"), s.keepScreenOn) { v ->
        c.settings.update { it.copy(keepScreenOn = v) }
    }
    SectionTitle(tr("Overlays", "Overlay"))
    val o = s.overlays
    SwitchRow("Zebra", tr("Stripes at or above ${o.zebraLevel}% luma", "Garis pada luma ${o.zebraLevel}% ke atas"), o.zebra) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(zebra = v)) } }
    Text(tr("Zebra level ${o.zebraLevel}%", "Level zebra ${o.zebraLevel}%"), color = Cine.Text, fontSize = 13.sp)
    Slider(o.zebraLevel.toFloat(), { v -> c.settings.update { it.copy(overlays = it.overlays.copy(zebraLevel = v.toInt())) } }, valueRange = 70f..100f,
        colors = SliderDefaults.colors(thumbColor = Cine.Accent, activeTrackColor = Cine.Accent))
    SwitchRow("Focus peaking", null, o.peaking) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(peaking = v)) } }
    Text(tr("Peaking sensitivity ${o.peakingSensitivity}", "Sensitivitas peaking ${o.peakingSensitivity}"), color = Cine.Text, fontSize = 13.sp)
    Slider(o.peakingSensitivity.toFloat(), { v -> c.settings.update { it.copy(overlays = it.overlays.copy(peakingSensitivity = v.toInt())) } },
        valueRange = 0f..100f, colors = SliderDefaults.colors(thumbColor = Cine.Accent, activeTrackColor = Cine.Accent))
    ChipRow {
        listOf(tr("RED", "MERAH") to 0xFFFF3B30.toInt(), tr("GREEN", "HIJAU") to 0xFF34C759.toInt(), tr("BLUE", "BIRU") to 0xFF0A84FF.toInt(),
            tr("WHITE", "PUTIH") to 0xFFFFFFFF.toInt(), tr("YELLOW", "KUNING") to 0xFFFFD60A.toInt()).forEach { (n, col) ->
            CineChip(n, o.peakingColor == col) { c.settings.update { it.copy(overlays = it.overlays.copy(peakingColor = col)) } }
        }
    }
    SwitchRow(tr("Rule of thirds", "Garis sepertiga"), null, o.thirds) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(thirds = v)) } }
    SwitchRow(tr("Center crosshair", "Tanda tengah"), null, o.crosshair) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(crosshair = v)) } }
    SwitchRow(tr("Safe area guides", "Panduan area aman"), tr("Action 93% / title 90%", "Aksi 93% / judul 90%"), o.safeAreas) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(safeAreas = v)) } }
    SwitchRow("Histogram", null, o.histogram) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(histogram = v)) } }
}

@Composable
fun NdiSettingsScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage("NDI", onBack) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val status by c.streamController.ndiStatus.collectAsStateWithLifecycle()
    val version by c.streamController.ndiVersion.collectAsStateWithLifecycle()
    val discovered by c.streamController.discoveredSources.collectAsStateWithLifecycle()
    var name by remember(s.sourceName) { mutableStateOf(s.sourceName) }
    var groups by remember(s.ndiGroups) { mutableStateOf(s.ndiGroups) }
    var extra by remember(s.ndiExtraIps) { mutableStateOf(s.ndiExtraIps) }
    var testResult by remember { mutableStateOf<String?>(null) }

    SectionTitle("Status")
    val (pill, col) = ndiPill(status)
    StatusPill(pill, col)
    KeyValue("NDI SDK", version ?: tr("not loaded", "belum dimuat"))
    when (val st = status) {
        is NdiStatus.SdkNotInstalled -> Text(st.detail, color = Cine.Rec, fontSize = 12.sp)
        is NdiStatus.InitFailed -> Text(st.detail, color = Cine.Rec, fontSize = 12.sp)
        else -> Unit
    }
    SectionTitle(tr("Source", "Source"))
    OutlinedTextField(name, { name = it }, label = { Text(tr("NDI source name", "Nama source NDI")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text(tr("Receivers show this as \"DEVICE-NAME (${name.ifBlank { "…" }})\".", "Receiver menampilkannya sebagai \"NAMA-PERANGKAT (${name.ifBlank { "…" }})\"."), color = Cine.Dim, fontSize = 12.sp)
    OutlinedTextField(groups, { groups = it }, label = { Text(tr("NDI groups (comma separated, empty = public)", "Grup NDI (pisahkan koma, kosong = publik)")) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    Button(onClick = {
        c.settings.update { it.copy(sourceName = name, ndiGroups = groups) }
        if (c.streamController.isStreaming) c.streamController.restartSender()
    }, colors = ButtonDefaults.buttonColors(containerColor = Cine.Accent), modifier = Modifier.padding(top = 8.dp)) {
        Text(if (c.streamController.isStreaming) tr("SAVE & RESTART SENDER", "SIMPAN & MULAI ULANG SENDER") else tr("SAVE", "SIMPAN"), color = Cine.Black)
    }
    SectionTitle("Clocking")
    SwitchRow(tr("Clock video in SDK", "Clock video di SDK"), tr("Off (recommended): the camera paces frames. On: NDI rate-limits submissions to the frame rate.", "Mati (disarankan): kamera yang mengatur laju frame. Nyala: NDI membatasi pengiriman sesuai frame rate."), s.clockVideo) { v ->
        c.settings.update { it.copy(clockVideo = v) }
    }
    SectionTitle(tr("Discovery self-test", "Uji discovery"))
    Text(tr("Runs an NDI finder on this phone and checks that its own source is advertised over mDNS. " +
        "Passing proves local advertisement only; a remote receiver can still be blocked by Wi-Fi client isolation or a firewall.",
        "Menjalankan NDI finder di ponsel ini dan memeriksa source-nya sendiri diumumkan lewat mDNS. " +
        "Lulus hanya membuktikan pengumuman lokal; receiver lain masih bisa terhalang client isolation Wi-Fi atau firewall."),
        color = Cine.Dim, fontSize = 12.sp)
    OutlinedTextField(extra, { extra = it }, label = { Text(tr("Extra IPs for finder (optional, comma separated)", "IP tambahan untuk finder (opsional, pisahkan koma)")) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    Button(onClick = {
        c.settings.update { it.copy(ndiExtraIps = extra) }
        testResult = tr("Searching for 4 s…", "Mencari selama 4 detik…")
        c.streamController.runDiscoveryTest { found, sources ->
            testResult = when {
                !c.streamController.isStreaming -> tr("Found ${sources.size} source(s). Start the stream to test this device's own source.", "Ditemukan ${sources.size} source. Mulai stream untuk menguji source perangkat ini.")
                found -> tr("PASS: own source visible locally. ${sources.size} source(s) found.", "LULUS: source sendiri terlihat secara lokal. ${sources.size} source ditemukan.")
                else -> tr("FAIL: own source not visible. Check multicast/mDNS on this network.", "GAGAL: source sendiri tidak terlihat. Periksa multicast/mDNS di jaringan ini.")
            }
        }
    }, colors = ButtonDefaults.buttonColors(containerColor = Cine.PanelRaised), modifier = Modifier.padding(top = 8.dp)) {
        Text(tr("RUN DISCOVERY TEST", "JALANKAN UJI DISCOVERY"), color = Cine.Text)
    }
    testResult?.let { Text(it, color = Cine.Text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
    discovered?.forEach { Text("• $it", color = Cine.Dim, fontSize = 12.sp, fontFamily = Cine.Mono) }
}

@Composable
fun AudioSettingsScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage("Audio", onBack) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val devices by c.audioEngine.devices.collectAsStateWithLifecycle()
    val levels by c.audioEngine.levels.collectAsStateWithLifecycle()
    val state by c.audioEngine.state.collectAsStateWithLifecycle()
    val silenced by c.audioEngine.silencedBySystem.collectAsStateWithLifecycle()
    SwitchRow(tr("Send audio over NDI", "Kirim audio lewat NDI"), null, s.audioEnabled) { v -> c.settings.update { it.copy(audioEnabled = v) } }
    KeyValue(tr("State", "Status"), when (val st = state) {
        is id.lcscam.app.domain.model.AudioState.Capturing -> "${st.deviceName} · ${st.sampleRate} Hz · ${st.channels} ch"
        is id.lcscam.app.domain.model.AudioState.Error -> st.message
        id.lcscam.app.domain.model.AudioState.PermissionRequired -> tr("Microphone permission required", "Izin mikrofon diperlukan")
        id.lcscam.app.domain.model.AudioState.Idle -> tr("Idle", "Diam")
    })
    if (silenced) Text(tr("Android has silenced this app's microphone (another app has priority).", "Android membisukan mikrofon aplikasi ini (aplikasi lain diprioritaskan)."), color = Cine.Rec, fontSize = 12.sp)
    AudioMeter(levels, Modifier.fillMaxWidth().padding(vertical = 8.dp).height(40.dp))
    SectionTitle(tr("Input device", "Perangkat input"))
    devices.forEach { d ->
        CineChip("${d.name} · ${d.typeLabel}${if (d.isUsb) " · USB" else ""}", s.audioDeviceId == d.id) {
            c.settings.update { it.copy(audioDeviceId = d.id) }
        }
    }
    Text(tr("USB microphones and audio interfaces appear here when connected (USB class-compliant devices only).", "Mikrofon USB dan audio interface muncul di sini saat tersambung (hanya perangkat USB class-compliant)."), color = Cine.Dim, fontSize = 12.sp)
    val dev = devices.firstOrNull { it.id == s.audioDeviceId }
    SectionTitle("Sample rate")
    ChipRow {
        listOf(48_000, 44_100).forEach { r ->
            val ok = dev == null || dev.sampleRates.isEmpty() || r in dev.sampleRates
            CineChip("$r Hz", s.audioSampleRate == r, ok) { c.settings.update { it.copy(audioSampleRate = r) } }
        }
    }
    SectionTitle(tr("Channels", "Kanal"))
    ChipRow {
        listOf(1 to "MONO", 2 to "STEREO").forEach { (n, l) ->
            val ok = dev == null || dev.channelCounts.isEmpty() || n in dev.channelCounts
            CineChip(l, s.audioChannels == n, ok) { c.settings.update { it.copy(audioChannels = n) } }
        }
    }
    SectionTitle(tr("Capture source", "Sumber rekam"))
    ChipRow {
        AudioSourceType.entries.forEach { t ->
            val ok = t != AudioSourceType.UNPROCESSED || c.audioEngine.unprocessedSupported
            CineChip(t.label, s.audioSource == t, ok) { c.settings.update { it.copy(audioSource = t) } }
        }
    }
    SectionTitle(tr("Gain (digital, applied before NDI)", "Gain (digital, diterapkan sebelum NDI)"))
    Text(String.format(Locale.US, "%+.1f dB", s.audioGainDb), color = Cine.Text, fontFamily = Cine.Mono)
    Slider(s.audioGainDb, { v -> c.settings.update { it.copy(audioGainDb = (v * 2).toInt() / 2f) } }, valueRange = -24f..24f,
        colors = SliderDefaults.colors(thumbColor = Cine.Accent, activeTrackColor = Cine.Accent))
    Text(tr("Android exposes no standard hardware input-gain API; this is a digital gain stage.", "Android tidak menyediakan API gain input hardware standar; ini adalah gain digital."), color = Cine.Dim, fontSize = 12.sp)
    SectionTitle(tr("Audio/video sync offset", "Offset sinkron audio/video"))
    Text(tr("${s.avOffsetMs} ms (positive delays audio timecodes)", "${s.avOffsetMs} ms (positif menunda timecode audio)"), color = Cine.Text, fontFamily = Cine.Mono)
    Slider(s.avOffsetMs.toFloat(), { v -> c.settings.update { it.copy(avOffsetMs = (v / 5).toInt() * 5) } }, valueRange = -300f..300f,
        colors = SliderDefaults.colors(thumbColor = Cine.Accent, activeTrackColor = Cine.Accent))
}
