part of '../library_screen.dart';

// Local equality token only. No credential material is displayed or persisted.
Object _integrationSourceFor(ConnectionController controller) {
  final profile = controller.profile;
  return (
    controller.promptShelfProfileID,
    profile?.baseUrl,
    profile?.username,
    profile?.password,
    controller.locationRevision,
    controller.repository,
  );
}

enum _CredentialAction { activate, rename, remove }

class _CredentialManagementSheet extends StatefulWidget {
  const _CredentialManagementSheet({
    required this.controller,
    required this.integrationID,
    required this.integrationName,
  });
  final ConnectionController controller;
  final String integrationID;
  final String integrationName;

  @override
  State<_CredentialManagementSheet> createState() =>
      _CredentialManagementSheetState();
}

class _CredentialManagementSheetState
    extends State<_CredentialManagementSheet> {
  late final Object _source;
  late final int _location;
  late int _connectionRevision;
  StreamSubscription<dynamic>? _events;
  IntegrationInfo? _integration;
  bool _invalidated = false;
  bool _loading = false;
  bool _activeKnown = false;
  String? _activeID;
  String? _busyCredential;
  String? _error;
  String? _message;

  ConnectionController get _controller => widget.controller;
  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));
  bool get _current =>
      mounted &&
      !_invalidated &&
      _source == _integrationSourceFor(_controller) &&
      _controller.isProfileReadable(_controller.promptShelfProfileID);
  bool get _canAct => _current && !_loading && _busyCredential == null;

  @override
  void initState() {
    super.initState();
    _source = _integrationSourceFor(_controller);
    _location = _controller.locationRevision;
    _connectionRevision = _controller.connectionRevision;
    _controller.addListener(_connectionChanged);
    _controller.profileDataChanges.addListener(_connectionChanged);
    _events = _controller.events.listen((event) {
      if (!_current ||
          _controller.status != StreamStatus.connected ||
          _connectionRevision != _controller.connectionRevision ||
          event.type != 'credential.switched') {
        return;
      }
      final properties = event.properties;
      if (properties['integrationID'] != widget.integrationID ||
          !properties.containsKey('credentialID')) {
        return;
      }
      final id = properties['credentialID'];
      if (id != null && (id is! String || id.isEmpty)) return;
      if (_integration == null ||
          (id != null && !_integration!.credentialIDs.contains(id))) {
        return;
      }
      setState(() {
        _activeKnown = true;
        _activeID = id as String?;
        _message = _l10n.credentialActiveUpdated;
      });
    });
    unawaited(_load());
  }

  void _connectionChanged() {
    if (!mounted) return;
    if (!_current) {
      setState(() {
        _invalidated = true;
        _integration = null;
        _activeKnown = false;
        _activeID = null;
        _message = null;
      });
      return;
    }
    if (_controller.status != StreamStatus.connected ||
        _connectionRevision != _controller.connectionRevision) {
      setState(() {
        _connectionRevision = _controller.connectionRevision;
        _activeKnown = false;
        _activeID = null;
        _message = null;
      });
    }
  }

  Future<void> _load() async {
    if (!_current || _loading) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repository = _controller.repository;
      if (repository == null ||
          repository is! IntegrationCredentialGateway ||
          !_controller.capabilities.integrationCredentials) {
        throw StateError('Unavailable');
      }
      final values = await repository.listIntegrations();
      if (!_current) return;
      final matches = values
          .where((value) => value.id == widget.integrationID)
          .toList();
      setState(() {
        _integration = matches.isEmpty ? null : matches.single;
        if (_integration == null) _error = _l10n.credentialProviderMissing;
        if (_activeID != null &&
            !(_integration?.credentialIDs.contains(_activeID) ?? false)) {
          // A disappeared ID does not tell us which other account is active.
          _activeKnown = false;
          _activeID = null;
        }
      });
    } catch (_) {
      if (_current) setState(() => _error = _l10n.credentialLoadFailed);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _act(
    IntegrationConnectionInfo connection,
    String label,
    _CredentialAction action,
  ) async {
    final id = connection.id;
    if (!_canAct ||
        id == null ||
        id.isEmpty ||
        connection.type != 'credential') {
      return;
    }
    final route = ModalRoute.of(context);
    bool canFinish() => _current && (route?.isCurrent ?? true);
    setState(() {
      _busyCredential = id;
      _error = null;
      _message = null;
    });
    var dispatched = false;
    try {
      String? replacement;
      if (action == _CredentialAction.rename) {
        replacement = await showDialog<String>(
          context: context,
          builder: (_) => _CredentialLabelDialog(initial: connection.label),
        );
        if (replacement == null || !canFinish()) return;
      } else if (action == _CredentialAction.remove) {
        final confirmed = await showConfirmSheet(
          context,
          icon: Icons.person_remove_outlined,
          title: _l10n.credentialRemoveTitle(label),
          message: _l10n.credentialRemoveDetail,
          confirmLabel: _l10n.mcpRemove,
          cancelLabel: MaterialLocalizations.of(context).cancelButtonLabel,
          destructive: true,
        );
        if (!confirmed || !canFinish()) return;
      }
      if (!canFinish()) return;
      dispatched = true;
      switch (action) {
        case _CredentialAction.activate:
          await _controller.activateIntegrationCredential(
            widget.integrationID,
            id,
            locationRevision: _location,
          );
        case _CredentialAction.rename:
          await _controller.renameIntegrationCredential(
            widget.integrationID,
            id,
            replacement!,
            locationRevision: _location,
          );
        case _CredentialAction.remove:
          await _controller.removeIntegrationCredential(
            widget.integrationID,
            id,
            locationRevision: _location,
          );
      }
      if (!canFinish()) return;
      setState(() {
        _message = action == _CredentialAction.activate
            ? (_activeKnown && _activeID == id
                  ? _l10n.credentialActiveUpdated
                  : _l10n.credentialSwitchRequested)
            : null;
      });
    } catch (_) {
      if (canFinish()) setState(() => _error = _l10n.credentialMutationFailed);
    } finally {
      if (dispatched && canFinish()) {
        final mutationError = _error;
        await _load();
        if (_current && mutationError != null) {
          setState(() => _error = mutationError);
        }
      }
      if (mounted) setState(() => _busyCredential = null);
    }
  }

  @override
  void dispose() {
    unawaited(_events?.cancel());
    _controller.removeListener(_connectionChanged);
    _controller.profileDataChanges.removeListener(_connectionChanged);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    final connections =
        _integration?.connections ?? const <IntegrationConnectionInfo>[];
    final credentials = connections
        .where(
          (value) => value.type == 'credential' && value.id?.isNotEmpty == true,
        )
        .toList();
    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * .9,
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
              _integration?.name ?? widget.integrationName,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text(l10n.credentialMetadataOnly),
            const SizedBox(height: 12),
            if (!_current)
              Text(l10n.credentialScopeChanged)
            else ...[
              Semantics(
                liveRegion: true,
                child: Text(
                  !_activeKnown
                      ? l10n.credentialActiveUnknown
                      : _activeID == null
                      ? l10n.credentialNoneActive
                      : l10n.credentialActiveObserved,
                ),
              ),
              if (_loading || _busyCredential != null)
                const LinearProgressIndicator(),
              if (_error != null)
                Text(
                  _error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              if (_message != null)
                Semantics(liveRegion: true, child: Text(_message!)),
              Align(
                alignment: AlignmentDirectional.centerStart,
                child: TextButton.icon(
                  onPressed: _loading || _busyCredential != null ? null : _load,
                  icon: const Icon(Icons.refresh_rounded),
                  label: Text(l10n.credentialRefresh),
                ),
              ),
              if (!_loading && credentials.isEmpty && _error == null)
                Text(l10n.credentialEmpty),
              for (var index = 0; index < credentials.length; index++)
                _credentialTile(credentials[index], index),
              for (final connection in connections.where(
                (value) => value.type == 'env',
              ))
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.lock_outline_rounded),
                  title: Text(connection.label),
                  subtitle: Text(l10n.credentialEnvironment),
                ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _credentialTile(IntegrationConnectionInfo connection, int index) {
    final label = connection.label.trim().isEmpty
        ? _l10n.credentialUnnamed(index + 1)
        : connection.label;
    final active = _activeKnown && _activeID == connection.id;
    final style = TextButton.styleFrom(minimumSize: const Size(48, 48));
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SelectableText(
              label,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            if (active)
              Text(
                _l10n.credentialActive,
                style: TextStyle(
                  color: AppTheme.statusColor(
                    Theme.of(context),
                    AppStatusTone.ok,
                  ),
                ),
              ),
            Wrap(
              spacing: 8,
              children: [
                TextButton(
                  style: style,
                  onPressed: !_canAct || active
                      ? null
                      : () =>
                            _act(connection, label, _CredentialAction.activate),
                  child: Text(_l10n.credentialSetActive),
                ),
                TextButton(
                  style: style,
                  onPressed: !_canAct
                      ? null
                      : () => _act(connection, label, _CredentialAction.rename),
                  child: Text(_l10n.credentialRename),
                ),
                TextButton(
                  style: style,
                  onPressed: !_canAct
                      ? null
                      : () => _act(connection, label, _CredentialAction.remove),
                  child: Text(_l10n.mcpRemove),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _CredentialLabelDialog extends StatefulWidget {
  const _CredentialLabelDialog({required this.initial});
  final String initial;
  @override
  State<_CredentialLabelDialog> createState() => _CredentialLabelDialogState();
}

class _CredentialLabelDialogState extends State<_CredentialLabelDialog> {
  late final _label = TextEditingController(text: widget.initial);
  bool get _valid {
    final value = _label.text.trim();
    return value.isNotEmpty &&
        value.runes.length <= 128 &&
        !RegExp(r'[\x00-\x1f\x7f-\x9f]').hasMatch(value);
  }

  @override
  void dispose() {
    _label.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return AlertDialog(
      scrollable: true,
      title: Text(l10n.credentialRename),
      content: TextField(
        controller: _label,
        autofocus: true,
        maxLength: 128,
        textInputAction: TextInputAction.done,
        decoration: InputDecoration(labelText: l10n.credentialLabel),
        onChanged: (_) => setState(() {}),
        onSubmitted: (_) {
          if (_valid) Navigator.pop(context, _label.text.trim());
        },
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
        ),
        FilledButton(
          onPressed: !_valid
              ? null
              : () => Navigator.pop(context, _label.text.trim()),
          child: Text(l10n.credentialSave),
        ),
      ],
    );
  }
}
