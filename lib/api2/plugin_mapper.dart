import '../domain/plugin_inventory.dart';

/// The plugin endpoint includes raw loader errors and installation sources.
/// Retain only bounded display IDs, source kinds and npm package identifiers.
PluginInfo mapPluginInfo(Map<dynamic, dynamic> value) {
  final source = value['source'];
  if (source is! Map ||
      source['type'] is! String ||
      value['status'] is! String ||
      value['tui'] is! bool) {
    throw const FormatException('Invalid plugin metadata');
  }
  final rawID = value['id'];
  final id =
      rawID is String &&
          rawID.length <= 200 &&
          RegExp(r'^@?[A-Za-z0-9][A-Za-z0-9_./@-]*$').hasMatch(rawID)
      ? rawID
      : null;
  final package = source['package'];
  final packageName =
      package is String &&
          package.length <= 200 &&
          RegExp(
            r'^(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*(?:@[A-Za-z0-9][A-Za-z0-9._+-]*)?$',
          ).hasMatch(package)
      ? package
      : null;
  final kind = switch (source['type']) {
    'builtin' => PluginSourceKind.builtin,
    'package' => PluginSourceKind.package,
    'local' => PluginSourceKind.local,
    'sdk' => PluginSourceKind.sdk,
    _ => PluginSourceKind.unknown,
  };
  return PluginInfo(
    id: id,
    status: switch (value['status']) {
      'active' => PluginStatus.active,
      'failed' => PluginStatus.failed,
      _ => PluginStatus.unknown,
    },
    source: kind,
    packageName: kind == PluginSourceKind.package ? packageName : null,
    terminalUi: value['tui'] as bool,
  );
}
