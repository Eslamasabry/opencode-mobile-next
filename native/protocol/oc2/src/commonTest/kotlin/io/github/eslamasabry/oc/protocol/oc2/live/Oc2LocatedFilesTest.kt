package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.model.ServerOrigin
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

/** P12 fix (Codex files #1): a conversation's files are read from that conversation's folder. */
class Oc2LocatedFilesTest {
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
        }

    @Test
    fun `B-NAV-026 files of a conversation carry its own folder, not the gateway's shared location`() = runTest {
        val seen = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine { request ->
            seen += request.url.encodedPath to request.url.parameters["location[directory]"]
            when {
                request.url.encodedPath.endsWith("/fs/list") ->
                    respond("""{"data":[{"path":"src/","type":"directory"}]}""", HttpStatusCode.OK)
                request.url.encodedPath.endsWith("/fs/find") -> respond("""{"data":[{"path":"a.kt","type":"file"}]}""", HttpStatusCode.OK)
                else -> respond("hello", HttpStatusCode.OK)
            }
        }
        val gateway =
            Oc2Gateway(
                NetClient(HttpClient(engine) { expectSuccess = false; followRedirects = false }, { TransportError.Other }),
                ServerOrigin("https", "example.test", 443),
                ConnectionCredentials("opencode", "test-secret"),
                clock,
                Redactor(),
                initialLocation = ServerLocation("/work/b"),
                initialDialect = Oc2Dialect.Beta,
            )
        val files = gateway.filesAt(ServerLocation("/work/a"))
        assertIs<Outcome.Ok<*>>(files.listFiles(""))
        assertIs<Outcome.Ok<*>>(files.fileContent("README.md"))
        assertIs<Outcome.Ok<*>>(files.findFile("a"))
        assertEquals(listOf("/work/a", "/work/a", "/work/a"), seen.map { it.second })
        // The gateway's own reads keep its shared location.
        assertIs<Outcome.Ok<*>>(gateway.listFiles(""))
        assertEquals("/work/b", seen.last().second)
        gateway.close()
    }
}
