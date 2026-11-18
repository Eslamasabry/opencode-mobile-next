package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.engine.analytics.umami.UmamiField
import io.github.eslamasabry.oc.engine.analytics.umami.UmamiFieldGroup

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
    Turns,
    Approvals,
    Stall,
    Queue,
    PhoneSetup,
    Agents,
    Background,
    Updates,
    Pages,
    Journey,
    Starters,
    ShareIn,
    StarAsk,
    Start,
    Smooth,
    Memory,
    Device,
    CrashKind,
    Exit,
    Steps,
    CrashKeys,
    Report,

    /** What our own server gets, one line per [UmamiFieldGroup]: the build, the phone, the look, the use. */
    OwnBuild,
    OwnPhone,
    OwnLook,
    OwnUse,
}

/** The fields of our own server's envelope that a line names; null for every other line. */
public val CollectItem.ownGroup: UmamiFieldGroup?
    get() =
        when (this) {
            CollectItem.OwnBuild -> UmamiFieldGroup.Build
            CollectItem.OwnPhone -> UmamiFieldGroup.Phone
            CollectItem.OwnLook -> UmamiFieldGroup.Look
            CollectItem.OwnUse -> UmamiFieldGroup.Use
            else -> null
        }

/** Which consent sends an item: its group on the page. */
public enum class CollectGroup {
    Usage,
    Crashes,
    Report,

    /** Sent to our own analytics server only, with Usage and app health on. */
    OwnServer,
}

/**
 * What the page lists: usage and app health, crash reports and the problem report. [contractEvents] names the event,
 * property or key behind each line. `PrivacyNoticeSyncTest` reads it against the live allow-lists, so a name the code
 * can send that no line names (or a line naming something the code cannot send) fails the build.
 */
public object HealthContract {
    public val items: Map<CollectGroup, List<CollectItem>> =
        mapOf(
            CollectGroup.Usage to
                listOf(
                    CollectItem.Connect,
                    CollectItem.Drop,
                    CollectItem.Send,
                    CollectItem.Turns,
                    CollectItem.Approvals,
                    CollectItem.Stall,
                    CollectItem.Queue,
                    CollectItem.PhoneSetup,
                    CollectItem.Agents,
                    CollectItem.Background,
                    CollectItem.Updates,
                    CollectItem.Pages,
                    CollectItem.Journey,
                    CollectItem.Starters,
                    CollectItem.ShareIn,
                    CollectItem.StarAsk,
                    CollectItem.Start,
                    CollectItem.Smooth,
                    CollectItem.Memory,
                    CollectItem.Device,
                ),
            CollectGroup.Crashes to
                listOf(CollectItem.CrashKind, CollectItem.Exit, CollectItem.Steps, CollectItem.CrashKeys),
            CollectGroup.Report to listOf(CollectItem.Report),
            CollectGroup.OwnServer to
                listOf(CollectItem.OwnBuild, CollectItem.OwnPhone, CollectItem.OwnLook, CollectItem.OwnUse),
        )

    public val contractEvents: Map<CollectItem, List<String>> =
        mapOf(
            CollectItem.Connect to listOf("conn_attempt", "connect_computer_chosen"),
            CollectItem.Drop to listOf("conn_drop"),
            CollectItem.Send to listOf("send_result"),
            CollectItem.Turns to listOf("chat_turn"),
            CollectItem.Approvals to listOf("approval"),
            CollectItem.Stall to listOf("turn_stall"),
            CollectItem.Queue to listOf("queue_flush"),
            CollectItem.PhoneSetup to
                listOf(
                    "linux_setup_step",
                    "setup_step_reached",
                    "setup_done",
                    "setup_abandoned",
                    "setup_resumed",
                ),
            CollectItem.Agents to listOf("agent_start"),
            CollectItem.Background to listOf("bg_service_end"),
            CollectItem.Updates to listOf("update_result", "update_offered", "update_installed"),
            CollectItem.Pages to listOf("app_open", "first_reply", "screen_view", "feature_used", "session_length"),
            CollectItem.Journey to listOf("journey_milestone", "journey_stage"),
            CollectItem.Starters to listOf("starter_used", "use_for"),
            CollectItem.ShareIn to listOf("share_in"),
            CollectItem.StarAsk to listOf("star_prompt_shown", "star_prompt_tapped", "star_prompt_dismissed"),
            CollectItem.Start to listOf("app_start", "app_exit"),
            CollectItem.Smooth to listOf("jank"),
            CollectItem.Memory to listOf("memory_pressure"),
            CollectItem.Device to
                listOf(
                    "app_build",
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
                listOf(
                    "app_version",
                    "build_kind",
                    "server_kind",
                    "engine_state",
                    "connections",
                    "oc_server_version",
                    "report_kind",
                    "report_code",
                ),
            CollectItem.Report to listOf("problem_report"),
        ) + UmamiFieldGroup.entries.associate { group ->
            // From the one field list: a field added there is named here, so the notices must name it too.
            ownItem(group) to UmamiField.entries.filter { it.group == group }.map { it.wire }
        }

    private fun ownItem(group: UmamiFieldGroup): CollectItem =
        when (group) {
            UmamiFieldGroup.Build -> CollectItem.OwnBuild
            UmamiFieldGroup.Phone -> CollectItem.OwnPhone
            UmamiFieldGroup.Look -> CollectItem.OwnLook
            UmamiFieldGroup.Use -> CollectItem.OwnUse
        }
}
