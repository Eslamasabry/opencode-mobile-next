package io.github.eslamasabry.oc.engine.integrations.host

import kotlinx.coroutines.withTimeoutOrNull

/** DNS port (Android: `InetAddress.getAllByName`). Answers are numeric literals; errors may carry any text. */
public fun interface HostAddressLookup {
    public suspend fun lookup(host: String): List<String>
}

/** A parsed numeric address: 4 or 16 bytes, plus the literal it came from. */
public class HostAddress private constructor(public val bytes: ByteArray, public val text: String) {
    public val isV4: Boolean get() = bytes.size == 4

    public companion object {
        /** Flutter `InternetAddress.tryParse`: a numeric literal, or null for a hostname. */
        public fun parse(text: String): HostAddress? = parseV4(text)?.let { HostAddress(it, text) } ?: parseV6(text)?.let { HostAddress(it, text) }

        private fun parseV4(text: String): ByteArray? {
            val parts = text.split('.')
            if (parts.size != 4) return null
            val out = ByteArray(4)
            for ((i, p) in parts.withIndex()) {
                if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
                val v = p.toInt()
                if (v > 255) return null
                out[i] = v.toByte()
            }
            return out
        }

        private fun parseV6(text: String): ByteArray? {
            if (':' !in text || text.count { it == ':' } > 7 || "::" in text.replaceFirst("::", "")) return null
            var body = text
            var tail = ByteArray(0)
            // An embedded IPv4 tail (::ffff:1.2.3.4).
            val lastColon = body.lastIndexOf(':')
            if ('.' in body.substring(lastColon + 1)) {
                tail = parseV4(body.substring(lastColon + 1)) ?: return null
                body = body.substring(0, lastColon + 1) + "0:0"
            }
            val halves = body.split("::")
            fun groups(s: String): List<Int>? =
                if (s.isEmpty()) {
                    emptyList()
                } else {
                    s.split(':').map { g ->
                        if (g.isEmpty() || g.length > 4 || !g.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
                        g.toInt(16)
                    }
                }
            val head = groups(halves[0]) ?: return null
            val rest = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()
            val all: List<Int> =
                if (halves.size == 2) {
                    val missing = 8 - head.size - rest.size
                    if (missing < 1) return null
                    head + List(missing) { 0 } + rest
                } else {
                    if (head.size != 8) return null
                    head
                }
            val out = ByteArray(16)
            for ((i, g) in all.withIndex()) {
                out[i * 2] = (g shr 8).toByte()
                out[i * 2 + 1] = (g and 0xff).toByte()
            }
            if (tail.isNotEmpty()) tail.copyInto(out, 12)
            return out
        }
    }
}

/**
 * Resolves once, rejects mixed routes and returns a numeric SSH destination (Flutter `ByoHostTailnetResolver`). A
 * hostname suffix is never evidence of tailnet membership; range validation cannot prove VPN state either, so the
 * pinned host key and the actual route remain needed.
 */
public class ByoHostTailnetResolver(private val lookup: HostAddressLookup) {
    public suspend fun resolve(target: ByoHostTarget): HostResult<ByoHostTarget> =
        // DNS error text, answers and exception strings never enter diagnostics: every failure is the same code.
        hostResult(ByoHostFailureCode.TailnetRequired) {
            val literal = HostAddress.parse(target.host)
            val addresses =
                if (literal != null) {
                    listOf(literal)
                } else {
                    (withTimeoutOrNull(10_000) { lookup.lookup(target.host) } ?: abort(ByoHostFailureCode.TailnetRequired)).map { HostAddress.parse(it) ?: abort(ByoHostFailureCode.TailnetRequired) }
                }
            if (addresses.isEmpty() || addresses.any { !isTailnetAddress(it) }) abort(ByoHostFailureCode.TailnetRequired)
            val first = addresses.sortedWith(compareBy<HostAddress> { if (it.isV4) 0 else 1 }.thenBy { it.text }).first()
            // Never hand OpenSSH the original hostname to resolve again.
            ByoHostTarget.of(target.user, first.text, target.port) ?: abort(ByoHostFailureCode.TailnetRequired)
        }

    public companion object {
        private val V6_PREFIX = intArrayOf(0xfd, 0x7a, 0x11, 0x5c, 0xa1, 0xe0)

        public fun isTailnetAddress(address: HostAddress): Boolean {
            val b = address.bytes.map { it.toInt() and 0xff }
            if (address.isV4) {
                if (b[0] != 100 || b[1] < 64 || b[1] > 127) return false
                // Tailscale service/internal ranges are not assignable to machines.
                if (b[1] == 100 && (b[2] == 0 || b[2] == 100) ||
                    b[1] == 115 && (b[2] == 92 || b[2] == 93) ||
                    b[1] == 101 && b[2] == 102 && b[3] == 103
                ) {
                    return false
                }
                return true
            }
            if (b.size != 16) return false
            for (i in V6_PREFIX.indices) if (b[i] != V6_PREFIX[i]) return false
            // Quad100's IPv6 service endpoint is not a machine either.
            return !((6 until 15).all { b[it] == 0 } && b[15] == 0x53)
        }
    }
}
