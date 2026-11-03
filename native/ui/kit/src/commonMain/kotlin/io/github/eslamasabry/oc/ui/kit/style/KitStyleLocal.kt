package io.github.eslamasabry.oc.ui.kit.style

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.Corner
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Shapes

/**
 * The Style in force under a `KitTheme` (Dart `KitStyles.styleOf` + `knobsOf`): the effective style (Saver makes it
 * Solid, high contrast makes Soft Solid) and the Fine-tune knobs under the knob rules. Kit parts read it through
 * [LocalKitStyle]; screens never do.
 */
@Immutable
public data class KitStyleState(val style: KitSurfaceStyle, val knobs: KitKnobs) {
    val spec: KitStyleSpec get() = KitStyles.spec(style)

    /** Solid at its defaults: every part paints exactly its own look (Dart `KitSurfaceLook.isPlain`). */
    val plain: Boolean get() = style == KitSurfaceStyle.Solid && knobs == KitStyleSolid.tokens.defaults

    /** A named kit shape's corner under Corners (hook H4: a stadium follows Sharp too; a circle stays a circle). */
    public fun corner(shape: KitShapeId): Corner {
        val base = Shapes.of(shape)
        val sharp = spec.tokens.sharpRadius
        return when {
            shape == KitShapeId.Circle -> base
            base is Corner.Full -> if (knobs.corners == KitCorners.Sharp) Corner.Radius(sharp.toInt()) else base
            base is Corner.Radius -> Corner.Radius(KitStyles.corner(base.dp.toFloat(), knobs.corners, sharp).toInt())
            else -> base
        }
    }

    public companion object {
        public val Solid: KitStyleState = KitStyleState(KitSurfaceStyle.Solid, KitStyleSolid.tokens.defaults)

        /** The style the person chose, as it applies under [highContrast]. */
        public fun of(appearance: KitAppearance, highContrast: Boolean): KitStyleState {
            val chosen = appearance.surfaceStyle
            return KitStyleState(
                KitStyles.effectiveStyle(chosen, highContrast),
                KitStyles.knobsFor(chosen, appearance.fineTune, highContrast),
            )
        }
    }
}

/** Provided by `KitTheme`; Solid at its defaults outside one, so a part drawn alone looks as it always did. */
public val LocalKitStyle: ProvidableCompositionLocal<KitStyleState> = staticCompositionLocalOf { KitStyleState.Solid }
