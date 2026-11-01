import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';

/// What the composer's selection menu needs to offer "Paste image": a cheap
/// check that the clipboard holds a picture, and what to do when the
/// person taps it. The check runs when the menu opens, never in the
/// background.
class KitPasteImage {
  const KitPasteImage({required this.available, required this.onPaste});

  final Future<bool> Function() available;
  final VoidCallback onPaste;
}

/// The text field's long-press menu with "Paste image" added next to Paste
/// while the clipboard holds a picture. Without one it is the platform's
/// own menu, item for item.
class KitPasteImageMenu extends StatefulWidget {
  const KitPasteImageMenu({
    super.key,
    required this.state,
    required this.paste,
  });

  final EditableTextState state;
  final KitPasteImage paste;

  @override
  State<KitPasteImageMenu> createState() => _KitPasteImageMenuState();
}

class _KitPasteImageMenuState extends State<KitPasteImageMenu> {
  bool _hasImage = false;

  @override
  void initState() {
    super.initState();
    widget.paste.available().then((value) {
      if (mounted && value) setState(() => _hasImage = true);
    });
  }

  @override
  Widget build(BuildContext context) {
    final items = [...widget.state.contextMenuButtonItems];
    if (_hasImage) {
      final label =
          Localizations.of<AppLocalizations>(
            context,
            AppLocalizations,
          )?.composerPasteImage ??
          'Paste image';
      final at = items.indexWhere((i) => i.type == ContextMenuButtonType.paste);
      final item = ContextMenuButtonItem(
        label: label,
        onPressed: () {
          widget.state.hideToolbar();
          widget.paste.onPaste();
        },
      );
      items.insert(at < 0 ? items.length : at + 1, item);
    }
    if (items.isEmpty) return const SizedBox.shrink();
    return AdaptiveTextSelectionToolbar.buttonItems(
      anchors: widget.state.contextMenuAnchors,
      buttonItems: items,
    );
  }
}
