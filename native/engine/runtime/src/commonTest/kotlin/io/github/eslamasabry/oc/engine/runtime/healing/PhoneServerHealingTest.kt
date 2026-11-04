package io.github.eslamasabry.oc.engine.runtime.healing

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** Port of test/phone_server_healing_test.dart (B-LINUX-153, -154, -155, -156): the healing policy, not the widget. */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneServerHealingTest {
    private fun TestScope.world(): HealingWorld = HealingWorld(this, testScheduler)

    // ---- B-LINUX-153: reconnect back-off and the streamed-update economy

    @Test
    fun `B-LINUX-153 the back-off holds automatic reconnects while Try again does not wait`() = runTest {
        val w = world()
        w.host.running = true
        w.connection.refuseConnects = 5
        val owner = w.bind(reconnectBackoff = 1.hours)
        owner.setForeground(true)
        owner.startForLaunch(w.phone)
        owner.check(w.phone)
        owner.connectIfNeeded(w.phone)
        assertEquals(1, w.connection.connections.size)
        owner.startForLaunch(w.phone, retry = true)
        assertEquals(2, w.connection.connections.size)
        assertEquals(0, w.host.starts + w.host.restarts)
    }

    @Test
    fun `B-LINUX-153 a healthy server whose connect failed is connected again by the health poll once per back-off`() = runTest {
        val w = world()
        w.host.running = true
        w.connection.refuseConnects = 1
        val owner = w.bind(reconnectBackoff = 200.milliseconds)
        owner.setForeground(true)
        owner.startForLaunch(w.phone)
        owner.check(w.phone)
        assertEquals(listOf("phone"), w.connection.connections)
        assertEquals(0, w.host.starts + w.host.restarts)

        w.clock.extra = 250.milliseconds
        owner.check(w.phone)
        assertEquals(listOf("phone", "phone"), w.connection.connections)
        // Connected now: the next health poll does not connect again.
        owner.check(w.phone)
        assertEquals(listOf("phone", "phone"), w.connection.connections)
    }

    @Test
    fun `B-LINUX-153 a notify storm costs no native call`() = runTest {
        val w = world()
        val owner = w.bind(localAgentWorkBusy = { false })
        owner.setForeground(true)
        advanceUntilIdle()
        val statuses = w.host.statusCalls
        val observed = w.host.observations.size
        repeat(25) { owner.onConnectionChanged() }
        advanceUntilIdle()
        assertEquals(statuses, w.host.statusCalls)
        assertEquals(observed, w.host.observations.size)
    }

    @Test
    fun `B-LINUX-153 work observation follows a busy change only`() = runTest {
        val w = world()
        var busy = false
        val owner = w.bind(localAgentWorkBusy = { busy })
        owner.setForeground(true)
        advanceUntilIdle()
        val observed = w.host.observations.size
        owner.onConnectionChanged()
        advanceUntilIdle()
        assertEquals(observed, w.host.observations.size)
        busy = true
        owner.onConnectionChanged()
        advanceUntilIdle()
        assertEquals(observed + 1, w.host.observations.size)
        assertEquals("phone" to true, w.host.observations.last())
    }

    @Test
    fun `B-LINUX-153 going down checks the server once and restarts a dead one`() = runTest {
        val w = world()
        w.host.running = true
        val healing = w.bind()
        healing.setForeground(true)
        advanceUntilIdle()
        assertEquals(0, w.host.restarts)
        w.host.running = false
        w.host.healthy = false
        val statuses = w.host.statusCalls
        w.connection.shownStatus = LinkStatus.Reconnecting
        healing.onConnectionChanged()
        advanceUntilIdle()
        assertTrue(w.host.statusCalls > statuses)
        assertEquals(1, w.host.restarts)
        val after = w.host.statusCalls
        healing.onConnectionChanged()
        healing.onConnectionChanged()
        advanceUntilIdle()
        assertEquals(after, w.host.statusCalls)
    }

    // ---- B-LINUX-154: foreground idle resume

    @Test
    fun `B-LINUX-154 foreground idle resume awaits the helper and preserves an exhausted budget`() = runTest {
        val w = world()
        w.idleReceipt()
        val helpers = mutableListOf<Long>()
        val gate = CompletableDeferred<Unit>()
        val owner =
            w.bind(resumeAgentsAfterIdle = { id, generation ->
                assertEquals("phone", id)
                assertTrue(w.host.running)
                helpers += generation
                gate.await()
                Unit.ok()
            })
        owner.setForeground(true)
        val launch = async { owner.startForLaunch(w.phone) }
        runCurrent()
        assertEquals(listOf(9L), helpers)
        assertEquals(0, w.host.idleCompletes)
        assertEquals(0, w.host.starts)
        assertEquals(0, w.host.restarts)
        gate.complete(Unit)
        launch.await()
        assertEquals(1, w.host.idleResumes)
        assertEquals(1, w.host.idleCompletes)
        assertEquals(0, w.host.starts)
        assertEquals(0, w.host.restarts)
        assertEquals(0, w.starter.manualReadyCount)
        assertEquals(3, w.budgets.saved.getValue("phone").attempts)
    }

    @Test
    fun `B-LINUX-154 an absent idle helper never calls BA and keeps the generation`() = runTest {
        val w = world()
        w.idleReceipt(helper = false)
        var helpers = 0
        val owner = w.bind(resumeAgentsAfterIdle = { _, _ -> helpers++; Unit.ok() })
        owner.setForeground(true)
        owner.startForLaunch(w.phone)
        assertEquals(0, helpers)
        assertEquals(1, w.host.idleCompletes)
        assertEquals(9L, w.host.idleGeneration)
        assertEquals(0, w.host.starts)
    }

    @Test
    fun `B-LINUX-154 a completed idle generation cannot refill the budget through a stale launch`() = runTest {
        val w = world()
        w.idleReceipt(helper = false)
        w.host.idleStopped = false
        w.host.running = true
        w.host.healthy = false
        w.host.uptime = 5.minutes
        val owner = w.bind()
        owner.setForeground(true)
        owner.startForLaunch(w.phone)
        assertEquals(0, w.host.starts)
        assertEquals(0, w.host.restarts)
        assertEquals(0, w.starter.manualReadyCount)
        assertEquals(3, w.budgets.saved.getValue("phone").attempts)
    }

    @Test
    fun `B-LINUX-154 the launch start cannot retry or connect after an interruption during its own start`() = runTest {
        for (interruption in listOf("pause", "pause and resume", "deletion", "transfer")) {
            val w = world()
            w.budgets.saved["phone"] = RecoveryBudget(attempts = 3)
            w.host.dies = true
            w.host.startGate = CompletableDeferred()
            val owner = w.bind()
            owner.setForeground(true)
            val launching = async { owner.startForLaunch(w.phone) }
            w.host.startEntered.await()
            when (interruption) {
                "pause" -> owner.setForeground(false)
                "pause and resume" -> {
                    owner.setForeground(false)
                    owner.setForeground(true)
                }
                "deletion" -> {
                    w.connection.saved.clear()
                    owner.onProfilesChanged()
                }
                "transfer" -> {
                    val other = w.profile("other")
                    w.connection.saved += other
                    w.connection.policy[other.id to AutomationBehavior.RestartPhoneServer] = false
                    w.connection.prefs[FakeHealingConnection.OWNER_KEY] = other.id
                    owner.onProfilesChanged()
                }
            }
            w.host.startGate!!.complete(Unit)
            launching.await()
            advanceUntilIdle()
            assertEquals(1, w.host.starts, interruption)
            assertTrue(w.connection.connections.isEmpty(), interruption)
            owner.dispose()
        }
    }

    @Test
    fun `B-LINUX-154 a malformed native idle receipt cannot reach the launch fallback`() = runTest {
        val w = world()
        w.idleReceipt()
        w.host.idleValid = false
        val owner = w.bind()
        owner.setForeground(true)
        owner.startForLaunch(w.phone)
        assertEquals(IdleFailure.Unavailable, owner.idleResumeFailure)
        assertEquals(0, w.host.idleResumes)
        assertEquals(0, w.host.starts)
        assertEquals(0, w.host.restarts)
    }

    @Test
    fun `B-LINUX-154 a failed helper restore keeps the idle generation and forbids the manual fallback`() = runTest {
        val w = world()
        w.idleReceipt()
        val owner = w.bind(resumeAgentsAfterIdle = { _, _ -> Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic)) })
        owner.setForeground(true)
        owner.startForLaunch(w.phone)
        assertEquals(IdleFailure.Unavailable, owner.idleResumeFailure)
        assertTrue(w.host.helperStopped)
        assertEquals(0, w.host.idleCompletes)
        assertEquals(0, w.host.starts)
        assertEquals(0, w.host.restarts)
    }

    @Test
    fun `B-LINUX-154 an absent BA idle helper is unavailable, never a guess`() = runTest {
        val w = world()
        w.idleReceipt()
        val owner = w.bind(resumeAgentsAfterIdle = null)
        owner.setForeground(true)
        owner.startForLaunch(w.phone)
        assertEquals(IdleFailure.Unavailable, owner.idleResumeFailure)
        assertEquals(0, w.host.idleCompletes)
        assertEquals(0, w.host.starts)
    }

    @Test
    fun `B-LINUX-154 an idle server completion cannot restore the helper after an interruption`() = runTest {
        for (interruption in listOf("pause", "deletion", "transfer", "dispose", "stop")) {
            val w = world()
            w.idleReceipt()
            w.host.idleGate = CompletableDeferred()
            var helpers = 0
            val owner = w.bind(resumeAgentsAfterIdle = { _, _ -> helpers++; Unit.ok() })
            owner.setForeground(true)
            val checking = async { owner.check(w.phone) }
            w.host.idleEntered.await()
            when (interruption) {
                "pause" -> owner.setForeground(false)
                "deletion" -> {
                    w.connection.saved.clear()
                    owner.onProfilesChanged()
                }
                "transfer" -> {
                    val second = w.profile("second")
                    w.connection.saved += second
                    w.connection.prefs[FakeHealingConnection.OWNER_KEY] = second.id
                    owner.onProfilesChanged()
                }
                "dispose" -> owner.dispose()
                "stop" -> w.host.wanted = false
            }
            // Like the Dart awaits around the interruption, work it started runs up to its next native call.
            runCurrent()
            w.host.idleGate!!.complete(Unit)
            checking.await()
            advanceUntilIdle()
            assertEquals(0, helpers, interruption)
            assertEquals(0, w.host.idleCompletes, interruption)
            assertEquals(0, w.host.starts, interruption)
            assertEquals(0, w.host.restarts, interruption)
            owner.dispose()
        }
    }

    // ---- B-LINUX-155: the local agents' CPU lease

    @Test
    fun `B-LINUX-155 local agent truth owns a separate lease across background and unknown`() = runTest {
        val w = world()
        w.connection.prefs["oc.phoneAgentOwner.phone"] = "shared_agent_owner"
        var busy: Boolean? = true
        val queried = mutableListOf<String>()
        val owner = w.bind(localAgentWorkBusy = { id -> queried += id; busy })
        advanceUntilIdle()
        assertEquals(setOf("phone"), queried.toSet())
        assertEquals(1, w.host.agentHolds.size)
        assertEquals("shared_agent_owner", w.host.agentHolds.single().first)
        assertTrue(w.host.agentHolds.single().third)
        val leaseId = w.host.agentHolds.single().second
        assertEquals(listOf<Pair<String, Boolean?>>("phone" to true), w.host.observations, "native idle truth uses the readable server alias")
        owner.setForeground(false)
        busy = null
        owner.onProfilesChanged()
        advanceUntilIdle()
        assertTrue(w.host.agentHolds.none { !it.third }, "unknown does not end an observed busy run")
        busy = false
        owner.onProfilesChanged()
        advanceUntilIdle()
        assertEquals(Triple("shared_agent_owner", leaseId, false), w.host.agentHolds.last())
        assertEquals(0, w.host.starts)
        assertEquals(0, w.host.restarts)
    }

    @Test
    fun `B-LINUX-155 the agent lease closes when its runtime profile becomes unreadable`() = runTest {
        val w = world()
        val owner = w.bind(localAgentWorkBusy = { true })
        advanceUntilIdle()
        val lease = w.host.agentHolds.single()
        w.connection.blocked += "phone"
        owner.onProfilesChanged()
        advanceUntilIdle()
        assertEquals(Triple(lease.first, lease.second, false), w.host.agentHolds.last())
    }

    @Test
    fun `B-LINUX-155 missing or throwing local agent inventory never creates a CPU lease`() = runTest {
        val w = world()
        w.bind(localAgentWorkBusy = { throw IllegalStateException("synthetic unavailable inventory") })
        advanceUntilIdle()
        assertTrue(w.host.agentHolds.isEmpty())
        val w2 = world()
        w2.bind(localAgentWorkBusy = null)
        advanceUntilIdle()
        assertTrue(w2.host.agentHolds.isEmpty())
        assertEquals(listOf<Pair<String, Boolean?>>("phone" to null), w2.host.observations)
    }

    // ---- B-LINUX-156: owners and explicit actions

    @Test
    fun `B-LINUX-156 the app-lifetime owner heals only in the foreground and reconnects once`() = runTest {
        val w = world()
        val owner = w.bind()
        owner.check(w.phone)
        assertEquals(0, w.host.restarts)
        assertTrue(w.connection.acts.isEmpty())

        owner.setForeground(true)
        owner.check(w.phone)
        assertEquals(1, w.host.restarts)
        assertEquals(1, w.connection.acts.size)
        assertTrue(w.connection.acts.single().second.startsWith("builtin-restart:"), "a restart act")
        assertEquals(listOf("phone"), w.connection.connections)
        owner.check(w.phone)
        owner.connectIfNeeded(w.phone)
        assertEquals(1, w.connection.acts.size)
        assertEquals(listOf("phone"), w.connection.connections)

        owner.setForeground(false)
        w.host.running = false
        owner.check(w.phone)
        advanceUntilIdle()
        assertEquals(1, w.host.restarts)
    }

    @Test
    fun `B-LINUX-156 the persisted runtime owner wins over another selected local profile`() = runTest {
        val w = world()
        val other = w.profile("other")
        w.connection.saved += other
        w.connection.activeId = other.id
        w.connection.prefs[FakeHealingConnection.OWNER_KEY] = w.phone.id
        val owner = w.bind()
        owner.setForeground(true)
        owner.check(w.phone)
        owner.check(other)
        advanceUntilIdle()
        assertEquals(1, w.host.restarts)
        assertEquals("phone", w.connection.acts.single().first)
        assertTrue(w.connection.connections.isEmpty())
        assertFalse(w.budgets.saved.containsKey(other.id))
    }

    @Test
    fun `B-LINUX-156 a restart confirmed after its initial timeout still reconnects`() = runTest {
        val w = world()
        w.host.healthy = false
        val owner = w.bind()
        owner.setForeground(true)
        owner.check(w.phone)
        advanceUntilIdle()
        assertEquals(1, w.host.restarts)
        assertNotNull(w.starter.failureFor("phone"))
        assertTrue(w.connection.acts.isEmpty())
        assertTrue(w.connection.connections.isEmpty())

        w.host.healthy = true
        owner.check(w.phone)
        assertEquals(1, w.host.restarts)
        assertNull(w.starter.failureFor("phone"))
        assertEquals(1, w.connection.acts.size)
        assertEquals(listOf("phone"), w.connection.connections)
    }

    private fun HealingWorld.nativeBinding() {
        host.authority = true
        host.wanted = false
        host.bindingGate = CompletableDeferred()
    }

    @Test
    fun `B-LINUX-156 an explicit Start waits for the native owner binding before its launch`() = runTest {
        val w = world()
        w.nativeBinding()
        w.bind().setForeground(true)
        runCurrent()
        val starting = async { w.starter.start(w.phone) }
        runCurrent()
        assertTrue(w.host.bindingRequested.isCompleted)
        assertEquals(0, w.host.starts)
        w.host.bindingGate!!.complete(Unit)
        assertNull(starting.await())
        assertTrue(w.host.restorationArmedOnStart)
        assertEquals(1, w.host.starts)
        assertEquals("phone", w.connection.prefs[FakeHealingConnection.OWNER_KEY])
    }

    @Test
    fun `B-LINUX-156 a late native binding cannot launch after an owner transfer or a profile deletion`() = runTest {
        for (transfer in listOf(false, true)) {
            val w = world()
            w.nativeBinding()
            val owner = w.bind()
            owner.setForeground(true)
            runCurrent()
            val starting = async { w.starter.start(w.phone) }
            runCurrent()
            if (transfer) {
                w.connection.saved += w.profile("other")
                w.connection.prefs[FakeHealingConnection.OWNER_KEY] = "other"
                owner.onProfilesChanged()
            } else {
                w.connection.blocked += "phone"
            }
            w.host.bindingGate!!.complete(Unit)
            assertNotNull(starting.await(), "transfer=$transfer")
            assertEquals(0, w.host.starts, "transfer=$transfer")
            owner.dispose()
        }
    }

    @Test
    fun `B-LINUX-156 ambiguous legacy profiles wait for an explicit Start to claim ownership`() = runTest {
        val w = world()
        val other = w.profile("other")
        w.connection.saved += other
        val owner = w.bind()
        owner.setForeground(true)
        owner.check(w.phone)
        owner.check(other)
        advanceUntilIdle()
        assertEquals(0, w.host.restarts)
        assertTrue(w.connection.acts.isEmpty())

        assertNull(w.starter.start(other))
        assertEquals(other.id, w.connection.prefs[FakeHealingConnection.OWNER_KEY])
        owner.check(other)
        assertTrue(w.connection.acts.isEmpty())
        w.host.running = false
        owner.check(other)
        assertEquals(1, w.host.restarts)
        assertEquals("other", w.connection.acts.single().first)
    }

    @Test
    fun `B-LINUX-156 the shared restart policy and an explicit Stop each prevent healing`() = runTest {
        val w = world()
        w.connection.policy["phone" to AutomationBehavior.RestartPhoneServer] = false
        val owner = w.bind()
        owner.setForeground(true)
        owner.check(w.phone)
        advanceUntilIdle()
        assertEquals(0, w.host.restarts)

        w.host.wanted = false
        w.connection.policy["phone" to AutomationBehavior.RestartPhoneServer] = true
        owner.recovery.policyChanged()
        owner.check(w.phone)
        advanceUntilIdle()
        assertEquals(0, w.host.restarts)
        assertTrue(w.connection.acts.isEmpty())
    }

    @Test
    fun `B-LINUX-156 an explicit connect works with automatic reconnect off and shares deduplication`() = runTest {
        val w = world()
        w.connection.policy["phone" to AutomationBehavior.Reconnect] = false
        w.host.wanted = false
        val owner = w.bind()
        owner.setForeground(true)
        owner.check(w.phone)
        advanceUntilIdle()

        owner.connectIfNeeded(w.phone)
        assertTrue(w.connection.connections.isEmpty())
        owner.connectIfNeeded(w.phone, automatic = false)
        assertEquals(listOf("phone"), w.connection.connections)

        w.connection.policy["phone" to AutomationBehavior.Reconnect] = true
        owner.connectIfNeeded(w.phone)
        owner.connectIfNeeded(w.phone, automatic = false)
        assertEquals(listOf("phone"), w.connection.connections)
        assertEquals(0, w.host.restarts)
    }

    @Test
    fun `B-LINUX-156 profile deletion admission cannot be reopened by a store notification`() = runTest {
        val w = world()
        w.connection.blocked += "phone"
        val owner = w.bind()
        owner.setForeground(true)
        owner.onProfilesChanged()
        owner.check(w.phone)
        advanceUntilIdle()
        assertEquals(0, w.host.restarts)
        assertTrue(w.connection.acts.isEmpty())
        assertFalse(w.budgets.saved.containsKey("phone"))

        w.connection.blocked.clear()
        owner.onConnectionChanged()
        owner.check(w.phone)
        advanceUntilIdle()
        assertEquals(1, w.host.restarts)
        assertEquals(1, w.connection.acts.size)
    }
}
