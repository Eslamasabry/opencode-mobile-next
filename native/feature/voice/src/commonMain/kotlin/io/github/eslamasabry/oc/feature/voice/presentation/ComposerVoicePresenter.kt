package io.github.eslamasabry.oc.feature.voice.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingIds
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingRegistry
import io.github.eslamasabry.oc.engine.settings.teaching.TipController
import io.github.eslamasabry.oc.engine.settings.teaching.TipMoment
import io.github.eslamasabry.oc.engine.settings.teaching.TipVisit
import io.github.eslamasabry.oc.engine.settings.teaching.offer
import io.github.eslamasabry.oc.engine.voice.conversation.VoiceConversationSnapshot
import io.github.eslamasabry.oc.engine.voice.conversation.VoiceReplyDecision
import io.github.eslamasabry.oc.engine.voice.conversation.VoiceReplyWatch
import io.github.eslamasabry.oc.engine.voice.dictation.AutoSendCountdown
import io.github.eslamasabry.oc.engine.voice.dictation.DraftMerge
import io.github.eslamasabry.oc.engine.voice.dictation.VoiceStart
import io.github.eslamasabry.oc.engine.voice.dictation.stopsOnSilence
import io.github.eslamasabry.oc.engine.voice.readaloud.ReadRequest
import io.github.eslamasabry.oc.engine.voice.state.ReadAloudState
import io.github.eslamasabry.oc.engine.voice.state.VoiceEnd
import io.github.eslamasabry.oc.engine.voice.state.VoiceHost
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import io.github.eslamasabry.oc.engine.voice.setup.VoiceSetupHost
import io.github.eslamasabry.oc.engine.voice.setup.VoiceSetupStage
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    public val dictationOnly: Boolean
        get() = false
}

/** What the mic and the pill show. */
public enum class MicPhase {
    Idle,
    Starting,
    Listening,
    Writing,
    Countdown,
}

/** One plain line under the composer after voice ends; never exception text. */
public sealed interface VoiceNote {
    /** The recording ended with no words: the field is as it was. */
    public data object NothingHeard : VoiceNote

    /**
     * "Send when I stop talking" stopped the recording after a pause, but the words were not
     * sendable by themselves.
     */
    public data object StoppedAfterPause : VoiceNote

    /** The microphone is not allowed; [blocked] = only Android settings can allow it now. */
    public data class MicNeeded(val blocked: Boolean) : VoiceNote

    /** The offline speech model is missing: Voice settings downloads it. */
    public data object NeedsModel : VoiceNote

    public data class Failed(val problem: MediaProblem) : VoiceNote

    /** Voice could not start at all on this phone or build. */
    public data object Unavailable : VoiceNote
}

public enum class ConversationReplyNote {
    Review,
    Interrupted,
    NoProse,
    Failed,
    DraftFirst,
    Paused,
    CommandsOnly,
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
    val conversationAvailable: Boolean = false,
    val conversation: Boolean = false,
    val speakReplies: Boolean = false,
    val askingReplyConsent: Boolean = false,
    val pickingReplyVoice: Boolean = false,
    val replyVoices: List<io.github.eslamasabry.oc.engine.voice.readaloud.TtsVoice> = emptyList(),
    val waitingReply: Boolean = false,
    val speakingReply: Boolean = false,
    val canListen: Boolean = false,
    val canReadReply: Boolean = false,
    val replyNote: ConversationReplyNote? = null,
    val tutorialStep: Int? = null,
    val micTip: Boolean = false,
    /**
     * The first-mic "Voice typing" sheet (Flutter `showVoiceAutomaticSetupSheet`): the mic found no speech model, so
     * the model picked for this phone is offered for download right here. Null: no sheet.
     */
    val setup: VoiceSetupStage? = null,
) {
    val active: Boolean
        get() = phase != MicPhase.Idle
}

/**
 * Voice typing in one conversation's composer (Flutter `chat_voice.dart` P10.3, plan 10 §6): tap to
 * talk until Done (or until a pause when "Send when I stop talking" is on), hold to talk and let go
 * to finish, slide away and let go to cancel. The words heard so far are shown live; when the
 * recording ends they are merged into the field around its selection ([DraftMerge], B-MEDIA-024). A
 * recording that stopped on a pause, with the setting on and sendable words, waits
 * [AutoSendCountdown] (3 s) with Send now / Don't send, then goes through the composer's one send
 * path (B-MEDIA-016, B-MEDIA-017). A touch shorter than 400 ms is a slip, not a message.
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

    private enum class EarlyEnd {
        Finish,
        Discard,
    }

    private var parts: VoiceParts? = null
    private var field: VoiceFieldPort? = null
    private var owner = false
    private var sawActive = false
    private var listeningSince: Instant? = null
    private var earlyEnd: EarlyEnd? = null

    /** "Insert and send": the words are written, then go through the composer's one send. */
    private var sendAfterWrite = false
    private var countdown: AutoSendCountdown? = null

    /**
     * Bumped to end the running clock, level mirror and countdown (they stop at their next tick).
     */
    private var ticking = 0
    private var counting = 0
    private var foreground = true
    private var conversationRef: ConversationRef? = null
    private var conversationHandle: VoiceConversationHandle? = null
    private var conversationJob: Job? = null
    private var replyJob: Job? = null
    private var replyWatch: VoiceReplyWatch? = null
    private var conversationEpoch = 0L
    private var conversationSending = false
    private var selectedReplyVoice: String? = null
    private var observed = VoiceConversationSnapshot()
    private var conversationAttached = false
    private var tips: TipController? = null

    /**
     * The field as it was when dictation began: live words are merged around this each time they
     * change, and cancelling puts it back. Null when nothing is being dictated into the field.
     */
    private var previewBase: Triple<String, Int, Int>? = null

    private var setupRun: MicSetupRun? = null
    private var setupWatch: Job? = null

    /** What the box held when the current note appeared; other words dismiss the note (Flutter `_composerNoteTyped`). */
    private var noteTyped: String? = null
    private val visit = TipVisit()
    private val tourVisit = TipVisit()
    private var teachingAttached = false

    public fun attachTeaching(hub: VoiceTeachingHub) {
        if (teachingAttached) return
        teachingAttached = true
        scope.launch {
            val controller = hub.controller() ?: return@launch
            tips = controller
            offerMicTip()
            controller.active.collect { active ->
                mutable.update {
                    it.copy(
                        micTip = active?.id == TeachingIds.VoiceMicConversation,
                        tutorialStep =
                            if (active?.id == TeachingIds.VoiceConversation) it.tutorialStep ?: 0
                            else null,
                    )
                }
            }
        }
    }

    public fun nextTutorial() {
        val step = mutable.value.tutorialStep ?: return
        if (step >= 2) dismissTutorial() else mutable.update { it.copy(tutorialStep = step + 1) }
    }

    public fun dismissTutorial() {
        tips?.markUsed(TeachingIds.VoiceConversation)
        mutable.update { it.copy(tutorialStep = null) }
    }

    private val replyPrefix: String
        get() = "${conversationRef?.sessionId?.raw}/voice-reply/"

    public fun attachConversation(ref: ConversationRef?, conversations: VoiceConversations) {
        if (conversationAttached && ref == conversationRef) return
        conversationAttached = true
        exitConversation()
        conversationJob?.cancel()
        conversationHandle?.release?.invoke()
        conversationRef = ref
        val handle = ref?.let(conversations::open)
        conversationHandle = handle
        mutable.update { it.copy(conversationAvailable = handle != null) }
        offerMicTip()
        if (handle != null)
            conversationJob = scope.launch {
                handle.session.voiceConversation.collect { snapshot ->
                    val previous = observed
                    observed = snapshot
                    if (mutable.value.conversation && previous.generation != snapshot.generation) {
                        exitConversation()
                        mutable.update { it.copy(replyNote = ConversationReplyNote.Review) }
                    }
                    if (
                        mutable.value.conversation &&
                            mutable.value.speakingReply &&
                            (snapshot.requestWaiting || !snapshot.connected)
                    ) {
                        stopReply()
                        mutable.update {
                            it.copy(
                                replyNote =
                                    if (snapshot.requestWaiting) ConversationReplyNote.Interrupted
                                    else ConversationReplyNote.Review
                            )
                        }
                    }
                    checkReply()
                    refreshConversation()
                }
            }
    }

    /** Visible entry beside voice typing. Drafts and attached files must be dealt with first. */
    public fun startConversation() {
        if (!mutable.value.available || conversationHandle == null || mutable.value.active) return
        if (field?.text()?.isNotEmpty() == true || observed.hasAttachments) {
            mutable.update { it.copy(replyNote = ConversationReplyNote.DraftFirst) }
            return
        }
        if (!mayListen()) {
            mutable.update { it.copy(replyNote = ConversationReplyNote.Paused) }
            return
        }
        mutable.update { it.copy(conversation = true, replyNote = null, micTip = false) }
        tips?.offer(
            TeachingRegistry.defaults,
            TeachingIds.VoiceConversation,
            tourVisit,
            TipMoment(questionWaiting = observed.requestWaiting),
        )
        tips?.markUsed(TeachingIds.VoiceMicConversation)
        listenConversation()
    }

    public fun listenConversation() {
        if (
            !mutable.value.conversation ||
                !mayListen() ||
                mutable.value.active ||
                conversationSending
        )
            return
        if (field?.text()?.isNotEmpty() == true || observed.hasAttachments) {
            mutable.update { it.copy(replyNote = ConversationReplyNote.DraftFirst) }
            return
        }
        stopReply()
        mutable.update { it.copy(replyNote = null) }
        start(VoiceMode.Conversation)
    }

    public fun exitConversation(cleanup: CoroutineScope = scope) {
        conversationEpoch++
        selectedReplyVoice = null
        tips?.dismiss(TeachingIds.VoiceConversation)
        replyWatch = null
        conversationSending = false
        replyJob?.cancel()
        replyJob = null
        if (mutable.value.conversation && owner) {
            owner = false
            parts?.let { p -> cleanup.launch { p.dictation.cancel(keepWords = false) } }
            endUi(null)
        }
        mutable.update {
            it.copy(
                conversation = false,
                speakReplies = false,
                askingReplyConsent = false,
                pickingReplyVoice = false,
                replyVoices = emptyList(),
                waitingReply = false,
                speakingReply = false,
                replyNote = null,
            )
        }
    }

    public fun speakReplies(enabled: Boolean) {
        if (!enabled) {
            replyWatch = null
            mutable.update {
                it.copy(
                    speakReplies = false,
                    askingReplyConsent = false,
                    pickingReplyVoice = false,
                    replyVoices = emptyList(),
                    waitingReply = false,
                )
            }
            stopReply()
            return
        }
        if (!mutable.value.conversation) return
        val reader = parts?.reader ?: return
        if (!reader.consentGiven()) mutable.update { it.copy(askingReplyConsent = true) }
        else chooseReplyVoice()
    }

    public fun consentToReplies() {
        val reader = parts?.reader ?: return
        val epoch = conversationEpoch
        mutable.update { it.copy(askingReplyConsent = false) }
        scope.launch {
            val result = reader.giveConsent()
            if (epoch != conversationEpoch || !mutable.value.conversation || !foreground)
                return@launch
            if (result is Outcome.Ok) chooseReplyVoice()
            else mutable.update { it.copy(replyNote = ConversationReplyNote.Failed) }
        }
    }

    private fun chooseReplyVoice() {
        val reader = parts?.reader ?: return
        val epoch = conversationEpoch
        scope.launch {
            when (val voices = reader.voices()) {
                is Outcome.Failed ->
                    if (epoch == conversationEpoch)
                        mutable.update { it.copy(replyNote = ConversationReplyNote.Failed) }
                is Outcome.Ok ->
                    if (epoch == conversationEpoch && mutable.value.conversation) {
                        mutable.update {
                            it.copy(
                                pickingReplyVoice = voices.value.isNotEmpty(),
                                replyVoices = voices.value,
                                replyNote =
                                    if (voices.value.isEmpty()) ConversationReplyNote.Failed
                                    else null,
                            )
                        }
                    }
            }
        }
    }

    public fun pickReplyVoice(id: String) {
        if (mutable.value.replyVoices.none { it.id == id } || !mutable.value.conversation) return
        selectedReplyVoice = id
        mutable.update {
            it.copy(
                speakReplies = true,
                pickingReplyVoice = false,
                replyVoices = emptyList(),
                replyNote = null,
            )
        }
    }

    public fun closeReplyVoices() {
        mutable.update { it.copy(pickingReplyVoice = false, replyVoices = emptyList()) }
    }

    public fun declineReplyConsent() {
        mutable.update { it.copy(askingReplyConsent = false) }
    }

    public fun readReply() {
        if (!mutable.value.conversation || replyWatch != null) return
        val reply =
            observed.messages.lastOrNull {
                !it.user && it.settled && SpeechProse.of(it.text).isNotBlank()
            } ?: return
        val reader = parts?.reader ?: return
        if (!reader.consentGiven()) {
            mutable.update { it.copy(askingReplyConsent = true) }
            return
        }
        readReply(reply.id, automatic = false)
    }

    public fun stopReply() {
        replyJob?.cancel()
        replyJob = null
        mutable.update { it.copy(speakingReply = false) }
    }

    private fun mayListen(): Boolean =
        foreground &&
            observed.connected &&
            observed.ready &&
            !observed.busy &&
            !observed.requestWaiting

    private fun refreshConversation() {
        mutable.update {
            it.copy(
                canListen = mayListen() && !conversationSending && replyWatch == null,
                waitingReply = conversationSending || replyWatch != null || observed.busy,
                canReadReply =
                    !observed.busy &&
                        replyWatch == null &&
                        !it.speakingReply &&
                        observed.messages.any { message ->
                            !message.user &&
                                message.settled &&
                                SpeechProse.of(message.text).isNotBlank()
                        },
            )
        }
    }

    private fun sendConversation(words: String) {
        val handle = conversationHandle ?: return
        if (field?.text()?.isNotEmpty() == true || observed.hasAttachments) {
            keepConversationWords(words)
            mutable.update { it.copy(replyNote = ConversationReplyNote.DraftFirst) }
            return
        }
        if (!mutable.value.conversation || !mayListen()) {
            keepConversationWords(words)
            mutable.update { it.copy(replyNote = ConversationReplyNote.Paused) }
            return
        }
        if (words.trimStart().startsWith("/")) {
            keepConversationWords(words)
            mutable.update { it.copy(replyNote = ConversationReplyNote.CommandsOnly) }
            return
        }
        val epoch = conversationEpoch
        conversationSending = true
        replyWatch = if (mutable.value.speakReplies) VoiceReplyWatch(observed) else null
        refreshConversation()
        scope.launch {
            val result = handle.session.sendVoice(words)
            if (epoch != conversationEpoch) return@launch
            conversationSending = false
            if (result is Outcome.Failed) {
                keepConversationWords(words)
                replyWatch = null
                mutable.update { it.copy(replyNote = ConversationReplyNote.Review) }
            }
            checkReply()
            refreshConversation()
        }
    }

    private fun keepConversationWords(words: String) {
        if (field?.text() != words) merge(words)
    }

    private fun checkReply() {
        val watch = replyWatch ?: return
        val decision = watch.decide(observed)
        if (decision == VoiceReplyDecision.Wait) return
        replyWatch = null
        mutable.update { it.copy(waitingReply = false) }
        when (decision) {
            VoiceReplyDecision.Wait -> Unit
            VoiceReplyDecision.Review ->
                mutable.update { it.copy(replyNote = ConversationReplyNote.Review) }
            VoiceReplyDecision.Interrupted ->
                mutable.update { it.copy(replyNote = ConversationReplyNote.Interrupted) }
            VoiceReplyDecision.NoProse ->
                mutable.update { it.copy(replyNote = ConversationReplyNote.NoProse) }
            is VoiceReplyDecision.Read -> readReply(decision.id, automatic = true, watch = watch)
        }
    }

    private fun readReply(id: String, automatic: Boolean, watch: VoiceReplyWatch? = null) {
        val reader = parts?.reader ?: return
        if (
            !foreground || !mutable.value.conversation || (automatic && !mutable.value.speakReplies)
        )
            return
        val epoch = conversationEpoch
        stopReply()
        mutable.update { it.copy(speakingReply = true, replyNote = null) }
        replyJob = scope.launch {
            val session = conversationHandle?.session ?: return@launch
            val complete = session.readVoiceReply(id, automatic)
            if (epoch != conversationEpoch || !foreground || !mutable.value.conversation)
                return@launch
            if (
                complete is Outcome.Failed ||
                    (automatic && (watch?.decide(observed) as? VoiceReplyDecision.Read)?.id != id)
            ) {
                mutable.update {
                    it.copy(speakingReply = false, replyNote = ConversationReplyNote.Review)
                }
                return@launch
            }
            val prose = SpeechProse.of((complete as Outcome.Ok).value)
            if (prose.isBlank()) {
                mutable.update {
                    it.copy(speakingReply = false, replyNote = ConversationReplyNote.NoProse)
                }
                return@launch
            }
            val result =
                reader.read(
                    ReadRequest(
                        "$replyPrefix$id",
                        prose,
                        voiceId = selectedReplyVoice,
                        automatic = automatic,
                    )
                )
            if (epoch != conversationEpoch) return@launch
            mutable.update {
                it.copy(
                    speakingReply = false,
                    replyNote =
                        if (result is Outcome.Failed) ConversationReplyNote.Failed else null,
                )
            }
            refreshConversation()
        }
    }

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
                offerMicTip()
                scope.launch {
                    p.reader.state.collect { reading ->
                        if (reading is ReadAloudState.Failed && mutable.value.speakingReply)
                            mutable.update {
                                it.copy(
                                    speakingReply = false,
                                    replyNote = ConversationReplyNote.Failed,
                                )
                            }
                    }
                }
                p.dictation.state.collect { onDictation(it) }
            }
        }
    }

    /** The composer's field, attached on every composition (latest wins). */
    public fun attach(port: VoiceFieldPort) {
        field = port
    }

    public fun offerMicTip() {
        val state = mutable.value
        if (
            !state.available ||
                !state.conversationAvailable ||
                state.active ||
                state.conversation ||
                field?.text()?.isNotBlank() != false ||
                observed.requestWaiting ||
                observed.busy ||
                !foreground
        )
            return
        tips?.offer(
            TeachingRegistry.defaults,
            TeachingIds.VoiceMicConversation,
            visit,
            TipMoment(questionWaiting = observed.requestWaiting),
        )
    }

    public fun dismissMicTip() {
        tips?.dismiss(TeachingIds.VoiceMicConversation)
    }

    // ---- the mic ----

    public fun tapMic() {
        when (mutable.value.phase) {
            MicPhase.Idle ->
                if (mutable.value.conversation) listenConversation() else start(VoiceMode.Tap)
            MicPhase.Starting,
            MicPhase.Listening -> done()
            MicPhase.Writing,
            MicPhase.Countdown -> Unit
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

    /**
     * "Insert and send" (Flutter voice composer): stop listening, write the words into the box and
     * send them through the composer's own send. A dictation that hears nothing sends nothing.
     */
    public fun doneAndSend() {
        if (mutable.value.mode == VoiceMode.Conversation) return
        sendAfterWrite = true
        done()
    }

    /** "Cancel": the words said are dropped and the field stays as it was. */
    public fun cancel() {
        when (mutable.value.phase) {
            MicPhase.Starting -> earlyEnd = EarlyEnd.Discard
            MicPhase.Listening,
            MicPhase.Writing -> discard()
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

    /**
     * The field changed while the countdown ran: anything else than the counted words cancels the
     * send.
     */
    public fun fieldChanged(text: String) {
        // Flutter `_composerNoteFollowsEdits`: the note stays beside the box until the next words; typing them
        // (anything other than what the box held when the note appeared) dismisses it.
        val typed = noteTyped
        if (typed != null && text != typed && mutable.value.note != null) {
            noteTyped = null
            mutable.update { it.copy(note = null) }
        }
        val counted = countdown ?: return
        if (text != counted.text) clearCountdown()
    }

    // ---- notes and places ----

    public fun dismissNote() {
        noteTyped = null
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

    /**
     * The chat left the screen or came back (plan 10 §6.5): leaving ends a recording, keeping the
     * words written.
     */
    public fun hostChanged(inForeground: Boolean) {
        foreground = inForeground
        if (!inForeground) dismissMicTip()
        parts
            ?.dictation
            ?.onHostChanged(if (inForeground) VoiceHost.Foreground else VoiceHost.Background)
        parts?.reader?.onHostChanged(inForeground)
        if (!inForeground) {
            clearCountdown()
            exitConversation()
        }
    }

    /**
     * The conversation's composer went away: a recording it started is cancelled, keeping nothing.
     * [cleanup] outlives this presenter's own scope (the ViewModel's scope is already cancelled
     * when it is cleared).
     */
    public fun close(cleanup: CoroutineScope = scope) {
        clearCountdown()
        conversationJob?.cancel()
        conversationHandle?.release?.invoke()
        conversationHandle = null
        exitConversation(cleanup)
        setupRun?.let { run ->
            setupRun = null
            setupWatch?.cancel()
            cleanup.launch { run.close() }
        }
        if (owner) {
            val p = parts
            owner = false
            if (p != null) cleanup.launch { p.dictation.cancel(keepWords = false) }
        }
    }

    // ---- inner workings ----

    private fun start(mode: VoiceMode) {
        val p = parts ?: return
        val request = beginStart(mode) ?: return
        scope.launch {
            when (val started = p.dictation.start(request)) {
                is Outcome.Ok -> Unit
                is Outcome.Failed -> {
                    if (!owner) return@launch
                    owner = false
                    val note = noteFor(started.failure)
                    if (note == VoiceNote.NeedsModel && p.setup != null) openSetup(mode) else endUi(note)
                }
            }
        }
    }

    /** The pill shows Starting and this presenter owns the recording; null when voice cannot start here. */
    private fun beginStart(mode: VoiceMode): VoiceStart? {
        val p = parts ?: return null
        if (field == null || !mutable.value.available) return null
        clearCountdown()
        previewBase =
            field?.takeIf { mode != VoiceMode.Conversation }?.let {
                val selection = it.selection()
                Triple(it.text(), selection?.first ?: -1, selection?.last ?: -1)
            }
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
        return VoiceStart(mode, stopsOnSilence(mode, p.models.state.value.autoSend))
    }

    // ---- first-mic setup (Flutter `showVoiceAutomaticSetupSheet`) ----

    /**
     * The mic found no speech model: instead of only saying "A local model is required", the sheet offers the model
     * picked for this phone's memory and downloads it here. When it is ready, listening starts in this composer (a
     * held mic was let go long ago, so it listens as a tap; a voice conversation keeps its mode).
     */
    private fun openSetup(mode: VoiceMode) {
        val setup = parts?.setup
        endUi(null)
        if (setup == null || setupRun != null) return
        val after = if (mode == VoiceMode.Conversation) VoiceMode.Conversation else VoiceMode.Tap
        val run =
            setup.begin(
                object : VoiceSetupHost {
                    override suspend fun startListening(): Outcome<Unit> {
                        val p = parts ?: return Failure.Unavailable(FeatureId("voice")).failed()
                        val request = beginStart(after) ?: return Failure.Unavailable(FeatureId("voice")).failed()
                        val started = p.dictation.start(request)
                        // The sheet says why; the composer goes back to rest.
                        if (started is Outcome.Failed && owner) {
                            owner = false
                            endUi(null)
                        }
                        return started
                    }

                    override suspend fun cancelListening() {
                        parts?.dictation?.cancel(keepWords = false)
                    }

                    override suspend fun askNotifications() {}

                    override val inForeground: Boolean
                        get() = foreground
                }
            )
        setupRun = run
        mutable.update { it.copy(setup = VoiceSetupStage.Checking) }
        setupWatch?.cancel()
        setupWatch =
            scope.launch {
                run.stage.collect { stage ->
                    if (setupRun !== run) return@collect
                    if (stage == VoiceSetupStage.Listening) {
                        // The recording belongs to the composer's own voice surface from here on.
                        run.handOff()
                        endSetup(run)
                    } else {
                        mutable.update { it.copy(setup = if (stage == VoiceSetupStage.Idle) VoiceSetupStage.Checking else stage) }
                    }
                }
            }
        scope.launch { run.start() }
    }

    private fun endSetup(run: MicSetupRun) {
        if (setupRun !== run) return
        setupRun = null
        setupWatch?.cancel()
        setupWatch = null
        mutable.update { it.copy(setup = null) }
        scope.launch { run.close() }
    }

    /** "Download 153 MB" (or "… on mobile data", which is the consent to use it). */
    public fun setupDownload() {
        val run = setupRun ?: return
        val asked = mutable.value.setup as? VoiceSetupStage.NeedsConsent ?: return
        scope.launch {
            run.consent(asked.attemptId, allowMetered = asked.network != SetupNetwork.Unmetered)
        }
    }

    /** "Start listening" once the model is on the phone. */
    public fun setupListen() {
        val run = setupRun ?: return
        scope.launch { run.listen() }
    }

    /** "Try again": checks again; an installed model never downloads twice. */
    public fun setupRetry() {
        val run = setupRun ?: return
        scope.launch { run.start() }
    }

    /** "Not now", "Cancel download" or closing the sheet: a download this sheet started stops (its parts stay). */
    public fun closeSetup() {
        setupRun?.let(::endSetup)
    }

    /** "Choose another speech model" / "Show the download": Settings › Voice typing makes that choice. */
    public fun setupOpenSettings() {
        closeSetup()
        openVoiceSettings()
    }

    /** "Allow microphone in Android settings" after a permanent denial. */
    public fun setupAllowMic() {
        hub.openAppSettings()
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
                preview(s.words)
                when (earlyEnd.also { earlyEnd = null }) {
                    EarlyEnd.Finish -> {
                        if (mutable.value.mode == VoiceMode.Hold) discard()
                        else finish() // a hold released while starting is a slip
                    }
                    EarlyEnd.Discard -> discard()
                    null -> Unit
                }
            }
            is VoiceState.Writing -> {
                if (!sawActive) return
                mutable.update { it.copy(phase = MicPhase.Writing, words = s.words) }
                preview(s.words)
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
                if (s.wordsKept.isNotBlank()) {
                    merge(s.wordsKept)
                    previewBase = null
                }
                endUi(noteFor(Failure.Media(s.problem)))
            }
            VoiceState.NeedsModel -> {
                owner = false
                val mode = mutable.value.mode ?: VoiceMode.Tap
                if (parts?.setup != null && setupRun == null) openSetup(mode)
                else endUi(if (setupRun != null) null else VoiceNote.NeedsModel)
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
        if (mode == VoiceMode.Conversation) {
            endUi(null)
            sendConversation(words.trim())
            return
        }
        // Dictation never sends by itself: the words wait in the box for the person to send.
        merge(words)
        previewBase = null
        val send = sendAfterWrite
        // A tap stopped by the pause (the setting is on) says why it stopped: the words are in the
        // box, nothing was sent (B-MEDIA-017 "stops by itself after the pause and says why").
        val stoppedByPause =
            !send && endedBy == VoiceEnd.Silence && mode == VoiceMode.Tap && field?.dictationOnly != true
        endUi(if (stoppedByPause) VoiceNote.StoppedAfterPause else null)
        if (send) field?.let { f -> if (f.canSend() && f.text().isNotBlank()) f.send() }
    }

    /** Shows the words heard so far in the box, around where the person's cursor was. */
    private fun preview(words: String) {
        val base = previewBase ?: return
        val f = field ?: return
        val next = if (words.isBlank()) base.first else DraftMerge.merge(base.first, base.second, base.third, words)
        if (next != f.text()) f.replace(next)
    }

    /** Merges [words] into the field around its selection; returns the new field text. */
    private fun merge(words: String): String? {
        val f = field ?: return null
        val base = previewBase
        val current = f.text()
        val selection = f.selection()
        val merged =
            if (base != null) DraftMerge.merge(base.first, base.second, base.third, words)
            else DraftMerge.merge(current, selection?.first ?: -1, selection?.last ?: -1, words)
        if (merged != current) f.replace(merged)
        return merged
    }

    private fun endUi(note: VoiceNote?) {
        // Anything still previewed was not kept (cancelled, nothing heard): the box is as it was.
        previewBase?.let { base -> field?.takeIf { it.text() != base.first }?.replace(base.first) }
        previewBase = null
        stopTicking()
        listeningSince = null
        earlyEnd = null
        sendAfterWrite = false
        mutable.update {
            it.copy(
                phase = MicPhase.Idle,
                mode = null,
                holding = false,
                cancelArmed = false,
                words = "",
                elapsedSeconds = 0,
                note = note,
            )
        }
        noteTyped = if (note != null) field?.text() else null
    }

    private fun startTicking() {
        stopTicking()
        val generation = ticking
        val p = parts ?: return
        scope.launch {
            p.dictation.level.takeWhile { generation == ticking }.collect { levelMirror.value = it }
        }
        scope.launch {
            hub.ticks(TICK)
                .takeWhile { generation == ticking && listeningSince != null }
                .collect {
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
            hub.ticks(COUNTDOWN_STEP)
                .takeWhile { generation == counting && countdown === counted }
                .collect {
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
