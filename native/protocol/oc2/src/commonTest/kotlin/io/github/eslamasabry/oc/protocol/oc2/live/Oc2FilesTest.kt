package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** OpenCode 2 file browsing (lib/api2/client.dart fsList/fsReadBytes/fsFind, protocol notes §11). */
class Oc2FilesTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun gateway(status: HttpStatusCode = HttpStatusCode.OK, body: () -> Any): Oc2Gateway =
        Oc2Gateway(
            NetClient(
                HttpClient(
                    MockEngine { request ->
                        requests += request
                        when (val b = body()) {
                            is ByteArray -> respond(b, status)
                            else -> respond(b.toString(), status)
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
            initialLocation = ServerLocation(directory = "/work/app"),
            initialDialect = Oc2Dialect.Beta,
        )

    @Test
    fun `B-NAV-026 a folder lists through fs list with the pinned location, folders keep their kind`() = runTest {
        val g = gateway {
            """{"location":{},"data":[{"path":"lib/api/","type":"directory"},{"path":"lib/main.dart","type":"file"}]}"""
        }
        try {
            val nodes = assertIs<Outcome.Ok<*>>(g.listFiles("lib")).value as List<*>
            assertEquals(2, nodes.size)
            val first = nodes[0] as io.github.eslamasabry.oc.core.model.FileNode
            assertEquals("lib/api", first.path)
            assertEquals("api", first.name)
            assertTrue(first.isDir)
            val url = requests.single().url
            assertEquals("/api/fs/list", url.encodedPath)
            assertEquals("lib", url.parameters["path"])
            assertEquals("/work/app", url.parameters["location[directory]"])
            assertTrue(g.capabilities.value.fileBrowsing)
        } finally {
            g.close()
        }
    }

    @Test
    fun `B-NAV-026 the project root lists as dot`() = runTest {
        val g = gateway { """{"data":[]}""" }
        try {
            g.listFiles("")
            assertEquals(".", requests.single().url.parameters["path"])
        } finally {
            g.close()
        }
    }

    @Test
    fun `B-MEDIA-001 a text file reads as text and every path segment is encoded`() = runTest {
        val g = gateway { "héllo".encodeToByteArray() }
        try {
            val content = assertIs<Outcome.Ok<*>>(g.fileContent("docs/my notes.md")).value
                as io.github.eslamasabry.oc.core.model.FileContent
            assertEquals("héllo", content.content)
            assertEquals("text", content.type)
            assertEquals("/api/fs/read/docs/my%20notes.md", requests.single().url.encodedPath)
        } finally {
            g.close()
        }
    }

    @Test
    fun `B-MEDIA-001 invalid UTF-8 and NUL bytes stay exact as base64`() = runTest {
        val invalid = byteArrayOf(0x68, 0xC3.toByte(), 0x28, 0x00)
        val g = gateway { invalid }
        try {
            val content = assertIs<Outcome.Ok<*>>(g.fileContent("a.bin")).value
                as io.github.eslamasabry.oc.core.model.FileContent
            assertEquals("base64", content.encoding)
            assertContentEquals(invalid, Base64.decode(content.content))
        } finally {
            g.close()
        }
    }

    @Test
    fun `B-NAV-026 a missing file is a failure, never empty text`() = runTest {
        val g = gateway(HttpStatusCode.NotFound) { """{"name":"NotFoundError"}""" }
        try {
            assertIs<Outcome.Failed>(g.fileContent("gone.txt"))
        } finally {
            g.close()
        }
    }

    @Test
    fun `B-NAV-026 name search asks fs find for files`() = runTest {
        val g = gateway { """{"data":[{"path":"lib/main.dart","type":"file"}]}""" }
        try {
            val found = assertIs<Outcome.Ok<*>>(g.findFile("main")).value as List<*>
            assertEquals(listOf("lib/main.dart"), found)
            val url = requests.single().url
            assertEquals("/api/fs/find", url.encodedPath)
            assertEquals("main", url.parameters["query"])
            assertEquals("file", url.parameters["type"])
        } finally {
            g.close()
        }
    }
}
