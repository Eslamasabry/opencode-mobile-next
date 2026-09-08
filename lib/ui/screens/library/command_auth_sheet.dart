part of '../library_screen.dart';

class _CommandAuthSheet extends StatefulWidget {
  const _CommandAuthSheet({
    required this.controller,
    required this.integration,
    required this.method,
  });
  final ConnectionController controller;
  final IntegrationInfo integration;
  final IntegrationMethodInfo method;
  @override
  State<_CommandAuthSheet> createState() => _CommandAuthSheetState();
}

class _CommandAuthSheetState extends State<_CommandAuthSheet> {
  late final Object _source;
  late final int _location;
  String? _attempt;
  IntegrationAuthState? _status;
  bool _busy = false;
  bool _invalidated = false;
  bool _uncertainStart = false;
  String? _error;
  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));
  bool get _current =>
      mounted &&
      !_invalidated &&
      _source == _authSourceFor(widget.controller) &&
      widget.controller.isProfileReadable(
        widget.controller.promptShelfProfileID,
      );

  @override
  void initState() {
    super.initState();
    _source = _authSourceFor(widget.controller);
    _location = widget.controller.locationRevision;
    widget.controller.addListener(_changed);
    widget.controller.profileDataChanges.addListener(_changed);
    try {
      _attempt = widget.controller.pendingIntegrationCommand(
        widget.integration.id,
        locationRevision: _location,
      );
    } catch (_) {
      _invalidated = true;
    }
  }

  void _changed() {
    if (mounted && !_current) setState(() => _invalidated = true);
  }

  Future<void> _start() async {
    if (!_current || _busy || _attempt != null || _uncertainStart) return;
    final route = ModalRoute.of(context);
    setState(() {
      _busy = true;
      _error = null;
    });
    var dispatched = false;
    try {
      final confirmed = await showConfirmSheet(
        context,
        icon: AppIconography.terminal,
        title: _l10n.commandAuthConfirmTitle,
        message: _l10n.commandAuthConfirmDetail,
        confirmLabel: _l10n.commandAuthStart,
        cancelLabel: MaterialLocalizations.of(context).cancelButtonLabel,
      );
      if (!confirmed || !_current || !(route?.isCurrent ?? true)) return;
      dispatched = true;
      final launch = await widget.controller.startIntegrationCommand(
        widget.integration.id,
        widget.method.id!,
        locationRevision: _location,
      );
      if (!_current) return;
      setState(() {
        _attempt = launch.attemptID;
        _status = IntegrationAuthState.pending;
      });
    } catch (_) {
      if (_current) {
        String? recovered;
        try {
          recovered = widget.controller.pendingIntegrationCommand(
            widget.integration.id,
            locationRevision: _location,
          );
        } catch (_) {}
        setState(() {
          _attempt = recovered;
          _uncertainStart = dispatched && recovered == null;
          _error = _l10n.commandAuthFailed;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _check({bool cancel = false}) async {
    final attempt = _attempt;
    if (!_current || _busy || attempt == null) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      if (cancel) {
        await widget.controller.cancelIntegrationCommand(
          widget.integration.id,
          attempt,
          locationRevision: _location,
        );
        if (!_current) return;
        setState(() {
          _attempt = null;
          _status = null;
        });
      } else {
        final result = await widget.controller.integrationCommandStatus(
          widget.integration.id,
          attempt,
          locationRevision: _location,
        );
        if (!_current) return;
        setState(() {
          _status = result.state;
          if (result.state == IntegrationAuthState.complete) _attempt = null;
        });
        if (result.state == IntegrationAuthState.complete) {
          await widget.controller.refreshCatalog();
        }
      }
    } catch (_) {
      if (_current) setState(() => _error = _l10n.commandAuthFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    widget.controller.profileDataChanges.removeListener(_changed);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * .85,
        ),
        child: ListView(
          shrinkWrap: true,
          padding: EdgeInsets.fromLTRB(
            16,
            0,
            16,
            24 + MediaQuery.viewInsetsOf(context).bottom,
          ),
          children: [
            Text(
              widget.integration.name,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            Text(widget.method.label),
            const SizedBox(height: 12),
            Text(l10n.commandAuthMethodHint),
            if (!_current)
              Text(l10n.commandAuthScopeChanged)
            else ...[
              if (_busy) const LinearProgressIndicator(),
              if (_error != null)
                Semantics(liveRegion: true, child: Text(_error!)),
              if (_uncertainStart) ...[
                Text(l10n.commandAuthUncertainStart),
                Text(l10n.uncertainAuthCloseHint),
              ],
              if (widget.controller.pendingAuthPersistenceUncertain)
                Text(l10n.pendingAuthSaveUncertain),
              if (_attempt != null) Text(l10n.commandAuthPending),
              if (_status == IntegrationAuthState.complete)
                Text(l10n.commandAuthComplete),
              if (_status == IntegrationAuthState.failed)
                Text(l10n.commandAuthFailed),
              if (_status == IntegrationAuthState.expired)
                Text(l10n.commandAuthExpired),
              Wrap(
                spacing: 8,
                children: [
                  if (_attempt == null &&
                      !_uncertainStart &&
                      _status != IntegrationAuthState.complete)
                    FilledButton(
                      onPressed: _busy ? null : _start,
                      child: Text(l10n.commandAuthStart),
                    ),
                  if (_attempt != null) ...[
                    FilledButton(
                      onPressed: _busy ? null : () => _check(),
                      child: Text(l10n.commandAuthCheck),
                    ),
                    TextButton(
                      onPressed: _busy ? null : () => _check(cancel: true),
                      child: Text(l10n.commandAuthCancel),
                    ),
                  ],
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}
