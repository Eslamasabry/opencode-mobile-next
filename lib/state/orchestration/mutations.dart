part of '../orchestration.dart';

extension OrchestrationControllerMutations on OrchestrationController {
  // -------------------------------------------------------------------------
  // Mutations (TEAM-202)
  // -------------------------------------------------------------------------

  /// Every mutation of this profile the store knows, oldest first.
  List<MutationRecord> get mutations {
    final all = _mutations.values.toList()
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
    return List.unmodifiable(all);
  }

  /// One record by idempotency key.
  MutationRecord? mutation(String key) => _mutations[key];

  /// The answer a gate row shows: the newest record answering [gateId]
  /// that no retry superseded, or null when the gate was never answered
  /// from this device. Its [MutationRecord.status] picks the copy (sent →
  /// "Sent · waiting for the host to confirm", confirmed → "Answered",
  /// unconfirmed → "Sent, unconfirmed — check on the host before
  /// re-sending"); the row leaves the list only on confirmed.
  MutationRecord? mutationFor(String gateId) =>
      latestMutation(kind: MutationKind.respond, targetId: gateId);

  /// The newest record of [kind] (any when null) for [targetId] (any when
  /// null) that no retry superseded.
  MutationRecord? latestMutation({MutationKind? kind, String? targetId}) {
    MutationRecord? best;
    for (final record in _mutations.values) {
      if (record.retriedBy != null) continue;
      if (kind != null && record.kind != kind) continue;
      if (targetId != null && record.targetId != targetId) continue;
      if (best == null || record.createdAt.isAfter(best.createdAt)) {
        best = record;
      }
    }
    return best;
  }

  /// Answers the gate [gateId]. A gate already sent and not yet settled
  /// is not sent again: the existing record comes back.
  Future<MutationRecord> answerGate(String gateId, GateResponse response) =>
      mutate(MutationRequest.respond(gateId, response));

  /// Sends [text] to the agent [agentId].
  Future<MutationRecord> messageAgent(String agentId, String text) =>
      mutate(MutationRequest.message(agentId, text));

  /// Nudge / pause / resume / stop / restart the agent [agentId].
  Future<MutationRecord> controlAgent(
    String agentId,
    AgentControlAction action,
  ) => mutate(MutationRequest.controlAgent(agentId, action));

  /// Cancels the run [runId].
  Future<MutationRecord> cancelRun(String runId) =>
      mutate(MutationRequest.cancelRun(runId));

  /// Assigns the work item [workId] to the agent [agentId].
  Future<MutationRecord> assignWork(String workId, {required String agentId}) =>
      mutate(MutationRequest.assign(workId, agentId: agentId));

  /// Creates one work item (TEAM-306) titled [title] in the project
  /// [projectId]; the created id is the record's
  /// [MutationReceipt.createdId].
  Future<MutationRecord> createWork({
    required String title,
    String? description,
    String? projectId,
  }) => mutate(
    MutationRequest.createWork(
      title: title,
      description: description,
      projectId: projectId,
    ),
  );

  /// Gives one task straight to an agent when the planner is off
  /// (TEAM-306): creates the work item, then — when the host accepted it
  /// and answered its id — assigns it to [agentId] (a pool id such as
  /// `<rig>/gastown.polecat`). Work and runs are fetched again afterwards
  /// so the new item shows without waiting for the stream. `assigned` is
  /// null when the create was refused or came back without an id.
  ///
  /// [onCreated] hears the create's record as soon as the host answered
  /// it, before the assignment is sent (P6.3): the per-attempt signal a
  /// caller shows as "Task created · sending it to the team", never
  /// inferred from elapsed time.
  Future<({MutationRecord created, MutationRecord? assigned})> giveTask({
    required String title,
    String? description,
    required String projectId,
    required String agentId,
    ValueChanged<MutationRecord>? onCreated,
  }) async {
    final created = await createWork(
      title: title,
      description: description,
      projectId: projectId,
    );
    final receipt = created.receipt;
    final workId = receipt?.createdId;
    onCreated?.call(created);
    MutationRecord? assigned;
    if (receipt != null && receipt.isAccepted && workId != null) {
      assigned = await assignWork(workId, agentId: agentId);
    }
    if (!_disposed && !_stoppedMeanwhile) {
      _dirty
        ..add(OrchestrationScope.work)
        ..add(OrchestrationScope.runs);
      unawaited(_refetchDirty());
    }
    return (created: created, assigned: assigned);
  }

  /// Approves the merge request [mergeRequestId] (TEAM-205). The run's
  /// readiness is fetched again once the host answered.
  Future<MutationRecord> approveMergeRequest(
    String mergeRequestId, {
    String? runId,
  }) async {
    final record = await mutate(MutationRequest.approveMerge(mergeRequestId));
    if (runId != null) unawaited(mergeReadiness(runId, force: true));
    return record;
  }

  /// Merges the run [runId] into the rig's default branch (TEAM-205).
  /// Only a person's confirmed tap calls this; the host still checks its
  /// readiness and boundaries. The readiness is fetched again afterwards.
  Future<MutationRecord> mergeRun(String runId) async {
    final record = await mutate(MutationRequest.merge(runId));
    unawaited(mergeReadiness(runId, force: true));
    return record;
  }

  // -------------------------------------------------------------------------
  // Merge readiness (TEAM-205)
  // -------------------------------------------------------------------------

  /// The cached readiness of [runId], null until [mergeReadiness] answered.
  MergeReadiness? mergeReadinessFor(String runId) => _mergeReadiness[runId];

  /// Why the last readiness read of [runId] failed, null when it did not.
  Object? mergeReadinessError(String runId) => _mergeReadinessErrors[runId];

  /// A readiness read of [runId] is in flight.
  bool mergeReadinessLoading(String runId) =>
      _mergeReadinessLoads.containsKey(runId);

  /// Fetches the host's readiness for [runId] and caches it per run until
  /// [refresh] or a merge mutation; a read already in flight is shared.
  /// Returns null when the host has no merge roles or the read failed
  /// (see [mergeReadinessError]). Never throws.
  Future<MergeReadiness?> mergeReadiness(String runId, {bool force = false}) {
    final running = _mergeReadinessLoads[runId];
    if (running != null) return running;
    if (!force && _mergeReadiness.containsKey(runId)) {
      return Future.value(_mergeReadiness[runId]);
    }
    final load = _loadMergeReadiness(runId).whenComplete(() {
      _mergeReadinessLoads.remove(runId);
      _notify();
    });
    _mergeReadinessLoads[runId] = load;
    _notify();
    return load;
  }

  Future<MergeReadiness?> _loadMergeReadiness(String runId) async {
    final merges = _merges;
    if (merges == null || _stoppedMeanwhile || !_capabilities.mergeReadiness) {
      return null;
    }
    try {
      final readiness = await merges.mergeReadiness(runId);
      if (_stoppedMeanwhile) return null;
      _mergeReadinessErrors.remove(runId);
      if (readiness == null) {
        _mergeReadiness.remove(runId);
      } else {
        _mergeReadiness[runId] = readiness;
      }
      return readiness;
    } catch (error) {
      if (_stoppedMeanwhile) return null;
      _mergeReadinessErrors[runId] = error;
      return null;
    }
  }

  /// Sends one write: mints a key, persists the record as sent, calls the
  /// gateway, applies the receipt and waits for the host's result (§6).
  /// Returns the record as it stands once the receipt is in; listeners
  /// hear every later change. Never throws: a gateway failure lands in
  /// the record as unconfirmed with the error as its receipt message.
  Future<MutationRecord> mutate(
    MutationRequest request, {
    String? retryOf,
  }) async {
    if (request.kind == MutationKind.respond && retryOf == null) {
      final existing = mutationFor(request.targetId);
      if (existing != null && existing.isSent) return existing;
    }
    final gateway = _gateway;
    final key = _mintKey();
    var record = MutationRecord(
      key: key,
      request: request,
      createdAt: _now(),
      status: MutationStatus.sent,
      retryOf: retryOf,
    );
    // Persist first: a crash from here on shows the record as unconfirmed
    // and never sends it again.
    _mutations[key] = record;
    await _store.mutations.save(profile.id, record);
    _notify();

    if (gateway == null || _stoppedMeanwhile || !_allowed(request.kind)) {
      return _update(
        key,
        status: MutationStatus.rejected,
        receipt: MutationReceipt.rejected(
          key,
          gateway == null || _stoppedMeanwhile
              ? 'not connected to the host'
              : 'the host does not allow this control',
        ),
      );
    }

    MutationReceipt receipt;
    try {
      receipt = await _send(gateway, request, key);
    } catch (error) {
      receipt = MutationReceipt(
        id: key,
        status: MutationReceiptStatus.pending,
        message: '$error',
      );
    }
    if (_disposed) return _mutations[key] ?? record;
    record = _applyReceipt(key, receipt);
    unawaited(_store.mutations.prune(profile.id));
    return record;
  }

  /// Sends [key]'s request again under a new key, when the record may be
  /// retried ([MutationRecord.canRetry]); the old record is marked as
  /// superseded. Null when there is nothing to retry. Only a person's tap
  /// calls this.
  Future<MutationRecord?> retryMutation(String key) async {
    final old = _mutations[key];
    if (old == null || !old.canRetry) return null;
    final next = await mutate(old.request, retryOf: key);
    _update(key, retriedBy: next.key);
    return next;
  }

  bool _allowed(MutationKind kind) => switch (kind) {
    MutationKind.respond => _capabilities.controlRespond,
    MutationKind.message => _capabilities.controlMessage,
    MutationKind.controlAgent => _capabilities.controlAgent,
    MutationKind.cancelRun => _capabilities.controlCancelRun,
    MutationKind.assign => _capabilities.controlAssign,
    MutationKind.createWork => _capabilities.controlCreateWork,
    MutationKind.approveMerge ||
    MutationKind.merge => _capabilities.mergeReadiness && _merges != null,
  };

  /// The policy side of the gateway, when the adapter has one.
  OrchestrationPolicyGateway? get _policies {
    final gateway = _gateway;
    return gateway is OrchestrationPolicyGateway
        ? gateway as OrchestrationPolicyGateway
        : null;
  }

  /// The merge side of the gateway, when the adapter has one.
  OrchestrationMergeGateway? get _merges {
    final gateway = _gateway;
    // The merge interface sits beside the gateway rather than inside it,
    // so a checked value does not promote and needs the cast.
    return gateway is OrchestrationMergeGateway
        ? gateway as OrchestrationMergeGateway
        : null;
  }

  Future<MutationReceipt> _send(
    OrchestrationGateway gateway,
    MutationRequest request,
    String key,
  ) => switch (request.kind) {
    MutationKind.approveMerge => _merges!.approveMerge(
      request.targetId,
      requestId: key,
    ),
    MutationKind.merge => _merges!.merge(request.targetId, requestId: key),
    MutationKind.respond => gateway.respond(
      request.targetId,
      request.response!,
      requestId: key,
    ),
    MutationKind.message => gateway.message(
      request.targetId,
      request.text ?? '',
      requestId: key,
    ),
    MutationKind.controlAgent => gateway.controlAgent(
      request.targetId,
      request.action ?? AgentControlAction.nudge,
      requestId: key,
    ),
    MutationKind.cancelRun => gateway.cancelRun(
      request.targetId,
      requestId: key,
    ),
    MutationKind.assign => gateway.assign(
      request.targetId,
      agentId: request.agentId ?? '',
      requestId: key,
    ),
    MutationKind.createWork => gateway.createWork(
      title: request.targetId,
      description: request.text,
      projectId: request.projectId,
      requestId: key,
    ),
  };

  /// Folds the gateway's receipt into the record: rejected settles it;
  /// accepted with a synchronous answer (HTTP 200) is confirmed, accepted
  /// with an asynchronous one (202) waits for the result event under the
  /// timer; pending (transport failure, front without a stored receipt)
  /// and timed-out are unconfirmed at once.
  MutationRecord _applyReceipt(String key, MutationReceipt receipt) {
    switch (receipt.status) {
      case MutationReceiptStatus.rejected:
        return _update(key, status: MutationStatus.rejected, receipt: receipt);
      case MutationReceiptStatus.pending:
      case MutationReceiptStatus.timedOut:
        return _update(
          key,
          status: MutationStatus.unconfirmed,
          receipt: receipt,
        );
      case MutationReceiptStatus.accepted:
        final early = receipt.correlationId == null
            ? null
            : _earlyResults.remove(receipt.correlationId);
        if (early != null) {
          return _update(
            key,
            status: early.ok
                ? MutationStatus.confirmed
                : MutationStatus.rejected,
            receipt: early.ok
                ? receipt
                : MutationReceipt(
                    id: key,
                    status: MutationReceiptStatus.rejected,
                    message: early.errorMessage ?? early.errorCode,
                    raw: receipt.raw,
                    hostRequestId: receipt.hostRequestId,
                    correlationId: receipt.correlationId,
                    upstreamStatus: receipt.upstreamStatus,
                  ),
          );
        }
        // A synchronous answer: 200, or 201 carrying the created resource
        // (createWork's bead) — there is no later result event to wait for.
        if (receipt.upstreamStatus == 200 ||
            (receipt.upstreamStatus == 201 && receipt.createdId != null)) {
          return _update(
            key,
            status: MutationStatus.confirmed,
            receipt: receipt,
          );
        }
        final record = _update(key, receipt: receipt);
        _armTimer(key);
        return record;
    }
  }

  void _armTimer(String key) {
    _mutationTimers[key]?.cancel();
    _mutationTimers[key] = Timer(mutationTimeout, () {
      _mutationTimers.remove(key);
      final record = _mutations[key];
      if (record == null || !record.isSent) return;
      _update(
        key,
        status: MutationStatus.unconfirmed,
        receipt: record.receipt == null
            ? MutationReceipt(id: key, status: MutationReceiptStatus.timedOut)
            : null,
      );
    });
  }

  /// A host event that proves a write happened (or failed) settles the
  /// matching record, whether it is still sent or already unconfirmed:
  ///
  /// | Event | Confirms |
  /// |---|---|
  /// | [RequestResult] with the receipt's correlation id | any kind (failed → rejected) |
  /// | [GateChanged] resolved | respond on that gate |
  /// | [RunChanged] cancelled / completed | cancelRun on that run |
  /// | [SessionChanged] stopped / woke | controlAgent stop, pause / resume, start, restart on that agent or session |
  /// | [BeadChanged] updated | assign on that work item; approveMerge on that request bead |
  /// | [BeadChanged] created | createWork whose receipt carries that bead's id |
  void _settleMutations(OrchestrationEvent event) {
    if (event is RequestResult) {
      var matched = false;
      for (final record in _mutations.values.toList()) {
        if (record.isSettled || record.correlationId != event.requestId) {
          continue;
        }
        matched = true;
        _update(
          record.key,
          status: event.ok ? MutationStatus.confirmed : MutationStatus.rejected,
          receipt: event.ok
              ? null
              : MutationReceipt(
                  id: record.key,
                  status: MutationReceiptStatus.rejected,
                  message: event.errorMessage ?? event.errorCode,
                  raw: record.receipt?.raw ?? const {},
                  hostRequestId: record.receipt?.hostRequestId,
                  correlationId: event.requestId,
                  upstreamStatus: record.receipt?.upstreamStatus,
                ),
        );
      }
      if (!matched) {
        _earlyResults[event.requestId] = event;
        if (_earlyResults.length > 64) {
          _earlyResults.remove(_earlyResults.keys.first);
        }
      }
      return;
    }
    for (final record in _mutations.values.toList()) {
      if (record.isSettled || !_confirms(record, event)) continue;
      _update(record.key, status: MutationStatus.confirmed);
    }
  }

  bool _confirms(MutationRecord record, OrchestrationEvent event) {
    final target = record.targetId;
    switch (event) {
      case GateChanged():
        return record.kind == MutationKind.respond &&
            event.resolved &&
            event.gateId == target;
      case RunChanged():
        return record.kind == MutationKind.cancelRun &&
            event.runId == target &&
            (event.state == RunState.cancelled ||
                event.state == RunState.completed);
      case SessionChanged():
        if (record.kind != MutationKind.controlAgent) return false;
        if (event.agentId != target && event.sessionId != target) {
          final agent = _agentById(target);
          if (agent == null || agent.sessionId != event.sessionId) {
            return false;
          }
        }
        return switch (record.request.action) {
          AgentControlAction.stop ||
          AgentControlAction.pause => event.change == SessionChange.stopped,
          AgentControlAction.resume ||
          AgentControlAction.start ||
          AgentControlAction.restart => event.change == SessionChange.woke,
          AgentControlAction.nudge || null => false,
        };
      case BeadChanged():
        if (event.change == BeadChange.closed) {
          // Proven live (docs/qa/ai-team/write-proof-2026-09-11.md): Gas
          // City 1.4.1 signals an explicit session stop as `bead.closed`
          // on the session bead and a convoy close as `bead.closed` on the
          // convoy bead, with no `session.stopped` / run event.
          if (record.kind == MutationKind.cancelRun) {
            return event.beadId == target;
          }
          if (record.kind == MutationKind.controlAgent &&
              (record.request.action == AgentControlAction.stop ||
                  record.request.action == AgentControlAction.pause)) {
            return event.beadId == target ||
                event.beadId == _agentById(target)?.sessionId;
          }
          return false;
        }
        if (record.kind == MutationKind.createWork) {
          return event.change == BeadChange.created &&
              event.beadId == record.receipt?.createdId;
        }
        return (record.kind == MutationKind.assign ||
                record.kind == MutationKind.approveMerge) &&
            event.beadId == target &&
            event.change == BeadChange.updated;
      case RequestResult() ||
          ActivityAppended() ||
          StreamHeartbeat() ||
          StreamHeadOnlyReplay() ||
          UnknownOrchestrationEvent():
        return false;
    }
  }

  OrchestrationAgent? _agentById(String id) {
    for (final agent in _snapshot.agents) {
      if (agent.id == id || agent.sessionId == id) return agent;
    }
    return null;
  }

  /// Updates and persists one record; settled records drop their timer.
  MutationRecord _update(
    String key, {
    MutationStatus? status,
    MutationReceipt? receipt,
    String? retriedBy,
  }) {
    final current = _mutations[key];
    if (current == null) {
      throw StateError('unknown mutation $key');
    }
    final next = current.copyWith(
      status: status,
      receipt: receipt,
      retriedBy: retriedBy,
      updatedAt: _now(),
    );
    _mutations[key] = next;
    if (next.status != MutationStatus.sent) {
      _mutationTimers.remove(key)?.cancel();
    }
    unawaited(_store.mutations.save(profile.id, next));
    _notify();
    return next;
  }

  /// Loads the profile's records. A record still sent when the app went
  /// away is shown as unconfirmed and never sent again (§6).
  Future<void> _loadMutations() async {
    if (_mutationsLoaded) return;
    _mutationsLoaded = true;
    for (final record in _store.mutations.read(profile.id)) {
      if (record.isSent) {
        final stale = record.copyWith(
          status: MutationStatus.unconfirmed,
          updatedAt: _now(),
        );
        _mutations[record.key] = stale;
        await _store.mutations.save(profile.id, stale);
      } else {
        _mutations[record.key] = record;
      }
    }
  }
}
