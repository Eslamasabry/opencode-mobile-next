part of '../product_repository.dart';

mixin _SdkDevOps on _SdkCore {
  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) => _guard(
    mode == VcsDiffMode.workingTree
        ? 'Could not load working tree changes'
        : 'Could not load branch changes',
    () async {
      final response = await _client.getInstanceApi().vcsDiff(
        mode: mode.wireValue,
        directory: _directory,
        workspace: _workspace,
        context: 3,
      );
      return (response.data ?? const [])
          .map(
            (diff) => FileDiff(
              file: diff.file,
              patch: diff.patch_,
              additions: diff.additions.toInt(),
              deletions: diff.deletions.toInt(),
              status: diff.status?.value.toString(),
            ),
          )
          .toList();
    },
  );

  @override
  Future<VersionControlHealth> loadVersionControlHealth() =>
      _guard('Could not load version control status', () async {
        final projectFuture = () async {
          try {
            return (await _client.getProjectApi().projectCurrent(
              directory: _directory,
              workspace: _workspace,
            )).data;
          } catch (_) {
            // Older servers can still provide useful VCS truth without the
            // current-project metadata needed to offer Git initialization.
            return null;
          }
        }();
        final responses = await Future.wait([
          _client.getInstanceApi().vcsGet(
            directory: _directory,
            workspace: _workspace,
          ),
          _client.getInstanceApi().vcsStatus(
            directory: _directory,
            workspace: _workspace,
          ),
        ]);
        final info = responses[0].data as sdk.VcsInfo?;
        final status = responses[1].data as List<sdk.VcsFileStatus>?;
        final project = await projectFuture;
        return VersionControlHealth(
          branch: info?.branch,
          defaultBranch: info?.defaultBranch,
          setupState: project?.vcs == sdk.ProjectVcs.git
              ? VersionControlSetupState.git
              : project != null && project.vcs == null
              ? VersionControlSetupState.absent
              : VersionControlSetupState.unknown,
          changes: (status ?? const [])
              .map(
                (file) => VersionControlFile(
                  path: file.file,
                  status: file.status.value.toString(),
                  additions: file.additions.toInt(),
                  deletions: file.deletions.toInt(),
                ),
              )
              .toList(),
        );
      });

  @override
  Future<void> initializeGitRepository() =>
      _guard('Could not initialize this Git repository', () async {
        final response = await _client.getProjectApi().projectInitGit(
          directory: _directory,
          workspace: _workspace,
        );
        if (response.data?.vcs != sdk.ProjectVcs.git) {
          throw const ProductException(
            'OpenCode did not confirm Git initialization',
          );
        }
      });

  @override
  Future<List<VersionControlFile>> listFileStatuses() =>
      _guard('Could not load file changes', () async {
        final response = await _client.getInstanceApi().vcsStatus(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map(
              (file) => VersionControlFile(
                path: file.file,
                status: file.status.value.toString(),
                additions: file.additions.toInt(),
                deletions: file.deletions.toInt(),
              ),
            )
            .toList();
      });

  @override
  Future<List<LanguageServiceHealth>> listLanguageServices() =>
      _guard('Could not load language server status', () async {
        final response = await _client.getInstanceApi().lspStatus(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map(
              (service) => LanguageServiceHealth(
                id: service.id,
                name: service.name,
                root: service.root,
                status: service.status.value.toString(),
              ),
            )
            .toList();
      });

  @override
  Future<List<FormatterHealth>> listFormatters() =>
      _guard('Could not load formatter status', () async {
        final response = await _client.getInstanceApi().formatterStatus(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map(
              (formatter) => FormatterHealth(
                name: formatter.name,
                extensions: formatter.extensions,
                enabled: formatter.enabled,
              ),
            )
            .toList();
      });

  @override
  Future<List<WorkspaceSymbol>> findWorkspaceSymbols(String query) =>
      _guard('Could not search workspace symbols', () async {
        final response = await _client.getFileApi().findSymbols(
          query: query,
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map(
              (symbol) => WorkspaceSymbol(
                name: symbol.name,
                kind: symbol.kind,
                path: _symbolPath(symbol.location.uri),
                line: symbol.location.range.start.line + 1,
                column: symbol.location.range.start.character + 1,
              ),
            )
            .where((symbol) => symbol.path.isNotEmpty)
            .toList();
      });

  @override
  Future<List<TerminalProcess>> listTerminals() =>
      _guard('Could not load terminal processes', () async {
        final response = await _client.getPtyApi().ptyList(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const []).map(_terminal).toList();
      });

  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() => _guard(
    'Could not load shell settings',
    () async {
      final config = await _client.getGlobalApi().globalConfigGet();
      final shells = await _client.getPtyApi().ptyShells(
        directory: _directory,
        workspace: _workspace,
      );
      return TerminalShellSettings(
        selected: config.data?.shell?.trim() ?? '',
        options: (shells.data ?? const [])
            .where(
              (shell) =>
                  shell.path.trim().isNotEmpty && shell.name.trim().isNotEmpty,
            )
            .map(
              (shell) => TerminalShellOption(
                path: shell.path.trim(),
                name: shell.name.trim(),
                acceptable: shell.acceptable,
              ),
            )
            .toList(),
      );
    },
  );

  @override
  Future<void> selectTerminalShell(String value) => _guard(
    'Could not update the default shell',
    () async {
      final normalized = value.trim();
      if (normalized.length > 4096 || normalized.contains(RegExp(r'[\r\n]'))) {
        throw const ProductException('OpenCode returned an invalid shell');
      }
      await _client.getGlobalApi().globalConfigUpdate(
        config: sdk.Config(shell: normalized),
      );
      final confirmed = await _client.getGlobalApi().globalConfigGet();
      if ((confirmed.data?.shell?.trim() ?? '') != normalized) {
        throw const ProductException(
          'OpenCode did not retain the selected default shell',
        );
      }
    },
  );

  @override
  Future<TerminalProcess> createTerminal({String? title}) =>
      _guard('Could not start a terminal', () async {
        final response = await _client.getPtyApi().ptyCreate(
          directory: _directory,
          workspace: _workspace,
          ptyCreateRequest: sdk.PtyCreateRequest(title: title),
        );
        final process = response.data;
        if (process == null) {
          throw const ProductException('Server returned no terminal');
        }
        return _terminal(process);
      });

  @override
  Future<void> renameTerminal(String id, String title) =>
      _guard('Could not rename the terminal', () async {
        await _client.getPtyApi().ptyUpdate(
          ptyID: id,
          directory: _directory,
          workspace: _workspace,
          ptyUpdateRequest: sdk.PtyUpdateRequest(title: title),
        );
      });

  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) => _guard('Could not resize the terminal', () async {
    await _client.getPtyApi().ptyUpdate(
      ptyID: id,
      directory: _directory,
      workspace: _workspace,
      ptyUpdateRequest: sdk.PtyUpdateRequest(
        size: sdk.PtyUpdateRequestSize(rows: rows, cols: cols),
      ),
    );
  });

  @override
  Future<void> removeTerminal(String id) =>
      _guard('Could not stop the terminal', () async {
        try {
          await _client.getPtyApi().ptyRemove(
            ptyID: id,
            directory: _directory,
            workspace: _workspace,
          );
        } on sdk.OpenCodeApiException catch (error) {
          // Already gone (the shell exited, or another client closed it):
          // stopping it is done, not an error to show.
          if (error.statusCode == 404) return;
          rethrow;
        }
      });

  @override
  Future<TerminalChannel> connectTerminal(
    String id, {
    int? cursor,
  }) => _guard('Could not connect to the terminal', () async {
    // A ticket is scoped to one location. Keep the socket query on that same
    // location even if the user switches workspaces while the request awaits.
    final directory = _directory;
    final workspace = _workspace;
    final token = await _client.getPtyApi().ptyConnectToken(
      ptyID: id,
      directory: directory,
      workspace: workspace,
      // Required by the server's CSRF guard but omitted from its OpenAPI spec.
      headers: const {'x-opencode-ticket': '1'},
    );
    final ticket = token.data?.ticket;
    if (ticket == null) {
      throw const ProductException('Terminal ticket was unavailable');
    }
    final base = Uri.parse(_client.dio.options.baseUrl);
    final query = <String, String>{
      'ticket': ticket,
      if (cursor != null) 'cursor': '$cursor',
    };
    if (directory != null) query['directory'] = directory;
    if (workspace != null) query['workspace'] = workspace;
    final uri = base.replace(
      scheme: base.scheme == 'https' ? 'wss' : 'ws',
      path:
          '${base.path.endsWith('/') ? base.path.substring(0, base.path.length - 1) : base.path}/pty/${Uri.encodeComponent(id)}/connect',
      queryParameters: query,
    );
    return _IoTerminalChannel(
      await WebSocket.connect(uri.toString()),
      initialCursor: cursor ?? 0,
    );
  });
}
