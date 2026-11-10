part of '../chat_screen.dart';

/// One turn sent from a voice conversation with "Speak replies" on. The
/// watch is bound to the app-authored ID carried on dispatch, the speech scope and the voice
/// epoch, so a reconnect, a scope switch, or Exit can never re-attach it to
/// some other text.
class _VoiceReplyWatch {
  _VoiceReplyWatch({
    required this.pending,
    required this.scope,
    required this.epoch,
    required this.existingMessageIDs,
  });
  final _PendingSend pending;
  final Object scope;
  final int epoch;
  final Set<String> existingMessageIDs;
  final Set<String> liveUserIDs = {};

  /// Set once the session has been seen busy after the send; only then can
  /// an idle session mean "the turn ran and finished" rather than "the
  /// status event has not arrived yet".
  bool sawBusy = false;
  bool sawIdle = false;
}

/// What the conversation strip reports about automatic reading.
enum _VoiceReplyState {
  idle,

  /// The turn was accepted; its reply has not completed yet.
  waiting,

  /// The reply completed but could not be tied to the sent turn with
  /// certainty; reading stays a tap away instead of a guess.
  reviewNeeded,

  /// A request, question or form interrupted the turn; automatic reading
  /// was cancelled for it.
  interrupted,

  /// The reply completed with nothing speakable (code, tools, or empty).
  noProse,

  /// The engine refused or failed; the reply can be read again by hand.
  failed,
}

extension _ChatVoiceConversation on _ChatScreenState {
  void _observeVoiceReplyStatus(EventEnvelope event) {
    final watch = _voiceReplyWatch;
    if (watch == null || event.properties['sessionID'] != widget.sessionID) {
      return;
    }
    if (event.type == 'session.status') {
      final raw = event.properties['status'];
      final status = raw is Map ? raw['type'] : raw;
      if (status == 'busy' || status == 'retry') {
        watch.sawBusy = true;
        watch.sawIdle = false;
      } else if (status == 'idle' && watch.sawBusy) {
        watch.sawIdle = true;
      }
    } else if (event.type == 'session.idle' && watch.sawBusy) {
      watch.sawIdle = true;
    }
  }

  bool get _conversationCanSend =>
      _conn.status == StreamStatus.connected &&
      !_conn.busySessions.contains(widget.sessionID) &&
      _conn.permissionsForSession(widget.sessionID).isEmpty &&
      _conn.questionForSession(widget.sessionID) == null &&
      _conn.formForSession(widget.sessionID) == null &&
      _conn.isProfileReadable(_conn.promptShelfProfileID);

  bool get _conversationBlockedByRequest =>
      _conn.permissionsForSession(widget.sessionID).isNotEmpty ||
      _conn.questionForSession(widget.sessionID) != null ||
      _conn.formForSession(widget.sessionID) != null;

  String get _conversationPauseCopy =>
      _chatL10n(context).voiceConversationPausedDetail;

  Future<void> _startVoiceConversation() async {
    if (_conn.isIsolated) return;
    if (!platformCapabilities.supportsVoiceConversation ||
        _sending ||
        _voiceOpening ||
        _promptShelfBusy) {
      return;
    }
    if (!_voiceConversation) {
      if (_composer.text.isNotEmpty ||
          _attachments.isNotEmpty ||
          _handoff.references.isNotEmpty) {
        _showComposerNote(_chatL10n(context).voiceConversationDraftFirst);
        return;
      }
      if (!_conversationCanSend) {
        _showComposerNote(_conversationPauseCopy);
        return;
      }
      _voiceOwnerScope = _speechScopeNow;
      _updateSpeech(() => _voiceConversation = true);
    }
    await _openVoice();
  }

  void _interruptVoiceConversation() {
    _voiceEpoch.value++;
    _voiceOwnerScope = null;
    unawaited(_voice?.cancel());
    unawaited(_stopReading());
    if (_voiceDictating) {
      // A scope change or a covered route ends dictation; the words already
      // written down stay in the draft.
      _dictationBase = null;
      _voiceSettingsOpened = false;
      _updateSpeech(() => _voiceDictating = false);
    }
    if (_voiceConversation) {
      // Clear while the persistence guard is still active.
      _composer.clear();
      _draftSaveTimer?.cancel();
      _updateSpeech(() {
        _voiceConversation = false;
        _voiceSpeakReplies = false;
        _voiceReplyWatch = null;
        _voiceReplyState = _VoiceReplyState.idle;
      });
    }
  }

  /// Drops the opt-in and anything owed under it, without leaving the
  /// conversation. Used when consent lapses with a scope change.
  void _revokeVoiceSpeakReplies() {
    if (!_voiceSpeakReplies && _voiceReplyWatch == null) return;
    _voiceSpeakReplies = false;
    _voiceReplyWatch = null;
    _voiceReplyState = _VoiceReplyState.idle;
    if (mounted) _updateSpeech(() {});
  }

  /// The "Speak replies" switch. Turning it on runs the same consent and
  /// voice-choice sheets as a manual Read aloud, right now, so no automatic
  /// playback can ever be the first time the engine is touched.
  Future<void> _setVoiceSpeakReplies(bool enabled) async {
    if (!enabled) {
      final wasSpeakingReply = _readAloud?.activeID?.startsWith(
        _voiceReplyUtterancePrefix,
      );
      _updateSpeech(() {
        _voiceSpeakReplies = false;
        _voiceReplyWatch = null;
        _voiceReplyState = _VoiceReplyState.idle;
      });
      if (wasSpeakingReply == true) unawaited(_stopReading());
      return;
    }
    if (!_voiceConversation ||
        _voiceSpeakReplies ||
        !platformCapabilities.supportsReadAloud ||
        _readAloudRequestBusy) {
      return;
    }
    final source = _speechScopeNow;
    final epoch = _voiceEpoch.value;
    final route = ModalRoute.of(context);
    final request = ++_readAloudRequest;
    bool current() =>
        mounted &&
        _voiceConversation &&
        request == _readAloudRequest &&
        epoch == _voiceEpoch.value &&
        source == _speechScopeNow &&
        (route?.isCurrent ?? true);
    _speechOwnerScope = source;
    _updateSpeech(() => _readAloudRequestBusy = true);
    try {
      if (!await _ensureSpeechReady(current: current)) return;
      _updateSpeech(() {
        _voiceSpeakReplies = true;
        _voiceReplyState = _VoiceReplyState.idle;
      });
    } on ReadAloudException catch (error) {
      if (current() && _readAloud?.failure != error.failure) {
        _showComposerNote(_speechFailure(error.failure));
      }
    } catch (_) {
      if (mounted && current()) {
        _showComposerNote(_chatL10n(context).readAloudUnavailable);
      }
    } finally {
      if (mounted && request == _readAloudRequest) {
        _updateSpeech(() => _readAloudRequestBusy = false);
      }
    }
  }

  String get _voiceReplyUtterancePrefix => '${widget.sessionID}/voice-reply/';

  /// Armed immediately before dispatch so fast echoes, requests and busy
  /// events are observed. Playback still waits for requestComplete.
  void _watchVoiceReply(_PendingSend pending) {
    if (!_voiceSpeakReplies) return;
    if (pending.dispatchedMessageID == null) {
      _voiceReplyWatch = null;
      _voiceReplyState = _VoiceReplyState.reviewNeeded;
      return;
    }
    _voiceReplyWatch = _VoiceReplyWatch(
      pending: pending,
      scope: _speechScopeNow,
      epoch: _voiceEpoch.value,
      existingMessageIDs: _messages.map((m) => m.info.id).toSet(),
    );
    _voiceReplyState = _VoiceReplyState.waiting;
  }

  /// The assistant messages that answer [canonicalID]: everything after that
  /// user message up to the next user message, with an explicit parent id.
  /// Order alone cannot distinguish another client's reply.
  List<MessageWithParts> _voiceReplyCandidates(String canonicalID) {
    final start = _messages.indexWhere((m) => m.info.id == canonicalID);
    if (start < 0) return const [];
    final byParent = <MessageWithParts>[];
    for (final message in _messages.skip(start + 1)) {
      final info = message.info;
      if (info.role == 'user') break;
      if (info.role != 'assistant') continue;
      final parent = info.parentID;
      // A mixed or parentless response is not safe to read partially.
      if (parent != canonicalID) return const [];
      byParent.add(message);
    }
    return byParent;
  }

  /// Re-evaluated on every event and controller change while a turn is
  /// owed. Speaks exactly once, only when the turn has verifiably finished,
  /// and otherwise resolves to an explicit state instead of guessing.
  void _checkVoiceReply() {
    final watch = _voiceReplyWatch;
    if (watch == null || !mounted) return;
    void settle(_VoiceReplyState state) {
      _voiceReplyWatch = null;
      _updateSpeech(() => _voiceReplyState = state);
    }

    if (!_voiceConversation ||
        !_voiceSpeakReplies ||
        watch.epoch != _voiceEpoch.value ||
        watch.scope != _speechScopeNow) {
      settle(_VoiceReplyState.idle);
      return;
    }
    if (_conversationBlockedByRequest) {
      // The turn now needs the user on screen; whatever it says afterwards
      // is read only on request.
      settle(_VoiceReplyState.interrupted);
      return;
    }
    if (_conn.status != StreamStatus.connected) {
      settle(_VoiceReplyState.reviewNeeded);
      return;
    }
    if (_conn.busySessions.contains(widget.sessionID)) {
      watch.sawBusy = true;
      return;
    }
    if (!watch.pending.requestComplete || !watch.sawBusy || !watch.sawIdle) {
      return;
    }
    // canonicalID is text/time reconciliation for optimistic rendering, not
    // evidence that this client dispatched a message. Never trust it here.
    final canonicalID = watch.pending.dispatchedMessageID;
    if (canonicalID == null ||
        watch.existingMessageIDs.contains(canonicalID) ||
        watch.liveUserIDs.length != 1 ||
        !watch.liveUserIDs.contains(canonicalID)) {
      // The session ran and went idle, or errored, but the server's copy of
      // the sent message never matched: the reply cannot be pinned to it.
      if (watch.sawBusy || _promptError != null) {
        settle(_VoiceReplyState.reviewNeeded);
      }
      return;
    }
    final replies = _voiceReplyCandidates(canonicalID);
    if (replies.isEmpty) {
      settle(_VoiceReplyState.reviewNeeded);
      return;
    }
    final settled = replies.every(
      (m) => (m.info.time?.isDone ?? false) || m.info.errorText != null,
    );
    if (!settled) return;
    final prose = [
      for (final reply in replies)
        markdownProseForSpeech(_ChatScreenState._messageText(reply)),
    ].where((text) => text.isNotEmpty).join('\n\n');
    if (prose.isEmpty) {
      settle(_VoiceReplyState.noProse);
      return;
    }
    _voiceReplyWatch = null;
    unawaited(_speakVoiceReply(canonicalID, prose));
  }

  Future<void> _speakVoiceReply(String canonicalID, String prose) async {
    // Consent and the voice were granted when the switch went on; a scope
    // change since then revoked both, and the switch with them.
    if (!_voiceSpeakReplies ||
        !_readAloudConsented ||
        _readAloudVoiceID == null ||
        _readAloud == null) {
      _updateSpeech(() => _voiceReplyState = _VoiceReplyState.reviewNeeded);
      return;
    }
    final source = _speechScopeNow;
    final epoch = _voiceEpoch.value;
    final route = ModalRoute.of(context);
    final request = ++_readAloudRequest;
    bool current() =>
        mounted &&
        _voiceConversation &&
        _voiceSpeakReplies &&
        request == _readAloudRequest &&
        epoch == _voiceEpoch.value &&
        source == _speechScopeNow &&
        (route?.isCurrent ?? true);
    _speechOwnerScope = source;
    _updateSpeech(() {
      _readAloudRequestBusy = true;
      _voiceReplyState = _VoiceReplyState.idle;
      _voiceReplyPlayback = true;
    });
    try {
      if (!current()) return;
      final speech = _readAloud!;
      await speech.speak(
        '$_voiceReplyUtterancePrefix$canonicalID',
        prose,
        voiceID: _readAloudVoiceID,
      );
      if (!current()) await speech.stop();
    } on FormatException {
      if (mounted && current()) {
        _updateSpeech(() => _voiceReplyState = _VoiceReplyState.failed);
        _showComposerNote(_chatL10n(context).readAloudTooLong);
      }
    } on ReadAloudException catch (error) {
      if (current()) {
        _updateSpeech(() => _voiceReplyState = _VoiceReplyState.failed);
        if (_readAloud?.failure != error.failure) {
          _showComposerNote(_speechFailure(error.failure));
        }
      }
    } catch (_) {
      if (mounted && current()) {
        _updateSpeech(() => _voiceReplyState = _VoiceReplyState.failed);
        _showComposerNote(_chatL10n(context).readAloudUnavailable);
      }
    } finally {
      if (mounted && request == _readAloudRequest) {
        _updateSpeech(() => _readAloudRequestBusy = false);
      }
    }
  }

  bool get _speakingVoiceReply =>
      _readAloud?.speaking == true &&
      (_readAloud?.activeID?.startsWith(_voiceReplyUtterancePrefix) ?? false);

  // --- voice mode (P10.3) ----------------------------------------------

  void _listenToVoice(VoiceComposerController voice) {
    if (identical(_voiceListened, voice)) return;
    _voiceListened?.removeListener(_onVoiceChanged);
    _voiceListened = voice..addListener(_onVoiceChanged);
  }

  /// The recording's changes: the level and clock go to the pill without a
  /// rebuild; dictation's words go into the draft as each chunk is written
  /// down; a conversation's finished words are sent.
  void _onVoiceChanged() {
    final voice = _voiceListened;
    if (!mounted || voice == null) return;
    final state = voice.state;
    final listening = state == VoiceComposerState.listening;
    _voiceLevel.value = listening ? voice.level : 0;
    if (_voiceDictating) {
      _mergeDictation(
        state == VoiceComposerState.draft ? voice.draft : voice.transcript,
      );
      if (state == VoiceComposerState.draft) {
        _finishDictation(voice);
        return;
      }
      if (state == VoiceComposerState.idle &&
          !_voiceOpening &&
          !voice.starting) {
        // Cancelled from elsewhere (the app paused, the setup closing).
        _leaveDictation();
        return;
      }
    } else if (_voiceConversation && state == VoiceComposerState.draft) {
      final words = voice.draft.trim();
      unawaited(voice.cancel());
      if (words.isEmpty) {
        _showComposerNote(_chatL10n(context).voiceModeNothingHeard);
      } else {
        // Conversation mode says it sends: the words go through the one send
        // path, so commands, delivery and the reply watch behave as typed.
        _composer.text = words;
        unawaited(_send());
      }
    }
    if (state != _voiceShownState) {
      _voiceShownState = state;
      _voiceListeningSince = listening
          ? clock.now().subtract(voice.elapsed)
          : null;
      _updateSpeech(() {});
    }
  }

  /// The composer as dictation found it, with what was said so far merged
  /// in at its selection. The draft store saves it like typed text.
  void _mergeDictation(String words) {
    final base = _dictationBase;
    if (base == null) return;
    final text = words.trim().isEmpty
        ? base.text
        : mergeVoiceDraft(base.text, base.selection, words);
    if (text == _composer.text) return;
    _composer.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
  }

  void _finishDictation(VoiceComposerController voice) {
    final heardNothing = voice.draft.trim().isEmpty;
    _dictationBase = null;
    _updateSpeech(() => _voiceDictating = false);
    unawaited(voice.cancel());
    unawaited(_persistDraft());
    if (heardNothing) {
      _showComposerNote(_chatL10n(context).voiceModeNothingHeard);
    } else {
      _focus.requestFocus();
    }
  }

  /// Leave (the pill's close, Esc): the recording stops; the words already
  /// written down stay in the draft.
  void _leaveDictation() {
    if (!_voiceDictating) return;
    _dictationBase = null;
    _voiceSettingsOpened = false;
    _updateSpeech(() => _voiceDictating = false);
    unawaited(_voice?.cancel());
    unawaited(_persistDraft());
  }

  /// The app went away or a page covered the chat: the microphone stops at
  /// once and what was recorded is still written into the draft.
  void _pauseDictation() {
    final voice = _voice;
    if (!_voiceDictating || voice == null) return;
    switch (voice.state) {
      case VoiceComposerState.listening:
        unawaited(voice.stopListening());
      case VoiceComposerState.initializing:
        _leaveDictation();
      default:
        break;
    }
  }

  void _openMicSettings() {
    _voiceSettingsOpened = true;
    unawaited(voiceDevicePlatform.openAppSettings());
  }

  void _retryVoiceAfterSettings() {
    final voice = _voice;
    if (voice == null || (!_voiceDictating && !_voiceConversation)) return;
    if (voice.state == VoiceComposerState.error &&
        voice.error is VoicePermissionDenied) {
      unawaited(voice.startListening());
    }
  }

  /// The pill's voice mode, or null when the composer is for typing.
  KitComposerVoice? _composerVoice() {
    if (!_voiceDictating && !_voiceConversation) return null;
    final l10n = _chatL10n(context);
    final voice = _voice;
    final state = voice?.state;
    final error = voice?.error;
    final recorded = (voice?.elapsed ?? Duration.zero) > Duration.zero;
    final tryAgain = KitAction(
      key: const Key('voice-mode-try-again'),
      label: l10n.e7VoiceUiRetry,
      icon: AppIconography.retry,
      onPressed: () => unawaited(_openVoice()),
    );
    KitVoicePhase phase;
    String? reason;
    KitAction? fix;
    switch (state) {
      case VoiceComposerState.listening:
        phase = KitVoicePhase.listening;
      case VoiceComposerState.loading when !recorded:
      case VoiceComposerState.initializing:
      case VoiceComposerState.downloading:
      case VoiceComposerState.verifying:
      case null:
        phase = KitVoicePhase.starting;
      case VoiceComposerState.loading:
      case VoiceComposerState.transcribing:
      case VoiceComposerState.finishingCancellation:
        phase = KitVoicePhase.transcribing;
      case VoiceComposerState.error when error is VoicePermissionDenied:
        phase = KitVoicePhase.micDenied;
        reason = error.permanent
            ? l10n.voiceModeMicBlocked
            : l10n.voiceModeMicAsk;
        fix = error.permanent
            ? KitAction(
                key: const Key('voice-mode-open-settings'),
                label: l10n.voiceAllowMicInSettings,
                icon: AppIconography.settings,
                onPressed: _openMicSettings,
              )
            : KitAction(
                key: const Key('voice-mode-allow-mic'),
                label: l10n.voiceModeMicAllow,
                icon: AppIconography.mic,
                onPressed: () => unawaited(voice!.startListening()),
              );
      case VoiceComposerState.error:
        phase = KitVoicePhase.failed;
        reason = voiceErrorText(error, l10n, manager: voice!.models);
        fix = tryAgain;
      case VoiceComposerState.modelRequired:
        phase = KitVoicePhase.failed;
        reason = l10n.e7VoiceUiModelRequired;
        fix = tryAgain;
      case VoiceComposerState.idle || VoiceComposerState.draft:
        if (!_voiceConversation) {
          // Dictation hands its words over and leaves in the same frame.
          phase = KitVoicePhase.transcribing;
        } else {
          (phase, reason, fix) = _conversationPhase(l10n);
        }
    }
    final conversation = _voiceConversation;
    return KitComposerVoice(
      voiceKey: const Key('voice-mode'),
      phase: phase,
      conversation: conversation,
      level: _voiceLevel,
      listeningSince: phase == KitVoicePhase.listening
          ? _voiceListeningSince
          : null,
      reason: reason,
      fix: fix,
      onExit: conversation ? _interruptVoiceConversation : _leaveDictation,
      onStopListening: voice == null
          ? null
          : () => unawaited(voice.stopListening()),
      onStopSpeaking: () => unawaited(_stopReading()),
      onListen: _conversationCanSend && !_voiceOpening && !_sending
          ? () => unawaited(_openVoice())
          : null,
      onReadReply: _offerReadReply
          ? () {
              _updateSpeech(() => _voiceReplyState = _VoiceReplyState.idle);
              unawaited(_readReply(_latestReadableReply!));
            }
          : null,
      readRepliesAloud: _voiceSpeakReplies,
      onReadRepliesAloudChanged:
          conversation && platformCapabilities.supportsReadAloud
          ? (value) {
              if (_readAloudRequestBusy && !_voiceSpeakReplies) return;
              unawaited(_setVoiceSpeakReplies(value));
            }
          : null,
    );
  }

  bool get _offerReadReply =>
      _voiceConversation &&
      !_speakingVoiceReply &&
      _voiceReplyWatch == null &&
      _latestReadableReply != null;

  /// The newest reply with words to read, for "Read it aloud".
  MessageWithParts? get _latestReadableReply {
    for (final message in _messages.reversed) {
      if (_canReadReply(message)) return message;
    }
    return null;
  }

  /// Between recordings in a voice conversation: a request on screen
  /// pauses it; a sent turn waits for its reply, which may be read aloud;
  /// then the reply is ready and Listen starts the next turn.
  (KitVoicePhase, String?, KitAction?) _conversationPhase(
    AppLocalizations l10n,
  ) {
    if (_conversationBlockedByRequest) {
      return (KitVoicePhase.paused, null, null);
    }
    if (_speakingVoiceReply) return (KitVoicePhase.speakingReply, null, null);
    if (_sending ||
        _voiceReplyWatch != null ||
        _conn.busySessions.contains(widget.sessionID)) {
      return (KitVoicePhase.waitingReply, null, null);
    }
    if (!_conversationCanSend) {
      // Offline, or the server's data is not readable: nothing can be sent.
      return (KitVoicePhase.failed, _conversationPauseCopy, null);
    }
    final String? reason = switch (_voiceReplyState) {
      _VoiceReplyState.idle || _VoiceReplyState.waiting => null,
      _VoiceReplyState.reviewNeeded => l10n.voiceConversationReplyReviewNeeded,
      _VoiceReplyState.interrupted => l10n.voiceConversationReplyInterrupted,
      _VoiceReplyState.noProse => l10n.voiceConversationReplyNoProse,
      _VoiceReplyState.failed => l10n.voiceConversationReplyFailed,
    };
    return (KitVoicePhase.replyReady, reason, null);
  }
}
