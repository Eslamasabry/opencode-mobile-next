package io.github.eslamasabry.oc.engine.voice.readaloud

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.voice.models.InstalledPack
import kotlinx.collections.immutable.ImmutableList

/** Plan 10 §4.5. Android: `AndroidSystemTts` (port of `ReadAloudBridge.kt`). Created lazily, never before consent. */
public interface SystemTts {
    /** Offline voices only, sorted by name; empty → `NoOfflineVoice`. */
    public suspend fun voices(): Outcome<ImmutableList<TtsVoice>>

    /** Suspends until spoken. Cancelling the caller stops it (operation-scoped by construction). */
    public suspend fun speak(text: String, voiceId: String?): Outcome<Unit>
}

/** Kokoro through sherpa-onnx (Android: `SherpaSpeechSynthesizer`). */
public interface SpeechSynthesizer {
    public suspend fun open(pack: InstalledPack, threads: Int): Outcome<SynthesisSession>
}

public interface SynthesisSession {
    /** Must equal the pack's 24,000 Hz or the reading fails. */
    public val sampleRate: Int

    /** Calls [sink] with float samples as they are made; returns when the text is done. Cancel stops at the next callback. */
    public suspend fun synthesize(text: String, speakerId: Int, speed: Float, sink: suspend (FloatArray) -> Unit): Outcome<Unit>

    public suspend fun close()
}
