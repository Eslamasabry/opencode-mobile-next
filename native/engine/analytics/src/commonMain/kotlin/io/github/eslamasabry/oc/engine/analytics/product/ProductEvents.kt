package io.github.eslamasabry.oc.engine.analytics.product

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

    /** A setup step started: `step`. */
    SetupStepReached("setup_step_reached"),

    /** A setup step failed: `step`, `kind`, `compat_mode`, `node_variant`. */
    SetupStepFailed("setup_step_failed"),

    /** A setup job finished: `minutes` (a bucket). */
    SetupDone("setup_done"),

    /** The first reply this install ever saw: `server_kind`, `seconds` (a bucket). */
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

    /** The app went to the background: `minutes` it was in front, a range. */
    SessionLength("session_length"),
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
                r == "chatsroute.new" -> NewChat
                r == "/" || r == "/home" || "chatshome" in r || r.startsWith("chatsroute") -> Conversations
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
            ProductEvent.SetupStepReached.wire to mapOf("step" to steps),
            ProductEvent.SetupStepFailed.wire to
                mapOf(
                    "step" to steps,
                    "kind" to ProductFailureKind.entries.map { it.wire }.toSet(),
                    "compat_mode" to bool,
                    "node_variant" to ProductNodeVariant.entries.map { it.wire }.toSet(),
                ),
            ProductEvent.SetupDone.wire to mapOf("minutes" to ProductBuckets.minutes.toSet()),
            ProductEvent.FirstReply.wire to
                mapOf(
                    "server_kind" to ProductServerKind.entries.map { it.wire }.toSet(),
                    "seconds" to ProductBuckets.seconds.toSet(),
                ),
            ProductEvent.ScreenView.wire to mapOf("screen" to ProductScreen.entries.map { it.wire }.toSet()),
            ProductEvent.FeatureUsed.wire to mapOf("feature" to ProductFeature.entries.map { it.wire }.toSet()),
            ProductEvent.SessionLength.wire to mapOf("minutes" to ProductBuckets.sessionMinutes.toSet()),
            ProductEvent.ConnectComputerChosen.wire to emptyMap(),
            ProductEvent.UpdateOffered.wire to emptyMap(),
            ProductEvent.UpdateInstalled.wire to emptyMap(),
        )
    }

private val SAFE_WORD = Regex("^[a-z0-9_]+$")
private val HOST_LIKE = Regex("\\w\\.\\w")

/**
 * A last check on any value, whatever the schema says (Flutter's `analyticsValueIsSafe`): no path separators, no `@`,
 * nothing shaped like a host or address (`a.b`), at most 40 characters, and plain lower-case words, digits and `_`.
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
                else -> null
            } ?: continue
        if (value !in values || !productValueIsSafe(value)) continue
        out[key] = value
    }
    return out
}
