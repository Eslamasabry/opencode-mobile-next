/// The confirm before restarting the phone's server.
library;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// Asks before restarting the phone's server: a turn in progress stops.
/// Neutral (LOOK-5): a restart is not a loss; the cancel word is "Cancel".
Future<bool> confirmPhoneServerRestart(BuildContext context) {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  return showKitConfirm(
    context,
    icon: AppIconography.retry,
    title: l10n.workServerRestartTitle,
    body: l10n.workServerRestartBody,
    confirmLabel: l10n.workServerRestart,
    confirmKey: const ValueKey('work-server-restart-confirm'),
  );
}
