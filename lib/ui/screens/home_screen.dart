import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/sse.dart';
import '../../builtin/builtin_server.dart' show builtinLinuxProvider;
import '../../domain/chat_feed.dart' show ChatFeedFilter;
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
import '../widgets/server_switcher_sheet.dart';
import 'chats/chats_home_screen.dart';
import 'servers_screen.dart' show ServersRouteRequest;
import 'project_hub_screen.dart';
import 'settings_screen.dart';
import 'terminal_screen.dart';
import 'this_phone_screen.dart' show openThisPhone;

/// Main mobile product shell for a connected OpenCode server.
///
/// Built from kit parts only (kit-v2 §9): [KitNav] draws the destinations
/// as the floating glass dock (compact), the glass rail (medium) or the PC
/// sidebar (expanded and large), switching at the KitLayout window classes;
/// the content is one [KitScreen] whose bar is the glass [KitShellControls]
/// (server pill with its status word, and search) on compact and medium.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key, this.initialTab, this.initialChatFilter});

  /// The destination to open on, by visible position (0 Chats, 1 Files, 2
  /// Settings). Null is a cold start, which opens on Chats.
  final int? initialTab;

  /// The Chats filter to open on ("Needs you" or "Running"), for what used
  /// to open the Inbox. Null is the plain list.
  final ChatFeedFilter? initialChatFilter;

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen>
    with AppShortcutSurface {
  // Tab ids follow the visible order, so Ctrl/Cmd+1..3 and `initialTab` mean
  // "the nth destination" and never drift from what the dock shows.
  static const _chatsTab = 0;
  static const _filesTab = 1;
  static const _settingsTab = 2;

  /// How long "Press back again to exit" stays and a second back exits.
  static const _backExitWindow = Duration(seconds: 2);

  late int _tab;

  /// What Chats opens on. A new request while the tab shows bumps
  /// [_chatsRequest], which re-creates the list with the new filter.
  ChatFeedFilter? _chatsFilter;
  int _chatsRequest = 0;

  /// Bumped by Ctrl+F while the Files destination is showing. The hub opens
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
    _chatsFilter = widget.initialChatFilter;
    _tab = _safeTab(widget.initialTab ?? _chatsTab, conn.capabilities);
    final firstRun = FirstRun(conn.store.prefs);
    if (widget.initialTab == null &&
        !conn.isIsolated &&
        firstRun.landingPending) {
      // First run ends on Chats. No empty conversation opens.
      unawaited(firstRun.markLanded());
    } else {
      unawaited(firstRun.markReturning());
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
  /// A shortcut or search result that leads to Files on a server without
  /// project tools explains why and offers the way back (switching to a
  /// server that has them) instead of doing nothing or landing on Work.
  @override
  bool onAppShortcut(Intent intent) {
    final capabilities = ref.read(connProvider).capabilities;
    switch (intent) {
      case SelectDestinationIntent(:final index) when index >= 0 && index <= 2:
        if (index == _filesTab && !ProjectHub.isAvailable(capabilities)) {
          unawaited(_explainProjectUnavailable());
          return true;
        }
        _selectTab(_safeTab(index, capabilities));
        return true;
      // Everything that used to open the Inbox: Chats, with the filter.
      case OpenChatsIntent(:final needsYou, :final running):
        _openChats(
          needsYou || running
              ? ChatFeedFilter(needsYou: needsYou, running: running)
              : null,
        );
        return true;
      case FindInSurfaceIntent()
          when _tab == _filesTab && capabilities.fileBrowsing:
        _findInFiles.value++;
        return true;
      // A search result that means Files or its search: both live inside the
      // Files tab, so the tab is selected first.
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

  /// Selects Chats, on [filter] when one is asked for. Asking while Chats
  /// already shows re-creates the list so it opens on the new filter.
  void _openChats(ChatFeedFilter? filter) {
    _clearBackExit();
    setState(() {
      _projectWentAway = false;
      _chatsFilter = filter;
      _chatsRequest++;
      _tab = _chatsTab;
    });
  }

  /// Selects Files and signals it. Destinations are built on their first
  /// visit, so a hub not showing yet hears the signal after the frame that
  /// builds it.
  void _signalProject(ValueNotifier<int> signal) {
    if (_tab == _filesTab) {
      signal.value++;
      return;
    }
    _selectTab(_filesTab);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) signal.value++;
    });
  }

  void _selectTab(int next) {
    if (_projectWentAway) setState(() => _projectWentAway = false);
    if (_tab == next) return;
    _clearBackExit();
    setState(() => _tab = next);
  }

  void _onConnChanged() {
    if (!mounted) return;
    final conn = ref.read(connProvider);
    final next = _safeTab(_tab, conn.capabilities);
    final wentAway = _tab == _filesTab && next != _filesTab;
    setState(() {
      if (wentAway) _projectWentAway = true;
      // The tools came back (switched to a server that has them).
      if (ProjectHub.isAvailable(conn.capabilities)) _projectWentAway = false;
      _tab = next;
    });
  }

  static int _safeTab(int requested, ServerCapabilities capabilities) {
    final tab = requested.clamp(_chatsTab, _settingsTab);
    return tab == _filesTab && !ProjectHub.isAvailable(capabilities)
        ? _chatsTab
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

  /// The server new conversations start on, with how many other
  /// connections the Conversations list also shows ("In-app Ubuntu +2").
  String _serverName(ConnectionController conn) {
    final name = serverDisplayName(
      conn.profile,
      _l10n(context),
      among: conn.store.profiles,
    );
    final others = conn.chatListSources
        .where((source) => source.shown && !source.main)
        .length;
    return others == 0
        ? name
        : _l10n(context).shellServerPlusOthers(name, others);
  }

  @override
  Widget build(BuildContext context) {
    final conn = ref.watch(connProvider);
    final l10n = _l10n(context);
    final activeTab = _safeTab(_tab, conn.capabilities);
    final sidebar = KitNav.layoutOf(context) == KitNavLayout.sidebar;

    // Chats first, project as a setting: Chats, Files, Settings. Chats
    // carries the product's single pending badge (what the Inbox tab did).
    // Files is absent only when the server offers none of the project tools
    // (Codex, Paseo today); reaching for it then explains why
    // ([_explainProjectUnavailable]).
    final hasProjectTools = ProjectHub.isAvailable(conn.capabilities);
    final tabs = <Widget>[
      ChatsHomeScreen(
        key: ValueKey('home-shell-chats-$_chatsRequest'),
        initialFilter: _chatsFilter,
      ),
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
        id: _chatsTab,
        destination: KitNavDestination(
          key: const ValueKey('home-shell-tab-chats'),
          label: l10n.shellTabChats,
          icon: AppIconography.chat,
          needsYou: conn.unifiedAttentionCount,
        ),
      ),
      if (hasProjectTools)
        (
          id: _filesTab,
          destination: KitNavDestination(
            key: const ValueKey('home-shell-tab-files'),
            label: l10n.shellTabFiles,
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
      // destination's own header (Chats' title, visual language
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
      // Each destination is built on its first visit (Chats from the
      // start, where Back returns): no hidden tab builds or reads at
      // startup (docs/qa/codex-perf-2026-09-28/startup.md).
      body: KitTabSwitcher(
        index: activeTab,
        reduceMotion: KitMotion.reduced(context),
        lazy: true,
        preload: const {_chatsTab},
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

  /// Files first unwinds its browser and returns to its hub, then
  /// destinations return home. Only Chats uses the double-back exit guard.
  void _onRootPop(bool didPop, Object? result) {
    if (didPop) return;
    if (_tab == _filesTab && _projectBack.handleBack()) {
      _clearBackExit();
      return;
    }
    if (_tab != _chatsTab) {
      _selectTab(_chatsTab);
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
