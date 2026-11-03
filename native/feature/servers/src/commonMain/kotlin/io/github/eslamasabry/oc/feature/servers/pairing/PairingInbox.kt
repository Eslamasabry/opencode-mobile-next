package io.github.eslamasabry.oc.feature.servers.pairing

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the server form fills in from a pairing code or a share link. [password] null = keep what the person typed.
 * Never put in observable state or saved state: the form takes it once and drops it.
 */
public class PairingFill(
    public val address: String,
    public val username: String?,
    private var secret: String?,
    /** The code listed this many addresses; [address] is the one a phone should try first. */
    public val addresses: Int,
    public val fromLink: Boolean,
    /** Every address of the code in the order a phone tries them ([address] first); probing picks among them. */
    public val candidates: List<String> = listOf(address),
) {
    /** The password, once. */
    public fun takePassword(): String? = secret.also { secret = null }

    /** The same fill pointing at [chosen] (one of [candidates]); the password moves over and leaves this one. */
    public fun at(chosen: String): PairingFill = PairingFill(chosen, username, takePassword(), addresses, fromLink, candidates)

    override fun toString(): String = "PairingFill(address=${PairingText.safeOrigin(address)}, fromLink=$fromLink)"

    public companion object {
        /** A phone tries routable addresses before the server's own loopback (B-PROTO-067). */
        public fun of(read: PairingRead, preferLoopback: Boolean = false): PairingFill? =
            when (read) {
                is PairingRead.Code -> {
                    val payload = read.payload
                    val ordered = PairingText.orderCandidates(payload.urls, preferLoopback)
                    PairingFill(ordered.first(), payload.username, payload.password(), payload.urls.size, fromLink = false, ordered)
                        .also { payload.consume() }
                }
                is PairingRead.Link -> PairingFill(read.origin, null, null, 1, fromLink = true)
                is PairingRead.Problem -> null
            }
    }
}

/**
 * The scanner's hand-off to the server form (plan 07 E03 `PairingInbox`): memory only, one item, taken once. A scanned
 * code never travels in a route key or a saved state; the process dying drops it, which is the safe outcome.
 */
@SingleIn(AppScope::class)
@Inject
public class PairingInbox {
    private var held: PairingFill? = null
    private val counter = MutableStateFlow(0)

    /** Ticks whenever something new arrives; the form then calls [take]. */
    public val arrivals: StateFlow<Int> = counter.asStateFlow()

    public fun put(fill: PairingFill) {
        held = fill
        counter.update { it + 1 }
    }

    public fun take(): PairingFill? = held.also { held = null }
}
