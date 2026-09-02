import 'package:flutter/foundation.dart';
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

/// Agent name → raw server colour, for widgets that only know an agent by
/// name (a `task` tool card naming its subagent). Hosts that hold the agent
/// catalogue wrap the transcript in one; without it [agentColorFor] falls
/// back to a stable per-name pick.
class AgentColorScope extends InheritedWidget {
  const AgentColorScope({
    super.key,
    required this.colors,
    required super.child,
  });

  final Map<String, String?> colors;

  static AgentColorScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AgentColorScope>();

  String? rawColorOf(String name) {
    if (colors[name] case final raw?) return raw;
    final lower = name.toLowerCase();
    for (final entry in colors.entries) {
      if (entry.key.toLowerCase() == lower) return entry.value;
    }
    return null;
  }

  @override
  bool updateShouldNotify(AgentColorScope oldWidget) =>
      !mapEquals(colors, oldWidget.colors);
}

/// Colour for an agent known only by name: the server colour from the
/// nearest [AgentColorScope] when there is one, otherwise a deterministic
/// pick from a small palette so two subagents in one transcript still read
/// apart.
Color agentColorFor(BuildContext context, String name) {
  final scheme = Theme.of(context).colorScheme;
  final raw = AgentColorScope.maybeOf(context)?.rawColorOf(name);
  if (raw != null && raw.trim().isNotEmpty) return agentColor(raw, scheme);
  return agentFallbackColor(name, scheme);
}

/// Stable palette pick for an agent name with no configured colour.
Color agentFallbackColor(String name, ColorScheme scheme) {
  final palette = <Color>[
    scheme.primary,
    Colors.teal,
    Colors.purple,
    Colors.orange,
    Colors.blue,
    Colors.green,
    Colors.cyan,
    Colors.amber,
  ];
  var hash = 0;
  for (final unit in name.toLowerCase().codeUnits) {
    hash = (hash * 31 + unit) & 0x7fffffff;
  }
  return palette[hash % palette.length];
}
