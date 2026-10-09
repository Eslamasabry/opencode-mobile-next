/// The Gate sheet (02-ux §6): the Details of a gate's card
/// (`TeamNeedsYouCard`, the one [KitRequestCard.ask]), and what a pointing
/// row opens from Activity, the AI Team lists, an agent or a notification.
/// Five variants — Choice, Confirmation, Free text, Gate bead, Run failed —
/// plus Review ready (slice-P4.1c).
///
/// Built from kit parts only: the one sheet frame ([showKitSheet]) with the
/// card's header — the kind's tile, the ask as the title ("Sync engine
/// stopped" for a failed task) and "{kind} · {task} · {age}" under it —
/// then the whole question, the variant's body, the receipt, the actions
/// and, last and folded, every id and raw value ([KitDetailsFold]).
///
/// The card answers the common cases in place (an option, Approve / Deny,
/// a reply); the sheet holds everything the card cannot: the whole
/// question, the destructive approval's two steps, a gate bead's Mark done
/// and a failed task's ways out. Each action is present only behind its
/// `control*` capability; without one the sheet explains where to answer
/// it and offers the host guide (How) instead of hiding the question:
///
/// - Choice: one [KitChoiceList.single] whose tap sends the answer.
/// - Confirmation: [Approve] (primary; a destructive prompt is confirmed in
///   the destructive tone first) and [Deny] (sends at once, as on the
///   card).
/// - Free text: a multi-line [KitField] whose draft is the card's too, so
///   it survives swipe, back and reopen (P7.1), and [Send].
/// - Gate bead: [Mark done].
/// - Run failed: **Ask the team to fix it** (sends the error to the
///   worker) as the primary, "Send Sync engine to fox again" (only when a
///   retry can recover it), the agent's page, Live output, Report this
///   failure (P8.4) and [Stop work] (confirmed, stop tone).
///
/// The sheet's own close button is the one way out: no action repeats it.
///
/// Every answer routes with the gate's own id through
/// [OrchestrationController.answerGate] and friends, which persist the
/// idempotency key before sending. The sheet then shows the receipt in
/// place ([KitReceipt]), the same one the card shows: sending, answered
/// (then it closes after a beat), not confirmed with Try again, or the
/// host's refusal with its reason. A retry is a new record under a new key
/// and only ever follows a tap.
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../../diagnostics/failed_job_report.dart';
import '../../../domain/orchestration_gateway.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/orchestration.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../widgets/relative_time.dart';
import '../app_diagnostics_screen.dart' show openReportProblem;
import '../../widgets/team_host_form.dart' show showTeamHostGuideSheet;
import '../../widgets/team_receipt.dart' show teamGateMutation;
import '../../widgets/team_vocabulary.dart';
import '../team_conversation/team_conversation.dart'
    show openTeamAgentConversationById;
import 'agent_screen.dart';
import 'work_sheet.dart';

/// How long "Answered" stays on screen before the sheet closes itself.
const gateSheetAnsweredBeat = Duration(milliseconds: 900);

/// Where the free-text answer to [gateId] is kept across dismissal (P7.1):
/// `oc.draft.team-gate.<gateId>.<profileId>`.
String gateSheetDraftTarget(String gateId) => 'team-gate.$gateId';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

OrchestrationGate? _gateIn(OrchestrationSnapshot snapshot, String gateId) {
  for (final candidate in snapshot.gates) {
    if (candidate.id == gateId) return candidate;
  }
  return null;
}

/// The title of the task a failed-run gate stopped, when the snapshot
/// lists it: the sheet is then titled after its task ("Sync engine
/// stopped") and says the failure once.
String? _stoppedTask(OrchestrationSnapshot snapshot, OrchestrationGate gate) {
  if (gate.kind != GateKind.runFailed) return null;
  for (final run in snapshot.runs) {
    if (run.id == gate.runId) return run.title;
  }
  return null;
}

/// Opens the Gate sheet for [gateId]. The sheet reads the gate from the
/// controller's snapshot on every rebuild, so one answered on the host
/// meanwhile says so instead of showing stale options. Work rows close
/// this sheet and open the Work sheet, so [context] must outlive it.
Future<void> showGateSheet(
  BuildContext context,
  OrchestrationController controller,
  String gateId, {
  DateTime Function()? now,
}) {
  final l10n = _copy(context);
  final gate = _gateIn(controller.snapshot, gateId);
  final at = (now ?? DateTime.now)();
  String? kicker;
  final stopped = gate == null ? null : _stoppedTask(controller.snapshot, gate);
  if (gate != null) {
    final age = gate.createdAt == null
        ? null
        : relativeTimeLabel(
            gate.createdAt!.millisecondsSinceEpoch,
            now: at,
            l10n: l10n,
          );
    // The card's own header: what it is, the task it belongs to (unless
    // the title already names it) and its age.
    final link = stopped != null
        ? null
        : teamGateLink(l10n, controller.snapshot, gate);
    kicker = [
      teamGateKindWord(l10n, gate.kind),
      ?link,
      ?age,
    ].join(teamUsageSeparator);
  }
  return showKitSheet<void>(
    context,
    // The ask is the title, as on the card this sheet is the Details of.
    title: gate == null
        ? l10n.teamUiAgentNeedsYou
        : stopped != null
        ? l10n.teamUiGateRunStoppedTitle(stopped)
        : gate.title,
    subtitle: kicker,
    icon: gate == null ? AppIconography.question : teamGateMark(gate.kind).icon,
    sheetKey: const ValueKey('team-gate-sheet'),
    body: (sheetContext) => GateSheet(
      controller: controller,
      gateId: gateId,
      now: now,
      onOpenWork: (id) {
        Navigator.of(sheetContext).pop();
        showWorkSheet(context, controller, id, now: now);
      },
      onOpenAgent: (id) {
        Navigator.of(sheetContext).pop();
        unawaited(
          pushKitPage<void>(
            context,
            (_) => AgentScreen(controller: controller, agentId: id, now: now),
          ),
        );
      },
      onOpenLogs: (id) {
        Navigator.of(sheetContext).pop();
        unawaited(openTeamAgentConversationById(context, controller, id));
      },
      onReport: (report) {
        Navigator.of(sheetContext).pop();
        unawaited(openReportProblem(context, error: report));
      },
    ),
  );
}

/// The sheet body; [showGateSheet] puts it in the kit's sheet frame, which
/// scrolls it.
class GateSheet extends StatelessWidget {
  const GateSheet({
    super.key,
    required this.controller,
    required this.gateId,
    required this.onOpenWork,
    this.onOpenAgent,
    this.onOpenLogs,
    this.onReport,
    this.now,
  });

  final OrchestrationController controller;
  final String gateId;
  final ValueChanged<String> onOpenWork;

  /// Opens the agent screen; the failed-run agent action is absent when
  /// null.
  final ValueChanged<String>? onOpenAgent;

  /// Opens the agent's conversation (Watch the agent).
  final ValueChanged<String>? onOpenLogs;

  /// Opens Report a problem with the failed run attached, its log when
  /// the host serves it (P8.4); Report is absent when null.
  final ValueChanged<KitReport>? onReport;
  final DateTime Function()? now;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final l10n = _copy(context);
      final snapshot = controller.snapshot;
      final gate = _gateIn(snapshot, gateId);
      if (gate == null) {
        // Answered or closed on the host meanwhile: a state, not a form.
        return KitStateView(
          key: const ValueKey('team-gate-sheet-missing'),
          size: KitStateSize.inline,
          icon: AppIconography.checkCircle,
          title: l10n.teamUiGateGone,
        );
      }
      return _Body(
        key: ValueKey('team-gate-sheet-${gate.id}'),
        controller: controller,
        gate: gate,
        snapshot: snapshot,
        hostMode: controller.host?.hostMode ?? controller.config.hostMode,
        onOpenWork: onOpenWork,
        onOpenAgent: onOpenAgent,
        onOpenLogs: onOpenLogs,
        onReport: onReport,
      );
    },
  );
}

/// A variant's actions in the one hierarchy, plus the lines that explain
/// them ([notes]).
class _GateActions {
  const _GateActions({
    this.primary,
    this.secondary,
    this.tertiary = const [],
    this.notes = const [],
    this.answers = false,
  });

  final KitAction? primary;
  final KitAction? secondary;
  final List<KitAction> tertiary;
  final List<String> notes;

  /// Whether this phone can act on the gate at all; when not, the sheet
  /// says where to answer it.
  final bool answers;

  bool get isEmpty => primary == null && secondary == null && tertiary.isEmpty;
}

class _Body extends StatefulWidget {
  const _Body({
    super.key,
    required this.controller,
    required this.gate,
    required this.snapshot,
    required this.hostMode,
    required this.onOpenWork,
    required this.onOpenAgent,
    required this.onOpenLogs,
    required this.onReport,
  });

  final OrchestrationController controller;
  final OrchestrationGate gate;
  final OrchestrationSnapshot snapshot;
  final OrchestrationHostMode hostMode;
  final ValueChanged<String> onOpenWork;
  final ValueChanged<String>? onOpenAgent;
  final ValueChanged<String>? onOpenLogs;
  final ValueChanged<KitReport>? onReport;

  @override
  State<_Body> createState() => _BodyState();
}

class _BodyState extends State<_Body> {
  int? _selected;
  final _text = TextEditingController();
  late final KitDraft _draft = KitDraft(
    target: gateSheetDraftTarget(widget.gate.id),
    profileId: widget.controller.profileId,
    controller: _text,
  );
  bool _sending = false;

  /// Report is waiting for the failed agent's output to arrive.
  bool _reporting = false;

  /// The record this sheet sent or retried; the receipt follows it (and
  /// any retry that superseded it).
  String? _activeKey;
  Timer? _closeTimer;

  OrchestrationGate get gate => widget.gate;
  OrchestrationSnapshot get snapshot => widget.snapshot;
  OrchestrationController get controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _text.addListener(_typed);
  }

  void _typed() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _closeTimer?.cancel();
    _text
      ..removeListener(_typed)
      ..dispose();
    super.dispose();
  }

  /// The record the receipt shows: the one this sheet sent, followed
  /// through retries, else the newest one answering the gate.
  MutationRecord? _record() {
    var record = _activeKey == null ? null : controller.mutation(_activeKey!);
    for (var hops = 0; record?.retriedBy != null && hops < 32; hops++) {
      final next = controller.mutation(record!.retriedBy!);
      if (next == null) break;
      record = next;
    }
    return record ?? teamGateMutation(controller, gate);
  }

  Future<void> _send(Future<MutationRecord> Function() action) async {
    if (_sending) return;
    setState(() => _sending = true);
    final record = await action();
    if (!mounted) return;
    setState(() {
      _sending = false;
      _activeKey = record.key;
    });
  }

  Future<void> _retry(MutationRecord record) async {
    if (_sending) return;
    setState(() => _sending = true);
    final next = await controller.retryMutation(record.key);
    if (!mounted) return;
    setState(() {
      _sending = false;
      if (next != null) _activeKey = next.key;
    });
  }

  /// The second step, raised inside this sheet (it replaces the content in
  /// place, KIT-16). Nothing is sent unless the person confirms here too.
  Future<bool> _confirm({
    required String title,
    required String body,
    required String label,
    required KitConfirmKind kind,
  }) => showKitConfirm(
    context,
    title: title,
    body: body,
    confirmLabel: label,
    kind: kind,
    sheetKey: const ValueKey('team-gate-confirm'),
    confirmKey: const ValueKey('team-gate-confirm-yes'),
  );

  void _scheduleClose() {
    if (_closeTimer != null) return;
    _closeTimer = Timer(gateSheetAnsweredBeat, () {
      if (!mounted) return;
      final navigator = Navigator.of(context);
      if (navigator.canPop()) navigator.pop();
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final record = _record();
    if (record != null && record.status == MutationStatus.confirmed) {
      _scheduleClose();
    }
    // While an answer is on its way the actions stay visible but off; an
    // unconfirmed one hides them (the same answer may have landed) and
    // offers Try again; a confirmed one hides them while the sheet closes;
    // a refused one keeps them beside Try again.
    final busy = _sending || (record?.isSent ?? false);
    final hideActions =
        record != null &&
        (record.status == MutationStatus.unconfirmed ||
            record.status == MutationStatus.confirmed);
    // The mapper uses the prompt as the title when the host gave no
    // other; say it once.
    final prompt = gate.prompt?.trim();
    final promptShown =
        prompt != null && prompt.isNotEmpty && prompt != gate.title;
    final caps = controller.capabilities;
    final interactive = caps.controlRespond && !hideActions;
    final body = switch (gate.kind) {
      GateKind.choice => [
        if (promptShown) _prompt(prompt),
        _choice(l10n, interactive: interactive, busy: busy),
      ],
      GateKind.confirmation => [
        if (teamGateIsDestructive(gate))
          KitNotice(
            key: const ValueKey('team-gate-destructive'),
            tone: AppStatusTone.failure,
            icon: AppIconography.warning,
            title: l10n.teamUiGateDestructive,
            message: l10n.gateSheetDestructiveBody,
          ),
        if (promptShown) _prompt(prompt),
      ],
      GateKind.freeText => [
        if (promptShown) _prompt(prompt),
        if (interactive) _composer(l10n, busy),
      ],
      GateKind.unknown => [if (promptShown) _prompt(prompt)],
      GateKind.gateBead ||
      GateKind.reviewReady => _bead(l10n, promptShown ? prompt : null),
      GateKind.runFailed => _runFailed(l10n),
    };
    // The sheet's close button is the way out; no action repeats it.
    final actions = hideActions
        ? const _GateActions()
        : _actions(l10n, caps, busy: busy);
    final error = gate.kind == GateKind.runFailed ? gate.prompt?.trim() : null;
    final gap = SizedBox(height: tokens.space3);
    return Column(
      key: const ValueKey('team-gate-body'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // The ask is the sheet's title (the card's header): no second
        // heading here.
        for (final (index, part) in body.indexed) ...[if (index > 0) gap, part],
        if (record != null) ...[
          gap,
          KitReceipt(
            key: ValueKey('team-gate-receipt-${record.status.name}'),
            state: switch (record.status) {
              MutationStatus.sent => KitReceiptState.sending,
              MutationStatus.confirmed => KitReceiptState.confirmed,
              MutationStatus.unconfirmed => KitReceiptState.notConfirmed,
              MutationStatus.rejected => KitReceiptState.refused,
            },
            label: l10n.teamUiReceiptAnswered,
            reason: record.receipt?.message?.trim(),
            since: record.createdAt,
            onRetry:
                record.canRetry &&
                    (record.status == MutationStatus.unconfirmed ||
                        record.status == MutationStatus.rejected) &&
                    !_sending
                ? () => _retry(record)
                : null,
            retryKey: const ValueKey('team-gate-retry'),
          ),
        ],
        if (!actions.answers && !hideActions && record == null) ...[
          gap,
          KitNotice(
            key: const ValueKey('team-gate-answer-on-host'),
            icon: AppIconography.info,
            message: _hostLine(l10n),
            actions: [
              KitAction(
                key: const ValueKey('team-gate-how'),
                label: l10n.teamUiHow,
                onPressed: () => unawaited(showTeamHostGuideSheet(context)),
              ),
            ],
          ),
        ],
        for (final (index, note) in actions.notes.indexed) ...[
          gap,
          KitText(
            note,
            key: ValueKey('team-gate-note-$index'),
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        ],
        if (!actions.isEmpty) ...[
          gap,
          KitActionBlock(
            primary: actions.primary,
            secondary: actions.secondary,
            tertiary: actions.tertiary,
          ),
        ],
        // Details close the sheet, never above the actions (KIT-33).
        gap,
        KitDetailsFold(
          label: l10n.teamUiTechnicalDetails,
          foldKey: const ValueKey('team-gate-technical'),
          values: _technical(l10n),
          text: error == null || error.isEmpty ? null : error,
          textKey: const ValueKey('team-gate-error'),
        ),
      ],
    );
  }

  /// Where to act, per kind and host mode: answer, close or review.
  String _hostLine(AppLocalizations l10n) {
    final phone = widget.hostMode == OrchestrationHostMode.phone;
    return switch (gate.kind) {
      GateKind.gateBead =>
        phone ? l10n.teamUiGateCloseOnHostPhone : l10n.teamUiGateCloseOnHost,
      GateKind.reviewReady =>
        phone ? l10n.teamUiGateReviewOnHostPhone : l10n.teamUiGateReviewOnHost,
      GateKind.choice ||
      GateKind.confirmation ||
      GateKind.freeText ||
      GateKind.unknown ||
      GateKind.runFailed =>
        phone ? l10n.teamUiGateAnswerOnHostPhone : l10n.teamUiGateAnswerOnHost,
    };
  }

  Widget _prompt(String text) => KitText(
    text,
    key: const ValueKey('team-gate-prompt'),
    role: KitTextRole.body,
  );

  /// The options: a tap sends the answer (KIT-25: one choice acts on tap);
  /// read-only rows when this phone cannot answer.
  Widget _choice(
    AppLocalizations l10n, {
    required bool interactive,
    required bool busy,
  }) {
    if (!interactive) {
      return KitRowGroup(
        margin: EdgeInsets.zero,
        label: l10n.teamUiHomeGateOptions,
        leadingIcons: false,
        children: [
          for (final (index, choice) in gate.choices.indexed)
            KitRow(
              key: ValueKey('team-gate-option-$index'),
              title: choice,
              titleMaxLines: 3,
            ),
        ],
      );
    }
    return KitChoiceList<int>.single(
      semanticsLabel: l10n.teamUiHomeGateOptions,
      sends: true,
      selected: _selected,
      choices: [
        for (final (index, choice) in gate.choices.indexed)
          KitChoice<int>(
            key: ValueKey('team-gate-option-$index'),
            value: index,
            title: choice,
            enabled: !busy,
            disabledReason: busy ? l10n.teamUiReceiptSent : null,
          ),
      ],
      onSelected: (index) {
        if (busy || index >= gate.choices.length) return;
        setState(() => _selected = index);
        unawaited(
          _send(
            () => controller.answerGate(
              gate.id,
              GateResponse.choice(gate.choices[index]),
            ),
          ),
        );
      },
    );
  }

  /// The free-text answer: multi-line, its draft kept (P7.1).
  Widget _composer(AppLocalizations l10n, bool busy) => KitField(
    label: l10n.gateSheetAnswerLabel,
    hint: l10n.teamUiGateAnswerHint,
    kind: KitFieldKind.multiline,
    draft: _draft,
    enabled: !busy,
    disabledReason: busy ? l10n.teamUiReceiptSent : null,
    fieldKey: const ValueKey('team-gate-composer'),
  );

  // -------------------------------------------------------------------------
  // Actions (TEAM-203)
  // -------------------------------------------------------------------------

  /// The variant's actions in the one hierarchy, each only behind its
  /// capability. When none is left the sheet explains where to answer.
  _GateActions _actions(
    AppLocalizations l10n,
    OrchestrationCapabilities caps, {
    required bool busy,
  }) {
    final after = l10n.gateSheetAfterAnswer;
    switch (gate.kind) {
      case GateKind.choice:
        if (!caps.controlRespond) return const _GateActions();
        return _GateActions(answers: true, notes: [after]);
      case GateKind.freeText:
        if (!caps.controlRespond) return const _GateActions();
        final text = _text.text.trim();
        return _GateActions(
          answers: true,
          notes: [after],
          primary: KitAction(
            key: const ValueKey('team-gate-send'),
            label: l10n.teamUiGateAnswerSend,
            icon: AppIconography.send,
            disabledReason: text.isEmpty ? l10n.gateSheetSendNeedsText : null,
            onPressed: busy || text.isEmpty
                ? null
                : () => _send(() async {
                    final record = await controller.answerGate(
                      gate.id,
                      GateResponse.text(text),
                    );
                    await _draft.clear();
                    return record;
                  }),
          ),
        );
      case GateKind.confirmation:
        if (!caps.controlRespond) return const _GateActions();
        final destructive = teamGateIsDestructive(gate);
        Future<void> answer(bool confirmed) => _send(
          () => controller.answerGate(
            gate.id,
            GateResponse.confirmation(confirmed: confirmed),
          ),
        );
        return _GateActions(
          answers: true,
          notes: [after],
          // The whole sheet is this one answer: a destructive approve is
          // the (destructive-toned, confirmed) primary.
          primary: KitAction(
            key: const ValueKey('team-gate-approve'),
            label: l10n.teamUiGateAnswerApprove,
            destructive: destructive,
            onPressed: busy
                ? null
                : destructive
                ? () async {
                    final ok = await _confirm(
                      title: l10n.teamUiGateAnswerConfirmApproveTitle,
                      body: l10n.teamUiGateAnswerConfirmApproveBody,
                      label: l10n.teamUiGateAnswerApprove,
                      kind: KitConfirmKind.destructive,
                    );
                    if (ok && mounted) await answer(true);
                  }
                : () => answer(true),
          ),
          // Deny sends at once, as on the card: saying no loses nothing.
          secondary: KitAction(
            key: const ValueKey('team-gate-deny'),
            label: l10n.teamUiGateAnswerDeny,
            onPressed: busy ? null : () => answer(false),
          ),
        );
      case GateKind.gateBead:
        if (!caps.controlRespond) return const _GateActions();
        return _GateActions(
          answers: true,
          notes: [after],
          primary: KitAction(
            key: const ValueKey('team-gate-mark-done'),
            label: l10n.teamUiGateAnswerMarkDone,
            onPressed: busy
                ? null
                : () => _send(
                    () => controller.answerGate(
                      gate.id,
                      const GateResponse.confirmation(confirmed: true),
                    ),
                  ),
          ),
        );
      case GateKind.runFailed:
        return _runActions(l10n, caps, busy: busy);
      case GateKind.reviewReady:
      case GateKind.unknown:
        return const _GateActions();
    }
  }

  /// A doable primary on failure: Ask the team to fix it (the error goes
  /// to the worker as a message). Then "Send the work to its agent again"
  /// (only when a retry can recover it), and under it the agent's page,
  /// View logs and Stop work (confirmed, stop tone, last).
  _GateActions _runActions(
    AppLocalizations l10n,
    OrchestrationCapabilities caps, {
    required bool busy,
  }) {
    final runId = gate.runId;
    final open = _affectedWork();
    WorkItem? stuck;
    for (final item in open) {
      if (teamWorkIsStuck(item.state)) {
        stuck = item;
        break;
      }
    }
    stuck ??= open.isEmpty ? null : open.first;
    String? agentId = stuck?.assignee ?? gate.agentId;
    if (agentId == null) {
      for (final item in open) {
        if (item.assignee != null) {
          agentId = item.assignee;
          break;
        }
      }
    }
    final target = agentId ?? _runTarget();
    final agentName = agentId == null ? null : _agentName(agentId);
    final notes = <String>[];
    KitAction? fix;
    if (caps.controlMessage && agentId != null) {
      final id = agentId;
      final error = _errorText().trim();
      final request = l10n.gateSheetFixRequest(
        stuck?.title ?? gate.title,
        error.isEmpty ? teamFailureClassWord(l10n, _failureClass()) : error,
      );
      fix = KitAction(
        key: const ValueKey('team-gate-run-fix'),
        label: l10n.gateSheetFixIt,
        icon: AppIconography.chat,
        onPressed: busy
            ? null
            : () => _send(() => controller.messageAgent(id, request)),
      );
      notes.add(l10n.gateSheetFixItDetail(agentName ?? id));
    }
    KitAction? retry;
    // A failure a retry cannot recover offers no retry: something needs
    // changing first, which Ask the team to fix it asks for.
    final recoverable = teamFailureRecoverable(_failureClass());
    if (caps.controlAssign &&
        stuck != null &&
        target != null &&
        recoverable != false) {
      final item = stuck;
      // The button names what it sends and to whom; no note repeats it.
      retry = KitAction(
        key: const ValueKey('team-gate-run-retry'),
        label: l10n.teamUiGateAnswerRunRetry(item.title, agentName ?? target),
        onPressed: busy
            ? null
            : () =>
                  _send(() => controller.assignWork(item.id, agentId: target)),
      );
    }
    KitAction? agent;
    final onOpenAgent = widget.onOpenAgent;
    if (onOpenAgent != null && agentId != null && caps.agents) {
      final id = agentId;
      agent = KitAction(
        key: const ValueKey('team-gate-run-agent'),
        label: l10n.gateSheetOpenAgent(agentName ?? id),
        onPressed: () => onOpenAgent(id),
      );
    }
    KitAction? logs;
    final onOpenLogs = widget.onOpenLogs;
    if (onOpenLogs != null && agentId != null && caps.agentOutput) {
      final id = agentId;
      logs = KitAction(
        key: const ValueKey('team-gate-run-logs'),
        label: l10n.teamUiGateAnswerRunLogs,
        onPressed: () => onOpenLogs(id),
      );
    }
    KitAction? cancel;
    if (caps.controlCancelRun && runId != null) {
      cancel = KitAction(
        key: const ValueKey('team-gate-run-cancel'),
        label: l10n.teamUiGateAnswerRunCancel,
        destructive: true,
        onPressed: busy
            ? null
            : () async {
                final task = _stoppedTask(controller.snapshot, gate)?.trim();
                final ok = await _confirm(
                  title: l10n.teamUiGateAnswerConfirmCancelRunTitle,
                  body: task == null || task.isEmpty
                      ? l10n.teamUiGateAnswerConfirmCancelRunBody
                      : l10n.teamUiGateAnswerConfirmCancelNamedBody(task),
                  label: l10n.teamUiGateAnswerRunCancel,
                  kind: KitConfirmKind.stop,
                );
                if (ok && mounted) {
                  await _send(() => controller.cancelRun(runId));
                }
              },
      );
    }
    // Report is always there for a failed run (P8.4), with the log when
    // the host serves it; it never answers the gate.
    KitAction? report;
    if (widget.onReport != null) {
      report = KitAction(
        key: const ValueKey('team-gate-run-report'),
        label: l10n.failedJobReport,
        icon: AppIconography.bug,
        onPressed: _reporting ? null : () => unawaited(_report(stuck)),
      );
    }
    return _GateActions(
      answers:
          fix != null ||
          retry != null ||
          agent != null ||
          logs != null ||
          cancel != null,
      primary: fix,
      secondary: retry,
      notes: notes,
      // Two tertiary actions show; the rest go under More. Stop work is
      // last; the sheet's close button is its way out.
      tertiary: [?agent, ?logs, ?report, ?cancel],
    );
  }

  /// How long Report waits for a failed agent's output before it opens
  /// the report without a log.
  static const reportOutputWait = Duration(seconds: 3);

  /// Captures the failed run for Report: the gate's own work item (else
  /// the run's displayed stuck one) and, when the host serves output, its
  /// agent's tail. [FailedJobReport.teamGate] attaches the log only when
  /// the tail's session is that work's session, never a reused agent's
  /// newer one.
  Future<void> _report(WorkItem? displayed) async {
    final onReport = widget.onReport;
    if (onReport == null || _reporting) return;
    WorkItem? work = displayed;
    if (gate.workId case final id?) {
      work = null;
      for (final item in snapshot.work) {
        if (item.id == id) work = item;
      }
    }
    final agentId = work?.assignee ?? gate.agentId;
    AgentOutputTail? tail;
    if (agentId != null && controller.capabilities.agentOutput) {
      setState(() => _reporting = true);
      tail = controller.watchAgentOutput(agentId);
      try {
        await _firstOutput(tail);
      } finally {
        controller.unwatchAgentOutput(agentId);
      }
      if (!mounted) return;
      setState(() => _reporting = false);
    }
    final report = FailedJobReport.teamGate(
      gate,
      work: work,
      sessionId: tail?.sessionId,
      logTail: tail?.text ?? '',
    );
    if (report == null) return;
    onReport(report.toKitReport(title: _stoppedTitle()));
  }

  /// The sheet's own title for a stopped task ("Sync engine stopped"),
  /// else the gate's.
  String? _stoppedTitle() {
    final stopped = _stoppedTask(snapshot, gate);
    return stopped == null
        ? null
        : _copy(context).teamUiGateRunStoppedTitle(stopped);
  }

  /// Completes once [tail] has text, ended, cannot be served or stopped
  /// streaming, or after [reportOutputWait].
  static Future<void> _firstOutput(AgentOutputTail tail) {
    bool settled() =>
        tail.text.isNotEmpty ||
        tail.received ||
        tail.ended ||
        !tail.available ||
        !tail.watching;
    if (settled()) return Future.value();
    final done = Completer<void>();
    void check() {
      if (settled() && !done.isCompleted) done.complete();
    }

    tail.addListener(check);
    return done.future
        .timeout(reportOutputWait, onTimeout: () {})
        .whenComplete(() => tail.removeListener(check));
  }

  /// The failed run's open work, stuck items first.
  List<WorkItem> _affectedWork() =>
      [
        for (final item in snapshot.work)
          if (gate.runId != null &&
              item.runId == gate.runId &&
              teamWorkIsOpen(item.state))
            item,
      ]..sort((a, b) {
        final stuck =
            (teamWorkIsStuck(b.state) ? 1 : 0) -
            (teamWorkIsStuck(a.state) ? 1 : 0);
        if (stuck != 0) return stuck;
        return teamWorkStateRank(a.state).compareTo(teamWorkStateRank(b.state));
      });

  /// The run's sling target as the host recorded it (`ocproof/polecats`),
  /// the fallback when no work item names an agent.
  String? _runTarget() {
    if (gate.raw['target'] case final String target when target.isNotEmpty) {
      return target;
    }
    return null;
  }

  String? _agentName(String id) {
    for (final agent in snapshot.agents) {
      if (agent.id == id || agent.sessionId == id) return agent.name;
    }
    return null;
  }

  /// Work rows that open the Work sheet.
  Widget _workRows(String prefix, String label, List<WorkItem> items) =>
      KitRowGroup(
        margin: EdgeInsets.zero,
        label: label,
        children: [
          for (final item in items)
            KitRow(
              key: ValueKey('team-gate-$prefix-${item.id}'),
              leading: KitRow.icon(context, teamWorkMark(item.state).icon),
              title: item.title,
              titleMaxLines: 2,
              supporting: TextSpan(
                text: teamWorkStateWord(_copy(context), item.state),
              ),
              onTap: () => widget.onOpenWork(item.id),
            ),
        ],
      );

  List<Widget> _bead(AppLocalizations l10n, String? prompt) {
    final unblocks = [
      for (final item in snapshot.work)
        if (gate.workId != null &&
            item.id != gate.workId &&
            item.dependsOn.contains(gate.workId))
          item,
    ];
    return [
      if (prompt != null)
        KitMarkdown(
          prompt,
          key: const ValueKey('team-gate-description'),
          selectable: false,
        )
      else
        KitText(
          l10n.teamUiGateNoDescription,
          key: const ValueKey('team-gate-description-none'),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
      if (gate.kind == GateKind.gateBead)
        if (unblocks.isEmpty)
          KitText(
            l10n.teamUiGateUnblocksNone,
            key: const ValueKey('team-gate-unblocks-none'),
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          )
        else
          _workRows('unblocks', l10n.teamUiGateUnblocks, unblocks),
    ];
  }

  TeamFailureClass _failureClass() => teamClassifyFailure(_errorText());

  /// What went wrong in words and what to do, and the open work it holds
  /// up. Whether a retry can recover it shows as the retry button being
  /// there or not. The raw error is under Details.
  List<Widget> _runFailed(AppLocalizations l10n) {
    final cls = _failureClass();
    final affected = _affectedWork();
    return [
      KitRowGroup(
        margin: EdgeInsets.zero,
        label: l10n.teamUiGateFailureClassification,
        children: [
          KitRow(
            key: const ValueKey('team-gate-classification'),
            leading: KitRow.icon(context, AppIconography.error),
            title: teamFailureClassWord(l10n, cls),
            supporting: TextSpan(text: teamFailureAction(l10n, cls)),
            supportingKey: const ValueKey('team-gate-action'),
            supportingMaxLines: 4,
          ),
        ],
      ),
      if (affected.isEmpty)
        KitText(
          l10n.teamUiGateFailureAffectedNone,
          key: const ValueKey('team-gate-affected-none'),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        )
      else
        _workRows('affected', l10n.teamUiGateFailureAffectedWork, affected),
      if ((gate.prompt?.trim() ?? '').isEmpty)
        KitText(
          l10n.teamUiGateFailureErrorNone,
          key: const ValueKey('team-gate-error-none'),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
    ];
  }

  /// The error's message and code together, so a host that only sends a
  /// code (`timeout`) still classifies.
  String _errorText() {
    final parts = <String>[?gate.prompt];
    if (gate.raw['last_error'] case final Map<Object?, Object?> last) {
      for (final key in ['code', 'message', 'error']) {
        if (last[key] case final String value) parts.add(value);
      }
    } else if (gate.raw['last_error'] case final String last) {
      parts.add(last);
    }
    return parts.join(' ');
  }

  /// Request id, session id, provider kind, the ids it names, then every
  /// raw scalar the provider sent (KIT-33: each once, mono, copyable).
  List<KitTechnicalValue> _technical(AppLocalizations l10n) {
    final raw = gate.raw;
    final requestId = switch (raw['request_id']) {
      final String id when id.isNotEmpty => id,
      _ => gate.id,
    };
    final sessionId = switch (raw['session_id']) {
      final String id when id.isNotEmpty => id,
      _ => gate.agentId,
    };
    const shown = {
      'request_id',
      'session_id',
      'kind',
      'prompt',
      'title',
      'description',
      'options',
    };
    final scalars = <(String, String)>[];
    void collect(Map<Object?, Object?> map, String prefix) {
      for (final entry in map.entries) {
        final key = '${entry.key}';
        final value = entry.value;
        if (prefix.isEmpty && shown.contains(key)) continue;
        if (value is String || value is num || value is bool) {
          final text = '$value';
          if (text.trim().isEmpty || KitRedact.containsSecret(text)) continue;
          scalars.add(('$prefix$key', text));
        } else if (value is Map<Object?, Object?> && prefix.isEmpty) {
          collect(value, '$key.');
        }
      }
    }

    collect(raw, '');
    scalars.sort((a, b) => a.$1.compareTo(b.$1));
    return [
      KitTechnicalValue(
        l10n.teamUiGateLabelRequestId,
        requestId,
        key: const ValueKey('team-gate-request-id'),
      ),
      if (sessionId != null && sessionId.isNotEmpty)
        KitTechnicalValue(l10n.teamUiGateLabelSessionId, sessionId),
      if (gate.rawKind case final kind? when kind.isNotEmpty)
        KitTechnicalValue(l10n.teamUiGateLabelKind, kind),
      if (gate.workId case final work?)
        KitTechnicalValue(l10n.teamUiGateLabelWorkId, work),
      if (gate.runId case final run?)
        KitTechnicalValue(l10n.teamUiGateLabelRunId, run),
      for (final (key, value) in scalars) KitTechnicalValue(key, value),
    ];
  }
}
