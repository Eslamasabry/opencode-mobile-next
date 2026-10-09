import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart' show ProviderScope;

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../api/provider_presentation.dart';
import '../../background/live_background.dart';
import '../../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../../builtin/setup/phone_setup.dart';
import '../../builtin/setup/setup_contract.dart' show SetupProgress;
import '../../diagnostics/perf_trace.dart';
import '../../diagnostics/report_problem_startup.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/interaction_defaults.dart' show DefaultReason;
import '../../state/app_locale.dart' show AppLocaleStore;
import '../../state/connection.dart';
import '../../state/offline_queue.dart';
import '../../state/profile_monitor.dart';
import '../../state/phone_host.dart' show PhoneHostKind;
import '../../state/profiles.dart';
import '../../termux/bridge.dart';
import '../app_theme.dart';
import '../early_l10n.dart';
import '../kit/kit.dart';
import '../theme_packs.dart';
import '../widgets/appearance_picker.dart';
import '../widgets/default_notices.dart' show modelDefaultOf;
import '../widgets/language_picker.dart'
    show languageChoiceLabel, languageChoiceNote;
import '../widgets/phone_server_card.dart' show serverDisplayName;
import '../widgets/product_states.dart';
import '../widgets/safety_confirms.dart';
import 'app_diagnostics_screen.dart' show reportProblemErrorCount;
import 'settings/ai_setup_screen.dart';
import 'settings/server_plugins_section.dart';
import 'automation_settings_screen.dart' show AutomationSettingsSection;
import 'keep_running_screen.dart' show KeepRunningSection;
import 'host_management_screen.dart';
import 'servers_screen.dart' show ServersRouteRequest;
import 'server_capabilities_screen.dart';
import 'this_phone_screen.dart' show openThisPhone;
import '../widgets/team_discover.dart';
import '../search/search_index.dart';
import 'usage_hub_screen.dart';

part 'settings/server_settings_screen.dart';
part 'settings/default_shell_row.dart';
part 'settings/notifications_settings_screen.dart';
part 'settings/personal_settings_screens.dart';

AppLocalizations _settingsCopy(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));

/// The groups of the Settings hub (target-ia §1.3): at most five rows each,
/// ordered as the page reads. The first is labelled with the connected
/// server's name, as the approved canvas draws it
/// (docs/design/visual-language-2026-09-26/Settings.png); the last panel,
/// Help, has no label.
enum SettingsGroup {
  /// This server, every saved server, and this phone (Android).
  server('server'),

  /// The model, providers and accounts, tools, and the AI Team.
  agent('agent'),

  /// The two transcript switches, the default shell and voice.
  conversations('conversations'),

  /// Notifications and background, appearance, privacy and usage of this app.
  thisApp('this-app'),

  /// Setup guide, Report a problem, Available on this server and About; no
  /// label.
  help('help');

  const SettingsGroup(this.slug);

  /// Stable name used in the `settings-group-<slug>` widget key.
  final String slug;
}

/// The one Settings hub: five groups of rows. Search is the header's
/// command launcher, which reads the same index. It is the
/// fourth tab of the shell ([embedded]) and the screen every other entry
/// point pushes, so a setting has exactly one home. Rows the connected server
/// cannot serve are absent, and the group says how many under its panel in
/// one muted line with a Why (target-ia §1.3); a group with no rows is
/// absent.
///
/// Kit only (screen-settings-1): a [KitScreen] with one [KitRowGroup] panel
/// per group. From expanded it
/// is [KitScreen.twoPane]: the groups are the list pane and the chosen
/// group's rows fill the detail pane; a row still opens its page.
/// Test seam: builds of the Settings hub.
@visibleForTesting
int settingsBuildCount = 0;

class SettingsScreen extends StatefulWidget {
  final ConnectionController controller;

  /// True inside the shell's tab view, which already supplies the app bar.
  final bool embedded;

  /// A group to scroll to on open, for entry points that mean one area.
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
  final _groupKeys = {
    for (final group in SettingsGroup.values)
      group: GlobalKey(debugLabel: 'settings-group-${group.slug}'),
  };
  Health? _health;
  String? _healthError;
  bool _checking = false;

  /// True while the loading bar is shown (a first check, not a refresh).
  bool _blocking = false;

  /// The tab is offstage (TickerMode off); notifications only mark it stale.
  bool _visible = true;

  /// The group in the detail pane (expanded and wider).
  SettingsGroup? _selected;

  @override
  void initState() {
    super.initState();
    _selected = widget.initialGroup;
    _openSpan = PerfTrace.begin('settings.open');
    _health = serverHealthCache[widget.controller.profile?.id];
    widget.controller.addListener(_connectionChanged);
    _checkHealth();
    final initial = widget.initialGroup;
    if (initial != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        final target = _groupKeys[initial]?.currentContext;
        if (mounted && target != null) Scrollable.ensureVisible(target);
      });
    }
  }

  PerfSpanHandle? _openSpan;

  void _connectionChanged() {
    if (!mounted) return;
    if (!_visible) return;
    setState(() {});
  }

  @override
  void dispose() {
    widget.controller.removeListener(_connectionChanged);
    _openSpan?.finish(attrs: {'outcome': 'closed'});
    super.dispose();
  }

  Future<void> _checkHealth() async {
    // Runs from initState, so inherited lookups are not yet allowed.
    final copy = earlyAppLocalizations(context);
    if (_checking) return;
    final cached = _health != null;
    final span = PerfTrace.begin('settings.health', attrs: {'cached': cached});
    setState(() {
      _checking = true;
      _blocking = !cached;
      _healthError = null;
    });
    try {
      final api = await widget.controller.prepareActionTransport();
      if (api == null) {
        throw ProductException(copy.e7SettingsUi18);
      }
      final health = await api.health();
      final id = widget.controller.profile?.id;
      if (id != null) serverHealthCache[id] = health;
      if (mounted) setState(() => _health = health);
      span.finish();
    } catch (error) {
      span.finish(error: error);
      // A failed check outdates the cached answer: the row loses its
      // success colour along with the version it can no longer vouch for.
      serverHealthCache.remove(widget.controller.profile?.id);
      if (mounted) {
        setState(() {
          _health = null;
          _healthError = productErrorText(error);
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _checking = false;
          _blocking = false;
        });
      }
    }
  }

  /// The background state at a glance, so the hub says whether runs keep
  /// updating after the app closes without opening the page.
  String _backgroundSummary(ConnectionController controller) {
    final live = controller.backgroundLive;
    if (live.stoppedByAndroidTimeout) {
      return _settingsCopy(context).e7SettingsUi12;
    }
    if (!controller.keepLiveInBackground) {
      return _settingsCopy(context).quotaBudgetOff;
    }
    return live.active
        ? _settingsCopy(context).e7SettingsUi14
        : _settingsCopy(context).e7SettingsUi15;
  }

  /// The Model row's value: the default model's name (the picker also
  /// sets its variant and agent).
  String _modelSummary(ConnectionController controller) {
    final copy = _settingsCopy(context);
    final selected = controller.selectedModel;
    final model = selected == null
        ? copy.modelServerDefault
        : controller.catalog?.models
                  .where(
                    (model) =>
                        model.providerID == selected.providerID &&
                        model.id == selected.modelID,
                  )
                  .firstOrNull
                  ?.name ??
              presentedModelLabel(selected.providerID, selected.modelID);
    return model;
  }

  Future<void> _open(Widget screen) async {
    await pushKitPage<void>(context, (_) => screen);
    if (mounted) setState(() {});
  }

  /// Runs an index entry, then redraws: most of them change something a row's
  /// subtitle shows.
  Future<void> _openEntry(SearchEntry entry, SearchScope scope) async {
    await entry.open(context, scope);
    if (mounted) setState(() {});
  }

  Widget _thisServerRow(ConnectionController controller) {
    final copy = _settingsCopy(context);
    final healthy = _health?.healthy == true;
    final status = _blocking
        ? copy.e7SettingsUi11
        : _healthError != null
        ? copy.e7SettingsHealthError(_healthError!)
        : healthy
        ? copy.e7SettingsHealthVersion(
            _health?.version ?? controller.version ?? copy.e7SettingsUi17,
          )
        : copy.e7SettingsVersion(controller.version ?? copy.e7SettingsUi17);
    return KeyedSubtree(
      key: const ValueKey('settings-connection-summary'),
      child: KitRow(
        key: const ValueKey('settings-category-server'),
        // Healthy is the success colour with its word; a failed check keeps
        // the neutral glyph and says so in words (LOOK-5).
        leading: KitRow.icon(
          context,
          AppIconography.server,
          color: healthy ? ThemeRoles.of(context).success : null,
        ),
        title: copy.settingsHubThisServer,
        // The group above is labelled with the server's name, so the row
        // says only how it is (R3).
        supporting: TextSpan(
          text: controller.profile == null ? copy.e7SettingsUi9 : status,
        ),
        // A failed check says why in a whole sentence (no raw errors):
        // three lines keep "…or report the problem." from being cut off.
        supportingMaxLines: _healthError != null ? 3 : 2,
        // A failed probe offers the one fix in place; otherwise the row is a
        // plain door like its neighbours. The check itself shows as the
        // screen's one loading bar, not a spinner in the row.
        trailing: _healthError != null
            ? KitIconButton(
                key: const ValueKey('settings-server-try-again'),
                tooltip: copy.commonRetry,
                icon: AppIconography.retry,
                onPressed: _checkHealth,
              )
            : const KitChevron(),
        onTap: () => _open(ServerSettingsScreen(controller: controller)),
      ),
    );
  }

  /// The hub's layout: which index entries are rows, in which order, and what
  /// each shows beside its title. Titles, keywords, gates and what a tap does
  /// come from the search index, so the hub, its search and every other
  /// search surface cannot disagree. A row whose entry is gated out is absent.
  List<_HubGroup> _groups(
    ConnectionController controller,
    Map<String, SearchEntry> entries,
    SearchScope scope,
  ) {
    final copy = _settingsCopy(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    _HubRow? row(
      String id, {
      String? value,
      String? subtitle,
      WidgetBuilder? builder,
    }) {
      final entry = entries[id];
      if (entry == null) return null;
      return _HubRow(
        entry: entry,
        value: value,
        subtitle: subtitle,
        builder: builder,
        onTap: () => _openEntry(entry, scope),
      );
    }

    final profile = controller.profile;
    final titles = {
      // The server's own name, as the canvas labels it ("Laptop").
      SettingsGroup.server: profile == null
          ? copy.settingsHubThisServer
          : serverDisplayName(profile, l10n, among: controller.store.profiles),
      SettingsGroup.agent: copy.settingsHubGroupAgent,
      SettingsGroup.conversations: copy.settingsHubGroupConversations,
      SettingsGroup.thisApp: copy.settingsHubGroupThisApp,
      SettingsGroup.help: copy.settingsHubGroupHelp,
    };
    // Short values at the row's end instead of two-line subtitles (canvas):
    // "Claude Sonnet 4", "On", "Dark".
    final rows = <SettingsGroup, List<_HubRow?>>{
      SettingsGroup.server: [
        row(
          'settings-category-server',
          builder: (_) => _thisServerRow(controller),
        ),
        row('settings-saved-servers'),
        row('settings-this-phone'),
        // Agents on this phone: always findable on a phone; where they
        // cannot run (a remote server, Termux) the row says so.
        row(
          'settings-agents',
          subtitle: controller.phoneAgentsAvailable
              ? null
              : copy.agentsRunOnBuiltIn,
        ),
      ],
      SettingsGroup.agent: [
        // The model the app uses without having asked (P6.6): its name,
        // and why it is that one when the person did not pick it. The row
        // opens the model sheet to change it.
        row(
          'settings-model-and-mode',
          value: _modelSummary(controller),
          subtitle:
              controller.selectedModel != null &&
                  modelDefaultOf(controller).reason ==
                      DefaultReason.serverDefault
              ? copy.modelServerDefault
              : null,
        ),
        row('settings-providers'),
        row('settings-tools'),
        // The AI Team is findable here whether it is on or off, in the
        // state words Settings › Tools › Plugins uses (teamStateLine):
        // "Turning on…" while this phone installs it, "On · This phone",
        // "Off".
        row(
          'settings-ai-team',
          builder: (context) {
            Widget build(SetupProgress? setup) => _CategoryRow(
              rowKey: 'settings-ai-team',
              icon: AppIconography.agent,
              title: l10n.teamUiHomeTitle,
              value: teamStateLine(l10n, controller, setup: setup),
              onTap: () => _openEntry(entries['settings-ai-team']!, scope),
            );

            if (profile == null ||
                teamServerKindOf(profile) != TeamServerKind.inApp) {
              return build(null);
            }
            return ValueListenableBuilder<SetupProgress>(
              valueListenable: PhoneSetup.engine.progress,
              builder: (context, setup, _) => build(setup),
            );
          },
        ),
      ],
      SettingsGroup.conversations: [
        // Two switches in place of the old Transcript display sheet: the
        // same stored values the conversation menu flips, for every
        // conversation on this device.
        row(
          'settings-show-reasoning',
          builder: (context) => KitSwitchRow(
            key: const ValueKey('settings-show-reasoning'),
            leading: KitRow.icon(context, AppIconography.idea),
            title: copy.settingsHubShowReasoning,
            supporting: copy.transcriptTogglesReasoningOn,
            value: controller.transcriptReasoningExpanded,
            onChanged: (value) =>
                unawaited(controller.setTranscriptReasoningExpanded(value)),
          ),
        ),
        row(
          'settings-show-timestamps',
          builder: (context) => KitSwitchRow(
            key: const ValueKey('settings-show-timestamps'),
            leading: KitRow.icon(context, AppIconography.clock),
            title: copy.settingsHubShowTimestamps,
            supporting: copy.transcriptTogglesUsageOn,
            value: controller.transcriptTimestampsVisible,
            onChanged: (value) =>
                unawaited(controller.setTranscriptTimestampsVisible(value)),
          ),
        ),
        row(
          'default-shell-settings-entry',
          builder: (_) => DefaultShellRow(controller: controller),
        ),
        row('settings-voice'),
      ],
      SettingsGroup.thisApp: [
        // One screen for everything that notifies or keeps the app running:
        // notifications, keep running and what runs by itself. The key
        // predates the merge and is kept for tests and deep links.
        row(
          'settings-category-background',
          value: platformCapabilities.supportsBackgroundService
              ? copy.notifyHubBackgroundSummary(_backgroundSummary(controller))
              : null,
        ),
        row(
          'settings-category-appearance',
          value: appearanceLabel(controller.appearance.value, context),
        ),
        row('settings-category-privacy'),
        // The value names the sections this connection really has.
        row(
          'settings-category-usage',
          value: [
            for (final section in UsageHubScreen.sectionsFor(controller))
              switch (section) {
                UsageSection.spent => copy.usageSectionSpent,
                UsageSection.remaining => copy.usageSectionRemaining,
              },
          ].join(' · '),
        ),
      ],
      SettingsGroup.help: [
        row('settings-setup-guide'),
        // Report a problem: every failure state offers it too; this row is
        // the deliberate path, with the count of errors kept (P8.2).
        row(
          'library-report-bug',
          builder: (context) => _ReportProblemRow(
            entry: entries['library-report-bug']!,
            controller: controller,
            onTap: () => _openEntry(entries['library-report-bug']!, scope),
          ),
        ),
        row('settings-server-capabilities'),
        row('settings-about-notices'),
      ],
    };
    // Rows the connected server hides, counted per group for its one line.
    // Without a saved server there is no server to blame.
    final hidden = <SettingsGroup, int>{};
    if (profile != null) {
      for (final entry in allSearchEntries(copy)) {
        final group = entry.group;
        if (entry.kind == SearchEntryKind.hubRow &&
            group != null &&
            entry.hiddenByServer(scope)) {
          hidden[group] = (hidden[group] ?? 0) + 1;
        }
      }
    }
    return [
      for (final group in SettingsGroup.values)
        _HubGroup(
          group,
          titles[group]!,
          rows[group]!.nonNulls.toList(),
          hidden: hidden[group] ?? 0,
        ),
    ];
  }

  static IconData _groupIcon(SettingsGroup group) => switch (group) {
    SettingsGroup.server => AppIconography.server,
    SettingsGroup.agent => AppIconography.model,
    SettingsGroup.conversations => AppIconography.chat,
    SettingsGroup.thisApp => AppIconography.phone,
    SettingsGroup.help => AppIconography.support,
  };

  /// The group's one line for the rows the server hides: how many, and a
  /// Why that opens Available on this server at the top.
  Widget _unavailableNote(_HubGroup group) {
    final copy = _settingsCopy(context);
    return KitGroupNote(
      key: ValueKey('settings-unavailable-${group.group.slug}'),
      message: copy.settingsHubUnavailableCount(group.hidden),
      action: KitAction(
        key: ValueKey('settings-unavailable-why-${group.group.slug}'),
        label: copy.settingsHubUnavailableWhy,
        onPressed: () => unawaited(
          _open(ServerCapabilitiesScreen(controller: widget.controller)),
        ),
      ),
    );
  }

  /// One group: its rows on a panel under the group's name.
  Widget _groupView(
    ({_HubGroup group, List<_HubRow> rows}) entry, {
    required bool scrollTarget,
    bool note = true,
  }) {
    // Groups without a row are dropped before they get here.
    final panel = KitRowGroup(
      key: ValueKey('settings-group-${entry.group.group.slug}'),
      // The last panel needs no name: guide, Report a problem, About.
      label: entry.group.group == SettingsGroup.help ? null : entry.group.title,
      children: [for (final row in entry.rows) row.build(context)],
    );
    final column = note && entry.group.hidden > 0
        ? Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [panel, _unavailableNote(entry.group)],
          )
        : panel;
    return scrollTarget
        ? KeyedSubtree(key: _groupKeys[entry.group.group], child: column)
        : column;
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
    final scope = SearchScope.of(context, controller);
    final entries = {
      for (final entry in searchIndex(copy, scope)) entry.id: entry,
    };
    final allGroups = [
      for (final group in _groups(controller, entries, scope))
        (group: group, rows: group.rows),
    ].where((entry) => entry.rows.isNotEmpty).toList();
    final groups = allGroups;
    final wide = KitScreen.showsDetail(context);

    // Not a lazy list: every group must exist for an entry point to scroll
    // to it, and the hub is a few dozen plain rows. One Column child keeps
    // them all laid out inside the scroll view.
    Widget hubList({required bool scrollTargets}) => KitScrollArea(
      builder: (scrollController) => ListView(
        key: const ValueKey('settings-hub-list'),
        controller: scrollController,
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final (index, entry) in groups.indexed) ...[
                if (index > 0) SizedBox(height: tokens.sectionGap),
                _groupView(entry, scrollTarget: scrollTargets),
              ],
            ],
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
        // The health check of "This server" is the one thing that loads here.
        loading: _blocking,
        loadingLabel: copy.e7SettingsUi11,
        width: KitScreenWidth.reading,
        body: hubList(scrollTargets: true),
      );
    }

    // Expanded and wider: the groups are the list, the chosen group fills
    // the detail pane.
    final selected =
        allGroups
            .where((entry) => entry.group.group == _selected)
            .firstOrNull ??
        allGroups.firstOrNull;
    final index = ListView(
      key: const ValueKey('settings-group-index'),
      padding: EdgeInsetsDirectional.only(
        top: tokens.space2,
        bottom: KitScreen.endPadding(context),
      ),
      children: [
        KitRowGroup(
          children: [
            for (final entry in allGroups)
              KitRow(
                key: ValueKey('settings-index-${entry.group.group.slug}'),
                leading: KitRow.icon(context, _groupIcon(entry.group.group)),
                // Titles only, so they fit the index pane (R5).
                title: entry.group.title,
                selected: identical(entry, selected),
                trailing: const KitChevron(),
                onTap: () => setState(() => _selected = entry.group.group),
              ),
          ],
        ),
      ],
    );
    return KitScreen.twoPane(
      topBar: topBar,
      loading: _blocking,
      loadingLabel: copy.e7SettingsUi11,
      listPaneKey: const ValueKey('settings-list-pane'),
      detailPaneKey: const ValueKey('settings-detail-pane'),
      list: index,
      detail: selected == null
          ? null
          : ListView(
              key: ValueKey('settings-detail-${selected.group.group.slug}'),
              padding: EdgeInsetsDirectional.only(
                top: tokens.space5,
                bottom: KitScreen.endPadding(context),
              ),
              children: [_groupView(selected, scrollTarget: false)],
            ),
      emptyDetail: KitStateView(
        key: const ValueKey('settings-detail-empty'),
        icon: AppIconography.search,
        title: l10n.settingsHubDetailEmpty,
      ),
    );
  }
}

class _HubGroup {
  final SettingsGroup group;
  final String title;
  final List<_HubRow> rows;

  /// How many of the group's rows the connected server hides.
  final int hidden;

  const _HubGroup(this.group, this.title, this.rows, {this.hidden = 0});
}

/// One hub row: a search-index entry plus what only the hub shows beside
/// it (a live subtitle, or a row that draws itself).
class _HubRow {
  final SearchEntry entry;

  /// What the row is set to now, at its end ("Claude Sonnet 4").
  final String? value;

  /// Why it is set so, under the title ("Server default").
  final String? subtitle;
  final VoidCallback onTap;

  /// A row that draws itself (live status or its own loading state) but is
  /// searched like any other.
  final WidgetBuilder? builder;

  const _HubRow({
    required this.entry,
    required this.onTap,
    this.value,
    this.subtitle,
    this.builder,
  });

  /// The row, marked as the place a search result arrives at (KitArrival):
  /// the switches and the shell choice act here, so their results open the
  /// hub at them.
  Widget build(BuildContext context) => KitArrival(
    id: entry.id,
    child:
        builder?.call(context) ??
        _CategoryRow(
          rowKey: entry.id,
          icon: entry.icon,
          title: entry.title,
          subtitle: subtitle,
          value: value,
          onTap: onTap,
        ),
  );
}

/// One settings row on the kit (design standard §6): an icon, the title, an
/// explanation of up to two lines or a short value at the end, and a
/// chevron when it opens a screen.
class _CategoryRow extends StatelessWidget {
  final String rowKey;
  final IconData icon;
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
    required this.icon,
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
      leading: KitRow.icon(context, icon),
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
