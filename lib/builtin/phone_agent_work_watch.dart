import 'dart:async';
import 'dart:math';

import 'builtin_linux.dart';

/// One bounded CPU lease for all local agent work owned by a profile.
/// Unknown work does not start a lease or end an already observed busy run.
/// Native monotonic lifetime and exact helper ownership remain authoritative.
class PhoneAgentWorkWatch {
  PhoneAgentWorkWatch({
    required Future<BuiltinWorkLeaseStatus> Function(
      String profileId,
      String leaseId,
      bool on,
      Duration hold,
    )
    hold,
    DateTime Function()? now,
  }) : _hold = hold,
       _now = now ?? DateTime.now;

  static const _ttl = Duration(minutes: 15);
  static const _renewEvery = Duration(minutes: 5);
  static const _ceiling = Duration(hours: 6);
  final Future<BuiltinWorkLeaseStatus> Function(
    String profileId,
    String leaseId,
    bool on,
    Duration hold,
  )
  _hold;
  final DateTime Function() _now;
  Future<void> _chain = Future.value();
  _AgentWorkRun? _run;
  bool _disposed = false;

  void observe({required String? profileId, required bool? busy}) {
    if (_disposed) return;
    final owner = profileId?.isNotEmpty == true ? profileId : null;
    if (_run != null && _run!.profileId != owner) _endRun();
    if (owner == null || busy == false) {
      _endRun();
      return;
    }
    final existing = _run;
    if (existing != null) {
      if (!existing.closed) _remaining(existing);
      return;
    }
    if (busy != true) return;
    final random = Random.secure();
    final id =
        'agent.${List.generate(16, (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0')).join()}';
    final started = _readNow();
    final run = _run = _AgentWorkRun(owner, id, started);
    if (started == null) {
      run.closed = true;
      return;
    }
    _queueOn(run);
    run.timer = Timer.periodic(_renewEvery, (_) {
      if (_current(run) && _remaining(run) != null) _queueOn(run);
    });
  }

  int? _readNow() {
    try {
      final value = _now().microsecondsSinceEpoch;
      return value < 0 ? null : value;
    } catch (_) {
      return null;
    }
  }

  Duration? _remaining(_AgentWorkRun run) {
    final current = _readNow();
    if (current == null ||
        current < run.lastNow! ||
        current - run.started! >= _ceiling.inMicroseconds) {
      _close(run);
      _queueCpuClose(run);
      return null;
    }
    run.lastNow = current;
    return Duration(
      microseconds: _ceiling.inMicroseconds - (current - run.started!),
    );
  }

  bool _retained(_AgentWorkRun run) => !_disposed && identical(_run, run);

  bool _current(_AgentWorkRun run) => _retained(run) && !run.closed;

  void _queueOn(_AgentWorkRun run) {
    _chain = _chain.then((_) async {
      if (!_current(run)) return;
      final remaining = _remaining(run);
      if (remaining == null) return;
      BuiltinWorkLeaseStatus status;
      try {
        status = await _hold(
          run.profileId,
          run.id,
          true,
          remaining < _ttl ? remaining : _ttl,
        );
      } catch (_) {
        // A partial handoff may own CPU, but failure does not prove idle.
        if (_retained(run)) _close(run);
        await _cpuClose(run);
        return;
      }
      if (!_retained(run)) {
        await _off(run);
        return;
      }
      if (run.closed || _remaining(run) == null) {
        await _cpuClose(run);
        return;
      }
      if (status.capped) {
        // Keep the exhausted logical name until actual idle/owner release.
        _close(run);
      } else if (!status.held) {
        _close(run);
        await _cpuClose(run);
      }
    });
  }

  void _close(_AgentWorkRun run) {
    run.closed = true;
    run.timer?.cancel();
    run.timer = null;
  }

  void _queueOff(_AgentWorkRun run) {
    // No current-intent guard: a newer owner/run never erases an owed off.
    _chain = _chain.then((_) => _off(run));
  }

  void _queueCpuClose(_AgentWorkRun run) {
    _chain = _chain.then((_) => _cpuClose(run));
  }

  Future<void> _cpuClose(_AgentWorkRun run) async {
    if (!_retained(run)) {
      await _off(run);
      return;
    }
    try {
      // Close CPU only; retain an existing native logical busy key. This
      // cannot acquire a token or reset its native lifetime boundary.
      await _hold(run.profileId, run.id, true, Duration.zero);
    } catch (_) {
      // Native expiry bounds CPU even if this close cannot reach the channel.
    }
    if (!_retained(run)) await _off(run);
  }

  Future<void> _off(_AgentWorkRun run) async {
    try {
      await _hold(run.profileId, run.id, false, _ttl);
    } catch (_) {
      // Native TTL/cap still bounds a token when its release is unavailable.
    }
  }

  void _endRun() {
    final run = _run;
    if (run == null) return;
    _run = null;
    _close(run);
    _queueOff(run);
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _endRun();
  }
}

class _AgentWorkRun {
  _AgentWorkRun(this.profileId, this.id, this.started) : lastNow = started;
  final String profileId;
  final String id;
  final int? started;
  int? lastNow;
  bool closed = false;
  Timer? timer;
}
