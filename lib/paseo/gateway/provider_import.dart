part of '../gateway.dart';

// ---- OD1 agent lane: import a conversation started in Claude Code ----------

extension _PaseoProviderImport on PaseoGateway {
  String? _importText(Object? value, int max) {
    if (value is! String) return null;
    final clean = value
        .replaceAll(RegExp(r'[\x00-\x1f\x7f]'), ' ')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
    if (clean.isEmpty) return null;
    return clean.length > max ? '${clean.substring(0, max)}…' : clean;
  }

  Future<ImportableConversations> _importableConversations() async {
    final scope = _scope, epoch = _locationEpoch;
    final result = await transport.request(
      'fetch_recent_provider_sessions_request',
      {
        'cwd': scope,
        'providers': const ['claude'],
        'limit': 50,
      },
      timeout: const Duration(seconds: 45),
    );
    _checkLocation(scope, epoch);
    final raw = result['entries'];
    final items = <ImportableConversation>[];
    if (raw is List) {
      for (final entry in raw.take(200)) {
        if (entry is! Map) continue;
        final agentId = entry['providerId'];
        final handle = entry['providerHandleId'];
        final directory = entry['cwd'];
        final last = entry['lastActivityAt'] is String
            ? DateTime.tryParse(entry['lastActivityAt'] as String)
            : null;
        if (agentId is! String ||
            !isPaseoProviderId(agentId) ||
            handle is! String ||
            handle.isEmpty ||
            handle.length > 256 ||
            RegExp(r'[\x00-\x1f\x7f]').hasMatch(handle) ||
            directory is! String ||
            directory.isEmpty ||
            last == null) {
          continue;
        }
        items.add(
          ImportableConversation(
            agentId: agentId,
            handle: handle,
            directory: directory,
            lastUsed: last,
            title: _importText(entry['title'], 120),
            firstPrompt: _importText(entry['firstPromptPreview'], 120),
          ),
        );
      }
    }
    items.sort((a, b) => b.lastUsed.compareTo(a.lastUsed));
    final already = result['filteredAlreadyImportedCount'];
    return ImportableConversations(
      items: items,
      alreadyImported: already is int && already > 0 ? already : 0,
    );
  }

  Future<Session> _importConversation(
    ImportableConversation conversation,
  ) async {
    final scope = _scope, epoch = _locationEpoch;
    final result = await transport.request(
      'import_agent_request',
      {
        'providerId': conversation.agentId,
        'providerHandleId': conversation.handle,
        'cwd': conversation.directory,
      },
      mutation: true,
      timeout: const Duration(seconds: 90),
    );
    _checkLocation(scope, epoch);
    final agent = result['agent'];
    final id = agent is Map ? agent['id'] : null;
    if (result['status'] != 'agent_resumed' || id is! String) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    // The ordinary read of one agent: scope, archive and local-work checks
    // all apply to an imported conversation as to any other.
    await _fetchAgent(id);
    final session = _sessions[id];
    if (session == null) throw PaseoFailure(PaseoFailureKind.scopeMismatch);
    return session;
  }
}
