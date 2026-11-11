package io.github.eslamasabry.oc.android.notifications

/** What to do with the phone server's own row (4747) when the conversations' state changes. */
public enum class ServerRowStep {
    /** Show the conversations' words there (a run works or waits for the person). */
    Lend,

    /** Give the server its own words back ("Ready"), because nothing runs and nothing waits any more. */
    Restore,

    /** Leave the row to whoever holds it now. */
    Leave,
}

/**
 * The phone server's own row says "Ready · nothing running". It must not say so while a conversation on the phone works
 * or waits for the person (a request alert shows, the reply watch has ended after its 10 s of no progress). This rule is
 * the whole decision; it holds no timer and starts no service: the app's process-lifetime state drives it.
 */
public object ServerRowPolicy {
    public fun step(
        conversationsShown: Boolean,
        lent: Boolean,
        replyWatchActive: Boolean,
        liveModeActive: Boolean,
        appVisible: Boolean,
    ): ServerRowStep =
        when {
            // The watch and live mode write the row themselves and hand it back through [ReplyRunSource.returnRowToServer].
            replyWatchActive || liveModeActive -> ServerRowStep.Leave
            // In the app the person sees the conversations themselves; the row only needs to stop claiming otherwise.
            appVisible -> if (lent) ServerRowStep.Restore else ServerRowStep.Leave
            conversationsShown -> ServerRowStep.Lend
            lent -> ServerRowStep.Restore
            else -> ServerRowStep.Leave
        }
}
