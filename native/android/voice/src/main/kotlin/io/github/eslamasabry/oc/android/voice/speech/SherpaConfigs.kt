package io.github.eslamasabry.oc.android.voice.speech

import io.github.eslamasabry.oc.engine.voice.models.InstalledPack

/**
 * The fields sherpa-onnx's `OfflineRecognizerConfig` / `OfflineModelConfig` / `OfflineWhisperModelConfig` receive
 * (plan 10 §6.3; Dart `buildWhisperRecognizerConfig`; B-MEDIA-033). Plain data so the mapping is tested with no
 * native load; [SherpaRuntime] copies it field by field into the sherpa classes.
 */
public data class WhisperRecognizerConfig(
    val encoder: String,
    val decoder: String,
    val tokens: String,
    /** Whisper's code; "" = detect from speech. */
    val language: String,
    val task: String = "transcribe",
    val numThreads: Int,
    val debug: Boolean = false,
    val provider: String = "cpu",
    val modelType: String = "whisper",
)

/** `OfflineTtsKokoroModelConfig(model, voices, tokens, lexicon)` with `lang` empty and `maxNumSentences = 1` (§8.5). */
public data class KokoroTtsConfig(
    val model: String,
    val voices: String,
    val tokens: String,
    val lexicon: String,
    val lang: String = "",
    val numThreads: Int,
    val provider: String = "cpu",
    val maxNumSentences: Int = 1,
)

public object SherpaConfigs {
    public fun recognizer(pack: InstalledPack, languageCode: String, threads: Int): WhisperRecognizerConfig =
        WhisperRecognizerConfig(
            encoder = pack.path("encoder"),
            decoder = pack.path("decoder"),
            tokens = pack.path("tokens"),
            language = languageCode,
            numThreads = threads,
        )

    public fun kokoro(pack: InstalledPack, threads: Int): KokoroTtsConfig =
        KokoroTtsConfig(
            model = pack.path("model"),
            voices = pack.path("voices"),
            tokens = pack.path("tokens"),
            lexicon = pack.path("lexicon"),
            numThreads = threads,
        )
}
