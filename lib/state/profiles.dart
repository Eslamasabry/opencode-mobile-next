import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// One opencode server the user can connect to.
class ServerProfile {
  final String id;
  String name;
  String baseUrl;
  String username;
  String password; // kept in secure storage, mirrored here at runtime

  ServerProfile({
    required this.id,
    required this.name,
    required this.baseUrl,
    this.username = '',
    this.password = '',
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'baseUrl': baseUrl,
        'username': username,
      };

  static ServerProfile fromJson(Map<String, dynamic> j) => ServerProfile(
        id: j['id'] as String,
        name: (j['name'] ?? '').toString(),
        baseUrl: (j['baseUrl'] ?? '').toString(),
        username: (j['username'] ?? '').toString(),
      );
}

/// Persists server profiles. Metadata in SharedPreferences, secrets in the
/// Android Keystore via flutter_secure_storage.
class ProfileStore {
  static const _profilesKey = 'oc.profiles';
  static const _activeKey = 'oc.activeProfile';
  static const _modelKey = 'oc.model.'; // + profileId -> "providerID|modelID"
  static const _agentKey = 'oc.agent.'; // + profileId

  final SharedPreferences prefs;
  final FlutterSecureStorage secure;

  ProfileStore({required this.prefs}) : secure = const FlutterSecureStorage();

  List<ServerProfile> _cache = [];
  List<ServerProfile> get profiles => List.unmodifiable(_cache);

  Future<List<ServerProfile>> load() async {
    final raw = prefs.getString(_profilesKey);
    if (raw == null) {
      _cache = [];
      return _cache;
    }
    try {
      final list = jsonDecode(raw) as List;
      _cache = list
          .whereType<Map<String, dynamic>>()
          .map(ServerProfile.fromJson)
          .toList();
    } catch (_) {
      _cache = [];
    }
    // Restore secrets.
    for (final p in _cache) {
      p.password = await secure.read(key: 'pw.${p.id}') ?? '';
    }
    return _cache;
  }

  Future<void> _persist() async {
    await prefs.setString(
        _profilesKey, jsonEncode(_cache.map((p) => p.toJson()).toList()));
  }

  Future<void> upsert(ServerProfile profile) async {
    final i = _cache.indexWhere((p) => p.id == profile.id);
    if (i >= 0) {
      _cache[i] = profile;
    } else {
      _cache.add(profile);
    }
    if (profile.password.isEmpty) {
      await secure.delete(key: 'pw.${profile.id}');
    } else {
      await secure.write(key: 'pw.${profile.id}', value: profile.password);
    }
    await _persist();
  }

  Future<void> remove(String id) async {
    _cache.removeWhere((p) => p.id == id);
    await secure.delete(key: 'pw.$id');
    if (prefs.getString(_activeKey) == id) await setActiveId(null);
    await _persist();
  }

  String? get activeId => prefs.getString(_activeKey);

  Future<void> setActiveId(String? id) async {
    if (id == null) {
      await prefs.remove(_activeKey);
    } else {
      await prefs.setString(_activeKey, id);
    }
  }

  ServerProfile? get active {
    final id = activeId;
    if (id == null) return null;
    for (final p in _cache) {
      if (p.id == id) return p;
    }
    return null;
  }

  // ----- per-profile model/agent selection -----

  (String?, String?) modelFor(String profileId) {
    final v = prefs.getString('$_modelKey$profileId');
    if (v == null || !v.contains('|')) return (null, null);
    final parts = v.split('|');
    return (parts[0], parts[1]);
  }

  Future<void> setModel(String profileId, String providerID, String modelID) =>
      prefs.setString('$_modelKey$profileId', '$providerID|$modelID');

  String agentFor(String profileId) => prefs.getString('$_agentKey$profileId') ?? '';

  Future<void> setAgent(String profileId, String agent) =>
      prefs.setString('$_agentKey$profileId', agent);
}
