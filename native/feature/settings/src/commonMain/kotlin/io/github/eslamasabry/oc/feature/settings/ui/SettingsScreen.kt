package io.github.eslamasabry.oc.feature.settings.ui

import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.eslamasabry.oc.ui.kit.frame.KitFloatingBar
import io.github.eslamasabry.oc.ui.kit.frame.KitPinnedFootBody
import androidx.compose.foundation.layout.PaddingValues
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.aboutAllLicences
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
    // The hub's scroll lives above the page switch, so coming back from a group finds the hub where it was left.
    val hubScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    when (val page = state.page) {
        SettingsPage.Hub -> HubPage(state, actions, modifier, onExit, hubScroll)
        is SettingsPage.Group -> GroupPage(state, actions, page, modifier, onExit, hubScroll)
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
        SettingsPage.Appearance -> {
            // The tab the page shows, shared with the tour's bar at its foot.
            // A question asked for from outside (a search result, a tip) opens on its group's tab.
            val asked = state.appearance.question?.group
            val appearanceTab =
                androidx.compose.runtime.saveable.rememberSaveable(asked) {
                    androidx.compose.runtime.mutableIntStateOf(appearanceTabs(state.appearance).indexOf(asked).coerceAtLeast(0))
                }
            // A new tab opens at its top: the guide's Next never lands mid-way down the next group.
            val appearanceScroll = rememberScrollState()
            LaunchedEffect(appearanceTab.intValue) { appearanceScroll.scrollTo(0) }
            SettingsFrame(
                stringResource(Res.string.e7AppearanceTitle),
                back,
                state,
                actions,
                modifier,
                scroll = appearanceScroll,
                pinned = { AppearanceTabRow(state.appearance, appearanceTab) },
                footer =
                    if (state.appearance.guide?.done == false) {
                        { AppearanceGuideNext(state.appearance, actions, appearanceTab) }
                    } else null,
            ) {
                AppearanceBody(state.appearance, actions, appearanceTab)
            }
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
        SettingsPage.Licences ->
            SettingsFrame(
                stringResource(Res.string.aboutAllLicences),
                back,
                state,
                actions,
                modifier,
            ) {
                NoticesBody(file = "files/LICENCES.md")
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
                    ?: KitStateView(kind = KitStateKind.Loading, title = stringResource(Res.string.migrationStorageTitle))
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
    /** A page's own navigation (the Appearance tabs): pinned under the bar, whole at every scroll position. */
    pinned: (@Composable () -> Unit)? = null,
    scroll: ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pageScroll = scroll ?: rememberScrollState()
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
        edges = KitScreenEdges.Bleed,
        // Glass step 4: where the lens is drawn the page scrolls on under its bar, a floating liquid capsule; the kit
        // records the page for it. Everywhere else the bar is pinned as before.
        // A pinned head cannot sit under a floating bar (the page would scroll its first rows beneath it), so a page
        // with one keeps the pinned bar.
        floatingBar = if (pinned == null) KitFloatingBar() else null,
    ) {
        KitPinnedFootBody(
            scroll = pageScroll,
            foot = footer,
            head = pinned,
            contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
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
