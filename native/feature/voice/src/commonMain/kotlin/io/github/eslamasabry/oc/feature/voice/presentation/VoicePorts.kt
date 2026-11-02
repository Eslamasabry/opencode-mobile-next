package io.github.eslamasabry.oc.feature.voice.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.engine.voice.dictation.VoiceController
import io.github.eslamasabry.oc.engine.voice.dictation.VoiceStart
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbeResult
import io.github.eslamasabry.oc.engine.voice.models.ModelCatalog
import io.github.eslamasabry.oc.engine.voice.models.ModelPackSpec
import io.github.eslamasabry.oc.engine.voice.models.ModelPacks
import io.github.eslamasabry.oc.engine.voice.readaloud.ReadAloud
import io.github.eslamasabry.oc.engine.voice.readaloud.ReadRequest
import io.github.eslamasabry.oc.engine.voice.state.ModelPacksState
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.ReadAloudState
import io.github.eslamasabry.oc.engine.voice.state.VoiceHost
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * The ports the voice screens talk through (P12-VOICE). Each is the exact slice of an `:engine:voice` type the screen
 * uses, so presenter tests need no files, microphone or speech engine. Production binds the engine objects through the
 * adapters below (and, for typing on Android without the offline engine, the phone's speech recognizer in the app's
 * VoiceWiring).
 */

/** Speech to text for the composer: the engine's [VoiceController] or the phone's recognizer. */
public interface Dictation {
    /** The engine's states: Starting → Listening(words) → Writing → Done(words, endedBy) | Failed | Idle. */
    public val state: StateFlow<VoiceState>

    /** 0..1, for the level meter. */
    public val level: StateFlow<Float>

    public suspend fun start(request: VoiceStart): Outcome<Unit>

    /** Stops listening and writes the rest down; ends in Done or Failed. */
    public suspend fun finish(): Outcome<String>

    /** [keepWords] false = the words are thrown away (Cancel, a slip, slide-to-cancel). */
    public suspend fun cancel(keepWords: Boolean)

    public fun onHostChanged(host: VoiceHost)
}

public fun VoiceController.asDictation(): Dictation =
    object : Dictation {
        override val state: StateFlow<VoiceState> get() = this@asDictation.state
        override val level: StateFlow<Float> get() = this@asDictation.level

        override suspend fun start(request: VoiceStart): Outcome<Unit> = this@asDictation.start(request)

        override suspend fun finish(): Outcome<String> = this@asDictation.finish()

        override suspend fun cancel(keepWords: Boolean) = this@asDictation.cancel(keepWords)

        override fun onHostChanged(host: VoiceHost) = this@asDictation.onHostChanged(host)
    }

/** The model manager as Settings › Voice typing uses it. */
public interface VoiceModels {
    public val state: StateFlow<ModelPacksState>
    public val speechPacks: List<ModelPackSpec>
    public val naturalPack: ModelPackSpec

    public suspend fun refresh()

    public suspend fun device(): DeviceProbeResult

    public suspend fun select(pack: PackId): Outcome<Unit>

    /** Downloads and verifies (SHA-256) [pack]; keeps running when the page closes. */
    public suspend fun download(pack: PackId, replaceExisting: Boolean = false): Outcome<Unit>

    public suspend fun cancelDownload()

    public suspend fun remove(pack: PackId): Outcome<Unit>

    public suspend fun discardPartial(pack: PackId): Outcome<Unit>

    public suspend fun setLanguage(choice: VoiceLanguage): Outcome<Unit>

    public suspend fun setAutoSend(on: Boolean): Outcome<Unit>

    public suspend fun setNaturalVoice(on: Boolean): Outcome<Unit>
}

public fun ModelPacks.asVoiceModels(): VoiceModels =
    object : VoiceModels {
        private val packs = this@asVoiceModels
        override val state: StateFlow<ModelPacksState> get() = packs.state
        override val speechPacks: List<ModelPackSpec> get() = packs.speechPacks
        override val naturalPack: ModelPackSpec get() = ModelCatalog.kokoroEn

        override suspend fun refresh() = packs.refresh()

        override suspend fun device(): DeviceProbeResult = packs.device()

        override suspend fun select(pack: PackId): Outcome<Unit> = packs.select(pack)

        override suspend fun download(pack: PackId, replaceExisting: Boolean): Outcome<Unit> =
            packs.download(pack, replaceExisting).map { }

        override suspend fun cancelDownload() = packs.cancelDownload()

        override suspend fun remove(pack: PackId): Outcome<Unit> = packs.remove(pack)

        override suspend fun discardPartial(pack: PackId): Outcome<Unit> = packs.discardPartial(pack)

        override suspend fun setLanguage(choice: VoiceLanguage): Outcome<Unit> = packs.setLanguage(choice)

        override suspend fun setAutoSend(on: Boolean): Outcome<Unit> = packs.setAutoSend(on)

        override suspend fun setNaturalVoice(on: Boolean): Outcome<Unit> = packs.setNaturalVoice(on)
    }

/** Read aloud as the reply reader uses it. */
public interface Reader {
    public val state: StateFlow<ReadAloudState>

    public fun consentGiven(): Boolean

    public suspend fun giveConsent(): Outcome<Unit>

    public suspend fun read(request: ReadRequest): Outcome<Unit>

    public suspend fun stop()

    public fun onHostChanged(resumed: Boolean)
}

public fun ReadAloud.asReader(): Reader =
    object : Reader {
        override val state: StateFlow<ReadAloudState> get() = this@asReader.state

        override fun consentGiven(): Boolean = this@asReader.consentGiven()

        override suspend fun giveConsent(): Outcome<Unit> = this@asReader.giveConsent()

        override suspend fun read(request: ReadRequest): Outcome<Unit> = this@asReader.read(request)

        override suspend fun stop() = this@asReader.stop()

        override fun onHostChanged(resumed: Boolean) = this@asReader.onHostChanged(resumed)
    }

/** Who turns speech into text on this build (Settings says it plainly). */
public enum class SpeechEngineKind {
    /** The downloaded offline model (sherpa-onnx Whisper): Settings' models are what types. */
    OfflineModel,

    /** The phone's own speech recognition (Android `SpeechRecognizer`), while the offline engine is not in the app. */
    PhoneRecognizer,
}

/** Everything voice, built once the app's stores are ready. */
public class VoiceParts(
    public val dictation: Dictation,
    public val models: VoiceModels,
    public val reader: Reader,
    public val engine: SpeechEngineKind,
    /** False when the phone has no way to capture speech: the mic is absent, never dead. */
    public val micAvailable: Boolean,
    /** False when the offline engine is missing: the natural voice cannot read yet (shown with the reason). */
    public val naturalVoiceAvailable: Boolean,
)

/** The app graph's voice: [parts] waits for the stores; a failure keeps voice visibly unavailable. */
public interface VoiceHub {
    public suspend fun parts(): Outcome<VoiceParts>

    /** Android's app settings page for this app ("Allow microphone in Android settings"). */
    public fun openAppSettings()

    /** A tick every [period] while collected (the pill's clock and the countdown); the platform owns the timer. */
    public fun ticks(period: Duration): Flow<Unit>
}
