import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_tokens.dart';

/// A row of cards to swipe through, one page at a time, with where you are
/// shown under it (docs/ux-system/kit-api/KitCarousel.md): themes, looks.
/// The next card peeks in so it is clear there is more. Swiping is an extra:
/// every card is its own focusable target, Tab brings an off-screen card into
/// view, and a mouse can drag it too.
///
/// With a few cards the place shows as dots; with many (more than
/// [dotsUpTo]) it shows as a short bar that grows along a track, since thirty
/// dots say nothing. The selected page is where the carousel opens.
///
/// States: none — it holds the cards it is given; each carries its own looks.
class KitCarousel extends StatefulWidget {
  const KitCarousel({
    super.key,
    required this.semanticsLabel,
    required this.itemCount,
    required this.itemBuilder,
    required this.itemHeight,
    this.initialIndex = 0,
    this.carouselKey,
    this.share = cardShare,
    this.showPlace = true,
    this.growPerTextStep = 140,
  }) : assert(itemCount > 0),
       assert(share > 0 && share <= 1);

  /// What the cards are: "Themes".
  final String semanticsLabel;
  final int itemCount;
  final IndexedWidgetBuilder itemBuilder;

  /// The cards' height at normal text; it grows with the text size by a
  /// line per step so a card's label never clips.
  final double itemHeight;

  /// The card shown first.
  final int initialIndex;

  /// On the scrolling pages.
  final Key? carouselKey;

  /// How much of the width one card takes: [cardShare] for big cards, about
  /// .4 for a strip of compact swatches (two and a half across).
  final double share;

  /// Whether the place (dots or bar) shows under the cards. A compact strip
  /// that already shows two and a half cards says "more" by itself.
  final bool showPlace;

  /// How much taller the cards get from normal text to double (a line per
  /// step for a card whose words sit inside it; less when only a short name
  /// sits under a picture).
  final double growPerTextStep;

  /// More cards than this show the place as a bar, not dots.
  static const dotsUpTo = 8;

  /// How much of the width one card takes (the rest shows the next).
  static const cardShare = .62;

  @override
  State<KitCarousel> createState() => _KitCarouselState();
}

class _KitCarouselState extends State<KitCarousel> {
  late final PageController _controller = PageController(
    initialPage: widget.initialIndex.clamp(0, widget.itemCount - 1),
    viewportFraction: widget.share,
  );
  double _page = 0;

  @override
  void initState() {
    super.initState();
    _page = _controller.initialPage.toDouble();
    _controller.addListener(_moved);
  }

  void _moved() {
    final page = _controller.page;
    if (page != null && page != _page) setState(() => _page = page);
  }

  @override
  void dispose() {
    _controller
      ..removeListener(_moved)
      ..dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final scale = MediaQuery.textScalerOf(context).scale(16) / 16;
    final height =
        widget.itemHeight + (scale - 1).clamp(0, 1.5) * widget.growPerTextStep;
    final n = widget.itemCount;
    final nearest = _page.round().clamp(0, n - 1);
    final reduced = KitMotion.reduced(context);
    final glide = reduced ? Duration.zero : KitMotion.quick;

    Widget place;
    if (n <= KitCarousel.dotsUpTo) {
      place = Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          for (var i = 0; i < n; i++)
            Padding(
              padding: EdgeInsets.symmetric(horizontal: tokens.space1),
              child: AnimatedContainer(
                duration: glide,
                curve: KitMotion.enter,
                width: i == nearest ? tokens.space5 : tokens.space2,
                height: tokens.space2,
                decoration: BoxDecoration(
                  color: i == nearest ? roles.accent : roles.surface3,
                  borderRadius: BorderRadius.circular(tokens.space2),
                ),
              ),
            ),
        ],
      );
    } else {
      final track = tokens.space6 * 3;
      final bar = track / n * 2;
      final t = n <= 1 ? 0.0 : (_page / (n - 1)).clamp(0.0, 1.0);
      place = Center(
        child: SizedBox(
          width: track,
          height: tokens.space2,
          child: Stack(
            children: [
              Positioned.fill(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: roles.surface3,
                    borderRadius: BorderRadius.circular(tokens.space2),
                  ),
                ),
              ),
              PositionedDirectional(
                start: (track - bar) * t,
                width: bar,
                top: 0,
                bottom: 0,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: roles.accent,
                    borderRadius: BorderRadius.circular(tokens.space2),
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: widget.semanticsLabel,
      value: l10n.kitCarouselPlace(nearest + 1, n),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            height: height,
            child: ScrollConfiguration(
              // A mouse or a trackpad drags it like a finger does.
              behavior: ScrollConfiguration.of(context).copyWith(
                scrollbars: false,
                dragDevices: {
                  PointerDeviceKind.touch,
                  PointerDeviceKind.mouse,
                  PointerDeviceKind.stylus,
                  PointerDeviceKind.trackpad,
                },
              ),
              // The next card peeks out to this box's edge and no further: on
              // a window wider than the part's cap that edge is the cap's,
              // not the window's (kit-v2 §8.6). Only the sides are cut.
              child: ClipRect(
                clipper: const _SidesClip(),
                child: Padding(
                  padding: EdgeInsetsDirectional.only(start: tokens.gutter),
                  child: PageView.builder(
                    key: widget.carouselKey,
                    controller: _controller,
                    padEnds: false,
                    clipBehavior: Clip.none,
                    itemCount: n,
                    itemBuilder: (context, i) => Padding(
                      padding: EdgeInsetsDirectional.only(
                        end: tokens.space3,
                        top: tokens.space1,
                        bottom: tokens.space1,
                      ),
                      child: widget.itemBuilder(context, i),
                    ),
                  ),
                ),
              ),
            ),
          ),
          if (widget.showPlace) ...[
            SizedBox(height: tokens.space3),
            ExcludeSemantics(child: place),
          ],
        ],
      ),
    );
  }
}

/// Clips to the box's own width and leaves the height open.
class _SidesClip extends CustomClipper<Rect> {
  const _SidesClip();

  @override
  Rect getClip(Size size) =>
      Rect.fromLTRB(0, -size.height, size.width, size.height * 2);

  @override
  bool shouldReclip(_SidesClip oldClipper) => false;
}
