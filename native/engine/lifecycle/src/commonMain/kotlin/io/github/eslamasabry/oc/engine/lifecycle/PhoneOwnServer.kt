package io.github.eslamasabry.oc.engine.lifecycle

// Port of isPhoneOwnServer (lib/ui/widgets/termux_running_server_entry.dart), looksLikeInAppServer
// (lib/builtin/builtin_server.dart), isManagedPhoneProfile and isLocalAgentProfile (lib/state/local_agent_server.dart).
// Emulator QA F14: a server reached at the loopback address through `adb reverse` is a server, not "This phone".
// Only the app's own servers (the in-app one, the Termux one it manages, the agent daemon on this phone) count.

/** Which protocol a saved server speaks, as far as the "this phone" rule cares. */
public enum class PhoneServerBackend { OpenCode, Paseo, Other }

/** The parts of a saved server profile the rule reads. */
public data class PhoneServerCandidate(val id: String, val baseUrl: String, val backend: PhoneServerBackend = PhoneServerBackend.OpenCode)

/** What this platform can host: the in-app Linux (Android only) and Termux (Android only). */
public data class PhoneHostSupport(val inAppLinux: Boolean, val termux: Boolean) {
    public companion object {
        public val Android: PhoneHostSupport = PhoneHostSupport(inAppLinux = true, termux = true)
        public val Elsewhere: PhoneHostSupport = PhoneHostSupport(inAppLinux = false, termux = false)
    }
}

public object PhoneOwnServers {
    /** The in-app server's port (BuiltinLinux.serverPort); not Termux's. */
    public const val IN_APP_PORT: Int = 4097
    public const val IN_APP_URL: String = "http://127.0.0.1:$IN_APP_PORT"

    /** The server this app runs in Termux (termuxManagedServerPort). */
    public const val TERMUX_PORT: Int = 4096
    public const val TERMUX_URL: String = "http://127.0.0.1:$TERMUX_PORT"

    /** The agent daemon on this phone (TermuxBridge.localAgentsPort). */
    public const val LOCAL_AGENTS_PORT: Int = 6767

    /** True only for the phone's own servers: these lead with the phone mark; any other loopback address is a server. */
    public fun isPhoneOwnServer(profile: PhoneServerCandidate, support: PhoneHostSupport): Boolean =
        looksLikeInAppServer(profile, support) || (support.termux && (isManagedTermux(profile) || isLocalAgent(profile)))

    public fun looksLikeInAppServer(profile: PhoneServerCandidate?, support: PhoneHostSupport): Boolean =
        profile != null && profile.backend == PhoneServerBackend.OpenCode && support.inAppLinux &&
            managesExactly(profile.baseUrl, IN_APP_PORT)

    public fun isManagedTermux(profile: PhoneServerCandidate): Boolean =
        profile.backend == PhoneServerBackend.OpenCode && managesExactly(profile.baseUrl, TERMUX_PORT)

    public fun isLocalAgent(profile: PhoneServerCandidate): Boolean {
        if (profile.backend != PhoneServerBackend.Paseo) return false
        val url = SimpleUrl.parse(profile.baseUrl.trim()) ?: return false
        return url.scheme == "ws" && isLoopbackHost(url.host) && url.portOr(80) == LOCAL_AGENTS_PORT
    }

    /** `http://127.0.0.1:<port>` exactly: localhost or any other port is not the app's server. */
    internal fun managesExactly(value: String?, port: Int): Boolean {
        val url = value?.let(SimpleUrl::parse) ?: return false
        return url.scheme == "http" && url.host == "127.0.0.1" && url.portOr(80) == port
    }

    public fun isLoopbackHost(host: String): Boolean = host.lowercase().let { it == "localhost" || it == "127.0.0.1" || it == "::1" }
}

/** The scheme, host and port of an absolute URL; null when it is not one. */
internal data class SimpleUrl(val scheme: String, val host: String, val port: Int?) {
    fun portOr(default: Int): Int = port ?: default

    companion object {
        fun parse(raw: String): SimpleUrl? {
            val sep = raw.indexOf("://")
            if (sep <= 0) return null
            val scheme = raw.substring(0, sep).lowercase()
            if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
            val rest = raw.substring(sep + 3)
            val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
            if (authority.isEmpty()) return null
            val host: String
            val portText: String?
            if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close < 0) return null
                host = authority.substring(1, close)
                portText = authority.substring(close + 1).removePrefix(":").ifEmpty { null }
            } else {
                host = authority.substringBefore(':')
                portText = if (':' in authority) authority.substringAfter(':') else null
            }
            val port = portText?.let { it.toIntOrNull() ?: return null }
            return SimpleUrl(scheme, host.lowercase(), port)
        }
    }
}
