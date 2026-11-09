package io.github.eslamasabry.oc.engine.analytics.product

import io.github.eslamasabry.oc.engine.analytics.health.HealthRanges
import io.github.eslamasabry.oc.engine.analytics.health.HealthWords
import kotlin.time.Duration

/*
 * B-180: the product analytics list of Flutter 2.1.5 (`lib/telemetry/analytics_events.dart`), word for word. It is the
 * complete list of what may ever be counted: every event name and every value comes from here. There is no free text:
 * a value is one of a fixed set of short words, a bucket, or true/false. [ProductAnalytics.track] drops any event or
 * value not listed, so a call site cannot widen what leaves the phone. PRIVACY.md "Usage analytics" lists exactly
 * these; change them together.
 */

/** The events. [wire] is the name the vendor receives (the same as Flutter 2.1.5, so reports keep one history). */
public enum class ProductEvent(public val wire: String) {
    /** The app came to the front: `kind` cold (a new process) or warm. */
    AppOpen("app_open"),

    /** A setup step started: `step`, `attempt`. */
    SetupStepReached("setup_step_reached"),

    /** A setup job finished: `minutes_range` (the whole setup), `server_kind`. */
    SetupDone("setup_done"),

    /** The person left an unfinished setup for 5 minutes or more: `step` (the last reached), `reason`, `minutes_range`. */
    SetupAbandoned("setup_abandoned"),

    /** The person came back to an unfinished setup: `step`, `minutes_range` (how long they were away). */
    SetupResumed("setup_resumed"),

    /** The first reply this install ever saw: `server_kind`, `first_reply_range`. */
    FirstReply("first_reply"),

    /** The person chose to connect a computer. */
    ConnectComputerChosen("connect_computer_chosen"),

    /** An app update was offered (ready to install). */
    UpdateOffered("update_offered"),

    /** The app started on a newer version than last time. */
    UpdateInstalled("update_installed"),

    /** A page came into view: `screen`, from [ProductScreen]. */
    ScreenView("screen_view"),

    /** The person used a feature: `feature`, from [ProductFeature]. */
    FeatureUsed("feature_used"),

    /** The app went to the background: `minutes_range` it was in front. */
    SessionLength("session_length"),

    // ---- B-190 health and diagnostics (docs/telemetry/health-and-diagnostics.md) ----

    /** A connection attempt: `server_kind`, `result`, `fail_kind`, `ms_range`. */
    ConnAttempt("conn_attempt"),

    /** A live connection ended: `server_kind`, `reason`, `recovered`. */
    ConnDrop("conn_drop"),

    /** A message was sent: `result`, `fail_kind`, `first_reply_range`, `agent`, `server_kind`, `input`. */
    SendResult("send_result"),

    /** A turn stopped moving: `stage`, `minutes_range`. */
    TurnStall("turn_stall"),

    /** A turn ended: `outcome`, `agent`, `server_kind`, `first_reply_range`, `ms_range`, `tools_range`, `fail_kind`, `recovered`, `follow_up`. */
    ChatTurn("chat_turn"),

    /** An approval or question the person was asked: `kind`, `result`, `ms_range` (asked to answered). */
    Approval("approval"),

    /** Queued messages were flushed: `sent`, `failed` (count ranges). */
    QueueFlush("queue_flush"),

    /** A step of the built-in Linux setup finished. */
    LinuxSetupStep("linux_setup_step"),

    /** An agent was started: `agent`, `result`, `fail_kind`. */
    AgentStart("agent_start"),

    /** The background service ended: `reason`, `battery_exempt`. */
    BgServiceEnd("bg_service_end"),

    /** An update finished: `kind`, `result`, `fail_kind`. */
    UpdateResult("update_result"),

    /** Time to the first frame: `kind`, `ms_range`. */
    AppStart("app_start"),

    /** Slow frames on one kind of page, once per session: `screen`, `janky_pct_range`, `refresh_hz`. */
    Jank("jank"),

    /** Android asked the app to free memory: `level`. */
    MemoryPressure("memory_pressure"),
}

/** A value with a fixed wire word. */
public interface ProductValue {
    public val wire: String
}

/** Setup steps, as the setup engine names them. Anything else is counted as [Other]. */
public enum class ProductSetupStep(override val wire: String) : ProductValue {
    Linux("linux"),
    Essentials("essentials"),
    Python("python"),
    Node("node"),
    Opencode("opencode"),
    Aiteam("aiteam"),
    Voice("voice"),
    Start("start"),
    Other("other");

    public companion object {
        public fun of(id: String): ProductSetupStep = entries.firstOrNull { it.wire == id } ?: Other
    }
}

/** Why a setup step failed, in five words. */
public enum class ProductFailureKind(override val wire: String) : ProductValue {
    Crash("crash"),
    Timeout("timeout"),
    Network("network"),
    Space("space"),
    Other("other");

    public companion object {
        /** Flutter's `analyticsFailureKind`: only the resulting word leaves the phone, never [error]. */
        public fun of(exit: Int? = null, error: String? = null): ProductFailureKind {
            val text = error.orEmpty().lowercase()
            fun has(vararg words: String) = words.any { it in text }
            return when {
                exit == 139 || exit == 134 || exit == 132 ||
                    has("segmentation fault", "illegal instruction", "aborted", "core dumped") -> Crash
                exit == 124 || has("timed out", "timeout") -> Timeout
                has("no space", "enospc", "storage", "disk full") -> Space
                has("network", "could not resolve", "connection", "unreachable", "download", "http", "tls", "ssl") ->
                    Network
                else -> Other
            }
        }
    }
}

/** Where the server that answered runs. */
public enum class ProductServerKind(override val wire: String) : ProductValue {
    Phone("phone"),
    Computer("computer"),
}

/** The Node.js safe mode setup settled on, or [NoneWorked] when it gave up. Anything else is sent as [Other]. */
public enum class ProductNodeVariant(override val wire: String) : ProductValue {
    Armcap("armcap"),
    Threads("threads"),
    Both("both"),
    NoneWorked("none_worked"),
    Other("other");

    public companion object {
        public fun of(name: String): ProductNodeVariant =
            entries.firstOrNull { it.wire == name || it.name.equals(name, ignoreCase = true) } ?: Other
    }
}

/** Pages, by kind. A page outside this list is sent as [Other]; nothing about what is on the page is ever sent. */
public enum class ProductScreen(override val wire: String) : ProductValue {
    Conversations("conversations"),
    Chat("chat"),
    NewChat("new_chat"),
    Servers("servers"),
    ThisPhone("this_phone"),
    Setup("setup"),
    Privacy("privacy"),
    Appearance("appearance"),
    Integrations("integrations"),
    Settings("settings"),
    Files("files"),
    Git("git"),
    Review("review"),
    Team("team"),
    Usage("usage"),
    Agents("agents"),
    About("about"),
    Other("other");

    public companion object {
        /**
         * Flutter's `AnalyticsScreen.of`, over a native route's class name (`ChatsRoute.Home`, `ChatRoute`,
         * `PhoneRoute.ThisPhone`): only the matching word of this list is kept, never an argument of the route.
         */
        @Suppress("CyclomaticComplexMethod")
        public fun of(route: String): ProductScreen {
            val r = route.lowercase()
            return when {
                r == "/" || r == "/home" || "chatshome" in r || "chatsroute.home" in r || r == "chatsroute" ->
                    Conversations
                "newchat" in r -> NewChat
                r.startsWith("/chat/") || "chatscreen" in r || "chatroute" in r -> Chat
                "thisphone" in r || "this-phone" in r -> ThisPhone
                "setup" in r -> Setup
                "privacy" in r -> Privacy
                "appearance" in r -> Appearance
                "integration" in r -> Integrations
                // "Profile" holds "file": a server's profile page is a servers page.
                "server" in r || "profile" in r -> Servers
                "setting" in r -> Settings
                "file" in r -> Files
                "git" in r -> Git
                "review" in r || "diff" in r -> Review
                "team" in r -> Team
                "usage" in r -> Usage
                "agent" in r -> Agents
                "about" in r -> About
                else -> Other
            }
        }
    }
}

/** Features whose use is counted, once per use. */
public enum class ProductFeature(override val wire: String) : ProductValue {
    Voice("voice"),
    Camera("camera"),
    FileAttach("file_attach"),
    AgentSwitch("agent_switch"),
    ModelSwitch("model_switch"),
    ServerAdd("server_add"),
    GitAction("git_action"),
    ReviewOpen("review_open"),
}

/** Duration buckets: the vendor never sees an exact time. */
public object ProductBuckets {
    public val minutes: List<String> = listOf("under_2", "2_to_5", "5_to_10", "10_to_20", "20_to_40", "over_40")
    public val sessionMinutes: List<String> = listOf("under_1", "1_to_5", "5_to_15", "15_to_60", "over_60")
    public val seconds: List<String> =
        listOf("under_5", "5_to_15", "15_to_30", "30_to_60", "60_to_180", "over_180", "unknown")

    public fun minutesOf(elapsed: Duration): String {
        val m = elapsed.inWholeSeconds / 60.0
        return minutes[
            when {
                m < 2 -> 0
                m < 5 -> 1
                m < 10 -> 2
                m < 20 -> 3
                m < 40 -> 4
                else -> 5
            }
        ]
    }

    public fun sessionMinutesOf(elapsed: Duration): String {
        val m = elapsed.inWholeSeconds / 60.0
        return sessionMinutes[
            when {
                m < 1 -> 0
                m < 5 -> 1
                m < 15 -> 2
                m < 60 -> 3
                else -> 4
            }
        ]
    }

    public fun secondsOf(elapsed: Duration?): String {
        if (elapsed == null || elapsed.isNegative()) return "unknown"
        val s = elapsed.inWholeMilliseconds / 1000.0
        return seconds[
            when {
                s < 5 -> 0
                s < 15 -> 1
                s < 30 -> 2
                s < 60 -> 3
                s < 180 -> 4
                else -> 5
            }
        ]
    }
}

/** For each event, its properties and every value each may take (Flutter's `analyticsSchema`). */
public val productSchema: Map<String, Map<String, Set<String>>> =
    run {
        val bool = setOf("true", "false")
        val steps = ProductSetupStep.entries.map { it.wire }.toSet()
        mapOf(
            ProductEvent.AppOpen.wire to mapOf("kind" to setOf("cold", "warm")),
            ProductEvent.SetupStepReached.wire to
                mapOf("step" to steps, "attempt" to HealthRanges.attempts.toSet()),
            ProductEvent.SetupDone.wire to
                mapOf(
                    "minutes_range" to HealthRanges.setupTotal.toSet(),
                    "server_kind" to ProductServerKind.entries.map { it.wire }.toSet(),
                ),
            ProductEvent.SetupAbandoned.wire to
                mapOf(
                    "step" to steps,
                    "reason" to HealthWords.abandonReasons,
                    "minutes_range" to HealthRanges.away.toSet(),
                ),
            ProductEvent.SetupResumed.wire to
                mapOf("step" to steps, "minutes_range" to HealthRanges.away.toSet()),
            ProductEvent.FirstReply.wire to
                mapOf(
                    "server_kind" to ProductServerKind.entries.map { it.wire }.toSet(),
                    "first_reply_range" to HealthRanges.firstReply.toSet(),
                ),
            ProductEvent.ScreenView.wire to mapOf("screen" to ProductScreen.entries.map { it.wire }.toSet()),
            ProductEvent.FeatureUsed.wire to mapOf("feature" to ProductFeature.entries.map { it.wire }.toSet()),
            ProductEvent.SessionLength.wire to mapOf("minutes_range" to HealthRanges.sessionMinutes.toSet()),
            ProductEvent.ConnectComputerChosen.wire to emptyMap(),
            ProductEvent.UpdateOffered.wire to emptyMap(),
            ProductEvent.UpdateInstalled.wire to emptyMap(),
        ) + healthSchema()
    }

/** Flutter's `_healthSchema`: the B-190 events, every value from [HealthWords] or [HealthRanges]. */
private fun healthSchema(): Map<String, Map<String, Set<String>>> {
    val screens = ProductScreen.entries.map { it.wire }.toSet()
    return mapOf(
        ProductEvent.ConnAttempt.wire to
            mapOf(
                "server_kind" to HealthWords.serverKinds,
                "result" to HealthWords.okFail,
                "fail_kind" to HealthWords.failKinds,
                "ms_range" to HealthRanges.connMs.toSet(),
            ),
        ProductEvent.ConnDrop.wire to
            mapOf(
                "server_kind" to HealthWords.serverKinds,
                "reason" to HealthWords.dropReasons,
                "recovered" to HealthWords.yesNo,
            ),
        ProductEvent.SendResult.wire to
            mapOf(
                "result" to setOf("ok", "fail", "queued"),
                "fail_kind" to HealthWords.failKinds + HealthWords.sendOnlyFailKinds + HealthWords.modelFailKinds,
                "first_reply_range" to HealthRanges.firstReply.toSet(),
                "agent" to HealthWords.chatAgents,
                "server_kind" to HealthWords.serverKinds,
                "input" to HealthWords.inputs,
            ),
        ProductEvent.TurnStall.wire to
            mapOf("stage" to HealthWords.stallStages, "minutes_range" to HealthRanges.stallMinutes.toSet()),
        ProductEvent.ChatTurn.wire to
            mapOf(
                "outcome" to HealthWords.turnOutcomes,
                "agent" to HealthWords.chatAgents,
                "server_kind" to HealthWords.serverKinds,
                "first_reply_range" to HealthRanges.firstReply.toSet(),
                "ms_range" to HealthRanges.turn.toSet(),
                "tools_range" to HealthRanges.tools.toSet(),
                "fail_kind" to HealthWords.turnErrorKinds,
                "recovered" to HealthWords.recoveredTurn,
                "follow_up" to HealthWords.followUps,
            ),
        ProductEvent.Approval.wire to
            mapOf(
                "kind" to HealthWords.approvalKinds,
                "result" to HealthWords.approvalResults,
                "ms_range" to HealthRanges.turn.toSet(),
            ),
        ProductEvent.QueueFlush.wire to
            mapOf("sent" to HealthRanges.counts.toSet(), "failed" to HealthRanges.counts.toSet()),
        ProductEvent.LinuxSetupStep.wire to
            mapOf(
                "step" to HealthWords.linuxSteps,
                "result" to HealthWords.setupResults,
                "attempt" to HealthRanges.attempts.toSet(),
                "fail_kind" to HealthWords.setupFailKinds,
                "ms_range" to HealthRanges.setupMs.toSet(),
                "exit_kind" to HealthWords.exitKinds,
                "signal" to HealthWords.signals,
                "compat_mode" to HealthWords.compatModes,
                "free_space_range" to HealthRanges.storage.toSet(),
            ),
        ProductEvent.AgentStart.wire to
            mapOf(
                "agent" to HealthWords.agents,
                "result" to HealthWords.okFail,
                "fail_kind" to HealthWords.agentFailKinds,
            ),
        ProductEvent.BgServiceEnd.wire to
            mapOf("reason" to HealthWords.bgReasons, "battery_exempt" to HealthWords.yesNo),
        ProductEvent.UpdateResult.wire to
            mapOf(
                "kind" to HealthWords.updateKinds,
                "result" to HealthWords.updateResults,
                "fail_kind" to HealthWords.updateFailKinds,
            ),
        ProductEvent.AppStart.wire to
            mapOf("kind" to HealthWords.startKinds, "ms_range" to HealthRanges.appStartMs.toSet()),
        ProductEvent.Jank.wire to
            mapOf(
                "screen" to screens,
                "janky_pct_range" to HealthRanges.jankPct.toSet(),
                "refresh_hz" to HealthRanges.refreshHz.toSet(),
            ),
        ProductEvent.MemoryPressure.wire to mapOf("level" to HealthWords.memoryLevels),
    )
}

private val SAFE_WORD = Regex("^[A-Za-z0-9_<>+\\-]+$")
private val HOST_LIKE = Regex("\\w\\.\\w")

/**
 * A last check on any value, whatever the schema says (Flutter's `analyticsValueIsSafe`): no path separators, no `@`,
 * nothing shaped like a host or address (`a.b`), at most 40 characters, and only letters, digits and `_ < > + -` (the
 * range words such as `<500`, `2-10s`, `4+`, and signal names such as `SIGILL`).
 */
public fun productValueIsSafe(value: String): Boolean =
    value.isNotEmpty() && value.length <= 40 && '/' !in value && '@' !in value &&
        !HOST_LIKE.containsMatchIn(value) && SAFE_WORD.matches(value)

/**
 * [props] reduced to what [productSchema] allows for [event]: listed keys with listed values (bools as `true`/`false`,
 * [ProductValue]s by wire word) that also pass [productValueIsSafe]. Null when the event is not listed.
 */
public fun cleanProductProps(event: ProductEvent, props: Map<String, Any?>): Map<String, String>? {
    val allowed = productSchema[event.wire] ?: return null
    if (!productValueIsSafe(event.wire)) return null
    val out = LinkedHashMap<String, String>()
    for ((key, raw) in props) {
        val values = allowed[key] ?: continue
        val value =
            when (raw) {
                is Boolean -> raw.toString()
                is ProductValue -> raw.wire
                is String -> raw
                is Int -> raw.toString()
                else -> null
            } ?: continue
        if (value !in values || !productValueIsSafe(value)) continue
        out[key] = value
    }
    return out
}
