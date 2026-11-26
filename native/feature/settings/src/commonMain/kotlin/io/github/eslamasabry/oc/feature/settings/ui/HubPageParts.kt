package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.dockModeTitle
import io.github.eslamasabry.oc.ui.words.res.settingsPhraseChipsHiddenCount
import io.github.eslamasabry.oc.ui.words.res.settingsPhraseChipsShowHidden
import io.github.eslamasabry.oc.ui.words.res.settingsV3Language
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The "Other conversations" sheet of a group's list, opened from the group's Choice row. */
@Composable
internal fun GroupOtherSheet(state: SettingsUi, actions: SettingsActions, open: Boolean, onClose: () -> Unit) {
    val otherNow = state.group?.rows.orEmpty().firstNotNullOfOrNull { (it.door as? SettingsDoor.Choice)?.mode }
    if (open && otherNow != null) {
        KitSheet(
            visible = true,
            onDismissRequest = onClose,
            title = stringResource(Res.string.dockModeTitle),
        ) {
            OtherConversationsBody(otherNow) {
                actions.onOtherConversations(it)
                onClose()
            }
        }
    }
}

/** The plain parts handed to [SettingsPagePanes]: the open page and the list pane's scroll. */
internal class SettingsPagePanesParts(
    val page: SettingsPage,
    val listScroll: ScrollState,
)

/** The language sheet of a group's list: picking a language closes it after the action runs. */
@Composable
internal fun GroupLanguageSheet(state: SettingsUi, actions: SettingsActions, open: Boolean, onClose: () -> Unit) {
    if (open) {
        KitSheet(
            visible = true,
            onDismissRequest = onClose,
            title = stringResource(Res.string.settingsV3Language),
        ) {
            LanguageBody(
                state.language,
                object : SettingsActions by actions {
                    override fun onLanguage(
                        language: io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
                    ) {
                        actions.onLanguage(language)
                        onClose()
                    }
                },
            )
        }
    }
}

/** The row that offers to show a group's hidden phrase chips, when any are hidden on this profile. */
@Composable
internal fun GroupHiddenPhrases(state: SettingsUi, actions: SettingsActions, page: SettingsPage.Group) {
    if (page.id == SettingsGroupId.Conversations)
        state.phrases
            ?.takeIf { it.hiddenCount > 0 && it.profile != null }
            ?.let { phrases ->
                KitRow(
                    title = stringResource(Res.string.settingsPhraseChipsShowHidden),
                    leading = KitRowLeading.Glyph(KitIcons.hidden),
                    supporting =
                        pluralStringResource(
                            Res.plurals.settingsPhraseChipsHiddenCount,
                            phrases.hiddenCount,
                            phrases.hiddenCount,
                        ),
                    onClick = actions::onShowHiddenPhrases,
                )
            }
}
