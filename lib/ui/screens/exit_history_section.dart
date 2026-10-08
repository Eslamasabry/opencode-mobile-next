import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../domain/app_diagnostics_gateway.dart';
import '../../domain/relative_age.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// FD1 "Recent app exits" on Report a problem (BC contract,
/// docs/design/BC-diagnostics-contract.md): Android's record of each time
/// the app's main process ended, newest first.
///
/// Problem exits lead and are the only rows shown by default: crashes and
/// "stopped responding", low memory, Android ending the app, and exits with
/// no reason. At most [visibleProblems] show until "Show all N". Routine
/// exits (updates, the person closing the app, a normal exit) fold under
/// one quiet row that opens to list them; with no problem exits the section
/// is one line, "No unexpected closes recently.", and no empty card. Each
/// problem row is a plain category and a time ("Today 08:37", "Yesterday
/// 21:42", else the date); the fixed summary, reason code and importance
/// sit under its Details.
///
/// Unsupported (Android 10 and earlier) and a failed read (with Try again)
/// keep their own states. The host owns the read so it can refresh on open
/// and on return to the app; [history] null means the read is still
/// running, and the section draws nothing until it lands.
class ExitHistorySection extends StatefulWidget {
  const ExitHistorySection({
    super.key,
    required this.history,
    required this.onRetry,
    this.clock,
  });

  final AppExitHistory? history;
  final VoidCallback onRetry;

  /// Tests: the moment "Today" and "Yesterday" are measured from.
  final DateTime Function()? clock;

  /// Problem rows shown before "Show all N".
  static const visibleProblems = 5;

  @override
  State<ExitHistorySection> createState() => _ExitHistorySectionState();
}

class _ExitHistorySectionState extends State<ExitHistorySection> {
  bool _showAll = false;

  @override
  Widget build(BuildContext context) {
    final history = widget.history;
    if (history == null) return const SizedBox.shrink();
    final copy = _copy(context);
    final tokens = KitTokens.of(context);
    final now = (widget.clock ?? DateTime.now)();
    final List<Widget> body;
    if (!history.supported) {
      body = [
        KitRowGroup(
          label: copy.diagnosticsExitHistoryTitle,
          children: [
            KitRow(
              key: const ValueKey('exit-history-unsupported'),
              leading: KitRow.icon(context, AppIconography.info),
              title: copy.diagnosticsExitHistoryUnsupported,
              supporting: TextSpan(
                text: copy.diagnosticsExitHistoryUnsupportedBody,
              ),
              supportingMaxLines: 2,
            ),
          ],
        ),
      ];
    } else if (history.error != null) {
      body = [
        KitSectionLabel(copy.diagnosticsExitHistoryTitle),
        Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          child: KitNotice(
            key: const ValueKey('exit-history-failed'),
            tone: AppStatusTone.failure,
            icon: AppIconography.error,
            message: copy.diagnosticsExitHistoryFailed,
            actions: [
              KitAction(
                key: const ValueKey('exit-history-retry'),
                label: copy.commonRetry,
                onPressed: widget.onRetry,
              ),
            ],
          ),
        ),
      ];
    } else {
      final problems = [
        for (final entry in history.entries)
          if (isProblemExit(entry)) entry,
      ];
      final routine = [
        for (final entry in history.entries)
          if (!isProblemExit(entry)) entry,
      ];
      final shown = _showAll
          ? problems
          : problems.take(ExitHistorySection.visibleProblems).toList();
      final hidden = problems.length - shown.length;
      body = [
        if (problems.isEmpty) ...[
          KitSectionLabel(copy.diagnosticsExitHistoryTitle),
          // One line, no empty card.
          KitGroupNote(
            key: const ValueKey('exit-history-no-problems'),
            message: copy.diagnosticsExitNoProblems,
          ),
          if (routine.isNotEmpty) SizedBox(height: tokens.space3),
        ],
        if (problems.isNotEmpty || routine.isNotEmpty)
          KitRowGroup(
            label: problems.isEmpty ? null : copy.diagnosticsExitHistoryTitle,
            children: [
              for (final (index, entry) in shown.indexed)
                _problemRow(context, copy, tokens, index, entry, now),
              if (hidden > 0)
                KitRow(
                  key: const ValueKey('exit-history-show-all'),
                  leading: KitRow.icon(context, AppIconography.info),
                  title: copy.diagnosticsExitShowAll(problems.length),
                  onTap: () => setState(() => _showAll = true),
                ),
              if (routine.isNotEmpty)
                KitExpandRow(
                  key: const ValueKey('exit-history-routine'),
                  leading: KitRow.icon(context, AppIconography.info),
                  title: copy.diagnosticsExitRoutine(routine.length),
                  titleMaxLines: 2,
                  children: [
                    for (final (index, entry) in routine.indexed)
                      KitRow(
                        key: ValueKey('exit-history-routine-$index'),
                        title: exitCategoryTitle(copy, entry),
                        supporting: TextSpan(
                          text: KitBidi.ltr(
                            exitTimeLabel(entry.at, copy: copy, now: now),
                          ),
                        ),
                      ),
                  ],
                ),
            ],
          ),
      ];
    }
    return Column(
      key: const ValueKey('exit-history'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ...body,
        KitGroupNote(message: copy.diagnosticsExitHistoryNote),
      ],
    );
  }

  Widget _problemRow(
    BuildContext context,
    AppLocalizations copy,
    KitTokens tokens,
    int index,
    AppExitEntry entry,
    DateTime now,
  ) => KitExpandRow(
    key: ValueKey('exit-history-$index'),
    leading: KitRow.icon(
      context,
      entry.category == AppExitCategory.crash
          ? AppIconography.error
          : AppIconography.warning,
    ),
    title: exitCategoryTitle(copy, entry),
    titleMaxLines: 2,
    supporting: TextSpan(
      text: KitBidi.ltr(exitTimeLabel(entry.at, copy: copy, now: now)),
    ),
    children: [
      Padding(
        padding: EdgeInsetsDirectional.only(
          start: tokens.gutter,
          end: tokens.gutter,
          bottom: tokens.space3,
        ),
        // Android's numbers and the fixed summary: Details only.
        child: KitDetailsFold(
          foldKey: ValueKey('exit-history-details-$index'),
          initiallyExpanded: true,
          values: [
            KitTechnicalValue(copy.diagnosticsExitSummary, entry.description),
            KitTechnicalValue(
              copy.diagnosticsExitReasonCode,
              '${entry.reason}',
            ),
            KitTechnicalValue(
              copy.diagnosticsExitImportance,
              '${entry.importance}',
            ),
          ],
        ),
      ),
    ],
  );
}

/// Android's `REASON_UNKNOWN`: the gateway files it under "normal", but an
/// exit Android cannot explain is worth a look.
const _reasonUnknown = 0;

/// Crashes and "stopped responding", low memory, Android ending the app,
/// and exits with no reason. Updates, the person closing the app and a
/// normal exit are routine.
bool isProblemExit(AppExitEntry entry) => switch (entry.category) {
  AppExitCategory.crash ||
  AppExitCategory.lowMemory ||
  AppExitCategory.killed => true,
  AppExitCategory.normal => entry.reason == _reasonUnknown,
  AppExitCategory.update || AppExitCategory.forceStop => false,
};

/// The plain words for an exit (stable category keys from the gateway).
String exitCategoryTitle(AppLocalizations copy, AppExitEntry entry) =>
    switch (entry.category) {
      AppExitCategory.normal when entry.reason == _reasonUnknown =>
        copy.diagnosticsExitUnknown,
      AppExitCategory.normal => copy.diagnosticsExitNormal,
      AppExitCategory.update => copy.diagnosticsExitUpdate,
      AppExitCategory.forceStop => copy.diagnosticsExitForceStop,
      AppExitCategory.lowMemory => copy.diagnosticsExitLowMemory,
      AppExitCategory.crash => copy.diagnosticsExitCrash,
      AppExitCategory.killed => copy.diagnosticsExitKilled,
    };

/// "Today 08:37", "Yesterday 21:42", else the app's short date and the
/// time ("Oct 6 08:05"), by the local calendar day.
String exitTimeLabel(
  DateTime at, {
  required AppLocalizations copy,
  required DateTime now,
}) {
  final local = at.toLocal();
  final time = _clock(local, copy.localeName);
  final today = DateTime(now.year, now.month, now.day);
  final day = DateTime(local.year, local.month, local.day);
  // Rounded, so a 23- or 25-hour day (clock change) still counts as one.
  final daysAgo = (today.difference(day).inHours + 12) ~/ 24;
  if (daysAgo == 0) return copy.diagnosticsExitToday(time);
  if (daysAgo == 1) return copy.diagnosticsExitYesterday(time);
  return '${shortDateLabel(local, localeName: copy.localeName, now: now)} '
      '$time';
}

String _clock(DateTime local, String localeName) {
  try {
    return DateFormat.Hm(localeName).format(local);
  } on Exception {
    return DateFormat.Hm('en_US').format(local);
  }
}

AppLocalizations _copy(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));
