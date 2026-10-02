import 'dart:async';
import 'dart:convert';
import 'dart:math';

import '../domain/byo_host.dart';
import 'profiles.dart';

/// A phone owns one durable adoption job. No SSH/provider text is UI state.
/// A controller's lifetime owns its tunnel, not the remote service or sessions.
class ByoHostController {
  ByoHostController({
    required this.profileId,
    required this.runner,
    required this.store,
    this.bundle,
    this.clearLocalData,
    this.enabled = byoHostBuildEnabled,
  }) {
    if (!byoHostSafeId(profileId)) {
      throw const ByoHostFailure(ByoHostFailureCode.invalidTarget);
    }
  }

  final String profileId;
  final ByoHostSshRunner runner;
  final ByoHostStore store;
  final ByoHostBundle? bundle;

  /// Full shared-data cascade supplied by the app composition. Throws on refusal.
  final Future<void> Function(String profileId)? clearLocalData;
  final bool enabled;
  final _events = StreamController<ByoHostSnapshot>.broadcast(sync: true);
  ByoHostSnapshot _snapshot = const ByoHostSnapshot(phase: ByoHostPhase.idle);
  ByoHostSnapshot get snapshot => _snapshot;
  Stream<ByoHostSnapshot> get changes => _events.stream;
  ByoHostTunnel? _tunnel;
  ByoHostSecrets? _secrets;
  ByoHostTarget? _previewTarget;
  ByoHostKey? _previewKey;
  bool _busy = false;
  bool _disposed = false;

  /// Ephemeral profile for the existing OpenCode1 ServerGateway factory.
  /// Never upsert this credential/loopback port in ProfileStore. Persist only
  /// the BYO record; resume verifies a fresh tunnel before returning a profile.
  ServerProfile connectedProfile({required String name}) {
    final tunnel = _tunnel;
    final secrets = _secrets;
    if (snapshot.phase != ByoHostPhase.ready ||
        tunnel == null ||
        secrets == null) {
      throw const ByoHostFailure(ByoHostFailureCode.transport);
    }
    return ServerProfile(
      id: profileId,
      name: name,
      transientTransport: true,
      baseUrl: 'http://127.0.0.1:${tunnel.localPort}',
      username: profileId,
      password: secrets.deviceToken,
      serverVersion: snapshot.record?.openCodeVersion,
    );
  }

  void _emit(
    ByoHostPhase phase, {
    ByoHostRecord? record,
    ByoHostKey? key,
    ByoHostFailure? failure,
  }) {
    _snapshot = ByoHostSnapshot(
      phase: phase,
      record: record ?? snapshot.record,
      hostKey: key,
      failure: failure,
    );
    if (!_events.isClosed) _events.add(_snapshot);
  }

  Future<T> _action<T>(Future<T> Function() action) async {
    if (_busy) throw const ByoHostFailure(ByoHostFailureCode.busy);
    if (!enabled || _disposed) {
      throw const ByoHostFailure(ByoHostFailureCode.disabled);
    }
    _busy = true;
    try {
      return await action();
    } catch (error) {
      final safe = error is ByoHostFailure
          ? error
          : const ByoHostFailure(ByoHostFailureCode.transport);
      _emit(
        safe.code == ByoHostFailureCode.revocationFailed && _tunnel != null
            ? ByoHostPhase.ready
            : ByoHostPhase.failed,
        failure: safe,
      );
      throw safe;
    } finally {
      _busy = false;
    }
  }

  /// Keyscan supplies a candidate, not trust. User must verify out of band.
  Future<ByoHostKey> inspect(ByoHostTarget target) => _action(() async {
    _emit(ByoHostPhase.checking);
    if (!await runner.available()) {
      throw const ByoHostFailure(ByoHostFailureCode.unavailable);
    }
    final existing = await store.readRecord(profileId);
    if (existing != null) throw const ByoHostFailure(ByoHostFailureCode.busy);
    final key = await runner.inspectKey(target);
    _previewTarget = target;
    _previewKey = key;
    _emit(ByoHostPhase.needsTrust, key: key);
    return key;
  });

  /// Public enrollment for owner-managed key-only SSH accounts. Reserve the
  /// journal before Keystore generation so app loss never loses the profile ID.
  Future<ByoHostIdentity> prepareSshIdentity({
    required String verifiedFingerprint,
  }) => _action(() async {
    final target = _previewTarget;
    final key = _previewKey;
    if (target == null ||
        key == null ||
        key.fingerprint != verifiedFingerprint) {
      throw const ByoHostFailure(ByoHostFailureCode.needsTrust);
    }
    final existing = await store.readRecord(profileId);
    if (existing != null && existing.phase != ByoHostPhase.needsTrust) {
      throw const ByoHostFailure(ByoHostFailureCode.busy);
    }
    final reservation = ByoHostRecord(
      profileId: profileId,
      target: target,
      hostKey: key,
      phase: ByoHostPhase.needsTrust,
    );
    await _checkKey(reservation);
    await store.saveRecord(reservation);
    final identity = await runner.generateIdentity(profileId);
    _emit(ByoHostPhase.needsTrust, record: reservation, key: key);
    return identity; // public key and alias only
  });

  /// Persist the exact device tuple before any remote mutation. A killed app
  /// can retry pairing the same tuple; it never invents a second device.
  Future<void> adopt({
    required String verifiedFingerprint,
    required ByoHostLogin login,
  }) async {
    try {
      await _action(() async {
        final target = _previewTarget;
        final key = _previewKey;
        if (target == null ||
            key == null ||
            verifiedFingerprint != key.fingerprint) {
          throw const ByoHostFailure(ByoHostFailureCode.needsTrust);
        }
        final package = bundle;
        if (package == null) {
          throw const ByoHostFailure(ByoHostFailureCode.bundleUnavailable);
        }
        final existing = await store.readRecord(profileId);
        if (existing != null &&
            (existing.phase != ByoHostPhase.needsTrust ||
                existing.hostKey.publicKey != key.publicKey ||
                jsonEncode(existing.target.toJson()) !=
                    jsonEncode(target.toJson()))) {
          throw const ByoHostFailure(ByoHostFailureCode.busy);
        }
        final identity = await runner.generateIdentity(profileId);
        final random = Random.secure();
        final token = base64UrlEncode(
          List<int>.generate(32, (_) => random.nextInt(256)),
        ).replaceAll('=', '');
        final secrets = ByoHostSecrets(identity: identity, deviceToken: token);
        await store.saveSecrets(profileId, secrets);
        final record = ByoHostRecord(
          profileId: profileId,
          target: target,
          hostKey: key,
          phase: ByoHostPhase.installing,
        );
        await store.saveRecord(record);
        _secrets = secrets;
        _emit(ByoHostPhase.installing, record: record);
        await _install(record, secrets, login, package);
      });
    } finally {
      login.consume();
    }
  }

  Future<void> _install(
    ByoHostRecord record,
    ByoHostSecrets secrets,
    ByoHostLogin login,
    ByoHostBundle package,
  ) async {
    await _checkKey(record);
    final descriptor = await runner.install(
      profileId: profileId,
      target: record.target,
      hostKey: record.hostKey,
      login: login,
      identity: secrets.identity,
      deviceToken: secrets.deviceToken,
      bundle: package,
    );
    _validateDescriptor(descriptor);
    if (descriptor.bundleVersion != package.version ||
        descriptor.openCodeVersion != package.openCodeVersion) {
      throw const ByoHostFailure(ByoHostFailureCode.identityChanged);
    }
    final installed = _record(
      record,
      ByoHostPhase.disconnected,
      descriptor: descriptor,
    );
    await store.saveRecord(installed);
    _emit(ByoHostPhase.disconnected, record: installed);
    await _connect(installed, secrets);
  }

  /// No initial admin password/private key survives. Interrupted installation
  /// requires fresh one-shot login; ordinary reconnect uses the restricted key.
  Future<void> resume({ByoHostLogin? login}) async {
    try {
      await _action(() async {
        final record = await store.readRecord(profileId);
        final secrets = await store.readSecrets(profileId);
        if (record != null &&
            record.phase == ByoHostPhase.needsTrust &&
            secrets == null) {
          await _checkKey(record);
          _previewTarget = record.target;
          _previewKey = record.hostKey;
          _emit(ByoHostPhase.needsTrust, record: record, key: record.hostKey);
          return;
        }
        if (record == null || secrets == null || record.revoked) {
          throw const ByoHostFailure(ByoHostFailureCode.storage);
        }
        _secrets = secrets;
        _emit(ByoHostPhase.disconnected, record: record);
        if (!await runner.available()) {
          throw const ByoHostFailure(ByoHostFailureCode.unavailable);
        }
        if (record.remotePort == null) {
          if (login == null) {
            throw const ByoHostFailure(ByoHostFailureCode.uncertain);
          }
          final package = bundle;
          if (package == null) {
            throw const ByoHostFailure(ByoHostFailureCode.bundleUnavailable);
          }
          await _install(record, secrets, login, package);
        } else {
          await _connect(record, secrets);
        }
      });
    } finally {
      login?.consume();
    }
  }

  Future<void> _checkKey(ByoHostRecord record) async {
    final key = await runner.inspectKey(record.target);
    if (key.publicKey != record.hostKey.publicKey ||
        key.fingerprint != record.hostKey.fingerprint) {
      throw const ByoHostFailure(ByoHostFailureCode.hostKeyChanged);
    }
  }

  void _validateDescriptor(ByoHostDescriptor descriptor) {
    if (!RegExp(r'^[a-zA-Z0-9_-]{1,64}$').hasMatch(descriptor.hostId) ||
        descriptor.port < 1024 ||
        descriptor.port > 65535 ||
        !RegExp(r'^\d+\.\d+\.\d+$').hasMatch(descriptor.bundleVersion) ||
        !RegExp(r'^\d+\.\d+\.\d+$').hasMatch(descriptor.openCodeVersion)) {
      throw const ByoHostFailure(ByoHostFailureCode.protocol);
    }
  }

  Future<void> _connect(ByoHostRecord record, ByoHostSecrets secrets) async {
    await _checkKey(record);
    _emit(ByoHostPhase.connecting, record: record);
    await _closeTunnel();
    final tunnel = await runner.forward(
      profileId: profileId,
      target: record.target,
      hostKey: record.hostKey,
      identity: secrets.identity,
      remotePort: record.remotePort!,
    );
    try {
      final descriptor = await runner.describe(
        tunnel: tunnel,
        deviceId: profileId,
        deviceToken: secrets.deviceToken,
      );
      _validateDescriptor(descriptor);
      if (descriptor.hostId != record.hostId ||
          descriptor.port != record.remotePort ||
          descriptor.bundleVersion != record.bundleVersion ||
          descriptor.openCodeVersion != record.openCodeVersion) {
        throw const ByoHostFailure(ByoHostFailureCode.identityChanged);
      }
      // Persist no loopback port; each process gets a newly verified lease.
      final ready = _record(record, ByoHostPhase.disconnected);
      await store.saveRecord(ready);
      _tunnel = tunnel;
      _secrets = secrets;
      _emit(ByoHostPhase.ready, record: ready);
    } catch (_) {
      await tunnel.close();
      rethrow;
    }
  }

  ByoHostRecord _record(
    ByoHostRecord record,
    ByoHostPhase phase, {
    ByoHostDescriptor? descriptor,
    bool? revoked,
  }) => ByoHostRecord(
    profileId: record.profileId,
    target: record.target,
    hostKey: record.hostKey,
    phase: phase,
    hostId: descriptor?.hostId ?? record.hostId,
    bundleVersion: descriptor?.bundleVersion ?? record.bundleVersion,
    openCodeVersion: descriptor?.openCodeVersion ?? record.openCodeVersion,
    remotePort: descriptor?.port ?? record.remotePort,
    revoked: revoked ?? record.revoked,
  );

  /// Revocation receipt is saved before local erasure. Any failed/uncertain
  /// revocation keeps the record, credentials and current connection available.
  Future<void> remove({required bool revokeAccess}) => _action(() async {
    final record = await store.readRecord(profileId);
    if (record == null) {
      await _closeTunnel();
      await runner.cleanup(profileId);
      await clearLocalData?.call(profileId);
      await store.remove(
        profileId,
      ); // includes orphan envelope after failed save
      _secrets = null;
      _snapshot = const ByoHostSnapshot(phase: ByoHostPhase.removed);
      _events.add(_snapshot);
      return;
    }
    if (revokeAccess && !record.revoked) {
      final secrets = _secrets ?? await store.readSecrets(profileId);
      if (secrets == null) {
        throw const ByoHostFailure(ByoHostFailureCode.storage);
      }
      try {
        if (_tunnel == null) await _connect(record, secrets);
        if (!await runner.revoke(
          tunnel: _tunnel!,
          deviceId: profileId,
          deviceToken: secrets.deviceToken,
        )) {
          throw const ByoHostFailure(ByoHostFailureCode.revocationFailed);
        }
      } catch (_) {
        throw const ByoHostFailure(ByoHostFailureCode.revocationFailed);
      }
      await store.saveRecord(
        _record(record, ByoHostPhase.removing, revoked: true),
      );
    }
    _emit(ByoHostPhase.removing, record: record);
    await _closeTunnel();
    await runner.cleanup(profileId);
    try {
      await clearLocalData?.call(profileId);
    } catch (_) {
      // The shared preference cascade may have swept our record before failing.
      // Restore a nonsecret cleanup journal so app restart can still find it.
      final pending = await store.readRecord(profileId);
      await store.saveRecord(
        pending ??
            _record(
              record,
              ByoHostPhase.removing,
              revoked: revokeAccess || record.revoked,
            ),
      );
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
    await store.remove(profileId);
    _secrets = null;
    _previewTarget = null;
    _previewKey = null;
    _snapshot = const ByoHostSnapshot(phase: ByoHostPhase.removed);
    _events.add(_snapshot);
  });

  Future<void> _closeTunnel() async {
    final previous = _tunnel;
    _tunnel = null;
    await previous?.close();
  }

  /// Close local transports only. Never uninstall/stop the persistent host.
  Future<void> dispose() async {
    if (_busy) throw const ByoHostFailure(ByoHostFailureCode.busy);
    _disposed = true;
    await _closeTunnel();
    _secrets = null;
    await _events.close();
  }
}
