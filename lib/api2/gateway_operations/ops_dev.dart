part of '../gateway_operations.dart';

mixin _Api2DevOps on _Api2Core {
  // Implemented by _Api2ProjectOps.
  Future<Map<String, dynamic>> _currentProjectJson();

  // ---------------- VCS & project health ----------------

  Future<dynamic> _optionalVcsJson(String path) async {
    try {
      return await _transport.getJson(path, query: _loc());
    } on Api2Error {
      return null;
    }
  }

  @override
  Future<VersionControlHealth> loadVersionControlHealth() =>
      _guard('Could not load version control status', () async {
        final vcsFuture = _optionalVcsJson('/vcs');
        final statusFuture = _optionalVcsJson('/vcs/status');
        final projectFuture = () async {
          try {
            return await _currentProjectJson();
          } catch (_) {
            // VCS truth is still useful without project metadata.
            return const <String, dynamic>{};
          }
        }();
        final (vcs, status, project) = await waitForRequests(
          vcsFuture,
          statusFuture,
          projectFuture,
        );
        final info = _dataMap(vcs);
        final changes = [
          for (final item in _dataMaps(status)) mapVcsStatusJson(item),
        ];
        final branch = info['branch'];
        final current = branch is Map ? branch['current']?.toString() : null;
        final fallback = branch is Map ? branch['default']?.toString() : null;
        return VersionControlHealth(
          branch: current,
          defaultBranch: fallback,
          changes: changes,
          // `/project/current` omits `vcs` on beta-18600 (only the project
          // list rows carry it), so live branch data is the primary signal.
          setupState:
              project['vcs']?.toString() == 'git' ||
                  current?.isNotEmpty == true ||
                  fallback?.isNotEmpty == true
              ? VersionControlSetupState.git
              : project.containsKey('id')
              ? VersionControlSetupState.absent
              : VersionControlSetupState.unknown,
        );
      });

  @override
  Future<List<VersionControlFile>> listFileStatuses() =>
      _guard('Could not load file statuses', () async {
        final json = await _transport.getJson('/vcs/status', query: _loc());
        return [for (final item in _dataMaps(json)) mapVcsStatusJson(item)];
      });

  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) =>
      _guard('Could not load the diff', () async {
        final json = await _transport.getJson(
          '/vcs/diff',
          query: _loc({
            'mode': switch (mode) {
              VcsDiffMode.workingTree => 'working',
              VcsDiffMode.branch => 'branch',
            },
          }),
        );
        return [for (final item in _dataMaps(json)) mapVcsDiffJson(item)];
      });

  @override
  Future<List<LanguageServiceHealth>> listLanguageServices() async =>
      // No LSP status endpoint in v2 (capability languageServiceStatus:
      // false).
      const [];

  @override
  Future<List<FormatterHealth>> listFormatters() async =>
      // No formatter status endpoint in v2 (capability formatterStatus:
      // false).
      const [];

  @override
  Future<List<WorkspaceSymbol>> findWorkspaceSymbols(String query) async =>
      // No symbol search in v2 (capability workspaceSymbols: false).
      const [];

  // ---------------- Terminals (PTY) ----------------

  @override
  Future<List<TerminalProcess>> listTerminals() =>
      _guard('Could not load terminals', () async {
        final json = await _transport.getJson('/pty', query: _loc());
        return [for (final item in _dataMaps(json)) _terminal(item)];
      });

  TerminalProcess _terminal(Map<String, dynamic> json) => TerminalProcess(
    id: (json['id'] ?? '').toString(),
    title: (json['title'] ?? '').toString(),
    command: (json['command'] ?? '').toString(),
    arguments: json['args'] is List
        ? (json['args'] as List).map((value) => value.toString()).toList()
        : const [],
    directory: (json['cwd'] ?? '').toString(),
    running: (json['status'] ?? '').toString() == 'running',
    pid: (json['pid'] as num?)?.toInt() ?? 0,
    exitCode: (json['exitCode'] as num?)?.toInt(),
  );

  @override
  Future<TerminalProcess> createTerminal({String? title}) =>
      _guard('Could not create the terminal', () async {
        final json = await _transport.postJson(
          '/pty',
          query: _loc(),
          body: {'title': ?title},
        );
        final data = _dataMap(json);
        if (data.isEmpty) {
          throw const ProductException('OpenCode returned no terminal');
        }
        return _terminal(data);
      });

  @override
  Future<void> renameTerminal(String id, String title) => _guard(
    'Could not rename the terminal',
    () => _transport.putJson('/pty/$id', body: {'title': title}),
  );

  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) => _guard(
    'Could not resize the terminal',
    () => _transport.putJson(
      '/pty/$id',
      body: {
        'size': {'rows': rows, 'cols': cols},
      },
    ),
  );

  @override
  Future<void> removeTerminal(String id) =>
      _guard('Could not close the terminal', () async {
        try {
          await _transport.deleteJson('/pty/$id');
        } on Api2Error catch (error) {
          // Already gone (the shell exited, or another client closed it):
          // closing it is done, not an error to show.
          if (error.statusCode == 404) return;
          rethrow;
        }
      });

  @override
  Future<TerminalChannel> connectTerminal(String id, {int? cursor}) =>
      _guard('Could not connect to the terminal', () async {
        final response = await _transport.dio.post<dynamic>(
          '/pty/$id/connect-token',
          queryParameters: _loc(),
          options: Options(headers: {'x-opencode-ticket': '1'}),
        );
        final ticket = _dataMap(response.data)['ticket']?.toString();
        if (ticket == null || ticket.isEmpty) {
          throw const ProductException(
            'OpenCode returned no terminal connect ticket',
          );
        }
        final base = Uri.parse('${_transport.apiBase}/pty/$id/connect');
        final uri = base.replace(
          scheme: base.scheme == 'https' ? 'wss' : 'ws',
          queryParameters: {
            'ticket': ticket,
            if (cursor != null) 'cursor': '$cursor',
            'location[directory]': ?_directory,
          },
        );
        final socket = await WebSocket.connect(uri.toString());
        return _Api2TerminalChannel(socket, initialCursor: cursor ?? 0);
      });
}
