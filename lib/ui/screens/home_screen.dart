import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/sse.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../widgets/connection_status_banner.dart';
import '../widgets/pickers.dart';
import 'activity_screen.dart';
import 'files_screen.dart';
import 'library_screen.dart';
import 'settings_screen.dart';
import 'workspace_screen.dart';

/// Main mobile product shell for a connected OpenCode server.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key, this.initialTab = 0});

  final int initialTab;

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  late int _tab;

  @override
  void initState() {
    super.initState();
    _tab = widget.initialTab.clamp(0, 3);
    final conn = ref.read(connProvider);
    conn.addListener(_onConnChanged);
    // If the SSE stream cannot connect at all, fall back to polling.
    if (conn.status == StreamStatus.disconnected) {
      conn.enablePollingFallback();
    }
  }

  void _onConnChanged() {
    if (!mounted) return;
    setState(() {});
  }

  @override
  void dispose() {
    try {
      ref.read(connProvider).removeListener(_onConnChanged);
    } catch (_) {}
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final conn = ref.watch(connProvider);
    final navigator = Navigator.of(context);

    // Audit §5: Activity replaces Terminal in primary navigation; Terminal is
    // reachable from Session and the More hub. One destination, one badge.
    final tabs = [
      WorkspaceScreen(controller: conn),
      FilesScreen(controller: conn),
      ActivityScreen(controller: conn, embedded: true),
      LibraryScreen(controller: conn),
    ];
    final pending =
        conn.permissions.length + conn.questions.length + conn.forms.length;
    final destinations = [
      const NavigationDestination(
        icon: Icon(Icons.workspaces_outline),
        selectedIcon: Icon(Icons.workspaces_rounded),
        label: 'Workspace',
      ),
      const NavigationDestination(
        icon: Icon(Icons.folder_outlined),
        selectedIcon: Icon(Icons.folder_rounded),
        label: 'Files',
      ),
      NavigationDestination(
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
      const NavigationDestination(
        icon: Icon(Icons.more_horiz_rounded),
        selectedIcon: Icon(Icons.more_rounded),
        label: 'More',
      ),
    ];

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: _onRootPop,
      child: Scaffold(
        appBar: AppBar(
          title: _WorkspaceAppBarTitle(
            profileName: conn.profile?.name ?? 'OpenCode',
            tabTitle: _titles[_tab],
            status: conn.status,
            compact: MediaQuery.sizeOf(context).width < 600,
          ),
          actions: [
            // §5 Root app bar: one contextual action plus overflow. The
            // pending badge lives on the Activity destination alone.
            IconButton(
              tooltip: 'Model / agent',
              icon: const Icon(Icons.tune_rounded),
              onPressed: () => showModelPicker(context),
            ),
            PopupMenuButton<String>(
              onSelected: (v) {
                if (v == 'refresh') conn.refreshSessions();
                if (v == 'settings') {
                  navigator.push(
                    MaterialPageRoute<void>(
                      builder: (_) => SettingsScreen(controller: conn),
                    ),
                  );
                }
                if (v == 'disconnect') {
                  conn.disconnect().then((_) {
                    navigator.pushNamedAndRemoveUntil('/servers', (_) => false);
                  });
                }
              },
              itemBuilder: (_) => const [
                PopupMenuItem(value: 'refresh', child: Text('Refresh')),
                PopupMenuItem(value: 'settings', child: Text('Settings')),
                PopupMenuItem(value: 'disconnect', child: Text('Disconnect')),
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
                  child: IndexedStack(index: _tab, children: tabs),
                ),
              ],
            );
            if (constraints.maxWidth < 760) return content;
            return Row(
              children: [
                NavigationRail(
                  selectedIndex: _tab,
                  extended: constraints.maxWidth >= 1040,
                  onDestinationSelected: (index) =>
                      setState(() => _tab = index),
                  destinations: [
                    for (final destination in destinations)
                      NavigationRailDestination(
                        icon: destination.icon,
                        selectedIcon: destination.selectedIcon,
                        label: Text(destination.label),
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
            ? NavigationBar(
                selectedIndex: _tab,
                onDestinationSelected: (i) => setState(() => _tab = i),
                destinations: destinations,
              )
            : null,
      ),
    );
  }

  /// Root back press: first press hints, a second within the window exits.
  /// Guards against losing a connected session to an accidental gesture.
  void _onRootPop(bool didPop, Object? result) {
    if (didPop) return;
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
        child: pulse
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
