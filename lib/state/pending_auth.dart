import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/server_gateway.dart';
import 'profiles.dart';

enum PendingAuthKind { oauth, command }

/// Recovery metadata only. Never add a method, label, answer, browser URL,
/// authorization code, provider message, or credential to this record.
class PendingAuthAttempt {
  const PendingAuthAttempt({
    required this.attemptID,
    required this.integrationID,
    required this.kind,
    required this.mode,
    required this.profileID,
    required this.origin,
    required this.expiresAt,
    this.directory,
    this.workspace,
  });

  final String attemptID;
  final String integrationID;
  final PendingAuthKind kind;
  final IntegrationAuthMode mode;
  final String profileID;
  final String origin;
  final String? directory;
  final String? workspace;
  final int expiresAt;

  bool get expired => DateTime.now().millisecondsSinceEpoch >= expiresAt;
  Object get key =>
      (profileID, origin, directory, workspace, kind, integrationID, attemptID);

  Map<String, Object?> toJson() => {
    'attemptID': attemptID,
    'integrationID': integrationID,
    'kind': kind.name,
    'mode': mode.name,
    'profileID': profileID,
    'origin': origin,
    'directory': directory,
    'workspace': workspace,
    'expiresAt': expiresAt,
  };

  static PendingAuthAttempt? parse(Object? value, String owner) {
    if (value is! Map || value.length != 9) return null;
    const fields = {
      'attemptID',
      'integrationID',
      'kind',
      'mode',
      'profileID',
      'origin',
      'directory',
      'workspace',
      'expiresAt',
    };
    if (value.keys.any((key) => !fields.contains(key))) return null;
    final id = value['attemptID'];
    final integration = value['integrationID'];
    final origin = value['origin'];
    final expiry = value['expiresAt'];
    bool validID(Object? id) =>
        id is String &&
        id.length <= 256 &&
        RegExp(r'^[A-Za-z0-9_.:-]+$').hasMatch(id) &&
        id != '.' &&
        id != '..';
    bool validLocation(Object? value) =>
        value == null ||
        (value is String &&
            value.length <= 2048 &&
            !RegExp(r'[\x00-\x1f\x7f-\x9f]').hasMatch(value));
    if (!validID(id) ||
        !validID(integration) ||
        value['profileID'] != owner ||
        owner.isEmpty ||
        owner.length > 256 ||
        origin is! String ||
        origin.length > 2048 ||
        validateServerProfileUrl(origin) != null ||
        expiry is! int ||
        expiry <= 0 ||
        !validLocation(value['directory']) ||
        !validLocation(value['workspace']) ||
        !['oauth', 'command'].contains(value['kind']) ||
        !['auto', 'code'].contains(value['mode'])) {
      return null;
    }
    return PendingAuthAttempt(
      attemptID: id as String,
      integrationID: integration as String,
      kind: value['kind'] == 'command'
          ? PendingAuthKind.command
          : PendingAuthKind.oauth,
      mode: value['mode'] == 'code'
          ? IntegrationAuthMode.code
          : IntegrationAuthMode.auto,
      profileID: owner,
      origin: origin,
      directory: value['directory'] as String?,
      workspace: value['workspace'] as String?,
      expiresAt: expiry,
    );
  }
}

/// Writes are serialized per owner. Failed writes preserve the memory copy and
/// expose uncertainty; failed removals never pretend the attempt was forgotten.
/// Admission is closed synchronously before profile deletion drains the writer.
class PendingAuthStore {
  PendingAuthStore(this.preferences);
  final SharedPreferences preferences;
  static const prefix = 'oc.pendingAuth.';
  static const maxCount = 16;
  static const maxBytes = 524288;
  static const retention = Duration(hours: 24);
  final _cache = <String, List<PendingAuthAttempt>>{};
  final _writes = <String, Future<void>>{};
  final _blocked = <String>{};
  final _uncertain = <String>{};
  final _corrupt = <String>{};

  bool uncertain(String owner) =>
      _blocked.contains(owner) ||
      _uncertain.contains(owner) ||
      _corrupt.contains(owner);

  List<PendingAuthAttempt> entries(String owner) {
    if (_blocked.contains(owner)) return const [];
    return List.unmodifiable(
      _cache.putIfAbsent(owner, () {
        try {
          final raw = preferences.getString('$prefix$owner');
          if (raw == null) return [];
          if (utf8.encode(raw).length > maxBytes) throw const FormatException();
          final values = jsonDecode(raw);
          if (values is! List || values.length > maxCount) {
            throw const FormatException();
          }
          final result = <PendingAuthAttempt>[];
          for (final value in values) {
            final entry = PendingAuthAttempt.parse(value, owner);
            if (entry == null ||
                entry.expiresAt >
                    DateTime.now().add(retention).millisecondsSinceEpoch ||
                result.any((e) => e.key == entry.key)) {
              throw const FormatException();
            }
            result.add(entry);
          }
          return result;
        } catch (_) {
          _corrupt.add(owner);
          return [];
        }
      }),
    );
  }

  void ensureCapacity(String owner) {
    final current = entries(owner);
    if (_blocked.contains(owner) ||
        _corrupt.contains(owner) ||
        current.where((e) => !_beyondRetention(e)).length >= maxCount) {
      throw StateError('Sign-in recovery storage is unavailable.');
    }
  }

  bool _beyondRetention(PendingAuthAttempt e) =>
      DateTime.now().millisecondsSinceEpoch - e.expiresAt >
      retention.inMilliseconds;

  Future<bool> save(PendingAuthAttempt entry) => _serial(
    entry.profileID,
    () async {
      final owner = entry.profileID;
      if (_blocked.contains(owner)) return false;
      final current = entries(owner);
      if (_corrupt.contains(owner) ||
          PendingAuthAttempt.parse(entry.toJson(), owner) == null) {
        _uncertain.add(owner);
        return false;
      }
      final next = [
        for (final e in current)
          if (e.key != entry.key && !_beyondRetention(e)) e,
        entry,
      ];
      if (next.length > maxCount ||
          utf8.encode(jsonEncode(next.map((e) => e.toJson()).toList())).length >
              maxBytes) {
        _uncertain.add(owner);
        return false;
      }
      _cache[owner] = next;
      return _write(owner, next);
    },
  );

  Future<bool> remove(PendingAuthAttempt entry) =>
      _serial(entry.profileID, () async {
        final owner = entry.profileID;
        final next = entries(owner).where((e) => e.key != entry.key).toList();
        final saved = await _write(owner, next);
        // Do not overwrite a concurrently admitted start with an old snapshot.
        if (saved && !_blocked.contains(owner)) {
          _cache[owner] = (_cache[owner] ?? [])
              .where((e) => e.key != entry.key)
              .toList();
        }
        return saved;
      });

  /// Passive local retention maintenance; no server calls. Failed pruning
  /// retains the old memory records so cleanup is never falsely reported.
  Future<bool> prune(String owner) => _serial(owner, () async {
    final current = entries(owner);
    if (_blocked.contains(owner) || _corrupt.contains(owner)) return false;
    final next = current.where((e) => !_beyondRetention(e)).toList();
    if (next.length == current.length) return true;
    if (!await _write(owner, next)) return false;
    if (!_blocked.contains(owner)) _cache[owner] = next;
    return true;
  });

  Future<bool> retry(String owner) => _serial(owner, () async {
    final current = entries(owner);
    if (_blocked.contains(owner) || _corrupt.contains(owner)) return false;
    return _write(owner, current);
  });

  Future<bool> _write(String owner, List<PendingAuthAttempt> next) async {
    final snapshot = jsonEncode(next.map((e) => e.toJson()).toList());
    if (_blocked.contains(owner)) return false;
    try {
      final ok = await preferences.setString('$prefix$owner', snapshot);
      if (ok) {
        _uncertain.remove(owner);
      } else {
        _uncertain.add(owner);
        // SharedPreferences updates its cache before the platform confirms a
        // write. Restore the backend view without discarding OUR recovery copy.
        // Otherwise a reconstructed controller can mistake a refused removal
        // for success merely because it shares this preferences instance.
        try {
          await preferences.reload();
        } catch (_) {}
      }
      return ok;
    } catch (_) {
      _uncertain.add(owner);
      try {
        await preferences.reload();
      } catch (_) {}
      return false;
    }
  }

  Future<bool> _serial(String owner, Future<bool> Function() action) {
    final operation = (_writes[owner] ?? Future<void>.value())
        .then((_) => action())
        .catchError((Object _) {
          _uncertain.add(owner);
          return false;
        });
    _writes[owner] = operation.then<void>((_) {});
    return operation;
  }

  void block(String owner) {
    _blocked.add(owner);
  }

  Future<void> drain(String owner) => _writes[owner] ?? Future<void>.value();
  void forget(String owner) {
    _cache.remove(owner);
    _uncertain.remove(owner);
    _corrupt.remove(owner);
    // Keep blocked for this store's lifetime, including failed deletion.
    // A new controller may recover any metadata whose deletion was refused.
  }
}
