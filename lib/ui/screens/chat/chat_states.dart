part of '../chat_screen.dart';

// The chat's states and its one status line, on the design kit
// (docs/design/design-standard.md §9 step 5). Everything here is drawn with
// lib/ui/kit/; test/design_standard_test.dart checks this file whole.
//
// - First load: the loading bar under the top bar (in the screen) and
//   placeholder turns ([_ChatLoadingBody]), never an empty-state text.
// - A conversation that could not load: [_ChatLoadError], a page state.
// - At most one status line under the top bar ([_chatStatus]), most
//   urgent first: the connection, then what the screen passes in (a message
//   that was not sent, a prompt the server refused, a staged revert, the
//   subagent context, sharing).

/// The conversation's first load: placeholder turns where the transcript
/// will be. The screen's loading bar says it is loading.
class _ChatLoadingBody extends StatelessWidget {
  const _ChatLoadingBody();

  @override
  Widget build(BuildContext context) =>
      const KitSkeletonTranscript(key: ValueKey('chat-loading'));
}

/// The end of the conversation as it read last time
/// (`ConnectionController.cachedSessionTail`), read-only while the live
/// history loads: the chat opens with its own words (speed contract item
/// 2). Clear of the floating composer, like the transcript it gives way to.
class _ChatOpeningExcerpt extends StatelessWidget {
  const _ChatOpeningExcerpt({required this.preview});

  final SessionTailPreview preview;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return KitTranscriptExcerpt(
      key: const ValueKey('chat-opening-excerpt'),
      labelKey: const ValueKey('chat-opening-excerpt-updated'),
      updated: LastKnownSessions.updatedLabel(l10n, preview.fetchedAt),
      bottomClearance: KitBottomInset.of(context).bottom,
      messages: [
        for (final message in preview.messages)
          KitExcerptMessage(
            key: ValueKey('chat-opening-excerpt-${message.id}'),
            text: message.text,
            fromPerson: message.role == 'user',
          ),
      ],
    );
  }
}

/// The conversation could not be loaded and nothing of it is on screen yet.
class _ChatLoadError extends StatelessWidget {
  const _ChatLoadError({
    required this.error,
    required this.onRetry,
    this.agentName,
  });

  final Object error;
  final VoidCallback onRetry;
  final String? agentName;

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    return KitStateView(
      key: const ValueKey('chat-load-error'),
      icon: AppIconography.error,
      tone: AppStatusTone.failure,
      // Every load failure draws the unplugged cable (design standard §10).
      illustration: const StatesUnpluggedScene(),
      title: l10n.chatLoadFailedTitle,
      body: agentName == null
          ? l10n.chatLoadFailedBody
          : l10n.agentNotAnsweringPhone(KitBidi.auto(agentName!)),
      primary: KitAction(
        key: const ValueKey('chat-load-retry'),
        label: l10n.commonRetry,
        onPressed: onRetry,
      ),
      // The failure surface is where a bug is found, so the report stays
      // one tap away here, as it was on the old error state.
      tertiary: [
        KitAction(
          key: const ValueKey('product-error-report-bug'),
          label: l10n.e7LibraryReportABug,
          onPressed: () => unawaited(openBugReport(context)),
        ),
      ],
      details: error.toString(),
    );
  }
}

/// What the chat's status line can say.
class _ChatStatus {
  const _ChatStatus({
    required this.id,
    required this.message,
    this.icon = AppIconography.info,
    this.tone = AppStatusTone.neutral,
    this.supporting,
    this.action,
    this.more = const [],
    this.onDismiss,
    this.dismissTooltip,
    this.key,
    this.messageKey,
    this.supportingKey,
  });

  /// Stable name of the kind of status, for keys and tests.
  final String id;
  final String message;
  final IconData icon;
  final AppStatusTone tone;
  final String? supporting;
  final KitAction? action;
  final List<KitAction> more;
  final VoidCallback? onDismiss;
  final String? dismissTooltip;
  final Key? key;
  final Key? messageKey;
  final Key? supportingKey;

  KitStatus get status => KitStatus(
    kind: KitStatusKind.work,
    id: 'chat:$id',
    key: key ?? ValueKey('chat-status-$id'),
    icon: icon,
    tone: tone,
    message: message,
    messageKey: messageKey,
    supporting: supporting,
    supportingKey: supportingKey,
    action: action,
    more: more,
    onDismiss: onDismiss,
    dismissTooltip: dismissTooltip,
  );
}

/// The chat's one error-details path (the prompt error on the status line,
/// a message that was not sent): the failure named in words as the title,
/// the server's exact text whole, selectable and copyable
/// ([showKitTechnicalDetails], the same sheet the transcript's error row
/// opens).
Future<void> _showChatErrorDetails(
  BuildContext context, {
  required String title,
  required String text,
}) => showKitTechnicalDetails(
  context,
  title: title,
  text: text,
  sheetKey: const ValueKey('chat-error-details'),
);

/// An agent or server failure in the app's words: the recognised cause,
/// else what its [kind] means, else one plain line. The server's own text
/// is only ever shown under Details (owner rule: no raw error text as copy).
String _plainErrorHeadline(
  AgentErrorWords words,
  MessageErrorKind kind,
  AppLocalizations l10n,
) {
  if (words.humanized) return words.headline;
  return switch (kind) {
    MessageErrorKind.modelNotFound => l10n.chatErrorModelNotFound,
    MessageErrorKind.contextOverflow => l10n.chatErrorContextOverflow,
    MessageErrorKind.providerAuth => l10n.chatErrorProviderAuth,
    MessageErrorKind.outputLength => l10n.chatErrorOutputLength,
    MessageErrorKind.contentFilter => l10n.chatErrorContentFilter,
    MessageErrorKind.aborted ||
    MessageErrorKind.unknown => l10n.chatErrorUnknown,
  };
}

/// The model a "model not found" error suggests ("Model not found:
/// openai/gpt-5.6. Did you mean: gpt-5.6-pro?"), when this server's catalog
/// has it enabled: the first suggestion that resolves, in the server's
/// order. A bare suggestion is looked up under the failing model's
/// provider first, then any provider. Null when nothing resolves.
CatalogModel? _suggestedModel(String raw, List<CatalogModel> models) {
  final offer = RegExp(
    r'did you mean:?\s*([^?\n]+)',
    caseSensitive: false,
  ).firstMatch(raw);
  if (offer == null) return null;
  final failed = RegExp(
    r'not found:?\s*([^\s/]+)/(\S+?)\.?(?:\s|$)',
    caseSensitive: false,
  ).firstMatch(raw);
  final failing = failed?.group(1);
  for (final piece in offer.group(1)!.split(RegExp(r',|\s+or\s+'))) {
    final name = piece.trim().replaceAll(RegExp('^[`\'"]+|[`\'".]+\$'), '');
    if (name.isEmpty) continue;
    final slash = name.indexOf('/');
    final provider = slash > 0 ? name.substring(0, slash) : failing;
    final id = slash > 0 ? name.substring(slash + 1) : name;
    // Never the model that just failed (the composite-id case suggests the
    // same model under its bare id).
    if (provider == failing && id == failed?.group(2)) continue;
    final usable = models.where((model) => model.enabled && model.id == id);
    final match =
        usable.where((model) => model.providerID == provider).firstOrNull ??
        (slash > 0 ? null : usable.firstOrNull);
    if (match != null) return match;
  }
  return null;
}

/// A prompt the server refused, or a session-level failure with no home in
/// the transcript: the plain sentence, what to do, the fix when there is one
/// (a model the server does not know: the model it suggests, one tap, and
/// the prompt goes again; or send it again as it was), and the server's
/// words under Details.
_ChatStatus _promptErrorStatus(
  BuildContext context, {
  required String message,
  required VoidCallback onDismiss,
  VoidCallback? onChooseModel,
  VoidCallback? onResend,
  String? suggestion,
  VoidCallback? onUseSuggestion,
}) {
  final l10n = _chatL10n(context);
  final words = agentErrorWords(message, l10n);
  final kind = MessageErrorKind.refineFromText(
    MessageErrorKind.unknown,
    message,
  );
  final headline = _plainErrorHeadline(words, kind, l10n);
  final details = message.trim().isNotEmpty
      ? KitAction(
          key: const ValueKey('prompt-error-details'),
          label: l10n.chatUiDetails,
          onPressed: () => unawaited(
            _showChatErrorDetails(context, title: headline, text: message),
          ),
        )
      : null;
  final modelMissing = kind == MessageErrorKind.modelNotFound;
  final use = modelMissing && suggestion != null && onUseSuggestion != null
      ? KitAction(
          key: const ValueKey('prompt-error-use-suggestion'),
          label: l10n.chatUiUseModelAndResend(suggestion),
          onPressed: onUseSuggestion,
        )
      : null;
  final choose = modelMissing && onChooseModel != null
      ? KitAction(
          key: const ValueKey('prompt-error-choose-model'),
          label: use == null
              ? l10n.chatUiChooseModel
              : l10n.chatUiChooseAnotherModel,
          onPressed: onChooseModel,
        )
      : null;
  final resend =
      !modelMissing &&
          kind != MessageErrorKind.contextOverflow &&
          kind != MessageErrorKind.providerAuth &&
          kind != MessageErrorKind.contentFilter &&
          onResend != null
      ? KitAction(
          key: const ValueKey('prompt-error-resend'),
          label: l10n.chatUiSendPromptAgain,
          onPressed: onResend,
        )
      : null;
  return _ChatStatus(
    id: 'prompt-error',
    key: const ValueKey('prompt-error-banner'),
    icon: AppIconography.error,
    tone: AppStatusTone.failure,
    message: headline,
    messageKey: const ValueKey('prompt-error-headline'),
    supporting: words.hint,
    supportingKey: const ValueKey('prompt-error-hint'),
    action: use ?? choose ?? resend ?? details,
    more: [
      if (use != null) ?choose,
      if (use != null || choose != null || resend != null) ?details,
    ],
    onDismiss: onDismiss,
    dismissTooltip: l10n.chatUiDismissPromptError,
  );
}

/// A message that did not reach the server. Its text is back in the
/// message box, so sending it again is the composer's own Send.
_ChatStatus _sendErrorStatus(
  BuildContext context, {
  required Object error,
  required VoidCallback onDismiss,
}) {
  final l10n = _chatL10n(context);
  final raw = productErrorText(error, l10n: l10n);
  final words = agentErrorWords(raw, l10n);
  final reason = words.headline.trim();
  return _ChatStatus(
    id: 'send-error',
    icon: AppIconography.error,
    tone: AppStatusTone.failure,
    message: l10n.chatSendFailed,
    supporting: reason.isEmpty ? l10n.chatSendFailedKept : reason,
    supportingKey: const ValueKey('chat-send-error-reason'),
    action: words.humanized || errorHasDetails(raw)
        ? KitAction(
            key: const ValueKey('chat-send-error-details'),
            label: l10n.chatUiDetails,
            onPressed: () => unawaited(
              _showChatErrorDetails(
                context,
                title: l10n.chatSendFailed,
                text: raw,
              ),
            ),
          )
        : null,
    onDismiss: onDismiss,
  );
}

/// A revert that waits for review before it is applied.
_ChatStatus _stagedRevertStatus(
  BuildContext context, {
  required VoidCallback onReview,
}) {
  final l10n = _chatL10n(context);
  return _ChatStatus(
    id: 'staged-revert',
    icon: AppIconography.history,
    message: l10n.revertStaged,
    action: KitAction(
      key: const ValueKey('chat-status-revert-review'),
      label: l10n.revertReview,
      onPressed: onReview,
    ),
  );
}

/// An undo the server applied at once (no review step): it holds until the
/// next prompt, so the way back sits on the line.
_ChatStatus _undoneStatus(
  BuildContext context, {
  required VoidCallback onPutBack,
}) {
  final l10n = _chatL10n(context);
  return _ChatStatus(
    id: 'undone',
    icon: AppIconography.history,
    message: l10n.undoneStatus,
    action: KitAction(
      key: const ValueKey('chat-status-undo-put-back'),
      label: l10n.undonePutBack,
      icon: AppIconography.restore,
      onPressed: onPutBack,
    ),
  );
}

/// This conversation is one another agent delegated: where it sits among
/// its siblings, the way back to the conversation that delegated it, and
/// the siblings behind More.
_ChatStatus _subagentStatus(
  BuildContext context, {
  required int? position,
  required int? total,
  required Future<void> Function() onParent,
  required Future<void> Function() onAll,
}) {
  final l10n = _chatL10n(context);
  return _ChatStatus(
    id: 'subagent',
    icon: AppIconography.nested,
    message: position != null && total != null
        ? l10n.chatUiSubagentCount(l10n.chatUiPositionOfTotal(position, total))
        : l10n.chatUiDelegatedSession,
    action: KitAction(
      key: const ValueKey('subagent-parent-session'),
      label: l10n.chatUiOpenParentSession,
      onPressed: () => unawaited(onParent()),
    ),
    more: [
      KitAction(
        key: const ValueKey('subagent-session-list'),
        label: l10n.chatUiShowAllSubagentSessions,
        onPressed: () => unawaited(onAll()),
      ),
    ],
  );
}

/// The conversation is shared by link, on one line: who can see it, Copy
/// link (what other people open), and Stop sharing behind More (it asks
/// first; also in the conversation menu).
_ChatStatus _sharedStatus(
  BuildContext context, {
  required String url,
  required VoidCallback onStop,
}) {
  final l10n = _chatL10n(context);
  return _ChatStatus(
    id: 'shared',
    icon: AppIconography.globe,
    message: l10n.chatUiSharedAnyoneWithTheLinkCanView,
    action: KitAction(
      key: const ValueKey('chat-status-copy-share-link'),
      label: l10n.chatUiCopyShareLink,
      // Redacted like any server text: a plain share address is unchanged.
      onPressed: () => unawaited(KitCopy.copy(context, url)),
    ),
    more: [
      KitAction(
        key: const ValueKey('chat-status-stop-sharing'),
        label: l10n.chatUiStopSharing,
        onPressed: onStop,
      ),
    ],
  );
}

/// The drafts that wait (the connection-status unification left this
/// unsaid): offline, the drafts this server's next reconnect sends, those
/// whose send was never confirmed, and those waiting for other servers; on
/// a connected server that keeps a queue, the ones waiting for other
/// servers with the way to move them here ([onMove], one action per source
/// server). Null when nothing waits.
_ChatStatus? _queuedDraftsStatus(
  BuildContext context,
  ConnectionController conn, {
  required void Function(ServerProfile source) onMove,
}) {
  if (conn.isIsolated) return null;
  final l10n = _chatL10n(context);
  final review = conn.queuedPromptReviewCount;
  final mine = conn.queuedPromptCount - review;
  final others = conn.queuedPromptCountForOtherProfiles;
  final offline = !conn.isConnected;
  final destination = conn.queuedPromptMoveDestination;
  final parts = <String>[
    if (offline && mine > 0) l10n.chatUiDraftsQueued(mine),
    if (offline && review > 0) l10n.queuedBannerReview(review),
    if (others > 0 && (offline || destination != null))
      l10n.chatUiOtherDraftsWaiting(others),
  ];
  if (parts.isEmpty) return null;
  final profiles = conn.store.profiles;
  final moves = [
    if (destination != null)
      for (final source in profiles)
        if (source.id != conn.profile?.id &&
            conn.queuedPromptCountForProfile(source.id) > 0)
          KitAction(
            key: ValueKey('chat-status-move-queued-${source.id}'),
            label: l10n.serverRowMoveQueued(
              conn.queuedPromptCountForProfile(source.id),
              serverDisplayName(destination, l10n, among: profiles),
            ),
            onPressed: () => onMove(source),
          ),
  ];
  return _ChatStatus(
    id: 'queued-drafts',
    icon: AppIconography.queueAdd,
    message: parts.join(' '),
    action: moves.firstOrNull,
    more: moves.skip(1).toList(),
  );
}

/// Local chat actions join the shared slot below app-wide conditions.
KitStatus? _chatStatus(Iterable<_ChatStatus?> statuses) {
  for (final status in statuses) {
    if (status != null) return status.status;
  }
  return null;
}
