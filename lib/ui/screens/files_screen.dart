import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/file_preview.dart';
import '../widgets/product_states.dart';
import 'review_workspace.dart';

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

  const FilesScreen({
    super.key,
    required this.controller,
    this.onAttachFile,
    this.onReviewPrompt,
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
  final _search = TextEditingController();
  Timer? _symbolSearchDebounce;
  ProductRepository? _repository;
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
    _captureLocation();
    _load('');
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

  int _revisionOf(ProductRepository? repository) => Object.hash(
    widget.controller.locationRevision,
    repository is LocationAwareProductRepository
        ? (repository as LocationAwareProductRepository).locationRevision
        : 0,
  );

  String _relativePath(String path) =>
      path.split('/').where((component) => component.isNotEmpty).join('/');

  void _navigateTo(String path) {
    _searchOriginPath = null;
    _search.clear();
    _load(path);
  }

  void _selectSurface(_FileSurface surface) {
    if (_surface == surface) return;
    _symbolSearchDebounce?.cancel();
    _symbolSearchDebounce = null;
    _requestGeneration++;
    _search.clear();
    _searchOriginPath = null;
    setState(() {
      _surface = surface;
      _loading = false;
      _error = null;
      if (surface == _FileSurface.symbols) _symbols = null;
    });
  }

  void _clearSearch() {
    _symbolSearchDebounce?.cancel();
    _symbolSearchDebounce = null;
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

  Future<void> _loadFileStatuses(ProductRepository repository) async {
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
    _symbolSearchDebounce?.cancel();
    _symbolSearchDebounce = null;
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
    _symbolSearchDebounce?.cancel();
    _symbolSearchDebounce = null;
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
    _symbolSearchDebounce = Timer(const Duration(milliseconds: 350), () {
      _symbolSearchDebounce = null;
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

  Future<void> _reviewFileChange(FileNode node) async {
    final prompt = await Navigator.of(context).push<String>(
      MaterialPageRoute<String>(
        builder: (_) => ReviewWorkspace(
          initialScope: ReviewDiffScope.workingTree,
          initialFile: node.path,
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
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
          child: SizedBox(
            width: double.infinity,
            child: SegmentedButton<_FileSurface>(
              key: const ValueKey('file-surface-selector'),
              segments: const [
                ButtonSegment(value: _FileSurface.files, label: Text('Files')),
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
            controller: _search,
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
                : null,
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
        Expanded(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final files = _fileList(theme);
              if (constraints.maxWidth < 900) return files;
              return Row(
                children: [
                  SizedBox(width: 340, child: files),
                  const VerticalDivider(width: 1),
                  Expanded(
                    child: _selectedPath == null
                        ? Center(
                            child: Text(
                              'Select a file to preview',
                              style: TextStyle(color: theme.hintColor),
                            ),
                          )
                        : _FileViewer(
                            key: ValueKey('$_selectedPath:$_selectedLine'),
                            controller: widget.controller,
                            path: _selectedPath!,
                            initialLine: _selectedLine,
                            embedded: true,
                            onAttachFile: widget.onAttachFile,
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
        onRefresh: () => _load(_path),
        child: ProductErrorState(message: _error!, onRetry: () => _load(_path)),
      );
    }
    if (_entries != null && entries.isEmpty) {
      return RefreshIndicator(
        onRefresh: () => _load(_path),
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
      onRefresh: () => _load(_path),
      child: ListView.builder(
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
          return ListTile(
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
                  : theme.hintColor,
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
                  _FileReviewAction(
                    change: change,
                    onPressed: () => _reviewFileChange(node),
                  )
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
                    style: const TextStyle(fontSize: 11),
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
          );
        },
      ),
    );
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
      child: ListView.builder(
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
    _symbolSearchDebounce?.cancel();
    widget.controller.removeListener(_controllerChanged);
    _search.removeListener(_searchChanged);
    _search.dispose();
    super.dispose();
  }
}

String _fileStatusLabel(String status) => switch (status) {
  'added' => 'Added',
  'deleted' => 'Deleted',
  'modified' => 'Modified',
  _ => 'Changed',
};

class _FileReviewAction extends StatelessWidget {
  final VersionControlFile change;
  final VoidCallback onPressed;

  const _FileReviewAction({required this.change, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = switch (change.status) {
      'deleted' => theme.colorScheme.error,
      'modified' => theme.colorScheme.tertiary,
      _ => theme.colorScheme.primary,
    };
    return IconButton(
      key: ValueKey('review-file-change-${change.path}'),
      tooltip: 'Review ${change.path} changes',
      onPressed: onPressed,
      icon: Icon(Icons.difference_outlined, size: 20, color: color),
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
          fontWeight: FontWeight.w800,
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
  const _FileViewer({
    super.key,
    required this.controller,
    required this.path,
    this.initialLine,
    this.embedded = false,
    this.onAttachFile,
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
                    child: Text(
                      widget.path.split('/').last,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Copy file contents',
                    icon: const Icon(Icons.copy_rounded, size: 18),
                    onPressed: _content?.isBinary == true
                        ? null
                        : () async {
                            await Clipboard.setData(
                              ClipboardData(text: _displayText),
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
                  if (widget.onAttachFile != null)
                    IconButton(
                      key: const Key('project-file-attach'),
                      tooltip: 'Attach to prompt',
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
                  IconButton(
                    key: const Key('project-file-download'),
                    tooltip: 'Save to device',
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
                  IconButton(
                    tooltip: 'Reload file',
                    icon: const Icon(Icons.refresh_rounded, size: 18),
                    onPressed: _fetch,
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(
                widget.initialLine == null
                    ? widget.path
                    : '${widget.path} · Line ${widget.initialLine}',
                style: theme.textTheme.labelSmall!.copyWith(
                  color: theme.hintColor,
                ),
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
