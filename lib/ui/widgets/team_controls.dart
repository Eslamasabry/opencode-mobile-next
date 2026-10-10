/// Shared pieces of the AI Team controls (TEAM-204; 02-ux §5.2, §6):
/// the receipt every control shows after a tap, the field the message and
/// objective sheets use, and the two-step confirmation the controls that
/// end work go through. Built from kit parts only (shared-team-1): the
/// receipt is a [KitReceipt] ([teamControlReceipt]), the field a
/// [KitField], the confirmation a [showKitConfirm].
library;

import 'package:flutter/material.dart';

import '../../domain/orchestration_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/mutation_store.dart';
import '../app_theme.dart';
import '../kit/kit_field.dart';
import '../kit/kit_receipt.dart';
import '../kit/kit_sheet.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// The receipt word for a record's status (02-ux §6, short form).
String teamControlReceiptWord(AppLocalizations l10n, MutationStatus status) =>
    switch (status) {
      MutationStatus.sent => l10n.teamUiControlReceiptSent,
      MutationStatus.confirmed => l10n.teamUiControlReceiptConfirmed,
      MutationStatus.unconfirmed => l10n.teamUiControlReceiptUnconfirmed,
      MutationStatus.rejected => l10n.teamUiControlReceiptRefused,
    };

/// The control's name for a receipt line: the button label the person
/// tapped, so the chip reads "Nudge · Sent".
String teamControlWord(AppLocalizations l10n, MutationRequest request) =>
    switch (request.kind) {
      MutationKind.message => l10n.teamUiControlMessage,
      MutationKind.controlAgent => switch (request.action) {
        AgentControlAction.nudge || null => l10n.teamUiControlNudge,
        AgentControlAction.pause => l10n.teamUiControlPause,
        AgentControlAction.resume ||
        AgentControlAction.start => l10n.teamUiControlResume,
        AgentControlAction.stop => l10n.teamUiControlStop,
        AgentControlAction.restart => l10n.teamUiControlRestart,
        AgentControlAction.kill => l10n.teamControlForceStop,
      },
      MutationKind.cancelRun => l10n.teamUiControlCancelRun,
      MutationKind.assign => l10n.teamUiControlReassign,
      MutationKind.respond => l10n.teamUiReceiptAnswered,
      MutationKind.approveMerge => l10n.teamUiMergeApprove,
      MutationKind.merge => l10n.teamUiMergeMerge,
      MutationKind.createWork => l10n.teamUiControlCreateWork,
      MutationKind.controlProject => switch (request.projectAction) {
        ProjectControlAction.resume => l10n.teamProjectControlResumeWord,
        ProjectControlAction.remove => l10n.teamProjectControlRemoveWord,
        ProjectControlAction.suspend ||
        null => l10n.teamProjectControlSuspendWord,
      },
      MutationKind.controlScheduledJob =>
        request.confirmed == true
            ? l10n.teamJobTurnOnWord
            : l10n.teamJobTurnOffWord,
    };

/// The receipt a team control shows after a tap, as the one [KitReceipt]
/// (02-ux §6): "Nudge · Sending…" while the host has not answered, then
/// "Nudge · Confirmed", "Not confirmed yet" with Try again, or "Not
/// accepted: {the host's reason}". The moving state names the act through
/// [KitReceipt.sendingLabel]; the state's own mark stays beside the words,
/// never colour alone.
///
/// [control] defaults to [teamControlWord]; [onRetry] shows Try again only
/// when the record may be retried (a retry is a new record under a new key
/// and only ever follows a tap).
KitReceipt teamControlReceipt(
  BuildContext context,
  MutationRecord record, {
  Key? key,
  String? control,
  Future<void> Function()? onRetry,
  Key? retryKey,
}) {
  final l10n = _copy(context);
  final act = control ?? teamControlWord(l10n, record.request);
  final reason = record.status == MutationStatus.rejected
      ? record.receipt?.message?.trim()
      : null;
  final retry = onRetry;
  return KitReceipt(
    key: key ?? ValueKey('team-receipt-${record.key}'),
    state: switch (record.status) {
      MutationStatus.sent => KitReceiptState.sending,
      MutationStatus.confirmed => KitReceiptState.confirmed,
      MutationStatus.unconfirmed => KitReceiptState.notConfirmed,
      MutationStatus.rejected => KitReceiptState.refused,
    },
    sendingLabel: l10n.teamControlReceiptSending(act),
    label: l10n.teamUiControlReceiptLine(
      act,
      l10n.teamUiControlReceiptConfirmed,
    ),
    reason: reason == null || reason.isEmpty ? null : reason,
    onRetry: retry != null && record.canRetry ? () => retry() : null,
    retryKey: retryKey ?? const ValueKey('team-receipt-retry'),
  );
}

/// The two-step gate every control that ends work goes through: the first
/// tap opened this, the second (the confirming button) returns true;
/// backing out returns false and nothing is sent. [kind] is
/// [KitConfirmKind.stop] (error tone, "Keep going") unless the caller asks
/// for a neutral question (a restart loses nothing, LOOK-5).
Future<bool> confirmTeamControl(
  BuildContext context, {
  required String title,
  required String message,
  required String confirmLabel,
  KitConfirmKind kind = KitConfirmKind.stop,
  List<String> consequences = const [],
  Key? sheetKey,
  Key? confirmKey,
}) => showKitConfirm(
  context,
  title: title,
  body: message,
  confirmLabel: confirmLabel,
  cancelLabel: _copy(context).teamUiControlKeep,
  kind: kind,
  icon: kind == KitConfirmKind.neutral ? null : AppIconography.stop,
  consequences: consequences,
  sheetKey: sheetKey,
  confirmKey: confirmKey,
);
