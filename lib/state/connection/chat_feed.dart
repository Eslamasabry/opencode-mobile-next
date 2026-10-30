part of '../connection.dart';

// Chats-first Home: every conversation across projects (see domain/chat_feed.dart).

/// The UI's door to the chat feed; the UI never needs more than the contract.
ChatFeedSource chatFeedSourceOf(ConnectionController c) => c;

/// Preference key for the project last used on [profileID]. Named so the
/// profile deletion sweep (`ProfileStore.profileScopedPreferenceKeys`) finds it.
String lastProjectPreferenceKey(String profileID) =>
    'oc.lastProject.$profileID';

/// [ConnectionController]'s [ChatFeedSource].
mixin _ConnectionControllerChatFeed on ChangeNotifier {
  ConnectionController get _self;

  static const _feedPages = 4;
  static const _feedPageSize = 50;
  static const _feedPreviewLength = 120;

  String? _feedProfileID;
  List<GlobalSessionResult> _feedGlobal = const [];
  List<WorkspaceProject> _feedProjects = const [];
  Set<String> _feedBusy = const {};

  /// Live run starts and ends seen per session, numbered in arrival order.
  /// The status read behind [_feedBusy] is a snapshot: once a session's own
  /// run event arrives after the read left, the live state answers for it
  /// (`busySessions` in the open project, the server-wide tally elsewhere).
  /// Otherwise a read taken mid-run kept the row "Working" after the run
  /// ended, while the chat showed it idle: OpenCode 2 sends no
  /// `session.updated` that would read again.
  final Map<String, int> _feedRunSignals = {};
  int _feedRunSignal = 0;

  /// [_feedRunSignal] when the read behind [_feedBusy] left.
  int _feedBusyAt = 0;
  bool _feedLoaded = false;
  bool _feedLoading = false;
  bool _feedComplete = true;
  Future<void>? _feedRefreshing;
  int? _feedRefreshingGeneration;
  int _feedRequestRevision = 0;
  Timer? _feedDebounce;

  /// Bumped only when something the rows are built from really changed
  /// (sessions, their statuses, titles, times, previews, scope). A notify
  /// that changed none of it leaves it alone, and the rows and sort order
  /// built for it are served again instead of derived again.
  int _feedRevision = 0;
  Object? _feedSignature;
  final Map<bool, List<ChatFeedItem>> _feedItemsMemo = {};
  final Map<ChatFeedFilter, ChatFeedSnapshot> _feedSnapshotMemo = {};

  /// Times the rows were derived from scratch (a test handle).
  @visibleForTesting
  int chatFeedDerivations = 0;

  /// The feed revision as of the last read; changes only with its content.
  int get chatFeedRevision => _feedRevision;

  bool get _ocAcross =>
      _self.api != null && _self.capabilities.globalSessionSearch;

  bool _feedEligible(String? directory) =>
      !isTemporaryProjectDirectory(directory) &&
      !isProtectedWorkspaceDirectory(directory) &&
      !isAiTeamDirectory(directory);

  String? get _feedOwnerID => (_self._connectedProfile ?? _self.profile)?.id;

  /// A cache from another server is never shown on this one.
  bool get _feedCacheCurrent => _feedProfileID == _feedOwnerID;

  Future<void> _ocRefresh() {
    final generation = _self._generation;
    // A pull or another explicit read consumes a pending automatic trigger.
    _feedDebounce?.cancel();
    _feedDebounce = null;
    if (_feedRefreshingGeneration == generation) {
      if (_feedRefreshing case final running?) return running;
    }
    final epoch = _self._feedQuestionEpoch;
    final revision = _feedRequestRevision;
    _feedRefreshingGeneration = generation;
    late final Future<void> tracked;
    tracked = _refreshFeed().whenComplete(() {
      // A retired read must not clear the new runtime's single-flight slot.
      if (!identical(_feedRefreshing, tracked)) return;
      _feedRefreshing = null;
      _feedRefreshingGeneration = null;
      // A debounce that fired during a slow read only joined this future.
      // Preserve both inventory and waiting-request invalidations as one
      // trailing refresh, even if neither stream emits another event.
      if (!_self._disposed &&
          generation == _self._generation &&
          (revision != _feedRequestRevision ||
              epoch != _self._feedQuestionEpoch)) {
        _feedScheduleRefresh();
      }
    });
    return _feedRefreshing = tracked;
  }

  /// Startup and (re)connect reconcile even before Home reads the feed.
  /// Session events share a bounded 2-second debounce; volatile OpenCode 2
  /// events are reconciled by refetch, never by replay.
  void _feedScheduleRefresh() {
    if (_self._disposed || _self.api == null || _self.repository == null) {
      return;
    }
    final generation = _self._generation;
    _feedRequestRevision++;
    // A busy stream must not postpone the first load or reconnect forever.
    // Keep the first trigger's deadline while coalescing subsequent events.
    if (_feedDebounce?.isActive ?? false) return;
    _feedDebounce = Timer(const Duration(seconds: 2), () {
      _feedDebounce = null;
      if (!_self._disposed && generation == _self._generation) {
        // This trigger belongs to OpenCode. Do not wait for another agent
        // helper to start before reconciling this server's inventory.
        unawaited(_ocRefresh());
      }
    });
  }

  void _feedRetireTransport() {
    _feedDebounce?.cancel();
    _feedDebounce = null;
    _feedRefreshing = null;
    _feedRefreshingGeneration = null;
    _feedLoading = false;
  }

  void _feedDispose() {
    _feedRetireTransport();
    _self._feedQuestionEpoch++;
    _self._feedDirectoryQuestions.clear();
    _self._feedDirectoryForms.clear();
  }

  Future<void> _refreshFeed() async {
    final currentRepository = _self.repository;
    final currentApi = _self.api;
    final owner = _feedOwnerID;
    if (currentRepository == null || currentApi == null || owner == null) {
      return;
    }
    final generation = _self._generation;
    if (_feedProfileID != owner) {
      _feedProfileID = owner;
      _feedGlobal = const [];
      _feedProjects = const [];
      _feedBusy = const {};
      _feedRunSignals.clear();
      _feedLoaded = false;
    }
    _feedLoading = !_feedLoaded;
    if (_feedLoading) _self._notifyListeners();
    var complete = true;
    final items = <GlobalSessionResult>[];
    if (_ocAcross) {
      String? cursor;
      try {
        for (var page = 0; page < _feedPages; page++) {
          final read = await currentRepository.listGlobalSessions(
            limit: _feedPageSize,
            cursor: cursor,
          );
          items.addAll(read.items);
          if (!read.hasMore || read.nextCursor == cursor) break;
          cursor = read.nextCursor;
          if (page == _feedPages - 1) complete = false;
        }
      } catch (_) {
        complete = false;
      }
    }
    List<WorkspaceProject>? projects;
    try {
      projects = await currentRepository.listProjects();
    } catch (_) {
      projects = null;
    }
    Map<String, String>? statuses;
    final signalAt = _feedRunSignal;
    try {
      statuses = await currentApi.sessionStatuses();
    } catch (_) {
      statuses = null;
    }
    if (_self._disposed ||
        !_self._isCurrent(generation, currentApi) ||
        _feedOwnerID != owner) {
      return;
    }
    if (_ocAcross && (items.isNotEmpty || complete)) {
      _feedGlobal = items;
    } else if (_ocAcross) {
      complete = false; // Keep what was known.
    }
    if (projects != null) _feedProjects = projects;
    if (statuses != null) {
      _feedBusy = {
        for (final entry in statuses.entries)
          if (entry.value == 'busy' || entry.value == 'retry') entry.key,
      };
      // Run events from before the read are in it; later ones outrank it.
      _feedBusyAt = signalAt;
      _feedRunSignals.removeWhere((_, at) => at <= signalAt);
    }
    _feedLoaded = true;
    _feedLoading = false;
    _feedComplete = complete;
    await _self._refreshFeedDirectoryQuestions();
    if (_self._disposed || !_self._isCurrent(generation, currentApi)) return;
    _self._genUiRefreshFeed();
    _self._notifyListeners();
    // The next start paints these rows at once (phone_agents_saved.dart).
    _self._paSaveMainRows();
  }

  /// Whether the last status read still answers for [sessionID]: it listed
  /// the session, and no run event for it has arrived since the read left.
  bool _feedReadSaysBusy(String sessionID) =>
      _feedBusy.contains(sessionID) &&
      (_feedRunSignals[sessionID] ?? 0) <= _feedBusyAt;

  /// Notes a run start or end from either event channel. True when it
  /// takes a "busy" from the status read off a row, so the caller can tell
  /// listeners when nothing else will.
  bool _feedObserveRunEvent(EventEnvelope event) {
    final props = event.properties;
    switch (event.type) {
      case 'session.idle' || 'session.error':
        break;
      case 'session.status':
        final raw = props['status'];
        final type = raw is Map ? raw['type']?.toString() : raw?.toString();
        if (type != 'idle' && type != 'busy' && type != 'retry') return false;
      default:
        return false;
    }
    final sessionID = props['sessionID']?.toString();
    if (sessionID == null || sessionID.isEmpty) return false;
    final shown = _feedReadSaysBusy(sessionID);
    _feedRunSignals[sessionID] = ++_feedRunSignal;
    return shown;
  }

  /// A project the server calls Git (OpenCode 2 says so per project) or a
  /// server that keeps projects for git repositories alone tells a git
  /// folder; otherwise no Git badge rather than a wrong one.
  bool _feedFolderIsGit(String directory) {
    final project = _feedProjectFor(directory);
    if (project == null) return false;
    return project.isGit ?? _self.capabilities.projectsAreGitRepositories;
  }

  WorkspaceProject? _feedProjectFor(String directory) {
    for (final project in _feedProjects) {
      if (project.id == 'global') continue;
      if (ConnectionController.projectContainsDirectory(project, directory)) {
        return project;
      }
    }
    return null;
  }

  static bool _feedIsProjectRoot(WorkspaceProject project, String directory) {
    final target = ConnectionController.normalizeDirectoryPath(directory);
    return [project.directory, ...project.worktrees].any(
      (root) => ConnectionController.normalizeDirectoryPath(root) == target,
    );
  }

  static String _feedFolderName(String directory) {
    final parts = directory.split(RegExp(r'[\\/]')).where((p) => p.isNotEmpty);
    return parts.isEmpty ? directory : parts.last;
  }

  String _feedProjectName(String directory, String? serverName) {
    final named = serverName?.trim();
    if (named != null && named.isNotEmpty) return named;
    // A project's name labels a chat only when the chat sits at that
    // project's root or one of its worktrees. OpenCode 2 makes a project of
    // the folder its server was started in (`/root/projects`), and every
    // migrated chat below it would otherwise read "projects".
    final project = _feedProjectFor(directory);
    final name = project?.name.trim();
    if (project != null &&
        name != null &&
        name.isNotEmpty &&
        _feedIsProjectRoot(project, directory)) {
      return name;
    }
    return _feedFolderName(directory);
  }

  /// One short plain-text line, or '' when none is known. The tail cache is
  /// redacted before it is stored; this only collapses and cuts it.
  String _feedPreview(String sessionID) {
    final tail = _self.cachedSessionTail(sessionID);
    if (tail == null) return '';
    for (final message in tail.messages.reversed) {
      final text = message.text.replaceAll(RegExp(r'\s+'), ' ').trim();
      if (text.isEmpty) continue;
      return text.length <= _feedPreviewLength
          ? text
          : '${text.substring(0, _feedPreviewLength - 1).trimRight()}…';
    }
    return '';
  }

  /// A hash of everything the rows are built from. Far cheaper than building
  /// them (no row objects, previews, paths or sorting). Moves the revision
  /// when it differs from the last one seen.
  int _feedSyncRevision() {
    final here = _self.directory;
    int set(Iterable<Object?> values) {
      var sum = 0;
      for (final value in values) {
        sum = (sum + value.hashCode) & 0x3fffffff;
      }
      return sum;
    }

    final cardScope = _self._genUiScope;
    int cards(Session s) => cardScope == null
        ? 0
        : _self._genUiState
              .waiting(
                GenUiScope(
                  profileID: cardScope.profileID,
                  sourceId: cardScope.sourceId,
                  directory: s.directory ?? here ?? '/',
                  workspace: cardScope.workspace,
                ),
                s.id,
              )
              .length;

    int session(Session s) => Object.hash(
      s.id,
      s.title,
      s.time?.created,
      s.time?.updated,
      s.time?.idle,
      s.time?.viewed,
      s.archived,
      s.parentID,
      s.directory,
      s.lastRunFailed,
      s.stagedRevert != null,
      s.contextUse?.fraction ?? _self.contextUsageMemory?.usageOf(s.id),
      _self.isSessionUnread(s),
      _self.finishedUnseen(s.id),
      _self.stoppedByPerson(s.id),
      identityHashCode(_self.cachedSessionTail(s.id)),
      cards(s),
    );

    final signature = Object.hashAll([
      here,
      _feedOwnerID,
      _feedProfileID,
      _ocAcross,
      _feedLoading,
      _feedComplete,
      _feedLoaded,
      _self._genUiLocal?.recoveryIncomplete ?? false,
      _self._sessionInventoryInitialized,
      set(_self._sessionInventoryIDs),
      identityHashCode(_feedGlobal),
      identityHashCode(_feedProjects),
      identityHashCode(_feedBusy),
      _feedBusyAt,
      set(_feedRunSignals.entries.map((e) => Object.hash(e.key, e.value))),
      set(_self.awaitingPermissions.map((p) => p.sessionID)),
      set(_self.questions.values.map((q) => q.sessionID)),
      set(_self.forms.values.map((f) => f.sessionID)),
      set(_self.busySessions),
      if (_self.api is BackgroundTaskGateway)
        BackgroundTaskBook.shared.revision,
      set(_self._failedAttentionSessions.keys),
      set(
        _self.elsewhereAttention
            .activity(except: here)
            .map((a) => Object.hash(set(a.running), set(a.waiting))),
      ),
      set(
        _self.elsewhereAttention
            .observations(except: here)
            .map((o) => Object.hash(o.kind, o.sessionID)),
      ),
      cardScope == null
          ? null
          : Object.hash(
              cardScope.profileID,
              cardScope.sourceId,
              cardScope.workspace,
            ),
      set(_self.sessionsById.values.map(session)),
      if (_ocAcross)
        set(
          _feedGlobal.map(
            (r) => Object.hash(
              session(r.session),
              r.projectName,
              r.projectDirectory,
            ),
          ),
        ),
      _self.capabilities.projectsAreGitRepositories,
    ]);
    if (_feedSignature != signature) {
      _feedSignature = signature;
      _feedRevision++;
      _feedItemsMemo.clear();
      _feedSnapshotMemo.clear();
    }
    return _feedRevision;
  }

  List<ChatFeedItem> _feedAllItems({required bool includeSubagents}) {
    _feedSyncRevision();
    return _feedItemsMemo[includeSubagents] ??= _feedDeriveItems(
      includeSubagents: includeSubagents,
    );
  }

  List<ChatFeedItem> _feedDeriveItems({required bool includeSubagents}) {
    chatFeedDerivations++;
    final here = _self.directory;
    final waiting = <String>{
      for (final permission in _self.awaitingPermissions) permission.sessionID,
      for (final question in _self.questions.values) question.sessionID,
      for (final form in _self.forms.values) form.sessionID,
    };
    final running = <String>{..._self.busySessions};
    final failed = <String>{..._self._failedAttentionSessions.keys};
    // A turn that ended with work still going on in the background reads as
    // running (lib/domain/background_tasks.dart has the rule), unless the
    // turn itself failed: the agent stopped, whatever it left running.
    if (_self.api case final BackgroundTaskGateway background) {
      for (final id in _self.sessionsById.keys) {
        if (background.lastTurnFailed(id)) {
          failed.add(id);
        } else if (background.backgroundTasksRunning(id)) {
          running.add(id);
        }
      }
    }
    // The server-wide tally already covers other projects; the selected one
    // answers from its own live state above.
    for (final activity in _self.elsewhereAttention.activity(except: here)) {
      running.addAll(activity.running);
      waiting.addAll(activity.waiting);
    }
    for (final observation in _self.elsewhereAttention.observations(
      except: here,
    )) {
      if (observation.kind == AttentionKind.failedRun &&
          observation.sessionID != null) {
        failed.add(observation.sessionID!);
      }
    }
    final other = _ocAcross && _feedCacheCurrent;
    // The status read fills in runs no live event has spoken for yet.
    if (other) running.addAll(_feedBusy.where(_feedReadSaysBusy));

    final byID = <String, ChatFeedItem>{};
    void add(Session session, String? directory, String? projectName) {
      if (session.archived) return;
      if (session.parentID != null && !includeSubagents) return;
      // A conversation is never hidden for its folder. Temporary, home and
      // root folders are only kept out of the project list; AI Team chats
      // belong to the AI Team screen.
      final where = ConnectionController.normalizeDirectoryPath(
        directory ?? here ?? '/',
      );
      if (isAiTeamDirectory(where)) return;
      final otherFolder = isOtherFolderDirectory(where);
      final id = session.id;
      final cardScope = _self._genUiScope;
      final cardWaits =
          cardScope != null &&
          _self._genUiState
              .waiting(
                GenUiScope(
                  profileID: cardScope.profileID,
                  sourceId: cardScope.sourceId,
                  directory: where,
                  workspace: cardScope.workspace,
                ),
                id,
              )
              .isNotEmpty;
      final status = (waiting.contains(id) || cardWaits)
          ? ChatStatus.needsYou
          : running.contains(id)
          ? ChatStatus.running
          : failed.contains(id)
          ? ChatStatus.failed
          // OpenCode 2 says outright that the last run failed; it stays
          // marked until the conversation is opened (the Done rule).
          : session.lastRunFailed && _self.isSessionUnread(session)
          ? ChatStatus.failed
          : ChatStatus.idle;
      final stamp = session.time?.updated ?? session.time?.created ?? 0;
      final title = session.title?.trim();
      byID[id] = ChatFeedItem(
        sessionID: id,
        title: title == null || title.isEmpty ? 'New chat' : title,
        directory: where,
        projectName: otherFolder
            ? otherFolderLabel(where)
            : _feedProjectName(where, projectName),
        isGit: !otherFolder && _feedFolderIsGit(where),
        status: status,
        lastActivity: DateTime.fromMillisecondsSinceEpoch(stamp),
        preview: _feedPreview(id),
        parentID: session.parentID,
        contextUsage:
            session.contextUse?.fraction ??
            _self.contextUsageMemory?.usageOf(id),
        // Idle, and its last run finished after it was last opened: the
        // server's watermark (OpenCode 2), or a run this connection watched
        // end (every server).
        finishedUnseen:
            status == ChatStatus.idle &&
            (_self.isSessionUnread(session) || _self.finishedUnseen(id)),
        stoppedByPerson: _self.stoppedByPerson(id),
      );
    }

    if (other) {
      for (final result in _feedGlobal) {
        add(
          result.session,
          result.session.directory ?? result.projectDirectory,
          result.projectName,
        );
      }
    }
    // The selected project's own list is fresher than any cached page.
    for (final session in _self.sessionsById.values) {
      if (!includeSubagents && session.parentID != null) continue;
      if (_self._sessionInventoryInitialized &&
          !_self._sessionInventoryIDs.contains(session.id)) {
        continue;
      }
      add(session, session.directory ?? here, null);
    }
    return byID.values.toList();
  }

  ChatFeedSnapshot _ocChatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) {
    _feedSyncRevision();
    return _feedSnapshotMemo[filter] ??= _ocDeriveChatFeed(filter);
  }

  ChatFeedSnapshot _ocDeriveChatFeed(ChatFeedFilter filter) {
    final across = _ocAcross;
    final wantDirectory = filter.projectDirectory == null
        ? null
        : ConnectionController.normalizeDirectoryPath(filter.projectDirectory!);
    final rows = _feedAllItems(includeSubagents: filter.includeSubagents).where(
      (item) {
        if (wantDirectory != null && item.directory != wantDirectory) {
          return false;
        }
        if (filter.agentId != null && item.agentId != filter.agentId) {
          return false;
        }
        if (filter.otherFolders && !item.inOtherFolder) return false;
        if (filter.needsYou || filter.running) {
          return (filter.needsYou && item.status == ChatStatus.needsYou) ||
              (filter.running && item.status == ChatStatus.running);
        }
        return true;
      },
    ).toList();
    int rank(ChatFeedItem item) => switch (item.status) {
      ChatStatus.needsYou => 0,
      ChatStatus.running => 1,
      _ => 2,
    };
    rows.sort((a, b) {
      final byRank = rank(a).compareTo(rank(b));
      if (byRank != 0) return byRank;
      final byTime = b.lastActivity.compareTo(a.lastActivity);
      return byTime != 0 ? byTime : a.sessionID.compareTo(b.sessionID);
    });
    return ChatFeedSnapshot(
      items: List.unmodifiable(rows),
      acrossProjects: across,
      loading: _feedLoading && rows.isEmpty,
      // Card recovery retries on its own (GenUiStateController): a card
      // not read yet is no conversation missing from the list.
      complete: across ? _feedComplete : true,
    );
  }

  List<ProjectSummary> get _ocProjectSummaries {
    final byDirectory = <String, ProjectSummary>{};
    for (final item in _feedAllItems(includeSubagents: false)) {
      // Chats in temporary, home and root folders are listed, but their
      // folders are never projects (reach them through otherFolders).
      if (item.inOtherFolder) continue;
      final before = byDirectory[item.directory];
      final last = before?.lastActivity;
      byDirectory[item.directory] = ProjectSummary(
        directory: item.directory,
        name: item.projectName,
        isGit: item.isGit,
        chatCount: (before?.chatCount ?? 0) + 1,
        runningCount:
            (before?.runningCount ?? 0) +
            (item.status == ChatStatus.running ? 1 : 0),
        needsYouCount:
            (before?.needsYouCount ?? 0) +
            (item.status == ChatStatus.needsYou ? 1 : 0),
        lastActivity: last == null || item.lastActivity.isAfter(last)
            ? item.lastActivity
            : last,
      );
    }
    void addEmpty(String directory, {String? name, required bool isGit}) {
      final where = ConnectionController.normalizeDirectoryPath(directory);
      if (!_feedEligible(where) || byDirectory.containsKey(where)) return;
      byDirectory[where] = ProjectSummary(
        directory: where,
        name: name == null || name.trim().isEmpty
            ? _feedFolderName(where)
            : name.trim(),
        isGit: isGit,
        chatCount: 0,
        runningCount: 0,
        needsYouCount: 0,
      );
    }

    if (_feedCacheCurrent) {
      for (final project in _feedProjects) {
        if (project.id == 'global') continue;
        addEmpty(
          project.directory,
          name: project.name,
          isGit: project.isGit ?? _self.capabilities.projectsAreGitRepositories,
        );
      }
    }
    final last = _ocLastUsed;
    if (last != null) {
      addEmpty(last, isGit: _feedFolderIsGit(last));
    }
    final here = _self.directory;
    if (here != null) addEmpty(here, isGit: _feedProjectFor(here) != null);
    final rows = byDirectory.values.toList()
      ..sort((a, b) {
        final at = a.lastActivity;
        final bt = b.lastActivity;
        if (at != null && bt != null) {
          final byTime = bt.compareTo(at);
          if (byTime != 0) return byTime;
        } else if (at != null || bt != null) {
          return at != null ? -1 : 1;
        }
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
      });
    return List.unmodifiable(rows);
  }

  String? get _ocLastUsed {
    final id = _feedOwnerID;
    if (id == null) return null;
    final saved = _self.store.prefs.getString(lastProjectPreferenceKey(id));
    if (saved == null || !_feedEligible(saved)) return null;
    return saved;
  }

  Future<void> _ocRemember(String directory) async {
    final id = _feedOwnerID;
    if (id == null || _self._deletingReadProfiles.contains(id)) return;
    final where = ConnectionController.normalizeDirectoryPath(directory);
    if (!_feedEligible(where)) return;
    try {
      await _self.store.prefs.setString(lastProjectPreferenceKey(id), where);
    } catch (_) {
      // A last-used hint is a convenience; losing it changes nothing else.
    }
    if (!_self._disposed) _self._notifyListeners();
  }

  /// Forgets the hint when it names [directory] (the folder is gone).
  Future<void> _forgetLastUsedProject(
    String profileID,
    String directory,
  ) async {
    final saved = _self.store.prefs.getString(
      lastProjectPreferenceKey(profileID),
    );
    if (saved == null ||
        !ConnectionController.sameDirectoryPath(saved, directory)) {
      return;
    }
    try {
      await _self.store.prefs.remove(lastProjectPreferenceKey(profileID));
    } catch (_) {}
  }

  Future<String> _ocStartChatIn(String directory, {String? firstPrompt}) async {
    final where = ConnectionController.normalizeDirectoryPath(directory);
    if (!_feedEligible(where)) {
      throw const ProductException('Choose a project folder for this chat.');
    }
    if (_self.api == null) {
      throw const ProductException(
        'OpenCode is not connected. Connect and try again.',
      );
    }
    if (!ConnectionController.sameDirectoryPath(_self.directory, where) ||
        _self.workspace != null) {
      await _self.selectLocation(directory: where);
      if (!ConnectionController.sameDirectoryPath(_self.directory, where)) {
        throw const ProductException(
          'Could not open that project. Check the folder and try again.',
        );
      }
    }
    final session = await _self.createSession();
    await _ocRemember(where);
    final text = firstPrompt?.trim();
    if (text != null && text.isNotEmpty) {
      final transport = await _self._requireActionTransport();
      final agent = _self.selectedAgent;
      final variant = _self.selectedVariant;
      await _self.ensureInteractiveSendAllowed(expectedApi: transport);
      await transport.promptAsync(
        session.id,
        text: text,
        model: _self.selectedModel,
        agent: agent.isEmpty ? null : agent,
        variant: variant.isEmpty ? null : variant,
      );
    }
    _feedScheduleRefresh();
    return session.id;
  }
}
