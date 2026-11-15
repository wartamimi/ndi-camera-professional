package id.lcscam.app.domain.model

import id.lcscam.app.i18n.tr

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

enum class NetworkWarning {
    DISCONNECTED, WEAK_WIFI, LOW_LINK_SPEED, INTERFACE_CHANGED, CELLULAR_ONLY, UNSTABLE_TRANSMISSION, DISCOVERY_FAILED;

    val message: String get() = when (this) {
        DISCONNECTED -> tr("Network disconnected: NDI receivers cannot reach this device",
            "Jaringan terputus: receiver NDI tidak dapat menjangkau perangkat ini")
        WEAK_WIFI -> tr("Weak Wi-Fi signal: expect frame drops", "Sinyal Wi-Fi lemah: frame bisa terputus")
        LOW_LINK_SPEED -> tr("Wi-Fi link speed is low for the selected resolution",
            "Kecepatan link Wi-Fi terlalu rendah untuk resolusi yang dipilih")
        INTERFACE_CHANGED -> tr("Network interface changed: NDI sender was restarted",
            "Antarmuka jaringan berubah: sender NDI dimulai ulang")
        CELLULAR_ONLY -> tr("Only a cellular network is active: NDI requires a local network",
            "Hanya jaringan seluler yang aktif: NDI membutuhkan jaringan lokal")
        UNSTABLE_TRANSMISSION -> tr("Unstable transmission: frames are being dropped",
            "Transmisi tidak stabil: ada frame yang terbuang")
        DISCOVERY_FAILED -> tr("This device could not discover its own NDI source (mDNS may be blocked)",
            "Perangkat ini tidak menemukan source NDI-nya sendiri (mDNS mungkin diblokir)")
    }
}
