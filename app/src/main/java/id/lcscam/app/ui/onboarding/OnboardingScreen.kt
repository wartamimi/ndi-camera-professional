package id.lcscam.app.ui.onboarding

import id.lcscam.app.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.lcscam.app.ui.theme.Cine
import kotlinx.coroutines.launch

private data class Page(val badge: String, val title: String, val body: String)

private fun pages() = listOf(
    Page("LCS", tr("Welcome to LCS Cam", "Selamat datang di LCS Cam"),
        tr("Turn your Android phone into an NDI® camera for vMix, OBS, Wirecast, TriCaster and other production software, straight over your local network.",
            "Ubah ponsel Android menjadi kamera NDI® untuk vMix, OBS, Wirecast, TriCaster dan software produksi lain, langsung lewat jaringan lokal.")),
    Page("LAN", tr("Same network as the production PC", "Satu jaringan dengan PC produksi"),
        tr("Connect the phone and the PC to the same Wi-Fi or Ethernet. Use 5 GHz Wi-Fi or a USB-Ethernet adapter for the most stable result. The source appears in receivers automatically.",
            "Sambungkan ponsel dan PC ke Wi-Fi atau Ethernet yang sama. Gunakan Wi-Fi 5 GHz atau adaptor USB-Ethernet untuk hasil paling stabil. Source akan muncul otomatis di receiver.")),
    Page("REC", tr("Cinema-style camera controls", "Kontrol ala kamera sinema"),
        tr("Tap the preview to focus, long-press to lock. Set lens, ISO, shutter, white balance, zoom and overlays from the bottom bar. Press START STREAM to begin sending.",
            "Ketuk preview untuk fokus, tekan lama untuk mengunci. Atur lensa, ISO, shutter, white balance, zoom dan overlay dari bar bawah. Tekan MULAI STREAM untuk mulai mengirim.")),
    Page("PRO", tr("Start free, upgrade any time", "Mulai gratis, upgrade kapan saja"),
        tr("Free version: 720p30, 15-minute sessions and a watermark. Basic, Pro, Ultra and Max unlock higher resolutions, manual controls, 4K and production features. See Settings › Plans & Pricing.",
            "Versi Free: 720p30, sesi 15 menit dan watermark. Paket Basic, Pro, Ultra dan Max membuka resolusi lebih tinggi, kontrol manual, 4K dan fitur produksi. Lihat di Pengaturan › Paket & Harga.")),
    Page("OK", tr("Permissions needed", "Izin yang dibutuhkan"),
        tr("The app asks for Camera and Microphone access to send video and audio, and Notifications to show the stream status while running in the background.",
            "Aplikasi meminta akses Kamera dan Mikrofon untuk mengirim video dan audio, serta Notifikasi untuk menampilkan status stream saat berjalan di latar belakang.")),
)

/** First-launch introduction. [onDone] marks onboarding finished and continues to permissions. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = pages()
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = state.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().background(Cine.Black).safeDrawingPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = onDone) { Text(tr("SKIP", "LEWATI"), color = Cine.Dim) }
        }
        HorizontalPager(state, Modifier.weight(1f).fillMaxWidth()) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).background(Cine.Panel, CircleShape), contentAlignment = Alignment.Center) {
                    Text(p.badge, color = Cine.Accent, fontFamily = Cine.Mono, fontWeight = FontWeight.Black, fontSize = 26.sp)
                }
                Spacer(Modifier.height(28.dp))
                Text(p.title, color = Cine.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(p.body, color = Cine.Dim, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            pages.indices.forEach { i ->
                Box(Modifier.padding(4.dp).size(if (i == state.currentPage) 10.dp else 7.dp)
                    .background(if (i == state.currentPage) Cine.Accent else Cine.Line, CircleShape))
            }
        }
        Button(
            onClick = { if (last) onDone() else scope.launch { state.animateScrollToPage(state.currentPage + 1) } },
            colors = ButtonDefaults.buttonColors(containerColor = Cine.Accent),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (last) tr("START", "MULAI") else tr("NEXT", "LANJUT"), color = Cine.Black, fontWeight = FontWeight.Black, fontFamily = Cine.Mono)
        }
    }
}
