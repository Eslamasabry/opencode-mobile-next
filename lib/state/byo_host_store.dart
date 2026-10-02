import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/byo_host.dart';

/// Profile-scoped metadata and a single, separately encrypted secret envelope.
///
/// The profile deletion coordinator must call [remove]: the ordinary scoped
/// preference sweep does not delete secure-storage entries.
class PersistentByoHostStore implements ByoHostStore {
  PersistentByoHostStore({required this.prefs, FlutterSecureStorage? secure})
    : secure = secure ?? const FlutterSecureStorage();

  final SharedPreferences prefs;
  final FlutterSecureStorage secure;

  // Order operations on this store so an overlapping save cannot resurrect a
  // secret between the two deletion steps. Callers share one store instance.
  Future<void> _pending = Future<void>.value();

  static const _recordPrefix = 'oc.byoHost.';
  static String _recordKey(String id) => '$_recordPrefix$id';
  static String _secretKey(String id) => 'oc.byoHostSecrets.$id';

  static void _validateId(String id) {
    if (!byoHostSafeId(id)) {
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
  }

  Future<T> _ordered<T>(Future<T> Function() action) {
    final result = _pending.then((_) async {
      try {
        return await action();
      } catch (_) {
        // Plugin exceptions and malformed JSON may contain credentials. Never
        // retain their message, cause or stack in a public failure.
        throw const ByoHostFailure(ByoHostFailureCode.storage);
      }
    });
    _pending = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  @override
  Future<List<ByoHostRecord>> listRecords() => _ordered(() async {
    await prefs.reload();
    final keys =
        prefs.getKeys().where((key) => key.startsWith(_recordPrefix)).toList()
          ..sort();
    final records = <ByoHostRecord>[];
    for (final key in keys) {
      final id = key.substring(_recordPrefix.length);
      _validateId(id);
      records.add(_decodeRecord(id, prefs.getString(key)!));
    }
    return List<ByoHostRecord>.unmodifiable(records);
  });

  @override
  Future<ByoHostRecord?> readRecord(String profileId) => _ordered(() async {
    _validateId(profileId);
    await prefs.reload();
    final raw = prefs.getString(_recordKey(profileId));
    return raw == null ? null : _decodeRecord(profileId, raw);
  });

  static ByoHostRecord _decodeRecord(String profileId, String raw) {
    final record = ByoHostRecord.fromJson(
      jsonDecode(raw) as Map<String, dynamic>,
    );
    if (record.profileId != profileId) {
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
    return record;
  }

  @override
  Future<ByoHostSecrets?> readSecrets(String profileId) => _ordered(() async {
    _validateId(profileId);
    final raw = await secure.read(key: _secretKey(profileId));
    return raw == null ? null : ByoHostSecrets.decode(raw);
  });

  @override
  Future<void> saveRecord(ByoHostRecord record) => _ordered(() async {
    _validateId(record.profileId);
    await _changePreferences(
      () => prefs.setString(
        _recordKey(record.profileId),
        jsonEncode(record.toJson()),
      ),
    );
  });

  @override
  Future<void> saveSecrets(String profileId, ByoHostSecrets secrets) =>
      _ordered(() async {
        _validateId(profileId);
        await secure.write(key: _secretKey(profileId), value: secrets.encode());
      });

  @override
  Future<void> remove(String profileId) => _ordered(() async {
    _validateId(profileId);
    final key = _secretKey(profileId);
    await secure.delete(key: key);
    // Refuse to discard the cleanup record if a platform reports success but
    // retains the vault entry. Either partial deletion is safe to retry.
    if (await secure.read(key: key) != null) {
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
    await _changePreferences(() => prefs.remove(_recordKey(profileId)));
  });

  Future<void> _changePreferences(Future<bool> Function() change) async {
    try {
      if (!await change()) {
        throw const ByoHostFailure(ByoHostFailureCode.storage);
      }
    } catch (_) {
      // SharedPreferences mutates its cache before persistence confirms. Load
      // the durable value again after refusal, including a partial deletion.
      try {
        await prefs.reload();
      } catch (_) {}
      rethrow;
    }
  }
}
