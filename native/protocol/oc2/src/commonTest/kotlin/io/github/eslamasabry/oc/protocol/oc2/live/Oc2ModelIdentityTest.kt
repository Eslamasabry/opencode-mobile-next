package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ModelInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Two catalog entries of one provider can share a `modelID` (a "Fast" entry over its plain model). The catalog `id`
 * is the unique identity: the picker lists, selects, sends and compares by it.
 */
class Oc2ModelIdentityTest {
    private fun entry(id: String, modelId: String, name: String) =
        """{"id":"$id","modelID":"$modelId","providerID":"anthropic","name":"$name","enabled":true,"status":"active","capabilities":{"tools":true,"input":["text"],"output":["text"]},"limit":{"context":1000,"output":100},"variants":[]}"""

    private val plain = entry("claude-fable-5-2", "claude-fable-5-2", "Claude Fable 5.2")
    private val fast = entry("claude-fable-5-2-fast", "claude-fable-5-2", "Claude Fable 5.2 Fast")

    private fun gateway(defaultRow: String, bodies: MutableList<String> = mutableListOf()): Oc2Gateway =
        Oc2Gateway(
            NetClient(
                HttpClient(
                    MockEngine { request ->
                        when (request.url.encodedPath) {
                            "/api/provider" -> respond("""{"data":[{"id":"anthropic","name":"Anthropic"}]}""", HttpStatusCode.OK)
                            "/api/model" -> respond("""{"data":[$plain,$fast]}""", HttpStatusCode.OK)
                            "/api/model/default" -> respond("""{"data":$defaultRow}""", HttpStatusCode.OK)
                            "/api/session/s1/model" -> {
                                bodies += (request.body as TextContent).text
                                respond("", HttpStatusCode.NoContent)
                            }
                            else -> error("Unexpected route ${request.url.encodedPath}")
                        }
                    }
                ) {
                    expectSuccess = false
                    followRedirects = false
                },
                { TransportError.Other },
            ),
            ServerOrigin("https", "example.test", 443),
            ConnectionCredentials.None,
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
            },
            Redactor(),
            initialDialect = Oc2Dialect.Stable,
        )

    private suspend fun inventory(g: Oc2Gateway): ProvidersResponse =
        assertIs<Outcome.Ok<ProvidersResponse>>(g.providers()).value

    @Test
    fun `two entries sharing a modelID are two models with their own names and data`() = runTest {
        val g = gateway(fast)
        try {
            val provider = inventory(g).providers.single()
            assertEquals(listOf("claude-fable-5-2", "claude-fable-5-2-fast"), provider.modelIds)
            assertEquals(
                listOf("Claude Fable 5.2", "Claude Fable 5.2 Fast"),
                provider.modelIds.map { (provider.modelData.getValue(it)["name"] as kotlinx.serialization.json.JsonPrimitive).content },
            )
            assertEquals(2, provider.modelData.size)
        } finally {
            g.close()
        }
    }

    @Test
    fun `choosing the Fast entry sends its catalog id`() = runTest {
        val bodies = mutableListOf<String>()
        val g = gateway(plain, bodies)
        try {
            val chosen = inventory(g).providers.single().modelIds.last()
            assertIs<Outcome.Ok<Unit>>(g.setSessionModel(SessionId("s1"), ModelRef("anthropic", chosen), ""))
            assertEquals(listOf("""{"model":{"id":"claude-fable-5-2-fast","providerID":"anthropic"}}"""), bodies)
        } finally {
            g.close()
        }
    }

    @Test
    fun `the server default names the entry itself when its id differs from its modelID`() = runTest {
        val g = gateway(fast)
        try {
            val inv = inventory(g)
            assertEquals("anthropic", inv.defaultProviderId)
            assertEquals("claude-fable-5-2-fast", inv.defaultModelId)
            assertTrue(inv.defaultModelId in inv.providers.single().modelIds)
        } finally {
            g.close()
        }
    }

    @Test
    fun `an entry reference carries the catalog id`() {
        val info = Oc2ModelInfo.fromJson(Json.parseToJsonElement(fast) as JsonObject)!!
        assertEquals("claude-fable-5-2-fast", info.ref().id)
    }
}
