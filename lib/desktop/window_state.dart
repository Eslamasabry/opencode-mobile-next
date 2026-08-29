import 'dart:async';
import 'dart:convert';
import 'dart:ui' show Rect, Size;

import 'package:flutter/foundation.dart';
import 'package:screen_retriever/screen_retriever.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:window_manager/window_manager.dart';

/// Geometry the desktop window opens at the very first time it runs, before
/// anything has been remembered.
const kDesktopDefaultWindowSize = Size(900, 700);

/// Floor for the window. Below this the navigation rail plus a readable
/// transcript stop fitting, so the window manager refuses to go smaller.
const kDesktopMinimumWindowSize = Size(480, 600);

/// Title shown in the title bar / header bar and used by the shell to label
/// the window. Kept in sync with `linux/runner/my_application.cc`, which sets
/// the same string natively for the frames drawn before Dart starts.
const kDesktopWindowTitle = 'OpenCode';

/// Preferences key. Versioned so a future schema change can be ignored
/// instead of misread.
const kDesktopWindowStateKey = 'desktop_window_state_v1';

/// The remembered position, size and maximized flag of the desktop window.
///
/// [bounds] is always the *restored* (un-maximized) rectangle even when
/// [maximized] is true, so un-maximizing a restored window lands somewhere
/// sensible instead of on a tiny leftover rectangle.
@immutable
class DesktopWindowState {
  const DesktopWindowState({required this.bounds, required this.maximized});

  final Rect bounds;
  final bool maximized;

  Map<String, Object?> toJson() => {
    'x': bounds.left,
    'y': bounds.top,
    'width': bounds.width,
    'height': bounds.height,
    'maximized': maximized,
  };

  /// Returns null for anything that is not a complete, finite record. A
  /// corrupt or partially written entry is treated as "nothing remembered".
  static DesktopWindowState? fromJson(Object? value) {
    if (value is! Map) return null;
    final x = _finite(value['x']);
    final y = _finite(value['y']);
    final width = _finite(value['width']);
    final height = _finite(value['height']);
    if (x == null || y == null || width == null || height == null) return null;
    if (width <= 0 || height <= 0) return null;
    return DesktopWindowState(
      bounds: Rect.fromLTWH(x, y, width, height),
      maximized: value['maximized'] == true,
    );
  }

  static double? _finite(Object? value) {
    final number = value is num ? value.toDouble() : null;
    if (number == null || !number.isFinite) return null;
    return number;
  }

  /// Encodes to the string actually stored in SharedPreferences.
  String encode() => jsonEncode(toJson());

  static DesktopWindowState? decode(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    try {
      return fromJson(jsonDecode(raw));
    } on FormatException {
      return null;
    }
  }

  @override
  bool operator ==(Object other) =>
      other is DesktopWindowState &&
      other.bounds == bounds &&
      other.maximized == maximized;

  @override
  int get hashCode => Object.hash(bounds, maximized);

  @override
  String toString() =>
      'DesktopWindowState(bounds: $bounds, maximized: $maximized)';
}

/// Picks the work area that a saved rectangle belongs to.
///
/// Returns the display sharing the most area with [saved]. When nothing
/// overlaps — the classic "saved on a monitor that is no longer plugged in"
/// case — the first work area (the platform reports the primary display
/// first) is returned so the caller can re-home the window there. Returns
/// null only when the platform reported no displays at all.
Rect? displayForBounds(Rect saved, List<Rect> workAreas) {
  if (workAreas.isEmpty) return null;
  Rect? best;
  var bestArea = 0.0;
  for (final area in workAreas) {
    final overlap = area.intersect(saved);
    if (overlap.width <= 0 || overlap.height <= 0) continue;
    final size = overlap.width * overlap.height;
    if (size > bestArea) {
      bestArea = size;
      best = area;
    }
  }
  return best ?? workAreas.first;
}

/// Fits [bounds] entirely inside [workArea], never smaller than
/// [minimumSize] and never larger than the display itself.
Rect fitBoundsToDisplay({
  required Rect bounds,
  required Rect workArea,
  required Size minimumSize,
}) {
  final width = bounds.width
      .clamp(minimumSize.width, double.infinity)
      .clamp(0.0, workArea.width);
  final height = bounds.height
      .clamp(minimumSize.height, double.infinity)
      .clamp(0.0, workArea.height);
  // clamp() needs a non-inverted range: when the window is as wide as the
  // display there is exactly one legal left edge.
  final maxLeft = workArea.right - width;
  final maxTop = workArea.bottom - height;
  final left = maxLeft <= workArea.left
      ? workArea.left
      : bounds.left.clamp(workArea.left, maxLeft);
  final top = maxTop <= workArea.top
      ? workArea.top
      : bounds.top.clamp(workArea.top, maxTop);
  return Rect.fromLTWH(left, top, width, height);
}

/// Centers a [size] rectangle inside [workArea].
Rect centerInDisplay(Size size, Rect workArea) {
  final width = size.width.clamp(0.0, workArea.width);
  final height = size.height.clamp(0.0, workArea.height);
  return Rect.fromLTWH(
    workArea.left + (workArea.width - width) / 2,
    workArea.top + (workArea.height - height) / 2,
    width,
    height,
  );
}

/// The geometry the window should actually open at.
@immutable
class DesktopWindowGeometry {
  const DesktopWindowGeometry({
    required this.bounds,
    required this.maximized,
    required this.center,
  });

  final Rect bounds;
  final bool maximized;

  /// True when no usable saved position survived and the caller should let
  /// the window manager center the window rather than trusting [bounds].
  final bool center;

  @override
  bool operator ==(Object other) =>
      other is DesktopWindowGeometry &&
      other.bounds == bounds &&
      other.maximized == maximized &&
      other.center == center;

  @override
  int get hashCode => Object.hash(bounds, maximized, center);

  @override
  String toString() =>
      'DesktopWindowGeometry(bounds: $bounds, maximized: $maximized, '
      'center: $center)';
}

/// Turns what was remembered plus the displays that exist *now* into the
/// rectangle to open at.
///
/// The rules, in order:
/// 1. Nothing remembered → default size, centered.
/// 2. No displays reported → default size, centered (the platform cannot
///    tell us where it is safe to put anything).
/// 3. Remembered rectangle overlaps a display → clamped to fit fully inside
///    that display.
/// 4. Remembered rectangle overlaps nothing (monitor unplugged, resolution
///    shrank, negative coordinates from a display to the left that is gone)
///    → the remembered *size* is kept, centered on the primary display.
///
/// A maximized window always carries its restored rectangle through, so
/// restoring maximized and then un-maximizing gives back a real window
/// rather than a sliver.
DesktopWindowGeometry resolveStartupGeometry({
  required DesktopWindowState? saved,
  required List<Rect> workAreas,
  Size defaultSize = kDesktopDefaultWindowSize,
  Size minimumSize = kDesktopMinimumWindowSize,
}) {
  if (saved == null || workAreas.isEmpty) {
    return DesktopWindowGeometry(
      bounds: Rect.fromLTWH(0, 0, defaultSize.width, defaultSize.height),
      maximized: saved?.maximized ?? false,
      center: true,
    );
  }
  final home = displayForBounds(saved.bounds, workAreas)!;
  final overlap = home.intersect(saved.bounds);
  final offscreen = overlap.width <= 0 || overlap.height <= 0;
  // Fit first so the remembered size is legal for this display, then either
  // keep the remembered position or re-home the window to the center.
  final fitted = fitBoundsToDisplay(
    bounds: saved.bounds,
    workArea: home,
    minimumSize: minimumSize,
  );
  return DesktopWindowGeometry(
    bounds: offscreen ? centerInDisplay(fitted.size, home) : fitted,
    maximized: saved.maximized,
    center: false,
  );
}

/// Reads and writes the remembered geometry. Every failure is swallowed:
/// window placement is a convenience and must never break startup or block
/// a close.
class DesktopWindowStateStore {
  const DesktopWindowStateStore();

  Future<DesktopWindowState?> read() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return DesktopWindowState.decode(prefs.getString(kDesktopWindowStateKey));
    } catch (error) {
      debugPrint('Desktop window state read failed: $error');
      return null;
    }
  }

  Future<void> write(DesktopWindowState state) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(kDesktopWindowStateKey, state.encode());
    } catch (error) {
      debugPrint('Desktop window state write failed: $error');
    }
  }
}

/// Reports the work area of every attached display, in logical pixels.
///
/// `visiblePosition`/`visibleSize` exclude panels and docks, which is what a
/// window should be clamped to. Displays that do not report a work area fall
/// back to their full size at the origin, and a platform failure yields an
/// empty list (the caller then simply centers).
Future<List<Rect>> desktopWorkAreas() async {
  try {
    final displays = await screenRetriever.getAllDisplays();
    return [
      for (final display in displays)
        Rect.fromLTWH(
          display.visiblePosition?.dx ?? 0,
          display.visiblePosition?.dy ?? 0,
          (display.visibleSize ?? display.size).width,
          (display.visibleSize ?? display.size).height,
        ),
    ].where((area) => area.width > 0 && area.height > 0).toList();
  } catch (error) {
    debugPrint('Desktop display enumeration failed: $error');
    return const [];
  }
}

/// Applies the remembered geometry at launch and writes it back as the user
/// moves, resizes, maximizes and finally closes the window.
///
/// Saves are debounced because GTK emits a configure event per frame while a
/// window is being dragged; the close path flushes synchronously before the
/// window is destroyed so the last drag is never lost.
class DesktopWindowController with WindowListener {
  DesktopWindowController({
    DesktopWindowStateStore store = const DesktopWindowStateStore(),
    WindowManager? manager,
    Future<List<Rect>> Function() workAreas = desktopWorkAreas,
    Duration saveDebounce = const Duration(milliseconds: 400),
  }) : _store = store,
       _manager = manager ?? windowManager,
       _workAreas = workAreas,
       _saveDebounce = saveDebounce;

  final DesktopWindowStateStore _store;
  final WindowManager _manager;
  final Future<List<Rect>> Function() _workAreas;
  final Duration _saveDebounce;

  Timer? _pending;
  Rect? _restoredBounds;
  bool _maximized = false;
  bool _closing = false;

  Future<void> start() async {
    await _manager.ensureInitialized();
    final saved = await _store.read();
    final geometry = resolveStartupGeometry(
      saved: saved,
      workAreas: await _workAreas(),
    );
    _restoredBounds = geometry.bounds;
    _maximized = geometry.maximized;

    final options = WindowOptions(
      size: geometry.bounds.size,
      minimumSize: kDesktopMinimumWindowSize,
      center: geometry.center,
      title: kDesktopWindowTitle,
    );
    _manager.addListener(this);
    // The window must be able to veto its own close so the final geometry is
    // flushed to disk before the process goes away.
    await _manager.setPreventClose(true);
    await _manager.waitUntilReadyToShow(options, () async {
      if (!geometry.center) {
        await _manager.setBounds(geometry.bounds);
      }
      // Maximize last: setBounds on an already-maximized window is a no-op
      // on GTK, which would lose the restored rectangle.
      if (geometry.maximized) await _manager.maximize();
      await _manager.show();
      await _manager.focus();
    });
  }

  DesktopWindowState get _snapshot => DesktopWindowState(
    bounds:
        _restoredBounds ??
        Rect.fromLTWH(
          0,
          0,
          kDesktopDefaultWindowSize.width,
          kDesktopDefaultWindowSize.height,
        ),
    maximized: _maximized,
  );

  /// Records the live rectangle, but only while the window is a normal
  /// window: the maximized rectangle is the display, not something worth
  /// remembering.
  Future<void> _captureBounds() async {
    if (_closing || _maximized) return;
    try {
      _restoredBounds = await _manager.getBounds();
    } catch (error) {
      debugPrint('Desktop window bounds read failed: $error');
    }
  }

  void _scheduleSave() {
    _pending?.cancel();
    _pending = Timer(_saveDebounce, () {
      unawaited(_flush());
    });
  }

  Future<void> _flush() async {
    _pending?.cancel();
    _pending = null;
    await _store.write(_snapshot);
  }

  Future<void> _captureAndSave() async {
    await _captureBounds();
    _scheduleSave();
  }

  @override
  void onWindowResized() => unawaited(_captureAndSave());

  @override
  void onWindowMoved() => unawaited(_captureAndSave());

  @override
  void onWindowMaximize() {
    _maximized = true;
    _scheduleSave();
  }

  @override
  void onWindowUnmaximize() {
    _maximized = false;
    unawaited(_captureAndSave());
  }

  @override
  void onWindowClose() {
    unawaited(_closeAndDestroy());
  }

  Future<void> _closeAndDestroy() async {
    if (_closing) return;
    _closing = true;
    try {
      // The window is still alive here, so the maximized flag and the last
      // restored rectangle are both still readable.
      if (!_maximized) {
        try {
          _restoredBounds = await _manager.getBounds();
        } catch (_) {
          // Keep whatever the last configure event captured.
        }
      }
      await _flush();
    } finally {
      _manager.removeListener(this);
      await _manager.destroy();
    }
  }
}

DesktopWindowController? _controller;

/// Entry point called from `main()` on desktop. Never throws: a window that
/// cannot be positioned is far better than an app that cannot start.
Future<void> setUpDesktopWindow() async {
  try {
    final controller = _controller ??= DesktopWindowController();
    await controller.start();
  } catch (error, stack) {
    debugPrint('Desktop window setup failed: $error\n$stack');
  }
}
