package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Lists breathe (motion lead 2026-11-07): the modifier a lazy list's item root takes so a row that arrives fades in, a
 * row that leaves fades out where it was, and the rows around it glide to their new places on the placement spring
 * instead of jumping. Rows present when the list first lays out, and rows scrolled into view, appear as they are.
 *
 * - [fadeIn] false: the row is already on screen in another form (the person's own prompt replacing the bubble that
 *   rose from the field) and must not flash.
 * - [fadeOut] false: the row is replaced in place by another row and must not leave a ghost.
 * - [placement] false: neighbours jump, e.g. while a reply streams (a growing row would otherwise drag the rows above
 *   it a few pixels behind, every frame).
 *
 * Motion: fade in STANDARD, out QUICK; placement on the Placement spring at Full; Calm = fades only; Off = none
 */
@Composable
public fun LazyItemScope.kitItemMotion(fadeIn: Boolean = true, fadeOut: Boolean = true, placement: Boolean = true): Modifier {
    val state = KitMotion.state
    if (state.reduced) return Modifier
    val specs = KitMotion.specs
    return Modifier.animateItem(
        fadeInSpec = if (fadeIn) specs.pace(KitPace.Standard) else null,
        placementSpec = if (placement && state.travels) specs.placement() else null,
        fadeOutSpec = if (fadeOut) specs.pace(KitPace.Quick) else null,
    )
}

/**
 * The modifier for a lazy item that stands for content on its way (skeleton rows, cached rows, an opening status): it
 * leaves on QUICK where it stands, never fades in and never places, so when the real rows arrive (fading in on their
 * own [kitItemMotion]) the two cross-fade with no blank frame between. Nothing at Off.
 */
@Composable
public fun LazyItemScope.kitLoadingItemMotion(): Modifier = kitItemMotion(fadeIn = false, fadeOut = true, placement = false)
