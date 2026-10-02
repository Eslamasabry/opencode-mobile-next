part of '../connection.dart';

// What needs the person across servers: attention feed, monitored requests and conversations elsewhere.

/// A conversation in a project other than the selected one, as the Work tab
/// lists it.
class ElsewhereConversation {
  const ElsewhereConversation({
    required this.session,
    required this.directory,
    required this.running,
    this.projectName,
  });

  final Session session;
  final String directory;
  final String? projectName;
  final bool running;

  /// The project's name as the server gives it, else its folder's name.
  String get project {
    final name = projectName?.trim();
    if (name != null && name.isNotEmpty) return name;
    final parts = directory.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? directory : parts.last;
  }
}

/// [ConnectionController]'s attention across servers.
mixin _ConnectionControllerAttention on ChangeNotifier {
  ConnectionController get _self;

  /// A monitor row is an observation, never authorization to reuse cached
  /// request content or another profile's active transport.
  Future<bool> prepareMonitoredRequest(MonitoredRoute target) =>
      _self._prepareMonitoredRequest(target);

  // Receipt times belong to gateway reads/events, never to a widget rebuild.
  final _attentionReads = <AttentionKind, DateTime>{};
  final _attentionEvents = <AttentionKind, DateTime>{};
  final _attentionReadRevisions = <AttentionKind, int>{};
  final _failedAttentionSessions = <String, ({DateTime at, int revision})>{};
  int _attentionTransportRevision = 0;

  /// One read-only projection for Inbox, Work and their badge. Merely reading
  /// it never polls, switches servers, acknowledges work or records an act.
  AttentionFeed get attentionFeed => _self._attentionFeed;

  /// Servers whose pending requests are not known now: zero lets the Inbox
  /// say "all caught up" for its scope. The current server answers from its
  /// own connection and request reads (the monitor's wider failed-run
  /// coverage is not needed to say no request waits here). Another saved
  /// server is in scope only while the person monitors it: one never opted
  /// into monitoring is outside the claim, not unknown, so a person who never
  /// turned monitoring on still reaches the all-caught-up state.
  int get unknownAttentionProfileCount => _self._unknownAttentionProfileCount;

  int get unifiedAttentionCount => attentionFeed.knownAttentionCount;

  String? _elsewhereProfileID;

  /// Conversations in other projects that are stopped on you.
  int get waitingElsewhereCount =>
      _self.elsewhereAttention.waitingCount(except: _self.directory);

  /// Conversations in this server's *other* projects: running ones first,
  /// then the most recently touched. Empty when the server cannot list
  /// across projects. Whether a conversation elsewhere is running is only
  /// known where the server reports activity server-wide (OpenCode 2); on
  /// other servers they are listed by recency alone.
  Future<List<ElsewhereConversation>> conversationsElsewhere({int limit = 6}) =>
      _self._conversationsElsewhere(limit: limit);
}

extension _ConnectionControllerAttentionImpl on ConnectionController {
  /// The body of [prepareMonitoredRequest].
  Future<bool> _prepareMonitoredRequest(MonitoredRoute target) async {
    if (isIsolated ||
        _disposed ||
        !isProfileReadable(target.profileID) ||
        !profileMonitor.rulesFor(target.profileID).enabled ||
        (target.createdAt.isAfter(DateTime.now()) ||
            DateTime.now().difference(target.createdAt) >
                const Duration(days: 1))) {
      return false;
    }
    final targetProfile = store.profiles
        .where((p) => p.id == target.profileID)
        .firstOrNull;
    if (targetProfile == null ||
        targetProfile.baseUrl != target.serverUrl ||
        target.sourceIdentity !=
            ProfileMonitor.routeSourceIdentity(targetProfile) ||
        (targetProfile.backend == ServerBackend.paseo
            ? targetProfile.requiresCodexTokenReentry
            : targetProfile.requiresPasswordReentry) ||
        !profileMonitor.supportsProfile(targetProfile)) {
      return false;
    }
    final before = (connectionRevision, locationRevision, profile?.id);
    final address = (
      targetProfile.baseUrl,
      targetProfile.username,
      targetProfile.password,
      targetProfile.flavor,
    );
    final pair = (_monitorGatewayFactory ?? _buildTransportPair)(targetProfile);
    try {
      pair.gateway.setLocation(
        directory: target.directory,
        workspace: target.workspace,
      );
      pair.operations.setLocation(
        directory: target.directory,
        workspace: target.workspace,
      );
      // A Paseo daemon knows its waiting requests once it has listed its
      // conversations, as the monitor that found this one did.
      if (targetProfile.backend == ServerBackend.paseo) {
        await pair.gateway
            .sessionPage(limit: 100)
            .timeout(const Duration(seconds: 8));
      }
      final found = switch (target.kind) {
        MonitoredRequestKind.permission =>
          (await ProfileMonitor.readPermissions(
            pair.gateway,
            const Duration(seconds: 8),
          )).any(
            (p) => p.id == target.requestID && p.sessionID == target.sessionID,
          ),
        MonitoredRequestKind.question =>
          (await ProfileMonitor.readQuestions(
            pair.gateway,
            pair.operations,
            const Duration(seconds: 8),
          )).any(
            (p) => p.id == target.requestID && p.sessionID == target.sessionID,
          ),
        MonitoredRequestKind.form =>
          pair.gateway.capabilities.forms &&
              (await pair.gateway.pendingForms().timeout(
                const Duration(seconds: 8),
              )).any(
                (p) =>
                    p.id == target.requestID && p.sessionID == target.sessionID,
              ),
        // A check-in has no server-side request to find: the session read
        // below is the whole check, and it opens whether or not the run is
        // still going — the user asked to look at it either way.
        MonitoredRequestKind.checkIn => true,
      };
      if (!found) return false;
      if (target.sessionID != 'global') {
        final session = await pair.gateway
            .session(target.sessionID)
            .timeout(const Duration(seconds: 8));
        if (session.id != target.sessionID ||
            (session.directory != null &&
                session.directory != target.directory) ||
            session.workspaceID != target.workspace) {
          return false;
        }
      }
    } catch (_) {
      return false;
    } finally {
      pair.gateway.close();
    }
    if (_disposed ||
        before != (connectionRevision, locationRevision, profile?.id) ||
        !isProfileReadable(target.profileID) ||
        !store.profiles.any(
          (p) =>
              p.id == target.profileID &&
              (p.baseUrl, p.username, p.password, p.flavor) == address,
        )) {
      return false;
    }
    if (profile?.id != target.profileID || !isConnected) {
      final connecting = connect(targetProfile);
      final expected = connectionRevision;
      await connecting;
      if (connectionRevision != expected) return false;
    }
    if (_disposed ||
        profile?.id != target.profileID ||
        !isConnected ||
        !isProfileReadable(target.profileID)) {
      return false;
    }
    var generation = connectionRevision;
    if (directory != target.directory || workspace != target.workspace) {
      final selecting = selectLocation(
        directory: target.directory,
        workspace: target.workspace,
      );
      generation = connectionRevision;
      await selecting;
    }
    if (_disposed ||
        connectionRevision != generation ||
        profile?.id != target.profileID ||
        directory != target.directory ||
        workspace != target.workspace ||
        locationLoading) {
      return false;
    }
    final location = locationRevision;
    await Future.wait([
      _syncPendingPermissions(),
      _syncPendingQuestions(),
      refreshPendingForms(),
    ]);
    return !_disposed &&
        generation == connectionRevision &&
        location == locationRevision &&
        profile?.id == target.profileID &&
        isProfileReadable(target.profileID);
  }

  void _observeAttentionRead(AttentionKind kind) {
    _attentionReads[kind] = DateTime.now();
    _attentionReadRevisions[kind] = _attentionTransportRevision;
  }

  /// The body of [attentionFeed].
  AttentionFeed get _attentionFeed {
    final now = DateTime.now();
    return AttentionFeed.fromServers([
      for (final saved in store.profiles)
        if (isProfileReadable(saved.id))
          AttentionServer(
            profileID: saved.id,
            name: saved.name,
            snapshot:
                saved.id == profile?.id &&
                    !isIsolated &&
                    _attentionUsesSavedSource(saved)
                ? _activeAttentionSnapshot(saved.id, now)
                : profileMonitor.snapshotFor(saved.id),
          ),
    ], now: now);
  }

  bool _attentionUsesSavedSource(ServerProfile saved) {
    final connected = _connectedProfile;
    return connected == null ||
        (
              connected.baseUrl,
              connected.username,
              connected.password,
              connected.backend,
              connected.codexToken,
              connected.flavor,
              connected.orchestration,
            ) ==
            (
              saved.baseUrl,
              saved.username,
              saved.password,
              saved.backend,
              saved.codexToken,
              saved.flavor,
              saved.orchestration,
            );
  }

  ProfileAttentionSnapshot _activeAttentionSnapshot(String id, DateTime now) {
    final monitored = profileMonitor.snapshotFor(id);
    final sameLocation =
        monitored.directory == directory && monitored.workspace == workspace;
    final dates = [..._attentionReads.values, ..._attentionEvents.values]
      ..sort();
    final checkedAt = dates.lastOrNull ?? monitored.checkedAt;
    bool readCurrent(AttentionKind kind) =>
        _attentionReadRevisions[kind] == _attentionTransportRevision;
    final requestsComplete =
        readCurrent(AttentionKind.permission) &&
        readCurrent(AttentionKind.question) &&
        (!supportsForms || readCurrent(AttentionKind.form));
    final requestsFailed =
        permissionsError != null ||
        questionsError != null ||
        formsError != null;
    final observations = <AttentionObservation>[];
    void request(String requestID, String sessionID, AttentionKind kind) {
      final session = sessionsById[sessionID];
      final receipts = [?_attentionEvents[kind], ?_attentionReads[kind]]
        ..sort();
      final at = receipts.lastOrNull;
      observations.add(
        AttentionObservation(
          id: requestID,
          kind: kind,
          facts: const WorkRowFacts(phase: WorkRowPhase.needsYou),
          observedAt: at ?? DateTime.fromMillisecondsSinceEpoch(0),
          isFresh: at != null && readCurrent(kind),
          sessionID: sessionID,
          requestID: requestID,
          title: session?.title,
          directory: session?.directory ?? directory,
          workspace: session?.workspaceID ?? workspace,
        ),
      );
    }

    for (final permission in awaitingPermissions) {
      request(permission.id, permission.sessionID, AttentionKind.permission);
    }
    for (final question in questions.values) {
      request(question.id, question.sessionID, AttentionKind.question);
    }
    for (final form in forms.values) {
      request(form.id, form.sessionID, AttentionKind.form);
    }
    if (_elsewhereProfileID == id) {
      observations.addAll(elsewhereAttention.observations(except: directory));
    }
    if (sameLocation) {
      observations.addAll(
        monitored.attention.where(
          (item) =>
              item.kind == AttentionKind.failedRun &&
              item.taskID == null &&
              item.runID == null &&
              !busySessions.contains(item.sessionID) &&
              !_deletedSessionIDs.contains(item.sessionID) &&
              !_failedAttentionSessions.containsKey(item.sessionID),
        ),
      );
    }
    for (final failure in _failedAttentionSessions.entries) {
      if (busySessions.contains(failure.key)) continue;
      final session = sessionsById[failure.key];
      observations.add(
        AttentionObservation(
          id: 'session-failure:${failure.key}',
          kind: AttentionKind.failedRun,
          facts: const WorkRowFacts(phase: WorkRowPhase.failed),
          observedAt: failure.value.at,
          isFresh: failure.value.revision == _attentionTransportRevision,
          sessionID: failure.key,
          title: session?.title,
          directory: session?.directory ?? directory,
          workspace: session?.workspaceID ?? workspace,
        ),
      );
    }
    final team = orchestration;
    final teamAt = team?.snapshot.refreshedAt;
    if (team != null && team.profileId == id && teamAt != null) {
      observations.addAll(
        MonitorAttentionReader.teamObservations(
          gates: team.snapshot.gates,
          work: team.snapshot.work,
          runs: team.snapshot.runs,
          observedAt: teamAt,
          directory: directory,
          workspace: workspace,
          isFresh:
              team.phase == OrchestrationPhase.ready &&
              !team.isStale &&
              !team.dirtyScopes.contains('gates') &&
              !team.dirtyScopes.contains('work'),
        ),
      );
    }
    return ProfileAttentionSnapshot(
      profileID: id,
      status: !isConnected
          ? ProfileMonitorStatus.paused
          : requestsFailed
          ? ProfileMonitorStatus.unavailable
          : requestsComplete
          ? ProfileMonitorStatus.current
          : ProfileMonitorStatus.checking,
      checkedAt: checkedAt,
      directory: directory,
      workspace: workspace,
      attention: List.unmodifiable(observations),
      complete: requestsComplete,
      // The selected transport does not enumerate all run histories. Its
      // requests can be current while the wider failed-run inventory is not.
      attentionComplete:
          sameLocation &&
          monitored.attentionComplete &&
          monitored.isCurrent &&
          (team == null || !team.isStale),
    );
  }

  /// The body of [unknownAttentionProfileCount].
  int get _unknownAttentionProfileCount {
    final active = profile?.id;
    return attentionFeed.servers.where((server) {
      if (server.profileID == active) {
        return !isConnected ||
            permissionsError != null ||
            questionsError != null ||
            formsError != null;
      }
      return server.state != AttentionCheckState.disabled && !server.isCurrent;
    }).length;
  }

  /// The body of [conversationsElsewhere].
  Future<List<ElsewhereConversation>> _conversationsElsewhere({
    int limit = 6,
  }) async {
    final currentRepository = repository;
    final currentApi = api;
    if (currentRepository == null ||
        currentApi == null ||
        !capabilities.globalSessionSearch) {
      return const [];
    }
    final generation = _generation;
    final here = directory;
    // The AI Team's agents keep their own sessions on this server, and a
    // busy team can fill a whole page with them; look a little further for
    // the person's own, then leave the team's out (see team_directories.dart).
    final items = <GlobalSessionResult>[];
    String? cursor;
    for (var pages = 0; pages < 3; pages++) {
      final page = await currentRepository.listGlobalSessions(
        limit: 40,
        cursor: cursor,
      );
      if (_disposed || generation != _generation) return const [];
      items.addAll(page.items);
      final listable = items.where(
        (r) =>
            r.session.parentID == null &&
            !isAiTeamConversation(r) &&
            (r.session.directory ?? r.projectDirectory) != here,
      );
      if (listable.length >= limit ||
          !page.hasMore ||
          page.nextCursor == cursor) {
        break;
      }
      cursor = page.nextCursor;
    }
    Map<String, String> statuses = const {};
    try {
      statuses = await currentApi.sessionStatuses();
    } catch (_) {}
    if (_disposed || generation != _generation) return const [];
    final seen = <String>{};
    final elsewhere = [
      for (final result in items)
        if ((result.session.directory ?? result.projectDirectory)
            case final String where
            when where != here &&
                result.session.parentID == null &&
                !isAiTeamConversation(result) &&
                seen.add(result.session.id))
          ElsewhereConversation(
            session: result.session,
            directory: where,
            projectName: result.projectName,
            running: statuses[result.session.id] == 'busy',
          ),
    ];
    elsewhere.sort((a, b) {
      if (a.running != b.running) return a.running ? -1 : 1;
      return (b.session.time?.updated ?? 0).compareTo(
        a.session.time?.updated ?? 0,
      );
    });
    return elsewhere.take(limit).toList();
  }
}
