package io.github.eslamasabry.oc.engine.runtime.diagnostics

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.setup.LinuxChannel
import io.github.eslamasabry.oc.engine.runtime.setup.channelInt

/** How proot runs the in-app server, as `/proc` shows it (BuiltinLinux.kt `performance`). */
public enum class ProotMode {
    /** proot's seccomp filter is in place: most system calls run without stopping in proot. */
    Seccomp,

    /** Every system call stops in proot: several times slower. */
    Ptrace,

    /** No server runs, or the kernel does not say. */
    Unknown;

    public companion object {
        public fun parse(value: Any?): ProotMode =
            when (value) {
                "seccomp" -> Seccomp
                "ptrace" -> Ptrace
                else -> Unknown
            }
    }
}

/** Classification of an exit, without inferring a confirmed memory shortage. */
public enum class ServiceExitReason {
    /** Exit 137 may be a memory or Android phantom-process kill; the code alone cannot tell those from another SIGKILL. */
    MemoryOrPhantomKill,
    Exited,
    Unknown;

    public companion object {
        /** Only the native reason strings; an exit code, `oom` or `sigkill` stays unknown. */
        public fun parse(value: Any?): ServiceExitReason =
            when (value) {
                "memory_or_phantom_kill" -> MemoryOrPhantomKill
                "exited" -> Exited
                else -> Unknown
            }
    }
}

/**
 * One app-owned service's lifetime, without command or log text (port of `BuiltinServiceDiagnostics`). Null means no
 * valid observation; zero is an observation. Native counters are whole numbers: fractional, non-finite, string or
 * Boolean values never become a fabricated exit or uptime.
 */
public data class ServiceLifetime(
    val running: Boolean = false,
    val lastExitCode: Long? = null,
    /** Completed run duration. */
    val lastUptimeMs: Long? = null,
    /** Wall-clock time of the observed exit, never fabricated after app death. */
    val lastExitAtMs: Long? = null,
    /** Null on older apps. True when our runtime deliberately stopped the child. */
    val lastStopRequested: Boolean? = null,
    /** Current run duration. */
    val uptimeMs: Long? = null,
    val restartCount: Long = 0,
    val exitReason: ServiceExitReason = ServiceExitReason.Unknown,
) {
    public companion object {
        private const val MAX_DATE_MS = 8_640_000_000_000_000L

        public fun fromMap(map: Map<*, *>): ServiceLifetime {
            fun nonNegative(value: Any?) = channelInt(value)?.takeIf { it >= 0 }
            return ServiceLifetime(
                running = map["running"] == true,
                lastExitCode = channelInt(map["lastExitCode"]),
                lastUptimeMs = nonNegative(map["lastUptimeMs"]),
                lastExitAtMs = channelInt(map["lastExitAtMs"])?.takeIf { it in 1..MAX_DATE_MS },
                lastStopRequested = map["lastStopRequested"] as? Boolean,
                uptimeMs = nonNegative(map["uptimeMs"]),
                restartCount = nonNegative(map["restartCount"]) ?: 0,
                exitReason = ServiceExitReason.parse(map["exitReason"]),
            )
        }
    }
}

/** One reading of `performance` (port of `BuiltinPerformance`): what the Performance details show. */
public data class RuntimePerformance(
    val serverRunning: Boolean = false,
    val prootMode: ProotMode = ProotMode.Unknown,
    /** Seccomp filters on proot itself and on the server under it. */
    val prootFilters: Long? = null,
    val serverFilters: Long? = null,
    /** Whether the phone is kept awake for a running reply now. */
    val workHeld: Boolean = false,
    /** Current and previously launched services by native name, in reply order. Empty on older apps. */
    val services: Map<String, ServiceLifetime> = emptyMap(),
) {
    public companion object {
        /** Malformed containers and entries (non-string or empty names, non-map values) are left out. */
        public fun fromMap(map: Map<*, *>): RuntimePerformance =
            RuntimePerformance(
                serverRunning = map["serverRunning"] == true,
                prootMode = ProotMode.parse(map["prootMode"]),
                prootFilters = channelInt(map["prootFilters"]),
                serverFilters = channelInt(map["serverFilters"]),
                workHeld = map["workHeld"] == true,
                services = buildMap {
                    (map["services"] as? Map<*, *>)?.forEach { (key, value) ->
                        if (key is String && key.isNotEmpty() && value is Map<*, *>) put(key, ServiceLifetime.fromMap(value))
                    }
                },
            )
    }
}

/**
 * Performance diagnostics through the Linux channel (port of builtin_linux.dart `performance`; B-LINUX-026): the
 * `performance` method with no arguments. An absent reply (an older app) reads as no records. A platform without the
 * built-in Linux answers an empty reading without calling the channel at all.
 */
public class ServicePerformance(private val channel: LinuxChannel, private val supported: Boolean) {
    public suspend fun read(): Outcome<RuntimePerformance> {
        if (!supported) return RuntimePerformance().ok()
        return channel.invoke("performance", null).map { reply ->
            RuntimePerformance.fromMap(reply as? Map<*, *> ?: emptyMap<Any?, Any?>())
        }
    }
}
