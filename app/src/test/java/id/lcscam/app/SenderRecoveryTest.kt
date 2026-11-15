package id.lcscam.app

import id.lcscam.app.domain.model.NetworkStatus
import id.lcscam.app.domain.model.Transport
import id.lcscam.app.streaming.SenderRecovery
import id.lcscam.app.streaming.SenderRecovery.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class SenderRecoveryTest {
    private val wifi = NetworkStatus(connected = true, transport = Transport.WIFI, interfaceName = "wlan0", ipv4Addresses = listOf("192.168.1.20"))
    private val wifiKey = SenderRecovery.networkKey(wifi)

    @Test fun keepsSenderWhenBurstEndsOnSameInterface() {
        assertEquals(Decision.KEEP, SenderRecovery.decide(true, wifiKey, false, wifi))
    }

    @Test fun recreatesOnNewAddress() {
        val renewed = wifi.copy(ipv4Addresses = listOf("192.168.1.31"))
        assertEquals(Decision.RECREATE, SenderRecovery.decide(true, wifiKey, false, renewed))
    }

    @Test fun recreatesOnHandoverToEthernet() {
        val eth = NetworkStatus(connected = true, transport = Transport.ETHERNET, interfaceName = "eth0", ipv4Addresses = listOf("10.0.0.5"))
        assertEquals(Decision.RECREATE, SenderRecovery.decide(true, wifiKey, false, eth))
    }

    @Test fun recreatesWhenLanCameBackAfterOutageEvenWithSameAddress() {
        assertEquals(Decision.RECREATE, SenderRecovery.decide(true, wifiKey, true, wifi))
    }

    @Test fun waitsWhileNoLan() {
        assertEquals(Decision.WAIT_FOR_NETWORK, SenderRecovery.decide(true, wifiKey, true, NetworkStatus()))
        val cellular = NetworkStatus(connected = false, transport = Transport.CELLULAR, interfaceName = "rmnet0", ipv4Addresses = listOf("100.64.1.2"))
        assertEquals(Decision.WAIT_FOR_NETWORK, SenderRecovery.decide(true, wifiKey, false, cellular))
    }

    @Test fun recreatesWhenSenderMissing() {
        assertEquals(Decision.RECREATE, SenderRecovery.decide(false, wifiKey, false, wifi))
    }

    @Test fun retryBackoffIsCapped() {
        assertEquals(1_000L, SenderRecovery.retryDelayMs(1))
        assertEquals(3_000L, SenderRecovery.retryDelayMs(3))
        assertEquals(5_000L, SenderRecovery.retryDelayMs(9))
        assertEquals(1_000L, SenderRecovery.retryDelayMs(0))
    }
}
