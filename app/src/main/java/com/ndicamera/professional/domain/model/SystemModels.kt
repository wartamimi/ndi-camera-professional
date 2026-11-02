package com.ndicamera.professional.domain.model

data class SystemHealth(
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val batteryTempC: Float? = null,
    val thermalStatus: Int = 0, // PowerManager.THERMAL_STATUS_*
    val thermalHeadroom: Float? = null,
    val javaHeapUsedMb: Long = 0,
    val nativeHeapUsedMb: Long = 0,
) {
    val thermalLabel: String get() = when (thermalStatus) {
        0 -> "NORMAL"; 1 -> "LIGHT"; 2 -> "MODERATE"; 3 -> "SEVERE"; 4 -> "CRITICAL"; 5 -> "EMERGENCY"; 6 -> "SHUTDOWN"
        else -> "UNKNOWN"
    }
    val throttling: Boolean get() = thermalStatus >= 2
}
