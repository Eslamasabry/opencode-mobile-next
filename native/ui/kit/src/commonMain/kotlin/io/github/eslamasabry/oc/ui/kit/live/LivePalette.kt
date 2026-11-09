package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.eslamasabry.oc.ui.design.effects.LiveVibrance
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** The theme colours a look is drawn in (kit colour roles, so every look follows light, dark and the accent). */
internal data class LivePalette(
    val ground: Color,
    val accent: Color,
    val partner: Color,
    val glow: Color,
    /** The body text colour: dot, line and glyph looks draw their quiet marks in it. */
    val ink: Color,
    val dark: Boolean,
    /** Vibrance spike: how lively the look draws (Calm = today). */
    val vibe: LiveVibrance = LiveVibrance.Calm,
)

@Composable
internal fun rememberLivePalette(): LivePalette {
    val c = LocalKitColors.current
    val vibe = LocalKitAppearance.current.liveVibrance
    return remember(c, vibe) { livePaletteOf(c, vibe) }
}

/** The look colours for a set of kit colours: glow is the accent 30 % toward the text, ink the text itself. */
internal fun livePaletteOf(c: KitColors, vibe: LiveVibrance = LiveVibrance.Calm): LivePalette =
    LivePalette(c.ground, c.accent, c.partner, lerp(c.accent, c.text1, GLOW_TOWARD_TEXT), c.text1, c.dark, vibe)

private const val GLOW_TOWARD_TEXT: Float = .3f
