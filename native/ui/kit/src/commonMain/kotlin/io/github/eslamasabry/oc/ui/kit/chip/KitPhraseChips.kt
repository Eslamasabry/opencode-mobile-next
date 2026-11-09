package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem

/**
 * One row of the person's own common phrases above the message box. A tap sends; a long-press opens the
 * Edit / Hide menu. No phrases draws nothing. The row is a [KitFilterChips] line (Dart: `KitFilterChips` in
 * kit_phrase_chips.dart): as wide as its chips while they fit, so the parent places it; past the width it scrolls
 * sideways with a fading edge. [bleed] false keeps the line on the screen gutter, as Flutter's part does; the chat
 * composer, whose block already keeps the gutter, passes true so the chips start on the message box's edge (E20-S11).
 */
@Composable
public fun KitPhraseChips(
    phrases: List<String>,
    label: String,
    insertLabel: (String) -> String,
    menuLabel: (String) -> String,
    hideLabel: String,
    onChoose: (String) -> Unit,
    onHide: (String) -> Unit,
    modifier: Modifier = Modifier,
    editLabel: String? = null,
    onEdit: ((String) -> Unit)? = null,
    bleed: Boolean = true,
) {
    if (phrases.isEmpty()) return
    Box(
        modifier.kitMaxWidth(CapRole.Conversation)
            .padding(bottom = Space.S2.dp) // F: lib/ui/kit/chat/kit_phrase_chips.dart:56-58
            .semantics { contentDescription = label },
    ) {
        KitFilterChips(bleed = bleed) {
            phrases.forEach { phrase ->
                var menu by remember(phrase) { mutableStateOf(false) }
                KitMenu(
                    menu,
                    listOfNotNull(
                        if (editLabel != null && onEdit != null) KitMenuItem(editLabel, { menu = false; onEdit(phrase) }) else null,
                        KitMenuItem(hideLabel, { menu = false; onHide(phrase) }),
                    ),
                    { menu = false },
                    title = menuLabel(phrase),
                ) {
                    // The long-press menu is also a screen reader action, so hiding never needs the gesture.
                    Box(
                        Modifier.pointerInput(phrase) { detectTapGestures(onLongPress = { menu = true }) }
                            .semantics { customActions = listOf(CustomAccessibilityAction(menuLabel(phrase)) { menu = true; true }) }
                    ) {
                        KitChip(phrase, kind = KitChipKind.Action, onClick = { onChoose(phrase) }, spoken = insertLabel(phrase))
                    }
                }
            }
        }
    }
}
