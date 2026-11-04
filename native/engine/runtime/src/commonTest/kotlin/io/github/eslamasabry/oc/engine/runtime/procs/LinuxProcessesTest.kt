package io.github.eslamasabry.oc.engine.runtime.procs

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.getOrNull
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/linux_manager_parse_test.dart group 'processes' (the parts that need no real process). */
internal fun statLine(
    pid: Int,
    comm: String,
    ppid: Int,
    utime: Int = 0,
    stime: Int = 0,
    start: Long = 1000,
    rssPages: Int = 2500,
    state: String = "S",
): String = "$pid ($comm) $state $ppid 1 1 0 -1 4194560 100 0 0 0 $utime $stime 0 0 " +
    "20 0 1 0 $start 1000000 $rssPages 18446744073709551615"

internal fun scanRow(pid: Int, comm: String, ppid: Int, cmd: String, start: Long = 1000, utime: Int = 0): String =
    "::p $pid\t${statLine(pid, comm, ppid, start = start, utime = utime)}\t$cmd"

internal val SCAN_FIXTURE: String = listOf(
    "::self 900",
    "::uptime 5000.00",
    scanRow(1, "init", 0, "/init"),
    scanRow(10, "proot", 1, "/data/app/x/lib/arm64/libproot.so --kill-on-exit"),
    scanRow(11, "io.github.eslamasabry.opencode_mobile", 1, "io.github.eslamasabry.opencode_mobile"),
    scanRow(20, "node", 10, "/opt/opencode2/bin/opencode2 serve --port 4097", start = 2000),
    scanRow(
        30, "node", 10,
        "/home/oc/.local/node/bin/node /home/oc/.local/share/oc-paseo/1.0/node_modules/.bin/paseo daemon start",
        start = 2100,
    ),
    scanRow(40, "claude", 10, "/usr/local/bin/claude --resume abc", start = 2200),
    scanRow(50, "sh", 10, "sh -c npm run dev -- --port 5173", start = 3000),
    scanRow(51, "node", 50, "node /root/projects/app/node_modules/.bin/vite --port 5173", start = 3010, utime = 600),
    scanRow(60, "bash", 10, "-bash", start = 4000),
    scanRow(900, "sh", 10, "sh -c # oc-linux-scan", start = 4900),
    scanRow(901, "tr", 900, "tr \\000 "),
    "::p 70\t${statLine(70, "defunct", 10, state = "Z")}\t",
    "warning: something on stderr",
).joinToString("\n")

class LinuxProcessesTest {
    private val scan = LinuxProcessScan.parse(SCAN_FIXTURE)
    private val byPid = scan.processes.associateBy { it.pid }

    @Test
    fun `B-LINUX-082 the process list leaves out Android, proot, the scan itself and zombies`() {
        assertEquals(setOf(20, 30, 40, 50, 51, 60), byPid.keys)
    }

    @Test
    fun `B-LINUX-082 sorts what a process is by its command`() {
        assertEquals(LinuxProcessKind.OpenCodeServer, byPid.getValue(20).kind)
        assertTrue(byPid.getValue(20).protected)
        assertEquals(LinuxProcessKind.Paseo, byPid.getValue(30).kind)
        assertEquals(LinuxProcessKind.Agent, byPid.getValue(40).kind)
        assertEquals(LinuxProcessKind.DevServer, byPid.getValue(50).kind)
        assertEquals(LinuxProcessKind.DevServer, byPid.getValue(51).kind)
        assertEquals(LinuxProcessKind.Other, byPid.getValue(60).kind)
    }

    @Test
    fun `B-LINUX-082 names the program a person would recognise`() {
        assertEquals("vite", byPid.getValue(51).programName)
        assertEquals("claude", byPid.getValue(40).programName)
        assertEquals("npm", byPid.getValue(50).programName)
        assertEquals("bash", byPid.getValue(60).programName)
    }

    @Test
    fun `B-LINUX-082 reads memory, age and the identity from the stat line`() {
        val vite = byPid.getValue(51)
        assertEquals("node", vite.name)
        assertEquals(10L, vite.memoryMb)
        // Age = the scan's start (tick 4900) minus the process's (3010).
        assertEquals(19L, vite.elapsedSeconds)
        assertEquals("51:3010", vite.identity)
        assertEquals(6L, vite.cpuSeconds)
    }

    @Test
    fun `B-LINUX-082 age ignores proc uptime, which can run on another clock`() {
        val skewed = LinuxProcessScan.parse(SCAN_FIXTURE.replaceFirst("::uptime 5000.00", "::uptime 90000.00"))
        assertEquals(19L, skewed.processes.first { it.pid == 51 }.elapsedSeconds)
    }

    @Test
    fun `B-LINUX-082 a process older than the scan by 72 minutes reads 1 h 12 min`() {
        val scan = LinuxProcessScan.parse(
            listOf(
                "::self 900",
                "::uptime 100.00",
                scanRow(30, "node", 10, "node /opt/oc-paseo/bin/paseo daemon", start = 1000),
                scanRow(900, "sh", 10, "sh -c # oc-linux-scan", start = 1000L + 72 * 6000),
            ).joinToString("\n"),
        )
        val age = scan.processes.single().elapsedSeconds
        assertEquals(72L * 60, age)
        assertEquals(DurationWords.Hours(1, 12), DurationWords.of(age))
    }

    @Test
    fun `B-LINUX-082 without the scan's own row, age falls back to uptime`() {
        val scan = LinuxProcessScan.parse(
            listOf(
                "::self 900",
                "::uptime 5000.00",
                scanRow(30, "node", 10, "node /opt/oc-paseo/bin/paseo daemon", start = 3000),
            ).joinToString("\n"),
        )
        assertEquals(4970L, scan.processes.single().elapsedSeconds)
    }

    private fun groupsOf(vararg rows: String): List<LinuxProcessGroup> =
        LinuxProcessScan.parse((listOf("::self 900", "::uptime 5000.00") + rows).joinToString("\n")).groups()

    @Test
    fun `B-LINUX-083 Paseo supervisor, daemon and their node children are one row`() {
        val groups = groupsOf(
            scanRow(30, "node", 10, "node /home/oc/oc-paseo/bin/paseo supervisor"),
            scanRow(31, "node", 10, "node /home/oc/oc-paseo/bin/paseo daemon start"),
            scanRow(32, "node", 31, "node /home/oc/oc-paseo/lib/worker.js"),
            scanRow(33, "node", 32, "node /tmp/helper.js"),
            scanRow(60, "bash", 10, "-bash"),
        )
        assertEquals(2, groups.size)
        val paseo = groups.first { it.leader.kind == LinuxProcessKind.Paseo }
        assertEquals(setOf(30, 31, 32, 33), paseo.members.map { it.pid }.toSet())
        assertEquals(4, paseo.count)
        assertEquals(setOf(30, 31), paseo.roots.map { it.pid }.toSet())
        assertEquals(40L, paseo.memoryMb)
    }

    @Test
    fun `B-LINUX-083 a dev server and its shell are one row, a bash that started it is not`() {
        val groups = groupsOf(
            scanRow(60, "bash", 10, "-bash"),
            scanRow(50, "sh", 60, "sh -c npm run dev -- --port 5173"),
            scanRow(51, "node", 50, "node /app/node_modules/.bin/vite --port 5173"),
        )
        assertEquals(2, groups.size)
        assertEquals(setOf(50, 51), groups.first { it.leader.pid == 50 }.members.map { it.pid }.toSet())
        assertEquals(1, groups.first { it.leader.pid == 60 }.count)
    }

    @Test
    fun `B-LINUX-083 measures processor use between two readings`() {
        val viteRow = "node /root/projects/app/node_modules/.bin/vite --port 5173"
        val later = LinuxProcessScan.parse(
            SCAN_FIXTURE
                .replaceFirst("::uptime 5000.00", "::uptime 5010.00")
                .replaceFirst(
                    scanRow(51, "node", 50, viteRow, start = 3010, utime = 600),
                    scanRow(51, "node", 50, viteRow, start = 3010, utime = 1100),
                ),
        )
        val vite = later.processes.first { it.pid == 51 }
        val pct = later.cpuPercent(vite, scan)
        assertTrue(pct != null && abs(pct - 50.0) < 0.01, "was $pct")
        assertNull(scan.cpuPercent(byPid.getValue(51), null))
    }

    @Test
    fun `B-LINUX-084 a stop includes what the process started, children first`() {
        assertEquals(listOf(51, 50), scan.withDescendants(listOf(byPid.getValue(50))).map { it.pid })
    }

    @Test
    fun `B-LINUX-084 a stop never includes the OpenCode server`() {
        assertTrue(scan.withDescendants(listOf(byPid.getValue(20))).isEmpty())
    }

    @Test
    fun `B-LINUX-084 the stop script accepts only process identities`() {
        val script = linuxStopScript(listOf("51:3010")).getOrNull()
        assertTrue(script != null && script.contains("oc_list=\"51:3010\""))
        val refused = Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        assertEquals(refused, linuxStopScript(emptyList()))
        assertEquals(refused, linuxStopScript(listOf("51;rm -rf /")))
        assertEquals(refused, linuxStopScript(listOf("1:5")))
        assertEquals(refused, linuxStopScript(listOf("$(id):1")))
        assertEquals(refused, linuxStopScript(listOf("51:3010", "52:1 ; reboot")))
    }

    @Test
    fun `B-LINUX-084 parses what a stop did`() {
        val result = LinuxStopResult.parse(
            "::stop stopped 51:3010\n::stop forced 50:3000\n::stop remaining 9:1\n::stop changed 8:2\nnoise\n",
        )
        assertEquals(2, result.ended)
        assertEquals(1, result.forced)
        assertEquals(1, result.remaining)
        assertEquals(1, result.changed)
        assertEquals(LinuxStopOutcome.Stopped, result.outcomes["51:3010"])
    }

    @Test
    fun `B-LINUX-084 the scan script is the Flutter script, word for word`() {
        assertTrue(LINUX_PROCESS_SCAN_SCRIPT.startsWith("# oc-linux-scan\nprintf '::self %s\\n' \"$$\"\n"))
        assertTrue(LINUX_PROCESS_SCAN_SCRIPT.contains("  p=\${d#/proc/}\n"))
        assertTrue(LINUX_PROCESS_SCAN_SCRIPT.contains("printf '::p %s\\t%s\\t%s\\n' \"\$p\" \"\$s\" \"\$c\""))
        assertTrue(LINUX_PROCESS_SCAN_SCRIPT.endsWith("done\nexit 0\n"))
    }
}
