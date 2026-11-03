package io.github.eslamasabry.oc.feature.voice.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.voice.dictation.AutoSendCountdown
import io.github.eslamasabry.oc.engine.voice.dictation.DraftMerge
import io.github.eslamasabry.oc.engine.voice.dictation.VoiceStart
import io.github.eslamasabry.oc.engine.voice.dictation.mayAutoSend
import io.github.eslamasabry.oc.engine.voice.dictation.stopsOnSilence
import io.github.eslamasabry.oc.engine.voice.state.VoiceEnd
import io.github.eslamasabry.oc.engine.voice.state.VoiceHost
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the composer lends the voice presenter (the pure twin of kit-chat's `KitVoiceField`). */
public interface VoiceFieldPort {
    public fun text(): String

    /** start..end UTF-16 offsets ([IntRange.first] = start, [IntRange.last] = end), or null. */
    public fun selection(): IntRange?

    public fun replace(text: String)

    public fun send()

    public fun canSend(): Boolean
}

/** What the mic and the pill show. */
public enum class MicPhase { Idle, Starting, Listening, Writing, Countdown }

/** One plain line under the composer after voice ends; never exception text. */
public sealed interface VoiceNote {
    /** The recording ended with no words: the field is as it was. */
    public data object NothingHeard : VoiceNote

    /** "Send when I stop talking" stopped the recording after a pause, but the words were not sendable by themselves. */
    public data object StoppedAfterPause : VoiceNote

    /** The microphone is not allowed; [blocked] = only Android settings can allow it now. */
    public data class MicNeeded(val blocked: Boolean) : VoiceNote

    /** The offline speech model is missing: Voice settings downloads it. */
    public data object NeedsModel : VoiceNote

    public data class Failed(val problem: MediaProblem) : VoiceNote

    /** Voice could not start at all on this phone or build. */
    public data object Unavailable : VoiceNote
}

public data class ComposerVoiceUi(
    /** False: no mic is drawn (never a dead one). */
    val available: Boolean = false,
    val phase: MicPhase = MicPhase.Idle,
    val mode: VoiceMode? = null,
    /** The finger is on the mic (hold to talk). */
    val holding: Boolean = false,
    /** The held finger slid far enough that letting go cancels. */
    val cancelArmed: Boolean = false,
    /** The live transcript. */
    val words: String = "",
    /** Whole seconds since listening began, for "0:07". */
    val elapsedSeconds: Int = 0,
    /** "Sending in 3…"; null when no countdown runs. */
    val secondsLeft: Int? = null,
    val note: VoiceNote? = null,
) {
    val active: Boolean get() = phase != MicPhase.Idle
}

/**
 * Voice typing in one conversation's composer (Flutter `chat_voice.dart` P10.3, plan 10 §6): tap to talk until Done
 * (or until a pause when "Send when I stop talking" is on), hold to talk and let go to finish, slide away and let go to
 * cancel. The words heard so far are shown live; when the recording ends they are merged into the field around its
 * selection ([DraftMerge], B-MEDIA-024). A recording that stopped on a pause, with the setting on and sendable words,
 * waits [AutoSendCountdown] (3 s) with Send now / Don't send, then goes through the composer's one send path
 * (B-MEDIA-016, B-MEDIA-017). A touch shorter than 400 ms is a slip, not a message.
 *
 * The [Dictation] is app-wide; this presenter reacts only to the recordings it started.
 */
public class ComposerVoicePresenter(
    private val hub: VoiceHub,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val openVoiceSettings: () -> Unit,
) {
    private val mutable = MutableStateFlow(ComposerVoiceUi())
    public val state: StateFlow<ComposerVoiceUi> = mutable.asStateFlow()

    private val levelMirror = MutableStateFlow(0f)

    /** 0..1 while listening; read in the draw phase only. */
    public val level: StateFlow<Float> = levelMirror.asStateFlow()

    private enum class EarlyEnd { Finish, Discard }

    private var parts: VoiceParts? = null
    private var field: VoiceFieldPort? = null
    private var owner = false
    private var sawActive = false
    private var listeningSince: Instant? = null
    private var earlyEnd: EarlyEnd? = null
    private var countdown: AutoSendCountdown? = null

    /** Bumped to end the running clock, level mirror and countdown (they stop at their next tick). */
    private var ticking = 0
    private var counting = 0
    private var foreground = true

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        when (val loaded = hub.parts()) {
            is Outcome.Failed -> mutable.update { it.copy(available = false) }
            is Outcome.Ok -> {
                val p = loaded.value
                parts = p
                mutable.update { it.copy(available = p.micAvailable) }
                p.dictation.state.collect { onDictation(it) }
            }
        }
    }

    /** The composer's field, attached on every composition (latest wins). */
    public fun attach(port: VoiceFieldPort) {
        field = port
    }

    // ---- the mic ----

    public fun tapMic() {
        when (mutable.value.phase) {
            MicPhase.Idle -> start(VoiceMode.Tap)
            MicPhase.Starting, MicPhase.Listening -> done()
            MicPhase.Writing, MicPhase.Countdown -> Unit
        }
    }

    public fun holdStart() {
        if (mutable.value.phase != MicPhase.Idle) return
        start(VoiceMode.Hold)
    }

    public fun holdArmed(armed: Boolean) {
        if (!mutable.value.holding) return
        mutable.update { it.copy(cancelArmed = armed) }
    }

    /** The finger lifted: write it down, or throw it away when it slid away first or was a slip. */
    public fun holdEnd(cancelled: Boolean) {
        if (!mutable.value.holding) return
        mutable.update { it.copy(holding = false, cancelArmed = false) }
        when (mutable.value.phase) {
            MicPhase.Starting -> earlyEnd = if (cancelled) EarlyEnd.Discard else EarlyEnd.Finish
            MicPhase.Listening -> {
                val since = listeningSince
                val slip = since == null || clock.now() - since < SLIP
                if (cancelled || slip) discard() else finish()
            }
            else -> Unit
        }
    }

    /** "Done": stop listening and write the words into the field. */
    public fun done() {
        when (mutable.value.phase) {
            MicPhase.Starting -> earlyEnd = EarlyEnd.Finish
            MicPhase.Listening -> finish()
            else -> Unit
        }
    }

    /** "Cancel": the words said are dropped and the field stays as it was. */
    public fun cancel() {
        when (mutable.value.phase) {
            MicPhase.Starting -> earlyEnd = EarlyEnd.Discard
            MicPhase.Listening, MicPhase.Writing -> discard()
            MicPhase.Countdown -> dontSend()
            MicPhase.Idle -> Unit
        }
    }

    // ---- the countdown ----

    public fun sendNow() {
        decide(sendNow = true)
    }

    /** The countdown stops; the words stay in the field, unsent. */
    public fun dontSend() {
        clearCountdown()
    }

    /** The field changed while the countdown ran: anything else than the counted words cancels the send. */
    public fun fieldChanged(text: String) {
        val counted = countdown ?: return
        if (text != counted.text) clearCountdown()
    }

    // ---- notes and places ----

    public fun dismissNote() {
        mutable.update { it.copy(note = null) }
    }

    public fun openSettings() {
        mutable.update { it.copy(note = null) }
        openVoiceSettings()
    }

    /** "Allow microphone": asks again, or opens Android settings when only they can allow it. */
    public fun allowMic() {
        val note = mutable.value.note as? VoiceNote.MicNeeded ?: return
        mutable.update { it.copy(note = null) }
        if (note.blocked) hub.openAppSettings() else start(VoiceMode.Tap)
    }

    /** The chat left the screen or came back (plan 10 §6.5): leaving ends a recording, keeping the words written. */
    public fun hostChanged(inForeground: Boolean) {
        foreground = inForeground
        parts?.dictation?.onHostChanged(if (inForeground) VoiceHost.Foreground else VoiceHost.Background)
        if (!inForeground) clearCountdown()
    }

    /**
     * The conversation's composer went away: a recording it started is cancelled, keeping nothing. [cleanup] outlives
     * this presenter's own scope (the ViewModel's scope is already cancelled when it is cleared).
     */
    public fun close(cleanup: CoroutineScope = scope) {
        clearCountdown()
        if (owner) {
            val p = parts
            owner = false
            if (p != null) cleanup.launch { p.dictation.cancel(keepWords = false) }
        }
    }

    // ---- inner workings ----

    private fun start(mode: VoiceMode) {
        val p = parts ?: return
        if (field == null || !mutable.value.available) return
        clearCountdown()
        owner = true
        sawActive = false
        earlyEnd = null
        listeningSince = null
        mutable.update {
            it.copy(
                phase = MicPhase.Starting,
                mode = mode,
                holding = mode == VoiceMode.Hold,
                cancelArmed = false,
                words = "",
                elapsedSeconds = 0,
                note = null,
            )
        }
        val request = VoiceStart(mode, stopsOnSilence(mode, p.models.state.value.autoSend))
        scope.launch {
            when (val started = p.dictation.start(request)) {
                is Outcome.Ok -> Unit
                is Outcome.Failed -> {
                    if (!owner) return@launch
                    owner = false
                    endUi(noteFor(started.failure))
                }
            }
        }
    }

    private fun finish() {
        val p = parts ?: return
        scope.launch { p.dictation.finish() }
    }

    private fun discard() {
        val p = parts ?: return
        mutable.update { it.copy(words = "") }
        scope.launch { p.dictation.cancel(keepWords = false) }
    }

    private fun onDictation(s: VoiceState) {
        if (!owner) return
        when (s) {
            VoiceState.Starting -> {
                sawActive = true
                mutable.update { it.copy(phase = MicPhase.Starting) }
            }
            is VoiceState.PreparingModel -> {
                sawActive = true
                mutable.update { it.copy(phase = MicPhase.Starting) }
            }
            is VoiceState.Listening -> {
                sawActive = true
                if (listeningSince == null) {
                    listeningSince = clock.now()
                    startTicking()
                }
                mutable.update { it.copy(phase = MicPhase.Listening, words = s.words) }
                when (earlyEnd.also { earlyEnd = null }) {
                    EarlyEnd.Finish -> {
                        if (mutable.value.mode == VoiceMode.Hold) discard() else finish() // a hold released while starting is a slip
                    }
                    EarlyEnd.Discard -> discard()
                    null -> Unit
                }
            }
            is VoiceState.Writing -> {
                if (!sawActive) return
                mutable.update { it.copy(phase = MicPhase.Writing, words = s.words) }
            }
            VoiceState.Cancelling -> Unit
            is VoiceState.Done -> {
                if (!sawActive) return
                owner = false
                written(s.words, s.endedBy)
            }
            is VoiceState.Failed -> {
                if (!sawActive) return
                owner = false
                if (s.wordsKept.isNotBlank()) merge(s.wordsKept)
                endUi(noteFor(Failure.Media(s.problem)))
            }
            VoiceState.NeedsModel -> {
                owner = false
                endUi(VoiceNote.NeedsModel)
            }
            VoiceState.Idle -> {
                if (!sawActive) return
                owner = false
                endUi(null)
            }
        }
    }

    private fun written(words: String, endedBy: VoiceEnd) {
        val mode = mutable.value.mode
        if (words.isBlank()) {
            endUi(VoiceNote.NothingHeard)
            return
        }
        val merged = merge(words) ?: return endUi(null)
        if (endedBy == VoiceEnd.Silence && mode == VoiceMode.Tap) {
            // B-MEDIA-016: the spoken words must be sendable by themselves (Flutter `mayAutoSend(voice.draft)`); a
            // rejected transcript never sends a draft that was already in the box.
            val counted =
                if (mayAutoSend(words)) AutoSendCountdown.startIfAllowed(merged, stoppedBySilence = true, now = clock.now()) else null
            if (counted != null) {
                startCountdown(counted)
                return
            }
            endUi(VoiceNote.StoppedAfterPause)
            return
        }
        endUi(null)
    }

    /** Merges [words] into the field around its selection; returns the new field text. */
    private fun merge(words: String): String? {
        val f = field ?: return null
        val current = f.text()
        val selection = f.selection()
        val merged = DraftMerge.merge(current, selection?.first ?: -1, selection?.last ?: -1, words)
        if (merged != current) f.replace(merged)
        return merged
    }

    private fun endUi(note: VoiceNote?) {
        stopTicking()
        listeningSince = null
        earlyEnd = null
        mutable.update {
            it.copy(phase = MicPhase.Idle, mode = null, holding = false, cancelArmed = false, words = "", elapsedSeconds = 0, note = note)
        }
    }

    private fun startTicking() {
        stopTicking()
        val generation = ticking
        val p = parts ?: return
        scope.launch {
            p.dictation.level.takeWhile { generation == ticking }.collect { levelMirror.value = it }
        }
        scope.launch {
            hub.ticks(TICK).takeWhile { generation == ticking && listeningSince != null }.collect {
                val since = listeningSince ?: return@collect
                val seconds = (clock.now() - since).inWholeSeconds.toInt()
                mutable.update { it.copy(elapsedSeconds = seconds) }
            }
        }
    }

    private fun stopTicking() {
        ticking++
        levelMirror.value = 0f
    }

    private fun startCountdown(counted: AutoSendCountdown) {
        stopTicking()
        listeningSince = null
        countdown = counted
        mutable.update {
            it.copy(
                phase = MicPhase.Countdown,
                holding = false,
                cancelArmed = false,
                words = "",
                secondsLeft = counted.secondsLeft(clock.now()),
                note = null,
            )
        }
        val generation = ++counting
        scope.launch {
            hub.ticks(COUNTDOWN_STEP).takeWhile { generation == counting && countdown === counted }.collect {
                val left = counted.secondsLeft(clock.now())
                mutable.update { it.copy(secondsLeft = left) }
                if (left == 0) decide(sendNow = false)
            }
        }
    }

    private fun decide(sendNow: Boolean) {
        val counted = countdown ?: return
        val f = field
        if (f == null) {
            clearCountdown()
            return
        }
        when (
            val decision =
                counted.decide(
                    now = clock.now(),
                    fieldText = f.text(),
                    foreground = foreground,
                    sendInFlight = !f.canSend(),
                    sendNow = sendNow,
                )
        ) {
            AutoSendCountdown.Decision.Wait -> Unit
            AutoSendCountdown.Decision.Cancel -> clearCountdown()
            is AutoSendCountdown.Decision.Send -> {
                clearCountdown()
                if (f.text() == decision.text) f.send()
            }
        }
    }

    private fun clearCountdown() {
        countdown = null
        counting++
        if (mutable.value.phase == MicPhase.Countdown) {
            mutable.update { it.copy(phase = MicPhase.Idle, mode = null, secondsLeft = null) }
        } else {
            mutable.update { it.copy(secondsLeft = null) }
        }
    }

    public companion object {
        /** A held touch shorter than this is a slip, not a message (Flutter `_finishHold`). */
        public val SLIP: kotlin.time.Duration = 400.milliseconds
        private val TICK = 1.seconds
        private val COUNTDOWN_STEP = 200.milliseconds

        public fun noteFor(failure: Failure): VoiceNote =
            when (failure) {
                is Failure.Media ->
                    when (failure.problem) {
                        MediaProblem.MicDenied -> VoiceNote.MicNeeded(blocked = false)
                        MediaProblem.MicDeniedForever -> VoiceNote.MicNeeded(blocked = true)
                        MediaProblem.SpeechModelMissing -> VoiceNote.NeedsModel
                        else -> VoiceNote.Failed(failure.problem)
                    }
                is Failure.Unavailable -> VoiceNote.Unavailable
                else -> VoiceNote.Failed(MediaProblem.RecordingInterrupted)
            }
    }
}
