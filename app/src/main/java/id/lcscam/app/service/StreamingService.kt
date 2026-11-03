package id.lcscam.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import id.lcscam.app.LcsCamApp
import id.lcscam.app.R
import id.lcscam.app.streaming.ForegroundServiceControl
import id.lcscam.app.ui.MainActivity

/**
 * Foreground service that keeps the process (camera, microphone, NDI sender) alive while
 * streaming, including when the UI is in the background. It is started from the foreground
 * (START STREAM tap), as Android 11+ requires for camera/microphone foreground services.
 * Background camera access is ultimately subject to the OEM's policies.
 */
class StreamingService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as LcsCamApp).container.streamController.stopStreaming()
            return START_NOT_STICKY
        }
        val name = intent?.getStringExtra(EXTRA_SOURCE) ?: "NDI"
        val withMic = intent?.getBooleanExtra(EXTRA_MIC, false) == true &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (withMic) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(name), type)
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException; streaming continues while visible.
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        acquireLocks()
        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Partial wake lock only while streaming so the CPU keeps feeding NDI with the screen off.
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NDICamera:streaming").apply {
                setReferenceCounted(false); acquire(12 * 60 * 60 * 1000L)
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "NDICamera:wifi").apply {
                setReferenceCounted(false); acquire()
            }
        }
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away: stop cleanly so camera and NDI resources are released.
        (application as LcsCamApp).container.streamController.stopStreaming()
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotification(source: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, StreamingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(source)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop_stream), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "StreamingService"
        private const val CHANNEL = "streaming"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_MIC = "mic"
        const val ACTION_STOP = "id.lcscam.app.STOP"

        fun controller(context: Context) = object : ForegroundServiceControl {
            private val app = context.applicationContext
            override fun startStreamingService(sourceName: String, withMicrophone: Boolean) {
                val i = Intent(app, StreamingService::class.java)
                    .putExtra(EXTRA_SOURCE, sourceName).putExtra(EXTRA_MIC, withMicrophone)
                try { app.startForegroundService(i) } catch (e: Exception) { Log.e(TAG, "startForegroundService failed", e) }
            }
            override fun stopStreamingService() {
                app.stopService(Intent(app, StreamingService::class.java))
            }
        }
    }
}
