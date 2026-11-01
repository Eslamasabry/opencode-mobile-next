package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import io.github.eslamasabry.oc.engine.runtime.host.RestorePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.opencode_mobile.SessionsWidgetProvider
import io.github.eslamasabry.opencode_mobile.StatusWidgetProvider
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge

/** The Status widget's "this phone's Linux" line (plan 11 §4.4 E10): `Off | Running | Restoring`. */
public enum class PhoneServerLine {
    Off,
    Running,
    Restoring,
    ;

    public companion object {
        /** Derived from the runtime engine's status contract (plan 11 §5.7); never reads the runtime classes. */
        public fun from(status: RuntimeStatus): PhoneServerLine =
            when {
                !status.installed -> Off
                status.serverRunning -> Running
                status.restorePhase == RestorePhase.Waiting || status.restorePhase == RestorePhase.Restoring -> Restoring
                else -> Off
            }
    }
}

/** Hands one admitted widget action to the live connection (replaces the Flutter `oc/background` channel call). */
public fun interface WidgetLiveDispatcher {
    public fun dispatch(payload: Map<String, Any?>, complete: (Map<String, Any?>) -> Unit)
}

/**
 * Runs one widget action or one read-only status check when no live connection owns it (replaces the headless Dart
 * engine of `WidgetActionRuntime`; plan 11 §11.3: `WidgetActionRunner` / `WidgetStatusRunner` in `:engine:attention`,
 * bound by the app graph). [operation] is `"execute"` or `"checkStatus"`; [complete] gets the Dart-shaped result map,
 * or null when the run could not finish (the caller then reads "uncertain" or "unavailable", never a retry).
 */
public interface WidgetBackgroundRunner {
    public fun start(
        context: Context,
        payload: Map<String, Any?>,
        snapshotJson: String,
        deadlineEpochMs: Long,
        operation: String,
        complete: (Map<*, *>?) -> Unit,
    ): Handle

    public fun interface Handle {
        public fun cancel()
    }
}

/**
 * What the native engines tell the widgets (plan 11 §11.3). The app graph binds the runner and dispatcher and forwards
 * engine state here; each `on…` call redraws only when what a widget shows changed. Values are process-local, like the
 * Flutter service companions they replace: after a process start the widgets read "not live" until an engine says so.
 */
public object WidgetFeed {
    @Volatile public var connectionActive: Boolean = false
        private set

    @Volatile public var widgetActionPending: Boolean = false
        private set

    @Volatile public var phoneServer: PhoneServerLine = PhoneServerLine.Off
        private set

    @Volatile public var liveDispatcher: WidgetLiveDispatcher? = null

    @Volatile public var backgroundRunner: WidgetBackgroundRunner? = null

    /** From `RuntimeHost.status` (`:engine:runtime`). Redraws the Status widget only when its line changes. */
    public fun onRuntimeStatus(context: Context, status: RuntimeStatus): Boolean {
        val line = PhoneServerLine.from(status)
        if (line == phoneServer) return false
        phoneServer = line
        StatusWidgetProvider.refreshAll(context)
        return true
    }

    /**
     * From the connection engine: the live link came up or ended. Ending it marks the snapshot offline and keeps the
     * titles (`WidgetV2Bridge.invalidateConnection`, the Flutter service-stop path); starting it redraws only, because a
     * start alone must never mark an old snapshot connected again (the engine's next publish does that).
     */
    public fun onConnection(context: Context, active: Boolean): Boolean {
        if (active == connectionActive) return false
        connectionActive = active
        if (active) SessionsWidgetProvider.refreshAll(context) else WidgetV2Bridge.invalidateConnection(context)
        return true
    }

    /** A widget action took or released its lease ("Sending…" on the row). */
    public fun onWidgetActionPending(pending: Boolean) {
        widgetActionPending = pending
    }

    /** Test and process-reset hook: forget everything the engines said. */
    internal fun reset() {
        connectionActive = false
        widgetActionPending = false
        phoneServer = PhoneServerLine.Off
        liveDispatcher = null
        backgroundRunner = null
    }
}
