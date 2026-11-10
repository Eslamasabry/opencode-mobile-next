package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
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
    val removingAgent: PhoneAgentRow? = null,
    val removal: PhoneAgentRemoval? = null,
    val installIssue: PhoneAgentInstallIssue? = null,
    val installingAgent: PhoneAgentRow? = null,
    val revertingAgent: PhoneAgentRow? = null,
    val previousVersionFailed: Boolean = false,
    val previousVersionRestored: Boolean = false,
    val helperOffered: Boolean = false,
    /** The page is connecting phone agents on its own (an agent is installed); never a button. */
    val helperConnecting: Boolean = false,
    /** Only a real failed connection: never set while no agent is installed. */
    val helperConnectFailed: Boolean = false,
    val helperConnectFailure: Failure? = null,
    val helperConnected: Boolean = false,
    /** Which tab of the page shows: this phone's agents or the computers'. */
    val tab: AgentsTab = AgentsTab.Phone,
    /** A pull to refresh is running. */
    val refreshing: Boolean = false,
    /** Computers whose agents could not be read on the last try. */
    val unreadComputers: kotlinx.collections.immutable.ImmutableSet<ProfileId> = kotlinx.collections.immutable.persistentSetOf(),
    /** The agent whose sheet is open (its actions: install, sign in, sign out, earlier version, remove). */
    val sheetAgent: String? = null,
    val resettingSignIns: Boolean = false,
    val resetFailed: Boolean = false,
    val signingOutAgent: PhoneAgentRow? = null,
    val signOutFailed: String? = null,
)

public enum class AgentsTab {
    Phone,
    Computers,
}

public interface AgentsActions {
    public fun selectTab(tab: AgentsTab) {}

    /** Pull to refresh: this phone's agents, and on the computers tab every computer's agents. */
    public fun pullRefresh() {}

    /** A new conversation with the phone's agents (a ready tile's New chat). */
    public fun newPhoneConversation() {}

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

    public fun openPhone() {}

    public fun checkPhoneAgents() {}

    public fun readAgents(id: ProfileId)

    public fun newConversation(id: ProfileId)

    /**
     * BUILD-PROTOCOLS: opens the computer's page (update its agents, import Claude Code
     * conversations).
     */
    public fun openComputer(id: ProfileId) {}

    public fun requestInstall(id: String) {}

    public fun dismissInstall() {}

    public fun signInPhone(id: String) {}

    public fun installPhone(id: String) {}

    public fun cancelPhoneInstall() {}

    /** "Update the agent helper" when the helper is missing or older than the app can run. */
    public fun updatePhoneHelper() {}

    /** Puts the last failed or interrupted install's notice away. */
    public fun dismissInstallResult() {}

    public fun refreshPhone()

    public fun requestPreviousVersion(id: String) {}

    public fun cancelPreviousVersion() {}

    public fun confirmPreviousVersion() {}

    /** "Try again" after a failed connection; the page connects on its own otherwise. */
    public fun connectPhoneHelper() {}

    public fun openAgent(id: String) {}

    public fun closeAgent() {}

    public fun requestSignInReset() {}

    public fun cancelSignInReset() {}

    public fun confirmSignInReset() {}

    public fun requestSignOut(id: String) {}

    public fun cancelSignOut() {}

    public fun confirmSignOut() {}

    public fun requestRemoval(id: String)

    public fun cancelRemoval()

    public fun confirmRemoval()
}
