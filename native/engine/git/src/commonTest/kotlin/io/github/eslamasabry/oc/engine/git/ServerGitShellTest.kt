package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ServerGitShellTest {
    private val origin = ServerOrigin("https", "box.example.invalid", 4096)
    private val calls = mutableListOf<String>()
    private var startBody = ""

    private fun MockRequestHandleScope.json(text: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun shell(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ServerGitShell {
        val engine =
            MockEngine { request ->
                calls += "${request.method.value} ${request.url.encodedPath}"
                if (request.method == HttpMethod.Post) startBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                handler(request)
            }
        val client = HttpClient(engine) { expectSuccess = false }
        return ServerGitShell(NetClient(client, { TransportError.Other }), origin, "Basic dGVzdDp0ZXN0", "/srv/my app", pollIntervalMs = 1)
    }

    @Test
    fun `B-BUILD-013 start, poll, page output and drop it`() = runTest {
        var polls = 0
        val sh =
            shell { request ->
                val path = request.url.encodedPath
                when {
                    request.method == HttpMethod.Post -> json("""{"data":{"id":"sh_1","status":"running"}}""")
                    request.method == HttpMethod.Delete -> json("""{"data":true}""")
                    path.endsWith("/output") ->
                        if (request.url.parameters["cursor"] == "0") json("""{"data":{"output":"@@OCG be","cursor":8,"size":20,"truncated":false}}""")
                        else json("""{"data":{"output":"gin\n@@OCG end\n","cursor":20,"size":20,"truncated":false}}""")
                    else -> json(if (++polls < 3) """{"data":{"id":"sh_1","status":"running"}}""" else """{"data":{"id":"sh_1","status":"exited","exit":0}}""")
                }
            }
        val reply = assertIs<GitShellReply.Output>(sh.run("echo hi", 5_000))
        assertEquals("@@OCG begin\n@@OCG end\n", reply.text)
        assertEquals("POST /api/shell", calls.first())
        assertEquals("DELETE /api/shell/sh_1", calls.last())
        assertEquals(3, calls.count { it == "GET /api/shell/sh_1" })
        assertEquals(2, calls.count { it == "GET /api/shell/sh_1/output" })
    }

    @Test
    fun `B-BUILD-013 the script is wrapped as quoted sh -c in the project folder`() = runTest {
        val sh =
            shell { request ->
                when (request.method) {
                    HttpMethod.Post -> json("""{"data":{"id":"sh_2"}}""")
                    HttpMethod.Delete -> json("{}")
                    else ->
                        if (request.url.encodedPath.endsWith("/output")) json("""{"data":{"output":"","cursor":0,"size":0}}""")
                        else json("""{"data":{"id":"sh_2","status":"exited","exit":0}}""")
                }
            }
        sh.run("printf 'x'", 1_000)
        val body = Json.parseToJsonElement(startBody).jsonObject
        assertEquals("sh -c 'printf '\\''x'\\'''", body["command"]!!.jsonPrimitive.content)
        assertEquals("/srv/my app", body["cwd"]!!.jsonPrimitive.content)
        assertEquals("1000", body["timeout"]!!.jsonPrimitive.content)
    }

    @Test
    fun `B-BUILD-013 a missing endpoint means unsupported without server text`() = runTest {
        val sh = shell { json("""{"message":"route /api/shell not found: echo secret"}""", HttpStatusCode.NotFound) }
        val reply = assertIs<GitShellReply.Failed>(sh.run("echo secret", 1_000))
        assertEquals(GitShellFailure.Unsupported, reply.failure)
        assertFalse("secret" in reply.details)
        val down = shell { json("{}", HttpStatusCode.InternalServerError) }
        assertEquals(GitShellFailure.Unavailable, assertIs<GitShellReply.Failed>(down.run("x", 1_000)).failure)
    }

    @Test
    fun `B-BUILD-013 a command the server timed out is a timeout and is still dropped`() = runTest {
        val sh =
            shell { request ->
                when (request.method) {
                    HttpMethod.Post -> json("""{"data":{"id":"sh_3"}}""")
                    HttpMethod.Delete -> json("{}")
                    else -> json("""{"data":{"id":"sh_3","status":"timeout"}}""")
                }
            }
        assertEquals(GitShellFailure.TimedOut, assertIs<GitShellReply.Failed>(sh.run("sleep 99", 1_000)).failure)
        assertTrue(calls.last().startsWith("DELETE"))
    }
}
