import 'dart:convert';

import 'transport.dart';

/// Strict parsing prevents a malformed policy from silently losing denials.
class Api2SessionPermissionPolicy {
  Api2SessionPermissionPolicy._(
    this.rules,
    this.reverted,
    this.agent,
    this.metadata,
  );

  static const metadataKey = 'oc.mobile.permissionRequests';
  final List<Map<String, String>> rules;
  final bool reverted;
  final String? agent;
  final Map<String, dynamic> metadata;

  /// OC2's agent list places its resolved default first (including fallback
  /// from a hidden or subagent-only configured default).
  static Map<String, dynamic> defaultAgent(Object? response) {
    final entries = response is Map ? response['data'] : null;
    if (entries is! List || entries.isEmpty) {
      throw const Api2RequestError('Could not read the default agent.');
    }
    final selected = entries.first;
    if (selected is! Map ||
        selected['id'] is! String ||
        (selected['id'] as String).isEmpty ||
        selected['hidden'] != false ||
        !const ['primary', 'all'].contains(selected['mode'])) {
      throw const Api2RequestError('Could not verify the default agent.');
    }
    return {'data': selected};
  }

  static List<Map<String, String>> _rules(Object? raw) {
    if (raw is! List || raw.length > 1000) {
      throw const Api2RequestError('Could not read conversation permissions.');
    }
    return [
      for (final rule in raw)
        if (rule is Map &&
            rule['action'] is String &&
            (rule['action'] as String).isNotEmpty &&
            rule['resource'] is String &&
            (rule['resource'] as String).isNotEmpty &&
            const ['allow', 'ask', 'deny'].contains(rule['effect']))
          {
            'action': rule['action'] as String,
            'resource': rule['resource'] as String,
            'effect': rule['effect'] as String,
          }
        else
          throw const Api2RequestError(
            'Could not read conversation permissions.',
          ),
    ];
  }

  factory Api2SessionPermissionPolicy.fromSession(
    Object? response,
    String sessionID,
  ) {
    final data = response is Map ? response['data'] : null;
    if (data is! Map ||
        data['id'] != sessionID ||
        (data['agent'] != null &&
            (data['agent'] is! String || (data['agent'] as String).isEmpty)) ||
        (data['metadata'] != null && data['metadata'] is! Map)) {
      throw const Api2RequestError(
        'Could not verify conversation permissions.',
      );
    }
    return Api2SessionPermissionPolicy._(
      _rules(data.containsKey('permissions') ? data['permissions'] : const []),
      data['revert'] != null,
      data['agent'] as String?,
      Map<String, dynamic>.from(data['metadata'] as Map? ?? const {}),
    );
  }

  /// Keep the original session layer separate from the last app-installed
  /// policy. Otherwise a previous build agent's allow/ask rules would mask a
  /// newly selected plan agent's denials on the next message.
  List<Map<String, String>> get _original {
    final saved = metadata[metadataKey];
    if (saved == null) return rules;
    if (saved is! Map || saved['version'] != 1) {
      throw const Api2RequestError(
        'Could not verify saved conversation permissions.',
      );
    }
    final applied = _rules(saved['applied']);
    final original = _rules(saved['original']);
    // A different client changed this session's rules: preserve its new truth.
    return matches(applied) ? original : rules;
  }

  /// OC2 evaluates agent rules first, then session rules, with last match
  /// winning. Preserve that order (including deny/allow exceptions) while
  /// turning implicit allowances into requests. Saved user grants still apply.
  ///
  /// With [serverRuns] (the person approves this conversation automatically)
  /// the session gets its original rules back, so the server runs what its
  /// own rules allow without waiting for this phone. A request this phone
  /// never sees (sent while it was away, or hidden by a server whose pending
  /// list fails to load) would otherwise hold the tool until the server's
  /// hour-long idle sweep stops the reply. Asks and denials still apply.
  Map<String, dynamic> updateForAgent(
    Object? response, {
    bool serverRuns = false,
  }) {
    final data = response is Map ? response['data'] : null;
    if (data is! Map ||
        data['id'] is! String ||
        (data['id'] as String).isEmpty ||
        (agent != null && data['id'] != agent)) {
      throw const Api2RequestError('Could not verify agent permissions.');
    }
    final original = _original;
    final required = serverRuns
        ? original
        : <Map<String, String>>[
            {'action': '*', 'resource': '*', 'effect': 'ask'},
            for (final rule in [..._rules(data['permissions']), ...original])
              {...rule, if (rule['effect'] == 'allow') 'effect': 'ask'},
          ];
    if (required.length > 1000) {
      throw const Api2RequestError(
        'Conversation permissions are too large to update safely.',
      );
    }
    return {
      'permissions': required,
      'metadata': {
        ...metadata,
        metadataKey: {'version': 1, 'original': original, 'applied': required},
      },
    };
  }

  bool matches(List<Map<String, String>> expected) =>
      jsonEncode(rules) == jsonEncode(expected);

  bool confirms(Map<String, dynamic> update) {
    final saved = metadata[metadataKey];
    final expected = (update['metadata'] as Map)[metadataKey] as Map;
    return matches(_rules(update['permissions'])) &&
        saved is Map &&
        saved['version'] == expected['version'] &&
        jsonEncode(_rules(saved['original'])) ==
            jsonEncode(_rules(expected['original'])) &&
        jsonEncode(_rules(saved['applied'])) ==
            jsonEncode(_rules(expected['applied']));
  }
}
