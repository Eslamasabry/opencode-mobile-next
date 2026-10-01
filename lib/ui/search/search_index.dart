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

part 'search_index/hub_rows.dart';
part 'search_index/inside_pages.dart';
part 'search_index/places.dart';

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
  final build = _IndexBuild(l10n);
  return [...build.hubRows(), ...build.insidePages(), ...build.places()];
}

/// The names every section of the ungated index shares: the localized
/// group titles and the catalog rows the inside-settings entries read.
class _IndexBuild {
  _IndexBuild(this.l10n)
    : notifications = l10n.settingsHubGroupNotifications,
      appearance = l10n.e7AppearanceTitle,
      usage = l10n.settingsHubGroupUsage,
      commandsAndTools = l10n.libraryCommandsToolsTitle,
      onThisPhone = l10n.onboardingTermuxSetup,
      rows = {
        for (final row in settingsSearchRows(
          l10n,
          supportsBackgroundService: true,
          thermalGuardAvailable: true,
          managedRecoveryAvailable: true,
        ))
          row.id: row,
      };

  final AppLocalizations l10n;
  final String notifications;
  final String appearance;
  final String usage;
  final String commandsAndTools;
  final String onThisPhone;
  // Rows inside pages (P9.4): their words and targets come from the shared
  // settings catalog, so Settings, the command launcher and the palette
  // find them alike.
  final Map<String, SettingsSearchDocument> rows;

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

  SettingsSearchDocument get diagnostics => rows['app-diagnostics-entry']!;
}
