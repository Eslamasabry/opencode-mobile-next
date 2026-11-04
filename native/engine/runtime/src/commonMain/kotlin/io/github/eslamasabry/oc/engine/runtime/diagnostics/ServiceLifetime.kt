package io.github.eslamasabry.oc.engine.runtime.diagnostics

/** A native observation; exit 137 alone cannot identify why a service stopped. */
public enum class NamedServiceExitReason {
    Unknown,
    Exited,
    MemoryOrPhantomKill,
}

public data class NamedServiceLifetime(
    val name: String,
    val running: Boolean = false,
    val lastExitCode: Long? = null,
    val lastUptimeMs: Long? = null,
    val lastExitAtMs: Long? = null,
    val lastStopRequested: Boolean? = null,
    val uptimeMs: Long? = null,
    val restartCount: Long = 0,
    val exitReason: NamedServiceExitReason = NamedServiceExitReason.Unknown,
)

/** B-LINUX-026: missing/old maps are empty; malformed values never become invented observations. */
public object ServiceLifetimes {
    public fun read(performance: Any?): List<NamedServiceLifetime> {
        val root = performance as? Map<*, *> ?: return emptyList()
        val services = root["services"] as? Map<*, *> ?: return emptyList()
        return services.entries.mapNotNull { (name, value) ->
            if (name !is String || name.isEmpty()) return@mapNotNull null
            val map = value as? Map<*, *> ?: return@mapNotNull null
            NamedServiceLifetime(
                name = name,
                running = map["running"] == true,
                lastExitCode = integer(map["lastExitCode"]),
                lastUptimeMs = nonNegative(map["lastUptimeMs"]),
                lastExitAtMs =
                    integer(map["lastExitAtMs"])?.takeIf { it in 1..8_640_000_000_000_000L },
                lastStopRequested = map["lastStopRequested"] as? Boolean,
                uptimeMs = nonNegative(map["uptimeMs"]),
                restartCount = nonNegative(map["restartCount"]) ?: 0,
                exitReason =
                    when (map["exitReason"]) {
                        "exited" -> NamedServiceExitReason.Exited
                        "memory_or_phantom_kill" -> NamedServiceExitReason.MemoryOrPhantomKill
                        else -> NamedServiceExitReason.Unknown
                    },
            )
        }
    }

    /**
     * Native channels encode integer types; a Double, string or Boolean is never truncated into
     * one.
     */
    private fun integer(value: Any?): Long? =
        when (value) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            else -> null
        }

    private fun nonNegative(value: Any?): Long? = integer(value)?.takeIf { it >= 0 }
}
