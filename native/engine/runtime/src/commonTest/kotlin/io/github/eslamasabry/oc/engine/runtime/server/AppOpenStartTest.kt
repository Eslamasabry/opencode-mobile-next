@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.engine.runtime.server

import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.FakeLinuxHost
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Port of test/phone_server_healing_test.dart groups "the launch start (QA B1)" and "a running server that does not answer". */
class AppOpenStartTest {
    private val host = FakeLinuxHost()
    private val phone = StartTarget(ProfileId("builtin"), password = "secret")
    private var policyAllows = true

    private fun TestScope.opening(): AppOpenStart {
        val clock = schedulerClock()
        return AppOpenStart(host, PhoneServerStarter(host, clock), clock, mayAct = { policyAllows })
    }

    @Test
    fun `B-LINUX-151 opening the app starts a stopped server with the guarded native start`() = runTest {
        host.generation = 7
        val result = opening().run(phone)
        assertEquals(AppOpenResult(starts = 1), result)
        assertEquals(listOf<LaunchMode>(LaunchMode.ForAppOpen(7)), host.launches)
        assertTrue(host.running)
    }

    @Test
    fun `B-LINUX-151 leaves an explicitly stopped server stopped`() = runTest {
        host.personStops()
        val result = opening().run(phone)
        assertEquals(LaunchLeave.PersonStopped, result.leave)
        assertEquals(0, host.launchCount)
        assertTrue(host.calls.isEmpty(), "no password write, no launch")
    }

    @Test
    fun `B-LINUX-151 leaves it stopped when restarting the phone server is off`() = runTest {
        policyAllows = false
        assertEquals(LaunchLeave.NotNow, opening().run(phone).leave)
        assertEquals(0, host.launchCount)
    }

    @Test
    fun `B-LINUX-151 only connects to a server that already answers`() = runTest {
        host.running = true
        host.uptime = 5.minutes
        assertEquals(LaunchLeave.Answering, opening().run(phone).leave)
        assertEquals(0, host.launchCount)
    }

    @Test
    fun `B-LINUX-151 tries once more after a fast failure, then leaves the reason, once per open`() = runTest {
        host.dies = true
        val open = opening()
        val result = open.run(phone)
        assertEquals(2, result.starts)
        assertEquals(StartProblem.Exited, result.failure?.problem)
        assertEquals(LaunchLeave.AlreadyUsed, open.run(phone).leave)
        assertEquals(2, host.launchCount)
        open.allowAgain()
        open.run(phone)
        assertEquals(4, host.launchCount, "the next app open may try again")
    }

    @Test
    fun `B-LINUX-151 a timeout is not tried again on its own`() = runTest {
        host.answersAfterStart = false
        val result = opening().run(phone)
        assertEquals(StartProblem.TimedOut, result.failure?.problem)
        assertEquals(1, host.launchCount)
    }

    @Test
    fun `B-LINUX-151 a failed crash restart counts as the first try`() = runTest {
        host.dies = true
        opening().run(phone, previous = StartFailure(StartProblem.Exited))
        assertEquals(1, host.launchCount)
    }

    @Test
    fun `B-LINUX-151 a start that needs the app restarted is not tried`() = runTest {
        val result = opening().run(phone, previous = StartFailure(StartProblem.ComponentRecovery))
        assertEquals(LaunchLeave.NeedsAppRestart, result.leave)
        assertEquals(0, host.launchCount)
    }

    @Test
    fun `B-LINUX-151 opening never supersedes a scheduled native retry or the idle owner`() = runTest {
        host.recoveryAuthority = true
        host.recoveryScheduled = true
        assertEquals(LaunchLeave.NativeRecoveryOwns, opening().run(phone).leave)
        host.recoveryAuthority = false
        host.recoveryScheduled = false
        host.idle = RuntimeStatus(serverIdlePolicySupported = true, serverIdleStopped = true)
        assertEquals(LaunchLeave.IdleHolds, opening().run(phone).leave)
        assertEquals(0, host.launchCount)
    }

    @Test
    fun `B-LINUX-152 a Stop during the launch health probe prevents the replacement`() = runTest {
        host.running = true
        host.rejectsPassword = true
        host.uptime = 5.minutes
        host.duringProbe = { host.personStops() }
        assertEquals(LaunchLeave.PersonStopped, opening().run(phone).leave)
        assertEquals(0, host.launchCount)
    }

    @Test
    fun `B-LINUX-152 a Stop while the launch password is saved prevents the replacement`() = runTest {
        host.running = true
        host.rejectsPassword = true
        host.uptime = 5.minutes
        host.duringPasswordWrite = { host.personStops() }
        val result = opening().run(phone)
        assertFalse(host.running)
        assertTrue(host.userStopped)
        assertEquals(1, host.launchCount, "the one guarded launch is refused natively; no second try")
        assertEquals(LaunchLeave.PersonStopped, result.leave)
    }

    @Test
    fun `B-LINUX-152 one that rejects our password is replaced at launch`() = runTest {
        host.running = true
        host.rejectsPassword = true
        host.uptime = 5.seconds
        assertNull(opening().run(phone).failure)
        assertEquals(1, host.launchCount)
    }

    @Test
    fun `B-LINUX-152 one older than the stale age that stays silent is replaced`() = runTest {
        host.running = true
        host.silent = true
        host.uptime = 5.minutes
        assertEquals(AppOpenResult(starts = 1), opening().run(phone))
    }

    @Test
    fun `B-LINUX-152 a young booting one is waited for, and a young one that never answers is replaced once stale`() =
        runTest {
            host.running = true
            host.silent = true
            host.uptime = 10.seconds
            var probes = 0
            host.duringProbe = { if (++probes == 3) host.silent = false }
            assertEquals(LaunchLeave.Answering, opening().run(phone).leave)
            assertEquals(0, host.launchCount)

            host.duringProbe = null
            host.silent = true
            val started = testScheduler.currentTime
            assertEquals(AppOpenResult(starts = 1), opening().run(phone))
            assertTrue(testScheduler.currentTime - started >= 80_000, "waited until 90 s of age")
        }

    @Test
    fun `B-LINUX-152 the rules wait the stale age less the uptime, and 30 s for an unknown age`() {
        val now = Instant.parse("2026-11-01T12:00:00Z")
        val running = RuntimeStatus(installed = true, serverRunning = true, serverRestartWanted = true)
        assertEquals(LaunchStep.Probe(now + 80.seconds), LaunchStartRules.firstLook(running.copy(serverUptime = 10.seconds), now))
        assertEquals(LaunchStep.Probe(now + 30.seconds), LaunchStartRules.firstLook(running, now))
        assertEquals(LaunchStep.Probe(now), LaunchStartRules.firstLook(running.copy(serverUptime = 5.minutes), now))
        assertEquals(LaunchStep.Leave(LaunchLeave.StatusUnreadable), LaunchStartRules.firstLook(null, now))
        assertEquals(LaunchStep.Leave(LaunchLeave.NotInstalled), LaunchStartRules.firstLook(RuntimeStatus(), now))
    }
}
