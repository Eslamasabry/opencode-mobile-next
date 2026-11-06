package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.WorkspaceProject
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * E03-S02: the project and share actions an OpenCode 2 server is offered for. Flutter
 * lib/api2/gateway_operations/ops_projects.dart (renameProject) and ops_sessions.dart (share/unshare).
 */
class Oc2ProjectShareRowsTest {
    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `SCREENS-NAV-69 rename patches the project by id and reads the renamed project back`() = runTest {
        val server = RowServer {
            json("""{"data":{"id":"prj 1","name":"Shop","directory":"/root/shop","vcs":"git","time":{"created":5,"updated":9}}}""")
        }
        val renamed = server.gateway.renameProject("prj 1", "/root/shop", "Shop")
        assertEquals(
            Outcome.Ok(WorkspaceProject("prj 1", "Shop", "/root/shop", persistentListOf(), 9, true)),
            renamed,
        )
        val patch = server.seen.single()
        assertEquals("PATCH", patch.method)
        assertEquals("/api/project/prj%201", patch.path)
        assertEquals(obj("""{"name":"Shop"}"""), patch.json)
        server.gateway.close()
    }

    @Test
    fun `SCREENS-NAV-69 rename without a project in the reply keeps the sent name and the folder`() = runTest {
        for (reply in listOf("""{}""", """{"data":null}""", "")) {
            val server = RowServer { if (reply.isEmpty()) respond("", HttpStatusCode.NoContent) else json(reply) }
            val renamed = assertIs<Outcome.Ok<WorkspaceProject>>(server.gateway.renameProject("prj_1", "/root/shop", "Shop")).value
            assertEquals("prj_1", renamed.id)
            assertEquals("Shop", renamed.name)
            assertEquals("/root/shop", renamed.directory)
            assertEquals(1_000L, renamed.updatedAtMs, reply)
            server.gateway.close()
        }
    }

    @Test
    fun `SCREENS-NAV-69 a blank name is sent so the server clears the custom name`() = runTest {
        val server = RowServer { json("""{}""") }
        val renamed = assertIs<Outcome.Ok<WorkspaceProject>>(server.gateway.renameProject("prj_1", "/root/shop", "")).value
        assertEquals(obj("""{"name":""}"""), server.seen.single().json)
        // The row falls back to the folder name, as listProjects does.
        assertEquals("shop", renamed.name)
        server.gateway.close()
    }

    @Test
    fun `SCREENS-NAV-69 a refused rename is a failure and a blank id sends nothing`() = runTest {
        val refused = RowServer { json("""{"error":"nope"}""", HttpStatusCode.InternalServerError) }
        assertIs<Outcome.Failed>(refused.gateway.renameProject("prj_1", "/root/shop", "Shop"))
        refused.gateway.close()

        val blank = RowServer { error("nothing is sent for a blank id") }
        assertIs<Failure.NotFound>(assertIs<Outcome.Failed>(blank.gateway.renameProject(" ", "/root/shop", "Shop")).failure)
        assertTrue(blank.seen.isEmpty())
        blank.gateway.close()
    }

    @Test
    fun `SCREENS-NAV-69 rename is offered because the gateway advertises project management`() {
        assertTrue(Oc2Gateway.Capabilities.projectManagement)
    }

    @Test
    fun `B-PROTO-016 share and unshare are Unavailable on OpenCode 2 and send nothing`() = runTest {
        val server = RowServer { error("sharing sends no request") }
        assertFalse(server.gateway.capabilities.value.sessionShare)
        val unavailable = Failure.Unavailable(FeatureId("sessionShare"))
        assertEquals(unavailable, assertIs<Outcome.Failed>(server.gateway.shareSession(SessionId("ses_a"))).failure)
        assertEquals(unavailable, assertIs<Outcome.Failed>(server.gateway.unshareSession(SessionId("ses_a"))).failure)
        assertTrue(server.seen.isEmpty())
        server.gateway.close()
    }
}
