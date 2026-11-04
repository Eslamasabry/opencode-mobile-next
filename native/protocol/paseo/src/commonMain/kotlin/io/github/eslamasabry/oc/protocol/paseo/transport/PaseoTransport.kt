package io.github.eslamasabry.oc.protocol.paseo.transport

import io.github.eslamasabry.oc.protocol.paseo.PaseoEndpoint
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.PaseoTestedVersions
import io.github.eslamasabry.oc.protocol.paseo.PaseoWireJson
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The Paseo daemon transport (protocol 1), a port of `lib/paseo/transport.dart` (plan 04 §8).
 *
 * One WebSocket at `/ws`, one JSON object per text frame. The client opens with `hello`; the daemon
 * answers with a `server_info` status within 8 s. Every later message travels inside
 * `{"type":"session","message":{…}}`. Requests carry a `requestId` (`m<epoch>_<n>`) and any payload
 * repeating it settles the request.
 *
 * Concurrency: every state change runs on [lane] (one serial lane, native/AGENTS.md rule 4); public
 * calls hop onto it. Connection epochs fence everything: a frame, answer or timer of an older
 * socket is ignored, and a lost socket fails its pending reads with `Disconnected` and its
 * mutations with `DeliveryUnknown` (never retried).
 */
public class PaseoTransport(
    endpoint: String,
    password: String = "",
    private val socketFactory: PaseoSocketFactory,
    scope: CoroutineScope,
    private val lane: CoroutineDispatcher,
    /** Where frames of [BACKGROUND_DECODE_CHARS] or more are decoded (AppDispatchers.default). */
    private val decodeDispatcher: CoroutineDispatcher = lane,
    public val requestTimeoutMs: Long = 20_000,
    clientId: String? = null,
    random: Random = Random.Default,
    /** Set only on the private bounded-history connection. */
    private val boundedFrameBytes: Int? = null,
    /**
     * Reads the stored password at connect time when [password] is empty (the profile's
     * CredentialReader, plan 04 §4.8). Null or a failed read means no password.
     */
    private val passwordSource: (suspend () -> String?)? = null,
) {
    public val endpoint: PaseoEndpoint = PaseoEndpoint.parse(endpoint)
    public val clientId: String = clientId ?: newClientId(random)
    private var password: String = password
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val scope = CoroutineScope(scope.coroutineContext + job + lane)
    private val listeners = mutableListOf<PaseoTransportListener>()
    private val pending = LinkedHashMap<String, Pending>()
    private var socket: PaseoSocket? = null
    private var reader: Job? = null
    private var keepAlive: Job? = null
    private var connecting: Deferred<Int>? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var hold: CompletableDeferred<Unit>? = null
    private var helloRejection: PaseoException? = null
    private var initialized = false
    private var nextId = 0

    /** Bumped on every connect and every loss; a request stamped with an older epoch is stale. */
    public var epoch: Int = 0
        private set

    public var isClosed: Boolean = false
        private set

    /**
     * Daemon version from `server_info` (a plain semver), null before the handshake and after a
     * loss.
     */
    public var serverVersion: String? = null
        private set

    /** Negotiated writer support (`server_info.features.workspaceFileEditing`), reset on loss. */
    public var workspaceFileEditing: Boolean = false
        private set

    /**
     * Fixed wording for the last refused request (diagnostics and tests only; never daemon text).
     */
    public var lastDaemonError: String? = null
        private set

    /** Text received on the current socket (counts only, for the radio audit). */
    public var bytesIn: Long = 0
        private set

    public val connected: Boolean
        get() = socket != null && initialized

    /** Adds a synchronous observer; returns the remover. */
    public fun addListener(listener: PaseoTransportListener): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    public suspend fun connect() {
        withContext(lane) { connectReady() }
    }

    private suspend fun connectReady(): Int {
        if (isClosed) paseoFail(PaseoFailureKind.Disconnected)
        if (connected) return epoch
        val inFlight = connecting
        if (inFlight != null) return inFlight.await()
        val attempt =
            scope.async(start = CoroutineStart.LAZY) {
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
        serverVersion = null
        workspaceFileEditing = false
        helloRejection = null
        val myEpoch = ++epoch
        if (password.isEmpty() && passwordSource != null) {
            password = passwordSource.invoke().orEmpty()
            if (
                !io.github.eslamasabry.oc.protocol.paseo.PaseoProfileRules.passwordAcceptable(
                    password
                )
            ) {
                password = ""
                paseoFail(PaseoFailureKind.Authentication)
            }
            if (isClosed || myEpoch != epoch) paseoFail(PaseoFailureKind.Disconnected)
        }
        val opened =
            try {
                socketFactory.open(endpoint, password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PaseoException) {
                throw e
            } catch (_: Throwable) {
                paseoFail(PaseoFailureKind.Disconnected)
            }
        if (isClosed || myEpoch != epoch) {
            opened.close()
            paseoFail(PaseoFailureKind.Disconnected)
        }
        socket = opened
        val handshake = CompletableDeferred<Unit>()
        ready = handshake
        reader = scope.launch { readLoop(myEpoch, opened) }
        try {
            sendRaw(helloFrame(), myEpoch)
            withTimeout(HELLO_TIMEOUT_MS) { handshake.await() }
            if (isClosed || myEpoch != epoch) paseoFail(PaseoFailureKind.Disconnected)
            initialized = true
            keepAlive = scope.launch {
                while (true) {
                    delay(KEEPALIVE_MS)
                    try {
                        sendRaw(jsonOf("type" to "ping"), myEpoch)
                    } catch (_: PaseoException) {
                        return@launch // sendRaw already invalidated the socket
                    }
                }
            }
            return myEpoch
        } catch (e: Throwable) {
            lost(myEpoch)
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            throw e as? PaseoException ?: PaseoException(PaseoFailureKind.Disconnected)
        }
    }

    private fun helloFrame(): JsonObject =
        jsonOf(
                "type" to "hello",
                "clientId" to clientId,
                "clientType" to "mobile",
                "protocolVersion" to 1,
                "appVersion" to CLIENT_APP_VERSION,
                // The in-protocol credential (daemons before 0.10 ignore it); the header form still
                // travels too.
                "auth" to
                    password
                        .takeIf { it.isNotEmpty() }
                        ?.let { mapOf("kind" to "password", "password" to it) },
                // Only what this client implements. Never `explicit_event_subscriptions`:
                // permission and stream events
                // would stop arriving (plan 04 §8).
                "capabilities" to
                    mapOf(
                        "all_providers" to true,
                        "hello_rejection" to true,
                        "provider_subagents" to true,
                        "projected_subagent_timeline" to true,
                    ),
            )
            .let { hello ->
                JsonObject(hello.filterValues { it != kotlinx.serialization.json.JsonNull })
            }

    /**
     * Sends a session request and returns the answer payload. A read that times out or loses its
     * socket fails `Disconnected` (the caller may re-read); a [mutation] fails `DeliveryUnknown`
     * and is never retried. At most [MAX_PENDING] requests wait; the next fails `Overloaded`
     * without writing. [beforeSend] runs after the connection is ready and immediately before the
     * frame is written (a final fence: it may throw to refuse).
     */
    public suspend fun request(
        type: String,
        body: JsonObject,
        mutation: Boolean = false,
        timeoutMs: Long? = null,
        expectedEpoch: Int? = null,
        beforeSend: (() -> Unit)? = null,
        onProgress: ((String, JsonObject) -> Unit)? = null,
        answerErrors: Boolean = false,
    ): JsonObject =
        withContext(lane) {
            val p =
                enqueue(
                    type,
                    body,
                    mutation,
                    timeoutMs,
                    expectedEpoch,
                    beforeSend,
                    onProgress,
                    answerErrors,
                    holds = false,
                )
            p.result.await()
        }

    /**
     * [request], then [read] runs on the answer while later frames wait (Dart `holdingMessages`): a
     * long history is mapped off the lane and the frames that arrived meanwhile are handled after
     * it, in order.
     */
    public suspend fun <T> requestThen(
        type: String,
        body: JsonObject,
        timeoutMs: Long? = null,
        read: suspend (JsonObject) -> T,
    ): T =
        withContext(lane) {
            val p = enqueue(type, body, false, timeoutMs, null, null, null, false, holds = true)
            try {
                read(p.result.await())
            } finally {
                p.released.complete(Unit)
            }
        }

    private suspend fun enqueue(
        type: String,
        body: JsonObject,
        mutation: Boolean,
        timeoutMs: Long?,
        expectedEpoch: Int?,
        beforeSend: (() -> Unit)?,
        onProgress: ((String, JsonObject) -> Unit)?,
        answerErrors: Boolean,
        holds: Boolean,
    ): Pending {
        if (expectedEpoch != null && (!connected || expectedEpoch != epoch))
            paseoFail(PaseoFailureKind.StaleRequest)
        val myEpoch = connectReady()
        if (expectedEpoch != null && myEpoch != expectedEpoch)
            paseoFail(PaseoFailureKind.StaleRequest)
        if (isClosed || myEpoch != epoch || socket == null || !initialized) {
            paseoFail(
                if (mutation) PaseoFailureKind.DeliveryUnknown else PaseoFailureKind.Disconnected
            )
        }
        if (boundedFrameBytes != null && !PaseoTestedVersions.paseoSupports(serverVersion)) {
            paseoFail(PaseoFailureKind.Unavailable)
        }
        if (pending.size >= MAX_PENDING) paseoFail(PaseoFailureKind.Overloaded)
        beforeSend?.invoke()
        val id = "m${myEpoch}_${++nextId}"
        val p = Pending(mutation, onProgress, answerErrors, holds)
        pending[id] = p
        p.timer = scope.launch {
            delay(timeoutMs ?: requestTimeoutMs)
            if (pending.remove(id) != null) p.fail()
        }
        try {
            sendRaw(
                JsonObject(
                    mapOf("type" to JsonPrimitive("session"), "message" to body.withType(type, id))
                ),
                myEpoch,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            if (pending.remove(id) != null) p.fail()
        }
        return p
    }

    /** Sends a session message that has no answer (a permission reply). */
    public suspend fun send(type: String, body: JsonObject, expectedEpoch: Int? = null) {
        withContext(lane) {
            if (!connected) paseoFail(PaseoFailureKind.StaleRequest)
            sendRaw(
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("session"),
                        "message" to body.withType(type, null),
                    )
                ),
                expectedEpoch ?: epoch,
            )
        }
    }

    private suspend fun sendRaw(value: JsonObject, expectedEpoch: Int) {
        val current = socket
        if (isClosed || current == null || expectedEpoch != epoch)
            paseoFail(PaseoFailureKind.Disconnected)
        val text = PaseoWireJson.encodeToString(JsonObject.serializer(), value)
        if (text.length > MAX_FRAME_BYTES || text.encodeToByteArray().size > MAX_FRAME_BYTES) {
            paseoFail(PaseoFailureKind.InvalidResponse)
        }
        try {
            current.send(text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            lost(expectedEpoch)
            paseoFail(PaseoFailureKind.Disconnected)
        }
    }

    // ---- receiving

    private suspend fun readLoop(myEpoch: Int, from: PaseoSocket) {
        try {
            for (frame in from.incoming) {
                if (myEpoch != epoch || isClosed) return
                when (frame) {
                    is PaseoFrame.Binary -> handleBinary(myEpoch, frame.bytes)
                    is PaseoFrame.Text -> receiveText(myEpoch, frame.text)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // An erroring socket is a lost socket; nothing of the error reaches logs.
        }
        lost(myEpoch)
    }

    private suspend fun receiveText(myEpoch: Int, text: String) {
        bytesIn += text.length
        val cap = boundedFrameBytes
        val decoded: Decoded? =
            if (
                text.length >= BACKGROUND_DECODE_CHARS &&
                    text.length <= MAX_FRAME_BYTES &&
                    (cap == null || text.length <= cap)
            ) {
                // Off the lane; this reader waits, so every later frame (and the close) is handled
                // after it, in order.
                Decoded(withContext(decodeDispatcher) { decodeOrNull(text) })
            } else {
                null
            }
        if (myEpoch != epoch || isClosed) return
        handleText(myEpoch, text, decoded)
    }

    private class Decoded(val value: JsonElement?)

    private suspend fun handleText(myEpoch: Int, frame: String, decoded: Decoded?) {
        val json: JsonObject
        val message: JsonObject
        val type: String
        try {
            if (frame.length > MAX_FRAME_BYTES) throw SerializationException()
            val cap = boundedFrameBytes
            if (cap != null && (frame.length > cap || frame.encodeToByteArray().size > cap))
                throw SerializationException()
            json =
                (if (decoded != null) decoded.value else PaseoWireJson.parseToJsonElement(frame))
                    as? JsonObject ?: throw SerializationException()
            when (json["type"].stringOrNull()) {
                "pong",
                "ping" -> return
                "hello.rejected" -> {
                    helloRejection = rejectionFailure(json["reason"])
                    lost(myEpoch)
                    return
                }
                "session" -> Unit
                else -> return
            }
            message = json["message"] as? JsonObject ?: throw SerializationException()
            type =
                message["type"].stringOrNull()?.takeIf { it.isNotEmpty() && it.length <= 200 }
                    ?: throw SerializationException()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            lost(myEpoch) // malformed JSON drops the connection (B-AGENT-150)
            return
        }
        val payload = message["payload"].objectOrNull() ?: JsonObject(emptyMap())
        if (type == "status" && payload["status"].stringOrNull() == "server_info") {
            workspaceFileEditing =
                payload["features"].objectOrNull()?.get("workspaceFileEditing")?.let {
                    it is JsonPrimitive && !it.isString && it.content == "true"
                } == true
            serverVersion =
                payload["version"].stringOrNull()?.takeIf { it.length <= 64 && VERSION.matches(it) }
            ready?.takeIf { !it.isCompleted }?.complete(Unit)
            // Feature withdrawal on a live socket must reach the gateway immediately.
            val serverInfo = PaseoPush(myEpoch, type, payload)
            for (listener in listeners.toList()) listener.onPush(serverInfo)
            return
        }
        val requestId = payload["requestId"].stringOrNull()
        if (type == "rpc_error") {
            val p = requestId?.let { pending.remove(it) }
            val error = payload["error"].stringOrNull()
            if (error != null) lastDaemonError = FIXED_DAEMON_ERROR
            p?.fail(PaseoException(PaseoFailureKind.Unavailable, error?.let(::withoutPassword)))
            return
        }
        // Progress repeats the request id but leaves the request waiting.
        if (type.endsWith(".progress") && requestId != null && pending.containsKey(requestId)) {
            pending[requestId]?.onProgress?.invoke(type, payload)
            return
        }
        // Most answers are `*_response` / `*.response`, but some actions answer with the push they
        // cause
        // (`agent_archived`): request ids are unique, so any payload repeating one settles it.
        val settled = requestId?.let { pending.remove(it) }
        if (settled != null) {
            settled.timer?.cancel()
            val error = payload["error"].stringOrNull()
            if (!settled.answerErrors && !error.isNullOrEmpty()) {
                lastDaemonError = FIXED_DAEMON_ERROR
                settled.fail(PaseoException(PaseoFailureKind.Unavailable, withoutPassword(error)))
            } else {
                settled.result.complete(payload)
            }
        }
        if (!isResponseType(type)) {
            val push = PaseoPush(myEpoch, type, payload)
            for (listener in listeners.toList()) listener.onPush(push)
        }
        if (
            settled != null &&
                settled.holds &&
                settled.result.isCompleted &&
                !settled.released.isCompleted
        ) {
            hold = settled.released
            settled.released.await()
            hold = null
        }
    }

    private fun handleBinary(myEpoch: Int, bytes: ByteArray) {
        if (bytes.size < 2 || bytes.size > MAX_FRAME_BYTES) return
        val frame =
            PaseoBinaryFrame(
                myEpoch,
                bytes[0].toInt() and 0xFF,
                bytes[1].toInt() and 0xFF,
                bytes.copyOfRange(2, bytes.size),
            )
        for (listener in listeners.toList()) listener.onBinary(frame)
    }

    private fun withoutPassword(text: String): String =
        if (password.length < 3) text else text.replace(password, "•••")

    /** Never suspends: safe to call from the reader, a timer or a failed send. */
    private fun lost(lostEpoch: Int) {
        if (lostEpoch != epoch) return
        serverVersion = null
        workspaceFileEditing = false
        val current = socket ?: return
        bytesIn = 0
        val disconnectedEpoch = epoch
        epoch++ // invalidate frames and callbacks of the old socket before a reconnect allocates a
        // new epoch
        socket = null
        initialized = false
        keepAlive?.cancel()
        keepAlive = null
        val handshake = ready
        ready = null
        if (handshake != null && !handshake.isCompleted) {
            handshake.completeExceptionally(
                helloRejection
                    ?: PaseoException(
                        when (current.closeCode) {
                            CLOSE_AUTH_FAILED -> PaseoFailureKind.Authentication
                            CLOSE_INCOMPATIBLE_PROTOCOL -> PaseoFailureKind.IncompatibleProtocol
                            else -> PaseoFailureKind.Disconnected
                        }
                    )
            )
        }
        hold?.complete(Unit)
        val oldReader = reader
        reader = null
        oldReader?.cancel()
        if (job.isActive) scope.launch { current.close() }
        val waiting = pending.values.toList()
        pending.clear()
        for (p in waiting) p.fail()
        if (!isClosed) for (listener in listeners.toList()) listener.onDisconnect(disconnectedEpoch)
    }

    /**
     * A read-only recovery request on a private connection with a 1 MiB frame cap and a 10 s
     * budget, so a huge history cannot crowd the live connection (plan 04 §8 "Bounded history").
     * Only tested daemon versions are asked.
     */
    public suspend fun requestBoundedHistory(
        body: JsonObject,
        agentSnapshot: Boolean = false,
    ): JsonObject {
        if (isClosed) paseoFail(PaseoFailureKind.Disconnected)
        val isolated =
            PaseoTransport(
                endpoint = endpoint.url,
                password = password,
                socketFactory = socketFactory,
                scope = scope,
                lane = lane,
                decodeDispatcher = decodeDispatcher,
                boundedFrameBytes = BOUNDED_FRAME_BYTES,
            )
        try {
            return try {
                withTimeout(BOUNDED_TIMEOUT_MS) {
                    isolated.connect()
                    if (
                        !isolated.connected ||
                            !PaseoTestedVersions.paseoSupports(isolated.serverVersion)
                    ) {
                        paseoFail(PaseoFailureKind.Unavailable)
                    }
                    isolated.request(
                        if (agentSnapshot) "fetch_agent_request"
                        else "fetch_agent_timeline_request",
                        body,
                        timeoutMs = BOUNDED_TIMEOUT_MS,
                        expectedEpoch = isolated.epoch,
                    )
                }
            } catch (_: TimeoutCancellationException) {
                paseoFail(PaseoFailureKind.Disconnected)
            }
        } finally {
            isolated.close()
        }
    }

    /**
     * Idempotent. Fails pending requests, forgets the password, stops every coroutine this
     * transport started.
     */
    public suspend fun close() {
        withContext(lane) {
            if (isClosed) return@withContext
            isClosed = true
            password = ""
            val current = socket
            lost(epoch)
            try {
                current?.close()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Closing cannot expose the daemon's close reason.
            }
            listeners.clear()
            job.cancel()
        }
    }

    private class Pending(
        val mutation: Boolean,
        val onProgress: ((String, JsonObject) -> Unit)?,
        val answerErrors: Boolean,
        val holds: Boolean,
    ) {
        val result = CompletableDeferred<JsonObject>()
        val released = CompletableDeferred<Unit>()
        var timer: Job? = null

        fun fail(
            error: PaseoException =
                PaseoException(
                    if (mutation) PaseoFailureKind.DeliveryUnknown
                    else PaseoFailureKind.Disconnected
                )
        ) {
            timer?.cancel()
            result.completeExceptionally(error)
        }
    }

    public companion object {
        /**
         * The daemon accepts the upgrade, then closes with this code when the password is missing
         * or wrong.
         */
        public const val CLOSE_AUTH_FAILED: Int = 4401

        /** The daemon closes with this code when it does not speak this protocol. */
        public const val CLOSE_INCOMPATIBLE_PROTOCOL: Int = 4003
        public const val MAX_FRAME_BYTES: Int = 8 * 1024 * 1024
        public const val BOUNDED_FRAME_BYTES: Int = 1024 * 1024
        public const val MAX_PENDING: Int = 64

        /**
         * Text frames this long or longer are decoded off the lane
         * (docs/qa/json-decode-2026-10-28).
         */
        public const val BACKGROUND_DECODE_CHARS: Int = 64 * 1024

        /**
         * Sent in `hello`. Below 0.1.45 the daemon hides every provider except claude, codex and
         * opencode.
         */
        public const val CLIENT_APP_VERSION: String = "0.8.0"
        public const val HELLO_TIMEOUT_MS: Long = 8_000
        public const val KEEPALIVE_MS: Long = 20_000
        public const val BOUNDED_TIMEOUT_MS: Long = 10_000
        private const val FIXED_DAEMON_ERROR = "The agent request could not be completed."
        private val VERSION = Regex("""^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$""")

        private fun isResponseType(type: String): Boolean =
            type.endsWith("_response") || type.endsWith(".response")

        private fun decodeOrNull(text: String): JsonElement? =
            try {
                PaseoWireJson.parseToJsonElement(text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

        /**
         * The plain failure for a `hello.rejected` reason; the daemon's reason only reaches
         * Details.
         */
        internal fun rejectionFailure(reason: JsonElement?): PaseoException {
            val name = reason.stringOrNull()?.takeIf { it.length <= 40 }
            return when (name) {
                "password_required" ->
                    PaseoException(PaseoFailureKind.Authentication, name, passwordMissing = true)
                "incorrect_password" -> PaseoException(PaseoFailureKind.Authentication, name)
                "incompatible_protocol" ->
                    PaseoException(PaseoFailureKind.IncompatibleProtocol, name)
                else -> PaseoException(PaseoFailureKind.Disconnected, name)
            }
        }

        private fun newClientId(random: Random): String =
            "cid_" +
                random.nextBytes(16).joinToString("") {
                    (it.toInt() and 0xFF).toString(16).padStart(2, '0')
                }

        private fun JsonObject.withType(type: String, requestId: String?): JsonObject =
            JsonObject(
                LinkedHashMap<String, JsonElement>(this).apply {
                    put("type", JsonPrimitive(type))
                    if (requestId != null) put("requestId", JsonPrimitive(requestId))
                }
            )
    }
}
