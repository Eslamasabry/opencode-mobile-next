import 'ui/search/search_index.dart';
import 'ui/screens/profile_monitor_screen.dart';
import 'ui/screens/usage_hub_screen.dart';
import 'dart:async';
import 'dart:io' show Platform;
import 'dart:ui' show PlatformDispatcher;

import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/foundation.dart' show kIsWeb, visibleForTesting;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'background/live_background.dart';
import 'builtin/app_exit_recovery.dart';
import 'builtin/builtin_server.dart';
import 'builtin/phone_server_healing.dart';
import 'builtin/reply_watch.dart';
import 'builtin/setup/phone_setup.dart';
import 'builtin/setup/setup_engine.dart' show ChannelSetupEngine;
import 'builtin/setup/setup_finish.dart';
import 'builtin/setup/termux_setup_finish.dart';
import 'builtin/team/builtin_team.dart' show BuiltinTeam;
import 'builtin/thermal_guard.dart' show ThermalNoticeKind;
import 'builtin/thermal_guard_teams.dart';
import 'desktop/window_icon.dart';
import 'desktop/window_state.dart';
import 'diagnostics/app_diagnostics.dart';
import 'diagnostics/perf_trace.dart';
import 'diagnostics/report_problem_startup.dart';
import 'domain/connection_status.dart';
import 'domain/server_gateway.dart' show ProductException;
import 'l10n/app_localizations.dart';
import 'platform/launch_shortcut.dart';
import 'platform/session_link.dart';
import 'state/session_address_controller.dart'
    show SessionAddressFailure, sessionAddressProvider;
import 'platform/platform_capabilities.dart';
import 'platform/share_intent.dart';
import 'domain/session_handoff.dart';
import 'domain/while_away.dart' show AutomaticActKind;
import 'domain/team_link.dart';
import 'state/connection.dart';
import 'state/automation_policy.dart';
import 'state/local_server_controls.dart';
import 'termux/bridge.dart';
import 'state/profiles.dart';
import 'state/session_inventory_cache.dart' show SessionInventoryPreview;
import 'update/desktop_release_check.dart';
import 'update/shorebird_update_notice.dart';
import 'ui/app_theme.dart';
import 'ui/capability_flows.dart';
import 'ui/desktop/desktop_interaction.dart';
import 'ui/desktop/shortcuts.dart';
import 'ui/kit/kit.dart';
import 'ui/theme_packs.dart';
import 'ui/navigation/attention_landing.dart'
    show chatLandingPage, chatLandingRoute;
import 'ui/navigation/chat_route.dart';
import 'ui/screens/settings_screen.dart';
import 'ui/widgets/product_states.dart' show productErrorText;
import 'ui/widgets/saved_server_connection_card.dart';
import 'ui/widgets/session_address_sheets.dart';
import 'ui/widgets/last_known_sessions.dart';
import 'ui/widgets/app_connection_status.dart';
import 'ui/widgets/phone_server_card.dart' show serverDisplayName;
import 'ui/screens/guide_screen.dart';
import 'ui/screens/about_screen.dart';
import 'ui/screens/home_screen.dart';
import 'ui/screens/servers_screen.dart';
import 'ui/screens/chat_screen.dart';
import 'ui/screens/team/project_destination.dart';
import 'ui/screens/team_conversation/team_conversation.dart'
    show TeamConversation;
import 'ui/screens/this_phone_screen.dart';
import 'state/phone_host.dart' show PhoneHostKind;
import 'ui/screens/phone_setup/phone_setup_routes.dart'
    show openPhoneSetupFromNotification, openPhoneSetupStart;
import 'ui/screens/app_diagnostics_screen.dart';
import 'manage_space_main.dart' show runManageSpaceApp;

part 'main_parts/bootstrap_gate.dart';
part 'main_parts/share_route.dart';
part 'main_parts/launch_route.dart';
part 'main_parts/link_routes.dart';
part 'main_parts/alerts_and_shortcuts.dart';
part 'main_parts/root.dart';

/// Android's App info › Storage › Manage space (ManageSpaceActivity): a
/// small app of its own that guards "Clear storage".
@pragma('vm:entry-point')
void manageSpaceMain() => runManageSpaceApp();

Future<void> main() async {
  // First thing: starts the trace clock, so every later OCTRACE `at=` reads
  // as time since launch.
  PerfTrace.markOnce('app.main');
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
  WidgetsBinding.instance.addPostFrameCallback((_) {
    PerfTrace.markOnce('app.first_frame');
    // Disk-backed diagnostics are not needed to paint the opening state.
    // Capture imports buffered errors/timings when the store attaches, so
    // installing in-memory error capture above still protects early failures.
    unawaited(ReportProblemStartup.start(diagnostics));
  });
}

typedef AppBootstrapLoader = Future<AppBootstrap> Function();

class OcApp extends ConsumerStatefulWidget {
  const OcApp({
    super.key,
    this.updateService,
    this.shareIntent,
    this.launchShortcut,
    this.sessionLinkIntent,
  });

  final AppUpdateService? updateService;

  /// Text shared in from other apps; injectable so tests can drive it.
  final ShareIntent? shareIntent;

  /// Home-screen shortcut actions (Connect, New task); injectable so tests
  /// can drive it. Absent, the app owns a real [LaunchShortcut].
  final LaunchShortcut? launchShortcut;

  /// Session handoff links (`opencode-mobile://session`) opened on this
  /// phone; injectable so tests can drive it. Absent, the app owns a real
  /// [SessionLinkIntent].
  final SessionLinkIntent? sessionLinkIntent;

  @override
  ConsumerState<OcApp> createState() => _OcAppState();
}

class _OcAppState extends ConsumerState<OcApp> with WidgetsBindingObserver {
  late final ConnectionController _controller;
  late final AppUpdateService _updateService;
  final _navigatorKey = GlobalKey<NavigatorState>();
  // Desktop only: the shell shortcut registry. Surfaces claim intents through
  // it, and the Ctrl+K launcher dispatches the same intents the keyboard does.
  final _shortcutSignals = AppShortcutSignals();
  bool _codingAlertRouteScheduled = false;
  late final ShareIntent _share;
  bool _shareRouteScheduled = false;
  String? _failedShareText;
  bool _shareWaitingNoticeShown = false;
  late final LaunchShortcut _launchShortcut;
  bool _launchRouteScheduled = false;
  bool _launchWaitingNoticeShown = false;
  late final SessionLinkIntent _sessionLink;
  bool _linkRouteScheduled = false;
  bool _teamLinkRouteScheduled = false;
  bool _addressLinkRouteScheduled = false;
  bool _addressSheetOpen = false;
  bool _linkWaitingNoticeShown = false;
  // Tracks the route on top of the shell navigator so a shortcut never
  // stacks a second servers screen over one already showing.
  final _routeTracker = _TopRouteTracker();
  final _routeTiming = PerfTraceNavigatorObserver();

  /// The app's own condition in every screen's one status line (added to
  /// KitStatusScope above the navigator): a share or a launch waiting for
  /// the server, and what became of one that could not open. It replaced
  /// the snackbars and the share banner (G1).
  final _notice = ValueNotifier<KitStatus?>(null);
  Timer? _noticeTimer;
  Object? _shareFailure;
  int _shareFailures = 0;

  static const _waitingNotice = 'app:waiting';
  static const _shareWaitingNotice = 'app:share-waiting';
  static const _shareFailedNotice = 'app:share-failed';
  static const _oneShotNotice = 'app:notice';

  @override
  void initState() {
    super.initState();
    unawaited(_harvestDynamicColors());
    _controller = ref.read(connProvider);
    // Every capabilities.json enable flow resolves to its page (C20), so a
    // missing capability offers "Turn it on" wherever that can happen here.
    registerCapabilityFlows(_controller);
    ref.read(phoneServerHealingProvider);
    _controller.addListener(_controllerChanged);
    _share = widget.shareIntent ?? ShareIntent();
    _share.pending.addListener(_scheduleShareRoute);
    unawaited(_share.start());
    _launchShortcut = widget.launchShortcut ?? LaunchShortcut();
    _launchShortcut.pending.addListener(_scheduleLaunchRoute);
    _launchShortcut.pendingSession.addListener(_scheduleLaunchRoute);
    unawaited(_launchShortcut.start());
    _sessionLink = widget.sessionLinkIntent ?? SessionLinkIntent();
    _sessionLink.pending.addListener(_scheduleSessionLinkRoute);
    _sessionLink.pendingTeam.addListener(_scheduleTeamLinkRoute);
    _sessionLink.pendingAddress.addListener(_scheduleAddressLinkRoute);
    _sessionLink.pendingAddressFailure.addListener(_scheduleAddressLinkRoute);
    unawaited(_sessionLink.start());
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
    ref
        .read(phoneServerHealingProvider)
        .setForeground(state == AppLifecycleState.resumed);
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
      _resumeTransport(),
    ]);
  }

  /// Resume uses the same durable recovery budget as crash monitoring.
  Future<void> _resumeTransport() async {
    final profile = _controller.profile;
    final healing = ref.read(phoneServerHealingProvider);
    if (looksLikeInAppServer(profile)) await healing.check(profile!);
    await _controller.resumeFromLifecycle();
    if (looksLikeInAppServer(profile)) await healing.connectIfNeeded(profile!);
  }

  /// Shows [status] as the app's line. A [lasting] one stays until its
  /// condition ends; any other goes after the kit's undo window, except
  /// under accessible navigation, where it waits for Dismiss.
  void _showNotice(KitStatus status, {bool lasting = false}) {
    _noticeTimer?.cancel();
    _noticeTimer = null;
    _notice.value = status;
    if (lasting) return;
    _noticeTimer = Timer(KitMotion.undoWindow, () {
      final context = _navigatorKey.currentContext;
      if (context != null && MediaQuery.accessibleNavigationOf(context)) {
        return;
      }
      _clearNotice(status.id!);
    });
  }

  void _clearNotice(String id) {
    if (_notice.value?.id != id) return;
    _noticeTimer?.cancel();
    _noticeTimer = null;
    _notice.value = null;
  }

  /// A line that says what just happened, with Dismiss. [supporting] is
  /// words ([productErrorText]), never the raw failure.
  void _say(String message, {String? supporting, bool failed = false}) {
    _showNotice(
      KitStatus(
        // `work`: the person's own act, above "Update ready".
        kind: KitStatusKind.work,
        id: _oneShotNotice,
        icon: failed ? AppIconography.error : AppIconography.info,
        tone: failed ? AppStatusTone.failure : AppStatusTone.neutral,
        message: message,
        supporting: supporting,
        onDismiss: () => _clearNotice(_oneShotNotice),
      ),
    );
  }

  /// A launch, link or share that opens once the server answers.
  void _showWaiting(String id, String message) {
    _showNotice(
      KitStatus(
        kind: KitStatusKind.work,
        id: id,
        icon: AppIconography.waiting,
        message: message,
      ),
      lasting: true,
    );
  }

  void _controllerChanged() {
    _scheduleCodingAlertRoute();
    _scheduleShareRoute();
    _scheduleLaunchRoute();
    _scheduleSessionLinkRoute();
    _scheduleTeamLinkRoute();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<AppAppearance>(
      valueListenable: _controller.appearance,
      builder: (context, appearance, _) => ListenableBuilder(
        listenable: Listenable.merge([
          _controller.themePack,
          _controller.appLocale,
          harvestedDynamicPack,
        ]),
        builder: (context, _) {
          final pack = effectiveThemePack(_controller.themePack.value);
          return MaterialApp(
            navigatorKey: _navigatorKey,
            navigatorObservers: [_routeTracker, _routeTiming],
            builder: (context, child) {
              // Global text-scale safety net: only the extreme top end is
              // capped (KitText.appScaler).
              return Theme(
                data: AppTheme.forLocale(
                  Theme.of(context),
                  Localizations.localeOf(context),
                ),
                child: MediaQuery(
                  data: MediaQuery.of(context).copyWith(
                    textScaler: KitText.appScaler(
                      MediaQuery.textScalerOf(context),
                      max: AppTheme.maxTextScale,
                    ),
                  ),
                  // The app's own line joins the conditions every screen's
                  // status slot reads; the update notices below add theirs.
                  child: AppConnectionStatusScope(
                    controller: _controller,
                    navigatorKey: _navigatorKey,
                    child: ValueListenableBuilder<KitStatus?>(
                      valueListenable: _notice,
                      builder: (context, notice, notices) =>
                          UpdateStatusScope(status: notice, child: notices!),
                      child: ShorebirdUpdateNotice(
                        service: _updateService,
                        currentProfileId: () => _controller.profile?.id,
                        allowsAutomaticUpdate: (id) =>
                            AutomationPolicyController.forProfile(
                              _controller.store.prefs,
                              id,
                            ).value.allows(AutomationBehavior.applyCodePush),
                        onDownloaded:
                            ({
                              required profileId,
                              required eventId,
                              required at,
                            }) async {
                              await _controller.recordServerAct(
                                profileId: profileId,
                                kind: AutomaticActKind.update,
                                eventId: eventId,
                                at: at,
                              );
                            },
                        child: DesktopReleaseNotice(
                          navigatorKey: _navigatorKey,
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
                            // Settings › Appearance › Effects, above the
                            // navigator so every route and its transitions read
                            // the same choices. Calls without a context (a send
                            // from a controller) obey Vibration via the flag.
                            child: ValueListenableBuilder<KitEffects>(
                              valueListenable: _controller.effects,
                              builder: (context, effects, navigator) {
                                KitHaptics.enabled = effects.haptics;
                                return KitEffectsScope(
                                  effects: effects,
                                  child: navigator!,
                                );
                              },
                              child: child ?? const SizedBox.shrink(),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              );
            },
            scrollBehavior: const KitScrollBehavior(),
            title: 'OpenCode Mobile',
            onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            locale: _controller.appLocale.value,
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
              '/': (_) => _Root(say: _say),
              '/servers': (_) => const ServersScreen(),
              '/home': (_) => const HomeScreen(),
              '/guide': (_) => GuideScreen(embedded: false),
              '/about': (_) => const AboutScreen(),
              // This phone (in the app or in Termux) exists only on
              // Android: a desktop deep link, or any leftover push, must not
              // land on a page with no phone behind it.
              if (platformCapabilities.supportsTermux)
                thisPhoneRoute: (context) => ThisPhoneScreen(
                  kind:
                      ModalRoute.of(context)?.settings.arguments
                          as PhoneHostKind?,
                ),
              '/debug': (_) => AppDiagnosticsScreen(controller: _controller),
            },
            onGenerateRoute: (settings) {
              if (settings.name?.startsWith('/chat/') == true) {
                final id = settings.name!.substring('/chat/'.length);
                final arguments = settings.arguments;
                final chat = arguments is ChatRouteArguments
                    ? arguments
                    : const ChatRouteArguments();
                // Built once for the page (P4.2a landing scope).
                final page = chatLandingPage(
                  sessionID: id,
                  discardIfUntouched: chat.discardIfUntouched,
                  focusComposer: chat.focusComposer,
                  landOnRequestID: chat.landOnRequestID,
                  landOnFailure: chat.landOnFailure,
                  menuAction: chat.menuAction,
                );
                return KitPageRoute<void>(builder: (_) => page);
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
    _launchShortcut.pending.removeListener(_scheduleLaunchRoute);
    _launchShortcut.pendingSession.removeListener(_scheduleLaunchRoute);
    if (widget.launchShortcut == null) _launchShortcut.dispose();
    _sessionLink.pending.removeListener(_scheduleSessionLinkRoute);
    _sessionLink.pendingTeam.removeListener(_scheduleTeamLinkRoute);
    _sessionLink.pendingAddress.removeListener(_scheduleAddressLinkRoute);
    _sessionLink.pendingAddressFailure.removeListener(
      _scheduleAddressLinkRoute,
    );
    if (widget.sessionLinkIntent == null) _sessionLink.dispose();
    _noticeTimer?.cancel();
    _notice.dispose();
    super.dispose();
  }
}
