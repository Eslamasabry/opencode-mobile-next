part of '../gateway_operations.dart';

mixin _Api2ShellOps on _Api2Core {
  @override
  Future<ManagedShell> startManagedShell({
    required String command,
    required String directory,
    required String ownerToken,
  }) => _guard('Could not start the development command', () async {
    if (command.trim().isEmpty ||
        command.length > 4096 ||
        directory.trim().isEmpty ||
        ownerToken.isEmpty) {
      throw const ProductException('Invalid development command');
    }
    final json = await _transport.postJson(
      '/shell',
      query: _loc(),
      body: {
        'command': command,
        'cwd': directory,
        'timeout': 0,
        'metadata': {'ocDevelopmentServiceOwner': ownerToken},
      },
    );
    return _managedShell(_dataMap(json));
  });

  @override
  Future<ManagedShellList> loadRunningShells() =>
      _guard('Could not load running commands', () async {
        try {
          final json = await _transport.getJson(
            '/shell',
            query: _loc(),
            receiveTimeout: const Duration(seconds: 8),
          );
          return ManagedShellList(
            supported: true,
            shells: [for (final item in _dataMaps(json)) _managedShell(item)],
          );
        } on Api2Error catch (error) {
          if ([404, 405, 501].contains(error.statusCode)) {
            return const ManagedShellList(supported: false);
          }
          rethrow;
        }
      });

  @override
  Future<String?> managedShellServerIdentity() =>
      _guard('Could not identify the server', () async {
        final health = await _transport.getJson(
          '/health',
          receiveTimeout: const Duration(seconds: 8),
        );
        return health is Map && health['pid'] is num
            ? health['pid'].toString()
            : null;
      });

  @override
  Future<ManagedShell?> getManagedShell(String id) =>
      _guard('Could not load the command', () async {
        try {
          final json = await _transport.getJson(
            '/shell/${Uri.encodeComponent(id)}',
            query: _loc(),
            receiveTimeout: const Duration(seconds: 8),
          );
          return _managedShell(_dataMap(json));
        } on Api2Error catch (error) {
          if (error.isNotFound) return null;
          rethrow;
        }
      });

  @override
  Future<ManagedShellOutput> readManagedShellOutput(
    String id, {
    required int cursor,
    int limit = 65536,
  }) => _guard('Could not read command output', () async {
    final json = _dataMap(
      await _transport.getJson(
        '/shell/${Uri.encodeComponent(id)}/output',
        query: _loc({'cursor': cursor, 'limit': limit.clamp(1, 65536)}),
        receiveTimeout: const Duration(seconds: 8),
      ),
    );
    return ManagedShellOutput(
      text: json['output'] as String? ?? '',
      cursor: (json['cursor'] as num).toInt(),
      size: (json['size'] as num).toInt(),
      truncated: json['truncated'] == true,
    );
  });

  @override
  Future<void> stopManagedShell(String id) =>
      _guard('Could not stop the command', () async {
        await _transport.deleteJson(
          '/shell/${Uri.encodeComponent(id)}',
          query: _loc(),
        );
      });

  @override
  Future<ManagedShell> setManagedShellTimeout(String id, Duration? timeout) =>
      _guard('Could not change the timeout', () async {
        final json = await _transport.patchJson(
          '/shell/${Uri.encodeComponent(id)}/timeout',
          query: _loc(),
          body: {'timeout': timeout?.inMilliseconds ?? 0},
        );
        return _managedShell(_dataMap(json));
      });

  static ManagedShell _managedShell(Map<String, dynamic> json) {
    final time = json['time'] as Map? ?? const {};
    final metadata = json['metadata'] as Map? ?? const {};
    return ManagedShell(
      id: json['id'] as String,
      command: json['command'] as String? ?? '',
      directory: json['cwd'] as String?,
      ownerToken: metadata['ocDevelopmentServiceOwner'] as String?,
      status: ManagedShellStatus.values.firstWhere(
        (status) => status.name == json['status'],
        orElse: () => ManagedShellStatus.unknown,
      ),
      startedAt: DateTime.fromMillisecondsSinceEpoch(
        (time['started'] as num).toInt(),
      ),
      completedAt: time['completed'] is num
          ? DateTime.fromMillisecondsSinceEpoch(
              (time['completed'] as num).toInt(),
            )
          : null,
      exitCode: (json['exit'] as num?)?.toInt(),
      sessionID: metadata['sessionID'] as String?,
    );
  }

  @override
  Future<BackgroundWorkSupport> loadBackgroundWorkSupport() async =>
      BackgroundWorkSupport.subagentsAndShells;

  @override
  Future<BackgroundWorkResult> backgroundSession(String sessionID) =>
      _guard('Could not background running work', () async {
        await _transport.postJson(
          '/session/${Uri.encodeComponent(sessionID)}/background',
          query: _loc(),
        );
        return BackgroundWorkResult.requested;
      });
}
