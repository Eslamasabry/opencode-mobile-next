package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel

/**
 * The thinking levels of a model, as every sheet that offers them shows them (the chat's model sheet, New conversation's
 * "Choose a model", Settings' Model defaults): the section word, then one chip per level, the chosen one filled. The
 * chips wrap onto lines, never scroll and never cut at the edge (360 dp, text 1.3). It belongs in the model sheet's
 * `header`, first under the title and above the search, so it stays put while the list scrolls. One arrangement: a sheet
 * that draws its levels another way is a fault.
 *
 * [labels] are the words of each level in order (Default first); [selected] indexes the chosen one (-1: none).
 */
@Composable
public fun KitThinkingLevels(
    title: String,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    KitSectionLabel(title)
    KitChipWrap(modifier) {
        labels.forEachIndexed { index, label ->
            KitChip(
                label = label,
                kind = KitChipKind.Action,
                selected = index == selected,
                onClick = { onSelect(index) },
            )
        }
    }
}
