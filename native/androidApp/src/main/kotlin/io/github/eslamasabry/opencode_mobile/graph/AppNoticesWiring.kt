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
import io.github.eslamasabry.opencode_mobile.R
import java.text.DateFormat
import java.util.Date
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
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
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
                            val at =
                                DateFormat.getDateTimeInstance(
                                        DateFormat.SHORT,
                                        DateFormat.SHORT,
                                        words.localized().resources.configuration.locales[0],
                                    )
                                    .format(Date(exit.at.toEpochMilliseconds()))
                            val why =
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
        }
    }

    override fun dismiss(id: String) {
        when (id) {
            "app-exit" -> exits.dismiss()
            "thermal" -> thermal.dismiss()
            "background-pause" -> background.launchOnMain { background.notice().dismiss() }
        }
    }

    override fun answerReplyOffer(allow: Boolean) {
        val current = offer ?: return
        background.launchOnMain {
            if (mutableOffer.value?.failed == true) current.dismissFailure()
            else if (allow) current.accept() else current.decline()
        }
    }
}

@dev.zacsweers.metro.ContributesTo(AppScope::class)
interface AppNoticesAccessors {
    val appNotices: AndroidAppNotices
}
