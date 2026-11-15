package id.lcscam.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.lcscam.app.audio.AudioSourceType
import id.lcscam.app.domain.model.OutputOrientation
import id.lcscam.app.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists all user settings with Jetpack DataStore. */
class SettingsRepository(private val context: Context, private val scope: CoroutineScope) {

    private object K {
        val sourceName = stringPreferencesKey("source_name")
        val groups = stringPreferencesKey("ndi_groups")
        val extraIps = stringPreferencesKey("ndi_extra_ips")
        val clockVideo = booleanPreferencesKey("clock_video")
        val lensKey = stringPreferencesKey("lens_key")
        val width = intPreferencesKey("width")
        val height = intPreferencesKey("height")
        val fps = intPreferencesKey("fps")
        // v2: FOLLOW_SCREEN became the default; older installs stored PORTRAIT on every save.
        val orientation = stringPreferencesKey("output_orientation_v2")
        val uiOrientation = stringPreferencesKey("ui_orientation")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val mirrorFront = booleanPreferencesKey("mirror_front")
        val audioEnabled = booleanPreferencesKey("audio_enabled")
        val audioDevice = intPreferencesKey("audio_device")
        val sampleRate = intPreferencesKey("sample_rate")
        val channels = intPreferencesKey("channels")
        val gainDb = floatPreferencesKey("gain_db")
        val audioSource = stringPreferencesKey("audio_source")
        val avOffset = intPreferencesKey("av_offset_ms")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val zoomDuration = intPreferencesKey("zoom_duration_sec")
        val language = stringPreferencesKey("language")
        val zebra = booleanPreferencesKey("ov_zebra")
        val zebraLevel = intPreferencesKey("ov_zebra_level")
        val peaking = booleanPreferencesKey("ov_peaking")
        val peakingSens = intPreferencesKey("ov_peaking_sens")
        val peakingColor = intPreferencesKey("ov_peaking_color")
        val thirds = booleanPreferencesKey("ov_thirds")
        val crosshair = booleanPreferencesKey("ov_crosshair")
        val safe = booleanPreferencesKey("ov_safe")
        val histogram = booleanPreferencesKey("ov_histogram")
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        val o = d.overlays
        return AppSettings(
            sourceName = this[K.sourceName] ?: d.sourceName,
            ndiGroups = this[K.groups] ?: d.ndiGroups,
            ndiExtraIps = this[K.extraIps] ?: d.ndiExtraIps,
            clockVideo = this[K.clockVideo] ?: d.clockVideo,
            lensKey = this[K.lensKey],
            width = this[K.width] ?: d.width,
            height = this[K.height] ?: d.height,
            fps = this[K.fps] ?: d.fps,
            outputOrientation = this[K.orientation]?.let { runCatching { OutputOrientation.valueOf(it) }.getOrNull() } ?: d.outputOrientation,
            uiOrientation = this[K.uiOrientation]?.let { runCatching { UiOrientation.valueOf(it) }.getOrNull() } ?: d.uiOrientation,
            onboardingDone = this[K.onboarding] ?: d.onboardingDone,
            mirrorFrontOutput = this[K.mirrorFront] ?: d.mirrorFrontOutput,
            audioEnabled = this[K.audioEnabled] ?: d.audioEnabled,
            audioDeviceId = this[K.audioDevice] ?: d.audioDeviceId,
            audioSampleRate = this[K.sampleRate] ?: d.audioSampleRate,
            audioChannels = this[K.channels] ?: d.audioChannels,
            audioGainDb = this[K.gainDb] ?: d.audioGainDb,
            audioSource = this[K.audioSource]?.let { runCatching { AudioSourceType.valueOf(it) }.getOrNull() } ?: d.audioSource,
            avOffsetMs = this[K.avOffset] ?: d.avOffsetMs,
            keepScreenOn = this[K.keepScreenOn] ?: d.keepScreenOn,
            zoomDurationSec = (this[K.zoomDuration] ?: d.zoomDurationSec).coerceIn(0, 8),
            language = this[K.language]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: d.language,
            overlays = OverlaySettings(
                zebra = this[K.zebra] ?: o.zebra,
                zebraLevel = this[K.zebraLevel] ?: o.zebraLevel,
                peaking = this[K.peaking] ?: o.peaking,
                peakingSensitivity = this[K.peakingSens] ?: o.peakingSensitivity,
                peakingColor = this[K.peakingColor] ?: o.peakingColor,
                thirds = this[K.thirds] ?: o.thirds,
                crosshair = this[K.crosshair] ?: o.crosshair,
                safeAreas = this[K.safe] ?: o.safeAreas,
                histogram = this[K.histogram] ?: o.histogram,
            ),
        )
    }

    /** Initial value is read synchronously once at startup so the first camera open uses saved settings. */
    val settings: StateFlow<AppSettings> = context.dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, runBlocking { context.dataStore.data.first().toSettings() })

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch {
            context.dataStore.edit { p ->
                val s = transform(p.toSettings())
                p[K.sourceName] = AppSettings.sanitizeSourceName(s.sourceName)
                p[K.groups] = s.ndiGroups.trim()
                p[K.extraIps] = s.ndiExtraIps.trim()
                p[K.clockVideo] = s.clockVideo
                val lensKey = s.lensKey
                if (lensKey != null) p[K.lensKey] = lensKey else p.remove(K.lensKey)
                p[K.width] = s.width
                p[K.height] = s.height
                p[K.fps] = s.fps
                p[K.orientation] = s.outputOrientation.name
                p[K.uiOrientation] = s.uiOrientation.name
                p[K.onboarding] = s.onboardingDone
                p[K.mirrorFront] = s.mirrorFrontOutput
                p[K.audioEnabled] = s.audioEnabled
                p[K.audioDevice] = s.audioDeviceId
                p[K.sampleRate] = s.audioSampleRate
                p[K.channels] = s.audioChannels
                p[K.gainDb] = s.audioGainDb
                p[K.audioSource] = s.audioSource.name
                p[K.avOffset] = s.avOffsetMs
                p[K.keepScreenOn] = s.keepScreenOn
                p[K.zoomDuration] = s.zoomDurationSec.coerceIn(0, 8)
                p[K.language] = s.language.name
                val o = s.overlays
                p[K.zebra] = o.zebra
                p[K.zebraLevel] = o.zebraLevel
                p[K.peaking] = o.peaking
                p[K.peakingSens] = o.peakingSensitivity
                p[K.peakingColor] = o.peakingColor
                p[K.thirds] = o.thirds
                p[K.crosshair] = o.crosshair
                p[K.safe] = o.safeAreas
                p[K.histogram] = o.histogram
            }
        }
    }
}
