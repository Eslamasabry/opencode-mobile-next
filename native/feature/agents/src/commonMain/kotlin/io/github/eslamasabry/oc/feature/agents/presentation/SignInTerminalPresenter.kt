package io.github.eslamasabry.oc.feature.agents.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.agents.signin.AgentRef
import io.github.eslamasabry.oc.feature.agents.signin.SignInAgents
import io.github.eslamasabry.oc.feature.agents.signin.SignInCheck
import io.github.eslamasabry.oc.feature.agents.signin.SignInClipboard
import io.github.eslamasabry.oc.feature.agents.signin.SignInCommand
import io.github.eslamasabry.oc.feature.agents.signin.SignInDetails
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
    private var rechecking = false
    private var pendingPage: String? = null

    /**
     * The page Claude asked the system to open (its `BROWSER` hand-off), in memory only and never logged. Claude gives
     * that page a redirect to a port on this phone, so signing in there finishes without a code; the page it prints
     * for people to copy asks for one. "Open sign-in page" reopens the handed-over one (Flutter #137).
     */
    private var handedOver: String? = null
    private val sessionWatchers = mutableListOf<() -> Unit>()
    private val terminalWatchers = mutableListOf<() -> Unit>()

    /** Reads the plan and, for a terminal sign-in, starts it. Call once. */
    public fun begin() {
        when (val plan = agents.plan(agent)) {
            is SignInPlan.Terminal -> {
                ui.update {
                    it.copy(
                        agentName = plan.agentName,
                        usesClaudeAccount = plan.usesClaudeAccount,
                        pastesBrowserCode = agent.id == ANTIGRAVITY,
                        offersAntigravityNote = agent.id == "gemini",
                        firstUse = plan.firstUse,
                        typesAuth = plan.typesAuth,
                        // Antigravity's sign-in ends with a code to paste, and Claude Code's may ask
                        // for one ("Paste code here if prompted"). The readable view has a real text
                        // field (long-press paste, the keyboard, Send); the live grid did not take
                        // keyboard input on the phone (owner reports, merge41 and 2026-11-18), so the
                        // field is where the code goes. The person can still switch to the grid.
                        accessibleMode = if (agent.id in ASKS_FOR_CODE) true else it.accessibleMode,
                    )
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
        handedOver = null
        checking = false
        val next = SignInTerminalSession(scope, host, foreground)
        session = next
        ui.update {
            cleared(it)
                .copy(
                    step = SignInStep.Starting,
                    failureCode = null,
                    startStep = null,
                    failureDetails = null,
                    stopFailed = false,
                    checkBusy = false,
                    checkCode = null,
                )
        }
        val states = scope.launch { next.state.collect { onSession(next, it) } }
        val urls = scope.launch {
            next.openUrls.collect { url ->
                if (agent.id == CLAUDE && next === session) {
                    handedOver = url
                    ui.update { it.copy(canOpenPage = true, pageAddress = url) }
                }
                requestPage(url, next)
            }
        }
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
                ui.update {
                    cleared(it)
                        .copy(
                            step = step,
                            failureCode = now.problem.code,
                            startStep = (now.problem as? SignInProblem.Start)?.step,
                            failureDetails = SignInDetails.of(now.problem),
                        )
                }
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
                        transcript = foldAddresses(transcript),
                        canOpenPage = page() != null,
                        pageAddress = page(),
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
        if (ui.value.firstUse) {
            // No read-only sign-in status exists for this agent: remember the set-up and go back; its own answer in
            // the next conversation tells the truth.
            ui.update { it.copy(inputEnabled = false) }
            agents.markSetUp(owner, agent)
            val ended = stopFirst || owned.end() is Outcome.Ok
            ui.update { it.copy(step = SignInStep.SetUpDone, stopFailed = !ended) }
            return
        }
        ui.update { it.copy(step = SignInStep.Checking, inputEnabled = false, checkBusy = false, checkCode = null) }
        val answer = read()
        val stopped = stopFirst || owned.end() is Outcome.Ok
        show(answer, stopped)
    }

    /**
     * Three answers, never two (owner, 2026-11-18): only the agent's own "signed out" is NotYet. A read that could
     * not tell (busy, timed out, no answer) is tried again within the host's bound ([SignInAgents.checkTries]),
     * saying while it waits that a conversation keeps the agent busy; then it is CouldNotCheck, with one "Check
     * sign-in again".
     */
    private suspend fun read(): SignInCheck {
        val tries = agents.checkTries.coerceAtLeast(1)
        var round = 0
        var answer = agents.checkSignedIn(owner, agent, round)
        while (round + 1 < tries && worthAnother(answer, round)) {
            ui.update { it.copy(checkBusy = (answer as? SignInCheck.CouldNotCheck)?.busy == true) }
            round++
            answer = agents.checkSignedIn(owner, agent, round)
        }
        return answer
    }

    /** A reason that can pass by itself; a time-out already waited its whole budget, so it gets one more try only. */
    private fun worthAnother(answer: SignInCheck, round: Int): Boolean {
        val open = answer as? SignInCheck.CouldNotCheck ?: return false
        return open.passing && !(open.code == SignInCheck.CouldNotCheck.TIMED_OUT && round >= 1)
    }

    private fun show(answer: SignInCheck, stopped: Boolean) {
        ui.update {
            it.copy(
                step =
                    when (answer) {
                        SignInCheck.SignedIn -> SignInStep.SignedIn
                        SignInCheck.SignedOut -> SignInStep.NotYet
                        is SignInCheck.CouldNotCheck -> SignInStep.CouldNotCheck
                    },
                checkBusy = false,
                checkCode = (answer as? SignInCheck.CouldNotCheck)?.code,
                stopFailed = !stopped,
            )
        }
    }

    /** "Check sign-in again" after [SignInStep.CouldNotCheck]: the account only, no new sign-in. */
    private fun checkAgain() {
        if (rechecking) return
        rechecking = true
        scope.launch {
            try {
                ui.update { it.copy(step = SignInStep.Checking, checkBusy = false, checkCode = null) }
                show(read(), stopped = !ui.value.stopFailed)
            } finally {
                rechecking = false
            }
        }
    }

    /** The page "Open sign-in page" opens: Claude's handed-over one when there is one, else the printed one. */
    private fun page(): String? = handedOver ?: found.page

    override fun openSignInPage() {
        val page = page() ?: return
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

    override fun paste(text: String) {
        val running = session?.state?.value as? SignInSessionState.Running ?: return
        if (running.exitCode != null) return
        val clean = pasteText(text) ?: return
        scope.launch { running.terminal.paste(clean) }
    }

    override fun setAccessibleMode(on: Boolean) {
        ui.update { it.copy(accessibleMode = on) }
    }

    override fun finish() {
        if (ui.value.step == SignInStep.CouldNotCheck) {
            checkAgain()
        } else {
            val owned = session
            if (owned != null && !checking) {
                checking = true
                scope.launch { check(owned, stopFirst = true) }
            }
        }
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
        handedOver = null
        return now.copy(
            terminal = null,
            inputEnabled = false,
            canOpenPage = false,
            code = null,
            linkPrompt = null,
            pageAddress = null,
        )
    }

    private fun stop(watchers: MutableList<() -> Unit>) {
        watchers.forEach { it() }
        watchers.clear()
    }

    internal companion object {
        const val CLAUDE = "claude"
        const val ANTIGRAVITY = "antigravity"

        /** Agents whose sign-in may ask for a code typed back into the program: their page opens on the field. */
        val ASKS_FOR_CODE: Set<String> = setOf(CLAUDE, ANTIGRAVITY)

        /** The reason a read that never ran gives under Details. */
        const val NO_ANSWER = "noAnswer"

        private val ADDRESS = Regex("""https?://[^\s/?#]+[^\s]*""")

        /**
         * The terminal text as the readable view shows it: an address with a path or a query (a sign-in page's wall of
         * query text) folds to its site and an ellipsis. "Open sign-in page" opens it, and Details holds it whole.
         */
        fun foldAddresses(text: String): String =
            ADDRESS.replace(text) { match ->
                val whole = match.value
                val site = whole.substringBefore("://") + "://" + whole.substringAfter("://").takeWhile { it !in "/?#" }
                if (whole.length > site.length + 1) "$site/\u2026" else whole
            }

        /** A pasted code stays far below this; anything longer is not a sign-in code. */
        const val PASTE_MAX = 4096

        /** The clipboard text a paste writes: trimmed, and nothing when empty or too long. */
        fun pasteText(text: String): String? = text.trim().takeIf { it.isNotEmpty() && it.length <= PASTE_MAX }
    }
}
