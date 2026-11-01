package io.github.eslamasabry.oc.engine.analytics

/**
 * Coarse buckets (plan 16 §5.2). An event never carries a raw number except an app [VersionCode]; counts and
 * durations are bucketed at the emitter so a value can never identify a person or a conversation.
 */
public enum class CountBucket {
    Zero, One, TwoToFive, SixToTwenty, MoreThanTwenty;

    public companion object {
        public fun of(count: Int): CountBucket =
            when {
                count <= 0 -> Zero
                count == 1 -> One
                count <= 5 -> TwoToFive
                count <= 20 -> SixToTwenty
                else -> MoreThanTwenty
            }
    }
}

public enum class DurationBucket {
    Under100Ms, Under300Ms, Under1S, Under3S, Under10S, Under60S, Longer;

    public companion object {
        public fun ofMillis(ms: Long): DurationBucket =
            when {
                ms < 100 -> Under100Ms
                ms < 300 -> Under300Ms
                ms < 1_000 -> Under1S
                ms < 3_000 -> Under3S
                ms < 10_000 -> Under10S
                ms < 60_000 -> Under60S
                else -> Longer
            }
    }
}

/**
 * An app versionCode (the only number an event may carry). A plain class rather than plan 16's `@JvmInline value
 * class` so the JVM field type stays [VersionCode] and the K-SEC-11 reflection test can tell it from a raw `Int`.
 */
public class VersionCode(public val value: Int) {
    init {
        require(value in 0..MAX) { "versionCode out of range" }
    }

    override fun equals(other: Any?): Boolean = other is VersionCode && other.value == value

    override fun hashCode(): Int = value

    override fun toString(): String = "VersionCode($value)"

    public companion object {
        /** Google Play's ceiling for versionCode. */
        public const val MAX: Int = 2_100_000_000
    }
}
