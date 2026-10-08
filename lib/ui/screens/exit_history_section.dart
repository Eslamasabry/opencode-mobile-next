import 'package:flutter/material.dart';

import '../../domain/app_diagnostics_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// FD1 "Recent app exits" on Report a problem (BC contract,
/// docs/design/BC-diagnostics-contract.md): Android's record of each time
/// the app's main process ended, newest first. Each row is a plain category
/// and its time; the numeric reason, importance and the fixed safe summary
/// sit under the row's Details. Unsupported (Android 10 and earlier), empty
/// and a failed read are three different states, each in plain words, the
/// failure with Try again.
///
/// The host owns the read ([AppDiagnosticsGateway.exitHistory]) so it can
/// refresh on open and on return to the app; [history] null means the read
/// is still running, and the section draws nothing until it lands.
class ExitHistorySection extends StatelessWidget {
  const ExitHistorySection({
    super.key,
    required this.history,
    required this.onRetry,
  });

  final AppExitHistory? history;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final history = this.history;
    if (history == null) return const SizedBox.shrink();
    final copy = _copy(context);
    final tokens = KitTokens.of(context);
    final Widget body;
    if (!history.supported) {
      body = KitRowGroup(
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
      );
    } else if (history.error != null) {
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
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
                  onPressed: onRetry,
                ),
              ],
            ),
          ),
        ],
      );
    } else {
      body = KitRowGroup(
        label: copy.diagnosticsExitHistoryTitle,
        children: [
          if (history.entries.isEmpty)
            KitRow(
              key: const ValueKey('exit-history-empty'),
              leading: KitRow.icon(context, AppIconography.info),
              title: copy.diagnosticsExitHistoryEmpty,
            ),
          for (final (index, entry) in history.entries.indexed)
            KitExpandRow(
              key: ValueKey('exit-history-$index'),
              leading: KitRow.icon(context, _icon(entry.category)),
              title: exitCategoryTitle(copy, entry.category),
              titleMaxLines: 2,
              supporting: TextSpan(text: KitBidi.ltr(_time(entry.at))),
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
                      KitTechnicalValue(
                        copy.diagnosticsExitSummary,
                        entry.description,
                      ),
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
            ),
        ],
      );
    }
    return Column(
      key: const ValueKey('exit-history'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        body,
        KitGroupNote(message: copy.diagnosticsExitHistoryNote),
      ],
    );
  }
}

/// The plain words for an exit category (stable keys from the gateway).
String exitCategoryTitle(AppLocalizations copy, AppExitCategory category) =>
    switch (category) {
      AppExitCategory.normal => copy.diagnosticsExitNormal,
      AppExitCategory.update => copy.diagnosticsExitUpdate,
      AppExitCategory.forceStop => copy.diagnosticsExitForceStop,
      AppExitCategory.lowMemory => copy.diagnosticsExitLowMemory,
      AppExitCategory.crash => copy.diagnosticsExitCrash,
      AppExitCategory.killed => copy.diagnosticsExitKilled,
    };

IconData _icon(AppExitCategory category) => switch (category) {
  AppExitCategory.crash => AppIconography.error,
  AppExitCategory.killed ||
  AppExitCategory.lowMemory ||
  AppExitCategory.forceStop => AppIconography.warning,
  AppExitCategory.normal || AppExitCategory.update => AppIconography.info,
};

String _time(DateTime value) {
  final local = value.toLocal();
  String two(int part) => part.toString().padLeft(2, '0');
  return '${local.year}-${two(local.month)}-${two(local.day)} '
      '${two(local.hour)}:${two(local.minute)}';
}

AppLocalizations _copy(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));
