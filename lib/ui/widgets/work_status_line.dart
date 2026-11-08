/// Phone-server helpers for a quiet notice: the leftover-process notice with
/// its Stop, and the confirm before restarting the phone's server.
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// What the line can say.
class WorkStatus {
  const WorkStatus({
    required this.id,
    required this.message,
    this.icon = AppIconography.info,
    this.tone = AppStatusTone.neutral,
    this.action,
    this.more = const [],
    this.onDismiss,
    this.messageKey,
  });

  /// Stable name of the kind of status, for keys and tests.
  final String id;
  final String message;
  final IconData icon;
  final AppStatusTone tone;
  final KitAction? action;
  final List<KitAction> more;

  /// Only when dismissing changes nothing real.
  final VoidCallback? onDismiss;
  final Key? messageKey;
}

/// Asks before restarting the phone's server: a turn in progress stops.
/// Neutral (LOOK-5): a restart is not a loss; the cancel word is "Cancel".
Future<bool> confirmPhoneServerRestart(
  BuildContext context, {
  bool commandRunning = false,
}) {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  return showKitConfirm(
    context,
    icon: AppIconography.retry,
    title: l10n.workServerRestartTitle,
    body: commandRunning
        ? l10n.workServerRestartBodyCommand
        : l10n.workServerRestartBody,
    confirmLabel: l10n.workServerRestart,
    confirmKey: const ValueKey('work-server-restart-confirm'),
  );
}
