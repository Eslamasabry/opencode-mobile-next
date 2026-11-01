part of '../chat_screen.dart';

// Voice in the composer: the voice controller and opening voice input.

mixin _ChatVoiceFields {
  Future<VoiceComposerController>? _voiceFuture;
  VoiceComposerController? _voice;
  bool _voiceOpening = false;
  bool _voiceConversation = false;
  Object? _voiceOwnerScope;
  final ValueNotifier<int> _voiceEpoch = ValueNotifier(0);

  /// The "Speak replies" opt-in of the current voice conversation. Never
  /// persisted: it is granted per conversation, after consent and voice
  /// choice, and Exit, a scope change or a lifecycle pause revoke it.
  bool _voiceSpeakReplies = false;
  bool _voiceReplyPlayback = false;
  bool _speechSheetOpen = false;

  /// Dictation (P10.3): the mic turned the composer into voice mode and
  /// what is said lands in the draft, chunk by chunk.
  bool _voiceDictating = false;

  /// The composer as dictation found it; the transcript is merged in at
  /// its selection each time a chunk is written down.
  TextEditingValue? _dictationBase;

  /// Push-to-talk: the mic is held. [_pttHeld] while the finger is down,
  /// [_pttArmed] once it is dragged far enough that letting go cancels.
  bool _pttMode = false;
  bool _pttHeld = false;
  bool _pttArmed = false;

  /// The finger lifted while the recording was still starting: what to do as
  /// soon as it is listening (null: nothing yet; false: finish; true: cancel).
  bool? _pttEndedEarly;

  /// When the words of a dictation that ended on silence will be sent
  /// (Settings › Voice typing › Send when I stop talking), and the words as
  /// they were then: any other change cancels the send.
  DateTime? _autoSendAt;
  String? _autoSendText;

  /// A recording was started by a tap: the hold-to-talk tip may be offered
  /// once the box is empty again.
  bool _micTipWanted = false;

  /// The controller whose changes drive voice mode (listened to once).
  VoiceComposerController? _voiceListened;
  VoiceComposerState? _voiceShownState;
  DateTime? _voiceListeningSince;
  final ValueNotifier<double> _voiceLevel = ValueNotifier(0);

  /// "Allow microphone in Android settings" was tapped: coming back to the
  /// app tries the microphone again instead of leaving voice mode.
  bool _voiceSettingsOpened = false;

  /// The turn sent from this conversation whose reply is still owed, or
  /// null. Only this turn's reply is ever spoken automatically.
  _VoiceReplyWatch? _voiceReplyWatch;

  /// What the conversation strip says about the last automatic reading.
  _VoiceReplyState _voiceReplyState = _VoiceReplyState.idle;
}

extension _ChatVoice on _ChatScreenState {
  Future<VoiceComposerController> _getVoice() {
    final strings = _chatL10n(context);
    if (_conn.isIsolated) {
      return Future.error(StateError(strings.chatUiVoiceInputIsUnavailable));
    }
    return _voiceFuture ??= VoiceComposerController.create().then((voice) {
      if (!mounted) {
        voice.dispose();
        throw StateError(strings.chatUiVoiceInputIsUnavailable);
      }
      _voice = voice;
      return voice;
    });
  }

  /// The mic (P10.3): turns the composer into voice mode. Dictation puts
  /// what is said into the draft; in a voice conversation it is sent. The
  /// first time, P10.4's automatic setup picks and fetches the speech model
  /// and hands over a recording already listening.
  Future<void> _openVoice({bool pushToTalk = false}) async {
    if (_conn.isIsolated) return;
    // The tools sheet hides the entry point off Android; this keeps a
    // programmatic call (a shortcut, a restored intent) from starting a model
    // download for a recognizer that can never be fed.
    if (!platformCapabilities.supportsVoice) return;
    if (_voiceOpening || _sending) return;
    if (_voiceConversation && !_conversationCanSend) {
      _showComposerNote(_conversationPauseCopy);
      return;
    }
    final scope = _speechScopeNow;
    final epoch = _voiceEpoch.value;
    _voiceOwnerScope = scope;
    bool current() =>
        mounted &&
        epoch == _voiceEpoch.value &&
        scope == _speechScopeNow &&
        _conn.isProfileReadable(_conn.promptShelfProfileID) &&
        (ModalRoute.of(context)?.isCurrent ?? true);
    _setChatState(() => _voiceOpening = true);
    _autoSendAt = null;
    _autoSendText = null;
    try {
      await _stopReading();
      if (!current()) return;
      final voice = await _getVoice();
      if (!mounted || !current()) return;
      _listenToVoice(voice);
      voice.appLanguageCode = Localizations.localeOf(context).languageCode;
      if (!voice.models.isReady) {
        // Whoever pressed is not holding the mic once a setup sheet has been
        // through: this one is a plain recording.
        _pttMode = false;
        _pttHeld = false;
        _pttEndedEarly = null;
        final ready = await showVoiceAutomaticSetupSheet(context, voice);
        if (!ready || !current()) {
          // A recording the setup started belongs to no mode now.
          if (voice.state == VoiceComposerState.listening) {
            unawaited(voice.cancel());
          }
          if (mounted && _voiceConversation && !ready) {
            _interruptVoiceConversation();
          }
          return;
        }
      }
      if (!_voiceConversation && !_voiceDictating) {
        _dictationBase = _composer.value;
        _updateSpeech(() => _voiceDictating = true);
      }
      _pttMode = pushToTalk && _pttMode;
      // A tap may end by itself on silence, when the person opted in; a
      // held mic, and a voice conversation, end only by the person.
      final byTap = !pushToTalk && !_voiceConversation;
      if (byTap) _micTipWanted = true;
      voice.stopOnSilence = byTap && voice.models.autoSend;
      await voice.startListening();
      await _applyEarlyRelease(voice);
    } catch (error) {
      if (mounted && current()) {
        if (_voiceDictating) _leaveDictation();
        _showActionError(_chatL10n(context).voiceInputUnavailable);
      }
    } finally {
      if (mounted) _setChatState(() => _voiceOpening = false);
    }
  }
}

// --- push-to-talk ------------------------------------------------------

extension _ChatPushToTalk on _ChatScreenState {
  /// Holding the mic: record until the finger lifts.
  KitComposerMicHold? _micHold() => KitComposerMicHold(
    onStart: _pttStart,
    onEnd: _pttEnd,
    onArmedChanged: (armed) {
      if (!_pttHeld || _pttArmed == armed) return;
      _updateSpeech(() => _pttArmed = armed);
    },
  );

  void _pttStart() {
    if (_voiceOpening || _sending || _voiceDictating || _voiceConversation) {
      return;
    }
    _conn.tips.markUsed(TeachingIds.holdMic);
    _micTipWanted = false;
    _pttMode = true;
    _pttHeld = true;
    _pttArmed = false;
    _pttEndedEarly = null;
    unawaited(_openVoice(pushToTalk: true));
  }

  /// The finger lifted: finish and write it down, or throw it away.
  void _pttEnd(bool cancelled) {
    if (!_pttHeld) return;
    _updateSpeech(() {
      _pttHeld = false;
      _pttArmed = false;
    });
    final voice = _voice;
    if (voice == null || !_pttMode) return;
    if (voice.state != VoiceComposerState.listening) {
      // Still getting the microphone: act when it is listening.
      _pttEndedEarly = cancelled;
      return;
    }
    _finishHold(voice, cancelled);
  }

  Future<void> _applyEarlyRelease(VoiceComposerController voice) async {
    final cancelled = _pttEndedEarly;
    _pttEndedEarly = null;
    if (cancelled == null || !_pttMode || !mounted) return;
    if (voice.state == VoiceComposerState.listening) {
      _finishHold(voice, cancelled);
    } else if (_voiceDictating) {
      _discardDictation();
    }
  }

  /// A touch too short to be speech is a slip, not a message.
  void _finishHold(VoiceComposerController voice, bool cancelled) {
    if (cancelled || voice.elapsed < const Duration(milliseconds: 400)) {
      _discardDictation();
    } else {
      unawaited(voice.stopListening());
    }
  }

  /// Dictation ends and the box is as it was before: the words said so far
  /// are dropped.
  void _discardDictation() {
    final base = _dictationBase;
    if (base != null) _composer.value = base;
    _leaveDictation();
  }
}

// --- send when I stop talking ------------------------------------------

extension _ChatAutoSend on _ChatScreenState {
  /// A dictation that ended on silence, with the setting on: the words wait
  /// [voiceAutoSendCountdown] in the pill, with Cancel. True when it began.
  bool _beginAutoSend(VoiceComposerController voice) {
    if (!voice.stoppedOnSilence ||
        !voice.models.autoSend ||
        _conn.isIsolated ||
        _sending ||
        !mayAutoSend(voice.draft)) {
      return false;
    }
    _autoSendAt = clock.now().add(voiceAutoSendCountdown);
    _autoSendText = _composer.text;
    unawaited(voice.cancel());
    _updateSpeech(() {});
    return true;
  }

  void _cancelAutoSend() {
    if (_autoSendAt == null) return;
    _autoSendAt = null;
    _autoSendText = null;
    _leaveDictation();
    _focus.requestFocus();
  }

  /// The countdown ran out (or Send was tapped): the words go through the
  /// one send path, as if typed - unless anything changed meanwhile.
  void _autoSendNow() {
    final at = _autoSendAt;
    if (at == null || !_voiceDictating) return;
    final unchanged =
        _composer.text == _autoSendText && mayAutoSend(_composer.text);
    if (!unchanged || _sending || !mounted) {
      _cancelAutoSend();
      return;
    }
    _autoSendAt = null;
    _autoSendText = null;
    _dictationBase = null;
    _updateSpeech(() => _voiceDictating = false);
    unawaited(_send());
  }

  /// The hold-to-talk tip while it is this screen's to show.
  KitComposerTip? _micTip() {
    const id = TeachingIds.holdMic;
    final tips = _conn.tips;
    if (!tips.isActive(id) || tips.activeIsReplay) return null;
    return KitComposerTip(
      message: _chatL10n(context).tipHoldMic,
      onDismissed: () => tips.dismiss(id),
    );
  }

  /// The tip's moment: the mic was used by tapping and the box is empty
  /// again, so the mic is on screen. Called as the words change.
  void _offerMicTip() {
    const id = TeachingIds.holdMic;
    final tips = _conn.tips;
    if (!_micTipWanted ||
        _composer.text.isNotEmpty ||
        _voiceDictating ||
        _voiceConversation ||
        _conn.isIsolated ||
        !platformCapabilities.supportsVoice ||
        tips.isActive(id) ||
        !tips.due(id, maxCount: teachingEntry(id).maxCount)) {
      return;
    }
    if (offerTip(
      tips,
      id,
      visit: this,
      moment: TipMoment(
        questionWaiting: _tipMoment().questionWaiting,
        onboarding: _tipMoment().onboarding,
      ),
    )) {
      _micTipWanted = false;
    }
  }
}
