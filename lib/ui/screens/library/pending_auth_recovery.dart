part of '../library_screen.dart';

// Equality only. Unlike inventory identity this deliberately excludes the
// replaceable transport: each explicit action reacquires it in the controller.
Object _authSourceFor(ConnectionController controller) => (
  controller.profile?.id,
  controller.profile?.baseUrl,
  controller.profile?.username,
  controller.profile?.password,
  controller.profile?.flavor,
  controller.locationRevision,
);

class _PendingAuthRecoveryTile extends StatefulWidget {
  const _PendingAuthRecoveryTile({
    super.key,
    required this.controller,
    required this.entry,
    required this.onComplete,
  });
  final ConnectionController controller;
  final PendingAuthAttempt entry;
  final Future<void> Function() onComplete;
  @override
  State<_PendingAuthRecoveryTile> createState() =>
      _PendingAuthRecoveryTileState();
}

class _PendingAuthRecoveryTileState extends State<_PendingAuthRecoveryTile> {
  bool _busy = false;
  IntegrationAuthState? _status;
  String? _error;
  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  Future<void> _act({
    bool cancel = false,
    bool enterCode = false,
    bool forget = false,
  }) async {
    if (_busy) return;
    final controller = widget.controller;
    final entry = widget.entry;
    final source = _authSourceFor(controller);
    final location = controller.locationRevision;
    final route = ModalRoute.of(context);
    bool current() =>
        mounted &&
        source == _authSourceFor(controller) &&
        controller.isProfileReadable(entry.profileID) &&
        (route?.isCurrent ?? true);
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      String? code;
      if (forget) {
        final confirmed = await showConfirmSheet(
          context,
          icon: AppIconography.delete,
          title: _l10n.pendingAuthForget,
          message: _l10n.pendingAuthForgetDetail,
          confirmLabel: _l10n.pendingAuthForget,
        );
        if (!confirmed || !current()) return;
        await controller.forgetIntegrationAuth(
          entry,
          locationRevision: location,
        );
        return;
      }
      if (enterCode) {
        code = await showDialog<String>(
          context: context,
          builder: (_) => _OAuthCodeDialog(
            integrationName: entry.integrationID,
            instructions: '',
          ),
        );
        if (code == null || !current()) return;
        code = providerOAuthCompletionCode(code);
      }
      if (!current()) return;
      final result = await controller.recoverIntegrationAuth(
        entry,
        cancel: cancel,
        code: code,
        locationRevision: location,
      );
      if (!mounted || !current()) return;
      setState(() => _status = result.state);
      if (result.state == IntegrationAuthState.complete) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(_l10n.pendingAuthComplete)));
        await widget.onComplete();
      }
    } catch (_) {
      if (current()) setState(() => _error = _l10n.pendingAuthFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final entry = widget.entry;
    final l10n = _l10n;
    final expired = entry.expired || _status == IntegrationAuthState.expired;
    final supported = widget.controller.integrationAuthRecoverySupported;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.pendingAuthTitle(entry.integrationID),
            style: Theme.of(context).textTheme.titleMedium,
          ),
          Text(
            entry.kind == PendingAuthKind.command
                ? l10n.commandAuthPending
                : l10n.pendingAuthDetail,
          ),
          if (!supported) Text(l10n.pendingAuthUnsupported),
          if (expired) Text(l10n.pendingAuthExpired),
          if (_status == IntegrationAuthState.pending)
            Text(l10n.pendingAuthStillPending),
          if (_status == IntegrationAuthState.failed)
            Text(l10n.pendingAuthServerFailed),
          if (_error != null) Semantics(liveRegion: true, child: Text(_error!)),
          if (_busy) const LinearProgressIndicator(),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              if (!expired && supported) ...[
                FilledButton(
                  onPressed: _busy ? null : () => _act(),
                  child: Text(l10n.pendingAuthResume),
                ),
                if (entry.kind == PendingAuthKind.oauth &&
                    entry.mode == IntegrationAuthMode.code)
                  TextButton(
                    onPressed: _busy ? null : () => _act(enterCode: true),
                    child: Text(l10n.pendingAuthEnterCode),
                  ),
              ],
              if (supported)
                TextButton(
                  onPressed: _busy ? null : () => _act(cancel: true),
                  child: Text(l10n.commandAuthCancel),
                ),
              TextButton(
                onPressed: _busy ? null : () => _act(forget: true),
                child: Text(l10n.pendingAuthForget),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _UncertainAuthRecoveryTile extends StatelessWidget {
  const _UncertainAuthRecoveryTile({
    required this.controller,
    required this.integrationID,
    required this.kind,
  });
  final ConnectionController controller;
  final String integrationID;
  final PendingAuthKind kind;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.uncertainAuthTitle(integrationID)),
          Text(l10n.uncertainAuthDetail),
          TextButton(
            onPressed: () async {
              final source = _authSourceFor(controller);
              final location = controller.locationRevision;
              final confirmed = await showConfirmSheet(
                context,
                icon: AppIconography.delete,
                title: l10n.uncertainAuthForgetTitle,
                message: l10n.uncertainAuthForgetDetail,
                confirmLabel: l10n.uncertainAuthForget,
              );
              if (!confirmed ||
                  !context.mounted ||
                  source != _authSourceFor(controller)) {
                return;
              }
              try {
                controller.forgetUncertainIntegrationAuth(
                  integrationID,
                  kind,
                  locationRevision: location,
                );
              } catch (_) {
                if (context.mounted) {
                  showProductError(
                    context,
                    const ProductException('The sign-in source changed.'),
                  );
                }
              }
            },
            child: Text(l10n.uncertainAuthForget),
          ),
        ],
      ),
    );
  }
}
