package io.github.eslamasabry.oc.engine.analytics.health

/**
 * Firebase Performance Monitoring, the fixed part (section C2 of docs/telemetry/health-and-diagnostics.md; Flutter
 * 2.1.5 `lib/telemetry/perf_traces.dart`). Only the traces listed here reach the vendor, with attributes whose values
 * are listed words and the one numeric metric [DURATION_METRIC]; anything else is dropped, never cleaned up.
 *
 * There are no automatic network traces: the Performance Gradle plugin (`com.google.firebase.firebase-perf`), which
 * would record request URLs and so server addresses, is never applied (`TelemetryManifestTest` checks the build).
 */
public object PerfSchema {
    public const val CONN_CONNECT: String = "conn_connect"
    public const val SEND_FIRST_REPLY: String = "send_first_reply"
    public const val AGENT_START: String = "agent_start"
    public const val APP_COLD_START: String = "app_cold_start"
    public const val LINUX_SETUP_PREFIX: String = "linux_setup_"

    /** The metric every trace carries: the measured time in milliseconds. */
    public const val DURATION_METRIC: String = "duration_ms"

    /** `linux_setup_<step>` for one setup step word. */
    public fun linuxSetup(step: String): String = "$LINUX_SETUP_PREFIX$step"

    /** Every trace name that may be sent. */
    public val names: Set<String> =
        linkedSetOf(CONN_CONNECT, SEND_FIRST_REPLY, AGENT_START, APP_COLD_START) +
            HealthWords.linuxSteps.map(::linuxSetup)

    private val linuxAttributes: Map<String, Set<String>> =
        mapOf("step" to HealthWords.linuxSteps, "result" to HealthWords.okFail)

    /** The attributes [name] may carry and their listed values, or null when the trace is not listed. */
    public fun allowed(name: String): Map<String, Set<String>>? =
        when (name) {
            CONN_CONNECT -> mapOf("server_kind" to HealthWords.serverKinds, "result" to HealthWords.okFail)
            SEND_FIRST_REPLY -> mapOf("server_kind" to HealthWords.serverKinds)
            AGENT_START -> mapOf("agent" to HealthWords.agents, "result" to HealthWords.okFail)
            APP_COLD_START -> emptyMap()
            in names -> linuxAttributes
            else -> null
        }

    /** [name] and its listed [attributes], or null when the trace is not listed. Unlisted names and values drop. */
    public fun clean(name: String, attributes: Map<String, Any?>): PerfTraceRecord? {
        val allowed = allowed(name) ?: return null
        val out = LinkedHashMap<String, String>()
        for ((key, value) in attributes) {
            val values = allowed[key] ?: continue
            if (value !is String || value !in values) continue
            out[key] = value
        }
        return PerfTraceRecord(name, out)
    }
}

/** A cleaned trace. */
public data class PerfTraceRecord(val name: String, val attributes: Map<String, String>)
