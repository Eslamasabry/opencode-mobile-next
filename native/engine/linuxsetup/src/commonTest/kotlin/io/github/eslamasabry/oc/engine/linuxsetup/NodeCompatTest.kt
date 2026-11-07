package io.github.eslamasabry.oc.engine.linuxsetup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Node.js on phones that crash it (#117/#119; Flutter fix/117-adapt test/node_compat_test.dart decisions). The shell
 * itself runs against a fake node in :engine:runtime's NodeCompatShellTest (same text, checked equal by
 * :feature:phonesetup's NodeCompatTwinTest).
 */
class NodeCompatTest {
    @Test
    fun `B-LINUX-203 a phone that runs Node as it is gets no safe mode`() {
        val decision = NodeCompat.decide(inCompat = false) { true }
        assertEquals(NodeCompatDecision.Runs(NodeCompatState(NodeCompatMode.None)), decision)
    }

    @Test
    fun `B-LINUX-203 the first safe mode that passes is kept, in the order armcap, single-threaded, both`() {
        val tried = mutableListOf<NodeCompatMode>()
        val decision =
            NodeCompat.decide(inCompat = false) {
                tried += it
                it == NodeCompatMode.Single || it == NodeCompatMode.Both
            }
        assertEquals(listOf(NodeCompatMode.None, NodeCompatMode.Armcap, NodeCompatMode.Single), tried)
        assertEquals(NodeCompatDecision.Runs(NodeCompatState(NodeCompatMode.Single)), decision)
    }

    @Test
    fun `B-LINUX-203 nothing passing asks for compatibility mode first, then gives up`() {
        assertEquals(NodeCompatDecision.RetryInCompat, NodeCompat.decide(inCompat = false) { false })
        assertEquals(NodeCompatDecision.CannotRun, NodeCompat.decide(inCompat = true) { false })
        assertEquals(
            NodeCompatDecision.Runs(NodeCompatState(NodeCompatMode.Both, prootCompat = true)),
            NodeCompat.decide(inCompat = true) { it == NodeCompatMode.Both },
        )
    }

    @Test
    fun `B-LINUX-203 the saved state reads back with its proot line, and anything else is no state`() {
        assertEquals(NodeCompatState(NodeCompatMode.Armcap), NodeCompat.parse("armcap\n"))
        assertEquals(NodeCompatState(NodeCompatMode.None, prootCompat = true), NodeCompat.parse("none\nproot-compat\n"))
        assertEquals(NodeCompatState(NodeCompatMode.Both, prootCompat = true), NodeCompat.parse("both\r\nproot-compat"))
        assertNull(NodeCompat.parse(null))
        assertNull(NodeCompat.parse(""))
        assertNull(NodeCompat.parse("fast\nproot-compat\n"))
        assertNull(NodeCompat.parse("proot-compat\n"))
    }

    @Test
    fun `B-LINUX-203 the give-up line becomes the plain message for the row and the job`() {
        val failed = SetupJobComponent(id = "node", state = "failed", error = NodeCompat.FAILURE_LINE)
        val text = describeSetupFailure(failed, "[oc] probe both-version 139 # node --version\n", "Node.js")
        assertEquals(SetupProblem.NodeCannotRun, text.component)
        assertEquals(SetupProblem.NodeCannotRun, text.job)
        assertEquals(
            "This phone's system can't run Node.js inside built-in Linux. " +
                "You can still use OpenCode Mobile by connecting to your computer.",
            SetupProblemWordsEn.of(text.job),
        )
        // The reason may be a generic exit line; the log's last lines still carry it.
        val generic = SetupJobComponent(id = "node", state = "failed", error = "exit 1")
        assertEquals(
            SetupProblem.NodeCannotRun,
            describeSetupFailure(generic, "x\n${NodeCompat.FAILURE_LINE}\n", "Node.js").job,
        )
        // A compatibility-mode retry message is not a give-up.
        val retry = SetupJobComponent(id = "node", state = "failed", error = "exit 139")
        assertFalse(
            describeSetupFailure(retry, "[oc] Node.js crashed on this phone; trying again in compatibility mode\n", "Node.js")
                .job == SetupProblem.NodeCannotRun
        )
    }

    @Test
    fun `B-LINUX-203 the Node install checks the phone before the download and Node before npm`() {
        val script = SetupScripts.NODE_INSTALL
        assertTrue(script.contains(NodeCompat.FUNCTIONS))
        val early = script.indexOf(NodeCompat.EARLY_CHECK)
        val download = script.indexOf("oc_stage 'Downloading Node.js")
        val links = script.indexOf("for tool in node npm npx")
        val start = script.indexOf(NodeCompat.START_CHECK)
        val npm = script.indexOf("npm config set prefix /usr/local")
        assertTrue(script.indexOf("oc_node_early_check()") < early, "functions come first")
        assertTrue(early in 0 until download, "early check before the download")
        assertTrue(links in 0 until start, "start check after the links")
        assertTrue(start in 0 until npm, "start check in place of npm config, which runs after it")
    }

    @Test
    fun `B-LINUX-203 probes print exit codes only, and the safe modes stay off NODE_OPTIONS`() {
        val functions = NodeCompat.FUNCTIONS
        assertTrue(functions.contains("echo \"[oc] probe \$oc_probe_id \$oc_probe_rc # \$oc_probe_show\""))
        assertTrue(functions.contains("timeout 30"))
        assertTrue(functions.contains("--single-threaded --v8-pool-size=1"))
        assertFalse(functions.contains("NODE_OPTIONS="))
        assertTrue(functions.contains(NodeCompat.FAILURE_LINE))
        assertTrue(functions.contains("exit ${NodeCompat.RETRY_EXIT}"))
        for (name in NodeCompat.ENV_NAMES) assertTrue(NodeCompat.SERVER_ENV.contains(name))
    }
}
