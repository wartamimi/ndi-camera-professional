package id.lcscam.app.diagnostics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.Process
import id.lcscam.app.domain.model.SystemHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Battery, thermal and memory monitoring (polled every 2 s on a background thread). */
class SystemMonitor(private val context: Context) {
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val thread = HandlerThread("system-monitor", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val handler = Handler(thread.looper)
    private val _health = MutableStateFlow(SystemHealth())
    val health: StateFlow<SystemHealth> = _health.asStateFlow()
    @Volatile private var thermalStatus = PowerManager.THERMAL_STATUS_NONE

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        thermalStatus = status
        handler.post(sampleOnce)
    }

    private val sampleOnce = Runnable { sample() }
    private val loop = object : Runnable {
        override fun run() { sample(); handler.postDelayed(this, 2000) }
    }

    fun start() {
        thermalStatus = power.currentThermalStatus
        power.addThermalStatusListener(context.mainExecutor, thermalListener)
        handler.post(loop)
    }

    fun stop() {
        power.removeThermalStatusListener(thermalListener)
        handler.removeCallbacks(loop)
    }

    private fun sample() {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            power.getThermalHeadroom(10).takeIf { !it.isNaN() }
        } else null
        val rt = Runtime.getRuntime()
        _health.value = SystemHealth(
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = plugged,
            batteryTempC = if (temp != Int.MIN_VALUE) temp / 10f else null,
            thermalStatus = thermalStatus,
            thermalHeadroom = headroom,
            javaHeapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
            nativeHeapUsedMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024),
        )
    }
}
