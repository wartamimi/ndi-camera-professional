package id.lcscam.app.di

import android.content.Context
import android.hardware.camera2.CameraManager
import id.lcscam.app.audio.AudioEngine
import id.lcscam.app.billing.BillingRepository
import id.lcscam.app.camera.CameraEngine
import id.lcscam.app.camera.CameraRepository
import id.lcscam.app.camera.NativeFrameAnalyzer
import id.lcscam.app.diagnostics.SystemMonitor
import id.lcscam.app.domain.ndi.NdiRuntime
import id.lcscam.app.ndi.NativeNdiRuntime
import id.lcscam.app.network.NetworkMonitor
import id.lcscam.app.service.StreamingService
import id.lcscam.app.settings.SettingsRepository
import id.lcscam.app.streaming.StreamController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Manual constructor-injection container (process scoped). Chosen over Hilt to avoid
 * annotation processing in a project whose CI must also build without the proprietary SDK;
 * every dependency is still injected through constructors and replaceable in tests.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ndiDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "ndi-control") }.asCoroutineDispatcher()

    val settings = SettingsRepository(appContext, appScope)
    val ndiRuntime: NdiRuntime = NativeNdiRuntime(appContext)
    val cameraRepository = CameraRepository(appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
    val cameraEngine = CameraEngine(appContext, cameraRepository)
    val audioEngine = AudioEngine(appContext)
    val networkMonitor = NetworkMonitor(appContext).also { it.start() }
    val systemMonitor = SystemMonitor(appContext).also { it.start() }
    val analyzer = NativeFrameAnalyzer()
    val billing = BillingRepository(appContext)
    val streamController = StreamController(
        ndiRuntime, cameraEngine, audioEngine, networkMonitor, settings, analyzer, appScope, ndiDispatcher, billing.tier,
    ).also { it.serviceControl = StreamingService.controller(appContext) }
}
