import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/sse.dart';
import '../../state/connection.dart';
import '../widgets/pickers.dart';
import 'chat_screen.dart';
import 'files_screen.dart';
import 'guide_screen.dart';

/// Main app shell once connected: Chats / Files / Guide tabs.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  int _tab = 0;

  @override
  void initState() {
    super.initState();
    final conn = ref.read(connProvider);
    conn.addListener(_onConnChanged);
    // If the SSE stream cannot connect at all, fall back to polling.
    if (conn.status == StreamStatus.disconnected) {
      conn.enablePollingFallback();
    }
  }

  void _onConnChanged() {
    if (!mounted) return;
    final conn = ref.read(connProvider);
    if (conn.status == StreamStatus.disconnected && conn.api == null) {
      Navigator.of(context).pushNamedAndRemoveUntil('/servers', (_) => false);
      return;
    }
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
    final theme = Theme.of(context);
    final navigator = Navigator.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Row(children: [
          _StatusDot(status: conn.status),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              conn.profile?.name ?? 'OpenCode',
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 17),
            ),
          ),
          if (conn.version != null)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
              decoration: BoxDecoration(
                color:
                    theme.colorScheme.surfaceContainerHighest.withValues(alpha: .6),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text('v${conn.version}',
                  style: theme.textTheme.labelSmall!
                      .copyWith(color: theme.hintColor)),
            ),
        ]),
        actions: [
          IconButton(
            tooltip: 'Model / agent',
            icon: const Icon(Icons.tune_rounded),
            onPressed: () => showModelPicker(context, ref),
          ),
          PopupMenuButton<String>(
            onSelected: (v) {
              if (v == 'refresh') conn.refreshSessions();
              if (v == 'guide') navigator.pushNamed('/guide');
              if (v == 'disconnect') {
                conn.disconnect().then((_) {
                  navigator.pushNamedAndRemoveUntil('/servers', (_) => false);
                });
              }
            },
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'refresh', child: Text('Refresh sessions')),
              PopupMenuItem(value: 'guide', child: Text('Setup guide')),
              PopupMenuItem(value: 'disconnect', child: Text('Disconnect')),
            ],
          ),
        ],
      ),
      body: IndexedStack(
        index: _tab,
        children: [
          SessionsTab(controller: conn),
          FilesScreen(controller: conn),
          GuideScreen(embedded: true),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        height: 64,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(
              icon: Icon(Icons.chat_bubble_outline_rounded),
              selectedIcon: Icon(Icons.chat_bubble_rounded),
              label: 'Chats'),
          NavigationDestination(
              icon: Icon(Icons.folder_outlined),
              selectedIcon: Icon(Icons.folder_rounded),
              label: 'Files'),
          NavigationDestination(
              icon: Icon(Icons.menu_book_outlined),
              selectedIcon: Icon(Icons.menu_book_rounded),
              label: 'Guide'),
        ],
      ),
    );
  }
}

class _StatusDot extends StatelessWidget {
  final StreamStatus status;
  const _StatusDot({required this.status});

  @override
  Widget build(BuildContext context) {
    final (color, pulse) = switch (status) {
      StreamStatus.connected => (Colors.green.shade400, false),
      StreamStatus.connecting || StreamStatus.reconnecting =>
        (Colors.orange.shade400, true),
      StreamStatus.disconnected => (Colors.red.shade400, false),
    };
    return Tooltip(
      message: switch (status) {
        StreamStatus.connected => 'Live event stream connected',
        StreamStatus.connecting => 'Connecting…',
        StreamStatus.reconnecting => 'Reconnecting…',
        StreamStatus.disconnected => 'Disconnected (polling fallback)',
      },
      child: pulse
          ? SizedBox(
              width: 12,
              height: 12,
              child: CircularProgressIndicator(strokeWidth: 2, color: color))
          : Container(
              width: 10,
              height: 10,
              decoration:
                  BoxDecoration(color: color, shape: BoxShape.circle)),
    );
  }
}
