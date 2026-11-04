package io.github.eslamasabry.oc.feature.agents.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.feature.agents.signin.AgentRef
import io.github.eslamasabry.oc.feature.agents.signin.SignInAgents
import io.github.eslamasabry.oc.feature.agents.signin.SignInClipboard
import io.github.eslamasabry.oc.feature.agents.signin.SignInForeground
import io.github.eslamasabry.oc.feature.agents.signin.SignInLinks
import io.github.eslamasabry.oc.feature.agents.signin.TerminalHost
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The real agent sign-in route; leaving drains exactly its native PTY before releasing protection.
 */
@AssistedInject
public class SignInTerminalViewModel(
    @Assisted route: AgentsRoute.SignInTerminal,
    agents: SignInAgents,
    host: TerminalHost,
    foreground: SignInForeground,
    links: SignInLinks,
    clipboard: SignInClipboard,
    nav: AppNavigator,
    private val cleanup: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) : ViewModel(), SignInTerminalActions {
    private val presenter =
        SignInTerminalPresenter(
                viewModelScope,
                route.owner,
                AgentRef(route.agentId),
                agents,
                host,
                foreground,
                links,
                clipboard,
                nav::back,
            )
            .also { it.begin() }

    public val state: StateFlow<SignInTerminalUi> = presenter.state

    override fun openSignInPage(): Unit = presenter.openSignInPage()

    override fun copyCode(): Unit = presenter.copyCode()

    override fun send(line: String): Unit = presenter.send(line)

    override fun setAccessibleMode(on: Boolean): Unit = presenter.setAccessibleMode(on)

    override fun finish(): Unit = presenter.finish()

    override fun startAgain(): Unit = presenter.startAgain()

    override fun leave(): Unit = presenter.leave()

    override fun onCleared() {
        cleanup.launch(dispatchers.main) { presenter.close() }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: AgentsRoute.SignInTerminal): SignInTerminalViewModel
    }
}
