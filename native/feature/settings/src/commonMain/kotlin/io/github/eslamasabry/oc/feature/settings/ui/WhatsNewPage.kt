package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The declared Flutter release notes, explicitly labelled by that release, never as this native
 * build's changes.
 */
@Composable
internal fun ColumnScope.WhatsNewBody(step: Int, actions: SettingsActions) {
    val notes =
        listOf(
            Res.string.whatsNew220Updates,
            Res.string.whatsNew220Agents,
            Res.string.whatsNew220LargeText,
            Res.string.whatsNew220PasteImages,
            Res.string.whatsNew220Voice,
        )
    KitGroupNote(stringResource(Res.string.kitTourStep, step + 1, notes.size))
    KitRowGroup {
        KitRow(
            title = stringResource(notes[step]),
            modifier = Modifier.testTag("whats-new-step-$step"),
        )
        if (step <= 2)
            KitRow(title = stringResource(Res.string.whatsNewShowMe), onClick = actions::onNewsShow)
        if (step > 0)
            KitRow(
                title = stringResource(Res.string.whatsNewBack),
                onClick = { actions.onNewsStep(step - 1) },
            )
        if (step < notes.lastIndex) {
            KitRow(
                title = stringResource(Res.string.kitTourNext),
                onClick = { actions.onNewsStep(step + 1) },
            )
            KitRow(title = stringResource(Res.string.kitTourSkip), onClick = { actions.onBack() })
        } else {
            KitRow(
                title = stringResource(Res.string.whatsNewShare),
                onClick = { actions.onOutside(SettingsOutside.ShareApp) },
            )
            KitRow(title = stringResource(Res.string.whatsNewClose), onClick = { actions.onBack() })
        }
    }
}

@Composable
internal fun ColumnScope.TipsBody(actions: SettingsActions) {
    KitRowGroup(label = stringResource(Res.string.tipsToursLabel)) {
        KitRow(
            title = stringResource(Res.string.tourMakeItYoursTitle),
            onClick = {
                actions.onAppearanceGuide(0, false)
                actions.onOpen(
                    io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage.Appearance
                )
            },
        )
        KitRow(
            title = stringResource(Res.string.whatsNewTipsTitle),
            onClick = {
                actions.onOpen(
                    io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage.WhatsNew
                )
            },
        )
    }
    KitRow(
        title = stringResource(Res.string.terminal40TipTitle),
        supporting = stringResource(Res.string.terminal40Tip),
        supportingMaxLines = 4,
    )
    KitRow(
        title = stringResource(Res.string.discoverShowTipsAgain),
        supporting = stringResource(Res.string.discoverShowTipsSubtitle),
        onClick = actions::onShowTipsAgain,
    )
}
