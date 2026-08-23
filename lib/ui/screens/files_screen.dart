import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../state/connection.dart';

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
  final _search = TextEditingController();

  @override
  void initState() {
    super.initState();
    _load('');
  }

  Future<void> _load(String path) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final nodes = await widget.controller.api!.listFiles(path);
      if (!mounted) return;
      setState(() {
        _entries = nodes;
        _path = path;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _searchFiles(String q) async {
    if (q.trim().isEmpty) {
      await _load(_path);
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await widget.controller.api!.findFile(q.trim());
      if (!mounted) return;
      setState(() {
        _entries = results.map((r) {
          final parts = r.split('/');
          return FileNode(name: parts.last, path: r, isDir: false);
        }).toList();
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _openFile(FileNode node) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) => _FileViewer(
        controller: widget.controller,
        path: node.path,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final crumbs = _path.split('/').where((c) => c.isNotEmpty).toList();

    return Column(children: [
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
                    }),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
          onSubmitted: _searchFiles,
          textInputAction: TextInputAction.search,
        ),
      ),
      if (_path.isNotEmpty)
        SizedBox(
          height: 40,
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
        child: _loading && _entries == null
            ? const Center(child: CircularProgressIndicator())
            : _error != null
                ? Center(
                    child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text('$_error', textAlign: TextAlign.center),
                  ))
                : RefreshIndicator(
                    onRefresh: () => _load(_path),
                    child: ListView.builder(
                      itemCount: _entries?.length ?? 0,
                      itemBuilder: (context, i) {
                        final n = _entries![i];
                        return ListTile(
                          dense: true,
                          leading: Icon(
                            n.isDir
                                ? Icons.folder_rounded
                                : Icons.description_outlined,
                            size: 20,
                            color: n.isDir
                                ? theme.colorScheme.primary
                                : theme.hintColor,
                          ),
                          title: Text(n.name,
                              maxLines: 1, overflow: TextOverflow.ellipsis),
                          onTap: () {
                            if (n.isDir) {
                              _search.clear();
                              final base =
                                  _path.isEmpty ? '' : (_path.endsWith('/') ? _path : '$_path/');
                              final child =
                                  n.path.startsWith('/') ? n.path.substring(1) : n.path;
                              _load('$base$child');
                            } else {
                              _openFile(n);
                            }
                          },
                        );
                      },
                    ),
                  ),
      ),
    ]);
  }
}

class _FileViewer extends StatefulWidget {
  final ConnectionController controller;
  final String path;
  const _FileViewer({required this.controller, required this.path});

  @override
  State<_FileViewer> createState() => __FileViewerState();
}

class __FileViewerState extends State<_FileViewer> {
  String? _content;
  String? _error;

  static const maxChars = 200000;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    try {
      final c = await widget.controller.api!.fileContent(widget.path);
      var text = c.content;
      if (text.length > maxChars) text = '${text.substring(0, maxChars)}\n… truncated';
      if (mounted) setState(() => _content = text);
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * .85,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 6, 4, 4),
            child: Row(children: [
              Expanded(
                child: Text(widget.path.split('/').last,
                    style: const TextStyle(fontWeight: FontWeight.w600)),
              ),
              IconButton(
                tooltip: 'Copy',
                icon: const Icon(Icons.copy_rounded, size: 18),
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: _content ?? ''));
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                        content: Text('Copied'),
                        duration: Duration(seconds: 1)));
                  }
                },
              ),
              IconButton(
                tooltip: 'Open in new sheet',
                icon: const Icon(Icons.refresh_rounded, size: 18),
                onPressed: _fetch,
              ),
            ]),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(widget.path,
                style: theme.textTheme.labelSmall!
                    .copyWith(color: theme.hintColor, fontSize: 10)),
          ),
          const Divider(),
          Expanded(
            child: _content == null && _error == null
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                    ? Center(
                        child: Text(_error!,
                            style: TextStyle(color: theme.colorScheme.error)))
                    : SingleChildScrollView(
                        padding: const EdgeInsets.all(12),
                        child: SelectableText(
                          _content!,
                          style: theme.textTheme.bodySmall!
                              .copyWith(fontFamily: 'monospace', fontSize: 12.5, height: 1.45),
                        ),
                      ),
          ),
        ]),
      ),
    );
  }
}
