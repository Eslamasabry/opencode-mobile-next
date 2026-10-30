import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui';

import '../../../theme_roles.dart' show contrastRatio;
import '../kit_live_style.dart';

/// Shared drawing helpers of the looks: value noise, colour mixing and a
/// low-resolution colour field drawn as one smooth vertex mesh (the GPU
/// interpolates between the grid points, so a coarse field upscales softly
/// with nothing blurred and no per-frame image decoding).

int _imul(int a, int b) => (a * b) & 0xFFFFFFFF;

double hash2(int x, int y) {
  var h = _imul(x & 0xFFFFFFFF, 374761393) ^ _imul(y & 0xFFFFFFFF, 668265263);
  h = _imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) & 0xFFFFFFFF) / 4294967295;
}

double valueNoise(double x, double y) {
  final xi = x.floor(), yi = y.floor();
  final xf = x - xi, yf = y - yi;
  final u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
  final a = hash2(xi, yi),
      b = hash2(xi + 1, yi),
      c = hash2(xi, yi + 1),
      d = hash2(xi + 1, yi + 1);
  return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
}

double fbm(double x, double y) =>
    valueNoise(x, y) * .6 +
    valueNoise(x * 2.03 + 5.2, y * 2.03 + 1.3) * .3 +
    valueNoise(x * 4.1 + 9.1, y * 4.1 + 3.7) * .1;

double smooth(double e0, double e1, double x) {
  final t = ((x - e0) / (e1 - e0)).clamp(0.0, 1.0);
  return t * t * (3 - 2 * t);
}

/// Red, green, blue of [c] as 0..255.
List<double> rgb(Color c) => [c.r * 255, c.g * 255, c.b * 255];

List<double> mixRgb(List<double> a, List<double> b, double t) => [
  a[0] + (b[0] - a[0]) * t,
  a[1] + (b[1] - a[1]) * t,
  a[2] + (b[2] - a[2]) * t,
];

Color mixColor(Color a, Color b, double t) => Color.lerp(a, b, t.clamp(0, 1))!;

Color withA(Color c, double a) => c.withValues(alpha: a.clamp(0.0, 1.0));

int packArgb(double r, double g, double b, double a) =>
    ((a * 255).round().clamp(0, 255) << 24) |
    (r.round().clamp(0, 255) << 16) |
    (g.round().clamp(0, 255) << 8) |
    b.round().clamp(0, 255);

/// Strength of a look: the demo's "A": 1 in dark, 0.7 in light, times the
/// frame's strength.
double strengthOf(KitLiveFrame f) => (f.palette.dark ? 1.0 : 0.7) * f.strength;

class _FieldCache {
  int lw = 0, lh = 0;
  double w = 0, h = 0;
  Float32List positions = Float32List(0);
  Uint16List indices = Uint16List(0);
  Int32List colors = Int32List(0);
  double lastCompute = -1e9;
}

/// Draws a colour field: [fn] gives the packed ARGB at (u, v) where u runs
/// 0..1 across and v 0..height/width down. The field is sampled on a grid of
/// about [div] logical pixels and recomputed at most every [every] seconds.
void paintField(
  Canvas canvas,
  Size size,
  KitLiveFrame frame,
  String key,
  int div,
  int Function(double u, double v) fn, {
  double every = 0,
}) {
  final c = frame.memory.of<_FieldCache>(key, _FieldCache.new);
  var lw = math.max(8, (size.width / div).round());
  var lh = math.max(8, (size.height / div).round());
  while ((lw + 1) * (lh + 1) > 65000) {
    lw = (lw * .8).round();
    lh = (lh * .8).round();
  }
  if (c.lw != lw || c.lh != lh || c.w != size.width || c.h != size.height) {
    c
      ..lw = lw
      ..lh = lh
      ..w = size.width
      ..h = size.height
      ..lastCompute = -1e9;
    final n = (lw + 1) * (lh + 1);
    c.positions = Float32List(n * 2);
    c.colors = Int32List(n);
    var k = 0;
    for (var y = 0; y <= lh; y++) {
      for (var x = 0; x <= lw; x++) {
        c.positions[k++] = x / lw * size.width;
        c.positions[k++] = y / lh * size.height;
      }
    }
    final idx = Uint16List(lw * lh * 6);
    k = 0;
    for (var y = 0; y < lh; y++) {
      for (var x = 0; x < lw; x++) {
        final a = y * (lw + 1) + x, b = a + 1, d = a + lw + 1, e = d + 1;
        idx[k++] = a;
        idx[k++] = b;
        idx[k++] = d;
        idx[k++] = b;
        idx[k++] = e;
        idx[k++] = d;
      }
    }
    c.indices = idx;
  }
  if (frame.t - c.lastCompute >= every || frame.t < c.lastCompute) {
    c.lastCompute = frame.t;
    final aspect = size.height / size.width;
    var k = 0;
    for (var y = 0; y <= lh; y++) {
      final v = y / lh * aspect;
      for (var x = 0; x <= lw; x++) {
        c.colors[k++] = fn(x / lw, v);
      }
    }
  }
  final vertices = Vertices.raw(
    VertexMode.triangles,
    c.positions,
    colors: c.colors,
    indices: c.indices,
  );
  canvas.drawVertices(vertices, BlendMode.dst, Paint());
  vertices.dispose();
}

/// Whether [p]'s text reads on [paint] mixed [a] into [base]: text1 bare
/// and text2 and text3 through [halo] of the ground, each at its floor (AA,
/// or within .2 of the theme's own contrast where that is lower) plus
/// [spare]; with a halo in a dark theme the accent (the glow) is held to
/// its floor too, as it reads bare.
bool liveTextReads(
  KitLivePalette p,
  Color base,
  Color paint,
  double a, {
  double halo = 0,
  double spare = 0,
}) {
  double floor(Color text) {
    final before = contrastRatio(text, p.ground);
    return (before >= 4.5 ? 4.5 : before - .2) + .04 + spare;
  }

  final under = Color.lerp(base, paint, a)!;
  for (final text in [p.ink, if (halo > 0 && p.dark) p.glow]) {
    if (contrastRatio(text, under) < floor(text)) return false;
  }
  final behind = Color.lerp(under, p.ground, halo)!;
  for (final text in [p.muted, p.faint]) {
    if (contrastRatio(text, behind) < floor(text)) return false;
  }
  return true;
}

/// The most of [paint] (0..1) that can be mixed into [base] while [p]'s
/// text reads ([liveTextReads]), less a sliver for rounding.
double liveMost(
  KitLivePalette p,
  Color base,
  Color paint, {
  double halo = 0,
  double spare = 0,
}) {
  bool reads(double a) =>
      liveTextReads(p, base, paint, a, halo: halo, spare: spare);
  if (!reads(0)) return 0;
  if (reads(1)) return 1;
  var lo = 0.0, hi = 1.0;
  for (var i = 0; i < 18; i++) {
    final mid = (lo + hi) / 2;
    if (reads(mid)) {
      lo = mid;
    } else {
      hi = mid;
    }
  }
  return lo;
}
