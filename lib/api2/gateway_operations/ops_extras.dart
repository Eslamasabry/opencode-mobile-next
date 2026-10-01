part of '../gateway_operations.dart';

mixin _Api2ExtrasOps on _Api2Core {
  @override
  Future<List<PluginInfo>> listPlugins() async {
    final location = _loc();
    try {
      final json = await _transport.getJson('/plugin', query: location);
      if (json is! Map || json['data'] is! List) {
        throw const FormatException('Invalid plugin inventory');
      }
      final rows = json['data'] as List;
      if (rows.length > 1000 || rows.any((row) => row is! Map)) {
        throw const FormatException('Invalid plugin inventory');
      }
      return List.unmodifiable(rows.cast<Map>().map(mapPluginInfo));
    } catch (_) {
      // Plugin failures and source URLs may contain credentials. Never retain
      // the raw response/transport error in the product error or its cause.
      throw const ProductException('Could not load plugins. Try again.');
    }
  }

  bool _activeContextSupported = true;
  @override
  bool get activeContextSupported => _activeContextSupported;

  @override
  Future<List<ActiveContextMessage>> loadActiveContext(String sessionID) async {
    if (!_activeContextSupported) {
      throw const ActiveContextException(ActiveContextFailure.unsupported);
    }
    try {
      final json = await _transport.getJson(
        '/session/${Uri.encodeComponent(sessionID)}/context',
      );
      if (json is! Map || json['data'] is! List) {
        throw const ActiveContextException(
          ActiveContextFailure.invalidResponse,
        );
      }
      final messages = <ActiveContextMessage>[];
      final ids = <String>{};
      for (final value in json['data'] as List) {
        if (value is! Map ||
            value['id'] is! String ||
            value['type'] is! String ||
            !ids.add(value['id'] as String)) {
          throw const ActiveContextException(
            ActiveContextFailure.invalidResponse,
          );
        }
        final message = Api2Message.fromJson(Map<String, dynamic>.from(value));
        if (message == null) {
          throw const ActiveContextException(
            ActiveContextFailure.invalidResponse,
          );
        }
        messages.add(mapActiveContext(message));
      }
      return messages;
    } on Api2Error catch (error) {
      if ([405, 501].contains(error.statusCode) ||
          (error.statusCode == 404 && error.tag != 'SessionNotFoundError')) {
        _activeContextSupported = false;
        throw const ActiveContextException(ActiveContextFailure.unsupported);
      }
      rethrow;
    }
  }

  bool _skillsSupported = true;
  @override
  bool get sessionSkillsSupported => _skillsSupported;

  @override
  Future<void> activateSessionSkill(
    String sessionID,
    String skillID, {
    required bool resume,
  }) async {
    if (!_skillsSupported) {
      throw const SessionSkillException(SessionSkillFailure.unsupported);
    }
    try {
      // The session identifies its location. The route has no location query.
      await _transport.postJson(
        '/session/${Uri.encodeComponent(sessionID)}/skill',
        body: {'skill': skillID, 'resume': resume},
      );
    } on Api2Error catch (error) {
      if ([405, 501].contains(error.statusCode) ||
          (error.statusCode == 404 &&
              ![
                'SessionNotFoundError',
                'SkillNotFoundError',
              ].contains(error.tag))) {
        _skillsSupported = false;
        throw const SessionSkillException(SessionSkillFailure.unsupported);
      }
      if (error.statusCode == null || error.statusCode! >= 500) {
        throw const SessionSkillException(SessionSkillFailure.uncertain);
      }
      rethrow;
    }
  }

  bool _importSupported = true;
  @override
  bool get sessionImportSupported => _importSupported;

  @override
  Future<Session> importSession(
    SessionImportDocument document,
    SessionImportDestination destination,
  ) async {
    if (!_importSupported) throw const SessionImportUnsupported();
    try {
      final json = await _transport.postJson(
        '/session/import',
        body: document.requestBody(destination),
      );
      final session = Api2Session.fromJson(_dataMap(json));
      if (session == null ||
          session.id != document.id ||
          session.directory?.isNotEmpty != true) {
        throw const Api2RequestError('Import returned an invalid session');
      }
      return mapApi2Session(session);
    } on Api2Error catch (error) {
      // 404 can mean the source's parent session needs importing first.
      if ([405, 501].contains(error.statusCode) ||
          (error.statusCode == 404 && error.tag != 'SessionNotFoundError')) {
        _importSupported = false;
        throw const SessionImportUnsupported();
      }
      rethrow;
    }
  }

  bool _exportSupported = true;
  @override
  bool get sessionExportSupported => _exportSupported;

  @override
  Future<Uint8List> exportSession(
    String sessionID, {
    bool sanitize = true,
    CancelToken? cancelToken,
    ProgressCallback? onReceiveProgress,
  }) async {
    if (!_exportSupported) throw const SessionExportUnsupported();
    try {
      // This route identifies its session globally; never attach _loc(). Keep
      // the server envelope byte-for-byte, without decoded models or re-encoding.
      final bytes = await _transport.getBytes(
        '/session/${Uri.encodeComponent(sessionID)}/export',
        query: {'sanitize': sanitize.toString()},
        cancelToken: cancelToken,
        onReceiveProgress: onReceiveProgress,
      );
      return bytes is Uint8List ? bytes : Uint8List.fromList(bytes);
    } on Api2Error catch (error) {
      if ([405, 501].contains(error.statusCode) ||
          (error.statusCode == 404 && error.tag != 'SessionNotFoundError')) {
        _exportSupported = false;
        throw const SessionExportUnsupported();
      }
      rethrow;
    }
  }

  bool _usageSupported = true;
  @override
  bool get usageStatisticsSupported => _usageSupported;

  @override
  Future<UsageStatistics> loadUsageStatistics(UsageQuery query) async {
    try {
      // Server-wide endpoint: never attach _loc(). Only the explicit project
      // ID scopes this query; all projects omits the filter entirely.
      final json = await _transport.getJson(
        '/session/stats',
        query: query.toQuery(),
      );
      return UsageStatistics.fromJson(_dataMap(json));
    } on Api2Error catch (error) {
      if ([404, 405, 501].contains(error.statusCode)) {
        _usageSupported = false;
        throw const UsageUnsupported();
      }
      rethrow;
    }
  }

  bool _notesSupported = true;
  @override
  bool get sessionNotesSupported => _notesSupported;

  String _notePath(String sessionID) =>
      '/session/${Uri.encodeComponent(sessionID)}/instructions/entries';

  Future<T> _noteRequest<T>(Future<T> Function() action) async {
    try {
      return await action();
    } on Api2Error catch (error) {
      // A missing session does not mean the endpoint is absent.
      if ([405, 501].contains(error.statusCode) ||
          (error.statusCode == 404 && error.tag != 'SessionNotFoundError')) {
        _notesSupported = false;
        throw const SessionNoteException(SessionNoteFailure.unsupported);
      }
      if (error.tag == 'InstructionEntryValueTooLargeError') {
        final limit = error.body?['maxBytes'];
        throw SessionNoteException(
          SessionNoteFailure.tooLarge,
          maxBytes: limit is int && limit > 0
              ? limit
              : SessionNoteGateway.maxBytes,
        );
      }
      rethrow;
    }
  }

  @override
  Future<String?> loadSessionNote(String sessionID) => _noteRequest(() async {
    final json = await _transport.getJson(_notePath(sessionID), query: _loc());
    if (json is! Map || json['data'] is! List) {
      throw const SessionNoteException(SessionNoteFailure.invalidValue);
    }
    // Other entry names and values never leave the transport adapter.
    final owned = (json['data'] as List)
        .whereType<Map>()
        .where((entry) => entry['key'] == SessionNoteGateway.key)
        .toList();
    if (owned.isEmpty) return null;
    if (owned.length != 1 || owned.single['value'] is! String) {
      throw const SessionNoteException(SessionNoteFailure.invalidValue);
    }
    return owned.single['value'] as String;
  });

  @override
  Future<void> saveSessionNote(String sessionID, String value) async {
    if (SessionNoteGateway.encodedBytes(value) > SessionNoteGateway.maxBytes) {
      throw const SessionNoteException(SessionNoteFailure.tooLarge);
    }
    await _noteRequest(
      () => _transport.putJson(
        '${_notePath(sessionID)}/${SessionNoteGateway.key}',
        query: _loc(),
        body: {'value': value},
      ),
    );
  }

  @override
  Future<void> removeSessionNote(String sessionID) async {
    await _noteRequest(
      () => _transport.deleteJson(
        '${_notePath(sessionID)}/${SessionNoteGateway.key}',
        query: _loc(),
      ),
    );
  }
}
