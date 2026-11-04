package io.github.eslamasabry.oc.engine.runtime.heat

import kotlin.time.Instant

/** What the guard confirmed (Flutter `ThermalNoticeKind`). */
public enum class ThermalNoticeKind { Paused, Stopped, Resumed }

/**
 * The one shell line about the guard, shown until dismissed (Flutter `ThermalNotice`). Typed: `:ui:words` turns [kind]
 * into the sentence ([ThermalNoticeEnglish] is the reference English).
 */
public data class ThermalNotice(val kind: ThermalNoticeKind, val at: Instant)

/**
 * One confirmed act on one paused thing, told once per target (Flutter `onAct`): the app files it in that server's
 * "While you were away" (P6.2). [since] is the episode's start, [at] the act's time. This is the only report the guard
 * makes; it posts no notification of its own: the pause shows in the one status notification ([ThermalGuard.pauseActive]).
 */
public data class HeatAct(
    val kind: ThermalNoticeKind,
    val target: HeatPausable,
    val since: Instant,
    val at: Instant,
)

/**
 * The English copy of the shell line, the quiet status and the setting, exactly as lib/l10n/app_en.arb has them
 * (`thermalPausedNotice` …). Reference for `:ui:words` and the tests; the engine itself never shows copy.
 */
public object ThermalNoticeEnglish {
    public const val PAUSED_NOTICE: String = "Your phone is hot — paused the AI Team to cool down. It resumes by itself."
    public const val STOPPED_NOTICE: String =
        "Your phone is very hot — stopped the AI Team to protect it. Its work is kept, and it starts again once the " +
            "phone has cooled down."
    public const val RESUMED_NOTICE: String = "Resumed the AI Team — your phone has cooled down."

    public const val QUIET_PAUSED: String = "Phone is hot · AI Team paused until it cools"
    public const val QUIET_STOPPED: String = "Phone is very hot · AI Team stopped until it cools"
    public const val QUIET_RESUMED: String = "Phone cooled down · AI Team resumed"

    public const val SETTING: String = "Pause the AI Team when the phone is hot"
    public const val SETTING_DETAIL: String =
        "The team pauses with its work kept and resumes by itself once the phone cools."

    /** Flutter `thermalNoticeText`: the shell line's words. */
    public fun notice(kind: ThermalNoticeKind): String =
        when (kind) {
            ThermalNoticeKind.Paused -> PAUSED_NOTICE
            ThermalNoticeKind.Stopped -> STOPPED_NOTICE
            ThermalNoticeKind.Resumed -> RESUMED_NOTICE
        }

    /** The app-wide status slot's quiet words (`thermalKitStatus`). */
    public fun quiet(kind: ThermalNoticeKind): String =
        when (kind) {
            ThermalNoticeKind.Paused -> QUIET_PAUSED
            ThermalNoticeKind.Stopped -> QUIET_STOPPED
            ThermalNoticeKind.Resumed -> QUIET_RESUMED
        }
}
