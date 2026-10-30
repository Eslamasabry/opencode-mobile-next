part of '../gateway.dart';

/// Keeps idle stored agents asleep and keeps their list time honest.
///
/// Incident 2026-10-27: reading an agent's history (`fetch_agent_timeline`)
/// makes the daemon load it: a provider process starts and `updatedAt` is
/// re-stamped to now. Background gen-UI recovery read the history of the 20
/// most recent chats after every reconnect, so idle chats from days ago
/// jumped to "Just now". Two defences live here:
///
/// 1. [genUiMayLoad]: background reads only touch agents the daemon already
///    holds in memory as far as this app can tell.
/// 2. [_guardedSession]: a list time that rises without evidence of real work
///    keeps its earlier value.
class _PaseoIdleGuard {
  /// Agents seen working (or hydrated, or sent to) since their time last
  /// moved: the daemon holds them in memory.
  final awake = <String>{};

  /// The prompt stamp and list time last shown per agent.
  final prompt = <String, int?>{};
  final shown = <String, int>{};

  /// A rise below this is the daemon's own bookkeeping, not a wake-up.
  static const tolerance = 1000;

  static bool working(Map<String, dynamic> agent) =>
      agent['status'] == 'running' ||
      agent['status'] == 'initializing' ||
      agent['activeTurn'] != null;

  /// A turn runs: the agent works on a prompt. An agent the daemon is only
  /// loading ("initializing" with no turn: the person opened its chat) is
  /// in memory but has done nothing, so it earns no new list time (jitter
  /// J10, 2026-10-28: an opened chat came back as "Done · Just now").
  static bool turning(Map<String, dynamic> agent) =>
      agent['status'] == 'running' || agent['activeTurn'] != null;

  /// The list time for [agent]: [updated] when the rise is backed by real
  /// activity, otherwise the time already shown. [saved] is the time the
  /// list showed last time the app ran: on a first sight it stands in for
  /// the time shown, so a load stamped while the app was closed does not
  /// move the row either; a prompt after it does.
  int? settle(
    String id,
    Map<String, dynamic> agent,
    int? updated, {
    int? Function()? saved,
  }) {
    final stamp = paseoMillis(agent['lastUserMessageAt']);
    var previous = shown[id];
    final before = prompt[id];
    prompt[id] = stamp;
    final isWorking = turning(agent);
    if (isWorking) awake.add(id);
    if (updated == null) return updated;
    final firstSight = previous == null;
    if (firstSight) previous = saved?.call();
    if (previous == null || updated <= previous + tolerance) {
      shown[id] = updated;
      return updated;
    }
    // A new prompt, a turn in progress, or a turn this app watched run is
    // real work (a long turn that ends hours after its prompt included).
    // On a first sight against the saved time, only a prompt sent after
    // that time is new.
    final newPrompt =
        stamp != null &&
        (firstSight
            ? stamp > previous + tolerance
            : before == null || stamp > before);
    final real = newPrompt || isWorking || awake.contains(id);
    if (!real) {
      shown[id] = previous;
      return previous;
    }
    shown[id] = updated;
    // A turn that has ended stops being evidence for the next rise.
    if (!isWorking) awake.remove(id);
    return updated;
  }
}

mixin _PaseoIdleGuardApi {
  final _guard = _PaseoIdleGuard();

  Map<String, Map<String, dynamic>> get _agents;
  Map<String, _PaseoLive> get _live;
  Set<String> get _awaitingTurn;
  Set<String> get _turnActive;
  Set<String> get _liveAgentSessions;
  String _app(String realID);
  String _real(String appID);

  /// The list time a conversation (by the daemon's id) had when the app
  /// last ran (its saved row), for a first sight; null when none is saved.
  int? Function(String realID)? savedListTime;

  /// True when a history read cannot wake [sessionID]'s agent: the person
  /// opened the chat, sent to it, or this app watched it work.
  bool genUiMayLoad(String sessionID) {
    final id = _app(sessionID);
    final agent = _agents[id];
    return _live.containsKey(id) ||
        _awaitingTurn.contains(id) ||
        _turnActive.contains(id) ||
        _liveAgentSessions.contains(id) ||
        _guard.awake.contains(id) ||
        (agent != null && _PaseoIdleGuard.working(agent));
  }

  /// [paseoSession] with the list time held back unless it is earned.
  Session _guardedSession(Map<String, dynamic> agent) {
    final session = paseoSession(agent);
    final time = session.time;
    final lookup = savedListTime;
    final settled = _guard.settle(
      session.id,
      agent,
      time?.updated,
      saved: lookup == null ? null : () => lookup(_real(session.id)),
    );
    if (time == null || settled == time.updated) return session;
    return session.copyWith(
      time: SessionTime(
        created: time.created,
        updated: settled,
        archived: time.archived,
        compacting: time.compacting,
        idle: time.idle,
        viewed: time.viewed,
      ),
    );
  }
}

extension _PaseoGenUiRecovery on PaseoGateway {
  ({String? promptStamp, bool idle})? _genUiRecoverySnapshot(String sessionID) {
    final id = _app(sessionID);
    final agent = _agents[id];
    if (_closed || agent == null) return null;
    String? stamp(Object? value) =>
        value is String && value.trim().isNotEmpty ? value : null;
    return (
      promptStamp:
          stamp(agent['lastUserMessageAt']) ?? stamp(agent['createdAt']),
      idle:
          const {'idle', 'closed', 'error'}.contains(agent['status']) &&
          agent['activeTurn'] == null &&
          !_awaitingTurn.contains(id) &&
          !_turnActive.contains(id),
    );
  }
}
