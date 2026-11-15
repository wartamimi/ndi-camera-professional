package id.lcscam.app.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery optimisation is the most common reason a long background stream is killed (Xiaomi,
 * Oppo, Vivo, Samsung and others stop apps even while a foreground service is running).
 * We do not request REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (restricted by Google Play policy);
 * instead the user is sent to the system screen where they can set the app to "Unrestricted".
 */
object BatteryOptimization {
    fun isRestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the app's battery settings, falling back to the global optimisation list. */
    fun openSettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        for (i in candidates) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}
