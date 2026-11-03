package io.github.eslamasabry.oc.ui.kitchat.composer

/** The level meter's bars (Dart `LevelMeter`, B-MEDIA-003): bar i is lit when the level reaches (i + 1) / 12. */
public object KitLevelMeterRules {
    public const val BARS: Int = 9

    public fun isLit(index: Int, level: Float): Boolean = level >= (index + 1) / 12f
}
