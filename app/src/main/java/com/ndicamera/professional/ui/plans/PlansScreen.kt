package com.ndicamera.professional.ui.plans

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndicamera.professional.BuildConfig
import com.ndicamera.professional.billing.BillingStatus
import com.ndicamera.professional.billing.Feature
import com.ndicamera.professional.billing.PlanCatalog
import com.ndicamera.professional.billing.PlanLimits
import com.ndicamera.professional.billing.Tier
import com.ndicamera.professional.di.AppContainer
import com.ndicamera.professional.ui.components.CineChip
import com.ndicamera.professional.ui.components.SectionTitle
import com.ndicamera.professional.ui.components.SettingsPage
import com.ndicamera.professional.ui.theme.Cine

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** What the Free plan is limited to, in plain language (mirrors [PlanLimits]). */
private val freeLimits = listOf(
    "Maksimal 720p 30 fps",
    "Sesi stream ${PlanLimits.FREE_SESSION_MS / 60_000} menit, lalu berhenti otomatis",
    "Watermark \"${PlanLimits.WATERMARK_TEXT}\" pada output NDI",
    "Audio mono, mikrofon internal",
    "Nama source NDI default",
    "Kamera utama depan/belakang saja",
    "Exposure, fokus dan white balance otomatis",
    "Stream berhenti saat app ditutup / layar mati",
)

private val tierTagline = mapOf(
    Tier.BASIC to "Untuk streaming harian tanpa batas waktu",
    Tier.PRO to "Kontrol kamera manual penuh dan alat bantu sinematik",
    Tier.ULTRA to "4K, tally dan fitur NDI lanjutan untuk produksi",
    Tier.MAX to "Semua fitur Ultra, sekali bayar selamanya",
)

@Composable
fun PlansScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage("Paket & Harga", onBack) {
    val billing = c.billing
    val tier by billing.tier.collectAsStateWithLifecycle()
    val status by billing.status.collectAsStateWithLifecycle()
    val offers by billing.offers.collectAsStateWithLifecycle()
    val message by billing.message.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { billing.refresh() }

    Text("Paket aktif: ${tier.label}", color = Cine.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    when (val s = status) {
        BillingStatus.Connecting -> Text("Menghubungkan ke Google Play…", color = Cine.Dim, fontSize = 12.sp)
        is BillingStatus.Unavailable -> Text("Google Play Billing tidak tersedia: ${s.reason}", color = Cine.Warn, fontSize = 12.sp)
        BillingStatus.Ready -> Unit
    }
    message?.let { m ->
        Text(m, color = Cine.Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).clickable { billing.clearMessage() })
    }

    PlanCard(title = "FREE", tagline = "Coba semua dasar NDI", current = tier == Tier.FREE) {
        freeLimits.forEach { Text("• $it", color = Cine.Dim, fontSize = 12.sp) }
    }

    Tier.entries.filter { it != Tier.FREE }.forEach { t ->
        val product = PlanCatalog.productFor(t)!!
        val tierOffers = offers.filter { it.tier == t }
        PlanCard(title = t.label.uppercase(), tagline = tierTagline[t].orEmpty(), current = tier == t) {
            val newHere = Feature.entries.filter { it.minTier == t }
            if (t == Tier.MAX) {
                Text("• Semua fitur Ultra", color = Cine.Text, fontSize = 12.sp)
                Text("• Tanpa langganan: bayar sekali, berlaku selamanya", color = Cine.Text, fontSize = 12.sp)
            } else {
                if (t != Tier.BASIC) Text("• Semua fitur ${Tier.entries[t.ordinal - 1].label}", color = Cine.Text, fontSize = 12.sp)
                newHere.forEach { Text("• ${it.title}", color = Cine.Text, fontSize = 12.sp) }
            }
            if (tierOffers.isEmpty()) {
                Text("Harga belum tersedia dari Google Play (produk \"${product.productId}\" belum aktif di Play Console " +
                    "atau app tidak diinstal dari Google Play). Harga yang disarankan: ${product.suggestedPrice}",
                    color = Cine.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            } else if (!tier.atLeast(t)) {
                tierOffers.forEach { o ->
                    Button(
                        onClick = { ctx.findActivity()?.let { billing.launchPurchase(it, o) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Cine.Accent),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        val period = when (o.periodLabel) {
                            "monthly" -> "/ bulan"; "yearly" -> "/ tahun"; "lifetime" -> "sekali bayar"; else -> "/ ${o.periodLabel}"
                        }
                        Text("${o.formattedPrice} $period", color = Cine.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    SectionTitle("Kelola")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { billing.refresh() }) { Text("PULIHKAN PEMBELIAN", color = Cine.Text) }
        OutlinedButton(onClick = {
            val uri = Uri.parse("https://play.google.com/store/account/subscriptions?package=${ctx.packageName}")
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }) { Text("LANGGANAN DI PLAY", color = Cine.Text) }
    }
    Text("Langganan diperpanjang otomatis dan bisa dibatalkan kapan saja di Google Play. Pembayaran ditangani oleh Google Play.",
        color = Cine.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))

    if (BuildConfig.DEBUG) {
        SectionTitle("Debug: paksa paket (hanya build debug)")
        Row {
            CineChip("PLAY", billing.debugOverride == null) { billing.debugOverride = null }
            Tier.entries.forEach { t -> CineChip(t.label, billing.debugOverride == t) { billing.debugOverride = t } }
        }
    }
}

@Composable
private fun PlanCard(title: String, tagline: String, current: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp)
            .border(1.dp, if (current) Cine.Accent else Cine.Line, RoundedCornerShape(6.dp))
            .background(Cine.Panel, RoundedCornerShape(6.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Cine.Accent, fontFamily = Cine.Mono, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.weight(1f))
            if (current) Text("AKTIF", color = Cine.Ok, fontFamily = Cine.Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(tagline, color = Cine.Text, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}
