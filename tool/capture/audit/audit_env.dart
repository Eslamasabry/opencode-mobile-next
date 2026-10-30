// AUDIT TOOLING ONLY (branch audit/style-coherence, 2026-10-21). Not a product
// change and not for merging: it lets the existing capture harnesses (the
// census, the vision captures) draw any screen under a chosen Style, mode
// and Fine-tune, for docs/audits/style-coherence-2026-10-21.md.
//
// Read from the process environment at run time (so one compile serves
// every combination):
//
//   AUDIT_STYLE  solid | soft | brutal | paper   (unset: audit off)
//   AUDIT_MODE   light | dark                            (unset: the shot's own)
//   AUDIT_SAVER  1: Saver (true black theme, effects.underSaver)
//   AUDIT_HC     1: the platform asks for high contrast
//   AUDIT_RTL    1: Arabic (captureApp paths only)
//   AUDIT_FINE   corners,depth,density,edges e.g. sharp,flat,compact,none
//   AUDIT_LIVE   0: live background off (default on)
//   AUDIT_OUT    root to write every PNG under, as <root>/<dir>/<file>
//
// Driver: tool/capture/audit/run_style_audit.sh.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

abstract final class AuditEnv {
  static Map<String, String> get _env => Platform.environment;

  static bool get on => (_env['AUDIT_STYLE'] ?? '').isNotEmpty;

  static KitSurfaceStyle get style =>
      KitSurfaceStyle.values.byName(_env['AUDIT_STYLE'] ?? 'solid');

  /// The forced brightness, or null to keep the shot's own.
  static bool? get light => switch (_env['AUDIT_MODE']) {
    'light' => true,
    'dark' => false,
    _ => null,
  };

  static bool get saver => _env['AUDIT_SAVER'] == '1';
  static bool get highContrast => _env['AUDIT_HC'] == '1';
  static bool get rtl => _env['AUDIT_RTL'] == '1';
  static bool get live => _env['AUDIT_LIVE'] != '0';
  static String? get out => _env['AUDIT_OUT'];

  static KitFineTune get fineTune {
    final raw = _env['AUDIT_FINE'];
    if (raw == null || raw.isEmpty) return KitFineTune.none;
    KitCorners? corners;
    KitDepth? depth;
    KitDensity? density;
    KitEdges? edges;
    for (final word in raw.split(',')) {
      corners ??= KitCorners.values.where((v) => v.name == word).firstOrNull;
      depth ??= KitDepth.values.where((v) => v.name == word).firstOrNull;
      density ??= KitDensity.values.where((v) => v.name == word).firstOrNull;
      edges ??= KitEdges.values.where((v) => v.name == word).firstOrNull;
    }
    return KitFineTune(
      corners: corners,
      depth: depth,
      density: density,
      edges: edges,
    );
  }

  /// The effects the app's scope would provide (main.dart applies Saver).
  static KitEffects get effects {
    final chosen = KitEffects(
      surfaceStyle: style,
      fineTune: fineTune,
      liveBackground: live,
    );
    return saver ? chosen.underSaver : chosen;
  }

  static Locale? get locale => rtl ? const Locale('ar') : null;
}

/// The capture theme under the audit: forced mode, Saver's true black, the
/// Arabic face; [fallback] when the audit is off.
ThemeData auditTheme(bool light, ThemeData Function(bool light) fallback) {
  if (!AuditEnv.on) return fallback(light);
  final isLight = AuditEnv.light ?? light;
  ThemeData theme = AuditEnv.saver
      ? AppTheme.saver()
      : (isLight ? AppTheme.light() : AppTheme.dark());
  theme = theme.copyWith(platform: TargetPlatform.android);
  final locale = AuditEnv.locale;
  return locale == null ? theme : AppTheme.forLocale(theme, locale);
}

/// What main.dart puts above the navigator: the effects scope and the
/// Style's own ground (hook H6), plus the platform's high-contrast flag.
/// [child] itself when the audit is off.
Widget auditWrap(BuildContext context, Widget child) {
  if (!AuditEnv.on) return child;
  KitLiveBackground.paintWithoutLoops = true;
  KitLiveBackground.frozenSeconds = 8;
  KitMotion.loops = false;
  final media = MediaQuery.of(context);
  return MediaQuery(
    data: media.copyWith(highContrast: AuditEnv.highContrast),
    child: KitEffectsScope(
      effects: AuditEnv.effects,
      child: kitStyleTheme(child: child),
    ),
  );
}

/// [path] under AUDIT_OUT as `<root>/<parent dir name>/<file name>`.
String auditPath(String path) {
  final root = AuditEnv.out;
  if (!AuditEnv.on || root == null || root.isEmpty) return path;
  final file = File(path);
  final dir = file.parent.uri.pathSegments.where((s) => s.isNotEmpty).last;
  return '$root/$dir/${file.uri.pathSegments.last}';
}

/// Runs [capture] with blurred shadows painted as on a phone (soft
/// surfaces are made of them), then puts the flag back.
Future<T> auditWithShadows<T>(
  WidgetTester tester,
  Future<T> Function() capture,
) async {
  if (!AuditEnv.on) return capture();
  final before = debugDisableShadows;
  debugDisableShadows = false;
  void dirty(RenderObject o) {
    o.markNeedsPaint();
    o.visitChildren(dirty);
  }

  try {
    for (final view in tester.binding.renderViews) {
      dirty(view);
    }
    await tester.pump();
    return await capture();
  } finally {
    debugDisableShadows = before;
  }
}
