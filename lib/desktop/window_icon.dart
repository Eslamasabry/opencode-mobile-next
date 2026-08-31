import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:window_manager/window_manager.dart';

/// The bundled brand mark, resolved by `window_manager` against the built
/// bundle's `data/flutter_assets`. The 256 px variant is what ships: a window
/// and taskbar icon is never drawn larger, and the 1024 px master beside it
/// stays a source artifact rather than 795 KB of payload in every build.
const desktopWindowIconAsset = 'assets/branding/app-icon-256.png';

/// Gives the running Linux window and its taskbar entry the product's own
/// icon instead of the GTK default.
///
/// This is the *running window's* icon, which is a different thing from the
/// installed launcher icon: `linux/packaging/` ships the desktop entry and
/// the hicolor theme icons that a `.deb` install registers, and those are
/// what a menu or dock shows for an installed copy. This one is what the
/// window manager hangs on the window itself, including when the bundle is
/// run straight out of `build/` with nothing installed.
///
/// Done from Dart, through `window_manager`, rather than in the GTK runner:
/// `setIcon` resolves the path against the bundle's assets and calls
/// `gtk_window_set_icon_from_file`, so `linux/runner/**` needs no change.
///
/// Linux only, deliberately. The Windows path wants an `.ico` and macOS has
/// no implementation at all, so both need their own icon pipeline; claiming
/// them here without being able to build and check either would be a guess.
///
/// Uses `dart:io` rather than `PlatformCapabilities` for the same reason the
/// window setup beside it does: this is about the process that is actually
/// running and whether a real GTK window exists to hang an icon on, not about
/// a feature a test needs to pump both ways.
///
/// Failure is never fatal — a missing or unreadable file leaves the default
/// icon, which is a cosmetic loss, not a startup one.
Future<void> applyDesktopWindowIcon() async {
  if (kIsWeb || !Platform.isLinux) return;
  try {
    await windowManager.setIcon(desktopWindowIconAsset);
  } catch (_) {
    // No icon is better than no window.
  }
}
