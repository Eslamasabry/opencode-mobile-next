import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../builtin/app_exit_recovery.dart';
import '../../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../../l10n/app_localizations.dart';
import '../../platform/app_exit.dart';
import '../../state/profiles.dart' show ServerProfile;
import '../app_theme.dart';
import '../kit/kit.dart';
import '../screens/keep_running_screen.dart';

/// "at 12:06 AM" today, "on Sep 25 at 8:19 PM" before today, in the
/// person's 12/24-hour clock ([KitTime], F15).
String appExitTimeText(
  BuildContext context,
  AppLocalizations l10n,
  DateTime at, {
  DateTime? now,
}) {
  final local = at.toLocal();
  final time = KitTime.clock(context, local);
  final today = (now ?? DateTime.now()).toLocal();
  final sameDay =
      local.year == today.year &&
      local.month == today.month &&
      local.day == today.day;
  return sameDay
      ? l10n.appExitAtTime(time)
      : l10n.appExitOnDay(
          MaterialLocalizations.of(context).formatShortMonthDay(local),
          time,
        );
}

/// The notice's one sentence: what ended the app, what stopped with it and
/// whether it is coming back. It says "starting again" only when the
/// automation policy lets this launch restart it ([AppExitNotice.
/// recoveryAllowed]); otherwise the person starts it. Once the phone's
/// OpenCode is connected again ([serverBack]) it says so, so the notice
/// never says "starting again" beside a pill that says Connected (F10).
String appExitMessage(
  AppLocalizations l10n,
  AppExitNotice notice,
  String at, {
  bool serverBack = false,
}) {
  final what = switch (notice.kind) {
    AppExitKind.lowMemory => l10n.appExitLowMemory(at),
    AppExitKind.crash => l10n.appExitCrashed(at),
    AppExitKind.killed => l10n.appExitKilled(at),
    _ => l10n.appExitForceStopped(at),
  };
  if (serverBack) {
    return notice.teamStopped
        ? l10n.appExitServerBackTeam(what)
        : l10n.appExitServerBack(what);
  }
  if (!notice.recoveryAllowed) {
    return notice.teamStopped
        ? l10n.appExitServerAndTeamStoppedManual(what)
        : l10n.appExitServerStoppedManual(what);
  }
  return notice.teamStopped
      ? l10n.appExitServerAndTeamStopped(what)
      : l10n.appExitServerStopped(what);
}

/// Shown once in the shell after Android ended the app while the phone's
/// OpenCode ran in it; nothing after a normal exit or an update
/// ([AppExitRecovery]). It folds away when dismissed.
class AppExitNoticeLine extends ConsumerWidget {
  const AppExitNoticeLine({super.key, this.now});

  /// For tests: the moment "today" is measured from.
  final DateTime? now;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final recovery = ref.watch(appExitRecoveryProvider);
    return ListenableBuilder(
      listenable: recovery,
      builder: (context, _) {
        final notice = recovery.notice;
        if (notice == null) return const KitReveal(child: null);
        final l10n = lookupAppLocalizations(Localizations.localeOf(context));
        final at = appExitTimeText(context, l10n, notice.at, now: now);
        return KitReveal(
          child: Padding(
            key: const ValueKey('app-exit-notice'),
            padding: EdgeInsetsDirectional.fromSTEB(
              KitTokens.of(context).gutter,
              KitTokens.of(context).space2,
              KitTokens.of(context).space1,
              KitTokens.of(context).space1,
            ),
            child: KitNotice(
              // Neutral, as its KitStatus twin: amber is needs-you only
              // (LOOK-24).
              tone: AppStatusTone.neutral,
              icon: AppIconography.restart,
              message: appExitMessage(l10n, notice, at),
              actions: [
                if (notice.offersKeepAlive)
                  KitAction(
                    key: const ValueKey('app-exit-keep-running'),
                    label: l10n.appExitKeepRunning,
                    onPressed: () => openKeepRunningScreen(context),
                  ),
              ],
              onDismiss: recovery.dismiss,
            ),
          ),
        );
      },
    );
  }
}

/// App-wide condition: the same notice survives route and tab changes.
///
/// [serverBack] is true once the app is connected to the phone's OpenCode
/// again. The words then say it is running again; a notice with nothing
/// left to offer (a crash has no Keep it running) resolves by itself then,
/// so it does not sit on every tab after the server is back (F10).
///
/// [viewing] is the server the app shows. When that is another server (the
/// Termux one, a computer), the notice is about a server off this page: it
/// says whether the phone's OpenCode is running again from
/// [AppExitRecovery.phoneServerRunning], never "starting again" above
/// another server's "stopped", and its action switches to the phone's
/// OpenCode ([onSwitchToPhone]); Keep it running moves under More (owner
/// report, 2026-10-12).
KitStatus? appExitKitStatus(
  BuildContext context,
  AppExitRecovery recovery, {
  DateTime? now,
  BuildContext? Function()? actionContext,
  bool serverBack = false,
  ServerProfile? viewing,
  void Function(BuildContext context, String profileId)? onSwitchToPhone,
}) {
  final notice = recovery.notice;
  if (notice == null) return null;
  if (serverBack && !notice.offersKeepAlive) return null;
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final phoneId = notice.phoneProfileId;
  final elsewhere =
      phoneId != null &&
      viewing != null &&
      viewing.id != phoneId &&
      !looksLikeInAppServer(viewing);
  void inCurrent(void Function(BuildContext current) run) {
    final current = actionContext == null ? context : actionContext();
    if (current != null && current.mounted) run(current);
  }

  final keepRunning = notice.offersKeepAlive
      ? KitAction(
          key: const ValueKey('app-exit-keep-running'),
          label: l10n.appExitKeepRunning,
          onPressed: () => inCurrent(openKeepRunningScreen),
        )
      : null;
  final switchToPhone = elsewhere && onSwitchToPhone != null
      ? KitAction(
          key: const ValueKey('app-exit-switch-to-phone'),
          label: l10n.appExitSwitchToPhone,
          onPressed: () =>
              inCurrent((current) => onSwitchToPhone(current, phoneId)),
        )
      : null;
  final back = serverBack || (elsewhere && recovery.phoneServerRunning);
  final full = appExitMessage(
    l10n,
    notice,
    appExitTimeText(context, l10n, notice.at, now: now),
    serverBack: back,
  );
  // Information only: the phone's OpenCode is back or coming back by
  // itself, nothing else stopped, and no switch is on offer. That is one
  // quiet line; the time stays in what a screen reader says.
  final quiet =
      switchToPhone == null &&
      !notice.teamStopped &&
      (back || notice.recoveryAllowed);
  if (quiet) {
    return KitStatus(
      kind: KitStatusKind.appStopped,
      id: 'app-stopped',
      key: const ValueKey('app-exit-notice'),
      icon: AppIconography.restart,
      message: back ? l10n.appExitQuietRestarted : l10n.appExitQuietRestarting,
      semanticsLabel: full,
      action: keepRunning,
      onDismiss: recovery.dismiss,
      quiet: true,
    );
  }
  return KitStatus(
    kind: KitStatusKind.appStopped,
    id: 'app-stopped',
    key: const ValueKey('app-exit-notice'),
    icon: AppIconography.restart,
    tone: AppStatusTone.neutral,
    message: full,
    action: switchToPhone ?? keepRunning,
    more: [if (switchToPhone != null) ?keepRunning],
    onDismiss: recovery.dismiss,
  );
}
