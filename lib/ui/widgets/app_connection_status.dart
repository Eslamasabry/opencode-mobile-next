import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../builtin/app_exit_recovery.dart';
import '../../builtin/builtin_server.dart';
import '../../builtin/thermal_guard.dart';
import '../../builtin/thermal_guard_teams.dart';
import '../../state/connection.dart';
import '../kit/kit.dart';
import 'app_exit_notice.dart';
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
    BuildContext? actionContext() =>
        navigatorKey.currentState?.overlay?.context;
    return ValueListenableBuilder<ThermalGuard?>(
      valueListenable: thermal,
      builder: (context, guard, _) => ListenableBuilder(
        listenable: Listenable.merge([controller, recovery, builtin, ?guard]),
        builder: (context, _) {
          final serverBack =
              controller.isConnected &&
              looksLikeInAppServer(controller.profile);
          // Starts a timer only; never notifies during build.
          if (serverBack) recovery.noteServerBack();
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
              appExitKitStatus(
                context,
                recovery,
                actionContext: actionContext,
                serverBack: serverBack,
              ),
              thermalKitStatus(context, guard),
            ],
            child: child,
          );
        },
      ),
    );
  }
}

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
