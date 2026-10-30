part of '../chat_screen.dart';

/// The assistant error, keyed on the server's typed error: overflow, auth,
/// length and model errors get the one action that fixes them, named for
/// what it acts on; anything else says what happened. A model's usage limit
/// offers another model. Every error offers its exact server words under a
/// quiet "Details" link, which can be copied (P8.3).
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
    this.onContinueWithAnother,
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

  /// Opens "Continue with…": a quiet second way out of a provider failure
  /// (never for a refusal by the content filter, never automatic).
  final VoidCallback? onContinueWithAnother;

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
    // An agent on this phone that failed the turn and said why ("[System
    // Error] Internal error: You need to sign in to use this model."): the
    // same kinds and fixes as a refused send, under the agent's own name.
    final chat = context.findAncestorStateOfType<_ChatScreenState>();
    final run = chat?._agentRunFor(info);
    final agentKind = run == null || run.kind == AgentRunFailureKind.unknown
        ? null
        : run.kind;
    // Signed in already, and this model needs the agent's account.
    final needsAccount = run != null && (chat?._accountNeeded(run) ?? false);
    // Words, never the server's text: that is under Error details.
    final company = agentModelCompany(run?.provider);
    final agentName = agentRunFailureAgentName(run?.provider, strings);
    final text = agentKind == null
        ? _plainErrorHeadline(words, kind, strings)
        // The turn stopped there ("API Error: Can't reach the API server"):
        // said in full, with Continue, unless the agent went on after it.
        : agentKind == AgentRunFailureKind.connectionLost && !recovered
        ? (company == null
              ? strings.chatAgentTurnLostConnection(agentName)
              : strings.chatAgentTurnLostConnectionTo(agentName, company))
        : agentRunFailureWords(run!, strings, accountNeeded: needsAccount);
    final useSuggestion = onUseSuggestion;
    final named = suggestion;
    final choose = onChooseModel == null || recovered
        ? null
        : KitAction(
            key: const Key('error-action-choose-model'),
            label: strings.chatUiChooseAnotherModel,
            onPressed: onChooseModel,
          );
    // "This model's usage limit is reached": the way on is another model,
    // not the same prompt to the same model again.
    final usageLimit =
        agentKind == null &&
        classifyAgentError(raw) == AgentErrorCause.usageLimit;
    final signIn = run == null || recovered ? null : chat?._agentSignIn(run);
    // Another model first, then the agent's own sign-in by name; Error
    // details moves to the reply's menu (a notice shows two actions).
    final signInAgain = needsAccount && signIn != null
        ? KitAction(
            key: const Key('error-action-agent-sign-in'),
            label: strings.chatSendFailureSignInToAgent(
              agentRunFailureAgentName(run.provider, strings),
            ),
            onPressed: signIn,
          )
        : null;
    final (String id, KitAction? fix) = switch (agentKind) {
      AgentRunFailureKind.signIn when needsAccount => (
        'agent-account',
        choose ?? signInAgain,
      ),
      AgentRunFailureKind.signIn || AgentRunFailureKind.noCredit => (
        'agent-${agentKind!.name}',
        signIn == null
            ? choose
            : KitAction(
                key: const Key('error-action-agent-sign-in'),
                label: agentKind == AgentRunFailureKind.noCredit
                    ? strings.chatSendFailureAccountAction
                    : strings.chatSendFailureSignInAction,
                onPressed: signIn,
              ),
      ),
      AgentRunFailureKind.modelUnavailable => ('agent-model', choose),
      // Asking the agent to continue picks up where the turn stopped.
      AgentRunFailureKind.connectionLost => (
        'agent-connection',
        onContinue == null || recovered
            ? null
            : KitAction(
                key: const Key('error-action-continue'),
                label: strings.returnBriefContinue,
                onPressed: onContinue,
              ),
      ),
      AgentRunFailureKind.busy => (
        'agent-busy',
        onResend == null || recovered
            ? null
            : KitAction(
                key: const Key('error-action-resend'),
                label: strings.chatUiSendPromptAgain,
                onPressed: onResend,
              ),
      ),
      _ when usageLimit && choose != null => ('usage-limit', choose),
      _ => _typedFix(strings, kind, useSuggestion, named),
    };
    final hint =
        agentKind == null &&
            (kind == MessageErrorKind.contentFilter ||
                kind == MessageErrorKind.unknown)
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
          if (signInAgain != null && !identical(fix, signInAgain)) signInAgain,
          // A notice shows two actions. When nothing else fixes the failure
          // this is the way on; otherwise it waits in the reply's menu and
          // the conversation menu.
          if (fix == null &&
              onContinueWithAnother != null &&
              !recovered &&
              kind != MessageErrorKind.contentFilter)
            KitAction(
              key: const Key('error-action-continue-with'),
              label: strings.continueWithAnother,
              onPressed: onContinueWithAnother,
            ),
        ],
        // A way to look, not a fix: a quiet link after the fix.
        link: KitAction(
          key: const Key('error-action-details'),
          label: strings.chatUiDetails,
          onPressed: () => unawaited(
            _showErrorRowDetails(context, info: info, run: run, text: text),
          ),
        ),
      ),
    );
  }

  /// The fix a typed server error names (model, overflow, sign-in, length),
  /// or Send again when nothing else fixes it.
  (String, KitAction?) _typedFix(
    AppLocalizations strings,
    MessageErrorKind kind,
    VoidCallback? useSuggestion,
    String? named,
  ) => switch (kind) {
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
}

/// An error row's Details: the agent, model and code beside the host's whole
/// words ("Internal error: …" kept here, never in the row's words), or the
/// server's text.
Future<void> _showErrorRowDetails(
  BuildContext context, {
  required MessageInfo info,
  required AgentRunFailure? run,
  required String text,
}) {
  final strings = _chatL10n(context);
  final raw = info.errorText ?? '';
  final technical = run == null ? null : agentRunFailureDetails(run, strings);
  return showKitTechnicalDetails(
    context,
    title: strings.chatUiErrorDetails,
    values: technical?.values ?? const [],
    text: technical?.text ?? (raw.trim().isEmpty ? text : raw),
    sheetKey: const ValueKey('chat-error-details'),
  );
}
