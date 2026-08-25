import 'dart:convert';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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
                        : const Icon(Icons.add_comment_outlined, size: 20),
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
  const FilePreviewBody({super.key, required this.data});

  final FilePreviewData data;

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
      return SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: SelectableText(
          data.text!,
          key: const Key('file-preview-text'),
          style: theme.textTheme.bodySmall?.copyWith(
            fontFamily: 'monospace',
            fontSize: 12.5,
            height: 1.45,
          ),
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
