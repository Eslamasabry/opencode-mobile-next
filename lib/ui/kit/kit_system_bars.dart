// The phone's own bars follow the app's theme (kit-v2 §7.1, owner rule
// 2026-10-26: native Android feel): a clear status bar over the page with
// icons that read on its ground, and the navigation bar in the page's
// ground with icons to match. Every KitScreen page announces it.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme_roles.dart';

/// The system bar look for the theme here.
abstract final class KitSystemBars {
  /// Clear status bar, the ground behind the navigation bar, and icons
  /// light on a dark theme and dark on a light one.
  static SystemUiOverlayStyle of(BuildContext context) {
    final roles = ThemeRoles.of(context);
    final dark = roles.isDark;
    final icons = dark ? Brightness.light : Brightness.dark;
    return SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: icons,
      // iOS names the bar's own brightness, not its icons'.
      statusBarBrightness: dark ? Brightness.dark : Brightness.light,
      systemNavigationBarColor: roles.ground,
      systemNavigationBarDividerColor: Colors.transparent,
      systemNavigationBarIconBrightness: icons,
    );
  }
}
