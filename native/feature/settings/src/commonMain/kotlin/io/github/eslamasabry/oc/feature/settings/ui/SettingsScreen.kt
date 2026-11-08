package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityExplainer
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityVariant
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.aboutBundledComponents
import io.github.eslamasabry.oc.ui.words.res.aboutTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdatesTitle
import io.github.eslamasabry.oc.ui.words.res.capabilityScreenTitle
import io.github.eslamasabry.oc.ui.words.res.e7AppearanceTitle
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.librarySettingsTitle
import io.github.eslamasabry.oc.ui.words.res.migrationStorageTitle
import io.github.eslamasabry.oc.ui.words.res.monitorQuietEnd
import io.github.eslamasabry.oc.ui.words.res.monitorQuietStart
import io.github.eslamasabry.oc.ui.words.res.nativeBundledPrivacy
import io.github.eslamasabry.oc.ui.words.res.nativePhraseChipsTitle
import io.github.eslamasabry.oc.ui.words.res.notifyDismiss
import io.github.eslamasabry.oc.ui.words.res.privacyPolicyTitle
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupNotifications
import io.github.eslamasabry.oc.ui.words.res.settingsHubPrivacyRow
import io.github.eslamasabry.oc.ui.words.res.settingsHubVoice
import io.github.eslamasabry.oc.ui.words.res.settingsV3Language
import io.github.eslamasabry.oc.ui.words.res.shareSessionViewsTitle
import io.github.eslamasabry.oc.ui.words.res.whatsNewTitle
import io.github.eslamasabry.oc.ui.words.res.collectTitle
import io.github.eslamasabry.oc.ui.words.res.e7LibraryKeyboardShortcuts
import io.github.eslamasabry.oc.ui.words.res.onboardingSetupGuide
import org.jetbrains.compose.resources.stringResource

/**
 * Settings (plan 07 §5 I): the page on top of [SettingsUi.stack], arranged from kit parts only.
 * Back in the bar walks the stack; at the hub it calls [onExit] (the host closes Settings). The
 * host also routes system Back to [SettingsActions.onBack] first.
 */
@Composable
public fun SettingsScreen(
    state: SettingsUi,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    onExit: (() -> Unit)? = null,
    monitoring: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val back: () -> Unit = { actions.onBack() }
    // Back from Android's notification settings: the blocked notice must say what is true now
    // (Flutter: on resume).
    LifecycleResumeEffect(actions) {
        actions.onResume()
        onPauseOrDispose {}
    }
    when (val page = state.page) {
        SettingsPage.Hub -> HubPage(state, actions, modifier, onExit)
        is SettingsPage.Group -> GroupPage(state, actions, page, modifier, onExit)
        is SettingsPage.ByServer ->
            SettingsFrame(
                if (
                    page.section ==
                        io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
                            .ReadState
                )
                    stringResource(Res.string.shareSessionViewsTitle)
                else
                    rowWords(
                        when (page.section) {
                            io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
                                .Permissions ->
                                io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
                                    .Permissions
                            io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
                                .ModelAndMode ->
                                io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
                                    .ModelAndMode
                            io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
                                .Providers ->
                                io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
                                    .Providers
                            io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
                                .Tools ->
                                io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
                                    .Tools
                            else ->
                                io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
                                    .DefaultShell
                        }
                    ),
                back,
                state,
                actions,
                modifier,
            ) {
                SavedServersBody(state, actions, page.section)
            }
        SettingsPage.Appearance ->
            SettingsFrame(
                stringResource(Res.string.e7AppearanceTitle),
                back,
                state,
                actions,
                modifier,
                footer =
                    if (state.appearance.guide?.done == false) {
                        { AppearanceGuideNext(state.appearance, actions) }
                    } else null,
            ) {
                AppearanceBody(state.appearance, actions)
            }
        SettingsPage.Voice ->
            SettingsFrame(
                stringResource(Res.string.settingsHubVoice),
                back,
                state,
                actions,
                modifier,
            ) {
                state.voice?.let { VoiceBody(it, actions) }
            }
        SettingsPage.Notifications ->
            SettingsFrame(
                stringResource(Res.string.settingsHubGroupNotifications),
                back,
                state,
                actions,
                modifier,
            ) {
                state.notifications?.let { NotificationsBody(it, actions) }
                val request = remember { BringIntoViewRequester() }
                LaunchedEffect(state.focusedAnchor, monitoring) {
                    if (state.focusedAnchor == "notifications-servers") request.bringIntoView()
                }
                Column(Modifier.testTag("notifications-servers").bringIntoViewRequester(request)) {
                    monitoring?.invoke(this)
                }
            }
        SettingsPage.Updates ->
            SettingsFrame(
                stringResource(Res.string.appUpdatesTitle),
                back,
                state,
                actions,
                modifier,
            ) {
                state.updates?.let { UpdatesBody(it, actions) }
            }
        SettingsPage.Notices ->
            SettingsFrame(
                stringResource(Res.string.aboutBundledComponents),
                back,
                state,
                actions,
                modifier,
            ) {
                NoticesBody()
            }
        SettingsPage.PhraseChips ->
            SettingsFrame(
                stringResource(Res.string.nativePhraseChipsTitle),
                back,
                state,
                actions,
                modifier,
            ) {
                PhraseChipsBody(state.phraseChips, actions)
            }
        SettingsPage.WhatsNew ->
            SettingsFrame(
                stringResource(Res.string.whatsNewTitle, state.whatsNew?.version.orEmpty()),
                back,
                state,
                actions,
                modifier,
            ) {
                state.whatsNew?.let { WhatsNewBody(it, actions) }
            }
        SettingsPage.About ->
            SettingsFrame(stringResource(Res.string.aboutTitle), back, state, actions, modifier) {
                AboutBody(
                    state.about,
                    actions,
                    state.voiceLicenses,
                    state.updates,
                    state.memoryPreview,
                    reportCode = state.reportCode,
                )
            }
        SettingsPage.KeyboardShortcuts ->
            SettingsFrame(
                stringResource(Res.string.e7LibraryKeyboardShortcuts),
                back,
                state,
                actions,
                modifier,
            ) {
                ShortcutsBody(state.about)
            }
        SettingsPage.Guide ->
            SettingsFrame(
                stringResource(Res.string.onboardingSetupGuide),
                back,
                state,
                actions,
                modifier,
            ) {
                GuideBody(state.guide, actions)
            }
        is SettingsPage.VoiceLicense -> VoiceLicenseScreen(page.id, state, actions)
        SettingsPage.PrivacyPolicy ->
            SettingsFrame(
                stringResource(Res.string.privacyPolicyTitle),
                back,
                state,
                actions,
                modifier,
            ) {
                KitText(
                    stringResource(Res.string.nativeBundledPrivacy),
                    Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                )
            }
        SettingsPage.Privacy ->
            SettingsFrame(
                stringResource(Res.string.settingsHubPrivacyRow),
                back,
                state,
                actions,
                modifier,
            ) {
                state.localData?.let { LocalDataBody(it, actions) }
                PrivacyBody(state.privacy, actions)
            }
        SettingsPage.WhatWeCollect ->
            SettingsFrame(stringResource(Res.string.collectTitle), back, state, actions, modifier) {
                state.privacy?.let { WhatWeCollectBody(it, actions) }
            }
        SettingsPage.Language ->
            SettingsFrame(
                stringResource(Res.string.settingsV3Language),
                back,
                state,
                actions,
                modifier,
            ) {
                LanguageBody(state.language, actions)
            }
        SettingsPage.ServerCapabilities ->
            SettingsFrame(
                stringResource(Res.string.capabilityScreenTitle),
                back,
                state,
                actions,
                modifier,
            ) {
                state.capabilities?.let { ServerCapabilitiesBody(it, actions) }
                    ?: KitCapabilityExplainer(
                        "server.any",
                        variant = KitCapabilityVariant.State,
                        source = "settings-capabilities",
                    )
            }
        SettingsPage.ServerConfiguration -> ServerConfigurationPage(onBack = back)
        SettingsPage.Storage ->
            SettingsFrame(
                stringResource(Res.string.migrationStorageTitle),
                back,
                state,
                actions,
                modifier,
            ) {
                state.localModels?.let { LocalModelsBody(it, actions) }
            }
        SettingsPage.ReportProblem -> ReportProblemPage(state, actions, back, modifier)
        is SettingsPage.QuietTime ->
            SettingsFrame(
                stringResource(
                    if (page.start) Res.string.monitorQuietStart else Res.string.monitorQuietEnd
                ),
                back,
                state,
                actions,
                modifier,
            ) {
                QuietTimeBody(page.start, state.notifications, actions)
            }
    }
}

/**
 * The settings page frame: the bar with Back, the one save-failed note, then a scrolling column of
 * kit parts.
 */
@Composable
internal fun SettingsFrame(
    title: String,
    onBack: (() -> Unit)?,
    state: SettingsUi,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    search: (@Composable () -> Unit)? = null,
    large: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    KitScreen(
        modifier = modifier,
        topBar = {
            KitTopBar(
                title = title,
                exit =
                    if (onBack != null && (state.stack.size > 1 || state.page == SettingsPage.Hub))
                        KitTopBarExit.Back
                    else KitTopBarExit.None,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = onBack,
                large = large,
                // Flutter's pushed pages: Back and the title share one row, the title on the 56 dp
                // text rail beside it (lib/ui/kit/kit_top_bar.dart:439).
                backBesideTitle = true,
            )
        },
        search = search,
        width =
            if (state.page == SettingsPage.Appearance) KitScreenWidth.List
            else KitScreenWidth.Reading,
        page = true,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = Space.S2.dp, bottom = Space.S6.dp),
                verticalArrangement =
                    Arrangement.spacedBy(
                        if (state.page == SettingsPage.Hub || state.page == SettingsPage.Appearance)
                            0.dp
                        else Space.S2.dp
                    ),
            ) {
                state.note?.let { NoteLine(it, actions) }
                content()
            }
            footer?.invoke()
        }
    }
}

@Composable
private fun NoteLine(note: SettingsNote, actions: SettingsActions) {
    KitNotice(
        noteWords(note),
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
        dismiss = KitLineAction(stringResource(Res.string.notifyDismiss), { actions.onNoteSeen() }),
    )
}

@Composable internal fun settingsTitle(): String = stringResource(Res.string.librarySettingsTitle)
