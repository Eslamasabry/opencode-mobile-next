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
import io.github.eslamasabry.oc.feature.agents.signin.SignInCommand
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
            assertEquals(terminal.transcript.value, ui.transcript)
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
            val screen = presenter()
            loop.pump()
            assertNull(screen.state.value.accessibleMode)
            screen.setAccessibleMode(true)
            assertEquals(true, screen.state.value.accessibleMode)
            screen.setAccessibleMode(false)
            assertEquals(false, screen.state.value.accessibleMode)
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
}
