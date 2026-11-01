import 'package:flutter/material.dart';

import '../../platform/platform_capabilities.dart';
import '../kit/kit_scrollbar.dart';
import '../kit/kit_text.dart' show KitSelectable, KitSelectMode;

/// Whether this build should present desktop-native interaction: a keyboard
/// shortcut layer, right-click context menus, persistent scrollbars, mouse
/// text selection in the transcript, and file drops onto the composer.
///
/// Everything gated behind this getter is additive. On Android it is always
/// false, so the touch product keeps the exact behaviour it shipped with.
///
/// Delegates to [PlatformCapabilities] — the shared seam landed while this
/// layer was in flight, and one answer to "is this desktop?" is the whole
/// point of it. Kept as a named getter rather than inlining the capability
/// at ~40 call sites so the *reason* each gate exists stays legible, and so
/// `debugPlatformCapabilities` swaps every one of them at once.
///
/// This is [PlatformCapabilities.usesDesktopInteractions], not `isDesktop`:
/// a desktop *browser* has the same keyboard and mouse as a desktop window
/// and gets the same layer, while a phone browser stays on the touch
/// product. What a browser does not get is anything about a native window —
/// geometry, taskbar icon, release check — which still key off `isDesktop`.
bool get desktopInteractions => platformCapabilities.usesDesktopInteractions;

/// The modifier a desktop user expects for accelerators: Command on macOS,
/// Control everywhere else. Both are accepted by every binding so a keyboard
/// attached to any platform keeps working. A browser on a Mac is still a Mac
/// keyboard, so the label follows the operating system there too.
bool get _isApple => platformCapabilities.platform == TargetPlatform.macOS;

/// Human-readable prefix for the shortcut help sheet.
String get shortcutModifierLabel => _isApple ? '⌘' : 'Ctrl';

/// Human-readable label for the Alt/Option key, used by the bindings that
/// have to step off Ctrl/Cmd in a browser (see `appShortcutBindings`).
String get shortcutAltLabel => _isApple ? '⌥' : 'Alt';

/// App-wide scroll behaviour.
///
/// Retired by screen-shell-1: use [KitScrollBehavior] (KitScrollbar.md). The
/// rule moved into the kit unchanged; this name forwards to it so its
/// importers keep compiling (KIT-43).
class AppScrollBehavior extends KitScrollBehavior {
  const AppScrollBehavior();
}

/// Gives a long scrollable a permanent, draggable scrollbar on desktop.
///
/// Retired by screen-shell-1: use [KitScrollArea]. [builder] receives the
/// controller to hand its scrollable, or null off desktop.
class DesktopScrollbarArea extends StatelessWidget {
  const DesktopScrollbarArea({super.key, required this.builder});

  final Widget Function(ScrollController? controller) builder;

  @override
  Widget build(BuildContext context) => KitScrollArea(builder: builder);
}

/// Stops the app scroll behaviour adding a second thumb inside a subtree
/// that already builds its own scrollbar.
///
/// Retired by screen-shell-1: use [KitOwnScrollbar].
class OwnScrollbar extends StatelessWidget {
  const OwnScrollbar({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => KitOwnScrollbar(child: child);
}

/// Makes the transcript selectable with a mouse on desktop.
///
/// Retired by screen-shell-1: use `KitSelectable(mode:
/// KitSelectMode.finePointer)` (KitText.md). Chat bubbles keep long-press
/// for the message menu on touch; with a fine pointer the primary button
/// selects text instead, across messages, and Ctrl+C copies it. Right-click
/// still opens the message menu: the per-message context region sits deeper
/// in the hit-test path and wins the gesture arena.
class DesktopSelectionArea extends StatelessWidget {
  const DesktopSelectionArea({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) =>
      KitSelectable(mode: KitSelectMode.finePointer, child: child);
}
