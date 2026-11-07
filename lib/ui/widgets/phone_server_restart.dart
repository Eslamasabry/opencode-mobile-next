import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../builtin/builtin_server.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_iconography.dart';
import '../kit/kit_dialog.dart';

/// Whether the connected server is this phone's own, and how to restart it.
///
/// The one place that tells the phone's servers apart for the Work tab: the
/// in-app OpenCode restarts through [BuiltinServerStarter] and reconnects
/// afterwards. Any other server is remote: no restart.
({bool onThisPhone, Future<void> Function()? restart}) phoneServerRestartFor({
  required ConnectionController connection,
  required BuiltinServerStarter builtin,
  required BuildContext context,
}) {
  final profile = connection.profile;
  if (profile == null || connection.usesConnectionToken) {
    return (onThisPhone: false, restart: null);
  }
  // A failed restart blocks nothing else, but it must be read: one alert
  // that names what failed (KIT-15, KIT-34: a snack bar is only for Undo).
  void fail(String message) {
    if (!context.mounted) return;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    unawaited(
      showKitAlert(
        context,
        title: l10n.phoneServerRestartFailedTitle,
        body: message.isEmpty ? l10n.phoneServerRestartFailed : message,
        icon: AppIconography.restart,
        alertKey: const ValueKey('phone-server-restart-failed'),
      ),
    );
  }

  if (looksLikeInAppServer(profile)) {
    return (
      onThisPhone: true,
      restart: () async {
        final failure = await builtin.start(profile);
        if (failure != null) {
          fail('');
          return;
        }
        await connection.retryConnection();
      },
    );
  }
  return (onThisPhone: false, restart: null);
}
