/// Safe display metadata only: no plugin options, executable UI, paths, URLs,
/// raw failures, or credentials cross this read-only domain surface.
enum PluginStatus { active, failed, unknown }

enum PluginSourceKind { builtin, package, local, sdk, unknown }

class PluginInfo {
  const PluginInfo({
    required this.id,
    required this.status,
    required this.source,
    required this.terminalUi,
    this.packageName,
  });
  final String? id;
  final PluginStatus status;
  final PluginSourceKind source;
  final bool terminalUi;
  final String? packageName;
}

abstract interface class PluginGateway {
  Future<List<PluginInfo>> listPlugins();
}
