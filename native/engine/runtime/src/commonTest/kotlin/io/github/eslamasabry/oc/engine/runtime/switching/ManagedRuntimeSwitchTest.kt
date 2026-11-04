package io.github.eslamasabry.oc.engine.runtime.switching

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/managed_runtime_switch_preflight_test.dart (B-LINUX-104, -105, -106). */
class ManagedRuntimeSwitchTest {
    private enum class Stream { Connected, Disconnected }

    private data class Recovery(val enabled: Boolean = true, val manuallySuspended: Boolean = false)

    /** The connection controller, the profile store, the saved queue and the recovery records, in memory. */
    private class World : SwitchConnection, RecoverySuspension, ProfileWriter {
        val profiles = mutableListOf<LocalProfile>()
        val secrets = mutableMapOf<String, String>()
        var activeId: String? = null
        var connected: LocalProfile? = null
        var status = Stream.Connected
        var work = LocalWork()
        val monitor = mutableMapOf<String, MonitoredWork>()

        /** `oc.offlineQueue` exactly as saved; never rewritten by the switch. */
        var queueRaw: String? = null
        val queue = mutableListOf<QueuedPromptRef>()
        val recovery = mutableMapOf<String, Recovery>()
        var refuseRecovery = false
        var recoveryGate: CompletableDeferred<Unit>? = null
        val recoveryEntered = CompletableDeferred<Unit>()
        var refuseActiveClear = false
        var transportClosed = false
        var upserts = 0

        fun queueFor(id: String) {
            queue += QueuedPromptRef("queued-$id", id)
            queueRaw = queue.joinToString(prefix = "[", postfix = "]") { "{\"id\":\"${it.id}\",\"profileID\":\"${it.profileId}\"}" }
        }

        override fun profiles(): List<LocalProfile> = profiles.toList()

        override fun currentProfile(): LocalProfile? = connected ?: profiles.firstOrNull { it.id == activeId }

        override fun localWork(): LocalWork = work

        override fun monitored(profileId: String): MonitoredWork? = monitor[profileId]

        override fun savedQueue(): List<QueuedPromptRef> = if (queueRaw?.startsWith("[") == true) queue.toList() else emptyList()

        override suspend fun clearActiveProfile(): Outcome<Unit> {
            if (refuseActiveClear) return Failure.StorageRefused().failed()
            activeId = null
            return Unit.ok()
        }

        override suspend fun disconnectKeepingActive() {
            transportClosed = true
            connected = null
            status = Stream.Disconnected
        }

        override suspend fun suspendForProfile(profileId: String): Outcome<Unit> {
            val record = recovery[profileId] ?: return Unit.ok()
            if (!recoveryEntered.isCompleted) recoveryEntered.complete(Unit)
            recoveryGate?.await()
            if (refuseRecovery) return Failure.StorageRefused().failed()
            recovery[profileId] = record.copy(manuallySuspended = true)
            return Unit.ok()
        }

        override suspend fun upsert(profile: LocalProfile): Outcome<Unit> {
            upserts++
            profiles.replaceAll { if (it.id == profile.id) profile else it }
            return Unit.ok()
        }
    }

    private val world = World()
    private var local = LocalProfile("local", "This phone", "http://127.0.0.1:4096")
    private val remote = LocalProfile("remote", "Computer", "https://server.example")
    private val switch = ManagedRuntimeSwitch(world, world)

    init {
        world.profiles += local
        world.profiles += remote
        world.secrets["pw.local"] = "fixture-local-password"
        world.activeId = local.id
        world.connected = local
    }

    private fun replaceLocal(profile: LocalProfile) {
        world.profiles.replaceAll { if (it.id == local.id) profile else it }
        world.connected = profile
        local = profile
    }

    private fun recoveryOn(id: String) {
        world.recovery[id] = Recovery()
    }

    @Test
    fun `B-LINUX-104 idle local clears the selection and keeps the profile and credentials`() = runTest {
        recoveryOn(local.id)
        val before = local
        assertEquals(SwitchResult.Ready(disconnected = true), switch.prepare())
        assertEquals(Stream.Disconnected, world.status)
        assertNull(world.activeId)
        assertEquals(before, world.profiles.first { it.id == local.id })
        assertEquals("fixture-local-password", world.secrets["pw.local"])
        assertEquals(listOf(local, remote), world.profiles)
        assertTrue(world.recovery.getValue(local.id).manuallySuspended)
        assertTrue(world.recovery.getValue(local.id).enabled, "the person's automation policy stays on")
    }

    @Test
    fun `B-LINUX-104 every local alias is suspended while remote work stays connected`() = runTest {
        val alias = LocalProfile("local-v2", "This phone beta", "http://127.0.0.1:4096")
        world.profiles += alias
        recoveryOn(local.id)
        recoveryOn(alias.id)
        recoveryOn(remote.id)
        world.activeId = remote.id
        world.connected = remote
        world.work = LocalWork(busySessions = 1)
        world.queueFor(remote.id)
        val queue = world.queueRaw

        assertEquals(SwitchResult.Ready(disconnected = false), switch.prepare())

        assertEquals(Stream.Connected, world.status)
        assertEquals(remote.id, world.activeId)
        assertEquals(1, world.work.busySessions, "remote busy work is untouched")
        assertEquals(queue, world.queueRaw)
        assertEquals(1, world.savedQueue().count { it.profileId == remote.id })
        assertFalse(world.transportClosed)
        assertTrue(world.recovery.getValue(local.id).manuallySuspended)
        assertTrue(world.recovery.getValue(alias.id).manuallySuspended)
        assertFalse(world.recovery.getValue(remote.id).manuallySuspended)
        assertTrue(world.recovery.getValue(remote.id).enabled)
    }

    @Test
    fun `B-LINUX-104 an unmanaged endpoint is not disconnected`() = runTest {
        for (url in listOf("http://127.0.0.1:4999", "http://localhost:4096")) {
            replaceLocal(local.copy(baseUrl = url))
            world.work = LocalWork(busySessions = 1)
            assertEquals(SwitchResult.Ready(disconnected = false), switch.prepare(), url)
            assertEquals(Stream.Connected, world.status)
            assertEquals(1, world.work.busySessions)
            assertFalse(world.transportClosed)
        }
    }

    @Test
    fun `B-LINUX-104 another backend at the local URL is not treated as managed OpenCode`() = runTest {
        replaceLocal(local.copy(backend = ServerBackendKind.Codex))
        world.work = LocalWork(busySessions = 1)
        recoveryOn(local.id)
        assertEquals(SwitchResult.Ready(disconnected = false), switch.prepare())
        assertEquals(Stream.Connected, world.status)
        assertTrue(world.recovery.getValue(local.id).enabled)
        assertFalse(world.recovery.getValue(local.id).manuallySuspended)
    }

    @Test
    fun `B-LINUX-104 managed addresses are exactly the two loopback ports over http`() {
        assertTrue(ManagedLocalServer.isTermux("http://127.0.0.1:4096"))
        assertTrue(ManagedLocalServer.isBuiltin("http://127.0.0.1:4097/"))
        assertFalse(ManagedLocalServer.isTermux("https://127.0.0.1:4096"))
        assertFalse(ManagedLocalServer.isTermux("http://localhost:4096"))
        assertFalse(ManagedLocalServer.isBuiltin("http://127.0.0.1:4999"))
        assertFalse(ManagedLocalServer.isBuiltin("http://127.0.0.1"))
        assertFalse(ManagedLocalServer.isBuiltin(null))
    }

    @Test
    fun `B-LINUX-105 the in-app managed server also blocks a move while work is running`() = runTest {
        replaceLocal(local.copy(baseUrl = "http://127.0.0.1:4097"))
        world.work = LocalWork(busySessions = 1)
        assertEquals(SwitchResult.Blocked(SwitchBlock.LocalWorkRunning), switch.prepare())
        assertEquals(Stream.Connected, world.status)
        assertEquals(1, world.work.busySessions)
        assertFalse(world.transportClosed)
    }

    @Test
    fun `B-LINUX-105 known local running work blocks before recovery or disconnect`() = runTest {
        recoveryOn(local.id)
        world.work = LocalWork(busySessions = 1)
        assertEquals(SwitchResult.Blocked(SwitchBlock.LocalWorkRunning), switch.prepare())
        assertEquals(Stream.Connected, world.status)
        assertTrue(world.recovery.getValue(local.id).enabled)
        assertFalse(world.recovery.getValue(local.id).manuallySuspended)
        assertEquals(local.id, world.activeId)
    }

    @Test
    fun `B-LINUX-105 a pending permission or an admitted inbox send blocks without busy status`() = runTest {
        for (work in listOf(LocalWork(permissions = 1), LocalWork(inboxItems = 1), LocalWork(questions = 1), LocalWork(pendingReplies = 1))) {
            world.work = work
            assertEquals(SwitchResult.Blocked(SwitchBlock.LocalWorkRunning), switch.prepare(), work.toString())
            assertEquals(Stream.Connected, world.status)
            assertFalse(world.transportClosed)
        }
    }

    @Test
    fun `B-LINUX-105 background monitor running work on the phone blocks with remote active`() = runTest {
        world.activeId = remote.id
        world.connected = remote
        world.monitor[local.id] = MonitoredWork(isCurrent = true, runningCount = 1, pendingCount = 0)
        assertEquals(SwitchResult.Blocked(SwitchBlock.MonitoredWorkRunning), switch.prepare())
        assertEquals(Stream.Connected, world.status)
        assertEquals(remote.id, world.activeId)
        world.monitor[local.id] = MonitoredWork(isCurrent = false, runningCount = 1, pendingCount = 0)
        assertEquals(SwitchResult.Ready(disconnected = false), switch.prepare(), "a stale snapshot is not evidence")
    }

    @Test
    fun `B-LINUX-105 new work arriving during recovery persistence blocks the final disconnect`() = runTest {
        recoveryOn(local.id)
        world.recoveryGate = CompletableDeferred()
        val switching = async { switch.prepare() }
        world.recoveryEntered.await()
        world.work = LocalWork(busySessions = 1)
        world.recoveryGate!!.complete(Unit)
        assertEquals(SwitchResult.Blocked(SwitchBlock.LocalWorkRunning), switching.await())
        assertEquals(Stream.Connected, world.status)
        assertEquals(local.id, world.activeId)
        assertEquals(1, world.work.busySessions)
    }

    @Test
    fun `B-LINUX-105 an in-flight local send blocks without closing its transport`() = runTest {
        world.queueFor(local.id)
        world.work = LocalWork(flushingOfflineQueue = true, queuedPromptInFlight = "queued-local")
        assertEquals(SwitchResult.Blocked(SwitchBlock.LocalWorkRunning), switch.prepare())
        assertEquals(Stream.Connected, world.status)
        assertEquals(local.id, world.activeId)
        assertFalse(world.transportClosed)
        // The send is accepted and leaves the queue: now the switch goes ahead.
        world.queue.clear()
        world.queueRaw = "[]"
        world.work = LocalWork()
        assertEquals(SwitchResult.Ready(disconnected = true), switch.prepare())
        assertEquals(Stream.Disconnected, world.status)
        assertNull(world.activeId)
    }

    @Test
    fun `B-LINUX-105 a phone prompt on the wire blocks even while a remote profile is in use`() = runTest {
        world.queueFor(local.id)
        world.activeId = remote.id
        world.connected = remote
        world.work = LocalWork(queuedPromptInFlight = "queued-local")
        assertEquals(SwitchResult.Blocked(SwitchBlock.SendInFlight), switch.prepare())
        assertFalse(world.transportClosed)
    }

    @Test
    fun `B-LINUX-106 the saved local queue is retained during a switch, dispatched or not`() = runTest {
        world.queueFor(local.id)
        val before = world.queueRaw
        assertEquals(SwitchResult.Ready(disconnected = true), switch.prepare())
        assertEquals(before, world.queueRaw)
        assertEquals(Stream.Disconnected, world.status)
        assertNull(world.activeId)
    }

    @Test
    fun `B-LINUX-106 an unreadable saved queue is left untouched during a switch`() = runTest {
        world.queueRaw = "{broken"
        assertEquals(SwitchResult.Ready(disconnected = true), switch.prepare())
        assertEquals("{broken", world.queueRaw)
        assertEquals(Stream.Disconnected, world.status)
    }

    @Test
    fun `B-LINUX-106 a recovery persistence failure is actionable and never disconnects`() = runTest {
        recoveryOn(local.id)
        world.refuseRecovery = true
        assertEquals(SwitchResult.Blocked(SwitchBlock.RecoveryNotSuspended), switch.prepare())
        assertEquals(Stream.Connected, world.status)
        assertEquals(local.id, world.activeId)
        assertFalse(world.transportClosed)
    }

    @Test
    fun `B-LINUX-106 a selection persistence failure prevents the switch from completing`() = runTest {
        world.refuseActiveClear = true
        assertEquals(SwitchResult.Blocked(SwitchBlock.SelectionNotCleared), switch.prepare())
        assertEquals(Stream.Connected, world.status)
        assertTrue(local in world.profiles)
        assertFalse(world.transportClosed)
    }

    private class Transport(private val healthy: Boolean) : ConnectTransport {
        var closed = false

        override suspend fun healthy(): Boolean = healthy

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `B-LINUX-106 a managed legacy reply requires the safe move and is never retried`() = runTest {
        var builds = 0
        var probes = 0
        val gate = ManagedConnectGate(world)
        val verdict =
            gate.connect(
                local,
                buildTransport = { builds++; Transport(healthy = false) },
                probe = { probes++; ProbeVerdict(ok = false, unsupportedLegacy = true) },
            )
        assertEquals(1, probes)
        assertEquals(1, builds, "never retry as a legacy runtime")
        val required = assertIs<ConnectVerdict.MigrationRequired>(verdict)
        assertTrue(required.profile.requiresOpenCode2Migration)
        assertTrue(required.markSaved)
        assertTrue(gate.migrationRequired(required.profile))
        assertEquals("fixture-local-password", world.secrets["pw.local"])
        assertTrue(world.profiles.first { it.id == local.id }.requiresOpenCode2Migration, "the mark is persisted")
        // A remote legacy reply is not a managed move.
        assertEquals(ConnectVerdict.Failed, gate.connect(remote, { Transport(false) }, { ProbeVerdict(false, unsupportedLegacy = true) }))
        assertFalse(world.profiles.first { it.id == remote.id }.requiresOpenCode2Migration)
    }

    @Test
    fun `B-LINUX-106 persisted legacy metadata blocks transport creation before moving`() = runTest {
        val marked = local.copy(requiresOpenCode2Migration = true)
        world.upsert(marked)
        val gate = ManagedConnectGate(world)
        var builds = 0
        val verdict = gate.connect(marked, buildTransport = { builds++; Transport(true) }, probe = { ProbeVerdict(true) })
        assertIs<ConnectVerdict.MigrationRequired>(verdict)
        assertEquals(0, builds, "a legacy profile must not construct a transport")
        assertEquals("fixture-local-password", world.secrets["pw.local"])
        // The same profile on a remote address only needs its marker retired by its owner, not a phone move.
        assertFalse(gate.migrationRequired(remote.copy(requiresOpenCode2Migration = true)))
    }
}
