package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion

/**
 * The composer's status strip — the chip row above the field — and its motion (plan 08 §5.2). [chips] (the model chip,
 * context chips) keep their place and give their room up first, scrolling sideways inside themselves when they no
 * longer fit; [stop] (a `KitComposerStop`) sits at the trailing end, takes its fixed width at once and never shrinks
 * or scrolls away.
 *
 * When the strip was empty, the whole strip unfolds (height + fade, `STANDARD`) with the Stop in it; when it becomes
 * empty it folds away (`QUICK`). When the strip already shows chips, the Stop fades in and out by itself (`QUICK`). Calm
 * keeps the folds and fades; Off is instant. Leaving content stays drawn while it folds.
 *
 * Motion: fold + fade. Replaces: `KitComposerStatusStrip` reveal (lib/ui/kit/chat/kit_composer_status_strip.dart).
 */
@Composable
public fun KitComposerStripReveal(
    stop: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    chips: (@Composable RowScope.() -> Unit)? = null,
) {
    val motion = KitMotion.state
    val specs = KitMotion.specs
    // What leaves stays drawn while it folds or fades (non-snapshot holders: read only by the leaving content).
    val heldStop = remember { arrayOfNulls<@Composable () -> Unit>(1) }
    val heldChips = remember { arrayOfNulls<@Composable RowScope.() -> Unit>(1) }
    if (stop != null) heldStop[0] = stop
    if (chips != null) heldChips[0] = chips
    AnimatedVisibility(
        visible = stop != null || chips != null,
        modifier = modifier,
        enter = if (motion.reduced) EnterTransition.None else expandVertically(specs.enter()) + fadeIn(specs.enter()),
        exit = if (motion.reduced) ExitTransition.None else shrinkVertically(specs.quick()) + fadeOut(specs.quick()),
        label = "composer-strip",
    ) {
        Row(
            Modifier.fillMaxWidth().testTag(STRIP_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                val shown = chips ?: heldChips[0].takeIf { stop == null }
                shown?.invoke(this)
            }
            AnimatedVisibility(
                visible = stop != null,
                enter = if (motion.reduced) EnterTransition.None else fadeIn(specs.quick()),
                exit = if (motion.reduced) ExitTransition.None else fadeOut(specs.quick()),
                label = "composer-stop",
            ) {
                Box { (stop ?: heldStop[0])?.invoke() }
            }
        }
    }
}

/** Test handle of the strip's row. */
public const val STRIP_TAG: String = "kit-composer-strip"
