package com.ndicamera.professional.domain.model

enum class Transport { WIFI, ETHERNET, CELLULAR, VPN, OTHER, NONE }

data class NetworkStatus(
    val connected: Boolean = false,
    val transport: Transport = Transport.NONE,
    val interfaceName: String? = null,
    val ipv4Addresses: List<String> = emptyList(),
    val ipv6Addresses: List<String> = emptyList(),
    val wifiLinkSpeedMbps: Int? = null,
    val wifiTxLinkSpeedMbps: Int? = null,
    val wifiRssiDbm: Int? = null,
    val wifiFrequencyMhz: Int? = null,
    /** NetworkCapabilities estimate. This is NOT a measurement of available bandwidth. */
    val estimatedUpstreamKbps: Int? = null,
    val estimatedDownstreamKbps: Int? = null,
    val validated: Boolean = false,
    val metered: Boolean = false,
    val interfaceChangeCount: Int = 0,
)

enum class NetworkWarning(val message: String) {
    DISCONNECTED("Network disconnected: NDI receivers cannot reach this device"),
    WEAK_WIFI("Weak Wi-Fi signal: expect frame drops"),
    LOW_LINK_SPEED("Wi-Fi link speed is low for the selected resolution"),
    INTERFACE_CHANGED("Network interface changed: NDI sender was restarted"),
    CELLULAR_ONLY("Only a cellular network is active: NDI requires a local network"),
    UNSTABLE_TRANSMISSION("Unstable transmission: frames are being dropped"),
    DISCOVERY_FAILED("This device could not discover its own NDI source (mDNS may be blocked)"),
}
