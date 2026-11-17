package id.lcscam.app.i18n

import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Language choice stored in settings. SYSTEM follows the device language and region. */
enum class AppLanguage { SYSTEM, INDONESIAN, ENGLISH }

/** Languages the interface is translated into. */
enum class Lang { EN, ID }

/**
 * In-app localisation. Every user-visible string is written as `tr(english, indonesian)` next
 * to where it is used, so a missing translation is a compile error rather than a silent
 * fallback. [current] is Compose snapshot state: changing the language in Settings recomposes
 * the whole interface immediately, without restarting the activity or the camera.
 */
object I18n {
    var current by mutableStateOf(resolve(AppLanguage.SYSTEM, systemLocale()))
        private set

    fun apply(choice: AppLanguage) {
        val l = resolve(choice, systemLocale())
        if (l != current) current = l
    }

    /**
     * SYSTEM picks Indonesian when the device language is Indonesian ("in"/"id") or the device
     * region is Indonesia; every other device gets English.
     */
    fun resolve(choice: AppLanguage, system: Locale): Lang = when (choice) {
        AppLanguage.INDONESIAN -> Lang.ID
        AppLanguage.ENGLISH -> Lang.EN
        AppLanguage.SYSTEM -> if (system.language == "in" || system.language == "id" || system.country == "ID") Lang.ID else Lang.EN
    }

    /** The device locale, unaffected by any per-app override. */
    fun systemLocale(): Locale = runCatching { Resources.getSystem().configuration.locales[0] }.getOrNull() ?: Locale.getDefault()
}

/** Returns the string for the current interface language. */
fun tr(en: String, id: String): String = if (I18n.current == Lang.ID) id else en
