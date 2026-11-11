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
 * How the dock's body is painted: the face's [modifier], the ink of the words on it, and whether the outline has
 * shoulders into the screen edge.
 *
 * - [drawsOwnBody] false (Solid, the only face today): the body node clips [modifier] to the body's outline, the join
 *   field's zero line built from the frame ([KitEdgeDockState.bodyOutline]).
 * - [drawsOwnBody] true (a lens face, the glass owner's): the body node applies NO clip and draws NO path; [modifier]
 *   is drawn over the whole dock area and must draw the body itself from [KitEdgeDockState.body] (box A, box B as it is
 *   now, and `joinReach`), read in its draw phase. The marks and the failure pulse still draw over it.
 */
@Immutable
internal class DockFace(
    val modifier: Modifier,
    val ink: Color,
    val ink2: Color,
    val shoulders: Boolean,
    val drawsOwnBody: Boolean = false,
)

/**
 * THE ONE PLACE the dock's face is decided (for the design/glass owner to switch).
 *
 * Today it asks the resolver for the nearest existing floating role, as a square face that the body clips to its own
 * outline: the kit's rule (a part gets its face from `kitSurfaceLook` by role; it never paints its own surface, border or
 * shadow). When the owner adds a `Dock` surface role and its recipe, this becomes a one-line switch of [SurfaceRole];
 * under a lens this function returns a face with [DockFace.drawsOwnBody] set, whose modifier draws the joined glass
 * from [state]'s [KitEdgeDockState.body] (the state is passed in for that; Solid does not read it).
 *
 * Shoulders (the concave blends into the screen edge) are a third shape the two-box join does not draw yet: on for the
 * non-lens looks, off under Glass for now, decided here so turning them on later is one line.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun rememberDockFace(state: KitEdgeDockState): DockFace {
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
    return DockFace(look.modifier, ink, colors.text2, shoulders, drawsOwnBody = false)
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
 * `KitMotionSpecs.liquidDrop` and `liquidFill`); they live here, in one place. EVERY spec takes `reduced` and snaps by
 * itself under reduced motion, so no caller has to remember to gate it.
 *
 * The rule (joined-glass.md, "Motion"): liquid levels, it does not ring. One small overshoot along the direction of
 * travel (damping ratio 0.62, about 8 %: the dock arriving from the edge, the swell's growth, the ring riding out, the
 * list's width), and only ONE value of a motion takes it (the swell's length and height fill calmly while its growth
 * drops); a calm fill sideways (0.92: the swell or the list growing taller); no bounce on closing. The ring's own
 * accents (arc, kick, pop) overshoot once at most (damping 0.6 or more); the failure's shake swings twice at most.
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

    /** A look change (colour, alpha): a short cross-fade; a snap under reduced motion, as every kit look. */
    fun <T> look(reduced: Boolean): AnimationSpec<T> = if (reduced) snap() else tween(140, easing = KitEasing.Enter)

    /** A timed part of a ring's motion (the check drawing, the settle, the flash, the charge, the pulse's run). */
    fun <T> timed(reduced: Boolean, ms: Int): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(ms.coerceAtLeast(0), easing = KitEasing.Steady)

    /** The task arc advancing: one small overshoot along its travel (the drop's ratio). */
    fun <T> arc(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(dampingRatio = 0.62f, stiffness = 190f)

    /** A kick in a ring's scale (a tap, a task done, the check landing), released with a velocity: one overshoot. */
    fun <T> kick(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(dampingRatio = 0.6f, stiffness = 600f)

    /** The request's pop (played twice), released with a velocity: one overshoot each. */
    fun <T> pop(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(dampingRatio = 0.6f, stiffness = 760f)

    /**
     * The failure's sideways shake, released with a velocity: the one cue that may swing, and only twice (at 0.45 each
     * swing keeps a fifth of the last: about 6.5, 1.3, then 0.3 dp for the release the ring uses).
     */
    fun <T> shake(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else spring(dampingRatio = 0.45f, stiffness = 2600f)
}
