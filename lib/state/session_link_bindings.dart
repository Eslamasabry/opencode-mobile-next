import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/session_address_link.dart';
import '../ui/kit/kit_redact.dart';

/// Verified installation metadata only. It grants no authority to a session.
class SessionLinkBinding {
  const SessionLinkBinding({
    required this.origin,
    required this.instanceId,
    required this.verifiedAt,
  });

  final String origin;
  final String instanceId;
  final DateTime verifiedAt;
}

/// Shared per-profile writer. The deletion transaction closes admission and
/// drains this owner before sweeping its key. Closed facades never reopen.
class SessionLinkBindings extends ChangeNotifier {
  SessionLinkBindings._(this._prefs, this._profileId);

  static final _owners = Expando<Map<String, SessionLinkBindings>>();
  static final _closed = Expando<Set<String>>();
  static final _closeEpochs = Expando<Map<String, int>>();

  static SessionLinkBindings forProfile(
    SharedPreferences prefs,
    String profileId,
  ) {
    if (RegExp(r'^[a-zA-Z0-9_-]{1,128}$').firstMatch(profileId)?.end !=
            profileId.length ||
        KitRedact.text(profileId) != profileId) {
      throw const SessionAddressFailure(SessionAddressFailureCode.invalidLink);
    }
    if ((_closed[prefs]?.contains(profileId) ?? false) ||
        !_hasProfile(prefs, profileId)) {
      throw const SessionAddressFailure(
        SessionAddressFailureCode.profileMissing,
      );
    }
    return (_owners[prefs] ??= {}).putIfAbsent(
      profileId,
      () => SessionLinkBindings._(prefs, profileId),
    );
  }

  static bool _hasProfile(SharedPreferences prefs, String id) {
    try {
      final raw = prefs.getString('oc.profiles');
      final rows = raw == null ? null : jsonDecode(raw);
      return rows is List && rows.any((row) => row is Map && row['id'] == id);
    } catch (_) {
      return false;
    }
  }

  final SharedPreferences _prefs;
  final String _profileId;
  Future<void> _tail = Future<void>.value();
  bool _admitted = true;
  bool _storageAvailable = true;
  int _revision = 0;

  String get _key => 'oc.sessionLinkBinding.$_profileId';
  int get revision => _revision;
  bool get isAvailable =>
      _admitted && _storageAvailable && _hasProfile(_prefs, _profileId);

  void _checkAdmission() {
    if (!_admitted || !_hasProfile(_prefs, _profileId)) {
      throw const SessionAddressFailure(
        SessionAddressFailureCode.profileMissing,
      );
    }
    if (!_storageAvailable) {
      throw const SessionAddressFailure(SessionAddressFailureCode.storage);
    }
  }

  static SessionLinkBinding _validated(SessionLinkBinding binding) {
    final origin = SessionAddressLink.normalizeOrigin(binding.origin);
    if (!SessionAddressLink.validInstance(binding.instanceId) ||
        KitRedact.text(binding.instanceId) != binding.instanceId ||
        binding.verifiedAt.millisecondsSinceEpoch < 0) {
      throw const SessionAddressFailure(SessionAddressFailureCode.invalidLink);
    }
    return SessionLinkBinding(
      origin: origin,
      instanceId: binding.instanceId.toLowerCase(),
      verifiedAt: binding.verifiedAt.toUtc(),
    );
  }

  SessionLinkBinding? read() {
    _checkAdmission();
    try {
      final raw = _prefs.getString(_key);
      if (raw == null) return null;
      if (raw.length > 2048) throw const FormatException();
      final json = jsonDecode(raw);
      if (json is! Map ||
          json.length != 4 ||
          json['schemaVersion'] != 1 ||
          json['origin'] is! String ||
          json['instanceId'] is! String ||
          json['verifiedAt'] is! int) {
        throw const FormatException();
      }
      return _validated(
        SessionLinkBinding(
          origin: json['origin'] as String,
          instanceId: json['instanceId'] as String,
          verifiedAt: DateTime.fromMillisecondsSinceEpoch(
            json['verifiedAt'] as int,
            isUtc: true,
          ),
        ),
      );
    } catch (_) {
      throw const SessionAddressFailure(SessionAddressFailureCode.storage);
    }
  }

  Future<void> save(SessionLinkBinding binding) {
    try {
      _checkAdmission();
      final checked = _validated(binding);
      final next = _tail.then((_) => _save(checked));
      _tail = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
      return next;
    } catch (error, stack) {
      return Future<void>.error(error, stack);
    }
  }

  Future<void> _save(SessionLinkBinding binding) async {
    _checkAdmission();
    try {
      // Confirm membership and the previous binding against durable storage;
      // never replace a different installation using an optimistic cache.
      await _prefs.reload();
    } catch (_) {
      _storageAvailable = false;
      throw const SessionAddressFailure(SessionAddressFailureCode.storage);
    }
    _checkAdmission();
    final previous = read();
    if (previous != null &&
        (previous.origin != binding.origin ||
            previous.instanceId != binding.instanceId)) {
      throw const SessionAddressFailure(
        SessionAddressFailureCode.instanceMismatch,
      );
    }
    final encoded = jsonEncode({
      'schemaVersion': 1,
      'origin': binding.origin,
      'instanceId': binding.instanceId,
      'verifiedAt': binding.verifiedAt.millisecondsSinceEpoch,
    });
    try {
      if (!await _prefs.setString(_key, encoded)) throw const FormatException();
      await _prefs.reload();
      if (_prefs.getString(_key) != encoded) throw const FormatException();
    } catch (_) {
      // SharedPreferences caches before the platform confirms persistence.
      // This facade stays unavailable even when best-effort reconciliation
      // succeeds; a failed reload must never expose the optimistic binding.
      _storageAvailable = false;
      try {
        await _prefs.reload();
      } catch (_) {}
      _revision++;
      notifyListeners();
      throw const SessionAddressFailure(SessionAddressFailureCode.storage);
    }
    _checkAdmission();
    _revision++;
    notifyListeners();
  }

  static Future<void> closeProfile(SharedPreferences prefs, String profileId) {
    (_closed[prefs] ??= {}).add(profileId);
    final epochs = _closeEpochs[prefs] ??= {};
    epochs[profileId] = (epochs[profileId] ?? 0) + 1;
    final owner = _owners[prefs]?[profileId];
    if (owner == null) return Future<void>.value();
    if (owner._admitted) {
      owner._admitted = false;
      owner._revision++;
      owner.notifyListeners();
    }
    return owner._tail;
  }

  /// Only the completed deletion-abort path may call this. Verify durable
  /// membership before allowing a new facade; existing references stay closed.
  static Future<void> cancelDeletion(
    SharedPreferences prefs,
    String profileId,
  ) async {
    if (!(_closed[prefs]?.contains(profileId) ?? false)) return;
    final previous = _owners[prefs]?[profileId];
    final epoch = _closeEpochs[prefs]?[profileId];
    await previous?._tail;
    try {
      await prefs.reload();
      if (!_hasProfile(prefs, profileId) ||
          _closeEpochs[prefs]?[profileId] != epoch) {
        return;
      }
      if (!identical(_owners[prefs]?[profileId], previous)) return;
      _owners[prefs]?.remove(profileId);
      _closed[prefs]?.remove(profileId);
    } catch (_) {
      // An uncertain profile membership never reopens a writer.
    }
  }
}
