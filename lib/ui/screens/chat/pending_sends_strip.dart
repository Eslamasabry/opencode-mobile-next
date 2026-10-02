part of '../chat_screen.dart';

// The prompts still on their way, shown above the composer.

/// Everything waiting to reach the agent, as one bubble between the
/// transcript and the composer ([KitQueuedMessage], "Waiting to send · N"):
/// offline drafts waiting for a reconnect and, on OpenCode 2, sends the
/// server accepted but has not delivered. Oldest first, each with its own
/// state words and its own menu; the bubble never acts on its own.
class _PendingSendsStrip extends StatelessWidget {
  const _PendingSendsStrip({
    super.key,
    required this.drafts,
    required this.inboxItems,
    required this.isSending,
    required this.isAcceptedUnrecorded,
    required this.receipts,
    required this.isChecking,
    required this.checkedAt,
    required this.onCheck,
    required this.onEdit,
    required this.onResend,
    required this.onRetry,
    required this.onDiscard,
    required this.onCancelInbox,
    required this.onFlipDelivery,
  });

  final List<QueuedPrompt> drafts;
  final List<Api2InboxItem> inboxItems;

  /// Whether a flush is dispatching a draft or persisting its outcome.
  final bool Function(QueuedPrompt entry) isSending;

  /// Whether the server accepted a draft's send but the device could not
  /// record it; resending it would be a certain duplicate.
  final bool Function(QueuedPrompt entry) isAcceptedUnrecorded;

  /// This conversation's command receipts by queue entry id. An entry with a
  /// receipt is checked, never resent.
  final Map<String, CommandReceipt> receipts;

  /// Whether a receipt check for the draft is running now.
  final bool Function(QueuedPrompt entry) isChecking;

  /// When the draft's last check ran without confirming it.
  final DateTime? Function(QueuedPrompt entry) checkedAt;

  /// Check again: a lookup only. Null while there is no connection.
  final ValueChanged<QueuedPrompt>? onCheck;
  final ValueChanged<QueuedPrompt> onEdit;

  /// Explicit resend of a draft whose earlier send was never confirmed.
  final ValueChanged<QueuedPrompt> onResend;

  /// Sends a draft the server refused again, now; null while there is no
  /// connection to send it on (it then waits for the reconnect).
  final ValueChanged<QueuedPrompt>? onRetry;
  final ValueChanged<QueuedPrompt> onDiscard;
  final ValueChanged<Api2InboxItem> onCancelInbox;
  final ValueChanged<Api2InboxItem> onFlipDelivery;

  /// A draft whose send left and never came back confirmed: it can be sent
  /// again (the person decides; it never resends on its own).
  bool _unconfirmed(QueuedPrompt entry) =>
      entry.dispatched &&
      !isSending(entry) &&
      !isAcceptedUnrecorded(entry) &&
      !_receiptBound(entry) &&
      !_storageStopped(entry);

  /// A draft that left with a receipt record: its arrival is checked by
  /// that record, never by sending it again.
  bool _receiptBound(QueuedPrompt entry) =>
      entry.dispatched &&
      !isSending(entry) &&
      !isAcceptedUnrecorded(entry) &&
      receipts.containsKey(entry.id);

  /// The receipt could not be stored, so the send never left: the draft
  /// stays and says so.
  bool _storageStopped(QueuedPrompt entry) =>
      entry.dispatched &&
      !isSending(entry) &&
      !receipts.containsKey(entry.id) &&
      entry.error == const CommandReceiptException().toString();

  /// A draft whose send the server refused before anything was delivered:
  /// sending it again is safe, so Retry is offered.
  bool _refused(QueuedPrompt entry) =>
      !entry.dispatched && !isSending(entry) && entry.error != null;

  KitQueuedItem _draftItem(
    BuildContext context,
    QueuedPrompt entry,
    int index,
  ) {
    final strings = _chatL10n(context);
    final sending = isSending(entry);
    final review = entry.dispatched && !sending;
    final accepted = review && isAcceptedUnrecorded(entry);
    final receipt = receipts[entry.id];
    final bound = _receiptBound(entry);
    final storage = _storageStopped(entry);
    final state = sending
        ? KitQueuedState.sending
        : accepted
        ? KitQueuedState.reachedServer
        : bound
        ? (isChecking(entry)
              ? KitQueuedState.checking
              : KitQueuedState.uncertain)
        : storage
        ? KitQueuedState.storageFull
        : review
        ? KitQueuedState.notConfirmed
        : entry.error != null
        ? KitQueuedState.failed
        : KitQueuedState.waiting;
    return KitQueuedItem(
      id: entry.id,
      key: ValueKey('queued-send-$index'),
      text: entry.text,
      state: state,
      attachmentCount: entry.attachments.length,
      // The server's words never show as copy: its plain headline only
      // (agentErrorWords), the same words the transcript uses.
      checkedAt: bound ? checkedAt(entry) : null,
      details: bound && receipt != null
          ? [
              KitTechnicalValue(
                strings.queuedReceiptDetailSentAt,
                DateTime.fromMillisecondsSinceEpoch(
                  receipt.createdAt,
                ).toIso8601String(),
              ),
              KitTechnicalValue(
                strings.queuedReceiptDetailCommand,
                receipt.commandID,
              ),
              KitTechnicalValue(
                strings.queuedReceiptDetailReceipt,
                receipt.receiptID,
              ),
            ]
          : const [],
      detailNotes: bound ? [strings.queuedReceiptDetailNote] : const [],
      reason: switch (entry.error) {
        final raw?
            when state == KitQueuedState.failed ||
                state == KitQueuedState.notConfirmed =>
          agentErrorWords(raw, strings).headline,
        _ => null,
      },
      menu: [
        if (_refused(entry) && onRetry != null)
          KitMenuItem(
            key: const ValueKey('queued-action-retry'),
            icon: AppIconography.retry,
            label: strings.queuedRetry,
            onSelected: () => onRetry!(entry),
          ),
        if (bound && onCheck != null)
          KitMenuItem(
            key: const ValueKey('queued-action-check'),
            icon: AppIconography.retry,
            label: strings.queuedRetry,
            enabled: !isChecking(entry),
            onSelected: () => onCheck!(entry),
          ),
        if (review && !accepted && !bound && !storage)
          KitMenuItem(
            key: const ValueKey('queued-action-resend'),
            icon: AppIconography.send,
            label: strings.messageViewSendAgain,
            onSelected: () => onResend(entry),
          ),
        KitMenuItem(
          key: const ValueKey('queued-action-edit'),
          icon: AppIconography.edit,
          label: strings.chatUiEditDraft,
          enabled: !sending,
          onSelected: () => onEdit(entry),
        ),
        KitMenuItem(
          key: const ValueKey('queued-action-discard'),
          icon: AppIconography.delete,
          label: strings.chatUiDiscardDraft,
          destructive: true,
          enabled: !sending,
          onSelected: () => onDiscard(entry),
        ),
      ],
    );
  }

  KitQueuedItem _inboxItem(BuildContext context, Api2InboxItem item) {
    final strings = _chatL10n(context);
    final isUser = item.type == 'user';
    final steering = item.delivery == Api2Delivery.steer;
    return KitQueuedItem(
      id: item.id,
      key: ValueKey('pending-send-${item.id}'),
      text: isUser ? (item.promptText ?? '') : '',
      state: !isUser
          ? KitQueuedState.contextUpdate
          : steering
          ? KitQueuedState.addToThisTurn
          : KitQueuedState.afterThisReply,
      menu: [
        // Only the flip that changes the current mode is offered; the server
        // has no reorder, so none is faked.
        if (isUser)
          steering
              ? KitMenuItem(
                  key: const ValueKey('inbox-action-queue'),
                  icon: AppIcons.queue,
                  label: strings.chatUiWaitForThisRunInstead,
                  onSelected: () => onFlipDelivery(item),
                )
              : KitMenuItem(
                  key: const ValueKey('inbox-action-steer'),
                  icon: AppIcons.run,
                  label: strings.chatUiSendNowAndSteerInstead,
                  onSelected: () => onFlipDelivery(item),
                ),
        if (isUser)
          KitMenuItem(
            key: const ValueKey('inbox-action-cancel'),
            icon: AppIconography.close,
            label: strings.chatUiCancelAndReturnToTheComposer,
            onSelected: () => onCancelInbox(item),
          ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final entries = <({int time, KitQueuedItem item})>[
      for (final (index, draft) in drafts.indexed)
        (time: draft.createdAt, item: _draftItem(context, draft, index)),
      for (final item in inboxItems)
        (time: item.timeCreated ?? 0, item: _inboxItem(context, item)),
    ]..sort((a, b) => a.time.compareTo(b.time));
    // The bubble's one call to action: sending again the one message whose
    // delivery is unconfirmed. With several, each item's menu offers it
    // (the host confirms each resend; a batch resend waits for its own
    // confirmation).
    final unconfirmed = drafts.where(_unconfirmed).toList();
    // Otherwise, Retry for what the server refused: safe to send again, so
    // one tap sends every refused draft (each keeps Retry in its menu).
    final refused = onRetry == null
        ? const <QueuedPrompt>[]
        : drafts.where(_refused).toList();
    final strings = _chatL10n(context);
    // A message with a receipt record is only ever checked again.
    final checkable = onCheck == null
        ? const <QueuedPrompt>[]
        : drafts
              .where((entry) => _receiptBound(entry) && !isChecking(entry))
              .toList();
    final action = checkable.length == 1
        ? KitAction(
            key: const ValueKey('queued-bubble-check'),
            icon: AppIconography.retry,
            label: strings.queuedRetry,
            onPressed: () => onCheck!(checkable.single),
          )
        : unconfirmed.length == 1
        ? KitAction(
            key: const ValueKey('queued-bubble-resend'),
            icon: AppIconography.send,
            label: strings.messageViewSendAgain,
            onPressed: () => onResend(unconfirmed.single),
          )
        : refused.isNotEmpty
        ? KitAction(
            key: const ValueKey('queued-bubble-retry'),
            icon: AppIconography.retry,
            label: refused.length == 1
                ? strings.queuedRetry
                : strings.queuedRetryAll(refused.length),
            onPressed: () {
              for (final entry in refused) {
                onRetry!(entry);
              }
            },
          )
        : null;
    return Center(
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: KitLayout.paneDetailMaxWidth,
          maxHeight:
              MediaQuery.sizeOf(context).height * KitLayout.composerMaxShare,
        ),
        child: ListView(
          // Always mounted: never claim the page's primary controller.
          primary: false,
          shrinkWrap: true,
          padding: entries.isEmpty
              ? EdgeInsets.zero
              : EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.space4,
                  vertical: tokens.space1,
                ),
          children: [
            KitQueuedMessage(
              items: [for (final entry in entries) entry.item],
              action: action,
            ),
          ],
        ),
      ),
    );
  }
}
