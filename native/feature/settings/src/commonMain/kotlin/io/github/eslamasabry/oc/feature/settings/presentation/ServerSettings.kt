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
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.TerminalShellSettings
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ComputerRoute
import io.github.eslamasabry.oc.ui.routes.ServerIntegrationPage
import io.github.eslamasabry.oc.ui.routes.ServerIntegrationRoute
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import io.github.eslamasabry.oc.ui.routes.ServerSettingsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
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
    val value: String,
    val label: String?,
    val terminalOnly: Boolean = false,
)

/** Preserve custom selections and disambiguate equal names using the server's absolute paths. */
public fun shellChoices(settings: TerminalShellSettings): ImmutableList<ShellChoice> {
    val names = settings.options.groupingBy { it.name }.eachCount()
    val rows = mutableListOf(ShellChoice("", null))
    val seen = mutableSetOf("")
    for (option in settings.options) {
        val value = if (names[option.name] == 1) option.name else option.path
        if (seen.add(value)) rows.add(ShellChoice(value, value, !option.acceptable))
    }
    if (settings.selected.isNotEmpty() && seen.add(settings.selected)) {
        rows.add(ShellChoice(settings.selected, settings.selected))
    }
    return rows.toImmutableList()
}

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
)

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
}

@AssistedInject
public class ServerSettingsViewModel(
    @Assisted private val route: ServerSettingsRoute,
    private val source: ServerSettingsSource,
    private val preferences: ServerPreferencesSource,
    private val nav: AppNavigator,
) : ViewModel(), ServerSettingsActions {
    private val mutable = MutableStateFlow(ServerSettingsUi(page = route.page))
    public val state: StateFlow<ServerSettingsUi> = mutable.asStateFlow()

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
                val firstLive = facts?.connected == true && mutable.value.facts?.connected != true
                mutable.update { it.copy(loading = false, facts = facts) }
                if (firstLive && route.page == ServerSettingsPage.Home) refresh()
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
                previous.selected == value ||
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

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ServerSettingsRoute): ServerSettingsViewModel
    }
}
