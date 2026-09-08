import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/sse.dart';
import '../../domain/server_gateway.dart' show ServerCapabilities;
import '../../state/connection.dart';
import '../app_theme.dart';
import '../desktop/shortcuts.dart';
import '../widgets/connection_status_banner.dart';
import '../widgets/glass_surface.dart';
import '../widgets/pickers.dart';
import 'activity_screen.dart';
import 'files_screen.dart';
import 'library_screen.dart';
import 'terminal_screen.dart';
import 'workspace_screen.dart';

/// Main mobile product shell for a connected OpenCode server.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key, this.initialTab = 0});

  final int initialTab;

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen>
    with AppShortcutSurface {
  late int _tab;

  /// Bumped by Ctrl+F while the Files destination is showing. Files listens
  /// and focuses its search field. Desktop-only in practice — nothing
  /// dispatches shortcuts off desktop.
  final _findInFiles = ValueNotifier<int>(0);
  final _filesBack = FilesBackController();

  @override
  void initState() {
    super.initState();
    final conn = ref.read(connProvider);
    _tab = _safeTab(widget.initialTab, conn.capabilities);
    conn.addListener(_onConnChanged);
    // If the SSE stream cannot connect at all, fall back to polling.
    if (conn.status == StreamStatus.disconnected) {
      conn.enablePollingFallback();
    }
  }

  /// The shell's own share of the shortcut layer: primary destinations, and
  /// routing Find to the one destination that has a find field.
  @override
  bool onAppShortcut(Intent intent) {
    switch (intent) {
      case SelectDestinationIntent(:final index) when index >= 0 && index <= 3:
        final conn = ref.read(connProvider);
        final next = _safeTab(index, conn.capabilities);
        _selectTab(next);
        return true;
      case FindInSurfaceIntent()
          when _tab == 1 && ref.read(connProvider).capabilities.fileBrowsing:
        _findInFiles.value++;
        return true;
      case OpenTerminalIntent()
          when ref.read(connProvider).capabilities.terminal:
        Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => TerminalPage(controller: ref.read(connProvider)),
          ),
        );
        return true;
      default:
        return false;
    }
  }

  void _selectTab(int next) {
    if (_tab == next) return;
    _lastBackAt = null;
    setState(() => _tab = next);
  }

  void _onConnChanged() {
    if (!mounted) return;
    final next = _safeTab(_tab, ref.read(connProvider).capabilities);
    setState(() => _tab = next);
  }

  static int _safeTab(int requested, ServerCapabilities capabilities) {
    final tab = requested.clamp(0, 3);
    return tab == 1 && !capabilities.fileBrowsing ? 0 : tab;
  }

  @override
  void dispose() {
    try {
      ref.read(connProvider).removeListener(_onConnChanged);
    } catch (_) {}
    _findInFiles.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final conn = ref.watch(connProvider);
    final navigator = Navigator.of(context);
    final activeTab = _safeTab(_tab, conn.capabilities);

    // Audit §5: Activity replaces Terminal in primary navigation; Terminal is
    // reachable from Session and the More hub. One destination, one badge.
    final tabs = <Widget>[
      WorkspaceScreen(controller: conn),
      if (conn.capabilities.fileBrowsing)
        FilesScreen(
          controller: conn,
          focusSearchSignal: _findInFiles,
          backController: _filesBack,
        )
      else
        const SizedBox.shrink(),
      ActivityScreen(controller: conn, embedded: true),
      LibraryScreen(controller: conn),
    ];
    final pending = conn.unifiedAttentionCount;
    final destinations = <({int id, NavigationDestination destination})>[
      (
        id: 0,
        destination: const NavigationDestination(
          icon: Icon(Icons.workspaces_outline),
          selectedIcon: Icon(Icons.workspaces_rounded),
          label: 'Workspace',
        ),
      ),
      if (conn.capabilities.fileBrowsing)
        (
          id: 1,
          destination: const NavigationDestination(
            icon: Icon(Icons.folder_outlined),
            selectedIcon: Icon(Icons.folder_rounded),
            label: 'Files',
          ),
        ),
      (
        id: 2,
        destination: NavigationDestination(
          icon: _ActivityIcon(
            pending: pending,
            icon: Icons.notifications_outlined,
          ),
          selectedIcon: _ActivityIcon(
            pending: pending,
            icon: Icons.notifications_rounded,
          ),
          label: 'Activity',
        ),
      ),
      (
        id: 3,
        destination: const NavigationDestination(
          icon: Icon(Icons.more_horiz_rounded),
          selectedIcon: Icon(Icons.more_horiz_rounded),
          label: 'More',
        ),
      ),
    ];
    final selectedDestination = destinations.indexWhere(
      (entry) => entry.id == activeTab,
    );

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: _onRootPop,
      child: Scaffold(
        appBar: AppBar(
          title: _WorkspaceAppBarTitle(
            profileName: conn.profile?.name ?? 'OpenCode',
            tabTitle: _titles[activeTab],
            status: conn.status,
            compact: MediaQuery.sizeOf(context).width < 600,
          ),
          actions: [
            // §5 Root app bar: one contextual action plus overflow. The
            // pending badge lives on the Activity destination alone.
            // Settings and the shortcuts list have one entry point each, on
            // the More tab; this overflow holds only connection-level acts.
            IconButton(
              tooltip: 'Model / agent',
              icon: const Icon(Icons.tune_rounded),
              onPressed: () => showModelPicker(context),
            ),
            PopupMenuButton<String>(
              onSelected: (v) {
                if (v == 'refresh') conn.refreshSessions();
                if (v == 'disconnect') {
                  conn.disconnect().then((_) {
                    navigator.pushNamedAndRemoveUntil('/servers', (_) => false);
                  });
                }
              },
              itemBuilder: (_) => [
                const PopupMenuItem(value: 'refresh', child: Text('Refresh')),
                const PopupMenuItem(
                  value: 'disconnect',
                  child: Text('Disconnect'),
                ),
              ],
            ),
          ],
        ),
        body: LayoutBuilder(
          builder: (context, constraints) {
            final content = Column(
              children: [
                if (conn.status != StreamStatus.connected)
                  ConnectionStatusBanner(controller: conn),
                Expanded(
                  child: IndexedStack(index: activeTab, children: tabs),
                ),
              ],
            );
            if (constraints.maxWidth < 760) return content;
            return Row(
              children: [
                NavigationRail(
                  selectedIndex: selectedDestination,
                  extended: constraints.maxWidth >= 1040,
                  onDestinationSelected: (index) =>
                      _selectTab(destinations[index].id),
                  destinations: [
                    for (final entry in destinations)
                      NavigationRailDestination(
                        icon: entry.destination.icon,
                        selectedIcon: entry.destination.selectedIcon,
                        label: Text(entry.destination.label),
                      ),
                  ],
                ),
                const VerticalDivider(width: 1),
                Expanded(child: content),
              ],
            );
          },
        ),
        bottomNavigationBar: MediaQuery.sizeOf(context).width < 760
            ? SafeArea(
                top: false,
                minimum: const EdgeInsets.fromLTRB(12, 6, 12, 8),
                child: GlassSurface(
                  child: LayoutBuilder(
                    builder: (context, constraints) => _buildNavigation(
                      context,
                      constraints.maxWidth,
                      [for (final entry in destinations) entry.destination],
                      selectedDestination,
                      (index) => _selectTab(destinations[index].id),
                    ),
                  ),
                ),
              )
            : null,
      ),
    );
  }

  // Keep destination names intact. Compact navigation labels scale as far as
  // their equal-width slots allow; content elsewhere keeps the user's full scale.
  Widget _buildNavigation(
    BuildContext context,
    double width,
    List<NavigationDestination> destinations,
    int selectedIndex,
    ValueChanged<int> onSelected,
  ) {
    final theme = Theme.of(context);
    final navigation = theme.navigationBarTheme;
    var maxScale = 2.0;
    var labelHeight = 0.0;
    for (final destination in destinations) {
      for (final states in [
        <WidgetState>{},
        {WidgetState.selected},
      ]) {
        final painter = TextPainter(
          text: TextSpan(
            text: destination.label,
            style:
                navigation.labelTextStyle?.resolve(states) ??
                theme.textTheme.labelMedium,
          ),
          textDirection: Directionality.of(context),
        )..layout();
        final fit = (width / destinations.length - 8) / painter.width;
        if (fit < maxScale) maxScale = fit;
        if (painter.height > labelHeight) labelHeight = painter.height;
        painter.dispose();
      }
    }
    maxScale = maxScale.clamp(1.0, 2.0);
    final scaler = MediaQuery.textScalerOf(
      context,
    ).clamp(maxScaleFactor: maxScale);
    final requiredHeight = 48 + 4 + scaler.scale(labelHeight) + 16;
    final baseHeight = navigation.height ?? 80.0;
    return MediaQuery.withClampedTextScaling(
      maxScaleFactor: maxScale,
      child: NavigationBar(
        height: requiredHeight > baseHeight ? requiredHeight : baseHeight,
        backgroundColor: Colors.transparent,
        animationDuration: GlassSurface.reduceEffects(context)
            ? Duration.zero
            : const Duration(milliseconds: 220),
        selectedIndex: selectedIndex,
        onDestinationSelected: onSelected,
        destinations: destinations,
      ),
    );
  }

  /// Files first unwinds its local navigation, then destinations return home.
  /// Only Workspace uses the double-back exit guard.
  void _onRootPop(bool didPop, Object? result) {
    if (didPop) return;
    if (_tab == 1 &&
        ref.read(connProvider).capabilities.fileBrowsing &&
        _filesBack.handleBack()) {
      _lastBackAt = null;
      return;
    }
    if (_tab != 0) {
      _selectTab(0);
      return;
    }
    final now = DateTime.now();
    if (_lastBackAt != null &&
        now.difference(_lastBackAt!) < const Duration(seconds: 2)) {
      SystemNavigator.pop();
      return;
    }
    _lastBackAt = now;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text('Press back again to exit'),
          duration: Duration(seconds: 2),
        ),
      );
  }

  DateTime? _lastBackAt;

  static const _titles = ['Workspace', 'Files', 'Activity', 'More'];
}

/// The product's single pending badge (audit UX-P0-01). Semantics carry the
/// count in words so the number is not colour- or shape-only.
class _ActivityIcon extends StatelessWidget {
  final int pending;
  final IconData icon;

  const _ActivityIcon({required this.pending, required this.icon});

  @override
  Widget build(BuildContext context) {
    if (pending <= 0) return Icon(icon);
    return Semantics(
      label: '$pending item${pending == 1 ? '' : 's'} need attention',
      child: Badge(
        key: const ValueKey('activity-pending-badge'),
        label: Text('$pending'),
        child: Icon(icon),
      ),
    );
  }
}

class _WorkspaceAppBarTitle extends StatelessWidget {
  final String profileName;
  final String tabTitle;
  final StreamStatus status;
  final bool compact;

  const _WorkspaceAppBarTitle({
    required this.profileName,
    required this.tabTitle,
    required this.status,
    required this.compact,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final profile = Tooltip(
      message: profileName,
      child: Semantics(
        label: 'Server: $profileName',
        excludeSemantics: true,
        child: Text(
          profileName,
          key: const ValueKey('server-profile-title'),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.titleMedium,
        ),
      ),
    );
    final page = Text(
      tabTitle,
      key: const ValueKey('current-tab-title'),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: theme.textTheme.labelSmall?.copyWith(
        color: AppTheme.mutedOf(theme),
      ),
    );
    final server = Row(
      children: [
        _StatusDot(status: status),
        const SizedBox(width: 8),
        Expanded(child: profile),
      ],
    );

    if (compact) {
      return Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          server,
          const SizedBox(height: 1),
          Padding(padding: const EdgeInsets.only(left: 18), child: page),
        ],
      );
    }

    return Row(
      children: [
        Expanded(child: server),
        const SizedBox(width: 12),
        page,
      ],
    );
  }
}

class _StatusDot extends StatelessWidget {
  final StreamStatus status;
  const _StatusDot({required this.status});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final (tone, pulse) = switch (status) {
      StreamStatus.connected => (AppStatusTone.ok, false),
      StreamStatus.connecting ||
      StreamStatus.reconnecting => (AppStatusTone.progress, true),
      StreamStatus.disconnected => (AppStatusTone.failure, false),
    };
    final color = AppTheme.statusColor(theme, tone);
    final label = switch (status) {
      StreamStatus.connected => 'Connected',
      StreamStatus.connecting => 'Connecting',
      StreamStatus.reconnecting => 'Reconnecting',
      StreamStatus.disconnected => 'Offline',
    };
    return Semantics(
      label: 'Server $label',
      child: Tooltip(
        message: label,
        child: pulse && !GlassSurface.reduceEffects(context)
            ? SizedBox(
                width: 12,
                height: 12,
                child: CircularProgressIndicator(strokeWidth: 2, color: color),
              )
            : Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(color: color, shape: BoxShape.circle),
              ),
      ),
    );
  }
}
