package io.github.eslamasabry.oc.ui.kitchat.composer

import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens

/** The meter lights from the start edge, clamping the input and treating NaN as silence. */
public object KitLevelMeterRules {
    public const val BARS: Int = FlutterKitTokens.METER_BARS

    public fun isLit(index: Int, level: Float): Boolean =
        index in 0 until BARS && !level.isNaN() && level.coerceIn(0f, 1f) >= (index + 1) / 12f // F: lib/ui/kit/kit_level_meter.dart:52-63
}
