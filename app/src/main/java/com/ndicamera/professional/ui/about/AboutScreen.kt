package com.ndicamera.professional.ui.about

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
import com.ndicamera.professional.BuildConfig
import com.ndicamera.professional.di.AppContainer
import com.ndicamera.professional.ui.components.KeyValue
import com.ndicamera.professional.ui.components.SectionTitle
import com.ndicamera.professional.ui.components.SettingsPage
import com.ndicamera.professional.ui.theme.Cine

@Composable
fun AboutScreen(c: AppContainer, onBack: () -> Unit) = SettingsPage("About", onBack) {
    val version by c.streamController.ndiVersion.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val tier by c.billing.tier.collectAsStateWithLifecycle()
    Text("NDI Camera Professional", color = Cine.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("Professional mobile broadcast camera", color = Cine.Dim, fontSize = 13.sp)
    Text("Dibuat oleh Munawar Tamimi", color = Cine.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp))
    SectionTitle("Pengembang")
    KeyValue("Developer", "Munawar Tamimi")
    KeyValue("Paket aktif", tier.label)
    Text("© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} Munawar Tamimi. All rights reserved.",
        color = Cine.Dim, fontSize = 12.sp)
    SectionTitle("Build")
    KeyValue("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    KeyValue("Build type", BuildConfig.BUILD_TYPE + if (BuildConfig.NDI_VALIDATION_BUILD) " · CI validation (no NDI SDK)" else "")
    KeyValue("NDI® SDK runtime", version ?: "not loaded")
    SectionTitle("NDI®")
    Text("This application uses the NDI® SDK to send video and audio over IP. " +
        "NDI® is a registered trademark of Vizrt NDI AB.", color = Cine.Text, fontSize = 13.sp)
    Text("https://ndi.video/", color = Cine.Accent, fontSize = 14.sp, textDecoration = TextDecoration.Underline,
        modifier = Modifier.padding(vertical = 8.dp).clickable {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ndi.video/")))
        })
    Text("NDI Tools (Studio Monitor, Access Manager) for testing reception: https://ndi.video/tools/",
        color = Cine.Dim, fontSize = 12.sp)
    SectionTitle("Licences")
    Text("The NDI SDK is licensed under the NDI SDK License Agreement and is not covered by this project's source licence. " +
        "Third-party notices for libndi are shipped by NDI in libndi_licenses.txt. " +
        "AndroidX and Kotlin libraries are licensed under the Apache License 2.0.", color = Cine.Dim, fontSize = 12.sp)
}
