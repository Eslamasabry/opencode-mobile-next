package io.github.eslamasabry.oc.engine.linuxtools

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Port of the tools.sh halves of test/termux_processes_test.dart: procs-scan groups a fixture `ps` listing (stub on
 * PATH) the way the owner's phone looked, and procs-stop is exercised against real `sleep` processes.
 */
class TermuxProcessesScriptTest {
    private val phonePs =
        """
  100     1  0.0 00:00:00 2000  5000 bash /data/data/com.termux/files/usr/bin/bash /data/data/com.termux/files/home/.oc/manager.sh setup 4096
  101   100  0.2 00:01:00 90000 4000 opencode opencode serve --hostname 127.0.0.1 --port 4096
  102   101  0.0 00:00:03 30000 3900 node node /root/.npm/_npx/abc/node_modules/.bin/some-mcp
  200     1 99.0 01:02:03 45000 3700 node node /root/.npm/_npx/xyz/node_modules/minimax-coding-plan-mcp/dist/index.js
  300     1  1.0 02:00:00 300000 7200 java java -Xmx2g org.gradle.launcher.daemon.bootstrap.GradleDaemon 8.5
  400     1  0.0 00:00:10 20000 1000 gc /data/data/com.termux/files/usr/bin/gc start
  401   400  0.0 00:00:05 40000 900 dolt dolt sql-server
  402   400  0.5 00:00:07 50000 800 opencode opencode acp
  403   402  0.0 00:00:01 10000 700 node node /x/mcp-helper
  500   700  0.0 00:10:00 1000 5000 python3 python3 /root/stray.py
  600     1  0.0 00:00:00 3000 100 sshd sshd: /usr/sbin/sshd
  700     1  0.0 00:00:00 3000 100 bash bash
  800   700  3.0 00:00:30 5000 40 node node build/watch.js
""".removePrefix("\n")

    private inner class Phone {
        val root: File = tempDir("oc-tools-procs-")
        val home = File(root, "home").apply { mkdirs() }
        val bin = File(root, "bin").apply { mkdirs() }
        val tools = File(root, "tools.sh").apply { writeText(TERMUX_TOOLS_SCRIPT) }
        val psFixture = File(root, "ps.txt").apply { writeText(phonePs) }

        init {
            writeExecutable(File(bin, "ps"), "#!/bin/bash\ncat \"\$OC_PS_FIXTURE\"\n")
        }

        fun processes(lines: String) = psFixture.writeText(lines)

        fun run(vararg args: String): Ran =
            requireHermetic(root, home.path, "${root.path}/prefix", tools.path, psFixture.path).let { runProcess(
                listOf("bash", tools.path) + args,
                mapOf(
                    "HOME" to home.path,
                    "PREFIX" to "${root.path}/prefix",
                    "PATH" to "${bin.path}:${System.getenv("PATH")}",
                    "OC_PS_FIXTURE" to psFixture.path,
                    // The grace before KILL is 5 s on the phone (asserted below); the tests shorten it.
                    "OC_STOP_WAIT_SECONDS" to "1",
                ),
            ) }

        fun scan(): TermuxProcessReport {
            val result = run("procs-scan")
            assertEquals(0, result.exitCode, "${result.stdout}\n${result.stderr}")
            return TermuxProcessReport.parse(result.stdout)!!
        }

        fun stop(target: String): TermuxProcessStopResult {
            val result = run("procs-stop", target)
            assertEquals(0, result.exitCode, "${result.stdout}\n${result.stderr}")
            return TermuxProcessStopResult.parse(result.stdout)!!
        }
    }

    private val phones = mutableListOf<Phone>()
    private val started = mutableListOf<Process>()

    private fun phone() = Phone().also { phones += it }

    private fun start(vararg command: String): Process = ProcessBuilder(*command).start().also { started += it }

    @AfterTest
    fun cleanUp() {
        for (process in started) {
            runProcess(listOf("pkill", "-KILL", "-P", "${process.pid()}"))
            process.destroyForcibly()
        }
        phones.forEach { it.root.deleteTree() }
    }

    private fun byPid(report: TermuxProcessReport) = report.processes.associateBy { it.pid }

    private fun exitedNonZero(process: Process): Boolean = process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() != 0

    @Test
    fun `B-LINUX-239 procs-scan groups by ancestry and flags orphans with a reason`() {
        val report = phone().scan()
        val p = byPid(report)
        assertTrue(p.keys.containsAll(listOf(100, 101, 102, 200, 300, 400, 401, 402, 403, 500, 600, 700, 800)))
        assertFalse(1 in p.keys)
        // OpenCode server: manager, `opencode serve` (protected) and its MCP.
        assertEquals(TermuxProcessGroup.OpencodeServer, p[100]!!.group)
        assertTrue(p[100]!!.protected)
        assertEquals(TermuxProcessGroup.OpencodeServer, p[101]!!.group)
        assertTrue(p[101]!!.protected)
        assertEquals("opencode serve", p[101]!!.name)
        assertEquals(60, p[101]!!.cpuSeconds)
        assertEquals(TermuxProcessGroup.OpencodeServer, p[102]!!.group)
        assertFalse(p[102]!!.protected)
        assertEquals("some-mcp", p[102]!!.name)
        // The minimax case: parent gone, an hour of CPU.
        assertEquals(TermuxProcessGroup.Orphans, p[200]!!.group)
        assertEquals(TermuxOrphanReason.ParentGone, p[200]!!.orphanReason)
        assertEquals("minimax-coding-plan-mcp", p[200]!!.name)
        assertEquals(3723, p[200]!!.cpuSeconds)
        assertEquals(99.0, p[200]!!.cpuPct)
        // Build daemons keep their group even when daemonised (ppid 1).
        assertEquals(TermuxProcessGroup.BuildDaemons, p[300]!!.group)
        assertEquals("Gradle daemon", p[300]!!.name)
        assertNull(p[300]!!.orphanReason)
        // AI Team: supervisor, dolt, per-agent acp and its helper.
        for (pid in listOf(400, 401, 402, 403)) {
            assertEquals(TermuxProcessGroup.AiTeam, p[pid]!!.group, "$pid")
            assertFalse(p[pid]!!.protected)
        }
        assertEquals("opencode acp", p[402]!!.name)
        // Ten minutes of CPU under a plain shell: no OpenCode or gc owner.
        assertEquals(TermuxProcessGroup.Orphans, p[500]!!.group)
        assertEquals(TermuxOrphanReason.CpuNoOwner, p[500]!!.orphanReason)
        assertEquals("stray.py", p[500]!!.name)
        // sshd is protected; shells are plain "other".
        assertEquals(TermuxProcessGroup.Other, p[600]!!.group)
        assertTrue(p[600]!!.protected)
        assertEquals(TermuxProcessGroup.Other, p[700]!!.group)
        assertFalse(p[700]!!.protected)
        assertNull(p[700]!!.orphanReason)
        // node not under OpenCode is a build daemon; little CPU, live parent.
        assertEquals(TermuxProcessGroup.BuildDaemons, p[800]!!.group)
        assertEquals("watch.js", p[800]!!.name)
        assertEquals(listOf(200), report.orphansOver(10.minutes).map { it.pid })
        assertEquals(listOf(200, 500), report.orphansOver(5.minutes).map { it.pid })
        assertEquals(103.7, report.totalCpuPct, 0.01)
        assertEquals(
            listOf(
                TermuxProcessGroup.Orphans, TermuxProcessGroup.OpencodeServer, TermuxProcessGroup.AiTeam,
                TermuxProcessGroup.BuildDaemons, TermuxProcessGroup.Other,
            ),
            report.groups,
        )
    }

    @Test
    fun `B-LINUX-239 an MCP whose live parent is opencode serve is never an orphan`() {
        val phone = phone()
        phone.processes(
            "  101     1  0.2 00:01:00 90000 4000 opencode opencode serve --hostname 127.0.0.1 --port 4096\n" +
                "  102   101 50.0 01:00:00 30000 3900 node node /root/.npm/_npx/abc/node_modules/.bin/busy-mcp\n",
        )
        val p = byPid(phone.scan())
        assertEquals(TermuxProcessGroup.OpencodeServer, p[102]!!.group)
        assertNull(p[102]!!.orphanReason)
    }

    @Test
    fun `B-LINUX-239 reads the listing without procps by walking proc`() {
        val phone = phone()
        File(phone.bin, "ps").delete()
        // Without the stub, a real procps may still be on PATH; hide it so the /proc walk is what answers.
        val result =
            runProcess(
                listOf("bash", phone.tools.path, "procs-scan"),
                mapOf("HOME" to phone.home.path, "PREFIX" to "${phone.root.path}/prefix", "PATH" to noPsPath(phone)),
            )
        assertEquals(0, result.exitCode, result.stderr)
        val report = TermuxProcessReport.parse(result.stdout)!!
        // This very test runner is one of the processes the walk sees.
        assertTrue(report.processes.any { it.pid.toLong() == ProcessHandle.current().pid() })
    }

    /** A PATH of symlinks to the tools the script needs, without `ps`. */
    private fun noPsPath(phone: Phone): String {
        val dir = File(phone.root, "nops").apply { mkdirs() }
        for (tool in listOf(
            "cat", "cut", "tr", "sed", "awk", "grep", "readlink", "date", "sort", "head", "tail", "printf", "getconf",
            "kill", "sleep", "stat", "wc", "basename", "dirname", "env", "id", "mkdir", "rm", "mv", "ls", "find",
        )) {
            val real = runProcess(listOf("bash", "-c", "command -v $tool")).stdout.trim()
            if (real.startsWith("/")) symlink(File(dir, tool), real)
        }
        return dir.path
    }

    @Test
    fun `B-LINUX-239 procs-stop refuses protected processes, the server group and unknown pids`() {
        val phone = phone()
        assertEquals(RefusedPid(101, "protected"), phone.stop("101").refused.single())
        assertEquals(RefusedPid(600, "protected"), phone.stop("600").refused.single())
        assertEquals("protected_group", phone.stop("opencode_server").refused.single().reason)
        assertEquals("not_found", phone.stop("4242").refused.single().reason)
        assertEquals(64, phone.run("procs-stop", "zygote").exitCode)
        assertEquals(64, phone.run("procs-stop").exitCode)
    }

    @Test
    fun `B-LINUX-239 TERM ends a cooperative process and KILL follows after the grace`() {
        // The phone's grace is 5 s; the tests run with 1 s.
        assertTrue(TERMUX_TOOLS_SCRIPT.contains("STOP_WAIT_SECONDS=\${OC_STOP_WAIT_SECONDS:-5}"))
        val phone = phone()
        val polite = start("sleep", "300")
        val stubborn = start("bash", "-c", "trap \"\" TERM; sleep 300")
        Thread.sleep(200) // let bash install its trap
        phone.processes(
            "  ${polite.pid()}     1  0.0 00:00:00 3000 100 sleep sleep 300\n" +
                "  ${stubborn.pid()}     1  0.0 00:00:00 3000 100 bash bash -c trap\n",
        )
        val first = phone.stop("${polite.pid()}")
        assertEquals(listOf(polite.pid().toInt()), first.stopped)
        assertTrue(first.killed.isEmpty())
        assertTrue(first.remaining.isEmpty())
        assertTrue(exitedNonZero(polite))
        val begin = System.nanoTime()
        val second = phone.stop("${stubborn.pid()}")
        val elapsedMs = (System.nanoTime() - begin) / 1_000_000
        assertTrue(second.stopped.isEmpty())
        assertEquals(listOf(stubborn.pid().toInt()), second.killed)
        assertTrue(second.remaining.isEmpty())
        assertTrue(elapsedMs >= 1000, "KILL only after the grace ($elapsedMs ms)")
        assertTrue(elapsedMs < 4000, "and soon after it ($elapsedMs ms)")
        assertTrue(exitedNonZero(stubborn))
    }

    @Test
    fun `B-LINUX-239 a group stop skips its protected members and reports them`() {
        val phone = phone()
        val helper = start("sleep", "300")
        // A shell at ppid 1 is "other" and never an orphan; the sleep with a dead parent and a non-shell name is.
        phone.processes(
            "  600     1  0.0 00:00:00 3000 100 sshd sshd: /usr/sbin/sshd\n" +
                "  ${helper.pid()}     1  0.0 00:00:00 3000 100 helper /x/helper\n",
        )
        val scan = byPid(phone.scan())
        assertEquals(TermuxProcessGroup.Orphans, scan[helper.pid().toInt()]!!.group)
        assertEquals(TermuxProcessGroup.Other, scan[600]!!.group)
        val result = phone.stop("orphans")
        assertEquals(listOf(helper.pid().toInt()), result.stopped)
        assertTrue(result.refused.isEmpty())
        assertTrue(exitedNonZero(helper))
        val other = phone.stop("other")
        assertEquals(RefusedPid(600, "protected"), other.refused.single())
        assertTrue(other.stopped.isEmpty())
    }

    @Test
    fun `B-LINUX-239 a list of pids stops exactly those, checking each again`() {
        val phone = phone()
        val a = start("sleep", "300")
        val b = start("sleep", "300")
        val bystander = start("sleep", "300")
        phone.processes(
            "  600     1  0.0 00:00:00 3000 100 sshd sshd: /usr/sbin/sshd\n" +
                "  ${a.pid()}     1  0.0 00:00:00 3000 100 sleep sleep 300\n" +
                "  ${b.pid()}     1  0.0 00:00:00 3000 100 sleep sleep 300\n" +
                "  ${bystander.pid()}     1  0.0 00:00:00 3000 100 sleep sleep 300\n",
        )
        val result = phone.stop("${a.pid()},600,4242,${b.pid()}")
        assertEquals(listOf(a.pid().toInt(), b.pid().toInt()), result.stopped)
        assertEquals(listOf(RefusedPid(600, "protected"), RefusedPid(4242, "not_found")), result.refused)
        assertTrue(exitedNonZero(a))
        assertTrue(exitedNonZero(b))
        // Only the named ones: the third sleep keeps running.
        assertTrue(bystander.isAlive)
    }

    @Test
    fun `B-LINUX-239 each process has one of the six kinds and the Termux app itself is never stopped or counted`() {
        val phone = phone()
        phone.processes(
            phonePs +
                "  900   700  5.0 00:02:00 150000 600 node node /data/data/com.termux/files/usr/lib/node_modules/@anthropic-ai/claude-code/cli.js\n" +
                "   50     1  0.5 00:05:00 200000 9000 com.termux com.termux\n",
        )
        val report = phone.scan()
        val p = byPid(report)
        assertEquals(
            mapOf(
                50 to PhoneProcessKind.Helpers, 100 to PhoneProcessKind.OpenCodeServer,
                101 to PhoneProcessKind.OpenCodeServer, 102 to PhoneProcessKind.OpenCodeServer,
                200 to PhoneProcessKind.Helpers, 300 to PhoneProcessKind.DevServices, 400 to PhoneProcessKind.AiTeam,
                401 to PhoneProcessKind.AiTeam, 402 to PhoneProcessKind.AiTeam, 403 to PhoneProcessKind.AiTeam,
                500 to PhoneProcessKind.Helpers, 600 to PhoneProcessKind.Helpers, 700 to PhoneProcessKind.Terminals,
                800 to PhoneProcessKind.DevServices, 900 to PhoneProcessKind.ClaudeCode,
            ),
            p.mapValues { it.value.kind },
        )
        assertTrue(p[50]!!.isHostApp)
        assertFalse(p[50]!!.stoppable)
        assertFalse(p[101]!!.stoppable)
        assertFalse(p[600]!!.stoppable)
        assertTrue(p[900]!!.stoppable)
        // Everything Termux started counts against Android's 32; the app itself does not.
        assertEquals(15, report.count)
        assertEquals(14, report.backgroundCount)
        assertEquals(32, TermuxProcessReport.ANDROID_BACKGROUND_LIMIT)
        // Memory in MB, rounded.
        assertEquals(293, p[300]!!.memoryMb)
        assertNotEquals(0, report.totalRssKb)
    }
}
