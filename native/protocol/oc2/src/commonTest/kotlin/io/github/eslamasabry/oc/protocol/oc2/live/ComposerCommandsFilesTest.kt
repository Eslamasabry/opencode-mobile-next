package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Slash commands, shell lines and @-file search on OC2 (Flutter `Api2Gateway` endpoints). */
class ComposerCommandsFilesTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun gateway(body: (HttpRequestData) -> String): Oc2Gateway =
        Oc2Gateway(
            NetClient(
                HttpClient(
                    MockEngine { request ->
                        requests += request
                        respond(body(request), HttpStatusCode.OK)
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

    private fun HttpRequestData.bodyText(): String =
        when (val content = body) {
            is io.ktor.http.content.TextContent -> content.text
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            else -> ""
        }

    @Test
    fun `B-NAV-051 the server's commands are listed by name and a command posts name and text`() =
        runTest {
            val g = gateway {
                if (it.url.encodedPath.endsWith("/command") && it.method.value == "GET")
                    """{"data":[{"name":"review","description":"Review the diff"},{"name":"init"},{"nope":1}]}"""
                else "{}"
            }
            try {
                val commands = assertIs<Outcome.Ok<*>>(g.commands()).value as List<*>
                assertEquals(
                    listOf("init", "review"),
                    commands.map { (it as io.github.eslamasabry.oc.core.model.CommandInfo).name },
                )
                assertIs<Outcome.Ok<Unit>>(g.slashCommand(SessionId("s1"), "/review", "now please"))
                val post = requests.last()
                assertTrue(post.url.encodedPath.endsWith("/session/s1/command"))
                assertEquals("""{"command":"review","text":"now please"}""", post.bodyText())
                assertTrue(g.capabilities.value.slashCommands)
            } finally {
                g.close()
            }
        }

    @Test
    fun `B-CHAT-087 file search asks fs find for files and keeps only file paths`() = runTest {
        val g = gateway {
            """{"data":[{"path":"src/main.kt","type":"file"},{"path":"src","type":"directory"},{"path":"README.md"}]}"""
        }
        try {
            val found = assertIs<Outcome.Ok<*>>(g.findFile("mai")).value as List<*>
            assertEquals(listOf("src/main.kt", "README.md"), found)
            val url = requests.last().url
            assertTrue(url.encodedPath.endsWith("/fs/find"))
            assertEquals("mai", url.parameters["query"])
            assertEquals("file", url.parameters["type"])
        } finally {
            g.close()
        }
    }

    @Test
    fun `B-PROTO-043 a shell line posts the command to the session`() = runTest {
        val g = gateway { "{}" }
        try {
            assertIs<Outcome.Ok<Unit>>(g.shell(SessionId("s1"), "ls -la", ""))
            val post = requests.last()
            assertTrue(post.url.encodedPath.endsWith("/session/s1/shell"))
            assertEquals("""{"command":"ls -la"}""", post.bodyText())
        } finally {
            g.close()
        }
    }
}
