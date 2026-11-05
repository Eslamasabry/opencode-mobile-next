package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/** Flutter KitComposerChips.model: a measured label or glyph-only pill, with the full name always spoken. */
@Composable
public fun KitComposerModelChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KitChip(
        label = label,
        modifier = modifier.testTag("kit-composer-model-chip"),
        kind = KitChipKind.Action,
        icon = KitIcons.model,
        opensMenu = true,
        onClick = onClick,
        adaptiveModel = true,
    )
}
