package io.github.eslamasabry.oc.engine.setupassist

/**
 * The parts of Dart's `Uri.tryParse` the setup rules read: scheme, user info, host and whether a query or fragment
 * exists. Null when the text is not a URI reference this reader accepts.
 */
internal data class SetupUri(
    val scheme: String,
    val userInfo: String,
    val host: String,
    val hasQuery: Boolean,
    val hasFragment: Boolean,
) {
    companion object {
        private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")

        fun parse(raw: String): SetupUri? {
            if (raw.any { it.code <= 0x20 || it.code == 0x7f }) return null
            val scheme = SCHEME.find(raw)?.groupValues?.get(1)?.lowercase() ?: ""
            var rest = raw.substring(if (scheme.isEmpty()) 0 else scheme.length + 1)
            val fragmentAt = rest.indexOf('#')
            val hasFragment = fragmentAt >= 0
            if (hasFragment) rest = rest.substring(0, fragmentAt)
            val queryAt = rest.indexOf('?')
            val hasQuery = queryAt >= 0
            if (hasQuery) rest = rest.substring(0, queryAt)
            var userInfo = ""
            var host = ""
            if (rest.startsWith("//")) {
                val authority = rest.substring(2).substringBefore('/')
                val at = authority.lastIndexOf('@')
                if (at >= 0) userInfo = authority.substring(0, at)
                val hostPort = if (at >= 0) authority.substring(at + 1) else authority
                host =
                    if (hostPort.startsWith("[")) {
                        val close = hostPort.indexOf(']')
                        if (close < 0) return null
                        hostPort.substring(1, close)
                    } else {
                        hostPort.substringBefore(':')
                    }
                val port = if (hostPort.startsWith("[")) hostPort.substringAfter(']', "").removePrefix(":") else hostPort.substringAfter(':', "")
                if (port.isNotEmpty() && port.any { !it.isDigit() }) return null
                if (host.any { it in "<>\"\\^`{|}" }) return null
            }
            return SetupUri(scheme, userInfo, host.lowercase(), hasQuery, hasFragment)
        }

        /** Dart `Uri.decodeFull`: null when a percent escape is malformed or the bytes are not UTF-8. */
        fun decodeFull(raw: String): String? {
            if ('%' !in raw) return raw
            val bytes = ArrayList<Byte>()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '%') {
                    if (i + 2 >= raw.length) return null
                    val hex = raw.substring(i + 1, i + 3)
                    val value = hex.toIntOrNull(16) ?: return null
                    bytes.add(value.toByte())
                    i += 3
                } else {
                    c.toString().encodeToByteArray().forEach(bytes::add)
                    i++
                }
            }
            val array = bytes.toByteArray()
            val text = array.decodeToString()
            return if (text.encodeToByteArray().contentEquals(array)) text else null
        }

        private val LOOPBACK = setOf("localhost", "127.0.0.1", "::1")

        /** The planner and controller rule: HTTPS, or HTTP to loopback, with a host and no credentials, query or fragment. */
        fun isSafeMcpUrl(raw: String): Boolean {
            val uri = parse(raw) ?: return false
            if (uri.host.isEmpty() || uri.userInfo.isNotEmpty() || uri.hasQuery || uri.hasFragment) return false
            return uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOOPBACK)
        }
    }
}
