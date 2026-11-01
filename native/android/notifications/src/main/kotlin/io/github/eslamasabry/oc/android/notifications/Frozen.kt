package io.github.eslamasabry.oc.android.notifications

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri

/**
 * Notification ids (plan 11 §10.2, frozen): a Flutter-posted notification is dismissed by the native app with the
 * same id, and the person's shade keeps one row per id across the update.
 */
public object NotificationIds {
    public const val STATUS: Int = 4747
    public const val WIDGET_ACTION: Int = 4748
    public const val SETUP_PROGRESS: Int = 4098
    public const val SETUP_RESULT: Int = 4099
    public const val IDLE_RETURN: Int = 4100
    public const val NEEDS_YOU_SUMMARY: Int = 5998
    public const val FINISHED_SUMMARY: Int = 5999
    public const val VOICE_DOWNLOAD: Int = 0x701CE
    public const val HEAT: Int = 0x7E4A1
    public const val ALERT_BASE: Int = 6000

    /** Copied verbatim (`BackgroundConnectionService.codingAlertNotificationID`), so ids match Flutter-era alerts. */
    public fun alert(key: String): Int = ALERT_BASE + (key.hashCode() and 0x0fffffff)

    /** Broadcast request code of the [index]th button of notification [notificationId] (plan 11 §10.6). */
    public fun actionRequestCode(notificationId: Int, index: Int): Int = notificationId * 4 + index + 1
}

/** Alert dedupe keys (plan 11 §10.2). */
public object AlertKeys {
    public fun input(profile: String, session: String): String = "input:$profile:$session"

    public fun status(profile: String, session: String): String = "status:$profile:$session"
}

/** Group keys of the alert groups (plan 11 §10.4). */
public object AlertGroups {
    public const val NEEDS_YOU: String = "opencode_needs_you"
    public const val FINISHED: String = "opencode_finished"
}

/**
 * Intent actions, extras and data URIs the OS holds in `PendingIntent`s (plan 11 §2.5, frozen; copied verbatim from
 * the Flutter app's `BackgroundConnectionService` and `StatusNotification` companions). A Flutter-era alert still in
 * the shade after the update fires these exact strings into the native receivers.
 */
public object FrozenExtras {
    private const val PKG = "io.github.eslamasabry.opencode_mobile"

    public const val ACTION_CODING_ALERT: String = "$PKG.action.CODING_ALERT"
    public const val EXTRA_CODING_ALERT_KIND: String = "$PKG.extra.CODING_ALERT_KIND"
    public const val EXTRA_CODING_ALERT_SESSION_ID: String = "$PKG.extra.CODING_ALERT_SESSION_ID"
    public const val EXTRA_MONITOR_TOKEN: String = "$PKG.MONITOR_TOKEN"
    public const val EXTRA_CODING_ALERT_PROFILE_ID: String = "$PKG.extra.CODING_ALERT_PROFILE_ID"
    public const val EXTRA_CODING_ALERT_KEY: String = "$PKG.extra.CODING_ALERT_KEY"
    public const val EXTRA_CODING_ALERT_DECISION: String = "$PKG.extra.CODING_ALERT_DECISION"
    public const val EXTRA_CODING_ALERT_REQUEST_ID: String = "$PKG.CODING_ALERT_REQUEST_ID"
    public const val EXTRA_CODING_ALERT_OPTION_TEXT: String = "$PKG.extra.CODING_ALERT_OPTION_TEXT"
    public const val REMOTE_INPUT_REPLY: String = "oc.codingAlertReply"
    public const val EXTRA_SUMMARY_LINE: String = "oc.codingAlertSummaryLine"

    public const val ACTION_STATUS: String = "$PKG.action.STATUS"
    public const val EXTRA_STATUS_ACTION: String = "$PKG.extra.STATUS_ACTION"
    public const val EXTRA_STATUS_SESSION: String = "$PKG.extra.STATUS_SESSION"
    /** Native routing additions; legacy status taps without these cannot select a profile or turn. */
    public const val EXTRA_STATUS_PROFILE: String = "oc.statusProfile"
    public const val EXTRA_STATUS_TURN: String = "oc.statusTurn"
    public const val STOP_REPLY_URI: String = "opencode://status/stop_reply"

    /** New in the native app (S11-9): the status row's delete intent. Not held by any Flutter-era row. */
    public const val STATUS_DISMISSED_URI: String = "opencode://status/dismissed"

    /** `MainActivity.EXTRA_LAUNCH_ACTION` (`oc.shortcut`); New chat carries `new_task`. */
    public const val EXTRA_LAUNCH_ACTION: String = "oc.shortcut"

    /** The server service's stop action (`BuiltinServerService.ACTION_STOP`). */
    public const val ACTION_STOP_SERVER: String = "stop"

    /** Per-decision data URI so extras of two buttons never merge (map T17). */
    public fun codingAlertUri(key: String, decision: String): Uri = "opencode://coding-alert/$key/$decision".toUri()
}

/** OS-visible classes live in :androidApp at their frozen FQCNs (plan 11 §3); this module addresses them by name. */
public object FrozenComponents {
    private const val PKG = "io.github.eslamasabry.opencode_mobile"
    public const val MAIN_ACTIVITY: String = "$PKG.MainActivity"
    public const val CODING_ACTION_RECEIVER: String = "$PKG.CodingActionReceiver"
    public const val STATUS_ACTION_RECEIVER: String = "$PKG.StatusActionReceiver"
    public const val BACKGROUND_SERVICE: String = "$PKG.BackgroundConnectionService"
    public const val SERVER_SERVICE: String = "$PKG.BuiltinServerService"

    public fun of(context: Context, fqcn: String): ComponentName = ComponentName(context.packageName, fqcn)
}
