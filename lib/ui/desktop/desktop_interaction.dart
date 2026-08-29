import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

/// Whether this build should present desktop-native interaction: a keyboard
/// shortcut layer, right-click context menus, persistent scrollbars, mouse
/// text selection in the transcript, and file drops onto the composer.
///
/// Everything gated behind this getter is additive. On Android it is always
/// false, so the touch product keeps the exact behaviour it shipped with.
///
/// Reconciliation note: a parallel workstream is landing a shared platform
/// capability seam for `lib/voice/**` and `lib/termux/**`. This is a
/// deliberately tiny local helper so the interaction layer does not block on
/// it — when the shared seam lands, this getter delegates to it and every
/// call site below stays put.
bool get desktopInteractions =>
    !kIsWeb &&
    switch (defaultTargetPlatform) {
      TargetPlatform.linux ||
      TargetPlatform.macOS ||
      TargetPlatform.windows => true,
      TargetPlatform.android ||
      TargetPlatform.iOS ||
      TargetPlatform.fuchsia => false,
    };

/// The modifier a desktop user expects for accelerators: Command on macOS,
/// Control everywhere else. Both are accepted by every binding so a keyboard
/// attached to any platform keeps working.
bool get _isApple => !kIsWeb && defaultTargetPlatform == TargetPlatform.macOS;

/// Human-readable prefix for the shortcut help sheet.
String get shortcutModifierLabel => _isApple ? '⌘' : 'Ctrl';

/// App-wide scroll behaviour.
///
/// Material already installs a fading scrollbar on desktop, but only an
/// interactive, always-visible one reads as native — and only a scrollable
/// that owns a [ScrollController] can have one, so the thumb is pinned
/// exactly where that is true and falls back to Material's behaviour
/// otherwise. Horizontal strips keep the plain treatment: a permanent bar
/// under a row of chips is noise, not affordance.
///
/// Mouse is deliberately *not* added to [dragDevices]. Drag-to-scroll with a
/// mouse would take the same gesture desktop users expect to select text with,
/// and the transcript's selection support depends on that gesture. Wheel and
/// trackpad scrolling need no configuration — they arrive as pointer signals
/// and pan/zoom events, not drags.
class AppScrollBehavior extends MaterialScrollBehavior {
  const AppScrollBehavior();

  @override
  Widget buildScrollbar(
    BuildContext context,
    Widget child,
    ScrollableDetails details,
  ) {
    if (!desktopInteractions ||
        axisDirectionToAxis(details.direction) != Axis.vertical) {
      return super.buildScrollbar(context, child, details);
    }
    return Scrollbar(
      controller: details.controller,
      // RawScrollbar asserts on a visible thumb with no attached position, so
      // only a controller-owning scrollable gets the pinned treatment.
      thumbVisibility: details.controller != null,
      child: child,
    );
  }
}

/// Wraps a long scrollable so desktop shows a permanent, draggable thumb.
///
/// [controller] must be the same controller the scrollable uses. On Android
/// (and anywhere else non-desktop) the child is returned untouched.
class DesktopScrollbar extends StatelessWidget {
  const DesktopScrollbar({
    super.key,
    required this.controller,
    required this.child,
  });

  final ScrollController controller;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (!desktopInteractions) return child;
    return Scrollbar(
      controller: controller,
      thumbVisibility: true,
      child: child,
    );
  }
}

/// Gives a tappable that is not already an [InkWell]/[ListTile] the pointer
/// cursor desktop users read as "this is clickable". A no-op off desktop, and
/// a no-op when [enabled] is false so a disabled row does not lie.
class ClickCursor extends StatelessWidget {
  const ClickCursor({super.key, required this.child, this.enabled = true});

  final Widget child;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    if (!desktopInteractions || !enabled) return child;
    return MouseRegion(cursor: SystemMouseCursors.click, child: child);
  }
}
