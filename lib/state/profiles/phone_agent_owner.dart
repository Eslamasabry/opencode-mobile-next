part of '../profiles.dart';

// A protocol profile owns server settings; the in-app Ubuntu owns one agent
// home. Reuse an existing home instead of moving or duplicating credentials.
extension PhoneAgentOwnership on ProfileStore {
  static const _ownerPrefix = 'oc.phoneAgentOwner.';

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
    for (final profile in local) {
      final key = '$_ownerPrefix${profile.id}';
      if (prefs.getString(key) != owner && !await prefs.setString(key, owner)) {
        throw const AgentHostException(AgentHostFailure.storage);
      }
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
