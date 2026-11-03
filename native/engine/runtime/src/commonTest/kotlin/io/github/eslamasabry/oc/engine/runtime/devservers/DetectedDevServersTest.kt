package io.github.eslamasabry.oc.engine.runtime.devservers

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.runtime.services.FakeShells
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShell
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShellStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of test/detected_dev_servers_test.dart. */
class DetectedDevServersTest {
    private val row =
        "   0: 0100007F:1435 00000000:0000 0A 00000000:00000000 00:00000000 " +
            "00000000  1000        0 5001 1 0000000000000000 100 0 0 10 0"
    private val row2 =
        "   1: 00000000:1F90 00000000:0000 0A 00000000:00000000 00:00000000 " +
            "00000000  1000        0 5002 1 0000000000000000 100 0 0 10 0"

    private fun phoneOutput(log: String = "/root/p/dev.log"): String =
        listOf(
            "#T4",
            row,
            row2,
            "#S",
            "lrwx------ 1 root root 64 Oct 16 10:00 /proc/41/fd/3 -> socket:[5001]",
            "lrwx------ 1 root root 64 Oct 16 10:00 /proc/42/fd/3 -> socket:[5002]",
            "#C",
            "41\tnode vite --port 5173 \t/root/p\t$log",
            "42\tpython3 -m http.server 8080 \t/other\t/dev/pts/0",
            "#P",
            "",
        ).joinToString("\n")

    private fun shell(id: String, command: String, running: Boolean = true) =
        ManagedShell(
            id = id,
            command = command,
            status = if (running) ManagedShellStatus.Running else ManagedShellStatus.Exited,
            startedAtMillis = 1000,
            directory = "/root/p",
        )

    private val repo = FakeShells()
    private val scripts = mutableListOf<String>()
    private var exit = 0
    private var output = phoneOutput()
    private val clock = TestClock()

    private val phone = PhoneScriptRunner { script ->
        scripts += script
        PhoneScriptResult(exit, output).ok()
    }

    private fun make(phone: Boolean = true, owned: Set<String> = emptySet()) =
        DetectedDevServers(
            directory = "/root/p",
            resolveShells = { repo },
            isCurrent = { true },
            runOnPhone = if (phone) this.phone else null,
            ownedShellIds = { owned },
            clock = clock,
        )

    // ---- B-LINUX-054

    @Test
    fun `B-LINUX-054 a program listening on the phone is listed with port and logs`() = runTest {
        val detected = make()
        detected.refresh()
        assertTrue(detected.state.value.scanned)
        assertEquals(listOf(5173, 8080), detected.servers.map { it.firstPort })
        val vite = detected.servers.first()
        assertEquals("vite", vite.name)
        assertEquals(41, vite.pid)
        assertTrue(vite.hasLogs)
        assertTrue(vite.canStop)
        assertFalse(detected.servers.last().hasLogs)
        assertEquals(42, detected.forPort(8080)?.pid)
        assertEquals(listOf(5173, 8080), detected.allPorts)
        assertTrue(vite.loopbackOnly)
        assertFalse(detected.servers.last().loopbackOnly, "bound to every address")
    }

    @Test
    fun `B-LINUX-054 a server shell is listed with the ports its own output printed`() = runTest {
        repo.shells["sh_1"] = shell("sh_1", "npm run dev")
        val detected = make(phone = false)
        detected.refresh()
        val listed = detected.servers.single()
        assertEquals(DetectedSource.Shell, listed.source)
        assertEquals("sh_1", listed.shellId)
        assertEquals(listOf(5173), listed.ports, "from \"Local: http://localhost:5173/\"")
        assertTrue(listed.hasLogs)
        assertFalse(detected.canScanProcesses)
        assertTrue(scripts.isEmpty(), "no phone, no script")
    }

    @Test
    fun `B-LINUX-054 a program that is already a listed shell is not listed twice`() = runTest {
        repo.shells["sh_1"] = shell("sh_1", "npm run dev")
        val detected = make()
        detected.refresh()
        assertEquals(listOf(5173, 8080), detected.allPorts)
        assertEquals(1, detected.servers.count { it.source == DetectedSource.Shell })
        assertEquals(1, detected.servers.count { 5173 in it.ports })
    }

    @Test
    fun `B-LINUX-054 a shell a saved command owns is not listed but its ports are known`() = runTest {
        repo.shells["sh_1"] = shell("sh_1", "npm run dev")
        val detected = make(phone = false, owned = setOf("sh_1"))
        detected.refresh()
        assertTrue(detected.servers.isEmpty())
        assertEquals(listOf(5173), detected.portsForShell("sh_1"))
    }

    // ---- B-LINUX-055

    @Test
    fun `B-LINUX-055 servers in the project folder come first`() = runTest {
        output =
            phoneOutput()
                .replace("/other", "/root/p")
                .replace("\t/root/p\t/root/p/dev.log", "\t/elsewhere\t/dev/null")
        val detected = make()
        detected.refresh()
        assertEquals(8080, detected.servers.first().firstPort)
    }

    @Test
    fun `B-LINUX-055 a shell that ended is not listed`() = runTest {
        repo.shells["sh_1"] = shell("sh_1", "npm run dev", running = false)
        val detected = make(phone = false)
        detected.refresh()
        assertTrue(detected.servers.isEmpty())
    }

    // ---- B-LINUX-056

    @Test
    fun `B-LINUX-056 a failed phone script keeps shells and records the failure`() = runTest {
        repo.shells["sh_1"] = shell("sh_1", "npm run dev")
        val detected =
            DetectedDevServers(
                directory = "/root/p",
                resolveShells = { repo },
                isCurrent = { true },
                runOnPhone = { Failure.Device().failed() },
                clock = clock,
            )
        detected.refresh()
        assertEquals(1, detected.servers.size)
        assertNotNull(detected.state.value.error)
        assertTrue(detected.state.value.scanned)
    }

    @Test
    fun `B-LINUX-056 a look that fails while its own stop runs keeps the list and reports nothing`() = runTest {
        val stopStarted = CompletableDeferred<Unit>()
        val finishStop = CompletableDeferred<Unit>()
        var lookFails = false
        val detected =
            DetectedDevServers(
                directory = "/root/p",
                resolveShells = { repo },
                isCurrent = { true },
                runOnPhone = { script ->
                    if (script.contains("kill")) {
                        stopStarted.complete(Unit)
                        lookFails = true
                        finishStop.await()
                        lookFails = false
                        PhoneScriptResult(0, "").ok()
                    } else if (lookFails) {
                        Failure.Device().failed()
                    } else {
                        PhoneScriptResult(0, phoneOutput()).ok()
                    }
                },
                clock = clock,
            )
        detected.refresh()
        val vite = detected.servers.first { it.pid == 41 }
        val stopping = async { detected.stop(vite) }
        stopStarted.await()
        detected.refresh()
        assertNull(detected.state.value.error)
        assertEquals(2, detected.servers.size, "the last list stays")
        finishStop.complete(Unit)
        assertTrue(stopping.await() is Outcome.Ok)
        assertNull(detected.state.value.error)
    }

    @Test
    fun `B-LINUX-056 a stale answer after invalidate is dropped`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val detected =
            DetectedDevServers(
                directory = "/root/p",
                resolveShells = { repo },
                isCurrent = { true },
                runOnPhone = {
                    gate.await()
                    PhoneScriptResult(0, phoneOutput()).ok()
                },
                clock = clock,
            )
        val pending = async { detected.refresh() }
        runCurrent()
        detected.invalidate()
        gate.complete(Unit)
        pending.await()
        assertTrue(detected.servers.isEmpty())
        assertFalse(detected.state.value.scanned)
        assertFalse(detected.state.value.scanning)
    }

    // ---- B-LINUX-057

    @Test
    fun `B-LINUX-057 a shell's log is its output tail with colour codes removed`() = runTest {
        repo.output = "\u001B[32mready\u001B[0m\nLocal: http://localhost:5173/\n"
        repo.shells["sh_1"] = shell("sh_1", "npm run dev")
        val detected = make(phone = false)
        detected.refresh()
        val text = detected.readLog(detected.servers.single()).getOrNull()
        assertEquals("ready\nLocal: http://localhost:5173/\n", text)
    }

    @Test
    fun `B-LINUX-057 a program's log is read from its output file`() = runTest {
        val detected = make()
        detected.refresh()
        output = "line one\n\u001B[31mline two\u001B[0m\n"
        val text = detected.readLog(detected.servers.first()).getOrNull()
        assertEquals("line one\nline two\n", text)
        assertTrue(scripts.last().contains("tail -c 65536 -- '/root/p/dev.log'"), scripts.last())
    }

    @Test
    fun `B-LINUX-057 a program with no log file cannot be read`() = runTest {
        val detected = make()
        detected.refresh()
        val before = scripts.size
        val read = detected.readLog(detected.servers.last())
        assertTrue(read is Outcome.Failed)
        assertEquals(before, scripts.size, "nothing ran")
    }

    // ---- B-LINUX-058

    @Test
    fun `B-LINUX-058 stopping a shell asks the server and drops the row`() = runTest {
        repo.shells["sh_1"] = shell("sh_1", "npm run dev")
        val detected = make(phone = false)
        detected.refresh()
        assertTrue(detected.stop(detected.servers.single()) is Outcome.Ok)
        assertEquals(listOf("sh_1"), repo.stops)
        assertTrue(detected.servers.isEmpty())
    }

    @Test
    fun `B-LINUX-058 stopping a program runs the guarded kill for that pid only`() = runTest {
        val detected = make()
        detected.refresh()
        val vite = detected.servers.first()
        scripts.clear()
        output = ""
        detected.stop(vite)
        assertEquals(ListeningPorts.stopScript(41, vite.command), scripts.first())
        assertTrue(scripts.first().contains("kill -TERM 41"))
        assertFalse(scripts.first().contains("kill -TERM 42"))
        assertTrue(scripts.first().contains("case \"\$now\" in 'node vite --port 5173'*)"), scripts.first())
    }

    @Test
    fun `B-LINUX-058 a stop that did not work says so and keeps the row`() = runTest {
        val detected = make()
        detected.refresh()
        exit = 5
        val stop = detected.stop(detected.servers.first())
        assertTrue(stop is Outcome.Failed)
        assertTrue(detected.servers.isNotEmpty())
    }

    // ---- B-LINUX-059

    @Test
    fun `B-LINUX-059 scanning is offered only on the phone itself, where the in-app Linux exists`() {
        assertSame(phone, PhoneScan.runnerFor("http://127.0.0.1:4097", linuxSupported = true, linux = phone))
        assertSame(phone, PhoneScan.runnerFor("http://localhost:4097", linuxSupported = true, linux = phone))
        assertSame(phone, PhoneScan.runnerFor("http://[::1]:4097", linuxSupported = true, linux = phone))
        assertNull(PhoneScan.runnerFor("http://dev-box.lan:4097", linuxSupported = true, linux = phone))
        assertNull(PhoneScan.runnerFor("http://127.0.0.1:4097", linuxSupported = false, linux = phone))
        assertNull(PhoneScan.runnerFor(null, linuxSupported = true, linux = phone))
        assertNull(
            PhoneScan.runnerFor("http://127.0.0.1:4097", linuxSupported = true, linux = phone, transientTransport = true),
        )
        assertNull(PhoneScan.runnerFor("http://user@remote.example:4097", linuxSupported = true, linux = phone))
        assertFalse(make(phone = false).canScanProcesses, "without a runner nothing scans")
    }
}
