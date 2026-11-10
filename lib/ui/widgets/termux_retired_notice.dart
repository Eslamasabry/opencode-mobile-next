/// The one thing the app still says about Termux (owner decision,
/// 2026-11-07): it is no longer supported, what to use instead, and that the
/// projects in it are still there. Nothing is imported from Termux.
///
/// Built from kit parts only: a [KitNotice] with the two ways forward.
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../builtin/builtin_linux.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/kit.dart';
import '../screens/phone_setup/phone_setup_routes.dart';
import '../screens/servers_screen.dart' show ServersRouteRequest;
import '../../telemetry/analytics_events.dart';
import '../../telemetry/telemetry_provider.dart';

class TermuxRetiredNotice extends StatelessWidget {
  const TermuxRetiredNotice({
    super.key,
    this.onSetUpLinux,
    this.onConnectComputer,
  });

  /// Tests replace the two ways forward; the app opens phone setup and the
  /// server editor.
  final VoidCallback? onSetUpLinux;
  final VoidCallback? onConnectComputer;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return KitNotice(
      key: const ValueKey('termux-retired-notice'),
      icon: AppIconography.info,
      message: l10n.termuxRetiredNotice,
      actions: [
        if (BuiltinLinux.supported || onSetUpLinux != null)
          KitAction(
            key: const ValueKey('termux-retired-set-up-linux'),
            label: l10n.termuxRetiredSetUpLinux,
            onPressed:
                onSetUpLinux ?? () => unawaited(openPhoneSetupStart(context)),
          ),
        KitAction(
          key: const ValueKey('termux-retired-connect-computer'),
          label: l10n.chatsHomeConnectComputer,
          onPressed: () {
            AppTelemetry.track(AnalyticsEvent.connectComputerChosen);
            final connect = onConnectComputer;
            if (connect != null) return connect();
            unawaited(
              Navigator.of(context).pushNamed(
                '/servers',
                arguments: const ServersRouteRequest.add(),
              ),
            );
          },
        ),
      ],
    );
  }
}
