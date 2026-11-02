package com.ndicamera.professional.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import com.ndicamera.professional.domain.model.NetworkStatus
import com.ndicamera.professional.domain.model.Transport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * Tracks every local network (not only the default one: a Wi-Fi without internet access is
 * often not the default network, but it is exactly what NDI needs). Runs on its own thread.
 * Also measures the app's real transmit rate with TrafficStats, which unlike Wi-Fi link speed
 * reflects what is actually being sent.
 */
class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val thread = HandlerThread("network-monitor", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val handler = Handler(thread.looper)

    private val networks = mutableMapOf<Network, Pair<NetworkCapabilities?, LinkProperties?>>()
    private val _status = MutableStateFlow(NetworkStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()
    private val _txBitsPerSecond = MutableStateFlow(0L)
    val txBitsPerSecond: StateFlow<Long> = _txBitsPerSecond.asStateFlow()

    /** Emits when the primary local interface or its addresses change. */
    private val _interfaceChanges = MutableSharedFlow<NetworkStatus>(extraBufferCapacity = 4)
    val interfaceChanges: SharedFlow<NetworkStatus> = _interfaceChanges.asSharedFlow()

    private var lastKey: String? = null
    private var changeCount = 0
    private var lastTxBytes = -1L
    private var lastTxTime = 0L
    private var multicastLock: WifiManager.MulticastLock? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { networks[network] = networks[network] ?: Pair(null, null); recompute() }
        override fun onLost(network: Network) { networks.remove(network); recompute() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            networks[network] = Pair(caps, networks[network]?.second); recompute()
        }
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            networks[network] = Pair(networks[network]?.first, lp); recompute()
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        cm.registerNetworkCallback(request, callback, handler)
        handler.post(sampleTraffic)
    }

    fun stop() {
        try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        handler.removeCallbacks(sampleTraffic)
    }

    /** NDI discovery uses mDNS multicast; many Wi-Fi drivers drop multicast without this lock. */
    fun setMulticastLock(held: Boolean) = handler.post {
        if (held) {
            val lock = multicastLock ?: wifiManager.createMulticastLock("ndi-mdns").apply { setReferenceCounted(false) }
            multicastLock = lock
            if (!lock.isHeld) lock.acquire()
        } else {
            multicastLock?.takeIf { it.isHeld }?.release()
        }
    }

    val multicastLockHeld: Boolean get() = multicastLock?.isHeld == true

    private val sampleTraffic = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val tx = TrafficStats.getUidTxBytes(Process.myUid())
            if (tx >= 0 && lastTxBytes >= 0 && now > lastTxTime) {
                _txBitsPerSecond.value = (tx - lastTxBytes) * 8_000L / (now - lastTxTime)
            }
            lastTxBytes = tx
            lastTxTime = now
            // Refresh Wi-Fi RSSI/link speed periodically: callbacks do not fire for every change.
            recompute()
            handler.postDelayed(this, 1000)
        }
    }

    private fun transportOf(caps: NetworkCapabilities?): Transport = when {
        caps == null -> Transport.OTHER
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Transport.VPN
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
        else -> Transport.OTHER
    }

    private fun recompute() {
        val ranked = networks.entries.sortedBy {
            when (transportOf(it.value.first)) {
                Transport.ETHERNET -> 0; Transport.WIFI -> 1; Transport.OTHER -> 2; Transport.VPN -> 3
                Transport.CELLULAR -> 4; Transport.NONE -> 5
            }
        }
        val primary = ranked.firstOrNull()
        val caps = primary?.value?.first
        val lp = primary?.value?.second
        val transport = if (primary == null) Transport.NONE else transportOf(caps)
        val v4 = lp?.linkAddresses?.mapNotNull { (it.address as? Inet4Address)?.hostAddress } ?: emptyList()
        val v6 = lp?.linkAddresses?.mapNotNull { (it.address as? Inet6Address)?.hostAddress } ?: emptyList()
        var linkSpeed: Int? = null
        var txSpeed: Int? = null
        var rssi: Int? = null
        var freq: Int? = null
        if (transport == Transport.WIFI) {
            val info: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                caps?.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION") wifiManager.connectionInfo
            }
            info?.let {
                linkSpeed = it.linkSpeed.takeIf { s -> s > 0 }
                txSpeed = it.txLinkSpeedMbps.takeIf { s -> s > 0 }
                rssi = it.rssi.takeIf { r -> r > -127 && r != 0 }
                freq = it.frequency.takeIf { f -> f > 0 }
            }
        }
        val key = "${lp?.interfaceName}|${v4.joinToString()}"
        if (lastKey != null && key != lastKey) changeCount++
        val status = NetworkStatus(
            connected = primary != null && transport != Transport.CELLULAR,
            transport = transport,
            interfaceName = lp?.interfaceName,
            ipv4Addresses = v4,
            ipv6Addresses = v6,
            wifiLinkSpeedMbps = linkSpeed,
            wifiTxLinkSpeedMbps = txSpeed,
            wifiRssiDbm = rssi,
            wifiFrequencyMhz = freq,
            estimatedUpstreamKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
            estimatedDownstreamKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            interfaceChangeCount = changeCount,
        )
        _status.value = status
        if (lastKey != null && key != lastKey) _interfaceChanges.tryEmit(status)
        lastKey = key
    }
}
