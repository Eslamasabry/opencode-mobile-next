import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'state/connection.dart';
import 'ui/app_theme.dart';
import 'ui/screens/guide_screen.dart';
import 'ui/screens/about_screen.dart';
import 'ui/screens/home_screen.dart';
import 'ui/screens/servers_screen.dart';
import 'ui/screens/chat_screen.dart';
import 'ui/screens/termux_setup_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final bootstrap = await AppBootstrap.create();
  final conn = ConnectionController(bootstrap.store);

  runApp(
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(bootstrap),
        connProvider.overrideWithValue(conn),
      ],
      child: const OcApp(),
    ),
  );
}

class OcApp extends ConsumerStatefulWidget {
  const OcApp({super.key});

  @override
  ConsumerState<OcApp> createState() => _OcAppState();
}

class _OcAppState extends ConsumerState<OcApp> with WidgetsBindingObserver {
  late final ConnectionController _controller;

  @override
  void initState() {
    super.initState();
    _controller = ref.read(connProvider);
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(_controller.restoreBackgroundLiveMode());
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.resumed:
        _controller.resumeFromLifecycle();
      case AppLifecycleState.hidden:
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
        _controller.suspendForLifecycle();
      case AppLifecycleState.inactive:
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'OpenCode',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark,
      darkTheme: AppTheme.dark(),
      initialRoute: '/',
      routes: {
        '/': (_) => _Root(),
        '/servers': (_) => const ServersScreen(),
        '/home': (_) => const HomeScreen(),
        '/guide': (_) => GuideScreen(embedded: false),
        '/about': (_) => const AboutScreen(),
        '/termux-setup': (_) => const TermuxSetupScreen(),
      },
      onGenerateRoute: (settings) {
        if (settings.name?.startsWith('/chat/') == true) {
          final id = settings.name!.substring('/chat/'.length);
          return MaterialPageRoute(builder: (_) => ChatScreen(sessionID: id));
        }
        return null;
      },
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}

/// Decides the start destination from persisted state.
class _Root extends ConsumerStatefulWidget {
  @override
  ConsumerState<_Root> createState() => _RootState();
}

class _RootState extends ConsumerState<_Root> {
  bool _started = false;
  late final ConnectionController _controller;

  @override
  void initState() {
    super.initState();
    _controller = ref.read(connProvider)..addListener(_changed);
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  void _connectSaved() {
    if (_started) return;
    final conn = _controller;
    final profile = conn.profile;
    if (profile == null || profile.requiresPasswordReentry) return;
    _started = true;
    WidgetsBinding.instance.addPostFrameCallback((_) => conn.connect(profile));
  }

  @override
  Widget build(BuildContext context) {
    final conn = ref.watch(connProvider);
    if (conn.profile == null) return const ServersScreen();
    if (conn.api != null && conn.repository != null && conn.version != null) {
      return const HomeScreen();
    }
    if (conn.profile!.requiresPasswordReentry) {
      return const ServersScreen();
    }
    _connectSaved();
    return Scaffold(
      body: SafeArea(
        child: _SavedServerConnectionView(
          profileName: conn.profile!.name,
          baseUrl: conn.profile!.baseUrl,
          error: conn.lastError,
          onChangeServer: () => Navigator.of(
            context,
          ).pushNamedAndRemoveUntil('/servers', (_) => false),
          onRetry: () {
            _started = false;
            _connectSaved();
          },
        ),
      ),
    );
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    super.dispose();
  }
}

class _SavedServerConnectionView extends StatelessWidget {
  final String profileName;
  final String baseUrl;
  final String? error;
  final VoidCallback onChangeServer;
  final VoidCallback onRetry;

  const _SavedServerConnectionView({
    required this.profileName,
    required this.baseUrl,
    required this.error,
    required this.onChangeServer,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final failed = error != null;

    return Semantics(
      container: true,
      liveRegion: true,
      label: failed
          ? 'Could not connect to $profileName. $error'
          : 'Connecting to $profileName',
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: scheme.surfaceContainerLow,
                borderRadius: BorderRadius.circular(24),
                border: Border.all(
                  color: scheme.outlineVariant.withValues(alpha: .75),
                ),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x33000000),
                    blurRadius: 36,
                    offset: Offset(0, 18),
                  ),
                ],
              ),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(24, 26, 24, 24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: failed
                              ? scheme.errorContainer
                              : scheme.primaryContainer,
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: Icon(
                          failed
                              ? Icons.cloud_off_outlined
                              : Icons.terminal_rounded,
                          color: failed
                              ? scheme.onErrorContainer
                              : scheme.onPrimaryContainer,
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text(
                      failed
                          ? 'Could not connect'
                          : 'Connecting to $profileName',
                      style: theme.textTheme.titleLarge,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      failed ? error! : 'Opening your saved workspace.',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: scheme.onSurfaceVariant,
                        height: 1.45,
                      ),
                    ),
                    const SizedBox(height: 18),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 10,
                      ),
                      decoration: BoxDecoration(
                        color: scheme.surfaceContainer,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            Icons.dns_outlined,
                            size: 18,
                            color: scheme.onSurfaceVariant,
                          ),
                          const SizedBox(width: 9),
                          Expanded(
                            child: Text(
                              baseUrl,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: scheme.onSurfaceVariant,
                                fontFamily: 'monospace',
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (!failed) ...[
                      const SizedBox(height: 22),
                      const LinearProgressIndicator(
                        key: ValueKey('saved-server-connect-progress'),
                        minHeight: 3,
                        borderRadius: BorderRadius.all(Radius.circular(2)),
                      ),
                    ] else ...[
                      const SizedBox(height: 22),
                      Wrap(
                        alignment: WrapAlignment.end,
                        spacing: 10,
                        runSpacing: 10,
                        children: [
                          OutlinedButton(
                            onPressed: onChangeServer,
                            child: const Text('Change server'),
                          ),
                          FilledButton.icon(
                            onPressed: onRetry,
                            icon: const Icon(Icons.refresh_rounded, size: 19),
                            label: const Text('Try again'),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
