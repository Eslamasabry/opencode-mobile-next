package io.github.eslamasabry.oc.engine.connectors.bridge

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

/** One HTTP request as the loopback socket received it (headers already split; body not read yet). */
public class BridgeRequest(
    public val method: String,
    /** The request target's path, without the query. */
    public val path: String,
    public val hasQuery: Boolean,
    /** Every `Authorization` header value received. */
    public val authorization: List<String>,
    /** The declared `Content-Length`; null when absent (chunked). */
    public val contentLength: Long?,
    public val body: BridgeBody,
)

/** PORT: reads the request body. The adapter stops reading past [maxBytes] and reports [BodyRead.TooLarge]. */
public fun interface BridgeBody {
    public suspend fun read(maxBytes: Int): BodyRead
}

public sealed interface BodyRead {
    public class Bytes(public val value: ByteArray) : BodyRead

    public data object TooLarge : BodyRead

    /** The stream failed (client went away, bad framing). */
    public data object Broken : BodyRead
}

/**
 * The response to write: [status], JSON [body], and these fixed headers: `Content-Type: application/json`,
 * `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Connection: close`, an exact `Content-Length`.
 */
public class BridgeReply(public val status: Int, public val body: ByteArray) {
    public val bodyText: String get() = body.decodeToString()

    public companion object {
        public val HEADERS: Map<String, String> =
            mapOf(
                "Content-Type" to "application/json",
                "Cache-Control" to "no-store",
                "X-Content-Type-Options" to "nosniff",
            )

        internal fun failure(status: Int, error: String): BridgeReply =
            BridgeReply(status, buildJsonObject { put("error", error) }.toString().encodeToByteArray())
    }
}

/** What the search callback answers. Its failures never carry details: the reply is generic. */
public sealed interface BridgeSearchResult {
    public class Answer(public val json: JsonObject) : BridgeSearchResult

    /** The request's arguments are invalid (400). */
    public data object BadRequest : BridgeSearchResult

    /** Anything else went wrong (403 when the source changed meanwhile, else 500). */
    public data object Failed : BridgeSearchResult
}

/** PORT: binds `127.0.0.1` on an ephemeral port and hands every request to [handler]; a null reply writes nothing. */
public fun interface LoopbackServerPort {
    public suspend fun bind(handler: suspend (BridgeRequest) -> BridgeReply?): Outcome<BoundLoopback>
}

public interface BoundLoopback {
    /** The port the OS chose (> 0). */
    public val port: Int

    /** Stops listening and drops open connections (new connections are refused). */
    public suspend fun close()
}

/** PORT: cryptographically secure random bytes (Android `SecureRandom`). */
public fun interface SecureRandomBytes {
    public fun next(count: Int): ByteArray
}

/**
 * The read-only connector-search bridge for an agent's helper (Dart `McpConnectorSearchBridge`; B-AGENT-136).
 *
 * Accepts only an authorized (`Bearer <64 hex>`, compared in constant time) `POST /find-connectors` without a query,
 * a JSON object body of at most 2048 UTF-8 bytes, while [isCurrent] holds before and after the search. At most four
 * searches run at once; a search answers 504 after five seconds, but its slot stays taken until the callback really
 * ends (so retries cannot exceed four real calls). Responses over 16384 bytes become 502; failure details never leave.
 * The bearer is handed to the helper directly and never appears in [endpoint] or [toString].
 */
@OptIn(ExperimentalAtomicApi::class)
public class ConnectorSearchBridge private constructor(
    private val search: suspend (JsonObject) -> BridgeSearchResult,
    private val isCurrent: () -> Boolean,
    private val work: CoroutineScope,
    public val bearer: String,
) {
    private lateinit var bound: BoundLoopback
    private val active = AtomicInt(0)
    private val closed = AtomicBoolean(false)
    private val closing = Mutex()
    private var closedServer = false

    public val port: Int get() = bound.port

    /** `http://127.0.0.1:<port>/find-connectors`: no user info, no query, no bearer. */
    public val endpoint: String get() = "http://$HOST:$port$PATH"

    override fun toString(): String = "ConnectorSearchBridge($HOST:$port)"

    private fun current(): Boolean {
        if (closed.load()) return false
        return try {
            isCurrent()
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun authorized(request: BridgeRequest): Boolean {
        val values = request.authorization
        if (values.size != 1) return false
        val expected = "Bearer $bearer"
        val actual = values.single()
        if (actual.length != expected.length) return false
        var difference = 0
        for (i in expected.indices) difference = difference or (actual[i].code xor expected[i].code)
        return difference == 0
    }

    /** The request handling, independent of the socket. Null once closed (nothing is written). */
    public suspend fun handle(request: BridgeRequest): BridgeReply? {
        val reply =
            when {
                !authorized(request) -> BridgeReply.failure(401, "Unauthorized")
                request.path != PATH || request.hasQuery -> BridgeReply.failure(404, "Not found")
                request.method != "POST" -> BridgeReply.failure(405, "Method not allowed")
                !current() -> BridgeReply.failure(403, "Source unavailable")
                else -> admit(request)
            }
        return if (closed.load()) null else reply
    }

    private suspend fun admit(request: BridgeRequest): BridgeReply {
        if (active.incrementAndFetch() > MAX_ACTIVE) {
            active.decrementAndFetch()
            return BridgeReply.failure(429, "Search busy")
        }
        // The slot is released when the work ends, not when the reply times out.
        val job = work.async(start = CoroutineStart.LAZY) { resolve(request) }
        job.invokeOnCompletion { active.decrementAndFetch() }
        job.start()
        return withTimeoutOrNull(DEADLINE_MS) { job.await() } ?: BridgeReply.failure(504, "Search timed out")
    }

    private suspend fun resolve(request: BridgeRequest): BridgeReply {
        val declared = request.contentLength
        if (declared != null && declared > REQUEST_LIMIT) return BridgeReply.failure(413, "Request too large")
        val body =
            when (val read = request.body.read(REQUEST_LIMIT)) {
                BodyRead.TooLarge -> return BridgeReply.failure(413, "Request too large")
                BodyRead.Broken -> return BridgeReply.failure(400, "Invalid request")
                is BodyRead.Bytes -> read.value
            }
        if (body.size > REQUEST_LIMIT) return BridgeReply.failure(413, "Request too large")
        val value =
            try {
                Json.parseToJsonElement(body.decodeToString(throwOnInvalidSequence = true))
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: CharacterCodingException) {
                null
            }
        if (value !is JsonObject) return BridgeReply.failure(400, "Invalid request")
        if (!current()) return BridgeReply.failure(403, "Source unavailable")
        val result = attempt({ Failure.Unavailable(SEARCH_FEATURE) }) { search(value) }
        if (!current()) return BridgeReply.failure(403, "Source unavailable")
        return when (val answer = (result as? Outcome.Ok)?.value ?: BridgeSearchResult.Failed) {
            BridgeSearchResult.BadRequest -> BridgeReply.failure(400, "Invalid request")
            BridgeSearchResult.Failed -> BridgeReply.failure(500, "Search unavailable")
            is BridgeSearchResult.Answer -> {
                val bytes = answer.json.toString().encodeToByteArray()
                if (bytes.size > RESPONSE_LIMIT) {
                    BridgeReply.failure(502, "Search response too large")
                } else {
                    BridgeReply(200, bytes)
                }
            }
        }
    }

    /** Idempotent: stops serving at once and closes the socket once. */
    public suspend fun close() {
        closed.store(true)
        closing.withLock {
            if (closedServer) return
            closedServer = true
            bound.close()
        }
    }

    public val isClosed: Boolean get() = closed.load()

    public companion object {
        public const val HOST: String = "127.0.0.1"
        public const val PATH: String = "/find-connectors"
        public const val REQUEST_LIMIT: Int = 2048
        public const val RESPONSE_LIMIT: Int = 16384
        public const val DEADLINE_MS: Long = 5_000
        public const val MAX_ACTIVE: Int = 4
        private val SEARCH_FEATURE = io.github.eslamasabry.oc.core.common.FeatureId("mcpChatConnect")

        /** 32 random bytes as 64 lower-case hex digits. */
        public fun newBearer(random: SecureRandomBytes): String =
            random.next(32).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

        /**
         * Binds the loopback port and starts serving. [work] runs the searches (an engine scope); [search] owns the
         * catalogue and connection policy; [isCurrent] says whether the source this bridge was made for still holds.
         */
        public suspend fun start(
            server: LoopbackServerPort,
            random: SecureRandomBytes,
            work: CoroutineScope,
            isCurrent: () -> Boolean,
            search: suspend (JsonObject) -> BridgeSearchResult,
        ): Outcome<ConnectorSearchBridge> {
            val bridge = ConnectorSearchBridge(search, isCurrent, work, newBearer(random))
            return when (val bound = server.bind(bridge::handle)) {
                is Outcome.Failed -> bound
                is Outcome.Ok -> {
                    bridge.bound = bound.value
                    Outcome.Ok(bridge)
                }
            }
        }
    }
}
