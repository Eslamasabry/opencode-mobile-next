package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.AboutFacts
import io.github.eslamasabry.oc.feature.settings.presentation.MemoryPreviewUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.ShortcutDoes
import io.github.eslamasabry.oc.feature.settings.presentation.ShortcutHelp
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateState
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * I20: the version and its state in one row, the state's one main action, Check now, the switch
 * that turns checks off, What's new and the build number.
 */
@Composable
internal fun ColumnScope.UpdatesBody(u: UpdateFacts, actions: SettingsActions) {
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.appUpdateCurrentVersion, u.current),
            modifier = Modifier.testTag("updates-state"),
            supporting = updateLine(u.state),
            supportingMaxLines = 3,
        )
    }
    Column(
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        when (u.state) {
            is UpdateState.Available ->
                KitButton(stringResource(Res.string.appUpdateUpdate), actions::onUpdateAction)
            is UpdateState.Downloading ->
                KitButton(
                    stringResource(Res.string.appUpdateCancel),
                    actions::onUpdateAction,
                    role = KitButtonRole.Secondary,
                )
            is UpdateState.Downloaded ->
                KitButton(stringResource(Res.string.appUpdateInstall), actions::onUpdateAction)
            is UpdateState.Failed ->
                KitButton(stringResource(Res.string.appUpdateTryAgain), actions::onCheckForUpdates)
            else -> Unit
        }
        val busy = u.state is UpdateState.Downloading || u.state == UpdateState.Installing
        if (!busy && u.state !is UpdateState.Failed) {
            KitButton(
                stringResource(Res.string.appUpdateCheckNow),
                actions::onCheckForUpdates,
                modifier = Modifier.testTag("updates-check-now"),
                role =
                    if (u.state is UpdateState.Available || u.state is UpdateState.Downloaded)
                        KitButtonRole.Secondary
                    else KitButtonRole.Primary,
                working = u.state == UpdateState.Checking,
            )
        }
    }
    KitRowGroup {
        KitSwitchRow(
            title = stringResource(Res.string.appUpdateCheckSwitch),
            checked = u.checksOn,
            supporting = stringResource(Res.string.appUpdateCheckSwitchBody),
            onCheckedChange = actions::onUpdateChecks,
        )
    }
    if (!u.checksOn) KitGroupNote(stringResource(Res.string.appUpdateCheckingOff))
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.appUpdateWhatsNew),
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.onOutside(SettingsOutside.ReleaseNotes) },
        )
        KitRow(
            title = stringResource(Res.string.appUpdateBuildLabel),
            trailing = KitRowTrailing.Value(u.build, chevron = false),
        )
    }
}

@Composable
private fun updateLine(state: UpdateState): String =
    when (state) {
        UpdateState.NeverChecked -> stringResource(Res.string.appUpdateNeverChecked)
        UpdateState.Checking -> stringResource(Res.string.appUpdateChecking)
        is UpdateState.UpToDate ->
            state.checked?.let { stringResource(Res.string.appUpdateUpToDate, it) }
                ?: stringResource(Res.string.appUpdateCheckedJustNow)
        is UpdateState.Available ->
            stringResource(
                Res.string.appUpdateAvailableLine,
                state.version,
                stringResource(Res.string.appUpdateSizeMb, state.sizeMb),
            )
        is UpdateState.Downloading ->
            stringResource(Res.string.appUpdateDownloadingTitle, state.version) +
                " · " +
                stringResource(Res.string.appUpdatePercent, state.percent.toString())
        is UpdateState.Downloaded ->
            stringResource(Res.string.appUpdateInstallReadyTitle, state.version)
        UpdateState.Installing -> stringResource(Res.string.appUpdateInstalling)
        is UpdateState.Failed -> updateFailureWords(state.why)
    }

/**
 * About › Keyboard shortcuts (F: shortcuts.dart `showShortcutsHelp`): one row per shortcut, what it
 * does with its keys at the end. Key names are the same in every language and read left to right.
 */
@Composable
internal fun ColumnScope.ShortcutsBody(about: AboutFacts) {
    KitRowGroup(Modifier.testTag("shortcuts-list")) {
        ShortcutHelp.entries(about.shortcutModifier).forEach { entry ->
            KitRow(
                title = shortcutWords(entry.does),
                titleMaxLines = 2,
                trailing = KitRowTrailing.Value(entry.keys, chevron = false),
            )
        }
    }
}

@Composable
private fun shortcutWords(does: ShortcutDoes): String =
    stringResource(
        when (does) {
            ShortcutDoes.NewConversation -> Res.string.e7LocaleUiNewSession
            ShortcutDoes.Destinations -> Res.string.e7LocaleUiDestinations
            ShortcutDoes.Settings -> Res.string.e7LocaleUiSettings
            ShortcutDoes.CloseScreen -> Res.string.e7LocaleUiCloseScreen
            ShortcutDoes.ThisList -> Res.string.e7LocaleUiThisList
            ShortcutDoes.CloseOverlay -> Res.string.e7LocaleUiCloseOverlay
        }
    )

/** About identity, notices and bundled components (Flutter about_screen.dart:246-416). */
@Composable
internal fun ColumnScope.AboutBody(
    about: AboutFacts,
    actions: SettingsActions,
    voiceLicenses: Boolean = false,
    updates: UpdateFacts? = null,
    memory: MemoryPreviewUi? = null,
) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val knownBuild = about.version.isNotBlank() && about.build.isNotBlank()
    val version =
        if (knownBuild) stringResource(Res.string.aboutBuildVersion, about.version, about.build)
        else stringResource(Res.string.appTitle)
    Column {
        KitRowGroup(leadingTiles = true) {
            KitRow(
                title = version,
                leading = KitRowLeading.Glyph(KitIcons.phone),
                trailing =
                    if (knownBuild)
                        KitRowTrailing.Copy(version, stringResource(Res.string.aboutCopyVersion))
                    else KitRowTrailing.None,
                modifier = Modifier.testTag("about-version"),
            )
            updates?.let { update ->
                val checking =
                    update.state == UpdateState.Checking ||
                        update.state is UpdateState.Downloading ||
                        update.state == UpdateState.Installing
                val line = aboutUpdateLine(update.state)
                KitRow(
                    title =
                        stringResource(
                            when (update.state) {
                                is UpdateState.Available -> Res.string.appUpdateUpdate
                                is UpdateState.Downloaded -> Res.string.appUpdateInstall
                                else -> Res.string.rb44AboutCheckUpdates
                            }
                        ),
                    leading = KitRowLeading.Glyph(KitIcons.download),
                    supporting = line,
                    supportingMaxLines = 2, // F: lib/ui/screens/about_screen.dart:389
                    enabled = !checking,
                    disabledReason = line.takeIf { checking },
                    onClick = {
                        if (
                            update.state is UpdateState.Available ||
                                update.state is UpdateState.Downloaded
                        )
                            actions.onUpdateAction()
                        else actions.onCheckForUpdates()
                    },
                    modifier = Modifier.testTag("about-check-updates"),
                )
            }
        }
        // F: about_screen.dart:407-430: the package id and signer folded under Details, for the
        // same-signer check before an update.
        if (about.packageId.isNotBlank()) {
            val redactor = LocalKitRedactor.current
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyAll),
                modifier = gutter.padding(top = Space.S2.dp).testTag("about-details"),
                values =
                    listOfNotNull(
                        KitTechnicalValue(
                            stringResource(Res.string.aboutPackageId),
                            redactor.details(about.packageId),
                        ),
                        about.signer
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                KitTechnicalValue(
                                    stringResource(Res.string.aboutSigningCertificate),
                                    redactor.details(it),
                                )
                            },
                    ),
            )
        }
        Spacer(Modifier.height(Space.S4.dp))
        KitNotice(stringResource(Res.string.e7SettingsNonAffiliation), gutter, liveRegion = false)
        Spacer(Modifier.height(Space.S3.dp))
        KitNotice(
            stringResource(Res.string.e7SettingsAlphaBody),
            gutter,
            title = stringResource(Res.string.e7SettingsDetailUi19),
            icon = KitIcons.experiments,
            liveRegion = false,
        )
        // F: about_screen.dart:274-310: the shortcut layer is discoverable without already knowing a
        // shortcut, and means nothing without a keyboard.
        if (about.keyboardShortcuts) {
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
            KitRowGroup(label = stringResource(Res.string.aboutHelpSection), leadingTiles = true) {
                KitRow(
                    title = stringResource(Res.string.e7LibraryKeyboardShortcuts),
                    leading = KitRowLeading.Glyph(KitIcons.keyboard),
                    trailing = KitRowTrailing.Chevron,
                    modifier = Modifier.testTag("library-keyboard-shortcuts"),
                    onClick = { actions.onOpen(SettingsPage.KeyboardShortcuts) },
                )
            }
        }
        if (about.notices) {
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
            KitRowGroup(
                label = stringResource(Res.string.e7SettingsDetailUi18),
                leadingTiles = true,
            ) {
                KitRow(
                    title = stringResource(Res.string.aboutBundledComponents),
                    supporting = stringResource(Res.string.aboutBundledComponentsDetail),
                    supportingMaxLines = 2, // F: lib/ui/screens/about_screen.dart:330
                    leading = KitRowLeading.Glyph(KitIcons.info),
                    trailing = KitRowTrailing.Chevron,
                    modifier = Modifier.testTag("about-bundled-components"),
                    onClick = { actions.onOpen(SettingsPage.Notices) },
                )
            }
        }
        if (voiceLicenses) {
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
            VoiceLicenseRows(actions)
        }
        // The developer switch (F: about_screen.dart _DeveloperGroup): off by default; Chats home's Memory section
        // shows while it is on.
        if (memory != null) {
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
            KitRowGroup(label = stringResource(Res.string.fbDeveloperTitle), leadingTiles = true) {
                KitSwitchRow(
                    title = stringResource(Res.string.fbDeveloperFactsBook),
                    checked = memory.on,
                    supporting = stringResource(Res.string.fbDeveloperFactsBookDetail),
                    leading = KitRowLeading.Glyph(KitIcons.database),
                    onCheckedChange = actions::onMemoryPreview,
                )
                if (memory.on && memory.phoneNode) {
                    KitRow(
                        title = stringResource(Res.string.fbNodeRow),
                        supporting = stringResource(Res.string.fbNodeRowDetail),
                        supportingMaxLines = 3,
                        leading = KitRowLeading.Glyph(KitIcons.phone),
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.onOutside(SettingsOutside.MemoryOnPhone) },
                    )
                }
            }
        }
    }
}

@Composable
private fun aboutUpdateLine(state: UpdateState): String =
    when (state) {
        UpdateState.NeverChecked -> stringResource(Res.string.rb44AboutUpdateIdle)
        UpdateState.Checking -> stringResource(Res.string.rb44AboutUpdateChecking)
        is UpdateState.UpToDate -> stringResource(Res.string.rb44AboutUpdateCurrent)
        is UpdateState.Downloading -> stringResource(Res.string.rb44AboutUpdateDownloading)
        is UpdateState.Failed -> stringResource(Res.string.rb44AboutUpdateFailed)
        else -> updateLine(state)
    }
