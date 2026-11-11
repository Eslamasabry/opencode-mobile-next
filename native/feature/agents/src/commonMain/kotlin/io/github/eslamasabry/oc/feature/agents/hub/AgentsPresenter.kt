package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.account.AgentAccountController
import kotlin.time.Clock
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Agents hub's logic (no Compose, no platform types). One tap admits one operation; a failed
 * step never navigates or retries on its own. A computer is saved only when the person taps Save —
 * the check alone saves nothing (Flutter `probePaseoConnection` then save).
 */
public class AgentsPresenter(
    private val scope: CoroutineScope,
    private val computers: AgentComputers,
    private val phone: PhoneAgents,
    private val onBack: () -> Unit,
    private val openChat: (ConversationRef) -> Unit,
    private val openComputerPage: (ProfileId) -> Unit = {},
    private val phoneHost: PhoneAgentHostStarter? = null,
    private val accounts: AgentAccounts? = null,
    private val clock: Clock? = null,
    private val drafts: AgentHandoffDrafts? = null,
) : AgentsActions {
    private val local = MutableStateFlow(AgentsUi(helperOffered = phoneHost != null))

    /** The running helper connection, the installed agents it was last tried for, and a wish to run once more. */
    private var connectJob: Job? = null
    private var triedFor: Set<String> = emptySet()
    private var restartedStopped = false
    private var connectAgain = false
    private var followJob: Job? = null
    private var readJob: Job? = null

    init {
        refreshPhone()
        // Phone agents connect on their own, as in Flutter: when the page opens with an agent installed, and again when
        // another agent finishes installing. Nothing is tried, and so nothing fails, while no agent is installed.
        if (phoneHost != null) scope.launch { phone.state.collect(::connectWhenInstalled) }
        // No Refresh button: while an agent installs, its tile follows the install on its own.
        scope.launch { phone.state.collect(::followInstall) }
    }

    private fun installing(phoneState: PhoneAgentsState): Boolean =
        phoneState.installation?.phase == PhoneAgentInstallPhase.Installing

    private fun followInstall(phoneState: PhoneAgentsState) {
        if (!installing(phoneState) || followJob?.isActive == true) return
        followJob =
            scope.launch {
                while (installing(phone.state.value)) {
                    delay(INSTALL_FOLLOW_MS)
                    phone.refresh()
                }
            }
    }

    override fun selectTab(tab: AgentsTab) {
        local.update { it.copy(tab = tab) }
        if (tab == AgentsTab.Computers) readComputers(force = false)
    }

    override fun pullRefresh() {
        if (local.value.refreshing) return
        local.update { it.copy(refreshing = true) }
        scope.launch {
            try {
                phone.refresh()
                if (local.value.tab == AgentsTab.Computers) readComputers(force = true)?.join()
            } finally {
                local.update { it.copy(refreshing = false) }
            }
        }
    }

    override fun newPhoneConversation() {
        val id = phone.state.value.conversations ?: return
        newConversation(id)
    }

    /** Reads each computer's ready agents (not the phone's own helper profile), without blocking the page. */
    private fun readComputers(force: Boolean): Job? {
        if (readJob?.isActive == true) return readJob
        val phoneProfile = phone.state.value.conversations
        val targets =
            computers.computers.value.filter {
                it.id != phoneProfile && (force || it.id !in local.value.ready)
            }
        if (targets.isEmpty()) return null
        readJob =
            scope.launch {
                for (computer in targets) {
                    when (val result = computers.agents(computer.id)) {
                        is Outcome.Ok ->
                            local.update {
                                it.copy(
                                    ready = (it.ready + (computer.id to result.value)).toPersistentMap(),
                                    unreadComputers = (it.unreadComputers - computer.id).toPersistentSet(),
                                )
                            }
                        is Outcome.Failed ->
                            local.update { it.copy(unreadComputers = (it.unreadComputers + computer.id).toPersistentSet()) }
                    }
                }
            }
        return readJob
    }

    public val state: StateFlow<AgentsUi> =
        combine(local, computers.computers, phone.state) { own, saved, phoneState ->
                own.copy(
                    computers = saved,
                    // While the page is starting the helper the recorded stop is already being answered.
                    phone = phoneState.withoutStaleStop(own.helperConnectFailed || own.helperConnecting),
                )
            }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                local.value.copy(computers = computers.computers.value, phone = phone.state.value),
            )

    /**
     * "The helper stopped" is a saved record of an earlier exit. Once a start after it failed, the page names that
     * failure instead (Flutter showed the record only while no helper ran and nothing newer had happened).
     */
    private fun PhoneAgentsState.withoutStaleStop(startFailed: Boolean): PhoneAgentsState =
        if (startFailed && (helperStoppedUnexpectedly || helperMayHaveBeenStoppedByAndroid))
            copy(helperStoppedUnexpectedly = false, helperMayHaveBeenStoppedByAndroid = false)
        else this

    private var accountController: AgentAccountController? = null
    private var accountCollection: Job? = null
    private var accountGeneration = 0
    private var draftGeneration = 0

    init {
        drafts?.let { source ->
            scope.launch {
                source.sources.collect { targets ->
                    local.update { it.copy(draftSources = targets) }
                    val selected = local.value.draftSource
                    if (selected != null && targets.none { it.id == selected.id }) closeDrafts()
                }
            }
        }
        accounts?.let { source ->
            scope.launch {
                source.accounts.collect { targets ->
                    local.update { it.copy(accounts = targets) }
                    val selected = local.value.accountTarget
                    if (selected != null && targets.none { it.id == selected.id }) closeAccount()
                }
            }
        }
    }

    override fun back() {
        when {
            local.value.sheetAgent != null -> closeAgent()
            local.value.installingAgent != null -> dismissInstall()
            local.value.draftSource != null -> closeDrafts()
            local.value.accountTarget != null -> closeAccount()
            else -> onBack()
        }
    }

    override fun openAccount(id: ProfileId) {
        val source = accounts ?: return
        val time = clock ?: return
        val target = source.accounts.value.firstOrNull { it.id == id } ?: return
        closeAccount()
        val generation = ++accountGeneration
        local.update {
            it.copy(accountTarget = target, accountOpening = true, accountFailed = false)
        }
        scope.launch {
            when (val opened = source.open(id)) {
                is Outcome.Failed ->
                    if (generation == accountGeneration) {
                        local.update { it.copy(accountOpening = false, accountFailed = true) }
                    }
                is Outcome.Ok -> {
                    if (generation != accountGeneration) {
                        opened.value.close()
                        return@launch
                    }
                    val controller = AgentAccountController(opened.value, scope, time)
                    accountController = controller
                    accountCollection = scope.launch {
                        controller.state.collect { panel ->
                            if (generation == accountGeneration)
                                local.update {
                                    it.copy(accountPanel = panel, accountOpening = false)
                                }
                        }
                    }
                    controller.refresh()
                }
            }
        }
    }

    override fun closeAccount() {
        ++accountGeneration
        accountController?.close()
        accountController = null
        accountCollection?.cancel()
        accountCollection = null
        local.update {
            it.copy(
                accountTarget = null,
                accountPanel = null,
                accountOpening = false,
                accountFailed = false,
            )
        }
    }

    override fun refreshAccount() {
        val controller = accountController ?: return
        scope.launch { controller.refresh() }
    }

    override fun signInAccount() {
        val controller = accountController ?: return
        scope.launch { controller.signIn() }
    }

    override fun cancelAccountSignIn() {
        val controller = accountController ?: return
        scope.launch { controller.cancel() }
    }

    override fun openAccountSignIn() {
        val code = accountController?.state?.value?.deviceCode ?: return
        val source = accounts ?: return
        val generation = accountGeneration
        scope.launch {
            if (
                generation != accountGeneration ||
                    accountController?.state?.value?.deviceCode !== code
            )
                return@launch
            val result = source.openSignIn(code.verificationUrl)
            if (generation == accountGeneration && result is Outcome.Failed) {
                local.update { it.copy(accountFailed = true) }
            }
        }
    }

    public fun close() {
        closeAccount()
        closeDrafts()
    }

    override fun openDrafts(id: ProfileId) {
        val source = drafts ?: return
        val target = source.sources.value.firstOrNull { it.id == id } ?: return
        closeDrafts()
        val generation = ++draftGeneration
        local.update { it.copy(draftSource = target, draftLoading = true) }
        scope.launch {
            val result = source.conversations(id)
            if (generation != draftGeneration) return@launch
            when (result) {
                is Outcome.Ok ->
                    local.update {
                        it.copy(draftConversations = result.value, draftLoading = false)
                    }
                is Outcome.Failed ->
                    local.update { it.copy(draftFailed = true, draftLoading = false) }
            }
        }
    }

    override fun closeDrafts() {
        ++draftGeneration
        local.update {
            it.copy(
                draftSource = null,
                draftConversations = kotlinx.collections.immutable.persistentListOf(),
                draftRecord = null,
                draftLoading = false,
                draftFailed = false,
                draftTooLarge = false,
                draftCopied = false,
            )
        }
    }

    override fun prepareDraft(id: SessionId) {
        val source = drafts ?: return
        val target = local.value.draftSource ?: return
        if (local.value.draftLoading || local.value.draftConversations.none { it.id == id }) return
        val generation = ++draftGeneration
        local.update {
            it.copy(
                draftLoading = true,
                draftRecord = null,
                draftCopied = false,
                draftFailed = false,
                draftTooLarge = false,
            )
        }
        scope.launch {
            val result = source.prepare(target.id, id)
            if (generation != draftGeneration) return@launch
            when (result) {
                is Outcome.Ok ->
                    local.update { it.copy(draftRecord = result.value, draftLoading = false) }
                is Outcome.Failed -> {
                    val failure = result.failure
                    val oversized =
                        failure is Failure.Rejected &&
                            failure.status == 413 &&
                            failure.tag?.raw == "handoff_record_too_large"
                    local.update {
                        it.copy(
                            draftFailed = !oversized,
                            draftTooLarge = oversized,
                            draftLoading = false,
                        )
                    }
                }
            }
        }
    }

    override fun copyDraft() {
        val record = local.value.draftRecord ?: return
        val copied = drafts?.copy(record.markdown) == true
        local.update { it.copy(draftCopied = copied, draftFailed = !copied) }
    }

    override fun selectComputerKind(kind: AgentComputerKind) {
        if (!local.value.busy)
            local.update { it.copy(computerKind = kind, check = null, issue = null) }
    }

    override fun startAdding() {
        local.update { it.copy(adding = true, check = null, issue = null) }
    }

    override fun cancelAdding() {
        if (local.value.busy) return
        local.update { it.copy(adding = false, check = null, issue = null) }
    }

    override fun edited() {
        if (local.value.check != null) local.update { it.copy(check = null) }
    }

    override fun check(address: String, password: String, folder: String): Unit = operation {
        when (
            val result =
                computers.check(address.trim(), password, folder.trim(), local.value.computerKind)
        ) {
            is Outcome.Ok -> local.update { it.copy(check = result.value) }
            is Outcome.Failed -> failed(AgentsIssue.CheckFailed, result.failure)
        }
    }

    override fun add(name: String, address: String, password: String, folder: String): Unit =
        operation {
            when (
                val result =
                    computers.add(
                        name.trim(),
                        address.trim(),
                        password,
                        folder.trim(),
                        local.value.computerKind,
                    )
            ) {
                is Outcome.Ok -> {
                    val ready = (local.value.check as? ComputerCheck.Ready)?.agents
                    local.update {
                        it.copy(
                            adding = false,
                            check = null,
                            savedGeneration = it.savedGeneration + 1,
                            issue = AgentsProblem(AgentsIssue.Added),
                            ready =
                                if (ready != null)
                                    (it.ready + (result.value to ready)).toPersistentMap()
                                else it.ready,
                        )
                    }
                }
                is Outcome.Failed -> failed(AgentsIssue.AddFailed, result.failure)
            }
        }

    override fun checkPhoneAgents(): Unit = operation {
        val result = phone.refresh()
        if (result is Outcome.Failed) failed(AgentsIssue.AgentsFailed, result.failure)
    }

    override fun readAgents(id: ProfileId): Unit = operation {
        when (val result = computers.agents(id)) {
            is Outcome.Ok ->
                local.update {
                    it.copy(ready = (it.ready + (id to result.value)).toPersistentMap())
                }
            is Outcome.Failed -> failed(AgentsIssue.AgentsFailed, result.failure)
        }
    }

    override fun newConversation(id: ProfileId): Unit = operation {
        when (val result = computers.newConversation(id)) {
            is Outcome.Ok -> openChat(result.value)
            is Outcome.Failed -> failed(AgentsIssue.StartFailed, result.failure)
        }
    }

    override fun openComputer(id: ProfileId): Unit = openComputerPage(id)

    override fun requestInstall(id: String) {
        if (local.value.busy) return
        val agent =
            phone.state.value.agents.firstOrNull {
                it.id == id &&
                    it.supportedInstallation &&
                    it.status == PhoneAgentStatus.NotInstalled
            } ?: return
        local.update { it.copy(installingAgent = agent, installIssue = null, sheetAgent = null) }
    }

    override fun dismissInstall() {
        if (!local.value.busy) local.update { it.copy(installingAgent = null) }
    }

    override fun installPhone(id: String): Unit = operation {
        // Try again after a failed install is allowed even when the agent is partly there.
        val retrying = phone.state.value.installation?.let { it.id == id && it.phase != PhoneAgentInstallPhase.Installing } == true
        if (phone.state.value.agents.none { it.id == id && (it.installable || retrying) }) return@operation
        val answer =
            when (val started = phone.install(id)) {
                is Outcome.Ok -> started.value
                is Outcome.Failed ->
                    PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable)
            }
        local.update {
            it.copy(
                installIssue = (answer as? PhoneAgentInstallResult.Refused)?.issue,
                installingAgent =
                    if (answer == PhoneAgentInstallResult.Started) null else it.installingAgent,
            )
        }
    }

    /**
     * "Update the agent helper": the install that sets up the helper again, run for an agent that is already there (its
     * own steps are skipped when they are in place).
     */
    override fun updatePhoneHelper(): Unit = operation {
        val id = phoneInstalledAgents(phone.state.value).sorted().firstOrNull() ?: return@operation
        val answer =
            when (val started = phone.install(id)) {
                is Outcome.Ok -> started.value
                is Outcome.Failed -> PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable)
            }
        local.update { it.copy(installIssue = (answer as? PhoneAgentInstallResult.Refused)?.issue) }
    }

    override fun dismissInstallResult(): Unit = operation {
        phone.dismissInstallResult()
        phone.refresh()
    }

    override fun cancelPhoneInstall(): Unit = operation {
        when (phone.cancelInstall()) {
            is Outcome.Ok -> local.update { it.copy(installIssue = null) }
            is Outcome.Failed ->
                local.update { it.copy(installIssue = PhoneAgentInstallIssue.Unavailable) }
        }
    }

    override fun refreshPhone(): Unit = operation {
        when (phone.refresh()) {
            is Outcome.Ok -> Unit
            is Outcome.Failed ->
                local.update {
                    it.copy(removal = PhoneAgentRemoval.Refused(PhoneAgentRemovalIssue.Unavailable))
                }
        }
    }

    override fun requestPreviousVersion(id: String) {
        if (local.value.busy) return
        val agent =
            phone.state.value.agents.firstOrNull { it.id == id && it.previousVersionAvailable }
                ?: return
        local.update {
            it.copy(
                sheetAgent = null,
                revertingAgent = agent,
                previousVersionFailed = false,
                previousVersionRestored = false,
            )
        }
    }

    override fun cancelPreviousVersion() {
        if (!local.value.busy) local.update { it.copy(revertingAgent = null) }
    }

    override fun confirmPreviousVersion() {
        val agent = local.value.revertingAgent ?: return
        operation {
            local.update { it.copy(revertingAgent = null) }
            val result = phone.usePreviousVersion(agent.id)
            local.update {
                it.copy(
                    previousVersionFailed = result is Outcome.Failed,
                    previousVersionRestored = result is Outcome.Ok,
                )
            }
            // The helper stopped for the switch: phone agents connect again on their own.
            if (result is Outcome.Ok && connectable(phone.state.value)) connectHelper()
        }
    }

    override fun openAgent(id: String) {
        if (phone.state.value.agents.any { it.id == id }) local.update { it.copy(sheetAgent = id) }
    }

    override fun closeAgent() {
        local.update { it.copy(sheetAgent = null) }
    }

    /** "Try again" on the failure line: the same connection the page makes on its own. */
    override fun connectPhoneHelper() {
        if (phoneHost == null || !connectable(phone.state.value)) return
        connectHelper()
    }

    private fun connectWhenInstalled(phoneState: PhoneAgentsState) {
        if (!connectable(phoneState)) return
        val installed = phoneInstalledAgents(phoneState)
        if (triedFor.containsAll(installed)) {
            restartStoppedOnce(phoneState)
            return
        }
        triedFor = triedFor + installed
        connectHelper()
    }

    /**
     * The helper was connected and then stopped on its own (Android ended it): the page starts it again by itself,
     * once. A second stop is left to the notice's own "Start the agent helper", so a phone that keeps ending the
     * helper is never restarted in a loop.
     */
    private fun restartStoppedOnce(phoneState: PhoneAgentsState) {
        val stopped = phoneState.helperStoppedUnexpectedly || phoneState.helperMayHaveBeenStoppedByAndroid
        if (!stopped || restartedStopped || connectJob?.isActive == true || local.value.helperConnectFailed) return
        restartedStopped = true
        connectHelper()
    }

    private fun connectable(phoneState: PhoneAgentsState): Boolean =
        phoneState.available && !phoneState.needsRestart && phoneInstalledAgents(phoneState).isNotEmpty()

    private fun connectHelper() {
        val host = phoneHost ?: return
        if (connectJob?.isActive == true) {
            connectAgain = true
            return
        }
        connectJob =
            scope.launch {
                // A connect asked for while one runs (Try again, another install) runs once more after it.
                do {
                    connectAgain = false
                    local.update { it.copy(helperConnecting = true, helperConnectFailed = false, helperConnectFailure = null) }
                    var result = host.connect()
                    // A helper that was only just started can refuse its first look and answer a moment later: look
                    // again a few times before the page says "not connected" (a manual Try again used to fix it).
                    var again = 0
                    while (
                        result is Outcome.Failed &&
                            result.failure.mayAnswerSoon() &&
                            again < AUTO_RETRIES &&
                            connectable(phone.state.value)
                    ) {
                        again++
                        delay(AUTO_RETRY_MS)
                        result = host.connect()
                    }
                    local.update {
                        it.copy(
                            helperConnecting = false,
                            helperConnectFailed = result is Outcome.Failed,
                            helperConnectFailure = (result as? Outcome.Failed)?.failure,
                            helperConnected = result is Outcome.Ok,
                        )
                    }
                    phone.refresh()
                } while (connectAgain && connectable(phone.state.value))
            }
    }

    override fun requestSignInReset() {
        if (!local.value.busy)
            local.update { it.copy(resettingSignIns = true, resetFailed = false) }
    }

    override fun cancelSignInReset() {
        if (!local.value.busy) local.update { it.copy(resettingSignIns = false) }
    }

    override fun confirmSignInReset(): Unit = operation {
        local.update { it.copy(resettingSignIns = false) }
        val answer = phone.resetSavedSignIns()
        local.update {
            it.copy(
                resetFailed = answer is Outcome.Failed,
                ready = kotlinx.collections.immutable.persistentMapOf(),
            )
        }
    }

    override fun requestSignOut(id: String) {
        if (local.value.busy) return
        val agent =
            phone.state.value.agents.firstOrNull { it.id == id && it.signOutAvailable } ?: return
        local.update { it.copy(signingOutAgent = agent, signOutFailed = null, sheetAgent = null) }
    }

    override fun cancelSignOut() {
        if (!local.value.busy) local.update { it.copy(signingOutAgent = null) }
    }

    override fun confirmSignOut() {
        val agent = local.value.signingOutAgent ?: return
        operation {
            local.update { it.copy(signingOutAgent = null, signOutFailed = null) }
            val answer = phone.signOut(agent.id)
            if (answer !is Outcome.Ok || !answer.value)
                local.update { it.copy(signOutFailed = agent.name) }
            // Re-read the real state either way; the status check decides, not the logout.
            phone.refresh()
        }
    }

    override fun requestRemoval(id: String) {
        if (local.value.busy) return
        val agent = phone.state.value.agents.firstOrNull { it.id == id && it.removable } ?: return
        local.update { it.copy(removingAgent = agent, removal = null, sheetAgent = null) }
    }

    override fun cancelRemoval() {
        if (!local.value.busy) local.update { it.copy(removingAgent = null) }
    }

    override fun confirmRemoval() {
        val agent = local.value.removingAgent ?: return
        operation {
            local.update { it.copy(removingAgent = null, removal = null) }
            val result =
                when (val removed = phone.remove(agent.id)) {
                    is Outcome.Ok -> removed.value
                    is Outcome.Failed ->
                        PhoneAgentRemoval.Refused(PhoneAgentRemovalIssue.Unavailable)
                }
            local.update { it.copy(removal = result) }
        }
    }

    private fun operation(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, issue = null) }
        scope.launch {
            try {
                block()
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private fun failed(kind: AgentsIssue, failure: Failure) {
        local.update { it.copy(issue = AgentsProblem(kind, failure)) }
    }

    private companion object {
        /** How often a running install is read again (the old "Tap Refresh to read its latest progress"). */
        const val INSTALL_FOLLOW_MS = 2_000L
        const val AUTO_RETRIES = 3
        const val AUTO_RETRY_MS = 5_000L
    }
}
