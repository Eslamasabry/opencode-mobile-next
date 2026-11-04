package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.net.ws.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Read-only app-server connection. It never starts login, a thread, a turn, or a tool. */
public class CodexReads
private constructor(
    private val socket: WsSession,
    private val directory: String,
    scope: CoroutineScope,
    dispatchers: AppDispatchers,
) {
    private val lane = dispatchers.serial("codex-reader")
    private val requests = Mutex()
    private val pending = HashMap<Int, CompletableDeferred<Outcome<JsonObject>>>()
    private var sequence = 0
    private val json = Json { ignoreUnknownKeys = true }
    private var qualified = false
    public val accountQualified: Boolean
        get() = qualified

    public val connected: Boolean
        get() = !socket.closeReason.isCompleted

    init {
        scope.launch(lane) {
            try {
                socket.incoming.collect { message ->
                    if (message !is WsMessage.Text) {
                        invalidate(Failure.UnreadableReply())
                        return@collect
                    }
                    val root =
                        try {
                            json.parseToJsonElement(message.text) as? JsonObject
                        } catch (_: Exception) {
                            null
                        }
                    if (root == null) {
                        invalidate(Failure.UnreadableReply())
                        return@collect
                    }
                    val id = (root["id"] as? JsonPrimitive)?.intOrNull
                    if (id == null) {
                        if (root["id"] != null || root["method"] !is JsonPrimitive)
                            invalidate(Failure.UnreadableReply())
                        // Notifications are deliberately not retained by this read-only client.
                        return@collect
                    }
                    val waiting = pending.remove(id) ?: return@collect
                    waiting.complete(
                        when {
                            root.containsKey("error") -> Failure.Rejected(null).failed()
                            root["result"] is JsonObject -> (root["result"] as JsonObject).ok()
                            else -> Failure.UnreadableReply().failed()
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                failPending(Failure.Network(NetworkWhy.Reset))
            }
        }
        scope.launch(lane) {
            socket.closeReason.await()
            failPending(Failure.Network(NetworkWhy.Reset))
        }
    }

    private fun failPending(failure: Failure) {
        pending.values.toList().forEach { it.complete(failure.failed()) }
        pending.clear()
    }

    private suspend fun invalidate(failure: Failure) {
        failPending(failure)
        socket.close(1002)
    }

    private suspend fun request(method: String, params: JsonObject): Outcome<JsonObject> =
        requests.withLock {
            withContext(lane) {
                if (!connected) return@withContext Failure.Network(NetworkWhy.Reset).failed()
                val id = ++sequence
                val result = CompletableDeferred<Outcome<JsonObject>>()
                pending[id] = result
                try {
                    when (
                        val sent =
                            socket.sendText(
                                buildJsonObject {
                                    put("id", id)
                                    put("method", method)
                                    put("params", params)
                                }
                                    .toString()
                            )
                    ) {
                        is Outcome.Failed -> sent
                        is Outcome.Ok ->
                            withTimeoutOrNull(8000) { result.await() }
                                ?: Failure.TimedOut().failed()
                    }
                } finally {
                    pending.remove(id)
                }
            }
        }

    private suspend fun initialize(): Outcome<Unit> {
        val reply =
            request(
                "initialize",
                buildJsonObject {
                    putJsonObject("clientInfo") {
                        put("name", "opencode_mobile")
                        put("version", "1.0.0")
                    }
                    putJsonObject("capabilities") { put("experimentalApi", false) }
                },
            )
        if (reply is Outcome.Failed) return reply
        val agent = ((reply as Outcome.Ok).value["userAgent"] as? JsonPrimitive)?.contentOrNull
        qualified = agent != null && Regex("""(^|/)0\.153\.4(?:\s|$)""").containsMatchIn(agent)
        return socket.sendText(
            buildJsonObject {
                put("method", "initialized")
                putJsonObject("params") {}
            }
                .toString()
        )
    }

    /** A scoped list is a protocol probe, not a claim that an empty folder exists. */
    public suspend fun probe(): Outcome<Unit> {
        val reply =
            request(
                "thread/list",
                buildJsonObject {
                    put("cwd", directory)
                    put("limit", 1)
                    put("archived", false)
                },
            )
        if (reply is Outcome.Failed) return reply
        val rows =
            (reply as Outcome.Ok).value["data"] as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        if (rows.size > 100) return Failure.UnreadableReply().failed()
        for (row in rows) {
            val obj = row as? JsonObject ?: return Failure.UnreadableReply().failed()
            if ((obj["cwd"] as? JsonPrimitive)?.contentOrNull != directory) {
                socket.close(1002)
                return Failure.Refused(RefusalReason.StaleScope).failed()
            }
            val id = (obj["id"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            if (id.isNullOrEmpty() || id.length > 256) return Failure.UnreadableReply().failed()
            val name = obj["name"] as? JsonPrimitive
            if (name?.isString == true && name.content.isNotBlank() && name.content.length > 4096)
                return Failure.UnreadableReply().failed()
            for (key in listOf("createdAt", "updatedAt")) {
                val time = obj[key]
                if (time != null && time != JsonNull) {
                    val value = (time as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
                    if (value == null || value !in 0..8640000000000L)
                        return Failure.UnreadableReply().failed()
                }
            }
            val status =
                ((obj["status"] as? JsonObject)?.get("type") as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.contentOrNull
            if (status !in setOf("active", "idle", "notLoaded", "systemError"))
                return Failure.UnreadableReply().failed()
        }
        return Unit.ok()
    }

    public suspend fun account(): Outcome<CodexAccount> {
        if (!qualified)
            return Failure.Incompatible(IncompatibleKind.UnsupportedServerVersion).failed()
        val reply = request("account/read", buildJsonObject { put("refreshToken", false) })
        if (reply is Outcome.Failed) return reply
        return parseAccount((reply as Outcome.Ok).value)
    }

    public suspend fun limits(): Outcome<List<CodexLimitBucket>> {
        if (!qualified)
            return Failure.Incompatible(IncompatibleKind.UnsupportedServerVersion).failed()
        val reply = request("account/rateLimits/read", buildJsonObject {})
        if (reply is Outcome.Failed) return reply
        return parseLimits((reply as Outcome.Ok).value)
    }

    public suspend fun awaitClosed() {
        socket.closeReason.await()
    }

    public suspend fun close() {
        socket.close()
    }

    public companion object {
        public suspend fun open(
            target: CodexTarget,
            sockets: WsConnections,
            owner: CoroutineScope,
            dispatchers: AppDispatchers,
        ): Outcome<CodexReads> {
            val opened =
                sockets.open(
                    WsSpec(
                        target.transport,
                        target.url,
                        listOf("Authorization" to "Bearer ${target.bearer}"),
                        owner,
                    )
                )
            if (opened is Outcome.Failed)
                return if (
                    opened.failure is Failure.SignIn &&
                        (opened.failure as Failure.SignIn).why == SignInWhy.PasswordRejected
                )
                    Failure.SignIn(SignInWhy.TokenRejected).failed()
                else opened
            val reader =
                CodexReads((opened as Outcome.Ok).value, target.directory, owner, dispatchers)
            try {
                val ready = reader.initialize()
                if (ready is Outcome.Failed) {
                    reader.close()
                    return ready
                }
                return reader.ok()
            } catch (e: CancellationException) {
                withContext(NonCancellable) { reader.close() }
                throw e
            }
        }
    }
}
