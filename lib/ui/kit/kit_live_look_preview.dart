import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import 'kit_effects.dart';
import 'kit_live_background.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// A short, wide live sample of the person's chosen look behind two mock chat
/// lines, at the top of Settings › Appearance › Effects. It follows the
/// Live background switch (off: the flat ground and a quiet "Off") and the
/// Intensity choice, cross-fades when either or the look changes, and opens
/// the look picker when tapped.
///
/// Motion: the look moves only while the tile is on screen (it rests while
/// scrolled away) and rests as a still frame under reduced motion, Calm or
/// Off ([KitLiveBackground] decides that).
///
/// States: disabled.
class KitLiveLookPreview extends StatefulWidget {
  const KitLiveLookPreview({
    super.key,
    required this.style,
    required this.intensity,
    required this.enabled,
    required this.onTap,
  });

  final KitLiveStyle style;
  final KitLiveIntensity intensity;

  /// The Live background switch.
  final bool enabled;
  final VoidCallback onTap;

  /// The tile's height.
  static const double height = 108;

  @override
  State<KitLiveLookPreview> createState() => _KitLiveLookPreviewState();
}

class _KitLiveLookPreviewState extends State<KitLiveLookPreview> {
  ScrollPosition? _position;
  bool _visible = true;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final next = Scrollable.maybeOf(context)?.position;
    if (!identical(next, _position)) {
      _position?.removeListener(_scrolled);
      _position = next;
      next?.addListener(_scrolled);
      _scrolled();
    }
  }

  @override
  void dispose() {
    _position?.removeListener(_scrolled);
    super.dispose();
  }

  bool _pending = false;

  /// Measures once the frame has laid out at the new scroll offset.
  void _scrolled() {
    if (_pending) return;
    _pending = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _pending = false;
      _measure();
    });
  }

  /// Whether any of the tile is inside the scroll view's window.
  void _measure() {
    if (!mounted) return;
    final box = context.findRenderObject();
    final view = Scrollable.maybeOf(context)?.context.findRenderObject();
    if (box is! RenderBox || view is! RenderBox) return;
    if (!box.attached || !view.attached || !box.hasSize || !view.hasSize) {
      return;
    }
    final mine = box.localToGlobal(Offset.zero) & box.size;
    final window = view.localToGlobal(Offset.zero) & view.size;
    final visible = mine.overlaps(window);
    if (visible != _visible) setState(() => _visible = visible);
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final name = widget.style.label(l10n);
    final reduced = KitMotion.reduced(context);
    final fade = reduced ? Duration.zero : KitMotion.standard;
    final strength = widget.intensity.strength;
    final stateKey = widget.enabled
        ? '${widget.style.id}-${widget.intensity.name}'
        : 'off';
    return KitTappable(
      onTap: widget.onTap,
      label: widget.enabled
          ? l10n.effectsLookPreviewLabel(name)
          : l10n.effectsLookPreviewOffLabel,
      role: KitTappableRole.button,
      child: SizedBox(
        height: KitLiveLookPreview.height,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(tokens.cardRadius),
          child: DecoratedBox(
            decoration: BoxDecoration(
              border: Border.all(
                color: roles.hairline,
                width: KitTokens.hairlineWidth(context),
              ),
              borderRadius: BorderRadius.circular(tokens.cardRadius),
            ),
            child: ExcludeSemantics(
              child: AnimatedSwitcher(
                duration: fade,
                switchInCurve: KitMotion.enter,
                switchOutCurve: KitMotion.exit,
                child: KeyedSubtree(
                  key: ValueKey(stateKey),
                  child: widget.enabled
                      ? TickerMode(
                          enabled: _visible,
                          child: KitLiveBackground(
                            style: widget.style,
                            strength: strength,
                            ignoreSetting: true,
                            child: _MockLines(),
                          ),
                        )
                      : ColoredBox(
                          color: roles.ground,
                          child: Stack(
                            fit: StackFit.expand,
                            children: [
                              Positioned.fill(
                                child: Center(
                                  child: DecoratedBox(
                                    decoration: BoxDecoration(
                                      color: roles.surface1,
                                      borderRadius: BorderRadius.circular(
                                        tokens.buttonRadius,
                                      ),
                                    ),
                                    child: Padding(
                                      padding: EdgeInsets.symmetric(
                                        horizontal: tokens.space3,
                                        vertical: tokens.space1,
                                      ),
                                      child: KitText(
                                        l10n.effectsLookPreviewOff,
                                        role: KitTextRole.secondary,
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Two chat lines: a short message from the person and a longer reply, so the
/// look is seen behind real-looking content. Solid surfaces only.
class _MockLines extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    Widget bar(double w, Color c) => Container(
      width: w,
      height: 6,
      decoration: BoxDecoration(
        color: c,
        borderRadius: BorderRadius.all(Radius.circular(tokens.space1)),
      ),
    );
    return Padding(
      padding: EdgeInsets.all(tokens.space3),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Align(
            alignment: AlignmentDirectional.centerEnd,
            child: Container(
              padding: EdgeInsets.all(tokens.space2),
              decoration: BoxDecoration(
                color: roles.surface2,
                borderRadius: BorderRadius.circular(tokens.buttonRadius),
              ),
              child: bar(72, roles.text2),
            ),
          ),
          SizedBox(height: tokens.space3),
          bar(150, roles.text2),
          SizedBox(height: tokens.space2),
          bar(112, roles.text3),
        ],
      ),
    );
  }
}
