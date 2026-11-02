package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Coordinator regressions derived from target-session selection in Flutter model_picker_test. */
class CoordinatorComposerModelTest {
    private fun gateway(body: String): Oc2Gateway = Oc2Gateway(
        NetClient(HttpClient(MockEngine { respond(body, HttpStatusCode.OK) }) {
            expectSuccess = false
            followRedirects = false
        }, { TransportError.Other }),
        ServerOrigin("https", "example.test", 443),
        ConnectionCredentials.None,
        object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(1_000) },
        Redactor(),
        initialDialect = Oc2Dialect.Stable,
    )

    @Test
    fun `B-NAV-034 session readback keeps exact selected model and normalized variant`() = runTest {
        val g = gateway("""{"data":{"id":"s","model":{"id":"glm-5.3-flash-free","providerID":"opencode","variant":"default"},"agent":"build"}}""")
        try {
            val session = assertIs<Outcome.Ok<*>>(g.session(SessionId("s"))).value as io.github.eslamasabry.oc.core.model.Session
            val choice = assertNotNull(session.selection)
            assertTrue(choice.modelKnown)
            assertTrue(choice.agentKnown)
            assertEquals(ModelRef("opencode", "glm-5.3-flash-free"), choice.model)
            assertEquals("", choice.variant)
            assertEquals("build", choice.agent)
        } finally { g.close() }
    }

    @Test
    fun `B-NAV-034 live shaped catalog supplies usable models without retaining raw provider config`() = runTest {
        val g = gateway("""{"data":[{"id":"glm-5.3-flash-free","modelID":"glm-5.3-flash-free","providerID":"opencode","name":"GLM-5.3-Flash Free","enabled":true,"status":"active","capabilities":{"tools":true,"input":["text","image"],"output":["text"]},"limit":{"context":128000,"output":8192},"variants":{},"apiKey":"TEST_SECRET_MUST_NOT_SURVIVE"}]}""")
        try {
            assertTrue(g.capabilities.value.serverCatalog)
            val catalog = assertIs<Outcome.Ok<*>>(g.catalog()).value as io.github.eslamasabry.oc.core.model.CatalogSnapshot
            val model = catalog.models.single()
            assertEquals("opencode", model.providerId)
            assertEquals("glm-5.3-flash-free", model.id)
            assertTrue(model.enabled)
            assertTrue(model.attachments)
            assertFalse(catalog.toString().contains("TEST_SECRET_MUST_NOT_SURVIVE"))
        } finally { g.close() }
    }
}
