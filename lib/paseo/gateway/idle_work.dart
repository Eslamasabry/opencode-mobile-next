part of '../gateway.dart';

/// Helper inventory is separate from the visible project's session cache.
/// Only complete snapshots plus ordered turn boundaries prove idle; time and
/// expiring CPU leases are never evidence that work finished.
final class _PaseoWorkInventory {
  final changes = StreamController<void>.broadcast(sync: true);
  final agents = <String, bool?>{};
  final turns = <String>{};
  final permissions = <String, String>{};
  final resolvedPermissions = <String>{};
  final livePermissions = <String, String>{};
  final activeChildren = <String>{};
  final coveredParents = <String>{};
  final parents = <String>{};
  final retiredStreamEpochs = <String, Set<String>>{};
  final children = <String, ({String parent, bool? busy})>{};
  final touched = <String, int>{};
  final childTouched = <String, int>{};
  final sequences = <String, ({String epoch, int seq})>{};
  int epoch = -1, revision = 0, brokenRevision = -1;
  bool complete = false;
  _PaseoWorkScan? scan;

  bool? read(bool connected, int currentEpoch) {
    if (turns.isNotEmpty ||
        activeChildren.isNotEmpty ||
        permissions.isNotEmpty ||
        agents.values.contains(true) ||
        children.values.any((v) => v.busy == true)) {
      return true;
    }
    if (!connected ||
        epoch != currentEpoch ||
        !complete ||
        !parents.every(coveredParents.contains) ||
        brokenRevision >= 0 ||
        agents.values.contains(null) ||
        children.values.any((v) => v.busy == null)) {
      return null;
    }
    return false;
  }

  void changed() {
    if (!changes.isClosed) changes.add(null);
  }

  void invalidate() {
    complete = false;
    scan = null;
    changed();
  }

  void malformed() {
    brokenRevision = ++revision;
    changed();
  }

  bool? status(Object? value) => switch (value) {
    'initializing' ||
    'running' ||
    'busy' ||
    'waiting' ||
    'waiting_permission' ||
    'awaiting_permission' => true,
    'idle' ||
    'closed' ||
    'error' ||
    'completed' ||
    'canceled' ||
    'cancelled' ||
    'failed' => false,
    _ => null,
  };

  void remove(String id, {bool explicit = true}) {
    agents.remove(id);
    parents.remove(id);
    coveredParents.remove(id);
    if (explicit) {
      turns.remove(id);
      permissions.removeWhere((_, parent) => parent == id);
      livePermissions.removeWhere((_, parent) => parent == id);
      activeChildren.removeWhere((childId) => children[childId]?.parent == id);
      children.removeWhere((_, child) => child.parent == id);
      touched[id] = ++revision;
    } else {
      // A lagging archived/absent list row is not a live turn boundary.
      permissions.removeWhere(
        (requestId, parent) =>
            parent == id && !livePermissions.containsKey(requestId),
      );
      children.removeWhere(
        (childId, child) =>
            child.parent == id &&
            !activeChildren.contains(childId) &&
            child.busy != true,
      );
    }
  }

  void observeParent(String id) {
    parents.add(id);
    if (!coveredParents.contains(id)) complete = false;
  }

  bool agent(Object? raw, {int? snapshotRevision}) {
    if (raw is! Map || raw['id'] is! String || (raw['id'] as String).isEmpty) {
      return false;
    }
    final id = raw['id'] as String;
    if (snapshotRevision != null && (touched[id] ?? -1) > snapshotRevision) {
      return true;
    }
    if (raw['archivedAt'] is String) {
      remove(id, explicit: snapshotRevision == null);
      return true;
    }
    final busy = status(raw['status']);
    agents[id] = busy;
    // Even an unloaded parent can retain independently running children.
    observeParent(id);
    final pending = raw['pendingPermissions'];
    if (pending is! List) return false;
    permissions.removeWhere(
      (requestId, parent) =>
          parent == id && !livePermissions.containsKey(requestId),
    );
    for (final request in pending) {
      if (request is! Map || request['id'] is! String) return false;
      final requestId = request['id'] as String;
      if (!resolvedPermissions.contains(requestId)) permissions[requestId] = id;
    }
    if (snapshotRevision == null) touched[id] = ++revision;
    return busy != null;
  }

  bool child(Object? raw, {int? snapshotRevision}) {
    if (raw is! Map ||
        raw['id'] is! String ||
        raw['parentAgentId'] is! String) {
      return false;
    }
    final id = raw['id'] as String;
    observeParent(raw['parentAgentId'] as String);
    if (snapshotRevision != null &&
        (childTouched[id] ?? -1) > snapshotRevision) {
      return true;
    }
    final busy = status(raw['status']);
    if (snapshotRevision == null) {
      if (busy == true) {
        activeChildren.add(id);
      } else if (busy == false) {
        activeChildren.remove(id);
      }
    }
    children[id] = (parent: raw['parentAgentId'] as String, busy: busy);
    if (snapshotRevision == null) childTouched[id] = ++revision;
    return busy != null;
  }

  void startEpoch(int currentEpoch) {
    if (epoch != currentEpoch) {
      epoch = currentEpoch;
      agents.clear();
      turns.clear();
      permissions.clear();
      resolvedPermissions.clear();
      children.clear();
      activeChildren.clear();
      coveredParents.clear();
      parents.clear();
      retiredStreamEpochs.clear();
      livePermissions.clear();
      touched.clear();
      childTouched.clear();
      sequences.clear();
      brokenRevision = -1;
      complete = false;
      scan = null;
    }
  }

  _PaseoWorkScan? page(
    Map<String, dynamic> result,
    String? cursor,
    int currentEpoch,
    int requestRevision,
  ) {
    startEpoch(currentEpoch);
    if (cursor == null) {
      complete = false;
      scan = _PaseoWorkScan(requestRevision);
    }
    final active = scan;
    if (active == null || cursor != active.nextCursor) {
      malformed();
      return null;
    }
    final rows = result['entries'];
    final pageInfo = result['pageInfo'];
    if (rows is! List ||
        rows.length > 200 ||
        pageInfo is! Map ||
        pageInfo['hasMore'] is! bool) {
      active.valid = false;
      changed();
      return active;
    }
    active.parents.clear();
    for (final row in rows) {
      final raw = row is Map ? row['agent'] : null;
      if (!agent(raw, snapshotRevision: active.revision)) active.valid = false;
      if (raw is Map && raw['id'] is String) {
        final id = raw['id'] as String;
        active.seen.add(id);
        if (raw['archivedAt'] is! String) {
          active.parents.add(id);
        }
      }
    }
    final more = pageInfo['hasMore'] as bool;
    final next = pageInfo['nextCursor'];
    if (more &&
        (next is! String || next.isEmpty || !active.cursors.add(next))) {
      active.valid = false;
    }
    active.nextCursor = more && next is String && next.isNotEmpty ? next : null;
    active.lastPage = !more;
    changed();
    return active;
  }

  void finish(_PaseoWorkScan active) {
    if (!identical(scan, active)) return;
    if (active.lastPage && active.valid) {
      final removed = agents.keys
          .where(
            (id) =>
                !active.seen.contains(id) &&
                (touched[id] ?? -1) <= active.revision,
          )
          .toList();
      for (final id in removed) {
        remove(id, explicit: false);
      }
      complete = parents.every(coveredParents.contains);
      if (brokenRevision <= active.revision) brokenRevision = -1;
      scan = null;
    }
    changed();
  }

  void event(PaseoEvent event) {
    startEpoch(event.epoch);
    final p = event.payload;
    final raw = p['agentId'] ?? (p['agent'] is Map ? p['agent']['id'] : null);
    final id = raw is String && raw.isNotEmpty ? raw : null;
    switch (event.type) {
      case 'agent_update':
        if (p['kind'] == 'remove' && id != null) {
          remove(id);
        } else if (!agent(p['agent'])) {
          malformed();
        }
      case 'agent_deleted' || 'agent_archived':
        if (id != null) {
          remove(id);
        } else {
          malformed();
        }
      case 'agent_stream':
        final inner = p['event'];
        if (id == null || inner is! Map || inner['type'] is! String) {
          malformed();
          break;
        }
        observeParent(id);
        final seq = p['seq'], streamEpoch = p['epoch'];
        final boundary = const {
          'turn_started',
          'turn_completed',
          'turn_canceled',
          'turn_failed',
        }.contains(inner['type']);
        final ordered =
            seq is int &&
            seq >= 0 &&
            streamEpoch is String &&
            streamEpoch.isNotEmpty;
        if (boundary && !ordered) {
          // Starting work is safe to retain even without an ordered boundary;
          // an unorderable completion can never authorize idle.
          if (inner['type'] == 'turn_started') {
            turns.add(id);
            touched[id] = ++revision;
          }
          malformed();
          return;
        }
        if (ordered) {
          final previous = sequences[id];
          final retired = retiredStreamEpochs.putIfAbsent(id, () => <String>{});
          if (retired.contains(streamEpoch)) return;
          if (previous != null) {
            if (previous.epoch == streamEpoch && seq <= previous.seq) return;
            if (previous.epoch != streamEpoch) {
              if (inner['type'] != 'turn_started') {
                malformed();
                return;
              }
              retired.add(previous.epoch);
            }
          }
          sequences[id] = (epoch: streamEpoch, seq: seq);
        }
        switch (inner['type']) {
          case 'turn_started':
            turns.add(id);
            touched[id] = ++revision;
          case 'turn_completed' || 'turn_canceled' || 'turn_failed':
            turns.remove(id);
            agents[id] = false;
            touched[id] = ++revision;
          case 'permission_requested':
            final request = inner['request'];
            if (request is Map && request['id'] is String) {
              permissions[request['id'] as String] = id;
              livePermissions[request['id'] as String] = id;
              touched[id] = ++revision;
            } else {
              malformed();
            }
          case 'permission_resolved':
            resolve(inner['requestId']);
        }
      case 'agent_permission_request':
        final request = p['request'];
        if (id != null && request is Map && request['id'] is String) {
          observeParent(id);
          permissions[request['id'] as String] = id;
          livePermissions[request['id'] as String] = id;
          touched[id] = ++revision;
        } else {
          malformed();
        }
      case 'agent_permission_resolved':
        resolve(p['requestId']);
      case 'agent.provider_subagents.update':
        switch (p['kind']) {
          case 'upsert':
            if (!child(p['subagent'])) malformed();
          case 'remove':
            if (p['subagentId'] is String) {
              final childId = p['subagentId'] as String;
              children.remove(childId);
              activeChildren.remove(childId);
              childTouched[childId] = ++revision;
            } else {
              malformed();
            }
          case 'timeline':
            break;
          default:
            malformed();
        }
    }
    changed();
  }

  void resolve(Object? requestId) {
    if (requestId is! String) {
      malformed();
      return;
    }
    final parent = permissions.remove(requestId);
    livePermissions.remove(requestId);
    resolvedPermissions.add(requestId);
    if (parent != null) touched[parent] = ++revision;
  }
}

final class _PaseoWorkScan {
  _PaseoWorkScan(this.revision);
  final int revision;
  final seen = <String>{}, cursors = <String>{}, parents = <String>{};
  final budget = Stopwatch()..start();
  int checkedParents = 0;
  String? nextCursor;
  bool valid = true, lastPage = false;
}

extension _PaseoLocalWork on PaseoGateway {
  bool? _readLocalWorkBusy() {
    if (_closed || !_trackLocalWork) return null;
    if (_pendingPayloadWork > 0 ||
        _awaitingTurn.isNotEmpty ||
        _turnActive.isNotEmpty ||
        _uncertain.isNotEmpty) {
      return true;
    }
    return _localWork.read(transport.connected, transport.epoch);
  }

  Future<void> _observeWorkPage(
    Map<String, dynamic> result,
    String? cursor,
    int requestRevision,
  ) async {
    if (!_trackLocalWork || _closed) return;
    final epoch = transport.epoch;
    final active = _localWork.page(result, cursor, epoch, requestRevision);
    if (active == null) return;
    final parents = active.parents.toList();
    // A bounded observation that cannot finish stays unknown. It never labels
    // the unchecked part idle and never scales a list read without a bound.
    for (var offset = 0; offset < parents.length; offset += 4) {
      if (active.checkedParents >= 64 ||
          active.budget.elapsed >= const Duration(seconds: 8)) {
        active.valid = false;
        break;
      }
      final batch = parents.skip(offset).take(4).toList();
      active.checkedParents += batch.length;
      await Future.wait(
        batch.map((parent) async {
          try {
            final children = await transport.request(
              'agent.provider_subagents.list.request',
              {'parentAgentId': parent},
              timeout: const Duration(seconds: 2),
              expectedEpoch: epoch,
            );
            if (_closed ||
                transport.epoch != epoch ||
                !identical(_localWork.scan, active)) {
              return;
            }
            final rows = children['subagents'];
            if (children['error'] != null ||
                children['parentAgentId'] != parent ||
                rows is! List ||
                rows.length > 500) {
              active.valid = false;
              return;
            }
            _localWork.coveredParents.add(parent);
            final seen = <String>{};
            for (final row in rows) {
              if (row is! Map ||
                  row['parentAgentId'] != parent ||
                  !_localWork.child(row, snapshotRevision: active.revision)) {
                active.valid = false;
              } else {
                seen.add(row['id'] as String);
              }
            }
            _localWork.children.removeWhere(
              (id, child) =>
                  child.parent == parent &&
                  !seen.contains(id) &&
                  !_localWork.activeChildren.contains(id) &&
                  (_localWork.childTouched[id] ?? -1) <= active.revision,
            );
          } catch (_) {
            active.valid = false;
          }
        }),
      );
    }
    if (!_closed && transport.epoch == epoch) _localWork.finish(active);
  }
}
