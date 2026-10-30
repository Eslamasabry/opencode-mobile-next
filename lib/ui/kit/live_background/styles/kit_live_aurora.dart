import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui';

import 'package:flutter/painting.dart' show HSLColor;

import '../../../../l10n/app_localizations.dart';
import '../../kit_text_halo.dart';
import '../kit_live_style.dart';
import 'kit_live_toolkit.dart';

class KitLiveAurora extends KitLiveStyle {
  const KitLiveAurora();

  @override
  String get id => 'aurora';

  @override
  String label(AppLocalizations l10n) => l10n.liveStyleAurora;

  /// In a dark theme the ribbons are brighter than text2 and text3 could
  /// read on, so those keep a halo of the ground.
  @override
  bool wantsTextHalo(KitLivePalette palette) => palette.dark;

  @override
  void paint(Canvas canvas, Size size, KitLiveFrame f) {
    final p = f.palette;
    final a = strengthOf(f);
    final run = f.running;
    final tt = f.t * .03;
    final teal = rgb(p.teal), blue = rgb(p.blue), glow = rgb(p.glow);
    // A faint drifting haze, brighter while a reply runs. The ribbons are
    // measured on top of it at its brightest (KitAuroraBands.haze).
    final haze = KitAuroraBands.haze * (.4 + .6 * run) * a;
    paintField(canvas, size, f, 'f', 7, (u, v) {
      final n = fbm(u * 1.6 + tt, v * 1.6 - tt * .7);
      final m = fbm(u * 1.1 - tt * .5 + 3, v * 1.3 + tt * .4 + 7);
      final c = mixRgb(mixRgb(teal, blue, m), glow, run * .7);
      return packArgb(c[0], c[1], c[2], smooth(.38, .82, n) * haze);
    }, every: .04);
    f.memory
        .of<KitAuroraBands>('bands', KitAuroraBands.new)
        .paint(canvas, size, palette: p, t: f.t, strength: f.strength);
  }
}

/// One ribbon of light: a spine across the screen (fractions of the width
/// and height, off the edges at both ends) with a bright soft core, a glow
/// either side of it and a wide faint bloom, and soft strands of silk
/// weaving along it.
class _Ribbon {
  const _Ribbon({
    required this.spine,
    required this.side,
    required this.phase,
    this.width = 1,
    this.peak = 1,
  });

  /// Control points the spine passes through, as (x / width, y / height).
  final List<(double, double)> spine;

  /// Which way the longer side of the glow falls: 1 or -1.
  final double side;

  final double phase;

  /// The ribbon's width against the main one.
  final double width;

  /// The ribbon's brightness against the brightest one.
  final double peak;
}

/// A soft strand of silk along a ribbon: where it sits across it (a share
/// of the screen's short side, negative on the longer side), how far and
/// how often it weaves, its width, how bright against the room the glow
/// leaves it, and the stretch of the ribbon it runs along.
class _Strand {
  const _Strand(
    this.at,
    this.weave,
    this.waves,
    this.phase,
    this.width,
    this.alpha,
    this.from,
    this.to,
  );

  final double at, weave, waves, phase, width, alpha, from, to;
}

/// The aurora's ribbons, as cached meshes: built once per size, theme and
/// Intensity, then only moved (a translate and a rotate per ribbon per
/// frame), so a frame allocates nothing. [paint] draws any moment; a still
/// frame is just a fixed [t]. Also used, once and still, for the theme
/// cards' own aurora.
///
/// The light sits where pages are usually empty: a sweep through the top
/// corner, a long S across the lower half, a short arc along the bottom
/// edge and a faint one in the other top corner; the middle of the page
/// stays calm. The ribbons never cross, so no spot is lit twice.
///
/// Brightness is measured, not guessed. Everything is one colour, the
/// accent's hue, and the brightest spot anywhere (the core, or a strand
/// over the glow) is that hue mixed into the ground, under the haze at its
/// brightest, exactly as far as the text colours allow: AA
/// (4.5:1), or within .2 of the theme's own contrast where that is lower.
/// So every theme pack gets the brightest aurora its text allows, and no
/// spot of it, at any moment, passes that line
/// (test/kit/kit_live_background_test.dart checks it).
///
/// Cost per frame: 8 drawVertices calls (a glow and a strand mesh per
/// ribbon, about 9,000 vertices in all, built once), no layers, no blur, no
/// shaders.
class KitAuroraBands {
  /// The haze's peak opacity at strength 1 while a reply runs.
  static const haze = .012;

  static const _rows = 72;

  /// Across a ribbon, as shares of the screen's short side: from the core
  /// out through the glow to the bloom's edge. The longer side multiplies
  /// these by [_long].
  static const _across = [
    0.0,
    .005,
    .011,
    .018,
    .027,
    .039,
    .054,
    .072,
    .094,
    .12,
    .15,
    .19,
    .24,
  ];
  static const _long = 1.45;

  static const _ribbons = [
    // The sweep through the top start corner.
    _Ribbon(
      spine: [(-.16, .25), (.02, .14), (.2, .065), (.42, .0), (.62, -.08)],
      side: 1,
      phase: 0,
      width: .85,
    ),
    // The long S across the lower half.
    _Ribbon(
      spine: [
        (.02, 1.08),
        (.16, .92),
        (.33, .82),
        (.53, .78),
        (.68, .74),
        (.81, .66),
        (.9, .56),
        (1.02, .44),
        (1.14, .36),
      ],
      side: -1,
      phase: 1.9,
    ),
    // The short arc along the bottom edge.
    _Ribbon(
      spine: [(.42, 1.12), (.62, 1.02), (.84, .975), (1.2, .955)],
      side: -1,
      phase: 3.7,
      width: .7,
      peak: .85,
    ),
    // A faint one in the top end corner.
    _Ribbon(
      spine: [(.7, -.09), (.86, .03), (1.14, .17)],
      side: 1,
      phase: 5.3,
      width: .6,
      peak: .6,
    ),
  ];

  static const _strands = [
    // Silk just off the core, on the short side.
    _Strand(.03, .008, 1.3, 0, .012, .9, 0, .9),
    // Two in the glow on the long side, weaving past each other.
    _Strand(-.05, .014, 1.9, 1.7, .016, .8, .08, 1),
    _Strand(-.1, .014, 1.4, 3.1, .02, .7, .2, .85),
  ];

  /// The glow across a ribbon at [d] shares of the short side from its
  /// spine, 0..1: a bright soft core, a glow either side, a wide faint
  /// bloom; [bold] widens and lifts the glow and the bloom. In a dark theme
  /// the core is far brighter than the glow around it, as in the concepts;
  /// a light theme's wash is fuller and even.
  static double _profile(double d, double bold, bool dark) {
    final core = math.exp(-math.pow(d / (dark ? .009 : .018), 2) / 2);
    final body =
        ((dark ? .4 : .8) + .1 * bold) *
        math.exp(-math.pow(d / (.055 * (1 + .3 * bold)), 2) / 2);
    final bloom =
        ((dark ? .16 : .3) + .08 * bold) *
        math.exp(-math.pow(d / (.12 * (1 + .3 * bold)), 2) / 2);
    return 1 - (1 - core) * (1 - body) * (1 - bloom);
  }

  final List<Vertices?> _glows = List.filled(_ribbons.length, null);
  final List<Vertices?> _silk = List.filled(_ribbons.length, null);
  final List<Offset> _pivots = List.filled(_ribbons.length, Offset.zero);
  final Paint _paint = Paint();
  Size _size = Size.zero;
  Object? _sig;

  /// The aurora's colour in [p] and its opacity at the brightest spot: the
  /// accent's hue (saturated in dark themes, a pale wash in light ones)
  /// mixed into the ground, under the haze at its brightest, as far as the
  /// text allows. In a dark theme text2 and text3 keep a halo of the ground
  /// ([KitTextHalo]), so they are read through [KitTextHalo.cover] of it and
  /// the ribbons can be far brighter; text1 and the accent have no halo and
  /// are read on the ribbon itself.
  static (Color, double) peakOf(KitLivePalette p, double strength) {
    final hsl = HSLColor.fromColor(p.glow);
    final hue = p.dark
        ? hsl
              .withSaturation(math.max(hsl.saturation, .8))
              .withLightness(hsl.lightness.clamp(.45, .6))
              .toColor()
        : hsl
              .withSaturation(hsl.saturation.clamp(.45, .7))
              .withLightness(.8)
              .toColor();
    // The haze's most telling colour: the brightest of its hues in a dark
    // theme, the darkest in a light one.
    final hazes = [p.teal, p.blue, p.glow]
      ..sort((a, b) => a.computeLuminance().compareTo(b.computeLuminance()));
    final hazed = Color.lerp(
      p.ground,
      p.dark ? hazes.last : hazes.first,
      haze * (p.dark ? 1 : .7) * math.max(strength, 1),
    )!;
    // A light theme's wash could go further than its text needs; it stops
    // short so it stays a soft wash, not a stain.
    return (
      hue,
      p.dark
          ? liveMost(p, hazed, hue, halo: KitTextHalo.cover)
          : liveMost(p, hazed, hue) * .78,
    );
  }

  void _build(Size size, KitLivePalette p, double strength, double boost) {
    for (final v in [..._glows, ..._silk]) {
      v?.dispose();
    }
    final (hue, peak) = peakOf(p, strength);
    final top = math.min(1.0, peak * boost);
    // Subtle dims everything; Bold (and the previews) widens and lifts the
    // glow and the bloom. Nothing passes [top].
    final dim = strength.clamp(0.0, 1.0);
    final bold = ((strength - 1) / .6).clamp(0.0, 1.0);
    final w = size.width, h = size.height;
    final short = math.min(w, h);
    for (var b = 0; b < _ribbons.length; b++) {
      final r = _ribbons[b];
      final pts = [for (final (x, y) in r.spine) Offset(x * w, y * h)];
      final spine = List<Offset>.generate(
        _rows + 1,
        (i) => _catmullRom(pts, i / _rows),
      );
      final normals = List<Offset>.generate(_rows + 1, (i) {
        final a = spine[math.max(0, i - 1)];
        final c = spine[math.min(_rows, i + 1)];
        final d = c - a;
        final len = d.distance == 0 ? 1.0 : d.distance;
        return Offset(-d.dy / len, d.dx / len) * r.side;
      });
      final pivot = spine[_rows ~/ 2];
      _pivots[b] = pivot;
      // Where the sheet folds it narrows and brightens, as silk does; the
      // ends fade out, well beyond the screen.
      double fold(double v) => math.sin(2 * math.pi * (v * 1.35) + r.phase);
      double width(double v) => (1 + .18 * fold(v)) * r.width;
      double shine(double v) =>
          (.9 - .1 * fold(v)) * smooth(0, .14, v) * smooth(0, .14, 1 - v);
      // The opacity of the glow at [d] (shares of the short side, signed:
      // negative on the long side) at [v] along the ribbon.
      double glowAt(double d, double v) {
        final x = (d < 0 ? -d / _long : d) / width(v);
        return _profile(x, bold, p.dark) * top * shine(v) * r.peak * dim;
      }

      // The glow: one column either side per offset, the core shared.
      final offsets = [
        for (final o in _across.reversed) -o * _long,
        for (final o in _across.skip(1)) o,
      ];
      final cols = offsets.length;
      final pos = Float32List((_rows + 1) * cols * 2);
      final col = Int32List((_rows + 1) * cols);
      var k = 0;
      for (var i = 0; i <= _rows; i++) {
        final v = i / _rows;
        for (var c = 0; c < cols; c++) {
          final d = offsets[c];
          final at = spine[i] + normals[i] * (d * short * width(v)) - pivot;
          pos[k * 2] = at.dx;
          pos[k * 2 + 1] = at.dy;
          final edge = c == 0 || c == cols - 1;
          final a = edge ? 0.0 : glowAt(d * width(v), v);
          col[k] = hue.withValues(alpha: a.clamp(0.0, 1.0)).toARGB32();
          k++;
        }
      }
      _glows[b] = Vertices.raw(
        VertexMode.triangles,
        pos,
        colors: col,
        indices: _grid(_rows, cols),
      );

      // The strands: a soft ridge each, five vertices across, as bright as
      // the room the glow leaves under it (its brightest point along the
      // weave), so glow and strand together stay within [top].
      const ridgeAt = [-1.0, -.5, 0.0, .5, 1.0];
      const ridge = [0.0, .55, 1.0, .55, 0.0];
      final n = _strands.length;
      final sp = Float32List(n * (_rows + 1) * ridgeAt.length * 2);
      final sc = Int32List(n * (_rows + 1) * ridgeAt.length);
      final si = Uint16List(n * _rows * (ridgeAt.length - 1) * 6);
      var vp = 0, vi = 0;
      for (var s = 0; s < n; s++) {
        final st = _strands[s];
        final base = vp;
        for (var i = 0; i <= _rows; i++) {
          final v = i / _rows;
          final wv = width(v);
          final d =
              st.at +
              st.weave *
                  math.sin(2 * math.pi * st.waves * v + st.phase + r.phase);
          final near = st.at.sign * math.max(0.0, st.at.abs() - st.weave);
          final under = glowAt(near * wv, v);
          final full = top * shine(v) * r.peak * dim;
          final room = under >= full ? 0.0 : (full - under) / (1 - under);
          final shown =
              smooth(st.from, st.from + .12, v) * smooth(st.to, st.to - .12, v);
          for (var c = 0; c < ridgeAt.length; c++) {
            final off = d * wv + ridgeAt[c] * st.width * wv / 2;
            final at = spine[i] + normals[i] * (off * short) - pivot;
            sp
              ..[vp * 2] = at.dx
              ..[vp * 2 + 1] = at.dy;
            sc[vp] = hue
                .withValues(
                  alpha: (room * st.alpha * shown * ridge[c]).clamp(0.0, 1.0),
                )
                .toARGB32();
            vp++;
          }
        }
        final grid = _grid(_rows, ridgeAt.length);
        for (final g in grid) {
          si[vi++] = base + g;
        }
      }
      _silk[b] = Vertices.raw(
        VertexMode.triangles,
        sp,
        colors: sc,
        indices: si,
      );
    }
  }

  /// Two triangles per cell of a [rows] x ([cols] - 1) strip of vertices
  /// laid out row by row.
  static Uint16List _grid(int rows, int cols) {
    final idx = Uint16List(rows * (cols - 1) * 6);
    var q = 0;
    for (var i = 0; i < rows; i++) {
      for (var c = 0; c < cols - 1; c++) {
        final a = i * cols + c, b = a + 1, d = a + cols, e = d + 1;
        idx
          ..[q++] = a
          ..[q++] = b
          ..[q++] = d
          ..[q++] = b
          ..[q++] = e
          ..[q++] = d;
      }
    }
    return idx;
  }

  /// A point on the Catmull-Rom curve through [pts] at [s] (0..1).
  static Offset _catmullRom(List<Offset> pts, double s) {
    final segs = pts.length - 1;
    final f = (s * segs).clamp(0.0, segs - 1e-9);
    final i = f.floor();
    final u = f - i;
    final p0 = pts[math.max(0, i - 1)], p1 = pts[i];
    final p2 = pts[i + 1], p3 = pts[math.min(segs, i + 2)];
    final u2 = u * u, u3 = u2 * u;
    return (p1 * 2 +
            (p2 - p0) * u +
            (p0 * 2 - p1 * 5 + p2 * 4 - p3) * u2 +
            (p1 * 3 - p0 - p2 * 3 + p3) * u3) *
        .5;
  }

  /// Draws the ribbons at moment [t] seconds in [palette]'s colours.
  /// [strength] is the Intensity (1 = Normal, the strength the contrast
  /// gate measures). [boost] multiplies the measured brightness, only for a
  /// picture whose words all sit on solid bubbles (a theme card); a page
  /// never passes it.
  void paint(
    Canvas canvas,
    Size size, {
    required KitLivePalette palette,
    required double t,
    required double strength,
    double boost = 1,
  }) {
    if (size.isEmpty) return;
    // Opacity is in the vertex colours; it is rebuilt only when the size,
    // the theme or the Intensity changes, never between frames.
    final q = (strength * 32).round() / 32;
    final sig = (
      palette.ground,
      palette.glow,
      palette.teal,
      palette.blue,
      palette.ink,
      palette.muted,
      palette.faint,
      palette.dark,
      q,
      boost,
    );
    if (size != _size || sig != _sig) {
      _size = size;
      _sig = sig;
      _build(size, palette, q, boost);
    }
    final w = size.width, h = size.height;
    for (var b = 0; b < _ribbons.length; b++) {
      final ph = _ribbons[b].phase;
      final dx = math.sin(t * .041 + ph * 2.1) * w * .022;
      final dy = math.sin(t * .033 + ph * 1.3) * h * .012;
      final sway = math.sin(t * .027 + ph) * .03;
      final pivot = _pivots[b];
      canvas
        ..save()
        ..translate(pivot.dx + dx, pivot.dy + dy)
        ..rotate(sway)
        ..drawVertices(_glows[b]!, BlendMode.src, _paint)
        ..drawVertices(_silk[b]!, BlendMode.src, _paint)
        ..restore();
    }
  }
}
