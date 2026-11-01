package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * The one ongoing status notification.
 *
 * [BackgroundConnectionService] (the live connection) and
 * [BuiltinServerService] (the phone server) both call `startForeground` with
 * [ID] and the notification built here, so one row ever shows. Dart sends the
 * wording (`updateLiveStatus` carries a `status` map); when Dart is not
 * running, the phone server alone keeps a minimal fallback: Ready, Paused,
 * Stop OpenCode.
 *
 * The rules are in [StatusNotificationModel.kt]: the owner set (stopping one
 * service must not remove the row while the other runs) and the throttle (one
 * update per two seconds, only when the content changed).
 */
internal object StatusNotification {
    const val ID = 4747
    const val CHANNEL_ID = "opencode_live_connection"
    const val OWNER_LIVE = "live"
    const val OWNER_SERVER = "server"

    const val ACTION_STATUS = "io.github.eslamasabry.opencode_mobile.action.STATUS"
    const val EXTRA_STATUS_ACTION = "io.github.eslamasabry.opencode_mobile.extra.STATUS_ACTION"
    const val EXTRA_STATUS_SESSION = "io.github.eslamasabry.opencode_mobile.extra.STATUS_SESSION"
    const val METHOD_STATUS_ACTION = "statusAction"

    private val owners = StatusOwners()
    private val throttle = StatusThrottle()
    private val main = Handler(Looper.getMainLooper())

    /** What Dart last said; cleared when the live connection ends. */
    private val wording = StatusNotificationWording()

    @Volatile private var appContext: Context? = null

    fun isOwned(owner: String): Boolean = owners.has(owner)
    fun anyOwner(): Boolean = owners.any()

    /**
     * Called before `startForeground`: records [owner], clears the stale setup
     * "ready" row, and returns the notification both services show.
     */
    fun begin(context: Context, owner: String, notice: String? = null): Notification {
        appContext = context.applicationContext
        ensureChannel(context)
        if (owner == OWNER_SERVER) wording.serverNotice = notice?.takeIf { it.isNotBlank() }
        wording.androidPaused = false
        owners.start(owner)
        SetupService.cancelResult(context)
        val content = wording.effective(context, owners.has(OWNER_SERVER))
        throttle.force(content, SystemClock.elapsedRealtime())
        return build(context, content, ongoing = true)
    }

    /**
     * Called when [owner]'s service stops. Returns true when it was the last
     * owner and the caller should remove the foreground notification. While the
     * other service still runs the row stays, re-posted with what remains true.
     */
    fun end(context: Context, owner: String): Boolean {
        val wasOwner = owners.has(owner)
        val last = owners.stop(owner)
        if (owner == OWNER_LIVE) wording.dart = null
        if (owner == OWNER_SERVER) wording.serverNotice = null
        if (last) {
            throttle.reset()
        } else if (wasOwner && owners.any()) {
            val content = wording.effective(context, owners.has(OWNER_SERVER))
            throttle.force(content, SystemClock.elapsedRealtime())
            notify(context, content, ongoing = true)
        }
        return last
    }

    /** Dart's wording. Held for the next start when no service is running. */
    fun push(context: Context, content: StatusContent?): Boolean {
        appContext = context.applicationContext
        wording.dart = content
        if (content == null || !owners.any()) return false
        when (val step = throttle.offer(content, SystemClock.elapsedRealtime())) {
            is StatusThrottle.Step.Post -> notify(context, step.content, ongoing = true)
            is StatusThrottle.Step.Wait -> main.postDelayed({
                val latestContext = appContext
                if (latestContext != null && owners.any()) {
                    throttle.due(SystemClock.elapsedRealtime())?.let {
                        notify(latestContext, it, ongoing = true)
                    }
                }
            }, step.delayMs)
            StatusThrottle.Step.Skip -> Unit
        }
        return true
    }

    /** Locale changes re-read fixed copy even when nothing else changed. */
    fun refresh(context: Context) {
        if (!owners.any()) return
        val content = wording.effective(context, owners.has(OWNER_SERVER))
        throttle.force(content, SystemClock.elapsedRealtime())
        notify(context, content, ongoing = true)
    }

    /**
     * Android's background limit ended the live service: say so, in place of
     * the "connected" row, and keep saying it until the person opens the app.
     */
    fun pausedByAndroid(context: Context) {
        wording.androidPaused = true
        wording.dart = null
        val content = pausedContent(context)
        throttle.force(content, SystemClock.elapsedRealtime())
        // Still ongoing when the phone server holds the row; otherwise a plain,
        // dismissable notification that outlives the stopped service.
        notify(context, content, ongoing = owners.any())
    }

    fun clearPaused(context: Context) {
        wording.androidPaused = false
        if (!owners.any()) {
            try { context.getSystemService(NotificationManager::class.java)?.cancel(ID) } catch (_: Throwable) { }
        }
    }

    fun ensureChannel(context: Context) = ensureStatusChannel(context)

    internal fun build(context: Context, content: StatusContent, ongoing: Boolean): Notification =
        StatusNotificationRenderer.build(context, content, ongoing)
}

private fun pausedContent(context: Context) = StatusContent(
    kind = StatusKind.PAUSED_ANDROID,
    title = NativeStrings.get(context, R.string.native_status_paused_android_title),
    text = NativeStrings.get(context, R.string.native_status_paused_android_body),
    actions = listOf(StatusAction.OPEN),
    publicTitle = NativeStrings.get(context, R.string.native_status_public_paused),
)

private fun ensureStatusChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    // Silent: the status never makes a sound; moments do that.
    manager.createNotificationChannel(
        NotificationChannel(
            StatusNotification.CHANNEL_ID,
            NativeStrings.get(context, R.string.native_live_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = NativeStrings.get(context, R.string.native_live_description)
            setShowBadge(false)
        },
    )
}

private fun notify(context: Context, content: StatusContent, ongoing: Boolean) {
    try {
        context.getSystemService(NotificationManager::class.java)
            ?.notify(StatusNotification.ID, StatusNotification.build(context, content, ongoing))
    } catch (_: Throwable) { /* A cosmetic update never breaks a service. */ }
}

private class StatusNotificationWording {
    @Volatile var dart: StatusContent? = null
    /** Android's background limit stopped the live service. */
    @Volatile var androidPaused = false
    /** A title the phone server was started with ("Terminal is working..."). */
    @Volatile var serverNotice: String? = null
    fun effective(context: Context, server: Boolean): StatusContent =
        dart ?: if (androidPaused) pausedContent(context) else fallback(context, server)

    /**
     * What the row says when Dart has not spoken: the phone server alone, or a
     * live connection that has not reported yet. Ready, with Stop OpenCode
     * when the phone server is what is running.
     */
    private fun fallback(context: Context, server: Boolean): StatusContent {
        return StatusContent(
            kind = StatusKind.IDLE,
            title = serverNotice ?: NativeStrings.get(context, R.string.native_status_ready_title),
            text = NativeStrings.get(
                context,
                if (server) R.string.native_phone_server_body else R.string.native_status_ready_body,
            ),
            subText = if (server) NativeStrings.get(context, R.string.native_status_on_this_phone) else "",
            actions = if (server) listOf(StatusAction.NEW_CHAT, StatusAction.STOP_SERVER)
            else listOf(StatusAction.NEW_CHAT),
            publicTitle = NativeStrings.get(context, R.string.native_status_public_ready),
        )
    }
}
