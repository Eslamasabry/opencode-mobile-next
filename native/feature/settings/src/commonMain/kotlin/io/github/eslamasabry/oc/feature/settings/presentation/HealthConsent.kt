package io.github.eslamasabry.oc.feature.settings.presentation

/**
 * The two answers of Flutter 2.1.5's health and diagnostics contract (docs/telemetry/health-and-diagnostics.md
 * on release/2.1.5): `usage` (usage and app health, sections B to D) and `crashes` (crash reports, section A).
 * Both start off. Stored once for the app, never per server.
 */
public data class HealthAnswers(val usage: Boolean = false, val crashes: Boolean = false)

/** Flutter 2.1.5's own keys (F: lib/telemetry/telemetry_provider.dart:28-34, crash_offer.dart), read and written as is. */
public object HealthConsentKeys {
    public const val USAGE: String = "oc.telemetry.usage"
    public const val CRASHES: String = "oc.telemetry.crashes"

    /** 2.1.5's single switch before the split: read once to carry an old answer over, never written. */
    public const val OLD_SINGLE: String = "oc.telemetry.crashReports"

    /** "Offer crash reports" (on unless turned off): an explicit off keeps crashes off on carry-over. */
    public const val CRASH_OFFER: String = "oc.crashReports.offer"

    /** The one-time "Help improve OpenCode Mobile?" question was asked. */
    public const val ASKED: String = "oc.telemetryConsentAsked"
}

/**
 * The carry-over rules, exactly as Flutter's `PreferencesTelemetryConsentStore` (telemetry_provider.dart:51-79):
 * saved answers win; with neither saved, the old single yes turns `usage` on, and `crashes` too unless the crash
 * offer was explicitly turned off; no old yes leaves both off.
 */
public object HealthCarryOver {
    public fun read(usage: Boolean?, crashes: Boolean?, oldYes: Boolean?, offer: Boolean?): HealthAnswers {
        if (usage != null || crashes != null) return HealthAnswers(usage ?: false, crashes ?: false)
        val yes = oldYes ?: false
        return HealthAnswers(usage = yes, crashes = yes && offer != false)
    }

    /**
     * What to save under the new keys once (and mark the question asked), or null: the new keys exist already, or
     * no old answer was ever given (then the first-start question asks).
     */
    public fun migrate(usage: Boolean?, crashes: Boolean?, oldYes: Boolean?, offer: Boolean?): HealthAnswers? {
        if (usage != null || crashes != null) return null
        if (oldYes == null && offer == null) return null
        return read(null, null, oldYes, offer)
    }
}

/** The stored answers (the host's adapter over the app preferences). */
public interface HealthConsentSetting {
    public fun read(): HealthAnswers

    /** Saves both answers; false when the store refused. */
    public suspend fun write(answers: HealthAnswers): Boolean

    /** This build can send crash reports at all (the vendor is present): the switches are shown only then. */
    public val available: Boolean
        get() = true
}

/** One plain line of "What we collect"; the screen gives each its words. */
public enum class CollectItem {
    Connect,
    Drop,
    Send,
    Stall,
    Queue,
    PhoneSetup,
    Agents,
    Background,
    Updates,
    Pages,
    Journey,
    Starters,
    Start,
    Smooth,
    Memory,
    Device,
    CrashKind,
    Exit,
    Steps,
    CrashKeys,
    Report,
}

/** Which consent sends an item: its group on the page. */
public enum class CollectGroup {
    Usage,
    Crashes,
    Report,
}

/**
 * The 2.1.5 contract as the page lists it: sections B, C and D under Usage and app health, A under Crash reports,
 * E under the problem report. [contractEvents] names the doc's event or section behind each line, so a test can
 * check none is left out.
 */
public object HealthContract {
    public val items: Map<CollectGroup, List<CollectItem>> =
        mapOf(
            CollectGroup.Usage to
                listOf(
                    CollectItem.Connect,
                    CollectItem.Drop,
                    CollectItem.Send,
                    CollectItem.Stall,
                    CollectItem.Queue,
                    CollectItem.PhoneSetup,
                    CollectItem.Agents,
                    CollectItem.Background,
                    CollectItem.Updates,
                    CollectItem.Pages,
                    CollectItem.Journey,
                    CollectItem.Starters,
                    CollectItem.Start,
                    CollectItem.Smooth,
                    CollectItem.Memory,
                    CollectItem.Device,
                ),
            CollectGroup.Crashes to
                listOf(CollectItem.CrashKind, CollectItem.Exit, CollectItem.Steps, CollectItem.CrashKeys),
            CollectGroup.Report to listOf(CollectItem.Report),
        )

    public val contractEvents: Map<CollectItem, List<String>> =
        mapOf(
            CollectItem.Connect to listOf("conn_attempt"),
            CollectItem.Drop to listOf("conn_drop"),
            CollectItem.Send to listOf("send_result"),
            CollectItem.Stall to listOf("turn_stall"),
            CollectItem.Queue to listOf("queue_flush"),
            CollectItem.PhoneSetup to listOf("linux_setup_step"),
            CollectItem.Agents to listOf("agent_start"),
            CollectItem.Background to listOf("bg_service_end"),
            CollectItem.Updates to listOf("update_result", "update_offered", "update_installed"),
            CollectItem.Pages to
                listOf("app_open", "setup_step_*", "first_reply", "screen_view", "feature_used", "session_length"),
            CollectItem.Journey to listOf("journey_milestone", "journey_stage"),
            CollectItem.Starters to listOf("starter_used"),
            CollectItem.Start to listOf("app_start"),
            CollectItem.Smooth to listOf("jank"),
            CollectItem.Memory to listOf("memory_pressure"),
            CollectItem.Device to
                listOf(
                    "ram_range",
                    "free_storage_range",
                    "abi",
                    "webview_major",
                    "notif_allowed",
                    "server_kind",
                    "oc_server_version",
                ),
            CollectItem.CrashKind to listOf("crash"),
            CollectItem.Exit to listOf("exit_reason"),
            CollectItem.Steps to listOf("breadcrumbs"),
            CollectItem.CrashKeys to
                listOf("app_version", "build_kind", "server_kind", "engine_state", "connections", "oc_server_version"),
            CollectItem.Report to listOf("problem_report"),
        )
}
