package com.ndicamera.professional.di

import android.content.Context
import android.hardware.camera2.CameraManager
import com.ndicamera.professional.audio.AudioEngine
import com.ndicamera.professional.camera.CameraEngine
import com.ndicamera.professional.camera.CameraRepository
import com.ndicamera.professional.camera.NativeFrameAnalyzer
import com.ndicamera.professional.diagnostics.SystemMonitor
import com.ndicamera.professional.domain.ndi.NdiRuntime
import com.ndicamera.professional.ndi.NativeNdiRuntime
import com.ndicamera.professional.network.NetworkMonitor
import com.ndicamera.professional.service.StreamingService
import com.ndicamera.professional.settings.SettingsRepository
import com.ndicamera.professional.streaming.StreamController
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
    val streamController = StreamController(
        ndiRuntime, cameraEngine, audioEngine, networkMonitor, settings, analyzer, appScope, ndiDispatcher,
    ).also { it.serviceControl = StreamingService.controller(appContext) }
}
