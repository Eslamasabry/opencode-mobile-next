package io.github.eslamasabry.oc.feature.agents.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.agents.signin.AgentRef
import io.github.eslamasabry.oc.feature.agents.signin.SignInAgents
import io.github.eslamasabry.oc.feature.agents.signin.SignInClipboard
import io.github.eslamasabry.oc.feature.agents.signin.SignInCommand
import io.github.eslamasabry.oc.feature.agents.signin.SignInForeground
import io.github.eslamasabry.oc.feature.agents.signin.SignInFound
import io.github.eslamasabry.oc.feature.agents.signin.SignInLinkCheck
import io.github.eslamasabry.oc.feature.agents.signin.SignInLinks
import io.github.eslamasabry.oc.feature.agents.signin.SignInPlan
import io.github.eslamasabry.oc.feature.agents.signin.SignInProblem
import io.github.eslamasabry.oc.feature.agents.signin.SignInSessionState
import io.github.eslamasabry.oc.feature.agents.signin.SignInTerminalSession
import io.github.eslamasabry.oc.feature.agents.signin.TerminalHost
import io.github.eslamasabry.oc.feature.agents.signin.TerminalModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The sign-in terminal's logic (plan 07 G03, plan 17 §1.5 "Sign in"; AGENTS-BACKENDS-13/14), kept
 * out of the ViewModel so it runs on any scope in tests. The agent's own sign-in runs in a visible
 * terminal ([SignInTerminalSession] holds the foreground claim); what it prints becomes "Open
 * sign-in page" and "Copy code"; when it ends the account is read, and it counts as signed in only
 * on a `SignedIn` read. The app never reads, stores or relays the code beyond the sensitive copy
 * the person asks for (owner terms decision 2026-10-05).
 */
public class SignInTerminalPresenter(
    private val scope: CoroutineScope,
    private val owner: ProfileId,
    private val agent: AgentRef,
    private val agents: SignInAgents,
    private val host: TerminalHost,
    private val foreground: SignInForeground,
    private val links: SignInLinks,
    private val clipboard: SignInClipboard,
    private val onLeave: () -> Unit,
) : SignInTerminalActions {
    private val ui = MutableStateFlow(SignInTerminalUi())
    public val state: StateFlow<SignInTerminalUi> = ui.asStateFlow()

    private var command: SignInCommand? = null
    private var session: SignInTerminalSession? = null
    private var watched: TerminalModel? = null
    private var found = SignInFound()
    private var checking = false
    private var pendingPage: String? = null
    private val sessionWatchers = mutableListOf<() -> Unit>()
    private val terminalWatchers = mutableListOf<() -> Unit>()

    /** Reads the plan and, for a terminal sign-in, starts it. Call once. */
    public fun begin() {
        when (val plan = agents.plan(agent)) {
            is SignInPlan.Terminal -> {
                ui.update {
                    it.copy(agentName = plan.agentName, usesClaudeAccount = plan.usesClaudeAccount)
                }
                when (val built = agents.command(owner, agent)) {
                    is Outcome.Ok -> {
                        command = built.value
                        open()
                    }
                    is Outcome.Failed -> ui.update { it.copy(step = SignInStep.Unavailable) }
                }
            }
            is SignInPlan.ProviderKeyOnHost ->
                ui.update {
                    it.copy(agentName = plan.agentName, step = SignInStep.ProviderKeyOnHost)
                }
            is SignInPlan.NotAvailable ->
                ui.update { it.copy(agentName = plan.agentName, step = SignInStep.Unavailable) }
        }
    }

    private fun open() {
        val run = command ?: return
        stop(sessionWatchers)
        stop(terminalWatchers)
        watched = null
        found = SignInFound()
        checking = false
        val next = SignInTerminalSession(scope, host, foreground)
        session = next
        ui.update {
            cleared(it).copy(step = SignInStep.Starting, failureCode = null, stopFailed = false)
        }
        val states = scope.launch { next.state.collect { onSession(next, it) } }
        val urls = scope.launch { next.openUrls.collect { requestPage(it, next) } }
        sessionWatchers += { states.cancel() }
        sessionWatchers += { urls.cancel() }
        next.start(run)
    }

    private fun onSession(owned: SignInTerminalSession, now: SignInSessionState) {
        if (owned !== session) return
        when (now) {
            SignInSessionState.Idle -> Unit
            SignInSessionState.Starting -> ui.update { it.copy(step = SignInStep.Starting) }
            is SignInSessionState.Running -> {
                watch(now.terminal)
                if (now.exitCode == null) {
                    ui.update {
                        it.copy(
                            step = SignInStep.Running,
                            terminal = now.terminal.view,
                            inputEnabled = true,
                        )
                    }
                } else if (!checking) {
                    checking = true
                    ui.update { it.copy(step = SignInStep.Checking, inputEnabled = false) }
                    scope.launch { check(owned, stopFirst = false) }
                }
            }
            is SignInSessionState.Failed -> {
                stop(terminalWatchers)
                val step =
                    when (now.problem) {
                        SignInProblem.NotificationsBlocked -> SignInStep.NotificationsBlocked
                        SignInProblem.Unavailable -> SignInStep.Unavailable
                        else -> SignInStep.Failed
                    }
                ui.update { cleared(it).copy(step = step, failureCode = now.problem.code) }
            }
            SignInSessionState.Ended -> {
                stop(terminalWatchers)
                ui.update { cleared(it) }
            }
        }
    }

    private fun watch(terminal: TerminalModel) {
        if (terminal === watched) return
        watched = terminal
        val text = scope.launch {
            terminal.transcript.collect { transcript ->
                found = agents.read(transcript)
                ui.update {
                    it.copy(
                        transcript = transcript,
                        canOpenPage = found.page != null,
                        code = found.code,
                    )
                }
            }
        }
        val title = scope.launch {
            terminal.title.collect { t -> ui.update { it.copy(title = t) } }
        }
        terminalWatchers += { text.cancel() }
        terminalWatchers += { title.cancel() }
    }

    /**
     * Reads the account; the claim is held through the read and released after it (B-LINUX-096).
     */
    private suspend fun check(owned: SignInTerminalSession, stopFirst: Boolean) {
        if (stopFirst && owned.end() is Outcome.Failed) {
            checking = false
            ui.update { it.copy(stopFailed = true) }
            return
        }
        dismissSignInPage()
        ui.update { it.copy(step = SignInStep.Checking, inputEnabled = false) }
        val signedIn = agents.confirmSignedIn(owner, agent)
        val stopped = stopFirst || owned.end() is Outcome.Ok
        ui.update {
            it.copy(
                step = if (signedIn) SignInStep.SignedIn else SignInStep.NotYet,
                stopFailed = !stopped,
            )
        }
    }

    override fun openSignInPage() {
        val page = found.page ?: return
        if (ui.value.terminal == null || session?.canOpenLinks != true) return
        val owned = session ?: return
        scope.launch { requestPage(page, owned) }
    }

    private suspend fun requestPage(page: String, owned: SignInTerminalSession) {
        if (session !== owned || !owned.canOpenLinks || ui.value.linkPrompt != null) return
        when (val decision = links.check(page)) {
            SignInLinkCheck.Direct -> links.open(page)
            SignInLinkCheck.Refused -> Unit
            is SignInLinkCheck.Ask -> {
                pendingPage = page
                ui.update {
                    it.copy(linkPrompt = SignInPagePrompt(decision.host, decision.insecure))
                }
            }
        }
    }

    override fun confirmSignInPage() {
        val page = pendingPage ?: return
        val owned = session ?: return
        dismissSignInPage()
        scope.launch {
            if (session === owned && owned.canOpenLinks) links.openConfirmed(page)
        }
    }

    override fun dismissSignInPage() {
        pendingPage = null
        ui.update { it.copy(linkPrompt = null) }
    }

    override fun copyCode() {
        if (ui.value.terminal == null) return
        found.code?.let(clipboard::copySensitive)
    }

    override fun send(line: String) {
        val running = session?.state?.value as? SignInSessionState.Running ?: return
        if (running.exitCode != null) return
        scope.launch { running.terminal.write(line.encodeToByteArray()) }
    }

    override fun setAccessibleMode(on: Boolean) {
        ui.update { it.copy(accessibleMode = on) }
    }

    override fun finish() {
        val owned = session ?: return
        if (checking) return
        checking = true
        scope.launch { check(owned, stopFirst = true) }
    }

    override fun startAgain() {
        val previous = session
        scope.launch {
            if (previous != null && previous.end() is Outcome.Failed) {
                ui.update { it.copy(stopFailed = true) }
                return@launch
            }
            open()
        }
    }

    override fun leave() {
        dismissSignInPage()
        val owned = session
        scope.launch {
            if (owned != null && owned.end() is Outcome.Failed) {
                ui.update { it.copy(stopFailed = true) }
                return@launch
            }
            onLeave()
        }
    }

    /** Host-owned cleanup survives a ViewModel being removed while native admission is pending. */
    public suspend fun close(): io.github.eslamasabry.oc.core.common.Outcome<Unit> {
        dismissSignInPage()
        stop(sessionWatchers)
        stop(terminalWatchers)
        return session?.end() ?: io.github.eslamasabry.oc.core.common.Outcome.Ok(Unit)
    }

    /** App owner cleanup, also called after its route is torn down or its profile is deleted. */
    public suspend fun drain(): io.github.eslamasabry.oc.core.common.Outcome<Unit> = close()

    private fun cleared(now: SignInTerminalUi): SignInTerminalUi {
        pendingPage = null
        return now.copy(
            terminal = null,
            inputEnabled = false,
            canOpenPage = false,
            code = null,
            linkPrompt = null,
        )
    }

    private fun stop(watchers: MutableList<() -> Unit>) {
        watchers.forEach { it() }
        watchers.clear()
    }
}
