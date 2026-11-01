package io.github.eslamasabry.oc.feature.phone.presentation

import kotlin.time.Instant

/** Which guided card Running on this phone shows (port of child_process_limit.dart `ChildLimitCardKind`). */
public enum class ChildLimitCardKind {
    /** Developer options are on: one button opens them, with the one step to take there. */
    OpenSettings,

    /** Developer options are off: one button opens About phone, with how to turn them on. */
    EnableDeveloperOptions,

    /** This build cannot open Settings (or it did not open): the steps as text only. */
    InstructionsOnly,
}

/**
 * The guided "Disable child process restrictions" card's rules (plan 11 §7.2; port of `childLimitCard`). Never a nag:
 * only on Android 14+, only while the limit still applies, only after Android killed one of the app's programs or the
 * budget guard queued work often; hidden by the person until a newer kill. The app never changes the setting.
 */
public object ChildLimitCards {
    public const val FIRST_SDK: Int = 34

    public fun kind(
        status: ChildLimitStatus,
        recentKill: Boolean,
        queuedOften: Boolean,
        hiddenAt: Instant?,
        lastKillAt: Instant?,
    ): ChildLimitCardKind? {
        val sdk = status.sdkInt ?: return null
        if (sdk < FIRST_SDK) return null
        if (status.state == ChildLimitState.Lifted) return null
        if (!recentKill && !queuedOften) return null
        if (hiddenAt != null && !(lastKillAt != null && lastKillAt > hiddenAt)) return null
        if (!status.canOpenSettings) return ChildLimitCardKind.InstructionsOnly
        return if (status.developerOptionsEnabled == true) {
            ChildLimitCardKind.OpenSettings
        } else {
            ChildLimitCardKind.EnableDeveloperOptions
        }
    }
}
