package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoFrame
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoSocket
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoSocketFactory
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A scripted daemon, port of the Dart `FakeDaemon` (test/paseo_gateway_test.dart): answers `hello`, records every
 * session message, and lets a test reply or push at will. Shapes follow frames captured from Paseo 0.8.0–0.11.2.
 * Frames are queued at once; the transport reads them when the test scheduler runs.
 */
class FakeDaemon : PaseoSocket {
    private val channel = Channel<PaseoFrame>(Channel.UNLIMITED)
    override val incoming get() = channel
    val sent = mutableListOf<JsonObject>()
    var hello: JsonObject? = null
    var closed = false
    var version = "0.8.0"
    var features: JsonObject? = null

    /** When set, `hello` is answered by closing with this code. */
    var rejectWith: Int? = null

    /** When set, `hello` is answered by a `hello.rejected` frame with this reason, then the matching close. */
    var rejectReason: String? = null

    /** When true, `hello` gets no answer at all. */
    var silent = false

    /** When set, sending throws (a broken pipe). */
    var failSends = false
    override var closeCode: Int? = null

    /** Auto-replies by request type: return (response type, payload) or null to stay silent. */
    val handlers = mutableMapOf<String, (JsonObject) -> Pair<String, JsonObject>?>()

    override suspend fun send(text: String) {
        if (failSends) error("broken pipe")
        val json = PaseoWireJson.parseToJsonElement(text) as JsonObject
        when (json.str("type")) {
            "hello" -> {
                hello = json
                if (silent) return
                rejectReason?.let {
                    pushRaw(jsonOf("type" to "hello.rejected", "reason" to it, "accepts" to listOf("password")))
                    closeWith(PaseoTransport.CLOSE_AUTH_FAILED)
                    return
                }
                rejectWith?.let {
                    closeWith(it)
                    return
                }
                push(
                    "status",
                    jsonOf("status" to "server_info", "version" to version, "features" to features),
                )
            }
            "ping" -> Unit
            else -> {
                val inner = json.obj("message")!!
                sent += inner
                val reply = handlers[inner.str("type")]?.invoke(inner) ?: return
                push(reply.first, reply.second + mapOf("requestId" to (inner["requestId"] ?: JsonPrimitive(""))))
            }
        }
    }

    fun closeWith(code: Int) {
        closeCode = code
        closed = true
        channel.close()
    }

    /** A top-level frame that is not a session message. */
    fun pushRaw(frame: JsonObject) {
        if (!closed) channel.trySend(PaseoFrame.Text(PaseoWireJson.encodeToString(JsonObject.serializer(), frame)))
    }

    fun pushText(text: String) {
        if (!closed) channel.trySend(PaseoFrame.Text(text))
    }

    fun push(type: String, payload: JsonObject) =
        pushRaw(jsonOf("type" to "session", "message" to mapOf("type" to type, "payload" to payload)))

    fun pushBinary(opcode: Int, slot: Int, payload: ByteArray) {
        if (!closed) channel.trySend(PaseoFrame.Binary(byteArrayOf(opcode.toByte(), slot.toByte()) + payload))
    }

    /** The network dropped the socket (no close code). */
    fun drop() {
        closed = true
        channel.close()
    }

    fun of(type: String): List<JsonObject> = sent.filter { it.str("type") == type }

    override suspend fun close() {
        closed = true
        channel.close()
    }
}

/** A factory handing out [daemons] in order (the last one repeats), recording what it was asked. */
class FakeSockets(vararg daemons: FakeDaemon) : PaseoSocketFactory {
    private val queue = daemons.toMutableList()
    val opened = mutableListOf<Pair<PaseoEndpoint, String>>()
    var refuse: PaseoException? = null

    override suspend fun open(endpoint: PaseoEndpoint, password: String): PaseoSocket {
        opened += endpoint to password
        refuse?.let { throw it }
        return if (queue.size > 1) queue.removeAt(0) else queue.first()
    }
}

const val DIR = "/work/app"

fun TestScope.transport(
    sockets: PaseoSocketFactory,
    password: String = "",
    scope: CoroutineScope = backgroundScope,
    requestTimeoutMs: Long = 20_000,
): PaseoTransport =
    PaseoTransport(
        endpoint = "ws://127.0.0.1:6767",
        password = password,
        socketFactory = sockets,
        scope = scope,
        lane = StandardTestDispatcher(testScheduler),
        requestTimeoutMs = requestTimeoutMs,
    )

fun TestScope.transport(daemon: FakeDaemon, password: String = ""): PaseoTransport = transport(FakeSockets(daemon), password)

fun JsonObject.list(key: String): JsonArray = this[key] as JsonArray
