package io.github.eslamasabry.oc.feature.servers.pairing

import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException

/** A fixed verdict only: upstream errors and credentials never enter screen state. */
public enum class PairingAnswer {
    Works,
    Answers,
    NoAnswer,
    TimedOut,
    Refused,
    ProbeFailed,
    InvalidAddress,
    NeedsCleartextConsent,
}

/** Asks one trusted address; credentials remain in memory and travel in headers only. */
public fun interface PairingProbe {
    public suspend fun check(address: String): PairingAnswer

    public suspend fun check(address: String, credentials: ConnectionCredentials?): PairingAnswer =
        check(address)
}

/** Desktop has no server pairing probe. */
public object NoPairingProbe : PairingProbe {
    override suspend fun check(address: String): PairingAnswer = PairingAnswer.NoAnswer
}

/** Display-only origins; no untrusted URL, credential, or exception text is retained here. */
public data class PairingCheck(val origin: String, val answer: PairingAnswer)

public data class PairingChecks(val chosen: String?, val outcomes: ImmutableList<PairingCheck>)

/**
 * Tries candidates in order. A usable host wins over one asking for a password. Plain HTTP to
 * another device waits for the form's explicit consent, and one failed probe never prevents trying
 * the next address (B-PROTO-068).
 */
public object PairingChoice {
    public suspend fun choose(candidates: List<String>, probe: PairingProbe): String? =
        inspect(candidates, probe).chosen

    public suspend fun inspect(
        candidates: List<String>,
        probe: PairingProbe,
        credentials: ConnectionCredentials? = null,
    ): PairingChecks {
        var answered: String? = null
        val outcomes = mutableListOf<PairingCheck>()
        for (raw in candidates) {
            val url = PairingText.normalizeCandidate(raw)
            val verdict =
                when {
                    url == null -> PairingAnswer.InvalidAddress
                    !PairingText.mayProbe(url) -> PairingAnswer.NeedsCleartextConsent
                    else ->
                        try {
                            probe.check(url, credentials)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            PairingAnswer.ProbeFailed
                        }
                }
            outcomes += PairingCheck(PairingText.safeOrigin(url ?: raw), verdict)
            when (verdict) {
                PairingAnswer.Works -> return PairingChecks(url, outcomes.toImmutableList())
                PairingAnswer.Answers -> if (answered == null) answered = url
                else -> Unit
            }
        }
        return PairingChecks(answered, outcomes.toImmutableList())
    }
}
