package io.github.eslamasabry.oc.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.model.TerminalShellSettings
import io.github.eslamasabry.oc.core.model.realShells
import io.github.eslamasabry.oc.core.model.sameShellPath
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ComputerRoute
import io.github.eslamasabry.oc.ui.routes.ServerIntegrationPage
import io.github.eslamasabry.oc.ui.routes.ServerIntegrationRoute
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import io.github.eslamasabry.oc.ui.routes.ServerSettingsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class ServerSettingsFacts(
    val name: String,
    val address: String,
    val connected: Boolean,
    val hasSecret: Boolean,
    val lastVersion: String?,
    val canCheck: Boolean,
    val capabilities: ServerCapabilities?,
    val tokenSignIn: Boolean = false,
    val managedOnPhone: Boolean = false,
    val connectionFailure: Failure? = null,
    /** The pinned command that updates this server on its own computer; null when the app cannot tell one. */
    val updateCommands: String? = null,
)

public enum class ConnectionStep {
    Address,
    SignIn,
    Version,
    LiveUpdates,
    Speed,
}

public enum class ConnectionMark {
    Waiting,
    Running,
    Pass,
    Warn,
    Fail,
    Skipped,
}

public enum class ConnectionReason {
    Ok,
    Unreachable,
    NotOpenCode,
    PasswordMissing,
    PasswordRejected,
    VersionUnsupported,
    NotReady,
    LiveNotChecked,
    LiveConnecting,
    LiveDown,
    Slow,
    EarlierFailed,
}

public data class ConnectionResult(
    val step: ConnectionStep,
    val mark: ConnectionMark,
    val reason: ConnectionReason = ConnectionReason.Ok,
    val version: String? = null,
    val millis: Long? = null,
)

public interface ServerSettingsSource {
    public fun watch(profile: ProfileId): Flow<ServerSettingsFacts?>

    public suspend fun health(profile: ProfileId): Outcome<Health>

    public suspend fun check(profile: ProfileId): Outcome<ImmutableList<ConnectionResult>>

    public suspend fun shells(profile: ProfileId): Outcome<TerminalShellSettings>

    public suspend fun selectShell(
        profile: ProfileId,
        selected: String,
    ): Outcome<TerminalShellSettings>

    public suspend fun connect(profile: ProfileId): Outcome<Unit>

    public suspend fun disconnect(profile: ProfileId): Outcome<Unit>

    public suspend fun recheckAgents(profile: ProfileId): Outcome<ImmutableList<String>>
}

public data class ShellChoice(
    /** What the server is asked to save: the shell's absolute path ("" = automatic). */
    val value: String,
    val label: String?,
    val terminalOnly: Boolean = false,
    /** The bare name, which an older save may hold instead of the path. */
    val name: String? = null,
)

/**
 * One row per real shell (a merged-/usr Linux lists `/bin/sh` and `/usr/bin/sh` as two), named by its name; the path
 * names it only when two different shells share a name (Flutter default_shell_row `_shellChoices`). Custom selections
 * the server does not list are kept.
 */
public fun shellChoices(settings: TerminalShellSettings): ImmutableList<ShellChoice> {
    val real = settings.realShells()
    val names = real.groupingBy { it.name }.eachCount()
    val rows = mutableListOf(ShellChoice("", null))
    val seen = mutableSetOf("")
    for (option in real) {
        if (seen.add(option.path))
            rows.add(
                ShellChoice(
                    option.path,
                    if (names[option.name] == 1) option.name else option.path,
                    !option.acceptable,
                    option.name,
                )
            )
    }
    if (settings.selected.isNotEmpty() && rows.none { it.matches(settings.selected) }) {
        rows.add(ShellChoice(settings.selected, settings.selected))
    }
    return rows.toImmutableList()
}

private fun ShellChoice.matches(saved: String): Boolean =
    value == saved ||
        (name != null && name == saved) ||
        (value.isNotEmpty() && saved.isNotEmpty() && sameShellPath(value, saved))

/** The value of the row that stands for what the server saved (a saved name matches its shell's path). */
public fun TerminalShellSettings.selectedValue(): String =
    shellChoices(this).firstOrNull { it.matches(selected) }?.value ?: selected

public data class ServerSettingsUi(
    val page: ServerSettingsPage,
    val loading: Boolean = true,
    val facts: ServerSettingsFacts? = null,
    val health: Health? = null,
    val checkingHealth: Boolean = false,
    val checking: Boolean = false,
    val results: ImmutableList<ConnectionResult> = persistentListOf(),
    val shells: TerminalShellSettings? = null,
    val working: Boolean = false,
    val failure: Failure? = null,
    val local: ServerLocalFacts? = null,
    val modelChoices: ImmutableList<ServerModelChoice> = persistentListOf(),
    val modelWorking: Boolean = false,
    val resetDone: Boolean = false,
    val disconnectConfirm: Boolean = false,
    val agents: ImmutableList<String>? = null,
    val automations: ImmutableList<ServerAutomation> = persistentListOf(),
    val automationsLoading: Boolean = false,
    val automationsWorking: Boolean = false,
    val automationsLoadFailed: Boolean = false,
    val automationsHidden: Boolean = false,
    val selectedAutomationId: String? = null,
    val automationSheetFailed: Boolean = false,
    val deleteAutomationConfirmId: String? = null,
    val stopAllAutomationsConfirm: Boolean = false,
    val automationsNotice: AutomationsNotice? = null,
    val nowMs: Long = 0L,
    val timeZoneId: String? = null,
) {
    /** What the Home page's top bar names (the screen turns it into words). */
    public val homeTitle: ServerSettingsTitle
        get() {
            val f = facts ?: return ServerSettingsTitle.Generic
            // P-114: the built-in server is saved as "This phone", which is also This phone's own
            // page; its settings say "Server on this phone" so the two pages never share a title.
            return if (f.managedOnPhone) ServerSettingsTitle.PhoneServer
            else ServerSettingsTitle.Named(f.name)
        }

    public val showAutomations: Boolean
        get() =
            facts?.connected == true &&
                facts.capabilities?.serverAutomations == true &&
                !automationsHidden

    public val selectedAutomation: ServerAutomation?
        get() = selectedAutomationId?.let { id -> automations.firstOrNull { it.id == id } }

    public val deleteAutomationTarget: ServerAutomation?
        get() = deleteAutomationConfirmId?.let { id -> automations.firstOrNull { it.id == id } }

    public val activeAutomationsCount: Int
        get() = automations.count { it.status == AutomationStatus.Active }
}

/** The Home page title: the saved name, the phone's own server, or "Server settings" while loading. */
public sealed interface ServerSettingsTitle {
    public data class Named(val name: String) : ServerSettingsTitle

    public data object PhoneServer : ServerSettingsTitle

    public data object Generic : ServerSettingsTitle
}

public interface ServerSettingsActions {
    public fun back()

    public fun refresh()

    public fun edit()

    public fun connect()

    public fun checkConnection()

    public fun showShells()

    public fun selectShell(value: String)

    public fun showCapabilities()

    public fun savedPermissions()

    public fun computerTools()

    public fun reachHelp()

    public fun askRemove()

    public fun showModels()

    public fun selectModel(model: ModelRef?, variant: String = "")

    public fun watching(enabled: Boolean)

    public fun notifications(enabled: Boolean)

    public fun shareRead(enabled: Boolean)

    public fun resetDefaults()

    public fun askDisconnect()

    public fun cancelDisconnect()

    public fun disconnect()

    public fun recheckAgents()

    public fun integration(page: ServerIntegrationPage)

    public fun refreshAutomations()

    public fun openAutomation(id: String)

    public fun closeAutomation()

    public fun pauseAutomation(id: String)

    public fun resumeAutomation(id: String)

    public fun askDeleteAutomation(id: String)

    public fun cancelDeleteAutomation()

    public fun confirmDeleteAutomation()

    public fun askStopAllAutomations()

    public fun cancelStopAllAutomations()

    public fun confirmStopAllAutomations()
}

@AssistedInject
public class ServerSettingsViewModel(
    @Assisted private val route: ServerSettingsRoute,
    private val source: ServerSettingsSource,
    private val automationsSource: ServerAutomationsSource,
    private val preferences: ServerPreferencesSource,
    private val nav: AppNavigator,
    private val clock: Clock,
) : ViewModel(), ServerSettingsActions {
    private val mutable = MutableStateFlow(ServerSettingsUi(page = route.page))
    public val state: StateFlow<ServerSettingsUi> = mutable.asStateFlow()
    private var automationsMutationVersion: Long = 0L

    init {
        viewModelScope.launch {
            preferences.watch(route.profile).collect { result ->
                when (result) {
                    is Outcome.Ok -> mutable.update { it.copy(local = result.value) }
                    is Outcome.Failed ->
                        mutable.update { it.copy(local = null, failure = result.failure) }
                }
            }
        }
        viewModelScope.launch {
            source.watch(route.profile).collect { facts ->
                val previous = mutable.value.facts
                val firstLive = facts?.connected == true && previous?.connected != true
                val supportsAutomations =
                    facts?.connected == true && facts.capabilities?.serverAutomations == true
                val automationsBecameReady =
                    supportsAutomations &&
                        (previous?.connected != true ||
                            previous.capabilities?.serverAutomations != true)
                mutable.update { current ->
                    val resetAutomations = !supportsAutomations
                    current.copy(
                        loading = false,
                        facts = facts,
                        automations =
                            if (resetAutomations) persistentListOf() else current.automations,
                        automationsLoadFailed =
                            if (resetAutomations) false else current.automationsLoadFailed,
                        automationsHidden =
                            if (resetAutomations) false else current.automationsHidden,
                        selectedAutomationId =
                            if (resetAutomations) null else current.selectedAutomationId,
                        automationSheetFailed =
                            if (resetAutomations) false else current.automationSheetFailed,
                        deleteAutomationConfirmId =
                            if (resetAutomations) null else current.deleteAutomationConfirmId,
                        stopAllAutomationsConfirm =
                            if (resetAutomations) false else current.stopAllAutomationsConfirm,
                        nowMs = clock.now().toEpochMilliseconds(),
                    )
                }
                if (firstLive && route.page == ServerSettingsPage.Home) {
                    refresh()
                    loadModelNames()
                }
                if (automationsBecameReady && route.page == ServerSettingsPage.Home) {
                    refreshAutomations()
                }
            }
        }
        if (
            route.page == ServerSettingsPage.Check ||
                route.page == ServerSettingsPage.Shell ||
                route.page == ServerSettingsPage.Model
        )
            refresh()
    }

    override fun back(): Unit = nav.back()

    override fun refresh() {
        when (route.page) {
            ServerSettingsPage.Check -> runCheck()
            ServerSettingsPage.Shell -> loadShells()
            ServerSettingsPage.Model -> loadModels()
            ServerSettingsPage.Capabilities,
            ServerSettingsPage.Reach -> Unit
            ServerSettingsPage.Home -> {
                if (mutable.value.checkingHealth) return
                mutable.update { it.copy(checkingHealth = true, health = null, failure = null) }
                viewModelScope.launch {
                    when (val result = source.health(route.profile)) {
                        is Outcome.Ok ->
                            mutable.update {
                                it.copy(checkingHealth = false, health = result.value)
                            }
                        is Outcome.Failed ->
                            mutable.update {
                                it.copy(
                                    checkingHealth = false,
                                    health = null,
                                    failure = result.failure,
                                )
                            }
                    }
                }
            }
        }
    }

    private fun runCheck() {
        if (mutable.value.checking) return
        mutable.update {
            it.copy(
                checking = true,
                failure = null,
                results =
                    ConnectionStep.entries
                        .map { step ->
                            ConnectionResult(
                                step,
                                if (step == ConnectionStep.Address) ConnectionMark.Running
                                else ConnectionMark.Waiting,
                            )
                        }
                        .toImmutableList(),
            )
        }
        viewModelScope.launch {
            when (val result = source.check(route.profile)) {
                is Outcome.Ok ->
                    mutable.update { it.copy(checking = false, results = result.value) }
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(
                            checking = false,
                            failure = result.failure,
                            results =
                                ConnectionStep.entries
                                    .map { step ->
                                        ConnectionResult(
                                            step,
                                            ConnectionMark.Skipped,
                                            ConnectionReason.EarlierFailed,
                                        )
                                    }
                                    .toImmutableList(),
                        )
                    }
            }
        }
    }

    private fun loadShells() {
        if (mutable.value.working) return
        mutable.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            when (val result = source.shells(route.profile)) {
                is Outcome.Ok -> mutable.update { it.copy(working = false, shells = result.value) }
                is Outcome.Failed ->
                    mutable.update { it.copy(working = false, failure = result.failure) }
            }
        }
    }

    override fun edit(): Unit = nav.open(ServersRoute.Edit(route.profile))

    override fun connect() {
        if (mutable.value.working) return
        mutable.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            val result = source.connect(route.profile)
            mutable.update {
                it.copy(working = false, failure = (result as? Outcome.Failed)?.failure)
            }
        }
    }

    override fun checkConnection(): Unit = nav.open(route.copy(page = ServerSettingsPage.Check))

    override fun showShells(): Unit = nav.open(route.copy(page = ServerSettingsPage.Shell))

    override fun showCapabilities(): Unit =
        nav.open(route.copy(page = ServerSettingsPage.Capabilities))

    override fun savedPermissions(): Unit = nav.open(ServersRoute.Permissions(route.profile))

    override fun computerTools(): Unit = nav.open(ComputerRoute(route.profile))

    override fun reachHelp(): Unit = nav.open(route.copy(page = ServerSettingsPage.Reach))

    override fun selectShell(value: String) {
        val previous = mutable.value.shells ?: return
        if (
            mutable.value.working ||
                previous.selectedValue() == value ||
                shellChoices(previous).none { it.value == value }
        )
            return
        mutable.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            when (val result = source.selectShell(route.profile, value)) {
                is Outcome.Ok -> mutable.update { it.copy(working = false, shells = result.value) }
                is Outcome.Failed ->
                    mutable.update { it.copy(working = false, failure = result.failure) }
            }
        }
    }

    override fun askRemove() {
        if (!mutable.value.working) nav.open(ServersRoute.Remove(route.profile))
    }

    override fun showModels(): Unit =
        nav.open(ServerSettingsRoute(route.profile, ServerSettingsPage.Model))

    private fun loadModels() {
        if (mutable.value.modelWorking) return
        mutable.update { it.copy(modelWorking = true, failure = null) }
        viewModelScope.launch {
            when (val result = preferences.models(route.profile)) {
                is Outcome.Ok ->
                    mutable.update { it.copy(modelWorking = false, modelChoices = result.value) }
                is Outcome.Failed ->
                    mutable.update { it.copy(modelWorking = false, failure = result.failure) }
            }
        }
    }

    /**
     * P-020: Home names the chosen model from the server's catalog. Quiet: the row falls back to the tidied ids when
     * the catalog can't be read, so a failure here is not the page's failure.
     */
    private fun loadModelNames() {
        viewModelScope.launch {
            val result = preferences.models(route.profile)
            if (result is Outcome.Ok) mutable.update { it.copy(modelChoices = result.value) }
        }
    }

    private fun change(block: suspend () -> Outcome<Unit>, reset: Boolean = false) {
        if (mutable.value.working) return
        mutable.update { it.copy(working = true, failure = null, resetDone = false) }
        viewModelScope.launch {
            val result = block()
            mutable.update {
                it.copy(
                    working = false,
                    failure = (result as? Outcome.Failed)?.failure,
                    resetDone = reset && result is Outcome.Ok,
                )
            }
        }
    }

    override fun selectModel(model: ModelRef?, variant: String): Unit =
        change({ preferences.selectModel(route.profile, model, variant) })

    override fun watching(enabled: Boolean): Unit =
        change({ preferences.watching(route.profile, enabled) })

    override fun notifications(enabled: Boolean): Unit =
        change({ preferences.notifications(route.profile, enabled) })

    override fun shareRead(enabled: Boolean): Unit =
        change({ preferences.shareRead(route.profile, enabled) })

    override fun resetDefaults(): Unit = change({ preferences.reset(route.profile) }, true)

    override fun askDisconnect() {
        mutable.update { it.copy(disconnectConfirm = true) }
    }

    override fun cancelDisconnect() {
        if (!mutable.value.working) mutable.update { it.copy(disconnectConfirm = false) }
    }

    override fun disconnect() {
        if (!mutable.value.disconnectConfirm) return
        mutable.update { it.copy(disconnectConfirm = false) }
        change({ source.disconnect(route.profile) })
    }

    override fun recheckAgents() {
        if (mutable.value.working) return
        mutable.update { it.copy(working = true, failure = null, agents = null) }
        viewModelScope.launch {
            when (val result = source.recheckAgents(route.profile)) {
                is Outcome.Ok -> mutable.update { it.copy(working = false, agents = result.value) }
                is Outcome.Failed ->
                    mutable.update { it.copy(working = false, failure = result.failure) }
            }
        }
    }

    override fun integration(page: ServerIntegrationPage): Unit =
        nav.open(ServerIntegrationRoute(route.profile, page))

    override fun refreshAutomations() {
        val snapshot = mutable.value
        if (!snapshot.showAutomations || snapshot.automationsLoading || snapshot.automationsWorking) {
            return
        }
        val versionAtStart = automationsMutationVersion
        mutable.update {
            it.copy(
                automationsLoading = true,
                automationsLoadFailed = false,
                nowMs = clock.now().toEpochMilliseconds(),
            )
        }
        viewModelScope.launch {
            when (val result = automationsSource.automations(route.profile)) {
                is Outcome.Ok -> applyAutomationsRefresh(result.value, versionAtStart)
                is Outcome.Failed -> applyAutomationsRefreshFailure(result.failure, versionAtStart)
            }
        }
    }

    private fun applyAutomationsRefresh(
        items: ImmutableList<ServerAutomation>,
        versionAtStart: Long,
    ) {
        if (automationsMutationVersion != versionAtStart || mutable.value.automationsWorking) {
            mutable.update { it.copy(automationsLoading = false) }
            return
        }
        val sorted = sortAutomations(items)
        mutable.update { current ->
            val keptSelection = current.selectedAutomationId?.takeIf { id -> sorted.any { it.id == id } }
            current.copy(
                automationsLoading = false,
                automations = sorted,
                automationsLoadFailed = false,
                selectedAutomationId = keptSelection,
                automationSheetFailed =
                    if (keptSelection == null) false else current.automationSheetFailed,
                deleteAutomationConfirmId =
                    current.deleteAutomationConfirmId?.takeIf { id -> sorted.any { it.id == id } },
                nowMs = clock.now().toEpochMilliseconds(),
            )
        }
    }

    private fun applyAutomationsRefreshFailure(failure: Failure, versionAtStart: Long) {
        when {
            failure is Failure.Unavailable -> hideAutomationsSection()
            automationsMutationVersion != versionAtStart ->
                mutable.update { it.copy(automationsLoading = false) }
            else ->
                mutable.update {
                    it.copy(automationsLoading = false, automationsLoadFailed = true)
                }
        }
    }

    override fun openAutomation(id: String) {
        if (mutable.value.automations.any { it.id == id }) {
            mutable.update {
                it.copy(
                    selectedAutomationId = id,
                    automationSheetFailed = false,
                    nowMs = clock.now().toEpochMilliseconds(),
                )
            }
        }
    }

    override fun closeAutomation() {
        mutable.update { it.copy(selectedAutomationId = null, automationSheetFailed = false) }
    }

    override fun pauseAutomation(id: String) {
        mutateAutomation(id) { automationsSource.pauseAutomation(route.profile, id) }
    }

    override fun resumeAutomation(id: String) {
        mutateAutomation(id) { automationsSource.resumeAutomation(route.profile, id) }
    }

    private fun mutateAutomation(id: String, call: suspend () -> Outcome<ServerAutomation>) {
        if (mutable.value.automationsWorking) return
        automationsMutationVersion++
        mutable.update {
            it.copy(
                automationsWorking = true,
                automationsNotice = null,
                automationSheetFailed = false,
            )
        }
        viewModelScope.launch {
            when (val result = call()) {
                is Outcome.Ok -> {
                    val updated = result.value
                    mutable.update { current ->
                        val replaced =
                            sortAutomations(
                                current.automations.map { if (it.id == id) updated else it }
                            )
                        current.copy(
                            automationsWorking = false,
                            automations = replaced,
                            automationSheetFailed = false,
                            nowMs = clock.now().toEpochMilliseconds(),
                        )
                    }
                }
                is Outcome.Failed -> handleAutomationMutationFailure(result.failure, targetId = id)
            }
        }
    }

    override fun askDeleteAutomation(id: String) {
        if (mutable.value.automationsWorking) return
        if (mutable.value.automations.any { it.id == id }) {
            mutable.update { it.copy(deleteAutomationConfirmId = id) }
        }
    }

    override fun cancelDeleteAutomation() {
        if (!mutable.value.automationsWorking) {
            mutable.update { it.copy(deleteAutomationConfirmId = null) }
        }
    }

    override fun confirmDeleteAutomation() {
        val targetId = mutable.value.deleteAutomationConfirmId ?: return
        if (mutable.value.automationsWorking) return
        automationsMutationVersion++
        mutable.update {
            it.copy(
                deleteAutomationConfirmId = null,
                automationsWorking = true,
                automationsNotice = null,
                automationSheetFailed = false,
            )
        }
        viewModelScope.launch {
            when (val result = automationsSource.deleteAutomation(route.profile, targetId)) {
                is Outcome.Ok ->
                    mutable.update { current ->
                        current.copy(
                            automationsWorking = false,
                            automations =
                                current.automations
                                    .filterNot { it.id == targetId }
                                    .toImmutableList(),
                            selectedAutomationId =
                                current.selectedAutomationId?.takeUnless { it == targetId },
                            automationSheetFailed = false,
                            nowMs = clock.now().toEpochMilliseconds(),
                        )
                    }
                is Outcome.Failed ->
                    handleAutomationMutationFailure(result.failure, targetId = targetId)
            }
        }
    }

    override fun askStopAllAutomations() {
        if (mutable.value.automationsWorking || mutable.value.activeAutomationsCount <= 0) return
        mutable.update { it.copy(stopAllAutomationsConfirm = true) }
    }

    override fun cancelStopAllAutomations() {
        if (!mutable.value.automationsWorking) {
            mutable.update { it.copy(stopAllAutomationsConfirm = false) }
        }
    }

    override fun confirmStopAllAutomations() {
        if (!mutable.value.stopAllAutomationsConfirm || mutable.value.automationsWorking) return
        val active = mutable.value.automations.filter { it.status == AutomationStatus.Active }
        if (active.isNotEmpty()) automationsMutationVersion++
        mutable.update {
            it.copy(
                stopAllAutomationsConfirm = false,
                automationsWorking = active.isNotEmpty(),
                automationsNotice = null,
                automationSheetFailed = false,
            )
        }
        if (active.isNotEmpty()) {
            viewModelScope.launch { runStopAll(active) }
        }
    }

    private suspend fun runStopAll(active: List<ServerAutomation>) {
        val updatedById = mutableMapOf<String, ServerAutomation>()
        val missingIds = mutableSetOf<String>()
        var unavailable = false
        for (item in active) {
            when (val result = automationsSource.pauseAutomation(route.profile, item.id)) {
                is Outcome.Ok -> updatedById[item.id] = result.value
                is Outcome.Failed ->
                    when (result.failure) {
                        is Failure.Unavailable -> {
                            unavailable = true
                            break
                        }
                        is Failure.NotFound -> missingIds += item.id
                        else -> Unit
                    }
            }
        }
        if (unavailable) {
            hideAutomationsSection()
            return
        }
        val expected = active.size - missingIds.size
        val stopped = updatedById.size
        val notice =
            when {
                expected == 0 -> null
                stopped == expected -> AutomationsNotice.StoppedAll(stopped)
                stopped > 0 -> AutomationsNotice.StoppedPartial(stopped, expected)
                else -> AutomationsNotice.ActionFailed
            }
        mutable.update { current ->
            val merged =
                sortAutomations(
                    current.automations
                        .filterNot { it.id in missingIds }
                        .map { updatedById[it.id] ?: it }
                )
            current.copy(
                automationsWorking = false,
                automations = merged,
                automationsNotice = notice,
                nowMs = clock.now().toEpochMilliseconds(),
            )
        }
        if (missingIds.isNotEmpty()) refreshAutomations()
    }

    private fun handleAutomationMutationFailure(failure: Failure, targetId: String) {
        when (failure) {
            is Failure.Unavailable -> hideAutomationsSection()
            is Failure.NotFound -> {
                mutable.update { current ->
                    val closingSheet = current.selectedAutomationId == targetId
                    current.copy(
                        automationsWorking = false,
                        automations =
                            current.automations.filterNot { it.id == targetId }.toImmutableList(),
                        selectedAutomationId =
                            if (closingSheet) null else current.selectedAutomationId,
                        automationSheetFailed =
                            if (closingSheet) false else current.automationSheetFailed,
                        deleteAutomationConfirmId =
                            current.deleteAutomationConfirmId?.takeUnless { it == targetId },
                        nowMs = clock.now().toEpochMilliseconds(),
                    )
                }
                refreshAutomations()
            }
            else ->
                mutable.update { current ->
                    val sheetOpen = current.selectedAutomationId != null
                    current.copy(
                        automationsWorking = false,
                        automationSheetFailed = sheetOpen,
                        automationsNotice =
                            if (sheetOpen) null else AutomationsNotice.ActionFailed,
                    )
                }
        }
    }

    private fun hideAutomationsSection() {
        mutable.update {
            it.copy(
                automationsLoading = false,
                automationsWorking = false,
                automations = persistentListOf(),
                automationsLoadFailed = false,
                automationsHidden = true,
                selectedAutomationId = null,
                automationSheetFailed = false,
                deleteAutomationConfirmId = null,
                stopAllAutomationsConfirm = false,
            )
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ServerSettingsRoute): ServerSettingsViewModel
    }
}
