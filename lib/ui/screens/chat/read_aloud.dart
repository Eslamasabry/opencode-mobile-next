part of '../chat_screen.dart';

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
    _updateSpeech(() => _lastReadAloudFailure = failure);
    if (announce && (ModalRoute.of(context)?.isCurrent ?? true)) {
      _showComposerNote(_speechFailure(failure));
    }
  }

  Future<void> _stopReading() async {
    _readAloudRequest++;
    if (mounted) _updateSpeech(() => _readAloudRequestBusy = false);
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
    _readAloudConsented = false;
    _readAloudVoiceID = null;
    _speechOwnerScope = null;
    unawaited(_stopReading());
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
      if (!_readAloudConsented) {
        final accepted = await showConfirmSheet(
          context,
          icon: Icons.volume_up_outlined,
          title: _chatL10n(context).readAloudConsentTitle,
          message: _chatL10n(context).readAloudConsentDetail,
          confirmLabel: _chatL10n(context).readAloudContinue,
          cancelLabel: MaterialLocalizations.of(context).cancelButtonLabel,
        );
        if (!accepted || !current()) return;
        _readAloudConsented = true;
      }
      if (!current()) return;
      final speech = _readAloud ??= (ReadAloudController()
        ..addListener(_readAloudChanged));
      if (_readAloudVoiceID == null || chooseVoice) {
        final voices = await speech.voices();
        if (!mounted || !current()) return;
        if (voices.isEmpty) {
          throw const ReadAloudException(ReadAloudFailure.noOfflineVoice);
        }
        final selected = await showModalBottomSheet<ReadAloudVoice>(
          context: context,
          isScrollControlled: true,
          useSafeArea: true,
          showDragHandle: true,
          builder: (context) => SafeArea(
            top: false,
            child: ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.sizeOf(context).height * .8,
              ),
              child: ListView(
                shrinkWrap: true,
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                children: [
                  Text(
                    _chatL10n(context).readAloudChooseVoice,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  for (final voice in voices)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(voice.label),
                      subtitle: Text(voice.locale),
                      onTap: () => Navigator.pop(context, voice),
                    ),
                ],
              ),
            ),
          ),
        );
        if (selected == null || !current()) return;
        _readAloudVoiceID = selected.id;
      }
      if (!current()) return;
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
