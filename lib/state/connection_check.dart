import 'package:flutter/foundation.dart';

import '../domain/server_probe.dart';
import 'profiles.dart';
import 'server_trust_probe.dart';

/// The five things a person can ask about a server, in the order a failure
/// would have to be fixed.
enum ConnectionCheckStep { address, signIn, version, liveUpdates, speed }

/// How one step ended. [warn] still works but deserves a hint; [skipped]
/// means an earlier step failed or the step needs a live connection.
enum ConnectionCheckState { waiting, running, pass, warn, fail, skipped }

/// Why a step ended the way it did. The screen maps each to plain words and
/// a fix hint; server text and the address never travel in a reason.
enum ConnectionCheckReason {
  ok,
  addressUnreachable,
  addressNotOpenCode,
  needsPassword,
  passwordRejected,
  versionUnsupported,
  versionNotReady,
  streamNotChecked,
  streamConnecting,
  streamDown,
  slow,
  earlierFailed,
}

class ConnectionCheckResult {
  const ConnectionCheckResult(this.state, this.reason, {this.value});

  const ConnectionCheckResult.waiting()
    : state = ConnectionCheckState.waiting,
      reason = ConnectionCheckReason.ok,
      value = null;

  final ConnectionCheckState state;
  final ConnectionCheckReason reason;

  /// The version for the version step, the milliseconds for the speed step.
  final Object? value;
}

/// A round trip slower than this is worth a hint.
const connectionSlowAfterMs = 1500;

/// The live-update state the check can read without opening a second stream.
enum ConnectionStreamView { notConnected, connecting, live, down }

/// Runs the manual connection check for one saved server. Nothing here runs
/// by itself: [run] is called when the person taps the row, and it makes one
/// probe request (the same one Save uses).
class ConnectionCheck extends ChangeNotifier {
  ConnectionCheck({
    required this.profile,
    required this.streamView,
    Stopwatch Function()? stopwatch,
  }) : _stopwatch = stopwatch ?? Stopwatch.new;

  final ServerProfile profile;

  /// Read when the live-update step is reached.
  final ConnectionStreamView Function() streamView;
  final Stopwatch Function() _stopwatch;

  bool _running = false;
  bool get running => _running;

  final Map<ConnectionCheckStep, ConnectionCheckResult> _results = {
    for (final step in ConnectionCheckStep.values)
      step: const ConnectionCheckResult.waiting(),
  };

  ConnectionCheckResult result(ConnectionCheckStep step) => _results[step]!;

  bool get finished =>
      !_running &&
      _results.values.any((r) => r.state != ConnectionCheckState.waiting);

  bool get allPassed => _results.values.every(
    (r) =>
        r.state == ConnectionCheckState.pass ||
        r.state == ConnectionCheckState.warn,
  );

  void _set(
    ConnectionCheckStep step,
    ConnectionCheckState state,
    ConnectionCheckReason reason, {
    Object? value,
  }) {
    _results[step] = ConnectionCheckResult(state, reason, value: value);
    notifyListeners();
  }

  void _skipFrom(ConnectionCheckStep first) {
    for (final step in ConnectionCheckStep.values) {
      if (step.index >= first.index) {
        _results[step] = const ConnectionCheckResult(
          ConnectionCheckState.skipped,
          ConnectionCheckReason.earlierFailed,
        );
      }
    }
    notifyListeners();
  }

  Future<void> run() async {
    if (_running) return;
    _running = true;
    for (final step in ConnectionCheckStep.values) {
      _results[step] = const ConnectionCheckResult.waiting();
    }
    _set(
      ConnectionCheckStep.address,
      ConnectionCheckState.running,
      ConnectionCheckReason.ok,
    );
    final timer = _stopwatch()..start();
    ServerProbeResult probe;
    try {
      probe = await probeTrustedServerConnection(
        baseUrl: profile.baseUrl,
        username: profile.username,
        password: profile.password,
        cleartextConfirmedOrigin: profile.cleartextConfirmedOrigin,
        certificatePinSha256:
            profile.certificatePinOrigin == cleartextOriginOf(profile.baseUrl)
            ? profile.certificatePinSha256
            : null,
      );
    } catch (_) {
      probe = const ServerProbeResult.failure('');
    }
    timer.stop();
    _apply(probe, timer.elapsedMilliseconds);
    _running = false;
    notifyListeners();
  }

  void _apply(ServerProbeResult probe, int millis) {
    const address = ConnectionCheckStep.address;
    const signIn = ConnectionCheckStep.signIn;
    const version = ConnectionCheckStep.version;
    if (probe.unsupportedLegacy) {
      _set(address, ConnectionCheckState.pass, ConnectionCheckReason.ok);
      _set(signIn, ConnectionCheckState.pass, ConnectionCheckReason.ok);
      _set(
        version,
        ConnectionCheckState.fail,
        ConnectionCheckReason.versionUnsupported,
      );
      _skipFrom(ConnectionCheckStep.liveUpdates);
      return;
    }
    if (!probe.ok) {
      if (probe.needsPassword) {
        _set(address, ConnectionCheckState.pass, ConnectionCheckReason.ok);
        _set(
          signIn,
          ConnectionCheckState.fail,
          profile.password.isEmpty
              ? ConnectionCheckReason.needsPassword
              : ConnectionCheckReason.passwordRejected,
        );
        _skipFrom(version);
      } else if (probe.flavor == ServerFlavor.v2) {
        // Answered like a server that is still starting or unwell.
        _set(address, ConnectionCheckState.pass, ConnectionCheckReason.ok);
        _set(signIn, ConnectionCheckState.pass, ConnectionCheckReason.ok);
        _set(
          version,
          ConnectionCheckState.warn,
          ConnectionCheckReason.versionNotReady,
        );
        _skipFrom(ConnectionCheckStep.liveUpdates);
      } else {
        _set(
          address,
          ConnectionCheckState.fail,
          probe.suggestsMissingServer
              ? ConnectionCheckReason.addressUnreachable
              : ConnectionCheckReason.addressNotOpenCode,
        );
        _skipFrom(signIn);
      }
      return;
    }
    _set(address, ConnectionCheckState.pass, ConnectionCheckReason.ok);
    _set(signIn, ConnectionCheckState.pass, ConnectionCheckReason.ok);
    _set(
      version,
      ConnectionCheckState.pass,
      ConnectionCheckReason.ok,
      value: probe.version,
    );
    switch (streamView()) {
      case ConnectionStreamView.live:
        _set(
          ConnectionCheckStep.liveUpdates,
          ConnectionCheckState.pass,
          ConnectionCheckReason.ok,
        );
      case ConnectionStreamView.connecting:
        _set(
          ConnectionCheckStep.liveUpdates,
          ConnectionCheckState.warn,
          ConnectionCheckReason.streamConnecting,
        );
      case ConnectionStreamView.down:
        _set(
          ConnectionCheckStep.liveUpdates,
          ConnectionCheckState.fail,
          ConnectionCheckReason.streamDown,
        );
      case ConnectionStreamView.notConnected:
        _set(
          ConnectionCheckStep.liveUpdates,
          ConnectionCheckState.skipped,
          ConnectionCheckReason.streamNotChecked,
        );
    }
    _set(
      ConnectionCheckStep.speed,
      millis > connectionSlowAfterMs
          ? ConnectionCheckState.warn
          : ConnectionCheckState.pass,
      millis > connectionSlowAfterMs
          ? ConnectionCheckReason.slow
          : ConnectionCheckReason.ok,
      value: millis,
    );
  }
}
