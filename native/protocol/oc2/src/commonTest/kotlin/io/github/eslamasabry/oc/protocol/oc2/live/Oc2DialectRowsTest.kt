package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.ktor.http.HttpMethod
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Flutter test/api2_dialect_test.dart (pinned from both servers' /openapi.json). */
class Oc2DialectRowsTest {
    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun el(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun route(
        method: HttpMethod,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: JsonObject? = null,
    ): List<Any?> = Oc2Dialect.Stable.route(path, method, body, query).let { listOf(it.method, it.path, it.query, it.body, it.removed) }

    @Test
    fun `B-PROTO-013 requests that did not change pass through untouched`() {
        val body = obj("""{"text":"hi"}""")
        val query = mapOf<String, String?>("limit" to "50")
        for ((method, path) in
            listOf(
                HttpMethod.Get to "/session",
                HttpMethod.Post to "/session/ses_1/prompt",
                HttpMethod.Get to "/session/ses_1/message",
                HttpMethod.Delete to "/session/ses_1",
                HttpMethod.Get to "/mcp",
                HttpMethod.Get to "/session/ses_1/form/frm_1",
                HttpMethod.Delete to "/session/ses_1/inbox/inb_1",
            )) {
            val r = Oc2Dialect.Stable.route(path, method, body, query)
            assertEquals(method to path, r.method to r.path)
            assertSame(query, r.query)
            assertSame(body, r.body)
            assertNull(r.removed)
        }
        // The beta is never translated.
        val beta = Oc2Dialect.Beta.route("/session/s/rename", HttpMethod.Post, body, query)
        assertEquals(HttpMethod.Post to "/session/s/rename", beta.method to beta.path)
    }

    @Test
    fun `B-PROTO-013 2_1 added fields and routes and moved none`() {
        assertEquals(listOf(HttpMethod.Patch, "/session/ses_1", emptyMap<String, String?>(), obj("""{"archived":true}"""), null),
            route(HttpMethod.Patch, "/session/ses_1", body = obj("""{"archived":true}""")))
        assertEquals(listOf(HttpMethod.Get, "/session", mapOf("archived" to "true"), null, null),
            route(HttpMethod.Get, "/session", mapOf("archived" to "true")))
        assertEquals(listOf(HttpMethod.Get, "/session/ses_1/message", mapOf("type" to "idle"), null, null),
            route(HttpMethod.Get, "/session/ses_1/message", mapOf("type" to "idle")))
        assertEquals(listOf(HttpMethod.Patch, "/permission/saved/per_1", emptyMap<String, String?>(), obj("{}"), null),
            route(HttpMethod.Patch, "/permission/saved/per_1", body = obj("{}")))
    }

    @Test
    fun `B-PROTO-013 server facts forms and the current project moved`() {
        assertEquals("/info", route(HttpMethod.Get, "/health")[1])
        assertEquals("/info", route(HttpMethod.Get, "/server")[1])
        assertEquals("/form", route(HttpMethod.Get, "/form/request")[1])
        assertEquals("/location", route(HttpMethod.Get, "/project/current")[1])
    }

    @Test
    fun `B-PROTO-013 experimental routes keep their method and body`() {
        for ((method, path) in
            listOf(
                HttpMethod.Post to "/session/ses_1/skill",
                HttpMethod.Post to "/session/ses_1/wait",
                HttpMethod.Get to "/session/ses_1/export",
                HttpMethod.Get to "/session/ses_1/instructions/entries",
                HttpMethod.Put to "/session/ses_1/instructions/entries/note",
                HttpMethod.Delete to "/session/ses_1/instructions/entries/note",
                HttpMethod.Post to "/session/import",
                HttpMethod.Get to "/session/stats",
                HttpMethod.Post to "/generate",
                HttpMethod.Put to "/mcp/github",
                HttpMethod.Delete to "/mcp/github",
                HttpMethod.Post to "/mcp/github/connect",
                HttpMethod.Post to "/mcp/github/disconnect",
            )) {
            assertEquals(listOf(method, "/experimental$path", emptyMap<String, String?>(), null, null), route(method, path), path)
        }
    }

    @Test
    fun `B-PROTO-013 actions that became a method on the parent resource`() {
        assertEquals(listOf(HttpMethod.Patch, "/session/ses_1", emptyMap<String, String?>(), obj("""{"title":"T"}"""), null),
            route(HttpMethod.Post, "/session/ses_1/rename", body = obj("""{"title":"T"}""")))
        assertEquals(listOf(HttpMethod.Delete, "/session/ses_1/revert", emptyMap<String, String?>(), null, null),
            route(HttpMethod.Post, "/session/ses_1/revert/clear"))
        assertEquals(listOf(HttpMethod.Delete, "/session/ses_1/form/frm_1", emptyMap<String, String?>(), null, null),
            route(HttpMethod.Post, "/session/ses_1/form/frm_1/cancel"))
        for (delivery in listOf("steer", "queue")) {
            assertEquals(
                listOf(HttpMethod.Patch, "/session/ses_1/inbox/inb_1", emptyMap<String, String?>(), obj("""{"delivery":"$delivery"}"""), null),
                route(HttpMethod.Post, "/session/ses_1/inbox/inb_1/$delivery"),
            )
        }
    }

    @Test
    fun `B-PROTO-013 worktrees carry the project in the request, not the path`() {
        assertEquals(listOf(HttpMethod.Get, "/worktree", mapOf("projectID" to "prj_1"), null, null), route(HttpMethod.Get, "/worktree/prj_1"))
        assertEquals(
            listOf(HttpMethod.Post, "/worktree", emptyMap<String, String?>(), obj("""{"name":"fix","projectID":"prj_1"}"""), null),
            route(HttpMethod.Post, "/worktree/prj_1", body = obj("""{"name":"fix"}""")),
        )
        assertEquals(
            listOf(HttpMethod.Delete, "/worktree", emptyMap<String, String?>(), obj("""{"directory":"/w/fix","force":false,"projectID":"prj_1"}"""), null),
            route(HttpMethod.Delete, "/worktree/prj_1", body = obj("""{"directory":"/w/fix","force":false}""")),
        )
        assertEquals(
            listOf(HttpMethod.Post, "/worktree/refresh", emptyMap<String, String?>(), obj("""{"projectID":"prj_1"}"""), null),
            route(HttpMethod.Post, "/worktree/prj_1/refresh"),
        )
    }

    @Test
    fun `B-PROTO-013 renamed request fields`() {
        assertEquals(obj("""{"message":"ok","decision":"once"}"""),
            route(HttpMethod.Post, "/session/ses_1/permission/per_1/reply", body = obj("""{"reply":"once","message":"ok"}"""))[3])
        assertEquals(obj("""{"text":"x","name":"init"}"""),
            route(HttpMethod.Post, "/session/ses_1/command", body = obj("""{"command":"init","text":"x"}"""))[3])
        // The shell route keeps `command`: only slash commands were renamed.
        assertEquals(obj("""{"command":"ls"}"""), route(HttpMethod.Post, "/session/ses_1/shell", body = obj("""{"command":"ls"}"""))[3])
        assertEquals(mapOf<String, String?>("resume" to "true"), route(HttpMethod.Post, "/session/ses_1/interrupt", mapOf("continue" to "true"))[2])
        assertEquals(obj("""{"before":"msg_9"}"""),
            route(HttpMethod.Post, "/session/ses_1/fork", body = obj("""{"boundary":{"type":"before","messageID":"msg_9"}}"""))[3])
        assertEquals(obj("{}"), route(HttpMethod.Post, "/session/ses_1/fork", body = obj("""{"boundary":{"type":"through"}}"""))[3])
    }

    @Test
    fun `B-PROTO-013 what the stable line removed fails clearly instead of 404ing`() = runTest {
        assertEquals("managedWorkspaces", route(HttpMethod.Post, "/workspace")[4])
        assertEquals("managedWorkspaces", route(HttpMethod.Delete, "/workspace/wrk_1")[4])
        assertEquals("developmentServices", route(HttpMethod.Patch, "/shell/sh_1/timeout")[4])
        // Through the gateway: a clear Unavailable and no request at all.
        val server = RowServer { error("a removed operation must not reach the network") }
        assertEquals(
            Failure.Unavailable(FeatureId("developmentServices")),
            assertIs<Outcome.Failed>(server.gateway.setManagedShellTimeout("sh_1", 5.minutes)).failure,
        )
        assertTrue(server.seen.isEmpty())
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-013 responses are reshaped into what the beta call sites read`() = runTest {
        val health = Oc2Dialect.Stable.response(HttpMethod.Get, "/health", el("""{"version":"2.0.10","pid":42,"urls":["http://127.0.0.1:4096"]}"""))
        assertEquals(el("""{"healthy":true,"version":"2.0.10","pid":42,"urls":["http://127.0.0.1:4096"]}"""), health)
        assertEquals(
            el("""{"data":{"id":"prj_1","directory":"/w","canonical":"/w"}}"""),
            Oc2Dialect.Stable.response(HttpMethod.Get, "/project/current", el("""{"directory":"/w","project":{"id":"prj_1","directory":"/w","canonical":"/w"}}""")),
        )
        assertEquals(
            el("""{"data":{"status":"pending"}}"""),
            Oc2Dialect.Stable.response(HttpMethod.Get, "/session/s/form/f/state", el("""{"data":{"id":"f","state":{"status":"pending"}}}""")),
        )
        val untouched = el("""{"data":[]}""")
        assertSame(untouched, Oc2Dialect.Stable.response(HttpMethod.Get, "/session", untouched))
        // Wired: a stable server's /info answers the beta-shaped health read with its pid.
        val server = RowServer { json("""{"version":"2.0.10","pid":42}""") }
        assertEquals("42", assertIs<Outcome.Ok<String?>>(server.gateway.managedShellServerIdentity()).value)
        assertEquals("/api/info", server.seen.single().path)
        server.gateway.close()
    }
}
