package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.StopReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.AgentSignIn
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.persona.AgentPersona

/**
 * What a stopped run's alert says, decided from typed values only (B-SEC-104): the run's end, the
 * row's agent. No words of the agent or the server reach here.
 */
internal object StoppedAlertRules {
    /**
     * The reason token of a stopped run. The agent's own sign-in that ran out comes first
     * (B-ANDR-131); never signed in and a refused key keep what their kind says, and OpenCode's
     * provider key keeps its own sentence.
     */
    fun stopReason(failed: RunOutcome.Failed): StopReason =
        if (failed.agentSignIn == AgentSignIn.Expired) {
            StopReason.SignInAgain
        } else {
            when (failed.kind) {
                MessageErrorKind.ProviderAuth -> StopReason.Auth
                MessageErrorKind.ContextOverflow -> StopReason.Context
                MessageErrorKind.OutputLength -> StopReason.Length
                MessageErrorKind.ContentFilter -> StopReason.Filter
                MessageErrorKind.ModelNotFound -> StopReason.Model
                else -> StopReason.Other
            }
        }

    /**
     * The agent's name a stopped alert may say: the app's own product name for the row's agent
     * (the one Home's rows show), empty when the app does not know it. Never the row's label or
     * anything the agent or server wrote (B-SEC-231).
     */
    fun agentName(item: ChatFeedItem): String = AgentPersona.of(item.agentId).productName.orEmpty()

    /**
     * A stopped alert already posted with the plain "Something went wrong." is said again, in
     * place, once the run's typed reason arrives (the list can report the failure before the run's
     * end does). Nothing else is repeated.
     */
    fun restates(posted: StopReason?, now: StopReason): Boolean =
        posted == StopReason.Other && now != StopReason.Other

    /**
     * The run's end was already said: the same kind was claimed (unless a stopped alert can now say
     * its typed reason), or a stopped alert stands and a finish would only replace it.
     */
    fun alreadySaid(claimed: AlertKind?, kind: AlertKind, posted: StopReason?, reason: StopReason): Boolean {
        val stopped = kind == AlertKind.Error
        if (claimed == kind) return !(stopped && restates(posted, reason))
        return claimed == AlertKind.Error && kind == AlertKind.Complete
    }
}
