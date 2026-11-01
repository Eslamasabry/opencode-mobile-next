package io.github.eslamasabry.oc.protocol.paseo.requests

import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.protocol.paseo.PaseoEndpoint
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ACP agents through Paseo: ported from test/paseo_acp_pilot_test.dart (catalog, continuation and permission option
 * parts; picker/prompt admission and provider caching are gateway tests) and test/paseo_provider_sign_in_test.dart.
 */
class PaseoHostAgentsTest {
    private val cwd = "/work/acp-pilot"
    private val fakeSecret = "synthetic-provider-credential-do-not-display"
    private val host = FakeRequestHost().apply {
        scope = cwd
        providers["existing"] = "gemini"
    }
    private val book = PaseoRequestBook(host)

    private fun provider(id: String, status: String = "ready", enabled: Boolean = true, source: String = "builtin", error: String? = null) =
        JsonObject(
            jsonOf(
                "provider" to id, "status" to status, "enabled" to enabled, "source" to source, "label" to "$id host label",
                "models" to listOf(mapOf("provider" to id, "id" to "$id-model", "label" to "$id model", "isDefault" to true)),
                "error" to error,
            ).filterValues { it !is kotlinx.serialization.json.JsonNull },
        )

    private fun agent(providerId: String, persisted: Boolean = true, loaded: Boolean = false) =
        JsonObject(
            jsonOf(
                "id" to "existing", "provider" to providerId, "cwd" to cwd, "status" to "idle",
                // Real wire fields with hardcoded ACPAdapter defaults: not resume proof.
                "capabilities" to mapOf("supportsSessionPersistence" to true, "supportsSessionListing" to true),
                "pendingPermissions" to emptyList<Any>(),
                "persistence" to if (persisted) mapOf("provider" to providerId, "sessionId" to "native-existing") else null,
            ).let { if (loaded) it.with("activeTurn" to null) else it },
        )

    private fun option(id: String, kind: String) = mapOf("optionId" to id, "name" to id, "kind" to kind)

    private fun onceAndStanding() = listOf(
        option("allow-this-call", "allow_once"),
        option("allow-future-calls", "allow_always"),
        option("reject-this-call", "reject_once"),
        option("reject-future-calls", "reject_always"),
    )

    private fun permission(options: List<Map<String, String>>, id: String = "approval"): JsonObject =
        jsonOf(
            "id" to id, "provider" to "gemini", "name" to "Write", "kind" to "tool", "title" to "Write project file",
            "detail" to mapOf("type" to "write", "filePath" to "$cwd/result.txt", "content" to "hello"),
            "actions" to options.map { mapOf("id" to it["optionId"], "label" to it["name"], "behavior" to if (it["kind"]!!.startsWith("allow")) "allow" else "deny") },
            "metadata" to mapOf("toolCallId" to "tool-call", "options" to options),
        )

    private fun ask(options: List<Map<String, String>>, id: String = "approval") = book.onPermissionRequest("existing", permission(options, id))

    // ---- catalog (B-AGENT-142, 143, 175)

    @Test
    fun `B-AGENT-142 ready agents are selectable without invented restoration proof`() {
        val catalog = paseoHostAgentCatalog(listOf(provider("claude"), provider("gemini"), provider("omp"), provider("fx")))
        assertEquals(4, catalog.selectable.size)
        for (id in listOf("gemini", "omp", "fx")) {
            val row = catalog.providers.single { it.id == id }
            assertTrue(row.selectable)
            assertNull(row.hiddenReason)
            assertEquals(HostAgentResumeSupport.Unknown, row.resumeSupport)
            assertFalse(row.resumeVerified)
        }
    }

    @Test
    fun `B-AGENT-142 known host auth failure is a safe sign-in state`() {
        val row = paseoHostAgentCatalog(listOf(provider("gemini", status = "error", error = "AuthRequired"))).providers.single()
        assertEquals(HostAgentAvailability.NeedsHostSignIn, row.availability)
        assertNull(row.hiddenReason)
        assertEquals(HostAgentLoginState.NeedsHostSignIn, row.loginState)
        assertFalse(row.selectable)
        // An unknown error is hidden as unavailable; its text appears nowhere in the row.
        val secret = paseoHostAgentCatalog(listOf(provider("gemini", status = "error", error = fakeSecret))).providers.single()
        assertEquals(HostAgentHiddenReason.Unavailable, secret.hiddenReason)
        assertFalse(fakeSecret in secret.toString())
    }

    @Test
    fun `B-AGENT-142 disabled, loading, signed-out or missing providers cannot be picked or started`() {
        val catalog = paseoHostAgentCatalog(listOf(provider("gemini", enabled = false), provider("fx", status = "loading")))
        assertTrue(catalog.selectable.isEmpty())
        assertEquals(HostAgentHiddenReason.Disabled, catalog.providers.single { it.id == "gemini" }.hiddenReason)
        assertEquals(HostAgentAvailability.Checking, catalog.providers.single { it.id == "fx" }.availability)
        for (unavailable in listOf(
            provider("gemini", enabled = false),
            provider("gemini", status = "error", error = "not installed"),
            provider("gemini", error = "not installed"),
            provider("gemini", error = "AuthRequired"),
            provider("gemini", status = "loading"),
        )) {
            assertFalse(paseoProviderCanStart(unavailable), unavailable.toString())
        }
        assertTrue(paseoProviderCanStart(provider("gemini")))
    }

    @Test
    fun `B-AGENT-142 a custom host agent is selectable with no resume proof`() {
        val row = paseoHostAgentCatalog(listOf(provider("future-agent", source = "custom"))).providers.single()
        assertTrue(row.selectable)
        assertFalse(row.resumeVerified)
        assertEquals("Other host agent", row.displayName)
    }

    @Test
    fun `B-AGENT-143 rows use catalog names rather than host labels and duplicates keep the first`() {
        val catalog = paseoHostAgentCatalog(listOf(provider("qwen"), provider("goose"), provider("qwen", status = "loading"), provider("Bad Id")))
        assertEquals(listOf("Qwen Code", "Goose"), catalog.selectable.map { it.displayName })
        assertEquals(2, catalog.providers.size)
        assertEquals("Gemini CLI", paseoHostAgentName("gemini"))
        assertEquals("GitHub Copilot", paseoHostAgentName("copilot"))
        assertEquals("omp", paseoHostAgentName("omp"))
        assertEquals("Claude Code", paseoAgentProductName("claude"))
        assertNull(paseoAgentProductName("gemini"))
    }

    @Test
    fun `B-AGENT-143 undeclared capability fields cannot fabricate resume proof`() {
        val catalog = paseoHostAgentCatalog(
            listOf(
                provider("gemini").with("capabilities" to mapOf("loadSession" to true, "sessionCapabilities" to mapOf("list" to emptyMap<String, Any>())), "loadSession" to true, "type" to "acp"),
                provider("custom-agent", source = "custom"),
            ),
        )
        assertEquals(2, catalog.selectable.size)
        assertTrue(catalog.providers.none { it.resumeVerified })
    }

    @Test
    fun `B-AGENT-143 an existing Copilot route stays and a custom override cannot reuse an existing identity`() {
        assertTrue(paseoProviderCanStart(provider("copilot")))
        assertFalse(paseoProviderCanStart(provider("claude", source = "custom")))
        assertTrue(isExistingPaseoProvider("copilot"))
        assertFalse(isPaseoProviderId("Claude"))
        assertFalse(isPaseoProviderId(null))
    }

    @Test
    fun `B-AGENT-143 catalog rows refuse contradictory states`() {
        assertFailsWith<IllegalArgumentException> {
            HostAgentProvider("x", "x", HostAgentAvailability.Hidden, null, HostAgentLoginState.Unknown, HostAgentResumeSupport.Unknown)
        }
        assertFailsWith<IllegalArgumentException> {
            HostAgentProvider("x", "x", HostAgentAvailability.Hidden, HostAgentHiddenReason.ResumeUnverified, HostAgentLoginState.Unknown, HostAgentResumeSupport.Unknown)
        }
        assertFailsWith<IllegalArgumentException> {
            HostAgentProvider("x", "x", HostAgentAvailability.Ready, null, HostAgentLoginState.NeedsHostSignIn, HostAgentResumeSupport.Unknown)
        }
    }

    @Test
    fun `B-AGENT-175 the usual agent CLI phrasings read as signed out and other failures do not`() {
        fun entry(error: String?) = provider("codex", status = "error", error = error)
        for (message in listOf(
            "Failed to fetch models for fx: fx needs access to Vercel AI Gateway. Run fx login to sign in, fx setup to use an API key, or set AI_GATEWAY_API_KEY.",
            "AuthRequired: run codex login",
            "Authentication required",
            "Not logged in. Please run /login",
            "Please set an API key or sign in with Google",
            "Request failed: 401 Unauthorized",
            "Use qwen auth to configure credentials",
        )) {
            assertTrue(paseoProviderNeedsSignIn(entry(message)), message)
        }
        val fx = paseoHostAgentCatalog(listOf(provider("fx", status = "error", error = "fx needs access to Vercel AI Gateway. Run fx login to sign in")))
        assertEquals(HostAgentAvailability.NeedsHostSignIn, fx.providers.single().availability)
        for (message in listOf("spawn ENOENT", "Timed out resolving gemini catalogue key", "Model list unavailable", null)) {
            assertFalse(paseoProviderNeedsSignIn(provider("gemini", status = "error", error = message)), "$message")
        }
    }

    // ---- continuation (B-AGENT-144)

    @Test
    fun `B-AGENT-144 restart state never promises restoration and a missing handle is honest`() {
        val unverified = paseoHostAgentContinuation("existing", agent("gemini"), live = false)
        assertEquals("gemini", unverified.providerId)
        assertEquals(HostAgentContinuationState.ResumeUnverified, unverified.state)
        assertTrue(unverified.requiresNewChat)
        assertEquals(HostAgentContinuationState.MissingHandle, paseoHostAgentContinuation("existing", agent("gemini", persisted = false), false).state)
        // A native runtime without its handle also needs the explicit new-chat action.
        assertTrue(paseoHostAgentContinuation("existing", agent("claude", persisted = false), false).requiresNewChat)
        assertEquals(HostAgentContinuationState.ExistingRoute, paseoHostAgentContinuation("existing", agent("claude"), false).state)
        // A chat live on this connection, or held by the daemon, continues without proof.
        assertEquals(HostAgentContinuationState.LiveSession, paseoHostAgentContinuation("existing", agent("gemini"), live = true).state)
        assertEquals(HostAgentContinuationState.LiveSession, paseoHostAgentContinuation("existing", agent("gemini", loaded = true), false).state)
        assertFalse(paseoHostAgentContinuation("existing", agent("gemini"), live = true).requiresNewChat)
        // A foreign handle is not this runtime's.
        val foreign = agent("gemini").with("persistence" to mapOf("provider" to "claude", "sessionId" to "x"))
        assertEquals(HostAgentContinuationState.MissingHandle, paseoHostAgentContinuation("existing", foreign, false).state)
        assertFailsWith<PaseoException> { paseoHostAgentContinuation("existing", agent("gemini").without("provider"), false) }
    }

    // ---- ACP permissions (B-SEC-109, B-AGENT-145)

    @Test
    fun `B-SEC-109 permission choices expose only offered one-call actions`() {
        ask(onceAndStanding())
        val pending = book.pendingHostPermissions().single()
        assertEquals("approval", pending.requestId)
        assertEquals("existing", pending.sessionId)
        assertEquals(listOf("allow-this-call", "reject-this-call"), pending.choices.map { it.actionId })
        assertEquals(listOf(HostAgentPermissionBehavior.AllowOnce, HostAgentPermissionBehavior.RejectOnce), pending.choices.map { it.behavior })
    }

    @Test
    fun `B-SEC-109 allow once echoes its exact offered id and stores no standing approval`() = runTest {
        ask(onceAndStanding())
        book.replyHostPermission("approval", "allow-this-call")
        assertEquals(jsonOf("behavior" to "allow", "selectedActionId" to "allow-this-call"), host.lastResponse())
        assertTrue(book.pendingHostPermissions().isEmpty())
        assertFailsWith<PaseoException> { book.replyHostPermission("approval", "allow-this-call") }
        assertEquals(1, host.responses().size)
        // The plain Once reply maps to the single offered allow-once action; Always is never possible.
        ask(onceAndStanding(), id = "second")
        assertFailsWith<PaseoException> { book.replyPermission("second", PaseoPermissionAnswer.Always) }
        book.replyPermission("second", PaseoPermissionAnswer.Once)
        assertEquals(jsonOf("behavior" to "allow", "selectedActionId" to "allow-this-call"), host.lastResponse())
    }

    @Test
    fun `B-SEC-109 default denial selects the offered reject-once id`() = runTest {
        ask(onceAndStanding())
        book.replyHostPermission("approval", null)
        assertEquals(jsonOf("behavior" to "deny", "selectedActionId" to "reject-this-call"), host.lastResponse())
    }

    @Test
    fun `B-SEC-109 without reject once denial interrupts instead of falling back to always`() = runTest {
        host.answers["cancel_agent_request"] = { jsonOf("agentId" to "existing", "agent" to agent("gemini")) }
        ask(listOf(option("allow-future-calls", "allow_always"), option("reject-future-calls", "reject_always")))
        book.replyHostPermission("approval", null)
        assertTrue(host.responses().isEmpty())
        assertEquals("existing", host.requests.single().let { it["agentId"].toString().trim('"') })
        assertTrue(book.pendingHostPermissions().isEmpty())
    }

    @Test
    fun `B-AGENT-145 an idle cancel acknowledgement cannot hide an unanswered permission`() = runTest {
        val offered = listOf(option("deny-future", "reject_always"))
        host.answers["cancel_agent_request"] = {
            jsonOf("agentId" to "existing", "agent" to agent("gemini").with("pendingPermissions" to JsonArray(listOf(permission(offered)))))
        }
        ask(offered)
        assertEquals(PaseoFailureKind.Unavailable, assertFailsWith<PaseoException> { book.replyHostPermission("approval", null) }.kind)
        assertEquals("approval", book.pendingHostPermissions().single().requestId)
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-AGENT-145 an old cancel acknowledgement cannot retire a new project's card`() = runTest {
        val offered = listOf(option("deny-future", "reject_always"))
        ask(offered)
        val answer = CompletableDeferred<JsonObject>()
        host.held = answer
        val cancel = async { assertFailsWith<PaseoException> { book.replyHostPermission("approval", null) } }
        runCurrent()
        // The project changes twice, and the same request id is asked again in the new project.
        book.clear()
        host.locationEpoch++
        book.clear()
        host.locationEpoch++
        ask(offered)
        answer.complete(jsonOf("agentId" to "existing", "agent" to agent("gemini")))
        assertEquals(PaseoFailureKind.ScopeMismatch, cancel.await().kind)
        assertEquals("approval", book.pendingHostPermissions().single().requestId)
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-SEC-109 contradictory action behaviour cannot grant an ACP permission`() = runTest {
        val request = permission(onceAndStanding()).edit("actions", 0, "behavior", value = "deny")
        book.onPermissionRequest("existing", request)
        assertTrue(book.pendingHostPermissions().single().choices.isEmpty())
        assertFailsWith<PaseoException> { book.replyHostPermission("approval", "allow-this-call") }
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-SEC-109 an ACP request cannot claim a native provider to bypass action mapping`() = runTest {
        val request = permission(onceAndStanding()).with(
            "provider" to "claude",
            "metadata" to emptyMap<String, Any>(),
            "suggestions" to listOf(mapOf("type" to "setMode", "mode" to "acceptEdits")),
        )
        book.onPermissionRequest("existing", request)
        assertTrue(book.pendingHostPermissions().single().choices.isEmpty())
        assertTrue(book.pendingPermissions().single().always.isEmpty())
        assertFalse(book.pendingPermissions().single().canAlwaysAllow)
        assertFailsWith<PaseoException> { book.replyPermission("approval", PaseoPermissionAnswer.Once) }
        assertFailsWith<PaseoException> { book.replyPermission("approval", PaseoPermissionAnswer.Always) }
        assertTrue(host.responses().isEmpty())
        assertEquals("approval", book.pendingPermissions().single().id.raw)
    }

    @Test
    fun `B-SEC-109 a native session cannot be claimed by a contradictory provider`() {
        host.providers["a1"] = "claude"
        book.onPermissionRequest("a1", writePermission("perm-1").with("provider" to "codex"))
        assertTrue(book.pendingPermissions().isEmpty())
    }

    @Test
    fun `B-AGENT-145 the ACP card keeps a structured task preview without raw host copy`() {
        val request = permission(onceAndStanding()).with(
            "title" to fakeSecret,
            "description" to fakeSecret,
            "input" to mapOf("credentials" to fakeSecret),
        ).edit("metadata", "rawRequest", value = mapOf("secret" to fakeSecret))
        book.onPermissionRequest("existing", request)
        val card = book.pendingPermissions().single()
        assertEquals("edit", card.permission)
        assertEquals("$cwd/result.txt", card.metadata["filePath"].toString().trim('"'))
        assertEquals("--- /dev/null\n+++ b/$cwd/result.txt\n@@ -0,0 +1,1 @@\n+hello", card.metadata["diff"].toString().let { kotlinx.serialization.json.Json.decodeFromString<String>(it) })
        assertTrue(card.always.isEmpty())
        assertFalse(fakeSecret in host.events.joinToString { it.toString() })
        assertFalse(fakeSecret in card.toString())
    }

    @Test
    fun `B-SEC-109 unoffered or standing action ids can never grant`() = runTest {
        for (selected in listOf("not-offered", "allow-future-calls")) {
            ask(onceAndStanding(), id = "approval-$selected")
            try {
                book.replyHostPermission("approval-$selected", selected)
            } catch (_: PaseoException) {
            }
        }
        assertTrue(host.responses().none { it.toString().contains("\"allow\"") })
    }

    @Test
    fun `B-SEC-109 an unknown ACP option kind cannot become an allow action`() = runTest {
        host.answers["cancel_agent_request"] = { jsonOf("agentId" to "existing", "agent" to agent("gemini")) }
        ask(listOf(option("future-choice", "allow_future")))
        assertTrue(book.pendingHostPermissions().single().choices.isEmpty())
        book.replyHostPermission("approval", null)
        assertTrue(host.responses().isEmpty())
        assertEquals(1, host.requests.size)
    }

    @Test
    fun `B-SEC-109 disconnected permissions cannot be replied through a replacement connection`() = runTest {
        ask(onceAndStanding())
        host.epoch++ // the replacement connection
        assertFailsWith<PaseoException> { book.replyHostPermission("approval", "allow-this-call") }
        book.disconnect()
        assertFailsWith<PaseoException> { book.replyHostPermission("approval", "allow-this-call") }
        assertTrue(host.responses().isEmpty())
        assertTrue(host.events.last() is GatewayEvent.PermissionSettled)
    }

    @Test
    fun `B-SEC-109 a native runtime's request without ACP options is not a host request`() {
        assertNull(paseoHostPermission("a1", writePermission("p"), "claude"))
        // Copilot goes through ACP even without options; an options-less request has no choices.
        assertTrue(paseoHostPermission("a1", writePermission("p"), "copilot")!!.choices.isEmpty())
        assertFailsWith<PaseoException> { paseoHostPermission("a1", permission(onceAndStanding()).without("id"), "gemini") }
    }

    @Test
    fun `B-SEC-110 encrypted public endpoints are rejected while private endpoints stay usable`() {
        assertFailsWith<PaseoException> { PaseoEndpoint.parse("wss://public.example/ws") }
        assertEquals("100.64.0.20", PaseoEndpoint.parse("wss://100.64.0.20:6767").host)
        assertEquals("wss://host.tail1234.ts.net:6767/ws", PaseoEndpoint.parse("wss://host.tail1234.ts.net:6767").url)
        assertEquals("127.0.0.1", PaseoEndpoint.parse("ws://127.0.0.1:6767").host)
    }
}
