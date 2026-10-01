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
  Future<void> _openVoice() async {
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
    try {
      await _stopReading();
      if (!current()) return;
      final voice = await _getVoice();
      if (!mounted || !current()) return;
      _listenToVoice(voice);
      if (!voice.models.isReady) {
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
      await voice.startListening();
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
