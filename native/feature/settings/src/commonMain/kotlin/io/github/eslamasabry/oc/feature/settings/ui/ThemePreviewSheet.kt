package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.theme.KitThemePreview
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ThemePreviewSheet(
    pack: PackId,
    ui: AppearanceUi,
    onDismiss: () -> Unit,
    onApply: (PackId) -> Unit,
) {
    val current = LocalKitColors.current.dark
    var dark by remember(pack) { mutableStateOf(current) }
    KitSheet(
        visible = true,
        onDismissRequest = onDismiss,
        title = packName(pack),
        primary =
            if (pack == ui.pack) null
            else KitAction(stringResource(Res.string.e7AppearanceApply), { onApply(pack) }),
    ) {
        if (pack == ui.pack) KitGroupNote(stringResource(Res.string.appearancePickerInUse))
        KitChoiceList(
            choices =
                listOf(
                    KitChoice(true, stringResource(Res.string.e7AppearanceDark)),
                    KitChoice(false, stringResource(Res.string.e7AppearanceLight)),
                ),
            selected = dark,
            semanticsLabel = stringResource(Res.string.appearanceAskMode),
            onSelect = { dark = it },
        )
        KitThemePreview(pack, dark) {
            Column {
                KitRow(
                    title = stringResource(Res.string.capabilityFiles),
                    supporting = stringResource(Res.string.capabilityFilesDetail),
                    supportingMaxLines = 3,
                )
                KitGroupNote(stringResource(Res.string.themePreviewOnly))
            }
        }
    }
}
