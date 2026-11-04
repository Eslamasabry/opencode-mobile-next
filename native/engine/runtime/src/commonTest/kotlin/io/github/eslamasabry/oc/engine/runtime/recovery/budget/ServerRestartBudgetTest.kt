package io.github.eslamasabry.oc.engine.runtime.recovery.budget

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.bridge.BuiltinLinuxBridge
import io.github.eslamasabry.oc.engine.runtime.bridge.BuiltinScripts
import io.github.eslamasabry.oc.engine.runtime.bridge.RestoreRecipe
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.LaunchMode
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerPort
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerStarter
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.engine.runtime.server.StartTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Port of test/builtin_server_recovery_native_test.dart over the real bridge and the shared start path. */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerRestartBudgetTest {
    private val phone = ProfileId("phone")
    private val key = ServerRestartBudget.keyFor(phone)
    private val markers = mutableMapOf<String, String>()
    private val native = NativeRecoveryChannel(markers)
    private val bridge = BuiltinLinuxBridge(native, supported = true)
    private var now = Instant.parse("2026-10-07T00:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private var recordsAllowed = true
    private val acts = linkedMapOf<String, Instant>()
    private var restartAllowed = true
    private val profiles = mutableSetOf(phone)

    private val host =
        object : RecoveryHost {
            override fun profileExists(profile: ProfileId) = profile in profiles

            override fun isInAppServer(profile: ProfileId) = true

            override fun restartAllowed(profile: ProfileId) = restartAllowed

            override suspend fun healthy(profile: ProfileId) = native.running

            override suspend fun recordRestart(profile: ProfileId, eventId: String, at: Instant): Boolean {
                if (!recordsAllowed) return false
                if (eventId !in acts) acts[eventId] = at
                return true
            }

            override suspend fun readMarker(key: String) = markers[key]

            override suspend fun writeMarker(key: String, value: String): Boolean {
                markers[key] = value
                return true
            }
        }

    /** The shared start path over the same bridge (the Android binding's PhoneServerPort). */
    private val port =
        object : PhoneServerPort {
            override suspend fun checkHistoryMove(): Outcome<Unit> = Unit.ok()

            override suspend fun writePassword(password: String): Outcome<Unit> =
                bridge.run(BuiltinScripts.writePasswordScript(password)!!).map { }

            override suspend fun launch(profile: ProfileId, mode: LaunchMode): Outcome<Unit> =
                when (mode) {
                    LaunchMode.Manual -> bridge.startServer("serve", restoreRecipe = RestoreRecipe(profile))
                    is LaunchMode.Recovery -> bridge.restartServer("serve", mode.expectedGeneration)
                    is LaunchMode.ForAppOpen -> bridge.startServerForLaunch("serve", mode.expectedGeneration)
                    is LaunchMode.AfterUpdate -> bridge.restartServer("serve", mode.expectedGeneration)
                }

            override suspend fun probe(password: String) = if (native.running) ProbeAnswer.Answered else ProbeAnswer.NoAnswer

            override suspend fun refreshStatus(): Outcome<RuntimeStatus> = bridge.status()

            override suspend fun confirmRecovery(expectedGeneration: Long): Outcome<Unit> = bridge.confirmServerRecovery(expectedGeneration)
        }
    private val starter = PhoneServerStarter(port, clock, readyTimeout = Duration.ZERO, pollInterval = Duration.ZERO)
    private val target = StartTarget(phone, "test-password")

    private val restarter =
        object : RecoveryRestarter {
            override val starting = false

            override suspend fun restart(profile: ProfileId, recoveryGeneration: Long, stillWanted: () -> Boolean): Boolean =
                starter.start(target, LaunchMode.Recovery(recoveryGeneration), stillWanted = stillWanted) == null
        }

    private lateinit var recovery: ServerRestartBudget

    private fun TestScope.owner(): ServerRestartBudget =
        ServerRestartBudget(bridge, host, restarter, clock, backgroundScope).apply { setForeground(true) }

    private fun TestScope.recreateOwner() {
        recovery.dispose()
        runCurrent()
        recovery = owner()
    }

    private fun start(block: suspend TestScope.() -> Unit) =
        runTest {
            recovery = owner()
            block()
        }

    /** A person's Start through the shared starter; a healthy one is reported to the budget, as the shell does. */
    private suspend fun manualStart(): Boolean {
        val ok = starter.start(target) == null
        if (ok) recovery.manualStartConfirmed(phone)
        return ok
    }

    private val phase: RecoveryPhase get() = recovery.value.value.phase

    private fun markerV2() {
        markers[key] = ServerRestartBudget.MARKER
    }

    private fun legacy(budget: Map<String, Any?>) {
        markers[key] = RecoveryBudget.json(budget)
    }

    @Test
    fun `B-LINUX-032 a spent legacy budget migrates once with its marker before activation`() = start {
        legacy(budgetMap(attempts = 2))
        native.running = true
        // Migration alone: a healthy check in an open app would also start the count again.
        native.presenceRefused = true
        recovery.check(phone)
        assertEquals(RecoveryPhase.Ready, phase)
        assertEquals(2L, (native.stagedMigrations.single()["attempts"] as Number).toLong())
        assertNull(native.migrations.single())
        assertTrue(native.markerValid)
        assertEquals(2, native.attempts)
        assertEquals(2, recovery.value.value.attempts)

        recreateOwner()
        recovery.check(phone)
        assertEquals(2, native.migrations.size)
        assertNull(native.migrations.last())
        assertEquals(1, native.stagedMigrations.size)
        assertEquals(2, native.attempts)
        assertEquals(0, native.directStarts)
    }

    @Test
    fun `B-LINUX-032 failed native staging keeps legacy spent attempts across retry`() = start {
        legacy(budgetMap(attempts = 2))
        native.stageWriteFails = true
        recovery.check(phone)
        assertEquals(RecoveryPhase.StorageUnavailable, phase)
        assertFalse(native.enabled)
        assertEquals(0, native.directStarts)
        assertNull(native.budget)
        assertEquals(2, RecoveryBudget.readJson(markers[key])!!.attempts)
        assertFalse(native.markerValid)

        recreateOwner()
        native.stageWriteFails = false
        native.failStart = true
        recovery.check(phone)
        assertEquals(3, native.attempts)
        assertEquals(1, native.directStarts)
        recovery.check(phone)
        recreateOwner()
        recovery.check(phone)
        assertEquals(RecoveryPhase.Exhausted, phase)
        assertEquals(3, native.attempts)
        assertEquals(1, native.directStarts)
    }

    @Test
    fun `B-LINUX-032 retrying a staged migration never overwrites native spent attempts`() = start {
        native.budget = budgetMap(attempts = 3, revision = 4)
        legacy(budgetMap(attempts = 1))
        recovery.check(phone)
        assertEquals(RecoveryPhase.Exhausted, phase)
        assertEquals(3, native.attempts)
        assertEquals(0, native.directStarts)
        assertEquals(1L, (native.stagedMigrations.single()["attempts"] as Number).toLong())
    }

    @Test
    fun `B-LINUX-032 a valid v2 marker with a missing native budget admits no restart`() = start {
        markerV2()
        recovery.check(phone)
        assertEquals(0, native.directStarts)
        assertNull(native.budget)
        assertFalse(native.enabled)
        assertNotEquals(RecoveryPhase.Ready, phase)
    }

    @Test
    fun `B-LINUX-032 a corrupt migration marker admits no native binding or restart`() = start {
        for (raw in listOf("broken", """{"version":2,"nativeAuthority":false}""")) {
            markers[key] = raw
            recovery.check(phone)
            assertEquals(RecoveryPhase.StorageUnavailable, phase, raw)
            assertTrue(native.migrations.isEmpty())
            assertEquals(0, native.directStarts)
        }
    }

    @Test
    fun `B-LINUX-032 a malformed native snapshot fails closed`() = start {
        native.corruptBudget = true
        recovery.check(phone)
        assertEquals(RecoveryPhase.StorageUnavailable, phase)
        assertEquals(0, native.directStarts)
        assertTrue(acts.isEmpty())
    }

    @Test
    fun `B-LINUX-033 direct and background reservations share three attempts across owners`() = start {
        native.failStart = true
        recovery.check(phone)
        assertEquals(1, native.directStarts)
        assertEquals(1, native.attempts)

        recovery.setForeground(false)
        runCurrent()
        native.backgroundRestart()
        assertEquals(2, native.attempts)
        assertEquals(1, native.backgroundStarts)
        recovery.check(phone)
        assertEquals(1, native.directStarts)

        recreateOwner()
        recovery.check(phone)
        assertEquals(3, native.attempts)
        assertEquals(2, native.directStarts)
        recovery.check(phone)
        assertEquals(RecoveryPhase.Exhausted, phase)
        recreateOwner()
        recovery.check(phone)
        assertEquals(RecoveryPhase.Exhausted, phase)
        assertEquals(3, native.directStarts + native.backgroundStarts)
        assertFalse(native.counterWriteRejected)
        assertTrue(acts.isEmpty())
    }

    private fun boundHealthy(budget: Map<String, Any?>) {
        markerV2()
        native.budget = budget
        native.running = true
        native.boundProfile = phone.raw
        native.enabled = true
    }

    @Test
    fun `B-LINUX-033 an open app with a healthy restored server starts the count again`() = start {
        // Three update or boot restores used the budget; each brought the server back.
        boundHealthy(budgetMap(attempts = 3, revision = 5))
        recovery.check(phone)
        assertEquals(RecoveryPhase.Ready, phase)
        assertEquals(1, native.presenceResets)
        assertEquals(0, native.attempts)
        assertEquals(0, native.directStarts)
    }

    @Test
    fun `B-LINUX-033 the reset happens once per visit`() = start {
        boundHealthy(budgetMap(attempts = 3, revision = 5))
        recovery.check(phone)
        assertEquals(1, native.presenceResets)
        native.budget = budgetMap(attempts = 3, revision = 9)
        recovery.check(phone)
        assertEquals(1, native.presenceResets)
        assertEquals(3, native.attempts)
        recovery.setForeground(false)
        recovery.setForeground(true)
        recovery.check(phone)
        assertEquals(2, native.presenceResets)
        assertEquals(0, native.attempts)
    }

    @Test
    fun `B-LINUX-033 a short visit after a restore is enough to confirm, then reset`() = start {
        boundHealthy(budgetMap(attempts = 3, revision = 5, pending = true, eventId = "native-restore:1", generation = 4))
        native.nativeOwned = true
        recovery.check(phone)
        assertTrue("native-restore:1" in acts)
        assertEquals(1, native.presenceResets)
        assertEquals(0, native.attempts)
    }

    @Test
    fun `B-LINUX-033 a refused presence leaves the count as it was`() = start {
        boundHealthy(budgetMap(attempts = 2, revision = 5))
        native.presenceRefused = true
        recovery.check(phone)
        assertEquals(RecoveryPhase.Ready, phase)
        assertEquals(2, native.attempts)
    }

    @Test
    fun `B-LINUX-033 only a confirmed manual Start resets native attempts`() = start {
        legacy(budgetMap(attempts = 3))
        recovery.check(phone)
        assertEquals(RecoveryPhase.Exhausted, phase)
        native.failStart = true
        assertFalse(manualStart())
        recovery.check(phone)
        assertEquals(3, native.attempts)
        assertEquals(0, native.manualResets)

        native.failStart = false
        assertTrue(manualStart())
        recovery.check(phone)
        assertEquals(1, native.manualResets)
        assertEquals(0, native.attempts)
        assertEquals(0, recovery.value.value.attempts)
        native.running = false
        recovery.check(phone)
        assertEquals(1, native.attempts)
        assertFalse(native.counterWriteRejected)
    }

    @Test
    fun `B-LINUX-033 a healthy manual Start resets the budget with automatic restart off`() = start {
        legacy(budgetMap(attempts = 2))
        native.failStart = true
        recovery.check(phone)
        recovery.check(phone)
        assertEquals(3, native.attempts)
        assertEquals(RecoveryPhase.Exhausted, phase)
        assertEquals(1, native.directStarts)

        restartAllowed = false
        native.failStart = false
        assertTrue(manualStart())
        recovery.check(phone)
        assertEquals(phone.raw, native.boundProfile)
        assertFalse(native.enabled)
        assertEquals(1, native.manualResets)
        assertEquals(0, native.attempts)
        assertEquals(1, native.directStarts)
        assertTrue(acts.isEmpty())

        native.running = false
        recovery.check(phone)
        assertEquals(0, native.attempts)
        assertEquals(1, native.directStarts)
        assertEquals(0, native.backgroundStarts)
    }

    @Test
    fun `B-LINUX-034 Stop, background and scheduled native recovery never dispatch from the app`() = start {
        native.wanted = false
        recovery.check(phone)
        assertEquals(RecoveryPhase.Stopped, phase)
        native.wanted = true
        recovery.setForeground(false)
        recovery.check(phone)
        assertEquals(RecoveryPhase.Paused, phase)
        recovery.setForeground(true)
        native.scheduled = true
        recovery.check(phone)
        assertEquals(RecoveryPhase.Waiting, phase)
        assertEquals(0, native.directStarts)
        assertEquals(0, native.attempts)
    }

    @Test
    fun `B-LINUX-034 a legacy pending confirmation survives migration and records after Stop`() = start {
        val confirmedAt = now - 1.minutes
        legacy(
            budgetMap(attempts = 2, pending = true, eventId = "legacy-confirmed", generation = 1, confirmedAt = confirmedAt.toEpochMilliseconds()),
        )
        recordsAllowed = false
        native.wanted = false
        recovery.check(phone)
        assertEquals(true, native.budget?.get("pending"))
        assertEquals(confirmedAt.toEpochMilliseconds(), (native.budget?.get("confirmedAt") as Number).toLong())
        assertEquals(2, native.attempts)
        recreateOwner()
        recordsAllowed = true
        recovery.check(phone)
        recovery.check(phone)
        assertEquals(listOf("legacy-confirmed"), acts.keys.toList())
        assertEquals(confirmedAt, acts["legacy-confirmed"])
        assertEquals(false, native.budget?.get("pending"))
        assertEquals(2, native.attempts)
        assertEquals(0, native.directStarts)
    }

    @Test
    fun `B-LINUX-034 a native background pending attempt is confirmed without another dispatch`() = start {
        native.running = true
        recovery.check(phone)
        recovery.setForeground(false)
        runCurrent()
        native.running = false
        native.backgroundRestart()
        assertEquals(true, native.budget?.get("pending"))
        assertTrue(acts.isEmpty())
        recreateOwner()
        recovery.check(phone)
        assertEquals(1, native.confirmations)
        assertEquals(0, native.directStarts)
        assertEquals(1, native.backgroundStarts)
        assertEquals(1, acts.size)
        assertEquals(false, native.budget?.get("pending"))
        assertEquals(RecoveryPhase.Ready, phase)
    }

    @Test
    fun `B-LINUX-034 a historical confirmed receipt survives a later crash and records once`() = start {
        recordsAllowed = false
        recovery.check(phone)
        val confirmedAt = now
        val firstEvent = native.budget!!["eventId"] as String
        assertEquals(confirmedAt.toEpochMilliseconds(), (native.budget!!["confirmedAt"] as Number).toLong())
        recovery.setForeground(false)
        runCurrent()
        native.running = false
        native.failStart = true
        native.backgroundRestart()
        assertEquals(firstEvent, native.receipts.single()["eventId"])
        recreateOwner()
        native.wanted = false
        recovery.check(phone)
        assertEquals(1, native.receipts.size)
        assertTrue(native.acknowledgements.isEmpty())
        recordsAllowed = true
        now += 1.hours
        recovery.check(phone)
        recreateOwner()
        recovery.check(phone)
        assertEquals(listOf(firstEvent), acts.keys.toList())
        assertEquals(confirmedAt, acts[firstEvent])
        assertEquals(listOf(firstEvent), native.acknowledgements)
        assertTrue(native.receipts.isEmpty())
        assertEquals(2, native.attempts)
    }

    @Test
    fun `B-LINUX-034 a healthy foreground check rebinds the saved profile after a private owner switch`() = start {
        boundHealthy(budgetMap(attempts = 1, revision = 2))
        native.presenceRefused = true
        recovery.check(phone)
        assertEquals(phone.raw, native.boundProfile)
        assertEquals(RecoveryPhase.Ready, phase)

        // An instrumentation fixture changes native ownership while the foreground still owns the saved profile.
        native.boundProfile = "qa_bb5_idle"
        native.calls.clear()
        val before = native.budget!!.toMap()
        recovery.check(phone)
        assertEquals(phone.raw, native.boundProfile)
        assertTrue("bind" in native.calls)
        assertEquals(RecoveryPhase.Ready, phase)
        assertEquals(before, native.budget)
        assertEquals(0, native.directStarts)
        assertEquals(0, native.manualResets)
    }

    @Test
    fun `B-LINUX-034 profile deletion drains the active check and native erase before the sweep`() = start {
        native.writeEntered = CompletableDeferred()
        native.writeGate = CompletableDeferred()
        native.deleteEntered = CompletableDeferred()
        native.deleteGate = CompletableDeferred()
        val checking = async { recovery.check(phone) }
        native.writeEntered!!.await()
        var deleted = false
        val deleting = async {
            recovery.suspendProfile(phone)
            deleted = true
        }
        runCurrent()
        native.writeGate!!.complete(Unit)
        checking.await()
        native.deleteEntered!!.await()
        assertFalse(deleted)
        assertEquals(0, native.directStarts)
        assertTrue(native.calls.indexOf("password-written") < native.calls.indexOf("delete-entered"))
        native.deleteGate!!.complete(Unit)
        deleting.await()
        profiles.clear()
        markers.remove(key)
        recovery.check(phone)
        assertNull(native.budget)
        assertEquals("deleted", native.calls.last())
        assertFalse(key in markers)
        assertEquals(0, native.directStarts)
    }
}
