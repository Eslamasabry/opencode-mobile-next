part of '../chat_screen.dart';

// Command receipts on queued prompts: what the bubble knows about a send
// whose arrival was never confirmed, and the "Check again" that only looks
// (docs/design/command-receipts-contract.md). Nothing here sends.

mixin _ChatReceiptFields {
  /// Queue entries whose receipt lookup is running now.
  final Set<String> _checkingReceipts = <String>{};

  /// When each entry's last check ran and could not confirm it. In memory:
  /// the journal's schema carries no check time, so after a restart the line
  /// simply omits "Last checked".
  final Map<String, DateTime> _receiptCheckedAt = <String, DateTime>{};
}

extension _ChatReceipts on _ChatScreenState {
  /// This conversation's receipts by queue entry id. Receipts of another
  /// conversation, profile or location are never read here.
  Map<String, CommandReceipt> _queueReceipts() {
    final receipts = <String, CommandReceipt>{};
    try {
      for (final receipt in _conn.commandReceiptsFor(widget.sessionID)) {
        const prefix = 'queue:';
        if (receipt.commandID.startsWith(prefix)) {
          receipts[receipt.commandID.substring(prefix.length)] = receipt;
        }
      }
    } catch (_) {
      // An unreadable journal shows the draft as it was, never a guess.
    }
    return receipts;
  }

  /// "Check again": asks the server whether it has the message. It never
  /// clears the marker and never sends the prompt again.
  Future<void> _checkQueuedReceipt(QueuedPrompt entry) async {
    final id = entry.id;
    if (_checkingReceipts.contains(id)) return;
    _setChatState(() => _checkingReceipts.add(id));
    String? note;
    var confirmed = false;
    try {
      confirmed = await _conn.checkQueuedPromptReceipt(id);
      if (!confirmed) _receiptCheckedAt[id] = DateTime.now();
      if (!mounted) return;
      final l10n = _chatL10n(context);
      note = confirmed
          ? l10n.queuedReceiptConfirmed
          : l10n.queuedReceiptStillUncertain;
    } on CommandReceiptException {
      if (!mounted) return;
      note = _chatL10n(context).queuedReceiptStorageProblem;
    } catch (_) {
      _receiptCheckedAt[id] = DateTime.now();
      if (!mounted) return;
      note = _chatL10n(context).queuedReceiptStillUncertain;
    } finally {
      if (mounted) _setChatState(() => _checkingReceipts.remove(id));
    }
    _showComposerNote(note);
    // The message is now a normal one: show it in the transcript.
    if (confirmed) unawaited(_load());
  }
}
