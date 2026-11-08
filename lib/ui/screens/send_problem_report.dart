import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../telemetry/problem_report_collector.dart';
import '../../telemetry/telemetry.dart';
import '../../telemetry/telemetry_provider.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// Settings › Help › Send a problem report
/// (docs/telemetry/health-and-diagnostics.md section E).
///
/// Asked every time, whatever the two switches say: the sheet builds the text
/// (the last log lines, the custom keys, the recent process ends, the device
/// context), redacts it, and shows all of it. Only the Send button sends it,
/// as one report to the developer; the sheet then shows the report code to
/// quote in a GitHub issue. Cancel, back or a swipe send nothing.
Future<void> openSendProblemReport(
  BuildContext context, {
  Telemetry? telemetry,
  ProblemReportCollector? collector,
}) async {
  final copy = _copy(context);
  final reporter = telemetry ?? telemetryOf(context);
  final builder =
      collector ??
      ProblemReportCollector(telemetry: reporter, health: AppTelemetry.health);
  final state = ValueNotifier<_ReportState>(const _ReportState.collecting());
  final primary = ValueNotifier<KitAction?>(null);
  var closed = false;
  final navigator = Navigator.of(context);

  Future<void> send(String text) async {
    state.value = _ReportState.sending(text);
    final code = await reporter.sendProblemReport(text);
    if (closed) return;
    state.value = code == null
        ? _ReportState.failed(text)
        : _ReportState.sent(text, code);
  }

  void syncPrimary() {
    final value = state.value;
    primary.value = switch (value.stage) {
      _Stage.collecting => null,
      _Stage.preview || _Stage.failed || _Stage.sending => KitAction(
        key: const ValueKey('send-problem-report-send'),
        label: copy.sendReportSend,
        icon: AppIconography.upload,
        working: value.stage == _Stage.sending,
        onPressed: value.stage == _Stage.sending
            ? null
            : () => unawaited(send(value.text)),
      ),
      _Stage.sent => KitAction(
        key: const ValueKey('send-problem-report-done'),
        label: copy.sendReportDone,
        onPressed: () {
          if (navigator.mounted) navigator.pop();
        },
      ),
    };
  }

  syncPrimary();
  state.addListener(syncPrimary);
  unawaited(
    builder.build().then((text) {
      if (!closed) state.value = _ReportState.preview(text);
    }),
  );

  try {
    await showKitSheet<void>(
      context,
      title: copy.sendReportTitle,
      subtitle: copy.sendReportSubtitle,
      icon: AppIconography.bug,
      height: KitSheetHeight.full,
      sheetKey: const ValueKey('send-problem-report-sheet'),
      primaryListenable: primary,
      body: (sheetContext) => ValueListenableBuilder<_ReportState>(
        valueListenable: state,
        builder: (context, value, _) {
          final tokens = KitTokens.of(context);
          if (value.stage == _Stage.collecting) {
            return KitText(
              copy.sendReportCollecting,
              key: const ValueKey('send-problem-report-collecting'),
              role: KitTextRole.secondary,
            );
          }
          if (value.stage == _Stage.sent) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                KitText(
                  copy.sendReportSentTitle,
                  key: const ValueKey('send-problem-report-sent'),
                  role: KitTextRole.title,
                ),
                SizedBox(height: tokens.space2),
                KitText(copy.sendReportSentBody, role: KitTextRole.secondary),
                SizedBox(height: tokens.space3),
                KitRowGroup(
                  children: [
                    KitRow(
                      key: const ValueKey('send-problem-report-code'),
                      leading: KitRow.icon(context, AppIconography.bug),
                      title: copy.reportCodeLabel,
                      supporting: TextSpan(text: value.code),
                      trailing: KitIconButton.copy(
                        key: const ValueKey('send-problem-report-copy-code'),
                        tooltip: copy.reportCodeCopy,
                        text: () => value.code!,
                      ),
                    ),
                  ],
                ),
              ],
            );
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (value.stage == _Stage.failed) ...[
                KitNotice(
                  key: const ValueKey('send-problem-report-failed'),
                  tone: AppStatusTone.failure,
                  icon: AppIconography.warning,
                  message: copy.sendReportFailed,
                ),
                SizedBox(height: tokens.space3),
              ],
              // Exactly the text that is sent, selectable.
              KitText.mono(
                value.text,
                key: const ValueKey('send-problem-report-text'),
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

enum _Stage { collecting, preview, sending, sent, failed }

@immutable
class _ReportState {
  const _ReportState._(this.stage, this.text, this.code);
  const _ReportState.collecting() : this._(_Stage.collecting, '', null);
  const _ReportState.preview(String text) : this._(_Stage.preview, text, null);
  const _ReportState.sending(String text) : this._(_Stage.sending, text, null);
  const _ReportState.failed(String text) : this._(_Stage.failed, text, null);
  const _ReportState.sent(String text, String code)
    : this._(_Stage.sent, text, code);

  final _Stage stage;
  final String text;
  final String? code;
}

AppLocalizations _copy(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));
