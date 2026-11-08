package io.github.eslamasabry.oc.feature.setup.presentation

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
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.install.PinnedHostCommands
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SetupRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The saved computer server as the guide shows it: its name and the port its commands use. */
public data class ComputerServer(val name: String, val port: Int)

/**
 * The host side of the guide (bound in androidApp): the saved server, the app's own connect, and the walkthrough link.
 * The app never runs these commands; the person copies them to the computer.
 */
public interface ComputerUpdateSource {
    /** Null when the saved server is gone or is not a computer server. */
    public suspend fun server(profile: ProfileId): ComputerServer?

    /** The app's connect to that saved server: Ok when it now answers as OpenCode 2 and the app connected. */
    public suspend fun recheck(profile: ProfileId): Outcome<Unit>

    /** The full walkthrough, through the external-link policy (an app-authored URL). */
    public fun openGuide()
}

/** What Check again found, in plain words only (the screen maps each). Never the server's raw error. */
public enum class RecheckAnswer {
    /** It still answers as OpenCode 1. */
    StillOpenCode1,

    /** It answers, but the saved password is not accepted (OpenCode 2 prints a new one). */
    PasswordRejected,

    /** Nothing answered at the saved address. */
    NoAnswer,

    /** Anything else the connect refused. */
    Other,
}

public data class ComputerUpdateUi(
    val loading: Boolean = true,
    /** The saved server is gone. */
    val missing: Boolean = false,
    val server: String = "",
    val port: Int = DEFAULT_PORT,
    val checking: Boolean = false,
    /** The last Check again that did not connect; null before the first and while one runs. */
    val answer: RecheckAnswer? = null,
) {
    /** The one command the person runs on the computer: remove OpenCode 1, install and start the pinned OpenCode 2. */
    val command: String
        get() = ComputerUpdateCommands.upgrade(port)

    internal companion object {
        const val DEFAULT_PORT: Int = 4096
    }
}

public object ComputerUpdateCommands {
    /** Flutter `openCode2UpgradeCommand` (lib/domain/server_probe.dart), pinned to the version the app installs. */
    public fun upgrade(port: Int): String = "npm uninstall -g opencode-ai &&\n" + PinnedHostCommands.install(port)
}

public interface ComputerUpdateActions {
    public fun recheck()

    public fun openGuide()

    /** The exit to the server list: the guide never traps the person. */
    public fun changeServer()

    public fun back()
}

/**
 * "Update this server" for a computer that still runs OpenCode 1 (Flutter connection_failure.dart's OpenCode 1 case
 * with host_management_screen.dart's command). Check again connects through the app's own path; once it connects the
 * person lands on their conversations.
 */
@AssistedInject
public class ComputerUpdateViewModel(
    @Assisted private val route: SetupRoute.UpdateComputer,
    private val source: ComputerUpdateSource,
    private val nav: AppNavigator,
) : ViewModel(), ComputerUpdateActions {
    private val mutable = MutableStateFlow(ComputerUpdateUi())
    public val state: StateFlow<ComputerUpdateUi> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val server = source.server(route.profile)
            mutable.value =
                if (server == null) ComputerUpdateUi(loading = false, missing = true)
                else ComputerUpdateUi(loading = false, server = server.name, port = server.port)
        }
    }

    override fun recheck() {
        val now = mutable.value
        if (now.loading || now.missing || now.checking) return
        mutable.update { it.copy(checking = true, answer = null) }
        viewModelScope.launch {
            when (val result = source.recheck(route.profile)) {
                is Outcome.Ok -> {
                    mutable.update { it.copy(checking = false) }
                    nav.land()
                }
                is Outcome.Failed -> mutable.update { it.copy(checking = false, answer = answerOf(result.failure)) }
            }
        }
    }

    override fun openGuide() {
        source.openGuide()
    }

    override fun changeServer() {
        nav.open(ServersRoute.Home)
    }

    override fun back() {
        nav.back()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: SetupRoute.UpdateComputer): ComputerUpdateViewModel
    }

    internal companion object {
        fun answerOf(failure: Failure): RecheckAnswer =
            when (failure) {
                is Failure.Incompatible ->
                    if (failure.kind == IncompatibleKind.LegacyOpenCode1) RecheckAnswer.StillOpenCode1
                    else RecheckAnswer.Other
                is Failure.SignIn -> RecheckAnswer.PasswordRejected
                is Failure.Network,
                is Failure.TimedOut -> RecheckAnswer.NoAnswer
                else -> RecheckAnswer.Other
            }
    }
}
