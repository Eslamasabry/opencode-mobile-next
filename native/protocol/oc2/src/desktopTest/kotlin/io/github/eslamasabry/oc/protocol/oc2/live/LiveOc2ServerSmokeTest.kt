package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.okhttp.OkHttpEngineFactory
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import java.security.SecureRandom
import kotlin.random.asKotlinRandom
import kotlin.test.*
import kotlin.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeTrue

/**
 * Explicit opt-in only: an isolated real OC2 server and model are required. No owner profiles are
 * read.
 */
class LiveOc2ServerSmokeTest {
    @Test
    fun `B-PROTO-042 real OC2 admits one prompt and emits a real response`() = runBlocking {
        val configuredAddress = System.getenv("OC_NATIVE_LIVE_URL")
        val configuredPassword = System.getenv("OC_NATIVE_LIVE_PASSWORD")
        assumeTrue(
            "Isolated OC2 live proof not configured",
            configuredAddress != null && configuredPassword != null,
        )
        val address = requireNotNull(configuredAddress)
        val password = requireNotNull(configuredPassword)
        val username = System.getenv("OC_NATIVE_LIVE_USERNAME") ?: "opencode"
        val directory = System.getenv("OC_NATIVE_LIVE_DIRECTORY")
        val origin =
            (AddressRules.origin(address) as? Outcome.Ok)?.value
                ?: error("Invalid isolated server address")
        val dispatchers =
            object : AppDispatchers {
                override val main = Dispatchers.Default
                override val default = Dispatchers.Default
                override val io = Dispatchers.IO

                override fun serial(name: String) = Dispatchers.Default.limitedParallelism(1)
            }
        val scope = ConnectionCoroutineScope(SupervisorJob() + dispatchers.default)
        val profile =
            ServerProfile(
                ProfileId("native-live-smoke"),
                "Native test",
                BackendKind.OpenCode2,
                origin,
                ProfileTrust(null, if (origin.scheme == "http") 1L else null),
                false,
                username,
            )
        val factory =
            Oc2GatewayFactory(
                HttpClientFactory(OkHttpEngineFactory()),
                dispatchers,
                Clock.System,
                Redactor(),
                SecureRandom().asKotlinRandom(),
            )
        val pair =
            factory.connect(
                GatewaySpec(
                    profile,
                    ConnectionCredentials(username, password),
                    scope,
                    location = ServerLocation(directory),
                )
            )
        assertIs<Outcome.Ok<GatewayPair>>(pair)
        val gateway = pair.value.live
        var session: Session? = null
        var receivedParts = 0
        val eventJob = launch {
            gateway.events.all.collect {
                if (it.event is GatewayEvent.PartUpserted || it.event is GatewayEvent.PartDelta)
                    receivedParts++
            }
        }
        try {
            withTimeout(20_000) { gateway.events.status.first { it == StreamStatus.Connected } }
            val created =
                (gateway.createSession(NewSession(title = "Native integration smoke"))
                        as? Outcome.Ok)
                    ?.value
            assertNotNull(created)
            session = created
            val page = gateway.sessionPage()
            assertIs<Outcome.Ok<ServerPage<Session>>>(page)
            assertTrue(page.value.items.any { it.id == created.id })
            val correlated = gateway as CorrelatedPromptGateway
            val admission =
                correlated.promptWithMessageId(
                    created.id,
                    correlated.createPromptMessageId(),
                    OutgoingPrompt("Reply with exactly NATIVE_OK. Do not use tools."),
                )
            assertIs<Outcome.Ok<PromptAdmission>>(admission)
            assertIs<PromptAdmission.Confirmed>(admission.value)
            var responseSeen = false
            withTimeout(120_000) {
                while (!responseSeen) {
                    delay(500)
                    val messages = gateway.messagePage(created.id)
                    assertIs<
                        Outcome.Ok<
                            ServerPage<
                                io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
                            >
                        >
                    >(
                        messages
                    )
                    responseSeen =
                        messages.value.items.any { message ->
                            message.info.role == Role.Assistant &&
                                message.parts.any {
                                    it.type == PartType.Text && it.text.contains("NATIVE_OK")
                                }
                        }
                }
            }
            assertTrue(receivedParts > 0, "No real stream parts received")
            assertIs<Outcome.Ok<StopResult>>(gateway.stop(created.id))
            println(
                "NATIVE_LIVE_SMOKE health=true session=true admission=true response=true stream=true stop=true"
            )
        } finally {
            session?.let { assertIs<Outcome.Ok<Unit>>(gateway.deleteSession(it.id)) }
            eventJob.cancelAndJoin()
            gateway.close()
            scope.cancel()
        }
    }
}
