package id.lcscam.app.ui.plans

import id.lcscam.app.i18n.tr
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
import id.lcscam.app.BuildConfig
import id.lcscam.app.billing.BillingStatus
import id.lcscam.app.billing.Feature
import id.lcscam.app.billing.PlanCatalog
import id.lcscam.app.billing.PlanLimits
import id.lcscam.app.billing.Tier
import id.lcscam.app.di.AppContainer
import id.lcscam.app.ui.components.CineChip
import id.lcscam.app.ui.components.SectionTitle
import id.lcscam.app.ui.components.SettingsPage
import id.lcscam.app.ui.theme.Cine

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** What the Free plan is limited to, in plain language (mirrors [PlanLimits]). */
private fun freeLimits() = listOf(
    tr("Up to 720p 30 fps", "Maksimal 720p 30 fps"),
    tr("${PlanLimits.FREE_SESSION_MS / 60_000}-minute stream sessions, then it stops automatically",
        "Sesi stream ${PlanLimits.FREE_SESSION_MS / 60_000} menit, lalu berhenti otomatis"),
    tr("\"${PlanLimits.WATERMARK_TEXT}\" watermark on the NDI output", "Watermark \"${PlanLimits.WATERMARK_TEXT}\" pada output NDI"),
    tr("Mono audio, built-in microphone", "Audio mono, mikrofon internal"),
    tr("Default NDI source name", "Nama source NDI default"),
    tr("Main front/rear camera only", "Kamera utama depan/belakang saja"),
    tr("Automatic exposure, focus and white balance", "Exposure, fokus dan white balance otomatis"),
    tr("Stream stops when the app is closed / screen is off", "Stream berhenti saat app ditutup / layar mati"),
)

private fun tierTagline(t: Tier) = when (t) {
    Tier.BASIC -> tr("For everyday streaming without a time limit", "Untuk streaming harian tanpa batas waktu")
    Tier.PRO -> tr("Full manual camera control and cinematic tools", "Kontrol kamera manual penuh dan alat bantu sinematik")
    Tier.ULTRA -> tr("4K, tally and advanced NDI features for production", "4K, tally dan fitur NDI lanjutan untuk produksi")
    Tier.MAX -> tr("Every Ultra feature, pay once, keep forever", "Semua fitur Ultra, sekali bayar selamanya")
    Tier.FREE -> tr("Try all the NDI basics", "Coba semua dasar NDI")
}

@Composable
fun PlansScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage(tr("Plans & Pricing", "Paket & Harga"), onBack) {
    val billing = c.billing
    val tier by billing.tier.collectAsStateWithLifecycle()
    val status by billing.status.collectAsStateWithLifecycle()
    val offers by billing.offers.collectAsStateWithLifecycle()
    val message by billing.message.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { billing.refresh() }

    Text(tr("Active plan: ${tier.label}", "Paket aktif: ${tier.label}"), color = Cine.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    when (val s = status) {
        BillingStatus.Connecting -> Text(tr("Connecting to Google Play…", "Menghubungkan ke Google Play…"), color = Cine.Dim, fontSize = 12.sp)
        is BillingStatus.Unavailable -> Text(tr("Google Play Billing unavailable: ${s.reason}", "Google Play Billing tidak tersedia: ${s.reason}"), color = Cine.Warn, fontSize = 12.sp)
        BillingStatus.Ready -> Unit
    }
    message?.let { m ->
        Text(m, color = Cine.Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).clickable { billing.clearMessage() })
    }

    PlanCard(title = "FREE", tagline = tierTagline(Tier.FREE), current = tier == Tier.FREE) {
        freeLimits().forEach { Text("• $it", color = Cine.Dim, fontSize = 12.sp) }
    }

    Tier.entries.filter { it != Tier.FREE }.forEach { t ->
        val product = PlanCatalog.productFor(t)!!
        val tierOffers = offers.filter { it.tier == t }
        PlanCard(title = t.label.uppercase(), tagline = tierTagline(t), current = tier == t) {
            val newHere = Feature.entries.filter { it.minTier == t }
            if (t == Tier.MAX) {
                Text(tr("• Every Ultra feature", "• Semua fitur Ultra"), color = Cine.Text, fontSize = 12.sp)
                Text(tr("• No subscription: pay once, valid forever", "• Tanpa langganan: bayar sekali, berlaku selamanya"), color = Cine.Text, fontSize = 12.sp)
            } else {
                if (t != Tier.BASIC) Text(tr("• Every ${Tier.entries[t.ordinal - 1].label} feature", "• Semua fitur ${Tier.entries[t.ordinal - 1].label}"), color = Cine.Text, fontSize = 12.sp)
                newHere.forEach { Text("• ${it.title}", color = Cine.Text, fontSize = 12.sp) }
            }
            if (tierOffers.isEmpty()) {
                Text(tr("Price not yet available from Google Play (product \"${product.productId}\" is not active in Play Console " +
                    "or the app was not installed from Google Play). Suggested price: ${product.suggestedPrice}",
                    "Harga belum tersedia dari Google Play (produk \"${product.productId}\" belum aktif di Play Console " +
                    "atau app tidak diinstal dari Google Play). Harga yang disarankan: ${product.suggestedPrice}"),
                    color = Cine.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            } else if (!tier.atLeast(t)) {
                tierOffers.forEach { o ->
                    Button(
                        onClick = { ctx.findActivity()?.let { billing.launchPurchase(it, o) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Cine.Accent),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        val period = when (o.periodLabel) {
                            "monthly" -> tr("/ month", "/ bulan"); "yearly" -> tr("/ year", "/ tahun"); "lifetime" -> tr("one-time", "sekali bayar"); else -> "/ ${o.periodLabel}"
                        }
                        Text("${o.formattedPrice} $period", color = Cine.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    SectionTitle(tr("Manage", "Kelola"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { billing.refresh() }) { Text(tr("RESTORE PURCHASES", "PULIHKAN PEMBELIAN"), color = Cine.Text) }
        OutlinedButton(onClick = {
            val uri = Uri.parse("https://play.google.com/store/account/subscriptions?package=${ctx.packageName}")
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }) { Text(tr("SUBSCRIPTIONS ON PLAY", "LANGGANAN DI PLAY"), color = Cine.Text) }
    }
    Text(tr("Subscriptions renew automatically and can be cancelled any time in Google Play. Payment is handled by Google Play.",
        "Langganan diperpanjang otomatis dan bisa dibatalkan kapan saja di Google Play. Pembayaran ditangani oleh Google Play."),
        color = Cine.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))

    if (BuildConfig.DEBUG) {
        SectionTitle(tr("Debug: force plan (debug builds only)", "Debug: paksa paket (hanya build debug)"))
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
            if (current) Text(tr("ACTIVE", "AKTIF"), color = Cine.Ok, fontFamily = Cine.Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(tagline, color = Cine.Text, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}
