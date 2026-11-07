package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap

/** What the composer's one round target shows: the mic, Send, or Stop while a reply runs. */
public enum class KitPrimaryFace { Mic, Send, Stop }

/**
 * The mic and Send share one circular target; changing its meaning cross-fades through the kit's [KitSwap] (QUICK,
 * 150 ms, under Full and Calm; instant with reduced motion). The leaving face ignores touches while it fades, so a
 * second tap never lands on the old meaning.
 */
@Composable
public fun KitComposerPrimaryAction(
    hasMessage: Boolean,
    modifier: Modifier = Modifier,
    mic: @Composable () -> Unit,
    send: @Composable () -> Unit,
) {
    KitComposerPrimaryAction(
        face = if (hasMessage) KitPrimaryFace.Send else KitPrimaryFace.Mic,
        modifier = modifier,
        mic = mic,
        send = send,
        stop = {},
    )
}

/**
 * M-002/M-020 (Flutter `kit_composer.dart` `_Trailing`): Send ⇄ Stop ⇄ mic on the same target, one QUICK cross-fade
 * through [KitSwap], never a one-frame swap. Reduced motion: instant.
 */
@Composable
public fun KitComposerPrimaryAction(
    face: KitPrimaryFace,
    modifier: Modifier = Modifier,
    mic: @Composable () -> Unit,
    send: @Composable () -> Unit,
    stop: @Composable () -> Unit,
) {
    KitSwap(face, modifier, pace = KitPace.Quick) { shown ->
        when (shown) {
            KitPrimaryFace.Mic -> mic()
            KitPrimaryFace.Send -> send()
            KitPrimaryFace.Stop -> stop()
        }
    }
}
