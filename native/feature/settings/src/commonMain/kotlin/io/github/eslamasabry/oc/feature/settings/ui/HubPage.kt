package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.ui.semantics.contentDescription
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearchEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitSearchNoMatch
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
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { actions.onQuery(it) } }
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
        large = true,
        search = {
            Box(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                KitSearchField(
                    label = stringResource(Res.string.settingsHubSearch),
                    onSearch = {},
                    state = field,
                    modifier = Modifier.testTag("settings-search"),
                )
            }
        },
    ) {
        if (hub.query.isBlank()) {
            // Looks pass, owner pick A (2026-11-05): the five groups as flat labelled sections, each with its key
            // rows and their current value at the end, so nobody opens a page only to read a setting. The rest of a
            // group is one "More" row away.
            hub.groups.forEach { group ->
                val rows = HUB_ROWS.getValue(group).filter { it in hub.searchable }
                val groupName = groupWords(group)
                KitSectionLabel(groupName, Modifier.testTag("settings-group-${group.name}"))
                if (group == SettingsGroupId.Server) {
                    KitRow(
                        title = state.hub.server?.name ?: stringResource(Res.string.settingsHubServerNone),
                        leading = KitRowLeading.Glyph(if (state.hub.server?.onPhone == true) KitIcons.phone else KitIcons.computer),
                        trailing =
                            state.hub.server?.let {
                                KitRowTrailing.Value(
                                    if (it.onPhone) stringResource(Res.string.settingsHubServerOnPhone)
                                    else stringResource(Res.string.settingsV3Connected)
                                )
                            } ?: KitRowTrailing.Chevron,
                        onClick = { actions.onOpen(SettingsPage.Group(group)) },
                    )
                }
                rows.forEach { row ->
                    KitRow(
                        title = rowWords(row),
                        modifier = Modifier.testTag(row.tag),
                        leading = KitRowLeading.Glyph(rowGlyph(row)),
                        trailing = rowValue(row, state)?.let { KitRowTrailing.Value(it) } ?: KitRowTrailing.Chevron,
                        onClick = { actions.onTarget(row) },
                    )
                }
                if (hub.searchable.count { it.group == group } > rows.size + (if (group == SettingsGroupId.Server) 1 else 0)) {
                    KitRow(
                        title = stringResource(Res.string.agentToolsTerminalMore),
                        modifier = Modifier.semantics { contentDescription = groupName },
                        leading = KitRowLeading.Glyph(KitIcons.more),
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.onOpen(SettingsPage.Group(group)) },
                    )
                }
            }
        } else if (hub.results.isEmpty()) {
            KitText(
                stringResource(Res.string.kitSearchNoMatch, hub.query),
                Modifier.padding(
                    horizontal = LocalKitTokens.current.gutter.dp,
                    vertical = Space.S4.dp,
                ),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        } else {
            KitRowGroup {
                hub.results.forEach { row ->
                    KitRow(
                        title = rowWords(row),
                        modifier = Modifier.testTag(row.tag),
                        supporting = groupWords(row.group),
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.onTarget(row) },
                    )
                }
            }
        }
    }
}

/** The rows each group shows on the hub (concept A): the ones people come to read or change most. */
private val HUB_ROWS: Map<SettingsGroupId, List<SettingsRowId>> =
    mapOf(
        SettingsGroupId.Server to listOf(SettingsRowId.ThisPhone),
        SettingsGroupId.Agent to listOf(SettingsRowId.ModelAndMode, SettingsRowId.Permissions, SettingsRowId.Usage),
        SettingsGroupId.Conversations to listOf(SettingsRowId.ShowReasoning, SettingsRowId.ShowTimestamps, SettingsRowId.Voice),
        SettingsGroupId.ThisApp to listOf(SettingsRowId.Appearance, SettingsRowId.Language, SettingsRowId.Notifications, SettingsRowId.Background),
        SettingsGroupId.Help to listOf(SettingsRowId.Guide, SettingsRowId.About),
    ).withDefault { emptyList() }

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
        SettingsRowId.PhraseChips -> KitIcons.textShort
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
    SettingsFrame(groupWords(page.id), { actions.onBack() }, state, actions, modifier) {
        val rows = state.group?.rows.orEmpty()
        KitRowGroup {
            rows.forEach { row ->
                val title = rowWords(row.id)
                val request = remember(row.id) { BringIntoViewRequester() }
                val focused = state.focusedRow == row.id
                LaunchedEffect(focused) { if (focused) request.bringIntoView() }
                val rowModifier =
                    Modifier.testTag(row.id.tag).bringIntoViewRequester(request).semantics {
                        selected = focused
                    }
                when (val door = row.door) {
                    is SettingsDoor.Switch ->
                        KitSwitchRow(title = title, checked = door.on, modifier = rowModifier) {
                            actions.onRow(row.id)
                        }
                    else ->
                        KitRow(
                            title = title,
                            modifier = rowModifier,
                            selected = focused,
                            supporting = rowValue(row.id, state),
                            trailing = KitRowTrailing.Chevron,
                            onClick = { actions.onRow(row.id) },
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
    KitRowGroup(label = stringResource(Res.string.settingsV3ServersBlock)) {
        state.hub.savedServers.forEach { server ->
            KitRow(
                title = server.name,
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
