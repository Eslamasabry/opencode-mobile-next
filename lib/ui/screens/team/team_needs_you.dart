/// What the team needs from the person, drawn the same wherever it shows
/// (docs/design/aiteam-redesign-2026-09-24.md): on the AI Team home, first,
/// and on a task's Overview.
///
/// One question is the one request card ([KitRequestCard.ask]) with its
/// answers in place, as in chat (slice-P4.1c): a decision's options, an
/// approval's Approve and Deny, a free-text reply, each through the same
/// [OrchestrationController.answerGate] the Gate sheet uses; the card then
/// carries the receipt until the host confirms. Details opens the Gate
/// sheet. Several questions are a short list of rows, each opening it.
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/orchestration_gateway.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/orchestration.dart';
import '../../kit/kit.dart';
import '../../widgets/relative_time.dart';
import '../../widgets/team_controls.dart' show teamControlWord;
import '../../widgets/team_receipt.dart';
import '../../widgets/team_vocabulary.dart';
import 'gate_sheet.dart' show gateSheetDraftTarget, teamGateIfIgnored;

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Most urgent first (BRD §47: a decision, a failed task, review ready, a
/// gate), then the newest.
int teamCompareGates(OrchestrationGate a, OrchestrationGate b) {
  final rank = teamGateRank(a.kind).compareTo(teamGateRank(b.kind));
  if (rank != 0) return rank;
  final at = a.createdAt, bt = b.createdAt;
  if (at == null || bt == null) return 0;
  return bt.compareTo(at);
}

/// The gates to show, in [teamCompareGates] order, without those whose
/// answer the host confirmed (02-ux §6). With [runId], only that task's,
/// and review-ready (informational) left out.
List<OrchestrationGate> teamOpenGates(
  OrchestrationController controller, {
  String? runId,
}) {
  final snapshot = controller.snapshot;
  return [
    for (final gate in snapshot.gates)
      if (!teamGateAnswered(controller, gate) &&
          (runId == null ||
              (gate.kind != GateKind.reviewReady &&
                  teamGateRunId(snapshot, gate) == runId)))
        gate,
  ]..sort(teamCompareGates);
}

/// The task [gate] belongs to, when the snapshot lists it.
OrchestrationRun? teamGateRun(
  OrchestrationSnapshot snapshot,
  OrchestrationGate gate,
) {
  final id = teamGateRunId(snapshot, gate);
  if (id == null) return null;
  for (final run in snapshot.runs) {
    if (run.id == id) return run;
  }
  return null;
}

/// Who asks, for a gate's card caption when no task names it: the agent
/// the gate belongs to, else "The team".
String teamGateWho(
  AppLocalizations l10n,
  OrchestrationSnapshot snapshot,
  OrchestrationGate gate,
) {
  final id = gate.agentId;
  if (id != null) {
    for (final agent in snapshot.agents) {
      if (agent.id == id || agent.sessionId == id) return agent.name;
    }
  }
  return l10n.teamChatLeadName;
}

/// The longest answer the card's sending line repeats before it ends with
/// an ellipsis (the whole answer is in the gate's Details).
const _answerWordsMax = 60;

/// One gate as the one request card ([KitRequestCard.ask]), the same card
/// the chat's permissions and questions use, wherever the gate shows: the
/// team's conversation, a task's Overview and the AI Team home. It answers
/// by itself through [OrchestrationController.answerGate], so any screen
/// that shows it can be answered from:
///
/// - a decision: its options in place; a tap sends;
/// - an approval: Approve and Deny in place (a destructive one is answered
///   in Details, where its two-step confirmation lives);
/// - a free-text question: the reply field (its draft is the Gate sheet's,
///   so words typed in either survive) and Send;
/// - a gate bead and a failed task: Answer / Choose what to do open
///   Details (the Gate sheet), where their actions live;
/// - review ready: Details only.
///
/// After an answer the card shows it with its [KitReceipt]: "Sending…",
/// then "Not confirmed yet" with Try again, or the host's refusal above the
/// answers again; once the host confirms, the card collapses to one line
/// (and the lists drop the gate). Without the respond control the card
/// says where to answer and keeps Details.
///
/// [title] names who asks or what it is about (the task on the home, the
/// agent in a conversation); it leads the card's caption. On the home the
/// card is also the task's row (nothing shown twice): [detail] carries the
/// task's step count and [onOpenTask] its conversation.
///
/// Keys: `<keyPrefix>` on the block, `-question`, `-option-<i>`,
/// `-approve`, `-deny`, `-reply`, `-send`, `-more` (Details), `-task`,
/// `-receipt`, `-retry`.
class TeamNeedsYouCard extends StatefulWidget {
  const TeamNeedsYouCard({
    super.key,
    required this.controller,
    required this.gate,
    required this.title,
    required this.onOpen,
    required this.keyPrefix,
    this.detail,
    this.onOpenTask,
    this.look = KitRequestLook.standard,
  });

  /// [KitRequestLook.question] in the task's conversation (the chat's
  /// question card); the AI Team home and lists keep the standard card.
  final KitRequestLook look;

  final OrchestrationController controller;
  final OrchestrationGate gate;

  /// Who asks or what it is about, in the caption.
  final String title;

  /// One line under the question: the task's progress ("1 of 5 steps
  /// done").
  final String? detail;

  /// Opens the task's conversation; no action when null.
  final VoidCallback? onOpenTask;

  /// Opens Details (the Gate sheet): the whole question, every action,
  /// Technical details.
  final VoidCallback onOpen;
  final String keyPrefix;

  @override
  State<TeamNeedsYouCard> createState() => _TeamNeedsYouCardState();
}

class _TeamNeedsYouCardState extends State<TeamNeedsYouCard> {
  late TextEditingController _reply;
  late KitDraft _draft;

  @override
  void initState() {
    super.initState();
    _reply = TextEditingController();
    _draft = _draftFor(widget.gate.id);
  }

  KitDraft _draftFor(String gateId) => KitDraft(
    target: gateSheetDraftTarget(gateId),
    profileId: widget.controller.profileId,
    controller: _reply,
  );

  @override
  void didUpdateWidget(TeamNeedsYouCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.gate.id != widget.gate.id ||
        oldWidget.controller != widget.controller) {
      final old = _reply;
      WidgetsBinding.instance.addPostFrameCallback((_) => old.dispose());
      _reply = TextEditingController();
      _draft = _draftFor(widget.gate.id);
    }
  }

  @override
  void dispose() {
    _reply.dispose();
    super.dispose();
  }

  Key _key(String part) => ValueKey('${widget.keyPrefix}-$part');

  Future<void> _answer(GateResponse response, {bool clearDraft = false}) async {
    final record = await widget.controller.answerGate(widget.gate.id, response);
    // The typed answer is kept until it left for good (DATA-1): a refused
    // one stays in the field to edit and send again.
    if (clearDraft && record.status != MutationStatus.rejected) {
      await _draft.clear();
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final card = _card(context);
      return KeyedSubtree(
        key: ValueKey(widget.keyPrefix),
        // In the conversation the transcript's own gutter places the
        // question; it rises in like the chat's.
        child: widget.look == KitRequestLook.question
            ? KitEntrance(child: card)
            : card,
      );
    },
  );

  KitRequestCard _card(BuildContext context) {
    final l10n = _copy(context);
    final controller = widget.controller;
    final gate = widget.gate;
    final caps = controller.capabilities;
    final canRespond = caps.controlRespond;
    final record = teamGateMutation(controller, gate);
    final prompt = gate.prompt?.trim();
    final promptShown =
        prompt != null && prompt.isNotEmpty && prompt != gate.title;
    final hostMode = controller.host?.hostMode ?? controller.config.hostMode;
    final destructive =
        gate.kind == GateKind.confirmation && teamGateIsDestructive(gate);
    final runActions =
        caps.controlMessage || caps.controlAssign || caps.controlCancelRun;
    final answerable = switch (gate.kind) {
      GateKind.runFailed => runActions,
      GateKind.reviewReady || GateKind.unknown => false,
      GateKind.choice => canRespond && gate.choices.isNotEmpty,
      _ => canRespond,
    };

    final (phase, receipt) = _state(l10n, record);
    final kind = switch (gate.kind) {
      GateKind.choice when answerable => KitRequestKind.question,
      GateKind.freeText when answerable => KitRequestKind.reply,
      _ => KitRequestKind.gate,
    };
    final KitRequestAnswers? answers = !answerable
        ? null
        : switch (gate.kind) {
            GateKind.choice => KitRequestChoose<int>(
              choices: [
                for (final (index, choice) in gate.choices.indexed)
                  KitChoice<int>(
                    key: _key('option-$index'),
                    value: index,
                    title: choice,
                  ),
              ],
              chosen: switch (record?.request.choice) {
                final String choice => gate.choices.indexOf(choice),
                null => null,
              },
              onChosen: (index) =>
                  unawaited(_answer(GateResponse.choice(gate.choices[index]))),
            ),
            GateKind.confirmation when !destructive => KitRequestDecide(
              rejectLabel: l10n.teamUiGateAnswerDeny,
              allowKey: _key('approve'),
              rejectKey: _key('deny'),
              onAllow: () => unawaited(
                _answer(const GateResponse.confirmation(confirmed: true)),
              ),
              onReject: () => unawaited(
                _answer(const GateResponse.confirmation(confirmed: false)),
              ),
            ),
            GateKind.freeText => KitRequestReply(
              fieldLabel: l10n.gateSheetAnswerLabel,
              draft: _draft,
              fieldKey: _key('reply'),
              sendKey: _key('send'),
              onSend: (text) =>
                  unawaited(_answer(GateResponse.text(text), clearDraft: true)),
            ),
            GateKind.runFailed => KitRequestInSheet(
              label: l10n.teamGateCardRunFailedOpen,
              key: _key('answer'),
            ),
            _ => KitRequestInSheet(
              label: l10n.teamUiHomeNeedsYouAnswer,
              key: _key('answer'),
            ),
          };
    // Where to answer when this phone only watches (a failed task and a
    // review have no respond answer to miss).
    final watchOnly =
        !answerable &&
            gate.kind != GateKind.reviewReady &&
            gate.kind != GateKind.unknown
        ? switch (hostMode) {
            OrchestrationHostMode.computer =>
              l10n.teamUiHomeGateAnswerOnComputer,
            OrchestrationHostMode.phone => l10n.teamUiHomeGateAnswerOnPhone,
          }
        : null;
    final detail = [
      if (promptShown) prompt,
      ?widget.detail,
      ?watchOnly,
    ].join('\n');
    final question = widget.look == KitRequestLook.question;
    return KitRequestCard.ask(
      look: widget.look,
      inList: question,
      kind: kind,
      icon: teamGateMark(gate.kind).icon,
      title: gate.title,
      titleKey: _key('question'),
      who: widget.title,
      reason: gate.kind == GateKind.runFailed
          ? KitNeedsYouReason.blocked
          : KitNeedsYouReason.decision,
      ifIgnored: teamGateIfIgnored(l10n, gate.kind),
      announcement: l10n.teamUiHomeNeedsYouAnnouncement(gate.title),
      detail: detail.isEmpty ? null : detail,
      // A gate's question is its title and its prompt the context (often a
      // plan): in the question look the prompt reads under the question,
      // formatted and bounded, never over it.
      detailMarkdown: question && promptShown,
      since: gate.createdAt,
      phase: phase,
      receipt: receipt,
      answer: record == null ? null : _answerWords(l10n, gate, record),
      answers: answers,
      onDetails: widget.onOpen,
      detailsKey: _key('more'),
      tertiary: [
        if (widget.onOpenTask case final openTask?)
          KitAction(
            key: _key('task'),
            label: l10n.teamOpenConversation,
            onPressed: openTask,
          ),
      ],
    );
  }

  /// The card's phase and receipt for the newest answer from this phone:
  /// none waits; sent and not confirmed are the sending line; a refusal
  /// waits again with its reason above the answers; confirmed collapses.
  (KitRequestPhase, KitReceipt?) _state(
    AppLocalizations l10n,
    MutationRecord? record,
  ) {
    if (record == null) return (KitRequestPhase.waiting, null);
    final retry = record.canRetry
        ? () => unawaited(widget.controller.retryMutation(record.key))
        : null;
    return switch (record.status) {
      MutationStatus.sent => (
        KitRequestPhase.sending,
        KitReceipt(
          key: _key('receipt'),
          state: KitReceiptState.sending,
          since: record.createdAt,
          onRetry: retry,
          retryKey: _key('retry'),
        ),
      ),
      MutationStatus.unconfirmed => (
        KitRequestPhase.sending,
        KitReceipt(
          key: _key('receipt'),
          state: KitReceiptState.notConfirmed,
          since: record.createdAt,
          onRetry: retry,
          retryKey: _key('retry'),
        ),
      ),
      MutationStatus.rejected => (
        KitRequestPhase.waiting,
        KitReceipt(
          key: _key('receipt'),
          state: KitReceiptState.refused,
          reason: switch (record.receipt?.message?.trim()) {
            final message? when message.isNotEmpty => message,
            _ => null,
          },
        ),
      ),
      MutationStatus.confirmed => (
        KitRequestPhase.answered,
        KitReceipt(
          key: _key('receipt'),
          state: KitReceiptState.confirmed,
          label: l10n.teamUiReceiptAnswered,
          at: record.updatedAt,
        ),
      ),
    };
  }
}

/// The answer in words on the card's sending line: the option, Approve or
/// Deny, Mark done, the typed words (shortened), or the control's name for
/// a failed task's way out.
String _answerWords(
  AppLocalizations l10n,
  OrchestrationGate gate,
  MutationRecord record,
) {
  final request = record.request;
  if (request.kind != MutationKind.respond) {
    return teamControlWord(l10n, request);
  }
  if (request.choice case final String choice) return choice;
  if (request.text case final String text) {
    final words = text.trim().replaceAll(RegExp(r'\s+'), ' ');
    return words.length <= _answerWordsMax
        ? words
        : '${words.substring(0, _answerWordsMax).trimRight()}…';
  }
  if (request.confirmed == false) return l10n.teamUiGateAnswerDeny;
  return gate.kind == GateKind.gateBead
      ? l10n.teamUiGateAnswerMarkDone
      : l10n.teamUiGateAnswerApprove;
}

/// One of several questions, as a row: the question (two lines), what it
/// is about and its age; the receipt once an answer left this phone. The
/// row opens the Gate sheet.
class TeamGateRow extends StatelessWidget {
  const TeamGateRow({
    super.key,
    required this.controller,
    required this.gate,
    required this.now,
    required this.onTap,
    this.receiptKey,
  });

  final OrchestrationController controller;
  final OrchestrationGate gate;
  final DateTime now;
  final VoidCallback onTap;
  final Key? receiptKey;

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final record = teamGateMutation(controller, gate);
    final age = gate.createdAt == null
        ? null
        : relativeTimeLabel(
            gate.createdAt!.millisecondsSinceEpoch,
            now: now,
            l10n: l10n,
          );
    final task = teamGateRun(controller.snapshot, gate)?.title;
    // "Question · Not confirmed yet · <task> · 3 min ago": the answer's
    // receipt as a word after the kind while the host has not confirmed
    // it. The row opens the Gate sheet, where Try again lives.
    return KitRow(
      leading: teamGateMark(gate.kind).leading(context),
      title: gate.title,
      titleMaxLines: 2,
      supporting: teamGateRowLine(context, [
        teamGateKindWord(l10n, gate.kind),
        ?task,
        ?age,
      ], record: record),
      supportingKey: receiptKey,
      trailing: const KitChevron(),
      onTap: onTap,
    );
  }
}
