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
  /// Runtime-only signal that the saved password could not be decrypted.
  bool requiresPasswordReentry;

  ServerProfile({
    required this.id,
    required this.name,
    required this.baseUrl,
    this.username = '',
    this.password = '',
    this.requiresPasswordReentry = false,
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

/// Validates the transport boundary used by both profile editing and connect.
/// Android only permits cleartext traffic to the two Termux loopback names.
/// Expands a pasted bare address into a full server URL so setup does not
/// require knowing URL syntax. `host[:port]` and bare IPs gain a scheme:
/// loopback becomes `http://` (the only place HTTP is allowed) and everything
/// else becomes `https://`. Values that already carry a scheme, and values
/// that are not a plausible single address, come back unchanged for the
/// validator to explain.
String normalizeServerProfileUrl(String value) {
  final raw = value.trim();
  if (raw.isEmpty || raw.contains('://')) return raw;
  final bare = RegExp(
    r'^\[?[A-Za-z0-9._\-:]+\]?(:\d{1,5})?$',
  );
  if (!bare.hasMatch(raw)) return raw;
  final host = raw.split(':').first.toLowerCase();
  final loopback = host == 'localhost' || host == '127.0.0.1';
  return '${loopback ? 'http' : 'https'}://$raw';
}

String? validateServerProfileUrl(
  String value, {
  String username = '',
  String password = '',
}) {
  final raw = value.trim();
  if (raw.isEmpty) return 'Enter a server URL.';
  if (!raw.contains('://')) {
    return 'Include https://. Use http:// only for localhost or 127.0.0.1.';
  }
  final uri = Uri.tryParse(raw);
  if (uri == null || !uri.hasScheme || uri.host.isEmpty) {
    return 'Enter a complete server URL, such as https://server.example:4096.';
  }
  if (uri.scheme != 'https' && uri.scheme != 'http') {
    return 'Server URLs must use https://, or http:// for local Termux.';
  }
  if (uri.userInfo.isNotEmpty) {
    return 'Do not put credentials in the URL. Use the fields below.';
  }
  if (uri.query.isNotEmpty || uri.fragment.isNotEmpty) {
    return 'Remove query parameters and fragments from the server URL.';
  }
  if (uri.path.isNotEmpty && uri.path != '/') {
    return 'Remove the path from the server URL. Enter only its origin.';
  }
  if (uri.scheme == 'http') {
    final host = uri.host.toLowerCase();
    final loopback = host == 'localhost' || host == '127.0.0.1';
    if (!loopback) {
      if (username.trim().isNotEmpty || password.isNotEmpty) {
        return 'HTTPS is required outside this device. Basic credentials must never be sent over HTTP.';
      }
      return 'HTTP is allowed only for localhost or 127.0.0.1. Use HTTPS for LAN and remote servers.';
    }
  }
  return null;
}

enum AppAppearance { system, light, dark }

class ProfileLocation {
  final String? directory;
  final String? workspace;

  const ProfileLocation({this.directory, this.workspace});
}

/// Persists server profiles. Metadata in SharedPreferences, secrets in the
/// Android Keystore via flutter_secure_storage.
class ProfileStore {
  static const _profilesKey = 'oc.profiles';
  static const _activeKey = 'oc.activeProfile';
  static const _modelKey = 'oc.model.'; // + profileId -> "providerID|modelID"
  static const _modelExplicitKey = 'oc.modelExplicit.'; // + profileId
  static const _agentKey = 'oc.agent.'; // + profileId
  static const _variantKey = 'oc.variant.'; // + profileId
  static const _locationKey = 'oc.location.'; // + profileId -> JSON
  static const _transcriptReasoningKey = 'oc.transcript.reasoningExpanded';
  static const _transcriptTimestampsKey = 'oc.transcript.timestampsVisible';
  static const _appearanceKey = 'oc.appearance';
  static const _providerRuntimeRefreshVersion = 'v1';

  final SharedPreferences prefs;
  final FlutterSecureStorage secure;

  ProfileStore({required this.prefs, FlutterSecureStorage? secure})
    : secure = secure ?? const FlutterSecureStorage();

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
      try {
        p.password = await secure.read(key: 'pw.${p.id}') ?? '';
        p.requiresPasswordReentry = false;
      } catch (_) {
        // Keystore entries can become unreadable after a device restore or a
        // lock-screen security change. Keep the non-secret profile usable so
        // the user can re-enter its password instead of failing app startup.
        p.password = '';
        p.requiresPasswordReentry = true;
      }
    }
    return _cache;
  }

  String _encode(List<ServerProfile> profiles) =>
      jsonEncode(profiles.map((p) => p.toJson()).toList());

  Future<void> _restoreProfiles(String? raw) async {
    final restored = raw == null
        ? await prefs.remove(_profilesKey)
        : await prefs.setString(_profilesKey, raw);
    if (!restored) {
      throw StateError('Could not restore the saved server profiles');
    }
  }

  Future<void> upsert(ServerProfile profile) async {
    final previousRaw = prefs.getString(_profilesKey);
    final next = List<ServerProfile>.of(_cache);
    final i = _cache.indexWhere((p) => p.id == profile.id);
    if (i >= 0) {
      next[i] = profile;
    } else {
      next.add(profile);
    }
    if (!await prefs.setString(_profilesKey, _encode(next))) {
      throw StateError('Could not save the server profile');
    }
    try {
      if (profile.password.isEmpty) {
        await secure.delete(key: 'pw.${profile.id}');
      } else {
        await secure.write(key: 'pw.${profile.id}', value: profile.password);
      }
    } catch (_) {
      await _restoreProfiles(previousRaw);
      rethrow;
    }
    profile.requiresPasswordReentry = false;
    _cache = next;
  }

  Future<void> remove(String id) async {
    final previousRaw = prefs.getString(_profilesKey);
    final previousActive = prefs.getString(_activeKey);
    final next = _cache.where((profile) => profile.id != id).toList();
    if (!await prefs.setString(_profilesKey, _encode(next))) {
      throw StateError('Could not remove the server profile');
    }
    try {
      if (previousActive == id) await setActiveId(null);
      await secure.delete(key: 'pw.$id');
    } catch (_) {
      await _restoreProfiles(previousRaw);
      if (previousActive == id &&
          !await prefs.setString(_activeKey, previousActive!)) {
        throw StateError('Could not restore the active server profile');
      }
      rethrow;
    }
    _cache = next;
  }

  String? get activeId => prefs.getString(_activeKey);

  Future<void> setActiveId(String? id) async {
    if (id == null) {
      if (!await prefs.remove(_activeKey)) {
        throw StateError('Could not clear the active server profile');
      }
    } else {
      if (!await prefs.setString(_activeKey, id)) {
        throw StateError('Could not save the active server profile');
      }
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

  ProfileLocation? locationFor(String profileId) {
    final raw = prefs.getString('$_locationKey$profileId');
    if (raw == null) return null;
    try {
      final value = jsonDecode(raw);
      if (value is! Map) return null;
      final directoryValue = value['directory'];
      final workspaceValue = value['workspace'];
      final directory = directoryValue is String && directoryValue.isNotEmpty
          ? directoryValue
          : null;
      final workspace = workspaceValue is String && workspaceValue.isNotEmpty
          ? workspaceValue
          : null;
      if (directory == null && workspace == null) return null;
      return ProfileLocation(directory: directory, workspace: workspace);
    } catch (_) {
      return null;
    }
  }

  Future<void> setLocation(
    String profileId, {
    String? directory,
    String? workspace,
  }) async {
    final normalizedDirectory = directory?.isNotEmpty == true
        ? directory
        : null;
    final normalizedWorkspace = workspace?.isNotEmpty == true
        ? workspace
        : null;
    if (normalizedDirectory == null && normalizedWorkspace == null) {
      await clearLocation(profileId);
      return;
    }
    if (!await prefs.setString(
      '$_locationKey$profileId',
      jsonEncode({
        'directory': normalizedDirectory,
        'workspace': normalizedWorkspace,
      }),
    )) {
      throw StateError('Could not save the selected server location');
    }
  }

  Future<void> clearLocation(String profileId) async {
    if (!await prefs.remove('$_locationKey$profileId')) {
      throw StateError('Could not clear the selected server location');
    }
  }

  String _providerRuntimeRefreshKey(
    String profileId, {
    String? directory,
    String? workspace,
  }) {
    final location = Uri.encodeComponent(
      '${directory ?? '<default>'}\n${workspace ?? '<default>'}',
    );
    return 'oc.providerRuntimeRefresh.$_providerRuntimeRefreshVersion.$profileId.$location';
  }

  bool providerRuntimeWasRefreshed(
    String profileId, {
    String? directory,
    String? workspace,
  }) =>
      prefs.getBool(
        _providerRuntimeRefreshKey(
          profileId,
          directory: directory,
          workspace: workspace,
        ),
      ) ??
      false;

  Future<void> markProviderRuntimeRefreshed(
    String profileId, {
    String? directory,
    String? workspace,
  }) async {
    if (!await prefs.setBool(
      _providerRuntimeRefreshKey(
        profileId,
        directory: directory,
        workspace: workspace,
      ),
      true,
    )) {
      throw StateError('Could not save the provider runtime migration');
    }
  }

  // ----- per-profile model/agent selection -----

  (String?, String?) modelFor(String profileId) {
    final v = prefs.getString('$_modelKey$profileId');
    if (v == null || !v.contains('|')) return (null, null);
    final parts = v.split('|');
    return (parts[0], parts[1]);
  }

  bool modelWasExplicitlySelected(String profileId) =>
      prefs.getBool('$_modelExplicitKey$profileId') ?? false;

  Future<void> setModel(
    String profileId,
    String providerID,
    String modelID, {
    bool explicit = false,
  }) async {
    await prefs.setString('$_modelKey$profileId', '$providerID|$modelID');
    await prefs.setBool('$_modelExplicitKey$profileId', explicit);
  }

  Future<void> clearModel(String profileId) async {
    await prefs.remove('$_modelKey$profileId');
    await prefs.remove('$_modelExplicitKey$profileId');
    await prefs.remove('$_variantKey$profileId');
  }

  String variantFor(String profileId) =>
      prefs.getString('$_variantKey$profileId') ?? '';

  Future<void> setVariant(String profileId, String variant) async {
    if (variant.isEmpty) {
      await prefs.remove('$_variantKey$profileId');
    } else {
      await prefs.setString('$_variantKey$profileId', variant);
    }
  }

  String agentFor(String profileId) =>
      prefs.getString('$_agentKey$profileId') ?? '';

  Future<void> setAgent(String profileId, String agent) =>
      prefs.setString('$_agentKey$profileId', agent);

  // ----- app-wide transcript display -----

  bool get transcriptReasoningExpanded =>
      prefs.getBool(_transcriptReasoningKey) ?? false;

  bool get transcriptTimestampsVisible =>
      prefs.getBool(_transcriptTimestampsKey) ?? false;

  Future<void> setTranscriptReasoningExpanded(bool expanded) async {
    if (!await prefs.setBool(_transcriptReasoningKey, expanded)) {
      throw StateError('Could not save the reasoning display preference');
    }
  }

  Future<void> setTranscriptTimestampsVisible(bool visible) async {
    if (!await prefs.setBool(_transcriptTimestampsKey, visible)) {
      throw StateError('Could not save the timestamp display preference');
    }
  }

  AppAppearance get appearance => switch (prefs.getString(_appearanceKey)) {
    'system' => AppAppearance.system,
    'light' => AppAppearance.light,
    _ => AppAppearance.dark,
  };

  Future<void> setAppearance(AppAppearance appearance) async {
    if (!await prefs.setString(_appearanceKey, appearance.name)) {
      throw StateError('Could not save the appearance preference');
    }
  }
}
