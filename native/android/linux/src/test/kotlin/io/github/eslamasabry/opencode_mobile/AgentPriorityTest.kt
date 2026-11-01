package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPriorityTest {
    private val uid = 10100
    private val app = 500
    private fun p(pid: Int, parent: Int, name: String, tracer: Int? = 0, argv: List<String> = emptyList(), state: Char = 'S') =
        ProcEntry(pid, uid, parent, tracer, pid.toLong(), name, null, state, 0, 0, argv)

    // The app (500) runs two proots: the chat server's (600 -> opencode2 601)
    // and the agent helper's (700). Under 700: node 701 (the helper), a
    // worker 702 and claude 703 with a tool 704; 705 was orphaned (parent 1)
    // but is still traced by 700. An unrelated program of the app (800).
    private val daemonArgv = listOf("node", "/home/oc/.local/bin/paseo", "daemon", "run", "--home", "/home/oc/.oc-profiles/42/paseo")
    private val entries = listOf(
        p(app, 1, "opencode_mobile"),
        p(600, app, "libproot.so"),
        p(601, 600, "opencode2", tracer = 600, argv = listOf("opencode2", "serve")),
        p(700, app, "libproot.so"),
        p(701, 700, "node", tracer = 700, argv = daemonArgv),
        p(702, 701, "Paseo Supervisor", tracer = 700),
        p(703, 702, "claude", tracer = 700),
        p(704, 703, "bash", tracer = 700),
        p(705, 1, "sleep", tracer = 700),
        p(800, app, "libaiteam"),
        // A shell running a script that merely mentions the helper.
        p(900, app, "sh", argv = listOf("sh", "-c", "pgrep -f \"paseo daemon run --home /home/oc/.oc-profiles/\"")),
    )

    @Test fun helpersAreFoundByTheirCommandLineNotByAScriptAboutThem() {
        assertEquals(listOf(701), AgentPriority.daemons(entries, app).map { it.pid })
    }

    @Test fun theHelpersProotAndEverythingBelowItFollowButNothingElse() {
        assertEquals(listOf(700, 701, 702, 703, 704, 705), AgentPriority.targets(entries, app).sorted())
    }

    @Test fun theChatServerAndTheAppAreNeverTargets() {
        val targets = AgentPriority.targets(entries, app)
        assertTrue(600 !in targets && 601 !in targets && app !in targets && 800 !in targets)
    }

    @Test fun levelsMapToAndroidPriorities() {
        assertEquals(0, AgentPriority.priorityOf("normal"))
        assertEquals(10, AgentPriority.priorityOf("slow"))
        assertEquals(19, AgentPriority.priorityOf("hold"))
        assertNull(AgentPriority.priorityOf("fast"))
    }

    @Test fun everyThreadOfEveryTargetIsSetAndRefusalsAreCounted() {
        val set = mutableListOf<Pair<Int, Int>>()
        val result = AgentPriority.apply(
            "slow", uid, app,
            scan = { entries },
            tidsOf = { pid -> listOf(pid, pid + 10000) },
            set = { tid, prio -> if (tid == 10704) throw SecurityException("no") else set.add(tid to prio) },
            groupOf = { "cpuset:/background" },
        )!!
        assertEquals(6, result.processes)
        assertEquals(11, result.threads)
        assertEquals(1, result.failed)
        assertTrue(set.all { it.second == 10 })
        assertEquals("cpuset:/background", result.group)
    }

    @Test fun anUnknownLevelDoesNothing() {
        assertNull(AgentPriority.apply("fast", uid, app, scan = { entries }))
    }
}
