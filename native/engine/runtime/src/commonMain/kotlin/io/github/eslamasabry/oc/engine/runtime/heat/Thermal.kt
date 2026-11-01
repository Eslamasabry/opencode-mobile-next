package io.github.eslamasabry.oc.engine.runtime.heat

/**
 * Android's thermal status (PowerManager, API 29+), mildest first (port of lib/platform/thermal.dart).
 *
 * Android does not close apps when the phone is hot: it slows the CPU and lets the heat build. [Severe] is where it
 * throttles hard; [Critical] and above is where the phone is about to shut itself down.
 */
public enum class ThermalStatus(public val wire: String) {
    /** Before Android 10, or a status this app does not know. */
    Unknown("unknown"),
    None("none"),
    Light("light"),
    Moderate("moderate"),
    Severe("severe"),
    Critical("critical"),
    Emergency("emergency"),
    Shutdown("shutdown"),
    ;

    public fun atLeast(other: ThermalStatus): Boolean = ordinal >= other.ordinal

    public companion object {
        /** The native status word; anything else is [Unknown] (B-ANDR-093). */
        public fun parse(value: String?): ThermalStatus = entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/**
 * One reading of how hot the phone is. [headroom] is `getThermalHeadroom(30)` (API 30+): the forecast 30 s ahead, where
 * 1.0 means Android throttles hard; null when the phone gives none.
 */
public data class ThermalReading(val status: ThermalStatus, val headroom: Double? = null) {
    public companion object {
        public val Unknown: ThermalReading = ThermalReading(ThermalStatus.Unknown)

        /** Android answers a negative number or NaN when it has no forecast (plan 11 §8.1, map T22): both read null. */
        public fun of(status: ThermalStatus, headroom: Double?): ThermalReading =
            ThermalReading(status, headroom?.takeIf { !it.isNaN() && it >= 0.0 })
    }
}

/** One reading of the battery: its level (0–100) and whether it charges. Either is null when the phone does not say. */
public data class BatteryReading(val level: Int? = null, val charging: Boolean? = null) {
    public companion object {
        public val Unknown: BatteryReading = BatteryReading()

        public fun of(level: Int?, charging: Boolean?): BatteryReading =
            BatteryReading(level?.takeIf { it in 0..100 }, charging)

        /** From the kernel's power-supply files (`capacity`, `status`), the fallback when the platform has no reading. */
        public fun fromSysfs(capacity: String?, status: String?): BatteryReading {
            val level = capacity?.trim()?.toIntOrNull()
            val charging =
                when (status?.trim()?.lowercase()) {
                    "charging", "full" -> true
                    "discharging", "not charging" -> false
                    else -> null
                }
            return of(level, charging)
        }
    }
}
