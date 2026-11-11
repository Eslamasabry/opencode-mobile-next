@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The phone helper's gateway is pinned to the folder that holds the projects and lists every project under it. */
class PaseoSubfoldersTest {
    private fun daemon(): FakeDaemon =
        FakeDaemon().seedDefaults().also { d ->
            d.handlers["fetch_agents_request"] = {
                "fetch_agents_response" to jsonOf(
                    "entries" to listOf(
                        mapOf("agent" to agentJson("root")),
                        mapOf("agent" to agentJson("project", cwd = "$DIR/project-one")),
                        mapOf("agent" to agentJson("deep", cwd = "$DIR/project-two/api")),
                        mapOf("agent" to agentJson("sibling", cwd = "$DIR-other")),
                        mapOf("agent" to agentJson("escape", cwd = "$DIR/../secret")),
                        mapOf("agent" to agentJson("archived", cwd = "$DIR/project-one", archivedAt = "2026-09-19T04:00:00.000Z")),
                    ),
                    "pageInfo" to mapOf("hasMore" to false),
                )
            }
        }

    @Test
    fun `a gateway that includes subfolders lists the conversations of every project under its folder`() = runTest {
        val h = GatewayHarness(this, daemon())
        h.gateway.includeSubfolders = true
        val ids = (h.gateway.allSessions() as Outcome.Ok).value.map { it.id.raw }
        assertEquals(listOf("root", "project", "deep"), ids)
    }

    @Test
    fun `a gateway without it keeps to its exact folder`() = runTest {
        val h = GatewayHarness(this, daemon())
        val ids = (h.gateway.allSessions() as Outcome.Ok).value.map { it.id.raw }
        assertEquals(listOf("root"), ids)
    }
}
