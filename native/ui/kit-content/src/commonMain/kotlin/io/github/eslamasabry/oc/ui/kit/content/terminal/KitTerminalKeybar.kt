package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled

/** A key name is protocol data; its face and accessible name come from native string resources. */
@Immutable
public data class KitTerminalKeyChoice(val id: String, val label: String, val selected: Boolean? = null)

/**
 * The single terminal key row: minimum kit targets, one shared horizontal scroll, labelled modifier
 * selection.
 */
@Composable
public fun KitTerminalKeybar(
    keys: List<KitTerminalKeyChoice>,
    enabled: KitEnabled,
    onKey: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { key ->
            KitButton(
                label = key.label,
                onClick = { onKey(key.id) },
                modifier = Modifier.semantics { key.selected?.let { selected = it } },
                role =
                    if (key.selected == true) KitButtonRole.Secondary else KitButtonRole.Tertiary,
                enabled = enabled,
            )
        }
    }
}
