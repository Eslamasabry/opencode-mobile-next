package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.account.AgentAccountController
import kotlin.time.Clock
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val accounts: AgentAccounts? = null,
    private val clock: Clock? = null,
    private val drafts: AgentHandoffDrafts? = null,
) : AgentsActions {
    private val local = MutableStateFlow(AgentsUi())

    init {
        refreshPhone()
    }

    public val state: StateFlow<AgentsUi> =
        combine(local, computers.computers, phone.state) { own, saved, phoneState ->
                own.copy(computers = saved, phone = phoneState)
            }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                local.value.copy(computers = computers.computers.value, phone = phone.state.value),
            )

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

    override fun installPhone(id: String): Unit = operation {
        if (phone.state.value.agents.none { it.id == id && it.installable }) return@operation
        val answer =
            when (val started = phone.install(id)) {
                is Outcome.Ok -> started.value
                is Outcome.Failed ->
                    PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable)
            }
        local.update { it.copy(installIssue = (answer as? PhoneAgentInstallResult.Refused)?.issue) }
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

    override fun requestRemoval(id: String) {
        if (local.value.busy) return
        val agent = phone.state.value.agents.firstOrNull { it.id == id && it.removable } ?: return
        local.update { it.copy(removingAgent = agent, removal = null) }
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
}
