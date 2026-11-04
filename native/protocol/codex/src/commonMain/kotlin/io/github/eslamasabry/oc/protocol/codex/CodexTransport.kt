package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** One connection epoch; no request or mutation is automatically retried. */
public class CodexTransport(
    private val endpoint: String,
    private val token: String,
    private val sockets: CodexSocketFactory,
    private val scope: CoroutineScope,
    private val lane: CoroutineDispatcher,
    private val timeoutMs: Long = 15_000,
) {
    private data class Pending(val mutation: Boolean, val reply: CompletableDeferred<JsonObject>)

    private val connecting = Mutex()
    private val pending = mutableMapOf<Long, Pending>()
    private var nextId = 0L
    private var socket: CodexSocket? = null
    private var reader: Job? = null
    private var closed = false
    public var epoch: Int = 0
        private set

    public var connected: Boolean = false
        private set

    public var accountSupported: Boolean = false
        private set

    public var serverVersion: String? = null
        private set

    public var onPush: suspend (CodexPush) -> Unit = {}
    public var onDisconnect: suspend (Int) -> Unit = {}

    public suspend fun connect(): Unit =
        withContext(lane) {
            connecting.withLock {
                if (closed) throw CodexWireException(Failure.ComputerDidNotAnswer())
                if (connected) return@withLock
                if (!validCodexEndpoint(endpoint))
                    throw CodexWireException(Failure.Refused(RefusalReason.InvalidAddress))
                val opened = sockets.open(endpoint, token)
                if (closed) {
                    opened.close()
                    throw CodexWireException(Failure.ComputerDidNotAnswer())
                }
                val generation = ++epoch
                socket = opened
                reader =
                    scope.launch(lane) {
                        try {
                            for (frame in opened.incoming) {
                                if (generation != epoch || closed) break
                                receive(generation, frame)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            /* The boundary maps every transport failure to fixed values. */
                        } finally {
                            lost(generation)
                        }
                    }
                try {
                    val result =
                        requestRaw(
                            "initialize",
                            json(
                                "clientInfo" to
                                    json("name" to s("opencode_mobile"), "version" to s("1.0.0")),
                                "capabilities" to json("experimentalApi" to JsonPrimitive(false)),
                            ),
                            false,
                            generation,
                        )
                    val agent = result.text("userAgent")
                    serverVersion = agent?.let {
                        Regex("(?:^|/)([0-9]+\\.[0-9]+\\.[0-9]+)(?:\\s|$)")
                            .find(it)
                            ?.groupValues
                            ?.get(1)
                    }
                    accountSupported = serverVersion == "0.153.4"
                    send(json("method" to s("initialized"), "params" to json()), generation)
                    connected = true
                } catch (e: Exception) {
                    lost(generation)
                    throw e
                }
            }
        }

    public suspend fun request(
        method: String,
        params: JsonObject,
        mutation: Boolean = false,
        expectedEpoch: Int = epoch,
    ): JsonObject =
        withContext(lane) {
            if (!connected || expectedEpoch != epoch || closed) {
                if (mutation) unknown() else throw CodexWireException(Failure.ComputerDidNotAnswer())
            }
            requestRaw(method, params, mutation, expectedEpoch)
        }

    private suspend fun requestRaw(
        method: String,
        params: JsonObject,
        mutation: Boolean,
        generation: Int,
    ): JsonObject {
        if (pending.size >= 64) throw CodexWireException(Failure.Busy())
        if (generation != epoch || socket == null) {
            if (mutation) unknown() else throw CodexWireException(Failure.ComputerDidNotAnswer())
        }
        val id = ++nextId
        val reply = CompletableDeferred<JsonObject>()
        pending[id] = Pending(mutation, reply)
        try {
            try {
                send(
                    json("id" to JsonPrimitive(id), "method" to s(method), "params" to params),
                    generation,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                lost(generation)
                if (mutation) unknown() else throw CodexWireException(Failure.ComputerDidNotAnswer())
            }
            return withTimeoutOrNull(timeoutMs) { reply.await() }
                ?: if (mutation) unknown() else throw CodexWireException(Failure.ComputerDidNotAnswer())
        } finally {
            pending.remove(id)
        }
    }

    public suspend fun reply(push: CodexPush, result: JsonObject): Unit =
        withContext(lane) {
            if (!connected || push.epoch != epoch || push.id == null) stale()
            send(json("id" to push.id, "result" to result), push.epoch)
        }

    public suspend fun reject(push: CodexPush): Unit =
        withContext(lane) {
            if (!connected || push.epoch != epoch || push.id == null) return@withContext
            send(
                json(
                    "id" to push.id,
                    "error" to
                        json(
                            "code" to JsonPrimitive(-32601),
                            "message" to s("Unsupported client request"),
                        ),
                ),
                push.epoch,
            )
        }

    private suspend fun send(value: JsonObject, generation: Int) {
        val opened = socket
        if (closed || generation != epoch || opened == null)
            throw CodexWireException(Failure.ComputerDidNotAnswer())
        val frame = value.toString()
        if (frame.encodeToByteArray().size > MAX_FRAME_BYTES) malformed()
        opened.send(frame)
    }

    private suspend fun receive(generation: Int, frame: String) {
        if (frame.length > MAX_FRAME_BYTES || frame.encodeToByteArray().size > MAX_FRAME_BYTES)
            malformed()
        val value = Json.parseToJsonElement(frame).obj()
        val id = value["id"]?.takeUnless { it == JsonNull }
        if (id != null && (id !is JsonPrimitive || (!id.isString && id.longOrNull == null)))
            malformed()
        val method = value["method"]
        if (method != null) {
            onPush(CodexPush(generation, method.str(200), value["params"].obj(), id))
            return
        }
        val requestId = (id as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: return
        val target = pending.remove(requestId) ?: return
        if (value["error"] is JsonObject) {
            val code = (value["error"].obj()["code"] as? JsonPrimitive)?.intOrNull
            val failure =
                if (code == -32001) Failure.Busy()
                else Failure.Unavailable(FeatureId("codexAction"))
            target.reply.completeExceptionally(CodexWireException(failure))
        } else {
            val result = value["result"] as? JsonObject
            if (result == null)
                target.reply.completeExceptionally(CodexWireException(Failure.UnreadableReply()))
            else target.reply.complete(result)
        }
    }

    private suspend fun lost(generation: Int) {
        if (generation != epoch || socket == null) return
        val opened = socket
        socket = null
        connected = false
        accountSupported = false
        epoch++
        for (request in pending.values) request.reply.completeExceptionally(
            CodexWireException(
                if (request.mutation) Failure.Refused(RefusalReason.DispatchUncertain)
                else Failure.ComputerDidNotAnswer()
            )
        )
        pending.clear()
        scope.launch(lane) {
            try {
                opened?.close()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        if (!closed) onDisconnect(generation)
    }

    public suspend fun close(): Unit =
        withContext(lane) {
            if (closed) return@withContext
            closed = true
            lost(epoch)
            reader?.cancel()
            reader = null
        }

    public companion object {
        public const val MAX_FRAME_BYTES: Int = 4 * 1024 * 1024
    }
}

/** IDs retain their JSON type so numeric and textual approval handles never collide. */
public class CodexPush(
    public val epoch: Int,
    public val method: String,
    public val params: JsonObject,
    public val id: JsonElement?,
)
