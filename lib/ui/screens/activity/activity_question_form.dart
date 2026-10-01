part of '../activity_screen.dart';

/// Every prompt of one question: its choices (one or several), its own
/// answer where it takes one, and the actions. Send says why it cannot send
/// yet (STATE-8); a failed send stays here with the reason and Try again.
class _QuestionForm extends StatefulWidget {
  final PendingQuestion question;
  final ConnectionController controller;
  final PendingRequestIdentity request;
  final RequestRoutes routes;
  final VoidCallback? onOpenConversation;

  /// In a sheet, where Send is pinned: the form publishes its Send here
  /// instead of drawing it. Null in the wide detail pane, which draws it.
  final ValueNotifier<KitAction?>? pinnedSend;

  const _QuestionForm({
    required this.question,
    required this.controller,
    required this.request,
    required this.routes,
    this.onOpenConversation,
    this.pinnedSend,
  });

  @override
  State<_QuestionForm> createState() => _QuestionFormState();
}

class _QuestionFormState extends State<_QuestionForm> {
  late final List<Set<String>> _answers = List.generate(
    widget.question.prompts.length,
    (_) => <String>{},
  );
  late final List<TextEditingController> _custom = List.generate(
    widget.question.prompts.length,
    (_) => TextEditingController(),
  );
  bool _busy = false;
  bool _confirming = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
    // The sheet opened with a first Send; say the form's own at once.
    WidgetsBinding.instance.addPostFrameCallback((_) => _publish());
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  /// Every change of the form's state also updates the pinned Send, from
  /// the event that changed it (never from build).
  @override
  void setState(VoidCallback fn) {
    super.setState(fn);
    _publish();
  }

  void _publish() {
    final pinned = widget.pinnedSend;
    if (pinned == null || !mounted) return;
    pinned.value = _send(_l10n(context));
  }

  /// The one Send: pinned in the sheet, inline in the detail pane.
  KitAction _send(AppLocalizations l10n) {
    final reason = !_canAnswer(widget.controller)
        ? l10n.activitySendOffline
        : !_complete
        ? l10n.activityAnswerEveryQuestion
        : null;
    return sendAction(
      l10n,
      reason: reason,
      working: _busy && !_confirming,
      onSend: _busy ? null : _submit,
    );
  }

  bool get _complete {
    for (var i = 0; i < widget.question.prompts.length; i++) {
      if (_answers[i].isEmpty && _custom[i].text.trim().isEmpty) return false;
    }
    return true;
  }

  Future<void> _submit() async {
    if (!_complete || _busy || !widget.routes.isPending) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    final answers = <List<String>>[];
    for (var i = 0; i < _answers.length; i++) {
      final prompt = widget.question.prompts[i];
      final customAnswer = _custom[i].text.trim();
      if (prompt.multiple) {
        answers.add([
          ..._answers[i],
          if (customAnswer.isNotEmpty) customAnswer,
        ]);
      } else {
        answers.add([
          if (customAnswer.isNotEmpty)
            customAnswer
          else if (_answers[i].isNotEmpty)
            _answers[i].first,
        ]);
      }
    }
    try {
      await widget.controller.answerQuestion(
        widget.question.id,
        answers,
        expectedRequest: widget.request,
      );
      widget.routes.close();
    } catch (error) {
      if (mounted && widget.routes.isPending) {
        setState(() => _error = productErrorText(error));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// The same selection rules the inline chat card applies: a single-select
  /// choice replaces the choice and clears custom text; a multi-select
  /// change keeps custom text.
  void _choose(int index, String label) {
    setState(() {
      _custom[index].clear();
      _answers[index]
        ..clear()
        ..add(label);
    });
  }

  Future<void> _reject() async {
    if (_busy || !widget.routes.isPending) return;
    setState(() {
      _busy = true;
      _confirming = true;
      _error = null;
    });
    try {
      final confirmed = await showKitConfirm(
        context,
        title: _l10n(context).e7WorkspaceDismissRequest,
        body: _l10n(context).e7WorkspaceDismissDetail,
        confirmLabel: _l10n(context).workspaceDismissNotice,
        kind: KitConfirmKind.destructive,
        icon: AppIconography.blocked,
        routes: widget.routes,
      );
      if (!confirmed || !mounted || !widget.routes.isPending) return;
      setState(() => _confirming = false);
      await widget.controller.rejectQuestion(
        widget.question.id,
        expectedRequest: widget.request,
      );
      widget.routes.close();
    } catch (error) {
      if (mounted && widget.routes.isPending) {
        setState(() => _error = productErrorText(error));
      }
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
          _confirming = false;
        });
      }
    }
  }

  /// A Send for [reason]: disabled with it, else sending with [onSend].
  static KitAction sendAction(
    AppLocalizations l10n, {
    required String? reason,
    required bool working,
    required VoidCallback? onSend,
  }) => KitAction(
    key: const ValueKey('question-send'),
    label: l10n.e7WorkspaceSendAnswers,
    working: working,
    disabledReason: reason,
    onPressed: reason == null ? onSend : null,
  );

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    final prompts = widget.question.prompts;
    final pinned = widget.pinnedSend;
    final error = _error;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var index = 0; index < prompts.length; index++) ...[
          if (index > 0) SizedBox(height: tokens.sectionGap),
          if (prompts.length > 1)
            KitText(
              l10n.activityQuestionProgress(index + 1, prompts.length),
              role: KitTextRole.caption,
              tone: KitTextTone.secondary,
            ),
          KitText(prompts[index].title, role: KitTextRole.headline),
          SizedBox(height: tokens.space1),
          KitText(prompts[index].question),
          if (prompts[index].choices.isNotEmpty) ...[
            SizedBox(height: tokens.space3),
            _choices(index, prompts[index]),
          ],
          if (prompts[index].custom) ...[
            SizedBox(height: tokens.space3),
            KitField(
              key: ValueKey('question-own-answer-$index'),
              label: l10n.activityOwnAnswer,
              controller: _custom[index],
              enabled: !_busy,
              disabledReason: _busy ? l10n.activitySending : null,
              textInputAction: TextInputAction.done,
              onChanged: (value) => setState(() {
                if (!prompts[index].multiple && value.trim().isNotEmpty) {
                  _answers[index].clear();
                }
              }),
            ),
          ],
        ],
        if (error != null) ...[
          SizedBox(height: tokens.space4),
          KitNotice.error(
            key: const ValueKey('question-send-failed'),
            message: error,
            retry: KitAction(
              label: l10n.isolatedTaskRetryOpen,
              onPressed: _complete && !_busy ? _submit : null,
            ),
          ),
        ],
        SizedBox(height: tokens.space5),
        KitActionBlock(
          primary: pinned == null ? _send(l10n) : null,
          tertiary: [
            if (widget.onOpenConversation case final open?)
              KitAction(
                key: const ValueKey('question-open-conversation'),
                label: l10n.digestOpenConversation,
                onPressed: _busy ? null : open,
              ),
            KitAction(
              key: const ValueKey('question-dismiss'),
              label: l10n.workspaceDismissNotice,
              destructive: true,
              onPressed: _busy ? null : _reject,
            ),
          ],
        ),
      ],
    );
  }

  Widget _choices(int index, QuestionPrompt prompt) {
    final choices = [
      for (final choice in prompt.choices)
        KitChoice<String>(
          value: choice.label,
          title: choice.label,
          supporting: choice.description.isEmpty ? null : choice.description,
          enabled: !_busy,
          disabledReason: _busy ? _l10n(context).activitySending : null,
        ),
    ];
    if (prompt.multiple) {
      return KitChoiceList<String>.multi(
        key: ValueKey('question-choices-$index'),
        choices: choices,
        selected: _answers[index],
        semanticsLabel: prompt.title,
        onChanged: (values) => setState(
          () => _answers[index]
            ..clear()
            ..addAll(values),
        ),
      );
    }
    return KitChoiceList<String>.single(
      key: ValueKey('question-choices-$index'),
      choices: choices,
      selected: _answers[index].isEmpty ? null : _answers[index].first,
      actsOnTap: false,
      semanticsLabel: prompt.title,
      onSelected: (value) => _choose(index, value),
    );
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    for (final controller in _custom) {
      controller.dispose();
    }
    super.dispose();
  }
}

/// Whether an answer can leave the phone: a server gateway is there (it
/// stays through a stream reconnect). Without one, answering is disabled
/// with its reason rather than failing after the tap.
bool _canAnswer(ConnectionController controller) =>
    controller.repository != null;

String _sessionTitle(
  BuildContext context,
  ConnectionController controller,
  String id,
) {
  final session = controller.sessionsById[id];
  return presentedSessionTitle(
    session,
    fallback: _l10n(context).e7WorkspaceSessionId(id),
    l10n: _l10n(context),
  );
}

/// A scoped result, rather than an unqualified claim about every project.
/// Offline it says only what that means here; the fix is the connection
/// line's Reconnect, so [onRefresh] is null there and no second button
/// repeats it (R3).
class _ActivityStatus extends StatelessWidget {
  const _ActivityStatus({
    required this.known,
    required this.offline,
    required this.onRefresh,
  });
  final bool known;
  final bool offline;
  final VoidCallback? onRefresh;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final retry = onRefresh;
    return KitStateView(
      key: ValueKey(known ? 'activity-all-clear' : 'activity-status-unknown'),
      size: KitStateSize.inline,
      icon: known ? AppIconography.inbox : AppIconography.networkOff,
      // All caught up: the last sheet settles into the tray. Not known yet:
      // the unplugged drawing every load failure uses.
      illustration: known
          ? const StatesTrayScene()
          : const StatesUnpluggedScene(),
      title: known
          ? l10n.activityClearHere
          : offline
          ? l10n.activityOfflineRequests
          : l10n.activityStatusIncomplete,
      // The all-clear says what would fill this list, so an empty Inbox
      // reads as "watching" rather than "nothing here" (UX plan 5.8, item
      // 3). The unknown state keeps its own copy: teaching there would
      // claim a calm the app has not verified.
      body: known
          ? l10n.emptyTeachInboxMessage
          : offline
          ? null
          : l10n.activityUnknownStatusDetail,
      tertiary: [
        if (!known && retry != null)
          KitAction(
            key: const ValueKey('activity-check-again'),
            label: l10n.activityCheckAgain,
            icon: AppIconography.retry,
            onPressed: retry,
          ),
      ],
    );
  }
}

AppLocalizations _l10n(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(Localizations.localeOf(context));
