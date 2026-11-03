package io.github.eslamasabry.oc.engine.runtime.services

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/development_services_failed_report_test.dart (B-LINUX-065). */
@OptIn(ExperimentalCoroutinesApi::class)
class DevelopmentServicesFailedReportTest {
    private val sample =
        DevelopmentService(
            id = "vite",
            name = "Shopfront preview",
            command = "npm run dev",
            directory = "/work/shopfront",
            url = "http://devbox.test:5173",
        )

    /** The server side (Dart `ServiceRepository`). */
    private class Gateway : ManagedShellGateway {
        val shells = mutableMapOf<String, ManagedShell>()
        val stops = mutableListOf<String>()
        var starts = 0
        var output = "VITE ready in 410 ms\nLocal: http://localhost:5173/\n"
        var infoGate: CompletableDeferred<Unit>? = null
        var failReads = false

        override suspend fun startManagedShell(command: String, directory: String, ownerToken: String): Outcome<ManagedShell> {
            starts++
            val shell = ManagedShell("sh_$starts", command, ManagedShellStatus.Running, starts * 1000L, directory = directory, ownerToken = ownerToken)
            shells[shell.id] = shell
            return shell.ok()
        }

        override suspend fun loadRunningShells(): Outcome<List<ManagedShell>> = shells.values.filter { it.running }.ok()

        override suspend fun getManagedShell(id: String): Outcome<ManagedShell?> {
            infoGate?.await()
            if (failReads) return Failure.Server(503).failed()
            return shells[id].ok()
        }

        override suspend fun readManagedShellOutput(id: String, cursor: Long, limit: Int): Outcome<ManagedShellOutput> {
            val bytes = output.encodeToByteArray()
            val end = min(bytes.size.toLong(), cursor + limit)
            return ManagedShellOutput(bytes.copyOfRange(cursor.toInt(), end.toInt()).decodeToString(), end, bytes.size.toLong(), cursor > 0).ok()
        }

        override suspend fun stopManagedShell(id: String): Outcome<Unit> {
            stops += id
            shells.remove(id)
            return Unit.ok()
        }
    }

    /** `oc.developmentServices.p84` in memory; [writes] counts every persisted change. */
    private class Store : DevelopmentServiceStore {
        override val key = DevelopmentServiceStore.keyFor("p84")
        var saved: List<DevelopmentService> = emptyList()
        var writes = 0

        override fun load(): Outcome<List<DevelopmentService>> = saved.ok()

        override suspend fun save(service: DevelopmentService): Outcome<Unit> = change { items ->
            val index = items.indexOfFirst { it.id == service.id }
            if (index < 0) items += service else items[index] = service
            true
        }

        override suspend fun remove(id: String): Outcome<Unit> = change { items -> items.removeAll { it.id == id }; true }

        override suspend fun updateRun(id: String, run: DevelopmentServiceRun?, expectedOwner: String?): Outcome<Unit> = change { items ->
            val index = items.indexOfFirst { it.id == id }
            if (index < 0 || (expectedOwner != null && items[index].run?.ownerToken != expectedOwner)) {
                false
            } else {
                items[index] = items[index].withRun(run)
                true
            }
        }

        private fun change(block: (MutableList<DevelopmentService>) -> Boolean): Outcome<Unit> {
            val items = saved.toMutableList()
            if (!block(items)) return Failure.StorageRefused().failed()
            saved = items.toList()
            writes++
            return Unit.ok()
        }
    }

    private val gateway = Gateway()
    private val store = Store()
    private var current = true
    private var supported = true
    private val mask = "•••"
    private val model =
        DevelopmentServices(
            store = store,
            directory = sample.directory,
            workspace = null,
            resolveGateway = { gateway },
            isCurrent = { current },
            supported = { supported },
            redact = { it.replace(Regex("(password=)\\S+"), "$1$mask") },
        )

    private suspend fun setUp() {
        model.register(sample)
        model.start(sample.id)
    }

    private fun failShell(owner: String? = null) {
        val previous = gateway.shells.getValue("sh_1")
        gateway.shells["sh_1"] = previous.copy(ownerToken = owner ?: previous.ownerToken, status = ManagedShellStatus.Exited, exitCode = 2)
    }

    @Test
    fun `B-LINUX-065 a failed service exposes its owned log excerpt after a read without persisting it`() = runTest {
        setUp()
        assertNull(model.failedReport("vite"), "a running service is not a failure")
        failShell()
        model.refresh()
        assertFalse(model.failedReport("vite")!!.hasLog)
        val stored = store.saved
        val writes = store.writes
        gateway.output = "build failed\npassword=synthetic-password"
        model.readLogs("vite")
        val report = model.failedReport("vite")!!
        assertEquals("build failed\npassword=$mask", report.logExcerpt)
        assertEquals("exited (exit 2)", report.detail)
        assertEquals(ManagedShellStatus.Exited, report.status)
        assertEquals(2, report.exitCode)
        assertEquals(stored, store.saved, "the log is never persisted")
        assertEquals(writes, store.writes)
        assertEquals(1, gateway.starts)
        assertTrue(gateway.stops.isEmpty())
        assertNull(model.failedReport("missing"))
        model.start("vite")
        assertNull(model.failedReport("vite"), "a new run replaces the failure")
        assertTrue(report.logExcerpt.contains("build failed"), "the taken snapshot is immutable")
    }

    @Test
    fun `B-LINUX-065 a scope change and lost ownership cannot attach a cached log`() = runTest {
        setUp()
        failShell()
        model.readLogs("vite")
        assertTrue(model.failedReport("vite")!!.hasLog)
        current = false
        assertNull(model.failedReport("vite"))
        current = true
        supported = false
        assertNull(model.failedReport("vite"))
        supported = true
        failShell(owner = "different-owner")
        model.readLogs("vite")
        assertNull(model.failedReport("vite"))
        assertFalse(model.logs.containsKey("vite"))
        assertEquals(ServiceError.Refused(ServiceRefusal.LogNotConfirmed), model.error)
    }

    @Test
    fun `B-LINUX-065 invalidation discards a pending read and its report`() = runTest {
        setUp()
        failShell()
        model.refresh()
        gateway.infoGate = CompletableDeferred()
        val pending = launch { model.readLogs("vite") }
        runCurrent()
        model.invalidateRuntime()
        gateway.infoGate!!.complete(Unit)
        pending.join()
        assertNull(model.failedReport("vite"))
        assertTrue(model.logs.isEmpty())
        assertFalse(model.busy)
    }

    @Test
    fun `B-LINUX-065 a failed log refresh cannot reuse a previous excerpt`() = runTest {
        setUp()
        failShell()
        model.readLogs("vite")
        assertTrue(model.logs.containsKey("vite"))
        gateway.failReads = true
        model.readLogs("vite")
        assertFalse(model.logs.containsKey("vite"))
        assertNull(model.failedReport("vite"))
    }

    @Test
    fun `B-LINUX-065 a replaced receipt cannot reuse the prior run log after refresh`() = runTest {
        setUp()
        failShell()
        gateway.output = "old run failure"
        model.readLogs("vite")
        val replacement =
            ManagedShell("sh_new", sample.command, ManagedShellStatus.Exited, 2000L, exitCode = 3, directory = sample.directory, ownerToken = "replacement-owner")
        gateway.shells[replacement.id] = replacement
        model.store.updateRun("vite", DevelopmentServiceRun("replacement-owner", "sh_new", 2000L))
        model.refresh()
        val report = assertNotNull(model.failedReport("vite"))
        assertEquals("sh_new", report.jobId)
        assertFalse(report.hasLog)
        gateway.output = "new run failure"
        model.readLogs("vite")
        assertEquals("new run failure", model.failedReport("vite")!!.logExcerpt)
    }

    @Test
    fun `B-LINUX-065 terminal codes are removed from the read log`() = runTest {
        setUp()
        failShell()
        gateway.output = "\u001B[31mred\u001B[0m\r\nnext\rline"
        model.readLogs("vite")
        assertEquals("red\nnext\nline", model.logs["vite"])
    }
}
