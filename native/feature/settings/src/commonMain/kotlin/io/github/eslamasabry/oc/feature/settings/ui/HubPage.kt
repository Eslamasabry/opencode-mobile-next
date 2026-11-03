package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearchEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitSearchNoMatch
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearch
import io.github.eslamasabry.oc.ui.words.res.settingsHubServerLine
import io.github.eslamasabry.oc.ui.words.res.settingsHubServerNone
import io.github.eslamasabry.oc.ui.words.res.settingsHubServerOnPhone
import io.github.eslamasabry.oc.ui.words.res.settingsV3Connected
import io.github.eslamasabry.oc.ui.words.res.settingsV3Off
import io.github.eslamasabry.oc.ui.words.res.settingsV3On
import org.jetbrains.compose.resources.stringResource

/** I01: the search field, then the five groups (only Server has a value line); with a query, the matching rows. */
@Composable
internal fun HubPage(state: SettingsUi, actions: SettingsActions, modifier: Modifier, onExit: (() -> Unit)?) {
    val hub = state.hub
    val entries = hub.searchable.map { SettingsSearchEntry(it, rowWords(it), aliasWords(it)) }
    LaunchedEffect(entries) { actions.onSearchEntries(entries) }
    val field = rememberTextFieldState(hub.query)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { actions.onQuery(it) } }
    // Back clears the search in the presenter; the field follows.
    LaunchedEffect(hub.query) { if (field.text.toString() != hub.query) field.edit { replace(0, length, hub.query) } }
    SettingsFrame(
        title = settingsTitle(),
        onBack = onExit,
        state = state,
        actions = actions,
        modifier = modifier,
        // Flutter parity: the search box with its glyph and the words inside it, not a labelled form field.
        search = {
            KitSearchField(
                label = stringResource(Res.string.settingsHubSearch),
                onSearch = {}, // the field state above already reports every edit to the presenter
                modifier = Modifier.testTag("settings-search"),
                state = field,
            )
        },
    ) {
        if (hub.query.isBlank()) {
            KitRowGroup {
                hub.groups.forEach { group ->
                    KitRow(
                        title = groupWords(group),
                        modifier = Modifier.testTag("settings-group-${group.name}"),
                        supporting = if (group == SettingsGroupId.Server) serverLine(state) else null,
                        leading = groupGlyph(group),
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.onOpen(SettingsPage.Group(group)) },
                    )
                }
            }
        } else if (hub.results.isEmpty()) {
            KitText(
                stringResource(Res.string.kitSearchNoMatch, hub.query),
                Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S4.dp),
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
                        leading = rowGlyph(row),
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.onRow(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun serverLine(state: SettingsUi): String {
    val server = state.hub.server ?: return stringResource(Res.string.settingsHubServerNone)
    val where = if (server.onPhone) stringResource(Res.string.settingsHubServerOnPhone) else stringResource(Res.string.settingsV3Connected)
    return stringResource(Res.string.settingsHubServerLine, server.name, where)
}

/** I02: the group's rows; switch rows flip in place, the rest open their page or hand off to the host. */
@Composable
internal fun GroupPage(state: SettingsUi, actions: SettingsActions, page: SettingsPage.Group, modifier: Modifier) {
    SettingsFrame(groupWords(page.id), { actions.onBack() }, state, actions, modifier) {
        val rows = state.group?.rows.orEmpty()
        KitRowGroup {
            rows.forEach { row ->
                val title = rowWords(row.id)
                when (val door = row.door) {
                    is SettingsDoor.Switch ->
                        KitSwitchRow(
                            title = title,
                            checked = door.on,
                            modifier = Modifier.testTag(row.id.tag),
                            leading = rowGlyph(row.id),
                        ) { actions.onRow(row.id) }
                    else ->
                        KitRow(
                            title = title,
                            modifier = Modifier.testTag(row.id.tag),
                            leading = rowGlyph(row.id),
                            // Flutter parity: the row says its current value where the page has one.
                            trailing = rowValue(row.id, state)?.let { KitRowTrailing.Value(it) } ?: KitRowTrailing.Chevron,
                            onClick = { actions.onRow(row.id) },
                        )
                }
            }
        }
    }
}

/** The current value a group row shows before its chevron (Flutter `KitRowValue`); null = the row has none. */
@Composable
private fun rowValue(row: SettingsRowId, state: SettingsUi): String? =
    when (row) {
        SettingsRowId.Appearance -> modeWords(state.appearance.mode)
        SettingsRowId.Language -> languageWords(state.language)
        SettingsRowId.Notifications ->
            state.notifications?.let {
                stringResource(if (it.blocked) Res.string.settingsV3Off else Res.string.settingsV3On)
            }
        SettingsRowId.Background -> state.notifications?.background?.let { backgroundWords(it) }
        else -> null
    }
