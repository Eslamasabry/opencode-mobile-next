package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.flutter.plugin.common.MethodChannel

class BackgroundConnectionService : Service() {
    private var liveOwner = false
    private val widgetActions = BackgroundWidgetActionController(this, { liveOwner }) {
        widgetActionPending = it
    }

    override fun onCreate() {
        super.onCreate()
        StatusNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_WIDGET_ACTION) {
            widgetActions.start(intent, startId)
            return START_NOT_STICKY
        }
        beginLive(startId)
        return START_NOT_STICKY
    }

    private fun beginLive(startId: Int) {
        // One shared status row: the phone server service uses the same ID.
        val notification = StatusNotification.begin(this, StatusNotification.OWNER_LIVE)
        // Android 15+ refuses a dataSync start once its daily budget is spent
        // (ForegroundServiceStartNotAllowedException). Uncaught, that ended the
        // whole app; now the start is reported as refused to the one waiting.
        val foreground = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    StatusNotification.ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(StatusNotification.ID, notification)
            }
        }.isSuccess
        if (!foreground) {
            lastStartFailure = START_REFUSED
            releaseStatus()
            stopSelf(startId)
            return
        }
        // Accepted start requests/onCreate are not evidence of a foreground
        // service. Clear the durable pause only after startForeground succeeds.
        if (!runCatching { BackgroundPauseStore(this).confirmedStarted() }.getOrDefault(false)) {
            lastStartFailure = START_RECEIPT_UNSAVED
            releaseStatus()
            stopSelf(startId)
        } else {
            lastStartFailure = null
            liveOwner = true
            active = true
            // A deliberate opt-in may arrive while the tap is executing. Its live
            // ownership survives completion of that independent transient action.
            if (widgetActions.hasLease) {
                getSystemService(NotificationManager::class.java).cancel(WIDGET_ACTION_NOTIFICATION_ID)
            }
            // Redraw only: an accepted Android service start is not server connectivity.
            runCatching { SessionsWidgetProvider.refreshAll(this) }
            LivePauseReceiver.setPausedByUser(this, false)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        widgetActions.destroy()
        runCatching { SessionsWidgetProvider.refreshAll(this) }
        if (liveOwner) {
            active = false
            runCatching { WidgetV2Bridge.invalidateConnection(this) }
        }
        getSystemService(NotificationManager::class.java).cancel(WIDGET_ACTION_NOTIFICATION_ID)
        // Idempotent: a service already released in onTimeout owns nothing.
        // While the phone server still runs, the shared row stays for it.
        if (liveOwner) StatusNotification.end(this, StatusNotification.OWNER_LIVE)
        super.onDestroy()
    }

    /**
     * Gives up this service's share of the one status row. Only the last owner
     * removes it; otherwise it is detached and stays for the service that
     * still runs.
     */
    private fun releaseStatus() {
        val last = StatusNotification.end(this, StatusNotification.OWNER_LIVE)
        stopForeground(if (last) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (liveOwner) runCatching { BackgroundPauseStore(this).recordTaskRemoved() }
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Android 15+ stops a dataSync foreground service once it has used its
     * daily budget. Stopping is required; staying silent is not. Dart's
     * "keep live in background" preference would otherwise stay true over a
     * service the system has already killed, so the user believes live mode
     * is running while events and terminals are disconnected.
     *
     * The notification goes first, then the push: whatever happens to the
     * channel, the ongoing "session is live" notification must not outlive
     * the session it claims is live.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        if (!liveOwner) {
            widgetActions.finish(null)
            stopSelf()
            return
        }
        // commit before stopping: neither Flutter nor onDestroy is guaranteed
        // to be alive afterward. An old enabled preference must not restart it.
        runCatching { BackgroundPauseStore(this).timeout() }
        active = false
        runCatching { WidgetV2Bridge.invalidateConnection(this) }
        releaseStatus()
        // The row says why, instead of vanishing: Android's background limit.
        StatusNotification.pausedByAndroid(this)
        notifyDartOfTimeout()
        stopSelf(startId)
    }

    /** Android 14 shortService timeout. Never change the permanent preference. */
    override fun onTimeout(startId: Int) {
        if (!liveOwner) {
            widgetActions.finish(null)
            stopSelf()
        }
    }

    private fun notifyDartOfTimeout() = notifyDartStopped(
        REASON_SYSTEM_TIMEOUT,
        runCatching { BackgroundPauseStore(this).status(false) }.getOrNull(),
    )

    companion object {
        const val ACTION_WIDGET_ACTION = "io.github.eslamasabry.opencode_mobile.action.WIDGET_ACTION"
        internal const val WIDGET_ACTION_NOTIFICATION_ID = 4748
        @Volatile internal var widgetActionPending = false
            private set

        private const val WIDGET_DISPATCH_TIMEOUT_MS = 6500L

        private const val ACTION_CHANNEL_ID = "opencode_coding_action"
        private const val STATUS_CHANNEL_ID = "opencode_coding_status"
        private const val TEAM_PROGRESS_CHANNEL_ID = "opencode_team_progress"
        // An ongoing team-progress notification removes itself if the app
        // stops refreshing it (nothing here assumes unbounded lifetime).
        private const val TEAM_PROGRESS_TIMEOUT_MS = 20L * 60L * 1000L
        private const val ACTION_QUIET_CHANNEL_ID = "opencode_coding_action_quiet"
        private const val CODING_ALERT_ID_BASE = 6000
        private const val GROUP_NEEDS_YOU = "opencode_needs_you"
        private const val GROUP_FINISHED = "opencode_finished"
        private const val NEEDS_SUMMARY_ID = 5998
        private const val FINISHED_SUMMARY_ID = 5999
        private const val MAX_SUMMARY_LINES = 5
        private const val MAX_ALERT_TITLE = 80

        // A finished reply clears itself after an hour.
        private const val FINISHED_TIMEOUT_MS = 60L * 60L * 1000L

        /// Pushed to Dart when Android's foreground-service time limit stops
        /// this service, and when the user taps "Pause background" on the
        /// ongoing notification. Handled in lib/background/live_background.dart.
        const val METHOD_TIMEOUT = "backgroundServiceTimeout"
        const val REASON_SYSTEM_TIMEOUT = "systemTimeout"

        /// Must match `BackgroundLiveController.pauseReason`.
        const val REASON_USER_PAUSE = "userPause"

        const val ACTION_PAUSE_LIVE =
            "io.github.eslamasabry.opencode_mobile.action.PAUSE_LIVE"

        @Volatile
        var active: Boolean = false
            private set

        /** Why the last start did not run, in fixed words Dart maps to a code. */
        @Volatile
        var lastStartFailure: String? = null
        const val START_REFUSED = "refused"
        const val START_RECEIPT_UNSAVED = "receiptUnsaved"

        /**
         * Dart's wording for the one status notification (the `status` map of
         * `updateLiveStatus`). Returns whether a notification was refreshed;
         * when no service is running the wording is only remembered for the
         * next start. A malformed map is refused and changes nothing.
         */
        fun updateLiveStatus(context: Context, status: Map<*, *>?): Boolean {
            val content = StatusContent.fromMap(status) ?: return false
            return StatusNotification.push(context, content)
        }

        /** Locale changes refresh fixed copy even when the status is unchanged. */
        fun refreshLocale(context: Context) = StatusNotification.refresh(context)

        /** Tells Dart the service stopped so the persisted preference and the
         *  settings switch follow the service rather than outliving it. */
        fun notifyDartStopped(reason: String, pause: Map<String, Any?>? = null) {
            val channel = MainActivity.backgroundChannel ?: return
            // invokeMethod is main-thread only; onTimeout and broadcasts are
            // not guaranteed to arrive there.
            Handler(Looper.getMainLooper()).post {
                channel.invokeMethod(
                    METHOD_TIMEOUT,
                    mapOf(
                        "enabled" to false,
                        "active" to false,
                        "reason" to reason,
                        "backgroundPause" to pause,
                    )
                )
            }
        }

        /** Widget and notification actions share the live engine and its domain gateway.
         * Never start/restart dataSync here: Android 15's budget is authoritative. */
        internal fun dispatchWidgetAction(
            payload: Map<String, Any?>,
            complete: (Map<String, Any?>) -> Unit,
        ) {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                val channel = MainActivity.backgroundChannel
                if (!active || channel == null) {
                    complete(WidgetActionBridge.result(payload, "rejected", "unavailable"))
                    return@post
                }
                var finished = false
                val timeout = Runnable {
                    if (!finished) {
                        finished = true
                        complete(WidgetActionBridge.result(payload, "uncertain", "uncertainDelivery"))
                    }
                }
                fun finish(value: Map<String, Any?>) {
                    if (finished) return
                    finished = true
                    handler.removeCallbacks(timeout)
                    complete(value)
                }
                handler.postDelayed(timeout, WIDGET_DISPATCH_TIMEOUT_MS)
                try {
                    channel.invokeMethod("widgetAction", payload, object : MethodChannel.Result {
                        override fun success(result: Any?) {
                            val response = result as? Map<*, *>
                            val status = response?.get("status") as? String
                            val sameAction = response != null && response["id"] == payload["id"] &&
                                response["action"] == payload["action"]
                            if (!sameAction || status !in setOf("accepted", "rejected", "uncertain")) {
                                finish(WidgetActionBridge.result(payload, "uncertain", "uncertainDelivery"))
                                return
                            }
                            // Only bounded enum tokens cross back into persistent snapshots.
                            val error = (response["error"] as? String)?.takeIf {
                                it in setOf(
                                    "invalidRequest", "staleRequest", "unavailable",
                                    "unsupported", "busy", "uncertainDelivery",
                                )
                            }
                            finish(WidgetActionBridge.result(payload, status!!, error))
                        }
                        override fun error(code: String, message: String?, details: Any?) {
                            finish(WidgetActionBridge.result(payload, "uncertain", "uncertainDelivery"))
                        }
                        override fun notImplemented() {
                            finish(WidgetActionBridge.result(payload, "rejected", "unavailable"))
                        }
                    })
                } catch (_: RuntimeException) {
                    finish(WidgetActionBridge.result(payload, "uncertain", "uncertainDelivery"))
                }
            }
        }

        fun start(context: Context) {
            lastStartFailure = null
            val intent = Intent(context, BackgroundConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            active = false
            context.stopService(Intent(context, BackgroundConnectionService::class.java))
        }

        internal fun showCodingAlert(context: Context, request: CodingAlertRequest): Boolean {
            if (!validCodingAlert(request)) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !manager.areNotificationsEnabled()) {
                false
            } else {
                baseCodingAlertContent(context, request)?.let {
                    postCodingAlert(context, manager, request, it)
                } ?: false
            }
        }

        private fun validCodingAlert(request: CodingAlertRequest): Boolean = with(request) {
            val quota = kind == "quota" || kind == "quota_reset"
            val validQuota = sessionID == "quota" && profileID.isNotBlank() &&
                monitorToken.matches(Regex("^[a-f0-9]{64}$"))
            sessionID.isNotBlank() && key.isNotBlank() && (!quota || validQuota)
        }

        private fun baseCodingAlertContent(context: Context, request: CodingAlertRequest): CodingAlertContent? =
            with(request) {
                val agent = agentName.takeIf { it.matches(Regex("^[A-Za-z0-9 .-]{1,32}$")) } ?: "OpenCode"
                when (kind) {
                    "quota" -> CodingAlertContent(
                        channelID = STATUS_CHANNEL_ID,
                        title = NativeStrings.get(context, R.string.native_alert_quota_title),
                        text = NativeStrings.get(context, R.string.native_alert_quota_body),
                        category = Notification.CATEGORY_STATUS,
                        priority = Notification.PRIORITY_DEFAULT
                    )
                    // A plan limit came back. Fixed copy; only a validated, short
                    // provider name enters the title.
                    "quota_reset" -> CodingAlertContent(
                        channelID = STATUS_CHANNEL_ID,
                        title = if (agentName.matches(Regex("^[A-Za-z0-9 .-]{1,32}$"))) {
                            NativeStrings.get(context, R.string.native_alert_quota_reset_title, agentName)
                        } else {
                            NativeStrings.get(context, R.string.native_alert_quota_reset_generic_title)
                        },
                        text = NativeStrings.get(context, R.string.native_alert_quota_reset_body),
                        category = Notification.CATEGORY_STATUS,
                        priority = Notification.PRIORITY_DEFAULT
                    )
                    // The lock screen's words: fixed, count-free and nameless.
                    // The shade's own words come from AlertCopy below.
                    "permission", "question" -> CodingAlertContent(
                        channelID = ACTION_CHANNEL_ID,
                        title = NativeStrings.get(context, R.string.native_public_needs_you),
                        text = "",
                        category = Notification.CATEGORY_RECOMMENDATION,
                        priority = Notification.PRIORITY_HIGH
                    )
                    "complete" -> CodingAlertContent(
                        channelID = STATUS_CHANNEL_ID,
                        title = NativeStrings.get(context, R.string.native_public_finished),
                        text = "",
                        category = Notification.CATEGORY_STATUS,
                        priority = Notification.PRIORITY_DEFAULT
                    )
                    "error" -> CodingAlertContent(
                        channelID = ACTION_CHANNEL_ID,
                        title = NativeStrings.get(context, R.string.native_status_public_stopped),
                        text = "",
                        category = Notification.CATEGORY_ERROR,
                        priority = Notification.PRIORITY_HIGH
                    )
                    // "Send a test notification": the requests channel, so it
                    // proves the loud one gets through; says what it is.
                    "test" -> CodingAlertContent(
                        channelID = ACTION_CHANNEL_ID,
                        title = NativeStrings.get(context, R.string.native_test_title),
                        text = NativeStrings.get(context, R.string.native_test_body),
                        category = Notification.CATEGORY_STATUS,
                        priority = Notification.PRIORITY_HIGH
                    )
                    else -> teamCodingAlertContent(context, request)
                }
            }

        private fun teamCodingAlertContent(context: Context, request: CodingAlertRequest): CodingAlertContent? =
            with(request) {
                val agent = agentName.takeIf { it.matches(Regex("^[A-Za-z0-9 .-]{1,32}$")) } ?: "OpenCode"
                when (kind) {
                    // Check-in reminder: the app observed a session busy past the
                    // user's chosen duration. Fixed copy like every other kind;
                    // the session is named only inside the app.
                    "checkin" -> CodingAlertContent(
                        channelID = STATUS_CHANNEL_ID,
                        title = NativeStrings.get(context, R.string.native_alert_checkin_title, agent),
                        text = NativeStrings.get(context, R.string.native_alert_checkin_body),
                        category = Notification.CATEGORY_STATUS,
                        priority = Notification.PRIORITY_DEFAULT
                    )
                    // AI Team (TEAM-203): fixed copy per kind; the session id is
                    // a gate or run id that Dart routes to the exact sheet. No
                    // title, prompt or option ever travels; `subtext` is only
                    // the saved server's name.
                    "team_decision" -> CodingAlertContent(
                        channelID = ACTION_CHANNEL_ID,
                        title = NativeStrings.get(context, R.string.native_team_decision_title),
                        text = NativeStrings.get(context, R.string.native_team_decision_body),
                        category = Notification.CATEGORY_RECOMMENDATION,
                        priority = Notification.PRIORITY_HIGH
                    )
                    "team_run_failed" -> CodingAlertContent(
                        channelID = ACTION_CHANNEL_ID,
                        title = NativeStrings.get(context, R.string.native_team_failed_title),
                        text = NativeStrings.get(context, R.string.native_team_failed_body),
                        category = Notification.CATEGORY_ERROR,
                        priority = Notification.PRIORITY_HIGH
                    )
                    "team_review" -> CodingAlertContent(
                        channelID = STATUS_CHANNEL_ID,
                        title = NativeStrings.get(context, R.string.native_team_review_title),
                        text = NativeStrings.get(context, R.string.native_team_review_body),
                        category = Notification.CATEGORY_STATUS,
                        priority = Notification.PRIORITY_DEFAULT
                    )
                    "team_completed" -> CodingAlertContent(
                        channelID = STATUS_CHANNEL_ID,
                        title = NativeStrings.get(context, R.string.native_team_completed_title),
                        text = NativeStrings.get(context, R.string.native_team_completed_body),
                        category = Notification.CATEGORY_STATUS,
                        priority = Notification.PRIORITY_DEFAULT
                    )
                    // AI Team progress: one ongoing, silent notification. The
                    // one place the app hands over its own line ("AI Team:
                    // <task> · step 3 of 5"); the lock screen shows fixed copy.
                    "team_progress" -> {
                        if (text.isBlank()) null else CodingAlertContent(
                            channelID = TEAM_PROGRESS_CHANNEL_ID,
                            title = title.ifBlank { NativeStrings.get(context, R.string.native_team_title) },
                            text = text,
                            category = Notification.CATEGORY_PROGRESS,
                            priority = Notification.PRIORITY_LOW
                        )
                    }
                    else -> null
                }
            }

        private fun privateCodingAlertContent(
            context: Context,
            request: CodingAlertRequest,
            baseContent: CodingAlertContent,
        ): CodingAlertContent {
            val agent = request.agentName.takeIf { it.matches(Regex("^[A-Za-z0-9 .-]{1,32}$")) } ?: "OpenCode"
            // The words the person chose (chat title, the first line of a
            // reply, the masked command) appear in the shade; the lock screen
            // gets the public version. Raw errors never travel: only a reason
            // token.
            val copy = AlertCopy.of(request) ?: return baseContent
            val title = when (copy.title) {
                AlertCopy.Title.LITERAL -> copy.titleArg
                AlertCopy.Title.ASK, AlertCopy.Title.QUESTION ->
                    NativeStrings.get(context, alertTitleRes(copy.title), agent)
                AlertCopy.Title.FINISHED_ANY, AlertCopy.Title.STOPPED_ANY ->
                    NativeStrings.get(context, alertTitleRes(copy.title))
                else -> NativeStrings.get(context, alertTitleRes(copy.title), copy.titleArg)
            }
            val text = if (request.kind == "error") {
                NativeStrings.get(context, alertReasonText(request.reason))
            } else copy.body
            return baseContent.copy(title = title, text = text)
        }

        private fun alertTitleRes(title: AlertCopy.Title): Int = when (title) {
            AlertCopy.Title.RUN -> R.string.native_ask_run
            AlertCopy.Title.EDIT -> R.string.native_ask_edit
            AlertCopy.Title.READ -> R.string.native_ask_read
            AlertCopy.Title.FOLDER -> R.string.native_ask_folder
            AlertCopy.Title.ASK -> R.string.native_alert_permission_title
            AlertCopy.Title.QUESTION -> R.string.native_alert_question_title
            AlertCopy.Title.FINISHED -> R.string.native_alert_complete_title
            AlertCopy.Title.FINISHED_ANY -> R.string.native_public_finished
            AlertCopy.Title.STOPPED -> R.string.native_alert_error_title
            AlertCopy.Title.STOPPED_ANY, AlertCopy.Title.LITERAL -> R.string.native_alert_stopped_any
        }

        private fun postCodingAlert(
            context: Context,
            manager: NotificationManager,
            request: CodingAlertRequest,
            baseContent: CodingAlertContent,
        ): Boolean = with(request) {
            val content = privateCodingAlertContent(context, request, baseContent)
            // Quiet requests use a low-importance channel, without sound or heads-up.
            val channelID = if (silent && content.channelID == ACTION_CHANNEL_ID) {
                ACTION_QUIET_CHANNEL_ID
            } else content.channelID
            createCodingAlertChannels(context, manager)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(channelID)?.importance == NotificationManager.IMPORTANCE_NONE
            ) return@with false
            val notificationID = codingAlertNotificationID(key)
            val builder = codingAlertBuilder(context, request, content, channelID)
            if (kind == "team_progress") {
                configureTeamProgress(context, builder)
            } else {
                configureCodingAlertPrivacy(context, request, builder, baseContent, channelID)
            }
            // Server · project rides beside the app name: one subtitle.
            if (subtext.isNotBlank()) builder.setSubText(subtext.trim().take(MAX_ALERT_TITLE))
            if (content.text.isNotEmpty()) builder.setStyle(Notification.BigTextStyle().bigText(content.text))
            builder.extras.putString(EXTRA_SUMMARY_LINE, AlertCopy.summaryLine(request, content.title))
            val buttons = AlertButtons.plan(kind, AlertCopy.answerable(kind, allowActions, detail))
            for (action in codingAlertActions(context, request, buttons, notificationID)) {
                builder.addAction(action)
            }
            manager.notify(notificationID, builder.build())
            alertGroup(kind)?.let { refreshGroupSummary(context, manager, it) }
            true
        }

        private fun codingAlertBuilder(
            context: Context,
            request: CodingAlertRequest,
            content: CodingAlertContent,
            channelID: String,
        ): Notification.Builder = with(request) {
            val pendingIntent = openCodingAlertIntent(context, request)
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, channelID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context).setPriority(content.priority)
            }
            builder
                .setSmallIcon(R.mipmap.ic_launcher)
                // Only a validated, short agent name enters the localized template.
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(kind != "team_progress")
                .setOnlyAlertOnce(kind != "quota" && kind != "quota_reset")
                .setCategory(content.category)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
        }

        /** Opens the app at the exact request or chat: the body tap and the Open button. */
        private fun openCodingAlertIntent(context: Context, request: CodingAlertRequest): PendingIntent =
            with(request) {
                val openApp = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_CODING_ALERT_KIND, kind)
                    putExtra(EXTRA_CODING_ALERT_SESSION_ID, sessionID)
                    putExtra(EXTRA_CODING_ALERT_PROFILE_ID, profileID)
                    putExtra(EXTRA_MONITOR_TOKEN, monitorToken)
                }
                PendingIntent.getActivity(
                    context,
                    codingAlertNotificationID(key),
                    openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        private fun configureTeamProgress(context: Context, builder: Notification.Builder) {
            builder.setOngoing(true).setShowWhen(false)
            // Timeout and a channel-bound public version need API 26;
            // minSdk is 24, where the app's own clear is the only one.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder
                    .setTimeoutAfter(TEAM_PROGRESS_TIMEOUT_MS)
                    .setPublicVersion(
                        Notification.Builder(context, TEAM_PROGRESS_CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle(NativeStrings.get(context, R.string.native_team_title))
                            .setContentText(NativeStrings.get(context, R.string.native_team_working))
                            .build()
                    )
            }
        }

        private fun configureCodingAlertPrivacy(
            context: Context,
            request: CodingAlertRequest,
            builder: Notification.Builder,
            publicContent: CodingAlertContent,
            channelID: String,
        ) {
            val kind = request.kind
            alertGroup(kind)?.let { builder.setGroup(it) }
            if (kind == "complete" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // A finished reply fades on its own; opening the chat clears
                // it sooner (Dart dismisses it).
                builder.setTimeoutAfter(FINISHED_TIMEOUT_MS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && kind != "quota" && kind != "quota_reset") {
                builder.setPublicVersion(
                    Notification.Builder(context, channelID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(publicContent.title)
                        .apply { if (publicContent.text.isNotEmpty()) setContentText(publicContent.text) }
                        .build()
                )
            }
        }

        private fun alertGroup(kind: String): String? = when (kind) {
            "permission", "question", "team_decision" -> GROUP_NEEDS_YOU
            "complete" -> GROUP_FINISHED
            else -> null
        }

        private fun alertReasonText(reason: String): Int = when (reason) {
            "auth" -> R.string.native_alert_reason_auth
            "context" -> R.string.native_alert_reason_context
            "length" -> R.string.native_alert_reason_length
            "filter" -> R.string.native_alert_reason_filter
            "model" -> R.string.native_alert_reason_model
            else -> R.string.native_alert_reason_other
        }

        /**
         * One summary per group ("3 need you", "3 conversations finished")
         * while two or more of its alerts show; none for a single alert. Its
         * lines are the children's own: each question, or each conversation's
         * name.
         */
        private fun refreshGroupSummary(context: Context, manager: NotificationManager, group: String) {
            try {
                val summaryID = if (group == GROUP_NEEDS_YOU) NEEDS_SUMMARY_ID else FINISHED_SUMMARY_ID
                val children = manager.activeNotifications.filter {
                    it.packageName == context.packageName &&
                        it.notification.group == group &&
                        (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
                }
                if (children.size < 2) {
                    manager.cancel(summaryID)
                    return
                }
                val needs = group == GROUP_NEEDS_YOU
                val plural = if (needs) R.plurals.native_group_need_you else R.plurals.native_group_finished
                val title = NativeStrings.quantity(context, plural, children.size, children.size)
                val channelID = if (needs) ACTION_CHANNEL_ID else STATUS_CHANNEL_ID
                val open = PendingIntent.getActivity(
                    context,
                    summaryID,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(context, channelID)
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(context)
                }
                builder
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setGroup(group)
                    .setGroupSummary(true)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setStyle(
                        groupSummaryStyle(children, title)
                    )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                    builder.setPublicVersion(
                        Notification.Builder(context, channelID)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("OpenCode · $title")
                            .build()
                    )
                }
                manager.notify(summaryID, builder.build())
            } catch (_: Throwable) { /* A summary is cosmetic; the alerts stand alone. */ }
        }

        private fun groupSummaryStyle(
            children: List<android.service.notification.StatusBarNotification>,
            title: String,
        ): Notification.InboxStyle =
            Notification.InboxStyle().also { inbox ->
                inbox.setBigContentTitle(title)
                children.take(MAX_SUMMARY_LINES).forEach { child ->
                    // Each conversation once, by name; a question by its words.
                    val extras = child.notification.extras
                    val line = extras.getString(EXTRA_SUMMARY_LINE)
                        ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                    if (line.isNotBlank()) inbox.addLine(line)
                }
            }

        // Fixed-copy notification actions; the intents carry only routing
        // identifiers, never request content.
        private fun codingAlertActions(
            context: Context,
            request: CodingAlertRequest,
            buttons: List<AlertButton>,
            notificationID: Int,
        ): List<Notification.Action> = with(request) {
            fun actionIntent(decision: String): Intent =
                Intent(context, CodingActionReceiver::class.java).apply {
                    action = "$ACTION_CODING_ALERT.$decision"
                    data = android.net.Uri.parse("opencode://coding-alert/$key/$decision")
                    putExtra(EXTRA_CODING_ALERT_KIND, kind)
                    putExtra(EXTRA_CODING_ALERT_SESSION_ID, sessionID)
                    putExtra(EXTRA_CODING_ALERT_KEY, key)
                    putExtra(EXTRA_CODING_ALERT_PROFILE_ID, profileID)
                    putExtra(EXTRA_CODING_ALERT_DECISION, decision)
                    // The exact pending request this notification represents;
                    // Dart refuses to resolve any other request with it.
                    putExtra(EXTRA_CODING_ALERT_REQUEST_ID, requestID)
                }

            val icon = Icon.createWithResource(context, R.mipmap.ic_launcher)
            buttons.mapIndexed { index, button ->
                val pending = if (button.label == AlertButton.Label.OPEN) {
                    openCodingAlertIntent(context, request)
                } else PendingIntent.getBroadcast(
                    context,
                    notificationID * 4 + index + 1,
                    actionIntent(button.decision),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                Notification.Action.Builder(icon, codingAlertButtonLabel(context, button), pending).apply {
                    // A lock-screen tap can authorize code execution on the
                    // server host. Android 12+ must authenticate the user.
                    if (button.authRequired && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setAuthenticationRequired(true)
                    }
                }.build()
            }
        }

        private fun codingAlertButtonLabel(context: Context, button: AlertButton): String =
            when (button.label) {
                AlertButton.Label.ALLOW_ONCE -> NativeStrings.get(context, R.string.native_allow_once)
                AlertButton.Label.REJECT -> NativeStrings.get(context, R.string.native_reject)
                AlertButton.Label.OPEN -> NativeStrings.get(context, R.string.native_open)
            }

        fun dismissCodingAlert(context: Context, key: String): Boolean {
            if (key.isBlank()) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.cancel(codingAlertNotificationID(key))
            // The last alert of a group takes its summary with it.
            refreshGroupSummary(context, manager, GROUP_NEEDS_YOU)
            refreshGroupSummary(context, manager, GROUP_FINISHED)
            return true
        }

        private fun createCodingAlertChannels(context: Context, manager: NotificationManager) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val action = NotificationChannel(
                ACTION_CHANNEL_ID,
                NativeStrings.get(context, R.string.native_coding_requests_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = NativeStrings.get(context, R.string.native_coding_requests_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val status = NotificationChannel(
                STATUS_CHANNEL_ID,
                NativeStrings.get(context, R.string.native_coding_status_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = NativeStrings.get(context, R.string.native_coding_status_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val teamProgress = NotificationChannel(
                TEAM_PROGRESS_CHANNEL_ID,
                NativeStrings.get(context, R.string.native_team_progress_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = NativeStrings.get(context, R.string.native_team_progress_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(false)
            }
            // Quiet hours: the same requests, with no sound and no heads-up.
            val quiet = NotificationChannel(
                ACTION_QUIET_CHANNEL_ID,
                NativeStrings.get(context, R.string.native_coding_requests_quiet_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = NativeStrings.get(context, R.string.native_coding_requests_quiet_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannels(listOf(action, status, teamProgress, quiet))
        }

        /** Whether the alert for [key] is still in the shade. */
        internal fun isCodingAlertShowing(context: Context, key: String): Boolean = try {
            val id = codingAlertNotificationID(key)
            context.getSystemService(NotificationManager::class.java).activeNotifications.any {
                it.id == id && it.packageName == context.packageName
            }
        } catch (_: Throwable) {
            false
        }

        private fun codingAlertNotificationID(key: String): Int =
            CODING_ALERT_ID_BASE + (key.hashCode() and 0x0fffffff)

        private data class CodingAlertContent(
            val channelID: String,
            val title: String,
            val text: String,
            val category: String,
            val priority: Int
        )

        const val EXTRA_CODING_ALERT_KIND =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_KIND"
        const val EXTRA_CODING_ALERT_SESSION_ID =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_SESSION_ID"
        const val EXTRA_MONITOR_TOKEN = "io.github.eslamasabry.opencode_mobile.MONITOR_TOKEN"
        const val EXTRA_CODING_ALERT_PROFILE_ID =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_PROFILE_ID"
        const val EXTRA_CODING_ALERT_KEY =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_KEY"
        const val EXTRA_CODING_ALERT_DECISION =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_DECISION"
        const val EXTRA_CODING_ALERT_REQUEST_ID =
            "io.github.eslamasabry.opencode_mobile.CODING_ALERT_REQUEST_ID"
        const val EXTRA_CODING_ALERT_OPTION_TEXT =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_OPTION_TEXT"
        const val ACTION_CODING_ALERT =
            "io.github.eslamasabry.opencode_mobile.action.CODING_ALERT"
        const val REMOTE_INPUT_REPLY = "oc.codingAlertReply"
        private const val EXTRA_SUMMARY_LINE = "oc.codingAlertSummaryLine"
    }
}
