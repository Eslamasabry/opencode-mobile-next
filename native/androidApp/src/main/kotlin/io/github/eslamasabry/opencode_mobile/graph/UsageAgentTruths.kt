package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.plans.presentation.PlanAgentTruth
import io.github.eslamasabry.oc.feature.usage.presentation.UsageAgentTruth

/**
 * What each phone agent says about itself, for the Usage page's Limits tab. A reader that finds no account cannot tell
 * a plan limit, an unconfirmed check and a real sign-out apart; the agent's own state can. Only a sign-in check that ran
 * and found no sign-in is "signed out"; a check that could not run, or an agent that is not in a state to answer
 * (checking, stopped, unreachable), is "not confirmed". Agents that are not installed say nothing.
 */
internal object UsageAgentTruths {
    fun of(rows: List<PhoneAgentRow>): Map<String, UsageAgentTruth> =
        buildMap {
            for (row in rows) {
                val truth =
                    when (row.status) {
                        PhoneAgentStatus.Limit -> UsageAgentTruth.Limit(row.limitResets)
                        PhoneAgentStatus.Ready -> UsageAgentTruth.SignedIn
                        PhoneAgentStatus.SignInNeeded ->
                            if (row.signInCheckFailed) UsageAgentTruth.NotConfirmed(row.name)
                            else UsageAgentTruth.SignedOut
                        PhoneAgentStatus.NotInstalled -> null
                        PhoneAgentStatus.SetUpAtFirstUse,
                        PhoneAgentStatus.Checking,
                        PhoneAgentStatus.PhoneCheck,
                        PhoneAgentStatus.Stopped,
                        PhoneAgentStatus.Unavailable -> UsageAgentTruth.NotConfirmed(row.name)
                    }
                if (truth != null) put(row.id, truth)
            }
        }

    fun planOf(rows: List<PhoneAgentRow>): Map<String, PlanAgentTruth> =
        of(rows).mapValues { (_, truth) ->
            when (truth) {
                UsageAgentTruth.SignedIn -> PlanAgentTruth.SignedIn
                UsageAgentTruth.SignedOut -> PlanAgentTruth.SignedOut
                is UsageAgentTruth.Limit -> PlanAgentTruth.Limit(truth.resets)
                is UsageAgentTruth.NotConfirmed -> PlanAgentTruth.NotConfirmed(truth.agentName)
            }
        }
}
