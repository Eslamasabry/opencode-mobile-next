package io.github.eslamasabry.oc.feature.settings.ui

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
        search = {
            KitField(
                field,
                stringResource(Res.string.settingsHubSearch),
                Modifier.testTag("settings-search"),
            )
        },
    ) {
        if (hub.query.isBlank()) {
            KitRowGroup {
                hub.groups.forEach { group ->
                    KitRow(
                        title = groupWords(group),
                        modifier = Modifier.testTag("settings-group-${group.name}"),
                        supporting =
                            if (group == SettingsGroupId.Server) serverLine(state) else null,
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

@Composable
private fun serverLine(state: SettingsUi): String {
    val server = state.hub.server ?: return stringResource(Res.string.settingsHubServerNone)
    val where =
        if (server.onPhone) stringResource(Res.string.settingsHubServerOnPhone)
        else stringResource(Res.string.settingsV3Connected)
    return stringResource(Res.string.settingsHubServerLine, server.name, where)
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
