package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorCause
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorText
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow

/** Issue 147: whether a reply's footer says the agent's own sign-in no longer works (a failed reply, ProviderAuth). */
internal object ChatSignInLapse {
    fun lapsed(footer: FooterRow): Boolean =
        footer.failed && !footer.stopped && footer.errorKind == MessageErrorKind.ProviderAuth &&
            limit(footer) == null

    /**
     * signin-truth: the agent's own words when a reply's footer is its plan's usage limit ("You've hit your weekly
     * limit · resets Nov 20, 1am (UTC)"), else null. Read only for the reset; a limit is never a lapsed sign-in.
     */
    fun limit(footer: FooterRow): String? =
        footer.reason?.takeIf {
            footer.failed && !footer.stopped &&
                AgentErrorText.classify(it).let { cause ->
                    cause == AgentErrorCause.UsageLimit || cause == AgentErrorCause.OutOfCredit
                }
        }
}
