/// Safe projections of Paseo 0.9.2 snapshots and ACP permission requests.
/// No provider authentication, capabilities or commands are inferred from CLI
/// availability, generic persistence flags or undeclared wire fields.
library;

import '../domain/agent_catalog.dart';
import '../domain/host_agent_providers.dart';
import 'transport.dart';

bool isExistingPaseoProvider(Object? id) =>
    const {'claude', 'codex', 'pi', 'opencode', 'copilot'}.contains(id);

bool isPaseoProviderId(Object? id) =>
    id is String && RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(id);

/// A provider error that asks the person to sign in. Paseo's own code says
/// "AuthRequired"; agent CLIs say it in their words, e.g. fx 0.0.12: "fx
/// needs access to Vercel AI Gateway. Run fx login to sign in, fx setup to
/// use an API key, …" (issue #95: such agents sat on "Checking sign-in…").
bool paseoProviderNeedsSignIn(Map<String, dynamic> entry) {
  final error = entry['error'];
  return error is String && _signInNeeded.hasMatch(error);
}

final _signInNeeded = RegExp(
  r'AuthRequired|Authentication required|\bnot (?:logged|signed) in\b|'
  r'\b(?:run|use) \S+ (?:login|auth)\b|\b(?:log|sign) ?in\b|'
  r'\bapi[ _-]?key\b|\bunauthori[sz]ed\b|\b401\b',
  caseSensitive: false,
);

bool paseoProviderCanStart(Map<String, dynamic> entry) =>
    isPaseoProviderId(entry['provider']) &&
    entry['enabled'] != false &&
    entry['status'] == 'ready' &&
    (entry['error'] == null || entry['error'] == '') &&
    !(entry['source'] == 'custom' &&
        isExistingPaseoProvider(entry['provider'])) &&
    !paseoProviderNeedsSignIn(entry);

String paseoHostAgentName(String id) {
  for (final agent in AgentCatalog.builtIn.agents) {
    if (agent.providerId == id) return agent.name;
  }
  return switch (id) {
    'omp' => 'omp',
    'copilot' => 'GitHub Copilot',
    'pi' => 'Pi',
    _ => 'Other host agent',
  };
}

HostAgentProviderCatalog paseoHostAgentCatalog(
  List<Map<String, dynamic>> entries,
) {
  final seen = <String>{};
  final rows = <HostAgentProvider>[];
  for (final entry in entries) {
    final id = entry['provider'];
    if (!isPaseoProviderId(id) || !seen.add(id as String)) continue;
    final loginRequired = paseoProviderNeedsSignIn(entry);
    final checking = entry['enabled'] != false && entry['status'] == 'loading';
    rows.add(
      HostAgentProvider(
        id: id,
        displayName: paseoHostAgentName(id),
        availability: checking
            ? HostAgentProviderAvailability.checking
            : entry['enabled'] == false
            ? HostAgentProviderAvailability.hidden
            : loginRequired
            ? HostAgentProviderAvailability.needsHostSignIn
            : paseoProviderCanStart(entry)
            ? HostAgentProviderAvailability.ready
            : HostAgentProviderAvailability.hidden,
        hiddenReason: checking
            ? null
            : entry['enabled'] == false
            ? HostAgentProviderHiddenReason.disabled
            : loginRequired || paseoProviderCanStart(entry)
            ? null
            : HostAgentProviderHiddenReason.unavailable,
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
