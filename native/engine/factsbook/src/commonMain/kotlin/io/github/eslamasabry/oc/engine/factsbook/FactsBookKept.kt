package io.github.eslamasabry.oc.engine.factsbook

import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Cache-first (owner rule, 2026-11-09): every FactsBook page opens on what the node last answered, then the live read
 * replaces it in place. What is kept is the node's own answer to a read (the display facts the person already saw),
 * one entry per read, so the pages parse it with the same code as a live answer and no second set of shapes exists.
 * The app binds [FactsBookKept] to its last-known store (per profile and server, wiped with the profile).
 */
public interface FactsBookKept {
    public suspend fun read(profile: String, key: String): JsonElement?

    public suspend fun keep(profile: String, key: String, body: JsonElement)
}

/** One kept answer. */
@Serializable public class KeptAnswer(public val body: JsonElement)

public object KeptFactsBook {
    /** One shelf: reads of the overview, things, areas, reminders and triggers; 256 KB an answer, 60 answers a profile. */
    public val SHELF: CacheShelf<KeptAnswer> = CacheShelf("factsBook", KeptAnswer.serializer(), maxBytes = 256 * 1024, maxEntries = 60)

    /** Reads whose answers are display facts. Never the control chain, the policy, a search, an export or pairing. */
    private val KEPT_PATHS =
        listOf(
            "/v1/status", "/v1/things", "/v1/inbox/pending", "/v1/review", "/v1/areas", "/v1/capabilities", "/v1/config",
            "/v1/rules", "/v1/events", "/v1/trigger-firings", "/v1/reminder-proposals", "/v1/offers", "/v1/reminders",
        )

    internal fun worthKeeping(path: String): Boolean =
        !path.endsWith("/export") && KEPT_PATHS.any { path == it || path.startsWith("$it/") }

    /** The node's address is part of the key: another node's answer never shows. */
    internal fun key(base: String, path: String, query: Map<String, String>): String =
        base + path + query.entries.sortedBy { it.key }.joinToString("&", prefix = "?") { "${it.key}=${it.value}" }
}

/** Passes every request on, and keeps each good answer to a read worth keeping. */
public class KeptFactsBookHttp(
    private val inner: FactsBookHttp,
    private val kept: FactsBookKept,
    private val profile: () -> String?,
    private val base: String,
) : FactsBookHttp {
    override suspend fun send(method: String, path: String, query: Map<String, String>, headers: Map<String, String>, body: String?): FbHttpResult {
        val answer = inner.send(method, path, query, headers, body)
        if (method == "GET" && answer is FbHttpResult.Answer && answer.status == 200 && KeptFactsBook.worthKeeping(path)) {
            val id = profile()
            val text = answer.body
            if (id != null && text != null) {
                val parsed = try { Json.parseToJsonElement(text) } catch (e: IllegalArgumentException) { null }
                if (parsed != null) kept.keep(id, KeptFactsBook.key(base, path, query), parsed)
            }
        }
        return answer
    }
}

/** Answers reads from what was kept; anything else, or a read never kept, is unreachable. Never sends anything. */
internal class ReplayFactsBookHttp(private val kept: FactsBookKept, private val profile: String, private val base: String) : FactsBookHttp {
    override suspend fun send(method: String, path: String, query: Map<String, String>, headers: Map<String, String>, body: String?): FbHttpResult {
        if (method != "GET" || !KeptFactsBook.worthKeeping(path)) return FbHttpResult.Unreachable
        val found = kept.read(profile, KeptFactsBook.key(base, path, query)) ?: return FbHttpResult.Unreachable
        return FbHttpResult.Answer(200, found.toString())
    }
}
