import 'package:flutter/material.dart';

import '../../theme_roles.dart';
import '../kit_effects.dart';
import '../kit_tokens.dart';
import 'kit_style.dart';

/// Kit-internal, used once by main.dart. Hook H6: the theme under the Style's own ground
/// ([KitStyleSpec.adjustRoles]): Soft's lifted ground, Brutal's cream
/// paper, Paper's newsprint. Placed once, under the effects scope and above
/// the navigator (main.dart), so every page and its kit tokens read the
/// adjusted roles. The accent guard re-runs on the new ground.
///
/// It always builds the same [Theme] wrapper (with the theme unchanged when
/// the style keeps the roles), so changing style never rebuilds the
/// navigator.
Widget kitStyleTheme({required Widget child}) => _KitStyleTheme(child: child);

/// [theme] with [style]'s roles ([KitStyleSpec.adjustRoles]); [theme]
/// itself when they do not change. Memoised per theme and style.
ThemeData kitStyleRestyled(ThemeData theme, KitSurfaceStyle style) =>
    _KitStyleTheme.restyled(theme, style);

class _KitStyleTheme extends StatelessWidget {
  const _KitStyleTheme({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = KitStyles.styleOf(context);
    return Theme(data: restyled(theme, style), child: child);
  }

  static final Expando<Map<Object, ThemeData>> _cache = Expando(
    'kitStyleTheme',
  );

  /// The theme each restyled theme was made from, keyed by the restyled
  /// theme's roles (`Theme.of` hands out a localized copy of the theme,
  /// which keeps its extensions).
  static final Expando<ThemeData> _sourceOf = Expando('kitStyleSource');

  /// [theme] with [style]'s roles; [theme] itself when they do not change.
  /// A theme another host already restyled is restyled from its source, so
  /// a host nested under another (a Saver or high-contrast scope under the
  /// chosen Soft) keeps none of the outer style's roles.
  static ThemeData restyled(ThemeData theme, KitSurfaceStyle style) {
    final source = _sourceOf[ThemeRoles.resolve(theme)] ?? theme;
    final spec = kitStyleSpec(style);
    final roles = ThemeRoles.resolve(source);
    final adjusted = spec.adjustRoles(roles);
    if (identical(adjusted, roles) || adjusted == roles) return source;
    final cache = _cache[source] ??= {};
    return cache[style] ??= _rebuild(source, adjusted);
  }

  static ThemeData _rebuild(ThemeData theme, ThemeRoles adjusted) {
    // The accent moves only as far as it must to read on the new ground.
    final roles = adjusted.withAccent(adjusted.accent);
    final restyled = theme.copyWith(
      scaffoldBackgroundColor: roles.ground,
      canvasColor: roles.ground,
      dividerColor: roles.hairline,
      extensions: [
        for (final extension in theme.extensions.values)
          if (extension is! ThemeRoles && extension is! KitTokens) extension,
        roles,
        KitTokens.fromRoles(roles, theme.textTheme),
      ],
    );
    _sourceOf[roles] = theme;
    return restyled;
  }
}
