package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.settings.walkthrough.WhatsNewChange
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.WhatsNewUi
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTourNext
import io.github.eslamasabry.oc.ui.words.res.kitTourSkip
import io.github.eslamasabry.oc.ui.words.res.kitTourStep
import io.github.eslamasabry.oc.ui.words.res.whatsNew220Agents
import io.github.eslamasabry.oc.ui.words.res.whatsNew220LargeText
import io.github.eslamasabry.oc.ui.words.res.whatsNew220PasteImages
import io.github.eslamasabry.oc.ui.words.res.whatsNew220Updates
import io.github.eslamasabry.oc.ui.words.res.whatsNew220Voice
import io.github.eslamasabry.oc.ui.words.res.whatsNewBack
import io.github.eslamasabry.oc.ui.words.res.whatsNewClose
import io.github.eslamasabry.oc.ui.words.res.whatsNewShare
import io.github.eslamasabry.oc.ui.words.res.whatsNewShowMe
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ColumnScope.WhatsNewBody(ui: WhatsNewUi, actions: SettingsActions) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    KitGroupNote(stringResource(Res.string.kitTourStep, ui.index + 1, ui.changes.size))
    KitText(
        text =
            stringResource(
                when (ui.change) {
                    WhatsNewChange.Updates -> Res.string.whatsNew220Updates
                    WhatsNewChange.Agents -> Res.string.whatsNew220Agents
                    WhatsNewChange.LargeText -> Res.string.whatsNew220LargeText
                    WhatsNewChange.PasteImages -> Res.string.whatsNew220PasteImages
                    WhatsNewChange.Voice -> Res.string.whatsNew220Voice
                }
            ),
        modifier = gutter.testTag("whats-new-step-${ui.index}"),
    )
    if (ui.canShow)
        KitButton(
            stringResource(Res.string.whatsNewShowMe),
            actions::onWhatsNewShowMe,
            gutter,
            role = KitButtonRole.Tertiary,
        )
    if (ui.last) {
        KitButton(stringResource(Res.string.whatsNewClose), actions::onWhatsNewClose, gutter)
        KitButton(
            stringResource(Res.string.whatsNewShare),
            { actions.onOutside(SettingsOutside.ShareApp) },
            gutter,
            role = KitButtonRole.Tertiary,
        )
    } else {
        KitButton(stringResource(Res.string.kitTourNext), actions::onWhatsNewNext, gutter)
        KitButton(
            stringResource(Res.string.kitTourSkip),
            actions::onWhatsNewClose,
            gutter,
            role = KitButtonRole.Tertiary,
        )
    }
    if (ui.index > 0)
        KitButton(
            stringResource(Res.string.whatsNewBack),
            actions::onWhatsNewPrevious,
            gutter,
            role = KitButtonRole.Tertiary,
        )
}
