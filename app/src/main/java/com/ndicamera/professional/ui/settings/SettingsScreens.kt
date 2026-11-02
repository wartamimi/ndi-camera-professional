package com.ndicamera.professional.ui.settings

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
import com.ndicamera.professional.audio.AudioSourceType
import com.ndicamera.professional.di.AppContainer
import com.ndicamera.professional.domain.model.CameraState
import com.ndicamera.professional.domain.model.NdiStatus
import com.ndicamera.professional.domain.model.OutputOrientation
import com.ndicamera.professional.ui.camera.ndiPill
import com.ndicamera.professional.ui.components.AudioMeter
import com.ndicamera.professional.ui.components.CineChip
import com.ndicamera.professional.ui.components.KeyValue
import com.ndicamera.professional.ui.components.NavRow
import com.ndicamera.professional.ui.components.SectionTitle
import com.ndicamera.professional.ui.components.SettingsPage
import com.ndicamera.professional.ui.components.StatusPill
import com.ndicamera.professional.ui.components.SwitchRow
import com.ndicamera.professional.ui.theme.Cine
import java.util.Locale

@Composable
fun SettingsHome(onBack: () -> Unit, navigate: (String) -> Unit) = SettingsPage("Settings", onBack) {
    NavRow("Camera", "Lens, resolution, frame rate, output orientation, overlays") { navigate("settings/camera") }
    NavRow("NDI", "Source name, groups, clocking, discovery self-test") { navigate("settings/ndi") }
    NavRow("Audio", "Input device, sample rate, channels, gain, A/V offset") { navigate("settings/audio") }
    NavRow("Network diagnostics", "Interface, IP, link, transmit rate, warnings") { navigate("network") }
    NavRow("Performance diagnostics", "Frame delivery, drops, thermal, memory, capabilities") { navigate("diagnostics") }
    NavRow("About", "Version, NDI® SDK, licences") { navigate("about") }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { content() }
}

@Composable
fun CameraSettingsScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage("Camera", onBack) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val lenses by c.cameraEngine.lenses.collectAsStateWithLifecycle()
    val caps by c.cameraEngine.capabilities.collectAsStateWithLifecycle()
    val state by c.cameraEngine.state.collectAsStateWithLifecycle()
    SectionTitle("Lens")
    ChipRow { lenses.forEach { l -> CineChip(l.label, caps?.lens?.key == l.key) { c.streamController.selectLens(l) } } }
    if (lenses.isEmpty()) Text("Open the camera screen once to enumerate lenses.", color = Cine.Dim, fontSize = 12.sp)
    SectionTitle("Video mode (verified for this lens)")
    val running = (state as? CameraState.Running)?.mode
    ChipRow { caps?.modes?.forEach { m -> CineChip(m.label, running == m) { c.streamController.selectMode(m) } } }
    Text("Only resolution/fps pairs whose YUV stream duration and AE fps range are supported are listed. " +
        "4K NDI output is listed only if the sensor delivers YUV at that size and rate.", color = Cine.Dim, fontSize = 12.sp)
    SectionTitle("NDI output orientation")
    ChipRow {
        OutputOrientation.entries.forEach { o -> CineChip(o.label, s.outputOrientation == o) { c.settings.update { it.copy(outputOrientation = o) } } }
    }
    SwitchRow("Mirror front camera output", "Receivers see a mirrored image (preview is always mirrored)", s.mirrorFrontOutput) { v ->
        c.settings.update { it.copy(mirrorFrontOutput = v) }
    }
    SwitchRow("Keep screen awake", "Prevents the display from sleeping on the camera screen", s.keepScreenOn) { v ->
        c.settings.update { it.copy(keepScreenOn = v) }
    }
    SectionTitle("Overlays")
    val o = s.overlays
    SwitchRow("Zebra", "Stripes at or above ${o.zebraLevel}% luma", o.zebra) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(zebra = v)) } }
    Text("Zebra level ${o.zebraLevel}%", color = Cine.Text, fontSize = 13.sp)
    Slider(o.zebraLevel.toFloat(), { v -> c.settings.update { it.copy(overlays = it.overlays.copy(zebraLevel = v.toInt())) } }, valueRange = 70f..100f,
        colors = SliderDefaults.colors(thumbColor = Cine.Accent, activeTrackColor = Cine.Accent))
    SwitchRow("Focus peaking", null, o.peaking) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(peaking = v)) } }
    Text("Peaking sensitivity ${o.peakingSensitivity}", color = Cine.Text, fontSize = 13.sp)
    Slider(o.peakingSensitivity.toFloat(), { v -> c.settings.update { it.copy(overlays = it.overlays.copy(peakingSensitivity = v.toInt())) } },
        valueRange = 0f..100f, colors = SliderDefaults.colors(thumbColor = Cine.Accent, activeTrackColor = Cine.Accent))
    ChipRow {
        listOf("RED" to 0xFFFF3B30.toInt(), "GREEN" to 0xFF34C759.toInt(), "BLUE" to 0xFF0A84FF.toInt(), "WHITE" to 0xFFFFFFFF.toInt(),
            "YELLOW" to 0xFFFFD60A.toInt()).forEach { (n, col) ->
            CineChip(n, o.peakingColor == col) { c.settings.update { it.copy(overlays = it.overlays.copy(peakingColor = col)) } }
        }
    }
    SwitchRow("Rule of thirds", null, o.thirds) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(thirds = v)) } }
    SwitchRow("Center crosshair", null, o.crosshair) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(crosshair = v)) } }
    SwitchRow("Safe area guides", "Action 93% / title 90%", o.safeAreas) { v -> c.settings.update { it.copy(overlays = it.overlays.copy(safeAreas = v)) } }
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
    KeyValue("NDI SDK", version ?: "not loaded")
    when (val st = status) {
        is NdiStatus.SdkNotInstalled -> Text(st.detail, color = Cine.Rec, fontSize = 12.sp)
        is NdiStatus.InitFailed -> Text(st.detail, color = Cine.Rec, fontSize = 12.sp)
        else -> Unit
    }
    SectionTitle("Source")
    OutlinedTextField(name, { name = it }, label = { Text("NDI source name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Receivers show this as \"DEVICE-NAME (${name.ifBlank { "…" }})\".", color = Cine.Dim, fontSize = 12.sp)
    OutlinedTextField(groups, { groups = it }, label = { Text("NDI groups (comma separated, empty = public)") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    Button(onClick = {
        c.settings.update { it.copy(sourceName = name, ndiGroups = groups) }
        if (c.streamController.isStreaming) c.streamController.restartSender()
    }, colors = ButtonDefaults.buttonColors(containerColor = Cine.Accent), modifier = Modifier.padding(top = 8.dp)) {
        Text(if (c.streamController.isStreaming) "SAVE & RESTART SENDER" else "SAVE", color = Cine.Black)
    }
    SectionTitle("Clocking")
    SwitchRow("Clock video in SDK", "Off (recommended): the camera paces frames. On: NDI rate-limits submissions to the frame rate.", s.clockVideo) { v ->
        c.settings.update { it.copy(clockVideo = v) }
    }
    SectionTitle("Discovery self-test")
    Text("Runs an NDI finder on this phone and checks that its own source is advertised over mDNS. " +
        "Passing proves local advertisement only; a remote receiver can still be blocked by Wi-Fi client isolation or a firewall.",
        color = Cine.Dim, fontSize = 12.sp)
    OutlinedTextField(extra, { extra = it }, label = { Text("Extra IPs for finder (optional, comma separated)") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    Button(onClick = {
        c.settings.update { it.copy(ndiExtraIps = extra) }
        testResult = "Searching for 4 s…"
        c.streamController.runDiscoveryTest { found, sources ->
            testResult = when {
                !c.streamController.isStreaming -> "Found ${sources.size} source(s). Start the stream to test this device's own source."
                found -> "PASS: own source visible locally. ${sources.size} source(s) found."
                else -> "FAIL: own source not visible. Check multicast/mDNS on this network."
            }
        }
    }, colors = ButtonDefaults.buttonColors(containerColor = Cine.PanelRaised), modifier = Modifier.padding(top = 8.dp)) {
        Text("RUN DISCOVERY TEST", color = Cine.Text)
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
    SwitchRow("Send audio over NDI", null, s.audioEnabled) { v -> c.settings.update { it.copy(audioEnabled = v) } }
    KeyValue("State", when (val st = state) {
        is com.ndicamera.professional.domain.model.AudioState.Capturing -> "${st.deviceName} · ${st.sampleRate} Hz · ${st.channels} ch"
        is com.ndicamera.professional.domain.model.AudioState.Error -> st.message
        com.ndicamera.professional.domain.model.AudioState.PermissionRequired -> "Microphone permission required"
        com.ndicamera.professional.domain.model.AudioState.Idle -> "Idle"
    })
    if (silenced) Text("Android has silenced this app's microphone (another app has priority).", color = Cine.Rec, fontSize = 12.sp)
    AudioMeter(levels, Modifier.fillMaxWidth().padding(vertical = 8.dp).height(40.dp))
    SectionTitle("Input device")
    devices.forEach { d ->
        CineChip("${d.name} · ${d.typeLabel}${if (d.isUsb) " · USB" else ""}", s.audioDeviceId == d.id) {
            c.settings.update { it.copy(audioDeviceId = d.id) }
        }
    }
    Text("USB microphones and audio interfaces appear here when connected (USB class-compliant devices only).", color = Cine.Dim, fontSize = 12.sp)
    val dev = devices.firstOrNull { it.id == s.audioDeviceId }
    SectionTitle("Sample rate")
    ChipRow {
        listOf(48_000, 44_100).forEach { r ->
            val ok = dev == null || dev.sampleRates.isEmpty() || r in dev.sampleRates
            CineChip("$r Hz", s.audioSampleRate == r, ok) { c.settings.update { it.copy(audioSampleRate = r) } }
        }
    }
    SectionTitle("Channels")
    ChipRow {
        listOf(1 to "MONO", 2 to "STEREO").forEach { (n, l) ->
            val ok = dev == null || dev.channelCounts.isEmpty() || n in dev.channelCounts
            CineChip(l, s.audioChannels == n, ok) { c.settings.update { it.copy(audioChannels = n) } }
        }
    }
    SectionTitle("Capture source")
    ChipRow {
        AudioSourceType.entries.forEach { t ->
            val ok = t != AudioSourceType.UNPROCESSED || c.audioEngine.unprocessedSupported
            CineChip(t.label, s.audioSource == t, ok) { c.settings.update { it.copy(audioSource = t) } }
        }
    }
    SectionTitle("Gain (digital, applied before NDI)")
    Text(String.format(Locale.US, "%+.1f dB", s.audioGainDb), color = Cine.Text, fontFamily = Cine.Mono)
    Slider(s.audioGainDb, { v -> c.settings.update { it.copy(audioGainDb = (v * 2).toInt() / 2f) } }, valueRange = -24f..24f,
        colors = SliderDefaults.colors(thumbColor = Cine.Accent, activeTrackColor = Cine.Accent))
    Text("Android exposes no standard hardware input-gain API; this is a digital gain stage.", color = Cine.Dim, fontSize = 12.sp)
    SectionTitle("Audio/video sync offset")
    Text("${s.avOffsetMs} ms (positive delays audio timecodes)", color = Cine.Text, fontFamily = Cine.Mono)
    Slider(s.avOffsetMs.toFloat(), { v -> c.settings.update { it.copy(avOffsetMs = (v / 5).toInt() * 5) } }, valueRange = -300f..300f,
        colors = SliderDefaults.colors(thumbColor = Cine.Accent, activeTrackColor = Cine.Accent))
}
