package io.github.eslamasabry.oc.protocol.codex.transport

import io.github.eslamasabry.oc.protocol.codex.CodexEndpoint
import io.github.eslamasabry.oc.protocol.codex.CodexException
import io.github.eslamasabry.oc.protocol.codex.CodexFailureKind
import io.github.eslamasabry.oc.protocol.codex.CodexWireJson
import io.github.eslamasabry.oc.protocol.codex.codexFail
import io.github.eslamasabry.oc.protocol.codex.encodeJson
import io.github.eslamasabry.oc.protocol.codex.intOrNull
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.stringOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The Codex app-server transport (pinned 0.153.4), a port of `lib/codex/transport.dart`: JSON-RPC, one message per
 * text frame. Connecting runs `initialize` then the `initialized` notification.
 *
 * Every state change runs on [lane]. Connection epochs fence everything: a frame, answer or timer of an older socket
 * is ignored; a lost socket fails its pending reads with `Disconnected` and its mutations with `DeliveryUnknown`, and a
 * mutation is never re-sent (B-AGENT-062). Remote error bodies never leave this class: an `error` answer becomes the
 * fixed `Unavailable` (or `Overloaded` for -32001).
 */
public class CodexTransport(
    public val endpoint: CodexEndpoint,
    secret: String,
    private val socketFactory: CodexSocketFactory,
    scope: CoroutineScope,
    public val lane: CoroutineDispatcher,
    public val requestTimeoutMs: Long = 15_000,
) {
    private var secret: String = secret
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val scope = CoroutineScope(scope.coroutineContext + job + lane)
    private val listeners = mutableListOf<CodexTransportListener>()
    private val pending = LinkedHashMap<Long, Pending>()
    private var socket: CodexSocket? = null
    private var reader: Job? = null
    private var connecting: Deferred<Int>? = null
    private var initialized = false
    private var nextId = 0L

    /** Bumped on every connect and every loss; a request stamped with an older epoch is stale. */
    public var epoch: Int = 0
        private set

    public var isClosed: Boolean = false
        private set

    public val connected: Boolean get() = socket != null && initialized

    /** Account methods are enabled only for the schema this client verified (the 0.153.4 `userAgent`). */
    public var accountApiSupported: Boolean = false

    /** Adds a synchronous observer; returns the remover. */
    public fun addListener(listener: CodexTransportListener): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    /** Connects and initializes (or joins the attempt in flight); throws [CodexException]. */
    public suspend fun connect() {
        withContext(lane) { connectReady() }
    }

    private suspend fun connectReady(): Int {
        if (isClosed) codexFail(CodexFailureKind.Disconnected)
        if (connected) return epoch
        connecting?.let { return it.await() }
        val attempt = scope.async(start = CoroutineStart.LAZY) {
            try {
                openSocket()
            } finally {
                connecting = null
            }
        }
        connecting = attempt
        return attempt.await()
    }

    private suspend fun openSocket(): Int {
        val myEpoch = ++epoch
        val opened = try {
            socketFactory.open(endpoint, secret)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CodexException) {
            throw e
        } catch (_: Throwable) {
            codexFail(CodexFailureKind.Disconnected)
        }
        if (isClosed || myEpoch != epoch) {
            opened.close()
            codexFail(CodexFailureKind.Disconnected)
        }
        socket = opened
        reader = scope.launch { readLoop(myEpoch, opened) }
        try {
            val answer = enqueue(
                "initialize",
                jsonOf(
                    "clientInfo" to mapOf("name" to "opencode_mobile", "version" to "1.0.0"),
                    "capabilities" to mapOf("experimentalApi" to false),
                ),
                mutation = false,
                myEpoch = myEpoch,
                requireInitialized = false,
            ).result.await()
            val agent = answer["userAgent"].stringOrNull()
            accountApiSupported = agent != null && PINNED_AGENT.containsMatchIn(agent)
            sendRaw(jsonOf("method" to "initialized", "params" to emptyMap<String, Any>()), myEpoch)
            initialized = true
            return myEpoch
        } catch (e: Throwable) {
            lost(myEpoch)
            throw e
        }
    }

    /** Connects when needed, then sends one request. */
    public suspend fun request(method: String, params: JsonObject, mutation: Boolean = false): JsonObject =
        withContext(lane) {
            val myEpoch = connectReady()
            enqueue(method, params, mutation, myEpoch, requireInitialized = true).result.await()
        }

    /** A scope-owned request: never triggers or waits for a new connection; a replaced socket fails it at once. */
    public suspend fun requestInEpoch(method: String, params: JsonObject, epoch: Int, mutation: Boolean = false): JsonObject =
        withContext(lane) { enqueue(method, params, mutation, epoch, requireInitialized = true).result.await() }

    private suspend fun enqueue(
        method: String,
        params: JsonObject,
        mutation: Boolean,
        myEpoch: Int,
        requireInitialized: Boolean,
    ): Pending {
        // A request that waited for an earlier connection is never sent on a replacement socket.
        if (isClosed || myEpoch != epoch || socket == null || (requireInitialized && !initialized)) {
            codexFail(if (mutation) CodexFailureKind.DeliveryUnknown else CodexFailureKind.Disconnected)
        }
        if (pending.size >= MAX_PENDING) codexFail(CodexFailureKind.Overloaded)
        val id = ++nextId
        val p = Pending(mutation)
        pending[id] = p
        p.timer = scope.launch {
            delay(requestTimeoutMs)
            if (pending.remove(id) != null) p.fail()
        }
        try {
            sendRaw(jsonOf("id" to id, "method" to method, "params" to params), myEpoch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CodexException) {
            pending.remove(id)
            p.fail()
            // An oversized frame was never written; any other send failure invalidates the socket.
            if (e.kind != CodexFailureKind.InvalidResponse) lost(myEpoch)
        }
        return p
    }

    /** Answers a server request; a request from an older socket (or after loss) fails `StaleRequest` (B-AGENT-064). */
    public suspend fun reply(request: CodexRpcEvent, result: JsonObject) {
        withContext(lane) {
            val id = request.requestId
            if (!connected || request.epoch != epoch || id == null) codexFail(CodexFailureKind.StaleRequest)
            sendRaw(jsonOf("id" to id, "result" to result), request.epoch)
        }
    }

    /** Refuses a server request this client does not implement (fire and forget, on the lane). */
    public fun rejectUnsupported(request: CodexRpcEvent) {
        val id = request.requestId ?: return
        if (!connected || request.epoch != epoch || !job.isActive) return
        scope.launch {
            try {
                sendRaw(jsonOf("id" to id, "error" to mapOf("code" to -32601, "message" to "Unsupported client request")), request.epoch)
            } catch (_: CodexException) {
                // The socket is gone; nothing to refuse any more.
            }
        }
    }

    private suspend fun sendRaw(value: JsonObject, expectedEpoch: Int) {
        val current = socket
        if (isClosed || current == null || expectedEpoch != epoch) codexFail(CodexFailureKind.Disconnected)
        val text = encodeJson(value)
        if (text.length > MAX_FRAME_BYTES || text.encodeToByteArray().size > MAX_FRAME_BYTES) {
            codexFail(CodexFailureKind.InvalidResponse)
        }
        try {
            current.send(text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            lost(expectedEpoch)
            codexFail(CodexFailureKind.Disconnected)
        }
    }

    private suspend fun readLoop(myEpoch: Int, from: CodexSocket) {
        try {
            for (frame in from.incoming) {
                if (myEpoch != epoch || isClosed) return
                receive(myEpoch, frame)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // An erroring socket is a lost socket; nothing of the error reaches logs.
        }
        lost(myEpoch)
    }

    private fun receive(myEpoch: Int, frame: String) {
        val json: JsonObject
        try {
            if (frame.length > MAX_FRAME_BYTES || frame.encodeToByteArray().size > MAX_FRAME_BYTES) {
                codexFail(CodexFailureKind.InvalidResponse)
            }
            json = CodexWireJson.parseToJsonElement(frame) as? JsonObject ?: codexFail(CodexFailureKind.InvalidResponse)
        } catch (_: Exception) {
            lost(myEpoch) // malformed JSON drops the connection
            return
        }
        val id = json["id"]
        val validId = id == null || id is kotlinx.serialization.json.JsonNull ||
            (id is JsonPrimitive && (id.isString || id.intOrNull() != null))
        if (!validId) {
            lost(myEpoch)
            return
        }
        val requestId = id?.takeUnless { it is kotlinx.serialization.json.JsonNull }
        val method = json["method"]
        if (method is JsonPrimitive && method.isString) {
            val params = json["params"]
            if (method.content.isEmpty() || method.content.length > 200 || params !is JsonObject) {
                lost(myEpoch)
                return
            }
            val event = CodexRpcEvent(myEpoch, method.content, params, requestId)
            for (listener in listeners.toList()) listener.onEvent(event)
            return
        }
        val numeric = requestId.intOrNull() ?: return
        val p = pending.remove(numeric) ?: return
        p.timer?.cancel()
        val error = json["error"]
        val result = json["result"]
        when {
            error is JsonObject ->
                p.fail(
                    CodexException(
                        if (error["code"].intOrNull() == -32001L) CodexFailureKind.Overloaded else CodexFailureKind.Unavailable,
                    ),
                )
            result is JsonObject -> p.result.complete(result)
            else -> p.fail(CodexException(CodexFailureKind.InvalidResponse))
        }
    }

    /** Never suspends: safe from the reader, a timer or a failed send. */
    private fun lost(lostEpoch: Int) {
        if (lostEpoch != epoch) return
        val current = socket ?: return
        val disconnectedEpoch = epoch
        epoch++ // invalidate frames and callbacks of the old socket before a reconnect allocates a new epoch
        socket = null
        initialized = false
        val oldReader = reader
        reader = null
        oldReader?.cancel()
        if (job.isActive) scope.launch { current.close() }
        val waiting = pending.values.toList()
        pending.clear()
        for (p in waiting) p.fail()
        if (!isClosed) for (listener in listeners.toList()) listener.onDisconnect(disconnectedEpoch)
    }

    /** Idempotent. Fails pending requests, forgets the secret, stops every coroutine this transport started. */
    public suspend fun close() {
        withContext(lane) {
            if (isClosed) return@withContext
            isClosed = true
            secret = ""
            val current = socket
            lost(epoch)
            try {
                current?.close()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Closing cannot expose the server's close reason.
            }
            listeners.clear()
            job.cancel()
        }
    }

    private class Pending(val mutation: Boolean) {
        val result = CompletableDeferred<JsonObject>()
        var timer: Job? = null

        fun fail(error: CodexException = CodexException(if (mutation) CodexFailureKind.DeliveryUnknown else CodexFailureKind.Disconnected)) {
            timer?.cancel()
            result.completeExceptionally(error)
        }
    }

    public companion object {
        public const val MAX_FRAME_BYTES: Int = 4 * 1024 * 1024
        public const val MAX_PENDING: Int = 64

        /** The verified app-server build (Dart `RegExp(r'(^|/)0\.153\.4(?:\s|$)')`). */
        private val PINNED_AGENT = Regex("""(^|/)0\.153\.4(?:\s|$)""")
    }
}
