package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion

/**
 * The mic and Send share one circular target; changing its meaning fades for 150 ms, or immediately
 * with reduced motion.
 */
@Composable
public fun KitComposerPrimaryAction(
    hasMessage: Boolean,
    modifier: Modifier = Modifier,
    mic: @Composable () -> Unit,
    send: @Composable () -> Unit,
) {
    val reduced = KitMotion.state.reduced
    AnimatedContent(
        targetState = hasMessage,
        modifier = modifier,
        transitionSpec = {
            (if (reduced) EnterTransition.None togetherWith ExitTransition.None
                else fadeIn(tween(150)) togetherWith fadeOut(tween(150)))
                .using(
                    SizeTransform(
                        clip = false,
                        sizeAnimationSpec = { _, _ -> if (reduced) snap() else tween(150) },
                    )
                )
        },
        label = "composer-mic-send",
    ) { message ->
        if (message) send() else mic()
    }
}
