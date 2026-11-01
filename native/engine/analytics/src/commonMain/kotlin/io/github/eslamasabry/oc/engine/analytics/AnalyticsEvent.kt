package io.github.eslamasabry.oc.engine.analytics

/** BASIC is always on (anonymous, minimal, disclosed). DETAILED is sent only after the person opts in. */
public enum class AnalyticsTier { Basic, Detailed }

/** The only kinds of value an event property can hold (K-SEC-11): enums, booleans, buckets and a versionCode. */
public enum class PropKind { Choice, Flag, Count, Duration, Version }

/** One typed property value. There is deliberately no String, number or collection case. */
public sealed interface PropValue {
    public val kind: PropKind
    public val wire: String

    public data class Choice(val value: Enum<*>) : PropValue {
        override val kind: PropKind get() = PropKind.Choice
        override val wire: String get() = value.name
    }

    public data class Flag(val value: Boolean) : PropValue {
        override val kind: PropKind get() = PropKind.Flag
        override val wire: String get() = if (value) "true" else "false"
    }

    public data class Count(val value: CountBucket) : PropValue {
        override val kind: PropKind get() = PropKind.Count
        override val wire: String get() = value.name
    }

    public data class Duration(val value: DurationBucket) : PropValue {
        override val kind: PropKind get() = PropKind.Duration
        override val wire: String get() = value.name
    }

    public data class Version(val value: VersionCode) : PropValue {
        override val kind: PropKind get() = PropKind.Version
        override val wire: String get() = value.value.toString()
    }
}

/** When an event is emitted, as a key the "What we collect" screen turns into words (`:ui:words` owns the copy). */
public enum class EventTrigger {
    AppStarted, FirstLaunchAfterUpdate, MigrationEnded, FirstConnectPerKindPerDay, UpdateAttemptEnded, CrashSeen,
    FreezeSeen, ErrorShown, OncePerDay, ScreenOpened, FeatureUsed, FlowStepped, ActionTimed, SettingChanged,
}

/** One property of an event: its wire key, its kind and every wire value it may take. */
public class PropSpec internal constructor(
    public val key: String,
    public val kind: PropKind,
    /** Every allowed wire value. Empty only for [PropKind.Version], whose value is checked as a bounded number. */
    public val allowed: List<String>,
) {
    public fun permits(wire: String): Boolean =
        when (kind) {
            PropKind.Version -> VERSION_VALUE.matches(wire) && wire.toLong() <= VersionCode.MAX
            else -> wire in allowed
        }

    internal companion object {
        private val VERSION_VALUE = Regex("^(0|[1-9][0-9]{0,9})$")

        fun <E : Enum<E>> choice(key: String, entries: List<E>): PropSpec =
            PropSpec(key, PropKind.Choice, entries.map { it.name })

        fun flag(key: String): PropSpec = PropSpec(key, PropKind.Flag, listOf("true", "false"))

        fun count(key: String): PropSpec = PropSpec(key, PropKind.Count, CountBucket.entries.map { it.name })

        fun duration(key: String): PropSpec = PropSpec(key, PropKind.Duration, DurationBucket.entries.map { it.name })

        fun version(key: String): PropSpec = PropSpec(key, PropKind.Version, emptyList())
    }
}

/** The schema of one event: the registry entry the scrubber and the "What we collect" source both read. */
public class EventSpec internal constructor(
    public val name: String,
    public val tier: AnalyticsTier,
    public val trigger: EventTrigger,
    public val props: List<PropSpec>,
) {
    public fun prop(key: String): PropSpec? = props.firstOrNull { it.key == key }
}

/**
 * The one event API (plan 16 §5.2). Every event is a class in AnalyticsSchema.kt and is listed in
 * [AnalyticsSchema.events]. Properties are enums, booleans, buckets or a [VersionCode] only (K-SEC-11), so content,
 * paths, addresses, ids and credentials cannot be expressed.
 */
public sealed interface AnalyticsEvent {
    public val spec: EventSpec

    /** The typed values, keyed by [PropSpec.key]. Must match [spec] exactly; the scrubber rejects anything else. */
    public fun props(): Map<String, PropValue>
}
