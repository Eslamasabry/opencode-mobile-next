package io.github.eslamasabry.oc.engine.runtime.install

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.host.refused
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/phone_agent_setup_engine_test.dart (the job an ARM64 OpenCode 1 host hands to the native runner). */
class AgentHostJobTest {
    private val scripts =
        AgentHostJob.AgentScripts(
            agentId = "claude",
            bootstrapUser = "# bootstrap the oc user as root\n",
            paseoCheck = "paseo --version",
            paseoInstall = "# install paseo\n",
            agentCheck = "claude --version",
            agentInstall = "# install claude\n",
            linuxCheck = "echo 24.04",
            essentialsCheck = "echo ready",
            pythonCheck = "echo 3.12",
        )

    /** What `_Arm64V1Linux` answers: Ubuntu ready, root checks pass for the base, no agent user yet. */
    private class Arm64V1Linux : AgentHostJob.CheckPort {
        val rootChecks = mutableListOf<String>()
        val agentChecks = mutableListOf<String>()
        var rootRefused = false

        override suspend fun ubuntuInstalled(): Boolean = true

        override suspend fun runAsRoot(script: String): Outcome<String> {
            rootChecks += script
            if (rootRefused) return RuntimeProblem.Generic.refused()
            val ids = Regex("::oc-check-begin %s\\\\n' '([^']+)'").findAll(script).map { it.groupValues[1] }
            val passing = mapOf("linux" to "24.04", "essentials" to "ready", "python" to "3.12")
            return ids.joinToString("") { id ->
                val version = passing[id]
                "\n::oc-check-begin $id\n${version ?: ""}\n\n::oc-check-end $id ${if (version != null) 0 else 1}\n"
            }.ok()
        }

        override suspend fun runAsAgentUser(script: String): Outcome<String> {
            agentChecks += script
            // First install has no agent user yet: the bridge refuses rather than crashing.
            return RuntimeProblem.NotInstalled.refused()
        }
    }

    @Test
    fun `B-LINUX-124 ARM64 OpenCode 1 host hands off pinned Node after root user bootstrap`() = runTest {
        val linux = Arm64V1Linux()
        val sent = assertNotNull(AgentHostJob.plan(scripts, linux))
        assertEquals(1, linux.agentChecks.size)
        val ids = sent.map { it.id }
        assertTrue(ids.indexOf("agent-user") < ids.indexOf("agent-node"))
        assertFalse("opencode" in ids, "an OpenCode 1 host's OpenCode is never touched")
        assertFalse("start" in ids)
        fun spec(id: String) = sent.single { it.id == id }
        assertFalse(spec("agent-user").agentUser, "the bootstrap runs as root")
        assertFalse(spec("agent-user").skipped)
        for (id in listOf("agent-node", "agent-paseo", "agent-claude")) {
            assertTrue(spec(id).agentUser, id)
            assertTrue(spec(id).script!!.startsWith(InstallPrelude.SETUP), id)
            assertFalse(spec(id).skipped, "a refused agent check leaves $id pending, not failed")
        }
        assertTrue(AgentNode.ARM64_SHA256 in spec("agent-node").script!!)
        for (id in listOf("linux", "essentials", "python")) assertTrue(spec(id).skipped, id)
        assertEquals("24.04", spec("linux").version)
        assertEquals(listOf("linux", "essentials", "python", "agent-user", "agent-node", "agent-paseo", "agent-claude"), ids)
    }

    @Test
    fun `B-LINUX-124 the checks go in one run per lane and a refused root lane stops the plan`() = runTest {
        val linux = Arm64V1Linux()
        AgentHostJob.plan(scripts, linux)
        assertEquals(1, linux.rootChecks.size)
        assertTrue("'agent-user'" in linux.rootChecks.single(), "the user bootstrap is checked as root")
        assertFalse("'agent-node'" in linux.rootChecks.single())
        assertTrue("'agent-node'" in linux.agentChecks.single() && "'agent-claude'" in linux.agentChecks.single())
        linux.rootRefused = true
        assertNull(AgentHostJob.plan(scripts, linux))
    }

    @Test
    fun `B-LINUX-124 the pinned Node for the agent user is checked by its own version`() {
        assertTrue(AgentNode.INSTALL.startsWith("set -eu\n[ \"\$(id -u)\" = 1000 ]"))
        assertTrue("x86_64|amd64) node_arch=x64; node_sha=${AgentNode.X64_SHA256} ;;" in AgentNode.INSTALL)
        assertTrue("aarch64|arm64) node_arch=arm64; node_sha=${AgentNode.ARM64_SHA256} ;;" in AgentNode.INSTALL)
        assertTrue(AgentNode.INSTALL.indexOf("oc_download") < AgentNode.INSTALL.indexOf("tar -xzf"))
        assertEquals(
            "set -eu\n[ \"\$(/home/oc/.local/node/bin/node --version)\" = 'v24.21.0' ]\nprintf '%s\\n' 'v24.21.0'\n",
            AgentNode.CHECK,
        )
    }

    @Test
    fun `B-LINUX-124 combined checks parse each result and drop a check that died`() {
        val script = InstallPrelude.combinedCheckScript(mapOf("a" to "echo 1\n", "b" to "false"))
        assertTrue(script.startsWith("printf '\\n::oc-check-begin %s\\n' 'a'\n(\necho 1\n) 2>/dev/null </dev/null\n"))
        val parsed =
            InstallPrelude.parseCombinedChecks("\n::oc-check-begin a\n1.0\n\n::oc-check-end a 0\n::oc-check-begin b\noops\n::oc-check-end b 1\n::oc-check-begin c\n")
        assertEquals(InstallPrelude.CheckResult(true, "1.0"), parsed["a"])
        assertEquals(InstallPrelude.CheckResult(false, "oops"), parsed["b"])
        assertFalse("c" in parsed)
    }
}
