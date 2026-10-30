package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProcessListTest {
    private val uid = 10234
    private val app = 9000
    private val loader = "/data/app/~~a1/x/lib/arm64/libproot-loader.so"

    private fun entry(pid: Int, parent: Int, tracer: Int? = 0, ticks: Long = 0, argv: List<String> = emptyList()) =
        ProcEntry(pid, uid, parent, tracer, 500L, "p$pid", loader, 'S', ticks, 10, argv)

    private val claude = listOf("claude", "--resume", "0f2c9d1e-aaaa-bbbb-cccc-1234567890ab", "--print")
    private val now = listOf(
        entry(app, 600).copy(exe = "/system/bin/app_process64"),
        entry(20000, app).copy(exe = "libproot.so"),
        entry(20005, 20000, tracer = 20000),
        entry(20100, 20005, tracer = 20000, argv = claude),
        entry(30000, app).copy(exe = "libproot.so"),
        entry(30001, 30000, tracer = 30000),
        entry(15001, 1),
        entry(40000, 600).copy(exe = "/system/bin/app_process64"),
    )

    @Test fun eachTreeTakesItsRootsOwnerAndTheRestIsAppOrphanOrOther() {
        val owners = NativeProcessList.owners(now, setOf(app),
            mapOf(20000 to NativeProcessList.ownerOfService("agent-host.p1"), 30000 to "opencode"), setOf(15001))
        assertEquals("app", owners[app])
        for (pid in listOf(20000, 20005, 20100)) assertEquals("paseo", owners[pid])
        for (pid in listOf(30000, 30001)) assertEquals("opencode", owners[pid])
        assertEquals("orphan", owners[15001])
        assertEquals("other", owners[40000])
        assertTrue(owners.values.all { it in NativeProcessList.OWNERS })
    }

    @Test fun serviceNamesMapToOwners() {
        assertEquals("opencode", NativeProcessList.ownerOfService(BuiltinLinux.SERVER))
        assertEquals("paseo", NativeProcessList.ownerOfService("agent-host.abc"))
        assertEquals("agent-sign-in", NativeProcessList.ownerOfService("agent-auth.abc"))
        assertEquals("engine", NativeProcessList.ownerOfService(BuiltinLinux.PHONE_ENGINE))
        assertEquals("helper", NativeProcessList.ownerOfService("aiteam"))
    }

    @Test fun theAgentSessionComesFromItsResumeOrSessionId() {
        assertEquals("0f2c9d1e-aaaa-bbbb-cccc-1234567890ab", NativeProcessList.session(claude))
        assertEquals("abc", NativeProcessList.session(listOf("claude", "--session-id=abc")))
        assertNull(NativeProcessList.session(listOf("claude", "--resume", "--print")))
        assertNull(NativeProcessList.session(listOf("bash", "-c", "flutter test")))
    }

    @Test fun theCommandIsShortAndHidesSecrets() {
        val long = listOf("bash", "-c", "x".repeat(500))
        assertEquals(NativeProcessList.COMMAND_CHARS, NativeProcessList.command(long, "bash").length)
        assertEquals("node server --api-key ••• OPENAI_API_KEY=••• --port 4097",
            NativeProcessList.command(listOf("node", "server", "--api-key", "sk-live", "OPENAI_API_KEY=sk-1",
                "--port", "4097"), "node"))
        assertEquals("[kworker]", NativeProcessList.command(emptyList(), "kworker"))
    }

    @Test fun cpuIsTheShareOfOneCoreOverTheSample() {
        // 100 ticks a second; 100 ticks used in 1 s is one full core.
        assertEquals(100.0, NativeProcessList.cpuPercent(1000, 1100, 1000, 100), 0.0)
        assertEquals(33.3, NativeProcessList.cpuPercent(0, 33, 990, 100), 0.0)
        assertEquals(0.0, NativeProcessList.cpuPercent(null, 1100, 1000, 100), 0.0)
        assertEquals(0.0, NativeProcessList.cpuPercent(10, 5, 1000, 100), 0.0)
    }

    @Test fun rowsCarryTheContractAndSkipZombies() {
        val before = listOf(entry(20100, 20005, tracer = 20000, ticks = 100))
        val after = listOf(entry(20100, 20005, tracer = 20000, ticks = 150, argv = claude),
            entry(15030, 1).copy(state = 'Z'))
        val rows = NativeProcessList.rows(before, after, NativeProcessList.Clock(1000, 100, 4096, 65.0),
            mapOf(20100 to "paseo"))
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(setOf("pid", "ppid", "name", "command", "cpuPercent", "rssBytes", "elapsedSeconds",
            "startTicks", "owner", "session"), row.keys)
        assertEquals(20100, row["pid"])
        assertEquals(20005, row["ppid"])
        assertEquals(50.0, row["cpuPercent"])
        assertEquals(40960L, row["rssBytes"])
        assertEquals(60L, row["elapsedSeconds"])
        assertEquals("paseo", row["owner"])
        assertEquals("0f2c9d1e-aaaa-bbbb-cccc-1234567890ab", row["session"])
    }

    @Test fun stopIsRefusedForTheAppARootOrWhatIsNotTheAppsLiveProcess() {
        val roots = setOf(20000, 30000)
        fun refusal(pid: Int, entry: ProcEntry?) =
            NativeProcessList.stopRefusal(pid, entry, uid, setOf(app, 9100), roots)
        assertEquals("app", refusal(app, entry(app, 600)))
        assertEquals("app", refusal(9100, entry(9100, 600)))
        assertEquals("serverRoot", refusal(20000, entry(20000, app)))
        assertEquals("invalid", refusal(1, null))
        assertEquals("invalid", refusal(0, null))
        assertEquals("notFound", refusal(4242, null))
        assertEquals("notFound", refusal(15001, entry(15001, 1).copy(uid = 10999)))
        assertEquals("notFound", refusal(15030, entry(15030, 1).copy(state = 'Z')))
        assertNull(refusal(20100, entry(20100, 20005, tracer = 20000)))
        assertNull(refusal(15001, entry(15001, 1)))
    }
}
