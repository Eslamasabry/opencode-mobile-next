part of '../library_screen.dart';

/// How each list of the page stands when it is not simply loaded: waiting
/// for the connection, not offered by this server, or failed with its own
/// reason (issue #124: a lone "Could not load this section" told nobody
/// what went wrong, and showed for conditions that are not failures).
extension _IntegrationsLoadStates on _IntegrationsScreenState {
  /// A list the server does not have (an older or newer server without the
  /// route) is an expected condition: the section says so calmly.
  static bool _unsupported(Object? error) =>
      error != null && listLoadProblemOf(error) == ListLoadProblem.unsupported;

  bool get _providersFailed =>
      _showProviders &&
      !_waitingForConnection &&
      _integrationError != null &&
      !_unsupported(_integrationError);
  bool get _serversFailed =>
      _showMcp &&
      !_waitingForConnection &&
      _serverError != null &&
      !_unsupported(_serverError);
  bool get _resourcesFailed =>
      _showMcp &&
      !_waitingForConnection &&
      _resourceError != null &&
      !_unsupported(_resourceError);

  /// The first section that failed, in page order; null when none did.
  _Section? get _firstFailed => _providersFailed
      ? _Section.providers
      : _serversFailed
      ? _Section.servers
      : _resourcesFailed
      ? _Section.resources
      : null;

  /// The first section the page shows: it carries the one waiting line.
  _Section get _firstShown =>
      _showProviders ? _Section.providers : _Section.servers;

  /// The words for why a list did not load: the section's own reason in
  /// plain words, never the server's or the exception's text (that goes to
  /// Details).
  String _loadFailureReason(Object error) {
    final l10n = _l10n;
    final cause = switch (error) {
      ProductException(:final cause?) => cause,
      _ => error,
    };
    return switch (listLoadProblemOf(error)) {
      ListLoadProblem.folderMissing => l10n.integrationsFolderMissing,
      ListLoadProblem.signIn ||
      ListLoadProblem.unreachable ||
      ListLoadProblem.timedOut => productErrorText(cause, l10n: l10n),
      // A gateway's own sentence is English-only; a cause the app could
      // not sort says only that the list did not come.
      ListLoadProblem.failed || ListLoadProblem.unsupported =>
        cause is ProductException ||
                ProductFailure.from(cause).category ==
                    ProductFailureCategory.unknown
            ? l10n.integrationsListFailedBody
            : productErrorText(cause, l10n: l10n),
    };
  }

  /// What [section] shows instead of its list when it is waiting, not
  /// offered or failed; null when the section shows its list (or its
  /// skeleton), and for every failed section but the first: when several
  /// failed (a server that could not answer at all) the page says so once,
  /// at the first of them, with the one Try again that reloads every failed
  /// section (one primary per screen, LAY-12; nothing shown twice).
  Widget? _sectionLoadState(_Section section, Object? error) {
    final l10n = _l10n;
    if (_waitingForConnection) {
      if (section != _firstShown) return null;
      return _railed(
        KitStateView.calm(
          key: const ValueKey('integrations-waiting'),
          size: KitStateSize.inline,
          title: l10n.integrationsWaitingTitle,
          body: l10n.integrationsWaitingBody,
          tertiary: [KitAction(label: l10n.commonRetry, onPressed: _load)],
        ),
      );
    }
    if (error == null) return null;
    if (_unsupported(error)) {
      return _railed(
        KitStateView(
          key: ValueKey('${section.name}-unsupported'),
          size: KitStateSize.inline,
          icon: AppIconography.info,
          title: switch (section) {
            _Section.providers => l10n.integrationsProvidersUnsupported,
            _Section.servers => l10n.integrationsMcpServersUnsupported,
            _Section.resources => l10n.integrationsResourcesUnsupported,
          },
          body: l10n.integrationsListUnsupportedBody,
        ),
      );
    }
    if (_firstFailed != section) return null;
    final failed = [
      _providersFailed,
      _serversFailed,
      _resourcesFailed,
    ].where((failed) => failed).length;
    final cause = switch (error) {
      ProductException(:final cause?) => cause,
      _ => error,
    };
    final details = productErrorDetails(error);
    return _railed(
      KitStateView.error(
        key: ValueKey(switch (section) {
          _Section.providers => 'providers-load-failed',
          _Section.servers => 'mcp-load-failed',
          _Section.resources => 'resources-load-failed',
        }),
        title: failed > 1
            ? l10n.integrationsPageLoadFailed
            : switch (section) {
                _Section.providers => l10n.integrationsProvidersLoadFailed,
                _Section.servers => l10n.integrationsMcpServersLoadFailed,
                _Section.resources => l10n.integrationsResourcesLoadFailed,
              },
        body: _loadFailureReason(error),
        error: cause,
        errorKind: productErrorKind(cause),
        details: details,
        size: KitStateSize.inline,
        retry: KitAction(label: l10n.commonRetry, onPressed: _retryFailed),
      ),
    );
  }
}
