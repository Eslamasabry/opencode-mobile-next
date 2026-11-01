package io.github.eslamasabry.oc.feature.agents.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.agents.signin.AgentRef
import io.github.eslamasabry.oc.feature.agents.signin.SignInAgents
import io.github.eslamasabry.oc.feature.agents.signin.SignInClipboard
import io.github.eslamasabry.oc.feature.agents.signin.SignInForeground
import io.github.eslamasabry.oc.feature.agents.signin.SignInLinks
import io.github.eslamasabry.oc.feature.agents.signin.TerminalHost
import kotlinx.coroutines.flow.StateFlow

/**
 * `SignInTerminalViewModel` (plan 07 G03, `AgentsRoute.SignInTerminal(agentId)`). Not yet contributed to the Metro
 * graph: the route, the `:engine:agents` and `TerminalHost` bindings and the foreground claim are not in this tree
 * (S3-TERMMIN report, "open problems"); the entries file that installs it comes with them.
 *
 * Sessions belong to the terminal host, not this ViewModel: leaving goes through [leave], which drains the sign-in
 * first. A screen torn down without it leaves the drain to the host's `owner-closed` path (plan 17 §1.5).
 */
public class SignInTerminalViewModel(
    owner: ProfileId,
    agent: AgentRef,
    agents: SignInAgents,
    host: TerminalHost,
    foreground: SignInForeground,
    links: SignInLinks,
    clipboard: SignInClipboard,
    onLeave: () -> Unit,
) : ViewModel(), SignInTerminalActions {
    private val presenter =
        SignInTerminalPresenter(viewModelScope, owner, agent, agents, host, foreground, links, clipboard, onLeave)
            .also { it.begin() }

    public val state: StateFlow<SignInTerminalUi> = presenter.state

    override fun openSignInPage(): Unit = presenter.openSignInPage()

    override fun copyCode(): Unit = presenter.copyCode()

    override fun send(line: String): Unit = presenter.send(line)

    override fun setAccessibleMode(on: Boolean): Unit = presenter.setAccessibleMode(on)

    override fun finish(): Unit = presenter.finish()

    override fun startAgain(): Unit = presenter.startAgain()

    override fun leave(): Unit = presenter.leave()
}
