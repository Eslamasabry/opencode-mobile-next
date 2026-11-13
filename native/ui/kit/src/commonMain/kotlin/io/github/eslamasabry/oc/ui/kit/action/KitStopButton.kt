package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairlineBorder
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/** The stop button's measures (LAMP-CRAFT §4, §4a). */
public object KitStopDims {
    /** Always this much to touch (`Space.S6` + `Space.S5`). */
    public const val TOUCH: Int = Space.S6 + Space.S5

    /** A conversation row's visible circle: the whole touch box. */
    public const val CIRCLE_ROW: Int = TOUCH

    /** A sub-agent's visible circle, inside the same touch box (`Space.S6` + `Space.S1`). */
    public const val CIRCLE_STEP: Int = Space.S6 + Space.S1

    /** The stop square inside the circle (`Space.S3`). */
    public const val GLYPH: Int = Space.S3
}

/**
 * Stop for a running conversation or sub-agent (LAMP-PLAN §C): a stop square in a quiet circle, no word, 44 dp to touch.
 * [label] names the target ("Stop “Bank API migration”"). [compact] is the sub-agent's 28 dp circle inside the same
 * 44 dp touch box. A tap plays [KitHaptic.Commit]; the caller then holds the stop and shows its receipt.
 *
 * States: default, pressed; Solid draws a hairline ring, the other styles their control face by role
 * Text: none (the label is semantics only)
 * Cap: none (44 dp square)
 * Motion: the platform press only
 * Replaces: none
 */
@Composable
public fun KitStopButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = LocalKitColors.current
    val solid = LocalKitStyle.current.style == Style.Solid
    val circle = (if (compact) KitStopDims.CIRCLE_STEP else KitStopDims.CIRCLE_ROW).dp
    val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Control, shape = KitShapeId.Circle, level = SurfaceLevel.Surface1))
    Box(
        modifier
            .size(KitStopDims.TOUCH.dp)
            .testTag(STOP_BUTTON_TAG)
            .semantics { contentDescription = label }
            .kitTappable(onClick = onClick, onClickLabel = label, haptic = KitHaptic.Commit, shape = KitShapeId.Circle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(circle)
                .kitSurface(look)
                .then(if (solid) Modifier.kitHairlineBorder(kitHairline(), colors.text3, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            KitGlyph(KitIcons.stop, look.words ?: colors.text1, KitStopDims.GLYPH.dp)
        }
    }
}

/** The stop button's touch box, for tests. */
public const val STOP_BUTTON_TAG: String = "kit-stop-button"
