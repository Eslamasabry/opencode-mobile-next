package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.protocol.codex.gateway.CodexGateway
import io.github.eslamasabry.oc.protocol.codex.transport.CodexSocket
import io.github.eslamasabry.oc.protocol.codex.transport.CodexSocketFactory
import io.github.eslamasabry.oc.protocol.codex.transport.CodexTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * An in-memory Codex app-server (port of the Dart test `_Socket` fakes): records every frame, answers by method, can
 * hold one answer per method for a gate, push notifications and server requests, answer with an error, or drop.
 */
class FakeAppServer(var userAgent: String = "codex/0.153.4") : CodexSocket {
    private val channel = Channel<String>(Channel.UNLIMITED)
    override val incoming get() = channel
    val sent = mutableListOf<JsonObject>()
    var closed = false

    /** Answers by method; null = stay silent. */
    val answers = mutableMapOf<String, (JsonObject) -> JsonObject?>()

    /** Methods answered with a JSON-RPC error of this code (with a private remote message that must never escape). */
    val errors = mutableMapOf<String, Int>()

    /** Runs before the answer (to push notifications that arrive first). */
    var beforeAnswer: ((JsonObject) -> Unit)? = null

    /** The next request of each method here is held until [release]. */
    val gated = mutableSetOf<String>()
    private val held = mutableMapOf<String, () -> Unit>()

    init {
        answers["initialize"] = { jsonOf("userAgent" to userAgent) }
    }

    override suspend fun send(text: String) {
        if (closed) error("closed")
        val frame = CodexWireJson.parseToJsonElement(text) as JsonObject
        sent += frame
        val id = frame["id"] ?: return
        val method = frame["method"].stringOrNull() ?: return
        beforeAnswer?.invoke(frame)
        val respond = { answer(id, method, frame) }
        if (gated.remove(method)) held[method] = respond else respond()
    }

    private fun answer(id: JsonElement, method: String, frame: JsonObject) {
        errors[method]?.let {
            push(jsonOf("id" to id, "error" to mapOf("code" to it, "message" to "Synthetic private remote detail sk-fixture-secret-0123456789")))
            return
        }
        val handler = answers[method] ?: { JsonObject(emptyMap()) }
        handler(frame)?.let { result(id, it) }
    }

    fun isHeld(method: String): Boolean = method in held

    fun release(method: String) {
        held.remove(method)?.invoke()
    }

    fun result(id: JsonElement, value: JsonObject) = push(jsonOf("id" to id, "result" to value))

    fun event(method: String, params: JsonObject, id: Any? = null) =
        push(if (id == null) jsonOf("method" to method, "params" to params) else jsonOf("method" to method, "params" to params, "id" to id))

    fun push(frame: JsonObject) {
        if (!closed) channel.trySend(encodeJson(frame))
    }

    fun calls(method: String): List<JsonObject> = sent.filter { it["method"].stringOrNull() == method }

    fun methods(): List<String?> = sent.map { it["method"].stringOrNull() }

    /** The network dropped the socket. */
    fun drop() {
        closed = true
        channel.close()
    }

    override suspend fun close() = drop()
}

/** Hands out [servers] in order; the last one repeats. */
class FakeSockets(vararg servers: FakeAppServer) : CodexSocketFactory {
    private val queue = servers.toMutableList()
    var opened = 0
    var refuseAfterFirst: CodexFailureKind? = null

    override suspend fun open(endpoint: CodexEndpoint, secret: String): CodexSocket {
        opened++
        if (opened > 1) refuseAfterFirst?.let { throw CodexException(it) }
        return if (queue.size > 1) queue.removeAt(0) else queue.first()
    }
}

const val DIR = "/project"

fun endpoint(raw: String = "wss://fixture.invalid"): CodexEndpoint = CodexEndpoint.parse(raw)!!

fun TestScope.transport(
    sockets: CodexSocketFactory,
    scope: CoroutineScope = backgroundScope,
    requestTimeoutMs: Long = 15_000,
): CodexTransport =
    CodexTransport(endpoint(), "fixture-token", sockets, scope, StandardTestDispatcher(testScheduler), requestTimeoutMs)

fun TestScope.gateway(sockets: CodexSocketFactory, directory: String? = DIR, requestTimeoutMs: Long = 15_000): CodexGateway =
    CodexGateway(transport(sockets, requestTimeoutMs = requestTimeoutMs), backgroundScope, TestClock(), directory)

fun JsonObject.params(): JsonObject = this["params"] as JsonObject

fun JsonObject.str(key: String): String? = this[key].stringOrNull()

/** A thread as app-server 0.153.4 returns it. */
fun thread(
    id: String = "thread-1",
    cwd: String = DIR,
    turns: List<JsonObject> = emptyList(),
    busy: Boolean = false,
    name: String = "Fixture thread",
): JsonObject =
    jsonOf(
        "id" to id,
        "cwd" to cwd,
        "name" to name,
        "createdAt" to 100,
        "updatedAt" to 101,
        "status" to mapOf("type" to if (busy) "active" else "idle"),
        "turns" to turns,
    )

fun turn(id: String = "turn-1", status: String = "completed", prefix: String? = null): JsonObject {
    val user = prefix?.let { "$it-user" } ?: "user-1"
    val agent = prefix?.let { "$it-agent" } ?: "agent-1"
    return jsonOf(
        "id" to id,
        "status" to status,
        "startedAt" to 100,
        "completedAt" to if (status == "completed") 101 else null,
        "items" to listOf(
            mapOf("id" to user, "type" to "userMessage", "content" to listOf(mapOf("type" to "text", "text" to "Hello"))),
            mapOf("id" to agent, "type" to "agentMessage", "text" to "Hello back"),
        ),
    )
}

/** Answers thread reads/resumes/starts with [current] and turn/start with an in-progress turn. */
fun FakeAppServer.serveThread(current: () -> JsonObject, newTurn: String = "turn-1"): FakeAppServer {
    answers["thread/list"] = { jsonOf("data" to listOf(current()), "nextCursor" to null) }
    answers["thread/read"] = { jsonOf("thread" to current()) }
    answers["thread/resume"] = { jsonOf("thread" to current()) }
    answers["thread/start"] = { jsonOf("thread" to current()) }
    answers["turn/start"] = { jsonOf("turn" to turn(newTurn, "inProgress")) }
    return this
}
