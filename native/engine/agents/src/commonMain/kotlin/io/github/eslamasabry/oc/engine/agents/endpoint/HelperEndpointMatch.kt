package io.github.eslamasabry.oc.engine.agents.endpoint

import io.github.eslamasabry.oc.core.common.ids.ProfileId

/** A saved server as this rule sees it: its id, whether it is a Paseo server, and its address text. */
public data class EndpointCandidate(val id: ProfileId, val isPaseo: Boolean, val baseUrl: String) {
    override fun toString(): String = "EndpointCandidate(${id.raw})"
}

/**
 * Which saved Paseo server stands for the agent helper on this phone (port of `isLocalAgentProfile` and
 * `savedLocalAgentProfile`, lib/state/local_agent_server.dart; B-AGENT-119). Matched on the endpoint only, never on a
 * name: a Paseo server at `ws://` on [port] by any loopback name (127.0.0.1, localhost, ::1) is the same server.
 * Another port, scheme, host or backend is a different server.
 */
public class HelperEndpointMatch(public val port: Int) {
    public fun matches(candidate: EndpointCandidate): Boolean {
        if (!candidate.isPaseo) return false
        val url = candidate.baseUrl.trim()
        if (!url.startsWith("ws://")) return false
        val rest = url.removePrefix("ws://")
        val authority = rest.substring(0, rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it })
        if ('@' in authority) return false
        val (host, portText) = if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return false
            authority.substring(1, close) to authority.substring(close + 1).removePrefix(":").ifEmpty { null }
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon < 0) authority to null else authority.substring(0, colon) to authority.substring(colon + 1)
        }
        val endpointPort = if (portText == null) DEFAULT_WS_PORT else portText.toIntOrNull() ?: return false
        return isLoopbackHost(host) && endpointPort == port
    }

    /** The one saved server for the helper: [preferId] (the connected one) wins, else the first. */
    public fun saved(candidates: List<EndpointCandidate>, preferId: ProfileId? = null): EndpointCandidate? {
        var first: EndpointCandidate? = null
        for (candidate in candidates) {
            if (!matches(candidate)) continue
            if (preferId != null && candidate.id == preferId) return candidate
            if (first == null) first = candidate
        }
        return first
    }

    /** Only one saved server per endpoint folds into the phone row; any other stays a normal row. */
    public fun foldsIntoPhoneRow(
        candidate: EndpointCandidate,
        among: List<EndpointCandidate>,
        preferId: ProfileId? = null,
    ): Boolean = matches(candidate) && saved(among, preferId)?.id == candidate.id

    public companion object {
        private const val DEFAULT_WS_PORT = 80

        /** lib/domain/loopback_host.dart `isLoopbackHost`. */
        public fun isLoopbackHost(host: String): Boolean = host.lowercase().let { it == "localhost" || it == "127.0.0.1" || it == "::1" }
    }
}
