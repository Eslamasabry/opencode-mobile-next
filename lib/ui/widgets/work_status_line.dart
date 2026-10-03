/// Phone-server helpers for a quiet notice: the leftover-process notice with
/// its Stop, and the confirm before restarting the phone's server.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart' show SemanticsService;

import '../../l10n/app_localizations.dart';
import '../../termux/bridge.dart' show TermuxBridgeException;
import '../../termux/processes.dart'
    show TermuxProcessStopResult, TermuxProcesses;
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

/// A process on the phone that keeps using the CPU with nothing waiting on
/// it, as a platform watcher reports it (see `termux_phone_tools.dart`).
///
/// Its one action stops that process ("Stop java"): the line already says
/// what it is doing, so the act that ends it lives on it, asked first with
/// [stopRunawayHelper]. A stop that did not end it keeps the line, in the
/// failure tone, with the same action to try again.
class WorkRunawayNotice {
  const WorkRunawayNotice({
    required this.identity,
    required this.helper,
    required this.busyFor,
    required this.onStop,
    required this.onDismiss,
    this.project,
    this.stopFailed = false,
  });

  /// Changes when the process does; a dismissal holds only for one identity.
  final Object identity;

  /// The process's own name ("java", "node"), as the phone reports it.
  final String helper;

  /// The project folder's name when the process runs inside one; null for
  /// the projects folder itself or anywhere else.
  final String? project;

  /// Already formatted ("10 min").
  final String busyFor;

  /// Asks and stops; the watcher runs [stopRunawayHelper] with its context.
  final VoidCallback onStop;
  final VoidCallback onDismiss;

  /// The last stop did not end the process.
  final bool stopFailed;

  /// The process's name as the line says it: its last path part, never a
  /// folder ("/usr/bin/node" is "node").
  String get helperName {
    final parts = helper.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? helper.trim() : parts.last;
  }

  /// OpenCode itself is the one busy, not a helper it left behind: only
  /// then does the line name OpenCode.
  bool get isOpenCode => helperName.toLowerCase().startsWith('opencode');

  /// [onSeeRunning] opens Running on this phone, where every process is,
  /// offered under the line's More.
  WorkStatus status(AppLocalizations l10n, {VoidCallback? onSeeRunning}) =>
      WorkStatus(
        id: 'runaway',
        icon: stopFailed ? AppIconography.warning : AppIconography.processor,
        tone: stopFailed ? AppStatusTone.failure : AppStatusTone.neutral,
        message: stopFailed
            ? l10n.workRunawayStopFailed(helperName)
            : isOpenCode
            ? (project == null
                  ? l10n.workRunaway(busyFor)
                  : l10n.workRunawayInProject(project!, busyFor))
            : project == null
            ? l10n.workRunawayHelper(helperName, busyFor)
            : l10n.workRunawayHelperInProject(helperName, project!, busyFor),
        action: KitAction(
          key: const ValueKey('work-status-runaway-stop'),
          label: l10n.termuxProcsStopSemantics(helperName),
          onPressed: onStop,
        ),
        more: [
          if (onSeeRunning != null)
            KitAction(
              key: const ValueKey('work-status-runaway-see-running'),
              label: l10n.workRunawaySeeRunning,
              onPressed: onSeeRunning,
            ),
        ],
        onDismiss: onDismiss,
      );
}

/// How [stopRunawayHelper] ended.
enum RunawayStopOutcome {
  /// The person kept it running (cancelled the question).
  kept,

  /// The process ended.
  stopped,

  /// The stop did not end it (still running, refused, or the phone's tools
  /// did not answer).
  failed,
}

/// Asks before stopping the leftover process [pid] named [helper], stops
/// exactly that one with [TermuxProcesses.stopPid] ([stop] in tests), and
/// announces the result once to a screen reader: "Stopped java" or "Couldn't
/// stop java…". The line itself shows the rest: it goes away, or stays in
/// the failure tone.
Future<RunawayStopOutcome> stopRunawayHelper(
  BuildContext context, {
  required int pid,
  required String helper,
  Future<TermuxProcessStopResult> Function(int pid)? stop,
}) async {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final confirmed = await showKitConfirm(
    context,
    title: l10n.termuxProcsStopOneTitle(helper),
    body: l10n.safetyStopOrphanBody,
    confirmLabel: l10n.termuxProcsStopSemantics(helper),
    icon: AppIcons.stop,
    kind: KitConfirmKind.stop,
    // No undo, and it says so.
    consequenceItems: [KitConsequence(l10n.termuxProcsNoRestart)],
    sheetKey: const ValueKey('work-runaway-stop-confirm'),
    confirmKey: const ValueKey('work-runaway-stop-confirm-stop'),
  );
  if (!confirmed || !context.mounted) return RunawayStopOutcome.kept;
  final view = View.of(context);
  final direction = Directionality.maybeOf(context) ?? TextDirection.ltr;
  RunawayStopOutcome outcome;
  try {
    final result = await (stop ?? TermuxProcesses.stopPid)(pid);
    outcome =
        result.remaining.isEmpty &&
            (result.endedCount > 0 || result.refused.isEmpty)
        ? RunawayStopOutcome.stopped
        : RunawayStopOutcome.failed;
  } on TermuxBridgeException {
    outcome = RunawayStopOutcome.failed;
  } on FormatException {
    outcome = RunawayStopOutcome.failed;
  }
  await SemanticsService.sendAnnouncement(
    view,
    outcome == RunawayStopOutcome.stopped
        ? l10n.workRunawayStopped(helper)
        : l10n.workRunawayStopFailed(helper),
    direction,
  );
  return outcome;
}

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
