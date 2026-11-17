package id.lcscam.app.ui.about

import id.lcscam.app.i18n.tr
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.lcscam.app.BuildConfig
import id.lcscam.app.di.AppContainer
import id.lcscam.app.ui.components.KeyValue
import id.lcscam.app.ui.components.SectionTitle
import id.lcscam.app.ui.components.SettingsPage
import id.lcscam.app.ui.theme.Cine

@Composable
fun AboutScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage(tr("About", "Tentang"), onBack) {
    val version by c.streamController.ndiVersion.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val tier by c.billing.tier.collectAsStateWithLifecycle()
    Text("LCS Cam", color = Cine.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(tr("Professional mobile broadcast camera", "Kamera broadcast mobile profesional"), color = Cine.Dim, fontSize = 13.sp)
    Text("Dibuat oleh Munawar Tamimi", color = Cine.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp))
    SectionTitle(tr("Developer", "Pengembang"))
    KeyValue(tr("Developer", "Pengembang"), "Munawar Tamimi")
    KeyValue(tr("Active plan", "Paket aktif"), tier.label)
    Text("© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} Munawar Tamimi. All rights reserved.",
        color = Cine.Dim, fontSize = 12.sp)
    SectionTitle("Build")
    KeyValue(tr("Version", "Versi"), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    KeyValue(tr("Build type", "Tipe build"), BuildConfig.BUILD_TYPE + if (BuildConfig.NDI_VALIDATION_BUILD) tr(" · CI validation (no NDI SDK)", " · validasi CI (tanpa NDI SDK)") else "")
    KeyValue("NDI® SDK runtime", version ?: tr("not loaded", "belum dimuat"))
    SectionTitle("NDI®")
    Text(tr("This application uses the NDI® SDK to send video and audio over IP. ",
        "Aplikasi ini memakai NDI® SDK untuk mengirim video dan audio lewat IP. ") +
        "NDI® is a registered trademark of Vizrt NDI AB.", color = Cine.Text, fontSize = 13.sp)
    Text("https://ndi.video/", color = Cine.Accent, fontSize = 14.sp, textDecoration = TextDecoration.Underline,
        modifier = Modifier.padding(vertical = 8.dp).clickable {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ndi.video/")))
        })
    Text(tr("NDI Tools (Studio Monitor, Access Manager) for testing reception: https://ndi.video/tools/", "NDI Tools (Studio Monitor, Access Manager) untuk menguji penerimaan: https://ndi.video/tools/"),
        color = Cine.Dim, fontSize = 12.sp)
    SectionTitle(tr("Licences", "Lisensi"))
    Text(tr("The NDI SDK is licensed under the NDI SDK License Agreement and is not covered by this project's source licence. " +
        "Third-party notices for libndi are shipped by NDI in libndi_licenses.txt. " +
        "AndroidX and Kotlin libraries are licensed under the Apache License 2.0.",
        "NDI SDK dilisensikan di bawah NDI SDK License Agreement dan tidak termasuk lisensi kode sumber proyek ini. " +
        "Pemberitahuan pihak ketiga untuk libndi disertakan NDI di libndi_licenses.txt. " +
        "Library AndroidX dan Kotlin dilisensikan di bawah Apache License 2.0."), color = Cine.Dim, fontSize = 12.sp)
}
