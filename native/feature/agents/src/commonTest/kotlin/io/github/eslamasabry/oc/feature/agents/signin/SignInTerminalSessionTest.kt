package io.github.eslamasabry.oc.feature.agents.signin

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Port of Dart `local_terminal_signin_foreground_test` onto the sign-in terminal session (plan 17 §1.5, 11 W11-08a). */
class SignInTerminalSessionTest {
    private val loop = TestLoop()
    private val host = FakeTerminalHost()

    private fun session(lease: FakeLease) = SignInTerminalSession(loop.scope, host, FakeForeground(lease))

    private fun signInStarts() = host.calls.filter { it.startsWith("sign-in ") }

    @Test
    fun `B-LINUX-094 reserves at once and waits for active foreground before the PTY starts`() =
        loop.run {
            val lease = FakeLease(pending = true)
            val foreground = FakeForeground(lease)
            val session = SignInTerminalSession(loop.scope, host, foreground)
            session.start(FakeAgents.CLAUDE)
            assertEquals(1, foreground.reservations)
            loop.pump()
            assertTrue(signInStarts().isEmpty())
            assertEquals(SignInSessionState.Starting, session.state.value)
            lease.admission.value = SignInAdmission.Ready
            loop.pump()
            assertIs<SignInSessionState.Running>(session.state.value)
            session.end()
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-LINUX-094 notifications blocked refuses with fixed words and never launches`() =
        loop.run {
            val lease = FakeLease(pending = true)
            val session = session(lease)
            session.start(FakeAgents.CLAUDE)
            lease.admission.value = SignInAdmission.NotificationsBlocked
            loop.pump()
            assertEquals(SignInProblem.NotificationsBlocked, session.state.failure)
            assertTrue(signInStarts().isEmpty())
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-LINUX-094 an admission refusal is a fixed code and never launches`() =
        loop.run {
            val lease = FakeLease(pending = true)
            val session = session(lease)
            session.start(FakeAgents.CLAUDE)
            lease.admission.value = SignInAdmission.Refused("service-refused")
            loop.pump()
            assertEquals("sign-in-admission/service-refused", session.state.failure?.code)
            assertTrue(signInStarts().isEmpty())
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-LINUX-094 an inactive admission cannot launch a PTY`() =
        loop.run {
            val lease = FakeLease().apply { held = false }
            val session = session(lease)
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            assertEquals("sign-in-admission/service-inactive", session.state.failure?.code)
            assertTrue(signInStarts().isEmpty())
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-SEC-098 native sign-in errors never expose platform or CLI text`() =
        loop.run {
            host.startFailure = "private CLI credential text"
            val lease = FakeLease()
            val session = session(lease)
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            val problem = session.state.failure
            assertEquals("sign-in-start", problem?.code)
            assertFalse("private" in problem.toString())
            assertFalse("private" in session.state.value.toString())
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-AGENT-129 the terminal runs exactly the sign-in command and nothing else`() =
        loop.run {
            val session = session(FakeLease())
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            assertEquals(listOf<TerminalTarget>(TerminalTarget.SignIn(FakeAgents.CLAUDE)), host.targets)
            assertEquals(listOf("claude", "auth", "login", "--claudeai"), FakeAgents.CLAUDE.let { listOf(it.program) + it.args })
            session.start(FakeAgents.CLAUDE) // a second start on a live session does nothing
            loop.pump()
            assertEquals(1, host.targets.size)
        }

    @Test
    fun `B-LINUX-095 ending a pending native start drains its late exact ID before releasing foreground`() =
        loop.run {
            host.startGate.value = false
            val lease = FakeLease()
            val session = session(lease)
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            var ended = false
            loop.scope.launch {
                session.end()
                ended = true
            }
            loop.pump()
            val endedEarly = ended
            val releasedEarly = lease.releases
            host.startGate.value = true
            loop.pump()
            assertFalse(endedEarly)
            assertEquals(0, releasedEarly)
            assertTrue("remove 1" in host.calls)
            assertTrue(ended)
            assertEquals(1, lease.releases)
            assertEquals(SignInSessionState.Ended, session.state.value)
        }

    @Test
    fun `B-LINUX-095 disposing a pending sign-in drains the late native ID without reopening the URL`() =
        loop.run {
            host.startGate.value = false
            val lease = FakeLease()
            val session = session(lease)
            val opened = mutableListOf<String>()
            loop.scope.launch { session.openUrls.collect { opened += it } }
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            loop.scope.launch { session.end() }
            loop.pump()
            val releasedEarly = lease.releases
            host.startGate.value = true
            loop.pump()
            host.terminals.single().eventsFlow.emit(TerminalEvent.OpenUrl("https://vercel.com/oauth/device"))
            loop.pump()
            assertEquals(0, releasedEarly)
            assertTrue("remove 1" in host.calls)
            assertEquals(1, lease.releases)
            assertTrue(opened.isEmpty())
        }

    @Test
    fun `B-LINUX-095 cancelling during admission ends at once without a native start`() =
        loop.run {
            val lease = FakeLease(pending = true)
            val session = session(lease)
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            assertIs<Outcome.Ok<Unit>>(session.end())
            assertEquals(1, lease.releases)
            assertTrue(signInStarts().isEmpty())
            lease.admission.value = SignInAdmission.Ready
            loop.pump()
            assertTrue(signInStarts().isEmpty())
        }

    @Test
    fun `B-LINUX-095 duplicate ends share one drain and hold protection until native removal completes`() =
        loop.run {
            val lease = FakeLease()
            val session = session(lease)
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            host.closeGate.value = false
            val results = mutableListOf<Outcome<Unit>>()
            loop.scope.launch { results += session.end() }
            loop.scope.launch { results += session.end() }
            loop.pump()
            assertEquals(0, lease.releases)
            assertTrue(lease.active)
            host.closeGate.value = true
            loop.pump()
            assertEquals(2, results.size)
            assertEquals(1, host.calls.count { it == "remove 1" })
            assertEquals(1, host.calls.count { it == "drain-begins 1" })
            session.end()
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-LINUX-095 a failed drain keeps protection and allows an exact-ID retry`() =
        loop.run {
            val lease = FakeLease()
            val session = session(lease)
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            host.closeFails = true
            val first = session.end()
            assertIs<Outcome.Failed>(first)
            assertEquals(0, lease.releases)
            assertTrue(lease.active)
            host.closeFails = false
            assertIs<Outcome.Ok<Unit>>(session.end())
            assertEquals(listOf("drain-begins 1", "drain-begins 1", "remove 1"), host.calls.filter { " 1" in it && !it.startsWith("sign-in") })
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-LINUX-096 exit holds foreground through authentication checking until explicit end`() =
        loop.run {
            val lease = FakeLease()
            val session = session(lease)
            val opened = mutableListOf<String>()
            loop.scope.launch { session.openUrls.collect { opened += it } }
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            val terminal = host.terminals.single()
            terminal.eventsFlow.emit(TerminalEvent.OpenUrl("https://vercel.com/oauth/device"))
            terminal.eventsFlow.emit(TerminalEvent.Exited(0))
            loop.pump()
            assertEquals(listOf("https://vercel.com/oauth/device"), opened)
            assertEquals(0, (session.state.value as SignInSessionState.Running).exitCode)
            assertTrue(lease.active)
            assertEquals(0, lease.releases)
            assertFalse("remove 1" in host.calls)
            session.end()
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-LINUX-096 foreground loss drains only its sign-in and leaves other terminals alone`() =
        loop.run {
            val other = host.open(TerminalTarget.SignIn(io.github.eslamasabry.oc.feature.agents.signin.SignInCommand("bash", emptyList())))
            assertIs<Outcome.Ok<TerminalModel>>(other)
            val lease = FakeLease()
            val session = session(lease)
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            lease.held = false
            lease.losses.emit("service-gone")
            loop.pump()
            assertEquals("sign-in-lost/service-gone", session.state.failure?.code)
            assertTrue("remove 2" in host.calls)
            assertFalse("remove 1" in host.calls)
            assertEquals(1, lease.releases)
        }

    @Test
    fun `B-LINUX-096 an unknown loss reason is reported as a fixed code, never echoed`() =
        loop.run {
            val lease = FakeLease()
            val session = session(lease)
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            lease.losses.emit("private native text")
            loop.pump()
            assertEquals("sign-in-lost/service-gone", session.state.failure?.code)
        }

    @Test
    fun `B-LINUX-096 URL handoff rechecks protection before a queued loss event arrives`() =
        loop.run {
            val lease = FakeLease()
            val session = session(lease)
            val opened = mutableListOf<String>()
            loop.scope.launch { session.openUrls.collect { opened += it } }
            session.start(FakeAgents.CLAUDE)
            loop.pump()
            lease.held = false
            host.terminals.single().eventsFlow.emit(TerminalEvent.OpenUrl("https://vercel.com/oauth/device"))
            loop.pump()
            assertTrue(opened.isEmpty())
            assertTrue("remove 1" in host.calls)
            assertEquals(1, lease.releases)
        }
}
