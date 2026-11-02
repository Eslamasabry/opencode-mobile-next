package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.CatalogSnapshot
import io.github.eslamasabry.oc.core.model.ChatDefaults
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** RUNBOOK §7 finding 8: a mode row's default is its own catalog id, never its base `modelID`. */
class ComposerFindingsModelTest {
    private fun gateway(body: String): Oc2Gateway =
        Oc2Gateway(
            NetClient(
                HttpClient(MockEngine { respond(body, HttpStatusCode.OK) }) {
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

    private val modeRow =
        """{"id":"gpt-5-high","modelID":"gpt-5","providerID":"openai","name":"GPT-5","enabled":true,"status":"active","capabilities":{"tools":true,"input":["text"],"output":["text"]},"limit":{"context":1000,"output":100},"variants":{}}"""

    @Test
    fun `B-NAV-034 finding 8 the default of a mode row maps to the catalog row the picker shows`() =
        runTest {
            val g = gateway("""{"data":$modeRow}""")
            try {
                val defaults = assertIs<Outcome.Ok<ChatDefaults>>(g.chatDefaults()).value
                assertEquals(ModelRef("openai", "gpt-5-high"), defaults.model)
            } finally {
                g.close()
            }
            val c = gateway("""{"data":[$modeRow]}""")
            try {
                val catalog = assertIs<Outcome.Ok<CatalogSnapshot>>(c.catalog()).value
                assertEquals("gpt-5-high", catalog.models.single().id)
            } finally {
                c.close()
            }
        }

    @Test
    fun `B-NAV-034 finding 8 a provider-prefixed default row peels its own provider like the catalog`() =
        runTest {
            val g =
                gateway(
                    """{"data":{"id":"opencode/glm-5.3","providerID":"opencode","name":"GLM","enabled":true,"capabilities":{"input":["text"],"output":["text"]},"limit":{},"variants":{}}}"""
                )
            try {
                val defaults = assertIs<Outcome.Ok<ChatDefaults>>(g.chatDefaults()).value
                assertEquals(ModelRef("opencode", "glm-5.3"), defaults.model)
            } finally {
                g.close()
            }
        }
}
