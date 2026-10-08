part of '../gateway.dart';

extension _PaseoCommands on PaseoGateway {
  Future<List<CommandInfo>> _listCommands() async {
    // Folder-wide draft discovery has no trusted conversation/daemon mapping.
    if (_browserHasRequestedSource) return const [];
    final scope = _scope;
    final epoch = _locationEpoch;
    // The daemon lists a draft's commands only for a named model.
    final runtimes = await providers();
    _checkLocation(scope, epoch);
    final runtime = runtimes.providers
        .where((entry) => entry.id == paseoDefaultProvider)
        .firstOrNull;
    final model =
        runtime?.modelIDs
            .where((id) => runtime.modelData[id]?['isDefault'] == true)
            .firstOrNull ??
        runtime?.modelIDs.where((id) => id != paseoDefaultModel).firstOrNull;
    if (model == null) return const [];
    final result = await transport.request(
      'list_commands_request',
      {
        'agentId': '',
        'draftConfig': {
          'provider': paseoDefaultProvider,
          'cwd': scope,
          'model': model,
        },
      },
      timeout: const Duration(seconds: 45),
      beforeSend: () {
        _checkLocation(scope, epoch);
        if (_browserHasRequestedSource) {
          throw _browserUnavailable;
        }
      },
    );
    _checkLocation(scope, epoch);
    return _browserCommandInfos(result);
  }

  List<CommandInfo> _browserCommandInfos(Map<String, dynamic> result) {
    final raw = result['commands'];
    return [
      for (final command in raw is List ? raw.take(300) : const [])
        if (command is Map &&
            command['name'] is String &&
            (command['name'] as String).isNotEmpty &&
            (command['name'] as String).length <= 128 &&
            // `__name` commands are the agent's own plumbing, not for people.
            !(command['name'] as String).replaceFirst('/', '').startsWith('__'))
          CommandInfo(
            name: (command['name'] as String).replaceFirst(RegExp('^/'), ''),
            description: command['description'] is String
                ? command['description'] as String
                : null,
            subtask: false,
          ),
    ];
  }
}
