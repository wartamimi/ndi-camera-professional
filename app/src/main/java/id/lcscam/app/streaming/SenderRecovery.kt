package id.lcscam.app.streaming

import id.lcscam.app.domain.model.NetworkStatus

/** Pure decision logic for keeping the NDI sender alive across network changes (unit-tested). */
object SenderRecovery {
    /** Quiet period after the last network callback before the sender is touched. */
    const val NETWORK_SETTLE_MS = 1_200L
    private const val MAX_RETRY_DELAY_MS = 5_000L

    enum class Decision { KEEP, RECREATE, WAIT_FOR_NETWORK }

    /** Identifies the LAN a sender is bound to: interface plus its IPv4 addresses. */
    fun networkKey(st: NetworkStatus): String = "${st.interfaceName}|${st.ipv4Addresses.joinToString(",")}"

    /**
     * - No usable LAN (no network, or cellular only): wait, recreating now would bind to nothing.
     * - Same interface and addresses as when the sender was created, and the LAN never dropped
     *   in between (e.g. a cellular network appeared or disappeared, or a burst of callbacks
     *   ended where it began): keep the sender, so connected receivers are not kicked.
     * - Anything else: recreate it on the new interface.
     */
    fun decide(hasSender: Boolean, senderKey: String?, lostSinceSender: Boolean, current: NetworkStatus): Decision = when {
        !current.connected -> Decision.WAIT_FOR_NETWORK
        hasSender && !lostSinceSender && senderKey == networkKey(current) -> Decision.KEEP
        else -> Decision.RECREATE
    }

    /** Backoff between failed NDIlib_send_create attempts: 1 s, 2 s, ... capped at 5 s. */
    fun retryDelayMs(attempt: Int): Long = (1_000L * attempt.coerceAtLeast(1)).coerceAtMost(MAX_RETRY_DELAY_MS)
}
