package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.account.AccountPanelState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * Field values and the password never enter this state; the screen keeps them in unsaved text
 * fields.
 */
public data class AgentsUi(
    val phone: PhoneAgentsState = PhoneAgentsState(),
    val computers: ImmutableList<AgentComputer> = persistentListOf(),
    /** Ready agents per computer, once read (a check or the row's "Check agents"). */
    val ready: ImmutableMap<ProfileId, ImmutableList<String>> = persistentMapOf(),
    val busy: Boolean = false,
    /** The last pairing check of the typed values; cleared by an edit. */
    val check: ComputerCheck? = null,
    val issue: AgentsProblem? = null,
    /** Moves after a save so the screen can clear the password field. */
    val savedGeneration: Int = 0,
    val adding: Boolean = false,
    val computerKind: AgentComputerKind = AgentComputerKind.Paseo,
    val accounts: ImmutableList<AgentAccountTarget> = persistentListOf(),
    val accountTarget: AgentAccountTarget? = null,
    val accountPanel: AccountPanelState? = null,
    val accountOpening: Boolean = false,
    val accountFailed: Boolean = false,
    val draftSources: ImmutableList<AgentDraftSource> = persistentListOf(),
    val draftSource: AgentDraftSource? = null,
    val draftConversations: ImmutableList<AgentDraftConversation> = persistentListOf(),
    val draftRecord: AgentDraftRecord? = null,
    val draftLoading: Boolean = false,
    val draftFailed: Boolean = false,
    val draftTooLarge: Boolean = false,
    val draftCopied: Boolean = false,
)

public interface AgentsActions {
    public fun back()

    public fun openAccount(id: ProfileId) {}

    public fun closeAccount() {}

    public fun refreshAccount() {}

    public fun signInAccount() {}

    public fun cancelAccountSignIn() {}

    public fun openAccountSignIn() {}

    public fun selectComputerKind(kind: AgentComputerKind) {}

    public fun openDrafts(id: ProfileId) {}

    public fun closeDrafts() {}

    public fun prepareDraft(id: SessionId) {}

    public fun copyDraft() {}

    public fun startAdding()

    public fun cancelAdding()

    /** Any edit to the add form invalidates the last check. */
    public fun edited()

    public fun check(address: String, password: String, folder: String)

    public fun add(name: String, address: String, password: String, folder: String)

    public fun checkPhoneAgents() {}

    public fun readAgents(id: ProfileId)

    public fun newConversation(id: ProfileId)
}
