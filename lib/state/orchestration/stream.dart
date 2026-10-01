part of '../orchestration.dart';

extension OrchestrationControllerStream on OrchestrationController {
  // -------------------------------------------------------------------------
  // Agent output
  // -------------------------------------------------------------------------

  /// The cached output of [agentId], never null; empty and unavailable
  /// until [watchAgentOutput] opened a stream for it.
  AgentOutputTail agentOutput(String agentId) =>
      _outputs[agentId] ?? _tailFor(agentId);

  /// Opens (or keeps open) the output stream of [agentId] and returns its
  /// tail. Every call is paired with one [unwatchAgentOutput]; the stream
  /// closes when the last watcher leaves, the text stays.
  AgentOutputTail watchAgentOutput(String agentId) {
    final tail = _tailFor(agentId);
    _outputWatchers[agentId] = (_outputWatchers[agentId] ?? 0) + 1;
    _openOutput(tail);
    return tail;
  }

  /// Releases one watch taken by [watchAgentOutput].
  void unwatchAgentOutput(String agentId) {
    final count = (_outputWatchers[agentId] ?? 0) - 1;
    if (count > 0) {
      _outputWatchers[agentId] = count;
      return;
    }
    _outputWatchers.remove(agentId);
    final subscription = _outputSubscriptions.remove(agentId);
    if (subscription != null) unawaited(subscription.cancel());
    final tail = _outputs[agentId];
    if (tail != null && tail._watching) {
      tail._watching = false;
      tail._notify();
    }
  }

  AgentOutputTail _tailFor(String agentId) {
    final existing = _outputs[agentId];
    if (existing != null) return existing;
    String? sessionId;
    for (final agent in _snapshot.agents) {
      if (agent.id == agentId || agent.sessionId == agentId) {
        sessionId = agent.sessionId;
        break;
      }
    }
    final tail = AgentOutputTail(agentId, sessionId: sessionId);
    _outputs[agentId] = tail;
    return tail;
  }

  void _openOutput(AgentOutputTail tail) {
    if (_outputSubscriptions.containsKey(tail.agentId) || tail.ended) return;
    final gateway = _gateway;
    final sessionId = tail.sessionId;
    // The output interface sits beside the gateway rather than inside it,
    // so a checked value does not promote and needs the cast.
    final source = gateway is OrchestrationAgentOutputGateway
        ? gateway as OrchestrationAgentOutputGateway
        : null;
    if (_stoppedMeanwhile ||
        source == null ||
        !_capabilities.agentOutput ||
        sessionId == null) {
      tail._available = false;
      tail._notify();
      return;
    }
    tail._available = true;
    tail._watching = true;
    tail._error = null;
    _outputSubscriptions[tail.agentId] = source
        .agentOutput(sessionId)
        .listen(
          tail._apply,
          onError: (Object error) {
            tail._error = error;
            tail._notify();
          },
          onDone: () {
            _outputSubscriptions.remove(tail.agentId);
            if (tail._watching) {
              tail._watching = false;
              tail._notify();
            }
          },
        );
    tail._notify();
  }

  // -------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------

  void _subscribe(OrchestrationGateway gateway) {
    if (gateway is GasCityGateway) {
      // The raw client exposes the connection status and head-only replays
      // the plain event stream folds away.
      final sse = gateway.openStream(resumeFrom: _cursor);
      _sse = sse;
      _status = sse.status.listen(_setStreamStatus);
      _replays = sse.headOnlyReplay.listen(
        (replay) => _onEvent(
          StreamHeadOnlyReplay(
            requestedSeq: replay.requestedSeq,
            firstSeq: replay.firstSeq,
          ),
        ),
      );
      _events = sse.frames
          .map(mapStreamFrame)
          .listen(_onEvent, onError: (Object _) {});
      _setStreamStatus(sse.currentStatus);
      return;
    }
    _events = gateway
        .events(resumeFrom: _cursor)
        .listen(
          _onEvent,
          onError: (Object _) =>
              _setStreamStatus(OrchestrationStreamStatus.reconnecting),
          onDone: () => _setStreamStatus(OrchestrationStreamStatus.closed),
        );
    _setStreamStatus(OrchestrationStreamStatus.live);
  }

  /// Cancels are not awaited, as `ConnectionController._retireTransport`
  /// does: a cancelled subscription answers from the root zone, which
  /// never resumes a caller inside a fake-async test. Closing the SSE
  /// client is what actually ends the connection, and that is awaited.
  Future<void> _unsubscribe() async {
    final replays = _replays;
    final status = _status;
    final events = _events;
    final sse = _sse;
    _replays = null;
    _status = null;
    _events = null;
    _sse = null;
    if (replays != null) unawaited(replays.cancel());
    if (status != null) unawaited(status.cancel());
    if (events != null) unawaited(events.cancel());
    if (sse != null) await sse.close();
  }

  /// Marks scopes dirty per §5 and appends to the timeline; heartbeats only
  /// refresh liveness.
  void _onEvent(OrchestrationEvent event) {
    if (_stoppedMeanwhile) return;
    _lastEventAt = _now();
    switch (event) {
      case StreamHeartbeat():
        _setStreamStatus(OrchestrationStreamStatus.live);
        return;
      case StreamHeadOnlyReplay():
        _cursor = EventCursor(seq: event.firstSeq - 1);
        _dirty.addAll(OrchestrationScope.all);
      case BeadChanged():
        _dirty
          ..add(OrchestrationScope.work)
          ..add(OrchestrationScope.runs)
          ..add(OrchestrationScope.gates);
        // A session bead (its id is the session id) changing is the only
        // sign Gas City 1.4.1 gives of a stopped or swept pool session
        // (write proof, TEAM-207): refresh the agents too.
        if (_agentById(event.beadId) != null) {
          _dirty.add(OrchestrationScope.agents);
        }
      case RunChanged():
        _dirty
          ..add(OrchestrationScope.runs)
          ..add(OrchestrationScope.run(event.runId));
      case SessionChanged():
        _dirty
          ..add(OrchestrationScope.agents)
          ..add(OrchestrationScope.agent(event.agentId ?? event.sessionId));
      case GateChanged():
        _dirty.add(OrchestrationScope.gates);
      case RequestResult():
        _dirty.add(OrchestrationScope.activity);
      case ActivityAppended():
        _dirty.add(OrchestrationScope.activity);
      case UnknownOrchestrationEvent():
        break;
    }
    if (event is! StreamHeadOnlyReplay) {
      if (event.seq != null) _cursor = _cursor.advance(event.seq);
      _append(event);
      _settleMutations(event);
    }
    _setStreamStatus(OrchestrationStreamStatus.live);
    _scheduleRefetch();
    _notify();
  }

  void _append(OrchestrationEvent event) {
    final seq = event.seq;
    if (seq != null) {
      final known = _timelineSeq;
      if (known != null && seq <= known) return;
      _timelineSeq = seq;
    }
    _timeline.add(event);
    if (_timeline.length > timelineLimit) {
      _timeline.removeRange(0, _timeline.length - timelineLimit);
    }
  }

  void _scheduleRefetch() {
    if (_dirty.isEmpty) return;
    _debounce?.cancel();
    _debounce = Timer(refreshDebounce, () {
      _debounce = null;
      unawaited(_refetchDirty());
    });
  }

  // -------------------------------------------------------------------------
  // Refresh
  // -------------------------------------------------------------------------

  /// Refetches the dirty scopes; a call during a refetch runs once more
  /// after it so no dirty mark is lost.
  Future<void> _refetchDirty() {
    final running = _refresh;
    if (running != null) {
      _refreshQueued = true;
      return running;
    }
    final run = _refetchNow().whenComplete(() {
      _refresh = null;
      if (_refreshQueued) {
        _refreshQueued = false;
        if (_dirty.isNotEmpty) unawaited(_refetchDirty());
      }
    });
    _refresh = run;
    return run;
  }

  Future<void> _refetchNow() async {
    final gateway = _gateway;
    if (gateway == null || _stoppedMeanwhile || _dirty.isEmpty) return;
    final scopes = Set.of(_dirty);
    _dirty.clear();
    var next = _snapshot;
    var succeeded = false;
    OrchestrationError? failure;

    // Each fetch awaits its read first and only then folds the result into
    // [next], so concurrent scopes never overwrite each other's update.
    Future<void> fetch(String scope, Future<_Fold> Function() load) async {
      try {
        final fold = await load();
        if (_stoppedMeanwhile) return;
        next = fold(next);
        succeeded = true;
      } catch (error) {
        failure ??= OrchestrationError(
          OrchestrationErrorKind.readFailed,
          '$scope: $error',
        );
      }
    }

    final fetches = <Future<void>>[];
    if (scopes.contains(OrchestrationScope.projects)) {
      fetches.add(
        fetch(OrchestrationScope.projects, () async {
          final projects = await gateway.projects();
          return (OrchestrationSnapshot s) => s.copyWith(projects: projects);
        }),
      );
      // The policy rides with the projects scope: it describes a rig and
      // changes only when the host's owner edits the rig config.
      if (_policies case final policies?) {
        fetches.add(
          fetch('policy', () async {
            final policy = await policies.policy();
            return (OrchestrationSnapshot s) {
              _policy = policy;
              return s;
            };
          }),
        );
      }
    }
    if (scopes.contains(OrchestrationScope.runs)) {
      fetches.add(
        fetch(OrchestrationScope.runs, () async {
          final runs = await gateway.runs();
          return (OrchestrationSnapshot s) => s.copyWith(runs: runs);
        }),
      );
    } else {
      for (final id in _ids(scopes, 'run:')) {
        fetches.add(
          fetch(OrchestrationScope.run(id), () async {
            final run = await gateway.run(id);
            return (OrchestrationSnapshot s) =>
                s.copyWith(runs: _merge(s.runs, run, (r) => r.id == id));
          }),
        );
      }
    }
    if (scopes.contains(OrchestrationScope.work)) {
      fetches.add(
        fetch(OrchestrationScope.work, () async {
          final work = await gateway.work();
          return (OrchestrationSnapshot s) => s.copyWith(work: work);
        }),
      );
    }
    if (scopes.contains(OrchestrationScope.agents)) {
      fetches.add(
        fetch(OrchestrationScope.agents, () async {
          final agents = await gateway.agents();
          return (OrchestrationSnapshot s) => s.copyWith(agents: agents);
        }),
      );
    } else {
      for (final id in _ids(scopes, 'agent:')) {
        fetches.add(
          fetch(OrchestrationScope.agent(id), () async {
            final agent = await gateway.agent(id);
            return (OrchestrationSnapshot s) => s.copyWith(
              agents: _merge(
                s.agents,
                agent,
                (a) =>
                    a.id == id ||
                    a.sessionId == id ||
                    (agent != null && a.id == agent.id),
              ),
            );
          }),
        );
      }
    }
    if (scopes.contains(OrchestrationScope.gates)) {
      fetches.add(
        fetch(OrchestrationScope.gates, () async {
          final gates = await gateway.gates();
          return (OrchestrationSnapshot s) => s.copyWith(gates: gates);
        }),
      );
    }
    if (scopes.contains(OrchestrationScope.usage)) {
      fetches.add(
        fetch(OrchestrationScope.usage, () async {
          final usage = await gateway.usage();
          return (OrchestrationSnapshot s) => s.copyWith(usage: usage);
        }),
      );
    }
    if (scopes.contains(OrchestrationScope.activity)) {
      fetches.add(
        fetch(OrchestrationScope.activity, () async {
          final page = await gateway.activity(afterSeq: _timelineSeq);
          return (OrchestrationSnapshot s) {
            for (final event in page) {
              _append(ActivityAppended(event: event, seq: event.seq));
            }
            return s;
          };
        }),
      );
    }
    await Future.wait(fetches);
    if (_stoppedMeanwhile) return;

    if (succeeded) next = next.copyWith(refreshedAt: _now());
    _snapshot = next;
    _lastError = failure;
    if (succeeded) {
      final lastKnown = TeamLastKnown.of(
        asOf: next.refreshedAt!,
        runs: next.runs,
        agents: next.agents,
      );
      _lastKnown = lastKnown;
      _lastKnownRead = true;
      unawaited(_store.saveLastKnown(profile.id, lastKnown));
      unawaited(_store.saveSnapshot(profile.id, next.toCache()));
      unawaited(_store.saveCursor(profile.id, _cursor));
    }
    _notify();
  }

  static Iterable<String> _ids(Set<String> scopes, String prefix) => [
    for (final scope in scopes)
      if (scope.startsWith(prefix)) scope.substring(prefix.length),
  ];

  /// [items] with every entry [matches] replaced by [item] (appended when
  /// none matched, dropped when [item] is null).
  static List<T> _merge<T>(List<T> items, T? item, bool Function(T) matches) {
    final out = <T>[];
    var placed = false;
    for (final existing in items) {
      if (matches(existing)) {
        if (item != null && !placed) out.add(item);
        placed = true;
      } else {
        out.add(existing);
      }
    }
    if (!placed && item != null) out.add(item);
    return out;
  }
}
