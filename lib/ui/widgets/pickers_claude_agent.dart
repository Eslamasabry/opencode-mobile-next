part of 'pickers.dart';

/// The Claude Agent trial in the model picker (lib/state/claude_agent_trial
/// .dart): a "Try Claude Agent" row among the providers that could give
/// models, one notice when it needs the person, and a Trial tag on its
/// models. Nothing shows unless [ServerCapabilities.claudeAgent] is on.
extension _ModelCatalogClaudeAgent on _ModelCatalogViewState {
  ClaudeAgentTrial get _trial => widget.controller.claudeAgentTrial;

  bool get _claudeAgentShown => widget.controller.capabilities.claudeAgent;

  Widget _claudeAgentTag() => KitStatusTag(
    label: _strings.claudeAgentTrialTag,
    tone: KitStatusTagTone.done,
  );

  /// The row while the trial is not on yet (or being set up).
  Widget? _claudeAgentRow(BuildContext context) {
    if (!_claudeAgentShown) return null;
    final state = _trial.state;
    if (state != ClaudeAgentTrialState.available &&
        state != ClaudeAgentTrialState.preparing) {
      return null;
    }
    final preparing = state == ClaudeAgentTrialState.preparing;
    return KitRow(
      key: const Key('model-picker-claude-agent'),
      leading: KitRow.icon(context, AppIconography.model),
      title: _strings.claudeAgentTryTitle,
      titleMark: _claudeAgentTag(),
      supporting: TextSpan(
        text: preparing
            ? _strings.claudeAgentPreparing
            : _strings.claudeAgentTrySupporting,
      ),
      supportingMaxLines: 3,
      enabled: !preparing,
      onTap: preparing ? null : () => unawaited(_startClaudeAgent()),
    );
  }

  /// One notice when the trial needs the person; null otherwise.
  Widget? _claudeAgentNotice() {
    if (!_claudeAgentShown) return null;
    final (String title, String message, bool agents)? words =
        switch (_trial.state) {
          ClaudeAgentTrialState.needsSignIn => (
            _strings.claudeAgentNeedsSignInTitle,
            _strings.claudeAgentNeedsSignInBody,
            true,
          ),
          ClaudeAgentTrialState.notInstalled => (
            _strings.claudeAgentNotInstalledTitle,
            _strings.claudeAgentNotInstalledBody,
            true,
          ),
          ClaudeAgentTrialState.blocked => (
            _strings.claudeAgentBlockedTitle,
            _strings.claudeAgentBlockedBody,
            false,
          ),
          ClaudeAgentTrialState.failed => (
            _strings.claudeAgentFailedTitle,
            _strings.claudeAgentFailedBody,
            false,
          ),
          _ => null,
        };
    if (words == null) return null;
    final (title, message, agents) = words;
    return KitNotice(
      key: const Key('model-picker-claude-agent-notice'),
      tone: AppStatusTone.attention,
      icon: AppIconography.info,
      title: title,
      message: message,
      actions: [
        if (agents)
          KitAction(
            key: const Key('model-picker-claude-agent-open-agents'),
            label: _strings.claudeAgentOpenAgents,
            onPressed: () => unawaited(_openAgentsForClaude()),
          ),
        KitAction(
          key: const Key('model-picker-claude-agent-check'),
          label: _strings.claudeAgentCheckAgain,
          semanticsLabel: _strings.claudeAgentCheckAgainLabel,
          onPressed: () => unawaited(_checkClaudeAgent()),
        ),
      ],
    );
  }

  Future<void> _startClaudeAgent() async {
    await _trial.start();
    _afterClaudeAgent();
  }

  Future<void> _checkClaudeAgent() async {
    await _trial.checkAgain();
    _afterClaudeAgent();
  }

  /// The existing place to install and sign in to Claude Code; checks again
  /// on the way back.
  Future<void> _openAgentsForClaude() async {
    await pushKitPage<void>(context, (_) => const AgentsScreen());
    if (!mounted) return;
    await _checkClaudeAgent();
  }

  void _afterClaudeAgent() {
    if (!mounted || _trial.state != ClaudeAgentTrialState.ready) return;
    _set(() {
      _provider = presentProvider(claudeAgentProviderId).groupID;
      _visible = _modelPage;
      _connectStatus = _strings.claudeAgentReady;
    });
  }
}
