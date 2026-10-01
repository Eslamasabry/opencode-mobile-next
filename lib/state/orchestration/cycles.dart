part of '../orchestration.dart';

extension OrchestrationControllerCycles on OrchestrationController {
  // -------------------------------------------------------------------------
  // Dispatch cycle (TEAM-116)
  // -------------------------------------------------------------------------

  /// Where the work item [workId] is in the host's dispatch chain and why
  /// it waits, derived from the snapshot, the timeline and the agent's
  /// transcript at this moment ([DispatchCycle.none] for an unknown id).
  /// Cached until the next event, refresh, mutation or tick.
  DispatchCycle cycleFor(String workId) {
    final cached = _cycles[workId];
    if (cached != null) return cached;
    final item = _workById(workId);
    if (item == null) return DispatchCycle.none;
    final now = _now();
    OrchestrationRun? run;
    if (item.runId case final runId?) {
      for (final candidate in _snapshot.runs) {
        if (candidate.id == runId) {
          run = candidate;
          break;
        }
      }
    }
    final cycle = deriveDispatchCycle(
      item: item,
      now: now,
      timeline: _timeline,
      agents: _snapshot.agents,
      transcript: cycleTranscriptFor(workId),
      runCompleted: run?.state == RunState.completed,
      runCompletedAt: run?.updatedAt,
    );
    _cycles[workId] = cycle;
    if (!cycle.isTerminal) _cyclesMoving = true;
    _syncCycleProbe(item, cycle);
    _syncCycleTimer();
    return cycle;
  }

  /// The least-advanced tracked item of the run [runId]: the batch's
  /// cycle is its cycle. Cancelled and failed items are passed over
  /// unless every item is one. Null when the run tracks no work.
  String? cycleWorkForRun(String runId) {
    WorkItem? best;
    DispatchCycle? bestCycle;
    var bestSkipped = true;
    for (final item in _snapshot.work) {
      if (item.runId != runId) continue;
      final skipped =
          item.state == WorkState.cancelled || item.state == WorkState.failed;
      final cycle = cycleFor(item.id);
      if (best == null ||
          (bestSkipped && !skipped) ||
          (bestSkipped == skipped && _lessAdvanced(cycle, bestCycle!))) {
        best = item;
        bestCycle = cycle;
        bestSkipped = skipped;
      }
    }
    return best?.id;
  }

  /// [cycleFor] of [cycleWorkForRun]; null when the run tracks no work.
  DispatchCycle? cycleForRun(String runId) {
    final workId = cycleWorkForRun(runId);
    return workId == null ? null : cycleFor(workId);
  }

  static bool _lessAdvanced(DispatchCycle a, DispatchCycle b) {
    final byStep = a.step.index.compareTo(b.step.index);
    if (byStep != 0) return byStep < 0;
    final at = a.since, bt = b.since;
    if (at == null || bt == null) return false;
    return at.isBefore(bt);
  }

  /// The agent working [workId] (by the item's session), for the strip's
  /// Open agent output and Stop: the agent's id when the snapshot lists
  /// it, else the session id (which [agentOutput] resolves too). Null
  /// when the item names no session.
  String? cycleAgentFor(String workId) {
    final sessionId = _workById(workId)?.sessionId;
    if (sessionId == null || sessionId.isEmpty) return null;
    return _agentById(sessionId)?.id ?? sessionId;
  }

  /// The rig's refinery agent for [workId] (its name ends in `refinery`
  /// and its rig matches the item's), for Nudge refinery. Null when the
  /// snapshot lists none.
  String? cycleRefineryFor(String workId) {
    final item = _workById(workId);
    if (item == null) return null;
    final rig = item.projectId;
    OrchestrationAgent? found;
    for (final agent in _snapshot.agents) {
      final name = agent.name.toLowerCase();
      if (!name.contains('refinery')) continue;
      final agentRig = agent.name.contains('/')
          ? agent.name.substring(0, agent.name.indexOf('/'))
          : null;
      if (rig != null && agentRig != null && agentRig != rig) continue;
      found = agent;
      if (agentRig == rig) break;
    }
    return found?.id;
  }

  /// The transcript the app holds for the agent on [workId]: the output
  /// tail a screen watched, or what the cycle probe read. Null when none.
  String? cycleTranscriptFor(String workId) {
    final sessionId = _workById(workId)?.sessionId;
    if (sessionId == null || sessionId.isEmpty) return null;
    final agent = _agentById(sessionId);
    final tail =
        (agent == null ? null : _outputs[agent.id]) ?? _outputs[sessionId];
    final watched = tail?.text ?? '';
    final probed = _cycleTranscripts[sessionId] ?? '';
    final text = watched.length >= probed.length ? watched : probed;
    return text.isEmpty ? null : text;
  }

  /// A strip is on screen: keeps the tick running while some cycle moves
  /// and lets the controller read a claimed agent's transcript. Paired
  /// with one [unwatchCycles].
  void watchCycles() {
    _cycleWatchers += 1;
    // Cycles derived before anyone watched opened no probe: derive again.
    _cycles.clear();
    _syncCycleTimer();
  }

  /// Releases one [watchCycles]; the last one stops the tick and the
  /// transcript probes (their text stays).
  void unwatchCycles() {
    if (_cycleWatchers == 0) return;
    _cycleWatchers -= 1;
    if (_cycleWatchers > 0) return;
    _cycleTimer?.cancel();
    _cycleTimer = null;
    for (final probe in _cycleProbes.values) {
      unawaited(probe.cancel());
    }
    _cycleProbes.clear();
  }

  /// True while the 30 s tick runs (tests read it).
  bool get debugCycleTicking => _cycleTimer != null;

  /// Sessions the controller reads the transcript of for their cycle.
  Set<String> get debugCycleProbes => Set.unmodifiable(_cycleProbes.keys);

  WorkItem? _workById(String id) {
    for (final item in _snapshot.work) {
      if (item.id == id) return item;
    }
    return null;
  }

  void _syncCycleTimer() {
    final wanted = _cycleWatchers > 0 && _cyclesMoving && !_stoppedMeanwhile;
    if (wanted == (_cycleTimer != null)) return;
    if (!wanted) {
      _cycleTimer?.cancel();
      _cycleTimer = null;
      return;
    }
    _cycleTimer = Timer.periodic(cycleTick, (_) {
      if (_stoppedMeanwhile) {
        _cycleTimer?.cancel();
        _cycleTimer = null;
        return;
      }
      // Nothing re-derived a moving cycle since the last tick: no strip
      // is asking, so stop until one does.
      if (!_cyclesMoving) {
        _cycleTimer?.cancel();
        _cycleTimer = null;
        return;
      }
      _notify();
    });
  }

  /// While a strip watches, reads the transcript of the agent on [item]
  /// once it claimed and until it pushed, so a provider limit is seen.
  /// The text lives beside the output tails and never opens or closes
  /// what a screen watches.
  void _syncCycleProbe(WorkItem item, DispatchCycle cycle) {
    final sessionId = item.sessionId;
    final wanted =
        _cycleWatchers > 0 &&
        sessionId != null &&
        sessionId.isNotEmpty &&
        !cycle.isTerminal &&
        (cycle.step == DispatchStep.claimed ||
            cycle.step == DispatchStep.working ||
            cycle.step == DispatchStep.pushed);
    if (!wanted) {
      if (sessionId != null) {
        final probe = _cycleProbes.remove(sessionId);
        if (probe != null) unawaited(probe.cancel());
      }
      return;
    }
    if (_cycleProbes.containsKey(sessionId) ||
        _cycleProbesEnded.contains(sessionId)) {
      return;
    }
    final gateway = _gateway;
    final source = gateway is OrchestrationAgentOutputGateway
        ? gateway as OrchestrationAgentOutputGateway
        : null;
    if (source == null || !_capabilities.agentOutput || _stoppedMeanwhile) {
      return;
    }
    _cycleProbes[sessionId] = source
        .agentOutput(sessionId)
        .listen(
          (event) {
            if (event is! AgentOutputText) return;
            final before = _cycleTranscripts[sessionId] ?? '';
            var text = mergeAgentOutput(before, event.text);
            if (text.length > agentOutputLimit) {
              text = text.substring(text.length - agentOutputLimit);
            }
            _cycleTranscripts[sessionId] = text;
            final limit = dispatchProviderLimitPattern.hasMatch(text);
            // Only what changes the cycle notifies: the first text
            // (working evidence) and a provider limit appearing.
            final limitWasSeen = _cycleLimitSeen.contains(sessionId);
            if (limit) _cycleLimitSeen.add(sessionId);
            if (before.isEmpty || limit != limitWasSeen) _notify();
          },
          onError: (Object _) {},
          onDone: () {
            _cycleProbes.remove(sessionId);
            _cycleProbesEnded.add(sessionId);
          },
        );
  }
}
