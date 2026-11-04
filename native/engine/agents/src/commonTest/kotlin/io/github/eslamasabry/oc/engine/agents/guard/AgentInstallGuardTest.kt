package io.github.eslamasabry.oc.engine.agents.guard

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

/** Port of test/agent_install_guard_test.dart. */
class AgentInstallGuardTest {
    private fun byId(id: String) = AgentCatalog.builtIn.byId(AgentId(id))!!

    @Test
    fun `B-LINUX-001 normal and negative QA floor preserve every pinned estimate`() {
        for (agent in AgentCatalog.builtIn.agents.filter { it.recipe != null }) {
            val sizes = agent.recipe!!.artifacts.values.mapNotNull { it.downloadBytes }.sorted()
            val normal = sizes.lastOrNull()
            assertEquals(normal, AgentInstallGuard(minimumFreeBytes = 0).downloadBytesFor(agent), agent.id.raw)
            assertEquals(normal, AgentInstallGuard(minimumFreeBytes = -1).downloadBytesFor(agent), agent.id.raw)
        }
    }

    @Test
    fun `B-LINUX-001 raised QA floor applies to the targets and excludes real Claude`() {
        val guard = AgentInstallGuard(minimumFreeBytes = 8589934592L)
        for (id in AgentInstallGuard.agentIds) {
            assertEquals(8589934592L, requiredSetupFreeBytes(guard.downloadBytesFor(byId(id))!!), id)
        }
        val claude = byId("claude")
        assertEquals(AgentInstallGuard(minimumFreeBytes = 0).downloadBytesFor(claude), guard.downloadBytesFor(claude))
    }

    @Test
    fun `B-LINUX-001 small QA floor never reduces catalog requirement and odd floor rounds up`() {
        val agent = byId("omp-acp")
        val pin = AgentInstallGuard(minimumFreeBytes = 0).downloadBytesFor(agent)!!
        assertEquals(pin, AgentInstallGuard(minimumFreeBytes = 1).downloadBytesFor(agent))
        assertEquals(
            8589934594L,
            requiredSetupFreeBytes(AgentInstallGuard(minimumFreeBytes = 8589934593L).downloadBytesFor(agent)!!),
        )
    }

    @Test
    fun `B-LINUX-001 maximum QA floor saturates native admission rather than wrapping`() {
        val bytes = AgentInstallGuard(minimumFreeBytes = Long.MAX_VALUE).downloadBytesFor(byId("fx"))!!
        assertEquals(Long.MAX_VALUE, requiredSetupFreeBytes(bytes))
    }
}
