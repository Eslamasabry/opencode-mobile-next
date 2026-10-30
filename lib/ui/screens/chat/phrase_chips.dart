part of '../chat_screen.dart';

// Phrase chips (the smarter input box, idea 1): one row above the message box
// with the person's own common short phrases, chosen for the moment and
// learned on this phone from what they send. The learning and ranking are
// PhraseChipStore, the moment is derivePhraseMoment, the row is
// KitPhraseChips; this file only maps the chat's state onto them.

extension _PhraseChips on _ChatScreenState {
  PhraseChipStore? get _phraseStore {
    final id = _conn.profile?.id ?? '';
    if (id.isEmpty || _conn.isIsolated) return null;
    return PhraseChipStore.forProfile(_conn.store.prefs, id);
  }

  PhraseMomentState _phraseMomentState() => derivePhraseMoment(
    messages: _messages,
    busy:
        _conn.busySessions.contains(widget.sessionID) ||
        _live != null ||
        _sending,
    stoppedByPerson: _stoppedPromptID != null,
    offline: _conn.status != StreamStatus.connected,
  );

  /// Counts [text] as a send in the moment it was sent in. Learned only from
  /// a send that went through (not a command, a voice conversation, or a
  /// message that carried files).
  void _learnPhrase(
    String text,
    List<PromptAttachment> attachments,
    PhraseMoment? moment,
  ) {
    final store = _phraseStore;
    if (store == null || !PhraseChipStore.enabled(_conn.store.prefs)) return;
    unawaited(
      store
          .record(
            text,
            moment ?? PhraseMoment.idle,
            hasAttachments: attachments.isNotEmpty,
          )
          .catchError((_) => false),
    );
  }

  /// The row, or nothing: hidden in an empty chat, while the person types or
  /// has files staged, while a question, request or form waits, under an
  /// error notice, in a window with no room, and when the person turned it
  /// off.
  Widget _phraseChipsRow(
    BoxConstraints bodyConstraints,
    List<PermissionRequest> pendingPermissions,
  ) {
    final store = _phraseStore;
    if (store == null ||
        _voiceConversation ||
        _watching ||
        bodyConstraints.maxHeight < 420) {
      return const SizedBox.shrink();
    }
    return ListenableBuilder(
      listenable: Listenable.merge([_composer, PhraseChipStore.changes]),
      builder: (context, _) {
        if (!PhraseChipStore.enabled(_conn.store.prefs) ||
            _composer.text.trim().isNotEmpty ||
            _attachments.isNotEmpty ||
            _sending ||
            pendingPermissions.isNotEmpty ||
            _conn.questionForSession(widget.sessionID) != null ||
            _conn.formForSession(widget.sessionID) != null ||
            (!_conn.capabilities.subagentReplies &&
                _conn.sessionsById[widget.sessionID]?.parentID != null)) {
          return const SizedBox.shrink();
        }
        final state = _phraseMomentState();
        // While the first history read is on its way the chat shows the end
        // it had last time (the opening excerpt): the row that history will
        // bring is there already, so the conversation does not rise by a
        // row when it lands (jitter J9, 2026-10-28).
        final moment =
            state.moment ?? (_openingExcerptShown ? PhraseMoment.idle : null);
        // While the agent works the screen is the conversation, the live
        // line and the glowing composer (concept C): no row of phrases over
        // it. "What's the update?" is what the live line already answers.
        if (moment == null || moment == PhraseMoment.working) {
          return const SizedBox.shrink();
        }
        // Under an error the notice holds the way on (another model, Send
        // again); retries in a row of chips would only crowd it. The
        // transcript's own reading counts too ([_unanswered]: the newest
        // prompt ended on an error, its reply never stored as a message).
        final ended = _unanswered;
        final endedOnError = ended != null && !ended.$3 && !state.continueShown;
        if (state.errorShown || endedOnError) return const SizedBox.shrink();
        final strings = _chatL10n(context);
        final continueWord = strings.returnBriefContinue;
        final chips = store.chipsFor(
          moment,
          starters: switch (moment) {
            // The redo in full ("Try again", never a bare "Try"), right
            // after Continue so it is never under the row's fading end.
            PhraseMoment.stoppedPartway => [
              continueWord,
              strings.phraseChipsRetryStarter,
            ],
            _ => const <String>[],
          },
          // The transcript's own Continue button is on screen: not twice.
          exclude: state.continueShown ? {continueWord, 'Continue'} : const {},
        );
        // No phrases yet: no row, and no gap above the message box either.
        if (chips.isEmpty) return const SizedBox.shrink();
        return Padding(
          padding: EdgeInsets.only(top: KitTokens.of(context).space1),
          child: KitPhraseChips(
            phrases: [for (final chip in chips) chip.text],
            onChoose: (text) => unawaited(_sendPhrase(text)),
            onEdit: _editPhrase,
            onHide: (text) => unawaited(store.hide(text)),
          ),
        );
      },
    );
  }

  /// The chat shows its opening excerpt (chat_page.dart's rule): the end
  /// of the conversation as it read last time, while the live history loads.
  bool get _openingExcerptShown =>
      _loading &&
      _messages.isEmpty &&
      !_watching &&
      !_conn.isIsolated &&
      (_conn.cachedSessionTail(widget.sessionID)?.messages.isNotEmpty ?? false);

  /// A tap: the phrase goes through the normal send path, as if typed and
  /// sent (the delivery choice while the agent works, the offline queue).
  Future<void> _sendPhrase(String text) => _sendInsteadOfDraft(text);

  /// Menu › Edit: the words in the box, not sent.
  void _editPhrase(String text) {
    _composer.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    _focus.requestFocus();
  }
}
