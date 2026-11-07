package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth

/** Dart `KitActionStack`: at most two tertiary actions (the assert of its constructor). */
public const val KIT_ACTION_STACK_MAX_TERTIARY: Int = 2 // F: lib/ui/kit/kit_action_stack.dart:41

/**
 * A block of actions where every rare path gets its own line (Dart `KitActionStack`; design standard §2): the
 * [primary], then the [secondary], full width, then each [tertiary] text action under the other, aligned to the start.
 * Used where the tertiary actions must never sit side by side (a server's Update and its destructive Stop), so a thumb
 * aimed at one cannot land on the other. At most two tertiary actions. A disabled action's reason shows as a muted line
 * under its button (STATE-8); every gap between actions is `space2`, a reason sits `space1` under its button.
 *
 * States: default, disabled reasons, destructive
 * Text: frame
 * Cap: Reading
 * Motion: none of its own
 * Replaces: KitActionStack (lib/ui/kit/kit_action_stack.dart)
 */
@Composable
public fun KitActionStack(
    modifier: Modifier = Modifier,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    tertiary: List<KitAction> = emptyList(),
) {
    require(tertiary.size <= KIT_ACTION_STACK_MAX_TERTIARY) { "KitActionStack: at most two tertiary actions" }
    if (primary == null && secondary == null && tertiary.isEmpty()) return
    Column(
        modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().testTag("kit-action-stack"),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        primary?.let { StackAction(it, KitButtonRole.Primary) }
        secondary?.let { StackAction(it, KitButtonRole.Secondary) }
        tertiary.forEach { StackAction(it, KitButtonRole.Tertiary) }
    }
}

/** One action of the stack and, when it cannot run, its reason line under it (Dart `_KitActionReason`). */
@Composable
private fun StackAction(action: KitAction, role: KitButtonRole) {
    Column(Modifier.fillMaxWidth()) {
        if (role == KitButtonRole.Tertiary) {
            // KitInset: the text action hugs its words at the start edge; its padding reaches into the gutter.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { KitButton(action, role = role) }
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                KitButton(action, Modifier.fillMaxWidth(), role)
            }
        }
        val reason = action.enabled.reason
        if (reason != null && reason.isNotBlank()) {
            KitText(
                reason,
                Modifier.padding(top = Space.S1.dp).testTag("kit-action-reason"),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
            )
        }
    }
}
