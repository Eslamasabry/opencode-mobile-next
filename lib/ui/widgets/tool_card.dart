import 'package:flutter/material.dart';

import '../../api/models.dart';
import 'file_preview.dart';

typedef ToolOutputFileLoader =
    Future<FilePreviewData> Function(ToolOutputFile file);
typedef ToolOutputFileAction =
    Future<void> Function(ToolOutputFile file, FilePreviewData data);

/// Renders a single tool invocation as an expandable card with status,
/// title, input and output.
class ToolCard extends StatefulWidget {
  final String toolName;
  final ToolState state;
  final ToolOutputFileLoader? filePreviewLoader;
  final ToolOutputFileAction? onAttachFile;
  final ToolOutputFileAction? onDownloadFile;
  const ToolCard({
    super.key,
    required this.toolName,
    required this.state,
    this.filePreviewLoader,
    this.onAttachFile,
    this.onDownloadFile,
  });

  @override
  State<ToolCard> createState() => _ToolCardState();
}

class _ToolCardState extends State<ToolCard> {
  bool _expanded = false;
  final Map<String, Future<FilePreviewData>> _previewLoads = {};

  List<ToolOutputFile> get _files => widget.state.outputFiles.take(8).toList();
  List<ToolOutputFile> get _images =>
      _files.where((file) => file.isImage).toList();

  @override
  void initState() {
    super.initState();
    _syncPreviewLoads();
  }

  @override
  void didUpdateWidget(covariant ToolCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.state.outputFiles, widget.state.outputFiles) ||
        oldWidget.filePreviewLoader != widget.filePreviewLoader) {
      _syncPreviewLoads(reset: true);
    }
  }

  void _syncPreviewLoads({bool reset = false}) {
    if (reset) _previewLoads.clear();
    final identities = _files.map((file) => file.identity).toSet();
    _previewLoads.removeWhere((key, _) => !identities.contains(key));
    for (final file in _images) {
      _previewLoads.putIfAbsent(file.identity, () => _loadPreview(file));
    }
  }

  Future<FilePreviewData> _loadPreview(ToolOutputFile file) async {
    try {
      final url = file.url;
      if (url?.isNotEmpty == true) {
        return FilePreviewData.fromDataUrl(
          name: file.displayName,
          mimeType: file.mimeType,
          url: url,
        );
      }
      final loader = widget.filePreviewLoader;
      if (loader != null && file.path?.isNotEmpty == true) {
        return await loader(file);
      }
      return FilePreviewData(
        name: file.displayName,
        mimeType: file.mimeType,
        error: 'The generated file is not available from this server.',
      );
    } catch (error) {
      return FilePreviewData(
        name: file.displayName,
        mimeType: file.mimeType,
        error: 'Could not load this file from the OpenCode server: $error',
      );
    }
  }

  Future<FilePreviewData> _loadCached(ToolOutputFile file) =>
      _previewLoads.putIfAbsent(file.identity, () => _loadPreview(file));

  void _retryPreview(ToolOutputFile file) {
    setState(() => _previewLoads[file.identity] = _loadPreview(file));
  }

  IconData get _icon {
    final n = widget.toolName.toLowerCase();
    if (n.contains('bash') || n.contains('shell') || n.contains('terminal')) {
      return Icons.terminal_rounded;
    }
    if (n.contains('edit') || n.contains('write')) {
      return Icons.edit_note_rounded;
    }
    if (n.contains('read')) return Icons.description_rounded;
    if (n.contains('grep') || n.contains('find')) return Icons.search_rounded;
    if (n.contains('glob')) return Icons.folder_open_rounded;
    if (n.contains('webfetch') || n.contains('fetch') || n.contains('web')) {
      return Icons.public_rounded;
    }
    if (n.contains('todo')) return Icons.checklist_rounded;
    if (n.contains('task') || n.contains('agent')) {
      return Icons.smart_toy_rounded;
    }
    return Icons.build_rounded;
  }

  Color get _statusColor {
    switch (widget.state.status) {
      case 'completed':
        return Colors.green.shade400;
      case 'error':
        return Theme.of(context).colorScheme.error;
      default:
        return Theme.of(context).colorScheme.primary;
    }
  }

  bool get _running =>
      widget.state.status == 'pending' || widget.state.status == 'running';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasBody =
        (widget.state.output?.isNotEmpty ?? false) ||
        (widget.state.inputJson?.isNotEmpty ?? false) ||
        _files.isNotEmpty;
    final title = widget.state.title ?? widget.toolName;
    final reduceMotion = MediaQuery.disableAnimationsOf(context);

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 3),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: .35),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: theme.dividerColor.withValues(alpha: .4)),
      ),
      child: Column(
        children: [
          Semantics(
            button: hasBody,
            expanded: hasBody ? _expanded : null,
            label: '$title, ${widget.state.status}',
            child: InkWell(
              onTap: hasBody
                  ? () => setState(() => _expanded = !_expanded)
                  : null,
              borderRadius: BorderRadius.circular(8),
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(10, 7, 8, 7),
                  child: Row(
                    children: [
                      Icon(_icon, size: 15, color: theme.hintColor),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodySmall!.copyWith(
                            color: theme.colorScheme.onSurface.withValues(
                              alpha: .85,
                            ),
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                      if (_running && !reduceMotion)
                        SizedBox(
                          width: 12,
                          height: 12,
                          child: CircularProgressIndicator(
                            strokeWidth: 1.6,
                            color: theme.colorScheme.primary,
                          ),
                        )
                      else
                        Icon(
                          _running
                              ? Icons.hourglass_top_rounded
                              : widget.state.status == 'error'
                              ? Icons.error_outline_rounded
                              : Icons.check_circle_outline_rounded,
                          size: 14,
                          color: _statusColor,
                        ),
                      if (hasBody) ...[
                        const SizedBox(width: 4),
                        AnimatedRotation(
                          turns: _expanded ? .5 : 0,
                          duration: reduceMotion
                              ? Duration.zero
                              : const Duration(milliseconds: 150),
                          child: Icon(
                            Icons.expand_more_rounded,
                            size: 16,
                            color: theme.hintColor,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
          if (_files.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 2, 10, 10),
              child: Column(
                children: [
                  for (final file in _files)
                    Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: file.isImage
                          ? _ToolOutputPreview(
                              file: file,
                              load: _previewLoads[file.identity]!,
                              onRetry: () => _retryPreview(file),
                              onAttach: widget.onAttachFile,
                              onDownload: widget.onDownloadFile,
                            )
                          : _ToolOutputFileTile(
                              file: file,
                              load: () => _loadCached(file),
                              onAttach: widget.onAttachFile,
                              onDownload: widget.onDownloadFile,
                            ),
                    ),
                ],
              ),
            ),
          if (_expanded && hasBody)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(10, 4, 10, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (widget.state.inputJson?.isNotEmpty ?? false) ...[
                    Text(
                      'INPUT',
                      style: theme.textTheme.labelSmall!.copyWith(
                        color: theme.hintColor,
                        fontSize: 9,
                      ),
                    ),
                    const SizedBox(height: 2),
                    _Mono(text: widget.state.inputJson!, maxLines: 20),
                  ],
                  if (widget.state.output?.isNotEmpty ?? false) ...[
                    const SizedBox(height: 6),
                    Text(
                      widget.state.status == 'error' ? 'ERROR' : 'OUTPUT',
                      style: theme.textTheme.labelSmall!.copyWith(
                        color: theme.hintColor,
                        fontSize: 9,
                      ),
                    ),
                    const SizedBox(height: 2),
                    _Mono(text: widget.state.output!, maxLines: 200),
                  ],
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _ToolOutputPreview extends StatelessWidget {
  const _ToolOutputPreview({
    required this.file,
    required this.load,
    required this.onRetry,
    this.onAttach,
    this.onDownload,
  });

  final ToolOutputFile file;
  final Future<FilePreviewData> load;
  final VoidCallback onRetry;
  final ToolOutputFileAction? onAttach;
  final ToolOutputFileAction? onDownload;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return FutureBuilder<FilePreviewData>(
      future: load,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return Container(
            key: const Key('tool-output-image-loading'),
            height: 112,
            width: double.infinity,
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const SizedBox.square(
                  dimension: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
                const SizedBox(height: 8),
                Text(
                  'Loading ${file.displayName}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall,
                ),
              ],
            ),
          );
        }
        final data = snapshot.data;
        final error = snapshot.error?.toString() ?? data?.error;
        if (error != null || data?.bytes?.isNotEmpty != true) {
          return Container(
            key: const Key('tool-output-image-error'),
            width: double.infinity,
            padding: const EdgeInsets.fromLTRB(10, 8, 4, 8),
            decoration: BoxDecoration(
              color: theme.colorScheme.errorContainer.withValues(alpha: .22),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(
                color: theme.colorScheme.error.withValues(alpha: .28),
              ),
            ),
            child: Row(
              children: [
                Icon(
                  Icons.broken_image_outlined,
                  size: 18,
                  color: theme.colorScheme.error,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        file.displayName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        error ?? 'Image data is unavailable.',
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.labelSmall,
                      ),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: 'Retry image preview',
                  onPressed: onRetry,
                  icon: const Icon(Icons.refresh_rounded, size: 18),
                ),
              ],
            ),
          );
        }

        final previewData = data!;
        final imageBytes = previewData.bytes!;
        void openPreview() => showFilePreviewSheet(
          context,
          previewData,
          onAttach: onAttach == null
              ? null
              : () => onAttach!(file, previewData),
          onDownload: onDownload == null
              ? null
              : () => onDownload!(file, previewData),
        );
        return Semantics(
          button: true,
          label: 'Preview generated image ${file.displayName}',
          child: InkWell(
            onTap: openPreview,
            borderRadius: BorderRadius.circular(8),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Stack(
                children: [
                  Container(
                    width: double.infinity,
                    constraints: const BoxConstraints(
                      minHeight: 120,
                      maxHeight: 260,
                    ),
                    color: theme.colorScheme.surfaceContainerLowest,
                    child: Image.memory(
                      imageBytes,
                      key: const Key('tool-output-image'),
                      fit: BoxFit.contain,
                    ),
                  ),
                  Positioned(
                    left: 8,
                    right: 8,
                    bottom: 8,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surface.withValues(alpha: .9),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 5,
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.image_outlined, size: 14),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                file.displayName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: theme.textTheme.labelSmall,
                              ),
                            ),
                            const Icon(Icons.open_in_full_rounded, size: 13),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _ToolOutputFileTile extends StatefulWidget {
  const _ToolOutputFileTile({
    required this.file,
    required this.load,
    this.onAttach,
    this.onDownload,
  });

  final ToolOutputFile file;
  final Future<FilePreviewData> Function() load;
  final ToolOutputFileAction? onAttach;
  final ToolOutputFileAction? onDownload;

  @override
  State<_ToolOutputFileTile> createState() => _ToolOutputFileTileState();
}

class _ToolOutputFileTileState extends State<_ToolOutputFileTile> {
  bool _opening = false;

  Future<void> _open() async {
    if (_opening) return;
    setState(() => _opening = true);
    try {
      final data = await widget.load();
      if (!mounted) return;
      setState(() => _opening = false);
      await showFilePreviewSheet(
        context,
        data,
        onAttach: widget.onAttach == null
            ? null
            : () => widget.onAttach!(widget.file, data),
        onDownload: widget.onDownload == null
            ? null
            : () => widget.onDownload!(widget.file, data),
      );
    } finally {
      if (mounted && _opening) setState(() => _opening = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Semantics(
      button: true,
      label: 'Open generated file ${widget.file.displayName}',
      child: Material(
        color: theme.colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(8),
        child: InkWell(
          key: const Key('tool-output-file'),
          onTap: _opening ? null : _open,
          borderRadius: BorderRadius.circular(8),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 56),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              child: Row(
                children: [
                  Icon(
                    Icons.insert_drive_file_outlined,
                    color: theme.colorScheme.primary,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.file.displayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodySmall?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        Text(
                          widget.file.mimeType ?? 'Generated file',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (_opening)
                    const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  else
                    const Icon(Icons.open_in_new_rounded, size: 18),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Mono extends StatelessWidget {
  final String text;
  final int maxLines;
  const _Mono({required this.text, this.maxLines = 100});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(8),
      constraints: BoxConstraints(maxHeight: maxLines * 18.0),
      decoration: BoxDecoration(
        color: theme.brightness == Brightness.dark
            ? Colors.black.withValues(alpha: .4)
            : Colors.black.withValues(alpha: .04),
        borderRadius: BorderRadius.circular(6),
      ),
      child: SingleChildScrollView(
        child: SelectableText(
          text.length > 8000 ? '${text.substring(0, 8000)}\n… truncated' : text,
          style: theme.textTheme.bodySmall!.copyWith(
            fontFamily: 'monospace',
            fontSize: 11.5,
          ),
        ),
      ),
    );
  }
}
