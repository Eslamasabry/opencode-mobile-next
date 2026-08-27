import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/file_preview.dart';
import '../widgets/product_states.dart';

/// Project file browser backed by `/file`, with name search (`/find/file`)
/// and content viewer.
enum _FileSurface { files, symbols }

class FilesScreen extends StatefulWidget {
  final ConnectionController controller;
  const FilesScreen({super.key, required this.controller});

  @override
  State<FilesScreen> createState() => _FilesScreenState();
}

class _FilesScreenState extends State<FilesScreen> {
  List<FileNode>? _entries;
  List<WorkspaceSymbol>? _symbols;
  _FileSurface _surface = _FileSurface.files;
  String _path = '';
  String? _error;
  bool _loading = false;
  String? _selectedPath;
  int? _selectedLine;
  String? _searchOriginPath;
  final _search = TextEditingController();
  ProductRepository? _repository;
  int _locationRevision = -1;
  int _controllerLocationRevision = -1;
  int _dataRefreshRevision = -1;
  int _requestGeneration = 0;

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
        throw StateError('The server is not connected.');
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
      setState(() => _error = '$e');
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
        throw StateError('The server is not connected.');
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
      setState(() => _error = '$e');
    } finally {
      if (mounted && generation == _requestGeneration) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _searchSymbols(String query) async {
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
        throw StateError('The server is not connected.');
      }
      final results = await repository.findWorkspaceSymbols(value);
      if (!mounted || generation != _requestGeneration) return;
      setState(() => _symbols = results);
    } catch (error) {
      if (!mounted || generation != _requestGeneration) return;
      setState(() => _error = error.toString());
    } finally {
      if (mounted && generation == _requestGeneration) {
        setState(() => _loading = false);
      }
    }
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
    if (_loading && _entries == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return RefreshIndicator(
        onRefresh: () => _load(_path),
        child: ProductErrorState(message: _error!, onRetry: () => _load(_path)),
      );
    }
    if (_entries?.isEmpty == true) {
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
        itemCount: _entries?.length ?? 0,
        itemBuilder: (context, i) {
          final node = _entries![i];
          return ListTile(
            dense: true,
            selected: node.path == _selectedPath,
            leading: Icon(
              node.isDir ? Icons.folder_rounded : Icons.description_outlined,
              size: 20,
              color: node.isDir ? theme.colorScheme.primary : theme.hintColor,
            ),
            title: Text(
              node.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: _search.text.isNotEmpty && node.path != node.name
                ? Text(
                    node.path,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 11,
                    ),
                  )
                : null,
            onTap: () {
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

  Widget _symbolList(ThemeData theme) {
    if (_loading && _symbols == null) {
      return const Center(child: CircularProgressIndicator());
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
          message: 'Try a different name or check language service status.',
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
    widget.controller.removeListener(_controllerChanged);
    _search.removeListener(_searchChanged);
    _search.dispose();
    super.dispose();
  }
}

class _FileViewer extends StatefulWidget {
  final ConnectionController controller;
  final String path;
  final int? initialLine;
  final bool embedded;
  const _FileViewer({
    super.key,
    required this.controller,
    required this.path,
    this.initialLine,
    this.embedded = false,
  });

  @override
  State<_FileViewer> createState() => __FileViewerState();
}

class __FileViewerState extends State<_FileViewer> {
  FileContent? _content;
  String? _error;
  int _generation = 0;

  static const maxChars = 200000;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    final generation = ++_generation;
    setState(() {
      _content = null;
      _error = null;
    });
    try {
      final api = await widget.controller.prepareActionTransport();
      if (!mounted || generation != _generation) return;
      if (api == null) throw StateError('The server is not connected.');
      final c = await api.fileContent(widget.path);
      if (mounted && generation == _generation) setState(() => _content = c);
    } catch (e) {
      if (mounted && generation == _generation) setState(() => _error = '$e');
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
                  fontSize: 10,
                ),
              ),
            ),
            const Divider(),
            Expanded(
              child: _content == null && _error == null
                  ? const Center(child: CircularProgressIndicator())
                  : _error != null
                  ? Center(
                      child: Text(
                        _error!,
                        style: TextStyle(color: theme.colorScheme.error),
                      ),
                    )
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
