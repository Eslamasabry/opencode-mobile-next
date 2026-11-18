package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateDecision
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the automatic agent update ([PhoneAgentAutoUpdate]) last decided, kept where the problem report can read it
 * without the updater: one plain line ("agent update: waiting, agents busy").
 */
@SingleIn(AppScope::class)
@Inject
class PhoneAgentUpdateStatus(val choices: PhoneUpdateChoices) {
    private val state = MutableStateFlow(AgentUpdateDecision(AgentUpdateStep.NotLooked))

    val decision: StateFlow<AgentUpdateDecision> = state.asStateFlow()

    fun set(decision: AgentUpdateDecision) {
        state.value = decision
    }

    /** The line the problem report's Environment block carries. */
    fun line(): String = state.value.line()
}
