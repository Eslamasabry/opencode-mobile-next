package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** B-182: "Clone a repository" in Open a project runs through the server's located shell, like New folder (N-41). */
class Oc2CloneRepositoryTest {
    private fun server(exit: Int) = RowServer { seen ->
        when {
            seen.method == "POST" && seen.path == "/api/shell" -> json("""{"data":{"id":"sh_c","status":"running"}}""")
            seen.method == "GET" && seen.path == "/api/shell/sh_c" ->
                json("""{"data":{"id":"sh_c","status":"exited","exit":$exit}}""")
            seen.method == "DELETE" && seen.path == "/api/shell/sh_c" -> respond("", HttpStatusCode.NoContent)
            else -> error("Unexpected ${seen.method} ${seen.path}")
        }
    }

    @Test
    fun `B-182 clone runs git clone in the folder shown and answers the new folder`() = runTest {
        val server = server(exit = 0)
        val made = server.gateway.cloneRepository("/srv/work", "https://example.test/o/app.git", "app")
        assertEquals(Outcome.Ok("/srv/work/app"), made)
        val start = server.seen.first { it.method == "POST" }
        val body = start.json!!.jsonObject
        assertEquals("git clone -q -- 'https://example.test/o/app.git' 'app'", body["command"]!!.jsonPrimitive.content)
        assertEquals("/srv/work", body["cwd"]!!.jsonPrimitive.content)
        for (seen in server.seen) assertEquals("/srv/work", seen.query["location[directory]"], seen.path)
        assertTrue(server.seen.any { it.method == "DELETE" }, "the shell's copy is dropped")
        server.gateway.close()
    }

    @Test
    fun `B-182 a clone that fails says so without Git's text`() = runTest {
        val server = server(exit = 128)
        val made = server.gateway.cloneRepository("/srv/work", "git@example.test:o/app.git", "app")
        assertEquals(Failure.Rejected(null), assertIs<Outcome.Failed>(made).failure)
        server.gateway.close()
    }

    @Test
    fun `B-182 an address or name that could be read as an option or a path never runs`() = runTest {
        val server = RowServer { error("nothing may run: ${it.path}") }
        for ((url, name) in
            listOf(
                "--upload-pack=touch /tmp/x" to "app",
                "https://example.test/a b" to "app",
                "https://example.test/a\nb" to "app",
                "https://example.test/a" to "../up",
                "https://example.test/a" to "a/b",
                "" to "app",
            )) {
            assertIs<Outcome.Failed>(server.gateway.cloneRepository("/srv/work", url, name), "$url / $name")
        }
        assertIs<Outcome.Failed>(server.gateway.cloneRepository("relative", "https://example.test/a", "a"))
        assertTrue(server.seen.isEmpty())
        server.gateway.close()
    }

    @Test
    fun `B-182 a quote in the name stays one shell word`() = runTest {
        val server = server(exit = 0)
        server.gateway.cloneRepository("/srv/work", "https://example.test/o/it's.git", "it's")
        val command = server.seen.first { it.method == "POST" }.json!!.jsonObject["command"]!!.jsonPrimitive.content
        assertEquals("git clone -q -- 'https://example.test/o/it'\\''s.git' 'it'\\''s'", command)
        server.gateway.close()
    }
}
