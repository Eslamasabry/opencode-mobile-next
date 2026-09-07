import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Explicit personal associations, never proof of plugin ownership.
/// Scope hashes bind them to endpoint, account and selected server location.
class PluginCommandMappings {
  PluginCommandMappings(this.preferences, this.profileID, this.isReadable);

  final SharedPreferences preferences;
  final String profileID;
  final bool Function() isReadable;
  static final Map<String, Future<void>> _writes = {};
  static const maxMappings = 64;
  static const maxCommands = 16;
  String get key => 'oc.pluginCommandMappings.$profileID';

  static String scope({
    required String baseUrl,
    String? username,
    String? directory,
    String? workspace,
  }) => sha256
      .convert(
        utf8.encode(jsonEncode([baseUrl, username, directory, workspace])),
      )
      .toString();

  static bool validName(String name) =>
      name.isNotEmpty &&
      name.length <= 200 &&
      !RegExp(r'[\x00-\x1f\x7f]').hasMatch(name);

  Map<String, List<String>> load(String scope) {
    if (!isReadable()) return {};
    return {
      for (final row in _read())
        if (row['scope'] == scope)
          row['plugin'] as String: List<String>.from(row['commands'] as List),
    };
  }

  List<Map<String, dynamic>> _read() {
    try {
      final raw = preferences.getString(key);
      if (raw == null || raw.length > 262144) return [];
      final data = jsonDecode(raw);
      if (data is! Map || data['version'] != 1 || data['mappings'] is! List) {
        return [];
      }
      final rows = data['mappings'] as List;
      if (rows.length > maxMappings) return [];
      final result = <Map<String, dynamic>>[];
      for (final row in rows) {
        if (row is! Map ||
            row['scope'] is! String ||
            !RegExp(r'^[a-f0-9]{64}$').hasMatch(row['scope'] as String) ||
            row['plugin'] is! String ||
            !validName(row['plugin'] as String) ||
            row['commands'] is! List) {
          return [];
        }
        final commands = row['commands'] as List;
        if (commands.length > maxCommands ||
            commands.any((c) => c is! String || !validName(c))) {
          return [];
        }
        result.add(Map<String, dynamic>.from(row));
      }
      return result;
    } catch (_) {
      return [];
    }
  }

  Future<void> set(
    String scope,
    String pluginID,
    Iterable<String> names,
  ) async {
    final commands = names.toSet().toList()..sort();
    if (!RegExp(r'^[a-f0-9]{64}$').hasMatch(scope) ||
        !validName(pluginID) ||
        commands.length > maxCommands ||
        commands.any((c) => !validName(c))) {
      throw const FormatException('Invalid personal command mapping.');
    }
    await _change((rows) {
      rows.removeWhere((r) => r['scope'] == scope && r['plugin'] == pluginID);
      if (commands.isNotEmpty) {
        if (rows.length >= maxMappings) {
          throw StateError('Mapping limit reached.');
        }
        rows.add({'scope': scope, 'plugin': pluginID, 'commands': commands});
      }
      return rows;
    });
  }

  /// Clears all locations for this profile through the same durable queue.
  Future<void> clear() => _change((_) => []);

  Future<void> _change(
    List<Map<String, dynamic>> Function(List<Map<String, dynamic>>) update,
  ) async {
    final previous = _writes[key] ?? Future<void>.value();
    final next = previous.then((_) async {
      if (!isReadable()) throw StateError('Profile removed.');
      await preferences.reload();
      if (!isReadable()) throw StateError('Profile removed.');
      final previousRaw = preferences.getString(key);
      final rows = update(_read());
      try {
        final saved = rows.isEmpty
            ? await preferences.remove(key)
            : await preferences.setString(
                key,
                jsonEncode({'version': 1, 'mappings': rows}),
              );
        if (!saved) throw StateError('Could not save personal mapping.');
      } catch (_) {
        await _restore(previousRaw);
        rethrow;
      } finally {
        // A deletion racing a platform write must not resurrect this key.
        if (!isReadable()) await preferences.remove(key);
      }
      if (!isReadable()) throw StateError('Profile removed.');
    });
    final tail = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    _writes[key] = tail;
    try {
      await next;
    } finally {
      if (identical(_writes[key], tail)) _writes.remove(key);
    }
  }

  Future<void> _restore(String? previousRaw) async {
    await preferences.reload();
    if (!isReadable()) return;
    if (preferences.getString(key) == previousRaw) return;
    if (!isReadable()) return;
    final restored = previousRaw == null
        ? await preferences.remove(key)
        : await preferences.setString(key, previousRaw);
    if (!isReadable()) return;
    if (!restored) {
      throw StateError('Could not restore personal mapping.');
    }
    await preferences.reload();
    if (!isReadable()) return;
    if (preferences.getString(key) != previousRaw) {
      throw StateError('Could not restore personal mapping.');
    }
  }
}
