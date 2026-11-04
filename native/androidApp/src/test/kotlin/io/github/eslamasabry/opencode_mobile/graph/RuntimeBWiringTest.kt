package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.runtime.procs.PhoneProcessWatch
import io.github.eslamasabry.oc.engine.runtime.procs.RunningProcessName
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessKind
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The Running page's real source: the native process table (BuiltinLinux.processTable) mapped to the page rows. */
class RuntimeBWiringTest {
    private fun row(pid: Int, ppid: Int, name: String, command: String, owner: String, cpu: Double = 1.0) =
        mapOf(
            "pid" to pid, "ppid" to ppid, "name" to name, "command" to command, "cpuPercent" to cpu,
            "rssBytes" to 4096L, "elapsedSeconds" to 60L, "owner" to owner, "session" to null,
        )

    private val table =
        mapOf(
            "processes" to
                listOf(
                    row(100, 1, "e.opencode_mobile", "io.github.app", "app"),
                    row(200, 100, "libproot.so", "/data/app/lib/libproot.so --rootfs", "opencode"),
                    row(201, 200, "MainThread", "node /usr/lib/node_modules/opencode-ai/bin/opencode serve", "opencode"),
                    row(300, 1, "bash", "bash -lc python3 -m http.server", "orphan"),
                ),
            "sampledMs" to 1000L,
        )

    private suspend fun names(n: RunningProcessName) =
        when (n) {
            is RunningProcessName.Kernel -> n.name
            is RunningProcessName.Plain -> n.kind.key
        }

    @Test
    fun `B-LINUX-180 the Running page names a process by its program, never its command line`() = runTest {
        val reading = PhoneProcessWatch().record(ProcessRows.snapshot(table), Instant.fromEpochSeconds(1000))
        val rows = ProcessRows.rows(reading, ::names).associateBy { it.pid }
        assertEquals("app", rows.getValue(100).name)
        assertEquals("linux", rows.getValue(200).name)
        assertEquals("openCode", rows.getValue(201).name)
        assertEquals("command", rows.getValue(300).name)
        rows.values.forEach { assertFalse(it.name.contains('/'), it.name) }
    }

    @Test
    fun `B-LINUX-146 the app and a tree's root have no Stop, left-behind work is marked, kinds come from the group`() = runTest {
        val reading = PhoneProcessWatch().record(ProcessRows.snapshot(table), Instant.fromEpochSeconds(1000))
        val rows = ProcessRows.rows(reading, ::names).associateBy { it.pid }
        assertEquals(ProcessRole.App, rows.getValue(100).role)
        assertEquals(ProcessRole.ServerRoot, rows.getValue(200).role)
        assertEquals(ProcessRole.Normal, rows.getValue(201).role)
        assertEquals(ProcessKind.OpenCode, rows.getValue(201).kind)
        assertTrue(rows.getValue(300).leftBehind)
        assertFalse(rows.getValue(201).leftBehind)
        assertEquals(3, reading.childCount)
    }

    @Test
    fun `B-LINUX-082 a native row without a pid is not a process and unknown owners read Other`() {
        val snapshot = ProcessRows.snapshot(mapOf("processes" to listOf(mapOf("name" to "x"), row(5, 1, "make", "make", "weird"))))
        assertEquals(listOf(5), snapshot.processes.map { it.pid })
        assertEquals("other", snapshot.processes.single().owner.wire)
    }
}
