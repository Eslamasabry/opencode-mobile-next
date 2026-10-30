part of '../settings_screen.dart';

/// The rows each hub group holds, in order: the index entries the group's
/// page lists. A row whose entry is gated out is absent, and a group with
/// none left has no door on the hub. Tests read it to find a row's page.
const settingsGroupRowIds = <SettingsGroup, List<String>>{
  SettingsGroup.connections: ['settings-saved-servers', 'settings-this-phone'],
  SettingsGroup.ai: [
    'settings-model-and-mode',
    'settings-providers',
    'settings-category-usage',
    'settings-tools',
    'settings-agents',
  ],
  SettingsGroup.chat: [
    'settings-show-reasoning',
    'settings-show-timestamps',
    'settings-phrase-chips',
    'default-shell-settings-entry',
    'settings-voice',
    'settings-automation',
  ],
  SettingsGroup.thisApp: [
    'settings-category-notifications',
    'settings-category-background',
    'settings-category-appearance',
    'settings-category-language',
    'settings-category-privacy',
  ],
  SettingsGroup.help: [
    'settings-setup-guide',
    'settings-tips',
    'library-report-bug',
    'settings-about-notices',
  ],
};

/// The group's one word on the hub and the title of its page.
String settingsGroupTitle(AppLocalizations copy, SettingsGroup group) =>
    switch (group) {
      SettingsGroup.connections => copy.settingsHubServer,
      SettingsGroup.ai => copy.settingsHubGroupAgent,
      SettingsGroup.chat => copy.settingsHubGroupConversations,
      SettingsGroup.thisApp => copy.settingsHubGroupThisApp,
      SettingsGroup.help => copy.settingsHubHelp,
    };

/// How many of [group]'s rows the connected server hides. Without a
/// connection there is no server to blame.
int _hiddenInGroup(
  AppLocalizations copy,
  ConnectionController controller,
  SearchScope scope,
  SettingsGroup group,
) {
  if (!controller.isConnected) return 0;
  var hidden = 0;
  for (final entry in allSearchEntries(copy)) {
    if (entry.kind == SearchEntryKind.hubRow &&
        entry.group == group &&
        entry.hiddenByServer(scope)) {
      hidden++;
    }
  }
  return hidden;
}

/// One group of Settings on its own page (vision 2026-10-20): the hub is a
/// search and five words, and each word opens the rows that group holds,
/// with the same values, switches, gates and "By server" pages as before.
class SettingsGroupScreen extends StatelessWidget {
  const SettingsGroupScreen({
    super.key,
    required this.controller,
    required this.group,
  });

  final ConnectionController controller;
  final SettingsGroup group;

  @override
  Widget build(BuildContext context) => KitScreen(
    topBar: KitTopBar(title: settingsGroupTitle(_settingsCopy(context), group)),
    width: KitScreenWidth.reading,
    body: _SettingsGroupBody(controller: controller, group: group),
  );
}

/// A group's rows, the muted line for the rows this server hides and, on
/// the Server page once several are saved, each server's own page.
class _SettingsGroupBody extends StatefulWidget {
  const _SettingsGroupBody({
    super.key,
    required this.controller,
    required this.group,
    this.detailPane = false,
  });

  final ConnectionController controller;
  final SettingsGroup group;

  /// In the detail pane of a wide window, under no top bar of its own.
  final bool detailPane;

  @override
  State<_SettingsGroupBody> createState() => _SettingsGroupBodyState();
}

class _SettingsGroupBodyState extends State<_SettingsGroupBody> {
  /// The page is offstage (TickerMode off); notifications only mark it
  /// stale.
  bool _visible = true;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_connectionChanged);
    widget.controller.profileMonitor.addListener(_connectionChanged);
  }

  @override
  void didUpdateWidget(_SettingsGroupBody old) {
    super.didUpdateWidget(old);
    if (old.controller != widget.controller) {
      old.controller.removeListener(_connectionChanged);
      old.controller.profileMonitor.removeListener(_connectionChanged);
      widget.controller.addListener(_connectionChanged);
      widget.controller.profileMonitor.addListener(_connectionChanged);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_connectionChanged);
    widget.controller.profileMonitor.removeListener(_connectionChanged);
    super.dispose();
  }

  void _connectionChanged() {
    if (!mounted || !_visible) return;
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

  /// The Model row's value: the default model's name (the picker also
  /// sets its variant and agent). A model the person never chose is the
  /// server's own pick, and only then does the row say so.
  String _modelSummary(ConnectionController controller) {
    final copy = _settingsCopy(context);
    final selected = controller.selectedModel;
    if (selected == null) return copy.modelServerDefault;
    return controller.catalog?.models
            .where(
              (model) =>
                  model.providerID == selected.providerID &&
                  model.id == selected.modelID,
            )
            .firstOrNull
            ?.name ??
        readableModelLabel(selected.providerID, selected.modelID);
  }

  /// The background state at a glance, so the row says whether runs keep
  /// updating after the app closes without opening the page.
  String _backgroundSummary(ConnectionController controller) {
    final copy = _settingsCopy(context);
    final live = controller.backgroundLive;
    if (live.stoppedByAndroidTimeout) return copy.e7SettingsUi12;
    if (!controller.keepLiveInBackground) return copy.quotaBudgetOff;
    return live.active ? copy.e7SettingsUi14 : copy.e7SettingsUi15;
  }

  /// Runs an index entry, then redraws: most of them change something a
  /// row's value shows.
  Future<void> _openEntry(SearchEntry entry, SearchScope scope) async {
    await entry.open(context, scope);
    if (mounted) setState(() {});
  }

  Future<void> _open(Widget screen) async {
    await pushKitPage<void>(context, (_) => screen);
    if (mounted) setState(() {});
  }

  /// The group's rows: which index entries, and what each shows beside its
  /// title. Titles, keywords, gates and what a tap does come from the
  /// search index, so the page, its search and every other search surface
  /// cannot disagree.
  ///
  /// Rows are one line (Settings Standard D1): the name at the start, its
  /// state at the end. With several saved servers the server-owned rows say
  /// "By server" and open a page that lists each one (SC4).
  List<_HubRow> _rows(
    ConnectionController controller,
    Map<String, SearchEntry> entries,
    SearchScope scope,
  ) {
    final copy = _settingsCopy(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final several = hasSeveralServers(controller);
    _HubRow? row(
      String id, {
      String? value,
      WidgetBuilder? builder,
      VoidCallback? onTap,
    }) {
      final entry = entries[id];
      if (entry == null) return null;
      return _HubRow(
        entry: entry,
        value: value,
        builder: builder,
        onTap: onTap ?? () => _openEntry(entry, scope),
      );
    }

    /// A server-owned row: "By server" once there are several, which opens
    /// the page that lists each server with its own answer.
    _HubRow? byServerRow(
      String id,
      ByServerKind kind, {
      String? single,
      VoidCallback? onTapSingle,
    }) => several
        ? row(
            id,
            value: copy.settingsV3ByServer,
            onTap: () =>
                _open(ByServerScreen(controller: controller, kind: kind)),
          )
        : row(id, value: single, onTap: onTapSingle);

    final profile = controller.profile;
    final serverName = profile == null
        ? null
        : serverDisplayName(profile, l10n, among: controller.store.profiles);
    final rows = switch (widget.group) {
      SettingsGroup.connections => [
        row('settings-saved-servers', value: serversRowValue(controller, l10n)),
        row('settings-this-phone', value: phoneRowValue(controller, copy)),
      ],
      SettingsGroup.ai => [
        // The model new chats start with: its name, or "By server".
        byServerRow(
          'settings-model-and-mode',
          ByServerKind.model,
          single: profile == null ? null : _modelSummary(controller),
          onTapSingle: () => unawaited(
            showModelPicker(
              context,
              title: serverName == null
                  ? null
                  : newConversationsTitle(copy, serverName),
            ),
          ),
        ),
        byServerRow('settings-providers', ByServerKind.providers),
        row('settings-category-usage'),
        byServerRow('settings-tools', ByServerKind.tools),
        row('settings-agents'),
      ],
      SettingsGroup.chat => [
        // Two switches: the same stored values the conversation menu flips,
        // for every conversation on this device.
        row(
          'settings-show-reasoning',
          builder: (context) => KitSwitchRow(
            key: const ValueKey('settings-show-reasoning'),
            leading: KitRow.icon(context, AppIconography.idea),
            title: copy.settingsHubShowReasoning,
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
            value: controller.transcriptTimestampsVisible,
            onChanged: (value) =>
                unawaited(controller.setTranscriptTimestampsVisible(value)),
          ),
        ),
        row(
          'settings-phrase-chips',
          builder: (_) => PhraseChipsSettingsRow(controller: controller),
        ),
        several
            ? byServerRow('default-shell-settings-entry', ByServerKind.shell)
            : row(
                'default-shell-settings-entry',
                builder: (_) => DefaultShellRow(controller: controller),
              ),
        row('settings-voice'),
        row('settings-automation'),
      ],
      SettingsGroup.thisApp => [
        row(
          'settings-category-notifications',
          value: notificationsRowValue(controller, copy),
        ),
        row(
          'settings-category-background',
          value: platformCapabilities.supportsBackgroundService
              ? _backgroundSummary(controller)
              : null,
        ),
        row(
          'settings-category-appearance',
          value: appearanceLabel(controller.appearance.value, context),
        ),
        row(
          'settings-category-language',
          builder: (_) => LanguageSettingsRow(controller: controller),
        ),
        row('settings-category-privacy'),
      ],
      SettingsGroup.help => [
        row('settings-setup-guide'),
        row('settings-tips'),
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
        row('settings-about-notices'),
      ],
    };
    return rows.nonNulls.toList();
  }

  /// The one line under the rows for every row of this group the connected
  /// server hides, with a Why that opens What this server supports (N6).
  Widget? _unavailableNote(int hidden) {
    if (hidden == 0) return null;
    final copy = _settingsCopy(context);
    return KitGroupNote(
      key: const ValueKey('settings-unavailable'),
      message: copy.settingsHubUnavailableCount(hidden),
      action: KitAction(
        key: const ValueKey('settings-unavailable-why'),
        label: copy.settingsHubUnavailableWhy,
        onPressed: () => unawaited(
          _open(ServerCapabilitiesScreen(controller: widget.controller)),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    _visible = TickerMode.valuesOf(context).enabled;
    final controller = widget.controller;
    final copy = _settingsCopy(context);
    final tokens = KitTokens.of(context);
    final scope = SearchScope.of(context, controller);
    final entries = {
      for (final entry in searchIndex(copy, scope)) entry.id: entry,
    };
    final group = widget.group;
    final rows = _rows(controller, entries, scope);
    final note = _unavailableNote(
      _hiddenInGroup(copy, controller, scope, group),
    );
    // "You can change some of these for one server": the saved servers,
    // each opening its own page. Only once there are several (SC4); with
    // one the server-owned rows open straight to it.
    final servers =
        group == SettingsGroup.connections && hasSeveralServers(controller)
        ? SettingsServersBlock(
            key: const ValueKey('settings-servers-block'),
            controller: controller,
          )
        : null;
    final children = [
      if (rows.isNotEmpty)
        KitRowGroup(
          key: ValueKey('settings-group-${group.slug}'),
          children: [for (final row in rows) row.build(context)],
        ),
      ?note,
      if (servers != null) ...[SizedBox(height: tokens.sectionGap), servers],
    ];
    final padding = EdgeInsetsDirectional.only(
      top: widget.detailPane ? tokens.space5 : tokens.space2,
      bottom: KitScreen.endPadding(context),
    );
    // Not a lazy list: a search result arrives at its row (KitArrival), and
    // a group is a few rows.
    if (widget.detailPane) {
      return ListView(
        key: ValueKey('settings-detail-${group.slug}'),
        padding: padding,
        children: children,
      );
    }
    return KitScrollArea(
      builder: (scrollController) => ListView(
        key: ValueKey('settings-group-page-${group.slug}'),
        controller: scrollController,
        padding: padding,
        children: children,
      ),
    );
  }
}

class _HubRow {
  final SearchEntry entry;

  /// What the row is set to now, at its end ("Claude Sonnet 4").
  final String? value;
  final VoidCallback onTap;

  /// A row that draws itself (live status or its own loading state) but is
  /// searched like any other.
  final WidgetBuilder? builder;

  const _HubRow({
    required this.entry,
    required this.onTap,
    this.value,
    this.builder,
  });

  /// The row, marked as the place a search result arrives at (KitArrival):
  /// the switches and the shell choice act here, so their results open the
  /// group's page at them.
  Widget build(BuildContext context) => KitArrival(
    id: entry.id,
    child:
        builder?.call(context) ??
        _CategoryRow(
          rowKey: entry.id,
          icon: entry.icon,
          title: entry.title,
          value: value,
          onTap: onTap,
        ),
  );
}
