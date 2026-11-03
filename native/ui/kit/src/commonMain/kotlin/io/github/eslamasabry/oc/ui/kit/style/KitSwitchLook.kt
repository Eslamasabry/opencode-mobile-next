package io.github.eslamasabry.oc.ui.kit.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import io.github.eslamasabry.oc.ui.design.tokens.Alphas
import io.github.eslamasabry.oc.ui.kit.theme.KitColors

/** A switch's track and thumb colours (Dart `AppTheme` switch theme, N1; B-KIT-004). */
public data class KitSwitchLook(val track: Color, val thumb: Color) {
    public companion object {
        /**
         * On and usable: the accent track. On but fixed (it cannot act): the accent at the disabled alpha over the tile
         * step, so it never reads as a live switch. Off: the tile step. The thumb is the accent's words when on.
         */
        public fun of(colors: KitColors, checked: Boolean, active: Boolean): KitSwitchLook =
            KitSwitchLook(
                track = when {
                    checked && active -> colors.accent
                    checked -> colors.accent.copy(alpha = Alphas.DISABLED).compositeOver(colors.surface3)
                    else -> colors.surface3
                },
                thumb = if (checked) colors.onAccent else colors.text2,
            )
    }
}
