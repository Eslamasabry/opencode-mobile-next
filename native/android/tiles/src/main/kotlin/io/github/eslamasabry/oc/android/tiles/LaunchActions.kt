package io.github.eslamasabry.oc.android.tiles

import android.content.Intent

/**
 * A whitelisted launch action carried by a static shortcut, a tile or a widget tap in the
 * [LaunchActions.EXTRA] extra. [wire] is the exact string the Flutter `LaunchShortcutChannel`
 * accepted.
 */
sealed class LaunchAction(val wire: String) {
    object Connect : LaunchAction("connect")

    object NewTask : LaunchAction("new_task")

    object Activity : LaunchAction("activity")

    object Usage : LaunchAction("usage")

    object UsageSettings : LaunchAction("usage_settings")

    object BackgroundSettings : LaunchAction("background_settings")

    object PhoneSetup : LaunchAction("phone_setup")

    object PhoneSetupDone : LaunchAction("phone_setup_done")

    object Linux : LaunchAction("linux")

    /**
     * `usage_target:<step>:<provider>:<target>`, from the usage widget; [step] is sign_in, add_key
     * or set_up.
     */
    data class UsageTarget(val step: String, val provider: String, val target: String) :
        LaunchAction("usage_target:$step:$provider:$target")

    override fun toString(): String = wire
}

/** A pinned-session launcher shortcut tap: both ids, trimmed and non-empty. */
data class SessionShortcutTap(val profileId: String, val sessionId: String)

/**
 * Reads the one-shot launch extras MainActivity receives (B-ANDR-027, B-ANDR-028, B-ANDR-031). Each
 * consume removes what it read from the intent, so a recreated activity does not replay it. Unknown
 * or empty values are ignored.
 */
object LaunchActions {
    const val EXTRA: String = "oc.shortcut"
    const val EXTRA_PROFILE: String = "oc.shortcut.profile"
    const val EXTRA_SESSION: String = "oc.shortcut.session"

    private val FIXED: List<LaunchAction> =
        listOf(
            LaunchAction.Connect,
            LaunchAction.NewTask,
            LaunchAction.Activity,
            LaunchAction.Usage,
            LaunchAction.UsageSettings,
            LaunchAction.BackgroundSettings,
            LaunchAction.PhoneSetup,
            LaunchAction.PhoneSetupDone,
            LaunchAction.Linux,
        )

    private val USAGE_TARGET =
        Regex(
            "^usage_target:(sign_in|add_key|set_up):([a-z0-9][a-z0-9_-]{0,31}):([A-Za-z0-9._-]{1,64})$"
        )

    /** Pure: the whitelisted action [action] names (trimmed), or null. */
    fun parse(action: String?): LaunchAction? {
        val value = action?.trim().orEmpty()
        if (value.isEmpty()) return null
        FIXED.firstOrNull { it.wire == value }
            ?.let {
                return it
            }
        val match = USAGE_TARGET.matchEntire(value) ?: return null
        return LaunchAction.UsageTarget(
            match.groupValues[1],
            match.groupValues[2],
            match.groupValues[3],
        )
    }

    /**
     * Reads and removes [EXTRA]; null when absent or not whitelisted (the extra is removed either
     * way).
     */
    fun consume(intent: Intent?): LaunchAction? {
        if (intent == null || !intent.hasExtra(EXTRA)) return null
        val raw = intent.getStringExtra(EXTRA)
        intent.removeExtra(EXTRA)
        return parse(raw)
    }

    /**
     * Reads and removes both pinned-session extras. Null when the session extra is absent, or when
     * either id is missing or blank (both extras are removed whenever the session extra was
     * present).
     */
    fun consumeSessionShortcut(intent: Intent?): SessionShortcutTap? {
        if (intent == null || (!intent.hasExtra(EXTRA_SESSION) && !intent.hasExtra(EXTRA_PROFILE)))
            return null
        val sessionId = intent.getStringExtra(EXTRA_SESSION)?.trim().orEmpty()
        val profileId = intent.getStringExtra(EXTRA_PROFILE)?.trim().orEmpty()
        intent.removeExtra(EXTRA_SESSION)
        intent.removeExtra(EXTRA_PROFILE)
        if (sessionId.isEmpty() || profileId.isEmpty()) return null
        return SessionShortcutTap(profileId = profileId, sessionId = sessionId)
    }

    /** An intent that opens the launcher activity with [action], as tiles and shortcuts send it. */
    fun mainActivityIntent(packageName: String, action: LaunchAction): Intent =
        Intent(Intent.ACTION_MAIN)
            .setClassName(packageName, TilesComponents.MAIN_ACTIVITY)
            .putExtra(EXTRA, action.wire)
}
