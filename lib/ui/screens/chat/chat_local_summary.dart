part of '../chat_screen.dart';

/// "Summarize on this phone": the conversation's words, in order, handed to
/// the on-device model. Only what was said is sent (no tool output, no
/// reasoning); an assistant reply is masked like every other copy.
extension _ChatLocalSummary on _ChatScreenState {
  String _localSummaryTranscript() {
    final l10n = _chatL10n(context);
    final out = StringBuffer();
    for (final message in _visibleHistory) {
      if (message.info.id.startsWith('local-')) continue;
      final own = message.info.role == 'user';
      final text = message.parts
          .where((part) => part.type == 'text' && part.text.trim().isNotEmpty)
          .map((part) => part.text.trim())
          .join('\n');
      if (text.isEmpty) continue;
      out.write(
        '${own ? l10n.chatUiUser : l10n.chatUiAssistant}: '
        '${own ? text : KitRedact.text(text)}\n\n',
      );
    }
    return out.toString().trim();
  }

  /// Reads the assistant's state so the menu knows whether to offer the
  /// summary; a phone that cannot run it never shows the entry.
  void _loadLocalAssistant() {
    if (!platformCapabilities.supportsVoice) return;
    LocalAssistantController.shared().then((_) {
      if (mounted) _setChatState(() {});
    }, onError: (Object _) {});
  }

  Future<void> _summarizeOnThisPhone() async {
    final transcript = _localSummaryTranscript();
    if (transcript.isEmpty) return;
    final controller = await LocalAssistantController.shared();
    if (!mounted || !controller.offersSummary) return;
    await showLocalSummarySheet(context, controller, transcript);
  }
}
