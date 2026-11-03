package io.github.eslamasabry.oc.feature.linux.manager

import io.github.eslamasabry.oc.feature.linux.manager.FakeLinuxHost.Companion.busySlot
import io.github.eslamasabry.oc.feature.linux.manager.FakeLinuxHost.Companion.notInstalled
import io.github.eslamasabry.oc.feature.linux.manager.FakeLinuxHost.Companion.out
import io.github.eslamasabry.oc.feature.linux.manager.FakeLinuxHost.Companion.ownership
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.ShellRun
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ports of test/linux_manager_retry_test.dart plus the manager's quiet rule and the long job. */
class LinuxManagerTest {
    @Test
    fun `B-LINUX-092 a passing ownership failure is retried and the list comes`() = runTest {
        val host = FakeLinuxHost(answer = { out("::open 5173\n") })
        host.failures += listOf(ownership, busySlot)
        val ports = LinuxManager(host, VirtualRetryWait).scanPorts()
        assertEquals(listOf(5173), assertIs<LinuxAnswer.Ok<List<LinuxPort>>>(ports).value.map { it.port })
        // Three tries of the process read (two passing failures, then it comes), then the probe.
        assertEquals(List(3) { LinuxPortScan.PROCESS_SCRIPT }, host.scripts.take(3))
        assertEquals(4, host.scripts.size)
    }

    @Test
    fun `B-LINUX-092 one that stays is busy and not not set up`() = runTest {
        val host = FakeLinuxHost()
        host.failures += listOf(ownership, ownership, ownership, ownership)
        val answer = LinuxManager(host, VirtualRetryWait).scanPorts()
        assertEquals(LinuxProblem.Busy, assertIs<LinuxAnswer.Problem>(answer).problem)
        assertEquals(3, host.scripts.size)
    }

    @Test
    fun `B-LINUX-092 Linux that is not there is said at once without retries`() = runTest {
        val host = FakeLinuxHost()
        host.failures += notInstalled
        val answer = LinuxManager(host, VirtualRetryWait).measureStorage()
        assertEquals(LinuxProblem.Unavailable, assertIs<LinuxAnswer.Problem>(answer).problem)
        assertEquals(1, host.scripts.size)
    }

    @Test
    fun `B-LINUX-092 a script that exits non-zero is a failed act`() = runTest {
        val host = FakeLinuxHost(answer = { Outcome.Ok(ShellRun(2, "boom")) })
        assertEquals(LinuxProblem.Failed, assertIs<LinuxAnswer.Problem>(LinuxManager(host, VirtualRetryWait).installedPackages()).problem)
    }

    @Test
    fun `B-LINUX-089 cleanup waits for setup and reports the bytes freed from two readings`() = runTest {
        var readings = 0
        val host =
            FakeLinuxHost(
                answer = { script ->
                    if (script.contains("oc_du") || script.contains("::du")) {
                        readings++
                        out(if (readings == 1) "::du 300 /var/cache/apt\n" else "::du 100 /var/cache/apt\n")
                    } else {
                        out("")
                    }
                },
            )
        host.setup = true
        val manager = LinuxManager(host, VirtualRetryWait)
        assertEquals(LinuxProblem.SetupRunning, assertIs<LinuxAnswer.Problem>(manager.cleanup(LinuxStorage.aptCache)).problem)
        assertTrue(host.scripts.isEmpty(), "nothing ran while setup ran")
        host.setup = false
        assertEquals(200L * 1024, assertIs<LinuxAnswer.Ok<Long>>(manager.cleanup(LinuxStorage.aptCache)).value)
        assertEquals(3, host.scripts.size)
    }

    @Test
    fun `B-LINUX-091 one long job at a time and its log says how it stands`() = runTest {
        val host = FakeLinuxHost()
        val manager = LinuxManager(host, VirtualRetryWait)
        assertIs<LinuxAnswer.Ok<Unit>>(manager.startJob(LinuxJobKind.Install, listOf("htop"), notice = "Installing Linux packages"))
        assertEquals(listOf(LinuxManager.JOB_SERVICE to "Installing Linux packages"), host.started)
        assertEquals("exit 0", host.scripts.single(), "the slot is taken once before the job starts")
        assertEquals(LinuxProblem.JobRunning, assertIs<LinuxAnswer.Problem>(manager.startJob(LinuxJobKind.Update, emptyList(), "x")).problem)
        host.logs[LinuxManager.JOB_SERVICE] = "::oc job install\n::oc names htop\n::oc percent 10\n"
        assertEquals(LinuxJobStatus.Running, manager.readJob().status)
        manager.stopJob()
        host.logs[LinuxManager.JOB_SERVICE] = "::oc job install\n::oc names htop\n::oc job-exit 0\n"
        assertEquals(LinuxJobStatus.Done, manager.readJob().status)
    }

    @Test
    fun `B-LINUX-091 a job that ends between the two reads is never read as interrupted`() = runTest {
        // The job writes its exit marker and stops right after its log is read (Flutter reads status first).
        val before = "::oc job install\n::oc names htop\n::oc percent 90\n"
        val host =
            object : LinuxHost by FakeLinuxHost() {
                var running = true
                var log = before

                override suspend fun serviceRunning(name: String): Boolean = running

                override suspend fun serviceLog(name: String, tailBytes: Int): String =
                    log.also {
                        log = before + "::oc job-exit 0\n"
                        running = false
                    }
            }
        val manager = LinuxManager(host, VirtualRetryWait)
        val first = manager.readJob().status
        assertTrue(first != LinuxJobStatus.Interrupted, "read as $first")
        assertEquals(LinuxJobStatus.Done, manager.readJob().status)
    }

    @Test
    fun `B-LINUX-085 a port only a launcher's command names is probed and listed with that launcher`() = runTest {
        // `sh start-server.sh 12345` starts a `node server.js` child whose own command, environment and logs name no port.
        val host =
            FakeLinuxHost(
                answer = { script ->
                    when {
                        script.startsWith(LinuxPortScan.PROCESS_MARK) ->
                            out("::c 300 sh start-server.sh 12345\n::c 301 node server.js\n")
                        script.startsWith("# oc-linux-ports") ->
                            out(if (Regex("""(^| )12345( |$)""").containsMatchIn(script.lineSequence().first { "/dev/tcp" in it })) "::open 12345\n" else "")
                        else -> out("")
                    }
                },
            )
        val ports = assertIs<LinuxAnswer.Ok<List<LinuxPort>>>(LinuxManager(host, VirtualRetryWait).scanPorts()).value
        val port = ports.single()
        assertEquals(12345, port.port)
        assertEquals(PortOwner(300, "sh start-server.sh 12345"), port.owner)
        assertTrue(port.canPreview)
    }
}
