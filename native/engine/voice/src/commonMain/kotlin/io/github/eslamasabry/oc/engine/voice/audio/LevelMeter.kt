package io.github.eslamasabry.oc.engine.voice.audio

/** The voice pill's level meter rule (Dart `kit_level_meter.dart`, B-MEDIA-003): 9 bars, bar i lit at (i + 1) / 12. */
public object LevelMeter {
    public const val BARS: Int = 9

    public fun isLit(bar: Int, level: Float): Boolean = bar in 0 until BARS && level >= (bar + 1) / 12f

    /** How many bars are lit, counted from the start edge (the kit mirrors them in RTL). */
    public fun litFor(level: Float): Int = (0 until BARS).count { isLit(it, level) }
}
