// WIDGETS seam (native-authored, not a twin). The copies name Flutter-app classes that are shells in :androidApp or
// bodies in other modules (plan 11 §3, §4.4 E5/E7/E10). Each object below keeps the simple name and the members the
// copies read, so a copy needs one import line instead of a body edit. Class literals the OS resolves by name became
// `setClassName(context, X.COMPONENT)` with the frozen FQCN: the in-place update keeps every component name.
package io.github.eslamasabry.oc.android.widgets

/** E5: the launcher activity. The frozen extra name is copied from the Flutter `MainActivity`. */
public object MainActivity {
    public const val COMPONENT: String = "io.github.eslamasabry.opencode_mobile.MainActivity"
    public const val EXTRA_LAUNCH_ACTION: String = "oc.shortcut"

    /** Flutter read `MainActivity.backgroundChannel != null` as "the live engine can take a widget action now". */
    public val backgroundChannel: Any? get() = WidgetFeed.liveDispatcher
}

/** E7: the service shell that hosts live mode and widget actions (`:androidApp` → `:android:background`). */
public object BackgroundConnectionService {
    public const val COMPONENT: String = "io.github.eslamasabry.opencode_mobile.BackgroundConnectionService"
    public const val ACTION_WIDGET_ACTION: String = "io.github.eslamasabry.opencode_mobile.action.WIDGET_ACTION"
    public const val WIDGET_ACTION_NOTIFICATION_ID: Int = 4748
    public const val EXTRA_CODING_ALERT_KIND: String = "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_KIND"
    public const val EXTRA_CODING_ALERT_SESSION_ID: String =
        "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_SESSION_ID"
    public const val EXTRA_CODING_ALERT_PROFILE_ID: String =
        "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_PROFILE_ID"

    /** The live connection is up (Flutter: the service's `active` flag). */
    public val active: Boolean get() = WidgetFeed.connectionActive

    /** A widget action holds its 30 s lease (Flutter: `widgetActionPending`). */
    public val widgetActionPending: Boolean get() = WidgetFeed.widgetActionPending

    /** Hands an admitted action to the live engine ([LiveWidgetDispatch], the Flutter `dispatchWidgetAction` body). */
    public fun dispatchWidgetAction(payload: Map<String, Any?>, complete: (Map<String, Any?>) -> Unit) {
        LiveWidgetDispatch.dispatch(payload, complete)
    }
}

/** E10: "this phone's Linux" reads the runtime engine's status, never the runtime classes. */
public object BuiltinLinux {
    public fun serverRunningIfStarted(): Boolean = WidgetFeed.phoneServer == PhoneServerLine.Running
}

/** E10: the second half of the old read (the server service in the foreground) is folded into [WidgetFeed.phoneServer]. */
public object BuiltinServerService {
    public val isForegroundRunning: Boolean get() = false
}

/** The live-connection channel id (frozen, plan 11 §10.1), copied from the Flutter `StatusNotification`. */
public object StatusNotification {
    public const val CHANNEL_ID: String = "opencode_live_connection"
}

/** `:androidApp` shell (plan 11 §3); the copies read only the frozen meta-data name. */
public object WidgetOverlayActivity {
    public const val READY_META: String = "io.github.eslamasabry.opencode_mobile.WIDGET_OVERLAY_READY"
}

/** `:androidApp` shell (plan 11 §3); the copies build its intent from the frozen name. */
public object WidgetRouteActivity {
    public const val COMPONENT: String = "io.github.eslamasabry.opencode_mobile.WidgetRouteActivity"
}

/** The result map shape of `WidgetActionBridge.result` (plan 11 §11.3 rule 7), for the seams that cannot see it. */
internal object WidgetActionResults {
    fun of(payload: Map<String, Any?>, status: String, error: String? = null): Map<String, Any?> =
        mutableMapOf<String, Any?>("id" to payload["id"], "action" to payload["action"], "status" to status).apply {
            if (error != null) put("error", error)
        }
}
