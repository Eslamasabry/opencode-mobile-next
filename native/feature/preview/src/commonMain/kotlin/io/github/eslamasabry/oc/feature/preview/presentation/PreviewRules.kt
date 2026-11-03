package io.github.eslamasabry.oc.feature.preview.presentation

import io.github.eslamasabry.oc.ui.routes.PreviewLink

/** The scheme, host and port of an http(s) address, or nothing for anything else (credentials included). */
public data class WebOrigin(val scheme: String, val host: String, val port: Int) {
    /** The origin as an address, default ports left out. */
    public val address: String
        get() = if (port == defaultPort(scheme)) "$scheme://$host" else "$scheme://$host:$port"

    public companion object {
        private val shape = Regex("""^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)""")

        public fun of(url: String?): WebOrigin? {
            val text = url?.trim() ?: return null
            if (text.length > 2048) return null
            val match = shape.find(text) ?: return null
            val scheme = match.groupValues[1].lowercase()
            if (scheme != "http" && scheme != "https") return null
            val authority = match.groupValues[2]
            if (authority.isEmpty() || '@' in authority) return null
            val host: String
            val portText: String?
            if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close < 0) return null
                host = authority.substring(0, close + 1)
                val rest = authority.substring(close + 1)
                portText = if (rest.isEmpty()) null else if (rest.startsWith(":")) rest.substring(1) else return null
            } else {
                if (authority.count { it == ':' } > 1) return null
                host = authority.substringBefore(':')
                portText = if (':' in authority) authority.substringAfter(':') else null
            }
            if (host.isEmpty() || host == "[]") return null
            val port =
                when {
                    portText == null || portText.isEmpty() -> defaultPort(scheme)
                    portText.length > 5 || !portText.all { it in '0'..'9' } -> return null
                    else -> portText.toInt().takeIf { it in 1..65535 } ?: return null
                }
            return WebOrigin(scheme, host.lowercase(), port)
        }

        private fun defaultPort(scheme: String): Int = if (scheme == "https") 443 else 80
    }
}

/** Why a link cannot be shown for the connected server (Flutter `DevPreviewBlock`). */
public enum class PreviewBlock {
    /** No saved server (or a private lease to another machine): there is no address to point the link at. */
    NoServer,

    /** The server's address has no usable host. */
    NoHost,
}

/** Where a link opens (Flutter `DevPreviewTarget`, B-CHAT-027). */
public sealed interface DevPreviewTarget {
    /** The phone's own server: loopback in the phone's network namespace. */
    public data class Phone(val url: String) : DevPreviewTarget

    /** A remote server: the link's host replaced by [host]. The dev server must listen on 0.0.0.0 to be reachable. */
    public data class Remote(val url: String, val host: String) : DevPreviewTarget

    public data class Blocked(val why: PreviewBlock) : DevPreviewTarget

    public companion object {
        public fun isLoopbackHost(host: String): Boolean {
            val h = host.lowercase()
            return h == "localhost" || h == "::1" || h == "[::1]" || h == "0.0.0.0" || h.startsWith("127.")
        }

        /**
         * Resolves [link] for the server at [serverAddress]: none, or [leased] (a runtime-only private transport) to
         * another machine → blocked; a loopback server → the phone's own loopback (0.0.0.0 written as 127.0.0.1); any
         * other → the same scheme, port, path and query on the server's host.
         */
        public fun resolve(link: PreviewLink, serverAddress: String?, leased: Boolean = false): DevPreviewTarget {
            if (serverAddress.isNullOrBlank()) return Blocked(PreviewBlock.NoServer)
            val server = WebOrigin.of(serverAddress)
            if (leased && (server == null || !isLoopbackHost(server.host))) return Blocked(PreviewBlock.NoServer)
            server ?: return Blocked(PreviewBlock.NoHost)
            val remote = !isLoopbackHost(server.host)
            val host = if (remote) server.host else "127.0.0.1"
            val url = "${link.scheme}://$host:${link.port}${link.pathAndQuery}"
            return if (remote) Remote(url, server.host) else Phone(url)
        }
    }
}

/**
 * The preview's egress rules (B-SEC-058; plan 16 hardened egress). The Android web view asks these for every request.
 * Only the origin the preview was opened for may load: same host and port, http(s), never with credentials. On that
 * origin the app's ordinary requests work, writes included (a login form, `POST /api/...`, as in Flutter); CONNECT and
 * TRACE never leave. Page documents carry [documentPolicy], which refuses frames, websockets, workers, plugins and
 * connections to any other address.
 */
public class PreviewEgress(public val origin: WebOrigin) {
    public fun mayLoad(url: String): Boolean {
        val target = WebOrigin.of(url) ?: return false
        return target.host == origin.host && target.port == origin.port
    }

    public fun methodAllowed(method: String): Boolean = method.uppercase() in ALLOWED_METHODS

    /** Explicit http(s) sources only: `'self'` would also admit ws:// to the same host, so it never appears in connect-src. */
    public val documentPolicy: String
        get() {
            val here = "http://${origin.host}:${origin.port} https://${origin.host}:${origin.port}"
            return listOf(
                "default-src $here 'unsafe-inline' 'unsafe-eval' data: blob:",
                "connect-src $here",
                "frame-src 'none'",
                "child-src 'none'",
                "worker-src 'none'",
                "object-src 'none'",
                "frame-ancestors 'none'",
                "form-action $here",
                "base-uri $here",
            ).joinToString("; ")
        }

    private companion object {
        val ALLOWED_METHODS = setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    }
}
