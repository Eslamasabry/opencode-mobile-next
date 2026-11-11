package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBodyBoxes
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import kotlin.math.max
import kotlin.math.min

/**
 * The recording of what lies behind the dock, provided by the screen that hosts it: the chat's transcript recording
 * (the one its bar and message box read), the conversations list's (the one its floating + reads). Null: none in scope,
 * and the dock keeps its solid face under every style. The dock is never inside this recording (it would bend itself).
 */
public val LocalKitDockGlass: ProvidableCompositionLocal<KitFrostState?> = staticCompositionLocalOf { null }

/**
 * How the dock's body is painted: the face's [modifier], the ink of the words on it, and whether the outline has
 * shoulders into the screen edge.
 *
 * - [drawsOwnBody] false (Solid, Soft, Brutal, Paper, Signal, and Glass without a lens or a recording): the body node
 *   clips [modifier] to the body's outline, the join field's zero line built from the frame
 *   ([KitEdgeDockState.bodyOutline]).
 * - [drawsOwnBody] true (Glass with a lens over a recording): the body node applies NO clip and draws NO path;
 *   [modifier] is drawn over the whole dock area and draws the body itself from [KitEdgeDockState.body] (box A, box B as
 *   it is now, and `joinReach`), read in its draw phase. The marks and the failure pulse still draw over it. [words]
 *   goes on the dock's own area, the ancestor of every word, ring and control on the body: their ink flips with the
 *   glass over bright and dark content as the bar's words do ([onGlass]: their second line reads brighter on glass).
 */
@Immutable
internal class DockFace(
    val modifier: Modifier,
    val ink: Color,
    val ink2: Color,
    val shoulders: Boolean,
    val drawsOwnBody: Boolean = false,
    val words: Modifier = Modifier,
    val onGlass: Boolean = false,
)

/**
 * THE ONE PLACE the dock's face is decided.
 *
 * Under Glass, with a lens (the same conditions as every liquid pane), a recording in scope ([LocalKitDockGlass]) and
 * a place in the window's liquid budget, the dock is ONE body of liquid glass (role [LiquidRole.Dock]: the bar's recipe
 * at rest, the menu's fully grown, mixed by the growth): its face draws the strip and the swell, the peek or the list
 * joined by the join field from [KitEdgeDockState.body], with no clip ([DockFace.drawsOwnBody]); the words and marks
 * take the bar's ink on glass and flip with it.
 *
 * Tucked or at rest the dock ranks below the pills in the window's budget, grown it takes its own rank
 * ([LiquidEdge.dockRank]); a dock short of a lens is frost of the same shape, as every liquid pane is.
 *
 * Otherwise (every other style, Glass without a lens or a recording) it asks the resolver for the
 * nearest existing floating role, as a square face that the body clips to its own outline: the kit's rule (a part gets
 * its face from `kitSurfaceLook` by role; it never paints its own surface, border or shadow).
 *
 * Shoulders (the concave blends into the screen edge) are a third shape the two-box join does not draw yet: on for the
 * non-lens looks, off under Glass, decided here so turning them on later is one line.
 */
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
    val shoulders = LocalKitStyle.current.style != Style.Glass
    val boxes = remember(state) { dockBodyBoxes(state) }
    val area = remember(state) { dockBodyArea(state) }
    // Tucked or at rest the dock yields its lens to the pills; grown it takes its own rank (LiquidEdge.dockRank).
    val grownNow = state.swell != null || state.panelShown
    val glass =
        kitLiquidFace(
            LiquidRole.Dock, LocalKitDockGlass.current, rest = LiquidRole.Dock, grown = LiquidRole.Menu, boxes = boxes, wordsArea = area,
            rank = LiquidEdge.dockRank(grownNow),
        )
    if (glass != null) {
        // Read in composition: the dock's marks follow the ink flip (180 ms, only when the content behind changes).
        val f = glass.flip.progress.value
        return DockFace(
            glass.face,
            lerp(glass.ink, glass.flipInk, f),
            lerp(glass.ink2, glass.flipInk2, f),
            shoulders = false,
            drawsOwnBody = true,
            words = glass.words,
            onGlass = true,
        )
    }
    val ink = look.words ?: colors.text1
    return DockFace(look.modifier, ink, colors.text2, shoulders, drawsOwnBody = false)
}

/** The dock's body as the glass draws it: [KitEdgeDockState.body], unchanged (dock-area px). */
private fun dockBodyBoxes(state: KitEdgeDockState): (KitLiquidBodyBoxes) -> Boolean {
    val body = KitDockBody()
    return { out ->
        state.body(body)
        out.a = Rect(body.stripLeft, body.stripTop, body.stripRight, body.stripBottom)
        out.aRadius = body.stripRadius
        out.hasB = body.hasSecond
        out.b = if (body.hasSecond) Rect(body.secondLeft, body.secondTop, body.secondRight, body.secondBottom) else Rect.Zero
        out.bRadius = body.secondRadius
        out.reach = body.joinReach
        out.growth = body.growth
        true
    }
}

/** The body's bounds now (dock-area px): what the words' ink flip samples. */
private fun dockBodyArea(state: KitEdgeDockState): () -> Rect? {
    val body = KitDockBody()
    return {
        state.body(body)
        var r = Rect(body.stripLeft, body.stripTop, body.stripRight, body.stripBottom)
        if (body.hasSecond) r = Rect(min(r.left, body.secondLeft), min(r.top, body.secondTop), max(r.right, body.secondRight), max(r.bottom, body.secondBottom))
        // The strip runs past the screen edge: only what shows is sampled.
        val w = state.widthDp * state.density
        Rect(max(r.left, 0f), r.top, min(r.right, w), r.bottom)
    }
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
