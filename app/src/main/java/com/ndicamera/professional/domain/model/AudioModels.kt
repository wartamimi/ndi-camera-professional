package com.ndicamera.professional.domain.model

data class AudioInputDevice(
    val id: Int,          // AudioDeviceInfo.id, or 0 for "system default"
    val name: String,
    val typeLabel: String,
    val sampleRates: List<Int>,
    val channelCounts: List<Int>,
    val isUsb: Boolean,
)

data class AudioLevels(
    val peakDb: FloatArray = floatArrayOf(-120f, -120f),
    val rmsDb: FloatArray = floatArrayOf(-120f, -120f),
    val clipped: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is AudioLevels && peakDb.contentEquals(other.peakDb) && rmsDb.contentEquals(other.rmsDb) && clipped == other.clipped
    override fun hashCode(): Int = 31 * peakDb.contentHashCode() + rmsDb.contentHashCode() + clipped.hashCode()
}

sealed interface AudioState {
    data object Idle : AudioState
    data object PermissionRequired : AudioState
    data class Capturing(val deviceName: String, val sampleRate: Int, val channels: Int, val source: String) : AudioState
    data class Error(val message: String) : AudioState
}
