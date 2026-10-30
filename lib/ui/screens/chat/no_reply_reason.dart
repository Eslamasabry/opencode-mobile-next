part of '../chat_screen.dart';

// Why the newest prompt got no reply, and what to do: the reason the last
// send hit ([_ChatSendFields._sendWall]), the one the agent's host still
// holds (`lastError`), or, after a restart that lost the words while the
// host still says the turn failed, the one this chat saw and kept
// ([AgentTurnFailureMemory]).

extension _NoReplyReasons on _ChatScreenState {
  /// The host's word on this conversation's last turn, when it keeps one.
  AgentLastTurn? _hostLastTurn() {
    final api = _conn.api;
    return api is AgentTurnFailureGateway
        ? (api as AgentTurnFailureGateway).lastAgentTurn(widget.sessionID)
        : null;
  }

  /// The failure behind the newest prompt's silence, best source first.
  AgentRunFailure? _noReplyFailure() {
    if (agentRunFailureOf(_sendWall) case final wall?) return wall;
    final last = _hostLastTurn();
    if (last == null) return null;
    if (last.reason case final reason?) return reason;
    // What this chat saw, whatever the host says now: after a restart the
    // loaded agent may read idle with an older attention (emulator,
    // 2026-10-28). It is forgotten once a turn answers.
    final profileId = _conn.profile?.id;
    final kept = profileId == null
        ? null
        : AgentTurnFailureMemory.read(
            _conn.store.prefs,
            profileId,
            widget.sessionID,
            alsoAs: last.sameConversation,
          );
    if (kept != null) return kept;
    // The host says it failed but not why, and this chat never saw why.
    return last.failed ? AgentRunFailure(reason: '') : null;
  }

  /// Why the prompt with no reply got none, in a few words; null when
  /// nothing is known (then it is plainly "No reply came back").
  String? _noReplyReason() {
    if (conversationNeedsNewChat(_sendWall)) {
      return _chatL10n(context).chatSendFailureNewConversation;
    }
    final run = _noReplyFailure();
    return run == null ? null : _agentRunWords(run);
  }

  /// The notice's actions, as for a failure seen live (the error row): the
  /// fix first (the agent's sign-in, another model, a new conversation),
  /// then Error details; Send again where trying again is the way. A model
  /// that needs the signed-in agent's account offers another model and
  /// Sign in to the agent, and its Error details is in the prompt's menu.
  List<KitAction> _noReplyActions(VoidCallback sendAgain) {
    final l10n = _chatL10n(context);
    final again = KitAction(
      key: const Key('no-reply-send-again'),
      label: l10n.chatUiSendPromptAgain,
      onPressed: sendAgain,
    );
    if (conversationNeedsNewChat(_sendWall) && !_conn.isIsolated) {
      return [
        KitAction(
          key: const Key('no-reply-continue-with'),
          label: l10n.continueWithMenu,
          onPressed: () => unawaited(_continueWithAnotherAgent()),
        ),
        again,
      ];
    }
    final run = _noReplyFailure();
    if (run == null) return [again];
    final details = run.reason.trim().isEmpty
        ? null
        : KitAction(
            key: const Key('no-reply-details'),
            label: l10n.chatUiErrorDetails,
            onPressed: () => unawaited(_showNoReplyDetails(run)),
          );
    final chooseModel = _chooseAnotherModel();
    final choose = chooseModel == null
        ? null
        : KitAction(
            key: const Key('no-reply-choose-model'),
            label: l10n.chatUiChooseAnotherModel,
            onPressed: chooseModel,
          );
    final signIn = _agentSignIn(run);
    KitAction? signInAs(String label) => signIn == null
        ? null
        : KitAction(
            key: const Key('no-reply-sign-in'),
            label: label,
            onPressed: signIn,
          );
    final agent = agentRunFailureAgentName(run.provider, l10n);
    return [
      ...switch (run.kind) {
        AgentRunFailureKind.signIn when _accountNeeded(run) => [
          choose,
          signInAs(l10n.chatSendFailureSignInToAgent(agent)),
        ],
        AgentRunFailureKind.signIn => [
          signInAs(l10n.chatSendFailureSignInAction) ?? choose,
          details,
        ],
        AgentRunFailureKind.noCredit => [
          signInAs(l10n.chatSendFailureAccountAction) ?? choose,
          details,
        ],
        AgentRunFailureKind.modelUnavailable => [choose, details],
        AgentRunFailureKind.busy ||
        AgentRunFailureKind.connectionLost ||
        AgentRunFailureKind.unknown => [again, details],
      }.nonNulls,
    ];
  }

  /// Whether the newest prompt's Error details sits in its menu (its notice
  /// shows two fixes instead).
  bool _noReplyDetailsInMenu(MessageWithParts message) {
    final unanswered = _unanswered;
    if (unanswered == null || !unanswered.$3) return false;
    final index = unanswered.$1;
    if (index >= _messages.length || _messages[index] != message) return false;
    final run = _noReplyFailure();
    return run != null &&
        run.reason.trim().isNotEmpty &&
        run.kind == AgentRunFailureKind.signIn &&
        _accountNeeded(run);
  }

  Future<void> _showNoReplyDetails(AgentRunFailure run) {
    final l10n = _chatL10n(context);
    final technical = agentRunFailureDetails(run, l10n);
    return showKitTechnicalDetails(
      context,
      title: l10n.chatUiErrorDetails,
      values: technical.values,
      text: technical.text,
      sheetKey: const ValueKey('chat-error-details'),
    );
  }

  /// Keeps the newest turn's failure for after a restart, and lets it go
  /// once a turn answers. Runs after a build that showed the transcript;
  /// writes only when what it would keep changes.
  void _noteTurnFailure() {
    final profileId = _conn.profile?.id;
    if (profileId == null || _conn.isIsolated || _watching) return;
    if (_messages.isEmpty) return;
    final last = _messages.last;
    final running = _conn.busySessions.contains(widget.sessionID) || _sending;
    AgentRunFailure? seen;
    var answered = false;
    if (!running) {
      final row = last.info.role == 'assistant'
          ? _agentRunFor(last.info)
          : null;
      if (row != null) {
        seen = row;
      } else if (_unanswered != null) {
        seen = agentRunFailureOf(_sendWall) ?? _hostLastTurn()?.reason;
      } else {
        answered = last.info.role == 'assistant' && last.info.errorText == null;
      }
    }
    final String? note = seen != null && seen.reason.trim().isNotEmpty
        ? 'keep:${seen.reason}'
        : answered
        ? 'forget'
        : null;
    if (note == null || note == _turnFailureNoted) return;
    _turnFailureNoted = note;
    // At once, not after the frame: a force-stop seconds later must find it
    // saved (SharedPreferences takes it into memory now, and to disk next).
    final prefs = _conn.store.prefs;
    final session = widget.sessionID;
    unawaited(
      seen != null
          ? AgentTurnFailureMemory.remember(prefs, profileId, session, seen)
          : AgentTurnFailureMemory.forget(
              prefs,
              profileId,
              session,
              alsoAs: _hostLastTurn()?.sameConversation ?? const [],
            ),
    );
  }
}

/// "No reply came back" on a prompt whose turn ended with nothing, with
/// Send again. When the reason is known, it is the notice's words and its
/// fix leads ([_NoReplyReasons._noReplyActions]); the words rise in when the
/// reason arrives.
Widget _noReplyNotice(
  BuildContext context, {
  required String messageID,
  required String? reason,
  required List<KitAction> actions,
}) {
  final l10n = _chatL10n(context);
  return KitEntrance(
    trigger: reason,
    onMount: false,
    child: KitNotice(
      key: ValueKey('prompt-no-reply-$messageID'),
      title: reason == null ? null : l10n.chatNoReplyCameBack,
      message: reason ?? l10n.chatNoReplyCameBack,
      messageKey: const Key('no-reply-reason'),
      actions: actions,
    ),
  );
}
