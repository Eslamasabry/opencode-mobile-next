package io.github.eslamasabry.oc.feature.agents.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.agents.signin.AgentRef
import io.github.eslamasabry.oc.feature.agents.signin.FakeAgents
import io.github.eslamasabry.oc.feature.agents.signin.FakeForeground
import io.github.eslamasabry.oc.feature.agents.signin.FakeLease
import io.github.eslamasabry.oc.feature.agents.signin.FakeTerminalHost
import io.github.eslamasabry.oc.feature.agents.signin.RecordingClipboard
import io.github.eslamasabry.oc.feature.agents.signin.RecordingLinks
import io.github.eslamasabry.oc.feature.agents.signin.SignInAdmission
import io.github.eslamasabry.oc.feature.agents.signin.SignInCheck
import io.github.eslamasabry.oc.feature.agents.signin.SignInCommand
import io.github.eslamasabry.oc.feature.agents.signin.SignInStartStep
import io.github.eslamasabry.oc.feature.agents.signin.TerminalEvent
import io.github.eslamasabry.oc.feature.agents.signin.TerminalTarget
import io.github.eslamasabry.oc.feature.agents.signin.TestLoop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The sign-in terminal screen (plan 07 G03, plan 17 §1.5 "Sign in"; AGENTS-BACKENDS-13/14). */
class SignInTerminalPresenterTest {
    private val loop = TestLoop()
    private val host = FakeTerminalHost()
    private val agents = FakeAgents()
    private val lease = FakeLease()
    private val foreground = FakeForeground(lease)
    private val links = RecordingLinks()
    private val clipboard = RecordingClipboard()
    private var left = 0
    private val owner = ProfileId("p1")

    private fun presenter(agent: String = "claude") =
        SignInTerminalPresenter(
            scope = loop.scope,
            owner = owner,
            agent = AgentRef(agent),
            agents = agents,
            host = host,
            foreground = foreground,
            links = links,
            clipboard = clipboard,
            onLeave = { left++ },
        ).also { it.begin() }

    @Test
    fun `B-AGENT-129 Sign in opens a visible terminal running exactly Claude's own sign-in`() =
        loop.run {
            val screen = presenter("claude")
            loop.pump()
            assertEquals(listOf<TerminalTarget>(TerminalTarget.SignIn(FakeAgents.CLAUDE)), host.targets)
            assertEquals(listOf("p1/claude"), agents.commands)
            val ui = screen.state.value
            assertEquals(SignInStep.Running, ui.step)
            assertNotNull(ui.terminal)
            assertTrue(ui.inputEnabled)
            assertTrue(ui.usesClaudeAccount)
            assertEquals("Claude Code", ui.agentName)
        }

    @Test
    fun `B-LINUX-125 every phone agent with a terminal sign-in opens its own sign-in terminal`() =
        loop.run {
            presenter("claude")
            loop.pump()
            presenter("codex")
            loop.pump()
            val programs = host.targets.map { (it as TerminalTarget.SignIn).command.program }
            assertEquals(listOf("claude", "codex"), programs)
            assertEquals(
                SignInCommand("codex", listOf("login", "--device-auth"), home = "/home/oc/.oc-profiles/p1"),
                (host.targets[1] as TerminalTarget.SignIn).command,
            )
        }

    @Test
    fun `AGENTS-BACKENDS-14 an API-key agent shows the provider-key words and opens nothing`() =
        loop.run {
            val screen = presenter("qwen")
            loop.pump()
            assertEquals(SignInStep.ProviderKeyOnHost, screen.state.value.step)
            assertTrue(host.targets.isEmpty())
            assertEquals(0, foreground.reservations)
        }

    @Test
    fun `AGENTS-BACKENDS-14 an agent without a phone sign-in is unavailable and nothing runs`() =
        loop.run {
            val screen = presenter("cursor")
            loop.pump()
            assertEquals(SignInStep.Unavailable, screen.state.value.step)
            assertTrue(host.targets.isEmpty())
            assertEquals(0, foreground.reservations)
        }

    @Test
    fun `AGENTS-BACKENDS-13 the page and code the sign-in printed become Open sign-in page and Copy code`() =
        loop.run {
            val screen = presenter()
            loop.pump()
            assertFalse(screen.state.value.canOpenPage)
            val terminal = host.terminals.single()
            terminal.transcript.value = "Open https://claude.ai/oauth/authorize?code=true\nEnter code ABCD-12345\n"
            loop.pump()
            val ui = screen.state.value
            assertTrue(ui.canOpenPage)
            assertEquals("ABCD-12345", ui.code)
            // The address is the action and Details, not reading text (owner, 2026-11-18).
            assertEquals("Open https://claude.ai/\u2026\nEnter code ABCD-12345\n", ui.transcript)
            assertEquals("https://claude.ai/oauth/authorize?code=true", ui.pageAddress)
            screen.openSignInPage()
            screen.copyCode()
            loop.pump()
            assertEquals(listOf("https://claude.ai/oauth/authorize?code=true"), links.opened)
            assertEquals(listOf("ABCD-12345"), clipboard.copied)
        }

    @Test
    fun `AGENTS-BACKENDS-13 a page the agent asks to open goes only through the link policy`() =
        loop.run {
            presenter()
            loop.pump()
            host.terminals.single().eventsFlow.emit(TerminalEvent.OpenUrl("https://claude.ai/oauth/authorize"))
            loop.pump()
            assertEquals(listOf("https://claude.ai/oauth/authorize"), links.opened)
        }

    @Test
    fun `B-189 Claude's handed-over page is the one offered again, not the printed one that asks for a code`() =
        loop.run {
            val screen = presenter("claude")
            loop.pump()
            val handed =
                "https://claude.com/cai/oauth/authorize?code=true&client_id=9d1c&response_type=code" +
                    "&redirect_uri=http%3A%2F%2Flocalhost%3A45369%2Fcallback&state=XyZ"
            val printed =
                "https://claude.com/cai/oauth/authorize?code=true&client_id=9d1c&response_type=code" +
                    "&redirect_uri=https%3A%2F%2Fplatform.claude.com%2Foauth%2Fcode%2Fcallback&state=XyZ"
            val terminal = host.terminals.single()
            terminal.eventsFlow.emit(TerminalEvent.OpenUrl(handed))
            loop.pump()
            assertEquals(listOf(handed), links.opened)
            assertTrue(screen.state.value.canOpenPage)
            terminal.transcript.value = "If the browser didn't open, visit: $printed\n"
            loop.pump()
            screen.openSignInPage()
            loop.pump()
            assertEquals(listOf(handed, handed), links.opened)
            // A new sign-in forgets it: only that run's pages are offered.
            lease.held = true
            screen.startAgain()
            loop.pump()
            assertFalse(screen.state.value.canOpenPage)
        }

    @Test
    fun `DESKTOP-WEB-CROSS-30 Send to terminal writes what the field sends`() =
        loop.run {
            val screen = presenter()
            loop.pump()
            screen.send("pwd\r")
            loop.pump()
            assertEquals(listOf("pwd\r"), host.terminals.single().written)
        }

    @Test
    fun `DESKTOP-WEB-CROSS-30 the person's accessible-mode choice is kept, null follows the system`() =
        loop.run {
            val screen = presenter("codex")
            loop.pump()
            assertNull(screen.state.value.accessibleMode)
            screen.setAccessibleMode(true)
            assertEquals(true, screen.state.value.accessibleMode)
            screen.setAccessibleMode(false)
            assertEquals(false, screen.state.value.accessibleMode)
        }

    // Coordinator ruling 2026-11-13: an agent set up at first use (no read-only sign-in status) runs its own set-up in
    // the terminal; when that ends it is marked set up and the person goes back. No account is read or claimed.
    @Test
    fun `B-AGENT-113 a set-up terminal ends by marking the agent set up and going back, reading no account`() =
        loop.run {
            val screen = presenter("goose")
            loop.pump()
            assertEquals(
                SignInCommand("goose", listOf("configure"), home = "/home/oc/.oc-profiles/p1"),
                (host.targets.single() as TerminalTarget.SignIn).command,
            )
            assertTrue(screen.state.value.firstUse)
            assertFalse(screen.state.value.typesAuth)
            host.terminals.single().eventsFlow.emit(TerminalEvent.Exited(1))
            loop.pump()
            assertEquals(listOf("p1/goose"), agents.setUp)
            assertEquals(0, agents.confirms)
            assertEquals(SignInStep.SetUpDone, screen.state.value.step)
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-AGENT-113 Qwen Code's set-up opens its own TUI and says to type auth`() =
        loop.run {
            val screen = presenter("qwen-tui")
            loop.pump()
            assertEquals("qwen", (host.targets.single() as TerminalTarget.SignIn).command.program)
            assertTrue((host.targets.single() as TerminalTarget.SignIn).command.args.isEmpty())
            assertTrue(screen.state.value.typesAuth)
            screen.finish()
            loop.pump()
            assertEquals(listOf("p1/qwen-tui"), agents.setUp)
            assertEquals(SignInStep.SetUpDone, screen.state.value.step)
        }

    @Test
    fun `B-LINUX-096 after the sign-in exits the account is checked while protection holds, then released`() =
        loop.run {
            agents.signedIn = true
            val screen = presenter()
            loop.pump()
            host.terminals.single().eventsFlow.emit(TerminalEvent.Exited(0))
            loop.pump()
            assertEquals(1, agents.confirms)
            assertEquals(SignInStep.SignedIn, screen.state.value.step)
            assertFalse(screen.state.value.inputEnabled)
            assertEquals(1, lease.releases)
            assertTrue("remove 1" in host.calls)
        }

    @Test
    fun `AGENTS-BACKENDS-13 exit code 0 is not proof, signed in only on a SignedIn read, and Start again reopens`() =
        loop.run {
            agents.signedIn = false
            val screen = presenter()
            loop.pump()
            host.terminals.single().eventsFlow.emit(TerminalEvent.Exited(0))
            loop.pump()
            assertEquals(SignInStep.NotYet, screen.state.value.step)
            lease.held = true
            screen.startAgain()
            loop.pump()
            assertEquals(2, host.targets.size)
            assertEquals(SignInStep.Running, screen.state.value.step)
        }

    @Test
    fun `B-LINUX-096 Done while the sign-in still runs drains it, then checks the account`() =
        loop.run {
            agents.signedIn = true
            val screen = presenter()
            loop.pump()
            screen.finish()
            loop.pump()
            assertTrue("remove 1" in host.calls)
            assertEquals(1, agents.confirms)
            assertEquals(SignInStep.SignedIn, screen.state.value.step)
        }

    @Test
    fun `B-LINUX-095 leaving drains the sign-in first and stays when the drain failed`() =
        loop.run {
            val screen = presenter()
            loop.pump()
            host.closeFails = true
            screen.leave()
            loop.pump()
            assertEquals(0, left)
            assertTrue(screen.state.value.stopFailed)
            assertEquals(0, lease.releases)
            host.closeFails = false
            screen.leave()
            loop.pump()
            assertEquals(1, left)
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-LINUX-094 blocked notifications show their own words and never start a terminal`() =
        loop.run {
            lease.admission.value = SignInAdmission.NotificationsBlocked
            val screen = presenter()
            loop.pump()
            assertEquals(SignInStep.NotificationsBlocked, screen.state.value.step)
            assertTrue(host.targets.isEmpty())
        }

    @Test
    fun `B-SEC-098 a failed start shows fixed words with the code under Details only`() =
        loop.run {
            host.startFailure = "private CLI credential text"
            val screen = presenter()
            loop.pump()
            val ui = screen.state.value
            assertEquals(SignInStep.Failed, ui.step)
            assertEquals("sign-in-start", ui.failureCode)
            assertFalse("private" in ui.toString())
        }

    @Test
    fun `a failed start says where it stopped and keeps the technical text for Details`() =
        loop.run {
            host.failAt =
                SignInStartStep.Connect to
                    io.github.eslamasabry.oc.core.common.Failure.Network(
                        io.github.eslamasabry.oc.core.common.NetworkWhy.Refused,
                    )
            val screen = presenter()
            loop.pump()
            val ui = screen.state.value
            assertEquals(SignInStep.Failed, ui.step)
            assertEquals(SignInStartStep.Connect, ui.startStep)
            assertEquals("sign-in-start · connect\nNetwork (Refused)", ui.failureDetails)
        }

    @Test
    fun `Start again after a failed start clears the old reason and opens a new terminal`() =
        loop.run {
            host.failAt =
                SignInStartStep.Terminal to io.github.eslamasabry.oc.core.common.Failure.Server(500)
            val screen = presenter()
            loop.pump()
            assertEquals(SignInStep.Failed, screen.state.value.step)
            host.failAt = null
            lease.held = true // the fake hands out one lease; a real retry reserves a new one
            screen.startAgain()
            loop.pump()
            val ui = screen.state.value
            assertEquals(SignInStep.Running, ui.step)
            assertNull(ui.startStep)
            assertNull(ui.failureDetails)
            assertEquals(2, host.targets.size)
        }

    @Test
    fun `Antigravity opens in the readable view, whose text field takes the pasted code`() =
        loop.run {
            val antigravity =
                SignInTerminalPresenter(
                    scope = loop.scope,
                    owner = owner,
                    agent = AgentRef("antigravity"),
                    agents =
                        object : io.github.eslamasabry.oc.feature.agents.signin.SignInAgents by agents {
                            override fun plan(agent: AgentRef) =
                                io.github.eslamasabry.oc.feature.agents.signin.SignInPlan.Terminal("Antigravity", false)

                            override fun command(owner: ProfileId, agent: AgentRef) =
                                io.github.eslamasabry.oc.core.common.Outcome.Ok(
                                    SignInCommand("agy", listOf("acp", "--login"), home = "p1")
                                )
                        },
                    host = host,
                    foreground = foreground,
                    links = links,
                    clipboard = clipboard,
                    onLeave = { left++ },
                ).also { it.begin() }
            loop.pump()
            val ui = antigravity.state.value
            assertEquals(SignInStep.Running, ui.step)
            assertTrue(ui.pastesBrowserCode)
            assertEquals(true, ui.accessibleMode)
            assertNull(presenter("codex").state.value.accessibleMode)
        }

    @Test
    fun `Paste writes the clipboard's code into the running sign-in, trimmed, and nothing once it ended`() =
        loop.run {
            val screen = presenter()
            loop.pump()
            val terminal = host.terminals.single()
            screen.paste("  4/0AbCdEf-GhIj_KlMn\n")
            loop.pump()
            assertEquals(listOf("4/0AbCdEf-GhIj_KlMn"), terminal.written)
            screen.paste("   ")
            loop.pump()
            assertEquals(1, terminal.written.size)
            terminal.eventsFlow.emit(TerminalEvent.Exited(0))
            loop.pump()
            screen.paste("late")
            loop.pump()
            assertEquals(1, terminal.written.size)
        }

    @Test
    fun `AGENTS-BACKENDS-13 the screen drops the page, code and grid once the sign-in ended`() =
        loop.run {
            val screen = presenter()
            loop.pump()
            host.terminals.single().transcript.value = "code ABCD-12345 at https://claude.ai/x"
            loop.pump()
            screen.leave()
            loop.pump()
            assertNull(screen.state.value.code)
            assertFalse(screen.state.value.canOpenPage)
            assertNull(screen.state.value.terminal)
        }

    // Owner, 2026-11-18: "Login successful." in the terminal (the browser handed the answer back, no code asked), and
    // the page said "isn't signed in yet". Every answer that was not a clean "signedIn" read as "not signed in".

    @Test
    fun `B-AGENT-442 after Login successful with no code asked the page reads Signed in by itself, past a held-back read`() =
        loop.run {
            agents.tries = 4
            // The chat's loaded Claude Code program held the first read back; the next one answers.
            agents.answers += SignInCheck.CouldNotCheck(SignInCheck.CouldNotCheck.BUSY)
            agents.signedIn = true
            val screen = presenter()
            loop.pump()
            val terminal = host.terminals.single()
            terminal.transcript.value = "Paste code here if prompted > Login successful.\n"
            terminal.eventsFlow.emit(TerminalEvent.Exited(0))
            loop.pump()
            assertEquals(listOf(0, 1), agents.rounds)
            assertEquals(SignInStep.SignedIn, screen.state.value.step)
            assertEquals(1, host.targets.size)
        }

    @Test
    fun `B-AGENT-443 a read that cannot tell is Could not check, never isn't signed in, and Check sign-in again reads again`() =
        loop.run {
            agents.tries = 3
            // The phone's one check slot taken twice, then a time-out: the bound ends there.
            agents.answers += SignInCheck.CouldNotCheck(SignInCheck.CouldNotCheck.HOST_UNAVAILABLE)
            agents.answers += SignInCheck.CouldNotCheck(SignInCheck.CouldNotCheck.HOST_UNAVAILABLE)
            agents.answers += SignInCheck.CouldNotCheck(SignInCheck.CouldNotCheck.TIMED_OUT)
            val screen = presenter()
            loop.pump()
            host.terminals.single().eventsFlow.emit(TerminalEvent.Exited(0))
            loop.pump()
            assertEquals(SignInStep.CouldNotCheck, screen.state.value.step)
            assertEquals("timedOut", screen.state.value.checkCode)
            assertEquals(3, agents.confirms)
            // Check sign-in again: the account only, no new sign-in.
            agents.signedIn = true
            screen.checkAgain()
            loop.pump()
            assertEquals(SignInStep.SignedIn, screen.state.value.step)
            assertNull(screen.state.value.checkCode)
            assertEquals(1, host.targets.size)
        }

    @Test
    fun `B-AGENT-444 only the agent's own signed-out answer says it isn't signed in, at once`() =
        loop.run {
            agents.tries = 4
            agents.answers += SignInCheck.SignedOut
            val screen = presenter()
            loop.pump()
            host.terminals.single().eventsFlow.emit(TerminalEvent.Exited(0))
            loop.pump()
            assertEquals(SignInStep.NotYet, screen.state.value.step)
            assertEquals(1, agents.confirms)
            // Check again belongs to Could not check only.
            screen.checkAgain()
            loop.pump()
            assertEquals(1, agents.confirms)
        }

    @Test
    fun `B-AGENT-445 a read held back by a busy conversation waits under its own words, then says it could not check`() =
        loop.run {
            agents.tries = 3
            repeat(3) { agents.answers += SignInCheck.CouldNotCheck(SignInCheck.CouldNotCheck.BUSY) }
            val screen = presenter()
            val busyWhileWaiting = mutableListOf<Boolean>()
            agents.onRound = { busyWhileWaiting += screen.state.value.checkBusy }
            loop.pump()
            host.terminals.single().eventsFlow.emit(TerminalEvent.Exited(0))
            loop.pump()
            // Round 0 has not heard yet; rounds 1 and 2 run while the page says the conversation keeps it busy.
            assertEquals(listOf(false, true, true), busyWhileWaiting)
            assertEquals(SignInStep.CouldNotCheck, screen.state.value.step)
            assertEquals("agentBusy", screen.state.value.checkCode)
            assertFalse(screen.state.value.checkBusy)
        }

    @Test
    fun `B-AGENT-446 a code typed or pasted reaches the program for every agent whose sign-in may ask for one`() =
        loop.run {
            // Claude Code ("Paste code here if prompted") opens on the readable view with its field, like Antigravity.
            val claude = presenter("claude")
            loop.pump()
            assertEquals(true, claude.state.value.accessibleMode)
            assertTrue(claude.state.value.takesInput)
            val terminal = host.terminals.single()
            claude.send("abc123#xyz\r")
            claude.paste(" abc123#xyz \n")
            loop.pump()
            assertEquals(listOf("abc123#xyz\r", "abc123#xyz"), terminal.written)
            // Codex keeps the grid by default, and still takes a pasted code.
            val codex = presenter("codex")
            loop.pump()
            codex.paste("CODE-1234")
            loop.pump()
            assertEquals(listOf("CODE-1234"), host.terminals.last().written)
            // Once the program ended, nothing takes input.
            terminal.eventsFlow.emit(TerminalEvent.Exited(0))
            loop.pump()
            assertFalse(claude.state.value.takesInput)
        }
}
