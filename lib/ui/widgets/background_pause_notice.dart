import 'dart:async';

import 'package:flutter/semantics.dart' show SemanticsService;
import 'package:flutter/widgets.dart';

import '../../domain/background_pause.dart';
import '../../l10n/app_localizations.dart';
import '../../state/background_pause_notice.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../screens/keep_running_screen.dart';
import 'app_exit_notice.dart' show appExitTimeText;

/// The notice's one sentence: why the background connection is off. The
/// time is said only where it is the moment it stopped (a time limit, a
/// closed app); for a restriction or an unknown stop it is only when the app
/// noticed, so no time is claimed (contract FD3).
String backgroundPauseMessage(
  BuildContext context,
  AppLocalizations l10n,
  BackgroundPauseNoticeState notice, {
  DateTime? now,
}) {
  String at() => appExitTimeText(context, l10n, notice.at, now: now);
  return switch (notice.reason) {
    BackgroundPauseReason.timeLimit => l10n.backgroundPauseTimeLimit(at()),
    BackgroundPauseReason.batteryRestricted => l10n.backgroundPauseRestricted,
    BackgroundPauseReason.userStopped => l10n.backgroundPauseUserStopped(at()),
    BackgroundPauseReason.interrupted ||
    BackgroundPauseReason.none => l10n.backgroundPauseInterrupted,
  };
}

/// App-wide condition (FD3): Android stopped the background connection.
/// One line in the status slot every screen already shows, with Resume
/// named for what it starts. While Android confirms the start the action
/// works in place; when it is not confirmed the pause stays and the line
/// offers Keep running in the background, with Resume again under More.
///
/// It shares [KitStatusKind.appStopped] with the app-exit notice ("Android
/// stopped the app; background checks paused"), so the slot shows one of
/// the two, never both; the caller orders them.
KitStatus? backgroundPauseKitStatus(
  BuildContext context,
  BackgroundPauseNotice source, {
  DateTime? now,
  BuildContext? Function()? actionContext,
}) {
  final notice = source.notice;
  if (notice == null) return null;
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  BuildContext? current() {
    final found = actionContext == null ? context : actionContext();
    return found != null && found.mounted ? found : null;
  }

  void openKeepRunning() {
    final target = current();
    if (target != null) unawaited(openKeepRunningScreen(target));
  }

  Future<void> resume() async {
    final confirmed = await source.resume();
    if (!confirmed) return;
    final target = current();
    if (target == null || !target.mounted) return;
    // The line folds away on success, so the result is said once.
    await SemanticsService.sendAnnouncement(
      View.of(target),
      l10n.backgroundPauseResumed,
      Directionality.maybeOf(target) ?? TextDirection.ltr,
    );
  }

  final resumeAction = KitAction(
    key: const ValueKey('background-pause-resume'),
    label: l10n.backgroundPauseResume,
    working: notice.phase == BackgroundPauseNoticePhase.resuming,
    onPressed: () => unawaited(resume()),
  );
  final keepRunning = KitAction(
    key: const ValueKey('background-pause-keep-running'),
    label: l10n.backgroundPauseOpenKeepRunning,
    onPressed: openKeepRunning,
  );
  final battery =
      notice.reason == BackgroundPauseReason.timeLimit ||
      notice.reason == BackgroundPauseReason.batteryRestricted;
  final common = (
    icon: battery ? AppIconography.batteryWarning : AppIconography.pause,
    key: const ValueKey('background-pause-notice'),
    id: 'background-paused',
  );
  return switch (notice.phase) {
    BackgroundPauseNoticePhase.resuming => KitStatus(
      kind: KitStatusKind.appStopped,
      id: '${common.id}:resuming',
      key: common.key,
      icon: common.icon,
      tone: AppStatusTone.progress,
      message: l10n.backgroundPauseResuming,
      since: notice.resumingSince,
      action: resumeAction,
    ),
    BackgroundPauseNoticePhase.failed => KitStatus(
      kind: KitStatusKind.appStopped,
      id: '${common.id}:failed',
      key: common.key,
      icon: common.icon,
      tone: AppStatusTone.failure,
      message: l10n.backgroundPauseResumeFailed,
      supporting: l10n.backgroundPauseResumeFailedNext,
      action: keepRunning,
      more: [if (notice.canResume) resumeAction],
      onDismiss: source.dismiss,
    ),
    BackgroundPauseNoticePhase.paused => KitStatus(
      kind: KitStatusKind.appStopped,
      id: common.id,
      key: common.key,
      icon: common.icon,
      // Neutral, as the app-exit line: amber is needs-you only (LOOK-24).
      tone: AppStatusTone.neutral,
      message: backgroundPauseMessage(context, l10n, notice, now: now),
      supporting: l10n.backgroundPauseConsequence,
      action: notice.canResume ? resumeAction : keepRunning,
      more: [if (notice.canResume) keepRunning],
      onDismiss: source.dismiss,
    ),
  };
}
