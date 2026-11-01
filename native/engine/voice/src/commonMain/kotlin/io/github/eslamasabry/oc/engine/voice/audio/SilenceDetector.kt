package io.github.eslamasabry.oc.engine.voice.audio

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** How long of quiet ends a recording (Dart `voiceSilenceToStop`). */
public val VOICE_SILENCE_TO_STOP: Duration = 1_500.milliseconds

/**
 * Port of Dart `SilenceDetector` (`lib/voice/silence.dart`, B-MEDIA-015): [silence] of quiet after the person has
 * spoken ends the recording. Quiet before any speech never ends it, a short click ([minSpeech]) is not speech, and
 * speaking again restarts the quiet. Loudness is the 0..1 RMS level of one buffer.
 */
public class SilenceDetector(
    public val silence: Duration = VOICE_SILENCE_TO_STOP,
    public val speechLevel: Float = 0.015f,
    public val minSpeech: Duration = 300.milliseconds,
) {
    private var loud: Duration = Duration.ZERO
    private var quiet: Duration = Duration.ZERO

    public val heardSpeech: Boolean get() = loud >= minSpeech

    /** One buffer: its [level] and how long it [span]s. True when the person has stopped talking. */
    public fun add(level: Float, span: Duration): Boolean {
        if (level >= speechLevel) {
            loud += span
            quiet = Duration.ZERO
            return false
        }
        if (!heardSpeech) return false
        quiet += span
        return quiet >= silence
    }

    public fun reset() {
        loud = Duration.ZERO
        quiet = Duration.ZERO
    }
}
