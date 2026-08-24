import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';

/// Project file browser backed by `/file`, with name search (`/find/file`)
/// and content viewer.
class FilesScreen extends StatefulWidget {
  final ConnectionController controller;
  const FilesScreen({super.key, required this.controller});

  @override
  State<FilesScreen> createState() => _FilesScreenState();
}

class _FilesScreenState extends State<FilesScreen> {
  List<FileNode>? _entries;
  String _path = '';
  String? _error;
  bool _loading = false;
  String? _selectedPath;
  final _search = TextEditingController();
  ProductRepository? _repository;
  int _locationRevision = -1;
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
  }

  void _controllerChanged() {
    final repository = widget.controller.repository;
    final revision = _revisionOf(repository);
    if (identical(repository, _repository) && revision == _locationRevision) {
      return;
    }
    _repository = repository;
    _locationRevision = revision;
    _requestGeneration++;
    _search.clear();
    setState(() {
      _entries = null;
      _path = '';
      _selectedPath = null;
      _error = null;
    });
    _load('');
  }

  int _revisionOf(ProductRepository? repository) => Object.hash(
    widget.controller.locationRevision,
    repository is LocationAwareProductRepository
        ? (repository as LocationAwareProductRepository).locationRevision
        : 0,
  );

  Future<void> _load(String path) async {
    final api = widget.controller.api;
    final generation = ++_requestGeneration;
    if (api == null) {
      setState(() {
        _loading = false;
        _error = 'The server is not connected.';
      });
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final nodes = await api.listFiles(path);
      if (!mounted || generation != _requestGeneration) return;
      setState(() {
        _entries = nodes;
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
      await _load(_path);
      return;
    }
    final api = widget.controller.api;
    final generation = ++_requestGeneration;
    if (api == null) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await api.findFile(q.trim());
      if (!mounted || generation != _requestGeneration) return;
      setState(() {
        _entries = results.map((r) {
          final parts = r.split('/');
          return FileNode(name: parts.last, path: r, isDir: false);
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

  void _openFile(FileNode node) {
    if (MediaQuery.sizeOf(context).width >= 900) {
      setState(() => _selectedPath = node.path);
      return;
    }
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) =>
          _FileViewer(controller: widget.controller, path: node.path),
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
          child: TextField(
            controller: _search,
            decoration: InputDecoration(
              isDense: true,
              prefixIcon: const Icon(Icons.search_rounded, size: 20),
              hintText: 'Find file by name…',
              suffixIcon: _search.text.isEmpty
                  ? null
                  : IconButton(
                      icon: const Icon(Icons.clear_rounded, size: 18),
                      onPressed: () {
                        _search.clear();
                        _load('');
                      },
                    ),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            onSubmitted: _searchFiles,
            textInputAction: TextInputAction.search,
          ),
        ),
        if (_path.isNotEmpty)
          SizedBox(
            height: 52,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              children: [
                ActionChip(
                  label: Text(_path.isEmpty ? '/' : '/${crumbs.join('/')}'),
                  onPressed: () {
                    _search.clear();
                    _load('');
                  },
                ),
                for (var i = 1; i <= crumbs.length; i++)
                  Padding(
                    padding: const EdgeInsets.only(left: 6),
                    child: ActionChip(
                      label: Text(crumbs[i - 1]),
                      onPressed: i == crumbs.length
                          ? null
                          : () {
                              _search.clear();
                              _load('/${crumbs.take(i).join('/')}');
                            },
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
                            key: ValueKey(_selectedPath),
                            controller: widget.controller,
                            path: _selectedPath!,
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
            onTap: () {
              if (node.isDir) {
                _search.clear();
                final base = _path.isEmpty
                    ? ''
                    : (_path.endsWith('/') ? _path : '$_path/');
                final child = node.path.startsWith('/')
                    ? node.path.substring(1)
                    : node.path;
                _load('$base$child');
              } else {
                _openFile(node);
              }
            },
          );
        },
      ),
    );
  }

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
  final bool embedded;
  const _FileViewer({
    super.key,
    required this.controller,
    required this.path,
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
      final c = await widget.controller.api!.fileContent(widget.path);
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
                widget.path,
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
                  : _content!.isBinary
                  ? _BinaryFileState(content: _content!)
                  : SingleChildScrollView(
                      padding: const EdgeInsets.all(12),
                      child: SelectableText(
                        _displayText,
                        style: theme.textTheme.bodySmall!.copyWith(
                          fontFamily: 'monospace',
                          fontSize: 12.5,
                          height: 1.45,
                        ),
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _BinaryFileState extends StatelessWidget {
  final FileContent content;

  const _BinaryFileState({required this.content});

  @override
  Widget build(BuildContext context) {
    final bytes = content.bytes();
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.insert_drive_file_outlined, size: 42),
            const SizedBox(height: 12),
            Text('Binary file', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 6),
            Text(
              [
                content.mimeType ?? 'Unknown media type',
                '${bytes.length} bytes',
                if (content.encoding != null) content.encoding!,
              ].join(' - '),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            const Text(
              'Binary data is not rendered as source text.',
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
