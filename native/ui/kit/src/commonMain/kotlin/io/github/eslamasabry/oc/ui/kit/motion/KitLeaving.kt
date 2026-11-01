package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * A child on its way out (Dart `KitReveal`/`KitSwap`): it is picture only. It ignores every touch and is hidden from
 * TalkBack, so nothing reads or acts on content that is going away. Kit-internal.
 */
internal fun Modifier.kitLeaving(leaving: Boolean): Modifier =
    if (!leaving) {
        this
    } else {
        this.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    }
