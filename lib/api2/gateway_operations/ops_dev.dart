part of '../gateway_operations.dart';

mixin _Api2DevOps on _Api2Core implements OneShotCommandGateway {
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
  Future<void> initializeGitRepository() =>
      _guard('Could not initialize this Git repository', () async {
        try {
          await _transport.postJson(
            '/vcs/init',
            query: _loc({'provider': 'git'}),
          );
        } on Api2Error catch (error) {
          // 409: the folder is already a repository, which is the goal.
          if (error.statusCode != 409) rethrow;
        }
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
        return [
          for (final item in _dataMaps(json))
            if (item['title'] != computerUsageTitle) _terminal(item),
        ];
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

  Future<List<TerminalShellOption>> _shellOptions() async {
    final json = await _transport.getJson('/config/shell', query: _loc());
    return [
      for (final item in _dataMaps(json))
        if ((item['path'] ?? '').toString().trim().isNotEmpty &&
            (item['name'] ?? '').toString().trim().isNotEmpty)
          TerminalShellOption(
            path: item['path'].toString().trim(),
            name: item['name'].toString().trim(),
            acceptable: item['acceptable'] == true,
          ),
    ];
  }

  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() =>
      _guard('Could not load shell settings', () async {
        final options = await _shellOptions();
        // Config documents run lowest to highest priority; the last one that
        // sets a shell wins.
        final config = await _transport.getJson('/config', query: _loc());
        var selected = '';
        for (final entry in _dataMaps(config)) {
          final info = entry['info'];
          final shell = info is Map ? info['shell'] : null;
          if (shell is String && shell.trim().isNotEmpty) {
            selected = shell.trim();
          }
        }
        return TerminalShellSettings(selected: selected, options: options);
      });

  @override
  Future<void> selectTerminalShell(String value) =>
      _guard('Could not update the default shell', () async {
        final normalized = value.trim();
        if (normalized.isNotEmpty) {
          // The server accepts any string, so only a listed, usable shell is
          // ever sent.
          final options = await _shellOptions();
          final listed = options.any(
            (option) => option.path == normalized && option.acceptable,
          );
          if (!listed) {
            throw const ProductException(
              'That shell is not available on this server',
            );
          }
        }
        await _transport.patchJson(
          '/experimental/config',
          query: _loc(),
          body: {'shell': normalized.isEmpty ? null : normalized},
        );
      });

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
  Future<TerminalProcess> createCommandTerminal({
    required String command,
    required List<String> arguments,
    String? title,
  }) => _guard('Could not start the command', () async {
    final json = await _transport.postJson(
      '/pty',
      query: _loc(),
      body: {'command': command, 'args': arguments, 'title': ?title},
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
        // The ticket authenticates OC2. A BYO proxy also needs the phone's
        // Basic header so revocation still applies before websocket forwarding.
        // Keep both credentials out of the URL except the single-use ticket.
        Exception? certificateFailure;
        final http = _TerminalTrustHttpClient(
          createServerHttpClient(
            baseUrl: _transport.serverRoot,
            cleartextConfirmedOrigin: _transport.cleartextConfirmedOrigin,
            certificatePinSha256: _transport.certificatePinSha256,
            onCertificateChanged: () =>
                certificateFailure = const ServerCertificateChangedException(),
            onCertificateInvalid: () =>
                certificateFailure = const ServerCertificateValidityException(),
          ),
        );
        try {
          final socket = await WebSocket.connect(
            uri.toString(),
            customClient: http,
            headers: {
              HttpHeaders.authorizationHeader: _transport.authorizationHeader,
            },
          );
          // The upgraded socket is detached from HttpClient ownership.
          return _Api2TerminalChannel(socket, initialCursor: cursor ?? 0);
        } catch (_) {
          if (certificateFailure != null) {
            throw certificateFailure!;
          }
          rethrow;
        } finally {
          http.close();
        }
      });
}
