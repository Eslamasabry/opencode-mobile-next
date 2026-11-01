import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb, visibleForTesting;
import 'package:flutter/services.dart' show BrowserContextMenu;

/// Keeps the browser's own context menu out of the way while the pointer is
/// over a region that supplies its own.
///
/// In a browser a right-click raises the page's `contextmenu` event, and
/// Flutter leaves the browser menu enabled by default, rightly, because in
/// a text field that menu is where a working Paste lives on a plain-`http`
/// origin. Disabling it for the whole page would trade that away. So the
/// menu is switched off only while a context region is hovered, and back on
/// when the pointer leaves the last one, counted so nested regions (a
/// message inside a list that also has a menu) do not re-enable it early.
///
/// The switch itself is a platform message; `setBrowserMenuEnabled` is
/// injectable so the counting can be tested without a browser. Off the web
/// the default switch does nothing at all.
class BrowserContextMenuGuard {
  BrowserContextMenuGuard({
    Future<void> Function(bool enabled)? setBrowserMenuEnabled,
  }) : _set = setBrowserMenuEnabled ?? _setBrowserContextMenu;

  final Future<void> Function(bool enabled) _set;
  int _hovered = 0;

  /// Regions currently under the pointer.
  int get hoveredRegions => _hovered;

  void enter() {
    _hovered += 1;
    if (_hovered == 1) unawaited(_set(false));
  }

  void leave() {
    if (_hovered == 0) return;
    _hovered -= 1;
    if (_hovered == 0) unawaited(_set(true));
  }

  static Future<void> _setBrowserContextMenu(bool enabled) {
    // BrowserContextMenu asserts kIsWeb; a test that pins browser
    // capabilities still runs on the VM and must never reach it.
    if (!kIsWeb) return Future<void>.value();
    return enabled
        ? BrowserContextMenu.enableContextMenu()
        : BrowserContextMenu.disableContextMenu();
  }
}

BrowserContextMenuGuard _guard = BrowserContextMenuGuard();

/// The app-wide guard every context region reports to.
BrowserContextMenuGuard get browserContextMenuGuard => _guard;

/// Swaps the guard for a test; pass `null` to restore the default.
@visibleForTesting
set debugBrowserContextMenuGuard(BrowserContextMenuGuard? guard) =>
    _guard = guard ?? BrowserContextMenuGuard();
