package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.kit.style.KitStyleState
import io.github.eslamasabry.oc.ui.kit.style.KitStyles

/**
 * Dart `schemeFromRoles` (`lib/ui/theme_roles.dart`), slot for slot (plan 06 §5.4). Only the M3 parts the kit wraps
 * read it. Compose has no common `fromSeed`, so the base is `dark/lightColorScheme()` and every slot Dart sets is set.
 */
internal object MaterialBridge {
    fun scheme(r: Roles): ColorScheme {
        val c = r.toKitColors()
        val primaryContainer = r.accent.withAlpha(if (r.dark) 0.16f else 0.14f).over(r.surface1).toColor()
        val tertiaryContainer = r.attentionSurface.over(r.surface1).toColor()
        val high = if (r.dark) lerp(r.surface2, r.surface3, 0.5f).toColor() else c.surface2
        val flat = r.hairline.over(r.surface1).toColor()
        val base = if (r.dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = c.accent, onPrimary = c.onAccent,
            primaryContainer = primaryContainer, onPrimaryContainer = c.text1,
            primaryFixed = c.accent, onPrimaryFixed = c.onAccent,
            secondary = c.text2, onSecondary = c.ground,
            secondaryContainer = c.surface3, onSecondaryContainer = c.text1,
            tertiary = c.attention, onTertiary = c.onAttentionFill,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = c.text1,
            // No red: Material's error roles are the neutral ink.
            error = c.text1, onError = c.ground, errorContainer = c.surface3, onErrorContainer = c.text1,
            surface = c.ground, onSurface = c.text1, onSurfaceVariant = c.text2,
            surfaceDim = c.ground, surfaceBright = c.surface3,
            surfaceContainerLowest = c.ground, surfaceContainerLow = c.surface1, surfaceContainer = c.surface2,
            surfaceContainerHigh = high, surfaceContainerHighest = c.surface3,
            outline = c.text3, outlineVariant = flat,
            inverseSurface = c.text1, inverseOnSurface = c.ground, inversePrimary = c.accent,
            scrim = Color.Black, surfaceTint = Color.Transparent,
            background = c.ground, onBackground = c.text1, surfaceVariant = c.surface1,
        )
    }

    val shapes: Shapes =
        Shapes(
            extraSmall = RoundedCornerShape(9.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(30.dp),
        )

    /** The radii behind [shapes] (tile, button/field/popover, panel, card, sheet), for Corners to reshape. */
    val radii: List<Int> = listOf(9, 14, 18, 22, 30)

    /**
     * [shapes] under the Style's Corners (Dart `AppTheme.shaped`, B-KIT-005): Round is [shapes] itself; Soft takes
     * about 60 %; Sharp squares every one to the style's sharp radius (Paper 0, Signal 4, the rest 2).
     */
    fun shapes(style: KitStyleState): Shapes {
        if (style.knobs.corners == KitCorners.Round) return shapes
        val r = radii.map { KitStyles.corner(it.toFloat(), style.knobs.corners, style.spec.tokens.sharpRadius).dp }
        return Shapes(
            extraSmall = RoundedCornerShape(r[0]),
            small = RoundedCornerShape(r[1]),
            medium = RoundedCornerShape(r[2]),
            large = RoundedCornerShape(r[3]),
            extraLarge = RoundedCornerShape(r[4]),
        )
    }
}
