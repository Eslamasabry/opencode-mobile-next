import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/app_diagnostics_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// FD2: share the saved crash reports (BC contract,
/// docs/design/BC-diagnostics-contract.md). The gateway builds the report
/// (at most 20 records, 16 KiB); the sheet shows exactly that text, and only
/// an explicit "Share report" tap hands it to the system share sheet.
///
/// A share sheet that opened closes this one and nothing more is said: the
/// chooser opening is not delivery, so the app never says "sent". A stale
/// preview (consent changed, new evidence, a rebuilt store) is rebuilt in
/// place with a notice and needs another tap; a share sheet that would not
/// open says so. A report that cannot be built at all closes nothing and
/// returns its plain words through [onProblem].
Future<void> openCrashReportShare(
  BuildContext context,
  AppDiagnosticsGateway gateway, {
  required ValueChanged<String> onProblem,
}) async {
  final copy = _copy(context);
  final first = await gateway.previewCrashReport();
  if (!context.mounted) return;
  final preview = first.preview;
  if (preview == null) {
    final words = crashReportProblem(copy, first.error);
    if (words != null) onProblem(words);
    return;
  }
  final navigator = Navigator.of(context);
  final state = ValueNotifier(_ShareState(preview));
  final primary = ValueNotifier<KitAction?>(null);
  var closed = false;

  Future<void> share() async {
    final current = state.value;
    if (current.working) return;
    state.value = current.copyWith(working: true);
    final result = await gateway.shareCrashReport(current.preview);
    if (closed) return;
    if (result.opened) {
      // The chooser is up; what happens next is the person's choice.
      if (navigator.mounted) navigator.pop();
      return;
    }
    switch (result.error) {
      case DiagnosticsError.stalePreview:
        final rebuilt = await gateway.previewCrashReport();
        if (closed) return;
        final next = rebuilt.preview;
        if (next == null) {
          if (navigator.mounted) navigator.pop();
          final words = crashReportProblem(copy, rebuilt.error);
          if (words != null) onProblem(words);
          return;
        }
        state.value = _ShareState(next, notice: copy.diagnosticsReportStale);
      case DiagnosticsError.busy:
        state.value = state.value.copyWith(working: false);
      default:
        state.value = state.value.copyWith(
          working: false,
          notice: copy.diagnosticsReportShareFailed,
        );
    }
  }

  void syncPrimary() => primary.value = KitAction(
    key: const ValueKey('crash-report-share'),
    label: copy.diagnosticsReportShare,
    icon: AppIconography.upload,
    working: state.value.working,
    onPressed: state.value.working ? null : () => unawaited(share()),
  );
  syncPrimary();
  state.addListener(syncPrimary);

  try {
    await showKitSheet<void>(
      context,
      title: copy.diagnosticsReportTitle,
      subtitle: copy.diagnosticsReportPrivacy,
      icon: AppIconography.bug,
      height: KitSheetHeight.full,
      sheetKey: const ValueKey('crash-report-share-sheet'),
      primaryListenable: primary,
      body: (sheetContext) => ValueListenableBuilder<_ShareState>(
        valueListenable: state,
        builder: (context, value, _) {
          final tokens = KitTokens.of(context);
          final notice = value.notice;
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (notice != null) ...[
                KitNotice(
                  key: const ValueKey('crash-report-share-notice'),
                  tone: AppStatusTone.failure,
                  icon: AppIconography.warning,
                  message: notice,
                ),
                SizedBox(height: tokens.space3),
              ],
              KitText(
                copy.diagnosticsReportSize(
                  value.preview.recordCount,
                  KitBidi.ltr(_size(value.preview.byteCount)),
                ),
                key: const ValueKey('crash-report-share-size'),
                role: KitTextRole.secondary,
              ),
              SizedBox(height: tokens.space2),
              // Exactly the text that is shared, selectable.
              KitText.mono(
                value.preview.text,
                key: const ValueKey('crash-report-share-text'),
                selectable: true,
              ),
            ],
          );
        },
      ),
    );
  } finally {
    closed = true;
    state.removeListener(syncPrimary);
    state.dispose();
    primary.dispose();
  }
}

/// Plain words for a report that could not be built; null when there is
/// nothing to say (another tap is already running).
String? crashReportProblem(AppLocalizations copy, DiagnosticsError? error) =>
    switch (error) {
      DiagnosticsError.captureDisabled => copy.diagnosticsReportCaptureOff,
      DiagnosticsError.noCapture => copy.diagnosticsReportEmpty,
      DiagnosticsError.shareUnavailable => copy.diagnosticsReportShareFailed,
      DiagnosticsError.busy || null => null,
      _ => copy.diagnosticsUnavailable,
    };

@immutable
class _ShareState {
  const _ShareState(this.preview, {this.notice, this.working = false});

  final CrashReportPreview preview;
  final String? notice;
  final bool working;

  _ShareState copyWith({bool? working, String? notice}) => _ShareState(
    preview,
    notice: notice ?? this.notice,
    working: working ?? this.working,
  );
}

String _size(int bytes) =>
    bytes < 1024 ? '$bytes B' : '${(bytes / 1024).toStringAsFixed(1)} KB';

AppLocalizations _copy(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));
