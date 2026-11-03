package com.ndicamera.professional.ui.onboarding

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
import com.ndicamera.professional.ui.theme.Cine
import kotlinx.coroutines.launch

private data class Page(val badge: String, val title: String, val body: String)

private val pages = listOf(
    Page("LCS", "Selamat datang di LCS Cam",
        "Ubah ponsel Android menjadi kamera NDI® untuk vMix, OBS, Wirecast, TriCaster dan software produksi lain, langsung lewat jaringan lokal."),
    Page("LAN", "Satu jaringan dengan PC produksi",
        "Sambungkan ponsel dan PC ke Wi-Fi atau Ethernet yang sama. Gunakan Wi-Fi 5 GHz atau adaptor USB-Ethernet untuk hasil paling stabil. Source akan muncul otomatis di receiver."),
    Page("REC", "Kontrol ala kamera sinema",
        "Ketuk preview untuk fokus, tekan lama untuk mengunci. Atur lensa, ISO, shutter, white balance, zoom dan overlay dari bar bawah. Tekan START STREAM untuk mulai mengirim."),
    Page("PRO", "Mulai gratis, upgrade kapan saja",
        "Versi Free: 720p30, sesi 15 menit dan watermark. Paket Basic, Pro, Ultra dan Max membuka resolusi lebih tinggi, kontrol manual, 4K dan fitur produksi. Lihat di Pengaturan › Paket & Harga."),
    Page("OK", "Izin yang dibutuhkan",
        "Aplikasi meminta akses Kamera dan Mikrofon untuk mengirim video dan audio, serta Notifikasi untuk menampilkan status stream saat berjalan di latar belakang."),
)

/** First-launch introduction. [onDone] marks onboarding finished and continues to permissions. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = state.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().background(Cine.Black).safeDrawingPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = onDone) { Text("LEWATI", color = Cine.Dim) }
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
            Text(if (last) "MULAI" else "LANJUT", color = Cine.Black, fontWeight = FontWeight.Black, fontFamily = Cine.Mono)
        }
    }
}
