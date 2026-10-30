part of '../connection.dart';

extension ConnectionControllerWidgetRouting on ConnectionController {
  /// A widget row tap. Returns true when the chat can be pushed: the session
  /// belongs to this controller, or its feed row was opened first (switching
  /// to its project and connection, as the Conversations list does). False
  /// when nothing opens it; the caller shows the Conversations screen.
  Future<bool> openWidgetConversation(String sessionID) async {
    if (sessionsById.containsKey(sessionID)) return true;
    final item = chatFeed().items
        .where((row) => row.sessionID == sessionID)
        .firstOrNull;
    if (item == null) return false;
    try {
      await openChatFeedItem(item);
      return true;
    } catch (_) {
      return false;
    }
  }
}

extension _ConnectionControllerWidgets on ConnectionController {
  String get _currentWidgetScope {
    if (_lifecycleSuspended && _widgetScope.isNotEmpty) return _widgetScope;
    final identity = (_generation, api, profile?.id, directory, workspace);
    if (_widgetScopeIdentity != identity) {
      _widgetScopeIdentity = identity;
      _widgetScope = base64Url.encode(
        List<int>.generate(24, (_) => Random.secure().nextInt(256)),
      );
      _widgetActionResult = null;
      _widgetActionResults.clear();
    }
    return _widgetScope;
  }

  Map<String, int> get _widgetAppTheme {
    final palette = effectiveThemePack(themePack.value).palette(
      switch (appearance.value) {
        AppAppearance.dark || AppAppearance.saver => Brightness.dark,
        AppAppearance.light => Brightness.light,
        AppAppearance.system => PlatformDispatcher.instance.platformBrightness,
      },
    );
    return {
      'background': palette.background.toARGB32(),
      'text': palette.scheme.onSurface.toARGB32(),
      'muted': palette.scheme.onSurfaceVariant.toARGB32(),
      'accent': palette.scheme.primary.toARGB32(),
    };
  }

  String get _widgetProjectID => directory == null && workspace == null
      ? ''
      : receipt_crypto.sha256
            .convert(utf8.encode('$_currentWidgetScope|project'))
            .toString();

  int _widgetStartedAt(String id) {
    if (!busySessions.contains(id)) return 0;
    return _busySince
        .putIfAbsent(id, () => _openTurns[id] ?? DateTime.now())
        .millisecondsSinceEpoch;
  }

  String _widgetSteerMode(String id) {
    final gateway = api;
    if (gateway == null) return 'unknown';
    if (gateway.capabilities.promptSteering) return 'steer';
    if (gateway is MidTurnPromptGateway) {
      return switch ((gateway as MidTurnPromptGateway).midTurnPrompt(id)) {
        MidTurnPrompt.joinsTurn => 'steer',
        MidTurnPrompt.restartsTurn => 'restart',
        null => 'unknown',
      };
    }
    // A gateway without native steering uses its ordinary after-reply path.
    return 'afterReply';
  }

  Map<String, Object?>? get _widgetOnDemand {
    final owner = _connectedProfile;
    if (owner == null ||
        owner.transientTransport ||
        !isProfileReadable(owner.id)) {
      return null;
    }
    return {
      'fingerprint': widgetProfileFingerprint(owner),
      'directory': directory,
      'workspace': workspace,
    };
  }

  List<String> _widgetOnDemandActions(String sessionID) {
    final owner = _connectedProfile;
    if (owner == null ||
        owner.transientTransport ||
        owner.backend != ServerBackend.openCode ||
        !isProfileReadable(owner.id)) {
      return const [];
    }
    return [
      if (permissionForSession(sessionID) != null) ...['allowOnce', 'reject'],
      if (busySessions.contains(sessionID) &&
          _widgetRunMarkers.containsKey(sessionID))
        'stop',
    ];
  }

  /// What the home-screen card says about a request: the question, the file
  /// or command it concerns, and the folder. The tool call is known only when
  /// its event was seen on the way; otherwise the request's own pattern says
  /// what it touches.
  Map<String, Object?> _widgetAsk(PermissionRequest request) {
    final call = permissionToolCall(request);
    return widgetAskFor(
      permission: request.permission,
      patterns: request.patterns,
      tool: call?.tool,
      target: call?.target,
      strings: _shellStrings(),
    );
  }

  String _widgetPreview(String sessionID) {
    final step = _runningToolDetail[sessionID];
    if (busySessions.contains(sessionID) && step != null && step.isNotEmpty) {
      return step;
    }
    final reply = _widgetReplyPreviews[sessionID];
    if (reply != null) return reply;
    for (final message
        in cachedSessionTail(sessionID)?.messages.reversed ??
            const <SessionTailText>[]) {
      if (message.role == 'assistant' && message.text.trim().isNotEmpty) {
        return safeWidgetPreview(message.text);
      }
    }
    return '';
  }

  // Observe full text parts, not deltas: a split credential is never published
  // before KitRedact can recognize it. Unknown roles and synthetic parts fail
  // closed. Text from a previous message cannot replace the current reply.
  bool _observeWidgetReply(Map part, String sessionID) {
    if (part['type'] != 'text' ||
        part['synthetic'] == true ||
        part['text'] is! String ||
        part['messageID'] != _widgetAssistantMessages[sessionID] ||
        !_widgetAssistantMessages.containsKey(sessionID)) {
      return false;
    }
    final preview = safeWidgetPreview(part['text'] as String);
    if (preview.isEmpty || _widgetReplyPreviews[sessionID] == preview) {
      return false;
    }
    if (_widgetReplyPreviews.length >= 64 &&
        !_widgetReplyPreviews.containsKey(sessionID)) {
      _widgetReplyPreviews.remove(_widgetReplyPreviews.keys.first);
    }
    _widgetReplyPreviews[sessionID] = preview;
    return true;
  }

  /// The model this conversation uses, by the display name the app shows
  /// elsewhere ("LL Alpha Free"). Empty when no name is known yet: a raw id
  /// such as "ll-alpha-free" is never put on the home screen.
  String _widgetModelLabel(String sessionID) {
    final model = sessionModels[sessionID]?.model ?? selectedModel;
    if (model == null) return '';
    String? name;
    for (final candidate in catalog?.models ?? const <CatalogModel>[]) {
      if (candidate.id == model.modelID &&
          candidate.providerID == model.providerID &&
          candidate.name.trim().isNotEmpty) {
        name = candidate.name.trim();
        break;
      }
    }
    name ??=
        knownModelName(model.providerID, model.modelID) ??
        knownModelName(model.providerID, model.normalized.modelID);
    return name == null ? '' : safeWidgetTitle(name);
  }

  /// The server's name on the home-screen card: "This phone" for a server on
  /// this device, else the name it was saved under (a bare address reads
  /// "Computer at …"). Never a URL.
  String get _widgetServerName {
    final owner = _connectedProfile;
    if (owner == null) return '';
    final host = Uri.tryParse(owner.baseUrl)?.host ?? '';
    if (looksLikeInAppServer(owner) || isLoopbackHost(host)) {
      return _shellStrings().phoneSetupProfileName;
    }
    final name = plainServerName(owner.name);
    return name.contains('://') ? '' : name;
  }

  /// Every server the Conversations list shows (this connection first, then
  /// the others), for the Status widget: its name, whether it answers, and
  /// since when it has not. Names only, never an address.
  List<Map<String, Object?>> _widgetServers() {
    final now = DateTime.now().millisecondsSinceEpoch;
    final rows = <Map<String, Object?>>[];
    final seen = <String>{};
    void add(ServerProfile owner, StreamStatus state, bool connected) {
      if (!seen.add(owner.id)) return;
      final host = Uri.tryParse(owner.baseUrl)?.host ?? '';
      final phone = looksLikeInAppServer(owner) || isLoopbackHost(host);
      final name = phone
          ? _shellStrings().phoneSetupProfileName
          : plainServerName(owner.name);
      final String word;
      if (connected) {
        word = 'online';
        _widgetOfflineSince.remove(owner.id);
      } else if (state == StreamStatus.connecting) {
        word = 'connecting';
      } else {
        word = 'offline';
      }
      rows.add({
        'name': name.contains('://') ? '' : name,
        'state': word,
        if (word == 'offline')
          'since': _widgetOfflineSince.putIfAbsent(owner.id, () => now),
        if (phone) 'phone': true,
      });
    }

    final main = _connectedProfile ?? profile;
    if (main != null) {
      add(main, status, status == StreamStatus.connected);
    }
    for (final entry in _sides.entries) {
      final side = entry.value;
      final owner = side.profile;
      if (side._disposed || owner == null) continue;
      add(owner, side.status, side.isConnected);
    }
    _widgetOfflineSince.removeWhere((id, _) => !seen.contains(id));
    return rows;
  }

  /// The projects a Conversations widget can be scoped to, most recent
  /// first: this server's recent projects, then those of the conversations
  /// it lists. The writer keeps the folder names only.
  List<String> _widgetProjects(
    List<Session> sessions,
    List<WidgetOtherConversation> others,
  ) => [
    for (final location in recentLocations) location.directory ?? '',
    for (final session in sessions) session.directory ?? '',
    for (final other in others) other.project,
  ];

  /// The first thing an agent question or form asks, for a card that can only
  /// open the chat. Null when nothing of the kind waits in [sessionID].
  String? _widgetQuestion(String sessionID) {
    for (final question in questions.values) {
      if (question.sessionID != sessionID) continue;
      final prompt = question.prompts.firstOrNull;
      final text = (prompt?.question.trim().isNotEmpty ?? false)
          ? prompt!.question
          : prompt?.title ?? '';
      return safeWidgetTitle(text);
    }
    for (final form in forms.values) {
      if (form.sessionID == sessionID) return '';
    }
    return null;
  }

  /// The Conversations screen's default view (all projects and connections,
  /// subagents excluded) minus what this controller already shows with full
  /// details: the widget lists them as plain rows.
  List<WidgetOtherConversation> _widgetOtherConversations() {
    final List<ChatFeedItem> items;
    try {
      // Not chatFeed(): that starts the list's hold clocks and timers, and
      // this runs on every change.
      items = _paQuietFeedRows();
    } catch (_) {
      return const [];
    }
    return [
      for (final item in items)
        if (!item.isSubagent && !sessionsById.containsKey(item.sessionID))
          WidgetOtherConversation(
            id: item.sessionID,
            title: item.title,
            project: safeWidgetProject(item.projectName),
            updatedAt: item.lastActivity.millisecondsSinceEpoch,
            busy: item.status == ChatStatus.running,
            needsYou: item.status == ChatStatus.needsYou,
            server: item.sourceLabel ?? '',
          ),
    ].take(WidgetSessionSnapshot.maxSessions).toList();
  }

  Map<String, Map<String, Object?>> _widgetSessionDetails() => {
    for (final session in sessionsById.values)
      session.id: {
        'project': safeWidgetProject(session.directory ?? directory ?? ''),
        'preview': _widgetPreview(session.id),
        if (_widgetModelLabel(session.id) case final model
            when model.isNotEmpty)
          'model': model,
        'startedAt': busySessions.contains(session.id)
            ? _widgetStartedAt(session.id)
            : 0,
        'step': busySessions.contains(session.id)
            ? (_runningToolDetail[session.id] ?? '')
            : '',
        'steerMode': _widgetSteerMode(session.id),
        'onDemandActions': _widgetOnDemandActions(session.id),
        'runMarker': ?_widgetRunMarkers[session.id],
        if (permissionForSession(session.id) case final request?) ...{
          'requestID': request.id,
          'ask': _widgetAsk(request),
        } else if (_widgetQuestion(session.id) case final question?) ...{
          'needsYou': true,
          if (question.isNotEmpty) 'ask': {'kind': 'other', 'q': question},
        },
        'todos': [
          for (final (index, task)
              in (_widgetTasks[session.id] ?? const <MobileTaskItem>[])
                  .take(5)
                  .indexed)
            {
              'title': _shellStrings().kitProgressStep(
                index + 1,
                _widgetTasks[session.id]!.length,
              ),
              'done': task.status == MobileTaskStatus.completed,
            },
        ],
        if (_sessionTasks[session.id] case final tasks?) ...{
          'tasksDone': tasks.done,
          'tasksTotal': tasks.total,
        },
      },
  };

  String? _widgetTeamAction(TeamProject project) {
    final team = orchestration;
    if (project.simulated ||
        team == null ||
        team.isStale ||
        team.phase != OrchestrationPhase.ready) {
      return null;
    }
    if (team.capabilities.projectLanes &&
        !project.planApproved &&
        const {'plan', 'planned'}.contains(project.status) &&
        project.tasks.isNotEmpty &&
        project.specVersions.isNotEmpty) {
      return 'approvePlan';
    }
    if (team.capabilities.projectResume &&
        project.planApproved &&
        const {'paused', 'interrupted'}.contains(project.status)) {
      return 'resume';
    }
    return null;
  }

  List<Map<String, Object?>> _widgetTeamRows() {
    final projects = orchestration?.projectController?.snapshot;
    if (projects == null || projects.simulated) return const [];
    final ranked = projects.projects.where((p) => !p.simulated).toList()
      ..sort((a, b) {
        int rank(TeamProject project) => _widgetTeamAction(project) != null
            ? 0
            : project.status == 'running'
            ? 1
            : 2;
        return rank(a).compareTo(rank(b));
      });
    return [
      for (final project in ranked.take(1))
        {
          'id': 'team:${project.id}',
          'title': safeWidgetTitle(project.name),
          'busy': project.status == 'running',
          'updatedAt':
              DateTime.tryParse(project.updatedAt)?.millisecondsSinceEpoch ?? 0,
          'startedAt': 0,
          'step': '',
          'steerMode': 'unknown',
          'todos': const [],
          'onDemandActions': [
            if (_connectedProfile?.transientTransport == false &&
                _connectedProfile?.orchestration?.provider ==
                    OrchestrationProvider.phoneEngine)
              ?_widgetTeamAction(project),
          ],
          'team': {
            'revision': project.revision,
            'runID': project.id,
            'gateID': receipt_crypto.sha256
                .convert(
                  utf8.encode(
                    '$_currentWidgetScope|${project.id}|${project.revision}',
                  ),
                )
                .toString(),
            'action': ?_widgetTeamAction(project),
            'done': project.tasks
                .where(
                  (t) =>
                      const {'done', 'merged', 'accepted'}.contains(t.status),
                )
                .length,
            'total': project.tasks.length,
          },
        },
    ];
  }

  Future<WidgetActionResult> _handleWidgetAction(WidgetAction action) async {
    WidgetActionResult refuse(String error) =>
        WidgetActionResult(action.id, action.action, 'rejected', error);
    final scope = _currentWidgetScope;
    bool current() =>
        !_disposed &&
        !_widgetSnapshotSuspended &&
        !_lifecycleSuspended &&
        isConnected &&
        (!_lifecycleWasBackgrounded ||
            keepLiveInBackground && backgroundLive.active) &&
        action.profileID == profile?.id &&
        action.scopeToken == _currentWidgetScope;
    if (!current()) return refuse('staleRequest');
    final previous = _widgetActionResults[action.id];
    if (previous != null) return previous;
    final gateway = api;
    if (gateway == null || !_ownsProfileServices) return refuse('unavailable');
    final target = action.action == 'voice'
        ? 'voice'
        : action.runID.isNotEmpty
        ? action.runID
        : action.sessionID;
    if (!_widgetActionsInFlight.add(target)) return refuse('busy');
    WidgetActionResult result = WidgetActionResult(
      action.id,
      action.action,
      'uncertain',
      'uncertainDelivery',
    );
    _widgetActionResults[action.id] = result;
    try {
      switch (action.action) {
        case 'allowOnce':
        case 'reject':
          final permission = permissions[action.requestID];
          if (permission == null ||
              permission.sessionID != action.sessionID ||
              !awaitingPermissions.contains(permission)) {
            result = refuse('staleRequest');
            break;
          }
          final identity = permissionIdentity(permission);
          var confirmed = false;
          await _sendPermissionReply(
            gateway,
            action.requestID,
            action.action == 'allowOnce' ? 'once' : 'reject',
            expectedRequest: identity,
            onConfirmed: () {
              confirmed = true;
            },
          );
          if (confirmed) {
            result = WidgetActionResult(action.id, action.action, 'accepted');
          } else if (current()) {
            result = refuse('staleRequest');
          }
        case 'stop':
        case 'steer':
          if (!busySessions.contains(action.sessionID) ||
              action.expectedStartedAt <= 0 ||
              action.expectedStartedAt != _widgetStartedAt(action.sessionID)) {
            result = refuse('staleRequest');
            break;
          }
          if (action.action == 'stop') {
            noteStopFromThisApp(action.sessionID);
            await gateway.abort(action.sessionID);
          } else {
            final mode = _widgetSteerMode(action.sessionID);
            if (mode == 'unknown' || mode == 'restart') {
              result = refuse('unsupported');
              break;
            }
            if (action.text.isEmpty) {
              result = refuse('invalidRequest');
              break;
            }
            await gateway.promptAsync(
              action.sessionID,
              text: action.text,
              delivery: gateway.capabilities.promptSteering
                  ? PromptDelivery.steer
                  : null,
            );
          }
          result = WidgetActionResult(action.id, action.action, 'accepted');
        case 'voice':
          if (action.text.isEmpty ||
              action.lastProjectID.isEmpty ||
              action.lastProjectID != _widgetProjectID) {
            result = refuse('staleRequest');
            break;
          }
          final session = gateway is SessionSelectionGateway
              ? await (gateway as SessionSelectionGateway)
                    .createSelectedSession(
                      SessionSelection(
                        model: selectedModel,
                        variant: selectedVariant,
                        agent: selectedAgent,
                      ),
                    )
              : await gateway.createSession();
          if (!current()) break; // Creation may have committed: never replay.
          sessionsById[session.id] = session;
          _sessionInventoryIDs.add(session.id);
          await gateway.promptAsync(
            session.id,
            text: action.text,
            model: selectedModel,
            agent: selectedAgent,
            variant: selectedVariant,
          );
          result = WidgetActionResult(action.id, action.action, 'accepted');
        case 'approvePlan':
        case 'resume':
          final controller = orchestration?.projectController;
          final project = controller?.snapshot?.projects
              .where((p) => p.id == action.runID)
              .firstOrNull;
          final expectedGate = project == null
              ? ''
              : receipt_crypto.sha256
                    .convert(
                      utf8.encode('$scope|${project.id}|${project.revision}'),
                    )
                    .toString();
          if (controller == null ||
              project == null ||
              controller.snapshot?.simulated != false ||
              action.gateID != expectedGate ||
              _widgetTeamAction(project) != action.action) {
            result = refuse('staleRequest');
            break;
          }
          final response = await controller.execute(
            TeamProjectCommand(
              requestId: action.id,
              action: action.action == 'approvePlan'
                  ? TeamProjectAction.approvePlan
                  : TeamProjectAction.resumeProject,
              projectId: project.id,
              expectedRevision: project.revision,
            ),
          );
          result = response.accepted
              ? WidgetActionResult(action.id, action.action, 'accepted')
              : response.code == 'busy'
              ? refuse('busy')
              : response.code == 'saveFailed'
              ? WidgetActionResult(
                  action.id,
                  action.action,
                  'uncertain',
                  'uncertainDelivery',
                )
              : refuse('unavailable');
      }
    } catch (_) {
      // A failed await cannot prove a mutation was not delivered. Never expose
      // a server error, auto retry, or turn an uncertain steer into a new turn.
    } finally {
      _widgetActionsInFlight.remove(target);
    }
    if (current() && scope == _currentWidgetScope) {
      _widgetActionResults[action.id] = result;
      while (_widgetActionResults.length > 32) {
        _widgetActionResults.remove(_widgetActionResults.keys.first);
      }
      _widgetActionResult = result;
      _notifyListeners();
    }
    return result;
  }
}
