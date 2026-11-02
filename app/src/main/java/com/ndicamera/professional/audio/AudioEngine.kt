package com.ndicamera.professional.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.ndicamera.professional.domain.model.AudioInputDevice
import com.ndicamera.professional.domain.model.AudioLevels
import com.ndicamera.professional.domain.model.AudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioSourceType(val label: String) {
    UNPROCESSED("Unprocessed (no AGC/NS)"),
    CAMCORDER("Camcorder"),
    MIC("Microphone (default)"),
    VOICE_RECOGNITION("Voice recognition"),
}

data class AudioConfig(
    val enabled: Boolean = true,
    val deviceId: Int = 0,
    val sampleRate: Int = 48_000,
    val channels: Int = 2,
    val gainDb: Float = 0f,
    val muted: Boolean = false,
    val source: AudioSourceType = AudioSourceType.UNPROCESSED,
)

/** Receives processed audio buffers on the audio-capture thread. */
fun interface AudioSink {
    fun onAudio(interleaved: FloatArray, frames: Int, channels: Int, sampleRate: Int, startMonotonicNs: Long)
}

/**
 * AudioRecord capture on a dedicated URGENT_AUDIO thread, 32-bit float PCM, 48 kHz preferred.
 * Supports input device selection (built-in, USB microphones/interfaces, wired headsets) and
 * reacts to USB attach/detach through AudioDeviceCallback.
 */
class AudioEngine(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<AudioState>(AudioState.Idle)
    val state: StateFlow<AudioState> = _state.asStateFlow()
    private val _levels = MutableStateFlow(AudioLevels())
    val levels: StateFlow<AudioLevels> = _levels.asStateFlow()
    private val _devices = MutableStateFlow<List<AudioInputDevice>>(emptyList())
    val devices: StateFlow<List<AudioInputDevice>> = _devices.asStateFlow()
    private val _silencedBySystem = MutableStateFlow(false)
    val silencedBySystem: StateFlow<Boolean> = _silencedBySystem.asStateFlow()

    @Volatile var sink: AudioSink? = null
    @Volatile private var config = AudioConfig()
    @Volatile private var running = false
    private var thread: Thread? = null
    private var sessionId = 0
    @Volatile private var peakHoldUntil = 0L

    val unprocessedSupported: Boolean =
        audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    init {
        refreshDevices()
        audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onDevicesChanged()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onDevicesChanged()
        }, mainHandler)
        audioManager.registerAudioRecordingCallback(object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                val mine = configs.firstOrNull { it.clientAudioSessionId == sessionId }
                _silencedBySystem.value = mine?.isClientSilenced == true
            }
        }, mainHandler)
    }

    fun currentConfig(): AudioConfig = config

    fun refreshDevices() {
        val list = mutableListOf(AudioInputDevice(0, "System default", "DEFAULT", emptyList(), emptyList(), false))
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).forEach { d ->
            list += AudioInputDevice(
                id = d.id,
                name = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeLabel(d.type),
                typeLabel = typeLabel(d.type),
                sampleRates = d.sampleRates.toList(),
                channelCounts = d.channelCounts.toList(),
                isUsb = d.type == AudioDeviceInfo.TYPE_USB_DEVICE || d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    d.type == AudioDeviceInfo.TYPE_USB_ACCESSORY,
            )
        }
        _devices.value = list
    }

    private fun onDevicesChanged() {
        refreshDevices()
        val cfg = config
        if (cfg.deviceId != 0 && _devices.value.none { it.id == cfg.deviceId }) {
            Log.w(TAG, "Selected input ${cfg.deviceId} removed; falling back to default")
            configure(cfg.copy(deviceId = 0))
        } else if (running) {
            // Routing may have changed (e.g. USB interface plugged in while on default).
            restart()
        }
    }

    fun configure(newConfig: AudioConfig) {
        val old = config
        config = newConfig
        val needsRestart = old.deviceId != newConfig.deviceId || old.sampleRate != newConfig.sampleRate ||
            old.channels != newConfig.channels || old.source != newConfig.source || old.enabled != newConfig.enabled
        if (running && needsRestart) restart()
        if (!newConfig.enabled && running) stop()
    }

    fun start() {
        if (running || !config.enabled) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = AudioState.PermissionRequired
            return
        }
        running = true
        thread = Thread({ captureLoop() }, "audio-capture").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
        _state.value = AudioState.Idle
        _levels.value = AudioLevels()
    }

    private fun restart() {
        stop()
        start()
    }

    @SuppressLint("MissingPermission")
    private fun captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var failures = 0
        while (running) {
            val cfg = config
            val record = try { createRecord(cfg) } catch (e: Exception) {
                Log.e(TAG, "AudioRecord creation failed", e); null
            }
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                _state.value = AudioState.Error("Cannot open audio input at ${cfg.sampleRate} Hz / ${cfg.channels} ch")
                if (++failures > 5) { running = false; break }
                SystemClock.sleep(500L * failures)
                continue
            }
            failures = 0
            sessionId = record.audioSessionId
            val channels = record.channelCount
            val rate = record.sampleRate
            val frames = AudioMath.framesPerBuffer(rate)
            val buf = FloatArray(frames * channels)
            val levels = FloatArray(2 * channels)
            val ts = AudioTimestamp()
            var framesRead = 0L
            val deviceName = record.routedDevice?.productName?.toString() ?: "default"
            try {
                record.startRecording()
                _state.value = AudioState.Capturing(deviceName, rate, channels, cfg.source.label)
                var lastLevelsMs = 0L
                while (running) {
                    val n = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        Log.e(TAG, "AudioRecord.read error $n")
                        _state.value = AudioState.Error("Audio input interrupted (error $n); reconnecting")
                        break
                    }
                    if (n == 0) continue
                    val got = n / channels
                    val c = config
                    NativeAudio.nativeProcess(buf, got, channels, AudioMath.dbToLinear(c.gainDb), c.muted, levels)
                    val startNs = if (record.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                        AudioMath.bufferStartNs(ts.framePosition, ts.nanoTime, framesRead, rate)
                    } else {
                        System.nanoTime() - got * 1_000_000_000L / rate
                    }
                    framesRead += got
                    sink?.onAudio(buf, got, channels, rate, startNs)
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastLevelsMs >= 50) {
                        lastLevelsMs = now
                        publishLevels(levels, channels, now)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio capture failed", e)
                _state.value = AudioState.Error("Audio capture failed: ${e.message}")
            } finally {
                try { record.stop() } catch (_: Exception) {}
                record.release()
            }
        }
    }

    private fun publishLevels(levels: FloatArray, channels: Int, now: Long) {
        val peak = FloatArray(channels) { AudioMath.linearToDb(levels[it]) }
        val rms = FloatArray(channels) { AudioMath.linearToDb(levels[channels + it]) }
        if (peak.any { it >= -0.1f }) peakHoldUntil = now + 1500
        _levels.value = AudioLevels(peak, rms, clipped = now < peakHoldUntil)
    }

    @SuppressLint("MissingPermission")
    private fun createRecord(cfg: AudioConfig): AudioRecord? {
        val mask = if (cfg.channels >= 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(cfg.sampleRate)
            .setChannelMask(mask)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(cfg.sampleRate, mask, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBuf <= 0) return null
        val source = when (cfg.source) {
            AudioSourceType.UNPROCESSED -> if (unprocessedSupported) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.CAMCORDER
            AudioSourceType.CAMCORDER -> MediaRecorder.AudioSource.CAMCORDER
            AudioSourceType.MIC -> MediaRecorder.AudioSource.MIC
            AudioSourceType.VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        val record = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuf * 2, AudioMath.framesPerBuffer(cfg.sampleRate) * cfg.channels * 4 * 4))
            .build()
        if (cfg.deviceId != 0) {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == cfg.deviceId }
                ?.let { record.setPreferredDevice(it) }
        }
        return record
    }

    private fun typeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in mic"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line in"
        AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
        else -> "Input type $type"
    }

    private companion object { const val TAG = "AudioEngine" }
}
