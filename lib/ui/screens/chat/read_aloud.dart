part of '../chat_screen.dart';

/// Where this phone remembers the read-aloud consent (app-wide: the speech
/// engine is the phone's, whatever server the reply came from).
const _readAloudConsentKey = 'oc.readAloudConsent';

extension _ChatReadAloud on _ChatScreenState {
  Object get _speechScopeNow {
    final profile = _conn.profile;
    return (
      widget.sessionID,
      _conn.promptShelfProfileID,
      _conn.locationRevision,
      profile?.baseUrl,
      profile?.username,
      profile?.password,
      _conn.isProfileReadable(_conn.promptShelfProfileID),
      _conn.sessionsById.containsKey(widget.sessionID),
    );
  }

  bool _canReadReply(MessageWithParts message) =>
      !_conn.isIsolated &&
      platformCapabilities.supportsReadAloud &&
      !_voiceOpening &&
      (!_voiceConversation || _conversationCanSend) &&
      !_readAloudRequestBusy &&
      _conn.isProfileReadable(_conn.promptShelfProfileID) &&
      message.info.role == 'assistant' &&
      _ChatScreenState._messageText(message).trim().isNotEmpty;

  String _speechFailure(ReadAloudFailure failure) => switch (failure) {
    ReadAloudFailure.unsupported => _chatL10n(context).readAloudUnsupported,
    ReadAloudFailure.noOfflineVoice => _chatL10n(context).readAloudNoVoice,
    ReadAloudFailure.engineUnavailable => _chatL10n(
      context,
    ).readAloudUnavailable,
    ReadAloudFailure.tooLong => _chatL10n(context).readAloudTooLong,
    ReadAloudFailure.busy => _chatL10n(context).readAloudBusy,
  };

  void _readAloudChanged() {
    if (!mounted) return;
    final failure = _readAloud?.failure;
    final announce = failure != null && failure != _lastReadAloudFailure;
    _updateSpeech(() {
      _lastReadAloudFailure = failure;
      if (_voiceReplyPlayback && failure != null) {
        _voiceReplyState = _VoiceReplyState.failed;
      }
      if (_readAloud?.speaking != true) _voiceReplyPlayback = false;
    });
    if (announce && (ModalRoute.of(context)?.isCurrent ?? true)) {
      _showComposerNote(_speechFailure(failure));
    }
  }

  Future<void> _stopReading() async {
    _readAloudRequest++;
    if (mounted) {
      _updateSpeech(() {
        _readAloudRequestBusy = false;
        _voiceReplyWatch = null;
        _voiceReplyPlayback = false;
        _voiceReplyState = _VoiceReplyState.idle;
      });
    }
    await _readAloud?.stop();
  }

  void _readAloudScopeChanged() {
    if (_voiceConversation &&
        !_conversationCanSend &&
        (_readAloudRequestBusy || _readAloud?.speaking == true)) {
      unawaited(_stopReading());
    }
    if (_voiceOwnerScope != null && _voiceOwnerScope != _speechScopeNow) {
      _interruptVoiceConversation();
    } else if (_voiceConversation && !_conversationCanSend && _voiceOpening) {
      _voiceEpoch.value++;
      unawaited(_voice?.cancel());
    }
    if (_speechOwnerScope == null || _speechOwnerScope == _speechScopeNow) {
      return;
    }
    _readAloudVoiceID = null;
    _speechOwnerScope = null;
    // Consent is the phone's and stays; automatic reading of replies was
    // turned on for the old server/session and lapses with it.
    _revokeVoiceSpeakReplies();
    unawaited(_stopReading());
  }

  /// Consent and voice choice, in that order, each an explicit sheet. True
  /// when both are in hand and [current] still holds; false when the user
  /// declined or the scope moved. Never touches the engine before consent.
  Future<bool> _ensureSpeechReady({
    required bool Function() current,
    bool chooseVoice = false,
  }) async {
    if (!_readAloudConsented) {
      _speechSheetOpen = true;
      bool accepted;
      try {
        final l10n = _chatL10n(context);
        // Two sentences; the caveats are the consequences under them.
        accepted = await showKitConfirm(
          context,
          icon: AppIconography.volume,
          title: l10n.readAloudConsentTitle,
          body: l10n.readAloudConsentDetail,
          consequences: [
            l10n.readAloudConsentEngine,
            l10n.readAloudConsentHeard,
          ],
          confirmLabel: l10n.readAloudContinue,
          sheetKey: const ValueKey('read-aloud-consent'),
        );
      } finally {
        _speechSheetOpen = false;
      }
      if (!accepted || !current()) return false;
      await _conn.store.prefs.setBool(_readAloudConsentKey, true);
    }
    if (!current()) return false;
    final speech = _readAloud ??= (ReadAloudController()
      ..addListener(_readAloudChanged));
    if (_readAloudVoiceID == null || chooseVoice) {
      final voices = await speech.voices();
      if (!mounted || !current()) return false;
      if (voices.isEmpty) {
        throw const ReadAloudException(ReadAloudFailure.noOfflineVoice);
      }
      // P10.4: the voice follows the app's language; the choice sheet
      // opens only for "Choose voice", or when no voice speaks it.
      if (!chooseVoice) {
        final byLocale = readAloudVoiceForLocale(
          voices,
          Localizations.localeOf(context),
        );
        if (byLocale != null) {
          _readAloudVoiceID = byLocale.id;
          return current();
        }
      }
      _speechSheetOpen = true;
      ReadAloudVoice? selected;
      try {
        // The current voice is checked; a tap chooses and closes.
        final id = await showKitChoiceSheet<String>(
          context,
          title: _chatL10n(context).readAloudChooseVoice,
          sheetKey: const ValueKey('read-aloud-voices'),
          selected: _readAloudVoiceID,
          choices: [
            for (final voice in voices)
              KitChoice(
                key: ValueKey('read-aloud-voice-${voice.id}'),
                value: voice.id,
                title: voice.label,
              ),
          ],
        );
        selected = voices.where((voice) => voice.id == id).firstOrNull;
      } finally {
        _speechSheetOpen = false;
      }
      if (selected == null || !current()) return false;
      _readAloudVoiceID = selected.id;
    }
    return current();
  }

  Future<void> _readReply(
    MessageWithParts message, {
    bool chooseVoice = false,
  }) async {
    if (!_canReadReply(message)) return;
    final source = _speechScopeNow;
    final route = ModalRoute.of(context);
    final request = ++_readAloudRequest;
    bool current() =>
        mounted &&
        request == _readAloudRequest &&
        source == _speechScopeNow &&
        (route?.isCurrent ?? true);
    _speechOwnerScope = source;
    _updateSpeech(() => _readAloudRequestBusy = true);
    try {
      // Never fall back to copy/export text, which can include other part kinds.
      final prose = markdownProseForSpeech(
        _ChatScreenState._messageText(message),
      );
      if (prose.isEmpty) {
        _showComposerNote(_chatL10n(context).readAloudNoProse);
        return;
      }
      if (!await _ensureSpeechReady(
        current: current,
        chooseVoice: chooseVoice,
      )) {
        return;
      }
      final speech = _readAloud!;
      await speech.speak(
        '${widget.sessionID}/${message.info.id}',
        prose,
        voiceID: _readAloudVoiceID,
      );
      if (!current()) await speech.stop();
    } on FormatException {
      if (mounted && current()) {
        _showComposerNote(_chatL10n(context).readAloudTooLong);
      }
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
}

mixin _ChatReadAloudFields {
  ReadAloudController? _readAloud;
  Object? _speechOwnerScope;
  bool _readAloudRequestBusy = false;
  int _readAloudRequest = 0;
  String? _readAloudVoiceID;
  ReadAloudFailure? _lastReadAloudFailure;
}

extension _ChatReadAloudConsent on _ChatScreenState {
  /// Consent to hand reply prose to the phone's speech engine: asked once
  /// and remembered on this phone (the engine is the phone's, not a
  /// server's), not asked again in every conversation or after a restart.
  bool get _readAloudConsented =>
      _conn.store.prefs.getBool(_readAloudConsentKey) ?? false;
}
