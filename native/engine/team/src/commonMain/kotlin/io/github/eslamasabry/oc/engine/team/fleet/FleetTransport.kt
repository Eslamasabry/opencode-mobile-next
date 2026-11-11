package io.github.eslamasabry.oc.engine.team.fleet

/*
 * The AI Team on a computer (Gas City), Flutter lib/orchestration/client/http.dart + sse.dart, as a port the app
 * implements. The host takes no credential: a request never carries Authorization, Cookie or Proxy-Authorization, and
 * the only header a write adds is X-GC-Request (plus Idempotency-Key behind the host front). The address rule
 * ([FleetAddress]) is the security boundary, checked before a transport is ever opened.
 */

/** What one HTTP exchange came back with. */
public sealed interface FleetReply {
    /**
     * The host answered with [status] and [text] (any status: a problem document is read by the caller).
     * [hostRequestId] is `X-GC-Request-Id`; [replayed] is `Idempotent-Replayed: true`.
     */
    public data class Answered(
        val status: Int,
        val text: String,
        val hostRequestId: String? = null,
        val replayed: Boolean = false,
    ) : FleetReply {
        val ok: Boolean
            get() = status in 200..299
    }

    /** Nothing came back: DNS, refused, TLS, timeout, cut connection. A write may still have landed. */
    public data class Unreachable(val timedOut: Boolean = false) : FleetReply
}

/** One server-sent event of a stream: `event`, `id` and the joined `data` lines (Dart `SseFrame`). */
public data class FleetFrame(val event: String?, val id: String?, val data: String)

/** How a stream ended. */
public sealed interface FleetStreamEnd {
    /** The body ended cleanly (the host closed it). */
    public data object Ended : FleetStreamEnd

    /** The host answered without a stream (a non-2xx status or not `text/event-stream`). */
    public data class Refused(val status: Int) : FleetStreamEnd

    /** The connection broke or went silent past the stall limit. */
    public data class Dropped(val timedOut: Boolean = false) : FleetStreamEnd
}

/** One host's HTTP, rooted at its base URL (no trailing slash). Paths are host-relative and already encoded. */
public interface FleetTransport {
    public suspend fun get(path: String, query: List<Pair<String, String>> = emptyList(), timeoutMs: Long = 30_000): FleetReply

    /**
     * A write: [method] `POST` or `DELETE` with a JSON [body] (none when null), `X-GC-Request: requestId` and, when
     * given, `Idempotency-Key`. Never retried by the transport.
     */
    public suspend fun send(
        method: String,
        path: String,
        body: Map<String, Any?>?,
        requestId: String,
        idempotencyKey: String?,
    ): FleetReply

    /**
     * Opens `GET path` as `text/event-stream` (with `Last-Event-ID` when [lastEventId] is set) and hands every frame to
     * [onFrame] until the stream ends; a silence longer than [stallMs] counts as dropped. Cancelling the caller closes it.
     */
    public suspend fun stream(
        path: String,
        lastEventId: String?,
        stallMs: Long = 90_000,
        onFrame: suspend (FleetFrame) -> Unit,
    ): FleetStreamEnd

    public fun close()
}

/** Opens a transport for one base URL; the app binds OkHttp. Called only with an address [FleetAddress] allows. */
public fun interface FleetTransports {
    public fun open(baseUrl: String): FleetTransport
}

/** Dart `isOrchestrationUrlAllowed`, `isTailnetHost`, `isLoopbackHost` (gascity_probe.dart, loopback_host.dart). */
public object FleetAddress {
    /** Strips whitespace and trailing slashes (Dart `normalizeBaseUrl`). */
    public fun normalize(url: String): String = url.trim().trimEnd('/')

    public fun isLoopback(host: String): Boolean = host.lowercase().let { it == "localhost" || it == "127.0.0.1" || it == "::1" }

    /** Loopback, `*.ts.net`, or an IPv4 address in Tailscale's 100.64.0.0/10. */
    public fun isTailnet(host: String): Boolean {
        var h = host.trim().lowercase()
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length - 1)
        if (h.isEmpty()) return false
        if (isLoopback(h)) return true
        if (h.endsWith(".ts.net")) return true
        val parts = h.split('.')
        if (parts.size != 4) return false
        val bytes = parts.map { p -> p.toIntOrNull()?.takeIf { it in 0..255 && p == it.toString() } ?: return false }
        return bytes[0] == 100 && (bytes[1] and 0xC0) == 0x40
    }

    /** The host part of [url] (brackets kept off an IPv6 literal), or null when [url] has no authority. */
    public fun host(url: String): String? {
        val scheme = url.indexOf("://").takeIf { it > 0 } ?: return null
        var rest = url.substring(scheme + 3)
        rest = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if ('@' in rest) return null
        if (rest.startsWith("[")) return rest.substringAfter('[').substringBefore(']').takeIf { it.isNotEmpty() }
        return rest.substringBefore(':').takeIf { it.isNotEmpty() }
    }

    /** `https://` anywhere, `http://` only to loopback or a tailnet host; anything else (or user info) is refused. */
    public fun allowed(url: String): Boolean {
        val trimmed = url.trim()
        val scheme = trimmed.substringBefore("://", "").lowercase()
        val host = host(trimmed) ?: return false
        return when (scheme) {
            "https" -> true
            "http" -> isLoopback(host) || isTailnet(host)
            else -> false
        }
    }

    /** `http://` to a host that is neither loopback nor tailnet (Dart `ProbePlainHttpRefused`). */
    public fun plainHttpRefused(url: String): Boolean {
        val trimmed = url.trim()
        val host = host(trimmed) ?: return false
        return trimmed.substringBefore("://", "").lowercase() == "http" && !isLoopback(host) && !isTailnet(host)
    }

    /** Dart `Uri.encodeComponent` for one path segment. */
    public fun segment(value: String): String = io.github.eslamasabry.oc.core.common.dart.DartJson.encodeComponent(value)
}
