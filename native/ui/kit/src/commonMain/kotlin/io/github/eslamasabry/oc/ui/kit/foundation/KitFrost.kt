package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import io.github.eslamasabry.oc.ui.design.effects.FrostConditions
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion

/**
 * What scrolls behind a frost surface, recorded once per frame (plan 06 K12). Compose cannot blur
 * "what is behind" without a source, so `KitScreen` hoists this and the transcript list wears
 * [kitFrostSource].
 */
@Stable
public class KitFrostState internal constructor(internal val layer: GraphicsLayer) {
    internal var sourceOrigin: Offset = Offset.Zero
}

@Composable
public fun rememberKitFrostState(): KitFrostState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { KitFrostState(layer) }
}

/**
 * Records the content into [state] and draws it as usual. A kit modifier on the feature allowlist.
 */
public fun Modifier.kitFrostSource(state: KitFrostState): Modifier =
    this.onGloballyPositioned { state.sourceOrigin = it.positionInRoot() }
        .drawWithContent {
            state.layer.record { this@drawWithContent.drawContent() }
            drawLayer(state.layer)
        }

/**
 * Frost behind a part (the composer or the chat header only, owner rule R3). The content recorded
 * by [kitFrostSource] is drawn through the platform's blur + saturation effect (Android API 31+),
 * then the role's tint at the strength's alpha, then the role's edge line. Solid — the tint colour,
 * opaque — when the platform has no effect (below API 31, desktop) or a hold applies (Saver,
 * reduced motion, high contrast, a Style other than Solid).
 */
@Composable
public fun Modifier.kitFrost(
    role: FrostRole,
    state: KitFrostState,
    shown: Boolean = true,
): Modifier {
    val platform = LocalKitPlatform.current
    val appearance = LocalKitAppearance.current
    val colors = LocalKitColors.current
    val reduced = LocalKitMotion.current.reduced
    val signals by platform.signals.collectAsState()
    val density = LocalDensity.current.density
    val conditions =
        FrostConditions(
            appearance.saver,
            reduced,
            signals.highContrast,
            styleAllows = appearance.style.name == "Solid",
        )
    val spec =
        if (shown) FrostPolicy.spec(role, appearance.frost, colors.dark, conditions) else null
    val effect =
        remember(spec, density, platform) { spec?.let { platform.frostEffect(it, density) } }
    val tint = if (role == FrostRole.ChatHeader) colors.ground else colors.surface2
    val edge: Color =
        if (role == FrostRole.ChatHeader) colors.text1.copy(alpha = FrostPolicy.HEADER_EDGE_ALPHA)
        else Color.White.copy(alpha = FrostPolicy.HIGHLIGHT_ALPHA)
    val blur = rememberGraphicsLayer()
    val origin = remember { arrayOf(Offset.Zero) }
    return this.onGloballyPositioned { origin[0] = it.positionInRoot() }
        .clipToBounds()
        .drawBehind {
            if (effect == null || spec == null) {
                drawRect(tint)
            } else {
                blur.renderEffect = effect
                val shift = state.sourceOrigin - origin[0]
                blur.record { translate(shift.x, shift.y) { drawLayer(state.layer) } }
                drawLayer(blur)
                drawRect(tint.copy(alpha = spec.tintAlpha))
            }
            if (effect != null && spec != null) {
                val y = if (role == FrostRole.ChatHeader) size.height - 1f else 0f
                drawLine(edge, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
        }
}
