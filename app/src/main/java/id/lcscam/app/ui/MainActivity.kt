package id.lcscam.app.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.lcscam.app.LcsCamApp
import id.lcscam.app.camera.OrientationMath
import id.lcscam.app.settings.UiOrientation
import id.lcscam.app.ui.about.AboutScreen
import id.lcscam.app.ui.camera.CameraScreen
import id.lcscam.app.ui.camera.CameraViewModel
import id.lcscam.app.ui.diagnostics.NetworkDiagnosticsScreen
import id.lcscam.app.ui.onboarding.OnboardingScreen
import id.lcscam.app.ui.plans.PlansScreen
import id.lcscam.app.ui.diagnostics.PerformanceDiagnosticsScreen
import id.lcscam.app.ui.settings.AudioSettingsScreen
import id.lcscam.app.ui.settings.CameraSettingsScreen
import id.lcscam.app.ui.settings.NdiSettingsScreen
import id.lcscam.app.ui.settings.SettingsHome
import id.lcscam.app.ui.theme.Cine
import id.lcscam.app.ui.theme.LcsCamTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as LcsCamApp).container
    private var permissionsGranted by mutableStateOf(false)
    private var orientationListener: OrientationEventListener? = null
    private var snapped = 0

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsGranted = hasCameraPermission()
        if (permissionsGranted) container.streamController.ensureCameraOpen()
        container.audioEngine.refreshDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        permissionsGranted = hasCameraPermission()
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                val s = OrientationMath.snap(orientation, snapped)
                if (s != snapped) {
                    snapped = s
                    container.streamController.onDeviceOrientation(s)
                }
            }
        }
        setContent {
            LcsCamTheme {
                val settings by container.settings.settings.collectAsStateWithLifecycle()
                LaunchedEffect(settings.uiOrientation) {
                    requestedOrientation = when (settings.uiOrientation) {
                        UiOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        UiOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        UiOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                    }
                }
                LaunchedEffect(settings.keepScreenOn) {
                    if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                when {
                    !settings.onboardingDone -> OnboardingScreen {
                        container.settings.update { it.copy(onboardingDone = true) }
                        if (!hasCameraPermission()) requestPermissions()
                    }
                    !permissionsGranted -> PermissionScreen { requestPermissions() }
                    else -> AppNav()
                }
            }
        }
        if (!permissionsGranted && container.settings.settings.value.onboardingDone) requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        orientationListener?.takeIf { it.canDetectOrientation() }?.enable()
    }

    override fun onPause() {
        orientationListener?.disable()
        super.onPause()
    }

    private fun hasCameraPermission() = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(perms.toTypedArray())
    }

    @Composable
    private fun AppNav() {
        val nav = rememberNavController()
        val c = container
        NavHost(nav, startDestination = "camera", modifier = Modifier.fillMaxSize().background(Cine.Black)) {
            composable("camera") {
                val vm: CameraViewModel = viewModel(factory = CameraViewModel.Factory(c))
                CameraScreen(vm, onOpenSettings = { nav.navigate("settings") }, onOpenPlans = { nav.navigate("plans") })
            }
            val back: () -> Unit = { nav.popBackStack() }
            composable("settings") { SettingsHome(back) { nav.navigate(it) } }
            composable("settings/camera") { CameraSettingsScreen(c, back) }
            composable("settings/ndi") { NdiSettingsScreen(c, back) }
            composable("settings/audio") { AudioSettingsScreen(c, back) }
            composable("network") { NetworkDiagnosticsScreen(c, back) }
            composable("diagnostics") { PerformanceDiagnosticsScreen(c, back) }
            composable("about") { AboutScreen(c, back) }
            composable("plans") { PlansScreen(c, back) }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Cine.Black).safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access required", color = Cine.Text, fontSize = 20.sp)
        Text("LCS Cam needs the camera to send video and the microphone to send audio over NDI. " +
            "If you denied permanently, enable them in Android Settings › Apps.", color = Cine.Dim, fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 16.dp))
        Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = Cine.Accent)) {
            Text("GRANT PERMISSIONS", color = Cine.Black)
        }
    }
}
