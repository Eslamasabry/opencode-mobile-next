part of '../orchestration.dart';

/// Mints one idempotency key per mutation.
typedef MutationKeyMinter = String Function();

/// Default: a version-4 UUID.
String mintMutationKey() => const Uuid().v4();

/// Probes the host described by a config. Never throws.
typedef OrchestrationProbe =
    Future<ProbeVerdict> Function(OrchestrationConfig config);

/// Builds the adapter for a config once the probe [found] the host.
typedef OrchestrationGatewayFactory =
    FutureOr<OrchestrationGateway> Function(
      OrchestrationConfig config,
      ProbeFound found,
    );

/// Why the plugin has no usable host, in a form the UI can map to copy.
enum OrchestrationErrorKind {
  /// The URL answered, but not like a Gas City supervisor.
  notGasCity,

  /// A Gas City answered, but the configured city is not running.
  cityNotRunning,

  /// `http://` to a host that is neither loopback nor tailnet.
  plainHttpRefused,

  /// No answer at all (DNS, refused, timeout, TLS, bad URL).
  unreachable,

  /// The host was reached but a read failed; cached data stays on show.
  readFailed,
}

/// The last thing that went wrong, with the probe verdict when there was
/// one so Technical details can show what the host said.
class OrchestrationError {
  const OrchestrationError(this.kind, this.message, {this.verdict});

  final OrchestrationErrorKind kind;
  final String message;
  final ProbeVerdict? verdict;

  /// Null for [ProbeFound]; otherwise the matching kind.
  static OrchestrationError? fromVerdict(ProbeVerdict verdict) =>
      switch (verdict) {
        ProbeFound() => null,
        ProbeNotGasCity() => OrchestrationError(
          OrchestrationErrorKind.notGasCity,
          verdict.describe(),
          verdict: verdict,
        ),
        ProbeCityNotRunning() => OrchestrationError(
          OrchestrationErrorKind.cityNotRunning,
          verdict.describe(),
          verdict: verdict,
        ),
        ProbePlainHttpRefused() => OrchestrationError(
          OrchestrationErrorKind.plainHttpRefused,
          verdict.describe(),
          verdict: verdict,
        ),
        ProbeUnreachable() => OrchestrationError(
          OrchestrationErrorKind.unreachable,
          verdict.describe(),
          verdict: verdict,
        ),
      };

  @override
  String toString() => 'OrchestrationError(${kind.name}: $message)';
}

/// Where the controller is in its lifecycle.
enum OrchestrationPhase {
  /// Constructed; [OrchestrationController.start] not called yet.
  idle,
  probing,
  connecting,

  /// Gateway built and subscribed; data flows.
  ready,

  /// The probe or the adapter refused; see
  /// [OrchestrationController.lastError].
  failed,

  /// [OrchestrationController.stop] ran; nothing more will arrive.
  stopped,
}

/// Names of the refetchable scopes an event can mark dirty (§5).
abstract final class OrchestrationScope {
  static const projects = 'projects';
  static const runs = 'runs';
  static const work = 'work';
  static const agents = 'agents';
  static const gates = 'gates';
  static const usage = 'usage';
  static const activity = 'activity';

  /// One run by id; subsumed by [runs] when both are dirty.
  static String run(String id) => 'run:$id';

  /// One agent by id; subsumed by [agents] when both are dirty.
  static String agent(String id) => 'agent:$id';

  /// Every list scope: what a full refresh or a head-only replay fetches.
  static const all = {projects, runs, work, agents, gates, usage, activity};
}

/// One scope's result folded into a snapshot.
typedef _Fold = OrchestrationSnapshot Function(OrchestrationSnapshot s);

/// An agent's live output as the controller holds it: the merged text so
/// far, whether the host still serves the session, and whether a stream
/// is open. The text survives the screen that watched it, so a session
/// that ended still shows what was cached (02-ux §5.2).
class AgentOutputTail extends ChangeNotifier {
  AgentOutputTail(this.agentId, {required this.sessionId});

  final String agentId;

  /// The session the output belongs to; null when the agent has none, in
  /// which case [available] is false.
  final String? sessionId;

  String _text = '';
  bool _available = true;
  bool _ended = false;
  bool _watching = false;
  bool _received = false;
  String? _endReason;
  Object? _error;

  /// Merged transcript, at most [agentOutputLimit] characters.
  String get text => _text;

  /// False when the adapter cannot serve output for this agent.
  bool get available => _available;

  /// The host stopped serving the session (404); nothing more arrives.
  bool get ended => _ended;

  /// Host-provided detail for [ended], when any.
  String? get endReason => _endReason;

  /// A stream is open and may still deliver text.
  bool get watching => _watching;

  /// At least one capture arrived since the stream opened.
  bool get received => _received;

  /// The last stream error, cleared by the next capture.
  Object? get error => _error;

  void _apply(AgentOutputEvent event) {
    switch (event) {
      case AgentOutputText():
        _text = mergeAgentOutput(_text, event.text);
        _received = true;
        _error = null;
      case AgentOutputEnded():
        _ended = true;
        _endReason = event.reason;
        _watching = false;
    }
    notifyListeners();
  }

  void _notify() => notifyListeners();
}

/// What the host last answered, per scope, plus when.
class OrchestrationSnapshot {
  const OrchestrationSnapshot({
    this.projects = const [],
    this.runs = const [],
    this.work = const [],
    this.agents = const [],
    this.gates = const [],
    this.usage,
    this.refreshedAt,
  });

  final List<OrchestrationProject> projects;
  final List<OrchestrationRun> runs;
  final List<WorkItem> work;
  final List<OrchestrationAgent> agents;
  final List<OrchestrationGate> gates;
  final OrchestrationUsage? usage;

  /// When any scope was last refreshed from the host; null before the
  /// first answer.
  final DateTime? refreshedAt;

  bool get hasData => refreshedAt != null;

  OrchestrationSnapshot copyWith({
    List<OrchestrationProject>? projects,
    List<OrchestrationRun>? runs,
    List<WorkItem>? work,
    List<OrchestrationAgent>? agents,
    List<OrchestrationGate>? gates,
    OrchestrationUsage? usage,
    DateTime? refreshedAt,
  }) => OrchestrationSnapshot(
    projects: projects ?? this.projects,
    runs: runs ?? this.runs,
    work: work ?? this.work,
    agents: agents ?? this.agents,
    gates: gates ?? this.gates,
    usage: usage ?? this.usage,
    refreshedAt: refreshedAt ?? this.refreshedAt,
  );

  /// The provider JSON behind every item, for [OrchestrationStore].
  OrchestrationSnapshotCache toCache() => OrchestrationSnapshotCache(
    projects: [for (final p in projects) p.raw],
    runs: [for (final r in runs) r.raw],
    work: [for (final w in work) w.raw],
    agents: [for (final a in agents) a.raw],
    gates: [for (final g in gates) g.raw],
    usage: usage?.raw,
    refreshedAt: refreshedAt,
  );
}
