package io.github.eslamasabry.oc.feature.handoff.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffPhase
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
    /** Why no agent can take this conversation over, in plain words on the screen; null when none is known. */
    val block: HandoffBlock? = null,
)

/** Which page the person is on. Each ends in something they can do (see the screen). */
public enum class HandoffPage {
    Loading,

    /** The folder check (or the read) said no: the plain reason, a way forward, the record only as detail. */
    Refused,

    /** Folder fine, but no agent is signed in and ready: the way to Agents. */
    NoAgent,

    /** Folder fine and at least one qualified agent: choose, review, send. */
    Choose,

    /** A review, send or result is under way: the buttons follow the attempt's phase. */
    Active,
}

public fun AgentHandoffUi.page(): HandoffPage =
    when {
        loading -> HandoffPage.Loading
        !connected -> HandoffPage.Refused
        attempt.phase != HandoffPhase.Idle -> HandoffPage.Active
        choices.none { it.issue == null } -> HandoffPage.NoAgent
        else -> HandoffPage.Choose
    }

/**
 * The refused page offers "Open Agents" unless the reason is the conversation itself (a computer, a folder outside
 * the phone's projects, no folder): then Agents cannot help. A refusal with no known reason, or "agents not
 * confirmed", is exactly when checking the agents is the way forward.
 */
public fun AgentHandoffUi.offersAgents(): Boolean =
    block == null || block == HandoffBlock.NotConfirmed

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
