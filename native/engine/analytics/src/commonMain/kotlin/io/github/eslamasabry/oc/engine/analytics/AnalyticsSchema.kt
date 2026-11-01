package io.github.eslamasabry.oc.engine.analytics

import io.github.eslamasabry.oc.engine.analytics.PropSpec.Companion.choice
import io.github.eslamasabry.oc.engine.analytics.PropSpec.Companion.count
import io.github.eslamasabry.oc.engine.analytics.PropSpec.Companion.duration
import io.github.eslamasabry.oc.engine.analytics.PropSpec.Companion.flag
import io.github.eslamasabry.oc.engine.analytics.PropSpec.Companion.version

/*
 * The single source of analytics events (plan 16 §5.3). A feature that wants a new event adds a class here, lists its
 * spec in AnalyticsSchema.events, and adds a PRIVACY.md row in the same change. AnalyticsSchemaTest checks names,
 * tiers and that every event class is registered.
 */

// ---------------------------------------------------------------- BASIC (always on, anonymous, minimal)

/** Each start, after the migration gate opens. Start-up time as a bucket. */
public data class AppStart(val kind: StartKind, val duration: DurationBucket) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> =
        mapOf("kind" to PropValue.Choice(kind), "duration" to PropValue.Duration(duration))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "app_start", AnalyticsTier.Basic, EventTrigger.AppStarted,
                listOf(choice("kind", StartKind.entries), duration("duration")),
            )
    }
}

/** First launch after the versionCode changed. */
public data class AppUpdated(val fromCode: VersionCode) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> = mapOf("from_code" to PropValue.Version(fromCode))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec("app_updated", AnalyticsTier.Basic, EventTrigger.FirstLaunchAfterUpdate, listOf(version("from_code")))
    }
}

/** Once, when the Flutter-to-native migration finishes or gives up. */
public data class MigrationDone(
    val outcome: MigrationOutcome,
    val failedStep: MigrationStep,
    val reentryCount: CountBucket,
    val duration: DurationBucket,
) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> =
        mapOf(
            "outcome" to PropValue.Choice(outcome),
            "failed_step" to PropValue.Choice(failedStep),
            "reentry_count" to PropValue.Count(reentryCount),
            "duration" to PropValue.Duration(duration),
        )

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "migration_done", AnalyticsTier.Basic, EventTrigger.MigrationEnded,
                listOf(
                    choice("outcome", MigrationOutcome.entries),
                    choice("failed_step", MigrationStep.entries),
                    count("reentry_count"),
                    duration("duration"),
                ),
            )
    }
}

/** First successful connect per server kind per UTC day. An address class, never an address. */
public data class BackendConnected(val kind: ServerKind, val where: AddressClass) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> =
        mapOf("kind" to PropValue.Choice(kind), "where" to PropValue.Choice(where))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "backend_connected", AnalyticsTier.Basic, EventTrigger.FirstConnectPerKindPerDay,
                listOf(choice("kind", ServerKind.entries), choice("where", AddressClass.entries)),
            )
    }
}

/** An in-app update attempt ended without the app being replaced. */
public data class UpdateOutcome(val result: UpdateResult) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> = mapOf("result" to PropValue.Choice(result))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "update_result", AnalyticsTier.Basic, EventTrigger.UpdateAttemptEnded,
                listOf(choice("result", UpdateResult.entries)),
            )
    }
}

/** A crash, by kind only: never a message, stack, thread or file name. */
public data class CrashSeen(val category: CrashCategory, val fatal: Boolean) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> =
        mapOf("category" to PropValue.Choice(category), "fatal" to PropValue.Flag(fatal))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "crash_seen", AnalyticsTier.Basic, EventTrigger.CrashSeen,
                listOf(choice("category", CrashCategory.entries), flag("fatal")),
            )
    }
}

/** A freeze (main-thread stall or ANR), by kind and length bucket. */
public data class FreezeSeen(val kind: FreezeKind, val duration: DurationBucket, val foreground: Boolean) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> =
        mapOf(
            "kind" to PropValue.Choice(kind),
            "duration" to PropValue.Duration(duration),
            "foreground" to PropValue.Flag(foreground),
        )

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "freeze_seen", AnalyticsTier.Basic, EventTrigger.FreezeSeen,
                listOf(choice("kind", FreezeKind.entries), duration("duration"), flag("foreground")),
            )
    }
}

/** An error the person saw, by category only (never the error text). */
public data class ErrorSeen(val category: ErrorCategory) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> = mapOf("category" to PropValue.Choice(category))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "error_seen", AnalyticsTier.Basic, EventTrigger.ErrorShown,
                listOf(choice("category", ErrorCategory.entries)),
            )
    }
}

/** Yesterday's use, as buckets. Emitted by the daily ledger when the UTC day rolls over. */
public data class DailyUse(
    val opens: CountBucket,
    val chatsStarted: CountBucket,
    val messagesSent: CountBucket,
) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> =
        mapOf(
            "opens" to PropValue.Count(opens),
            "chats_started" to PropValue.Count(chatsStarted),
            "messages_sent" to PropValue.Count(messagesSent),
        )

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "daily_use", AnalyticsTier.Basic, EventTrigger.OncePerDay,
                listOf(count("opens"), count("chats_started"), count("messages_sent")),
            )
    }
}

/** How many servers of each kind are set up, as buckets. Once per day. */
public data class ServerKinds(val openCode2: CountBucket, val paseo: CountBucket, val codex: CountBucket) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> =
        mapOf(
            "opencode2" to PropValue.Count(openCode2),
            "paseo" to PropValue.Count(paseo),
            "codex" to PropValue.Count(codex),
        )

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "server_kinds", AnalyticsTier.Basic, EventTrigger.OncePerDay,
                listOf(count("opencode2"), count("paseo"), count("codex")),
            )

        public fun of(counts: Map<ServerKind, Int>): ServerKinds =
            ServerKinds(
                openCode2 = CountBucket.of(counts[ServerKind.OpenCode2] ?: 0),
                paseo = CountBucket.of(counts[ServerKind.Paseo] ?: 0),
                codex = CountBucket.of(counts[ServerKind.Codex] ?: 0),
            )
    }
}

/** How many agents of each kind are in use, as buckets. Once per day. */
public data class AgentKinds(
    val openCode: CountBucket,
    val claudeCode: CountBucket,
    val codex: CountBucket,
    val gemini: CountBucket,
    val acp: CountBucket,
    val other: CountBucket,
) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> =
        mapOf(
            "opencode" to PropValue.Count(openCode),
            "claude_code" to PropValue.Count(claudeCode),
            "codex" to PropValue.Count(codex),
            "gemini" to PropValue.Count(gemini),
            "acp" to PropValue.Count(acp),
            "other" to PropValue.Count(other),
        )

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "agent_kinds", AnalyticsTier.Basic, EventTrigger.OncePerDay,
                listOf(count("opencode"), count("claude_code"), count("codex"), count("gemini"), count("acp"), count("other")),
            )

        public fun of(counts: Map<AgentKind, Int>): AgentKinds {
            fun b(kind: AgentKind) = CountBucket.of(counts[kind] ?: 0)
            return AgentKinds(
                b(AgentKind.OpenCode), b(AgentKind.ClaudeCode), b(AgentKind.Codex),
                b(AgentKind.Gemini), b(AgentKind.Acp), b(AgentKind.Other),
            )
        }
    }
}

// ---------------------------------------------------------------- DETAILED (opt-in)

/** A screen opened: its kind only, never arguments (07 N8). */
public data class ScreenOpened(val screen: ScreenId) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> = mapOf("screen" to PropValue.Choice(screen))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "screen_open", AnalyticsTier.Detailed, EventTrigger.ScreenOpened,
                listOf(choice("screen", ScreenId.entries)),
            )
    }
}

/** A feature used. */
public data class FeatureUsed(val feature: UsedFeature) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> = mapOf("feature" to PropValue.Choice(feature))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "feature_used", AnalyticsTier.Detailed, EventTrigger.FeatureUsed,
                listOf(choice("feature", UsedFeature.entries)),
            )
    }
}

/** One step of a multi-step flow (started, completed, abandoned, failed). */
public data class FlowStepped(val flow: FlowId, val step: FlowStep) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> =
        mapOf("flow" to PropValue.Choice(flow), "step" to PropValue.Choice(step))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "flow_step", AnalyticsTier.Detailed, EventTrigger.FlowStepped,
                listOf(choice("flow", FlowId.entries), choice("step", FlowStep.entries)),
            )
    }
}

/** How long an action took, as a bucket. */
public data class ActionTimed(val action: TimedAction, val duration: DurationBucket) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> =
        mapOf("action" to PropValue.Choice(action), "duration" to PropValue.Duration(duration))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "action_timed", AnalyticsTier.Detailed, EventTrigger.ActionTimed,
                listOf(choice("action", TimedAction.entries), duration("duration")),
            )
    }
}

/** A setting changed: which one, never its value. */
public data class SettingChanged(val setting: SettingId) : AnalyticsEvent {
    override val spec: EventSpec get() = SPEC

    override fun props(): Map<String, PropValue> = mapOf("setting" to PropValue.Choice(setting))

    public companion object {
        public val SPEC: EventSpec =
            EventSpec(
                "setting_changed", AnalyticsTier.Detailed, EventTrigger.SettingChanged,
                listOf(choice("setting", SettingId.entries)),
            )
    }
}

// ---------------------------------------------------------------- the registry

/** The schema registry: every event spec, in disclosure order. */
public object AnalyticsSchema {
    public val events: List<EventSpec> =
        listOf(
            AppStart.SPEC, AppUpdated.SPEC, MigrationDone.SPEC, BackendConnected.SPEC, UpdateOutcome.SPEC,
            CrashSeen.SPEC, FreezeSeen.SPEC, ErrorSeen.SPEC, DailyUse.SPEC, ServerKinds.SPEC, AgentKinds.SPEC,
            ScreenOpened.SPEC, FeatureUsed.SPEC, FlowStepped.SPEC, ActionTimed.SPEC, SettingChanged.SPEC,
        )

    private val byName: Map<String, EventSpec> = events.associateBy { it.name }

    public fun spec(name: String): EventSpec? = byName[name]

    public fun tier(tier: AnalyticsTier): List<EventSpec> = events.filter { it.tier == tier }
}
