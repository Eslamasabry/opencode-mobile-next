package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearchEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitIconSwitchRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearch
import io.github.eslamasabry.oc.ui.words.res.settingsHubServerLine
import io.github.eslamasabry.oc.ui.words.res.settingsHubServerNone
import io.github.eslamasabry.oc.ui.words.res.settingsHubServerOnPhone
import io.github.eslamasabry.oc.ui.words.res.settingsPhraseChipsHiddenCount
import io.github.eslamasabry.oc.ui.words.res.settingsPhraseChipsShowHidden
import io.github.eslamasabry.oc.ui.words.res.settingsV3Connected
import io.github.eslamasabry.oc.ui.words.res.settingsV3NotConnected
import io.github.eslamasabry.oc.ui.words.res.settingsV3ServersBlock
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * I01: the search field, then the five groups (only Server has a value line); with a query, the
 * matching rows.
 */
@Composable
internal fun HubPage(
    state: SettingsUi,
    actions: SettingsActions,
    modifier: Modifier,
    onExit: (() -> Unit)?,
) {
    val hub = state.hub
    val entries = hub.searchable.map { SettingsSearchEntry(it, rowWords(it), aliasWords(it)) }
    LaunchedEffect(entries) { actions.onSearchEntries(entries) }
    val field = rememberTextFieldState(hub.query)
    // Back clears the search in the presenter; the field follows.
    LaunchedEffect(hub.query) {
        if (field.text.toString() != hub.query) field.edit { replace(0, length, hub.query) }
    }
    SettingsFrame(
        title = settingsTitle(),
        onBack = onExit,
        state = state,
        actions = actions,
        modifier = modifier,
    ) {
        Box(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
            KitSearchField(
                label = stringResource(Res.string.settingsHubSearch),
                onSearch = actions::onQuery,
                onSubmit = { hub.results.firstOrNull()?.let(actions::onTarget) },
                resultCount = hub.results.size.takeIf { hub.query.isNotBlank() && it > 0 },
                prominent = true,
                state = field,
                modifier = Modifier.testTag("settings-search"),
            )
        }
        Spacer(Modifier.height(Space.S4.dp))
        if (hub.query.isBlank()) {
            val large =
                LocalDensity.current.fontScale * 10 >= // F: lib/ui/screens/settings_screen.dart:290
                    18 // F: lib/ui/screens/settings_screen.dart:290
            KitRowGroup(leadingTiles = true) {
                hub.groups.forEach { group ->
                    KitRow(
                        title = groupWords(group),
                        modifier =
                            Modifier.heightIn(min = LocalKitTokens.current.rowHeightTwo.dp)
                                .testTag("settings-group-${group.name}"),
                        leading = if (large) null else KitRowLeading.Glyph(groupGlyph(group)),
                        supporting =
                            if (group == SettingsGroupId.Server) hubServerLine(state) else null,
                        trailing = if (large) KitRowTrailing.None else KitRowTrailing.Chevron,
                        onClick = { actions.onOpen(SettingsPage.Group(group)) },
                    )
                }
            }
        } else if (hub.results.isEmpty()) {
            KitSearchNoMatch(
                query = hub.query,
                onClear = { actions.onQuery("") },
            )
        } else {
            KitRowGroup {
                hub.results.forEach { row ->
                    KitRow(
                        title = rowWords(row),
                        modifier = Modifier.testTag(row.tag),
                        supporting = groupWords(row.group),
                        leading = KitRowLeading.Glyph(rowGlyph(row)),
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.onTarget(row) },
                    )
                }
            }
        }
    }
}

/** The five hub glyphs, in Flutter's group order. */
private fun groupGlyph(group: SettingsGroupId) =
    when (group) {
        SettingsGroupId.Server -> KitIcons.server
        SettingsGroupId.Agent -> KitIcons.agent
        SettingsGroupId.Conversations -> KitIcons.chat
        SettingsGroupId.ThisApp -> KitIcons.phone
        SettingsGroupId.Help -> KitIcons.question
    }

@Composable
private fun hubServerLine(state: SettingsUi): String {
    val server = state.hub.server
    val saved = state.hub.savedServers
    if (server == null)
        return if (saved.isEmpty()) stringResource(Res.string.settingsHubServerNone)
        else pluralStringResource(Res.plurals.settingsV3ServersCount, saved.size, saved.size)
    val where =
        if (server.onPhone) stringResource(Res.string.settingsHubServerOnPhone) else server.name
    return stringResource(
        Res.string.settingsHubServerLine,
        where,
        stringResource(Res.string.settingsV3Connected),
    )
}

/** Each row's Phosphor glyph (the Flutter Settings icons). */
private fun rowGlyph(row: SettingsRowId) =
    when (row) {
        SettingsRowId.Servers -> KitIcons.server
        SettingsRowId.ThisPhone -> KitIcons.phone
        SettingsRowId.ModelAndMode -> KitIcons.model
        SettingsRowId.Providers -> KitIcons.cloud
        SettingsRowId.Usage -> KitIcons.usage
        SettingsRowId.Tools -> KitIcons.tools
        SettingsRowId.Agents -> KitIcons.agent
        SettingsRowId.ShowReasoning -> KitIcons.idea
        SettingsRowId.ShowTimestamps -> KitIcons.clock
        SettingsRowId.PhraseChips -> KitIcons.chat
        SettingsRowId.DefaultShell -> KitIcons.terminal
        SettingsRowId.Voice -> KitIcons.mic
        SettingsRowId.Permissions -> KitIcons.shield
        SettingsRowId.Notifications -> KitIcons.notificationImportant
        SettingsRowId.Background -> KitIcons.batteryCharging
        SettingsRowId.Appearance -> KitIcons.appearance
        SettingsRowId.Language -> KitIcons.globe
        SettingsRowId.Privacy -> KitIcons.privacy
        SettingsRowId.Updates -> KitIcons.systemDownload
        SettingsRowId.Storage -> KitIcons.storage
        SettingsRowId.Guide -> KitIcons.guide
        SettingsRowId.Tips -> KitIcons.idea
        SettingsRowId.Capabilities -> KitIcons.checklist
        SettingsRowId.ReportProblem -> KitIcons.bug
        SettingsRowId.About -> KitIcons.info
        else -> KitIcons.settings
    }

/**
 * I02: the group's rows; switch rows flip in place, the rest open their page or hand off to the
 * host.
 */
@Composable
internal fun GroupPage(
    state: SettingsUi,
    actions: SettingsActions,
    page: SettingsPage.Group,
    modifier: Modifier,
) {
    var languageOpen by remember { mutableStateOf(false) }
    SettingsFrame(groupWords(page.id), { actions.onBack() }, state, actions, modifier) {
        if (languageOpen) {
            KitSheet(
                visible = true,
                onDismissRequest = { languageOpen = false },
                title = stringResource(Res.string.settingsV3Language),
            ) {
                LanguageBody(
                    state.language,
                    object : SettingsActions by actions {
                        override fun onLanguage(
                            language:
                                io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
                        ) {
                            actions.onLanguage(language)
                            languageOpen = false
                        }
                    },
                )
            }
        }
        val rows = state.group?.rows.orEmpty()
        KitRowGroup(leadingTiles = true) {
            rows.forEach { row ->
                val title = rowWords(row.id)
                val request = remember(row.id) { BringIntoViewRequester() }
                val focused = state.focusedRow == row.id
                LaunchedEffect(focused) { if (focused) request.bringIntoView() }
                val rowModifier =
                    Modifier.testTag(row.id.tag).bringIntoViewRequester(request).semantics {
                        selected = focused
                    }
                when {
                    row.id == SettingsRowId.PhraseChips ->
                        KitIconSwitchRow(
                            title = stringResource(Res.string.settingsPhraseChipsTitle),
                            icon = KitIcons.chat,
                            checked = state.phrases?.enabled ?: state.phraseChips,
                            modifier = rowModifier,
                            onCheckedChange = actions::onPhraseChips,
                        )
                    row.door is SettingsDoor.Switch ->
                        KitIconSwitchRow(
                            title = title,
                            icon = rowGlyph(row.id),
                            checked = row.door.on,
                            modifier = rowModifier,
                            onCheckedChange = { actions.onRow(row.id) },
                        )
                    else ->
                        KitRow(
                            title = title,
                            modifier = rowModifier,
                            selected = focused,
                            leading = KitRowLeading.Glyph(rowGlyph(row.id)),
                            trailing =
                                rowValue(row.id, state)?.let { KitRowTrailing.Value(it) }
                                    ?: KitRowTrailing.Chevron,
                            onClick = {
                                if (row.id == SettingsRowId.Language) languageOpen = true
                                else actions.onRow(row.id)
                            },
                        )
                }
            }
        }
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
        if (page.id == SettingsGroupId.Server && state.hub.savedServers.size > 1) {
            SavedServersBody(state, actions, null)
        }
    }
}

@Composable
internal fun androidx.compose.foundation.layout.ColumnScope.SavedServersBody(
    state: SettingsUi,
    actions: SettingsActions,
    section: io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside?,
) {
    val note =
        when (section) {
            SettingsOutside.ModelAndMode -> Res.string.settingsV3ModelByServerNote
            SettingsOutside.Providers -> Res.string.settingsV3ProvidersByServerNote
            SettingsOutside.Tools -> Res.string.settingsV3ToolsByServerNote
            SettingsOutside.DefaultShell -> Res.string.settingsV3ShellByServerNote
            SettingsOutside.ReadState -> Res.string.settingsV3ReadStateByServerNote
            else -> null
        }
    note?.let { KitGroupNote(stringResource(it)) }
    KitRowGroup(
        leadingTiles = true,
        label = if (section == null) stringResource(Res.string.settingsV3ServersBlock) else null,
    ) {
        state.hub.savedServers.forEach { server ->
            KitRow(
                title = server.name,
                leading =
                    KitRowLeading.Glyph(if (server.onPhone) KitIcons.phone else KitIcons.server),
                supporting =
                    stringResource(
                        if (server.connected) Res.string.settingsV3Connected
                        else Res.string.settingsV3NotConnected
                    ),
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onSavedServer(server.id, section) },
            )
        }
    }
}

@Composable
private fun rowValue(row: SettingsRowId, state: SettingsUi): String? {
    val servers = state.hub.savedServers
    return when (row) {
        SettingsRowId.Language -> languageWords(state.language)
        SettingsRowId.Appearance -> modeWords(state.appearance.mode)
        SettingsRowId.Background -> state.notifications?.background?.let { backgroundWords(it) }
        SettingsRowId.Servers ->
            when {
                servers.isEmpty() -> null
                servers.size > 1 ->
                    pluralStringResource(
                        Res.plurals.settingsV3ServersCount,
                        servers.size,
                        servers.size,
                    )
                servers.single().connected ->
                    stringResource(Res.string.settingsV3ServerConnected, servers.single().name)
                else -> servers.single().name
            }
        SettingsRowId.ThisPhone ->
            stringResource(
                when {
                    servers.any { it.onPhone && it.connected } -> Res.string.settingsV3PhoneRunning
                    servers.any { it.onPhone } -> Res.string.settingsV3PhoneInstalled
                    else -> Res.string.settingsV3PhoneSetUp
                }
            )
        SettingsRowId.Notifications -> {
            val notify = state.notifications ?: return null
            val different = notify.profiles.count { it.enabled && !it.notifications }
            if (different > 0)
                pluralStringResource(Res.plurals.settingsV3DifferentOn, different, different)
            else
                stringResource(
                    if (notify.questions || notify.finishedWork) Res.string.settingsV3On
                    else Res.string.settingsV3Off
                )
        }
        SettingsRowId.DefaultShell,
        SettingsRowId.ModelAndMode,
        SettingsRowId.Providers,
        SettingsRowId.Tools,
        SettingsRowId.Permissions ->
            if (servers.size > 1) stringResource(Res.string.settingsV3ByServer) else null
        else -> null
    }
}
