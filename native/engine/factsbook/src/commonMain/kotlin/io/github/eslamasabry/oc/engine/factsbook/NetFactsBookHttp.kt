package io.github.eslamasabry.oc.engine.factsbook

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.AddressRules
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath

/**
 * [FactsBookHttp] over one trust-gated :core:net client for the node's origin. The node is on a private address
 * (`FactsBookAddress`), usually over plain HTTP, so cleartext is permitted for exactly that origin; the gate still
 * refuses any other origin, user info and redirects. Nothing here logs.
 */
public class NetFactsBookHttp(private val client: NetClient, private val origin: ServerOrigin) : FactsBookHttp {
    override suspend fun send(
        method: String,
        path: String,
        query: Map<String, String>,
        headers: Map<String, String>,
        body: String?,
    ): FbHttpResult {
        val outcome =
            client.exchangeAnswer(
                build = {
                    this.method = HttpMethod.parse(method)
                    url(
                        URLBuilder(origin.toString())
                            .apply {
                                encodedPath = path
                                query.forEach { (k, v) -> parameters.append(k, v) }
                            }
                            .build()
                    )
                    headers.forEach { (k, v) -> if (k != "Content-Type") header(k, v) }
                    if (body != null) setBody(TextContent(body, ContentType.Application.Json))
                },
                read = { response -> FbHttpResult.Answer(response.status.value, response.bodyAsText()) },
            )
        return when (outcome) {
            is Outcome.Ok -> outcome.value
            is Outcome.Failed -> FbHttpResult.Unreachable
        }
    }

    public companion object {
        /** The origin of a saved base URL ("http://127.0.0.1:8787"), or null when it is not a private address. */
        public fun originOf(baseUrl: String): ServerOrigin? {
            val parsed = FactsBookAddress.parse(baseUrl) ?: return null
            val scheme = parsed.substringBefore("://")
            val authority = parsed.substringAfter("://")
            val host = authority.substringBeforeLast(':').removeSurrounding("[", "]")
            val port = authority.substringAfterLast(':').toIntOrNull() ?: return null
            return ServerOrigin(scheme, host, port)
        }

        /** One client per pairing; [FactsBookAddress] already limited the host to a private one. */
        public fun create(http: HttpClientFactory, baseUrl: String): FactsBookHttp {
            val origin = originOf(baseUrl) ?: return FactsBookHttp { _, _, _, _, _ -> FbHttpResult.Unreachable }
            val client = http.create(TransportSpec(origin, AddressRules.classify(origin.host), cleartextPermitted = true))
            return NetFactsBookHttp(client, origin)
        }
    }
}
