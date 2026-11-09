package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.background.FirstReplyNotifyOffer
import io.github.eslamasabry.oc.android.background.LiveModeError
import io.github.eslamasabry.oc.android.background.NotifyOfferFailure
import io.github.eslamasabry.oc.android.notifications.NotifyWords
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.domain.platform.AppNotice
import io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction
import io.github.eslamasabry.oc.core.domain.platform.AppNotices
import io.github.eslamasabry.oc.core.domain.platform.ReplyNotificationOffer
import io.github.eslamasabry.oc.engine.runtime.recovery.PreviousExitKind
import io.github.eslamasabry.oc.feature.background.presentation.PauseReason
import io.github.eslamasabry.oc.feature.chats.presentation.UnsupportedAppNotices
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.opencode_mobile.R
import android.icu.text.RelativeDateTimeFormatter
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * App conditions use the existing single status slot on Conversations and Chat; a server return
 * outranks a pause.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [UnsupportedAppNotices::class])
@Inject
class AndroidAppNotices(
    application: Application,
    private val background: BackgroundHub,
    private val exits: AppExitHub,
    private val registry: io.github.eslamasabry.oc.engine.connection.ConnectionRegistry,
    private val navigator: AppNavigator,
    private val preferences: AppPreferences,
    private val thermal: ThermalRuntime,
    private val clock: Clock,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    private val continuity: AppTelemetryContinuity,
) : AppNotices {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val words = NotifyWords(application) { preferences.localeTag.value }
    private val mutableStatus = MutableStateFlow<AppNotice?>(null)
    override val status: StateFlow<AppNotice?> = mutableStatus.asStateFlow()
    private val mutableOffer = MutableStateFlow<ReplyNotificationOffer?>(null)
    override val replyOffer: StateFlow<ReplyNotificationOffer?> = mutableOffer.asStateFlow()
    private var offer: FirstReplyNotifyOffer? = null
    private var offerPending = false
    private var offerJob: Job? = null
    private var started = false

    private var termuxRetired: TermuxRetiredNotice? = null
    private val termuxRetiredShown = MutableStateFlow(false)

    /**
     * Owner decision 2026-11-07: shows the once-only "Termux is no longer supported" notice in the slot, below every
     * live condition. Only the first call counts.
     */
    fun follow(notice: TermuxRetiredNotice) {
        if (termuxRetired != null) return
        termuxRetired = notice
        scope.launch { notice.shown.collect { termuxRetiredShown.value = it } }
    }

    /** P-015: "12 minutes ago" / "2 hours ago" today, "on 6 Nov 2026" for an earlier day, in the app's language. */
    private fun exitTimeWords(time: ExitNoticeTime): String {
        val locale = words.localized().resources.configuration.locales[0]
        val relative = RelativeDateTimeFormatter.getInstance(locale)
        return when (time) {
            is ExitNoticeTime.MinutesAgo ->
                relative.format(
                    time.minutes.toDouble(),
                    RelativeDateTimeFormatter.Direction.LAST,
                    RelativeDateTimeFormatter.RelativeUnit.MINUTES,
                )
            is ExitNoticeTime.HoursAgo ->
                relative.format(
                    time.hours.toDouble(),
                    RelativeDateTimeFormatter.Direction.LAST,
                    RelativeDateTimeFormatter.RelativeUnit.HOURS,
                )
            is ExitNoticeTime.OnDay ->
                words.text(
                    R.string.andr_appExitOnDate,
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(time.date),
                )
        }
    }

    fun start() {
        if (started) return
        started = true
        background.start()
        exits.start()
        thermal.start()
        scope.launch {
            combine(
                    exits.notice,
                    exits.serverBack,
                    background.live,
                    preferences.localeTag,
                    thermal.notice,
                ) { exit, back, live, _, heat ->
                    val dismiss = words.text(R.string.andr_notifyDismiss)
                    when {
                        exit != null -> {
                            val at = exitTimeWords(exitNoticeTime(exit.at, clock.now(), ZoneId.systemDefault()))
                            val why =
                                if (exit.kind == PreviousExitKind.BackgroundStop)
                                    // Issue #138: a maker's battery manager records the same stop as the person's own,
                                    // so the line names both and blames neither; "Keep it running" leads to the steps.
                                    words.text(R.string.andr_appExitStoppedBackground)
                                else
                                    words.text(
                                        when (exit.kind) {
                                            PreviousExitKind.LowMemory,
                                            PreviousExitKind.MemoryLimit ->
                                                R.string.andr_appExitLowMemory
                                            PreviousExitKind.Crash -> R.string.andr_appExitCrashed
                                            PreviousExitKind.Killed -> R.string.andr_appExitKilled
                                            else -> R.string.andr_appExitForceStopped
                                        },
                                        at,
                                    )
                            val sentence =
                                when {
                                    back ->
                                        if (exit.teamStopped) R.string.andr_appExitServerBackTeam
                                        else R.string.andr_appExitServerBack
                                    exit.recoveryAllowed ->
                                        if (exit.teamStopped)
                                            R.string.andr_appExitServerAndTeamStopped
                                        else R.string.andr_appExitServerStopped
                                    else ->
                                        if (exit.teamStopped)
                                            R.string.andr_appExitServerAndTeamStoppedManual
                                        else R.string.andr_appExitServerStoppedManual
                                }
                            AppNotice(
                                "app-exit",
                                words.text(sentence, why),
                                action =
                                    if (exit.offersKeepAlive) AppNoticeAction.KeepRunning else null,
                                actionLabel =
                                    if (exit.offersKeepAlive)
                                        words.text(R.string.andr_appExitKeepRunning)
                                    else null,
                                dismissLabel = dismiss,
                                stoppedApp = true,
                            )
                        }
                        live.pause != null -> {
                            val pause = checkNotNull(live.pause)
                            val at =
                                pause.day?.let {
                                    words.text(R.string.andr_appExitOnDay, it, pause.clock)
                                } ?: words.text(R.string.andr_appExitAtTime, pause.clock)
                            val message =
                                when {
                                    live.resuming ->
                                        words.text(R.string.andr_backgroundPauseResuming)
                                    pause.resumeFailed ->
                                        words.text(R.string.andr_backgroundPauseResumeFailed)
                                    else ->
                                        when (pause.reason) {
                                            PauseReason.TimeLimit ->
                                                words.text(
                                                    R.string.andr_backgroundPauseTimeLimit,
                                                    at,
                                                )
                                            PauseReason.BatteryRestricted ->
                                                words.text(R.string.andr_backgroundPauseRestricted)
                                            PauseReason.UserStopped ->
                                                words.text(
                                                    R.string.andr_backgroundPauseUserStopped,
                                                    at,
                                                )
                                            PauseReason.AppUpdated ->
                                                words.text(
                                                    R.string.andr_backgroundPauseAppUpdated,
                                                    at,
                                                )
                                            PauseReason.DeviceRestarted ->
                                                words.text(
                                                    R.string.andr_backgroundPauseDeviceRestarted
                                                )
                                            PauseReason.Interrupted ->
                                                words.text(R.string.andr_backgroundPauseInterrupted)
                                        }
                                }
                            val keepRunning = !pause.canResume || pause.resumeFailed
                            AppNotice(
                                "background-pause",
                                message,
                                action =
                                    if (live.resuming) null
                                    else if (keepRunning) AppNoticeAction.KeepRunning
                                    else AppNoticeAction.ResumeBackground,
                                actionLabel =
                                    if (keepRunning)
                                        words.text(R.string.andr_backgroundPauseOpenKeepRunning)
                                    else words.text(R.string.andr_backgroundPauseResumeShort),
                                dismissLabel = dismiss,
                            )
                        }
                        heat != null ->
                            AppNotice(
                                "thermal",
                                words.text(
                                    when (heat.kind) {
                                        io.github.eslamasabry.oc.engine.runtime.heat
                                            .ThermalNoticeKind
                                            .Paused -> R.string.andr_thermalPaused
                                        io.github.eslamasabry.oc.engine.runtime.heat
                                            .ThermalNoticeKind
                                            .Stopped -> R.string.andr_thermalStopped
                                        io.github.eslamasabry.oc.engine.runtime.heat
                                            .ThermalNoticeKind
                                            .Resumed -> R.string.andr_thermalResumed
                                    }
                                ),
                                dismissLabel = dismiss,
                            )
                        else -> null
                    }
                }
                // s23-continuity: the one-time crash offer waits behind the live conditions (the exit notice first).
                .let { conditions ->
                    combine(conditions, continuity.runtime.offer.pending) { condition, crash ->
                        condition ?: crash?.let { crashOfferNotice() }
                    }
                }
                // The Termux notice ranks last; it waits behind any live condition.
                .let { conditions ->
                    combine(conditions, termuxRetiredShown) { condition, termux ->
                        condition ?: termuxRetiredNotice().takeIf { termux }
                    }
                }
                .collect { mutableStatus.value = it }
        }
        scope.launch {
            registry.live.collectLatest { live ->
                coroutineScope {
                    live.forEach { (profile, graph) ->
                        launch {
                            val completed = mutableSetOf<String>()
                            val ended = mutableSetOf<String>()
                            graph.session.server.events.all.collect { envelope ->
                                if (
                                    registry.live.value[profile] !== graph ||
                                        offer != null ||
                                        offerPending
                                )
                                    return@collect
                                val session =
                                    when (val event = envelope.event) {
                                        is GatewayEvent.RunStarted -> {
                                            completed.remove(event.sessionId.raw)
                                            ended.remove(event.sessionId.raw)
                                            return@collect
                                        }
                                        is GatewayEvent.MessageUpserted -> {
                                            val message = event.message
                                            if (
                                                message.role !=
                                                    io.github.eslamasabry.oc.core.model.transcript
                                                        .Role
                                                        .Assistant ||
                                                    message.time?.completedMs == null ||
                                                    message.errorKind != null ||
                                                    message.errorText != null ||
                                                    message.finish in
                                                        setOf("tool-calls", "tool_calls", "error")
                                            )
                                                return@collect
                                            completed += message.sessionId
                                            message.sessionId
                                        }
                                        is GatewayEvent.RunEnded -> {
                                            if (
                                                event.outcome != RunOutcome.Succeeded &&
                                                    event.outcome != RunOutcome.Idle
                                            ) {
                                                completed.remove(event.sessionId.raw)
                                                ended.remove(event.sessionId.raw)
                                                return@collect
                                            }
                                            ended += event.sessionId.raw
                                            event.sessionId.raw
                                        }
                                        else -> return@collect
                                    }
                                if (session in completed && session in ended) {
                                    // Two servers finishing together must not both claim an offer.
                                    offerPending = true
                                    try {
                                        showOffer(background.notifyOffer(profile.raw))
                                    } finally {
                                        offerPending = false
                                    }
                                }
                                // Only transient completion metadata is retained, bounded for a
                                // long-lived connection.
                                if (completed.size > 256) completed.clear()
                                if (ended.size > 256) ended.clear()
                            }
                        }
                    }
                    awaitCancellation()
                }
            }
        }
    }

    private fun termuxRetiredNotice(): AppNotice =
        AppNotice(
            TermuxRetiredNotice.ID,
            words.text(R.string.andr_termuxRetiredNotice),
            action = AppNoticeAction.SetUpBuiltInLinux,
            actionLabel = words.text(R.string.andr_termuxRetiredSetUpBuiltin),
            secondAction = AppNoticeAction.ConnectComputer,
            secondActionLabel = words.text(R.string.andr_termuxRetiredConnectComputer),
            dismissLabel = words.text(R.string.andr_notifyDismiss),
        )

    private fun showOffer(next: FirstReplyNotifyOffer) {
        offer = next
        offerJob?.cancel()
        offerJob = scope.launch {
            combine(next.state, preferences.localeTag, background.live) { state, _, _ ->
                    val shown = state.working || next.showFor(replyCompleted = true)
                    when {
                        state.failure != null ->
                            ReplyNotificationOffer(
                                words.text(
                                    when {
                                        state.failure == NotifyOfferFailure.SaveFailed ->
                                            R.string.andr_notifySaveFailed
                                        state.failureError == LiveModeError.NotificationsRequired ->
                                            R.string.andr_notifyPermissionRefused
                                        else -> R.string.andr_notifyEnableFailed
                                    }
                                ),
                                words.text(R.string.andr_firstRunNotifyAccept),
                                words.text(R.string.andr_notifyDismiss),
                                failed = true,
                            )
                        shown ->
                            ReplyNotificationOffer(
                                words.text(R.string.andr_firstRunNotifyTitle) +
                                    "\n" +
                                    words.text(R.string.andr_firstRunNotifyBody),
                                words.text(R.string.andr_firstRunNotifyAccept),
                                words.text(R.string.andr_firstRunNotifyDecline),
                                busy = state.working,
                            )
                        else -> null
                    }
                }
                .collect { mutableOffer.value = it }
        }
    }

    override fun act(action: AppNoticeAction) {
        when (action) {
            AppNoticeAction.KeepRunning -> navigator.open(PhoneRoute.KeepRunning)
            AppNoticeAction.ResumeBackground ->
                background.launchOnMain { background.notice().resume() }
            AppNoticeAction.SetUpBuiltInLinux -> {
                closeTermuxRetired()
                navigator.open(PhoneRoute.ThisPhone)
            }
            AppNoticeAction.ConnectComputer -> {
                closeTermuxRetired()
                navigator.open(ServersRoute.Edit())
            }
            // Either answer ends the offer for this crash; Report a problem sends nothing until the person does.
            AppNoticeAction.ReviewCrash -> {
                continuity.runtime.takeCrash()
                navigator.open(io.github.eslamasabry.oc.ui.routes.SettingsRoute.ReportProblem)
            }
        }
    }

    override fun dismiss(id: String) {
        when (id) {
            "app-exit" -> exits.dismiss()
            "thermal" -> thermal.dismiss()
            "background-pause" -> background.launchOnMain { background.notice().dismiss() }
            TermuxRetiredNotice.ID -> closeTermuxRetired()
            CRASH_OFFER_ID -> continuity.runtime.takeCrash()
        }
    }

    private fun crashOfferNotice(): AppNotice =
        AppNotice(
            CRASH_OFFER_ID,
            words.text(R.string.andr_crashOfferMessage),
            action = AppNoticeAction.ReviewCrash,
            actionLabel = words.text(R.string.andr_crashOfferReview),
            dismissLabel = words.text(R.string.andr_crashOfferNotNow),
        )

    private fun closeTermuxRetired() {
        termuxRetired?.let { scope.launch { it.close() } }
    }

    override fun answerReplyOffer(allow: Boolean) {
        val current = offer ?: return
        background.launchOnMain {
            if (mutableOffer.value?.failed == true) current.dismissFailure()
            else if (allow) current.accept() else current.decline()
        }
    }
}

/** The status line id of the crash offer. */
internal const val CRASH_OFFER_ID: String = "crash-offer"

@dev.zacsweers.metro.ContributesTo(AppScope::class)
interface AppNoticesAccessors {
    val appNotices: AndroidAppNotices
}
