package io.github.eslamasabry.oc.protocol.oc2.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of `test/api2_session_permission_policy_test.dart` (row B-PROTO-032). */
class Oc2PermissionPolicyTest {
    private val ask = rule("*", "ask")

    private fun rule(action: String, effect: String, resource: String = "*") = buildJsonObject {
        put("action", action)
        put("resource", resource)
        put("effect", effect)
    }

    private fun session(rules: List<JsonElement> = emptyList(), agent: String = "build", metadata: JsonObject? = null): Oc2PermissionPolicy =
        assertNotNull(
            Oc2PermissionPolicy.fromSession(
                buildJsonObject {
                    put("data", buildJsonObject {
                        put("id", "s")
                        put("agent", agent)
                        put("permissions", JsonArray(rules))
                        put("metadata", metadata ?: JsonNull)
                    })
                },
                "s",
            ),
        )

    private fun agent(id: String, rules: List<JsonElement>) = buildJsonObject {
        put("id", id)
        put("permissions", JsonArray(rules))
    }

    private fun JsonObject.permissions(): List<JsonElement> = this["permissions"] as JsonArray

    private fun JsonObject.meta(): JsonObject = this["metadata"] as JsonObject

    @Test
    fun `B-PROTO-032 a fresh session uses the server's resolved default agent first`() {
        val fresh = assertNotNull(Oc2PermissionPolicy.fromSession(Json.parseToJsonElement("""{"data":{"id":"s"}}"""), "s"))
        val resolved = assertNotNull(
            Oc2PermissionPolicy.defaultAgent(
                Json.parseToJsonElement(
                    """{"data":[{"id":"restricted","mode":"primary","hidden":false,"permissions":[${rule("shell", "deny")}]},
                       {"id":"build","mode":"primary","hidden":false,"permissions":[${rule("*", "allow")}]}]}""",
                ),
            ),
        )
        assertEquals(listOf(ask, rule("shell", "deny")), fresh.updateForAgent(resolved, serverRuns = false)!!.permissions())
        assertNull(Oc2PermissionPolicy.defaultAgent(Json.parseToJsonElement("""{"data":[{"id":"hidden","mode":"subagent","hidden":true}]}""")))
    }

    @Test
    fun `B-PROTO-032 an automatic session keeps its own rules including denials, and switching back asks again`() {
        val update = session(listOf(rule("shell", "deny"))).updateForAgent(agent("build", listOf(rule("*", "allow"))), serverRuns = true)!!
        assertEquals(listOf(rule("shell", "deny")), update.permissions())
        val back = session(update.permissions(), metadata = update.meta()).updateForAgent(agent("build", listOf(rule("*", "allow"))), serverRuns = false)!!
        assertEquals(listOf(ask, ask, rule("shell", "deny")), back.permissions())
    }

    @Test
    fun `B-PROTO-032 read-back metadata key order does not affect confirmation`() {
        val update = session().updateForAgent(agent("build", emptyList()), serverRuns = false)!!
        val applied = update.meta()[Oc2PermissionPolicy.METADATA_KEY]!!.jsonObject
        val reordered = session(
            update.permissions(),
            metadata = buildJsonObject {
                put(Oc2PermissionPolicy.METADATA_KEY, buildJsonObject {
                    put("applied", applied["applied"]!!)
                    put("original", applied["original"]!!)
                    put("version", 1)
                })
            },
        )
        assertTrue(reordered.confirms(update))
    }

    @Test
    fun `B-PROTO-032 an oversized composed policy is refused before writing`() {
        assertNull(session(List(500) { rule("edit", "deny") }).updateForAgent(agent("build", List(500) { rule("shell", "deny") }), serverRuns = false))
    }

    @Test
    fun `B-PROTO-032 a default agent allowance becomes a request, shell and edit included`() {
        assertEquals(listOf(ask, ask), session().updateForAgent(agent("build", listOf(rule("*", "allow"))), serverRuns = false)!!.permissions())
    }

    @Test
    fun `B-PROTO-032 agent denials and ordered read-only exceptions keep their meaning`() {
        val update = session().updateForAgent(
            agent("build", listOf(rule("*", "deny"), rule("read", "allow"), rule("glob", "allow"), rule("edit", "deny", "/etc/*"))),
            serverRuns = false,
        )!!
        assertEquals(listOf(ask, rule("*", "deny"), rule("read", "ask"), rule("glob", "ask"), rule("edit", "deny", "/etc/*")), update.permissions())
    }

    @Test
    fun `B-PROTO-032 original session rules keep their order and deny exceptions`() {
        val original = listOf(rule("*", "deny"), rule("read", "allow"))
        val update = session(original).updateForAgent(agent("build", listOf(rule("*", "allow"))), serverRuns = false)!!
        assertEquals(listOf(ask, ask, rule("*", "deny"), rule("read", "ask")), update.permissions())
        assertEquals(JsonArray(original), update.meta()[Oc2PermissionPolicy.METADATA_KEY]!!.jsonObject["original"])
    }

    @Test
    fun `B-PROTO-032 changing agent uses the original session rules, not the prior agent's allowances`() {
        val first = session(metadata = buildJsonObject { put("owner", "kept") })
            .updateForAgent(agent("build", listOf(rule("*", "allow"))), serverRuns = false)!!
        val planRules = listOf(rule("*", "allow"), rule("edit", "deny"))
        val next = session(first.permissions(), agent = "plan", metadata = first.meta()).updateForAgent(agent("plan", planRules), serverRuns = false)!!
        assertEquals(listOf(ask, ask, rule("edit", "deny")), next.permissions())
        assertEquals(JsonPrimitive("kept"), next.meta()["owner"])
        val repeated = session(next.permissions(), agent = "plan", metadata = next.meta())
        assertTrue(repeated.confirms(repeated.updateForAgent(agent("plan", planRules), serverRuns = false)!!))
    }

    @Test
    fun `B-PROTO-032 external session edits replace the saved original`() {
        val first = session().updateForAgent(agent("build", listOf(rule("*", "allow"))), serverRuns = false)!!
        val update = session(listOf(rule("shell", "deny")), metadata = first.meta())
            .updateForAgent(agent("build", listOf(rule("*", "allow"))), serverRuns = false)!!
        assertEquals(listOf(ask, ask, rule("shell", "deny")), update.permissions())
    }

    @Test
    fun `B-PROTO-032 malformed rules fail closed`() {
        for (invalid in listOf("null", "{}", """[${rule("shell", "unknown")}]""", "[null]", """[{"action":"shell","effect":"deny"}]""")) {
            val response = Json.parseToJsonElement("""{"data":{"id":"s","agent":"build","permissions":$invalid}}""")
            assertNull(Oc2PermissionPolicy.fromSession(response, "s"), invalid)
        }
    }

    @Test
    fun `B-PROTO-032 malformed provenance and a wrong agent fail closed`() {
        val badProvenance = session(metadata = buildJsonObject { put(Oc2PermissionPolicy.METADATA_KEY, buildJsonObject { put("version", 2) }) })
        assertNull(badProvenance.updateForAgent(agent("build", emptyList()), serverRuns = false))
        assertNull(session().updateForAgent(agent("other", emptyList()), serverRuns = false))
    }
}
