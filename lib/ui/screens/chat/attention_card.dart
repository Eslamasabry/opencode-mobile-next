part of '../chat_screen.dart';

// What the agent asks for, above the composer: one KitRequestCard per
// request (K2 §2.1, P4.1b). A permission, an OpenCode 1 question and an
// OpenCode 2 form share the one card: who asks and why, the common answer
// in place, one Details that opens the one request sheet, and a receipt
// once answered (Sending, Not confirmed yet, Not accepted). A request
// arriving mid-sentence never takes focus from the composer. One card shows
// at a time, oldest first; the card says how many more wait.
//
// Transcript turn model (STATE-16): a card is the turn's one control while
// the agent waits; the work line above it says so.

/// Who asks, in the card's caption: "The agent".
///
/// In a chat with an agent backend (Claude Code, Pi, ...) it names that
/// agent, as agent cards do.
String _requestWho(BuildContext context, {String? agentLabel}) {
  final name =
      agentLabel ??
      context.findAncestorStateOfType<_ChatScreenState>()?._agentName;
  return name == null || name.trim().isEmpty
      ? _chatL10n(context).chatRequestWho
      : name;
}

/// Who asks for a list row's request: its agent, and OpenCode for an
/// OpenCode row (as the row and its permission requests say); null outside
/// the list.
String? _feedAgent(ChatFeedItem? item) =>
    item == null ? null : item.agentLabel ?? 'OpenCode';

/// The chat screen's controller for a card inside it; null outside a chat
/// (a gallery), where the card cannot answer and says why.
ConnectionController? _requestConnection(BuildContext context) =>
    context.findAncestorStateOfType<_ChatScreenState>()?._conn;

/// "The agent waits until you answer. Nothing is lost." plus how many other
/// requests of this conversation wait behind this one.
String _requestIfIgnored(BuildContext context, int others) {
  final l10n = _chatL10n(context);
  return others <= 0
      ? l10n.chatRequestIfIgnored
      : '${l10n.chatRequestIfIgnored} ${l10n.chatRequestMoreWaiting(others)}';
}

/// Keeps a request card to [KitTokens.requestMaxHeightShare] of the window,
/// so the transcript and the composer keep their room in a short window
/// (landscape, the keyboard up); past that the card scrolls in its slot.
class _RequestSlot extends StatelessWidget {
  const _RequestSlot({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => ConstrainedBox(
    constraints: BoxConstraints(
      maxHeight:
          MediaQuery.sizeOf(context).height * KitTokens.requestMaxHeightShare,
    ),
    child: ListView(
      shrinkWrap: true,
      padding: EdgeInsets.zero,
      children: [child],
    ),
  );
}

/// A permission request: Allow once and Reject in place (one tap for the
/// common answer), Details for the whole command, the change, a note with
/// Reject and "Always allow".
class _PermissionAttentionCard extends StatefulWidget {
  const _PermissionAttentionCard({
    super.key,
    required this.permission,
    required this.onReview,
    this.autoApprovalFailed = false,
  });

  final PermissionRequest permission;

  /// Details: the request sheet.
  final VoidCallback onReview;

  /// The session approves automatically but this request's reply failed,
  /// so the card says why it is asking after all.
  final bool autoApprovalFailed;

  @override
  State<_PermissionAttentionCard> createState() =>
      _PermissionAttentionCardState();
}

class _PermissionAttentionCardState extends State<_PermissionAttentionCard> {
  late final DateTime _seen = requestSeenNow();

  @override
  Widget build(BuildContext context) => _RequestSlot(child: _content(context));

  Widget _content(BuildContext context) {
    final l10n = _chatL10n(context);
    final conn = _requestConnection(context);
    final permission = widget.permission;
    final detail = widget.autoApprovalFailed
        ? l10n.approvalsUiFailedDetail
        : null;
    if (conn == null) {
      return permissionRequestCard(
        context,
        permission: permission,
        who: _requestWho(context),
        since: _seen,
        detail: detail,
        onAllow: null,
        onReject: null,
        disabledReason: l10n.chatRequestNoConnection,
        onDetails: widget.onReview,
        detailsKey: const Key('permission-card-review'),
      );
    }
    final answers = PermissionAnswers.of(conn);
    final delayed = conn.delayedAnswers;
    return ListenableBuilder(
      listenable: Listenable.merge([answers, delayed]),
      builder: (context, _) {
        final answered = answers.answerFor(permission.id);
        final held = delayed.isHeld(permission.id);
        void answer(String reply, {String? message}) => unawaited(
          answerPermissionRequest(conn, permission, reply, message: message),
        );
        return permissionRequestCard(
          context,
          permission: permission,
          who: _requestWho(context),
          since: _seen,
          detail: detail,
          ifIgnored: _requestIfIgnored(
            context,
            conn.permissionsForSession(permission.sessionID).length - 1,
          ),
          answered: answered,
          alwaysAllow: permissionAlwaysStep(
            context,
            permission: permission,
            supported:
                conn.capabilities.persistentPermissionGrants &&
                permission.canAlwaysAllow,
            onConfirmed: () => answer('always'),
          ),
          onAllow: () => answer('once'),
          onReject: () => answer('reject'),
          heldLabel: held ? delayed.heldLabel(permission.id) : null,
          onUndo: held ? () => delayed.undo(permission.id) : null,
          onRetry: answered == null
              ? null
              : () => answer(answered.reply, message: answered.message),
          onDetails: widget.onReview,
          detailsKey: const Key('permission-card-review'),
        );
      },
    );
  }
}

/// An OpenCode 1 question, on the same card as an OpenCode 2 form: the
/// question's header as the title and its words under it.
///
/// - One prompt with one answer: the options in place; a tap sends at once
///   and the chosen option carries the receipt. "Something else" opens a
///   field whose text is kept as a draft of this request until it lands.
/// - One prompt with several answers: "Answer" opens the request sheet with
///   every option and Send.
/// - Anything longer (two or more prompts, long options, several answers
///   plus a typed one): "Answer" opens the full question sheet, as a form's
///   Answer opens the form.
class _QuestionAttentionCard extends StatefulWidget {
  const _QuestionAttentionCard({
    super.key,
    required this.question,
    required this.replying,
    required this.onAnswer,
    required this.onMore,
    this.connection,
    this.feedItem,
    this.inList = false,
  });

  /// The Conversations row this card answers for; its answers go through
  /// the main controller's feed route, whoever owns the conversation.
  final ChatFeedItem? feedItem;

  final PendingQuestion question;

  /// The connection that holds the question, when the card sits outside a
  /// chat screen (under a Conversations row).
  final ConnectionController? connection;

  /// Under a list row: secondary buttons, no request slot.
  final bool inList;

  /// The screen's own answer in flight (a sheet answered it); the card's
  /// own answers carry their receipt instead.
  final bool replying;

  /// Used only outside a chat screen, where the card has no connection.
  final ValueChanged<List<List<String>>> onAnswer;

  /// The full question sheet.
  final VoidCallback onMore;

  @override
  State<_QuestionAttentionCard> createState() => _QuestionAttentionCardState();
}

class _QuestionAttentionCardState extends State<_QuestionAttentionCard> {
  late final DateTime _seen = requestSeenNow();
  final TextEditingController _other = TextEditingController();

  /// The answer in words and when it left; null while nothing is in flight.
  String? _answer;
  DateTime? _since;
  String? _refused;

  /// The question object and request this card was built for (list rows
  /// only), captured once: kept across Undo and never looked up again at
  /// send time.
  PendingQuestion? _shownQuestion;
  PendingRequestIdentity? _shownRequest;

  /// Undo and draft identity: two sources reusing a request id never share.
  late final String _key = widget.feedItem == null
      ? widget.question.id
      : '${widget.feedItem!.identity}|${widget.question.id}';

  @override
  void initState() {
    super.initState();
    final owner = widget.connection;
    final item = widget.feedItem;
    if (widget.inList && owner != null && item != null) {
      _shownQuestion = widget.question;
      _shownRequest = owner.questionIdentityForFeedItem(item, widget.question);
    }
  }

  /// A long question (a plan to approve) in a list row: its first line only;
  /// the sheet has the whole text.
  String _listLine(String text) {
    if (!widget.inList) return text;
    final line = text
        .split('\n')
        .map((part) => part.trim())
        .firstWhere((part) => part.isNotEmpty, orElse: () => '');
    return line.isEmpty ? text : line;
  }

  List<QuestionPrompt> get _prompts => widget.question.prompts;

  ConnectionController? _conn(BuildContext context) =>
      widget.connection ?? _requestConnection(context);

  Widget _slot(Widget child) =>
      widget.inList ? child : _RequestSlot(child: child);

  @override
  void dispose() {
    _other.dispose();
    super.dispose();
  }

  KitDraft? _draft(ConnectionController? conn) {
    final profileId = conn?.profile?.id ?? conn?.store.activeId ?? '';
    if (profileId.isEmpty) return null;
    return KitDraft(
      target: 'request.$_key',
      profileId: profileId,
      controller: _other,
    );
  }

  /// Holds the answer for the undo window ([DelayedAnswers]); the card shows
  /// it collapsed with Undo, then [_sendNow] sends it. A second answer while
  /// one is held is ignored (Undo first).
  Future<void> _send(
    ConnectionController? conn,
    List<List<String>> answers,
    String words,
  ) async {
    if (_answer != null) return;
    if (conn == null) {
      widget.onAnswer(answers);
      return;
    }
    final delayed = conn.delayedAnswers;
    final id = _key;
    if (delayed.isHeld(id)) return;
    delayed.hold(id, label: words, send: () => _sendNow(conn, answers, words));
  }

  Future<void> _sendNow(
    ConnectionController conn,
    List<List<String>> answers,
    String words,
  ) async {
    final request = _shownRequest;
    if (request != null
        ? !conn.isRequestPending(request)
        : !conn.questions.containsKey(widget.question.id)) {
      return;
    }
    if (mounted) {
      setState(() {
        _answer = words;
        _since = requestSeenNow();
        _refused = null;
      });
    }
    try {
      // Under a list row the answer names the request it was shown for, so
      // a newer question of the same id is never answered by mistake.
      final item = widget.feedItem;
      if (item != null && request != null) {
        await conn.answerQuestionForFeedItem(
          item,
          answers,
          expectedRequest: request,
        );
      } else {
        await conn.answerQuestion(widget.question.id, answers);
      }
      unawaited(_draft(conn)?.clear());
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _answer = null;
        _since = null;
        _refused = productErrorText(error);
      });
    }
  }

  KitRequestCard _card(
    BuildContext context, {
    required KitRequestAnswers answers,
    VoidCallback? onDetails,
  }) {
    final l10n = _chatL10n(context);
    final first = _prompts.firstOrNull;
    final title = first?.title.trim().isNotEmpty == true
        ? first!.title.trim()
        : l10n.chatUiOpenCodeNeedsInput;
    final count = _prompts.length;
    final delayed = _conn(context)?.delayedAnswers;
    final heldLabel = delayed?.heldLabel(_key);
    final held = delayed != null && heldLabel != null;
    final sending = !held && _answer != null;
    return KitRequestCard.ask(
      kind: KitRequestKind.question,
      title: title,
      who: _requestWho(context, agentLabel: _feedAgent(widget.feedItem)),
      reason: KitNeedsYouReason.decision,
      ifIgnored: _requestIfIgnored(context, 0),
      announcement: l10n.chatUiQuestionLabel(title),
      since: _seen,
      detailMarkdown:
          !widget.inList && count == 1 && (first?.markdown ?? false),
      detail: first == null || first.question.trim().isEmpty
          ? null
          : count > 1
          ? l10n.chatUiQuestionsSummary(_listLine(first.question), count)
          : _listLine(first.question),
      phase: held
          ? KitRequestPhase.answered
          : sending
          ? KitRequestPhase.sending
          : KitRequestPhase.waiting,
      answer: held ? heldLabel : _answer,
      receipt: held
          ? KitReceipt(
              state: KitReceiptState.confirmed,
              label: heldLabel,
              onUndo: () => delayed.undo(_key),
              undoKey: const Key('question-card-undo'),
            )
          : sending
          ? KitReceipt(state: KitReceiptState.sending, since: _since)
          : _refused == null
          ? null
          : KitReceipt(state: KitReceiptState.refused, reason: _refused),
      answers: answers,
      onDetails: onDetails,
      detailsKey: const Key('question-card-more'),
      inList: widget.inList,
    );
  }

  @override
  Widget build(BuildContext context) {
    final conn = _conn(context);
    if (conn == null) return _slot(_content(context));
    return ListenableBuilder(
      listenable: conn.delayedAnswers,
      builder: (context, _) => _slot(_content(context)),
    );
  }

  Widget _content(BuildContext context) {
    final l10n = _chatL10n(context);
    final conn = _conn(context);
    final first = _prompts.firstOrNull;
    final single =
        first != null &&
        _prompts.length == 1 &&
        !questionPrefersSheet(widget.question);

    if (single && !first.multiple) {
      final choices = [
        for (final (index, choice) in first.choices.indexed)
          KitChoice<String>(
            key: ValueKey('question-card-option-$index'),
            value: choice.label,
            title: choice.label,
            supporting: choice.description.trim().isEmpty
                ? null
                : choice.description,
          ),
      ];
      late final KitRequestCard card;
      final answers = KitRequestChoose<String>(
        choices: choices,
        chosen: _answer,
        onChosen: (label) => unawaited(
          _send(conn, [
            [label],
          ], label),
        ),
        other: first.custom
            ? KitChoiceOther(
                label: l10n.chatRequestOtherAnswer,
                fieldLabel: l10n.chatRequestOtherField,
                draft: _draft(conn),
                fieldKey: const Key('question-card-custom-0'),
                onSubmitted: (text) => unawaited(
                  _send(conn, [
                    [text],
                  ], text),
                ),
              )
            : null,
      );
      card = _card(
        context,
        answers: answers,
        onDetails: conn == null
            ? null
            : () => unawaited(
                showQuestionDetails(
                  context,
                  controller: conn,
                  question: _shownQuestion ?? widget.question,
                  card: card,
                  request: _shownRequest,
                ),
              ),
      );
      return card;
    }

    if (single && !first.custom && conn != null) {
      late final KitRequestCard card;
      card = _card(
        context,
        answers: KitRequestChooseMany<String>(
          choices: [
            for (final choice in first.choices)
              KitChoice<String>(
                value: choice.label,
                title: choice.label,
                supporting: choice.description.trim().isEmpty
                    ? null
                    : choice.description,
              ),
          ],
          secondary: widget.inList,
          onSend: (chosen) =>
              unawaited(_send(conn, [chosen.toList()], chosen.join(', '))),
        ),
        onDetails: () => unawaited(
          showQuestionDetails(
            context,
            controller: conn,
            question: _shownQuestion ?? widget.question,
            card: card,
            request: _shownRequest,
          ),
        ),
      );
      return card;
    }

    return _card(
      context,
      answers: KitRequestInSheet(
        key: const Key('question-card-answer'),
        secondary: widget.inList,
      ),
      onDetails: _shownQuestion == null || conn == null
          ? widget.onMore
          : () => unawaited(
              showQuestionSheet(
                context,
                conn,
                _shownQuestion!,
                agentLabel:
                    _feedAgent(widget.feedItem) ??
                    context
                        .findAncestorStateOfType<_ChatScreenState>()
                        ?._agentName,
              ),
            ),
    );
  }
}

/// A waiting question as the card under its Conversations row: the same
/// card as in the chat, answered through [owner] (the connection that holds
/// it) with its Undo window, secondary buttons (the list keeps its own one
/// primary), and "Answer" opening the full question sheet over the list.
Widget questionRequestCard(
  BuildContext context, {
  Key? key,
  required ConnectionController owner,
  required ChatFeedItem item,
  required PendingQuestion question,
  required bool inList,
}) => _QuestionAttentionCard(
  key: key,
  question: question,
  replying: false,
  connection: owner,
  feedItem: item,
  inList: inList,
  onAnswer: (_) {},
  onMore: () => unawaited(
    showQuestionSheet(context, owner, question, agentLabel: _feedAgent(item)),
  ),
);

/// "Rate limited. Retrying 2 in 0:42" — the server sends no attempt ceiling,
/// so the banner names the attempt rather than inventing a total. [now]
/// defaults to the wall clock; tests pass a fixed instant.
@visibleForTesting
String retryBannerHeadline(
  SessionRetryState retry, {
  DateTime? now,
  AppLocalizations? l10n,
}) {
  final strings = l10n ?? lookupAppLocalizations(const Locale('en'));
  final attempt = retry.attempt > 0 ? ' ${retry.attempt}' : '';
  // Servers retry for many reasons (a dropped connection, an overloaded
  // provider). Only call it a rate limit when it is one, or when the server
  // gave no reason; otherwise the cause is named on the line below.
  final cause = classifyAgentError(retry.message ?? '');
  final rateLimit = cause == null || cause == AgentErrorCause.rateLimited;
  final next = retry.next;
  if (next == null) {
    return rateLimit
        ? strings.chatUiRateLimitRetry(attempt)
        : strings.chatUiRetryingSoon(attempt);
  }
  final delta = next.difference(now ?? DateTime.now());
  final remaining = delta.isNegative ? Duration.zero : delta;
  return rateLimit
      ? strings.chatUiRateLimitCountdown(attempt, _countdown(remaining))
      : strings.chatUiRetryingCountdown(attempt, _countdown(remaining));
}

String _countdown(Duration d) {
  final total = d.inSeconds;
  final minutes = total ~/ 60;
  final seconds = (total % 60).toString().padLeft(2, '0');
  if (minutes >= 60) {
    final hours = minutes ~/ 60;
    return '$hours:${(minutes % 60).toString().padLeft(2, '0')}:$seconds';
  }
  return '$minutes:$seconds';
}

/// A provider retry: a condition of the running turn, not something that
/// needs the person (LOOK-4), so a working notice rather than the request
/// card. It names the attempt, counts down to the next one, and gives the
/// server's reason in plain words when it sent one.
class _RetryAttentionCard extends StatelessWidget {
  const _RetryAttentionCard({super.key, required this.retry});

  final SessionRetryState retry;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final title = retryBannerHeadline(retry, l10n: _chatL10n(context));
    final raw = retry.message?.trim();
    // The reason in plain words, when the app knows it; never the server's
    // own text. A rate limit is already the title.
    final words = raw == null || raw.isEmpty
        ? null
        : agentErrorWords(raw, _chatL10n(context));
    final message =
        words == null ||
            !words.humanized ||
            classifyAgentError(raw!) == AgentErrorCause.rateLimited
        ? null
        : words.headline;
    final hasMessage = message != null && message.isNotEmpty;
    // A server that says what to do (upgrade, add credit) is heard: its
    // words and one labelled button, the address opened only through
    // openExternalLink.
    final action = retry.action;
    final ask = action == null
        ? null
        : [action.title, action.message].where((t) => t.isNotEmpty).join('. ');
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: KitLayout.readingWidth),
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(
            horizontal: tokens.gutter,
            vertical: tokens.space1,
          ),
          child: KitNotice(
            title: hasMessage || ask != null ? title : null,
            message: ask ?? (hasMessage ? message : title),
            tone: AppStatusTone.progress,
            icon: AppIcons.retry,
            actions: [
              if (action != null &&
                  action.link != null &&
                  action.label.isNotEmpty)
                KitAction(
                  key: const ValueKey('retry-action'),
                  label: action.label,
                  onPressed: () =>
                      unawaited(openExternalLink(context, action.link)),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// An OpenCode 2 form of the open session, on the same card as a question:
/// the form's title, how many questions it asks, and Answer, which opens
/// the form. While its answers are on their way the card says so, and a
/// refused answer shows the server's reason above Answer again.
class _FormRequestCard extends StatefulWidget {
  const _FormRequestCard({
    super.key,
    required this.form,
    required this.onAnswer,
  });

  final Api2FormInfo form;
  final VoidCallback onAnswer;

  @override
  State<_FormRequestCard> createState() => _FormRequestCardState();
}

class _FormRequestCardState extends State<_FormRequestCard> {
  late final DateTime _seen = requestSeenNow();

  @override
  Widget build(BuildContext context) => _RequestSlot(child: _content(context));

  Widget _content(BuildContext context) {
    final connection = _requestConnection(context);
    final request = connection?.formRequestForForm(widget.form);
    if (connection == null || request == null) return const SizedBox.shrink();
    return capturedFormRequestCard(
      context,
      connection,
      request,
      answerKey: ValueKey('form-request-answer-${widget.form.id}'),
      who: _requestWho(context),
      since: _seen,
      onAnswer: widget.onAnswer,
    );
  }
}

/// A single-line, self-hiding note above the composer for composer-local
/// outcomes (queued, staged, already present). It replaces snackbars that
/// used to cover the field the user is typing into.
class _ComposerNote extends StatelessWidget {
  const _ComposerNote({super.key, required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: KitLayout.readingWidth),
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          child: Semantics(
            liveRegion: true,
            child: KitText(
              text,
              role: KitTextRole.caption,
              tone: KitTextTone.secondary,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ),
      ),
    );
  }
}
