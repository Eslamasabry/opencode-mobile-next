package io.github.eslamasabry.oc.engine.analytics.umami

import io.github.eslamasabry.oc.engine.analytics.health.HealthRanges
import io.github.eslamasabry.oc.engine.analytics.health.HealthWords

/**
 * The ONE list of what our own analytics server (Umami) may be told about the app and the phone, beside an event's own
 * words. Three things read it, so they cannot drift apart:
 * - the sender (`umamiEvent` writes only these keys into an event's `data`);
 * - the allow-list guard on the real request body ([UmamiBodyGuard]), which drops any key or value not listed here;
 * - the "What we collect" page and the bundled policy (`:feature:settings` names every field of each [UmamiFieldGroup],
 *   and `PrivacyNoticeSyncTest` reads this list against the notices).
 *
 * Every value is one fixed word, a range word or a number matched by a pattern. Nothing here can name a person or
 * single out a phone: no model, maker, id, exact size, time zone, place, address, name, path or text.
 */
public enum class UmamiFieldGroup {
    /** The app build: [UmamiField.Version], [UmamiField.BuildNumber], [UmamiField.Channel]. */
    Build,

    /** The phone's kind and ranges. */
    Phone,

    /** How the app looks and runs: style, theme, battery saver. */
    Look,

    /** How the app is used: how long installed, which agents, which kind of connection. */
    Use,
}

public enum class UmamiField(
    public val wire: String,
    public val group: UmamiFieldGroup,
    private val words: Set<String>? = null,
    private val pattern: Regex? = null,
) {
    /** `3.0.1` or `3.0.1-test.10085`: the version name. */
    Version("version", UmamiFieldGroup.Build, pattern = Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.\\-]+)?")),

    /** The build number. */
    BuildNumber("build", UmamiFieldGroup.Build, pattern = Regex("[0-9]{1,10}")),

    /** `public` for a release, `test` for a test build (its version name carries `-test.<build>`). */
    Channel("channel", UmamiFieldGroup.Build, words = setOf("public", "test")),

    /** The Android MAJOR version only (for example 16). */
    OsMajor("os_major", UmamiFieldGroup.Phone, pattern = Regex("[1-9][0-9]{0,2}")),

    DeviceClass("device_class", UmamiFieldGroup.Phone, words = setOf("phone", "tablet")),

    /** Window width class by the Material cut-offs (600 and 840 dp), never a size. */
    WidthClass("width_class", UmamiFieldGroup.Phone, words = setOf("compact", "medium", "expanded")),

    /** The phone's text-size setting in four steps; see [textScaleOf]. */
    TextScale("text_scale", UmamiFieldGroup.Phone, words = setOf("smaller", "default", "larger", "largest")),

    /** The processor family. */
    Abi("abi", UmamiFieldGroup.Phone, words = HealthWords.abis + "other"),

    /** Total memory in GB, as a range ([HealthRanges.ram]). */
    RamRange("ram_range", UmamiFieldGroup.Phone, words = HealthRanges.ram.toSet()),

    /** Free storage in GB, as a range ([HealthRanges.storage]). */
    FreeStorageRange("free_storage_range", UmamiFieldGroup.Phone, words = HealthRanges.storage.toSet()),

    /** The chosen style (`Style.storageName`). */
    Style("style", UmamiFieldGroup.Look, words = setOf("solid", "glass", "soft", "brutal", "paper", "signal")),

    /** The chosen theme mode (the setting, not the colours). */
    ThemeMode("theme_mode", UmamiFieldGroup.Look, words = setOf("system", "light", "dark", "saver")),

    BatterySaver("battery_saver", UmamiFieldGroup.Look, words = HealthWords.yesNo),

    /** How long ago the app was first installed, in four steps; see [installAgeOf]. */
    InstallAge("install_age", UmamiFieldGroup.Use, words = installAgeWords),

    /** The kinds of agent server connected now: product names only, joined with `+`, or `none`. */
    Agents("agents", UmamiFieldGroup.Use, words = combinations(agentNames) + "none"),

    /** The kinds of connection open now: this phone, a paired computer, a remote server; joined with `+`, or `none`. */
    ConnectionKind("connection_kind", UmamiFieldGroup.Use, words = combinations(connectionNames) + "none"),
    ;

    /** Whether [value] is one of this field's fixed words or matches its pattern. */
    public fun accepts(value: String): Boolean =
        value.length <= MAX_VALUE && (words?.contains(value) ?: pattern?.matches(value) ?: false)

    /** The words this field may take, or null for a numeric or versioned field. */
    public val allowedWords: Set<String>? get() = words

    public companion object {
        private const val MAX_VALUE = 40
        private const val COMPACT_BELOW_DP = 600
        private const val MEDIUM_BELOW_DP = 840

        public fun ofWire(wire: String): UmamiField? = entries.firstOrNull { it.wire == wire }

        /** Keys that ride in an event's `data` besides the event's own words. */
        public val wires: Set<String> get() = entries.map { it.wire }.toSet()

        /** [raw] reduced to listed keys holding listed values. */
        public fun clean(raw: Map<String, String>): Map<String, String> =
            raw.filter { (key, value) -> ofWire(key)?.accepts(value) == true }

        public fun widthClassOf(widthDp: Int): String =
            when {
                widthDp < COMPACT_BELOW_DP -> "compact"
                widthDp < MEDIUM_BELOW_DP -> "medium"
                else -> "expanded"
            }

        /** Below 0.95 smaller, up to 1.05 default, up to 1.3 larger, above that largest. */
        public fun textScaleOf(scale: Float): String =
            when {
                scale < 0.95f -> "smaller"
                scale <= 1.05f -> "default"
                scale <= 1.3f -> "larger"
                else -> "largest"
            }

        public fun installAgeOf(elapsedMs: Long): String {
            val days = elapsedMs.coerceAtLeast(0) / DAY_MS
            return when {
                days < 1 -> installAgeWords.elementAt(0)
                days < 7 -> installAgeWords.elementAt(1)
                days < 30 -> installAgeWords.elementAt(2)
                else -> installAgeWords.elementAt(3)
            }
        }

        /** [names] sorted and joined with `+`, or `none` when empty. */
        public fun joined(names: Set<String>): String = names.sorted().joinToString("+").ifEmpty { "none" }
    }
}

private const val DAY_MS: Long = 86_400_000L

private val installAgeWords: Set<String> =
    linkedSetOf("under_1_day", "1_to_7_days", "7_to_30_days", "over_30_days")

/** The agent servers the app can be connected to, by product name. */
private val agentNames: List<String> = listOf("codex", "opencode", "paseo")

/** This phone, a paired computer, a remote server. */
private val connectionNames: List<String> = listOf("computer", "phone", "remote")

/** Every non-empty subset of [names], each sorted and joined with `+`. */
private fun combinations(names: List<String>): Set<String> {
    val out = linkedSetOf<String>()
    for (mask in 1 until (1 shl names.size)) {
        out += names.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.sorted().joinToString("+")
    }
    return out
}
