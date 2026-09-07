part of '../chat_screen.dart';

extension _ChatVoiceConversation on _ChatScreenState {
  bool get _conversationCanSend =>
      _conn.status == StreamStatus.connected &&
      !_conn.busySessions.contains(widget.sessionID) &&
      _conn.permissionsForSession(widget.sessionID).isEmpty &&
      _conn.questionForSession(widget.sessionID) == null &&
      _conn.formForSession(widget.sessionID) == null &&
      _conn.isProfileReadable(_conn.promptShelfProfileID);

  String get _conversationPauseCopy =>
      _chatL10n(context).voiceConversationPausedDetail;

  Future<void> _startVoiceConversation() async {
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
    if (_voiceConversation) {
      // Clear while the persistence guard is still active.
      _composer.clear();
      _draftSaveTimer?.cancel();
      _updateSpeech(() => _voiceConversation = false);
    }
  }

  Widget _voiceConversationControls() => Padding(
    padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          liveRegion: true,
          label: _conversationCanSend
              ? _chatL10n(context).voiceConversationTitle
              : _conversationPauseCopy,
          excludeSemantics: true,
          child: Text(
            _conversationCanSend
                ? _chatL10n(context).voiceConversationTitle
                : _chatL10n(context).voiceConversationPausedTitle,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        Wrap(
          spacing: 8,
          children: [
            TextButton.icon(
              style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
              onPressed: _voiceOpening || _sending || !_conversationCanSend
                  ? null
                  : _openVoice,
              icon: const Icon(Icons.mic_none_rounded),
              label: Text(_chatL10n(context).voiceConversationListen),
            ),
            TextButton.icon(
              style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
              onPressed: _interruptVoiceConversation,
              icon: const Icon(Icons.close_rounded),
              label: Text(_chatL10n(context).voiceConversationExit),
            ),
          ],
        ),
      ],
    ),
  );
}
