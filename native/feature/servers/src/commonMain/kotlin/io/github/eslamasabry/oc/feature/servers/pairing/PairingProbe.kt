package io.github.eslamasabry.oc.feature.servers.pairing

/** What one pairing address did when it was asked for its health/version (no credential is sent). */
public enum class PairingAnswer {
    /** A server answered and is usable. */
    Works,

    /** Something answered there (it wants a sign-in, it is starting, it is another version): the right host. */
    Answers,

    /** Nothing answered (refused, timed out, no route, a certificate the phone does not trust). */
    NoAnswer,
}

/** Asks one address whether a server is there (the app binds it to the protocol probes; never sends a credential). */
public fun interface PairingProbe {
    public suspend fun check(address: String): PairingAnswer
}

/** Where no probe is bound (desktop, tests): the form takes the best-ordered address without asking anyone. */
public object NoPairingProbe : PairingProbe {
    override suspend fun check(address: String): PairingAnswer = PairingAnswer.NoAnswer
}

/**
 * Which of a pairing code's addresses the form fills (Flutter `selectPairingUrl`, B-PROTO-068): the candidates are
 * tried in order and the first that works wins; failing that, the first that answered at all (a 401 from a stale
 * password still names the host); failing that, null and the form keeps the best-ordered guess. Plain HTTP to another
 * device is never probed before the person agrees to it (B-SEC-124).
 */
public object PairingChoice {
    public suspend fun choose(candidates: List<String>, probe: PairingProbe): String? {
        var answered: String? = null
        for (url in candidates) {
            if (!PairingText.mayProbe(url)) continue
            when (probe.check(url)) {
                PairingAnswer.Works -> return url
                PairingAnswer.Answers -> if (answered == null) answered = url
                PairingAnswer.NoAnswer -> Unit
            }
        }
        return answered
    }
}
