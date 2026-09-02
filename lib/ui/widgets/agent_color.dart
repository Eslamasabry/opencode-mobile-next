import 'package:flutter/material.dart';

/// Resolves a server agent colour — `#rgb`/`#rrggbb`/`#aarrggbb` hex, an
/// ANSI-style name (red, green, blue, yellow, magenta, cyan, ...) or a theme
/// token (primary, secondary, accent, error) — to a Material colour. Anything
/// unknown falls back to the primary colour.
Color agentColor(String? raw, ColorScheme scheme) {
  final value = raw?.trim().toLowerCase();
  if (value == null || value.isEmpty) return scheme.primary;
  if (value.startsWith('#')) {
    var hex = value.substring(1);
    if (hex.length == 3) hex = hex.split('').map((c) => '$c$c').join();
    if (hex.length == 6) hex = 'ff$hex';
    final parsed = hex.length == 8 ? int.tryParse(hex, radix: 16) : null;
    return parsed == null ? scheme.primary : Color(parsed);
  }
  return switch (value) {
    'primary' => scheme.primary,
    'secondary' => scheme.secondary,
    'accent' || 'tertiary' => scheme.tertiary,
    'error' || 'danger' => scheme.error,
    'red' => Colors.red,
    'green' => Colors.green,
    'blue' => Colors.blue,
    'yellow' => Colors.amber,
    'magenta' || 'purple' || 'pink' => Colors.purple,
    'cyan' || 'teal' => Colors.cyan,
    'orange' => Colors.orange,
    'white' || 'black' || 'gray' || 'grey' => scheme.onSurfaceVariant,
    _ => scheme.primary,
  };
}
