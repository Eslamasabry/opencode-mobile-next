import 'dart:ui' show Rect, Size;

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/desktop/window_state.dart';

/// A laptop panel at the origin with a 32px top bar.
const laptop = Rect.fromLTWH(0, 32, 1920, 1048);

/// A second monitor placed to the right of it.
const rightMonitor = Rect.fromLTWH(1920, 0, 2560, 1440);

/// A second monitor placed to the left, i.e. at negative coordinates.
const leftMonitor = Rect.fromLTWH(-1920, 0, 1920, 1080);

void main() {
  group('DesktopWindowState serialization', () {
    test('round-trips through the encoded string', () {
      const state = DesktopWindowState(
        bounds: Rect.fromLTWH(120, 64, 900, 700),
        maximized: true,
      );
      expect(DesktopWindowState.decode(state.encode()), state);
    });

    test('rejects nothing, empty, and non-JSON', () {
      expect(DesktopWindowState.decode(null), isNull);
      expect(DesktopWindowState.decode(''), isNull);
      expect(DesktopWindowState.decode('not json'), isNull);
      expect(DesktopWindowState.decode('[1,2,3]'), isNull);
    });

    test('rejects partial, non-finite and non-positive records', () {
      expect(DesktopWindowState.decode('{"x":0,"y":0,"width":800}'), isNull);
      expect(
        DesktopWindowState.decode(
          '{"x":0,"y":0,"width":0,"height":700,"maximized":false}',
        ),
        isNull,
      );
      expect(
        DesktopWindowState.fromJson({
          'x': double.nan,
          'y': 0,
          'width': 800,
          'height': 700,
        }),
        isNull,
      );
      expect(
        DesktopWindowState.fromJson({
          'x': 0,
          'y': 0,
          'width': double.infinity,
          'height': 700,
        }),
        isNull,
      );
    });

    test('treats a missing maximized flag as not maximized', () {
      final state = DesktopWindowState.decode(
        '{"x":10,"y":20,"width":800,"height":600}',
      );
      expect(state, isNotNull);
      expect(state!.maximized, isFalse);
      expect(state.bounds, const Rect.fromLTWH(10, 20, 800, 600));
    });
  });

  group('displayForBounds', () {
    test('returns null when the platform reported no displays', () {
      expect(displayForBounds(const Rect.fromLTWH(0, 0, 800, 600), const []),
          isNull);
    });

    test('picks the display holding most of the window', () {
      // Straddles the seam but sits mostly on the right monitor.
      const straddling = Rect.fromLTWH(1800, 200, 900, 700);
      expect(
        displayForBounds(straddling, const [laptop, rightMonitor]),
        rightMonitor,
      );
    });

    test('falls back to the primary display when nothing overlaps', () {
      const gone = Rect.fromLTWH(5000, 5000, 900, 700);
      expect(displayForBounds(gone, const [laptop, rightMonitor]), laptop);
    });

    test('handles displays at negative coordinates', () {
      const onLeft = Rect.fromLTWH(-1500, 100, 900, 700);
      expect(
        displayForBounds(onLeft, const [laptop, leftMonitor]),
        leftMonitor,
      );
    });
  });

  group('fitBoundsToDisplay', () {
    test('leaves a window that already fits alone', () {
      const fits = Rect.fromLTWH(200, 200, 900, 700);
      expect(
        fitBoundsToDisplay(
          bounds: fits,
          workArea: laptop,
          minimumSize: kDesktopMinimumWindowSize,
        ),
        fits,
      );
    });

    test('pulls a window that hangs off the right/bottom back inside', () {
      final fitted = fitBoundsToDisplay(
        bounds: const Rect.fromLTWH(1800, 900, 900, 700),
        workArea: laptop,
        minimumSize: kDesktopMinimumWindowSize,
      );
      expect(fitted.width, 900);
      expect(fitted.height, 700);
      expect(fitted.right, laptop.right);
      expect(fitted.bottom, laptop.bottom);
    });

    test('never places the window above the work area top bar', () {
      final fitted = fitBoundsToDisplay(
        bounds: const Rect.fromLTWH(-400, -400, 900, 700),
        workArea: laptop,
        minimumSize: kDesktopMinimumWindowSize,
      );
      expect(fitted.left, laptop.left);
      expect(fitted.top, laptop.top);
    });

    test('shrinks a window that is larger than the display', () {
      const small = Rect.fromLTWH(0, 0, 1024, 600);
      final fitted = fitBoundsToDisplay(
        bounds: const Rect.fromLTWH(0, 0, 3000, 2000),
        workArea: small,
        minimumSize: const Size(480, 560),
      );
      expect(fitted, small);
    });

    test('enforces the minimum size', () {
      final fitted = fitBoundsToDisplay(
        bounds: const Rect.fromLTWH(100, 100, 120, 90),
        workArea: laptop,
        minimumSize: kDesktopMinimumWindowSize,
      );
      expect(fitted.size, kDesktopMinimumWindowSize);
    });

    test('a display smaller than the minimum still yields a legal rect', () {
      const tiny = Rect.fromLTWH(0, 0, 320, 240);
      final fitted = fitBoundsToDisplay(
        bounds: const Rect.fromLTWH(0, 0, 900, 700),
        workArea: tiny,
        minimumSize: kDesktopMinimumWindowSize,
      );
      expect(fitted, tiny);
    });
  });

  group('resolveStartupGeometry', () {
    test('centers the default size when nothing was remembered', () {
      final geometry = resolveStartupGeometry(
        saved: null,
        workAreas: const [laptop],
      );
      expect(geometry.center, isTrue);
      expect(geometry.maximized, isFalse);
      expect(geometry.bounds.size, kDesktopDefaultWindowSize);
    });

    test('centers when the platform reports no displays', () {
      final geometry = resolveStartupGeometry(
        saved: const DesktopWindowState(
          bounds: Rect.fromLTWH(100, 100, 800, 600),
          maximized: false,
        ),
        workAreas: const [],
      );
      expect(geometry.center, isTrue);
      expect(geometry.bounds.size, kDesktopDefaultWindowSize);
    });

    test('restores an exact rectangle that still fits', () {
      const saved = DesktopWindowState(
        bounds: Rect.fromLTWH(240, 120, 1100, 820),
        maximized: false,
      );
      final geometry = resolveStartupGeometry(
        saved: saved,
        workAreas: const [laptop, rightMonitor],
      );
      expect(geometry.center, isFalse);
      expect(geometry.bounds, saved.bounds);
    });

    test('restores onto the secondary monitor it was saved on', () {
      const saved = DesktopWindowState(
        bounds: Rect.fromLTWH(2200, 300, 1200, 900),
        maximized: false,
      );
      final geometry = resolveStartupGeometry(
        saved: saved,
        workAreas: const [laptop, rightMonitor],
      );
      expect(geometry.bounds, saved.bounds);
    });

    test('re-homes to the primary display when the monitor is unplugged', () {
      const saved = DesktopWindowState(
        bounds: Rect.fromLTWH(2200, 300, 1200, 900),
        maximized: false,
      );
      final geometry = resolveStartupGeometry(
        saved: saved,
        workAreas: const [laptop],
      );
      expect(geometry.center, isFalse);
      // Same size, but fully on the display that still exists.
      expect(geometry.bounds.size, const Size(1200, 900));
      expect(laptop.contains(geometry.bounds.topLeft), isTrue);
      expect(laptop.intersect(geometry.bounds), geometry.bounds);
      // Centered rather than jammed into a corner.
      expect(geometry.bounds.center.dx, closeTo(laptop.center.dx, 0.001));
      expect(geometry.bounds.center.dy, closeTo(laptop.center.dy, 0.001));
    });

    test('re-homes a window saved at negative coordinates', () {
      const saved = DesktopWindowState(
        bounds: Rect.fromLTWH(-1400, 200, 900, 700),
        maximized: false,
      );
      final geometry = resolveStartupGeometry(
        saved: saved,
        workAreas: const [laptop],
      );
      expect(laptop.intersect(geometry.bounds), geometry.bounds);
    });

    test('a maximized window restores maximized, not tiny', () {
      const saved = DesktopWindowState(
        bounds: Rect.fromLTWH(300, 200, 1000, 800),
        maximized: true,
      );
      final geometry = resolveStartupGeometry(
        saved: saved,
        workAreas: const [laptop],
      );
      expect(geometry.maximized, isTrue);
      // The restored rectangle is carried through so un-maximizing works.
      expect(geometry.bounds, saved.bounds);
    });

    test('a saved rectangle smaller than the floor grows to the floor', () {
      const saved = DesktopWindowState(
        bounds: Rect.fromLTWH(10, 40, 200, 150),
        maximized: false,
      );
      final geometry = resolveStartupGeometry(
        saved: saved,
        workAreas: const [laptop],
      );
      expect(geometry.bounds.size, kDesktopMinimumWindowSize);
    });

    test('a saved rectangle wider than the new resolution shrinks', () {
      const saved = DesktopWindowState(
        bounds: Rect.fromLTWH(0, 0, 2400, 1300),
        maximized: false,
      );
      const small = Rect.fromLTWH(0, 0, 1366, 768);
      final geometry = resolveStartupGeometry(
        saved: saved,
        workAreas: const [small],
      );
      expect(geometry.bounds, small);
    });
  });

  test('the minimum size fits inside the default size', () {
    expect(
      kDesktopMinimumWindowSize.width,
      lessThanOrEqualTo(kDesktopDefaultWindowSize.width),
    );
    expect(
      kDesktopMinimumWindowSize.height,
      lessThanOrEqualTo(kDesktopDefaultWindowSize.height),
    );
  });
}
