package io.github.eslamasabry.oc.android.voice.speech

/**
 * The few sherpa-onnx calls voice needs, as a port. The adapter over `com.k2fsa.sherpa.onnx` (≈ 60 lines:
 * `OfflineRecognizer(assetManager = null, config)`, `createStream/acceptWaveform/decode/getResult/release`,
 * `OfflineTts(null, config).generateWithCallback`) lives where ExternalRules allow the vendored AAR (`:android:media`
 * today; see docs/native/units/VOICE.md). Every call here may block in JNI: callers run them on the speech lane.
 */
public interface SherpaRuntime {
    /** Throws when a file is missing or broken (sherpa's `require`). */
    public fun newRecognizer(config: WhisperRecognizerConfig): NativeRecognizer

    public fun newTts(config: KokoroTtsConfig): NativeTts
}

public interface NativeRecognizer {
    /** One chunk: createStream → acceptWaveform(samples, 16 000) → decode → getResult.text; the stream is released. */
    public fun decode(samples: FloatArray, sampleRate: Int): String

    public fun release()
}

public interface NativeTts {
    public val sampleRate: Int

    /** `generateWithCallback(text, sid, speed, sink)`: returns when done or when [sink] answered 0. */
    public fun generate(text: String, speakerId: Int, speed: Float, sink: KokoroSink)

    public fun release()
}
