package io.github.eslamasabry.oc.engine.entry.pairing

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt

/** What one probe of one address found (the app maps its protocol probes onto these; no free text crosses here). */
public enum class PairingVerdict(
    /** An OpenCode server is there, whether or not it took the credentials (Flutter `PairingUrlOutcome.answered`). */
    public val answered: Boolean,
) {
    Connected(true),
    PasswordRejected(true),
    Starting(true),
    Unhealthy(true),
    Refused(false),
    TimedOut(false),
    HostNotFound(false),
    CertificateRejected(false),
    NoServer(false),
    NotOpenCode(false),
}

/** Why an address was not chosen. One sentence each in the UI; never probe or exception text. */
public enum class PairingReason {
    InvalidAddress,
    CredentialsInUrl,
    QueryOrFragment,
    PathPresent,

    /** Plain HTTP to another device: held until the person confirms; nothing was sent to it. */
    CleartextNeedsConfirm,

    /** The probe itself failed (threw) before the server could be checked. */
    ProbeFailed,
    PasswordRejected,
    Refused,
    TimedOut,
    HostNotFound,
    CertificateRejected,
    Unhealthy,
    Starting,
    NoServer,
    NotOpenCode,
    ;

    internal companion object {
        fun of(verdict: PairingVerdict): PairingReason? =
            when (verdict) {
                PairingVerdict.Connected -> null
                PairingVerdict.PasswordRejected -> PasswordRejected
                PairingVerdict.Starting -> Starting
                PairingVerdict.Unhealthy -> Unhealthy
                PairingVerdict.Refused -> Refused
                PairingVerdict.TimedOut -> TimedOut
                PairingVerdict.HostNotFound -> HostNotFound
                PairingVerdict.CertificateRejected -> CertificateRejected
                PairingVerdict.NoServer -> NoServer
                PairingVerdict.NotOpenCode -> NotOpenCode
            }
    }
}

/** The pairing code's sign-in, handed to the probe exactly as the code carried it. Never printed. */
public class PairingCredentials(public val username: String?, public val password: String?) {
    override fun toString(): String = "PairingCredentials(username=${if (username == null) "none" else "set"}, password=***)"
}

/** Asks one address whether an OpenCode server is there, signing in with [credentials]. Bound by the app graph. */
public fun interface PairingAddressProbe {
    public suspend fun probe(baseUrl: String, credentials: PairingCredentials): PairingVerdict
}

/** One address's fate. [url] is the address as the app would use it (normalized). */
public data class PairingAddressOutcome(
    val url: String,
    val probed: Boolean,
    val reason: PairingReason?,
    val verdict: PairingVerdict? = null,
) {
    val needsCleartextConfirm: Boolean get() = reason == PairingReason.CleartextNeedsConfirm
    val answered: Boolean get() = verdict?.answered == true
}

/** One line of the per-address explanation: a safe origin (null: "an invalid address") and why. */
public data class PairingFailureLine(val address: String?, val reason: PairingReason)

/** The result of trying every address of a pairing code. */
public data class PairingSelection(
    val outcomes: List<PairingAddressOutcome>,
    val chosenUrl: String? = null,
    val chosenVerdict: PairingVerdict? = null,
) {
    val ok: Boolean get() = chosenUrl != null

    /** A server answered AND took the credentials. */
    val connected: Boolean get() = chosenVerdict == PairingVerdict.Connected

    /** Why nothing worked, address by address, with only [PairingAddress.display] origins (Flutter `failureDetail`). */
    val failureLines: List<PairingFailureLine>
        get() = outcomes.map { PairingFailureLine(PairingAddress.display(it.url), it.reason ?: PairingReason.NoServer) }
}

/**
 * Picks the pairing address to use (Flutter `selectPairingUrl`, B-PROTO-068). Every address goes through the URL
 * policy first; malformed ones are reported, never dialed; plain HTTP to another device is held (not probed) unless
 * its origin is in [confirmedCleartextOrigins]. The rest are probed in order — loopback first when [preferLoopback]
 * (a desktop), routable first otherwise (a phone) — and the first that connects wins. A probe that throws is isolated
 * and the next address is tried. When none connects, the first that answered (a 401 from a stale password) is chosen
 * with its honest verdict.
 */
public object PairingAddressSelector {
    public suspend fun select(
        urls: List<String>,
        credentials: PairingCredentials,
        probe: PairingAddressProbe,
        preferLoopback: Boolean,
        confirmedCleartextOrigins: Set<String> = emptySet(),
    ): PairingSelection {
        val outcomes = mutableListOf<PairingAddressOutcome>()
        val loopback = mutableListOf<String>()
        val routable = mutableListOf<String>()
        for (raw in urls) {
            when (val check = PairingAddress.check(raw)) {
                is PairingAddressCheck.Unusable -> outcomes += PairingAddressOutcome(check.url, probed = false, reason = check.reason)
                is PairingAddressCheck.Usable ->
                    if (check.cleartext && check.cleartextOrigin !in confirmedCleartextOrigins) {
                        outcomes += PairingAddressOutcome(check.url, probed = false, reason = PairingReason.CleartextNeedsConfirm)
                    } else {
                        (if (check.loopback) loopback else routable) += check.url
                    }
            }
        }
        val ordered = if (preferLoopback) loopback + routable else routable + loopback
        for (url in ordered) {
            val result = attempt({ Failure.Unexpected() }) { probe.probe(url, credentials) }
            val verdict = (result as? Outcome.Ok)?.value
            if (verdict == null) {
                outcomes += PairingAddressOutcome(url, probed = true, reason = PairingReason.ProbeFailed)
                continue
            }
            outcomes += PairingAddressOutcome(url, probed = true, reason = PairingReason.of(verdict), verdict = verdict)
            if (verdict == PairingVerdict.Connected) return PairingSelection(outcomes, url, verdict)
        }
        val answered = outcomes.firstOrNull { it.answered }
        return if (answered != null) PairingSelection(outcomes, answered.url, answered.verdict) else PairingSelection(outcomes)
    }
}
