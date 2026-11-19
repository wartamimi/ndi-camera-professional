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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import id.lcscam.app.ui.splash.AnimatedLogo
import id.lcscam.app.ui.splash.SplashBg
import id.lcscam.app.ui.splash.easeOutBack
import id.lcscam.app.ui.splash.easeOutCubic
import id.lcscam.app.ui.splash.seg
import kotlin.math.absoluteValue

private data class Page(val badge: String, val title: String, val body: String)

private fun pages() = listOf(
    Page("LCS", tr("Welcome to LCS Cam", "Selamat datang di LCS Cam"),
        tr("Turn your Android phone into an NDI® camera for vMix, OBS, Wirecast, TriCaster and other production software, straight over your local network.",
            "Ubah ponsel Android menjadi kamera NDI® untuk vMix, OBS, Wirecast, TriCaster dan software produksi lain, langsung lewat jaringan lokal.")),
    Page("LAN", tr("Same network as the production PC", "Satu jaringan dengan PC produksi"),
        tr("Connect the phone and the PC to the same Wi-Fi or Ethernet. Use 5 GHz Wi-Fi or a USB-Ethernet adapter for the most stable result. The source appears in receivers automatically.",
            "Sambungkan ponsel dan PC ke Wi-Fi atau Ethernet yang sama. Gunakan Wi-Fi 5 GHz atau adaptor USB-Ethernet untuk hasil paling stabil. Source akan muncul otomatis di receiver.")),
    Page("REC", tr("Cinema-style camera controls", "Kontrol ala kamera sinema"),
        tr("Tap the preview to focus, long-press to lock, pinch to zoom. Set lens, ISO, shutter, white balance, zoom and overlays from the bottom bar. Press START STREAM to begin sending.",
            "Ketuk preview untuk fokus, tekan lama untuk mengunci, cubit untuk zoom. Atur lensa, ISO, shutter, white balance, zoom dan overlay dari bar bawah. Tekan MULAI STREAM untuk mulai mengirim.")),
    Page("PRO", tr("Start free, upgrade any time", "Mulai gratis, upgrade kapan saja"),
        tr("Free version: 720p30, 15-minute sessions and a watermark. Pro unlocks 1080p60, manual controls and production features; Max adds 4K and is a one-time purchase. See Settings › Plans & Pricing.",
            "Versi Free: 720p30, sesi 15 menit dan watermark. Paket Pro membuka 1080p60, kontrol manual dan fitur produksi; Max menambah 4K dan cukup sekali bayar. Lihat di Pengaturan › Paket & Harga.")),
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
    // Whole screen fades and rises in when it first appears (right after the splash).
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(SplashBg, Cine.Black)))
            .safeDrawingPadding().padding(24.dp)
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 24.dp.toPx() },
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End) {
            androidx.compose.animation.AnimatedVisibility(!last, enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = onDone) { Text(tr("SKIP", "LEWATI"), color = Cine.Dim) }
            }
        }
        HorizontalPager(state, Modifier.weight(1f).fillMaxWidth()) { i ->
            OnboardingPage(pages[i], i, state)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            pages.indices.forEach { i ->
                val selected = i == state.currentPage
                val w by animateDpAsState(if (selected) 26.dp else 8.dp, tween(300), label = "dotWidth")
                val c by animateColorAsState(if (selected) Cine.Accent else Cine.Line, tween(300), label = "dotColor")
                Box(Modifier.padding(4.dp).height(8.dp).width(w).background(c, CircleShape))
            }
        }
        Button(
            onClick = { if (last) onDone() else scope.launch { state.animateScrollToPage(state.currentPage + 1) } },
            colors = ButtonDefaults.buttonColors(containerColor = Cine.Accent),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            AnimatedContent(last, transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            }, label = "cta") { isLast ->
                Text(if (isLast) tr("START", "MULAI") else tr("NEXT", "LANJUT"), color = Cine.Black,
                    fontWeight = FontWeight.Black, fontFamily = Cine.Mono)
            }
        }
    }
}

/**
 * One page. While swiping, pages fade, shrink and drift with the pager offset (parallax: the
 * badge moves less than the text). When a page settles its badge pops in and the title and body
 * slide up one after the other.
 */
@Composable
private fun OnboardingPage(p: Page, index: Int, state: PagerState) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(state.settledPage == index) {
        if (state.settledPage == index) reveal.animateTo(1f, tween(if (index == 0) 1600 else 900, easing = LinearEasing))
    }
    val r = reveal.value
    Column(
        Modifier.fillMaxSize().graphicsLayer {
            val off = ((state.currentPage - index) + state.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            alpha = lerp(1f, 0.2f, off)
            val sc = lerp(1f, 0.88f, off); scaleX = sc; scaleY = sc
        },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val parallax = Modifier.graphicsLayer {
            translationX = ((state.currentPage - index) + state.currentPageOffsetFraction) * size.width * 0.35f
        }
        if (index == 0) {
            AnimatedLogo(r, parallax.width(240.dp))
        } else {
            PulsingBadge(p.badge, easeOutBack(seg(r, 0f, 0.45f)), parallax)
        }
        Spacer(Modifier.height(28.dp))
        val titleT = easeOutCubic(seg(r, if (index == 0) 0.55f else 0.2f, if (index == 0) 0.8f else 0.6f))
        Text(p.title, color = Cine.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = titleT; translationY = (1f - titleT) * 28.dp.toPx() })
        Spacer(Modifier.height(12.dp))
        val bodyT = easeOutCubic(seg(r, if (index == 0) 0.7f else 0.35f, if (index == 0) 0.95f else 0.8f))
        Text(p.body, color = Cine.Dim, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp).graphicsLayer { alpha = bodyT; translationY = (1f - bodyT) * 28.dp.toPx() })
    }
}

/** Round badge with a slowly orbiting orange arc and a soft pulsing halo; [pop] scales it in. */
@Composable
private fun PulsingBadge(text: String, pop: Float, modifier: Modifier) {
    val inf = rememberInfiniteTransition(label = "badge")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "spin")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "pulse")
    Box(modifier.size(136.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop.coerceIn(0f, 1f) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val base = 48.dp.toPx()
            drawCircle(Cine.Accent.copy(alpha = 0.28f * (1f - pulse)), radius = base + pulse * 20.dp.toPx(), center = c)
            val r = base + 8.dp.toPx()
            drawArc(Cine.Accent, startAngle = spin, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawArc(Cine.Accent.copy(alpha = 0.35f), startAngle = spin + 180f, sweepAngle = 40f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        Box(Modifier.size(96.dp).background(Cine.Panel, CircleShape), contentAlignment = Alignment.Center) {
            Text(text, color = Cine.Accent, fontFamily = Cine.Mono, fontWeight = FontWeight.Black, fontSize = 26.sp)
        }
    }
}
