import 'dart:async';
import 'dart:io' show Platform;

import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'background/live_background.dart';
import 'desktop/window_icon.dart';
import 'desktop/window_state.dart';
import 'diagnostics/app_diagnostics.dart';
import 'l10n/app_localizations.dart';
import 'platform/platform_capabilities.dart';
import 'platform/share_intent.dart';
import 'state/connection.dart';
import 'state/profiles.dart';
import 'update/desktop_release_check.dart';
import 'update/shorebird_update_notice.dart';
import 'ui/app_theme.dart';
import 'ui/desktop/desktop_interaction.dart';
import 'ui/desktop/shortcuts.dart';
import 'ui/theme_packs.dart';
import 'ui/navigation/chat_route.dart';
import 'ui/screens/settings_screen.dart';
import 'ui/widgets/product_states.dart' show productErrorText;
import 'ui/screens/guide_screen.dart';
import 'ui/screens/about_screen.dart';
import 'ui/screens/home_screen.dart';
import 'ui/screens/servers_screen.dart';
import 'ui/screens/chat_screen.dart';
import 'ui/screens/activity_screen.dart';
import 'ui/screens/termux_setup_screen.dart';
import 'ui/screens/app_diagnostics_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  if (!kIsWeb && (Platform.isLinux || Platform.isWindows || Platform.isMacOS)) {
    // Restores the remembered size, position and maximized state, clamped to
    // a display that still exists, and saves it again on close. Android never
    // reaches this call. See lib/desktop/window_state.dart.
    //
    // The one platform branch that deliberately stays on dart:io rather than
    // PlatformCapabilities: this is about the process that is actually
    // running — whether a native window exists to size — not about a feature
    // a test needs to pump both ways. `main` is never entered by the suite,
    // so routing it through an overridable seam would only add a way for a
    // stray override to leave a real desktop window unshown.
    //
    // The icon is applied after, not inside: setUpDesktopWindow completes
    // only once waitUntilReadyToShow has shown and focused the window, and
    // GTK needs a realised window to hang an icon on.
    unawaited(setUpDesktopWindow().then((_) => applyDesktopWindowIcon()));
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
  const OcApp({super.key, this.updateService, this.shareIntent});

  final AppUpdateService? updateService;

  /// Text shared in from other apps; injectable so tests can drive it.
  final ShareIntent? shareIntent;

  @override
  ConsumerState<OcApp> createState() => _OcAppState();
}

class _OcAppState extends ConsumerState<OcApp> with WidgetsBindingObserver {
  late final ConnectionController _controller;
  late final AppUpdateService _updateService;
  final _navigatorKey = GlobalKey<NavigatorState>();
  final _messengerKey = GlobalKey<ScaffoldMessengerState>();
  // Desktop only: the shell shortcut registry. Surfaces claim intents through
  // it, and the Ctrl+K launcher dispatches the same intents the keyboard does.
  final _shortcutSignals = AppShortcutSignals();
  bool _codingAlertRouteScheduled = false;
  late final ShareIntent _share;
  bool _shareRouteScheduled = false;
  bool _shareWaitingNoticeShown = false;

  @override
  void initState() {
    super.initState();
    unawaited(_harvestDynamicColors());
    _controller = ref.read(connProvider);
    _controller.addListener(_controllerChanged);
    _share = widget.shareIntent ?? ShareIntent();
    _share.pending.addListener(_scheduleShareRoute);
    unawaited(_share.start());
    // Only the Android build is Shorebird-released; desktop gets its update
    // news from the GitHub release check in DesktopReleaseNotice below.
    _updateService =
        widget.updateService ??
        (platformCapabilities.supportsCodePush
            ? ShorebirdAppUpdateService()
            : const UnavailableAppUpdateService());
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

  void _controllerChanged() {
    _scheduleCodingAlertRoute();
    _scheduleShareRoute();
  }

  /// Text shared from another app becomes the first prompt of a new session.
  /// Until a server is connected the text waits, and the user is told once
  /// where it went, so a share never silently disappears.
  void _scheduleShareRoute() {
    if (_shareRouteScheduled || _share.pending.value == null) return;
    final connected =
        _controller.api != null &&
        _controller.repository != null &&
        _controller.version != null;
    if (!connected) {
      if (!_shareWaitingNoticeShown) {
        _shareWaitingNoticeShown = true;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          _messengerKey.currentState
            ?..hideCurrentSnackBar()
            ..showSnackBar(
              const SnackBar(
                content: Text(
                  'Connect to a server and the shared text opens in a new '
                  'session.',
                ),
              ),
            );
        });
      }
      return;
    }
    _shareRouteScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      _shareRouteScheduled = false;
      _shareWaitingNoticeShown = false;
      if (!mounted) return;
      final navigator = _navigatorKey.currentState;
      if (navigator == null) {
        _scheduleShareRoute();
        return;
      }
      final text = _share.take();
      if (text == null) return;
      try {
        final session = await _controller.createSession();
        if (!mounted) return;
        await navigator.push(
          MaterialPageRoute<void>(
            builder: (_) =>
                ChatScreen(sessionID: session.id, initialText: text),
          ),
        );
      } catch (error) {
        _messengerKey.currentState
          ?..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(productErrorText(error))));
      }
    });
  }

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
            builder: (_) => ActivityScreen(
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

  // ------------------------------------------------------------------
  // Desktop shortcut layer (no-op on Android: AppShortcuts passes through).
  // ------------------------------------------------------------------

  Future<void> _startNewSession() async {
    final navigator = _navigatorKey.currentState;
    if (navigator == null) return;
    try {
      final session = await _controller.createSession();
      await navigator.pushNamed(
        '/chat/${session.id}',
        arguments: const ChatRouteArguments.newlyCreated(),
      );
    } catch (error) {
      _messengerKey.currentState
        ?..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(productErrorText(error))));
    }
  }

  void _openSettings() {
    _navigatorKey.currentState?.push(
      MaterialPageRoute<void>(
        builder: (_) => SettingsScreen(controller: _controller),
      ),
    );
  }

  List<DesktopCommand> _shellCommands(BuildContext context) {
    final mod = shortcutModifierLabel;
    void go(int index) =>
        _shortcutSignals.dispatch(SelectDestinationIntent(index));
    return [
      DesktopCommand(
        label: 'New session',
        icon: Icons.add_rounded,
        hint: 'Start a chat in the active project',
        keys: '$mod + N',
        onInvoke: () => unawaited(_startNewSession()),
      ),
      DesktopCommand(
        label: 'Workspace',
        icon: Icons.workspaces_outline,
        hint: 'Recent sessions and the active project',
        keys: '$mod + 1',
        onInvoke: () => go(0),
      ),
      DesktopCommand(
        label: 'Files',
        icon: Icons.folder_outlined,
        hint: 'Browse the project tree',
        keys: '$mod + 2',
        onInvoke: () => go(1),
      ),
      DesktopCommand(
        label: 'Activity',
        icon: Icons.notifications_outlined,
        hint: 'Permissions, questions, and forms',
        keys: '$mod + 3',
        onInvoke: () => go(2),
      ),
      DesktopCommand(
        label: 'More',
        icon: Icons.more_horiz_rounded,
        hint: 'Models, providers, terminal, settings',
        keys: '$mod + 4',
        onInvoke: () => go(3),
      ),
      DesktopCommand(
        label: 'Settings',
        icon: Icons.settings_outlined,
        keys: '$mod + ,',
        onInvoke: _openSettings,
      ),
      DesktopCommand(
        label: 'Keyboard shortcuts',
        icon: Icons.keyboard_outlined,
        keys: '$mod + /',
        onInvoke: () => unawaited(showShortcutsHelp(context)),
      ),
      DesktopCommand(
        label: 'Refresh sessions',
        icon: Icons.refresh_rounded,
        onInvoke: () => unawaited(_controller.refreshSessions()),
      ),
      DesktopCommand(
        label: 'Diagnostics',
        icon: Icons.bug_report_outlined,
        hint: 'Recent errors and connection detail',
        onInvoke: () => _navigatorKey.currentState?.pushNamed('/debug'),
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<AppAppearance>(
      valueListenable: _controller.appearance,
      builder: (context, appearance, _) => ListenableBuilder(
        listenable: Listenable.merge([
          _controller.themePack,
          harvestedDynamicPack,
        ]),
        builder: (context, _) {
          final pack = effectiveThemePack(_controller.themePack.value);
          return MaterialApp(
            navigatorKey: _navigatorKey,
            scaffoldMessengerKey: _messengerKey,
            builder: (context, child) {
              // Global text-scale safety net: the system setting passes
              // through untouched below the ceiling — including scales under
              // 1.0, which users pick deliberately — and only the extreme top
              // end is capped so a runaway scale cannot break the shell.
              final scale = MediaQuery.textScalerOf(context).scale(1);
              return MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  textScaler: TextScaler.linear(
                    scale > AppTheme.maxTextScale
                        ? AppTheme.maxTextScale
                        : scale,
                  ),
                ),
                child: ShorebirdUpdateNotice(
                  service: _updateService,
                  messengerKey: _messengerKey,
                  child: DesktopReleaseNotice(
                    messengerKey: _messengerKey,
                    // Desktop only. On Android this returns its child
                    // untouched, so the touch product gains no key handling.
                    child: AppShortcuts(
                      navigatorKey: _navigatorKey,
                      signals: _shortcutSignals,
                      handlers: AppShortcutHandlers(
                        onNewSession: () => unawaited(_startNewSession()),
                        onOpenSettings: _openSettings,
                        paletteCommands: _shellCommands,
                      ),
                      child: child ?? const SizedBox.shrink(),
                    ),
                  ),
                ),
              );
            },
            scrollBehavior: const AppScrollBehavior(),
            title: 'OpenCode',
            onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            debugShowCheckedModeBanner: false,
            themeMode: switch (appearance) {
              AppAppearance.system => ThemeMode.system,
              AppAppearance.light => ThemeMode.light,
              AppAppearance.dark => ThemeMode.dark,
            },
            theme: AppTheme.light(pack),
            darkTheme: AppTheme.dark(pack),
            initialRoute: '/',
            routes: {
              '/': (_) => _Root(),
              '/servers': (_) => const ServersScreen(),
              '/home': (_) => const HomeScreen(),
              // Activity absorbed Mission Control and Pending requests; the
              // old deep link still resolves so notifications and shortcuts
              // built against it keep working.
              '/activity': (_) => ActivityScreen(controller: _controller),
              '/requests': (_) => ActivityScreen(controller: _controller),
              '/guide': (_) => GuideScreen(embedded: false),
              '/about': (_) => const AboutScreen(),
              // Termux is an Android app. Registering the route everywhere
              // meant a desktop deep link, or any leftover push, landed on a
              // setup flow with no bridge behind it.
              if (platformCapabilities.supportsTermux)
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
          );
        },
      ),
    );
  }

  Future<void> _harvestDynamicColors() async {
    try {
      final palette = await DynamicColorPlugin.getCorePalette();
      if (palette == null) return;
      harvestedDynamicPack.value = dynamicThemePack(
        lightScheme: palette.toColorScheme(brightness: Brightness.light),
        darkScheme: palette.toColorScheme(brightness: Brightness.dark),
      );
    } catch (_) {
      // Below Android 12, on desktop, or in tests: Material You stays
      // unavailable and the OpenCode pack remains the fallback.
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _controller.removeListener(_controllerChanged);
    _share.pending.removeListener(_scheduleShareRoute);
    if (widget.shareIntent == null) _share.dispose();
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
          error: conn.lastError == null
              ? null
              : productErrorText(conn.lastError!),
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
                      failed
                          ? productErrorText(error!)
                          : 'Opening your saved workspace.',
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
                        borderRadius: BorderRadius.circular(
                          AppTheme.radiusControl,
                        ),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            Icons.dns_outlined,
                            size: 18,
                            color: scheme.onSurfaceVariant,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              baseUrl,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: scheme.onSurfaceVariant,
                                fontFamily: AppTheme.monoFamily,
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
