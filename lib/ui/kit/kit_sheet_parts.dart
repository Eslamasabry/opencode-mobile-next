part of 'kit_sheet.dart';

/// The drag handle: a short bar with a spoken "Dismiss" action.
class _KitHandle extends StatelessWidget {
  const _KitHandle({this.onDismiss});

  final VoidCallback? onDismiss;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Semantics(
      label: onDismiss == null ? null : _l10n(context).kitSheetDismiss,
      onDismiss: onDismiss,
      child: SizedBox(
        height: tokens.handleHeight,
        child: Center(
          child: Container(
            key: const ValueKey('kit-sheet-handle'),
            width: tokens.handleSize.width,
            height: tokens.handleSize.height,
            decoration: BoxDecoration(
              color: tokens.handleColor,
              borderRadius: BorderRadius.circular(tokens.handleSize.height / 2),
            ),
          ),
        ),
      ),
    );
  }
}

/// The tone of the one header tile [_KitIconTile] draws (§5 Sheets).
/// [KitSheet] uses [neutral] and [attention]; the confirm part (kit_confirm_
/// sheet.dart) uses [neutral] and [danger] (LOOK-5: danger only inside a
/// confirmation).
enum _KitTileTone { neutral, attention, danger }

/// The one 44 dp header tile (§5 Sheets, new private seam): a rounded square
/// with a centred glyph, excluded from semantics — the title beside it (or,
/// for a confirmation, the title after it) carries the meaning.
class _KitIconTile extends StatelessWidget {
  const _KitIconTile({required this.icon, required this.tone});

  final IconData icon;
  final _KitTileTone tone;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    // The tile stays the one 44 dp square at every text size, so its edge
    // always lands on whole physical pixels (VL §7); only the glyph grows
    // with the person's text size, clamped at maxIconScale (22 → 33 dp at
    // most, still inside the 44 dp tile).
    final glyphSize = tokens.iconSize(context, tokens.markIconSize);
    final tileSize = tokens.markSize;
    final (Color background, Color glyph) = switch (tone) {
      _KitTileTone.neutral => (roles.surface3, roles.text1),
      _KitTileTone.attention => (
        Color.alphaBlend(
          roles.attention.withValues(alpha: tokens.markTintAlpha),
          tokens.sheetSurface,
        ),
        roles.attention,
      ),
      _KitTileTone.danger => (
        Color.alphaBlend(
          roles.danger.withValues(alpha: tokens.markTintAlpha),
          tokens.sheetSurface,
        ),
        roles.danger,
      ),
    };
    return ExcludeSemantics(
      child: Container(
        width: tileSize,
        height: tileSize,
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(tokens.markRadius),
        ),
        child: Icon(icon, size: glyphSize, color: glyph),
      ),
    );
  }
}

/// A swipe down the frame owns: far or fast enough, it asks to close.
class _PullDown extends StatefulWidget {
  const _PullDown({required this.onPullDown, required this.child});

  final VoidCallback onPullDown;
  final Widget child;

  @override
  State<_PullDown> createState() => _PullDownState();
}

class _PullDownState extends State<_PullDown> {
  /// How far or how fast a pull must go to count (behaviour, not look).
  static const _distance = 48.0;
  static const _velocity = 700.0;

  double _pulled = 0;

  /// A header that scrolls with the body gives its drags to the scroll
  /// view (it may fill the whole view); the pull is not offered then.
  bool _allowed() =>
      !(context
              .findAncestorRenderObjectOfType<_RenderKitSheetFrame>()
              ?._scrollsHeader ??
          false);

  void _onStart(DragStartDetails _) => _pulled = 0;

  void _onUpdate(DragUpdateDetails details) => _pulled += details.delta.dy;

  void _onEnd(DragEndDetails details) {
    if (_pulled > _distance || (details.primaryVelocity ?? 0) > _velocity) {
      widget.onPullDown();
    }
    _pulled = 0;
  }

  @override
  Widget build(BuildContext context) => RawGestureDetector(
    behavior: HitTestBehavior.translucent,
    gestures: {
      _PullDownRecognizer:
          GestureRecognizerFactoryWithHandlers<_PullDownRecognizer>(
            () => _PullDownRecognizer(debugOwner: this),
            (recognizer) => recognizer
              ..allowed = _allowed
              ..onStart = _onStart
              ..onUpdate = _onUpdate
              ..onEnd = _onEnd,
          ),
    },
    child: widget.child,
  );
}

/// A vertical drag that joins the arena only while [allowed] says so.
class _PullDownRecognizer extends VerticalDragGestureRecognizer {
  _PullDownRecognizer({super.debugOwner});

  bool Function() allowed = _always;

  static bool _always() => true;

  @override
  bool isPointerAllowed(PointerEvent event) =>
      allowed() && super.isPointerAllowed(event);
}
