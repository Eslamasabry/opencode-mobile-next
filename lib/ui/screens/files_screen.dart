import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/review_handoff.dart';
import '../desktop/context_menu.dart';
import '../desktop/desktop_interaction.dart';
import '../widgets/file_preview.dart';
import '../widgets/product_states.dart';
import 'review_workspace.dart';
import '../app_theme.dart';

/// Project file browser backed by `/file`, with name search (`/find/file`)
/// and content viewer.
enum _FileSurface { files, symbols }

typedef ProjectFileAttachment =
    Future<void> Function(String path, FilePreviewData data);
typedef ProjectReviewPrompt = void Function(String prompt);

class FilesScreen extends StatefulWidget {
  final ConnectionController controller;
  final ProjectFileAttachment? onAttachFile;
  final ProjectReviewPrompt? onReviewPrompt;

  /// UX-103: when Files is opened from a chat, add-to-prompt affordances
  /// stage structured references on that session's composer. Without it —
  /// Files opened from the workspace home — those affordances are hidden and
  /// review comments fall back to the clipboard.
  final ReviewHandoffSession? handoff;

  /// Bumped by the shell's Ctrl+F while this destination is showing. Desktop
  /// only in practice: nothing dispatches app shortcuts off desktop.
  final ValueListenable<int>? focusSearchSignal;

  const FilesScreen({
    super.key,
    required this.controller,
    this.onAttachFile,
    this.onReviewPrompt,
    this.handoff,
    this.focusSearchSignal,
  });

  @override
  State<FilesScreen> createState() => _FilesScreenState();
}

class _FilesScreenState extends State<FilesScreen> {
  List<FileNode>? _entries;
  List<WorkspaceSymbol>? _symbols;
  Map<String, VersionControlFile> _fileStatuses = const {};
  _FileSurface _surface = _FileSurface.files;
  String _path = '';
  String? _error;
  bool _loading = false;
  bool _fileStatusesLoading = false;
  String? _fileStatusesError;
  String? _selectedPath;
  int? _selectedLine;
  String? _searchOriginPath;

  /// Width of the tree pane in the wide two-pane layout. Draggable on
  /// desktop; the default is the width the split has always shipped with.
  double _treeWidth = 340;
  final _search = TextEditingController();
  final _searchFocus = FocusNode(debugLabel: 'files-search');
  Timer? _searchDebounce;
  ServerOperationsGateway? _repository;
  int _locationRevision = -1;
  int _controllerLocationRevision = -1;
  int _dataRefreshRevision = -1;
  int _requestGeneration = 0;
  int _fileStatusesGeneration = 0;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_controllerChanged);
    _search.addListener(_searchChanged);
    widget.focusSearchSignal?.addListener(_focusSearch);
    _captureLocation();
    _load('');
  }

  @override
  void didUpdateWidget(FilesScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.focusSearchSignal != widget.focusSearchSignal) {
      oldWidget.focusSearchSignal?.removeListener(_focusSearch);
      widget.focusSearchSignal?.addListener(_focusSearch);
    }
  }

  /// Ctrl+F: put the caret in the find field and select what is already there
  /// so a second search replaces the first, the way every desktop find does.
  void _focusSearch() {
    if (!mounted) return;
    _searchFocus.requestFocus();
    _search.selection = TextSelection(
      baseOffset: 0,
      extentOffset: _search.text.length,
    );
  }

  void _searchChanged() {
    if (mounted) setState(() {});
  }

  void _captureLocation() {
    _repository = widget.controller.repository;
    _locationRevision = _revisionOf(_repository);
    _controllerLocationRevision = widget.controller.locationRevision;
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
  }

  void _controllerChanged() {
    final repository = widget.controller.repository;
    final revision = _revisionOf(repository);
    final controllerLocationChanged =
        _controllerLocationRevision != widget.controller.locationRevision;
    final dataRefreshChanged =
        _dataRefreshRevision != widget.controller.dataRefreshRevision;
    if (identical(repository, _repository) &&
        revision == _locationRevision &&
        !controllerLocationChanged &&
        !dataRefreshChanged) {
      return;
    }
    _repository = repository;
    _locationRevision = revision;
    _controllerLocationRevision = widget.controller.locationRevision;
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    _requestGeneration++;
    _searchDebounce?.cancel();
    _fileStatusesGeneration++;
    if (widget.controller.lifecycleSuspended) {
      setState(() {
        _loading = false;
        _entries ??= const [];
      });
      return;
    }
    if (widget.controller.connectionLoading &&
        !dataRefreshChanged &&
        !controllerLocationChanged) {
      return;
    }
    if (dataRefreshChanged && !controllerLocationChanged) {
      if (_search.text.trim().isNotEmpty) {
        if (_surface == _FileSurface.symbols) {
          _searchSymbols(_search.text);
        } else {
          _searchFiles(_search.text);
        }
      } else if (_surface == _FileSurface.files) {
        _load(_path);
      } else {
        setState(() {
          _symbols = null;
          _error = null;
        });
      }
      return;
    }
    _search.clear();
    _searchOriginPath = null;
    setState(() {
      _entries = null;
      _path = '';
      _selectedPath = null;
      _selectedLine = null;
      _symbols = null;
      _error = null;
      _fileStatuses = const {};
      _fileStatusesError = null;
      _fileStatusesLoading = false;
    });
    if (_surface == _FileSurface.files) {
      _load('');
    } else {
      setState(() => _loading = false);
    }
  }

  int _revisionOf(ServerOperationsGateway? repository) => Object.hash(
    widget.controller.locationRevision,
    repository is LocationAwareProductRepository
        ? (repository as LocationAwareProductRepository).locationRevision
        : 0,
  );

  String _relativePath(String path) =>
      path.split('/').where((component) => component.isNotEmpty).join('/');

  void _navigateTo(String path) {
    _searchDebounce?.cancel();
    _searchOriginPath = null;
    _search.clear();
    _load(path);
  }

  void _selectSurface(_FileSurface surface) {
    if (_surface == surface) return;
    _searchDebounce?.cancel();
    _searchDebounce = null;
    _requestGeneration++;
    final origin = _searchOriginPath ?? _path;
    _search.clear();
    _searchOriginPath = null;
    setState(() {
      _surface = surface;
      _loading = false;
      _error = null;
      if (surface == _FileSurface.symbols) _symbols = null;
    });
    if (surface == _FileSurface.files) unawaited(_load(origin));
  }

  void _clearSearch() {
    _searchDebounce?.cancel();
    _searchDebounce = null;
    _search.clear();
    if (_surface == _FileSurface.symbols) {
      _requestGeneration++;
      setState(() {
        _symbols = null;
        _loading = false;
        _error = null;
      });
      return;
    }
    final origin = _searchOriginPath ?? _path;
    _searchOriginPath = null;
    _load(origin);
  }

  Future<void> _load(String path) async {
    path = _relativePath(path);
    final generation = ++_requestGeneration;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = await widget.controller.prepareActionTransport();
      if (!mounted || generation != _requestGeneration) return;
      if (api == null) {
        throw const ProductException('The server is not connected.');
      }
      final repository = widget.controller.repository;
      if (repository != null) {
        unawaited(_loadFileStatuses(repository));
      }
      final nodes = await api.listFiles(path);
      if (!mounted || generation != _requestGeneration) return;
      setState(() {
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
    } catch (e) {
      if (!mounted || generation != _requestGeneration) return;
      setState(() => _error = productErrorText(e));
    } finally {
      if (mounted && generation == _requestGeneration) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _searchFiles(String q) async {
    _searchDebounce?.cancel();
    _searchDebounce = null;
    if (q.trim().isEmpty) {
      final origin = _searchOriginPath ?? _path;
      _searchOriginPath = null;
      await _load(origin);
      return;
    }
    _searchOriginPath ??= _path;
    final generation = ++_requestGeneration;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = await widget.controller.prepareActionTransport();
      if (!mounted || generation != _requestGeneration) return;
      if (api == null) {
        throw const ProductException('The server is not connected.');
      }
      final repository = widget.controller.repository;
      if (repository != null) {
        unawaited(_loadFileStatuses(repository));
      }
      final results = await api.findFile(q.trim());
      if (!mounted || generation != _requestGeneration) return;
      setState(() {
        _entries = results.map((r) {
          final path = _relativePath(r);
          final parts = path.split('/');
          return FileNode(name: parts.last, path: path, isDir: false);
        }).toList();
      });
    } catch (e) {
      if (!mounted || generation != _requestGeneration) return;
      setState(() => _error = productErrorText(e));
    } finally {
      if (mounted && generation == _requestGeneration) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _retryFileStatuses() async {
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted) return;
    if (repository == null) {
      setState(() {
        _fileStatusesError = 'OpenCode is reconnecting. Try again shortly.';
      });
      return;
    }
    await _loadFileStatuses(repository);
  }

  Future<void> _refreshFiles() => _searchFiles(_search.text);

  void _scheduleFileSearch(String query) {
    _searchDebounce?.cancel();
    _requestGeneration++;
    if (query.trim().isEmpty) {
      unawaited(_searchFiles(query));
      return;
    }
    setState(() {
      _entries = null;
      _loading = true;
      _error = null;
    });
    _searchDebounce = Timer(const Duration(milliseconds: 350), () {
      _searchDebounce = null;
      if (mounted && _surface == _FileSurface.files && _search.text == query) {
        unawaited(_searchFiles(query));
      }
    });
  }

  Future<void> _loadFileStatuses(ServerOperationsGateway repository) async {
    final generation = ++_fileStatusesGeneration;
    setState(() {
      _fileStatusesLoading = true;
      _fileStatusesError = null;
    });
    try {
      final statuses = await repository.listFileStatuses();
      if (!mounted || generation != _fileStatusesGeneration) return;
      setState(() {
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
      if (!mounted || generation != _fileStatusesGeneration) return;
      setState(() {
        _fileStatusesError = _fileStatuses.isEmpty
            ? 'File change indicators are unavailable on this server.'
            : 'File change indicators could not refresh.';
      });
    } finally {
      if (mounted && generation == _fileStatusesGeneration) {
        setState(() => _fileStatusesLoading = false);
      }
    }
  }

  Future<void> _searchSymbols(String query) async {
    _searchDebounce?.cancel();
    _searchDebounce = null;
    final value = query.trim();
    if (value.isEmpty) {
      setState(() {
        _symbols = null;
        _loading = false;
        _error = null;
      });
      return;
    }
    final generation = ++_requestGeneration;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await widget.controller.prepareActionTransport();
      if (!mounted || generation != _requestGeneration) return;
      final repository = widget.controller.repository;
      if (repository == null) {
        throw const ProductException('The server is not connected.');
      }
      final results = await repository.findWorkspaceSymbols(value);
      if (!mounted || generation != _requestGeneration) return;
      setState(() => _symbols = results);
    } catch (error) {
      if (!mounted || generation != _requestGeneration) return;
      setState(() => _error = productErrorText(error));
    } finally {
      if (mounted && generation == _requestGeneration) {
        setState(() => _loading = false);
      }
    }
  }

  void _scheduleSymbolSearch(String query) {
    _searchDebounce?.cancel();
    _searchDebounce = null;
    final value = query.trim();
    _requestGeneration++;
    if (value.isEmpty) {
      setState(() {
        _symbols = null;
        _loading = false;
        _error = null;
      });
      return;
    }
    setState(() {
      _symbols = null;
      _loading = true;
      _error = null;
    });
    _searchDebounce = Timer(const Duration(milliseconds: 350), () {
      _searchDebounce = null;
      if (!mounted ||
          _surface != _FileSurface.symbols ||
          _search.text.trim() != value) {
        return;
      }
      _searchSymbols(value);
    });
  }

  void _openFile(FileNode node, {int? initialLine}) {
    final path = _relativePath(node.path);
    if (MediaQuery.sizeOf(context).width >= 900) {
      setState(() {
        _selectedPath = path;
        _selectedLine = initialLine;
      });
      return;
    }
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) => _FileViewer(
        controller: widget.controller,
        path: path,
        initialLine: initialLine,
        onAttachFile: widget.onAttachFile,
        onAddReference: widget.handoff == null
            ? null
            : () => _stageProjectFile(path, initialLine),
      ),
    );
  }

  /// A plain project file staged as a reference: the agent is pointed at the
  /// path (and the line the user was reading), nothing is uploaded.
  void _stageProjectFile(String path, int? line) {
    final handoff = widget.handoff;
    if (handoff == null) return;
    final change = _fileStatuses[path];
    _stageReference(
      ReviewReference(
        id: handoff.nextID('project-file'),
        kind: change == null
            ? ReviewReferenceKind.file
            : ReviewReferenceKind.changedFile,
        path: path,
        lineLabel: line == null ? null : 'line $line',
        added: change?.additions,
        removed: change?.deletions,
        status: change?.status,
      ),
    );
  }

  void _openSymbol(WorkspaceSymbol symbol) {
    _openFile(
      FileNode(
        name: symbol.path.split('/').last,
        path: symbol.path,
        isDir: false,
      ),
      initialLine: symbol.line,
    );
  }

  /// UX-102: the completion path. One tap from Files to the changed set,
  /// grouped by status, with per-file review and add-to-prompt.
  Future<void> _openChanges() async {
    final changes = _fileStatuses.values.toList()
      ..sort((a, b) => a.path.compareTo(b.path));
    if (changes.isEmpty) return;
    final choice = await showModalBottomSheet<_ChangeChoice>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) =>
          _ChangesSheet(changes: changes, canStage: widget.handoff != null),
    );
    if (!mounted || choice == null) return;
    switch (choice.action) {
      case _ChangeAction.reviewAll:
        await _reviewChanges();
      case _ChangeAction.review:
        await _reviewFileChange(
          FileNode(
            name: choice.path.split('/').last,
            path: choice.path,
            isDir: false,
          ),
        );
      case _ChangeAction.stage:
        final change = _fileStatuses[choice.path];
        _stageReference(
          ReviewReference(
            id: widget.handoff!.nextID('changed-file'),
            kind: ReviewReferenceKind.changedFile,
            path: choice.path,
            scope: ReviewReferenceScope.workingTree,
            added: change?.additions,
            removed: change?.deletions,
            status: change?.status,
          ),
        );
    }
  }

  /// Non-modal confirmation shared by every Files add-to-prompt affordance.
  void _stageReference(ReviewReference reference) {
    final handoff = widget.handoff;
    if (handoff == null) return;
    final outcome = handoff.stage(reference);
    if (!mounted) return;
    final message = switch (outcome) {
      ReviewStageOutcome.staged => 'Added ${reference.label} to the prompt',
      ReviewStageOutcome.duplicate =>
        '${reference.label} is already on the prompt',
      ReviewStageOutcome.full =>
        'The prompt already holds '
            '${ReviewHandoffStore.maxPerSession} references',
    };
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        key: const Key('files-staged-notice'),
        content: Text(message),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  Future<void> _reviewChanges() async {
    final prompt = await Navigator.of(context).push<String>(
      MaterialPageRoute<String>(
        builder: (_) => ReviewWorkspace(
          initialScope: ReviewDiffScope.workingTree,
          handoff: widget.handoff,
          loadWorkingTreeDiffs: () async {
            final repository = await widget.controller
                .prepareActionRepository();
            if (repository == null) {
              throw const ProductException('OpenCode is reconnecting.');
            }
            return repository.listVcsDiffs(VcsDiffMode.workingTree);
          },
        ),
      ),
    );
    _handleReviewPrompt(prompt);
  }

  Future<void> _reviewFileChange(FileNode node) async {
    final prompt = await Navigator.of(context).push<String>(
      MaterialPageRoute<String>(
        builder: (_) => ReviewWorkspace(
          initialScope: ReviewDiffScope.workingTree,
          initialFile: node.path,
          handoff: widget.handoff,
          loadWorkingTreeDiffs: () async {
            final repository = await widget.controller
                .prepareActionRepository();
            if (repository == null) {
              throw const ProductException('OpenCode is reconnecting.');
            }
            return repository.listVcsDiffs(VcsDiffMode.workingTree);
          },
        ),
      ),
    );
    _handleReviewPrompt(prompt);
  }

  /// Legacy return path, used only when there is no handoff session: the
  /// review workspace pops with formatted text that goes to the host chat if
  /// one supplied a callback, and to the clipboard otherwise.
  Future<void> _handleReviewPrompt(String? prompt) async {
    if (!mounted || prompt == null || prompt.trim().isEmpty) return;
    final reviewPrompt = prompt.trim();
    final callback = widget.onReviewPrompt;
    if (callback != null) {
      callback(reviewPrompt);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Review comment added. Return to the chat to continue.',
          ),
        ),
      );
      return;
    }
    await Clipboard.setData(ClipboardData(text: reviewPrompt));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Review comment copied. Paste it into a chat.'),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final crumbs = _path.split('/').where((c) => c.isNotEmpty).toList();

    return Column(
      children: [
        // §7 row 15: with no workspace-symbol search there is only one
        // surface left, so the whole selector goes rather than leaving a
        // one-segment control.
        if (widget.controller.capabilities.workspaceSymbols)
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
            child: SizedBox(
              width: double.infinity,
              child: SegmentedButton<_FileSurface>(
                key: const ValueKey('file-surface-selector'),
                segments: const [
                  ButtonSegment(
                    value: _FileSurface.files,
                    label: Text('Files'),
                  ),
                  ButtonSegment(
                    value: _FileSurface.symbols,
                    label: Text('Symbols'),
                  ),
                ],
                selected: {_surface},
                showSelectedIcon: false,
                onSelectionChanged: (value) => _selectSurface(value.single),
              ),
            ),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
          child: TextField(
            key: const ValueKey('files-search-field'),
            controller: _search,
            focusNode: _searchFocus,
            decoration: InputDecoration(
              isDense: true,
              prefixIcon: const Icon(Icons.search_rounded, size: 20),
              hintText: _surface == _FileSurface.symbols
                  ? 'Find class, function, or variable…'
                  : 'Find file by name…',
              suffixIcon: _search.text.isEmpty
                  ? null
                  : IconButton(
                      tooltip: _surface == _FileSurface.symbols
                          ? 'Clear symbol search'
                          : 'Clear file search',
                      icon: Icon(
                        Icons.clear_rounded,
                        size: 18,
                        semanticLabel: _surface == _FileSurface.symbols
                            ? 'Clear symbol search'
                            : 'Clear file search',
                      ),
                      onPressed: _clearSearch,
                    ),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            onSubmitted: _surface == _FileSurface.symbols
                ? _searchSymbols
                : _searchFiles,
            onChanged: _surface == _FileSurface.symbols
                ? _scheduleSymbolSearch
                : _scheduleFileSearch,
            textInputAction: TextInputAction.search,
          ),
        ),
        if (_surface == _FileSurface.files && _path.isNotEmpty)
          SizedBox(
            height: 52,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              children: [
                ActionChip(
                  label: const Text('/'),
                  onPressed: () => _navigateTo(''),
                ),
                for (var i = 1; i <= crumbs.length; i++)
                  Padding(
                    padding: const EdgeInsets.only(left: 6),
                    child: ActionChip(
                      label: Text(crumbs[i - 1]),
                      onPressed: i == crumbs.length
                          ? null
                          : () => _navigateTo(crumbs.take(i).join('/')),
                    ),
                  ),
              ],
            ),
          ),
        // UX-102: after a run the question is "what changed?", so the
        // changed set gets a standing card above the tree rather than
        // living only as per-row markers.
        if (_surface == _FileSurface.files && _fileStatuses.isNotEmpty)
          _ChangesCard(
            changes: _fileStatuses.values,
            onOpen: () => unawaited(_openChanges()),
          ),
        Expanded(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final files = _fileList(theme);
              if (constraints.maxWidth < 900) return files;
              final maxTree = constraints.maxWidth - 320;
              final treeWidth = _treeWidth.clamp(
                240.0,
                maxTree < 240 ? 240.0 : maxTree,
              );
              return Row(
                children: [
                  SizedBox(width: treeWidth, child: files),
                  _SplitHandle(
                    // Accumulate on the stored width, not the one this build
                    // captured: several drag updates can land before the
                    // next frame, and each must add to the last.
                    onDrag: (delta) => setState(
                      () => _treeWidth = (_treeWidth + delta).clamp(
                        240.0,
                        maxTree < 240 ? 240.0 : maxTree,
                      ),
                    ),
                  ),
                  Expanded(
                    child: _selectedPath == null
                        ? Center(
                            child: Text(
                              'Select a file to preview',
                              style: TextStyle(color: AppTheme.mutedOf(theme)),
                            ),
                          )
                        : _FileViewer(
                            key: ValueKey('$_selectedPath:$_selectedLine'),
                            controller: widget.controller,
                            path: _selectedPath!,
                            initialLine: _selectedLine,
                            embedded: true,
                            onAttachFile: widget.onAttachFile,
                            onAddReference: widget.handoff == null
                                ? null
                                : () => _stageProjectFile(
                                    _selectedPath!,
                                    _selectedLine,
                                  ),
                          ),
                  ),
                ],
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _fileList(ThemeData theme) {
    if (_surface == _FileSurface.symbols) return _symbolList(theme);
    final content = _fileListContent(theme);
    if (_fileStatusesError == null) return content;
    return Column(
      children: [
        _FileStatusNotice(
          message: _fileStatusesError!,
          loading: _fileStatusesLoading,
          onRetry: _fileStatusesLoading ? null : _retryFileStatuses,
        ),
        Expanded(child: content),
      ],
    );
  }

  Widget _fileListContent(ThemeData theme) {
    final entries = _displayEntries();
    if (_loading && _entries == null) {
      return const LoadingList(rows: 8);
    }
    if (_error != null) {
      return RefreshIndicator(
        onRefresh: _refreshFiles,
        child: ProductErrorState(message: _error!, onRetry: _refreshFiles),
      );
    }
    if (_entries != null && entries.isEmpty) {
      return RefreshIndicator(
        onRefresh: _refreshFiles,
        child: ProductEmptyState(
          icon: Icons.folder_off_outlined,
          title: _search.text.isEmpty ? 'Folder is empty' : 'No files found',
          message: _search.text.isEmpty
              ? 'Pull down to refresh this folder.'
              : 'Try a different file name.',
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _refreshFiles,
      child: DesktopScrollbarArea(
        builder: (scrollController) => ListView.builder(
          controller: scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          itemCount: entries.length,
          itemBuilder: (context, i) {
            final node = entries[i];
            final change = node.isDir ? null : _fileStatuses[node.path];
            final descendantChanges = node.isDir
                ? _fileStatuses.keys
                      .where((path) => path.startsWith('${node.path}/'))
                      .length
                : 0;
            final detail = _fileDetail(node, change, descendantChanges);
            return ContextMenuRegion(
              actions: () => _fileRowActions(node, change),
              child: ListTile(
                key: ValueKey('project-file-${node.path}'),
                dense: true,
                selected: node.path == _selectedPath,
                leading: Icon(
                  node.isDir
                      ? Icons.folder_rounded
                      : change?.status == 'deleted'
                      ? Icons.remove_circle_outline_rounded
                      : _fileTypeIcon(node.name),
                  size: 20,
                  color: node.isDir
                      ? theme.colorScheme.primary
                      : change?.status == 'deleted'
                      ? theme.colorScheme.error
                      : AppTheme.mutedOf(theme),
                ),
                title: Row(
                  children: [
                    Expanded(
                      child: Text(
                        node.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    if (change != null)
                      _FileChangeBadge(change: change)
                    else if (descendantChanges > 0)
                      _FolderStatusMark(count: descendantChanges),
                  ],
                ),
                subtitle: detail == null
                    ? null
                    : Text(
                        detail,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: AppTheme.captionFontSize,
                        ),
                      ),
                onTap: change?.status == 'deleted'
                    ? null
                    : () {
                        if (node.isDir) {
                          _navigateTo(node.path);
                        } else {
                          _openFile(node);
                        }
                      },
                // Touch counterpart of the right-click menu: every row action,
                // Review included, without a second control crammed into a
                // 40px row.
                onLongPress: () => unawaited(_showFileRowActions(node, change)),
              ),
            );
          },
        ),
      ),
    );
  }

  /// The row's actions as a bottom sheet — the same list the desktop context
  /// menu shows, so a long press and a right click never disagree.
  Future<void> _showFileRowActions(
    FileNode node,
    VersionControlFile? change,
  ) async {
    final actions = _fileRowActions(node, change);
    if (actions.isEmpty) return;
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) {
        final theme = Theme.of(sheetContext);
        return SafeArea(
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ListTile(
                  key: const ValueKey('file-row-actions-sheet'),
                  dense: true,
                  leading: Icon(
                    node.isDir
                        ? Icons.folder_rounded
                        : _fileTypeIcon(node.name),
                  ),
                  title: Text(
                    node.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  subtitle: Text(
                    _relativePath(node.path),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const Divider(height: 1),
                for (final action in actions)
                  ListTile(
                    key: action.menuKey,
                    leading: Icon(
                      action.icon,
                      color: action.destructive
                          ? theme.colorScheme.error
                          : theme.colorScheme.onSurfaceVariant,
                    ),
                    title: Text(
                      action.label,
                      style: action.destructive
                          ? TextStyle(color: theme.colorScheme.error)
                          : null,
                    ),
                    onTap: () {
                      Navigator.pop(sheetContext);
                      action.onSelected();
                    },
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  /// The desktop right-click menu for a file row. Every entry is something
  /// the row already offers by tap or by the viewer it opens — the menu just
  /// removes the round trip.
  List<ContextMenuAction> _fileRowActions(
    FileNode node,
    VersionControlFile? change,
  ) {
    final deleted = change?.status == 'deleted';
    final path = _relativePath(node.path);
    return [
      if (node.isDir && !deleted)
        ContextMenuAction(
          menuKey: const ValueKey('file-menu-open'),
          label: 'Open folder',
          icon: Icons.folder_open_rounded,
          onSelected: () => _navigateTo(node.path),
        )
      else if (!deleted) ...[
        ContextMenuAction(
          menuKey: const ValueKey('file-menu-open'),
          label: 'Open',
          icon: Icons.open_in_new_rounded,
          onSelected: () => _openFile(node),
        ),
        if (change != null)
          ContextMenuAction(
            menuKey: const ValueKey('file-menu-review'),
            label: 'Open in Review',
            icon: Icons.difference_outlined,
            onSelected: () => unawaited(_reviewFileChange(node)),
          ),
        if (widget.onAttachFile != null)
          ContextMenuAction(
            menuKey: const ValueKey('file-menu-attach'),
            label: 'Attach to prompt',
            icon: Icons.attach_file_rounded,
            onSelected: () => unawaited(_attachFile(path)),
          ),
        if (widget.handoff != null)
          ContextMenuAction(
            menuKey: const ValueKey('file-menu-reference'),
            label: 'Add as reference',
            icon: Icons.add_link_rounded,
            onSelected: () => _stageProjectFile(path, null),
          ),
      ],
      ContextMenuAction(
        menuKey: const ValueKey('file-menu-copy-path'),
        label: 'Copy path',
        icon: AppIcons.copy,
        onSelected: () => unawaited(_copyPath(path)),
      ),
    ];
  }

  /// Attaches a file straight from the tree. The viewer's Attach button does
  /// the same thing once it has the content; this fetches the content first
  /// so the menu does not need the viewer open.
  Future<void> _attachFile(String path) async {
    final action = widget.onAttachFile;
    if (action == null) return;
    try {
      final api = await widget.controller.prepareActionTransport();
      if (api == null) {
        throw const ProductException('The server is not connected.');
      }
      final content = await api.fileContent(path);
      await action(
        path,
        FilePreviewData(
          name: path.split('/').last,
          mimeType: content.mimeType,
          bytes: content.isBinary ? content.bytes() : null,
          text: content.isBinary ? null : content.content,
        ),
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${path.split('/').last} attached.')),
      );
    } catch (error) {
      if (!mounted) return;
      showProductError(context, error);
    }
  }

  Future<void> _copyPath(String path) async {
    await Clipboard.setData(ClipboardData(text: path));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text('Copied $path')));
  }

  /// Type-aware glyphs so a directory scans by kind, matching the developer
  /// file browsers cited in docs/design-inspiration.md.
  static IconData _fileTypeIcon(String name) {
    final dot = name.lastIndexOf('.');
    final ext = dot < 0 ? '' : name.substring(dot + 1).toLowerCase();
    return switch (ext) {
      'dart' ||
      'js' ||
      'ts' ||
      'tsx' ||
      'jsx' ||
      'py' ||
      'go' ||
      'rs' ||
      'kt' ||
      'java' ||
      'swift' ||
      'c' ||
      'cc' ||
      'cpp' ||
      'h' ||
      'cs' ||
      'rb' ||
      'php' ||
      'sh' => Icons.code_rounded,
      'png' ||
      'jpg' ||
      'jpeg' ||
      'gif' ||
      'webp' ||
      'svg' ||
      'ico' => Icons.image_outlined,
      'md' || 'txt' || 'rst' || 'pdf' => Icons.article_outlined,
      'json' ||
      'yaml' ||
      'yml' ||
      'toml' ||
      'xml' ||
      'ini' ||
      'lock' ||
      'gradle' ||
      'properties' => Icons.data_object_rounded,
      'zip' || 'tar' || 'gz' || 'jar' || 'apk' => Icons.folder_zip_outlined,
      _ => Icons.description_outlined,
    };
  }

  List<FileNode> _displayEntries() {
    final entries = [...?_entries];
    final paths = entries.map((node) => node.path).toSet();
    final searchMode = _searchOriginPath != null;
    final query = _search.text.trim().toLowerCase();
    final prefix = _path.isEmpty ? '' : '$_path/';
    for (final change in _fileStatuses.values) {
      if (change.status != 'deleted') continue;
      if (searchMode) {
        if (query.isNotEmpty && !change.path.toLowerCase().contains(query)) {
          continue;
        }
        if (paths.add(change.path)) {
          entries.add(
            FileNode(
              name: change.path.split('/').last,
              path: change.path,
              isDir: false,
            ),
          );
        }
        continue;
      }
      if (!change.path.startsWith(prefix)) continue;
      final remainder = change.path.substring(prefix.length);
      if (remainder.isEmpty) continue;
      final parts = remainder.split('/');
      final childPath = '$prefix${parts.first}';
      if (paths.add(childPath)) {
        entries.add(
          FileNode(name: parts.first, path: childPath, isDir: parts.length > 1),
        );
      }
    }
    return entries;
  }

  String? _fileDetail(
    FileNode node,
    VersionControlFile? change,
    int descendantChanges,
  ) {
    final details = <String>[];
    if (_search.text.isNotEmpty && node.path != node.name) {
      details.add(node.path);
    }
    if (change != null) {
      details.add(_fileStatusLabel(change.status));
      final counts = <String>[
        if (change.additions > 0) '+${change.additions}',
        if (change.deletions > 0) '−${change.deletions}',
      ];
      if (counts.isNotEmpty) details.add(counts.join(' '));
    } else if (descendantChanges > 0) {
      details.add(
        '$descendantChanges changed ${descendantChanges == 1 ? 'file' : 'files'}',
      );
    }
    return details.isEmpty ? null : details.join(' · ');
  }

  Widget _symbolList(ThemeData theme) {
    if (_loading && _symbols == null) {
      return const LoadingList(rows: 6);
    }
    if (_error != null) {
      return RefreshIndicator(
        onRefresh: () => _searchSymbols(_search.text),
        child: ProductErrorState(
          message: _error!,
          onRetry: () => _searchSymbols(_search.text),
        ),
      );
    }
    if (_search.text.trim().isEmpty) {
      return const ProductEmptyState(
        icon: Icons.data_object_rounded,
        title: 'Search workspace symbols',
        message: 'Find classes, functions, methods, and variables by name.',
      );
    }
    if (_symbols?.isEmpty == true) {
      return RefreshIndicator(
        onRefresh: () => _searchSymbols(_search.text),
        child: const ProductEmptyState(
          icon: Icons.search_off_rounded,
          title: 'No symbols found',
          message:
              'Try a different name. Some language services do not support workspace-wide symbol search.',
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: () => _searchSymbols(_search.text),
      child: DesktopScrollbarArea(
        builder: (scrollController) => ListView.builder(
          controller: scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          itemCount: _symbols?.length ?? 0,
          itemBuilder: (context, index) {
            final symbol = _symbols![index];
            return ListTile(
              key: ValueKey('workspace-symbol-${symbol.path}-${symbol.line}'),
              minTileHeight: 58,
              leading: Icon(
                _symbolIcon(symbol.kind),
                color: theme.colorScheme.primary,
              ),
              title: Text(
                symbol.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: Text(
                '${_symbolKind(symbol.kind)} · ${symbol.path}:${symbol.line}:${symbol.column}',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () => _openSymbol(symbol),
            );
          },
        ),
      ),
    );
  }

  static String _symbolKind(int kind) => switch (kind) {
    1 => 'File',
    2 => 'Module',
    3 => 'Namespace',
    4 => 'Package',
    5 => 'Class',
    6 => 'Method',
    7 => 'Property',
    8 => 'Field',
    9 => 'Constructor',
    10 => 'Enum',
    11 => 'Interface',
    12 => 'Function',
    13 => 'Variable',
    14 => 'Constant',
    22 => 'Enum member',
    23 => 'Struct',
    24 => 'Event',
    25 => 'Operator',
    26 => 'Type parameter',
    _ => 'Symbol',
  };

  static IconData _symbolIcon(int kind) => switch (kind) {
    5 || 10 || 11 || 23 => Icons.category_outlined,
    6 || 9 || 12 => Icons.functions_rounded,
    7 || 8 || 13 || 14 => Icons.data_object_rounded,
    1 || 2 || 3 || 4 => Icons.folder_copy_outlined,
    _ => Icons.code_rounded,
  };

  @override
  void dispose() {
    _searchDebounce?.cancel();
    widget.controller.removeListener(_controllerChanged);
    widget.focusSearchSignal?.removeListener(_focusSearch);
    _search.removeListener(_searchChanged);
    _search.dispose();
    _searchFocus.dispose();
    super.dispose();
  }
}

String _fileStatusLabel(String status) => switch (status) {
  'added' => 'Added',
  'deleted' => 'Deleted',
  'modified' => 'Modified',
  _ => 'Changed',
};

enum _ChangeAction { reviewAll, review, stage }

/// The divider between the tree and the preview.
///
/// On Android it stays the hairline [VerticalDivider] it has always been. On
/// desktop it becomes a real splitter: a wider grab strip, the resize-column
/// pointer, and a horizontal drag that resizes the tree pane.
class _SplitHandle extends StatelessWidget {
  const _SplitHandle({required this.onDrag});

  final ValueChanged<double> onDrag;

  @override
  Widget build(BuildContext context) {
    if (!desktopInteractions) return const VerticalDivider(width: 1);
    return MouseRegion(
      cursor: SystemMouseCursors.resizeColumn,
      child: GestureDetector(
        key: const ValueKey('files-split-handle'),
        behavior: HitTestBehavior.opaque,
        onHorizontalDragUpdate: (details) => onDrag(details.delta.dx),
        child: const SizedBox(
          width: 7,
          child: Center(child: VerticalDivider(width: 1)),
        ),
      ),
    );
  }
}

class _ChangeChoice {
  const _ChangeChoice(this.action, [this.path = '']);

  final _ChangeAction action;
  final String path;
}

/// UX-102: the changed-file card. Counts and totals are the summary the
/// audit asks for; the whole card is one tap into the changed set.
class _ChangesCard extends StatelessWidget {
  const _ChangesCard({required this.changes, required this.onOpen});

  final Iterable<VersionControlFile> changes;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final files = changes.length;
    final added = changes.fold<int>(0, (sum, file) => sum + file.additions);
    final removed = changes.fold<int>(0, (sum, file) => sum + file.deletions);
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
      child: Material(
        key: const ValueKey('files-changes-card'),
        color: theme.colorScheme.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(14),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onOpen,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 10, 8, 10),
            child: Row(
              children: [
                Icon(
                  Icons.difference_outlined,
                  size: 20,
                  color: theme.colorScheme.primary,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '$files changed ${files == 1 ? 'file' : 'files'}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        '+$added −$removed · Review the changes',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: AppTheme.mutedOf(theme),
                        ),
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right_rounded),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// The changed set, grouped by version-control status. Tapping a row opens
/// Review at that file; the add action stages the file as a prompt
/// reference instead of opening anything.
class _ChangesSheet extends StatelessWidget {
  const _ChangesSheet({required this.changes, required this.canStage});

  final List<VersionControlFile> changes;
  final bool canStage;

  static const _order = ['modified', 'added', 'deleted'];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final groups = <String, List<VersionControlFile>>{};
    for (final change in changes) {
      groups.putIfAbsent(change.status, () => []).add(change);
    }
    final statuses = groups.keys.toList()
      ..sort((a, b) {
        final left = _order.indexOf(a);
        final right = _order.indexOf(b);
        return (left < 0 ? _order.length : left).compareTo(
          right < 0 ? _order.length : right,
        );
      });
    final added = changes.fold<int>(0, (sum, file) => sum + file.additions);
    final removed = changes.fold<int>(0, (sum, file) => sum + file.deletions);

    return ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * .85,
      ),
      child: Column(
        key: const ValueKey('files-changes-sheet'),
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 14, 12, 4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Changes', style: theme.textTheme.titleLarge),
                const SizedBox(height: 2),
                Text(
                  '${changes.length} '
                  '${changes.length == 1 ? 'file' : 'files'} · +$added −$removed',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.tonalIcon(
                    key: const ValueKey('review-all-changes'),
                    onPressed: () => Navigator.pop(
                      context,
                      const _ChangeChoice(_ChangeAction.reviewAll),
                    ),
                    icon: const Icon(Icons.rate_review_outlined, size: 18),
                    label: const Text('Review all changes'),
                  ),
                ),
              ],
            ),
          ),
          Flexible(
            child: ListView(
              shrinkWrap: true,
              padding: const EdgeInsets.only(bottom: 12),
              children: [
                for (final status in statuses) ...[
                  Padding(
                    padding: const EdgeInsets.fromLTRB(18, 12, 18, 4),
                    child: Text(
                      '${_fileStatusLabel(status)} · ${groups[status]!.length}',
                      style: theme.textTheme.labelLarge?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                  for (final change in groups[status]!)
                    ListTile(
                      key: ValueKey('changed-file-${change.path}'),
                      dense: true,
                      title: Text(
                        change.path.split('/').last,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(
                        '${change.path} · +${change.additions} −${change.deletions}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: AppTheme.captionFontSize,
                        ),
                      ),
                      trailing: canStage
                          ? IconButton(
                              key: ValueKey('stage-change-${change.path}'),
                              tooltip: 'Add ${change.path} to the prompt',
                              icon: const Icon(
                                Icons.add_comment_outlined,
                                size: 20,
                              ),
                              onPressed: () => Navigator.pop(
                                context,
                                _ChangeChoice(_ChangeAction.stage, change.path),
                              ),
                            )
                          : null,
                      onTap: () => Navigator.pop(
                        context,
                        _ChangeChoice(_ChangeAction.review, change.path),
                      ),
                    ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// A compact, non-interactive change mark in the file row: one letter in the
/// status colour. Review itself lives in the row's long-press and right-click
/// menus, so no 48dp button has to fit a 40px row.
class _FileChangeBadge extends StatelessWidget {
  final VersionControlFile change;

  const _FileChangeBadge({required this.change});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = switch (change.status) {
      'deleted' => theme.colorScheme.error,
      'modified' => theme.colorScheme.tertiary,
      _ => theme.colorScheme.primary,
    };
    final label = _fileStatusLabel(change.status);
    return Semantics(
      label: label,
      child: ExcludeSemantics(
        child: Container(
          key: ValueKey('review-file-change-${change.path}'),
          margin: const EdgeInsets.only(left: 8),
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
          decoration: BoxDecoration(
            color: color.withValues(alpha: .14),
            borderRadius: BorderRadius.circular(6),
          ),
          child: Text(
            label.isEmpty ? '?' : label.substring(0, 1).toUpperCase(),
            style: theme.textTheme.labelSmall?.copyWith(
              color: color,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ),
    );
  }
}

class _FolderStatusMark extends StatelessWidget {
  final int count;

  const _FolderStatusMark({required this.count});

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    child: Padding(
      padding: const EdgeInsets.only(left: 8),
      child: Text(
        '$count',
        key: const ValueKey('folder-change-count'),
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
          color: Theme.of(context).colorScheme.primary,
          fontWeight: FontWeight.w700,
        ),
      ),
    ),
  );
}

class _FileStatusNotice extends StatelessWidget {
  final String message;
  final bool loading;
  final Future<void> Function()? onRetry;

  const _FileStatusNotice({
    required this.message,
    required this.loading,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) => Material(
    key: const ValueKey('file-status-notice'),
    color: Theme.of(context).colorScheme.surfaceContainerLow,
    child: Padding(
      padding: const EdgeInsets.fromLTRB(12, 4, 4, 4),
      child: Row(
        children: [
          Icon(
            Icons.info_outline_rounded,
            size: 18,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          TextButton(
            onPressed: onRetry,
            child: loading
                ? const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Try again'),
          ),
        ],
      ),
    ),
  );
}

class _FileViewer extends StatefulWidget {
  final ConnectionController controller;
  final String path;
  final int? initialLine;
  final bool embedded;
  final ProjectFileAttachment? onAttachFile;

  /// UX-103: stages the file as a prompt reference — a path the agent will
  /// read itself, not an upload.
  final VoidCallback? onAddReference;
  const _FileViewer({
    super.key,
    required this.controller,
    required this.path,
    this.initialLine,
    this.embedded = false,
    this.onAttachFile,
    this.onAddReference,
  });

  @override
  State<_FileViewer> createState() => __FileViewerState();
}

class __FileViewerState extends State<_FileViewer> {
  FileContent? _content;
  String? _error;
  int _generation = 0;
  bool _attaching = false;
  bool _downloading = false;

  static const maxChars = 200000;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    final generation = ++_generation;
    // Keep the current content on screen during a reload; the skeleton is
    // for the first load only.
    setState(() => _error = null);
    try {
      final api = await widget.controller.prepareActionTransport();
      if (!mounted || generation != _generation) return;
      if (api == null) {
        throw const ProductException('The server is not connected.');
      }
      final c = await api.fileContent(widget.path);
      if (mounted && generation == _generation) setState(() => _content = c);
    } catch (e) {
      if (!mounted || generation != _generation) return;
      if (_content != null) {
        // A failed reload keeps the stale content visible.
        showProductError(context, e);
      } else {
        setState(() => _error = productErrorText(e));
      }
    }
  }

  String get _displayText {
    var text = _content?.content ?? '';
    if (text.length > maxChars) {
      text = '${text.substring(0, maxChars)}\n... truncated';
    }
    return text;
  }

  FilePreviewData get _previewData {
    final content = _content!;
    return FilePreviewData(
      name: widget.path.split('/').last,
      mimeType: content.mimeType,
      bytes: content.isBinary ? content.bytes() : null,
      text: content.isBinary ? null : _displayText,
    );
  }

  FilePreviewData get _exportData {
    final content = _content!;
    return FilePreviewData(
      name: widget.path.split('/').last,
      mimeType: content.mimeType,
      bytes: content.isBinary ? content.bytes() : null,
      text: content.isBinary ? null : content.content,
    );
  }

  Future<void> _attach() async {
    final action = widget.onAttachFile;
    if (action == null || _content == null || _attaching) return;
    setState(() => _attaching = true);
    try {
      await action(widget.path, _exportData);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${widget.path.split('/').last} attached. Return to the chat to add your comment.',
          ),
          duration: const Duration(seconds: 3),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      showProductError(context, error);
    } finally {
      if (mounted) setState(() => _attaching = false);
    }
  }

  Future<void> _download() async {
    if (_content == null || _downloading) return;
    final data = _exportData;
    final bytes = data.exportBytes;
    if (bytes == null) return;
    setState(() => _downloading = true);
    try {
      final savedPath = await FilePicker.saveFile(
        dialogTitle: 'Save ${data.name}',
        fileName: data.name,
        bytes: bytes,
      );
      if (!mounted || savedPath == null) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${data.name} saved to your device.')),
      );
    } catch (error) {
      if (!mounted) return;
      showProductError(context, error);
    } finally {
      if (mounted) setState(() => _downloading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return SafeArea(
      child: SizedBox(
        height: widget.embedded
            ? double.infinity
            : MediaQuery.of(context).size.height * .85,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 6, 4, 4),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.path.split('/').last,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleMedium,
                        ),
                        Text(
                          widget.initialLine == null
                              ? widget.path
                              : '${widget.path} · Line ${widget.initialLine}',
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: AppTheme.mutedOf(theme),
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (!widget.embedded)
                    CloseButton(onPressed: () => Navigator.pop(context)),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Wrap(
                spacing: 4,
                children: [
                  TextButton.icon(
                    label: Text(l10n.fileCopy),
                    icon: const Icon(AppIcons.copy, size: 18),
                    onPressed: _content == null || _content!.isBinary
                        ? null
                        : () async {
                            await Clipboard.setData(
                              ClipboardData(text: _content!.content),
                            );
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(
                                  content: Text('Copied'),
                                  duration: Duration(seconds: 1),
                                ),
                              );
                            }
                          },
                  ),
                  if (widget.onAddReference != null)
                    TextButton.icon(
                      key: const Key('project-file-add-reference'),
                      label: Text(l10n.fileReference),
                      onPressed: widget.onAddReference,
                      icon: const Icon(Icons.add_comment_outlined, size: 19),
                    ),
                  if (widget.onAttachFile != null)
                    TextButton.icon(
                      key: const Key('project-file-attach'),
                      // Named apart from "Add to prompt": this one uploads
                      // the file's contents with the message.
                      label: Text(l10n.fileAttach),
                      onPressed: _content == null || _attaching
                          ? null
                          : _attach,
                      icon: _attaching
                          ? const SizedBox.square(
                              dimension: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.attach_file_rounded, size: 19),
                    ),
                  TextButton.icon(
                    key: const Key('project-file-download'),
                    label: Text(l10n.fileSave),
                    onPressed: _content == null || _downloading
                        ? null
                        : _download,
                    icon: _downloading
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.download_rounded, size: 19),
                  ),
                  TextButton.icon(
                    label: Text(l10n.fileReload),
                    icon: const Icon(Icons.refresh_rounded, size: 18),
                    onPressed: _fetch,
                  ),
                ],
              ),
            ),
            const Divider(),
            Expanded(
              child: _content == null && _error == null
                  ? const LoadingList(rows: 6)
                  : _error != null
                  ? ProductErrorState(message: _error!, onRetry: _fetch)
                  : FilePreviewBody(
                      data: _previewData,
                      initialLine: widget.initialLine,
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
