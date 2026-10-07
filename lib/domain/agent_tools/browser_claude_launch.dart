import 'dart:async';
import 'dart:convert';

/// IDs captured by the owning connection, never supplied by a guest tool.
final class BrowserEnrollmentTarget {
  const BrowserEnrollmentTarget({
    required this.profileId,
    required this.sourceId,
    required this.sessionId,
    required this.daemonAgentId,
  });

  final String profileId, sourceId, sessionId, daemonAgentId;

  bool get valid => [profileId, sourceId, sessionId, daemonAgentId].every(
    (id) =>
        id.isNotEmpty &&
        utf8.encode(id).length <= 128 &&
        !id.runes.any((r) => r < 0x20 || r == 0x7f),
  );

  (String, String, String) get key => (profileId, sourceId, sessionId);

  bool matches(BrowserEnrollmentTarget other) => this == other;

  @override
  bool operator ==(Object other) =>
      other is BrowserEnrollmentTarget &&
      key == other.key &&
      daemonAgentId == other.daemonAgentId;

  @override
  int get hashCode => Object.hash(key, daemonAgentId);
}

/// Opaque, non-secret generation metadata. This does not grant a browser lease.
final class BrowserLaunchReservation {
  const BrowserLaunchReservation({
    required this.id,
    required this.launchId,
    required this.target,
  });

  final String id, launchId;
  final BrowserEnrollmentTarget target;

  bool matches(BrowserEnrollmentTarget wanted) =>
      id.isNotEmpty &&
      id.length <= 128 &&
      RegExp(r'^[a-zA-Z0-9._-]+$').hasMatch(id) &&
      RegExp(r'^[a-f0-9]{32}$').hasMatch(launchId) &&
      target.valid &&
      target.key == wanted.key &&
      target.daemonAgentId == wanted.daemonAgentId;

  (String, String, BrowserEnrollmentTarget) get _generation =>
      (id, launchId, target);
}

/// BE installs an adapter only after its runtime qualification succeeds.
/// Revoke must invalidate this exact generation before cleaning up resources.
abstract interface class BrowserClaudeLaunchPort {
  Future<BrowserLaunchReservation?> beforeBrowserClaudeLaunch({
    required String profileId,
    required String sourceId,
    required String sessionId,
    required String daemonAgentId,
  });

  Future<void> revokeBrowserClaudeLaunch(BrowserLaunchReservation reservation);
}

final class NoopBrowserClaudeLaunchPort implements BrowserClaudeLaunchPort {
  const NoopBrowserClaudeLaunchPort();

  @override
  Future<BrowserLaunchReservation?> beforeBrowserClaudeLaunch({
    required String profileId,
    required String sourceId,
    required String sessionId,
    required String daemonAgentId,
  }) async => null;

  @override
  Future<void> revokeBrowserClaudeLaunch(
    BrowserLaunchReservation reservation,
  ) async {}
}

final class _Attempt {
  _Attempt(this.target);
  final BrowserEnrollmentTarget target;
  bool cancelled = false;
  BrowserLaunchReservation? reservation;
  late Future<BrowserLaunchReservation?> future;
}

typedef _Generation = (String, String, BrowserEnrollmentTarget);

/// Shared by feed and chat gateways belonging to one trusted host owner.
/// Gateways pass canonical daemon conversation IDs, rather than local aliases.
final class BrowserClaudeLaunchRegistry {
  BrowserClaudeLaunchRegistry({
    BrowserClaudeLaunchPort port = const NoopBrowserClaudeLaunchPort(),
    Duration timeout = const Duration(seconds: 2),
  }) : _port = port,
       _timeout = timeout {
    if (timeout <= Duration.zero || timeout > const Duration(seconds: 2)) {
      throw ArgumentError.value(timeout, 'timeout');
    }
  }

  final BrowserClaudeLaunchPort _port;
  final Duration _timeout;
  final _current = <(String, String, String), _Attempt>{};
  final _pendingNative = <_Attempt>{};
  final _cleanup = <_Generation, BrowserLaunchReservation>{};
  final _retiring = <_Generation, Future<bool>>{};
  final _retired = <_Generation>{};
  final _seenIds = <String>{};
  final _requested = <(String, String, String)>{};
  bool _closed = false;
  bool _quarantined = false;

  /// Transient browser choice shared by feed and chat gateways. This is intent,
  /// not enrollment, and survives abort or resume of the same conversation.
  bool isRequested({
    required String profileId,
    required String sourceId,
    required String sessionId,
  }) => !_closed && _requested.contains((profileId, sourceId, sessionId));

  bool hasRequestedSource({
    required String profileId,
    required String sourceId,
  }) =>
      !_closed &&
      _requested.any((key) => key.$1 == profileId && key.$2 == sourceId);

  /// Admission is bounded independently from the eight native operation slots.
  /// Disabling always succeeds, including after shutdown or quarantine.
  bool setRequested({
    required String profileId,
    required String sourceId,
    required String sessionId,
    required bool requested,
  }) {
    final key = (profileId, sourceId, sessionId);
    if (!requested) {
      _requested.remove(key);
      return true;
    }
    if (_closed || _quarantined || !_validRequestKey(key)) return false;
    if (_requested.contains(key)) return true;
    if (_requested.length >= 128) return false;
    _requested.add(key);
    return true;
  }

  bool _validRequestKey((String, String, String) key) =>
      BrowserEnrollmentTarget(
        profileId: key.$1,
        sourceId: key.$2,
        sessionId: key.$3,
        daemonAgentId: 'request',
      ).valid;

  /// Transfer a draft/local alias choice once the trusted daemon ID is known.
  void moveRequest({
    required String profileId,
    required String sourceId,
    required String oldSessionId,
    required String newSessionId,
  }) {
    final oldKey = (profileId, sourceId, oldSessionId);
    final newKey = (profileId, sourceId, newSessionId);
    if (_closed ||
        _quarantined ||
        !_requested.contains(oldKey) ||
        !_validRequestKey(newKey)) {
      return;
    }
    _requested.remove(oldKey);
    _requested.add(newKey);
  }

  Future<BrowserLaunchReservation?> reserve(BrowserEnrollmentTarget target) {
    if (_closed || _quarantined || !target.valid) return Future.value(null);
    final previous = _current[target.key];
    if (previous != null && previous.target == target && !previous.cancelled) {
      return previous.future;
    }
    // Retained cleanup and timed-out native requests still consume capacity.
    final active = _current.values.where((a) => a.reservation != null).length;
    if (active + _pendingNative.length + _cleanup.length >= 8) {
      return Future.value(null);
    }
    final attempt = _Attempt(target);
    _current[target.key] = attempt;
    if (previous != null) previous.cancelled = true;
    attempt.future = _reserve(attempt, previous);
    return attempt.future;
  }

  bool _isCurrent(_Attempt attempt) =>
      !_closed &&
      !_quarantined &&
      !attempt.cancelled &&
      identical(_current[attempt.target.key], attempt);

  /// Recheck after asynchronous work and immediately before sending a prompt.
  /// Another gateway may have revoked or replaced this generation meanwhile.
  bool isCurrent(BrowserLaunchReservation reservation) {
    final attempt = _current[reservation.target.key];
    return attempt != null &&
        _isCurrent(attempt) &&
        attempt.reservation?._generation == reservation._generation;
  }

  Future<BrowserLaunchReservation?> _reserve(
    _Attempt attempt,
    _Attempt? previous,
  ) async {
    try {
      final old = previous?.reservation;
      if (old != null && !await _retire(old)) return null;
      if (!_isCurrent(attempt)) return null;
      final active = _current.values.where((a) => a.reservation != null).length;
      if (active + _pendingNative.length + _cleanup.length >= 8) return null;
      _pendingNative.add(attempt);
      final target = attempt.target;
      final response = Future<BrowserLaunchReservation?>.sync(
        () => _port.beforeBrowserClaudeLaunch(
          profileId: target.profileId,
          sourceId: target.sourceId,
          sessionId: target.sessionId,
          daemonAgentId: target.daemonAgentId,
        ),
      );
      final received = response.then(
        (reservation) async {
          _pendingNative.remove(attempt);
          if (reservation == null) return null;
          final valid = reservation.matches(target);
          if (valid &&
              (!_seenIds.add(reservation.id) || _seenIds.length > 256)) {
            _quarantine();
          }
          if (!valid || !_isCurrent(attempt)) {
            await _retire(reservation);
            return null;
          }
          attempt.reservation = reservation;
          return reservation;
        },
        onError: (Object _, StackTrace _) {
          _pendingNative.remove(attempt);
          return null;
        },
      );
      return await received.timeout(_timeout);
    } catch (_) {
      // Native/provider/process wording is never exposed by this boundary.
      return null;
    } finally {
      if (attempt.reservation == null) {
        attempt.cancelled = true;
        if (identical(_current[attempt.target.key], attempt)) {
          _current.remove(attempt.target.key);
        }
      }
    }
  }

  void _quarantine() {
    if (_quarantined) return;
    _quarantined = true;
    final attempts = _current.values.toList();
    _current.clear();
    for (final attempt in attempts) {
      attempt.cancelled = true;
      final reservation = attempt.reservation;
      if (reservation != null) unawaited(_retire(reservation));
    }
  }

  Future<bool> _retire(BrowserLaunchReservation reservation) {
    final generation = reservation._generation;
    if (_retired.contains(generation)) return Future.value(true);
    final existing = _retiring[generation];
    if (existing != null) return existing;
    _cleanup[generation] = reservation;
    // Keep the operation registered until the underlying native future settles,
    // including after a timeout. A timed-out revoke cannot free a native slot.
    final native = Future<void>.sync(
      () => _port.revokeBrowserClaudeLaunch(reservation),
    );
    final settled = native.then(
      (_) {
        _retired.add(generation);
        _cleanup.remove(generation);
        _retiring.remove(generation);
        return true;
      },
      onError: (Object _, StackTrace _) {
        _retiring.remove(generation);
        _quarantine();
        return false;
      },
    );
    final bounded = settled.timeout(
      _timeout,
      onTimeout: () {
        _quarantine();
        return false;
      },
    );
    _retiring[generation] = bounded;
    return bounded;
  }

  Future<void> revokeSession({
    required String profileId,
    required String sourceId,
    required String sessionId,
  }) => _revokeWhere((t) => t.key == (profileId, sourceId, sessionId));

  Future<void> revokeSource({
    required String profileId,
    required String sourceId,
    bool clearRequests = true,
  }) {
    if (clearRequests) {
      _requested.removeWhere(
        (key) => key.$1 == profileId && key.$2 == sourceId,
      );
    }
    return _revokeWhere(
      (t) => t.profileId == profileId && t.sourceId == sourceId,
    );
  }

  Future<void> revokeProfile({required String profileId}) {
    _requested.removeWhere((key) => key.$1 == profileId);
    return _revokeWhere((t) => t.profileId == profileId);
  }

  Future<void> _revokeWhere(
    bool Function(BrowserEnrollmentTarget) matches,
  ) async {
    final reservations = <BrowserLaunchReservation>[];
    for (final attempt in _current.values.toList()) {
      if (!matches(attempt.target)) continue;
      attempt.cancelled = true;
      _current.remove(attempt.target.key);
      final reservation = attempt.reservation;
      if (reservation != null) reservations.add(reservation);
    }
    await Future.wait(reservations.map(_retire));
  }

  Future<void> close() async {
    _closed = true;
    _requested.clear();
    final reservations = <_Generation, BrowserLaunchReservation>{..._cleanup};
    for (final attempt in _current.values) {
      attempt.cancelled = true;
      final reservation = attempt.reservation;
      if (reservation != null) {
        reservations[reservation._generation] = reservation;
      }
    }
    _current.clear();
    await Future.wait(reservations.values.map(_retire));
  }
}
