part of '../chat_screen.dart';

/// The assistant error, keyed on the server's typed error: overflow, auth,
/// length and model errors get the one action that fixes them, named for
/// what it acts on; anything else says what happened. Every error offers
/// its exact server words ("Error details"), which can be copied (P8.3).
/// A stop is not an error: the turn says "You stopped this reply."
class _AssistantErrorRow extends StatelessWidget {
  const _AssistantErrorRow({
    required this.info,
    this.recovered = false,
    this.onCompact,
    this.onOpenProviders,
    this.providersLabel,
    this.onContinue,
    this.onChooseModel,
    this.onResend,
    this.suggestion,
    this.onUseSuggestion,
  });

  final MessageInfo info;

  /// True when the turn went on after this error (the server retried, or the
  /// agent took another step). It is then a line in the story, not an alarm.
  final bool recovered;
  final VoidCallback? onCompact;
  final VoidCallback? onOpenProviders;

  /// The sign-in action's words where it is not "Open providers" (an agent
  /// on this phone signs in on its own: "Sign in with Claude Code").
  final String? providersLabel;
  final VoidCallback? onContinue;
  final VoidCallback? onChooseModel;

  /// Sends the turn's prompt again as it was: given only for the newest
  /// turn when it got no answer and the prompt is words alone.
  final VoidCallback? onResend;

  /// The model the server suggested in a "model not found" error, by name,
  /// when this server has it; [onUseSuggestion] switches to it and resends.
  final String? suggestion;
  final VoidCallback? onUseSuggestion;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final raw = info.errorText ?? '';
    final words = agentErrorWords(raw, strings);
    final kind = MessageErrorKind.refineFromText(
      info.errorKind ?? MessageErrorKind.unknown,
      raw,
    );
    // Words, never the server's text: that is under Error details.
    final text = _plainErrorHeadline(words, kind, strings);
    final useSuggestion = onUseSuggestion;
    final named = suggestion;
    final (String id, KitAction? fix) = switch (kind) {
      MessageErrorKind.modelNotFound => (
        'model-not-found',
        // The server named a model it has: one tap switches to it and sends
        // the prompt again (review board: prompt error).
        useSuggestion != null && named != null && !recovered
            ? KitAction(
                key: const Key('error-action-use-suggestion'),
                label: strings.chatUiUseModelAndResend(named),
                onPressed: useSuggestion,
              )
            : onChooseModel == null
            ? null
            : KitAction(
                key: const Key('error-action-choose-model'),
                label: strings.chatUiChooseModel,
                onPressed: onChooseModel,
              ),
      ),
      MessageErrorKind.contextOverflow => (
        'context-overflow',
        onCompact == null
            ? null
            : KitAction(
                key: const Key('error-action-compact'),
                label: strings.chatUiCompactSession,
                onPressed: onCompact,
              ),
      ),
      MessageErrorKind.providerAuth => (
        'provider-auth',
        onOpenProviders == null
            ? null
            : KitAction(
                key: const Key('error-action-providers'),
                label: providersLabel ?? strings.chatUiOpenProviders,
                onPressed: onOpenProviders,
              ),
      ),
      MessageErrorKind.outputLength => (
        'output-length',
        onContinue == null
            ? null
            : KitAction(
                key: const Key('error-action-continue'),
                label: strings.messageViewContinueReply,
                onPressed: onContinue,
              ),
      ),
      // Nothing to fix first: the same prompt can simply go again.
      _ => (
        'generic',
        onResend == null || recovered || kind == MessageErrorKind.contentFilter
            ? null
            : KitAction(
                key: const Key('error-action-resend'),
                label: strings.chatUiSendPromptAgain,
                onPressed: onResend,
              ),
      ),
    };
    final hint =
        kind == MessageErrorKind.contentFilter ||
            kind == MessageErrorKind.unknown
        ? (recovered ? strings.agentErrorRecovered : words.hint)
        : null;
    return KeyedSubtree(
      key: Key('error-card-$id'),
      child: KitNotice(
        // The kit's one error glyph for every kind (map Fix: no per-kind
        // icon in the error tone).
        tone: recovered ? AppStatusTone.neutral : AppStatusTone.failure,
        message: text,
        liveRegion: false,
        notes: [?hint],
        actions: [
          ?fix,
          KitAction(
            key: const Key('error-action-details'),
            label: strings.chatUiErrorDetails,
            onPressed: () => unawaited(
              _showChatErrorDetails(
                context,
                title: strings.chatUiErrorDetails,
                text: raw.trim().isEmpty ? text : raw,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
