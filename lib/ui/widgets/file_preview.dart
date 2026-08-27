import 'dart:convert';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'markdown.dart';

/// Normalized file content that can be rendered by [FilePreviewBody].
class FilePreviewData {
  FilePreviewData({
    required this.name,
    String? mimeType,
    this.bytes,
    this.text,
    this.error,
  }) : mimeType = _normalizedMime(mimeType) ?? _mimeFromName(name);

  factory FilePreviewData.fromDataUrl({
    required String name,
    required String? mimeType,
    required String? url,
  }) {
    final normalizedMime = _normalizedMime(mimeType);
    if (url == null || url.trim().isEmpty) {
      return FilePreviewData(
        name: name,
        mimeType: normalizedMime,
        error: 'The attachment content is not included in this message.',
      );
    }
    if (!url.startsWith('data:')) {
      return FilePreviewData(
        name: name,
        mimeType: normalizedMime,
        error: 'Remote attachment previews are not available.',
      );
    }

    try {
      final data = UriData.parse(url);
      final resolvedMime = normalizedMime ?? _normalizedMime(data.mimeType);
      final bytes = Uint8List.fromList(data.contentAsBytes());
      return FilePreviewData(
        name: name,
        mimeType: resolvedMime,
        bytes: bytes,
        text: _isTextMime(resolvedMime)
            ? utf8.decode(bytes, allowMalformed: true)
            : null,
      );
    } on FormatException {
      return FilePreviewData(
        name: name,
        mimeType: normalizedMime,
        error: 'The attachment data could not be decoded.',
      );
    }
  }

  final String name;
  final String? mimeType;
  final Uint8List? bytes;
  final String? text;
  final String? error;

  bool get isRasterImage => switch (mimeType) {
    'image/png' ||
    'image/jpeg' ||
    'image/gif' ||
    'image/webp' ||
    'image/bmp' => true,
    _ => false,
  };

  int? get byteLength => bytes?.length;

  Uint8List? get exportBytes {
    if (bytes != null) return bytes;
    if (text != null) return Uint8List.fromList(utf8.encode(text!));
    return null;
  }

  static String? _normalizedMime(String? value) {
    final normalized = value?.split(';').first.trim().toLowerCase();
    return normalized == null || normalized.isEmpty ? null : normalized;
  }

  static String? _mimeFromName(String name) {
    final dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length - 1) return null;
    return switch (name.substring(dot + 1).toLowerCase()) {
      'png' => 'image/png',
      'jpg' || 'jpeg' => 'image/jpeg',
      'gif' => 'image/gif',
      'webp' => 'image/webp',
      'bmp' => 'image/bmp',
      'svg' => 'image/svg+xml',
      'pdf' => 'application/pdf',
      'json' => 'application/json',
      'xml' => 'application/xml',
      'md' ||
      'txt' ||
      'log' ||
      'dart' ||
      'js' ||
      'ts' ||
      'tsx' ||
      'jsx' ||
      'py' ||
      'go' ||
      'rs' ||
      'yaml' ||
      'yml' => 'text/plain',
      _ => null,
    };
  }

  static bool _isTextMime(String? mime) =>
      mime?.startsWith('text/') == true ||
      mime == 'application/json' ||
      mime == 'application/javascript' ||
      mime == 'application/xml' ||
      mime == 'image/svg+xml';
}

Future<void> showFilePreviewSheet(
  BuildContext context,
  FilePreviewData data, {
  Future<void> Function()? onAttach,
  Future<void> Function()? onDownload,
}) => showModalBottomSheet<void>(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  showDragHandle: true,
  builder: (context) =>
      _FilePreviewSheet(data: data, onAttach: onAttach, onDownload: onDownload),
);

class _FilePreviewSheet extends StatefulWidget {
  const _FilePreviewSheet({required this.data, this.onAttach, this.onDownload});

  final FilePreviewData data;
  final Future<void> Function()? onAttach;
  final Future<void> Function()? onDownload;

  @override
  State<_FilePreviewSheet> createState() => _FilePreviewSheetState();
}

class _FilePreviewSheetState extends State<_FilePreviewSheet> {
  bool _attaching = false;
  bool _downloading = false;

  Future<void> _attach() async {
    final action = widget.onAttach;
    if (action == null || _attaching) return;
    setState(() => _attaching = true);
    try {
      await action();
      if (mounted) Navigator.of(context).pop();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Could not attach file: $error')));
      setState(() => _attaching = false);
    }
  }

  Future<void> _download() async {
    if (_downloading) return;
    final bytes = widget.data.exportBytes;
    if (bytes == null) return;
    setState(() => _downloading = true);
    try {
      final action = widget.onDownload;
      if (action != null) {
        await action();
      } else {
        final savedPath = await FilePicker.saveFile(
          dialogTitle: 'Save ${widget.data.name}',
          fileName: widget.data.name,
          bytes: bytes,
        );
        if (mounted && savedPath != null) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('${widget.data.name} saved.')));
        }
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Could not save file: $error')));
      }
    } finally {
      if (mounted) setState(() => _downloading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final data = widget.data;
    return SizedBox(
      key: const Key('file-preview-sheet'),
      height: MediaQuery.sizeOf(context).height * .86,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 8, 8),
            child: Row(
              children: [
                Icon(
                  data.isRasterImage
                      ? Icons.image_outlined
                      : Icons.description_outlined,
                  size: 20,
                  color: theme.colorScheme.primary,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        data.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      Text(
                        _metadata(data),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                if (data.text != null)
                  IconButton(
                    tooltip: 'Copy file contents',
                    onPressed: () async {
                      await Clipboard.setData(ClipboardData(text: data.text!));
                      if (!context.mounted) return;
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('File contents copied'),
                          duration: Duration(seconds: 1),
                        ),
                      );
                    },
                    icon: const Icon(Icons.copy_rounded, size: 19),
                  ),
                if (widget.onAttach != null)
                  IconButton(
                    key: const Key('file-preview-attach'),
                    tooltip: 'Attach to prompt',
                    onPressed: _attaching ? null : _attach,
                    icon: _attaching
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.attach_file_rounded, size: 20),
                  ),
                if (data.exportBytes != null)
                  IconButton(
                    key: const Key('file-preview-download'),
                    tooltip: 'Save to device',
                    onPressed: _downloading ? null : _download,
                    icon: _downloading
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.download_rounded, size: 20),
                  ),
                IconButton(
                  tooltip: 'Close preview',
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(child: FilePreviewBody(data: data)),
        ],
      ),
    );
  }
}

/// Renders supported file content without sending it to another application.
class FilePreviewBody extends StatelessWidget {
  const FilePreviewBody({super.key, required this.data, this.initialLine});

  final FilePreviewData data;
  final int? initialLine;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (data.error != null) {
      return _PreviewNotice(
        icon: Icons.visibility_off_outlined,
        title: 'Preview unavailable',
        message: data.error!,
      );
    }
    if (data.isRasterImage && data.bytes?.isNotEmpty == true) {
      return ColoredBox(
        color: theme.colorScheme.surfaceContainerLowest,
        child: Stack(
          children: [
            Positioned.fill(
              child: InteractiveViewer(
                minScale: .75,
                maxScale: 5,
                boundaryMargin: const EdgeInsets.all(48),
                child: Center(
                  child: Image.memory(
                    data.bytes!,
                    key: const Key('file-preview-image'),
                    fit: BoxFit.contain,
                    errorBuilder: (_, _, _) => const _PreviewNotice(
                      icon: Icons.broken_image_outlined,
                      title: 'Image could not be displayed',
                      message: 'The file data is not a supported image.',
                    ),
                  ),
                ),
              ),
            ),
            Positioned(
              left: 12,
              bottom: 12,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: theme.colorScheme.surface.withValues(alpha: .88),
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(color: theme.colorScheme.outlineVariant),
                ),
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  child: Text('Pinch to zoom', style: TextStyle(fontSize: 11)),
                ),
              ),
            ),
          ],
        ),
      );
    }
    if (data.text != null) {
      if (initialLine != null) {
        return _FocusedSourcePreview(
          text: data.text!,
          initialLine: initialLine!,
        );
      }
      return SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: SmartTextPreview(
          key: const Key('file-preview-text'),
          data: data,
        ),
      );
    }
    return _PreviewNotice(
      icon: Icons.insert_drive_file_outlined,
      title: 'Preview unavailable',
      message: [
        data.mimeType ?? 'Unknown file type',
        if (data.byteLength != null) '${data.byteLength} bytes',
        'This format cannot be rendered in the app yet.',
      ].join('\n'),
    );
  }
}

class _FocusedSourcePreview extends StatefulWidget {
  final String text;
  final int initialLine;

  const _FocusedSourcePreview({required this.text, required this.initialLine});

  @override
  State<_FocusedSourcePreview> createState() => _FocusedSourcePreviewState();
}

class _FocusedSourcePreviewState extends State<_FocusedSourcePreview> {
  static const _lineHeight = 24.0;
  late final List<String> _lines = widget.text.split('\n');
  late final int _targetLine = widget.initialLine.clamp(1, _lines.length);
  late final ScrollController _vertical = ScrollController(
    initialScrollOffset: ((_targetLine - 1) * _lineHeight - _lineHeight * 2)
        .clamp(0, (_lines.length - 1) * _lineHeight),
  );
  final ScrollController _horizontal = ScrollController();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final longestLine = _lines.fold<int>(
      0,
      (length, line) => line.length > length ? line.length : length,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final textScale = MediaQuery.textScalerOf(context).scale(1);
        final widthScale = textScale < 2 ? 2.0 : textScale;
        final contentWidth = (longestLine * 7.8 * widthScale + 72).clamp(
          constraints.maxWidth,
          4000.0,
        );
        return Scrollbar(
          controller: _horizontal,
          child: SingleChildScrollView(
            controller: _horizontal,
            scrollDirection: Axis.horizontal,
            child: SizedBox(
              width: contentWidth,
              height: constraints.maxHeight,
              child: ListView.builder(
                key: const Key('file-preview-focused-source'),
                controller: _vertical,
                itemExtent: _lineHeight,
                itemCount: _lines.length,
                itemBuilder: (context, index) {
                  final selected = index + 1 == _targetLine;
                  return ColoredBox(
                    key: selected
                        ? const Key('file-preview-target-line')
                        : null,
                    color: selected
                        ? theme.colorScheme.primaryContainer.withValues(
                            alpha: .45,
                          )
                        : Colors.transparent,
                    child: Row(
                      children: [
                        SizedBox(
                          width: 54,
                          child: Text(
                            '${index + 1}',
                            textAlign: TextAlign.right,
                            style: theme.textTheme.labelSmall?.copyWith(
                              fontFamily: 'monospace',
                              color: selected
                                  ? theme.colorScheme.primary
                                  : theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        SelectableText(
                          _lines[index].isEmpty ? ' ' : _lines[index],
                          maxLines: 1,
                          style: theme.textTheme.bodySmall?.copyWith(
                            fontFamily: 'monospace',
                            height: 1.35,
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
          ),
        );
      },
    );
  }

  @override
  void dispose() {
    _vertical.dispose();
    _horizontal.dispose();
    super.dispose();
  }
}

/// Renders textual artifacts according to their actual content while keeping
/// a selectable raw representation available for Markdown.
class SmartTextPreview extends StatefulWidget {
  const SmartTextPreview({super.key, required this.data});

  final FilePreviewData data;

  @override
  State<SmartTextPreview> createState() => _SmartTextPreviewState();
}

class _SmartTextPreviewState extends State<SmartTextPreview> {
  bool _rawMarkdown = false;

  String get _text => widget.data.text ?? '';

  bool get _isMarkdown {
    final mime = widget.data.mimeType;
    final name = widget.data.name.toLowerCase();
    if (mime == 'text/markdown' ||
        name.endsWith('.md') ||
        name.endsWith('.mdx')) {
      return true;
    }
    return RegExp(
      r'(^|\n)#{1,6}\s+|(^|\n)```|(^|\n)\|[^\n]+\|\s*\n\|?\s*:?-{3,}',
      multiLine: true,
    ).hasMatch(_text);
  }

  String? get _language {
    final name = widget.data.name.toLowerCase().split('?').first;
    final dot = name.lastIndexOf('.');
    final extension = dot < 0 ? '' : name.substring(dot + 1);
    return switch (extension) {
      'dart' => 'dart',
      'js' || 'mjs' || 'cjs' => 'javascript',
      'ts' => 'typescript',
      'tsx' => 'tsx',
      'jsx' => 'jsx',
      'py' => 'python',
      'go' => 'go',
      'rs' => 'rust',
      'java' => 'java',
      'kt' || 'kts' => 'kotlin',
      'swift' => 'swift',
      'c' || 'h' => 'c',
      'cc' || 'cpp' || 'cxx' || 'hpp' => 'cpp',
      'cs' => 'csharp',
      'sh' || 'bash' || 'zsh' => 'shell',
      'html' || 'htm' => 'html',
      'css' => 'css',
      'scss' => 'scss',
      'xml' || 'svg' => 'xml',
      'yaml' || 'yml' => 'yaml',
      'toml' => 'toml',
      'sql' => 'sql',
      'gradle' => 'gradle',
      'diff' || 'patch' => 'diff',
      _ => null,
    };
  }

  String? get _prettyJson {
    final mime = widget.data.mimeType;
    final name = widget.data.name.toLowerCase();
    final trimmed = _text.trim();
    final candidate =
        mime == 'application/json' ||
        name.endsWith('.json') ||
        ((trimmed.startsWith('{') && trimmed.endsWith('}')) ||
            (trimmed.startsWith('[') && trimmed.endsWith(']')));
    if (!candidate) return null;
    try {
      return const JsonEncoder.withIndent('  ').convert(jsonDecode(trimmed));
    } on FormatException {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final prettyJson = _prettyJson;
    final language = _language;
    if (_isMarkdown) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Align(
            alignment: Alignment.centerRight,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHigh,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextButton(
                    key: const Key('file-preview-rendered-mode'),
                    onPressed: _rawMarkdown
                        ? () => setState(() => _rawMarkdown = false)
                        : null,
                    child: const Text('Rendered'),
                  ),
                  TextButton(
                    key: const Key('file-preview-raw-mode'),
                    onPressed: _rawMarkdown
                        ? null
                        : () => setState(() => _rawMarkdown = true),
                    child: const Text('Raw'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          if (_rawMarkdown)
            CodeBlock(code: _text, language: 'markdown')
          else
            MarkdownText(_text),
        ],
      );
    }
    if (prettyJson != null) {
      return CodeBlock(code: prettyJson, language: 'json');
    }
    if (language != null) {
      return CodeBlock(code: _text, language: language);
    }
    return SelectableText(
      _text,
      style: theme.textTheme.bodySmall?.copyWith(height: 1.45),
    );
  }
}

class _PreviewNotice extends StatelessWidget {
  const _PreviewNotice({
    required this.icon,
    required this.title,
    required this.message,
  });

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) => Center(
    child: SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 42),
          const SizedBox(height: 12),
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Text(message, textAlign: TextAlign.center),
        ],
      ),
    ),
  );
}

String _metadata(FilePreviewData data) => [
  data.mimeType ?? 'Unknown file type',
  if (data.byteLength != null) '${data.byteLength} bytes',
].join(' · ');
