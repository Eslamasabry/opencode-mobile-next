/// Safe projections of Paseo 0.9.2 snapshots and ACP permission requests.
/// No provider authentication, capabilities or commands are inferred from CLI
/// availability, generic persistence flags or undeclared wire fields.
library;

import '../domain/host_agent_providers.dart';
import 'transport.dart';

bool isExistingPaseoProvider(Object? id) =>
    const {'claude', 'codex', 'pi', 'opencode', 'copilot'}.contains(id);

bool _safeId(Object? id) =>
    id is String && RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(id);

HostAgentProviderCatalog paseoHostAgentCatalog(
  List<Map<String, dynamic>> entries,
) {
  final seen = <String>{};
  final rows = <HostAgentProvider>[];
  for (final entry in entries) {
    final id = entry['provider'];
    if (!_safeId(id) || !seen.add(id as String)) continue;
    final pilot = const {'gemini', 'omp', 'omp-acp', 'fx'}.contains(id);
    final loginRequired =
        pilot &&
        entry['error'] is String &&
        RegExp(
          r'AuthRequired|Authentication required',
          caseSensitive: false,
        ).hasMatch(entry['error'] as String);
    final checking = entry['enabled'] != false && entry['status'] == 'loading';
    rows.add(
      HostAgentProvider(
        id: id,
        displayName: switch (id) {
          'gemini' => 'Gemini CLI',
          'omp' || 'omp-acp' => 'omp',
          'fx' => 'fx',
          'claude' => 'Claude Code',
          'codex' => 'Codex',
          'copilot' => 'Copilot',
          'pi' => 'Pi',
          'opencode' => 'OpenCode',
          _ => 'Other host agent',
        },
        availability: checking
            ? HostAgentProviderAvailability.checking
            : HostAgentProviderAvailability.hidden,
        hiddenReason: checking
            ? null
            : entry['enabled'] == false
            ? HostAgentProviderHiddenReason.disabled
            : !pilot
            ? HostAgentProviderHiddenReason.notPilot
            : loginRequired
            ? HostAgentProviderHiddenReason.resumeUnverified
            : entry['status'] != 'ready'
            ? HostAgentProviderHiddenReason.unavailable
            : HostAgentProviderHiddenReason.resumeUnverified,
        loginState: loginRequired
            ? HostAgentLoginState.needsHostSignIn
            : HostAgentLoginState.unknown,
        resumeSupport: HostAgentResumeSupport.unknown,
      ),
    );
  }
  return HostAgentProviderCatalog(providers: rows);
}

HostAgentPermissionRequest? paseoHostPermission(
  String sessionId,
  Map<String, dynamic> request, {
  String? provider,
}) {
  final metadata = request['metadata'];
  final hasOptions = metadata is Map && metadata.containsKey('options');
  final runtime = provider ?? request['provider'];
  if (!hasOptions && isExistingPaseoProvider(runtime) && runtime != 'copilot') {
    return null;
  }
  final requestId = request['id'];
  if (requestId is! String || requestId.isEmpty || requestId.length > 512) {
    throw PaseoFailure(PaseoFailureKind.invalidResponse);
  }
  final choices = <HostAgentPermissionChoice>[];
  final options = hasOptions ? metadata['options'] : null;
  final actions = request['actions'];
  if (options is List &&
      options.length <= 64 &&
      actions is List &&
      actions.length <= 64) {
    final seen = <String>{};
    final actionIds = <String>{};
    var valid = true;
    for (final action in actions) {
      if (action is! Map ||
          action['id'] is! String ||
          !actionIds.add(action['id'] as String)) {
        valid = false;
        break;
      }
    }
    for (final option in options) {
      if (option is! Map) {
        valid = false;
        break;
      }
      final id = option['optionId'];
      final kind = option['kind'];
      if (id is! String ||
          id.isEmpty ||
          id.length > 512 ||
          !seen.add(id) ||
          !actionIds.contains(id)) {
        valid = false;
        break;
      }
      final matching = actions
          .whereType<Map>()
          .where((a) => a['id'] == id)
          .single;
      final behavior = kind == 'allow_once'
          ? HostAgentPermissionBehavior.allowOnce
          : kind == 'reject_once'
          ? HostAgentPermissionBehavior.rejectOnce
          : null;
      if (behavior == null) continue;
      if (matching['behavior'] !=
          (behavior == HostAgentPermissionBehavior.allowOnce
              ? 'allow'
              : 'deny')) {
        valid = false;
        break;
      }
      choices.add(HostAgentPermissionChoice(actionId: id, behavior: behavior));
    }
    if (!valid || seen.length != actionIds.length) choices.clear();
  }
  return HostAgentPermissionRequest(
    requestId: requestId,
    sessionId: sessionId,
    choices: choices,
  );
}
