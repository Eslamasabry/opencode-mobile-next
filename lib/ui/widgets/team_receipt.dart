/// The receipt a gate's card, its rows and the Gate sheet share (TEAM-203,
/// 02-ux §6): which [MutationRecord] answers a gate, the copy per status
/// and the row's receipt span ([teamGateReceiptSpan]). A row leaves the list only once the
/// host confirmed; nothing here sends.
library;

import 'package:flutter/material.dart';

import '../../domain/orchestration_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/orchestration.dart';
import '../kit/kit_receipt.dart';

/// The newest record answering [gate] from this device that no retry
/// superseded: the `respond` on the gate's own id, or — for a failed run —
/// the `cancelRun` on its run or the `assign` that re-slung one of its
/// work items. Null when the gate was never answered from here.
MutationRecord? teamGateMutation(
  OrchestrationController controller,
  OrchestrationGate gate,
) {
  if (gate.kind != GateKind.runFailed) return controller.mutationFor(gate.id);
  final runId = gate.runId;
  if (runId == null) return null;
  final workOfRun = {
    for (final item in controller.snapshot.work)
      if (item.runId == runId) item.id,
  };
  MutationRecord? best;
  for (final record in controller.mutations) {
    if (record.retriedBy != null) continue;
    final matches = switch (record.kind) {
      MutationKind.cancelRun || MutationKind.merge => record.targetId == runId,
      MutationKind.assign => workOfRun.contains(record.targetId),
      MutationKind.approveMerge => workOfRun.contains(record.targetId),
      MutationKind.respond ||
      MutationKind.message ||
      MutationKind.controlAgent ||
      MutationKind.controlProject ||
      MutationKind.controlScheduledJob ||
      MutationKind.createWork => false,
    };
    if (!matches) continue;
    if (best == null || record.createdAt.isAfter(best.createdAt)) {
      best = record;
    }
  }
  return best;
}

/// True when [gate]'s answer is confirmed and newer than the gate itself:
/// the row leaves the list (02-ux §6). A gate raised again after the
/// answer — a run that failed once more — shows again.
bool teamGateAnswered(
  OrchestrationController controller,
  OrchestrationGate gate,
) {
  final record = teamGateMutation(controller, gate);
  if (record == null || record.status != MutationStatus.confirmed) return false;
  final raised = gate.createdAt;
  return raised == null || !record.createdAt.isBefore(raised);
}

/// The receipt line per status: "Sent · waiting for the host to confirm",
/// "Answered", the unconfirmed copy, or the host's refusal.
String teamReceiptLine(AppLocalizations l10n, MutationRecord record) =>
    switch (record.status) {
      MutationStatus.sent => l10n.teamUiReceiptSent,
      MutationStatus.confirmed => l10n.teamUiReceiptAnswered,
      MutationStatus.unconfirmed => l10n.teamUiReceiptUnconfirmed,
      MutationStatus.rejected => switch (record.receipt?.message?.trim()) {
        final message? when message.isNotEmpty => l10n.teamUiGateAnswerRejected(
          message,
        ),
        _ => l10n.teamUiGateAnswerRejectedNoMessage,
      },
    };

/// A gate answer's receipt inside a row that points to the gate (the
/// Activity list, the AI Team lists): the state's glyph and word — "Sending…",
/// "Not confirmed yet" or "Not accepted" — as a span of the row's
/// supporting line, right after its first word ("Question · Not confirmed
/// yet · …"), so the row's trailing slot keeps its chevron and the row
/// itself opens the gate, where Try again lives. Null for a confirmed
/// answer (the row leaves the list).
InlineSpan? teamGateReceiptSpan(BuildContext context, MutationRecord record) {
  final state = switch (record.status) {
    MutationStatus.sent => KitReceiptState.sending,
    MutationStatus.unconfirmed => KitReceiptState.notConfirmed,
    MutationStatus.rejected => KitReceiptState.refused,
    MutationStatus.confirmed => null,
  };
  if (state == null) return null;
  return KitReceipt.span(context, state, mark: true);
}

/// A gate row's supporting line: [parts] joined by " · ", with the answer's
/// receipt ([teamGateReceiptSpan]) after the first part while the host has
/// not confirmed it — "Question · Not confirmed yet · Add dark mode · 3 min
/// ago".
InlineSpan teamGateRowLine(
  BuildContext context,
  List<String> parts, {
  MutationRecord? record,
}) {
  final receipt = record == null ? null : teamGateReceiptSpan(context, record);
  if (receipt == null || parts.isEmpty) {
    return TextSpan(text: parts.join(_separator));
  }
  final rest = parts.skip(1).join(_separator);
  return TextSpan(
    children: [
      TextSpan(text: '${parts.first}$_separator'),
      receipt,
      if (rest.isNotEmpty) TextSpan(text: '$_separator$rest'),
    ],
  );
}

/// The team's one separator (teamUsageSeparator).
const _separator = ' · ';
