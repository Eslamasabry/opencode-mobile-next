package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow

/** Local phrases with a visible per-phrase Hide menu. Choosing only inserts text; sending stays explicit. */
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
) {
    if (phrases.isEmpty() || LocalKitWindow.current.heightDp < 420f) return
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), verticalAlignment = Alignment.CenterVertically) {
        phrases.forEach { phrase ->
            var menu by remember(phrase) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitChip(phrase, kind = KitChipKind.Action, onClick = { onChoose(phrase) },
                    modifier = Modifier.semantics { contentDescription = insertLabel(phrase) })
                KitMenu(menu, listOf(KitMenuItem(hideLabel, { menu = false; onHide(phrase) })), { menu = false }, title = phrase) {
                    KitIconButton(KitPartGlyphs.More, menuLabel(phrase), { menu = true })
                }
            }
        }
    }
}
