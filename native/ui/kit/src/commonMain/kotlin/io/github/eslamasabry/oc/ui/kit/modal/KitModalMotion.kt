package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace

/** Plan 08 §5.5 modal motion numbers as pure rules (B-KIT-015, -016, -022, -032). */
public object KitModalMotionRules {
    /** MOT-2: a modal never scales in. Part B's 0.96 `scaleIn` goes when its owner adopts [KitModalMotion]. */
    public const val INITIAL_SCALE: Float = 1f

    /** A dialog or centred panel rises 8 dp as it fades in, at Full only. */
    public fun dialogRiseDp(state: KitMotionState): Int = if (state.travels) MotionTokens.ENTRANCE_RISE_DP else 0

    /**
     * A menu drops 8 dp from its anchor (starts 8 dp up, toward the anchor) or rises when it opens above; Full only,
     * position only.
     */
    public fun menuDropDp(state: KitMotionState, above: Boolean): Int =
        when {
            !state.travels -> 0
            above -> MotionTokens.MENU_DROP_DP
            else -> -MotionTokens.MENU_DROP_DP
        }
}

/**
 * The modal transitions of plan 08 §5.5, built only from `KitMotionSpecs` (rule R1), for the owners of `KitSheet`,
 * `KitDialog`, `KitMenu` and `KitUndoBar` to adopt (they are part-B files this unit does not edit; report §4 D3). Every
 * one is `None` under Off, so a modal's first frame is final (MOT-7).
 *
 * Motion: dialog / centred panel = fade STANDARD + 8 dp rise in, fade QUICK out; menu = fade QUICK + 8 dp drop; sheet =
 *   slide up and down on Spatial; scrim = fade with the sheet; Calm = fades only (dialog, menu) and springs (sheet);
 *   Off = none (classes Travel, Settle)
 */
public object KitModalMotion {
    @Composable
    public fun dialogEnter(): EnterTransition {
        val state = KitMotion.state
        if (state.reduced) return EnterTransition.None
        val specs = KitMotion.specs
        val rise = with(LocalDensity.current) { KitModalMotionRules.dialogRiseDp(state).dp.roundToPx() }
        val fade = fadeIn(specs.pace(KitPace.Standard))
        return if (rise == 0) fade else fade + slideInVertically(specs.pace<IntOffset>(KitPace.Standard)) { rise }
    }

    @Composable
    public fun dialogExit(): ExitTransition =
        if (KitMotion.state.reduced) ExitTransition.None else fadeOut(KitMotion.specs.pace(KitPace.Quick))

    @Composable
    public fun menuEnter(above: Boolean): EnterTransition {
        val state = KitMotion.state
        if (state.reduced) return EnterTransition.None
        val specs = KitMotion.specs
        val drop = with(LocalDensity.current) { KitModalMotionRules.menuDropDp(state, above).dp.roundToPx() }
        val fade = fadeIn(specs.pace(KitPace.Quick))
        return if (drop == 0) fade else fade + slideInVertically(specs.pace<IntOffset>(KitPace.Quick)) { drop }
    }

    @Composable
    public fun menuExit(): ExitTransition =
        if (KitMotion.state.reduced) ExitTransition.None else fadeOut(KitMotion.specs.pace(KitPace.Quick))

    /** The bottom sheet slides up from below the window on `Spatial` (Calm too: Settle class). */
    @Composable
    public fun sheetEnter(): EnterTransition =
        if (KitMotion.state.reduced) EnterTransition.None
        else slideInVertically(KitMotion.specs.spatial<IntOffset>()) { it }

    @Composable
    public fun sheetExit(): ExitTransition =
        if (KitMotion.state.reduced) ExitTransition.None
        else slideOutVertically(KitMotion.specs.spatial<IntOffset>()) { it }

    @Composable
    public fun scrimEnter(): EnterTransition =
        if (KitMotion.state.reduced) EnterTransition.None else fadeIn(KitMotion.specs.pace(KitPace.Standard))

    @Composable
    public fun scrimExit(): ExitTransition =
        if (KitMotion.state.reduced) ExitTransition.None else fadeOut(KitMotion.specs.pace(KitPace.Quick))
}
