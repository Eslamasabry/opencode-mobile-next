package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.aboutTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdatesTitle
import io.github.eslamasabry.oc.ui.words.res.e7AppearanceTitle
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.monitorQuietEnd
import io.github.eslamasabry.oc.ui.words.res.monitorQuietStart
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.librarySettingsTitle
import io.github.eslamasabry.oc.ui.words.res.notifyDismiss
import io.github.eslamasabry.oc.ui.words.res.settingsHubPrivacyRow
import io.github.eslamasabry.oc.ui.words.res.settingsHubVoice
import io.github.eslamasabry.oc.ui.words.res.settingsV3Language
import io.github.eslamasabry.oc.ui.words.res.settingsV3Notifications
import org.jetbrains.compose.resources.stringResource

/**
 * Settings (plan 07 §5 I): the page on top of [SettingsUi.stack], arranged from kit parts only. Back in the bar walks
 * the stack; at the hub it calls [onExit] (the host closes Settings). The host also routes system Back to
 * [SettingsActions.onBack] first.
 */
@Composable
public fun SettingsScreen(state: SettingsUi, actions: SettingsActions, modifier: Modifier = Modifier, onExit: (() -> Unit)? = null) {
    val back: () -> Unit = { actions.onBack() }
    // Back from Android's notification settings: the blocked notice must say what is true now (Flutter: on resume).
    LifecycleResumeEffect(actions) {
        actions.onResume()
        onPauseOrDispose {}
    }
    when (val page = state.page) {
        SettingsPage.Hub -> HubPage(state, actions, modifier, onExit)
        is SettingsPage.Group -> GroupPage(state, actions, page, modifier)
        SettingsPage.Appearance ->
            SettingsFrame(stringResource(Res.string.e7AppearanceTitle), back, state, actions, modifier) {
                AppearanceBody(state.appearance, actions)
            }
        SettingsPage.Voice ->
            SettingsFrame(stringResource(Res.string.settingsHubVoice), back, state, actions, modifier) {
                state.voice?.let { VoiceBody(it, actions) }
            }
        SettingsPage.Notifications ->
            SettingsFrame(stringResource(Res.string.settingsV3Notifications), back, state, actions, modifier) {
                state.notifications?.let { NotificationsBody(it, actions) }
            }
        SettingsPage.Updates ->
            SettingsFrame(stringResource(Res.string.appUpdatesTitle), back, state, actions, modifier) {
                state.updates?.let { UpdatesBody(it, actions) }
            }
        SettingsPage.About ->
            SettingsFrame(stringResource(Res.string.aboutTitle), back, state, actions, modifier) { AboutBody(state.about, actions) }
        SettingsPage.Privacy ->
            SettingsFrame(stringResource(Res.string.settingsHubPrivacyRow), back, state, actions, modifier) {
                PrivacyBody(state.privacy, actions)
            }
        SettingsPage.WhatWeCollect ->
            SettingsFrame(PendingWords.WHAT_WE_COLLECT, back, state, actions, modifier) {
                state.privacy?.let { WhatWeCollectBody(it, actions) }
            }
        SettingsPage.Language ->
            SettingsFrame(stringResource(Res.string.settingsV3Language), back, state, actions, modifier) {
                LanguageBody(state.language, actions)
            }
        SettingsPage.ReportProblem ->
            SettingsFrame(stringResource(Res.string.kitReportProblem), back, state, actions, modifier) {
                ReportBody(state.report, actions)
            }
        is SettingsPage.QuietTime ->
            SettingsFrame(
                stringResource(if (page.start) Res.string.monitorQuietStart else Res.string.monitorQuietEnd),
                back, state, actions, modifier,
            ) {
                QuietTimeBody(page.start, state.notifications, actions)
            }
    }
}

/** The settings page frame: the bar with Back, the one save-failed note, then a scrolling column of kit parts. */
@Composable
internal fun SettingsFrame(
    title: String,
    onBack: (() -> Unit)?,
    state: SettingsUi,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    search: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    KitScreen(
        modifier = modifier,
        topBar = {
            KitTopBar(
                title = title,
                exit = if (onBack != null) KitTopBarExit.Back else KitTopBarExit.None,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = onBack,
            )
        },
        search = search,
        width = KitScreenWidth.Reading,
        page = true,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Space.S6.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
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

@Composable
internal fun settingsTitle(): String = stringResource(Res.string.librarySettingsTitle)
