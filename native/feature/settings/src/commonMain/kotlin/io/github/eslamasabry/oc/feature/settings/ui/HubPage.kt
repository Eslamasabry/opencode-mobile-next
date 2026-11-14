package io.github.eslamasabry.oc.feature.settings.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitReportPageScroll
import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsInside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsInsideEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearch
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearchEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneScreen
import io.github.eslamasabry.oc.ui.words.res.settingsHubDetailEmpty
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
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
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitLocaleTag
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearch
import io.github.eslamasabry.oc.ui.words.res.settingsHubServerLine
import io.github.eslamasabry.oc.ui.words.res.settingsHubServerNone
import io.github.eslamasabry.oc.ui.words.res.settingsPhraseChipsHiddenCount
import io.github.eslamasabry.oc.ui.words.res.settingsPhraseChipsShowHidden
import io.github.eslamasabry.oc.ui.words.res.settingsV3Connected
import io.github.eslamasabry.oc.ui.words.res.settingsV3NotConnected
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
    scroll: ScrollState,
) {
    // The window's own width decides (no sidebar sits around Settings), so the page composes in the normal pass
    // instead of being subcomposed during layout just to read a width.
    val window = LocalKitWindow.current
    Box(modifier.fillMaxSize()) {
        // F: settings_screen.dart:459: from expanded the hub is the list pane and the detail says to choose a group.
        if (Layout.showsDetail(window.cls, window.short, window.widthDp)) {
            // The first group fills the detail until another is chosen; with no group, "Choose a group".
            val first = state.firstGroup
            SettingsPanes(
                if (first != null) state.copy(group = first) else state,
                actions,
                selected = first?.let { SettingsPage.Group(it.id) },
                onExit = onExit,
                pushed = false,
                listScroll = scroll,
            )
        } else {
            SettingsFrame(
                title = settingsTitle(),
                onBack = onExit,
                state = state,
                actions = actions,
                scroll = scroll,
            ) {
                HubBody(state, actions)
            }
        }
    }
}

/**
 * Settings on a wide window (F: lib/ui/screens/settings_screen.dart:459 `KitScreen.twoPane`): the search and the
 * five groups in the list pane, the chosen group's rows in the detail pane, "Choose a group of settings" while none
 * is chosen. The two panes are Settings' root: Back only when the host can leave Settings.
 */
@Composable
private fun SettingsPanes(
    state: SettingsUi,
    actions: SettingsActions,
    selected: SettingsPage.Group?,
    onExit: (() -> Unit)?,
    pushed: Boolean = true,
    listScroll: ScrollState? = null,
) {
    val paneScroll = listScroll ?: rememberScrollState()
    KitPaneScreen(
        topBar = {
            KitTopBar(
                title = settingsTitle(),
                exit = if (onExit != null) KitTopBarExit.Back else KitTopBarExit.None,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = onExit,
                backBesideTitle = true,
            )
        },
        list = {
            Column(
                Modifier.fillMaxSize()
                    .testTag("settings-list-pane")
                    .verticalScroll(paneScroll.also { KitReportPageScroll(it) })
                    .padding(top = Space.S2.dp, bottom = Space.S6.dp)
            ) {
                HubBody(state, actions, selected = selected?.id, selectedPushed = pushed)
            }
        },
        detail =
            selected?.let { page ->
                {
                    Column(
                        Modifier.fillMaxSize()
                            .testTag("settings-detail-pane")
                            .verticalScroll(kitPageScrollState())
                            // F: lib/ui/screens/settings/settings_group_page.dart:399-401
                            .padding(top = Space.S5.dp, bottom = Space.S6.dp)
                    ) {
                        GroupBody(state, actions, page)
                    }
                }
            },
        emptyDetail = {
            KitStateView(
                KitStateKind.Calm,
                stringResource(Res.string.settingsHubDetailEmpty),
                Modifier.testTag("settings-detail-empty"),
                icon = KitIcons.search,
            )
        },
    )
}

/** Remembers what [rememberSearchEntries] resolved, and for which rows and language. */
private class SearchEntryCache {
    var rows: List<SettingsRowId>? = null
    var locale: String? = null
    var entries: List<SettingsSearchEntry> = emptyList()
}

/**
 * The search index in the person's words. The ~40 titles and acceptance words are resolved when the rows or the
 * language change, not on every pass the hub redraws; the same list is handed on until then.
 */
@Composable
private fun rememberSearchEntries(rows: List<SettingsRowId>): List<SettingsSearchEntry> {
    val locale = LocalKitLocaleTag.current
    val cache = remember { SearchEntryCache() }
    if (cache.rows != rows || cache.locale != locale) {
        cache.entries = rows.map { SettingsSearchEntry(it, rowWords(it), aliasWords(it)) }
        cache.rows = rows
        cache.locale = locale
    }
    return cache.entries
}

/** S6: the inside pages' words, resolved when the set or the language changes. */
@Composable
private fun rememberInsideEntries(inside: List<SettingsInside>): List<SettingsInsideEntry> {
    val locale = LocalKitLocaleTag.current
    val titles = inside.map { insideTitle(it) }
    val aliases = inside.map { insideAliases(it) }
    return remember(inside, locale, titles, aliases) {
        inside.mapIndexed { i, it -> SettingsInsideEntry(it, titles[i], aliases[i]) }
    }
}

@Composable
private fun ColumnScope.HubBody(
    state: SettingsUi,
    actions: SettingsActions,
    selected: SettingsGroupId? = null,
    /** The selected group is a page on the stack (chosen), not the wide hub's first-group default. */
    selectedPushed: Boolean = false,
) {
    val hub = state.hub
    val entries = rememberSearchEntries(hub.searchable)
    LaunchedEffect(entries) { actions.onSearchEntries(entries) }
    val insideEntries = rememberInsideEntries(hub.insideSearchable)
    LaunchedEffect(insideEntries) { actions.onInsideEntries(insideEntries) }
    val field = rememberTextFieldState(hub.query)
    // Back clears the search in the presenter; the field follows.
    LaunchedEffect(hub.query) {
        if (field.text.toString() != hub.query) field.edit { replace(0, length, hub.query) }
    }
    Box(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
        KitSearchField(
            label = stringResource(Res.string.settingsHubSearch),
            onSearch = actions::onQuery,
            onSubmit = { text ->
                val row = SettingsSearch.find(text, entries).firstOrNull()
                if (row != null) actions.onTarget(row)
                else SettingsSearch.findInside(text, insideEntries).firstOrNull()?.let(actions::onInside)
            },
            resultCount =
                (hub.results.size + hub.insideResults.size).takeIf { hub.query.isNotBlank() && it > 0 },
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
                    selected = group == selected,
                    // Flutter's hub rows are `roomy` (lib/ui/screens/settings_screen.dart:324): at least 64 dp with one
                    // line or two, so five doors read calm. KitRow has no roomy flag yet, so the minimum is asked here.
                    modifier =
                        Modifier.heightIn(min = HUB_ROW_HEIGHT.dp)
                            .testTag("settings-group-${group.name}"),
                    leading = if (large) null else KitRowLeading.Glyph(groupGlyph(group)),
                    supporting =
                        if (group == SettingsGroupId.Server) hubServerLine(state) else null,
                    trailing = if (large) KitRowTrailing.None else KitRowTrailing.Chevron,
                    onClick = {
                        // Wide: the chosen group replaces the one shown; the one shown stays as it is.
                        if (group != selected) {
                            if (selected != null && selectedPushed) actions.onBack()
                            actions.onOpen(SettingsPage.Group(group))
                        }
                    },
                )
            }
        }
    } else if (hub.results.isEmpty() && hub.insideResults.isEmpty()) {
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
            hub.insideResults.forEach { inside ->
                KitRow(
                    title = insideTitle(inside),
                    modifier = Modifier.testTag(inside.tag),
                    supporting = groupWords(inside.group),
                    leading = KitRowLeading.Glyph(insideGlyph(inside)),
                    trailing = KitRowTrailing.Chevron,
                    onClick = { actions.onInside(inside) },
                )
            }
        }
    }
}

/** A roomy index row's height (Dart `KitRow.roomyHeight`). */
internal const val HUB_ROW_HEIGHT = 64 // F: lib/ui/kit/kit_row.dart:121

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
        // P-028: the phone's server has one name everywhere, "This phone", as on home.
        if (server.onPhone) stringResource(Res.string.settingsV3ThisPhone) else server.name
    return stringResource(
        Res.string.settingsHubServerLine,
        where,
        stringResource(Res.string.settingsV3Connected),
    )
}

/** Each row's Phosphor glyph (the Flutter Settings icons). */
private fun rowGlyph(row: SettingsRowId) =
    when (row) {
        SettingsRowId.Servers -> KitIcons.database // F: lib/ui/search/search_index/hub_rows.dart:31
        SettingsRowId.ThisPhone -> KitIcons.phone
        SettingsRowId.ModelAndMode -> KitIcons.model
        SettingsRowId.Providers -> KitIcons.cloud
        SettingsRowId.Usage -> KitIcons.usage
        SettingsRowId.Tools -> KitIcons.tools
        SettingsRowId.Agents -> KitIcons.agent
        SettingsRowId.AiTeam -> KitIcons.kanban
        SettingsRowId.ShowReasoning -> KitIcons.idea
        SettingsRowId.ShowTimestamps -> KitIcons.clock
        SettingsRowId.EdgeDock -> KitIcons.layers
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
        // P-031: each Server row has its own glyph (Flutter hub_rows: capabilities is the checklist).
        SettingsRowId.ServerCapabilities -> KitIcons.checklist
        SettingsRowId.ServerConfiguration -> KitIcons.dataObject
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
    onExit: (() -> Unit)? = null,
    hubScroll: ScrollState? = null,
) {
    val window = LocalKitWindow.current
    Box(modifier.fillMaxSize()) {
        if (Layout.showsDetail(window.cls, window.short, window.widthDp)) {
            SettingsPanes(state, actions, selected = page, onExit = onExit, listScroll = hubScroll)
        } else {
            SettingsFrame(groupWords(page.id), { actions.onBack() }, state, actions) {
                GroupBody(state, actions, page)
            }
        }
    }
}

@Composable
private fun ColumnScope.GroupBody(
    state: SettingsUi,
    actions: SettingsActions,
    page: SettingsPage.Group,
) {
    var languageOpen by rememberSaveable(page.id) { mutableStateOf(false) }
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
                        language: io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
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
    // P-031: the saved servers are listed once, behind Servers (each opens its own page there); a second list
    // here, under a sentence for a label, repeated them.
}

@Composable
internal fun androidx.compose.foundation.layout.ColumnScope.SavedServersBody(
    state: SettingsUi,
    actions: SettingsActions,
    section: SettingsOutside,
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
    KitRowGroup(leadingTiles = true) {
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
        SettingsRowId.PhraseChips ->
            stringResource(
                if (state.phrases?.enabled ?: state.phraseChips) Res.string.settingsV3On
                else Res.string.settingsV3Off
            )
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
            if (!notify.enabled) stringResource(Res.string.settingsV3Off)
            else if (different > 0)
                pluralStringResource(Res.plurals.settingsV3DifferentOn, different, different)
            else stringResource(Res.string.settingsV3On)
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
