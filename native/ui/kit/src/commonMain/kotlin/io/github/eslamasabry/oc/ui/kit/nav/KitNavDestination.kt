package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/**
 * One top-level place (Dart `KitNavDestination`). A destination the window cannot offer is absent, never disabled
 * (owner 2026-10-20). [needsYou] > 0 adds the amber count, always with its words in [needsYouWords] for TalkBack, so
 * the state is never colour alone. [icon] is a kit glyph (`KitGlyphs`, K0); without one the label stands alone.
 */
@Immutable
public data class KitNavDestination(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val needsYou: Int = 0,
    val needsYouWords: String? = null,
)

/** Rail and sidebar labels grow up to 2.0×, Flutter's `navLabelMaxScale`. */
internal const val NAV_LABEL_CAP: Float = 2.0f

/**
 * A nav label: the platform's own size up to [NAV_LABEL_CAP] × spec (the frame-text method of `KitText`, with the
 * nav cap). Kit-internal.
 */
@Composable
internal fun NavLabel(text: String, role: TypeRole, tone: TextTone, modifier: Modifier = Modifier, center: Boolean = false, maxLines: Int = 2) {
    val base = LocalKitTypography.current.style(role)
    val app = LocalKitAppDensity.current ?: LocalDensity.current
    val spec = base.fontSize.value
    val target = minOf(with(app) { spec.sp.toDp().value }, spec * NAV_LABEL_CAP)
    val k = target / spec
    val style =
        base.copy(
            color = LocalKitColors.current.tone(tone),
            fontSize = target.sp,
            lineHeight = (base.lineHeight.value * k).sp,
            letterSpacing = (base.letterSpacing.value * k).sp,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        )
    CompositionLocalProvider(LocalDensity provides Density(app.density, 1f)) {
        BasicText(text, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** The destination's glyph, tinted, decorative; grows with text up to 1.5×. */
@Composable
internal fun NavGlyph(icon: ImageVector, tone: TextTone) {
    val size = 24 * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)
    Image(
        icon,
        contentDescription = null,
        modifier = Modifier.size(size.dp),
        colorFilter = ColorFilter.tint(LocalKitColors.current.tone(tone)),
    )
}

/**
 * The amber needs-you count (an approved amber meaning, plan 06 §2), capped at 1.3× like every badge. Decorative: the
 * destination's semantics carry the words.
 */
@Composable
internal fun NavNeedsYouBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    Box(
        modifier
            .sizeIn(minWidth = LocalKitTokens.current.smallIcon.dp)
            .background(colors.attentionFill, KitShapeId.Pill.shape())
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val base = LocalKitTypography.current.style(TypeRole.Caption)
        val app = LocalKitAppDensity.current ?: LocalDensity.current
        val spec = base.fontSize.value
        val target = minOf(with(app) { spec.sp.toDp().value }, spec * TextScalePolicy.BADGE_CAP)
        CompositionLocalProvider(LocalDensity provides Density(app.density, 1f)) {
            BasicText(
                if (count > 99) "99+" else count.toString(),
                style = base.copy(color = colors.onAttentionFill, fontSize = target.sp, lineHeight = (target * 1.4f).sp),
                maxLines = 1,
            )
        }
    }
}
