import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../feedback/bug_report.dart' show openBugReport;
import '../../feedback/crash_offer.dart';
import '../../feedback/problem_report.dart' show problemReportAppVersion;
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// Opens Report a problem with the crash attached; tests pass their own.
typedef CrashReportOpener =
    Future<void> Function(BuildContext context, KitReport report);

/// The one calm line after a crash (E02-S91): "OpenCode Mobile closed
/// unexpectedly last time. Send a crash report?" with Review and send and
/// Not now. Fixed words only: the exception never shows here, only in
/// Report a problem's Details, which holds exactly what is sent.
///
/// Review and send opens Report a problem with the crash pre-filled; the
/// person files the GitHub issue from there (through `openExternalLink`).
/// Either answer ends the offer for this crash ([CrashReportOffer]).
KitStatus? crashOfferKitStatus(
  BuildContext context,
  CrashReportOffer offer, {
  BuildContext? Function()? actionContext,
  CrashReportOpener? open,
  Future<String> Function()? version,
}) {
  final facts = offer.pending;
  if (facts == null) return null;
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));

  Future<void> review() async {
    offer.dismiss();
    final appVersion = await (version ?? problemReportAppVersion)();
    final current = actionContext == null ? context : actionContext();
    if (current == null || !current.mounted) return;
    final report = crashKitReport(
      facts,
      title: l10n.crashOfferReportTitle,
      version: appVersion,
    );
    await (open ?? _openReport)(current, report);
  }

  return KitStatus(
    kind: KitStatusKind.appStopped,
    id: 'crash-offer',
    key: const ValueKey('crash-offer-notice'),
    icon: AppIconography.bug,
    message: l10n.crashOfferMessage,
    action: KitAction(
      key: const ValueKey('crash-offer-review'),
      label: l10n.crashOfferReview,
      onPressed: () => unawaited(review()),
    ),
    // "Not now" is the line's own dismiss (its tooltip and what a screen
    // reader says), so the offer has exactly two answers and no menu.
    dismissTooltip: l10n.crashOfferNotNow,
    onDismiss: offer.dismiss,
  );
}

Future<void> _openReport(BuildContext context, KitReport report) =>
    openBugReport(context, error: report);
