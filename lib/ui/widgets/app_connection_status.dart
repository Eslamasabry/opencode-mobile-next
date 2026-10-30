import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../builtin/app_exit_recovery.dart';
import '../../builtin/builtin_server.dart';
import '../../builtin/setup/phone_setup.dart';
import '../../builtin/setup/setup_contract.dart';
import '../../builtin/thermal_guard.dart';
import '../../builtin/thermal_guard_teams.dart';
import '../../state/background_pause_notice.dart';
import '../../state/connection.dart';
import '../../termux/bridge.dart' show TermuxBridge;
import '../kit/kit.dart';
import '../screens/servers_screen.dart' show ServersRouteRequest;
import 'app_exit_notice.dart';
import 'background_pause_notice.dart';
import 'connection_status_banner.dart';
import 'phone_server_restart.dart';
import 'runtime_switch_status.dart';
import 'thermal_notice.dart';

/// App conditions live above the navigator, so every route uses one source
/// and one priority order. Rebuilding never restarts the connection clock.
class AppConnectionStatusScope extends ConsumerWidget {
  const AppConnectionStatusScope({
    super.key,
    required this.controller,
    required this.navigatorKey,
    required this.child,
  });

  final ConnectionController controller;
  final GlobalKey<NavigatorState> navigatorKey;
  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final recovery = ref.watch(appExitRecoveryProvider);
    final thermal = ref.watch(thermalGuardSlotProvider);
    final builtin = ref.watch(builtinServerStarterProvider);
    final pause = ref.watch(backgroundPauseNoticeProvider);
    BuildContext? actionContext() =>
        navigatorKey.currentState?.overlay?.context;
    return ValueListenableBuilder<ThermalGuard?>(
      valueListenable: thermal,
      builder: (context, guard, _) => ListenableBuilder(
        listenable: Listenable.merge([
          controller,
          recovery,
          builtin,
          pause,
          ?guard,
        ]),
        builder: (context, _) {
          final serverBack =
              controller.isConnected &&
              looksLikeInAppServer(controller.profile);
          final phone = phoneServerRestartFor(
            connection: controller,
            builtin: builtin,
            context: context,
          );
          return AppConditionsScope(
            conditions: [
              connectionKitStatus(
                context,
                controller,
                serverOnThisPhone: phone.onThisPhone,
                phoneSetupRunning:
                    PhoneSetup.engineIfCreated?.progress.value.state ==
                    SetupState.running,
                onRecoverOwnership:
                    builtin.failureFor(controller.profile)?.problem ==
                        BuiltinStartProblem.componentRecovery
                    ? () => unawaited(TermuxBridge.openAppSettings())
                    : null,
                actionContext: actionContext,
                switchingTo: phoneRuntimeSwitchTarget(controller, builtin),
                onRestartServer: phone.restart == null
                    ? null
                    : () async {
                        final current = actionContext();
                        if (current == null || !current.mounted) return;
                        await phoneServerRestartFor(
                          connection: controller,
                          builtin: builtin,
                          context: current,
                        ).restart?.call();
                      },
              ),
              ...appStoppedLines(
                exit: appExitKitStatus(
                  context,
                  recovery,
                  actionContext: actionContext,
                  serverBack: serverBack,
                  viewing: controller.profile,
                  onSwitchToPhone: (current, profileId) =>
                      Navigator.of(current).pushNamed(
                        '/servers',
                        arguments: ServersRouteRequest.connect(profileId),
                      ),
                ),
                paused: backgroundPauseKitStatus(
                  context,
                  pause,
                  actionContext: actionContext,
                ),
                serverBack: serverBack,
              ),
              thermalKitStatus(context, guard),
            ],
            child: AppExitFoldOnPageChange(
              recovery: recovery,
              serverBack: serverBack,
              child: child,
            ),
          );
        },
      ),
    );
  }
}

/// Ticks each time a page opens or closes on the app's navigator (bumped by
/// the shell's route tracker, lib/main_parts/root.dart).
final ValueNotifier<int> appPageChanges = ValueNotifier<int>(0);

/// Folds the "Restarted after the app closed" line away once the phone's
/// OpenCode is back, at the next moment the screen changes anyway: a page
/// opening or closing, or the app leaving the screen. It used to fold on a
/// 15-second clock while the person read the page, and the whole page slid
/// up under it (jitter J5, 2026-10-28). It still never follows them from
/// page to page (owner report, 2026-10-07).
class AppExitFoldOnPageChange extends StatefulWidget {
  const AppExitFoldOnPageChange({
    super.key,
    required this.recovery,
    required this.serverBack,
    required this.child,
  });

  final AppExitRecovery recovery;

  /// The phone's OpenCode is connected again.
  final bool serverBack;
  final Widget child;

  @override
  State<AppExitFoldOnPageChange> createState() =>
      _AppExitFoldOnPageChangeState();
}

class _AppExitFoldOnPageChangeState extends State<AppExitFoldOnPageChange>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    appPageChanges.addListener(_fold);
    // An observer, not an AppLifecycleListener: the listener asserts on a
    // jump from paused straight to resumed, which tests drive.
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    appPageChanges.removeListener(_fold);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.hidden ||
        state == AppLifecycleState.paused) {
      _fold();
    }
  }

  void _fold() {
    if (!widget.serverBack || widget.recovery.notice == null) return;
    // A page can open while the tree builds: fold right after that build,
    // before the new page's first frame is drawn.
    final phase = SchedulerBinding.instance.schedulerPhase;
    if (phase == SchedulerPhase.persistentCallbacks ||
        phase == SchedulerPhase.midFrameMicrotasks) {
      SchedulerBinding.instance.addPostFrameCallback((_) {
        if (mounted && widget.serverBack) widget.recovery.dismiss();
      });
      return;
    }
    widget.recovery.dismiss();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

/// The two "Android stopped it" lines, in the order the status slot weighs
/// them: they share [KitStatusKind.appStopped] and ties keep the first, so
/// only one shows at a time and the other waits until it is dismissed or
/// resolves.
///
/// The app exit leads while the phone's server is still down ([serverBack]
/// false): it is the bigger event, usually the cause of the pause, and it
/// says what is being restarted. Once that server is back the exit line is
/// only a recap (it folds away at the next page change), while the paused
/// background connection still needs a tap, so the pause goes first.
List<KitStatus?> appStoppedLines({
  required KitStatus? exit,
  required KitStatus? paused,
  required bool serverBack,
}) => serverBack ? [paused, exit] : [exit, paused];

/// Merges complete conditions, including changed callbacks and action state.
/// No equality shortcut may keep an old profile's actions alive.
class AppConditionsScope extends StatefulWidget {
  const AppConditionsScope({
    super.key,
    required this.conditions,
    required this.child,
  });
  final List<KitStatus?> conditions;
  final Widget child;

  @override
  State<AppConditionsScope> createState() => _AppConditionsScopeState();
}

class _AppConditionsScopeState extends State<AppConditionsScope> {
  final _conditions = ValueNotifier<List<KitStatus>>(const []);
  ValueListenable<List<KitStatus>>? _outer;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final outer = KitStatusScope.of(context);
    if (!identical(outer, _outer)) {
      _outer?.removeListener(_merge);
      _outer = outer..addListener(_merge);
    }
    _merge();
  }

  @override
  void didUpdateWidget(AppConditionsScope oldWidget) {
    super.didUpdateWidget(oldWidget);
    _merge();
  }

  void _merge() =>
      _conditions.value = [...?_outer?.value, ...widget.conditions.nonNulls];

  @override
  Widget build(BuildContext context) =>
      KitStatusScope(conditions: _conditions, child: widget.child);

  @override
  void dispose() {
    _outer?.removeListener(_merge);
    _conditions.dispose();
    super.dispose();
  }
}
