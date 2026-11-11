package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/**
 * How the dock's body is painted: the face's [modifier] (applied to a node clipped to the body's outline), the ink of
 * the words on it, and whether the outline has shoulders into the screen edge.
 */
@Immutable
internal class DockFace(val modifier: Modifier, val ink: Color, val ink2: Color, val shoulders: Boolean)

/**
 * THE ONE PLACE the dock's face is decided (for the design/glass owner to switch).
 *
 * Today it asks the resolver for the nearest existing floating role, as a square face that the body clips to its own
 * outline: the kit's rule (a part gets its face from `kitSurfaceLook` by role; it never paints its own surface, border or
 * shadow). When the owner adds a `Dock` surface role and its recipe, this becomes a one-line switch of [SurfaceRole];
 * under a lens the body is drawn by the joined-glass field from [KitEdgeDockState.body] instead, and this function
 * returns that face.
 *
 * Shoulders (the concave blends into the screen edge) are a third shape the two-box join does not draw yet: on for the
 * non-lens looks, off under Glass for now, decided here so turning them on later is one line.
 */
@Composable
internal fun rememberDockFace(): DockFace {
    val colors = LocalKitColors.current
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                role = SurfaceRole.Floating,
                shape = KitShapeId.Square,
                level = SurfaceLevel.Surface2,
                ownFill = colors.surface2,
            ),
        )
    val ink = look.words ?: colors.text1
    val shoulders = LocalKitStyle.current.style != Style.Glass
    return DockFace(look.modifier, ink, colors.text2, shoulders)
}

/**
 * The failure ink. The design owner will add a red status role; until then the attention role stands in, here only,
 * so the swap is one line. Every use pairs it with the shape mark ("!" in a dot): it is never colour alone. Uses: the
 * ring's stroke (3 dp), the mark's dot (11.2 dp across) with the "!" in [KitColors.onAttentionFill], the state words
 * in the swell and the list (Caption/Secondary text), and the pulse along the body's outline (2.6 dp stroke, 600 ms).
 */
internal fun dockFailureInk(colors: KitColors): Color = colors.attention

/**
 * The dock's springs. The kit's motion scheme has no "liquid" pair yet (asked of the design owner as
 * `KitMotionSpecs.liquidDrop` and `liquidFill`); they live here, in one place, and every one turns into an instant
 * change (travel) or a short cross-fade (looks) under reduced motion.
 *
 * The rule (joined-glass.md, "Motion"): one small overshoot along the direction of travel (damping ratio 0.62: the dock
 * arriving from the edge, the swell coming out, the ring riding out, the list's width), a calm fill sideways (0.92: the
 * swell or the list growing taller), no bounce on closing.
 */
internal object DockSprings {
    fun <T> drop(reduced: Boolean, stiffness: Float = 300f): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.62f, stiffness = stiffness)

    fun <T> fill(reduced: Boolean, stiffness: Float = 260f): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.92f, stiffness = stiffness)

    fun <T> close(reduced: Boolean, stiffness: Float = 340f): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 1f, stiffness = stiffness)

    /** Following a finger closely (the swell's height while scrubbing, a ring finding its new slot). */
    fun <T> follow(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 900f)

    /** A look change (colour, alpha): a short cross-fade, also under reduced motion. */
    fun <T> look(): AnimationSpec<T> = tween(140, easing = KitEasing.Enter)

    /** A timed part of a ring's motion (the check drawing, the settle, the flash, the charge, the pulse's run). */
    fun <T> timed(ms: Int): FiniteAnimationSpec<T> = tween(ms.coerceAtLeast(0), easing = KitEasing.Steady)

    /** The task arc advancing: one small overshoot. */
    fun <T> arc(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(dampingRatio = 0.55f, stiffness = 190f)

    /** A kick in a ring's scale (a tap, a task done, the check landing), released with a velocity. */
    fun <T> kick(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.4f, stiffness = 600f)

    /** The request's pop (played twice). */
    fun <T> pop(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.35f, stiffness = 760f)

    /** The failure's one sideways shake (a stiff, lightly damped spring released with a velocity). */
    fun <T> shake(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.2f, stiffness = 2600f)
}
