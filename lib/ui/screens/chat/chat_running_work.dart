part of '../chat_screen.dart';

// Work running beside the conversation: background shells and agents,
// and handing the running turn to the background.

mixin _ChatRunningWorkFields {
  final _backgroundSupportState = ValueNotifier<BackgroundWorkSupport>(
    BackgroundWorkSupport.unavailable,
  );
  ServerOperationsGateway? _backgroundRepository;
  int _backgroundSupportRevision = 0;
  int _backgroundLocationRevision = -1;
  bool _backgrounding = false;
  final Set<String> _backgroundRequestedParts = {};
  List<ManagedShell> _runningShells = [];
  bool _readingShells = false;
  ServerOperationsGateway? _shellReadRepository;
  int _shellReadLocation = -1;
  int _shellReadRevision = 0;
}

extension _ChatRunningWork on _ChatScreenState {
  BackgroundWorkSupport get _backgroundSupport => _backgroundSupportState.value;

  Set<String> get _shellIDs => {
    for (final message in _messages)
      for (final part in message.parts)
        if (part.type == 'tool')
          if (part.toolState.metadata?['shellID'] case final String id) id,
  };

  Future<void> _loadRunningShells() async {
    if (_conn.isIsolated || !_conn.capabilities.conversationShellOn) return;
    if (_conn.status != StreamStatus.connected) return;
    final repo = _conn.repository;
    if (repo == null) return;
    final location = _conn.locationRevision;
    if (_readingShells &&
        repo == _shellReadRepository &&
        location == _shellReadLocation) {
      return;
    }
    final revision = ++_shellReadRevision;
    _shellReadRepository = repo;
    _shellReadLocation = location;
    _readingShells = true;
    try {
      final result = await repo.loadRunningShells();
      if (!mounted ||
          repo != _conn.repository ||
          location != _conn.locationRevision) {
        return;
      }
      _setChatState(
        () => _runningShells = result.supported ? result.shells : [],
      );
    } catch (_) {
      // Discovery must succeed before a shell-only entry is advertised.
    } finally {
      if (revision == _shellReadRevision) _readingShells = false;
    }
  }

  Future<void> _openRunningWork() async {
    if (!_conn.capabilities.projectManagement) return;
    final sessionID = widget.sessionID;
    final location = _conn.locationRevision;
    final profileID = _conn.profile?.id;
    final targetID = await showRunningWorkSheet(
      context,
      controller: _conn,
      sessionID: widget.sessionID,
      shellIDs: _shellIDs,
      backgroundSupport: _backgroundSupport,
      readBackgroundSupport: () => _backgroundSupport,
      canBackground: () =>
          mounted &&
          widget.sessionID == sessionID &&
          location == _conn.locationRevision &&
          profileID == _conn.profile?.id &&
          _canBackgroundWork,
      availabilityChanges: Listenable.merge([
        _historyChanges,
        _backgroundSupportState,
      ]),
      onBackground: () async {
        if (widget.sessionID != sessionID ||
            location != _conn.locationRevision ||
            profileID != _conn.profile?.id) {
          return null;
        }
        return _backgroundRunningWork();
      },
    );
    if (!mounted ||
        widget.sessionID != sessionID ||
        location != _conn.locationRevision ||
        profileID != _conn.profile?.id) {
      return;
    }
    if (targetID != null) await _openSubagentSession(targetID);
    if (mounted) unawaited(_loadRunningShells());
  }

  Future<void> _loadBackgroundSupport() async {
    if (_conn.isIsolated || !_conn.capabilities.projectManagement) return;
    final repository = _conn.repository;
    _backgroundRepository = repository;
    _backgroundLocationRevision = _conn.locationRevision;
    final location = _conn.locationRevision;
    final revision = ++_backgroundSupportRevision;
    _backgroundSupportState.value = BackgroundWorkSupport.unavailable;
    _backgroundRequestedParts.clear();
    if (repository == null) return;
    try {
      final support = await repository.loadBackgroundWorkSupport().timeout(
        const Duration(seconds: 8),
      );
      if (!mounted ||
          revision != _backgroundSupportRevision ||
          location != _conn.locationRevision ||
          repository != _conn.repository) {
        return;
      }
      _setChatState(() => _backgroundSupportState.value = support);
    } catch (_) {
      // Unknown/older v1 servers must not advertise an experimental action.
      // Retry capability discovery after the next reconnect, not every event.
    }
  }

  String _backgroundPartKey(Part part) =>
      '${part.messageID}/${part.id ?? part.callID}';

  bool get _canBackgroundWork =>
      !_conn.isIsolated &&
      !_backgrounding &&
      _backgroundLocationRevision == _conn.locationRevision &&
      _conn.status == StreamStatus.connected &&
      _conn.busySessions.contains(widget.sessionID) &&
      foregroundBackgroundableParts(_messages, _backgroundSupport).any(
        (part) => !_backgroundRequestedParts.contains(_backgroundPartKey(part)),
      );

  Future<BackgroundWorkResult?> _backgroundRunningWork() async {
    if (!_canBackgroundWork) return null;
    final repository = _conn.repository;
    if (repository == null) return null;
    final connection = _conn.connectionRevision;
    final location = _conn.locationRevision;
    final parts = foregroundBackgroundableParts(
      _messages,
      _backgroundSupport,
    ).map(_backgroundPartKey).toSet();
    _setChatState(() => _backgrounding = true);
    try {
      final result = await repository.backgroundSession(widget.sessionID);
      if (!mounted ||
          repository != _conn.repository ||
          connection != _conn.connectionRevision ||
          location != _conn.locationRevision) {
        return null;
      }
      if (result != BackgroundWorkResult.unchanged) {
        _backgroundRequestedParts.addAll(parts);
      }
      // Acknowledgement is not a job record. Reconcile the transcript and
      // family status, including the v2 204/idle race, before reporting state.
      await Future.wait([
        _load(),
        _conn.refreshSessions(),
        _loadRunningShells(),
      ]);
      if (!mounted) return null;
      if (result == BackgroundWorkResult.unchanged) {
        _showComposerNote(_chatL10n(context).backgroundWorkNoop);
      } else if (result == BackgroundWorkResult.promoted) {
        _showComposerNote(_chatL10n(context).backgroundWorkPromoted);
      } else {
        _showComposerNote(_chatL10n(context).workBackgroundRequested);
      }
      return result;
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) _setChatState(() => _backgrounding = false);
    }
    return null;
  }
}
