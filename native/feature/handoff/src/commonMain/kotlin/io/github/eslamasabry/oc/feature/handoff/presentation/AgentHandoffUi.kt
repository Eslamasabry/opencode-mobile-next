package io.github.eslamasabry.oc.feature.handoff.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public data class AgentHandoffUi(
    val loading: Boolean = true,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val title: String = "",
    val directory: String = "",
    val choices: ImmutableList<AgentHandoffChoice> = persistentListOf(),
    val selected: String? = null,
    val links: ImmutableList<AgentHandoffLink> = persistentListOf(),
    val attempt: HandoffState = HandoffState(),
    val prompt: String = "",
    val failure: Failure? = null,
    val sourceReview: AgentHandoffReview? = null,
)

public interface AgentHandoffActions {
    public fun back()

    public fun refresh()

    public fun select(id: String)

    public fun prepare()

    public fun editPrompt(value: String)

    public fun send()

    public fun retryLink()

    public fun openOriginal()

    public fun openCreated()

    public fun openLink(id: String, continued: Boolean)

    public fun setup()
}
