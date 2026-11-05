package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow

/**
 * One row of the person's own common phrases above the message box. A tap sends; a long-press opens the
 * Edit / Hide menu. No phrases draws nothing. The row scrolls sideways, centred while it fits.
 */
@Composable
public fun KitPhraseChips(
    phrases: List<String>,
    label: String,
    sendLabel: (String) -> String,
    menuLabel: String,
    editLabel: String,
    hideLabel: String,
    onChoose: (String) -> Unit,
    onEdit: (String) -> Unit,
    onHide: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (phrases.isEmpty()) return
    Row(
        modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth().padding(bottom = Space.S2.dp)
            .horizontalScroll(rememberScrollState()).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        phrases.forEach { phrase ->
            var menu by remember(phrase) { mutableStateOf(false) }
            KitMenu(
                menu,
                listOf(
                    KitMenuItem(editLabel, { menu = false; onEdit(phrase) }),
                    KitMenuItem(hideLabel, { menu = false; onHide(phrase) }),
                ),
                { menu = false },
                title = menuLabel,
            ) {
                Box(Modifier.pointerInput(phrase) { detectTapGestures(onLongPress = { menu = true }) }) {
                    KitChip(phrase, kind = KitChipKind.Action, onClick = { onChoose(phrase) }, spoken = sendLabel(phrase))
                }
            }
        }
    }
}
