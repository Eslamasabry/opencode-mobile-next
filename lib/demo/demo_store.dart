import 'package:shared_preferences/shared_preferences.dart';

import '../state/profiles.dart';

/// A route-owned preference map. Never replaces the application's plugin or
/// singleton, so a live connection can keep using its real preferences.
class DemoPreferences implements SharedPreferences {
  final _values = <String, Object>{};

  @override
  Set<String> getKeys() => Set.of(_values.keys);
  @override
  Object? get(String key) => switch (_values[key]) {
    final List<String> values => List<String>.of(values),
    final value => value,
  };
  @override
  bool containsKey(String key) => _values.containsKey(key);
  @override
  bool? getBool(String key) => get(key) as bool?;
  @override
  int? getInt(String key) => get(key) as int?;
  @override
  double? getDouble(String key) => get(key) as double?;
  @override
  String? getString(String key) => get(key) as String?;
  @override
  List<String>? getStringList(String key) => get(key) as List<String>?;

  Future<bool> _put(String key, Object value) async {
    _values[key] = value;
    return true;
  }

  @override
  Future<bool> setBool(String key, bool value) => _put(key, value);
  @override
  Future<bool> setInt(String key, int value) => _put(key, value);
  @override
  Future<bool> setDouble(String key, double value) => _put(key, value);
  @override
  Future<bool> setString(String key, String value) => _put(key, value);
  @override
  Future<bool> setStringList(String key, List<String> value) =>
      _put(key, List<String>.of(value));
  @override
  Future<bool> remove(String key) async {
    _values.remove(key);
    return true;
  }

  @override
  Future<bool> clear() async {
    _values.clear();
    return true;
  }

  @override
  Future<void> reload() async {}
  @override
  Future<bool> commit() async => true;
}

/// The synthetic identity exists only in this route's runtime. No profile JSON
/// or credentials are inserted into either preference store or secure storage.
class DemoProfileStore extends ProfileStore {
  DemoProfileStore() : super(prefs: DemoPreferences());

  final _profile = ServerProfile(
    id: 'offline-demo',
    name: 'Offline demo',
    baseUrl: 'https://offline-demo.invalid',
  );

  @override
  List<ServerProfile> get profiles => [_profile];
  @override
  String get activeId => _profile.id;
  @override
  Future<List<ServerProfile>> load() async => profiles;
  @override
  Future<void> setActiveId(String? id) async {
    if (id != _profile.id) {
      throw StateError('Leave the demo to select a server.');
    }
  }

  @override
  Future<void> upsert(ServerProfile profile) async =>
      throw StateError('Server settings are unavailable in the demo.');
  @override
  Future<void> remove(String id) async =>
      throw StateError('Leave the demo to manage servers.');
}
