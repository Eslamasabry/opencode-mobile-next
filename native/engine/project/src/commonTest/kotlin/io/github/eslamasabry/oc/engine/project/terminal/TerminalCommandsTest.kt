package io.github.eslamasabry.oc.engine.project.terminal

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTerminalWire(
    override var location: TerminalLocation,
    val reply: (TerminalWireRequest) -> Outcome<JsonElement?>,
) : TerminalWire {
    val requests = mutableListOf<TerminalWireRequest>()

    override suspend fun send(request: TerminalWireRequest): Outcome<JsonElement?> {
        requests += request
        return reply(request)
    }
}

private val ended =
    TerminalProcess(
        id = "pty_old",
        title = "Build",
        command = "/bin/tool",
        arguments = listOf("--label", "two words", "\$(literal)"),
        directory = "/project/child",
        running = false,
        pid = 17,
        exitCode = 0,
    )

private val response: JsonObject =
    buildJsonObject {
        put("id", "pty_new")
        put("title", "Build")
        put("command", "/bin/tool")
        putJsonArray("args") { ended.arguments.forEach { add(JsonPrimitive(it)) } }
        put("cwd", "/project/child")
        put("status", "running")
        put("pid", 18)
    }

class TerminalCommandsTest {
    @Test
    fun `B-LINUX-222 closing an OpenCode 2 terminal that is already gone succeeds`() = runTest {
        val wire =
            FakeTerminalWire(TerminalLocation("/project")) {
                Failure.NotFound().failed()
            }
        val result = Oc2TerminalCommands(wire).closeTerminal("pty_gone")
        assertEquals(Outcome.Ok(Unit), result)
        assertEquals("DELETE", wire.requests.single().method)
        assertEquals("/api/pty/pty_gone", wire.requests.single().path)
        // A transport that keeps the status reads the same.
        val rejected = FakeTerminalWire(TerminalLocation("/project")) { Failure.Rejected(status = 404).failed() }
        assertEquals(Outcome.Ok(Unit), Oc2TerminalCommands(rejected).closeTerminal("pty_gone"))
    }

    @Test
    fun `B-LINUX-222 any other failure to close still says so`() = runTest {
        val wire = FakeTerminalWire(TerminalLocation("/project")) { Failure.Server(status = 500).failed() }
        val result = Oc2TerminalCommands(wire).closeTerminal("pty_live")
        assertIs<Outcome.Failed>(result)
        assertEquals(Failure.Server(status = 500), result.failure)
        val empty = FakeTerminalWire(TerminalLocation("/p")) { null.ok() }
        assertEquals(Outcome.Ok(Unit), Oc2TerminalCommands(empty).closeTerminal("x"))
    }

    @Test
    fun `B-LINUX-225 reruns a reported command in a new scoped PTY`() = runTest {
        val wire =
            FakeTerminalWire(TerminalLocation("/project", "workspace-test")) {
                buildJsonObject { put("data", response) }.ok()
            }
        val gateway: TerminalCommandGateway = Oc2TerminalCommands(wire)
        val result = gateway.rerunTerminalCommand(ended)
        assertIs<TerminalRerunResult.Started>(result)
        assertEquals("pty_new", result.process.id)
        assertTrue(result.process.running)
        assertEquals(ended.arguments, result.process.arguments)
        assertEquals(1, wire.requests.size)
        val request = wire.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/pty", request.path)
        assertEquals(
            mapOf("location[directory]" to "/project", "location[workspace]" to "workspace-test"),
            request.query,
        )
        assertEquals(
            buildJsonObject {
                put("command", ended.command)
                put("args", JsonArray(ended.arguments.map(::JsonPrimitive)))
                put("cwd", ended.directory)
                put("title", ended.title)
            },
            request.body,
        )
        assertTrue(TerminalLifecycleCapabilities.supportsTerminalCommandRerun(terminal = true, gateway = gateway))
        assertFalse(TerminalLifecycleCapabilities.supportsTerminalCommandRerun(terminal = false, gateway = gateway))
    }

    @Test
    fun `B-LINUX-225 a failed response is sanitized and never retried`() = runTest {
        val wire =
            FakeTerminalWire(TerminalLocation("/project")) {
                Failure.Server(status = 500).failed()
            }
        val result = Oc2TerminalCommands(wire).rerunTerminalCommand(ended)
        assertEquals(TerminalRerunResult.Unconfirmed, result)
        assertEquals(1, wire.requests.size)
        // A reply that is not a valid terminal is the same unconfirmed result.
        val malformed =
            FakeTerminalWire(TerminalLocation("/project")) {
                buildJsonObject { put("data", buildJsonObject { put("id", "pty_new") }) }.ok()
            }
        assertEquals(TerminalRerunResult.Unconfirmed, Oc2TerminalCommands(malformed).rerunTerminalCommand(ended))
        assertEquals(1, malformed.requests.size)
    }

    @Test
    fun `B-LINUX-225 missing facet and missing wire timing stay unavailable`() = runTest {
        assertFalse(TerminalLifecycleCapabilities.supportsTerminalCommandRerun(terminal = true, gateway = null))
        assertFalse(TerminalLifecycleCapabilities.TERMINAL_AGE)
        assertFalse(TerminalLifecycleCapabilities.EXTERNAL_COMMAND_TIME_LEFT)
        val active = TerminalProcess("pty_active", "", "/bin/sh", emptyList(), "/project", running = true, pid = 7)
        val missing = TerminalProcess("pty_missing", "", "", emptyList(), "/project", running = false, pid = 7)
        val nul = ended.copy(arguments = listOf("a\u0000b"))
        assertNull(terminalRerunSnapshot(active))
        assertNull(terminalRerunSnapshot(missing))
        assertNull(terminalRerunSnapshot(nul))
        assertEquals(ended, terminalRerunSnapshot(ended))
        val wire = FakeTerminalWire(TerminalLocation("/project")) { error("no request expected") }
        assertEquals(TerminalRerunResult.CannotRerun, Oc2TerminalCommands(wire).rerunTerminalCommand(active))
        assertTrue(wire.requests.isEmpty())
    }

    @Test
    fun `B-LINUX-225 a stale project location is rejected without dispatching a request`() = runTest {
        val wire = FakeTerminalWire(TerminalLocation("/project")) { error("no request expected") }
        val gateway = Oc2TerminalCommands(wire)
        wire.location = TerminalLocation("/different")
        assertEquals(TerminalRerunResult.LocationChanged, gateway.rerunTerminalCommand(ended))
        assertTrue(wire.requests.isEmpty())
        val nowhere = FakeTerminalWire(TerminalLocation(null)) { error("no request expected") }
        assertEquals(TerminalRerunResult.LocationChanged, Oc2TerminalCommands(nowhere).rerunTerminalCommand(ended))
        assertTrue(nowhere.requests.isEmpty())
    }
}
