part of '../activity_screen.dart';

class _SessionRow extends StatelessWidget {
  final Session session;

  /// The connection is up: the row is live. Otherwise it is the last known
  /// state, said in words, with a still mark.
  final bool live;
  final int subagents;
  final String detail;
  final VoidCallback onTap;

  const _SessionRow({
    super.key,
    required this.session,
    required this.live,
    required this.subagents,
    required this.detail,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final title = presentedSessionTitle(
      session,
      fallback: l10n.globalSessionsUntitled,
      l10n: l10n,
    );
    final state = live
        ? l10n.globalSessionsWorking
        : l10n.activityLastSeenRunning;
    final line = [
      state,
      if (detail.isNotEmpty) detail,
      if (subagents > 0) l10n.e7WorkspaceSubagentCount(subagents),
    ].join(' · ');
    return KitRow(
      leading: KitStatusMark(
        state: live ? KitMarkState.working : KitMarkState.waiting,
        label: state,
      ),
      title: title,
      supporting: TextSpan(text: line),
      supportingKey: ValueKey('activity-running-${session.id}-line'),
      trailing: const KitChevron(),
      onTap: onTap,
    );
  }
}

/// A permission picked into the detail pane: the one answer card, with
/// Allow once and Reject in place and Details opening the full sheet
/// (the reject message, the diff, the persistent grant).
class _PermissionDetail extends StatefulWidget {
  const _PermissionDetail({
    super.key,
    required this.permission,
    required this.controller,
    required this.onOpenConversation,
  });

  final PermissionRequest permission;
  final ConnectionController controller;
  final VoidCallback onOpenConversation;

  @override
  State<_PermissionDetail> createState() => _PermissionDetailState();
}

class _PermissionDetailState extends State<_PermissionDetail> {
  DateTime? _since;
  String? _answer;
  String? _refused;

  Future<void> _reply(String reply, String words) async {
    if (_since != null) return;
    final controller = widget.controller;
    final request = controller.permissionIdentity(widget.permission);
    if (!controller.isRequestPending(request)) return;
    setState(() {
      _since = DateTime.now();
      _answer = words;
      _refused = null;
    });
    try {
      await controller.answerPermission(
        widget.permission.id,
        reply,
        expectedRequest: request,
      );
    } catch (error) {
      if (mounted) {
        setState(() {
          _since = null;
          _refused = productErrorText(error);
        });
      }
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
    final since = _since;
    final refused = _refused;
    final connected = _canAnswer(controller);
    return ListView(
      padding: KitScreen.padding(context),
      children: [
        SizedBox(height: KitTokens.of(context).space4),
        KitRequestCard.ask(
          kind: KitRequestKind.permission,
          title: title,
          who: _sessionTitle(context, controller, permission.sessionID),
          reason: KitNeedsYouReason.decision,
          ifIgnored: l10n.activityIfIgnored,
          announcement: l10n.activityPermissionAnnouncement(title),
          summary: permission.patterns.isEmpty
              ? null
              : permission.patterns.join('\n'),
          phase: since == null
              ? KitRequestPhase.waiting
              : KitRequestPhase.sending,
          answer: _answer,
          receipt: since != null
              ? KitReceipt(state: KitReceiptState.sending, since: since)
              : refused != null
              ? KitReceipt(state: KitReceiptState.refused, reason: refused)
              : null,
          answers: KitRequestDecide(
            allowKey: const ValueKey('activity-detail-allow'),
            rejectKey: const ValueKey('activity-detail-reject'),
            disabledReason: connected ? null : l10n.activitySendOffline,
            onAllow: connected
                ? () => _reply('once', l10n.chatUiAllowOnce)
                : null,
            onReject: connected
                ? () => _reply('reject', l10n.chatUiReject)
                : null,
          ),
          onDetails: () =>
              _openPermissionSheet(context, controller, permission),
          tertiary: [
            KitAction(
              key: const ValueKey('activity-detail-open-conversation'),
              label: l10n.digestOpenConversation,
              onPressed: widget.onOpenConversation,
            ),
          ],
        ),
      ],
    );
  }
}

/// A form picked into the detail pane: the answer card; Answer opens the
/// form flow, the one resolver for OpenCode 2 forms.
class _FormDetail extends StatelessWidget {
  const _FormDetail({super.key, required this.form, required this.controller});

  final Api2FormInfo form;
  final ConnectionController controller;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final title = form.title ?? l10n.e7WorkspaceInputRequested;
    return ListView(
      padding: KitScreen.padding(context),
      children: [
        SizedBox(height: KitTokens.of(context).space4),
        KitRequestCard.ask(
          kind: KitRequestKind.form,
          title: title,
          who: form.sessionID == 'global'
              ? l10n.e7WorkspaceMcpAsked
              : _sessionTitle(context, controller, form.sessionID),
          reason: KitNeedsYouReason.decision,
          ifIgnored: l10n.activityIfIgnored,
          announcement: l10n.activityFormAnnouncement(title),
          detail: l10n.e7WorkspaceQuestionCount(
            form.fields.length,
            _sessionTitle(context, controller, form.sessionID),
          ),
          answers: const KitRequestInSheet(
            key: ValueKey('activity-detail-form-answer'),
          ),
          onDetails: () => presentConnectionForm(context, controller, form),
        ),
      ],
    );
  }
}

/// A question picked into the detail pane: the same form the sheet holds,
/// under the sheet's header words.
class _QuestionDetail extends StatefulWidget {
  const _QuestionDetail({
    super.key,
    required this.question,
    required this.controller,
    required this.onOpenConversation,
  });

  final PendingQuestion question;
  final ConnectionController controller;
  final VoidCallback onOpenConversation;

  @override
  State<_QuestionDetail> createState() => _QuestionDetailState();
}

class _QuestionDetailState extends State<_QuestionDetail> {
  late final PendingRequestIdentity _request = widget.controller
      .questionIdentity(widget.question);

  /// Owns no route: the pane closes by the question leaving the list.
  late final RequestRoutes _routes = RequestRoutes(
    changes: widget.controller,
    isPending: () => widget.controller.isRequestPending(_request),
  );

  @override
  void dispose() {
    _routes.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    return ListView(
      padding: KitScreen.padding(context),
      children: [
        SizedBox(height: tokens.space4),
        KitText(l10n.e7WorkspaceNeedsInput, role: KitTextRole.title),
        SizedBox(height: tokens.space1),
        KitText(
          _sessionTitle(context, widget.controller, widget.question.sessionID),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
        SizedBox(height: tokens.space5),
        _QuestionForm(
          question: widget.question,
          controller: widget.controller,
          request: _request,
          routes: _routes,
          onOpenConversation: widget.onOpenConversation,
        ),
      ],
    );
  }
}
