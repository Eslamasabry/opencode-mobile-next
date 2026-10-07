import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_buttons.dart';
import 'kit_icon_button.dart';
import 'kit_image.dart';
import 'kit_surface.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// What an agent asks the phone for.
enum KitSenseKind { photo, file, voice }

/// One thing the person picked, shown with a way to take it back.
@immutable
class KitSenseItem {
  const KitSenseItem({
    required this.id,
    required this.name,
    required this.removeLabel,
    this.thumbnail,
    this.detail,
  });

  /// Stable across rebuilds; the caller's own key for the item.
  final String id;

  /// The file's name, or "Voice note".
  final String name;

  /// The remove control's name, naming the item: "Remove photo 1".
  final String removeLabel;

  /// A photo's preview bytes; without them the kind's glyph stands in.
  final Uint8List? thumbnail;

  /// A quiet second line: "2.1 MB", "0:14".
  final String? detail;
}

/// One way to get something from the phone: "Take photo", "Choose photo",
/// "Choose file", "Record".
@immutable
class KitSenseAction {
  const KitSenseAction({
    required this.label,
    required this.onPressed,
    this.icon,
    this.key,
  });

  final String label;

  /// The caller opens the camera, the picker or the recorder.
  final VoidCallback onPressed;
  final IconData? icon;
  final Key? key;
}

/// An agent asking for a photo, a file or a voice note from the phone, drawn
/// as pure UI: a [purpose] line (why the agent wants it), the [actions] that
/// get one ("Take photo", "Choose photo"), the [items] picked so far with a
/// remove control each, and a Send. Nothing is read or sent until the person
/// presses Send; the callbacks go out, the items come in.
///
/// With [items] at [max] the actions are disabled with [maxReachedLabel]
/// beneath them; with no items Send is disabled with [sendDisabledReason]
/// (a disabled control always says why). While [sending] Send shows its
/// working look and every control ignores input. Send is the card's one
/// primary unless [secondary] (a card in a list, where the screen keeps its
/// own).
///
/// States: disabled, working.
class KitSenseAsk extends StatelessWidget {
  const KitSenseAsk({
    super.key,
    required this.kind,
    required this.purpose,
    required this.actions,
    required this.items,
    required this.sendLabel,
    required this.onSend,
    this.max = 4,
    this.countLabel,
    this.maxReachedLabel,
    this.sendDisabledReason,
    this.sending = false,
    this.secondary = false,
    this.onRemove,
    this.sendKey,
  });

  final KitSenseKind kind;

  /// Why the agent wants it, in the agent's words (plain text).
  final String purpose;
  final List<KitSenseAction> actions;
  final List<KitSenseItem> items;

  /// How many items may be sent.
  final int max;

  /// "2 of 4", from the caller's ARB; null hides the line.
  final String? countLabel;

  /// Under the disabled actions once [max] is reached.
  final String? maxReachedLabel;

  final String sendLabel;
  final VoidCallback? onSend;

  /// Why Send is off with nothing picked.
  final String? sendDisabledReason;
  final bool sending;
  final bool secondary;

  /// Takes an item back; the item's own remove control calls it with its id.
  final ValueChanged<String>? onRemove;
  final Key? sendKey;

  IconData get _glyph => switch (kind) {
    KitSenseKind.photo => AppIconography.image,
    KitSenseKind.file => AppIconography.file,
    KitSenseKind.voice => AppIconography.mic,
  };

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final full = items.length >= max;
    final canSend = items.isNotEmpty && onSend != null && !sending;
    final send = KitAction(
      label: sendLabel,
      key: sendKey,
      working: sending,
      onPressed: canSend ? onSend : null,
      disabledReason: canSend || sending
          ? null
          : (sendDisabledReason ?? sendLabel),
    );
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitText(purpose, role: KitTextRole.body, tone: KitTextTone.primary),
        SizedBox(height: tokens.space3),
        for (final action in actions) ...[
          KitButton.fromAction(
            KitAction(
              label: action.label,
              icon: action.icon,
              key: action.key,
              // The one shared reason sits under the buttons (STATE-8).
              onPressed: full || sending ? null : action.onPressed,
              working: false,
            ),
            role: KitButtonRole.secondary,
          ),
          SizedBox(height: tokens.space2),
        ],
        if (full && maxReachedLabel != null)
          Padding(
            padding: EdgeInsetsDirectional.only(bottom: tokens.space2),
            child: KitText(
              maxReachedLabel!,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ),
        if (items.isNotEmpty) ...[
          if (countLabel != null)
            Padding(
              padding: EdgeInsetsDirectional.only(bottom: tokens.space1),
              child: KitText(
                countLabel!,
                role: KitTextRole.caption,
                tone: KitTextTone.secondary,
                tabular: true,
              ),
            ),
          for (final item in items) _item(context, item),
          SizedBox(height: tokens.space2),
        ],
        KitActionBlock(
          primary: secondary ? null : send,
          secondary: secondary ? send : null,
        ),
      ],
    );
  }

  Widget _item(BuildContext context, KitSenseItem item) {
    final tokens = KitTokens.of(context);
    final bytes = item.thumbnail;
    final Widget leading = bytes != null
        ? SizedBox.square(
            dimension: tokens.iconTileSize,
            child: KitImage(
              source: KitImageSource.memory(bytes),
              semanticsLabel: null,
              fit: KitImageFit.cover,
              shape: KitShape.tile,
              width: tokens.iconTileSize,
              height: tokens.iconTileSize,
            ),
          )
        : KitSurface.tile(_glyph);
    return Padding(
      padding: EdgeInsetsDirectional.only(bottom: tokens.space1),
      child: Row(
        key: ValueKey('kit-sense-item-${item.id}'),
        children: [
          ExcludeSemantics(child: leading),
          SizedBox(width: tokens.space3),
          Expanded(
            child: MergeSemantics(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  KitText(
                    item.name,
                    role: KitTextRole.rowTitle,
                    tone: KitTextTone.primary,
                  ),
                  if (item.detail != null)
                    KitText(
                      item.detail!,
                      role: KitTextRole.secondary,
                      tone: KitTextTone.secondary,
                      tabular: true,
                    ),
                ],
              ),
            ),
          ),
          KitIconButton(
            icon: AppIconography.close,
            tooltip: item.removeLabel,
            onPressed: sending || onRemove == null
                ? null
                : () => onRemove!(item.id),
            disabledReason: sending ? item.removeLabel : null,
          ),
        ],
      ),
    );
  }
}
