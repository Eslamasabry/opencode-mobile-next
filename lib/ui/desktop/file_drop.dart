import 'dart:async';
import 'dart:typed_data';

import 'package:desktop_drop/desktop_drop.dart';
import 'package:flutter/material.dart';

import 'desktop_interaction.dart';

/// A file the window manager handed the app through a drag-and-drop.
///
/// Deliberately not the plugin's own type: the composer's drop handler is
/// then plain Dart that a widget test can drive without a platform channel.
class DroppedFile {
  const DroppedFile({
    required this.name,
    required this.mimeType,
    required this.length,
    required this.readBytes,
  });

  final String name;
  final String? mimeType;

  /// Consulted before [readBytes], so an oversized file is refused without
  /// ever being pulled into memory.
  final Future<int> Function() length;
  final Future<Uint8List> Function() readBytes;
}

typedef DroppedFileHandler = Future<void> Function(List<DroppedFile> files);

/// Accepts files dropped onto [child] on desktop.
///
/// Off desktop this returns [child] untouched — no plugin, no listener, no
/// extra widget in the Android tree.
class DesktopFileDropTarget extends StatefulWidget {
  const DesktopFileDropTarget({
    super.key,
    required this.onDrop,
    required this.child,
  });

  final DroppedFileHandler onDrop;
  final Widget child;

  @override
  DesktopFileDropTargetState createState() => DesktopFileDropTargetState();
}

class DesktopFileDropTargetState extends State<DesktopFileDropTarget> {
  bool _dragging = false;

  /// Runs the drop handler as though the window manager had delivered
  /// [files]. Lets a widget test exercise the real attachment pipeline
  /// without a platform channel.
  @visibleForTesting
  Future<void> debugHandleDrop(List<DroppedFile> files) =>
      _handle(files);

  Future<void> _handle(List<DroppedFile> files) async {
    if (mounted) setState(() => _dragging = false);
    if (files.isEmpty) return;
    await widget.onDrop(files);
  }

  @override
  Widget build(BuildContext context) {
    if (!desktopInteractions) return widget.child;
    final theme = Theme.of(context);
    return DropTarget(
      onDragEntered: (_) => setState(() => _dragging = true),
      onDragExited: (_) => setState(() => _dragging = false),
      onDragDone: (details) => unawaited(
        _handle([
          for (final item in details.files)
            DroppedFile(
              name: item.name,
              mimeType: item.mimeType,
              length: item.length,
              readBytes: item.readAsBytes,
            ),
        ]),
      ),
      child: Stack(
        children: [
          widget.child,
          if (_dragging)
            Positioned.fill(
              child: IgnorePointer(
                child: DecoratedBox(
                  key: const ValueKey('composer-drop-highlight'),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primaryContainer.withValues(
                      alpha: .38,
                    ),
                    border: Border.all(color: theme.colorScheme.primary),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Center(
                    child: Text(
                      'Drop to attach',
                      style: theme.textTheme.labelLarge?.copyWith(
                        color: theme.colorScheme.onPrimaryContainer,
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
