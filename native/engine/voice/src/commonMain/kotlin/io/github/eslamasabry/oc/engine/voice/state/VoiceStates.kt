package io.github.eslamasabry.oc.engine.voice.state

import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlin.jvm.JvmInline
import kotlin.time.Instant

// Every type the UI renders (plan 10 §4.4). The package matches compose-stability.conf's `…oc.engine.**.state.**`.

/** "base" | "small" | "tiny" | "kokoro-en". */
@JvmInline
public value class PackId(public val raw: String)

public sealed interface VoiceState {
    public data object Idle : VoiceState

    /** Flutter `modelRequired`: the composer opens the first-mic setup. */
    public data object NeedsModel : VoiceState

    public data class PreparingModel(val pack: PackId, val step: PrepStep) : VoiceState

    /** Microphone opening and model loading; frames already buffer (plan 10 §6.3 "Load overlap"). */
    public data object Starting : VoiceState

    public data class Listening(val since: Instant, val mode: VoiceMode, val chunksDone: Int, val words: String) :
        VoiceState

    /** Flutter `transcribing` after stop. */
    public data class Writing(val words: String) : VoiceState

    /** Flutter `finishingCancellation`: a decode already inside the recognizer finishes, then is discarded. */
    public data object Cancelling : VoiceState

    /** `Done("")` is valid: the composer leaves the box as it was and says "Nothing was heard." */
    public data class Done(val words: String, val endedBy: VoiceEnd) : VoiceState

    public data class Failed(val problem: MediaProblem, val wordsKept: String) : VoiceState
}

public sealed interface PrepStep {
    public data class Downloading(val received: Long, val total: Long) : PrepStep

    public data object Verifying : PrepStep

    public data object Loading : PrepStep
}

public enum class VoiceMode { Tap, Hold, Conversation }

public enum class VoiceEnd { Person, Silence, LeftApp, PageCovered, ScopeChanged, ChunkFailed }

/** What the chat screen reports (plan 10 §6.5); the ViewModel forwards an enum, never a platform type. */
public enum class VoiceHost { Foreground, Inactive, Background, PageCovered, ScopeChanged }

/** What KitComposerVoice draws (MEDIA-VOICE-18). */
public enum class VoicePhase {
    Starting, Listening, Writing, WaitingReply, SpeakingReply, ReplyReady, Paused, MicDenied, Failed, SendingSoon,
}

/** One pack's place on this phone (plan 10 §7.8 rows). */
public sealed interface PackStatus {
    public data object Installed : PackStatus

    /** Kept `.part` files (M3): "Downloaded 120 MB of 375 MB". */
    public data class Partial(val bytes: Long, val total: Long) : PackStatus

    public data object Missing : PackStatus

    /** Dimmed with the reason in the row; null problem = the platform has no capture path. */
    public data class Unsupported(val problem: MediaProblem?) : PackStatus
}

public sealed interface PackJobPhase {
    public data class Downloading(val received: Long, val total: Long) : PackJobPhase

    public data object Verifying : PackJobPhase
}

public data class PackJob(val pack: PackId, val phase: PackJobPhase)

public data class ModelPacksState(
    val packs: ImmutableMap<PackId, PackStatus> = persistentMapOf(),
    val selected: PackId = PackId("base"),
    val language: VoiceLanguage = VoiceLanguage.Auto,
    val autoSend: Boolean = false,
    /** `voice.read_aloud_natural`: true only once the natural voice is installed. */
    val naturalVoice: Boolean = false,
    val job: PackJob? = null,
    val lastProblem: MediaProblem? = null,
)

public enum class ReadEngine { System, Natural }

public sealed interface ReadAloudState {
    public data object Idle : ReadAloudState

    /**
     * [fallbackLanguage] is set (once per language per app run) when the natural voice was chosen but cannot read
     * this language, so the phone's voice reads it (`naturalVoiceFallbackNote`).
     */
    public data class Speaking(val utteranceId: String, val engine: ReadEngine, val fallbackLanguage: String? = null) :
        ReadAloudState

    public data class Failed(val problem: MediaProblem) : ReadAloudState
}
