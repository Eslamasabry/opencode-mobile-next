package io.github.eslamasabry.oc.engine.linuxtools

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The model half of test/termux_processes_test.dart (lib/termux/processes.dart). */
class TermuxProcessesModelTest {
    private fun at(cpu: Int, elapsed: Int, name: String = "x", pid: Int = 7): TermuxProcess =
        TermuxProcess.fromJson(
            buildJsonObject {
                put("pid", pid)
                put("name", name)
                put("cpu_pct", 99.0)
                put("cpu_seconds", cpu)
                put("elapsed_s", elapsed)
            },
        )!!

    private fun report(p: TermuxProcess) = TermuxProcessReport(listOf(p))

    @Test
    fun `B-LINUX-239 busy is measured between two readings, never from the lifetime average`() {
        // No earlier reading: unknown, whatever ps's average says.
        assertEquals(PhoneProcessActivity.Unknown, TermuxProcessReport.activityOf(at(100, 100), null))
        // 5 s of processor in 10 s: busy.
        assertEquals(PhoneProcessActivity.Busy, TermuxProcessReport.activityOf(at(105, 110), report(at(100, 100))))
        // A second's rounding in 10 s: idle, despite "CPU 99%".
        assertEquals(PhoneProcessActivity.Idle, TermuxProcessReport.activityOf(at(101, 110), report(at(100, 100))))
        // A reused pid (younger than before) or another name: unknown.
        assertEquals(PhoneProcessActivity.Unknown, TermuxProcessReport.activityOf(at(5, 20), report(at(100, 100))))
        assertEquals(
            PhoneProcessActivity.Unknown,
            TermuxProcessReport.activityOf(at(105, 110, name = "y"), report(at(100, 100))),
        )
    }

    @Test
    fun `B-LINUX-239 an empty listing is an empty report and results parse`() {
        assertEquals(0, TermuxProcessReport.parse("")!!.count)
        assertEquals(0, TermuxProcessReport.parse("[]\n")!!.count)
        assertNull(TermuxProcessReport.parse("{\"not\":\"a list\"}"), "a non-list is unreadable, not empty")
        val stop =
            TermuxProcessStopResult.parse(
                "{\"stopped\":[1,2],\"killed\":[3],\"remaining\":[{\"pid\":3,\"name\":\"x\"}]," +
                    "\"refused\":[{\"pid\":9,\"reason\":\"protected\"}]}",
            )!!
        assertEquals(3, stop.endedCount)
        assertEquals("x", stop.remaining.single().name)
        assertEquals(9, stop.refused.single().pid)
        assertTrue(termuxToolsCommandScript("procs-stop", "ai_team").endsWith("exec \"\$TOOLS\" procs-stop 'ai_team'\n"))
    }

    @Test
    fun `B-LINUX-239 memory is whole megabytes and a missing reading stays unknown`() {
        assertNull(TermuxProcess.fromJson(buildJsonObject { put("pid", 7); put("rss_kb", 0) })!!.memoryMb)
        assertEquals(293, TermuxProcess(pid = 300, rssKb = 300000).memoryMb)
        assertEquals(1, TermuxProcess(pid = 3, rssKb = 10).memoryMb)
        assertNull(TermuxProcess.fromJson(buildJsonObject { put("pid", 0) }), "no positive pid, no process")
        assertNull(TermuxProcess.fromJson(JsonPrimitive("x")))
        assertEquals(32, TermuxProcessReport.ANDROID_BACKGROUND_LIMIT)
    }

    @Test
    fun `B-LINUX-239 a pid list stops as one comma argument and unsafe arguments are refused`() {
        assertTrue(termuxToolsCommandScript("procs-stop", "12,34").endsWith("exec \"\$TOOLS\" procs-stop '12,34'\n"))
        assertFailsWith<InvalidToolCommand> { termuxToolsCommandScript("procs-stop", "1;2") }
    }

    @Test
    fun `B-LINUX-239 the process verbs ask the tools script with the right argument and refuse low pids`() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val processes =
            TermuxProcesses { verb, argument, _ ->
                calls += verb to argument
                (if (verb == "procs-scan") "[]" else "{\"stopped\":[42]}").ok()
            }
        assertIs<Outcome.Ok<*>>(processes.scan())
        assertEquals(listOf(42), (processes.stopPids(listOf(42)) as Outcome.Ok).value.stopped)
        processes.stopPids(listOf(12, 34))
        processes.stopGroup(TermuxProcessGroup.Orphans)
        assertIs<Outcome.Failed>(processes.stopPid(1))
        assertIs<Outcome.Failed>(processes.stopPids(emptyList()))
        assertIs<Outcome.Failed>(processes.stopPids(listOf(5, 0)))
        assertEquals(
            listOf("procs-scan" to "", "procs-stop" to "42", "procs-stop" to "12,34", "procs-stop" to "orphans"),
            calls,
        )
        assertTrue(TermuxProcessGroup.Orphans.stoppable)
        assertTrue(!TermuxProcessGroup.OpencodeServer.stoppable)
    }
}
