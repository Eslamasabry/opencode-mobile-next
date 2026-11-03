package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * The Git page's commands on a connected OpenCode 2 server (port of lib/api2/shell_runner.dart + ServerGitShell in
 * lib/state/git_workspace.dart; B-BUILD-013): `POST /api/shell` starts `sh -c '<script>'` in the project folder, `GET
 * /api/shell/{id}` is polled until it is no longer running, `GET /api/shell/{id}/output` is paged by byte cursor, then
 * `DELETE /api/shell/{id}` drops the server's copy. A 404 on start means the server cannot run commands
 * ([GitShellFailure.Unsupported]); no server text ever reaches a failure (it may echo the command).
 *
 * The app's GitHub token is never sent: the server pushes with its own Git sign-in (B-SEC-082).
 */
public class ServerGitShell(
    private val net: NetClient,
    private val origin: ServerOrigin,
    /** `Basic …` or null; built by the caller from the profile's resolved credentials and registered as a secret. */
    private val authorization: String?,
    /** The project folder (`cwd` and `location[directory]`). */
    private val directory: String,
    private val pollIntervalMs: Long = 400,
) : GitShell {
    override suspend fun run(script: String, timeoutMs: Long): GitShellReply {
        val query = mapOf("location[directory]" to directory)
        val started =
            exchange(HttpMethod.Post, "/shell", query) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("command", "sh -c " + shQuote(script))
                        put("cwd", directory)
                        put("timeout", timeoutMs)
                    }.toString(),
                )
            }
        val id =
            when (started) {
                is Outcome.Failed ->
                    return GitShellReply.Failed(
                        if (started.failure is Failure.NotFound) GitShellFailure.Unsupported else GitShellFailure.Unavailable,
                    )
                is Outcome.Ok -> started.value.data()?.string("id") ?: return GitShellReply.Failed(GitShellFailure.Unavailable)
            }
        try {
            val maxPolls = (timeoutMs + GRACE_MS) / pollIntervalMs.coerceAtLeast(1) + 1
            var polls = 0L
            var status = "running"
            while (true) {
                val info =
                    when (val read = exchange(HttpMethod.Get, "/shell/$id", query)) {
                        is Outcome.Failed -> return GitShellReply.Failed(GitShellFailure.Unavailable)
                        is Outcome.Ok -> read.value.data() ?: return GitShellReply.Failed(GitShellFailure.Unavailable)
                    }
                status = info.string("status") ?: "exited"
                if (status != "running") break
                if (++polls >= maxPolls) return GitShellReply.Failed(GitShellFailure.TimedOut)
                delay(pollIntervalMs)
            }
            if (status == "timeout") return GitShellReply.Failed(GitShellFailure.TimedOut)
            val output = readOutput(id, query) ?: return GitShellReply.Failed(GitShellFailure.Unavailable)
            return GitShellReply.Output(output)
        } finally {
            // Drop the server's copy of the output, even when the page was left mid-command (its scope is cancelled
            // then): cancellation-independent, bounded by the client's connect / inactivity timeouts. A failure here
            // only leaves it for the server's own cleanup.
            withContext(NonCancellable) { exchange(HttpMethod.Delete, "/shell/$id", query) }
        }
    }

    private suspend fun readOutput(id: String, query: Map<String, String>): String? {
        val out = StringBuilder()
        var cursor = 0L
        repeat(MAX_PAGES) {
            val page =
                when (val read = exchange(HttpMethod.Get, "/shell/$id/output", query + mapOf("cursor" to "$cursor", "limit" to "$PAGE_LIMIT"))) {
                    is Outcome.Failed -> return if (out.isEmpty()) null else out.toString()
                    is Outcome.Ok -> read.value.data() ?: return out.toString()
                }
            val text = (page["output"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return out.toString()
            out.append(text)
            val next = (page["cursor"] as? JsonPrimitive)?.longOrNull
            val size = (page["size"] as? JsonPrimitive)?.longOrNull
            if (next == null || size == null || next <= cursor || next >= size) return out.toString()
            cursor = next
        }
        return out.toString()
    }

    private suspend fun exchange(
        verb: HttpMethod,
        path: String,
        query: Map<String, String>,
        extra: HttpRequestBuilder.() -> Unit = {},
    ): Outcome<JsonElement?> =
        net.exchange({
            method = verb
            url(origin.toString() + "/api" + path)
            for ((key, value) in query) url.parameters.append(key, value)
            authorization?.let { header(HttpHeaders.Authorization, it) }
            header(HttpHeaders.Accept, "application/json")
            extra()
        }) { reply ->
            val text = reply.bodyAsText()
            if (text.isBlank()) null else parseJson(text)
        }

    private fun JsonElement?.data(): JsonObject? {
        val obj = this as? JsonObject ?: return null
        return (obj["data"] as? JsonObject) ?: obj.takeIf { "id" in it || "output" in it || "status" in it }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    override fun toString(): String = "ServerGitShell"

    public companion object {
        private const val GRACE_MS = 15_000L
        private const val PAGE_LIMIT = 65_536
        private const val MAX_PAGES = 64
        private val JSON = Json { ignoreUnknownKeys = true }

        internal fun parseJson(text: String): JsonElement? =
            try {
                JSON.parseToJsonElement(text)
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}
