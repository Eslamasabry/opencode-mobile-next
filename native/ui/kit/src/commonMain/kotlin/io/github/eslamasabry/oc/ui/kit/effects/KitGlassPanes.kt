package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.effects.FrostConditions
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.style.GlassFinish
import io.github.eslamasabry.oc.ui.design.style.GlassSpec
import io.github.eslamasabry.oc.ui.design.style.Radii
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.drawGlassFace
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassBlurs
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/*
 * Glass's in-flow panes (owner 2026-11-08, option 1: liquid glass for the floating pieces only; cards, row groups, list
 * groups, chips and fields become much lighter frosted glass, the background's colour clearly showing through, as in
 * Apple's materials). The numbers are GlassSpec's (`resolveClear`); this file decides when they apply, lights the panes'
 * rims from the accent, and draws a chosen row as accent-tinted glass. No plate behind words anywhere: the pane's tint
 * is thickened only as far as its words need (GlassFinish.thickened).
 */

/** Whether Glass's in-flow panes are clear frosted glass here and now (FrostPolicy.clearPanes; before the picture). */
@Composable
internal fun kitClearPanesOn(): Boolean {
    val platform = LocalKitPlatform.current
    val signals by platform.signals.collectAsState()
    val appearance = LocalKitAppearance.current
    val conditions =
        FrostConditions(
            saver = appearance.saver,
            reducedMotion = LocalKitMotion.current.reduced,
            highContrast = signals.highContrast,
            styleAllows = LocalKitStyle.current.style == Style.Glass,
            lensSupported = platform.liquidGlass,
        )
    return FrostPolicy.clearPanes(conditions, appearance.frost, appearance.glassEdges)
}

/**
 * [d]'s rims as Glass draws them now: a chosen pane's accent ring becomes a soft accent rim, bright at its top start and
 * clearly there all round (the accent marks chosen states only); on a [clear] pane the rim is the same neutral white
 * light as the liquid panes' ([LiquidRim], owner 2026-11-08), brightest along the top-start edge (the specular) and
 * faint at the bottom end, never Flutter's .95 white hairline in a light theme.
 */
internal fun glassRims(d: SurfaceDecoration, roles: Roles, clear: Boolean): SurfaceDecoration {
    if (d.edgeWidthDp <= 0f) return d
    val accent = roles.accent
    val chosen = d.edgeStart == accent && d.edgeEnd == accent
    val start = d.edgeStart ?: return d
    if (!chosen && (!clear || start.alpha == 0)) return d
    val rim = if (chosen) accent.withAlpha(1f) else LiquidRim.Light.toArgbValue()
    val dark = roles.dark
    return if (chosen) {
        d.copy(edgeStart = rim.withAlpha(if (dark) CHOSEN_RIM_START_DARK else CHOSEN_RIM_START_LIGHT), edgeEnd = rim.withAlpha(if (dark) CHOSEN_RIM_END_DARK else CHOSEN_RIM_END_LIGHT))
    } else {
        d.copy(edgeStart = rim.withAlpha(if (dark) PANE_RIM_START_DARK else PANE_RIM_START_LIGHT), edgeEnd = rim.withAlpha(if (dark) PANE_RIM_END_DARK else PANE_RIM_END_LIGHT))
    }
}

/** A clear pane's white rim light: the specular at its top start, faint at its bottom end (low in a light theme). */
internal const val PANE_RIM_START_DARK: Float = .30f
internal const val PANE_RIM_START_LIGHT: Float = .45f
internal const val PANE_RIM_END_DARK: Float = .05f
internal const val PANE_RIM_END_LIGHT: Float = .10f

/** A chosen pane's accent rim: clearly there all round, brightest at its top start. */
internal const val CHOSEN_RIM_START_DARK: Float = .90f
internal const val CHOSEN_RIM_START_LIGHT: Float = .95f
internal const val CHOSEN_RIM_END_DARK: Float = .45f
internal const val CHOSEN_RIM_END_LIGHT: Float = .55f

private fun Color.toArgbValue(): Argb =
    Argb(((alpha * 255f + .5f).toInt() shl 24) or ((red * 255f + .5f).toInt() shl 16) or ((green * 255f + .5f).toInt() shl 8) or (blue * 255f + .5f).toInt())

/**
 * A chosen row's face under Glass (owner 2026-11-08: "why is selected looking like this"): accent-tinted frost with the
 * accent rim light, set [CHOSEN_ROW_INSET_DP] in from the row's edges with corners that follow its group's (the panel
 * radius under the Corners knob, less the inset), never Solid's opaque surface3 band. Null under every other style.
 */
@Composable
internal fun kitGlassChosenRow(): Modifier? {
    val style = LocalKitStyle.current
    if (style.style != Style.Glass) return null
    val roles = LocalKitRoles.current
    val backdrop = LocalKitGlassBackdrop.current
    val blurs = kitGlassBlurs(backdrop)
    val clear = kitClearPanesOn() && blurs
    val spots = if (blurs && backdrop != null) backdrop.frostedSpots else emptyList()
    val panel = StyleResolver.corner(FlutterKitTokens.PANEL_CORNER_RADIUS.toFloat(), style.knobs.corners, style.spec.tokens.sharpRadius)
    val radius = (panel - CHOSEN_ROW_INSET_DP).coerceAtLeast(0f)
    val face =
        remember(roles, style, clear, spots, radius) {
            chosenRowFace(roles, style.knobs, clear, spots, radius)
        }
    return Modifier.drawBehind { inset(CHOSEN_ROW_INSET_DP.dp.toPx()) { drawGlassFace(face) } }
}

/** The inset of a chosen row's face from the row's edges, in dp. */
internal const val CHOSEN_ROW_INSET_DP: Float = Space.S1.toFloat()

/**
 * A chosen row's face: GlassSpec's chosen control over its group's pane, thickened until the row's words read over the
 * group's face over the ground and the frosted picture, with its rim lit from the accent.
 */
internal fun chosenRowFace(
    roles: Roles,
    knobs: io.github.eslamasabry.oc.ui.design.style.Knobs,
    clear: Boolean,
    spots: List<Argb>,
    radius: Float,
): SurfaceDecoration {
    fun resolve(r: SurfaceRequest) = (if (clear) GlassSpec.resolveClear(r) else GlassSpec.resolve(r))!!
    val ground = roles.ground.withAlpha(1f)
    val group = resolve(SurfaceRequest(roles, SurfaceRole.Content, knobs, SurfaceLevel.Surface1))
    val under = (listOf(ground) + spots).flatMap { s -> listOfNotNull(group.fill, group.fillStart).map { it.over(s.withAlpha(1f)) } }
    val request =
        SurfaceRequest(roles, SurfaceRole.Control, knobs, SurfaceLevel.Surface3, Radii.all(radius), color = roles.surface3.withAlpha(1f), selected = true)
    val solid = roles.surface3.over(ground)
    // The row lies inside its group's pane, which frosts already: no frost, no shadow of its own.
    val face = resolve(request).copy(frost = 0f, shadows = emptyList())
    return glassRims(GlassFinish.thickened(face, roles, solid, under), roles, clear)
}
