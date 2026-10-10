/// Product state of an agent. Total: every provider string lands on a
/// member, unrecognised ones on [unknown].
enum AgentState {
  working,
  idle,
  waiting,
  blocked,
  stopped,
  crashed,
  unknown;

  /// Maps a provider agent or session state without throwing.
  ///
  /// Gas City: agent `idle` → [idle]; `stopped` and `suspended` →
  /// [stopped]; session `active`/`running` → [working]; `error`/`failed`
  /// → [crashed].
  static AgentState fromProvider(String? value) {
    switch (_normalize(value)) {
      case 'working':
      case 'active':
      case 'running':
      case 'busy':
        return working;
      case 'idle':
        return idle;
      case 'waiting':
        return waiting;
      case 'blocked':
        return blocked;
      case 'stopped':
      case 'suspended':
      case 'paused':
      case 'exited':
        return stopped;
      case 'crashed':
      case 'error':
      case 'failed':
        return crashed;
      default:
        return unknown;
    }
  }
}

/// One agent (Gas City agent instance or pool member). [rawState] keeps the
/// provider state string that [state] was derived from.
class OrchestrationAgent {
  const OrchestrationAgent({
    required this.id,
    required this.name,
    required this.state,
    this.rawState,
    this.sessionId,
    this.sessionName,
    this.pool,
    this.pack,
    this.provider,
    this.currentWorkId,
    this.lastActivity,
    this.harness,
    this.model,
    this.contextPercent,
    this.workDir,
    this.branch,
    this.sessionStartedAt,
    this.suspended = false,
    this.unavailableReason,
    this.raw = const {},
    this.sessionState,
    this.sessionRunning,
  });

  final String id;
  final String name;
  final AgentState state;

  /// True when the host reports the agent suspended (Gas City
  /// `suspended=true` or `state=suspended`): a named agent that exists on
  /// the host but is switched off. [state] is [AgentState.stopped].
  final bool suspended;

  /// The host's own words for why it cannot run the agent (Gas City
  /// `unavailable_reason`, set with `available=false`), exactly as received;
  /// null when the agent is available.
  final String? unavailableReason;

  /// Provider state string exactly as received; null when absent.
  final String? rawState;

  /// Live session id when the agent is attached to one.
  final String? sessionId;
  final String? sessionName;

  /// Pool template the instance was spawned from (`ocproof/gastown.polecat`).
  final String? pool;
  final String? pack;

  /// Model provider the agent runs on, when reported.
  final String? provider;
  final String? currentWorkId;
  final DateTime? lastActivity;

  /// The harness the agent runs in, as the provider names it for people
  /// (`OpenCode`); null when not reported.
  final String? harness;

  /// Model id when reported (`anthropic/claude-sonnet-4`).
  final String? model;

  /// Context window used, 0–100, when the provider reports it.
  final int? contextPercent;

  /// The session's working directory (a worktree for pool instances).
  final String? workDir;

  /// Branch the session works on, when the provider's metadata names it.
  final String? branch;

  /// When the session was created, for the session age.
  final DateTime? sessionStartedAt;

  /// Untouched provider payload.
  final Map<String, Object?> raw;

  /// The joined session's own `state` string as received (`active`,
  /// `asleep`, ...); null when no session was joined or it named none.
  /// The session is the truth about whether the agent runs: Gas City's
  /// `/agents` can say `stopped` while `/sessions` says `active`.
  final String? sessionState;

  /// The joined session's `running` flag; null when no session was joined.
  final bool? sessionRunning;

  @override
  String toString() => 'OrchestrationAgent($id, $state)';
}

/// Lower snake_case of a provider string: trims, splits camelCase, and
/// folds spaces and dashes to underscores, so `needsInput`, `needs-input`
/// and `Needs Input` all compare equal.
String _normalize(String? value) => value == null
    ? ''
    : value
          .trim()
          .replaceAllMapped(
            RegExp(r'([a-z0-9])([A-Z])'),
            (m) => '${m[1]}_${m[2]}',
          )
          .toLowerCase()
          .replaceAll(RegExp(r'[\s-]+'), '_');
