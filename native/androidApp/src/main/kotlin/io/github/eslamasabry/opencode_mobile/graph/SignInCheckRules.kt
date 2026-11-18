package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.agents.signin.SignInCheck

/**
 * The sign-in page's account read, shared by both wirings (NativePhoneSignIn and NativeSignInAgents). Owner,
 * 2026-11-18: "Login successful." in the terminal, and the page said "isn't signed in yet", because every answer that
 * was not a clean "signedIn" (a check held back beside a loaded conversation, a time-out, the phone's one check slot
 * taken) became "not signed in". Now only the agent's own "signed out" is; the rest is "could not check", tried again
 * within [TRIES] with growing pauses, and then said as such.
 */
internal object SignInCheckRules {
    /** Tries of one check: the first at once, then after [pauseMillis]. */
    const val TRIES: Int = 4

    /** The pause before the second try; each later one doubles it (2, 4, 8 s). */
    private const val FIRST_PAUSE_MILLIS: Long = 2_000L

    /** The pause before try [round] (0 for the first): at most 14 s of waiting in all, besides the reads. */
    fun pauseMillis(round: Int): Long =
        if (round <= 0) 0L else FIRST_PAUSE_MILLIS shl (round.coerceAtMost(TRIES - 1) - 1)

    private val CODE = Regex("^[A-Za-z]{1,40}$")

    /**
     * The answer in a private probe's projection (`{"state":…,"error":…}`), or null when the bridge gave none. Only
     * a fixed error code crosses into Details, never other text.
     */
    fun answer(projection: Map<String, Any?>?): SignInCheck {
        val error = (projection?.get("error") as? String)?.takeIf { CODE.matches(it) }
        return when {
            projection?.get("state") == "signedIn" -> SignInCheck.SignedIn
            projection?.get("state") == "signedOut" -> SignInCheck.SignedOut
            // fx's own word for a sign-in that ran out: the agent says it is not signed in.
            error == "signInExpired" -> SignInCheck.SignedOut
            else -> SignInCheck.CouldNotCheck(error ?: NO_ANSWER)
        }
    }

    /** The scope changed under the check (the phone's owner or profile): no answer about this sign-in. */
    const val STALE_SCOPE: String = "staleScope"

    /** The agent has no status check on this phone (not in the catalog, or no usable installation). */
    const val NO_PROBE: String = "probeUnavailable"

    /** The bridge itself gave no answer. */
    const val NO_ANSWER: String = "noAnswer"
}
