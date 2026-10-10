part of '../files_screen.dart';

/// Loading listings, statuses, search results and symbols.
extension _FilesLoading on _FilesScreenState {
  void _startLoading() {
    _loading = true;
    _loadStartedAt = DateTime.now();
    _error = null;
  }

  /// A listing of only hidden entries (or none) in a shared-storage folder
  /// may be Android hiding the rest: ask which host lacks access.
  Future<void> _checkStorageAccess(List<FileNode> nodes, int generation) async {
    if (!nodes.every((node) => node.name.startsWith('.'))) return;
    final block = await SharedStorageGate.blockFor(
      widget.controller.profile,
      widget.controller.directory,
    );
    if (!mounted ||
        generation != _requestGeneration ||
        block == _storageBlock) {
      return;
    }
    _set(() => _storageBlock = block);
  }

  Future<void> _allowStorageAccess() async {
    final outcome = await SharedStorageAccessFlow.resolve(
      context,
      _storageBlock,
    );
    if (outcome == SharedStorageOutcome.proceed && mounted) {
      await _refreshFiles();
    }
  }

  Widget _storageAccessNotice(AppLocalizations l10n) {
    final termux = _storageBlock == SharedStorageBlock.termuxAccess;
    return KitRefresh(
      onRefresh: _refreshFiles,
      child: KitStateView(
        key: const ValueKey('files-storage-access'),
        icon: AppIconography.folderOpen,
        title: l10n.filesAccessNeededTitle,
        body: termux
            ? l10n.filesAccessNeededTermuxBody
            : l10n.filesAccessNeededBody,
        primary: KitAction(
          key: const ValueKey('files-storage-access-action'),
          label: termux ? l10n.storageTermuxAllow : l10n.storageAccessAllow,
          onPressed: () => unawaited(_allowStorageAccess()),
        ),
      ),
    );
  }

  Future<void> _load(String path) async {
    path = _relativePath(path);
    final generation = ++_requestGeneration;
    _set(() {
      _startLoading();
      _notice = null;
      _storageBlock = SharedStorageBlock.none;
      // Keep the destination through errors so Retry opens the same folder.
      if (_path != path) _entries = null;
      _path = path;
    });
    try {
      final api = await widget.controller.prepareActionTransport();
      if (!mounted || generation != _requestGeneration) return;
      if (api == null) {
        throw ProductException(readerL10n(context).readerUiDisconnected);
      }
      final repository = widget.controller.repository;
      if (repository != null) {
        unawaited(_loadFileStatuses(repository));
      }
      final nodes = await api.listFiles(path);
      if (!mounted || generation != _requestGeneration) return;
      _set(() {
        _entries = nodes
            .map(
              (node) => FileNode(
                name: node.name,
                path: _relativePath(node.path),
                isDir: node.isDir,
              ),
            )
            .toList();
        _path = path;
      });
      unawaited(_checkStorageAccess(nodes, generation));
    } catch (e) {
      if (!mounted || generation != _requestGeneration) return;
      _set(() => _error = productErrorText(e));
    } finally {
      if (mounted && generation == _requestGeneration) {
        _set(() => _loading = false);
      }
    }
  }

  Future<void> _searchFiles(String q) async {
    if (q.trim().isEmpty) {
      final origin = _searchOriginPath ?? _path;
      _searchOriginPath = null;
      await _load(origin);
      return;
    }
    _searchOriginPath ??= _path;
    final generation = ++_requestGeneration;
    _set(_startLoading);
    try {
      final api = await widget.controller.prepareActionTransport();
      if (!mounted || generation != _requestGeneration) return;
      if (api == null) {
        throw ProductException(readerL10n(context).readerUiDisconnected);
      }
      final repository = widget.controller.repository;
      if (repository != null) {
        unawaited(_loadFileStatuses(repository));
      }
      final results = await api.findFile(q.trim());
      if (!mounted || generation != _requestGeneration) return;
      _set(() {
        _entries = results.map((r) {
          final path = _relativePath(r);
          final parts = path.split('/');
          return FileNode(name: parts.last, path: path, isDir: false);
        }).toList();
      });
    } catch (e) {
      if (!mounted || generation != _requestGeneration) return;
      _set(() => _error = productErrorText(e));
    } finally {
      if (mounted && generation == _requestGeneration) {
        _set(() => _loading = false);
      }
    }
  }

  Future<void> _refreshFiles() => _searchFiles(_search.text);

  /// Change marks load with every listing, so a failure is a condition on
  /// the status line, not a second Try again beside the list's own (map
  /// files: "two retries on failure").
  Future<void> _loadFileStatuses(ServerOperationsGateway repository) async {
    final generation = ++_fileStatusesGeneration;
    final profileID = widget.controller.profile?.id;
    final locationRevision = widget.controller.locationRevision;
    if (!_matchesFileScope(
      profileID: profileID,
      locationRevision: locationRevision,
      repository: repository,
    )) {
      return;
    }
    try {
      final statuses = await repository.listFileStatuses();
      if (generation != _fileStatusesGeneration ||
          !_matchesFileScope(
            profileID: profileID,
            locationRevision: locationRevision,
            repository: repository,
          )) {
        return;
      }
      _set(() {
        _fileStatusesError = null;
        _fileStatuses = {
          for (final status in statuses)
            _relativePath(status.path): VersionControlFile(
              path: _relativePath(status.path),
              status: status.status,
              additions: status.additions,
              deletions: status.deletions,
            ),
        };
      });
    } catch (error) {
      if (generation != _fileStatusesGeneration ||
          !_matchesFileScope(
            profileID: profileID,
            locationRevision: locationRevision,
            repository: repository,
          )) {
        return;
      }
      _set(() {
        _fileStatusesError = _fileStatuses.isEmpty
            ? readerL10n(context).readerUiIndicatorsUnavailable
            : readerL10n(context).readerUiIndicatorsFailed;
      });
    }
  }

  Future<void> _searchSymbols(String query) async {
    final value = query.trim();
    if (value.isEmpty) {
      _requestGeneration++;
      _set(() {
        _symbols = null;
        _loading = false;
        _error = null;
      });
      return;
    }
    final generation = ++_requestGeneration;
    _set(_startLoading);
    try {
      await widget.controller.prepareActionTransport();
      if (!mounted || generation != _requestGeneration) return;
      final repository = widget.controller.repository;
      if (repository == null) {
        throw ProductException(readerL10n(context).readerUiDisconnected);
      }
      final results = await repository.findWorkspaceSymbols(value);
      if (!mounted || generation != _requestGeneration) return;
      _set(() => _symbols = results);
    } catch (error) {
      if (!mounted || generation != _requestGeneration) return;
      _set(() => _error = productErrorText(error));
    } finally {
      if (mounted && generation == _requestGeneration) {
        _set(() => _loading = false);
      }
    }
  }

  /// The row above the file-name results: the typed words, searched inside
  /// the files (the filter's Text in files, with the query kept).
  Future<void> _searchInsideFiles(String query) {
    _requestGeneration++;
    _set(() {
      _surface = _FileSurface.text;
      _textMatches = null;
      _error = null;
    });
    return _searchText(query);
  }

  /// The most matching lines the list shows; more are summarised in a line.
  static const _maxTextMatches = 200;

  /// Text inside the files (`/find`, capability textSearch): one result per
  /// matching line.
  Future<void> _searchText(String query) async {
    final value = query.trim();
    if (value.isEmpty) {
      _requestGeneration++;
      _set(() {
        _textMatches = null;
        _loading = false;
        _error = null;
      });
      return;
    }
    final generation = ++_requestGeneration;
    _set(_startLoading);
    try {
      final api = await widget.controller.prepareActionTransport();
      if (!mounted || generation != _requestGeneration) return;
      if (api == null) {
        throw ProductException(readerL10n(context).readerUiDisconnected);
      }
      final results = await api.findText(value);
      if (!mounted || generation != _requestGeneration) return;
      _set(() {
        _textMatches = results;
        _error = null;
      });
    } catch (error) {
      if (!mounted || generation != _requestGeneration) return;
      _set(() => _error = productErrorText(error));
    } finally {
      if (mounted && generation == _requestGeneration) {
        _set(() => _loading = false);
      }
    }
  }

  // --- The viewer -------------------------------------------------------------

  /// Opens a file in the kit's one viewer: beside the list where the window
  /// shows two panes, otherwise as the viewer's own sheet or page.
  void _openFile(FileNode node, {int? initialLine}) {
    final session = _newSession(_relativePath(node.path), initialLine);
    if (KitScreen.showsDetail(context)) {
      _set(() => _viewer = session);
      return;
    }
    unawaited(_showViewer(session));
  }
}
