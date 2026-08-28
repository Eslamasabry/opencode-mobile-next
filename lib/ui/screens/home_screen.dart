import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/sse.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../widgets/connection_status_banner.dart';
import '../widgets/pickers.dart';
import 'files_screen.dart';
import 'library_screen.dart';
import 'requests_screen.dart';
import 'settings_screen.dart';
import 'terminal_screen.dart';
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

    final tabs = [
      WorkspaceScreen(controller: conn),
      FilesScreen(controller: conn),
      TerminalScreen(controller: conn),
      LibraryScreen(controller: conn),
    ];
    const destinations = [
      NavigationDestination(
        icon: Icon(Icons.workspaces_outline),
        selectedIcon: Icon(Icons.workspaces_rounded),
        label: 'Workspace',
      ),
      NavigationDestination(
        icon: Icon(Icons.folder_outlined),
        selectedIcon: Icon(Icons.folder_rounded),
        label: 'Files',
      ),
      NavigationDestination(
        icon: Icon(Icons.terminal_outlined),
        selectedIcon: Icon(Icons.terminal_rounded),
        label: 'Terminal',
      ),
      NavigationDestination(
        icon: Icon(Icons.more_horiz_rounded),
        selectedIcon: Icon(Icons.more_rounded),
        label: 'More',
      ),
    ];
    final pending = conn.permissions.length + conn.questions.length;

    return Scaffold(
      appBar: AppBar(
        title: _WorkspaceAppBarTitle(
          profileName: conn.profile?.name ?? 'OpenCode',
          tabTitle: _titles[_tab],
          status: conn.status,
          compact: MediaQuery.sizeOf(context).width < 600,
        ),
        actions: [
          IconButton(
            tooltip: 'Model / agent',
            icon: const Icon(Icons.tune_rounded),
            onPressed: () => showModelPicker(context),
          ),
          Badge(
            isLabelVisible: pending > 0,
            label: Text('$pending'),
            child: IconButton(
              tooltip: 'Pending requests',
              icon: const Icon(Icons.notifications_outlined),
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => RequestsScreen(controller: conn),
                ),
              ),
            ),
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
                onDestinationSelected: (index) => setState(() => _tab = index),
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
              height: 64,
              onDestinationSelected: (i) => setState(() => _tab = i),
              destinations: destinations,
            )
          : null,
    );
  }

  static const _titles = ['Workspace', 'Files', 'Terminal', 'Library'];
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
          style: const TextStyle(fontSize: 17),
        ),
      ),
    );
    final page = Text(
      tabTitle,
      key: const ValueKey('current-tab-title'),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: theme.textTheme.labelSmall?.copyWith(color: theme.hintColor),
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
    final (color, pulse) = switch (status) {
      StreamStatus.connected => (AppTheme.success(theme.colorScheme), false),
      StreamStatus.connecting ||
      StreamStatus.reconnecting => (theme.colorScheme.tertiary, true),
      StreamStatus.disconnected => (theme.colorScheme.error, false),
    };
    return Tooltip(
      message: switch (status) {
        StreamStatus.connected => 'Connected',
        StreamStatus.connecting => 'Connecting',
        StreamStatus.reconnecting => 'Reconnecting',
        StreamStatus.disconnected => 'Offline',
      },
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
    );
  }
}
