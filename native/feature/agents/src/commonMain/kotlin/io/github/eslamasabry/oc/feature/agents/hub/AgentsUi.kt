package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.ids.ProfileId
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
    val removingAgent: PhoneAgentRow? = null,
    val removal: PhoneAgentRemoval? = null,
    val installIssue: PhoneAgentInstallIssue? = null,
    val revertingAgent: PhoneAgentRow? = null,
    val previousVersionFailed: Boolean = false,
    val previousVersionRestored: Boolean = false,
    val helperOffered: Boolean = false,
    val helperConnectFailed: Boolean = false,
    val helperConnected: Boolean = false,
    val resettingSignIns: Boolean = false,
    val resetFailed: Boolean = false,
)

public interface AgentsActions {
    public fun back()

    public fun startAdding()

    public fun cancelAdding()

    /** Any edit to the add form invalidates the last check. */
    public fun edited()

    public fun check(address: String, password: String, folder: String)

    public fun add(name: String, address: String, password: String, folder: String)

    public fun readAgents(id: ProfileId)

    public fun newConversation(id: ProfileId)

    public fun installPhone(id: String) {}

    public fun cancelPhoneInstall() {}

    public fun refreshPhone()

    public fun requestPreviousVersion(id: String) {}

    public fun cancelPreviousVersion() {}

    public fun confirmPreviousVersion() {}

    public fun connectPhoneHelper() {}

    public fun requestSignInReset() {}

    public fun cancelSignInReset() {}

    public fun confirmSignInReset() {}

    public fun requestRemoval(id: String)

    public fun cancelRemoval()

    public fun confirmRemoval()
}
