import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/sse.dart';
import '../../builtin/builtin_server.dart'
    show builtinLinuxProvider, builtinServerStarterProvider;
import '../../domain/server_gateway.dart' show ServerCapabilities;
import '../../domain/connection_status.dart';
import '../../state/connection.dart';
import '../../state/first_run.dart';
import '../../state/phone_host.dart' show PhoneHostKind;
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../desktop/shortcuts.dart';
import '../kit/kit_motion.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_nav.dart';
import '../kit/kit_page_route.dart';
import '../kit/kit_row.dart';
import '../kit/kit_screen.dart';
import '../kit/kit_shape.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_status_line.dart';
import '../kit/kit_surface.dart';
import '../kit/kit_text.dart';
import '../kit/kit_top_bar.dart';
import '../kit/motion/kit_reveal.dart';
import '../kit/motion/kit_tab_switcher.dart';
import '../widgets/phone_server_card.dart';
import '../widgets/phone_server_restart.dart';
import '../widgets/server_switcher_sheet.dart';
import 'activity_screen.dart';
import 'servers_screen.dart' show ServersRouteRequest;
import 'project_hub_screen.dart';
import 'settings_screen.dart';
import 'terminal_screen.dart';
import 'this_phone_screen.dart' show openThisPhone;
import 'workspace_screen.dart';

/// Main mobile product shell for a connected OpenCode server.
///
/// Built from kit parts only (kit-v2 §9): [KitNav] draws the destinations
/// as the floating glass dock (compact), the glass rail (medium) or the PC
/// sidebar (expanded and large), switching at the KitLayout window classes;
/// the content is one [KitScreen] whose bar is the glass [KitShellControls]
/// (server pill with its status word, and search) on compact and medium.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key, this.initialTab});

  /// The destination to open on, by visible position. Null is a cold start:
  /// open on Inbox when something is waiting on the person, otherwise Work
  /// (UX plan 5.6, "Returning").
  final int? initialTab;

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen>
    with AppShortcutSurface {
  // Tab ids follow the visible order, so Ctrl/Cmd+1..4 and `initialTab` mean
  // "the nth destination" and never drift from what the dock shows.
  static const _workTab = 0;
  static const _inboxTab = 1;
  static const _projectTab = 2;
  static const _settingsTab = 3;

  /// How long "Press back again to exit" stays and a second back exits.
  static const _backExitWindow = Duration(seconds: 2);

  late int _tab;

  /// True from a cold start until the first read of what is waiting settles
  /// or the person picks a tab. Anything that arrives later belongs to the
  /// badge: moving someone who is already reading would be a hijack.
  bool _choosingColdStartTab = false;

  /// Bumped by Ctrl+F while the Project destination is showing. The hub opens
  /// Files and focuses its search field. Desktop-only in practice — nothing
  /// dispatches shortcuts off desktop.
  final _findInFiles = ValueNotifier<int>(0);
  final _openFiles = ValueNotifier<int>(0);
  final _projectBack = ProjectHubBackController();

  /// The person was on Project when the server stopped offering it (a switch
  /// to a server without project tools). The shell says why the tab went
  /// and how to get it back until they pick a tab (STATE-12), instead of
  /// the tab silently vanishing.
  bool _projectWentAway = false;

  /// The first back on Work: the shell's status slot says a second one
  /// exits, for [_backExitWindow].
  DateTime? _lastBackAt;
  Timer? _backExitHint;

  @override
  void initState() {
    super.initState();
    final conn = ref.read(connProvider);
    _tab = _safeTab(widget.initialTab ?? _workTab, conn.capabilities);
    final firstRun = FirstRun(conn.store.prefs);
    if (widget.initialTab == null &&
        !conn.isIsolated &&
        firstRun.landingPending) {
      // First run ends on Work, where the project chooser shows when one is
      // needed. Nothing can be waiting on a server connected seconds ago, so
      // no tab is chosen by what is waiting, and no empty conversation opens.
      unawaited(firstRun.markLanded());
    } else {
      unawaited(firstRun.markReturning());
      if (widget.initialTab == null) {
        _choosingColdStartTab = true;
        _chooseColdStartTab(conn);
      }
    }
    conn.addListener(_onConnChanged);
    // If the SSE stream cannot connect at all, fall back to polling.
    if (conn.status == StreamStatus.disconnected) {
      conn.enablePollingFallback();
    }
  }

  /// The shell's own share of the shortcut layer: primary destinations, and
  /// routing Find to the one destination that has a find field.
  ///
  /// A shortcut or search result that leads to Project on a server without
  /// project tools explains why and offers the way back (switching to a
  /// server that has them) instead of doing nothing or landing on Work.
  @override
  bool onAppShortcut(Intent intent) {
    final capabilities = ref.read(connProvider).capabilities;
    switch (intent) {
      case SelectDestinationIntent(:final index) when index >= 0 && index <= 3:
        if (index == _projectTab && !ProjectHub.isAvailable(capabilities)) {
          unawaited(_explainProjectUnavailable());
          return true;
        }
        _selectTab(_safeTab(index, capabilities));
        return true;
      case FindInSurfaceIntent()
          when _tab == _projectTab && capabilities.fileBrowsing:
        _findInFiles.value++;
        return true;
      // A search result that means Files or its search: both live inside the
      // Project tab, so the tab is selected first.
      case OpenProjectToolIntent(:final tool)
          when tool == ProjectTool.files || tool == ProjectTool.search:
        if (!capabilities.fileBrowsing) {
          unawaited(_explainProjectUnavailable());
          return true;
        }
        _signalProject(tool == ProjectTool.files ? _openFiles : _findInFiles);
        return true;
      // Always the Terminal page, even on a server that keeps no terminals:
      // the page says why (the terminal capability) and offers this phone's
      // terminal where there is one, instead of the keystroke doing nothing.
      case OpenTerminalIntent():
        unawaited(
          pushKitPage<void>(
            context,
            (_) => TerminalPage(controller: ref.read(connProvider)),
          ),
        );
        return true;
      default:
        return false;
    }
  }

  /// Connect starts the pending-request reads before this shell mounts, so
  /// "nothing waiting" is only known once none of them is still loading.
  void _chooseColdStartTab(ConnectionController conn) {
    if (!_choosingColdStartTab) return;
    if (conn.unifiedAttentionCount > 0) {
      _choosingColdStartTab = false;
      // A notification may already have opened its conversation over the
      // shell; the tab underneath still changes, the conversation does not.
      _tab = _inboxTab;
      return;
    }
    final reading =
        conn.permissionsLoading ||
        conn.questionsLoading ||
        (conn.capabilities.forms && conn.formsLoading);
    if (!reading) _choosingColdStartTab = false;
  }

  /// Selects Project and signals it. Destinations are built on their first
  /// visit, so a hub not showing yet hears the signal after the frame that
  /// builds it.
  void _signalProject(ValueNotifier<int> signal) {
    if (_tab == _projectTab) {
      signal.value++;
      return;
    }
    _selectTab(_projectTab);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) signal.value++;
    });
  }

  void _selectTab(int next) {
    _choosingColdStartTab = false;
    if (_projectWentAway) setState(() => _projectWentAway = false);
    if (_tab == next) return;
    _clearBackExit();
    setState(() => _tab = next);
  }

  void _onConnChanged() {
    if (!mounted) return;
    final conn = ref.read(connProvider);
    _chooseColdStartTab(conn);
    final next = _safeTab(_tab, conn.capabilities);
    final wentAway = _tab == _projectTab && next != _projectTab;
    setState(() {
      if (wentAway) _projectWentAway = true;
      // The tools came back (switched to a server that has them).
      if (ProjectHub.isAvailable(conn.capabilities)) _projectWentAway = false;
      _tab = next;
    });
  }

  static int _safeTab(int requested, ServerCapabilities capabilities) {
    final tab = requested.clamp(_workTab, _settingsTab);
    return tab == _projectTab && !ProjectHub.isAvailable(capabilities)
        ? _workTab
        : tab;
  }

  @override
  void dispose() {
    try {
      ref.read(connProvider).removeListener(_onConnChanged);
    } catch (_) {}
    _backExitHint?.cancel();
    _findInFiles.dispose();
    _openFiles.dispose();
    super.dispose();
  }

  String _serverName(ConnectionController conn) => serverDisplayName(
    conn.profile,
    _l10n(context),
    among: conn.store.profiles,
  );

  @override
  Widget build(BuildContext context) {
    final conn = ref.watch(connProvider);
    final l10n = _l10n(context);
    final activeTab = _safeTab(_tab, conn.capabilities);
    final sidebar = KitNav.layoutOf(context) == KitNavLayout.sidebar;

    // One tab per noun (UX plan 5.1): Work, Inbox, Project, Settings. The
    // Inbox carries the product's single pending badge. Project is absent
    // only when the server offers none of its tools (Codex, Paseo today);
    // reaching for it then explains why ([_explainProjectUnavailable]).
    final hasProjectTools = ProjectHub.isAvailable(conn.capabilities);
    final phoneServer = phoneServerRestartFor(
      connection: conn,
      builtin: ref.read(builtinServerStarterProvider),
      context: context,
    );
    final tabs = <Widget>[
      WorkspaceScreen(
        controller: conn,
        serverOnThisPhone: phoneServer.onThisPhone,
        onRestartServer: phoneServer.restart,
      ),
      ActivityScreen(controller: conn, embedded: true),
      if (hasProjectTools)
        ProjectHub(
          controller: conn,
          focusSearchSignal: _findInFiles,
          openFilesSignal: _openFiles,
          backController: _projectBack,
        )
      else
        const SizedBox.shrink(),
      SettingsScreen(controller: conn, embedded: true),
    ];
    final destinations = <({int id, KitNavDestination destination})>[
      (
        id: _workTab,
        destination: KitNavDestination(
          key: const ValueKey('home-shell-tab-work'),
          label: l10n.shellTabWork,
          icon: AppIconography.workspace,
          selectedIcon: AppIconography.workspaceSelected,
        ),
      ),
      (
        id: _inboxTab,
        destination: KitNavDestination(
          key: const ValueKey('home-shell-tab-inbox'),
          label: l10n.shellTabInbox,
          icon: AppIconography.activity,
          selectedIcon: AppIconography.activitySelected,
          needsYou: conn.unifiedAttentionCount,
        ),
      ),
      if (hasProjectTools)
        (
          id: _projectTab,
          destination: KitNavDestination(
            key: const ValueKey('home-shell-tab-project'),
            label: l10n.shellTabProject,
            icon: AppIconography.files,
            selectedIcon: AppIconography.filesSelected,
          ),
        ),
      (
        id: _settingsTab,
        destination: KitNavDestination(
          key: const ValueKey('home-shell-tab-settings'),
          label: l10n.librarySettingsTitle,
          icon: AppIconography.settings,
        ),
      ),
    ];
    final selected = destinations.indexWhere((entry) => entry.id == activeTab);

    final perform = AppShortcutScope.performOf(context);
    final (statusWord, statusTone) = _serverStatus(
      conn.connectionStatus.phase,
      l10n,
    );
    final controls = KitShellControls(
      server: _serverName(conn),
      serverStatus: statusWord,
      serverTone: statusTone,
      onServer: () => unawaited(_openServerSwitcher(conn)),
      // The same launcher as Ctrl/Cmd+K (commands and settings search).
      onSearch: perform == null
          ? null
          : () => perform(const OpenCommandPaletteIntent()),
      layout: sidebar
          ? KitShellControlsLayout.sidebar
          : KitShellControlsLayout.bar,
      serverKey: const ValueKey('server-switcher-button'),
      searchKey: const ValueKey('home-shell-search'),
    );

    final content = KitScreen(
      // Compact and medium: the glass top controls; the dock or rail names
      // the tab. The PC sidebar holds the controls and highlights the
      // destination, so the pane has no bar at all: it starts with the
      // destination's own header (the project on Work, visual language
      // Desktop.png), never a title repeating the sidebar (slice-R14).
      topBar: sidebar ? null : KitTopBar.shell(controls: controls),
      page: sidebar,
      status: _backExitHint == null
          ? null
          : KitStatus(
              kind: KitStatusKind.info,
              id: 'home-shell-back-exit',
              icon: AppIconography.back,
              message: l10n.e7WorkspaceBackExit,
            ),
      header: [
        KitReveal(
          child: _projectWentAway && !hasProjectTools
              ? KitRowGroup(
                  key: const ValueKey('home-shell-project-unavailable'),
                  children: [_projectUnavailableRow(conn)],
                )
              : null,
        ),
      ],
      // Each destination is built on its first visit (Work from the
      // start, where Back returns): no hidden tab builds or reads at
      // startup (docs/qa/codex-perf-2026-09-28/startup.md).
      body: KitTabSwitcher(
        index: activeTab,
        reduceMotion: KitMotion.reduced(context),
        lazy: true,
        preload: const {_workTab},
        children: tabs,
      ),
    );

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: _onRootPop,
      child: KitSurface(
        level: KitSurfaceLevel.ground,
        shape: KitShape.square,
        padding: KitSurfacePadding.none,
        clip: false,
        child: KitNav(
          navKey: const ValueKey('home-shell-nav'),
          destinations: [for (final entry in destinations) entry.destination],
          selected: selected < 0 ? 0 : selected,
          onSelected: (index) => _selectTab(destinations[index].id),
          sidebarHeader: sidebar ? controls : null,
          child: content,
        ),
      ),
    );
  }

  /// The status word beside the server name, always visible (STATE-9), and
  /// its tone.
  static (String, AppStatusTone) _serverStatus(
    ConnectionStatusPhase status,
    AppLocalizations l10n,
  ) => switch (status) {
    ConnectionStatusPhase.connected => (
      l10n.e7WorkspaceConnected,
      AppStatusTone.ok,
    ),
    ConnectionStatusPhase.connecting => (
      l10n.e7WorkspaceConnecting,
      AppStatusTone.progress,
    ),
    ConnectionStatusPhase.reconnecting => (
      l10n.mcpReconnecting,
      AppStatusTone.progress,
    ),
    _ => (l10n.e7WorkspaceOffline, AppStatusTone.failure),
  };

  /// Why Project is missing on this server, with the flow that brings it
  /// back: switching to a server that offers project tools.
  Widget _projectUnavailableRow(ConnectionController conn) {
    final l10n = _l10n(context);
    return KitRow.unavailable(
      title: l10n.homeShellProjectUnavailable,
      reason: l10n.homeShellProjectUnavailableShort(_serverName(conn)),
      leading: KitRow.icon(context, AppIconography.files),
      enable: KitAction(
        key: const ValueKey('home-shell-project-switch-server'),
        label: l10n.serverSwitcherOpen,
        icon: AppIconography.swap,
        onPressed: () => unawaited(_openServerSwitcher(conn)),
      ),
    );
  }

  /// A shortcut or search result asked for Project on a server without
  /// project tools: say why, and offer the server switcher.
  Future<void> _explainProjectUnavailable() async {
    final conn = ref.read(connProvider);
    final l10n = _l10n(context);
    final switchServer = await showKitSheet<bool>(
      context,
      sheetKey: const ValueKey('home-shell-project-unavailable-sheet'),
      title: l10n.homeShellProjectUnavailable,
      icon: AppIconography.files,
      body: (_) => KitText(
        l10n.homeShellProjectUnavailableReason(_serverName(conn)),
        tone: KitTextTone.secondary,
      ),
      primary: KitAction(
        key: const ValueKey('home-shell-project-switch-server'),
        label: l10n.serverSwitcherOpen,
        icon: AppIconography.swap,
        onPressed: () => Navigator.of(context).pop(true),
      ),
    );
    if (switchServer == true && mounted) await _openServerSwitcher(conn);
  }

  /// The switcher only chooses. Connecting, credentials, adding and
  /// forgetting all run on the Servers screen, which already owns the
  /// runtime-choice detour and shows a failed connect next to its fix.
  Future<void> _openServerSwitcher(ConnectionController conn) async {
    final navigator = Navigator.of(context);
    final choice = await showServerSwitcher(context, conn);
    if (choice == null || !navigator.mounted) return;
    switch (choice) {
      case ServerSwitcherOpenServers(:final ServersRouteRequest? request):
        unawaited(navigator.pushNamed('/servers', arguments: request));
      case ServerSwitcherOpenPhoneSetup():
        unawaited(openThisPhone(context, kind: PhoneHostKind.termux));
      case ServerSwitcherPhoneAction(
        :final action,
        :final profileID,
        :final bytesUsed,
      ):
        final profile = conn.store.profiles
            .where((profile) => profile.id == profileID)
            .firstOrNull;
        if (profile == null) return;
        final removed = await runPhoneServerAction(
          context,
          action,
          connection: conn,
          linux: ref.read(builtinLinuxProvider),
          profile: profile,
          bytesUsed: bytesUsed,
        );
        // The shell was on the server that is gone: the Servers screen is
        // where the person picks what comes next.
        if (removed && navigator.mounted && conn.api == null) {
          unawaited(
            navigator.pushNamedAndRemoveUntil('/servers', (_) => false),
          );
        }
      case ServerSwitcherLeave(:final alreadyDisconnected):
        if (!alreadyDisconnected) await conn.disconnect();
        if (!navigator.mounted) return;
        unawaited(navigator.pushNamedAndRemoveUntil('/servers', (_) => false));
    }
  }

  void _clearBackExit() {
    _lastBackAt = null;
    if (_backExitHint == null) return;
    _backExitHint!.cancel();
    _backExitHint = null;
    if (mounted) setState(() {});
  }

  /// Project first unwinds Files and returns to its hub, then destinations
  /// return home.
  /// Only Work uses the double-back exit guard.
  void _onRootPop(bool didPop, Object? result) {
    if (didPop) return;
    if (_tab == _projectTab && _projectBack.handleBack()) {
      _clearBackExit();
      return;
    }
    if (_tab != _workTab) {
      _selectTab(_workTab);
      return;
    }
    final now = DateTime.now();
    if (_lastBackAt != null && now.difference(_lastBackAt!) < _backExitWindow) {
      SystemNavigator.pop();
      return;
    }
    _lastBackAt = now;
    // The shell's one status line says it, and folds away with the window.
    _backExitHint?.cancel();
    setState(() {
      _backExitHint = Timer(_backExitWindow, () {
        if (!mounted) return;
        setState(() => _backExitHint = null);
      });
    });
  }
}

AppLocalizations _l10n(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(Localizations.localeOf(context));
