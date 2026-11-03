package io.github.eslamasabry.oc.engine.runtime.switching

/*
 * Port of `TermuxBridge.managesServerUrl` (lib/termux/bridge.dart), `BuiltinLinux.managesServerUrl`
 * (lib/builtin/builtin_linux.dart) and the `isManagedLocal` test of lib/state/connection/connect.dart: which saved
 * profile is an app-managed OpenCode on this phone.
 */

/** The kind of server a profile talks to (Dart `ServerBackend`); only [OpenCode] can be app-managed. */
public enum class ServerBackendKind { OpenCode, Codex, Other }

/** The profile facts the switch, the connect gate and the forget sweep need. Secrets are never part of it. */
public data class LocalProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val backend: ServerBackendKind = ServerBackendKind.OpenCode,
    val requiresOpenCode2Migration: Boolean = false,
    val openCode2HistoryPending: Boolean = false,
)

public object ManagedLocalServer {
    /** Termux's managed OpenCode. */
    public const val TERMUX_PORT: Int = 4096

    /** The in-app (built-in Linux) OpenCode: not Termux's 4096, so both can run at once. */
    public const val BUILTIN_PORT: Int = 4097

    public const val BUILTIN_URL: String = "http://127.0.0.1:4097"

    /** `http://127.0.0.1:<port>` exactly (default port 80); `localhost` or another port is not managed. */
    public fun managesUrl(value: String?, port: Int): Boolean {
        val address = parse(value) ?: return false
        return address.first == "127.0.0.1" && address.second == port
    }

    public fun isTermux(value: String?): Boolean = managesUrl(value, TERMUX_PORT)

    public fun isBuiltin(value: String?): Boolean = managesUrl(value, BUILTIN_PORT)

    /** An app-managed OpenCode at either managed address; another backend at the same URL is not. */
    public fun isManagedLocal(profile: LocalProfile?): Boolean =
        profile != null &&
            profile.backend == ServerBackendKind.OpenCode &&
            (isTermux(profile.baseUrl) || isBuiltin(profile.baseUrl))

    /** Dart `looksLikeInAppServer` (without the platform check, which the shell owns). */
    public fun looksLikeInAppServer(profile: LocalProfile?): Boolean =
        profile != null && profile.backend == ServerBackendKind.OpenCode && isBuiltin(profile.baseUrl)

    /** (host, port) of an `http` URL; null for any other scheme or an unparseable value. */
    private fun parse(value: String?): Pair<String, Int>? {
        if (value == null) return null
        val schemeEnd = value.indexOf("://")
        if (schemeEnd < 0 || !value.substring(0, schemeEnd).equals("http", ignoreCase = true)) return null
        val rest = value.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd).substringAfterLast('@')
        if (authority.startsWith("[")) return null
        val colon = authority.lastIndexOf(':')
        val host = (if (colon < 0) authority else authority.substring(0, colon)).lowercase()
        val port = if (colon < 0) 80 else authority.substring(colon + 1).toIntOrNull() ?: return null
        if (host.isEmpty()) return null
        return host to port
    }
}
