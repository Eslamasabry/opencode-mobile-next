import 'package:flutter/material.dart' show Material, MaterialType;
import 'package:flutter/widgets.dart';

import 'kit_surface_look.dart' show KitSurfaceDecoration;

/// A soft halo in the ground's colour behind text that sits on a bright
/// live background, the way lock-screen text sits on a bright wallpaper:
/// the letters keep the ground right around them, so they read at the same
/// contrast however bright the ribbons behind them are.
///
/// [KitLiveBackground] provides it while its look is painted and asks for
/// it (the aurora in a dark theme); [KitText.styleOf] adds it to the
/// secondary and tertiary tones (text2, text3). Primary text, the accent,
/// attention and success tones read on the brightest ribbon without one,
/// and words on a filled button never get one. Elsewhere (under
/// `flutter test`, with the live background off, in light themes) there is
/// none, and text is exactly as before.
///
/// Cost: one blurred copy of each text2/text3 run, drawn six times. The
/// six are the same shadow, and the engine (Impeller's text shadow cache)
/// blurs a run once and keeps it from frame to frame while its words do
/// not change, so a frame adds six textured quads per run; no layers.
class KitTextHalo extends InheritedWidget {
  const KitTextHalo({super.key, required this.ground, required super.child});

  /// The ground the halo is made of, or null for no halo.
  final Color? ground;

  /// How much of the ground the halo puts back around a letter at least
  /// (0..1), as measured on the kit's small text (caption and secondary):
  /// the live background may be as bright as text read through this much
  /// ground allows (test/kit/kit_live_background_test.dart measures it).
  static const cover = .55;

  /// The halo's shadows in [ground]: one soft shadow, stacked six times so
  /// the ground sits close and nearly whole around the strokes and fades
  /// out a few pixels away.
  static List<Shadow> shadowsFor(Color ground) =>
      List.filled(6, Shadow(color: ground, blurRadius: 3));

  /// The halo's shadows for text built at [context], or null for none: none
  /// when no bright look is behind the page, when the text sits on a solid
  /// fill of its own (a card, a row group, a chip, a sheet: anything opaque
  /// between it and the ground), and under a mask that repaints the words
  /// in its own colours (a live sweep would paint the halo in them too).
  static List<Shadow>? of(BuildContext context) {
    final ground = context
        .dependOnInheritedWidgetOfExactType<KitTextHalo>()
        ?.ground;
    if (ground == null) return null;
    return onGround(context) ? shadowsFor(ground) : null;
  }

  /// Whether words built at [context] sit right on the ground the nearest
  /// [KitTextHalo] is made of: nothing solid (or a recolouring mask)
  /// between them and it.
  static bool onGround(BuildContext context) {
    var onGround = true;
    bool visit(Element element) {
      final widget = element.widget;
      if (widget is KitTextHalo) return false;
      if (_covers(widget)) {
        onGround = false;
        return false;
      }
      return true;
    }

    context.visitAncestorElements(visit);
    return onGround;
  }

  /// Whether [widget] puts something solid (or a recolouring mask) between
  /// text under it and the live ground.
  static bool _covers(Widget widget) {
    Color? fill;
    switch (widget) {
      case ColoredBox(:final color):
        fill = color;
      case DecoratedBox(:final decoration):
        // A styled surface (KitSurfaceDecoration) covers like any other.
        fill = switch (decoration) {
          BoxDecoration(:final color) => color,
          ShapeDecoration(:final color) => color,
          KitSurfaceDecoration(:final fill) => fill,
          _ => null,
        };
      case ShaderMask(:final blendMode):
        return blendMode != BlendMode.dstIn && blendMode != BlendMode.dstOut;
      case final Material material
          when material.type != MaterialType.transparency:
        fill = material.color;
      default:
        return false;
    }
    return fill != null && fill.a > .9;
  }

  @override
  bool updateShouldNotify(KitTextHalo old) => old.ground != ground;
}
