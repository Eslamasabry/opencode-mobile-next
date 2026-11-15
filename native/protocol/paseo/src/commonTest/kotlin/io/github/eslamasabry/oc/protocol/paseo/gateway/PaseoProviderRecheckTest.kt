package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Issue 147 (Galaxy A54): after installing and signing in to Antigravity the app kept saying it was not ready, also
 * after reopening it again and again. The helper (Paseo 0.15) keeps each agent's last check until asked to look again
 * (`provider-snapshot-manager` reuses a cached result unless forced); a check made before the sign-in answers
 * "Authentication required" (seen from Antigravity 1.5.9's `session/new` without a sign-in), and the helper keeps
 * running while the app is reopened. Before, a start refused on that kept answer for good.
 */
class PaseoProviderRecheckTest {
    /** The helper: Antigravity refuses until a forced re-check runs after [signedIn] turned true. */
    private class Helper {
        var signedIn = false
        var checkedSinceSignIn = false

        fun daemon(): FakeDaemon =
            FakeDaemon().seedDefaults().apply {
                handlers["get_providers_snapshot_request"] = {
                    val entry =
                        if (checkedSinceSignIn)
                            mapOf(
                                "provider" to "antigravity",
                                "status" to "ready",
                                "enabled" to true,
                                "models" to listOf(
                                    mapOf("provider" to "antigravity", "id" to "gemini-3.8-flash-high", "label" to "Gemini 3.8 Flash (High)"),
                                ),
                            )
                        else
                            mapOf(
                                "provider" to "antigravity",
                                "status" to "error",
                                "enabled" to true,
                                "error" to "Authentication required",
                            )
                    "get_providers_snapshot_response" to jsonOf("entries" to listOf(entry))
                }
                handlers["refresh_providers_snapshot_request"] = {
                    if (signedIn) checkedSinceSignIn = true
                    "refresh_providers_snapshot_response" to jsonOf("acknowledged" to true)
                }
                handlers["create_agent_request"] = { m ->
                    val config = m["config"] as JsonObject
                    val agent =
                        agentJson("real-1", cwd = config.str("cwd") ?: DIR, provider = "antigravity", status = "running")
                    "status" to jsonOf("status" to "agent_created", "agentId" to "real-1", "agent" to agent)
                }
            }
    }

    private fun TestScope.started(helper: Helper): GatewayHarness =
        GatewayHarness(this, helper.daemon()).also {
            it.gateway.startListening()
            it.pump()
        }

    private suspend fun GatewayHarness.start(n: Int): Outcome<*> {
        val draft = gateway.createSession(NewSession())
        val id = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(draft).value.id
        assertIs<Outcome.Ok<Unit>>(gateway.seedDraftProvider(id, "antigravity"))
        return gateway.promptNewSessionWithMessageId(id, MessageId("m$n"), OutgoingPrompt("hi $n")) { true }
    }

    @Test
    fun `I147-AGY-3 a start refused on the helper's answer from before the sign-in asks it to check again`() = runTest {
        val helper = Helper()
        val h = started(helper)
        // The app read the helper's list before the person signed in: it keeps "Authentication required".
        assertIs<Outcome.Ok<*>>(h.gateway.providers())
        helper.signedIn = true
        val sent = h.start(1)
        assertIs<Outcome.Ok<*>>(sent, "started after the helper checked again: $sent")
        assertEquals(1, h.daemon.of("create_agent_request").size)
        val asked = h.daemon.of("refresh_providers_snapshot_request").single()
        assertEquals(listOf("antigravity"), (asked["providers"] as JsonArray).map { (it as JsonPrimitive).content })
    }

    @Test
    fun `I147-AGY-3b an agent still refused after its check is refused, and not checked again within 30 s`() = runTest {
        val helper = Helper()
        val h = started(helper)
        assertIs<Outcome.Failed>(h.start(1))
        assertIs<Outcome.Failed>(h.start(2))
        assertEquals(1, h.daemon.of("refresh_providers_snapshot_request").size, "one check per agent per 30 s")
        assertEquals(0, h.daemon.of("create_agent_request").size)
        h.clock.advance(kotlin.time.Duration.parse("31s"))
        helper.signedIn = true
        assertIs<Outcome.Ok<*>>(h.start(3))
        assertEquals(2, h.daemon.of("refresh_providers_snapshot_request").size)
    }
}
