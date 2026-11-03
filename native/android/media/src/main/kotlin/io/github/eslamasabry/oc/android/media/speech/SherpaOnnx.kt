package io.github.eslamasabry.oc.android.media.speech

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

/*
 * P12-FIX-LINUX-VOICE: the only code that touches sherpa-onnx (ExternalRules allow the vendored 2.0.1 AAR here only).
 * Plain parameters in, plain results out, so :android:voice's SherpaRuntime port is adapted in the app wiring without
 * an :android → :android edge. Every call may block in JNI; callers run it on the speech lane. Field values follow the
 * Flutter app's lib/voice/recognizer.dart and natural_synthesis.dart (same sherpa version, same files on disk).
 */

/** One warm Whisper recognizer (`OfflineRecognizer(assetManager = null, config)`): reads files, not assets. */
public class SherpaWhisper internal constructor(private val recognizer: OfflineRecognizer) {
    /** createStream → acceptWaveform → decode → getResult.text; the stream is always released. */
    public fun decode(samples: FloatArray, sampleRate: Int): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    public fun release(): Unit = recognizer.release()
}

/** One Kokoro voice (`OfflineTts(assetManager = null, config)`). */
public class SherpaKokoro internal constructor(private val tts: OfflineTts) {
    public val sampleRate: Int get() = tts.sampleRate()

    /** Streams float PCM chunks into [sink]; returns when done or once [sink] answered 0. */
    public fun generate(text: String, speakerId: Int, speed: Float, sink: (FloatArray) -> Int) {
        tts.generateWithCallback(text, speakerId, speed, sink)
    }

    public fun release(): Unit = tts.release()
}

public object SherpaOnnx {
    /** The native libraries the Kotlin API loads (`System.loadLibrary("sherpa-onnx-jni")`, which needs onnxruntime). */
    public val LIBRARIES: List<String> = listOf("libsherpa-onnx-jni.so", "libonnxruntime.so")

    /** Throws (sherpa's `require`) when a file is missing or broken. */
    public fun whisper(
        encoder: String,
        decoder: String,
        tokens: String,
        language: String,
        task: String,
        numThreads: Int,
        debug: Boolean,
        provider: String,
        modelType: String,
    ): SherpaWhisper {
        val config =
            OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig =
                    OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(encoder = encoder, decoder = decoder, language = language, task = task),
                        tokens = tokens,
                        numThreads = numThreads,
                        debug = debug,
                        provider = provider,
                        modelType = modelType,
                    ),
            )
        return SherpaWhisper(OfflineRecognizer(null, config))
    }

    /** Throws when a file is missing or broken. `lang` empty and one sentence at a time, as the Flutter app does. */
    public fun kokoro(
        model: String,
        voices: String,
        tokens: String,
        lexicon: String,
        lang: String,
        numThreads: Int,
        provider: String,
        maxNumSentences: Int,
    ): SherpaKokoro {
        val config =
            OfflineTtsConfig(
                model =
                    OfflineTtsModelConfig(
                        kokoro = OfflineTtsKokoroModelConfig(model = model, voices = voices, tokens = tokens, lexicon = lexicon, lang = lang),
                        numThreads = numThreads,
                        debug = false,
                        provider = provider,
                    ),
                maxNumSentences = maxNumSentences,
            )
        return SherpaKokoro(OfflineTts(null, config))
    }
}
