import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../diagnostics/crash_diagnostics.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// Saved crash reports on Report a problem (BD7 consent UI,
/// docs/design/bd7-contract.md): one switch the person turns on to keep
/// crash and "stopped responding" reports on this phone, its supporting
/// words saying what is kept, where and how much; while on, the saved
/// reports as rows (plain kind and time) that open a preview with the
/// technical source and category under Details only; and "Delete saved
/// crash reports" as its own named action.
///
/// The capture backend owns every rule: capture stays off until the switch
/// is turned on, turning it off erases what was saved, and a failed storage
/// change says so rather than confirming it. This section only reads
/// [CrashDiagnosticsController] and calls its `setEnabled` and `clear`.
///
/// [crash] and [ready] are for tests; the app uses the controller opened
/// at start-up ([CrashDiagnosticsStartup]). While start-up is still opening
/// it (at most 300 ms after the first frame) the section draws nothing;
/// when the store could not open it says so on a disabled switch.
///
/// The backend clears App diagnostics whenever the choice changes or the
/// reports are deleted. [savedErrors] is how many other errors the page
/// lists above; only when there are some (or saved reports) does a line
/// under the switch, and the delete question, say what goes with it.
class CrashReportsSection extends StatefulWidget {
  const CrashReportsSection({
    super.key,
    this.crash,
    this.ready,
    this.savedErrors = 0,
  });

  final CrashDiagnosticsController? crash;
  final Future<CrashDiagnosticsController?>? ready;
  final int savedErrors;

  @override
  State<CrashReportsSection> createState() => _CrashReportsSectionState();
}

class _CrashReportsSectionState extends State<CrashReportsSection> {
  CrashDiagnosticsController? _crash;
  bool _resolved = false;
  bool _actionFailed = false;

  @override
  void initState() {
    super.initState();
    final known = widget.crash ?? CrashDiagnosticsStartup.current;
    if (known != null) {
      _crash = known;
      _resolved = true;
      return;
    }
    unawaited(
      (widget.ready ?? CrashDiagnosticsStartup.ready).then((controller) {
        if (!mounted) return;
        setState(() {
          _crash = controller;
          _resolved = true;
        });
      }),
    );
  }

  void _setEnabled(CrashDiagnosticsController crash, bool value) {
    final ok = crash.setEnabled(value);
    setState(() => _actionFailed = !ok);
  }

  Future<void> _delete(CrashDiagnosticsController crash) async {
    final copy = _copy(context);
    final confirmed = await showKitConfirm(
      context,
      title: copy.crashReportsDeleteTitle,
      body: widget.savedErrors > 0
          ? copy.crashReportsDeleteBodyWithErrors(widget.savedErrors)
          : copy.crashReportsDeleteBody,
      confirmLabel: copy.crashReportsDeleteConfirm,
      icon: AppIconography.delete,
      kind: KitConfirmKind.destructive,
      confirmKey: const ValueKey('crash-reports-delete-confirm'),
    );
    if (!confirmed || !mounted) return;
    final ok = crash.clear();
    setState(() => _actionFailed = !ok);
  }

  Future<void> _preview(CrashRecord record) {
    final copy = _copy(context);
    return showKitSheet<void>(
      context,
      title: _kind(copy, record),
      subtitle: KitBidi.ltr(_time(record.time)),
      icon: _icon(record),
      sheetKey: const ValueKey('crash-report-preview-sheet'),
      body: (sheetContext) {
        final tokens = KitTokens.of(sheetContext);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitText(copy.crashReportPreviewBody, role: KitTextRole.secondary),
            SizedBox(height: tokens.space3),
            // The fixed source and category are technical: Details only.
            KitDetailsFold(
              foldKey: const ValueKey('crash-report-details'),
              values: [
                KitTechnicalValue(
                  copy.crashReportDetailSource,
                  'crash.${record.source}',
                ),
                KitTechnicalValue(
                  copy.crashReportDetailCategory,
                  record.category,
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  String? _effect(AppLocalizations copy, bool enabled, int reports) =>
      _effectFor(
        copy,
        enabled: enabled,
        reports: reports,
        errors: widget.savedErrors,
      );

  @override
  Widget build(BuildContext context) {
    if (!_resolved) return const SizedBox.shrink();
    final copy = _copy(context);
    final crash = _crash;
    if (crash == null) {
      // The browser build has no private crash store to offer.
      if (kIsWeb) return const SizedBox.shrink();
      return KitRowGroup(
        label: copy.crashReportsLabel,
        children: [
          KitSwitchRow(
            switchKey: const ValueKey('crash-reports-switch'),
            title: copy.crashReportsSwitch,
            value: false,
            onChanged: null,
            disabledReason: copy.crashReportsUnavailable,
          ),
        ],
      );
    }
    return ListenableBuilder(
      listenable: crash,
      builder: (context, _) {
        final enabled = crash.enabled;
        final records = enabled ? crash.records : const <CrashRecord>[];
        final failed = _actionFailed || crash.storageFailed;
        final tokens = KitTokens.of(context);
        final effect = _effect(copy, enabled, records.length);
        return Column(
          key: const ValueKey('crash-reports'),
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitRowGroup(
              label: copy.crashReportsLabel,
              children: [
                KitSwitchRow(
                  switchKey: const ValueKey('crash-reports-switch'),
                  title: copy.crashReportsSwitch,
                  supporting: copy.crashReportsSwitchBody,
                  value: enabled,
                  onChanged: (value) => _setEnabled(crash, value),
                  below: effect == null && !failed
                      ? null
                      : Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (effect != null)
                              KitText(
                                effect,
                                key: const ValueKey('crash-reports-effect'),
                                role: KitTextRole.secondary,
                              ),
                            if (failed) ...[
                              if (effect != null)
                                SizedBox(height: tokens.space2),
                              KitNotice(
                                key: const ValueKey('crash-reports-failed'),
                                tone: AppStatusTone.failure,
                                icon: AppIconography.error,
                                message: copy.crashReportsFailed,
                              ),
                            ],
                          ],
                        ),
                ),
              ],
            ),
            // What is kept and how much: in full, never cut to the row's
            // two supporting lines.
            KitGroupNote(message: copy.crashReportsNote),
            if (enabled) ...[
              SizedBox(height: tokens.space3),
              KitRowGroup(
                children: [
                  if (records.isEmpty)
                    KitRow(
                      key: const ValueKey('crash-reports-none'),
                      leading: KitRow.icon(context, AppIconography.info),
                      title: copy.crashReportsNone,
                      supporting: TextSpan(text: copy.crashReportsNoneBody),
                      supportingMaxLines: 2,
                    ),
                  for (final (index, record) in records.indexed)
                    KitRow(
                      key: ValueKey('crash-report-$index'),
                      leading: KitRow.icon(context, _icon(record)),
                      title: _kind(copy, record),
                      supporting: TextSpan(
                        text: KitBidi.ltr(_time(record.time)),
                      ),
                      onTap: () => unawaited(_preview(record)),
                    ),
                  if (records.isNotEmpty)
                    KitRow(
                      key: const ValueKey('crash-reports-delete'),
                      leading: KitRow.icon(context, AppIconography.delete),
                      title: copy.crashReportsDelete(records.length),
                      destructive: true,
                      onTap: () => unawaited(_delete(crash)),
                    ),
                ],
              ),
            ],
          ],
        );
      },
    );
  }
}

/// What flipping the switch takes with it, said only when something would
/// go: null when nothing is saved.
String? _effectFor(
  AppLocalizations copy, {
  required bool enabled,
  required int reports,
  required int errors,
}) {
  if (!enabled) return errors > 0 ? copy.crashReportsOnClears(errors) : null;
  if (reports > 0) {
    return errors > 0
        ? copy.crashReportsOffDeletesAndClears(errors)
        : copy.crashReportsOffDeletes;
  }
  return errors > 0 ? copy.crashReportsOffClears(errors) : null;
}

/// The plain words for a record: what the person noticed, not the category.
String _kind(AppLocalizations copy, CrashRecord record) =>
    switch (record.source) {
      'widget' => copy.crashKindScreen,
      'native' => copy.crashKindClosed,
      'anr' => copy.crashKindNotResponding,
      _ => copy.crashKindError,
    };

IconData _icon(CrashRecord record) => switch (record.source) {
  'anr' => AppIconography.timer,
  _ => AppIconography.error,
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
