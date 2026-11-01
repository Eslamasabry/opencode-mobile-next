@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.engine.runtime.server

import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.FakeLinuxHost
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeProblem
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Port of test/builtin_server_test.dart group startBuiltinServer and builtin_password_self_heal_test.dart. */
class PhoneServerStarterTest {
    private val host = FakeLinuxHost()
    private val phone = StartTarget(ProfileId("builtin"), password = "secret")

    private fun TestScope.starter(renew: (suspend (ProfileId) -> String?)? = null) =
        PhoneServerStarter(host, schedulerClock(), readyTimeout = 90.seconds, pollInterval = 2.seconds, renewPassword = renew)

    @Test
    fun `B-LINUX-037 starting writes the password, starts the profile's server and waits for its answer`() = runTest {
        assertNull(starter().start(phone))
        assertEquals(listOf("password", "launch"), host.calls)
        assertEquals(listOf("secret"), host.passwords)
        assertEquals(listOf<LaunchMode>(LaunchMode.Manual), host.launches)
    }

    @Test
    fun `B-LINUX-037 a profile that still needs the OpenCode 2 move never starts before the safe history move`() =
        runTest {
            val failure = starter().start(phone.copy(requiresOpenCode2Migration = true))
            assertEquals(StartProblem.Interrupted, failure?.problem)
            assertEquals(InterruptReason.NeedsOpenCode2Move, failure?.interrupt)
            assertTrue(host.calls.isEmpty())
            host.historyMoveRefused = true
            val refused = starter().start(phone.copy(requiresOpenCode2Migration = true), migrateLegacyHistory = true)
            assertEquals(InterruptReason.HistoryMoveRefused, refused?.interrupt)
            assertEquals(listOf("historyCheck"), host.calls)
        }

    @Test
    fun `B-LINUX-037 a cancelled confirmation never reports a successful start`() = runTest {
        var wanted = true
        host.duringProbe = { wanted = false }
        val failure = starter().start(phone, stillWanted = { wanted })
        assertEquals(StartProblem.Interrupted, failure?.problem)
        assertEquals(1, host.launchCount)
    }

    @Test
    fun `B-LINUX-037 a cancelled owner never starts the server`() = runTest {
        val failure = starter().start(phone, stillWanted = { false })
        assertEquals(StartProblem.Interrupted, failure?.problem)
        assertEquals(0, host.launchCount)
    }

    @Test
    fun `B-LINUX-035 a Stop during the password write prevents the launch`() = runTest {
        var wanted = true
        host.duringPasswordWrite = { wanted = false }
        val failure = starter().start(phone, stillWanted = { wanted })
        assertEquals(StartProblem.Interrupted, failure?.problem)
        assertEquals(0, host.launchCount)
    }

    @Test
    fun `B-LINUX-037 a server that exits is reported as exited and may be tried once more`() = runTest {
        host.dies = true
        val failure = starter().start(phone)
        assertEquals(StartProblem.Exited, failure?.problem)
        assertTrue(failure!!.retryable)
    }

    @Test
    fun `B-LINUX-037 a server that never answers times out after 90 seconds and is not retried`() = runTest {
        host.answersAfterStart = false
        val failure = starter().start(phone)
        assertEquals(StartProblem.TimedOut, failure?.problem)
        assertEquals(90.seconds, failure?.timeout)
        assertFalse(failure!!.retryable)
        assertEquals(46, host.probes, "a probe every 2 s for 90 s, plus the one at the deadline")
    }

    @Test
    fun `B-LINUX-037 unknown ownership requires app recovery and keeps the details`() = runTest {
        host.launchRefusal = RuntimeProblem.OwnershipUnknown
        val failure = starter().start(phone)
        assertEquals(StartProblem.ComponentRecovery, failure?.problem)
        assertEquals(StartStep.Start, failure?.step)
        assertFalse(failure!!.retryable)
        host.launchRefusal = RuntimeProblem.Generic
        val refused = starter().start(phone)
        assertEquals(StartProblem.Refused, refused?.problem)
        assertTrue(refused!!.retryable)
    }

    @Test
    fun `B-LINUX-037 a password the phone could not save is its own problem`() = runTest {
        host.passwordRefused = true
        assertEquals(StartProblem.PasswordNotSaved, starter().start(phone)?.problem)
        assertEquals(0, host.launchCount)
    }

    @Test
    fun `B-LINUX-025 a password the Keystore lost is replaced and the start continues`() = runTest {
        val renewed = mutableListOf<ProfileId>()
        val failure = starter(renew = { renewed += it; "fresh" }).start(phone.copy(password = ""))
        assertNull(failure)
        assertEquals(listOf(ProfileId("builtin")), renewed)
        assertEquals(listOf("fresh"), host.passwords)
        assertEquals(StartProblem.PasswordMissing, starter().start(phone.copy(password = ""))?.problem)
        assertEquals(StartProblem.PasswordMissing, starter(renew = { null }).start(phone.copy(password = ""))?.problem)
    }

    @Test
    fun `B-LINUX-035 an automatic recovery start is confirmed with the same generation`() = runTest {
        host.generation = 7
        assertNull(starter().start(phone, LaunchMode.Recovery(7)))
        assertEquals(listOf(7L), host.confirmed)
        assertEquals(listOf<LaunchMode>(LaunchMode.Recovery(7)), host.launches)
    }

    @Test
    fun `B-LINUX-038 a Stop that moved the generation first withdraws the app-open start`() = runTest {
        host.generation = 3
        host.personStops()
        val failure = starter().start(phone, LaunchMode.ForAppOpen(3))
        assertEquals(StartProblem.Refused, failure?.problem)
        assertFalse(host.running)
        assertTrue(host.userStopped, "the person's Stop still stands")
    }
}

/** A clock on the test scheduler's virtual time, so `delay` and deadlines agree. */
internal fun TestScope.schedulerClock(): Clock =
    object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_767_225_600_000 + testScheduler.currentTime)
    }
