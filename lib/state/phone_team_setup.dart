// The one-tap "Turn on AI Team on this phone" flow, as a controller.
//
// Finish line: a person taps once and the phone goes from "team off" to
// "team ready": the running reply finishes, the unprotected OpenCode server
// and terminals are stopped only after the person says yes, the engine
// proves this phone safe, and OpenCode comes back protected.
// Non-goal: anything about the engine's own internals (the native daemon,
// its proof, its adapter). This file only drives the calls the coordinator's
// handoff froze: phoneProjectEngine.start / attach / probe.
//
// Every step is a real signal (a started and an ended time), never a timer
// guess, and every failure keeps only a safe code for Details.
import 'dart:async';

import 'package:flutter/foundation.dart';

import '../builtin/builtin_linux.dart';
import '../builtin/builtin_server.dart';
import '../builtin/deliberate_stop.dart';
import '../builtin/local_terminal.dart';
import '../domain/phone_project_engine.dart';
import '../l10n/app_localizations.dart';
import '../ui/kit/kit_redact.dart';
import 'connection.dart';
import 'profiles.dart';

/// The steps the person sees, in order.
enum PhoneTeamSetupStep { reply, stop, check, server }

enum PhoneTeamSetupPhase {
  /// Nothing started (or the last run was dismissed).
  idle,
  running,

  /// Waiting for the person's yes to stop the server and terminals.
  confirming,
  failed,
  done,
}

/// Why a run stopped, in the plain kinds the page has words for.
enum PhoneTeamSetupProblem {
  /// This phone did not prove the team's copy of the code separate.
  unsafe,

  /// The engine did not start or did not answer.
  engine,

  /// The server or a terminal did not stop.
  stopFailed,

  /// The server did not come back.
  serverStart,

  /// Everything started but the engine still says it cannot run work.
  notReady,

  /// The person said no to stopping the server.
  declined,

  /// There is no OpenCode on this phone to protect.
  noServer,
}

/// What became of the person's OpenCode when a run ended without the team.
enum PhoneTeamServerState {
  /// The run never stopped it.
  untouched,

  /// The run stopped it and started it again: it is on.
  backOn,

  /// The run stopped it and could not start it again: it is off.
  stillOff,
}

/// The plain kinds of place a phone safety check can stop at.
enum PhoneProofStepKind {
  /// Android ended the test program before it could report.
  signal,

  /// One named check was ended by Android (a signal).
  checkSignal,

  /// The phone did not block something the sandbox must block.
  notBlocked,

  /// The team's sandbox launcher itself could not start.
  sandbox,

  /// A normal Ubuntu step failed inside the protected sandbox.
  ubuntu,

  /// The private test files changed or could not be set up.
  fixture,

  /// The test did not answer in time.
  noAnswer,

  /// A proot-tier check did not pass.
  proot,

  /// Any other named step.
  other,
}

/// The step a boundary proof stopped at (issue #111).
///
/// The native side reports a failed proof as `boundary_proof_failed:<step>`,
/// where `<step>` is a short static code such as `native.signal-31`,
/// `native.io-uring.signal-11`, `ubuntu.git-commit.exit-128` or
/// `proot-tier.daemonProcDenied`. It never holds a path or secret.
@immutable
class PhoneProofStep {
  const PhoneProofStep(this.code, this.kind, {this.check, this.signal});

  /// The raw step code, e.g. `native.signal-31`.
  final String code;
  final PhoneProofStepKind kind;

  /// The named check, when there is one (`io-uring`, `git-commit`).
  final String? check;

  /// The signal number that ended the test, when one did.
  final int? signal;

  static const _prefix = 'boundary_proof_failed:';
  static final _safe = RegExp(r'^[A-Za-z0-9._-]{1,120}$');

  /// Reads the step out of a failure's details, or null when it has none.
  static PhoneProofStep? parse(String? details) {
    if (details == null) return null;
    final at = details.indexOf(_prefix);
    if (at < 0) return null;
    var code = details.substring(at + _prefix.length);
    final end = code.indexOf(RegExp(r'[\s;]'));
    if (end >= 0) code = code.substring(0, end);
    if (!_safe.hasMatch(code)) return null;
    final parts = code.split('.');
    int? signalOf(String? part) {
      final m = RegExp(r'^signal-(\d{1,3})$').firstMatch(part ?? '');
      return m == null ? null : int.parse(m.group(1)!);
    }

    final leg = parts.first;
    final second = parts.length > 1 ? parts[1] : null;
    final last = parts.length > 1 ? parts.last : null;
    if (code == 'fixture-changed' ||
        code == 'proot-tier.fixture-changed' ||
        code == 'setup.proof_fixture_unavailable') {
      return PhoneProofStep(code, PhoneProofStepKind.fixture);
    }
    if (last == 'no-answer') {
      return PhoneProofStep(code, PhoneProofStepKind.noAnswer);
    }
    switch (leg) {
      case 'native':
        if (second == null) break;
        final whole = signalOf(second);
        if (whole != null) {
          return PhoneProofStep(code, PhoneProofStepKind.signal, signal: whole);
        }
        if (second == 'sandbox') {
          return PhoneProofStep(code, PhoneProofStepKind.sandbox);
        }
        if (second.startsWith('exit-')) break;
        final signal = parts.length > 2 ? signalOf(parts[2]) : null;
        if (signal != null) {
          return PhoneProofStep(
            code,
            PhoneProofStepKind.checkSignal,
            check: second,
            signal: signal,
          );
        }
        return PhoneProofStep(
          code,
          second == 'worker-write' || second == 'worker-rename'
              ? PhoneProofStepKind.other
              : PhoneProofStepKind.notBlocked,
          check: second,
        );
      case 'ubuntu':
        if (second == null) break;
        return PhoneProofStep(code, PhoneProofStepKind.ubuntu, check: second);
      case 'proot-tier':
        if (second == null) break;
        return PhoneProofStep(code, PhoneProofStepKind.proot, check: second);
      case 'setup':
        if (second == null) break;
        return PhoneProofStep(code, PhoneProofStepKind.other, check: second);
    }
    return PhoneProofStep(code, PhoneProofStepKind.other, check: code);
  }

  /// Short space-separated fields for the diagnostics export. Each field
  /// stays short so the export's long-token mask never hides it.
  String get diagnostic => [
    'step ${code.split('.').join(' ')}',
    'kind ${kind.name}',
    if (check != null) 'check $check',
    if (signal != null) 'signal $signal',
  ].join('; ');
}

/// What the phone is running right now.
class PhoneTeamHostState {
  const PhoneTeamHostState({
    this.serverRunning = false,
    this.terminals = 0,
    this.engineRunning = false,
    this.restartRequired = false,
  });
  final bool serverRunning;
  final int terminals;
  final bool engineRunning;
  final bool restartRequired;

  bool get needsStop => serverRunning || terminals > 0 || restartRequired;
}

/// Everything the flow asks of the phone, so tests can stand in.
abstract interface class PhoneTeamSetupPorts {
  /// Whether this phone has a supported in-app OpenCode server to set up.
  bool get hasServer;

  /// Whether the team was turned on here before (a saved engine config).
  bool get wasOn;

  /// A chat reply is on the wire.
  bool get replyRunning;
  Listenable get replyChanges;
  Future<PhoneTeamHostState> inspect();
  Future<void> stopServer();
  Future<void> closeTerminals();
  Future<PhoneEngineHealth> startEngine();

  /// Stops this setup's engine and releases its ownership before a retry.
  /// Keeps saved projects and team settings.
  Future<void> stopEngine();
  Future<PhoneEngineHealth> probeEngine();

  /// Starts OpenCode and waits until it answers; null when it does, else
  /// the technical reason (kept under Details).
  Future<String?> startServer();

  /// Reads the engine again into the app's team (capabilities refresh).
  Future<void> attach();

  /// Puts OpenCode back after a run that stopped it and failed: does
  /// nothing when it already answers, else starts it like the Restart
  /// control and reconnects the app. True when OpenCode is on.
  Future<bool> restoreServer();
}

/// Selects the supported phone profile without allocating setup state or controls.
ServerProfile? _phoneTeamProfile(ConnectionController connection) {
  ServerProfile? chosen;
  for (final profile in connection.store.profiles) {
    if (!looksLikeInAppServer(profile) ||
        !connection.serverCapabilitiesFor(profile).phoneTeamExecution) {
      continue;
    }
    if (profile.id == connection.store.activeId) return profile;
    chosen ??= profile;
  }
  return chosen;
}

/// The real phone: the same controls This phone uses, nothing reimplemented.
class BuiltinPhoneTeamSetupPorts implements PhoneTeamSetupPorts {
  BuiltinPhoneTeamSetupPorts({
    required this.connection,
    required this.linux,
    required this.starter,
    required this.notice,
    LocalTerminalSessions? terminals,
  }) : _terminals = terminals ?? LocalTerminalSessions();

  final ConnectionController connection;
  final BuiltinLinux linux;
  final BuiltinServerStarter starter;

  /// What the engine's ongoing notification says while it runs.
  final String notice;
  final LocalTerminalSessions _terminals;
  String? _startedEngineProfileId;
  Future<void>? _engineStop;

  ServerProfile? get _profile => _phoneTeamProfile(connection);

  String get _id => _profile!.id;

  @override
  bool get hasServer => _profile != null;

  @override
  bool get wasOn =>
      _profile?.orchestration?.provider == OrchestrationProvider.phoneEngine &&
      (_profile?.teamEngineAuth.isNotEmpty ?? false);

  @override
  bool get replyRunning => connection.replyInFlight;

  @override
  Listenable get replyChanges => connection;

  @override
  Future<PhoneTeamHostState> inspect() async {
    final status = await linux.status();
    var engine = const BuiltinPhoneEngineStatus(profileId: '');
    try {
      engine = await linux.phoneEngineStatus(_id);
    } on BuiltinLinuxException {
      // Unknown: treated as not running; the start reports for itself.
    }
    var shells = 0;
    try {
      await _terminals.load();
      shells = _terminals.shells.where((s) => s.running).length;
    } catch (_) {
      // No terminals to count.
    }
    return PhoneTeamHostState(
      serverRunning: status.serverRunning,
      terminals: shells,
      engineRunning: engine.running,
      restartRequired: engine.restartRequired,
    );
  }

  @override
  Future<void> stopServer() async {
    await linux.stopServer();
    await DeliberateServerStop.mark(connection.store.prefs, _id);
  }

  @override
  Future<void> closeTerminals() async {
    await _terminals.load();
    for (final shell in List.of(_terminals.shells)) {
      await _terminals.remove(shell);
    }
  }

  @override
  Future<PhoneEngineHealth> startEngine() async {
    await _engineStop;
    final id = _id;
    if (_startedEngineProfileId != null && _startedEngineProfileId != id) {
      await stopEngine();
    }
    _startedEngineProfileId = id;
    return connection.phoneProjectEngine.start(id, notice: notice);
  }

  @override
  Future<void> stopEngine() {
    final pending = _engineStop;
    if (pending != null) return pending;
    final id = _startedEngineProfileId;
    if (id == null) return Future.value();
    return _engineStop = connection.phoneProjectEngine
        .stop(id)
        .then((_) {
          if (_startedEngineProfileId == id) _startedEngineProfileId = null;
        })
        .whenComplete(() => _engineStop = null);
  }

  @override
  Future<PhoneEngineHealth> probeEngine() =>
      connection.phoneProjectEngine.probe(_id);

  @override
  Future<String?> startServer() async {
    final profile = _profile;
    if (profile == null) return 'noServerProfile';
    final failure = await starter.start(profile);
    if (failure == null) return null;
    return failure.problem.name;
  }

  @override
  Future<bool> restoreServer() async {
    final profile = _profile;
    if (profile == null) return false;
    var running = false;
    try {
      running = (await linux.status()).serverRunning;
    } on BuiltinLinuxException {
      // Unknown: start it; the starter is safe when it already runs.
    }
    if (!running && await starter.start(profile) != null) return false;
    await connection.retryConnection();
    return true;
  }

  @override
  Future<void> attach() async {
    await connection.phoneProjectEngine.attach(_id);
    connection.syncOrchestration();
  }
}

/// One failed setup run as a diagnostics entry: safe codes only.
class PhoneTeamSetupFailure implements Exception {
  const PhoneTeamSetupFailure(this.line);
  final String line;
  @override
  String toString() => line;
}

class _StepTimeout implements Exception {
  _StepTimeout(this.tag, this.problem);
  final String tag;
  final PhoneTeamSetupProblem problem;
}

/// One run of the flow. Listen to it; it never touches the widgets.
class PhoneTeamSetupController extends ChangeNotifier {
  PhoneTeamSetupController(
    this.ports, {
    DateTime Function()? now,
    Future<void> Function(Duration)? delay,
    this.readyAttempts = 15,
    this.readyGap = const Duration(seconds: 2),
    this.replyWait = const Duration(minutes: 3),
    this.stepWait = const Duration(seconds: 90),
    this.onFailure,
  }) : _now = now ?? DateTime.now,
       _delay = delay ?? Future<void>.delayed;

  final PhoneTeamSetupPorts ports;
  final DateTime Function() _now;
  final Future<void> Function(Duration) _delay;
  final int readyAttempts;
  final Duration readyGap;

  /// The longest the flow waits for a running reply (a stale "reply in
  /// flight" after a force-stop would otherwise hold it forever).
  final Duration replyWait;

  /// The longest any one call to the phone may take before the run ends in
  /// a failure with a reason, instead of an endless hourglass.
  final Duration stepWait;

  /// Told every failure as one safe line (codes only), for the app's
  /// diagnostics export (issue #111: a failed safety check names its step).
  final void Function(String line)? onFailure;

  PhoneTeamSetupPhase _phase = PhoneTeamSetupPhase.idle;
  PhoneTeamSetupProblem? _problem;
  PhoneTeamSetupStep? _step;
  PhoneTeamSetupStep? _failedStep;
  PhoneEngineHealth? _health;
  String? _details;
  bool _automatic = false;
  bool _stopped = false;
  bool _disposed = false;
  bool _serverStopped = false;
  bool _engineAttempted = false;
  bool _terminalsClosed = false;
  int _hostTerminals = 0;
  PhoneTeamServerState _serverState = PhoneTeamServerState.untouched;
  final Map<PhoneTeamSetupStep, DateTime> _began = {};
  final Map<PhoneTeamSetupStep, Duration> _took = {};
  final Set<PhoneTeamSetupStep> _skipped = {};
  Completer<bool>? _confirm;
  Future<void>? _running;

  PhoneTeamSetupPhase get phase => _phase;
  PhoneTeamSetupProblem? get problem => _problem;
  PhoneTeamSetupStep? get step => _step;
  PhoneTeamSetupStep? get failedStep => _failedStep;
  PhoneEngineHealth? get health => _health;

  /// What became of OpenCode, for a run that ended in failure.
  PhoneTeamServerState get serverState => _serverState;

  /// Whether the run closed the person's terminals.
  bool get terminalsClosed => _terminalsClosed;

  /// Safe technical text for Details; never a raw exception.
  String? get details => _details;

  /// Started by the app itself (after an update), not by a tap.
  bool get automatic => _automatic;
  bool get isRunning =>
      _phase == PhoneTeamSetupPhase.running ||
      _phase == PhoneTeamSetupPhase.confirming;
  bool get needsComponentRecovery =>
      _problem == PhoneTeamSetupProblem.serverStart &&
      _details == 'componentRecovery';
  bool get isDone => _phase == PhoneTeamSetupPhase.done;
  bool get skipped => _skipped.isNotEmpty;
  bool wasSkipped(PhoneTeamSetupStep step) => _skipped.contains(step);

  /// How long [step] took, once it ended.
  Duration? took(PhoneTeamSetupStep step) => _took[step];

  /// How long the current [step] has been going.
  Duration? elapsed(PhoneTeamSetupStep step) {
    final began = _began[step];
    if (began == null || _took.containsKey(step)) return null;
    return _now().difference(began);
  }

  /// Whether the page should show Finishing your current reply.
  bool get waitingForReply =>
      _phase == PhoneTeamSetupPhase.running &&
      _step == PhoneTeamSetupStep.reply;

  void _emit() {
    if (!_disposed) notifyListeners();
  }

  void _begin(PhoneTeamSetupStep step) {
    _step = step;
    _began[step] = _now();
    _emit();
  }

  void _end(PhoneTeamSetupStep step) {
    _took[step] = _now().difference(_began[step] ?? _now());
    _emit();
  }

  void _skip(PhoneTeamSetupStep step) {
    _skipped.add(step);
    _began[step] = _now();
    _took[step] = Duration.zero;
    _emit();
  }

  void _fail(
    PhoneTeamSetupProblem problem,
    PhoneTeamSetupStep step,
    String details,
  ) {
    _problem = problem;
    _failedStep = step;
    _details = KitRedact.text(details);
    _phase = PhoneTeamSetupPhase.failed;
    final proof = PhoneProofStep.parse(_details);
    try {
      onFailure?.call(
        [
          'AI Team setup stopped',
          'step ${step.name}',
          'problem ${problem.name}',
          'code ${proof != null ? 'boundary_proof_failed' : _details}',
          if (proof != null) proof.diagnostic,
        ].join('; '),
      );
    } catch (_) {
      // Diagnostics never change the outcome the person sees.
    }
    _emit();
  }

  /// The person's answer to the stop question. True stops the server and
  /// closes the terminals; false ends the run, changing nothing.
  void answer(bool yes) {
    final confirm = _confirm;
    if (confirm != null && !confirm.isCompleted) confirm.complete(yes);
  }

  /// Runs the whole flow; a second call while it runs joins the first.
  Future<void> run({bool automatic = false}) {
    final existing = _running;
    if (existing != null && isRunning) return existing;
    _automatic = automatic;
    return _running = _run().whenComplete(() => _running = null);
  }

  void _reset() {
    _phase = PhoneTeamSetupPhase.running;
    _problem = null;
    _step = null;
    _failedStep = null;
    _details = null;
    _health = null;
    _began.clear();
    _took.clear();
    _skipped.clear();
    _stopped = false;
    _serverStopped = false;
    _terminalsClosed = false;
    _hostTerminals = 0;
    _serverState = PhoneTeamServerState.untouched;
  }

  /// Ends the run in failure, releasing the attempted engine before putting
  /// OpenCode back. A readiness refusal still owns a live native daemon.
  Future<void> _failWith(
    PhoneTeamSetupProblem problem,
    PhoneTeamSetupStep step,
    String details,
  ) async {
    if (_engineAttempted) {
      try {
        await _bounded(ports.stopEngine(), 'stopEngine');
        _engineAttempted = false;
      } catch (_) {
        // Preserve the original reason while exposing a cleanup failure safely.
        details = '$details; engineStopFailed';
      }
    }
    if (_serverStopped) {
      if (problem == PhoneTeamSetupProblem.serverStart) {
        // It just failed to start; say so instead of waiting for it again.
        _serverState = PhoneTeamServerState.stillOff;
      } else {
        var on = false;
        try {
          on = await ports.restoreServer();
        } catch (_) {
          on = false;
        }
        _serverState = on
            ? PhoneTeamServerState.backOn
            : PhoneTeamServerState.stillOff;
      }
    }
    _fail(problem, step, details);
  }

  /// Bounds one call to the phone; a call that never answers becomes a
  /// [_StepTimeout] naming where it hung.
  Future<T> _bounded<T>(
    Future<T> call,
    String tag, {
    Duration? limit,
    PhoneTeamSetupProblem problem = PhoneTeamSetupProblem.engine,
  }) => call.timeout(
    limit ?? stepWait,
    onTimeout: () => throw _StepTimeout(tag, problem),
  );

  Future<void> _waitForReply() async {
    if (!ports.replyRunning) return;
    final done = Completer<void>();
    void check() {
      if (!ports.replyRunning && !done.isCompleted) done.complete();
    }

    ports.replyChanges.addListener(check);
    // A just-sent prompt's grace window ends without any change event, so
    // look again on a timer: the flow must never wait on a reply that ended.
    final poll = Timer.periodic(const Duration(seconds: 2), (_) => check());
    try {
      await done.future.timeout(
        replyWait,
        onTimeout: () => throw _StepTimeout(
          'replyStillRunning',
          PhoneTeamSetupProblem.engine,
        ),
      );
    } finally {
      poll.cancel();
      ports.replyChanges.removeListener(check);
    }
  }

  Future<bool> _stopForProtection() async {
    _phase = PhoneTeamSetupPhase.confirming;
    _begin(PhoneTeamSetupStep.stop);
    final confirm = _confirm = Completer<bool>();
    final yes = await confirm.future;
    _confirm = null;
    if (!yes) {
      await _failWith(
        PhoneTeamSetupProblem.declined,
        PhoneTeamSetupStep.stop,
        'declined',
      );
      return false;
    }
    _phase = PhoneTeamSetupPhase.running;
    // The wait for the person's answer is not work: the step's time starts
    // at the yes.
    _began[PhoneTeamSetupStep.stop] = _now();
    _emit();
    try {
      _serverStopped = true;
      await _bounded(ports.stopServer(), 'stopServer');
      await _bounded(ports.closeTerminals(), 'closeTerminals');
      _terminalsClosed = _hostTerminals > 0;
    } on _StepTimeout {
      await _failWith(
        PhoneTeamSetupProblem.stopFailed,
        PhoneTeamSetupStep.stop,
        'timeout',
      );
      return false;
    } catch (_) {
      await _failWith(
        PhoneTeamSetupProblem.stopFailed,
        PhoneTeamSetupStep.stop,
        'stopFailed',
      );
      return false;
    }
    _end(PhoneTeamSetupStep.stop);
    return true;
  }

  Future<void> _run() async {
    _reset();
    _emit();
    try {
      // A failed or timed-out cleanup still owns its original engine. Finish
      // that cleanup before selecting or starting another phone profile.
      if (_engineAttempted) {
        await _bounded(ports.stopEngine(), 'stopEngine');
        _engineAttempted = false;
      }
      if (!ports.hasServer) {
        await _failWith(
          PhoneTeamSetupProblem.noServer,
          PhoneTeamSetupStep.reply,
          'noServerProfile',
        );
        return;
      }
      // a) A running reply is never cut short.
      _begin(PhoneTeamSetupStep.reply);
      await _waitForReply();
      if (_stopped) return;
      _end(PhoneTeamSetupStep.reply);

      // b) The unprotected server and terminals: only with a yes.
      final host = await _bounded(ports.inspect(), 'inspect');
      _hostTerminals = host.terminals;
      var stopped = false;
      if (host.needsStop) {
        if (!await _stopForProtection()) return;
        stopped = true;
      } else {
        _skip(PhoneTeamSetupStep.stop);
      }

      // c) The engine proves this phone safe.
      _begin(PhoneTeamSetupStep.check);
      _engineAttempted = true;
      var health = await _bounded(ports.startEngine(), 'startEngine');
      if (health.restartRequired && !stopped) {
        // The engine found an old server it cannot vouch for: ask now.
        _took.remove(PhoneTeamSetupStep.stop);
        _skipped.remove(PhoneTeamSetupStep.stop);
        if (!await _stopForProtection()) return;
        stopped = true;
        _begin(PhoneTeamSetupStep.check);
        health = await _bounded(ports.startEngine(), 'startEngine');
      }
      _health = health;
      // The check proves the boundary; OpenCode itself is verified only
      // after step d starts it again, so canExecute is judged there.
      if (!health.boundary) {
        await _failWith(
          PhoneTeamSetupProblem.unsafe,
          PhoneTeamSetupStep.check,
          health.boundaryReason.isEmpty
              ? 'boundary_unverified'
              : health.boundaryReason,
        );
        return;
      }
      _end(PhoneTeamSetupStep.check);

      // d) OpenCode back, protected; probe until the engine can run work.
      _begin(PhoneTeamSetupStep.server);
      final failure = await _bounded(
        ports.startServer(),
        'startServer',
        limit: const Duration(minutes: 4),
        problem: PhoneTeamSetupProblem.serverStart,
      );
      if (failure != null) {
        await _failWith(
          PhoneTeamSetupProblem.serverStart,
          PhoneTeamSetupStep.server,
          failure,
        );
        return;
      }
      PhoneEngineHealth? last;
      for (var attempt = 0; attempt < readyAttempts; attempt++) {
        try {
          last = await _bounded(ports.probeEngine(), 'probe');
        } on PhoneEngineException {
          last = null;
        } on _StepTimeout {
          last = null;
        }
        if (last != null && last.canExecute) break;
        if (attempt + 1 < readyAttempts) await _delay(readyGap);
      }
      _health = last ?? _health;
      if (last == null || !last.canExecute) {
        await _failWith(
          PhoneTeamSetupProblem.notReady,
          PhoneTeamSetupStep.server,
          last == null
              ? 'engineUnavailable'
              : (last.boundaryReason.isEmpty
                    ? 'notExecutable'
                    : last.boundaryReason),
        );
        return;
      }
      await _bounded(ports.attach(), 'attach');
      _engineAttempted = false;
      _end(PhoneTeamSetupStep.server);
      _step = null;
      _phase = PhoneTeamSetupPhase.done;
      _emit();
    } on _StepTimeout catch (e) {
      await _failWith(
        e.problem,
        _step ?? PhoneTeamSetupStep.check,
        'timeout:${e.tag}',
      );
    } on PhoneEngineException catch (e) {
      await _failWith(
        PhoneTeamSetupProblem.engine,
        _step ?? PhoneTeamSetupStep.check,
        e.code,
      );
    } catch (_) {
      await _failWith(
        PhoneTeamSetupProblem.engine,
        _step ?? PhoneTeamSetupStep.check,
        'unexpected',
      );
    }
  }

  /// After an app update: the proof is per packaged binary, so a team that
  /// was on is checked again in the background. Nothing is stopped without
  /// the person (the flow waits at its question).
  Future<void> autoProof() async {
    if (isRunning || _autoTried || !ports.wasOn || !ports.hasServer) return;
    _autoTried = true;
    try {
      final health = await ports.probeEngine();
      if (health.canExecute) return;
    } catch (_) {
      // The engine is not answering: check it again.
    }
    await run(automatic: true);
  }

  bool _autoTried = false;

  /// Forgets a finished or failed run so the page shows its start again.
  void dismiss() {
    if (isRunning) return;
    _phase = PhoneTeamSetupPhase.idle;
    _emit();
  }

  @override
  void dispose() {
    _disposed = true;
    _stopped = true;
    final confirm = _confirm;
    if (confirm != null && !confirm.isCompleted) confirm.complete(false);
    super.dispose();
  }
}

/// The app's one setup flow for [connection], shared by the AI Team page,
/// Team settings and the Work strip (one run at a time).
class PhoneTeamSetup {
  PhoneTeamSetup._();

  static final Expando<PhoneTeamSetupController> _shared = Expando();

  /// Eligibility checks must not create an independent server starter.
  static bool hasServer(ConnectionController connection) =>
      _phoneTeamProfile(connection) != null;

  /// Tests replace the phone with this.
  @visibleForTesting
  static PhoneTeamSetupPorts Function(ConnectionController)? debugPorts;

  static PhoneTeamSetupController of(
    ConnectionController connection, {
    AppLocalizations? copy,
    BuiltinServerStarter? starter,
  }) {
    final existing = _shared[connection];
    if (existing != null) return existing;
    final testPorts = debugPorts?.call(connection);
    final PhoneTeamSetupPorts ports;
    if (testPorts != null) {
      ports = testPorts;
    } else {
      if (starter == null) {
        throw StateError('Phone team setup requires the app server starter');
      }
      ports = BuiltinPhoneTeamSetupPorts(
        connection: connection,
        linux: starter.linux,
        starter: starter,
        notice: copy?.aiteamComponentNotice ?? 'AI Team is running',
      );
    }
    return _shared[connection] = PhoneTeamSetupController(
      ports,
      onFailure: (line) => connection.diagnostics.record(
        PhoneTeamSetupFailure(line),
        null,
        source: 'aiteam-setup',
      ),
    );
  }
}
