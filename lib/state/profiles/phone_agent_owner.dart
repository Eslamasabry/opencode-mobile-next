part of '../profiles.dart';

// A protocol profile owns server settings; the in-app Ubuntu owns one agent
// home. Reuse an existing home instead of moving or duplicating credentials.
extension PhoneAgentOwnership on ProfileStore {
  static const _ownerPrefix = 'oc.phoneAgentOwner.';
  static const _gateMigrationPrefix = 'oc.phoneAgentGateMigration.';

  bool _phoneProfile(ServerProfile profile) =>
      profile.backend == ServerBackend.openCode &&
      BuiltinLinux.managesServerUrl(profile.baseUrl);

  String phoneAgentOwnerId(String profileId) =>
      prefs.getString('$_ownerPrefix$profileId') ?? profileId;

  ServerProfile phoneAgentOwnerProfile(ServerProfile profile) {
    if (!_phoneProfile(profile)) return profile;
    final owner = phoneAgentOwnerId(profile.id);
    if (owner == profile.id) return profile;
    return ServerProfile.fromJson({...profile.toJson(), 'id': owner});
  }

  Future<void> _migratePhoneAgentOwners() async {
    final local = _cache.where(_phoneProfile).toList();
    if (local.isEmpty) return;
    String? owner;
    // A saved mapping is authoritative even after its original server row
    // was deleted. The last remaining alias still owns and deletes the home.
    for (final profile in local) {
      final saved = prefs.getString('$_ownerPrefix${profile.id}');
      if (saved != null && RegExp(r'^[A-Za-z0-9_-]{1,80}$').hasMatch(saved)) {
        owner = saved;
        break;
      }
    }
    if (owner == null) {
      // Old installs may already have several protocol profiles. Prefer a
      // checked home, then the home with cached chats, then creation order.
      int score(ServerProfile p) {
        var result = prefs.getBool('oc.phoneAgentsUsed.${p.id}') == true
            ? 1
            : 0;
        try {
          final gates = jsonDecode(
            prefs.getString('$phoneAgentGatePrefix${p.id}') ?? '{}',
          );
          if (gates is Map &&
              gates.values.any(
                (gate) => gate is Map && gate['fingerprint'] is String,
              )) {
            result += 4;
          }
          final rows = jsonDecode(
            prefs.getString('oc.agentFeed.${p.id}') ?? '[]',
          );
          if (rows is List && rows.isNotEmpty) result += 2;
        } catch (_) {
          /* Malformed preferences never establish a checked home. */
        }
        return result;
      }

      var candidate = local.first;
      for (final profile in local.skip(1)) {
        if (score(profile) > score(candidate)) candidate = profile;
      }
      owner = candidate.id;
    }
    await _adoptLegacyPhoneAgentGates(owner, local);
    for (final profile in local) {
      final key = '$_ownerPrefix${profile.id}';
      if (prefs.getString(key) != owner && !await prefs.setString(key, owner)) {
        throw const AgentHostException(AgentHostFailure.storage);
      }
    }
  }

  // Checks certify the shared Ubuntu installation, not account credentials.
  // Keep the selected home, but carry over checks from the other legacy homes.
  // A donor is consumed once: retrying a check clears proof, and a subsequent
  // load must never resurrect that proof from the untouched legacy home.
  Future<void> _adoptLegacyPhoneAgentGates(
    String owner,
    List<ServerProfile> local,
  ) async {
    final receiptKey = '$_gateMigrationPrefix$owner';
    final Set<String> receipts;
    try {
      receipts = prefs.getStringList(receiptKey)?.toSet() ?? <String>{};
      if (receipts.any(
        (id) => !RegExp(r'^[A-Za-z0-9_-]{1,80}$').hasMatch(id),
      )) {
        throw const AgentHostException(AgentHostFailure.storage);
      }
    } catch (_) {
      throw const AgentHostException(AgentHostFailure.storage);
    }
    Map<String, dynamic> gatesFor(String id) {
      try {
        final gates = jsonDecode(
          prefs.getString('$phoneAgentGatePrefix$id') ?? '{}',
        );
        return gates is Map<String, dynamic> ? gates : {};
      } catch (_) {
        return {};
      }
    }

    final gates = gatesFor(owner);
    var changed = false;
    final donors = <String>[];
    for (final profile in local) {
      if (profile.id == owner || receipts.contains(profile.id)) continue;
      donors.add(profile.id);
      for (final entry in gatesFor(profile.id).entries) {
        final gate = entry.value;
        if (!gates.containsKey(entry.key) &&
            gate is Map &&
            gate['fingerprint'] is String &&
            (gate['fingerprint'] as String).isNotEmpty) {
          gates[entry.key] = gate;
          changed = true;
        }
      }
    }
    try {
      if (changed &&
          !await prefs.setString(
            '$phoneAgentGatePrefix$owner',
            jsonEncode(gates),
          )) {
        throw const AgentHostException(AgentHostFailure.storage);
      }
      if (donors.isNotEmpty &&
          !await prefs.setStringList(receiptKey, [...receipts, ...donors])) {
        throw const AgentHostException(AgentHostFailure.storage);
      }
    } catch (_) {
      // SharedPreferences updates its cache before the platform write. Retry
      // from persisted truth instead of mistaking a refused write for proof.
      try {
        await prefs.reload();
      } catch (_) {
        /* Preserve the typed storage failure. */
      }
      throw const AgentHostException(AgentHostFailure.storage);
    }
  }

  bool phoneAgentOwnerRetainedAfterRemoving(String profileId) {
    final owner = phoneAgentOwnerId(profileId);
    return _cache.any(
      (p) =>
          p.id != profileId &&
          _phoneProfile(p) &&
          phoneAgentOwnerId(p.id) == owner,
    );
  }

  Set<String> _phoneAgentPreferenceKeys(String owner) => {
    for (final key in prefs.getKeys())
      if (key == '$phoneAgentGatePrefix$owner' ||
          key == '$_gateMigrationPrefix$owner' ||
          key == '$phoneAgentInstallPrefix$owner' ||
          key == 'oc.agentFeed.$owner' ||
          key == 'oc.phoneAgentsUsed.$owner' ||
          (key.startsWith('oc.') && key.endsWith('.$owner.agents')))
        key,
  };

  Set<String> retainedPhoneAgentPreferenceKeys(String profileId) =>
      phoneAgentOwnerRetainedAfterRemoving(profileId)
      ? _phoneAgentPreferenceKeys(phoneAgentOwnerId(profileId))
      : const {};
}
