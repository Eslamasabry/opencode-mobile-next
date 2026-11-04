package io.github.eslamasabry.oc.engine.integrations.quota

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The Dio adapter fake of provider_quota_client_test.dart: records requests, cancellations and closes. */
internal class FakeQuotaTransport(private val handler: suspend (QuotaRequest) -> QuotaResponse) : QuotaTransport {
    val requests = mutableListOf<QuotaRequest>()
    var closes = 0
    var forceClosed = false
    var cancellations = 0
    val called = CompletableDeferred<Unit>()

    override suspend fun fetch(request: QuotaRequest): QuotaResponse {
        requests += request
        called.complete(Unit)
        try {
            return handler(request)
        } catch (e: CancellationException) {
            cancellations++
            throw e
        }
    }

    override fun close(force: Boolean) {
        closes++
        forceClosed = force
    }
}

internal fun bodyOf(text: String): Flow<ByteArray> = flowOf(text.encodeToByteArray())

internal fun jsonResponse(value: Any? = quotaFixture(), status: Int = 200): QuotaResponse = QuotaResponse(status, bodyOf(encode(value)))

internal const val PASSWORD = "fixture-only-basic-password"

/** A profile whose fields can change after the gateway was built (Flutter `ServerProfile` is mutable). */
internal class MutableProfile(
    var baseUrl: String = "https://collector.example:8443/",
    var username: String = "fixture-user",
    var password: String = PASSWORD,
    var requiresPasswordReentry: Boolean = false,
)

internal fun gateway(
    profile: MutableProfile,
    transport: QuotaTransport,
    provider: QuotaProvider = QuotaProvider.Codex,
    inAppToken: (suspend () -> String?)? = null,
): HttpProviderQuotaGateway =
    HttpProviderQuotaGateway(
        baseUrl = profile.baseUrl,
        username = profile.username,
        password = profile.password,
        requiresPasswordReentry = profile.requiresPasswordReentry,
        transport = transport,
        provider = provider,
        inAppToken = inAppToken,
    )

internal fun basic(user: String, password: String): String = "Basic " + kotlin.io.encoding.Base64.encode("$user:$password".encodeToByteArray())

internal fun QuotaRead.snapshot(): ProviderQuotaSnapshot = (this as? QuotaRead.Read)?.snapshot ?: error("expected a snapshot, got $this")

internal fun QuotaRead.kind(): QuotaFailureKind? = (this as? QuotaRead.Failed)?.failure?.kind
