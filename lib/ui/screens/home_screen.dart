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
import '../widgets/retained_tab_view.dart';
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
          icon: AppGlyph(AppIconography.workspace),
          selectedIcon: AppGlyph(AppIconography.workspaceSelected),
          label: 'Workspace',
        ),
      ),
      if (conn.capabilities.fileBrowsing)
        (
          id: 1,
          destination: const NavigationDestination(
            icon: AppGlyph(AppIconography.files),
            selectedIcon: AppGlyph(AppIconography.filesSelected),
            label: 'Files',
          ),
        ),
      (
        id: 2,
        destination: NavigationDestination(
          icon: _ActivityIcon(pending: pending, icon: AppIconography.activity),
          selectedIcon: _ActivityIcon(
            pending: pending,
            icon: AppIconography.activitySelected,
          ),
          label: 'Activity',
        ),
      ),
      (
        id: 3,
        destination: const NavigationDestination(
          icon: Icon(AppIconography.more),
          selectedIcon: Icon(AppIconography.more),
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
              icon: const Icon(AppIconography.settings),
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
                  child: RetainedTabView(
                    index: activeTab,
                    reduceMotion: GlassSurface.reduceEffects(context),
                    children: tabs,
                  ),
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
                minimum: const EdgeInsets.fromLTRB(16, 6, 16, 8),
                child: GlassSurface(
                  child: LayoutBuilder(
                    builder: (context, constraints) => _ShellNavigation(
                      width: constraints.maxWidth,
                      destinations: [
                        for (final entry in destinations) entry.destination,
                      ],
                      selectedIndex: selectedDestination,
                      onSelected: (index) => _selectTab(destinations[index].id),
                    ),
                  ),
                ),
              )
            : null,
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

/// Label metrics depend on typography and available width, not connection
/// traffic or which destination happens to be selected.
class _ShellNavigation extends StatefulWidget {
  const _ShellNavigation({
    required this.width,
    required this.destinations,
    required this.selectedIndex,
    required this.onSelected,
  });

  final double width;
  final List<NavigationDestination> destinations;
  final int selectedIndex;
  final ValueChanged<int> onSelected;

  @override
  State<_ShellNavigation> createState() => _ShellNavigationState();
}

class _ShellNavigationState extends State<_ShellNavigation> {
  Object? _metricsKey;
  double _maxScale = 1;
  double _labelHeight = 0;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final navigation = theme.navigationBarTheme;
    // Resolve partial theme styles before both measuring and painting labels;
    // otherwise Text inherits body metrics while TextPainter measures defaults.
    final labelBase = theme.textTheme.labelSmall!;
    final normal = labelBase.merge(navigation.labelTextStyle?.resolve({}));
    final selected = labelBase.merge(
      navigation.labelTextStyle?.resolve({WidgetState.selected}),
    );
    final direction = Directionality.of(context);
    final key = (
      widget.width,
      widget.destinations
          .map((destination) => destination.label)
          .join('\u0000'),
      normal,
      selected,
      direction,
    );
    if (_metricsKey != key) {
      _metricsKey = key;
      _maxScale = 2;
      _labelHeight = 0;
      for (final destination in widget.destinations) {
        for (final style in [normal, selected]) {
          final painter = TextPainter(
            text: TextSpan(text: destination.label, style: style),
            textDirection: direction,
          )..layout();
          final fit =
              (widget.width / widget.destinations.length - 8) / painter.width;
          if (fit < _maxScale) _maxScale = fit;
          if (painter.height > _labelHeight) _labelHeight = painter.height;
          painter.dispose();
        }
      }
      _maxScale = _maxScale.clamp(1.0, 2.0);
    }
    final scaler = MediaQuery.textScalerOf(
      context,
    ).clamp(maxScaleFactor: _maxScale);
    // The painted indicator is 32dp high. Its whole destination remains a
    // 72dp touch target; counting a separate 48dp icon hit box made the dock
    // unnecessarily tall. Leave at least 8dp above and below the visible stack.
    final requiredHeight = 32 + 4 + scaler.scale(_labelHeight) + 16;
    return MediaQuery.withClampedTextScaling(
      maxScaleFactor: _maxScale,
      child: NavigationBar(
        height: requiredHeight > 72 ? requiredHeight : 72,
        backgroundColor: Colors.transparent,
        labelTextStyle: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected) ? selected : normal,
        ),
        animationDuration: GlassSurface.reduceEffects(context)
            ? Duration.zero
            : RetainedTabView.duration,
        selectedIndex: widget.selectedIndex,
        onDestinationSelected: widget.onSelected,
        destinations: widget.destinations,
      ),
    );
  }
}

/// The product's single pending badge (audit UX-P0-01). Semantics carry the
/// count in words so the number is not colour- or shape-only.
class _ActivityIcon extends StatelessWidget {
  final int pending;
  final IconData icon;

  const _ActivityIcon({required this.pending, required this.icon});

  @override
  Widget build(BuildContext context) {
    if (pending <= 0) return AppGlyph(icon);
    return Semantics(
      label: '$pending item${pending == 1 ? '' : 's'} need attention',
      child: Badge(
        key: const ValueKey('activity-pending-badge'),
        label: Text('$pending'),
        child: AppGlyph(icon),
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
