import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart' show ProviderScope;

import '../../domain/connection_status.dart';
import '../../domain/models.dart';
import '../../domain/product_repository.dart';
import '../../domain/provider_presentation.dart';
import '../../background/live_background.dart';
import '../../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../../builtin/setup/phone_setup.dart';
import '../../builtin/setup/setup_contract.dart' show SetupProgress;
import '../../diagnostics/perf_trace.dart';
import '../../diagnostics/report_problem_startup.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/app_locale.dart' show AppLocaleStore;
import '../../state/appearance_guide.dart';
import '../../state/connection.dart';
import '../../state/external_agents.dart';
import '../../state/offline_queue.dart';
import '../../state/phrase_chips.dart';
import '../../state/profile_monitor.dart';
import '../../state/phone_host.dart' show PhoneHostKind;
import '../../domain/settings_scope.dart';
import '../../state/profiles.dart';
import '../../state/settings_access.dart';
import '../../termux/bridge.dart';
import '../app_theme.dart';
import '../early_l10n.dart';
import '../kit/kit.dart';
import '../theme_packs.dart';
import '../widgets/appearance_picker.dart';
import '../widgets/language_picker.dart'
    show languageChoiceLabel, languageChoiceNote;
import '../widgets/phone_server_card.dart'
    show
        newConversationsTitle,
        savedOnNote,
        sectionOnTitle,
        serverDisplayName,
        useMyDefaultDoneNote;
import '../widgets/product_states.dart';
import '../widgets/safety_confirms.dart';
import 'app_diagnostics_screen.dart' show reportProblemErrorCount;
import 'settings/ai_setup_screen.dart';
import 'settings/glass_choice_control.dart';
import 'settings/server_plugins_section.dart';
import 'automation_settings_screen.dart' show AutomationSettingsSection;
import 'keep_running_screen.dart' show KeepRunningSection;
import 'host_management_screen.dart';
import 'agents/agents_screen.dart' show AgentsScreen;
import 'servers_screen.dart' show ServersRouteRequest;
import 'capabilities_screen.dart' show openExternalAgents;
import '../widgets/pickers.dart' show showModelPicker;
import '../widgets/termux_running_server_entry.dart' show isPhoneOwnServer;
import 'server_capabilities_screen.dart';
import 'this_phone_screen.dart' show openThisPhone;
import '../widgets/team_discover.dart';
import '../search/search_index.dart';
import '../teaching/teaching.dart' show offerMakeItYours;

part 'settings/server_host_update.dart';
part 'settings/default_shell_row.dart';
part 'settings/phrase_chips_row.dart';
part 'settings/notifications_settings_screen.dart';
part 'settings/appearance_screen.dart';
part 'settings/appearance_one_page.dart';
part 'settings/personal_settings_screens.dart';
part 'settings/hub_servers.dart';
part 'settings/server_page.dart';
part 'settings/agents_permissions_screens.dart';
part 'settings/settings_group_page.dart';

AppLocalizations _settingsCopy(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));

/// The groups of the Settings hub (Settings Standard G1-G6): five groups,
/// each answering one question, with a short plain label that never changes
/// with the server.
enum SettingsGroup {
  /// What am I connected to? Servers and this phone.
  connections('connections'),

  /// Who answers, with what, and what does it cost? Model, providers, usage,
  /// tools and agents.
  ai('ai'),

  /// How chats look and behave.
  chat('chat'),

  /// How the app behaves on this phone.
  thisApp('this-app'),

  /// Where do I get help? Setup guide, Report a problem and About.
  help('help');

  const SettingsGroup(this.slug);

  /// Stable name used in the `settings-group-<slug>` widget key.
  final String slug;
}

/// The one Settings hub (vision 2026-10-20, concept C plus one value line):
/// a search field and five one-word rows, Server, Agent, Conversations,
/// This app and Help. Only Server says more, where the server in use runs
/// and how it is connected, because that is a state people check. Each row
/// opens its group's page ([SettingsGroupScreen]) with every row that group
/// held before; the search finds any setting in the same index the
/// header's search reads, so the hub needs no section headers, sub-rows or
/// summaries.
///
/// It is the last tab of the shell ([embedded]) and the screen every other
/// entry point pushes, so a setting has exactly one home. With
/// [initialGroup] (a search result that acts in place, like a switch) it is
/// that group's page. From expanded it is [KitScreen.twoPane]: the search
/// and the five rows are the list pane and the chosen group's rows fill
/// the detail pane.
/// Test seam: builds of the Settings hub.
@visibleForTesting
int settingsBuildCount = 0;

class SettingsScreen extends StatefulWidget {
  final ConnectionController controller;

  /// True inside the shell's tab view, which already supplies the app bar.
  final bool embedded;

  /// The group to open on, for entry points that mean one area: its page on
  /// a phone, its rows in the detail pane on a wide window.
  final SettingsGroup? initialGroup;

  const SettingsScreen({
    super.key,
    required this.controller,
    this.embedded = false,
    this.initialGroup,
  });

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  /// The tab is offstage (TickerMode off); notifications only mark it stale.
  bool _visible = true;

  /// The group in the detail pane (expanded and wider).
  SettingsGroup? _selected;

  /// The settled search; empty shows the five rows.
  String _query = '';
  final _search = TextEditingController();

  PerfSpanHandle? _openSpan;

  @override
  void initState() {
    super.initState();
    _selected = widget.initialGroup;
    _openSpan = PerfTrace.begin('settings.open');
    widget.controller.addListener(_connectionChanged);
    widget.controller.profileMonitor.addListener(_connectionChanged);
  }

  void _connectionChanged() {
    if (!mounted) return;
    if (!_visible) return;
    // The monitor can speak while another page is mid-build; redraw after.
    if (SchedulerBinding.instance.schedulerPhase ==
        SchedulerPhase.persistentCallbacks) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _visible) setState(() {});
      });
      return;
    }
    setState(() {});
  }

  @override
  void dispose() {
    widget.controller.removeListener(_connectionChanged);
    widget.controller.profileMonitor.removeListener(_connectionChanged);
    _search.dispose();
    _openSpan?.finish(attrs: {'outcome': 'closed'});
    super.dispose();
  }

  Future<void> _open(Widget screen) async {
    await pushKitPage<void>(context, (_) => screen);
    if (mounted) setState(() {});
  }

  /// A result opens what the header's search opens for it, then redraws:
  /// the Server line may have changed.
  Future<void> _openResult(SearchEntry entry, SearchScope scope) async {
    await entry.open(context, scope);
    if (mounted) setState(() {});
  }

  static IconData _groupIcon(SettingsGroup group) => switch (group) {
    SettingsGroup.connections => AppIconography.server,
    SettingsGroup.ai => AppIconography.agent,
    SettingsGroup.chat => AppIconography.chat,
    SettingsGroup.thisApp => AppIconography.phone,
    SettingsGroup.help => AppIconography.question,
  };

  /// The Server row's second line: where the server in use runs and how it
  /// is connected, in the server switcher's words. Nothing saved: not set
  /// up yet. Saved but none in use: only how many.
  String _serverLine(ConnectionController controller) {
    final copy = _settingsCopy(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final servers = settingsServers(controller);
    if (servers.isEmpty) return copy.settingsHubServerNone;
    final profile = controller.profile;
    if (profile == null) return l10n.settingsV3ServersCount(servers.length);
    final where = isPhoneOwnServer(profile)
        ? copy.settingsHubServerOnPhone
        : KitBidi.auto(serverDisplayName(profile, l10n, among: servers));
    final state = switch (controller.connectionStatus.phase) {
      ConnectionStatusPhase.hidden => null,
      ConnectionStatusPhase.connected => copy.e7WorkspaceConnected,
      ConnectionStatusPhase.connecting => copy.e7WorkspaceConnecting,
      ConnectionStatusPhase.reconnecting => copy.mcpReconnecting,
      _ => copy.e7WorkspaceOffline,
    };
    return state == null ? where : copy.settingsHubServerLine(where, state);
  }

  /// The settings a query finds: rows and what sits inside their pages,
  /// not the app's places (the header's search has those).
  List<SearchEntry> _results(SearchScope scope) {
    if (_query.isEmpty) return const [];
    return [
      for (final entry in searchEntries(_settingsCopy(context), scope, _query))
        if (entry.kind != SearchEntryKind.destination) entry,
    ];
  }

  /// Where a result lives, under its name: the group's word for a row of
  /// the hub, the page's title for a setting inside one.
  String? _resultPlace(SearchEntry entry) {
    final group = entry.group;
    if (entry.kind == SearchEntryKind.hubRow && group != null) {
      return settingsGroupTitle(_settingsCopy(context), group);
    }
    return entry.parent;
  }

  Widget _searchField({required int? resultCount}) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
      child: KitSearchField(
        key: const ValueKey('settings-hub-search'),
        fieldKey: const ValueKey('settings-hub-search-field'),
        label: _settingsCopy(context).settingsHubSearch,
        controller: _search,
        prominent: true,
        resultCount: resultCount,
        onChanged: (query) => setState(() => _query = query.trim()),
        onSubmitted: (_) {
          final first = _results(
            SearchScope.of(context, widget.controller),
          ).firstOrNull;
          if (first != null) {
            unawaited(
              _openResult(first, SearchScope.of(context, widget.controller)),
            );
          }
        },
      ),
    );
  }

  /// The five rows, or the results while there is a query.
  Widget _body({
    required List<SettingsGroup> groups,
    required List<SearchEntry> results,
    required SearchScope scope,
    required bool wide,
    required SettingsGroup? selected,
  }) {
    final copy = _settingsCopy(context);
    final large = MediaQuery.textScalerOf(context).scale(10) >= 18;
    final Widget child;
    if (_query.isNotEmpty && results.isEmpty) {
      child = KitSearchNoMatch(
        key: const ValueKey('settings-hub-no-match'),
        query: _query,
        onClear: _search.clear,
      );
    } else if (_query.isNotEmpty) {
      child = KitRowGroup(
        key: const ValueKey('settings-hub-results'),
        children: [
          for (final entry in results)
            KitRow(
              key: ValueKey('settings-hub-result-${entry.id}'),
              leading: KitRow.glyph(context, entry.icon),
              title: entry.title,
              supporting: switch (_resultPlace(entry)) {
                final place? => TextSpan(text: place),
                null => null,
              },
              trailing: const KitChevron(),
              onTap: () => unawaited(_openResult(entry, scope)),
            ),
        ],
      );
    } else {
      child = KitRowGroup(
        key: const ValueKey('settings-hub-rows'),
        leadingIcons: !large,
        children: [
          for (final group in groups)
            KitRow(
              key: ValueKey('settings-hub-row-${group.slug}'),
              roomy: true,
              // From 2x text the words need the row: a glyph or a chevron
              // would break "Conversations" mid-word at 320 dp.
              leading: large ? null : KitRow.glyph(context, _groupIcon(group)),
              title: settingsGroupTitle(copy, group),
              supporting: group == SettingsGroup.connections
                  ? TextSpan(text: _serverLine(widget.controller))
                  : null,
              supportingKey: group == SettingsGroup.connections
                  ? const ValueKey('settings-hub-server-line')
                  : null,
              // The whole row is the door; at 2x text the chevron's room
              // goes to the word.
              trailing: large ? null : const KitChevron(),
              selected: wide && group == selected,
              onTap: wide
                  ? () => setState(() => _selected = group)
                  : () => unawaited(
                      _open(
                        SettingsGroupScreen(
                          controller: widget.controller,
                          group: group,
                        ),
                      ),
                    ),
            ),
        ],
      );
    }
    // The rows and the results cross-fade (KitMotion.quick) instead of
    // popping; instant under reduced motion.
    return KitSwap(
      alignment: AlignmentDirectional.topStart,
      child: KeyedSubtree(key: ValueKey<bool>(_query.isEmpty), child: child),
    );
  }

  @override
  Widget build(BuildContext context) {
    settingsBuildCount++;
    _visible = TickerMode.valuesOf(context).enabled;
    if (_openSpan != null) {
      final span = _openSpan!;
      _openSpan = null;
      WidgetsBinding.instance.addPostFrameCallback((_) => span.finish());
    }
    final controller = widget.controller;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final copy = _settingsCopy(context);
    final tokens = KitTokens.of(context);
    final wide = KitScreen.showsDetail(context);
    final initial = widget.initialGroup;

    // A phone opened at one group (a search result that acts in place) is
    // that group's page.
    if (!wide && !widget.embedded && initial != null) {
      return SettingsGroupScreen(controller: controller, group: initial);
    }

    final scope = SearchScope.of(context, controller);
    final present = {for (final entry in searchIndex(copy, scope)) entry.id};
    final groups = [
      for (final group in SettingsGroup.values)
        if (settingsGroupRowIds[group]!.any(present.contains)) group,
    ];
    final results = _results(scope);
    final selected = groups.contains(_selected)
        ? _selected
        : groups.firstOrNull;

    // As a tab of the shell the page has no top bar, so it starts with its
    // own large title like Chats and Files do.
    final tabTitle = widget.embedded
        ? Padding(
            padding: EdgeInsetsDirectional.fromSTEB(
              tokens.gutter,
              tokens.space2,
              tokens.gutter,
              tokens.space4,
            ),
            child: Semantics(
              header: true,
              child: KitText(
                l10n.librarySettingsTitle,
                key: const ValueKey('settings-tab-title'),
                role: KitTextRole.largeTitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          )
        : null;

    final hub = KitScrollArea(
      builder: (scrollController) => ListView(
        key: const ValueKey('settings-hub-list'),
        controller: scrollController,
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          ?tabTitle,
          // No count line over "Nothing matches": the same fact twice.
          _searchField(
            resultCount: _query.isEmpty || results.isEmpty
                ? null
                : results.length,
          ),
          SizedBox(height: tokens.space4),
          _body(
            groups: groups,
            results: results,
            scope: scope,
            wide: wide,
            selected: selected,
          ),
        ],
      ),
    );

    final topBar = widget.embedded
        ? null
        : KitTopBar(title: l10n.librarySettingsTitle);

    if (!wide) {
      return KitScreen(
        topBar: topBar,
        width: KitScreenWidth.reading,
        body: hub,
      );
    }

    // Expanded and wider: the search and the five rows are the list, the
    // chosen group's rows fill the detail pane.
    return KitScreen.twoPane(
      topBar: topBar,
      listPaneKey: const ValueKey('settings-list-pane'),
      detailPaneKey: const ValueKey('settings-detail-pane'),
      list: hub,
      detail: selected == null
          ? null
          : _SettingsGroupBody(
              key: ValueKey('settings-detail-body-${selected.slug}'),
              controller: controller,
              group: selected,
              detailPane: true,
            ),
      emptyDetail: KitStateView(
        key: const ValueKey('settings-detail-empty'),
        icon: AppIconography.search,
        title: l10n.settingsHubDetailEmpty,
      ),
    );
  }
}

/// One settings row on the kit (design standard §6): an icon, the title, an
/// explanation of up to two lines or a short value at the end, and a
/// chevron when it opens a screen.
class _CategoryRow extends StatelessWidget {
  final String rowKey;

  /// Null on an inner page, whose rows carry no icon tile (R4).
  final IconData? icon;
  final String title;
  final String? subtitle;

  /// What the row is set to now, before the chevron ([KitRowValue]).
  final String? value;
  final VoidCallback? onTap;

  final bool enabled;

  /// A count badge before the chevron, with [badgeLabel] as its words.
  final int? badge;
  final String? badgeLabel;

  const _CategoryRow({
    required this.rowKey,
    this.icon,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.value,
    this.enabled = true,
    this.badge,
    this.badgeLabel,
  });

  @override
  Widget build(BuildContext context) {
    final value = this.value;
    final hasValue = value != null && value.isNotEmpty;
    // At large text the value moves under the title, so neither is cut to
    // a few letters (A11Y-8).
    final large = MediaQuery.textScalerOf(context).scale(10) > 13;
    final subtitle = hasValue && large ? value : this.subtitle;
    return KitRow(
      key: ValueKey(rowKey),
      leading: icon == null ? null : KitRow.icon(context, icon!),
      title: title,
      titleMaxLines: large ? 2 : 1,
      supporting: subtitle == null ? null : TextSpan(text: subtitle),
      supportingMaxLines: 2,
      trailing: (badge ?? 0) > 0
          ? KitRowValue.count(badge!, badgeLabel ?? '$badge')
          : hasValue && !large
          ? KitRowValue(value)
          : const KitChevron(),
      enabled: enabled,
      onTap: onTap,
    );
  }
}

/// Settings' Report a problem row (P8.2): the one way in besides the
/// failure states, with a badge counting the errors kept on this phone.
class _ReportProblemRow extends StatelessWidget {
  const _ReportProblemRow({
    required this.entry,
    required this.controller,
    required this.onTap,
  });

  final SearchEntry entry;
  final ConnectionController controller;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    final store = ReportProblemStartup.current?.report;
    return ListenableBuilder(
      listenable: Listenable.merge([controller.diagnostics, ?store]),
      builder: (context, _) {
        final count = reportProblemErrorCount(
          store: store,
          diagnostics: controller.diagnostics,
        );
        return _CategoryRow(
          rowKey: entry.id,
          icon: entry.icon,
          title: entry.title,
          badge: count,
          badgeLabel: copy.reportProblemErrorBadge(count),
          onTap: onTap,
        );
      },
    );
  }
}

class _ShellChoice {
  final String id;
  final String value;
  final String label;
  final bool terminalOnly;

  const _ShellChoice({
    required this.id,
    required this.value,
    required this.label,
    required this.terminalOnly,
  });
}
