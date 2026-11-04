package io.github.eslamasabry.oc.android.background

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Where the app-wide "background connection paused" notice is (Flutter `BackgroundPauseNoticePhase`, FD3). */
public enum class PauseNoticePhase {
    /** Android stopped the background connection; Resume is offered. */
    Paused,

    /** The person tapped Resume; Android has not confirmed the start yet. */
    Resuming,

    /** The last Resume was not confirmed; the pause is still in effect. */
    Failed,
}

public data class PauseNoticeState(
    val reason: PauseReason,
    /** UTC epoch ms: the stop time (time limit, closed or updated app) or when the app noticed. */
    val atMs: Long,
    val canResume: Boolean,
    val phase: PauseNoticePhase,
    /** When the current Resume tap began, while [phase] is resuming. */
    val resumingSinceMs: Long? = null,
)

/** The pause as the notice sees it (live mode implements it through [LiveModeController.pauseGateway]). */
public interface PauseGateway {
    public val pause: StateFlow<PauseState>

    public suspend fun resume(): ResumeResult

    public suspend fun refresh(): PauseState
}

/** `oc.backgroundPauseDismissed`: app-global (one receipt per phone), holds the id of the last pause hidden. */
public interface DismissedPauseStore {
    public fun read(): String?

    public suspend fun write(id: String): Boolean

    public companion object {
        public const val KEY: String = "oc.backgroundPauseDismissed"
    }
}

/**
 * The one owner of the pause notice (port of `BackgroundPauseNotice`): which pause it is about, whether the person hid
 * it, and the Resume tap in flight. It never invents or clears a pause; only a confirmed start makes it go by itself.
 * Confined with its [scope]. The shell calls [refresh] when the app comes back to the screen.
 */
public class PauseNotice(
    private val gateway: PauseGateway,
    private val dismissedStore: DismissedPauseStore,
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private var dismissed: String? = readDismissed()
    private var failedFor: String? = null
    private var inflight: Deferred<Boolean>? = null
    private var resumingSince: Long? = null
    private val revision = MutableStateFlow(0)

    /** Null when there is nothing to show: no pause, an unsupported platform, or this exact pause was hidden. */
    public val notice: PauseNoticeState? get() = compute(gateway.pause.value)

    public val notices: Flow<PauseNoticeState?> = combine(gateway.pause, revision) { pause, _ -> compute(pause) }

    private fun compute(pause: PauseState): PauseNoticeState? {
        val id = idOf(pause) ?: return null
        if (id == dismissed) return null
        val resuming = inflight != null
        return PauseNoticeState(
            reason = pause.reason,
            atMs = checkNotNull(pause.atMs),
            canResume = pause.canResume,
            phase =
                when {
                    resuming -> PauseNoticePhase.Resuming
                    id == failedFor -> PauseNoticePhase.Failed
                    else -> PauseNoticePhase.Paused
                },
            resumingSinceMs = if (resuming) resumingSince else null,
        )
    }

    /** Only from a visible Resume tap. A second tap joins the first. True once Android confirmed the start. */
    public suspend fun resume(): Boolean {
        inflight?.let { return it.await() }
        if (notice == null) return false
        // Dispatched: the platform call runs after the in-flight state is published.
        val run = scope.async { resumeBody() }
        inflight = run
        resumingSince = clock.now().toEpochMilliseconds()
        changed()
        return run.await()
    }

    private suspend fun resumeBody(): Boolean {
        val id = idOf(gateway.pause.value)
        val result =
            when (val outcome = attempt({ Failure.Device() }) { gateway.resume() }) {
                is Outcome.Ok -> outcome.value
                is Outcome.Failed -> ResumeResult(gateway.pause.value, ResumeError.ResumeFailed)
            }
        val confirmed = result.error == null && result.state.active && !result.state.paused
        // Busy means another start (a permission prompt) is already deciding: not this tap's failure.
        if (!confirmed && result.error != ResumeError.Busy) failedFor = id else if (confirmed) failedFor = null
        inflight = null
        resumingSince = null
        changed()
        return confirmed
    }

    /** Hides this pause for good; a later, different pause shows again. */
    public fun dismiss() {
        val id = idOf(gateway.pause.value)
        if (id == null || inflight != null) return
        dismissed = id
        failedFor = null
        scope.launch { dismissedStore.write(id) }
        changed()
    }

    /** Re-reads the service's receipt (on return to the app). */
    public suspend fun refresh() {
        if (inflight != null) return
        attempt({ Failure.Device() }) { gateway.refresh() }
    }

    private fun readDismissed(): String? =
        try {
            dismissedStore.read()
        } catch (_: RuntimeException) {
            null
        }

    private fun changed() {
        revision.value += 1
    }

    public companion object {
        /** The same pause keeps one id across restarts: its reason and time. */
        public fun idOf(pause: PauseState): String? {
            val at = pause.atMs
            if (!pause.supported || !pause.paused || at == null) return null
            return "${pause.reason.wire}@$at"
        }
    }
}

/** The notice's one sentence (`background_pause_*` strings). [saysTime]: the line names the moment it stopped. */
public enum class PauseMessage(public val saysTime: Boolean) {
    TimeLimit(true),
    Restricted(false),
    UserStopped(true),
    AppUpdated(true),
    DeviceRestarted(false),
    Interrupted(false),
    Resuming(false),
    ResumeFailed(false),
}

/**
 * The line's actions. [Resume] is the compact inline "Resume" a screen reader hears as "Resume background connection";
 * [ResumeAgain] stands alone under More, so it says the whole target.
 */
public enum class PauseAction { Resume, ResumeAgain, KeepRunning }

public enum class PauseTone { Neutral, Progress, Failure }

/** What the status line shows for one notice. No copy: the kit line maps these to the strings. */
public data class PauseNoticeLine(
    /** Kit status id: `background-paused`, `:resuming`, `:failed`. */
    val id: String,
    val message: PauseMessage,
    val atMs: Long?,
    val tone: PauseTone,
    /** A battery icon for a time limit or a restriction; a pause icon otherwise. */
    val batteryIcon: Boolean,
    val action: PauseAction?,
    val more: List<PauseAction>,
    val dismissible: Boolean,
    val sinceMs: Long?,
)

public object PauseNoticeLines {
    public const val ID: String = "background-paused"

    /** One compact line, nothing under it (port of `backgroundPauseKitStatus`). */
    public fun lineFor(notice: PauseNoticeState): PauseNoticeLine {
        val battery = notice.reason == PauseReason.TimeLimit || notice.reason == PauseReason.BatteryRestricted
        return when (notice.phase) {
            PauseNoticePhase.Resuming ->
                PauseNoticeLine(
                    "$ID:resuming", PauseMessage.Resuming, null, PauseTone.Progress, battery,
                    action = null, more = emptyList(), dismissible = false, sinceMs = notice.resumingSinceMs,
                )
            PauseNoticePhase.Failed ->
                PauseNoticeLine(
                    "$ID:failed", PauseMessage.ResumeFailed, null, PauseTone.Failure, battery,
                    action = PauseAction.KeepRunning,
                    more = if (notice.canResume) listOf(PauseAction.ResumeAgain) else emptyList(),
                    dismissible = true, sinceMs = null,
                )
            PauseNoticePhase.Paused -> {
                val message = messageFor(notice.reason)
                PauseNoticeLine(
                    ID, message, if (message.saysTime) notice.atMs else null,
                    // Neutral, as the app-exit line: amber is needs-you only.
                    PauseTone.Neutral, battery,
                    action = if (notice.canResume) PauseAction.Resume else PauseAction.KeepRunning,
                    more = if (notice.canResume) listOf(PauseAction.KeepRunning) else emptyList(),
                    dismissible = true, sinceMs = null,
                )
            }
        }
    }

    /** The time is said only where it is the moment it stopped; otherwise no time is claimed. */
    public fun messageFor(reason: PauseReason): PauseMessage =
        when (reason) {
            PauseReason.TimeLimit -> PauseMessage.TimeLimit
            PauseReason.BatteryRestricted -> PauseMessage.Restricted
            PauseReason.UserStopped -> PauseMessage.UserStopped
            PauseReason.DeviceRestarted -> PauseMessage.DeviceRestarted
            PauseReason.AppUpdated -> PauseMessage.AppUpdated
            PauseReason.Interrupted, PauseReason.None -> PauseMessage.Interrupted
        }

    /**
     * The pause shares the app-stopped slot with the app-exit notice, so one shows at a time: once the phone server is
     * back the pause goes first, before that the app exit leads (Flutter `appStoppedLines`).
     */
    public fun <T> appStoppedLines(exit: T?, paused: T?, serverBack: Boolean): List<T?> =
        if (serverBack) listOf(paused, exit) else listOf(exit, paused)
}

/** Live mode as the pause notice's gateway. */
public fun LiveModeController.pauseGateway(scope: CoroutineScope): PauseGateway =
    object : PauseGateway {
        private val pauses = MutableStateFlow(state.value.pause)

        init {
            scope.launch { state.collect { pauses.value = it.pause } }
        }

        override val pause: StateFlow<PauseState> = pauses

        override suspend fun resume(): ResumeResult = this@pauseGateway.resume().also { pauses.value = it.state }

        override suspend fun refresh(): PauseState = refreshPause().also { pauses.value = it }
    }
