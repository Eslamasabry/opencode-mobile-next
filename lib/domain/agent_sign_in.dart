import 'dart:async';
import 'dart:math';

import 'agent_catalog.dart';

enum AgentSignInPhase {
  signedOut,
  urlReady,
  awaitingCode,
  signedIn,
  limitReached,
  failed,
}

enum AgentSignInFailure {
  unavailable,
  cancelled,
  invalidChallenge,
  invalidCode,
  authenticationRejected,
  hostUnavailable,
  staleRun,
  invalidResponse,
  cancellationUnconfirmed,
}

final class AgentSignInException implements Exception {
  const AgentSignInException(this.failure);
  final AgentSignInFailure failure;
  @override
  String toString() => 'AgentSignInException(${failure.name})';
}

/// Ephemeral browser challenge. Never serialize, persist, or log [uri]. The
/// UI must pass it to openExternalLink, not launchUrl. No arbitrary host URL is
/// accepted: each agent needs an independently reviewed authorization route.
final class AgentAuthorizationUrl {
  AgentAuthorizationUrl._(this.uri);
  final Uri uri;

  static AgentAuthorizationUrl validate(String agentId, String value) {
    final uri = Uri.tryParse(value);
    if (value.length > 8192 ||
        RegExp(r'[\x00-\x20\x7f\\]').hasMatch(value) ||
        uri == null ||
        uri.scheme != 'https' ||
        uri.userInfo.isNotEmpty ||
        uri.hasPort ||
        uri.fragment.isNotEmpty ||
        agentId != 'claude' ||
        uri.host != 'claude.com' ||
        uri.path != '/cai/oauth/authorize') {
      throw const AgentSignInException(AgentSignInFailure.invalidChallenge);
    }
    return AgentAuthorizationUrl._(uri);
  }

  @override
  String toString() => 'AgentAuthorizationUrl(redacted)';
}

/// A pasted OAuth code is one transient input, never a reusable credential.
/// No JSON representation exists. The only consumer is the private host sink.
final class AgentSignInCode {
  AgentSignInCode(String value) : _value = value {
    final parts = value.split('#');
    if (value.isEmpty ||
        value.length > 4096 ||
        parts.length != 2 ||
        parts.any((part) => part.isEmpty) ||
        value.startsWith('sk-') ||
        RegExp(r'[\x00-\x20\x7f]').hasMatch(value)) {
      _value = null;
      throw const AgentSignInException(AgentSignInFailure.invalidCode);
    }
  }

  String? _value;
  bool get consumed => _value == null;

  /// Consume only at the trusted host transport boundary. Never echo it in
  /// exceptions, command arguments, method diagnostics, or terminal events.
  String consume() {
    final value = _value;
    _value = null;
    if (value == null) {
      throw const AgentSignInException(AgentSignInFailure.invalidCode);
    }
    return value;
  }

  void clear() => _value = null;
  @override
  String toString() => 'AgentSignInCode(redacted)';
}

final class AgentSignInRun {
  AgentSignInRun({
    required this.profileId,
    required this.agentId,
    required this.runId,
    required this.method,
  }) {
    if (![
      profileId,
      agentId,
      runId,
    ].every((value) => RegExp(r'^[a-zA-Z0-9_-]{1,128}$').hasMatch(value))) {
      throw const AgentSignInException(AgentSignInFailure.invalidResponse);
    }
  }

  final String profileId;
  final String agentId;
  final String runId;
  final AgentSignInMethod method;
  @override
  String toString() => 'AgentSignInRun(redacted)';
}

/// Sanitized host truth. Tokens, email, raw output, and code are not fields.
final class AgentSignInHostUpdate {
  AgentSignInHostUpdate({
    required this.runId,
    required this.phase,
    this.authorizationUrl,
    this.failure,
    this.resetAt,
  }) {
    if ((phase == AgentSignInPhase.failed) != (failure != null) ||
        (phase == AgentSignInPhase.urlReady && authorizationUrl == null) ||
        (authorizationUrl != null &&
            phase != AgentSignInPhase.urlReady &&
            phase != AgentSignInPhase.awaitingCode) ||
        (resetAt != null && phase != AgentSignInPhase.limitReached)) {
      throw const AgentSignInException(AgentSignInFailure.invalidResponse);
    }
  }

  final String runId;
  final AgentSignInPhase phase;
  final AgentAuthorizationUrl? authorizationUrl;
  final AgentSignInFailure? failure;

  /// Null means the host did not provide a reset time. Never estimate it.
  final DateTime? resetAt;
  @override
  String toString() => 'AgentSignInHostUpdate(${phase.name})';
}

abstract interface class AgentSignInHost {
  Future<AgentSignInHostUpdate> start(AgentSignInRun run);
  Future<AgentSignInHostUpdate> readChallenge(AgentSignInRun run);
  Future<AgentSignInHostUpdate> submitCode(
    AgentSignInRun run,
    AgentSignInCode code,
  );
  Future<AgentSignInHostUpdate> status(AgentSignInRun run);

  /// Cancel only this run, discard buffered challenge/input and drain its
  /// process. Cancellation must tombstone an ID even when start is in flight.
  /// Return normally only after drain is confirmed; never treat an ACK as drain.
  Future<void> cancelAndDrain(AgentSignInRun run);
}

final class AgentSignInState {
  const AgentSignInState({
    required this.phase,
    required this.method,
    this.authorizationUrl,
    this.failure,
    this.resetAt,
    this.inspected = false,
    this.codeSubmitted = false,
  });

  final AgentSignInPhase phase;
  final AgentSignInMethod method;
  final AgentAuthorizationUrl? authorizationUrl;
  final AgentSignInFailure? failure;
  final DateTime? resetAt;

  /// Initial/cancelled signedOut is not a claim about host credentials.
  final bool inspected;
  final bool codeSubmitted;
  bool get hostOnlyApiKey => method == AgentSignInMethod.apiKeyHost;

  /// The host waits for Claude's prompt itself, so a code can be sent as soon
  /// as the page is ready.
  bool get acceptsCode =>
      (phase == AgentSignInPhase.awaitingCode ||
          phase == AgentSignInPhase.urlReady) &&
      !codeSubmitted;
  @override
  String toString() => 'AgentSignInState(${phase.name})';
}

/// One ephemeral sign-in journey. No preferences, logging, terminal transcript,
/// or credential store is involved. The owner must await close before deletion.
final class AgentSignInSession {
  AgentSignInSession({
    required this.host,
    required this.profileId,
    required this.agentId,
    required this.method,
  }) : _state = AgentSignInState(
         phase: AgentSignInPhase.signedOut,
         method: method,
       );

  final AgentSignInHost host;
  final String profileId;
  final String agentId;
  final AgentSignInMethod method;
  final _changes = StreamController<AgentSignInState>.broadcast();
  AgentSignInState _state;
  AgentSignInRun? _run;
  Future<void>? _inFlight;
  Future<void>? _cancelling;
  int _epoch = 0;
  bool _closed = false;
  bool _loginStarted = false;
  bool _codeSubmitted = false;

  AgentSignInState get state => _state;
  Stream<AgentSignInState> get changes => _changes.stream;

  void _publish(AgentSignInState value) {
    _state = value;
    if (!_changes.isClosed) _changes.add(value);
  }

  AgentSignInState _plain(
    AgentSignInPhase phase, [
    AgentSignInFailure? failure,
  ]) => AgentSignInState(phase: phase, method: method, failure: failure);

  static String _newRunId() {
    final random = Random.secure();
    return List.generate(
      16,
      (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
    ).join();
  }

  Future<void> start() async {
    if (_closed || _cancelling != null || _inFlight != null || _loginStarted) {
      throw const AgentSignInException(AgentSignInFailure.staleRun);
    }
    if (method == AgentSignInMethod.apiKeyHost) {
      _publish(_plain(AgentSignInPhase.signedOut));
      return;
    }
    if (method == AgentSignInMethod.none) {
      _publish(_plain(AgentSignInPhase.failed, AgentSignInFailure.unavailable));
      return;
    }
    final run = _run ?? _newRun();
    _run = run;
    _loginStarted = true;
    _epoch++;
    await _perform(run, () => host.start(run));
  }

  AgentSignInRun _newRun() => AgentSignInRun(
    profileId: profileId,
    agentId: agentId,
    runId: _newRunId(),
    method: method,
  );

  /// Read existing host status without launching browser authentication.
  /// Explicit start can reuse this identity after a signed-out inspection.
  Future<void> inspectStatus() async {
    if (_closed || _cancelling != null || _inFlight != null) {
      throw const AgentSignInException(AgentSignInFailure.staleRun);
    }
    if (method != AgentSignInMethod.browserOAuthHost) {
      _publish(_plain(AgentSignInPhase.signedOut));
      return;
    }
    final run = _run ?? _newRun();
    _run = run;
    await _perform(run, () => host.status(run));
  }

  Future<void> readChallenge() => _withRun(host.readChallenge);
  Future<void> refreshStatus() => _withRun(host.status);

  Future<void> _withRun(
    Future<AgentSignInHostUpdate> Function(AgentSignInRun) operation,
  ) {
    final run = _run;
    if (_closed || run == null || _cancelling != null || _inFlight != null) {
      throw const AgentSignInException(AgentSignInFailure.staleRun);
    }
    return _perform(run, () => operation(run));
  }

  Future<void> submitCode(AgentSignInCode code) async {
    try {
      if (!state.acceptsCode || method != AgentSignInMethod.browserOAuthHost) {
        throw const AgentSignInException(AgentSignInFailure.invalidCode);
      }
      await _withRun((run) {
        _codeSubmitted = true;
        _publish(
          AgentSignInState(
            phase: state.phase,
            method: method,
            inspected: state.inspected,
            codeSubmitted: true,
          ),
        );
        return host.submitCode(run, code);
      });
    } finally {
      code.clear();
      // The host answered with Claude's verdict: a refused code can be pasted
      // again into the same sign-in, so "sent" ends with the answer.
      _codeSubmitted = false;
      if (!_closed) {
        _publish(
          AgentSignInState(
            phase: state.phase,
            method: method,
            authorizationUrl: state.authorizationUrl,
            failure: state.failure,
            resetAt: state.resetAt,
            inspected: state.inspected,
          ),
        );
      }
    }
  }

  Future<void> _perform(
    AgentSignInRun run,
    Future<AgentSignInHostUpdate> Function() operation,
  ) async {
    final epoch = _epoch;
    final done = Completer<void>();
    _inFlight = done.future;
    try {
      final update = await operation();
      if (_closed || epoch != _epoch || !identical(_run, run)) return;
      if (update.runId != run.runId ||
          (update.authorizationUrl != null && agentId != 'claude')) {
        throw const AgentSignInException(AgentSignInFailure.invalidResponse);
      }
      _publish(
        AgentSignInState(
          phase: update.phase,
          method: method,
          authorizationUrl: update.authorizationUrl,
          failure: update.failure,
          resetAt: update.resetAt,
          inspected: true,
          codeSubmitted: _codeSubmitted,
        ),
      );
    } catch (error) {
      if (!_closed && epoch == _epoch && identical(_run, run)) {
        _publish(
          _plain(
            AgentSignInPhase.failed,
            error is AgentSignInException
                ? error.failure
                : AgentSignInFailure.hostUnavailable,
          ),
        );
      }
    } finally {
      done.complete();
      if (identical(_inFlight, done.future)) _inFlight = null;
    }
  }

  Future<void> cancelAndDrain() {
    final existing = _cancelling;
    if (existing != null) return existing;
    final run = _run;
    if (run == null) {
      _publish(_plain(AgentSignInPhase.signedOut));
      return Future.value();
    }
    _epoch++;
    _publish(_plain(AgentSignInPhase.signedOut));
    final pending = _inFlight;
    final done = Completer<void>();
    _cancelling = done.future;
    () async {
      try {
        await host.cancelAndDrain(run);
        if (pending != null) {
          await pending;
          // A late start completion must not leave a live auth process behind.
          await host.cancelAndDrain(run);
        }
        if (identical(_run, run)) _run = null;
        _loginStarted = false;
        _codeSubmitted = false;
        done.complete();
      } catch (_) {
        _publish(
          _plain(
            AgentSignInPhase.failed,
            AgentSignInFailure.cancellationUnconfirmed,
          ),
        );
        done.completeError(
          const AgentSignInException(
            AgentSignInFailure.cancellationUnconfirmed,
          ),
        );
      } finally {
        _cancelling = null;
      }
    }();
    return done.future;
  }

  Future<void> close() async {
    if (_changes.isClosed) return;
    _closed = true;
    await cancelAndDrain();
    await _changes.close();
  }
}
