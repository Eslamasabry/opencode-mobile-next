import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart' show ProviderScope;

import '../../api/product_repository.dart';
import '../../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../../builtin/thermal_guard_teams.dart' show thermalGuardSlotProvider;
import '../../domain/settings_search.dart';
import '../../domain/settings_search_catalog.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../state/phone_host.dart' show PhoneHostKind;
import '../../termux/bridge.dart' show TermuxBridge;
import '../../voice/model_manager.dart';
import '../../voice/notices.dart';
import '../../voice/voice_ui.dart';
import '../app_iconography.dart';
import '../desktop/desktop_interaction.dart';
import '../desktop/shortcuts.dart';
import '../screens/about_screen.dart';
import '../screens/agent_account_screen.dart';
import '../screens/app_diagnostics_screen.dart';
import '../screens/automation_settings_screen.dart';
import '../screens/capabilities_screen.dart';
import '../screens/demo_screen.dart';
import '../screens/global_sessions_screen.dart';
import '../screens/guide_screen.dart';
import '../screens/keep_running_screen.dart';
import '../screens/library_screen.dart';
import '../screens/local_agent_screen.dart';
import '../screens/project_hub_screen.dart';
import '../screens/saved_permissions_screen.dart';
import '../screens/server_capabilities_screen.dart';
import '../screens/session_import_screen.dart';
import '../screens/settings/ai_setup_screen.dart';
import '../screens/settings_screen.dart';
import '../screens/servers_screen.dart' show ServersRouteRequest;
import '../screens/tailscale_setup_screen.dart';
import '../screens/tools_hub_screen.dart';
import '../screens/team/team_agents_screen.dart';
import '../screens/team/team_page.dart';
import '../screens/team/project_demo_screen.dart';
import '../screens/team/projects/team_project_editors.dart';
import '../screens/termux_processes_screen.dart';
import '../screens/phone_setup/phone_setup_routes.dart';
import '../screens/termux_storage_screen.dart';
import '../screens/this_phone_screen.dart' show ThisPhoneScreen, openThisPhone;
import '../screens/usage_hub_screen.dart';
import '../widgets/phone_server_card.dart' show serverDisplayName;
import '../widgets/pickers.dart';
import '../widgets/product_states.dart';
import '../kit/kit_arrival.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_dialog.dart';
import '../kit/kit_page_route.dart';

/// What a result is, which decides the header it is listed under.
enum SearchEntryKind {
  /// A row of the Settings hub. The hub draws these itself, with live status.
  hubRow,

  /// Something inside a second-level settings screen ("Quiet hours").
  insideSettings,

  /// A place in the app: a tab, a Project tool, AI Team.
  destination,
}

/// Everything a gate or an open-callback may read. Kept in one object so a
/// test can describe a connection without building the app around it.
class SearchScope {
  SearchScope({
    required this.controller,
    PlatformCapabilities? platform,
    bool? desktop,
    bool? hasTeam,
    this.hasShell = false,
    this.thermalGuard = false,
  }) : platform = platform ?? platformCapabilities,
       desktop = desktop ?? desktopInteractions,
       hasTeam = hasTeam ?? controller.orchestration != null;

  /// Reads the shell from [context]: the tabs can only be reached through the
  /// shell's signal bus, so without one the tab results are absent. The heat
  /// guard is read from the app's providers, when there are any.
  factory SearchScope.of(
    BuildContext context,
    ConnectionController controller,
  ) => SearchScope(
    controller: controller,
    hasShell: AppShortcutScope.read(context) != null,
    thermalGuard: _thermalGuardIn(context),
  );

  static bool _thermalGuardIn(BuildContext context) {
    try {
      return ProviderScope.containerOf(
            context,
            listen: false,
          ).read(thermalGuardSlotProvider).value !=
          null;
    } on StateError {
      return false;
    }
  }

  final ConnectionController controller;
  final PlatformCapabilities platform;
  final bool desktop;
  final bool hasShell;

  /// The AI Team plugin is set up for the connected server.
  final bool hasTeam;

  /// The heat guard runs on this phone, so Keep running shows its switch.
  final bool thermalGuard;

  ServerCapabilities get capabilities => controller.capabilities;
}

typedef SearchGate = bool Function(SearchScope scope);
typedef SearchOpen =
    Future<void> Function(BuildContext context, SearchScope scope);

bool _always(SearchScope _) => true;

/// One findable thing. Plain data, so the conversation action registry
/// (UX plan phase 4) can add its own entries to the same list.
class SearchEntry {
  const SearchEntry({
    required this.id,
    required this.title,
    required this.keywords,
    required this.kind,
    required this.icon,
    required this.open,
    this.gate = _always,
    this.serverGate,
    this.group,
    this.parent,
    this.pages = const [],
    this.target,
  });

  /// Stable. A hub row's id is its widget key.
  final String id;
  final String title;

  /// Extra words a person might type, including the retired nouns and the
  /// titles of the [pages] this door leads to.
  final String keywords;
  final SearchEntryKind kind;
  final IconData icon;

  /// Present or absent, never disabled (rule 7).
  final SearchGate gate;

  /// The part of [gate] that depends on what the connected server offers.
  /// A hub row this hides is counted on its group's one muted line ("2
  /// settings aren't available on this server · Why") instead of vanishing
  /// without a word (target-ia §1.3). Null: the server never hides it.
  final SearchGate? serverGate;

  /// Whether the connected server, not this device, hides this entry.
  bool hiddenByServer(SearchScope scope) =>
      !gate(scope) && serverGate != null && !serverGate!(scope);
  final SearchOpen open;

  /// The hub group a [SearchEntryKind.hubRow] is listed in.
  final SettingsGroup? group;

  /// Title of the screen that holds an [SearchEntryKind.insideSettings] entry.
  final String? parent;

  /// UI ledger page ids this entry opens or is the door to. The coverage test
  /// joins on these.
  final List<String> pages;

  /// Where the result lands: the page, and the section or row inside it a
  /// result that means one part scrolls to. Stable ids, not routes, so the
  /// entry survives the page moving. Absent: the first of [pages].
  final SettingsSearchTarget? target;

  /// This entry as the shared matcher reads it (static words only).
  SettingsSearchDocument get document => SettingsSearchDocument(
    id: id,
    title: title,
    parent: parent ?? '',
    aliases: keywords,
    target:
        target ??
        SettingsSearchTarget(pageId: pages.isEmpty ? id : pages.first),
  );

  /// Whether the shared matcher finds this entry for [query]: every word, by
  /// word, prefix or one typo.
  bool matches(String query) =>
      SettingsSearchIndex([document]).search(query).isNotEmpty;

  SearchEntry _withMoreWords(String? words) => words == null || words.isEmpty
      ? this
      : SearchEntry(
          id: id,
          title: title,
          keywords: '$keywords $words',
          kind: kind,
          icon: icon,
          open: open,
          gate: gate,
          serverGate: serverGate,
          group: group,
          parent: parent,
          pages: pages,
          target: target,
        );
}

/// The gated index: every entry the current connection and device can open.
/// Its keywords carry every language's words for the same entry, so an
/// Arabic word finds a setting while the app is in English and back.
List<SearchEntry> searchIndex(AppLocalizations l10n, SearchScope scope) {
  final otherWords = _otherLanguagesWords(l10n);
  return [
    for (final entry in allSearchEntries(l10n))
      if (entry.gate(scope)) entry._withMoreWords(otherWords[entry.id]),
  ];
}

/// Per entry id, its title, keywords and parent in every other language.
/// The words are static copy, so each language is read once.
Map<String, String> _otherLanguagesWords(AppLocalizations l10n) =>
    _otherWordsByLanguage.putIfAbsent(l10n.localeName, () {
      final words = <String, List<String>>{};
      for (final locale in AppLocalizations.supportedLocales) {
        final other = lookupAppLocalizations(locale);
        if (other.localeName == l10n.localeName) continue;
        for (final entry in allSearchEntries(other)) {
          (words[entry.id] ??= []).add(
            '${entry.title} ${entry.keywords} ${entry.parent ?? ''}',
          );
        }
      }
      return {for (final e in words.entries) e.key: e.value.join(' ')};
    });

final _otherWordsByLanguage = <String, Map<String, String>>{};

/// The phone's own OpenCode runs in Termux under a saved server, so it can
/// be restarted after a crash (This phone's recovery row).
bool _managedRecovery(SearchScope scope) =>
    scope.platform.supportsTermux &&
    scope.controller.store.profiles.any(
      (profile) => TermuxBridge.managesServerUrl(profile.baseUrl),
    );

/// [searchIndex] narrowed to [query] by the shared settings matcher
/// (lib/domain/settings_search.dart): every word must match by word, prefix
/// or one typo; a whole title first, then exact words, prefixes and typos.
/// A row inside a page outranks the broad door to that page on a tie.
List<SearchEntry> searchEntries(
  AppLocalizations l10n,
  SearchScope scope,
  String query,
) {
  final entries = searchIndex(l10n, scope);
  final byId = {for (final entry in entries) entry.id: entry};
  final index = SettingsSearchIndex(
    settingsSearchCatalog(
      l10n,
      existing: [for (final entry in entries) entry.document],
      supportsBackgroundService: scope.platform.supportsBackgroundService,
      thermalGuardAvailable: scope.thermalGuard,
      managedRecoveryAvailable: _managedRecovery(scope),
    ),
  );
  return [for (final document in index.search(query)) ?byId[document.id]];
}

Future<void> _push(BuildContext context, Widget screen) =>
    pushKitPage<void>(context, (_) => screen);

SearchOpen _screen(Widget Function(SearchScope scope) build) =>
    (context, scope) => _push(context, build(scope));

/// Tabs live in the shell's first route. The same bus the desktop shortcuts
/// use pops whatever is above it and hands the shell the request.
SearchOpen _shell(Intent intent) => (context, scope) async {
  final signals = AppShortcutScope.read(context);
  if (signals == null) return;
  dispatchAtShellRoot(Navigator.of(context), signals, intent);
};

/// Opens [target]'s page; a target that names a row opens the page arrived
/// at that row (KitArrival): scrolled to it, focused and marked once.
SearchOpen _arrive(SettingsSearchTarget target) => (context, scope) {
  final Widget? page = switch (target.pageId) {
    'appearance-settings' => AppearanceSettingsScreen(
      controller: scope.controller,
      initialSection: AppearanceSection.values.asNameMap()[target.sectionId],
    ),
    // Keep running and What runs by itself are sections of the one
    // Notifications and background page.
    'keep-running' => NotificationsSettingsScreen(
      controller: scope.controller,
      initialSection: 'keep-running',
    ),
    // Termux's server is the one with a crash restart; the row is there
    // only while a saved server is managed by it, rechecked by the page.
    'termux-setup-installed' => const ThisPhoneScreen(
      kind: PhoneHostKind.termux,
    ),
    'app-diagnostics' => AppDiagnosticsScreen(controller: scope.controller),
    _ => null,
  };
  if (page == null) return Future<void>.value();
  final row = target.rowId;
  return _push(
    context,
    row == null ? page : KitArrivalScope(rowId: row, child: page),
  );
};

SearchOpen _projectTool(ProjectTool tool) =>
    (context, scope) => openProjectTool(context, scope.controller, tool);

bool _hasProjectTool(SearchScope scope, ProjectTool tool) =>
    scope.controller.isConnected &&
    ProjectHub.toolsFor(scope.capabilities).contains(tool);

bool _catalog(SearchScope scope) => scope.capabilities.serverCatalog;

/// The tab a catalog sits on depends on whether Tools is there at all.
int _capabilitiesTab(SearchScope scope, int withTools) =>
    scope.capabilities.toolInventory || withTools < 1
    ? withTools
    : withTools - 1;

/// OpenCode is saved on this phone, in the app or in Termux.
bool _phoneSetUp(SearchScope scope) => scope.controller.store.profiles.any(
  (p) => looksLikeInAppServer(p) || TermuxBridge.managesServerUrl(p.baseUrl),
);

bool _account(SearchScope scope) =>
    scope.controller.isConnected && scope.capabilities.agentAccount;

/// Opens the Settings hub arrived at one of its own rows (a switch or the
/// shell choice, which act in place): the group is chosen on a wide window
/// and the row is scrolled to and marked (KitArrival).
SearchOpen _hubAt(SettingsGroup group, String rowId) => _screen(
  (scope) => KitArrivalScope(
    rowId: rowId,
    child: SettingsScreen(controller: scope.controller, initialGroup: group),
  ),
);

/// Why Claude Code on this phone is not offered here, instead of a search
/// that finds nothing (P7.4 "explain instead of vanish"): it runs on the
/// phone only through the Termux bridge; on a computer it is a Paseo server.
Future<void> _explainClaudeCodeGate(BuildContext context, SearchScope scope) {
  final l10n = AppLocalizations.of(context);
  final navigator = Navigator.of(context);
  return showKitAlert(
    context,
    title: l10n.searchClaudeCodeGateTitle,
    body: scope.desktop
        ? l10n.searchClaudeCodeGateDesktop
        : l10n.searchClaudeCodeGateDevice,
    icon: AppIconography.agent,
    alertKey: const ValueKey('search-claude-code-gate'),
    action: scope.controller.isIsolated
        ? null
        : KitAction(
            key: const ValueKey('search-claude-code-gate-servers'),
            label: l10n.searchClaudeCodeGateServers,
            onPressed: () => unawaited(navigator.pushNamed('/servers')),
          ),
  );
}

Future<void> _openVoice(BuildContext context, SearchScope scope) async {
  try {
    final models = await VoiceModelManager.shared();
    if (!context.mounted) return;
    await showVoiceModelSetupSheet(context, models);
  } catch (error) {
    if (context.mounted) showProductError(context, error);
  }
}

bool _canImport(SearchScope scope) {
  final repository = scope.controller.repository;
  return scope.capabilities.sessionImportExport &&
      repository is SessionImportGateway &&
      (repository as SessionImportGateway).sessionImportSupported;
}

/// Every entry, ungated, in the order results are listed: the hub's rows in
/// hub order, then what sits inside them, then the places.
List<SearchEntry> allSearchEntries(AppLocalizations l10n) {
  // Static copy per language: built once, not per Settings build.
  return _allEntriesByLanguage.putIfAbsent(l10n.localeName, () {
    allSearchEntriesBuilds++;
    return _buildAllSearchEntries(l10n);
  });
}

final _allEntriesByLanguage = <String, List<SearchEntry>>{};

/// Test seam: how many times the ungated index was actually built.
@visibleForTesting
int allSearchEntriesBuilds = 0;

List<SearchEntry> _buildAllSearchEntries(AppLocalizations l10n) {
  final notifications = l10n.settingsHubGroupNotifications;
  final appearance = l10n.e7AppearanceTitle;
  final usage = l10n.settingsHubGroupUsage;
  final commandsAndTools = l10n.libraryCommandsToolsTitle;
  final onThisPhone = l10n.onboardingTermuxSetup;
  // Rows inside pages (P9.4): their words and targets come from the shared
  // settings catalog, so Settings, the command launcher and the palette
  // find them alike.
  final rows = {
    for (final row in settingsSearchRows(
      l10n,
      supportsBackgroundService: true,
      thermalGuardAvailable: true,
      managedRecoveryAvailable: true,
    ))
      row.id: row,
  };
  SearchEntry row(String id, IconData icon, {SearchGate gate = _always}) {
    final document = rows[id]!;
    return SearchEntry(
      id: id,
      kind: SearchEntryKind.insideSettings,
      icon: icon,
      title: document.title,
      parent: document.parent,
      keywords: document.aliases,
      // Keep the legacy row target while indexing its current containing page.
      pages: [
        document.target.pageId == 'keep-running'
            ? 'notifications-settings'
            : document.target.pageId,
      ],
      target: document.target,
      gate: gate,
      open: _arrive(document.target),
    );
  }

  bool background(SearchScope scope) =>
      scope.platform.supportsBackgroundService;
  final diagnostics = rows['app-diagnostics-entry']!;
  return [
    // ---- Settings hub rows (target-ia §1.3), in hub order ------------
    // Server: the connected server, every saved server, this phone.
    SearchEntry(
      id: 'settings-category-server',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.server,
      icon: AppIconography.server,
      title: l10n.settingsHubThisServer,
      keywords:
          '${l10n.settingsHubSearchServerAliases} ${l10n.e7SettingsUi1} '
          '${l10n.e7SettingsUi65}',
      pages: const ['server-settings', 'host-management'],
      open: _screen(
        (scope) => ServerSettingsScreen(controller: scope.controller),
      ),
    ),
    SearchEntry(
      id: 'settings-saved-servers',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.server,
      icon: AppIconography.database,
      title: l10n.activitySavedServers,
      // The screen's own heading is the product name over "Servers".
      keywords:
          '${l10n.settingsHubSearchSavedServersAliases} '
          '${l10n.openCodeConnectionLabel} — ${l10n.e7SetupServers}',
      // The servers screen also holds the scanner and the editor; this row
      // is the one door to them. Each server row says what it needs.
      pages: const ['servers'],
      open: (context, _) => Navigator.of(context).pushNamed('/servers'),
    ),
    // Once OpenCode is set up on this phone, This phone is a place of its
    // own: what runs here, storage, updates and removal.
    SearchEntry(
      id: 'settings-this-phone',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.server,
      icon: AppIconography.phone,
      title: l10n.phoneServerCardTitle,
      keywords: '${l10n.settingsHubSearchPhoneAliases} $onThisPhone',
      pages: const ['termux-setup-installed'],
      gate: (scope) => scope.platform.supportsTermux && _phoneSetUp(scope),
      open: (context, _) => openThisPhone(context),
    ),
    // Before that, setting it up is one of Add server's ways (R3): the hub
    // holds no second door to it, and search finds it here.
    SearchEntry(
      id: 'settings-on-this-phone',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.activitySavedServers,
      icon: AppIconography.phone,
      title: onThisPhone,
      keywords: l10n.settingsHubSearchPhoneAliases,
      pages: const ['phone-setup-start'],
      // Local Android tools belong to the phone, not the connected server's
      // capability set.
      gate: (scope) => scope.platform.supportsTermux && !_phoneSetUp(scope),
      open: (context, _) => openPhoneSetupStart(context),
    ),
    // Agent: the model, who pays for it, what it can use, the AI Team.
    SearchEntry(
      id: 'settings-model-and-mode',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.agent,
      icon: AppIconography.model,
      title: l10n.settingsHubModelRow,
      keywords:
          '${l10n.settingsHubModelAndMode} '
          '${l10n.settingsHubSearchModelModeAliases}',
      open: (context, _) async => showModelPicker(context),
    ),
    // One row for whoever the model is paid through: the providers and
    // their keys, or the Codex account where the server signs in itself.
    SearchEntry(
      id: 'settings-providers',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.agent,
      icon: AppIconography.cloud,
      title: l10n.settingsHubProvidersRow,
      keywords:
          '${l10n.libraryProvidersTitle} ${l10n.settingsHubAccounts} '
          '${l10n.settingsHubSearchProvidersAliases} '
          '${l10n.settingsHubSearchAccountsAliases}',
      pages: const ['integrations', 'agent-account'],
      gate: (scope) => _catalog(scope) || _account(scope),
      serverGate: (scope) => _catalog(scope) || _account(scope),
      open: (context, scope) => _catalog(scope)
          ? _push(
              context,
              IntegrationsScreen(
                controller: scope.controller,
                mode: IntegrationsMode.providers,
              ),
            )
          : _push(context, AgentAccountScreen(connection: scope.controller)),
    ),
    SearchEntry(
      id: 'settings-tools',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.agent,
      icon: AppIconography.tools,
      title: l10n.settingsHubToolsRow,
      keywords:
          '${l10n.settingsHubSearchToolsAliases} ${l10n.libraryMcpTitle} '
          '$commandsAndTools ${l10n.teamUiPluginsTitle} ${l10n.a2aTitle}',
      pages: const ['tools-hub'],
      // External agents live in this app, so Tools always has a row.
      open: _screen((scope) => ToolsHubScreen(controller: scope.controller)),
    ),
    // The AI Team as a place of its own in Settings, not only a plugin
    // (docs/qa/team-discover-2026-09-25): the one team page (P3.4), on or
    // off; off, it sets the team up for this kind of server. The same door
    // 'ai-team' below uses.
    SearchEntry(
      id: 'settings-ai-team',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.agent,
      icon: AppIconography.agent,
      title: l10n.teamUiHomeTitle,
      keywords:
          '${l10n.discoverTeamAliases} ${l10n.teamSettingsTitle} ${l10n.teamUiPluginsTitle}',
      pages: const ['team-intro', 'team-settings'],
      gate: (scope) => scope.controller.profile != null,
      // Setup only: Team settings while on, the turn-on flow while off.
      open: (context, scope) => openTeamSetup(context, scope.controller),
    ),
    // Conversations: what runs by itself, how a transcript shows,
    // the shell it runs commands in, and voice.
    // What runs by itself (P6.1): the team's level and the doors to the
    // rules and the watching that let things happen without asking.
    // What runs by itself (P6.1): the last section of Notifications and
    // background; still found by its own words.
    SearchEntry(
      id: 'settings-automation',
      kind: SearchEntryKind.insideSettings,
      parent: notifications,
      icon: AppIconography.sync,
      title: l10n.automationTitle,
      keywords: l10n.automationSearchAliases,
      pages: const ['notifications-settings'],
      target: const SettingsSearchTarget(
        pageId: 'notifications-settings',
        sectionId: 'automation',
      ),
      gate: (scope) => AutomationSettingsSections.of(
        scope.controller,
        team: scope.hasTeam,
      ).any,
      open: _screen(
        (scope) => NotificationsSettingsScreen(
          controller: scope.controller,
          initialSection: 'automation',
        ),
      ),
    ),
    // Inside Notifications and background; still found by its own words.
    SearchEntry(
      id: 'saved-permissions-entry',
      kind: SearchEntryKind.insideSettings,
      parent: notifications,
      icon: AppIconography.permissions,
      title: l10n.e7SettingsUi74,
      keywords: l10n.settingsHubSearchPermissionsAliases,
      pages: const ['saved-permissions'],
      gate: (scope) => scope.controller.capabilities.savedPermissionList,
      open: _screen(
        (scope) => SavedPermissionsScreen(controller: scope.controller),
      ),
    ),
    // The two transcript switches are hub rows themselves (the old sheet
    // is gone); from anywhere else, open the hub arrived at the switch.
    SearchEntry(
      id: 'settings-show-reasoning',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.conversations,
      icon: AppIconography.idea,
      title: l10n.settingsHubShowReasoning,
      keywords:
          '${l10n.settingsHubSearchTranscriptAliases} '
          '${l10n.chatUiTranscriptDisplay} ${l10n.transcriptFindReasoning}',
      pages: const ['settings'],
      open: _hubAt(SettingsGroup.conversations, 'settings-show-reasoning'),
    ),
    SearchEntry(
      id: 'settings-show-timestamps',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.conversations,
      icon: AppIconography.clock,
      title: l10n.settingsHubShowTimestamps,
      keywords:
          '${l10n.settingsHubSearchTranscriptAliases} '
          '${l10n.chatUiTranscriptDisplay} ${l10n.chatUiTimestampsUsage} '
          'cost tokens',
      pages: const ['settings'],
      open: _hubAt(SettingsGroup.conversations, 'settings-show-timestamps'),
    ),
    SearchEntry(
      id: 'default-shell-settings-entry',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.conversations,
      icon: AppIconography.terminal,
      title: l10n.e7SettingsUi35,
      keywords: l10n.settingsHubSearchShellAliases,
      // Rule 7: absent where the server cannot change it; the group's line
      // counts it and "Available on this server" says why.
      gate: (scope) => scope.capabilities.shellSettings,
      serverGate: (scope) => scope.capabilities.shellSettings,
      // The hub row is its own control; from anywhere else, open the hub
      // arrived at it.
      open: _hubAt(SettingsGroup.conversations, 'default-shell-settings-entry'),
    ),
    SearchEntry(
      id: 'settings-voice',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.conversations,
      icon: AppIconography.mic,
      title: l10n.settingsHubVoice,
      keywords: l10n.settingsHubSearchVoiceAliases,
      // The speech models can neither download nor run off Android.
      gate: (scope) => scope.platform.supportsVoice,
      open: _openVoice,
    ),
    // This app: what notifies, what keeps it running, how it looks, what
    // it keeps, what it has cost.
    SearchEntry(
      id: 'settings-category-background',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.thisApp,
      icon: AppIconography.notificationImportant,
      title: notifications,
      keywords: l10n.settingsHubSearchNotificationsAliases,
      pages: const ['notifications-settings'],
      open: _screen(
        (scope) => NotificationsSettingsScreen(controller: scope.controller),
      ),
    ),
    SearchEntry(
      id: 'settings-keep-running',
      kind: SearchEntryKind.insideSettings,
      parent: notifications,
      icon: AppIconography.batteryCharging,
      title: l10n.keepRunningTitle,
      keywords: l10n.keepRunningRowSubtitle,
      pages: const ['notifications-settings'],
      target: const SettingsSearchTarget(
        pageId: 'notifications-settings',
        sectionId: 'keep-running',
      ),
      // What to allow so Android leaves the app (and the OpenCode inside
      // it) running: Android only.
      gate: (scope) => scope.platform.supportsBackgroundService,
      open: (context, _) => openKeepRunningScreen(context),
    ),
    SearchEntry(
      id: 'settings-category-appearance',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.thisApp,
      icon: AppIconography.appearance,
      title: appearance,
      keywords: l10n.settingsHubSearchAppearanceAliases,
      pages: const ['appearance-settings'],
      open: _screen(
        (scope) => AppearanceSettingsScreen(controller: scope.controller),
      ),
    ),
    SearchEntry(
      id: 'settings-category-privacy',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.thisApp,
      icon: AppIconography.privacy,
      title: l10n.settingsHubPrivacyRow,
      // Not "Older drafts": they are listed from the conversation that owns
      // them, and this page does not hold them.
      keywords: l10n.settingsHubSearchPrivacyAliases,
      pages: const ['privacy-settings'],
      open: _screen(
        (scope) => PrivacySettingsScreen(controller: scope.controller),
      ),
    ),
    SearchEntry(
      id: 'settings-category-usage',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.thisApp,
      icon: AppIconography.usage,
      title: usage,
      keywords: l10n.settingsHubSearchUsageAliases,
      pages: const ['usage-hub'],
      // "Spent" needs usage statistics, "Remaining" needs a saved server.
      gate: (scope) => UsageHubScreen.sectionsFor(scope.controller).isNotEmpty,
      open: _screen((scope) => UsageHubScreen(controller: scope.controller)),
    ),
    // Help, the last, unlabelled panel.
    SearchEntry(
      id: 'settings-setup-guide',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.help,
      icon: AppIconography.guide,
      title: l10n.onboardingSetupGuide,
      keywords: l10n.settingsHubSearchGuideAliases,
      pages: const ['guide'],
      open: _screen((_) => GuideScreen(embedded: false)),
    ),
    // Report a problem (P8.2): the one row for the GitHub form and the
    // diagnostics, which used to be two paths.
    SearchEntry(
      id: 'library-report-bug',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.help,
      icon: AppIconography.bug,
      title: l10n.e7LibraryReportABug,
      // "crash" too: what went wrong is here even without a phone server.
      keywords:
          '${l10n.settingsHubSearchBugAliases} ${l10n.e7SettingsUi88} '
          '${l10n.settingsHubSearchDiagnosticsAliases} ${diagnostics.aliases}',
      pages: const ['app-diagnostics'],
      target: diagnostics.target,
      open: _arrive(diagnostics.target),
    ),
    // The explanation for every row the connected server hides; each
    // group's "Why" opens it too.
    SearchEntry(
      id: 'settings-server-capabilities',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.help,
      icon: AppIconography.checklist,
      title: l10n.capabilityScreenTitle,
      keywords: l10n.capabilityScreenAliases,
      pages: const ['server-capabilities'],
      // It describes the connected server, so there is nothing to show
      // without one.
      gate: (scope) => scope.controller.isConnected,
      open: _screen(
        (scope) => ServerCapabilitiesScreen(controller: scope.controller),
      ),
    ),
    SearchEntry(
      id: 'settings-about-notices',
      kind: SearchEntryKind.hubRow,
      group: SettingsGroup.help,
      icon: AppIconography.info,
      title: l10n.aboutTitle,
      keywords:
          '${l10n.settingsHubSearchAboutAliases} ${l10n.e7SettingsUi96} '
          '${l10n.e7SettingsDetailUi18}',
      pages: const ['about', 'about-open-source-tab'],
      open: _screen((scope) => AboutScreen(controller: scope.controller)),
    ),

    // ---- Inside the hub's pages ----------------------------------------
    SearchEntry(
      id: 'settings-tailscale',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.activitySavedServers,
      icon: AppIconography.network,
      title: l10n.tailscaleTitle,
      keywords: l10n.settingsHubSearchTailscaleAliases,
      pages: const ['tailscale-setup'],
      gate: (scope) => scope.platform.supportsTailscaleHandoff,
      open: _screen((_) => const TailscaleSetupScreen()),
    ),
    SearchEntry(
      id: 'settings-models',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.settingsHubModelRow,
      icon: AppIconography.model,
      title: l10n.libraryModelsAgentsTitle,
      // The hub says "Models & agents"; the screen spells it out.
      keywords:
          '${l10n.settingsHubSearchModelsAliases} '
          '${l10n.e7LibraryModelsAndAgents}',
      // The model picker is the catalogue: the same list, where choosing
      // one is what a person came for.
      pages: const ['model-picker-sheet'],
      gate: _catalog,
      open: (context, _) async => showModelPicker(context),
    ),
    // The Codex account, when the Providers row already leads to the
    // providers (a server with both): otherwise that row is its door.
    SearchEntry(
      id: 'settings-accounts',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.settingsHubProvidersRow,
      icon: AppIconography.person,
      title: l10n.settingsHubAccounts,
      keywords:
          '${l10n.settingsHubSearchAccountsAliases} ${l10n.agentAccountTitle}',
      pages: const ['agent-account'],
      gate: (scope) => _account(scope) && _catalog(scope),
      open: _screen(
        (scope) => AgentAccountScreen(connection: scope.controller),
      ),
    ),
    // Tools' rows (external-agents moved inside it).
    SearchEntry(
      id: 'settings-mcp',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.settingsHubToolsRow,
      icon: AppIconography.network,
      title: l10n.libraryMcpTitle,
      // "Add MCP server" is a button on this screen, so its title leads here.
      keywords:
          '${l10n.settingsHubSearchMcpAliases} ${l10n.mcpAdd} '
          '${l10n.e7LibraryMCPAndIntegrations} ${l10n.mcpCatalogTitle}',
      pages: const ['integrations', 'mcp-setup', 'mcp-catalog'],
      gate: _catalog,
      serverGate: _catalog,
      open: _screen(
        (scope) => IntegrationsScreen(
          controller: scope.controller,
          mode: IntegrationsMode.mcp,
        ),
      ),
    ),
    SearchEntry(
      id: 'settings-commands-tools',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.settingsHubToolsRow,
      icon: AppIconography.play,
      title: commandsAndTools,
      keywords: l10n.settingsHubSearchCommandsAliases,
      pages: const ['capabilities'],
      gate: _catalog,
      serverGate: _catalog,
      open: _screen(
        (scope) => CapabilitiesScreen(controller: scope.controller),
      ),
    ),
    // The server's own plugins: a section of Settings > This server (the
    // Plugins page it used to sit on is gone).
    SearchEntry(
      id: 'settings-server-plugins',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.settingsHubThisServer,
      icon: AppIconography.extensions,
      title: l10n.pluginsSectionOnServer,
      keywords:
          '${l10n.settingsHubSearchPluginsAliases} ${l10n.teamUiPluginsTitle}',
      pages: const ['server-settings'],
      gate: (scope) =>
          scope.controller.profile != null &&
          scope.capabilities.pluginInventory,
      open: _screen(
        (scope) => KitArrivalScope(
          rowId: 'settings-server-plugins',
          child: ServerSettingsScreen(controller: scope.controller),
        ),
      ),
    ),
    SearchEntry(
      id: 'settings-external-agents',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.settingsHubToolsRow,
      icon: AppIconography.support,
      title: l10n.a2aTitle,
      keywords: l10n.settingsHubSearchExternalAgentsAliases,
      pages: const ['external-agents'],
      open: (context, scope) => openExternalAgents(context, scope.controller),
    ),
    SearchEntry(
      id: 'library-import-session',
      kind: SearchEntryKind.destination,
      parent: l10n.globalSessionsTitle,
      icon: AppIconography.fileUpload,
      title: l10n.importTitle,
      keywords: l10n.e7LibrarySearchImportAliases,
      pages: const ['session-import'],
      gate: _canImport,
      open: _screen(
        (scope) => SessionImportScreen(controller: scope.controller),
      ),
    ),
    SearchEntry(
      id: 'settings-privacy-data-use',
      kind: SearchEntryKind.insideSettings,
      // About's privacy tab merged into Privacy and data (P3.10).
      parent: l10n.settingsHubPrivacyRow,
      icon: AppIconography.policy,
      title: l10n.privacyPolicyTitle,
      keywords:
          '${l10n.e7SettingsUi92} ${l10n.settingsHubSearchAboutAliases} '
          '${l10n.e7SettingsDetailUi17}',
      pages: const ['privacy-settings'],
      open: (context, _) => showPrivacyPolicy(context),
    ),
    // The demo is the first-run welcome's "Just show me"; once a server is
    // saved, the setup guide is where it stays reachable.
    SearchEntry(
      id: 'settings-try-demo',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.onboardingSetupGuide,
      icon: AppIconography.play,
      title: l10n.settingsTryDemo,
      keywords: '${l10n.demoScreenTitle} ${l10n.demoScreenSimulated}',
      pages: const ['demo'],
      open: _screen((_) => const DemoScreen()),
    ),
    // About holds the tips, the shortcuts and the open source notices.
    SearchEntry(
      id: 'library-keyboard-shortcuts',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.aboutTitle,
      icon: AppIconography.keyboard,
      title: l10n.e7LibraryKeyboardShortcuts,
      keywords: l10n.e7LibrarySearchShortcutsAliases,
      // The shortcut layer must be discoverable without already knowing a
      // shortcut, and means nothing without a keyboard.
      gate: (scope) => scope.desktop,
      open: (context, _) => showShortcutsHelp(context),
    ),
    SearchEntry(
      id: 'settings-show-tips-again',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.aboutTitle,
      icon: AppIconography.idea,
      title: l10n.discoverShowTipsAgain,
      keywords: l10n.discoverShowTipsAliases,
      // About's row is the control itself; from anywhere else, open About
      // arrived at it.
      open: _screen(
        (scope) => KitArrivalScope(
          rowId: 'settings-show-tips-again',
          child: AboutScreen(controller: scope.controller),
        ),
      ),
    ),
    SearchEntry(
      id: 'settings-voice-notices',
      kind: SearchEntryKind.insideSettings,
      // About › Open source holds every licence, the voice models' too.
      parent: l10n.aboutTitle,
      icon: AppIconography.policy,
      title: l10n.e7SettingsUi94,
      keywords:
          '${l10n.settingsHubSearchAboutAliases} ${l10n.e7SettingsDetailUi18}',
      pages: const ['voice-notices'],
      gate: (scope) => scope.platform.supportsVoice,
      open: (context, _) async => showVoiceNotices(context),
    ),

    // ---- Inside second-level settings screens ------------------------
    // Disconnect lives on the page of the server it disconnects from; the
    // result opens that page at the row, which confirms before it acts.
    SearchEntry(
      id: 'inside-server-disconnect',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.unlink,
      title: l10n.e7SettingsUi8,
      parent: l10n.settingsHubThisServer,
      keywords: l10n.settingsHubSearchDisconnectAliases,
      pages: const ['server-settings'],
      target: const SettingsSearchTarget(
        pageId: 'server-settings',
        sectionId: 'disconnect',
      ),
      gate: (scope) => scope.controller.profile != null,
      open: _screen(
        (scope) => ServerSettingsScreen(
          controller: scope.controller,
          initialSection: ServerSettingsScreen.disconnectSection,
        ),
      ),
    ),
    // AI setup (review only, 2026-09-28) is a row of the connected
    // server's page; the result opens the page itself. Present exactly
    // when that row is: a saved server that can share its configuration.
    // The UI ledger has no page of its own for it yet, so the door page
    // is the server's settings page.
    SearchEntry(
      id: 'inside-server-ai-setup',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.sparkle,
      title: l10n.aiSetupTitle,
      parent: l10n.settingsHubThisServer,
      keywords: l10n.aiSetupEntryDetail,
      pages: const ['server-settings', 'ai-setup'],
      gate: (scope) =>
          scope.controller.profile != null &&
          scope.capabilities.setupConfigRead,
      serverGate: (scope) => scope.capabilities.setupConfigRead,
      open: (context, scope) {
        final profile = scope.controller.profile;
        if (profile == null) return Future<void>.value();
        return _push(
          context,
          AiSetupScreen(
            controller: scope.controller,
            serverName: serverDisplayName(
              profile,
              lookupAppLocalizations(Localizations.localeOf(context)),
              among: scope.controller.store.profiles,
            ),
          ),
        );
      },
    ),
    SearchEntry(
      id: 'inside-notifications-what',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.notificationImportant,
      title: l10n.notifySectionWhat,
      parent: notifications,
      keywords: l10n.discoverNotifyWhatAliases,
      pages: const ['notifications-settings'],
      target: const SettingsSearchTarget(
        pageId: 'notifications-settings',
        sectionId: 'what',
      ),
      gate: (scope) => scope.platform.supportsNotifications,
      open: _screen(
        (scope) => NotificationsSettingsScreen(
          controller: scope.controller,
          initialSection: 'what',
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-notifications-quiet',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.clock,
      title: l10n.monitorQuiet,
      parent: notifications,
      keywords: l10n.discoverNotifyQuietAliases,
      pages: const ['notifications-settings'],
      target: const SettingsSearchTarget(
        pageId: 'notifications-settings',
        sectionId: 'quiet',
      ),
      // Quiet hours only silence notifications.
      gate: (scope) => scope.platform.supportsNotifications,
      open: _screen(
        (scope) => NotificationsSettingsScreen(
          controller: scope.controller,
          initialSection: 'quiet',
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-notifications-background',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.sync,
      title: l10n.e7SettingsUi25,
      parent: notifications,
      keywords: l10n.discoverNotifyBackgroundAliases,
      pages: const ['notifications-settings'],
      target: const SettingsSearchTarget(
        pageId: 'notifications-settings',
        sectionId: 'background',
      ),
      gate: (scope) => scope.platform.supportsBackgroundService,
      open: _screen(
        (scope) => NotificationsSettingsScreen(
          controller: scope.controller,
          initialSection: 'background',
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-notifications-servers',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.database,
      title: l10n.discoverNotifyServersTitle,
      parent: notifications,
      // "Background checks" was a page of its own until slice-close-misc;
      // its words still find where each server's checks are turned on.
      keywords:
          '${l10n.discoverNotifyServersAliases} '
          '${l10n.monitorBackgroundChecks} ${l10n.discoverMonitorAliases}',
      pages: const ['notifications-settings'],
      target: const SettingsSearchTarget(
        pageId: 'notifications-settings',
        sectionId: 'servers',
      ),
      gate: (scope) =>
          !scope.controller.isIsolated &&
          scope.controller.store.profiles.isNotEmpty,
      open: _screen(
        (scope) => NotificationsSettingsScreen(
          controller: scope.controller,
          initialSection: 'servers',
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-appearance-mode',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.contrast,
      title: l10n.e7SettingsUi69,
      parent: appearance,
      keywords: l10n.discoverAppearanceModeAliases,
      pages: const ['appearance-settings'],
      target: const SettingsSearchTarget(
        pageId: 'appearance-settings',
        sectionId: 'mode',
      ),
      open: _screen(
        (scope) => AppearanceSettingsScreen(
          controller: scope.controller,
          initialSection: AppearanceSection.mode,
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-appearance-language',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.globe,
      title: l10n.e7LocaleUiLanguage,
      parent: appearance,
      keywords: l10n.discoverLanguageAliases,
      pages: const ['appearance-settings'],
      target: const SettingsSearchTarget(
        pageId: 'appearance-settings',
        sectionId: 'language',
      ),
      open: _screen(
        (scope) => AppearanceSettingsScreen(
          controller: scope.controller,
          initialSection: AppearanceSection.language,
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-appearance-theme',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.appearance,
      title: l10n.e7SettingsUi70,
      parent: appearance,
      keywords: l10n.discoverThemeAliases,
      pages: const ['appearance-settings'],
      target: const SettingsSearchTarget(
        pageId: 'appearance-settings',
        sectionId: 'theme',
      ),
      open: _screen(
        (scope) => AppearanceSettingsScreen(
          controller: scope.controller,
          initialSection: AppearanceSection.theme,
        ),
      ),
    ),
    // Settings › Appearance › Effects, one result per row.
    row('inside-appearance-motion', AppIconography.playCircle),
    // Keep running's rows: the battery exemption, and the heat pause only
    // where the guard runs (the page hides its switch otherwise).
    row(
      'inside-keep-running-battery',
      AppIconography.batteryCharging,
      gate: background,
    ),
    row(
      'inside-keep-running-thermal',
      AppIconography.pause,
      gate: (scope) => background(scope) && scope.thermalGuard,
    ),
    SearchEntry(
      id: 'inside-usage-spent',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.usage,
      title: l10n.usageSectionSpent,
      parent: usage,
      keywords: l10n.discoverSpentAliases,
      pages: const ['usage'],
      target: const SettingsSearchTarget(pageId: 'usage', sectionId: 'spent'),
      gate: (scope) => scope.controller.supportsUsageStatistics,
      open: _screen(
        (scope) => UsageHubScreen(
          controller: scope.controller,
          initialSection: UsageSection.spent,
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-usage-budgets',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.usageRing,
      title: l10n.usageBudgetTitle,
      parent: usage,
      keywords: l10n.discoverBudgetAliases,
      pages: const ['usage'],
      target: const SettingsSearchTarget(pageId: 'usage', sectionId: 'spent'),
      gate: (scope) => scope.controller.supportsUsageStatistics,
      open: _screen(
        (scope) => UsageHubScreen(
          controller: scope.controller,
          initialSection: UsageSection.spent,
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-usage-remaining',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.usageRing,
      title: l10n.usageSectionRemaining,
      parent: usage,
      keywords: l10n.discoverRemainingAliases,
      pages: const ['provider-quota'],
      target: const SettingsSearchTarget(
        pageId: 'usage',
        sectionId: 'remaining',
      ),
      gate: (scope) => scope.controller.profile != null,
      open: _screen(
        (scope) => UsageHubScreen(
          controller: scope.controller,
          initialSection: UsageSection.remaining,
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-usage-quota-monitoring',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.notificationImportant,
      title: l10n.quotaMonitorTitle,
      parent: usage,
      keywords: l10n.discoverQuotaMonitorAliases,
      pages: const ['provider-quota'],
      target: const SettingsSearchTarget(
        pageId: 'usage',
        sectionId: 'remaining',
      ),
      gate: (scope) => scope.controller.profile != null,
      open: _screen(
        (scope) => UsageHubScreen(
          controller: scope.controller,
          initialSection: UsageSection.remaining,
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-capabilities-commands',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.play,
      title: l10n.runResultsCommandsTitle,
      parent: commandsAndTools,
      keywords:
          '${l10n.discoverCommandsAliases} ${l10n.e7LibraryServerCommands}',
      pages: const ['commands'],
      gate: _catalog,
      open: _screen(
        (scope) => CapabilitiesScreen(controller: scope.controller),
      ),
    ),
    SearchEntry(
      id: 'inside-capabilities-tools',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.tools,
      title: l10n.e7SettingsDetailUi25,
      parent: commandsAndTools,
      keywords:
          '${l10n.discoverToolsAliases} ${l10n.e7LibraryToolsAndCapabilities}',
      pages: const ['tools'],
      gate: (scope) => _catalog(scope) && scope.capabilities.toolInventory,
      open: _screen(
        (scope) =>
            CapabilitiesScreen(controller: scope.controller, initialTab: 1),
      ),
    ),
    SearchEntry(
      id: 'inside-capabilities-skills',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.sparkle,
      title: l10n.e7SettingsDetailUi26,
      parent: commandsAndTools,
      keywords: l10n.discoverSkillsAliases,
      pages: const ['skills'],
      gate: _catalog,
      open: _screen(
        (scope) => CapabilitiesScreen(
          controller: scope.controller,
          initialTab: _capabilitiesTab(scope, 2),
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-capabilities-references',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.bookmarks,
      title: l10n.e7SettingsDetailUi27,
      parent: commandsAndTools,
      keywords: l10n.discoverReferencesAliases,
      pages: const ['references'],
      gate: _catalog,
      open: _screen(
        (scope) => CapabilitiesScreen(
          controller: scope.controller,
          initialTab: _capabilitiesTab(scope, 3),
        ),
      ),
    ),
    SearchEntry(
      id: 'inside-phone-running-now',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.processor,
      title: l10n.termuxProcsTitle,
      parent: onThisPhone,
      keywords: l10n.discoverRunningNowAliases,
      pages: const ['termux-processes'],
      gate: (scope) => scope.platform.supportsTermux,
      open: _screen((_) => const TermuxProcessesScreen()),
    ),
    SearchEntry(
      id: 'inside-phone-storage',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.database,
      title: l10n.termuxStorageTitle,
      parent: onThisPhone,
      keywords: l10n.discoverStorageAliases,
      pages: const ['termux-storage'],
      gate: (scope) => scope.platform.supportsTermux,
      open: _screen((_) => const TermuxStorageScreen()),
    ),
    row(
      'inside-phone-crash-recovery',
      AppIconography.restart,
      gate: _managedRecovery,
    ),
    SearchEntry(
      id: 'inside-phone-claude-code',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.agent,
      title: l10n.localAgentPageTitle,
      parent: onThisPhone,
      keywords: l10n.localAgentTitle,
      pages: const ['local-agent-page'],
      gate: (scope) => scope.platform.supportsTermux,
      open: (context, _) => _push(
        context,
        LocalAgentScreen(
          onConnected: () => Navigator.of(
            context,
          ).pushNamedAndRemoveUntil('/home', (_) => false),
        ),
      ),
    ),
    // The same door where the Termux bridge is missing: it explains why
    // instead of vanishing from search (P7.4).
    SearchEntry(
      id: 'inside-phone-claude-code-unavailable',
      kind: SearchEntryKind.insideSettings,
      icon: AppIconography.agent,
      title: l10n.localAgentPageTitle,
      parent: onThisPhone,
      keywords: l10n.localAgentTitle,
      pages: const ['local-agent-page'],
      gate: (scope) => !scope.platform.supportsTermux,
      open: _explainClaudeCodeGate,
    ),
    SearchEntry(
      id: 'settings-add-server',
      kind: SearchEntryKind.insideSettings,
      parent: l10n.activitySavedServers,
      icon: AppIconography.add,
      title: l10n.e7SetupAddServer,
      // Connection help folded into Add server (P3.9): its checks explain
      // an address where it is typed.
      keywords: l10n.discoverConnectionHelpAliases,
      // Add server is the Servers page's action; its form stays excluded
      // from title search (many titles, one form).
      pages: const ['servers'],
      open: (context, _) => Navigator.of(
        context,
      ).pushNamed('/servers', arguments: const ServersRouteRequest.add()),
    ),

    // ---- Places ------------------------------------------------------
    SearchEntry(
      id: 'tab-work',
      kind: SearchEntryKind.destination,
      icon: AppIconography.workspace,
      title: l10n.shellTabWork,
      keywords: l10n.discoverWorkAliases,
      pages: const ['workspace'],
      gate: (scope) => scope.hasShell,
      open: _shell(const SelectDestinationIntent(0)),
    ),
    SearchEntry(
      id: 'tab-inbox',
      kind: SearchEntryKind.destination,
      icon: AppIconography.activity,
      title: l10n.shellTabInbox,
      keywords: l10n.discoverInboxAliases,
      pages: const ['activity'],
      gate: (scope) => scope.hasShell,
      open: _shell(const SelectDestinationIntent(1)),
    ),
    SearchEntry(
      id: 'tab-project',
      kind: SearchEntryKind.destination,
      icon: AppIconography.files,
      title: l10n.shellTabProject,
      keywords: l10n.discoverProjectAliases,
      pages: const ['project-hub'],
      gate: (scope) =>
          scope.hasShell && ProjectHub.isAvailable(scope.capabilities),
      open: _shell(const SelectDestinationIntent(2)),
    ),
    SearchEntry(
      id: 'tab-settings',
      kind: SearchEntryKind.destination,
      icon: AppIconography.settings,
      title: l10n.librarySettingsTitle,
      keywords: '',
      pages: const ['settings'],
      gate: (scope) => scope.hasShell,
      open: _shell(const SelectDestinationIntent(3)),
    ),
    SearchEntry(
      id: 'project-files',
      kind: SearchEntryKind.destination,
      icon: AppIconography.files,
      title: l10n.readerUiFiles,
      parent: l10n.shellTabProject,
      keywords: l10n.discoverFilesAliases,
      pages: const ['files'],
      // Files lives inside the Project tab, so it needs the shell too.
      gate: (scope) =>
          scope.hasShell && _hasProjectTool(scope, ProjectTool.files),
      open: _shell(const OpenProjectToolIntent(ProjectTool.files)),
    ),
    SearchEntry(
      id: 'project-changes',
      kind: SearchEntryKind.destination,
      icon: AppIconography.review,
      title: l10n.readerUiChanges,
      parent: l10n.shellTabProject,
      keywords: '${l10n.discoverChangesAliases} ${l10n.demoReviewChanges}',
      pages: const ['review-workspace'],
      gate: (scope) => _hasProjectTool(scope, ProjectTool.changes),
      open: _projectTool(ProjectTool.changes),
    ),
    SearchEntry(
      id: 'project-terminal',
      kind: SearchEntryKind.destination,
      icon: AppIconography.terminal,
      title: l10n.libraryTerminalTitle,
      parent: l10n.shellTabProject,
      keywords: l10n.discoverTerminalAliases,
      pages: const ['terminal'],
      gate: (scope) => _hasProjectTool(scope, ProjectTool.terminal),
      open: _projectTool(ProjectTool.terminal),
    ),
    SearchEntry(
      id: 'project-health',
      kind: SearchEntryKind.destination,
      icon: AppIconography.diagnostics,
      title: l10n.e7LibraryProjectHealth,
      parent: l10n.shellTabProject,
      keywords: l10n.discoverHealthAliases,
      pages: const ['project-health'],
      gate: (scope) => _hasProjectTool(scope, ProjectTool.health),
      open: _projectTool(ProjectTool.health),
    ),
    SearchEntry(
      id: 'project-worktrees',
      kind: SearchEntryKind.destination,
      icon: AppIconography.branch,
      title: l10n.e7LibraryWorktrees,
      parent: l10n.shellTabProject,
      keywords: l10n.discoverWorktreesAliases,
      pages: const ['worktrees'],
      gate: (scope) => _hasProjectTool(scope, ProjectTool.worktrees),
      open: _projectTool(ProjectTool.worktrees),
    ),
    // Development services and cloud environments moved to the Project tab
    // with the retired Manage project page (slice-P3.11a).
    SearchEntry(
      id: 'project-services',
      kind: SearchEntryKind.destination,
      icon: AppIconography.processor,
      title: l10n.servicesTitle,
      parent: l10n.shellTabProject,
      keywords: l10n.discoverServicesAliases,
      pages: const ['development-services'],
      gate: (scope) => _hasProjectTool(scope, ProjectTool.services),
      open: _projectTool(ProjectTool.services),
    ),
    SearchEntry(
      id: 'project-workspaces',
      kind: SearchEntryKind.destination,
      icon: AppIconography.cloud,
      title: l10n.e7LibraryManagedWorkspaces,
      parent: l10n.shellTabProject,
      keywords: l10n.discoverCloudEnvironmentsAliases,
      pages: const ['managed-workspaces'],
      gate: (scope) => _hasProjectTool(scope, ProjectTool.workspaces),
      open: _projectTool(ProjectTool.workspaces),
    ),
    SearchEntry(
      id: 'project-search',
      kind: SearchEntryKind.destination,
      icon: AppIconography.search,
      title: l10n.readerUiSearchFiles,
      parent: l10n.shellTabProject,
      keywords: l10n.discoverSearchFilesAliases,
      pages: const ['files'],
      gate: (scope) =>
          scope.hasShell && _hasProjectTool(scope, ProjectTool.search),
      open: _shell(const OpenProjectToolIntent(ProjectTool.search)),
    ),
    SearchEntry(
      id: 'all-conversations',
      kind: SearchEntryKind.destination,
      icon: AppIconography.searchList,
      title: l10n.globalSessionsTitle,
      parent: l10n.shellTabWork,
      keywords: l10n.discoverAllConversationsAliases,
      pages: const ['global-sessions'],
      gate: (scope) =>
          scope.controller.isConnected &&
          scope.capabilities.globalSessionSearch,
      open: _screen(
        (scope) => GlobalSessionsScreen(controller: scope.controller),
      ),
    ),
    // Archived conversations are a filter of All conversations (P3.12);
    // this opens it with that filter on.
    SearchEntry(
      id: 'archived-conversations',
      kind: SearchEntryKind.destination,
      icon: AppIconography.archive,
      title: l10n.searchArchivedConversations,
      parent: l10n.globalSessionsTitle,
      keywords: l10n.globalSessionsArchivedShort,
      pages: const ['global-sessions'],
      gate: (scope) =>
          scope.controller.isConnected &&
          scope.capabilities.globalSessionSearch,
      open: _screen(
        (scope) =>
            GlobalSessionsScreen(controller: scope.controller, archived: true),
      ),
    ),
    SearchEntry(
      id: 'ai-team-agents',
      kind: SearchEntryKind.destination,
      icon: AppIconography.agent,
      title: l10n.teamRolesTitle,
      // Settings › AI Team › Agents: the team's roles.
      parent: l10n.librarySettingsTitle,
      keywords: l10n.teamRolesSearchAliases,
      pages: const ['team-agents'],
      gate: (scope) => scope.hasTeam,
      open: (context, scope) {
        final team = scope.controller.orchestration;
        if (team == null) return openTeamPage(context, scope.controller);
        final projects = team.projectController;
        if (team.capabilities.projectLifecycle && projects != null) {
          return openTeamRoles(context, projects);
        }
        return pushKitPage<void>(
          context,
          (_) =>
              TeamAgentsScreen(controller: team, connection: scope.controller),
        );
      },
    ),
    SearchEntry(
      id: 'ai-team-project-demo',
      kind: SearchEntryKind.destination,
      icon: AppIconography.agent,
      title: l10n.teamProjectTryDemo,
      keywords: l10n.teamProjectDemoDisclosure,
      pages: const ['team-project-demo'],
      open: _screen(
        (scope) =>
            TeamProjectDemoScreen(preferences: scope.controller.store.prefs),
      ),
    ),
    SearchEntry(
      id: 'ai-team',
      kind: SearchEntryKind.destination,
      icon: AppIconography.agent,
      title: l10n.teamUiHomeTitle,
      // Settings › AI Team, no longer a Work section.
      parent: l10n.librarySettingsTitle,
      keywords: l10n.discoverTeamAliases,
      pages: const ['team-home', 'team-projects'],
      // Not in the index at all while the server has no plugin config.
      gate: (scope) => scope.hasTeam,
      open: (context, scope) => openTeamPage(context, scope.controller),
    ),
  ];
}
