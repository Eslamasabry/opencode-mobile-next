package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Fault 1 of the Paseo 0.15.3 device pass: Archive on a Paseo row showed "Archived", then about 4 s later
 * "Couldn't archive the conversation." and the row came back; the daemon's log shows no archive request at all.
 * The home list reaches the operations through `operationsAt` (the app's `ConnectionGraph.operations()`), and that
 * object had no archive. Frames: [Paseo0153Frames] (a real 0.15.3 daemon, 2026-11-12).
 */
class Paseo0153ArchiveTest {
    private val agentId: String
        get() = Paseo0153Frames.fetchedAgent.obj("agent")!!.str("id")!!

    private fun TestScope.daemon0153(): GatewayHarness {
        val daemon =
            FakeDaemon().apply {
                version = "0.15.3"
                handlers["fetch_agents_request"] = {
                    "fetch_agents_response" to
                        jsonOf(
                            "entries" to listOf(mapOf("agent" to Paseo0153Frames.fetchedAgent.obj("agent"))),
                            "pageInfo" to mapOf("hasMore" to false),
                        )
                }
                handlers["get_providers_snapshot_request"] = {
                    "get_providers_snapshot_response" to Paseo0153Frames.providersSnapshot
                }
                handlers["fetch_agent_request"] = { "fetch_agent_response" to Paseo0153Frames.fetchedAgent }
                // 0.15.3 answers an archive with the push it causes, repeating the requestId.
                handlers["archive_agent_request"] = { "agent_archived" to Paseo0153Frames.agentArchived }
            }
        return GatewayHarness(this, daemon, directory = Paseo0153Frames.PASEO_0153_DIR)
    }

    @Test
    fun `fault 1 the Conversations list's Archive on a Paseo row sends archive_agent_request and succeeds on the daemon's agent_archived`() =
        runTest {
            val h = daemon0153()
            // The home list read the row first.
            assertIs<Outcome.Ok<*>>(h.gateway.sessionPage(null, 50))
            // What the app's Conversations list holds for this server: the operations at its current folder.
            val ops = h.gateway.operationsAt(h.gateway.location.value)
            val done = ops.archiveSession(SessionId(agentId))
            // Before the fix: Failed(Unavailable sessionArchive), and no frame left the phone.
            assertEquals(Outcome.Ok(Unit), done)
            val wire = h.daemon.of("archive_agent_request").single()
            assertEquals(agentId, wire.str("agentId"))
        }

    @Test
    fun `fault 1 a row the list has not read yet is fetched, then archived`() =
        runTest {
            val h = daemon0153()
            val ops = h.gateway.operationsAt(h.gateway.location.value)
            assertEquals(Outcome.Ok(Unit), ops.archiveSession(SessionId(agentId)))
            assertEquals(1, h.daemon.of("fetch_agent_request").size)
            assertEquals(agentId, h.daemon.of("archive_agent_request").single().str("agentId"))
        }

    @Test
    fun `fault 1 a refused archive is a failure, so the row comes back with its reason`() =
        runTest {
            val h = daemon0153()
            h.daemon.handlers["archive_agent_request"] = { "rpc_error" to jsonOf("error" to "agent is busy") }
            val ops = h.gateway.operationsAt(h.gateway.location.value)
            assertIs<Outcome.Failed>(ops.archiveSession(SessionId(agentId)))
            assertEquals(1, h.daemon.of("archive_agent_request").size)
        }

    @Test
    fun `B-293 the operations at a folder read the daemon's model list and defaults, as the gateway's own do`() =
        runTest {
            val h = daemon0153()
            val ops = h.gateway.operationsAt(h.gateway.location.value)
            // Before: Failed(Unavailable serverCatalog) from the interface default, nothing asked of the daemon.
            val located = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.CatalogSnapshot>>(ops.catalog()).value
            val own = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.CatalogSnapshot>>(
                PaseoOperations(h.gateway).catalog()
            ).value
            assertEquals(own, located)
            assertEquals(listOf("opencode"), located.providers.map { it.id })
            assertIs<Outcome.Ok<*>>(ops.chatDefaults())
        }
}
