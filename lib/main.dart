import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:window_manager/window_manager.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'background/live_background.dart';
import 'diagnostics/app_diagnostics.dart';
import 'state/connection.dart';
import 'state/profiles.dart';
import 'update/desktop_release_check.dart';
import 'update/shorebird_update_notice.dart';
import 'ui/app_theme.dart';
import 'ui/navigation/chat_route.dart';
import 'ui/screens/guide_screen.dart';
import 'ui/screens/about_screen.dart';
import 'ui/screens/home_screen.dart';
import 'ui/screens/servers_screen.dart';
import 'ui/screens/chat_screen.dart';
import 'ui/screens/requests_screen.dart';
import 'ui/screens/termux_setup_screen.dart';
import 'ui/screens/app_diagnostics_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  if (!kIsWeb &&
      (Platform.isLinux || Platform.isWindows || Platform.isMacOS)) {
    // Desktop windows get a sane default and floor; Android never reaches
    // these calls.
    await windowManager.ensureInitialized();
    const options = WindowOptions(
      size: Size(900, 700),
      minimumSize: Size(480, 600),
      title: 'OpenCode',
    );
    unawaited(
      windowManager.waitUntilReadyToShow(options, () async {
        await windowManager.show();
        await windowManager.focus();
      }),
    );
  }
  final diagnostics = AppDiagnosticsController();
  installAppErrorCapture(diagnostics);
  runApp(AppBootstrapGate(diagnostics: diagnostics));
}

typedef AppBootstrapLoader = Future<AppBootstrap> Function();

/// Renders immediately so a preferences or secure-storage failure can never
/// leave Android showing a blank native window.
class AppBootstrapGate extends StatefulWidget {
  const AppBootstrapGate({super.key, required this.diagnostics, this.loader});

  final AppDiagnosticsController diagnostics;
  final AppBootstrapLoader? loader;

  @override
  State<AppBootstrapGate> createState() => _AppBootstrapGateState();
}

class _AppBootstrapGateState extends State<AppBootstrapGate> {
  AppBootstrap? _bootstrap;
  ConnectionController? _controller;
  Object? _error;
  bool _loading = true;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final bootstrap = await (widget.loader ?? AppBootstrap.create)();
      if (!mounted || generation != _generation) return;
      final controller = ConnectionController(
        bootstrap.store,
        diagnostics: widget.diagnostics,
      );
      _controller?.dispose();
      setState(() {
        _bootstrap = bootstrap;
        _controller = controller;
        _loading = false;
      });
    } catch (error, stack) {
      widget.diagnostics.record(error, stack, source: 'bootstrap');
      if (!mounted || generation != _generation) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final bootstrap = _bootstrap;
    final controller = _controller;
    if (bootstrap != null && controller != null) {
      return ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(bootstrap),
          connProvider.overrideWithValue(controller),
        ],
        child: const OcApp(),
      );
    }
    return MaterialApp(
      title: 'OpenCode',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      home: Scaffold(
        body: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: _loading
                    ? const Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          CircularProgressIndicator(),
                          SizedBox(height: 18),
                          Text('Starting OpenCode…'),
                        ],
                      )
                    : Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.error_outline_rounded,
                            size: 40,
                            color: Theme.of(context).colorScheme.error,
                          ),
                          const SizedBox(height: 14),
                          Text(
                            'OpenCode could not start',
                            style: Theme.of(context).textTheme.headlineSmall,
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            widget.diagnostics.sanitize(
                              _error?.toString() ?? 'Unknown startup error',
                              limit: 300,
                            ),
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.bodyMedium
                                ?.copyWith(
                                  color: Theme.of(
                                    context,
                                  ).colorScheme.onSurfaceVariant,
                                ),
                          ),
                          const SizedBox(height: 20),
                          FilledButton.icon(
                            key: const ValueKey('retry-app-bootstrap'),
                            onPressed: _load,
                            icon: const Icon(Icons.refresh_rounded),
                            label: const Text('Try again'),
                          ),
                        ],
                      ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _generation++;
    _controller?.dispose();
    super.dispose();
  }
}

class OcApp extends ConsumerStatefulWidget {
  const OcApp({super.key, this.updateService});

  final AppUpdateService? updateService;

  @override
  ConsumerState<OcApp> createState() => _OcAppState();
}

class _OcAppState extends ConsumerState<OcApp> with WidgetsBindingObserver {
  late final ConnectionController _controller;
  late final AppUpdateService _updateService;
  final _navigatorKey = GlobalKey<NavigatorState>();
  final _messengerKey = GlobalKey<ScaffoldMessengerState>();
  bool _codingAlertRouteScheduled = false;

  @override
  void initState() {
    super.initState();
    _controller = ref.read(connProvider);
    _controller.addListener(_controllerChanged);
    _updateService = widget.updateService ?? ShorebirdAppUpdateService();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(_controller.restoreBackgroundLiveMode());
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.resumed:
        unawaited(_resumeAndConsumeCodingAlert());
      case AppLifecycleState.hidden:
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
        _controller.suspendForLifecycle();
      case AppLifecycleState.inactive:
        break;
    }
  }

  Future<void> _resumeAndConsumeCodingAlert() async {
    // Start destination capture before wake reconciliation performs any
    // potentially slow health or catalog requests, while allowing transport
    // recovery to proceed in parallel. Routing still waits for a usable
    // transport when the background connection had been suspended.
    await Future.wait<void>([
      _controller.consumeCodingAlertOpen(),
      _controller.resumeFromLifecycle(),
    ]);
  }

  void _controllerChanged() => _scheduleCodingAlertRoute();

  void _scheduleCodingAlertRoute() {
    if (_codingAlertRouteScheduled ||
        _controller.pendingCodingAlertOpen == null ||
        _controller.api == null ||
        _controller.repository == null ||
        _controller.version == null) {
      return;
    }
    _codingAlertRouteScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _codingAlertRouteScheduled = false;
      if (!mounted) return;
      final navigator = _navigatorKey.currentState;
      if (navigator == null) {
        _scheduleCodingAlertRoute();
        return;
      }
      final target = _controller.takePendingCodingAlertOpen();
      if (target == null) return;
      if (target.kind == CodingAlertKind.question) {
        navigator.push(
          MaterialPageRoute<void>(
            builder: (_) => RequestsScreen(
              controller: _controller,
              initialQuestionSessionID: target.sessionID,
            ),
          ),
        );
        return;
      }
      navigator.push(
        MaterialPageRoute<void>(
          builder: (_) => ChatScreen(sessionID: target.sessionID),
        ),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<AppAppearance>(
      valueListenable: _controller.appearance,
      builder: (context, appearance, _) => MaterialApp(
        navigatorKey: _navigatorKey,
        scaffoldMessengerKey: _messengerKey,
        builder: (context, child) => ShorebirdUpdateNotice(
          service: _updateService,
          messengerKey: _messengerKey,
          child: DesktopReleaseNotice(
            messengerKey: _messengerKey,
            child: child ?? const SizedBox.shrink(),
          ),
        ),
        title: 'OpenCode',
        debugShowCheckedModeBanner: false,
        themeMode: switch (appearance) {
          AppAppearance.system => ThemeMode.system,
          AppAppearance.light => ThemeMode.light,
          AppAppearance.dark => ThemeMode.dark,
        },
        theme: AppTheme.light(),
        darkTheme: AppTheme.dark(),
        initialRoute: '/',
        routes: {
          '/': (_) => _Root(),
          '/servers': (_) => const ServersScreen(),
          '/home': (_) => const HomeScreen(),
          '/guide': (_) => GuideScreen(embedded: false),
          '/about': (_) => const AboutScreen(),
          '/termux-setup': (_) => const TermuxSetupScreen(),
          '/debug': (_) => AppDiagnosticsScreen(controller: _controller),
        },
        onGenerateRoute: (settings) {
          if (settings.name?.startsWith('/chat/') == true) {
            final id = settings.name!.substring('/chat/'.length);
            final arguments = settings.arguments;
            return MaterialPageRoute(
              builder: (_) => ChatScreen(
                sessionID: id,
                discardIfUntouched:
                    arguments is ChatRouteArguments &&
                    arguments.discardIfUntouched,
              ),
            );
          }
          return null;
        },
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _controller.removeListener(_controllerChanged);
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
                                fontFamily: 'AppMono',
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
