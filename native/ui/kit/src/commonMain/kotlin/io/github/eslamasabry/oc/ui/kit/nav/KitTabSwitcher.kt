package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.fadeThroughIn
import io.github.eslamasabry.oc.ui.kit.motion.fadeThroughOut
import io.github.eslamasabry.oc.ui.kit.motion.kitLeaving
import io.github.eslamasabry.oc.ui.kit.motion.pace

/**
 * Views that each keep their own state, switched by [index] (Dart `KitTabSwitcher`; plan 08 §5.9.2). Every body keeps
 * its saved state while another is shown; the chosen one takes touches, focus and semantics from the first frame; the
 * one being left is picture only. No slide, no swipe paging, no scale (MOT-2).
 *
 * States: default, switching
 * Text: none (bodies decide)
 * Cap: Fill
 * Motion: fade-through STANDARD at Full; Calm = cross-fade QUICK; Off = instant (class Fade)
 * Replaces: KitTabSwitcher (lib/ui/kit/motion/kit_tab_switcher.dart)
 */
@Composable
public fun KitTabSwitcher(index: Int, bodies: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    kitAssert(index in bodies.indices) { "KitTabSwitcher: index is not a body" }
    val holder = rememberSaveableStateHolder()
    val motion = KitMotion.state
    val body: @Composable (Int) -> Unit = { i -> holder.SaveableStateProvider(i) { bodies.getOrNull(i)?.invoke() } }
    when (TabRules.bodyMotion(motion.level)) {
        TabBodyMotion.Instant -> Box(modifier) { body(index) }
        else -> {
            val specs = KitMotion.specs
            val through = TabRules.bodyMotion(motion.level) == TabBodyMotion.FadeThrough
            AnimatedContent(
                targetState = index,
                modifier = modifier,
                transitionSpec = {
                    if (through) fadeIn(specs.fadeThroughIn()) togetherWith fadeOut(specs.fadeThroughOut())
                    else fadeIn(specs.pace(KitPace.Quick)) togetherWith fadeOut(specs.pace(KitPace.Quick))
                },
                label = "kit.tabs",
            ) { i ->
                Box(Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit)) { body(i) }
            }
        }
    }
}

/**
 * The strip over the kept-alive bodies (Dart `KitTabSwitcher.tabs`). [tabs] and [bodies] have the same length: a
 * mismatch is refused in debug (B-KIT-050).
 *
 * States: default, counts loading, needs you, overflowing, switching
 * Text: frame (the strip)
 * Cap: Fill
 * Motion: as KitTabStrip and KitTabSwitcher
 */
@Composable
public fun KitTabSwitcher(
    tabs: List<KitTab>,
    index: Int,
    onSelected: (Int) -> Unit,
    bodies: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    semanticsLabel: String? = null,
) {
    kitAssert(TabRules.bodiesMatch(tabs.size, bodies.size)) { "KitTabSwitcher: ${tabs.size} tabs but ${bodies.size} bodies" }
    Column(modifier) {
        KitTabStrip(tabs, index, onSelected, semanticsLabel = semanticsLabel)
        KitTabSwitcher(index, bodies, Modifier.weight(1f, fill = false))
    }
}
