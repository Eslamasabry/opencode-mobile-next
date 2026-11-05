package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** An independent on/off filter, or an action opening the page that owns its results. */
@Composable
public fun KitFilterChip(label: String, selected: Boolean, onPressed: () -> Unit, modifier: Modifier = Modifier, needsYou: Boolean = false, opens: Boolean = false) {
    KitChip(label = label, modifier = modifier, kind = KitChipKind.Action, selected = if (opens) null else selected, tone = if (needsYou) KitChipTone.Attention else KitChipTone.Neutral, onClick = onPressed)
}
