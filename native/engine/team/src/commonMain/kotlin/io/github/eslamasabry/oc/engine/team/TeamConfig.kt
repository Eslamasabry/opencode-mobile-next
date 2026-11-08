package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.dart.DartJson

/** Where the team runs (Dart `OrchestrationProvider`, lib/state/profiles/models.dart). */
public enum class TeamProvider {
    /** A Gas City host on a computer. */
    GasCity,

    /** A fixture directory (demo). */
    Fixture,

    /** The phone's own team engine (libaiteam_engine.so, loopback HTTP). */
    PhoneEngine,
}

/** Where the host runs relative to the OpenCode server (Dart `OrchestrationHostMode`). */
public enum class TeamHostMode { Computer, Phone }

/**
 * The AI Team plugin's per-profile settings as Flutter stored them in the profile's `orchestration` object (Dart
 * `OrchestrationConfig`). A profile without one has the team off. Read only: native writes the object back untouched.
 */
public data class TeamConfig(
    val provider: TeamProvider,
    val url: String,
    val city: String = "",
    val hostMode: TeamHostMode = TeamHostMode.Computer,
    val front: Boolean = false,
    val enabledAt: String? = null,
) {
    /** The phone engine's loopback base without a trailing slash, or null when [url] is not one. */
    public val loopbackBase: String?
        get() = loopback(url)

    public companion object {
        /**
         * Dart `OrchestrationConfig.fromJson`: null for anything but a map with a non-empty string `url`, so a corrupt
         * entry reads as "team off". An unknown provider reads as Gas City, as in Dart.
         */
        public fun parse(encoded: String?): TeamConfig? {
            val map = encoded?.let(DartJson::decodeOrNull) as? Map<*, *> ?: return null
            return fromMap(map)
        }

        public fun fromMap(map: Map<*, *>): TeamConfig? {
            val url = map["url"] as? String
            if (url.isNullOrEmpty()) return null
            val provider =
                when (map["provider"]) {
                    "phoneEngine" -> TeamProvider.PhoneEngine
                    "fixture" -> TeamProvider.Fixture
                    else -> TeamProvider.GasCity
                }
            return TeamConfig(
                provider = provider,
                url = url,
                city = map["city"]?.toString() ?: "",
                hostMode = if (map["hostMode"] == "phone") TeamHostMode.Phone else TeamHostMode.Computer,
                front = map["front"] == true,
                enabledAt = map["enabledAt"] as? String,
            )
        }

        /**
         * Dart `PhoneEngineGateway`'s address rule: `http` to 127.0.0.1 or ::1 only, no user info, query, fragment or
         * path, and a port.
         */
        public fun loopback(url: String): String? {
            val rest = url.removePrefix("http://").takeIf { it.length < url.length } ?: return null
            val authority = rest.trimEnd('/')
            if (authority.isEmpty() || authority.any { it == '/' || it == '?' || it == '#' || it == '@' }) return null
            val host: String
            val port: String
            if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close < 0) return null
                host = authority.substring(1, close)
                if (authority.getOrNull(close + 1) != ':') return null
                port = authority.substring(close + 2)
            } else {
                val colon = authority.lastIndexOf(':')
                if (colon < 0) return null
                host = authority.substring(0, colon)
                port = authority.substring(colon + 1)
            }
            if (host != "127.0.0.1" && host != "::1") return null
            val number = port.toIntOrNull() ?: return null
            if (number !in 1..65535 || port != number.toString()) return null
            return "http://$authority"
        }
    }
}
