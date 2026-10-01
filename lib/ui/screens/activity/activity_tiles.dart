part of '../activity_screen.dart';

/// Opens the permission sheet with the conversation named, the one resolver
/// every door shares.
void _openPermissionSheet(
  BuildContext context,
  ConnectionController controller,
  PermissionRequest permission,
) => unawaited(
  showPermissionSheet(
    context,
    permission: permission,
    controller: controller,
    contextLabel: _l10n(context).e7WorkspaceRequestFor(
      _sessionTitle(context, controller, permission.sessionID),
    ),
  ),
);

/// One automatic act in While you were away, a row like a finished one:
/// the done mark, the thing it was done to as the title, and the
/// KitAutoLine's words as the supporting line ("Reconnected by itself ·
/// 12m ago"). Undo only where the act has a real inverse. A tap opens that
/// thing's page; Dismiss is a swipe and its menu twin.
class _AutomaticActRow extends StatelessWidget {
  const _AutomaticActRow({
    super.key,
    required this.act,
    required this.title,
    required this.words,
    required this.dismiss,
    required this.now,
    this.undoResult,
    this.onOpen,
    this.onUndo,
  });

  final AutomaticAct act;
  final String title;
  final String words;
  final KitSwipeAction dismiss;
  final DateTime now;
  final AutomaticUndoResult? undoResult;
  final VoidCallback? onOpen;
  final VoidCallback? onUndo;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final undone = act.undone || undoResult == AutomaticUndoResult.undone;
    // An Undo that went out and was never confirmed is never retried and
    // never reads as done (STATE-10).
    final line = undone
        ? l10n.whileAwayActUndone(words)
        : switch (undoResult) {
            AutomaticUndoResult.failed => l10n.whileAwayUndoFailed(words),
            AutomaticUndoResult.unconfirmed => l10n.whileAwayUndoUnconfirmed(
              words,
            ),
            _ when act.undoAttempted => l10n.whileAwayUndoUnconfirmed(words),
            _ => words,
          };
    final undo = undone ? null : onUndo;
    return KitRow(
      leading: KitStatusMark(
        state: KitMarkState.done,
        label: l10n.whileAwayMark,
      ),
      title: title,
      supporting: TextSpan(
        children: [
          KitReceipt.span(context, KitReceiptState.confirmed, label: line),
          TextSpan(
            text: relativeTimeLabel(
              act.occurredAt.millisecondsSinceEpoch,
              now: now,
              l10n: l10n,
            ),
          ),
        ],
      ),
      supportingMaxLines: 2,
      onTap: onOpen,
      swipe: dismiss,
      trailing: undo == null
          ? null
          : KitButton.tertiary(
              key: ValueKey('activity-auto-undo-${act.id}'),
              label: l10n.kitUndoAction,
              onPressed: undo,
            ),
    );
  }
}

/// A section of the list: the VL gap above each group (LAY-7).
class _Section extends StatelessWidget {
  const _Section({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsetsDirectional.only(top: KitTokens.of(context).sectionGap),
    child: child,
  );
}

/// Keyed rows that come and go gently ([KitAnimatedRows]) with the panel's
/// hairline between them, inset to where the words start.
class _DividedRows extends StatelessWidget {
  const _DividedRows({super.key, required this.rows});

  final List<Widget> rows;

  @override
  Widget build(BuildContext context) => KitAnimatedRows(
    children: [
      for (var i = 0; i < rows.length; i++)
        KeyedSubtree(
          key: ValueKey(('activity-row', rows[i].key)),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (i > 0) const KitDivider(inset: KitDividerInset.text),
              rows[i],
            ],
          ),
        ),
    ],
  );
}

/// One permission component, three entry points: this row opens the same
/// sheet the chat auto-presents, so resolving here is resolving there. Its
/// trailing action allows it once without the sheet (the common case), and
/// says so if the answer did not go through.
class ActivityPermissionTile extends StatefulWidget {
  final PermissionRequest permission;
  final ConnectionController controller;

  /// What a tap does; null opens the permission sheet.
  final VoidCallback? onOpen;

  /// The row shown in the detail pane (expanded windows).
  final bool selected;

  const ActivityPermissionTile({
    super.key,
    required this.permission,
    required this.controller,
    this.onOpen,
    this.selected = false,
  });

  @override
  State<ActivityPermissionTile> createState() => _ActivityPermissionTileState();
}

class _ActivityPermissionTileState extends State<ActivityPermissionTile> {
  bool _sending = false;
  String? _error;

  Future<void> _allowOnce() async {
    if (_sending) return;
    final controller = widget.controller;
    final request = controller.permissionIdentity(widget.permission);
    if (!controller.isRequestPending(request)) return;
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      await controller.answerPermission(
        widget.permission.id,
        'once',
        expectedRequest: request,
      );
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final permission = widget.permission;
    final controller = widget.controller;
    final title = permission.permission.isEmpty
        ? l10n.e7WorkspacePermissionRequired
        : permissionRequestTitle(permission.permission);
    final error = _error;
    final connected = _canAnswer(controller);
    return KitRow(
      leading: KitNeedsYou.mark(),
      title: title,
      selected: widget.selected,
      supporting: error != null
          ? TextSpan(text: l10n.activityAllowOnceFailed(error))
          : TextSpan(
              children: [
                KitNeedsYou.span(context),
                TextSpan(
                  text: permission.patterns.isNotEmpty
                      ? permission.patterns.first
                      : _sessionTitle(
                          context,
                          controller,
                          permission.sessionID,
                        ),
                ),
              ],
            ),
      supportingMaxLines: error != null ? 2 : 1,
      trailing: KitIconButton(
        key: ValueKey('activity-permission-allow-${permission.id}'),
        icon: AppIconography.check,
        tooltip: l10n.chatUiAllowOnce,
        working: _sending,
        disabledReason: connected ? null : l10n.activitySendOffline,
        onPressed: connected ? _allowOnce : null,
      ),
      onTap:
          widget.onOpen ??
          () => _openPermissionSheet(context, controller, permission),
    );
  }
}

class ActivityQuestionTile extends StatelessWidget {
  final PendingQuestion question;
  final ConnectionController controller;

  /// What a tap does; null opens the question sheet.
  final VoidCallback? onOpen;

  /// The row shown in the detail pane (expanded windows).
  final bool selected;

  const ActivityQuestionTile({
    super.key,
    required this.question,
    required this.controller,
    this.onOpen,
    this.selected = false,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    return KitRow(
      leading: KitNeedsYou.mark(),
      title: question.prompts.isEmpty
          ? l10n.e7WorkspaceAssistantQuestion
          : question.prompts.first.title,
      supporting: TextSpan(
        children: [
          KitNeedsYou.span(context),
          TextSpan(
            text: question.prompts.isEmpty
                ? _sessionTitle(context, controller, question.sessionID)
                : question.prompts.first.question,
          ),
        ],
      ),
      supportingMaxLines: 2,
      selected: selected,
      trailing: const KitChevron(),
      onTap: onOpen ?? () => showQuestionSheet(context, controller, question),
    );
  }
}

class ActivityFormTile extends StatelessWidget {
  final Api2FormInfo form;
  final ConnectionController controller;

  /// What a tap does; null opens the form.
  final VoidCallback? onOpen;

  /// The row shown in the detail pane (expanded windows).
  final bool selected;

  const ActivityFormTile({
    super.key,
    required this.form,
    required this.controller,
    this.onOpen,
    this.selected = false,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final count = form.fields.length;
    return KitRow(
      key: ValueKey('form-request-tile-${form.id}'),
      leading: KitNeedsYou.mark(),
      title: form.title ?? l10n.e7WorkspaceInputRequested,
      supporting: TextSpan(
        children: [
          KitNeedsYou.span(context),
          TextSpan(
            text: form.sessionID == 'global'
                ? l10n.e7WorkspaceMcpAsked
                : l10n.e7WorkspaceQuestionCount(
                    count,
                    _sessionTitle(context, controller, form.sessionID),
                  ),
          ),
        ],
      ),
      supportingMaxLines: 2,
      selected: selected,
      trailing: const KitChevron(),
      onTap: onOpen ?? () => presentConnectionForm(context, controller, form),
    );
  }
}

/// One AI Team row of the inbox with its §47 rank and time, for the merge
/// with the app's own rows.
class _TeamRow {
  const _TeamRow({required this.rank, required this.at, required this.widget});

  final int rank;
  final DateTime? at;
  final Widget widget;
}

/// A gate of the connected server's AI Team: kind glyph, one-line title,
/// the kind, what it belongs to, the server and its age, and — once
/// answered from here — the receipt chip ("Sent", "Unconfirmed", "Not
/// accepted"). Opens the Gate sheet (02-ux §6), where the answer and the
/// retry live.
class ActivityGateTile extends StatelessWidget {
  const ActivityGateTile({
    super.key,
    required this.gate,
    required this.team,
    required this.serverName,
    required this.now,
  });

  final OrchestrationGate gate;
  final OrchestrationController team;
  final String? serverName;
  final DateTime now;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final age = gate.createdAt == null
        ? null
        : relativeTimeLabel(
            gate.createdAt!.millisecondsSinceEpoch,
            now: now,
            l10n: l10n,
          );
    final record = teamGateMutation(team, gate);
    void open() {
      if (team.capabilities.projectLifecycle &&
          team.projectController != null) {
        unawaited(
          openTeamProjectDestination(context, team, requestId: gate.id),
        );
      } else {
        showGateSheet(context, team, gate.id, now: () => now);
      }
    }

    return KitRow(
      leading: KitNeedsYou.mark(),
      title: gate.title,
      // "Needs you · Question · Not confirmed yet · …": the answer's
      // receipt as a word while the host has not confirmed it; the row
      // opens the Gate sheet, where Try again lives.
      supporting: TextSpan(
        children: [
          KitNeedsYou.span(context),
          teamGateRowLine(context, [
            teamGateKindWord(l10n, gate.kind),
            ?teamGateLink(l10n, team.snapshot, gate),
            ?serverName,
            ?age,
          ], record: record),
        ],
      ),
      supportingMaxLines: 2,
      supportingKey: ValueKey('activity-team-gate-${gate.id}-line'),
      trailing: const KitChevron(),
      onTap: open,
    );
  }
}

/// A blocked agent of the connected server's AI Team (BRD §47 rank 5):
/// its name, what it works on, the server and its age. Opens the agent.
class ActivityAgentBlockedTile extends StatelessWidget {
  const ActivityAgentBlockedTile({
    super.key,
    required this.agent,
    required this.team,
    required this.serverName,
    required this.now,
  });

  final OrchestrationAgent agent;
  final OrchestrationController team;
  final String? serverName;
  final DateTime now;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    String? work;
    for (final item in team.snapshot.work) {
      if (item.id == agent.currentWorkId) {
        work = l10n.teamUiHomeGateLinkWork(item.title);
        break;
      }
    }
    final age = agent.lastActivity == null
        ? null
        : relativeTimeLabel(
            agent.lastActivity!.millisecondsSinceEpoch,
            now: now,
            l10n: l10n,
          );
    final subtitle = [
      l10n.teamUiGateKindAgentBlocked,
      ?work,
      ?serverName,
      ?age,
    ].join(' · ');
    return KitRow(
      leading: KitNeedsYou.mark(),
      title: agent.name,
      supporting: TextSpan(
        children: [
          KitNeedsYou.span(context),
          TextSpan(text: subtitle),
        ],
      ),
      supportingMaxLines: 2,
      trailing: const KitChevron(),
      onTap: () => pushKitPage<void>(
        context,
        (_) => AgentScreen(controller: team, agentId: agent.id, now: () => now),
      ),
    );
  }
}

/// The exact answer surface, shared by Inbox rows, the conversation and
/// notification taps: the kit sheet with each prompt's choices, an own
/// answer where the prompt takes one, Send with its reason while it cannot
/// send, and Dismiss (confirmed first: nobody can restore a dismissed
/// question, DATA-11). [onOpenConversation], when given, adds "Open
/// conversation" for context before answering.
Future<void> showQuestionSheet(
  BuildContext context,
  ConnectionController controller,
  PendingQuestion question, {
  VoidCallback? onOpenConversation,
}) async {
  final request = controller.questionIdentity(question);
  if (!controller.isRequestPending(request)) return;
  final routes = RequestRoutes(
    changes: controller,
    isPending: () => controller.isRequestPending(request),
  );
  final l10n = _l10n(context);
  // Send is pinned to the sheet's foot, above the keyboard, and enables as
  // the person answers: the form publishes it here (slice-P3.11a).
  final send = ValueNotifier<KitAction?>(
    _QuestionFormState.sendAction(
      l10n,
      reason: _canAnswer(controller)
          ? (question.prompts.isEmpty ? null : l10n.activityAnswerEveryQuestion)
          : l10n.activitySendOffline,
      working: false,
      onSend: null,
    ),
  );
  try {
    await showKitSheet<void>(
      context,
      title: l10n.e7WorkspaceNeedsInput,
      subtitle: _sessionTitle(context, controller, question.sessionID),
      icon: AppIconography.question,
      routes: routes,
      sheetKey: const ValueKey('question-sheet'),
      primaryListenable: send,
      body: (sheetContext) => _QuestionForm(
        question: question,
        controller: controller,
        request: request,
        routes: routes,
        pinnedSend: send,
        onOpenConversation: onOpenConversation == null
            ? null
            : () {
                Navigator.of(sheetContext).pop();
                onOpenConversation();
              },
      ),
    );
  } finally {
    routes.close();
    // Not disposed: the form may still publish while the sheet animates
    // out, and a notifier with no listeners holds nothing.
  }
}
