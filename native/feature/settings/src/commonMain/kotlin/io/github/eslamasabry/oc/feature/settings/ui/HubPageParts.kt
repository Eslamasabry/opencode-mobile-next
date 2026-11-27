package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.settings.presentation.LinuxSpeedFacts
import io.github.eslamasabry.oc.feature.settings.presentation.LinuxSpeedMode
import io.github.eslamasabry.oc.feature.settings.presentation.LinuxSpeedOutcome
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.dockModeTitle
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedChecking
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedCompatible
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedCompatibleSummary
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedFast
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedFastRestored
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedFastSummary
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedRetryFailed
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedStayedCompatible
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedTitle
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedTryFast
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedTryFastSummary
import io.github.eslamasabry.oc.ui.words.res.helpLinuxSpeedTryingFast
import io.github.eslamasabry.oc.ui.words.res.settingsPhraseChipsHiddenCount
import io.github.eslamasabry.oc.ui.words.res.settingsPhraseChipsShowHidden
import io.github.eslamasabry.oc.ui.words.res.settingsV3Language
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
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

/**
 * B-486: Settings › Help shows the phone's current Linux speed mode in plain words and, when it
 * fell back to compatible mode, offers one tap to try fast mode again.
 */
@Composable
internal fun GroupLinuxSpeed(state: SettingsUi, actions: SettingsActions, page: SettingsPage.Group) {
    if (page.id != SettingsGroupId.Help) return
    val speed = state.linuxSpeed?.takeIf { it.installed } ?: return
    KitRowGroup(leadingTiles = true, afterGroup = true) {
        KitRow(
            title = stringResource(Res.string.helpLinuxSpeedTitle),
            modifier = Modifier.testTag("settings-help-linux-speed"),
            leading = KitRowLeading.Glyph(KitIcons.speed),
            supporting = linuxSpeedSummary(speed),
            supportingMaxLines = 3,
            trailing = KitRowTrailing.Value(linuxSpeedLabel(speed), chevron = false),
        )
        if (speed.mode == LinuxSpeedMode.Compatible) {
            KitRow(
                title =
                    stringResource(
                        if (speed.checking) Res.string.helpLinuxSpeedTryingFast
                        else Res.string.helpLinuxSpeedTryFast
                    ),
                modifier = Modifier.testTag("settings-help-try-fast-mode"),
                leading = KitRowLeading.Glyph(KitIcons.lightning),
                supporting = stringResource(Res.string.helpLinuxSpeedTryFastSummary),
                supportingMaxLines = 3,
                onClick = if (speed.checking) null else actions::onTryFastLinuxMode,
            )
        }
    }
}

@Composable
private fun linuxSpeedLabel(speed: LinuxSpeedFacts): String =
    stringResource(
        when {
            speed.checking -> Res.string.helpLinuxSpeedChecking
            speed.mode == LinuxSpeedMode.Fast -> Res.string.helpLinuxSpeedFast
            else -> Res.string.helpLinuxSpeedCompatible
        }
    )

@Composable
private fun linuxSpeedSummary(speed: LinuxSpeedFacts): String =
    stringResource(
        when {
            speed.checking -> Res.string.helpLinuxSpeedTryingFast
            speed.mode == LinuxSpeedMode.Fast && speed.lastOutcome == LinuxSpeedOutcome.SwitchedToFast ->
                Res.string.helpLinuxSpeedFastRestored
            speed.mode == LinuxSpeedMode.Fast -> Res.string.helpLinuxSpeedFastSummary
            speed.lastOutcome == LinuxSpeedOutcome.StayedCompatible -> Res.string.helpLinuxSpeedStayedCompatible
            speed.lastOutcome == LinuxSpeedOutcome.Failed -> Res.string.helpLinuxSpeedRetryFailed
            else -> Res.string.helpLinuxSpeedCompatibleSummary
        }
    )
