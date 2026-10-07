part of '../connection.dart';

// Permission requests: hydration, auto-approval and replies.

const _maxAutoApprovedPerSession = 20;

const _permissionHydrationRetryDelays = [
  Duration(milliseconds: 250),
  Duration(seconds: 1),
  Duration(seconds: 2),
];

/// The logical request reviewed by a user, independent of transport recovery.
/// A refresh may recreate equivalent model objects, so compare their contents.
class PendingRequestIdentity {
  PendingRequestIdentity._(
    this._owner,
    this._location,
    this._permission,
    this._id,
    this._contents, {
    _FeedQuestionRoute? feed,
    _FeedPermissionRoute? feedPermission,
  }) : _feed = feed,
       _feedPermission = feedPermission;

  final ConnectionController _owner;
  final int _location;
  final bool _permission;
  final String _id;
  final String _contents;
  final _FeedQuestionRoute? _feed;
  final _FeedPermissionRoute? _feedPermission;
  bool _retired = false;
}

Object? _canonicalRequestValue(Object? value) {
  if (value is Map) {
    final keys = value.keys.map((key) => key.toString()).toList()..sort();
    return {for (final key in keys) key: _canonicalRequestValue(value[key])};
  }
  if (value is Iterable) return value.map(_canonicalRequestValue).toList();
  return value;
}

String _permissionContents(PermissionRequest value) => jsonEncode(
  _canonicalRequestValue({
    'session': value.sessionID,
    'permission': value.permission,
    'patterns': value.patterns,
    'always': value.always,
    'metadata': value.metadata,
    'message': value.message,
    'tool': [value.tool?.messageID, value.tool?.callID],
  }),
);

String _questionContents(PendingQuestion value) => jsonEncode([
  value.sessionID,
  for (final prompt in value.prompts)
    [
      prompt.title,
      prompt.question,
      prompt.multiple,
      prompt.custom,
      prompt.optional,
      for (final choice in prompt.choices) [choice.label, choice.description],
    ],
]);

/// [ConnectionController]'s permission requests.
mixin _ConnectionControllerPermissions on ChangeNotifier {
  ConnectionController get _self;

  bool permissionsLoading = false;
  String? permissionsError;

  /// Outstanding permission asks keyed by request ID. Includes requests the
  /// app is answering automatically for a session with auto-approval on
  /// while that reply is on the wire; [awaitingPermissions] excludes them.
  Map<String, PermissionRequest> permissions = {};

  /// Request IDs whose automatic "once" reply is in flight. They stay in
  /// [permissions] so the reply plumbing keeps its identity checks, but no
  /// surface asks a person about them unless the reply fails.
  final Set<String> _autoApprovingPermissionIDs = {};

  /// Recent automatic approvals per session, newest last, for the quiet
  /// in-chat indicator. In-memory and bounded; cleared with the connection.
  final Map<String, List<AutoApprovedPermission>> _autoApprovedBySession = {};

  /// Requests whose automatic reply failed, keyed by request ID with the
  /// failure text. The request stays pending and visible for review.
  final Map<String, String> _autoApprovalFailures = {};
  final Set<String> _resolvedPermissionIDs = {};
  final Map<String, ({String sessionID, String permissionID})>
  _legacyPermissionIdentities = {};
  final Map<String, String> _v2PermissionSessions = {};
  int _permissionRevision = 0;
  Timer? _permissionHydrationRetry;
  int _permissionHydrationGeneration = 0;

  /// The first request in [sessionID] that needs a person.
  PermissionRequest? permissionForSession(String sessionID) {
    for (final permission in awaitingPermissions) {
      if (permission.sessionID == sessionID) return permission;
    }
    return null;
  }

  /// Requests in [sessionID] that need a person; an automatic reply on the
  /// wire is not one of them until it fails.
  List<PermissionRequest> permissionsForSession(String sessionID) =>
      awaitingPermissions
          .where((permission) => permission.sessionID == sessionID)
          .toList();

  /// Pending requests that need a person: everything in [permissions] except
  /// the ones an automatic "once" reply is currently answering.
  Iterable<PermissionRequest> get awaitingPermissions =>
      _autoApprovingPermissionIDs.isEmpty
      ? permissions.values
      : permissions.values.where(
          (permission) => !_autoApprovingPermissionIDs.contains(permission.id),
        );

  int get awaitingPermissionCount => _autoApprovingPermissionIDs.isEmpty
      ? permissions.length
      : awaitingPermissions.length;

  /// True while the app is answering [requestID] automatically.
  bool isAutoApproving(String requestID) =>
      _autoApprovingPermissionIDs.contains(requestID);

  /// Why the automatic reply for [requestID] failed, when it did. The
  /// request is still pending and shown for review.
  String? autoApprovalFailure(String requestID) =>
      _autoApprovalFailures[requestID];

  /// Permissions this phone approved automatically in [sessionID] on the
  /// current connection, oldest first.
  List<AutoApprovedPermission> autoApprovedFor(String sessionID) =>
      List.unmodifiable(
        _autoApprovedBySession[sessionID] ?? const <AutoApprovedPermission>[],
      );

  /// The approval setting that applies to [sessionID] for the selected
  /// profile, after walking its parent chain through [sessionsById].
  EffectiveAutoApproval autoApprovalFor(String sessionID) =>
      _self._autoApprovalFor(sessionID);

  /// Whether this server runs in "approve everything" mode on this phone.
  bool get approvesEverything =>
      _self.sessionAutoApproval.approvesEverything(_self._autoApprovalProfile);

  /// Saves the server-wide choice. Throws when storage refuses.
  Future<void> setApprovesEverything(bool value) =>
      _self._setApprovesEverything(value);

  /// Stores the session's own approval setting, or clears it (null) so the
  /// session follows its parent again. Throws when storage refuses.
  Future<void> setSessionAutoApproval(
    String sessionID,
    SessionAutoApproval? setting,
  ) => _self._setSessionAutoApproval(sessionID, setting);

  final _feedPermissionSnapshots = Expando<_FeedPermissionRoute>();
  final _feedPermissionKeys = Expando<String>();
  int _feedPermissionSequence = 0;

  /// Pure read from the row's native feed inventory. Its display ID is local
  /// and scope-bound so shared Undo/receipt ledgers never mix wire IDs.
  PermissionRequest? permissionForFeedItem(ChatFeedItem item) =>
      _self._permissionForFeedItem(item);

  PendingRequestIdentity permissionIdentityForFeedItem(
    ChatFeedItem item,
    PermissionRequest permission,
  ) => _self._permissionIdentityForFeedItem(item, permission);

  Future<void> answerPermissionForFeedItem(
    ChatFeedItem item,
    String response, {
    required PendingRequestIdentity expectedRequest,
    String? message,
  }) => _self._replyToFeedPermission(
    item,
    response,
    expectedRequest,
    message: message,
  );

  final _permissionReads = _PendingReadGate();

  /// Reads the waiting permissions; a read already running for this stream
  /// connect is shared rather than repeated.
  Future<void> refreshPendingPermissions() => _permissionReads.run(
    _self._pendingReadEpoch,
    () =>
        PerfTrace.span('permissions.refresh', _self._refreshPendingPermissions),
  );

  /// [message] rides only on v2 rejections (steering-by-rejection); the v1
  /// reply shape has no field for it and ignores it.
  Future<void> answerPermission(
    String requestID,
    String response, {
    String? message,
    PendingRequestIdentity? expectedRequest,
  }) => _self._answerPermission(
    requestID,
    response,
    message: message,
    expectedRequest: expectedRequest,
  );

  PendingRequestIdentity permissionIdentity(PermissionRequest request) =>
      _self._permissionIdentity(request);

  PendingRequestIdentity questionIdentity(PendingQuestion request) =>
      _self._questionIdentity(request);

  bool isRequestPending(PendingRequestIdentity request) =>
      _self._isRequestPending(request);

  final _pendingReplies =
      <
        (int, bool, String, String, String?),
        ({PendingRequestIdentity request, Future<void> future})
      >{};

  /// True when [requestID] arrived over the OpenCode 2 permission contract,
  /// whose reject reply accepts an optional message shown to the model. The
  /// permission sheet omits its reject-message field otherwise.
  bool permissionSupportsRejectMessage(String requestID) =>
      _v2PermissionSessions.containsKey(requestID);
}

extension _ConnectionControllerPermissionsImpl on ConnectionController {
  void _handlePermission(Map<String, dynamic> props) {
    final permission = PermissionRequest.fromJson(props);
    if (permission.sessionID.isEmpty || permission.id.isEmpty) return;
    _resolvedPermissionIDs.remove(permission.id);
    _legacyPermissionIdentities.remove(permission.id);
    _v2PermissionSessions.remove(permission.id);
    permissions[permission.id] = permission;
    _permissionRevision += 1;
    _maybeAutoApprove(permission);
    _syncInputAlerts();
    _notifyListeners();
  }

  void _handlePermissionV2(Map<String, dynamic> props) {
    final permission = PermissionRequest.fromJson({
      'id': props['id'],
      'sessionID': props['sessionID'],
      'permission': props['action'],
      'patterns': props['resources'],
      'metadata': props['metadata'],
      'always': props['save'],
      'message': props['message'],
      if (props['source'] is Map) 'tool': props['source'],
    });
    if (permission.sessionID.isEmpty || permission.id.isEmpty) return;
    _resolvedPermissionIDs.remove(permission.id);
    _legacyPermissionIdentities.remove(permission.id);
    _v2PermissionSessions[permission.id] = permission.sessionID;
    permissions[permission.id] = permission;
    _permissionRevision += 1;
    _maybeAutoApprove(permission);
    _syncInputAlerts();
    _notifyListeners();
  }

  void _handleLegacyPermission(Map<String, dynamic> props) {
    final id = props['id']?.toString() ?? '';
    final sessionID = props['sessionID']?.toString() ?? '';
    if (id.isEmpty || sessionID.isEmpty) return;
    final rawPattern = props['pattern'];
    final patterns = rawPattern is List
        ? rawPattern.map((item) => item.toString()).toList()
        : rawPattern == null
        ? const <String>[]
        : [rawPattern.toString()];
    final messageID = props['messageID']?.toString() ?? '';
    final callID = props['callID']?.toString() ?? '';
    final permission = PermissionRequest(
      id: id,
      sessionID: sessionID,
      permission: props['type']?.toString() ?? '',
      patterns: patterns,
      metadata: props['metadata'] is Map
          ? Map<String, dynamic>.from(props['metadata'] as Map)
          : const {},
      tool: messageID.isNotEmpty && callID.isNotEmpty
          ? PermissionTool(messageID: messageID, callID: callID)
          : null,
    );
    _resolvedPermissionIDs.remove(id);
    _v2PermissionSessions.remove(id);
    permissions[id] = permission;
    _legacyPermissionIdentities[id] = (sessionID: sessionID, permissionID: id);
    _permissionRevision += 1;
    _maybeAutoApprove(permission);
    _syncInputAlerts();
    _notifyListeners();
  }

  void _handlePermissionReply(Map<String, dynamic> props) {
    final requestID =
        props['requestID']?.toString() ?? props['permissionID']?.toString();
    if (requestID == null || requestID.isEmpty) return;
    _resolvePermission(requestID);
  }

  void _resolvePermission(String requestID) {
    _resolvedPermissionIDs.add(requestID);
    permissions.remove(requestID);
    _autoApprovalFailures.remove(requestID);
    _legacyPermissionIdentities.remove(requestID);
    _v2PermissionSessions.remove(requestID);
    _permissionRevision += 1;
    _syncInputAlerts();
    _notifyListeners();
  }

  String get _autoApprovalProfile => profile?.id ?? '';

  /// The body of [autoApprovalFor].
  EffectiveAutoApproval _autoApprovalFor(String sessionID) =>
      sessionAutoApproval.effectiveFor(
        _autoApprovalProfile,
        sessionID,
        (id) => sessionsById[id]?.parentID,
      );

  /// The body of [setApprovesEverything].
  Future<void> _setApprovesEverything(bool value) async {
    await sessionAutoApproval.setApprovesEverything(
      _autoApprovalProfile,
      value,
    );
    if (_disposed) return;
    _sweepAutoApprovals();
    _notifyListeners();
  }

  /// The body of [setSessionAutoApproval].
  Future<void> _setSessionAutoApproval(
    String sessionID,
    SessionAutoApproval? setting,
  ) async {
    await sessionAutoApproval.set(_autoApprovalProfile, sessionID, setting);
    if (_disposed) return;
    _sweepAutoApprovals();
    _notifyListeners();
  }

  /// Answers a waiting request with "once" when its session runs with
  /// automatic approval and the app is connected. The reply rides the live
  /// transport like a notification action: no wake reconciliation, and the
  /// same identity checks as a tap on Allow once. Questions and forms never
  /// take this path; only permissions do. It runs for a request as it
  /// arrives, for requests a reconnect hydration finds, and (through
  /// [_sweepAutoApprovals]) for every request already waiting when an
  /// automatic mode is switched on: a person who chose automatic approval
  /// is not asked.
  void _maybeAutoApprove(PermissionRequest permission) {
    final currentApi = api;
    if (_disposed || currentApi == null || !isConnected) return;
    if (_autoApprovalProfile.isEmpty) return;
    // The person's own choice for this conversation (or "Approve
    // everything", confirmed when set) is the consent; the AI Team's
    // supervision level governs the team, not conversations.
    if (!autoApprovalFor(permission.sessionID).automatic) return;
    if (_resolvedPermissionIDs.contains(permission.id)) return;
    if (!_autoApprovingPermissionIDs.add(permission.id)) return;
    // Answer after the event that announced the request has been delivered:
    // a gateway whose events are synchronous (Paseo) announces the answer on
    // the same stream, which can't fire while it is still firing.
    unawaited(
      Future<void>.microtask(() => _autoApprove(currentApi, permission)),
    );
  }

  /// Applies the effective mode to every request already waiting: the
  /// ones in sessions that are now automatic are answered "once".
  void _sweepAutoApprovals() {
    for (final permission in permissions.values.toList()) {
      _maybeAutoApprove(permission);
    }
  }

  Future<void> _autoApprove(
    ServerGateway currentApi,
    PermissionRequest permission,
  ) async {
    final identity = permissionIdentity(permission);
    final scope = _automaticActScope();
    var failure = '';
    var confirmed = false;
    try {
      await _sendPermissionReply(
        currentApi,
        permission.id,
        'once',
        expectedRequest: identity,
        onConfirmed: () => confirmed = true,
      );
    } catch (_) {
      failure = 'Could not confirm automatic approval. Review the request.';
    }
    if (_disposed) return;
    _autoApprovingPermissionIDs.remove(permission.id);
    final stillPending = isRequestPending(identity);
    if (confirmed &&
        !stillPending &&
        failure.isEmpty &&
        _resolvedPermissionIDs.contains(permission.id)) {
      final record = _autoApprovedBySession.putIfAbsent(
        permission.sessionID,
        () => [],
      );
      if (record.length >= _maxAutoApprovedPerSession) record.removeAt(0);
      final at = DateTime.now();
      record.add(
        AutoApprovedPermission(
          requestID: permission.id,
          sessionID: permission.sessionID,
          permission: permission.permission,
          patterns: List.unmodifiable(permission.patterns),
          at: at,
        ),
      );
      // Filed by the conversation it was allowed in; the request's patterns
      // (paths, commands) never reach the history.
      if (scope != null) {
        unawaited(
          recordAutomaticAct(
            profileId: scope.profileId,
            location: scope.project,
            kind: AutomaticActKind.permissionApproval,
            target: _automaticActSessionTitle(permission.sessionID),
            eventId: 'permission.auto:${permission.id}',
            at: at,
            sessionId: permission.sessionID,
          ),
        );
      }
    } else if (stillPending) {
      // The reply did not land (transport gone, refused, or the wire went
      // quiet): the request stays pending and a person sees it, with the
      // reason when there is one.
      _autoApprovalFailures[permission.id] = failure.isEmpty
          ? 'Not connected to OpenCode'
          : failure;
    }
    _permissionRevision += 1;
    _syncInputAlerts();
    _notifyListeners();
  }

  Future<void> _refreshPendingPermissions() async {
    final currentApi = api;
    final connectionGeneration = _generation;
    if (currentApi == null) return;
    _permissionHydrationRetry?.cancel();
    _permissionHydrationRetry = null;
    final generation = ++_permissionHydrationGeneration;
    permissionsLoading = true;
    permissionsError = null;
    _notifyListeners();
    await _hydratePendingPermissions(
      currentApi,
      connectionGeneration,
      generation,
      0,
    );
  }

  Future<void> _hydratePendingPermissions(
    ServerGateway currentApi,
    int connectionGeneration,
    int generation,
    int attempt,
  ) async {
    final revision = _permissionRevision;
    final permissionsAtStart = Map<String, PermissionRequest>.of(permissions);
    try {
      final results = await _loadPendingPermissions(currentApi);
      if (!_isCurrentPermissionHydration(
        currentApi,
        connectionGeneration,
        generation,
      )) {
        return;
      }
      final unresolved = {
        for (final permission in results.pending)
          if (!_resolvedPermissionIDs.contains(permission.id))
            permission.id: permission,
      };
      if (!results.v2Succeeded) {
        for (final entry in permissions.entries) {
          if (_v2PermissionSessions.containsKey(entry.key) &&
              !_resolvedPermissionIDs.contains(entry.key)) {
            unresolved.putIfAbsent(entry.key, () => entry.value);
          }
        }
      }
      if (revision == _permissionRevision) {
        _resolvedPermissionIDs.addAll(
          permissions.keys.where((id) => !unresolved.containsKey(id)),
        );
        permissions = unresolved;
      } else {
        for (final entry in permissionsAtStart.entries) {
          if (!unresolved.containsKey(entry.key) &&
              identical(permissions[entry.key], entry.value)) {
            permissions.remove(entry.key);
            _resolvedPermissionIDs.add(entry.key);
          }
        }
        permissions.addAll(unresolved);
      }
      _legacyPermissionIdentities.removeWhere(
        (id, _) => !permissions.containsKey(id),
      );
      _v2PermissionSessions.removeWhere(
        (id, _) => !permissions.containsKey(id),
      );
      if (results.v2Succeeded) {
        _v2PermissionSessions.removeWhere(
          (id, _) => !results.v2IDs.contains(id),
        );
        for (final id in results.v2IDs) {
          final permission = permissions[id];
          if (permission != null) {
            _v2PermissionSessions[id] = permission.sessionID;
          }
        }
      }
      permissionsLoading = false;
      permissionsError = null;
      _observeAttentionRead(AttentionKind.permission);
      // Requests that waited while the app was away are answered too when
      // their session is automatic.
      _sweepAutoApprovals();
      _syncInputAlerts();
      _notifyListeners();
    } catch (error) {
      if (!_isCurrentPermissionHydration(
            currentApi,
            connectionGeneration,
            generation,
          ) ||
          attempt >= _permissionHydrationRetryDelays.length) {
        if (_isCurrentPermissionHydration(
          currentApi,
          connectionGeneration,
          generation,
        )) {
          permissionsLoading = false;
          permissionsError = error.toString();
          _recordLocationError(permissionsError!);
          _notifyListeners();
        }
        return;
      }
      _permissionHydrationRetry = Timer(
        _permissionHydrationRetryDelays[attempt],
        () => unawaited(
          _hydratePendingPermissions(
            currentApi,
            connectionGeneration,
            generation,
            attempt + 1,
          ),
        ),
      );
    }
  }

  Future<
    ({List<PermissionRequest> pending, Set<String> v2IDs, bool v2Succeeded})
  >
  _loadPendingPermissions(ServerGateway currentApi) async {
    List<PermissionRequest>? legacy;
    List<PermissionRequest>? v2;
    Object? legacyError;
    Object? v2Error;
    await Future.wait<void>([
      () async {
        try {
          legacy = await currentApi.pendingPermissions();
        } catch (error) {
          legacyError = error;
        }
      }(),
      () async {
        if (!_v2Probe('permission')) return;
        try {
          v2 = await currentApi.pendingPermissionsV2();
        } catch (error) {
          v2Error = error;
          _v2Failed('permission', error);
        }
      }(),
    ]);
    if (legacy == null && v2 == null) {
      throw ApiException(
        'Could not hydrate pending permissions: '
        '${legacyError ?? v2Error ?? 'no endpoint available'}',
      );
    }
    final merged = <String, PermissionRequest>{
      for (final permission in legacy ?? const <PermissionRequest>[])
        permission.id: permission,
      // Prefer V2 when both APIs briefly expose the same request so reply
      // routing follows the newer, session-scoped contract.
      for (final permission in v2 ?? const <PermissionRequest>[])
        permission.id: permission,
    };
    return (
      pending: merged.values.toList(),
      v2IDs: {
        for (final permission in v2 ?? const <PermissionRequest>[])
          permission.id,
      },
      v2Succeeded: v2 != null,
    );
  }

  bool _isCurrentPermissionHydration(
    ServerGateway currentApi,
    int connectionGeneration,
    int generation,
  ) =>
      _isCurrent(connectionGeneration, currentApi) &&
      generation == _permissionHydrationGeneration;

  void _cancelPermissionHydration() {
    _permissionHydrationGeneration += 1;
    _permissionHydrationRetry?.cancel();
    _permissionHydrationRetry = null;
    permissionsLoading = false;
  }

  /// The body of [answerPermission].
  Future<void> _answerPermission(
    String requestID,
    String response, {
    String? message,
    PendingRequestIdentity? expectedRequest,
  }) => _sendPermissionReply(
    api,
    requestID,
    response,
    message: message,
    expectedRequest: expectedRequest,
    prepareTransport: true,
  );

  /// The body of [permissionIdentity].
  PendingRequestIdentity _permissionIdentity(PermissionRequest request) =>
      PendingRequestIdentity._(
        this,
        locationRevision,
        true,
        request.id,
        _permissionContents(request),
        feedPermission: _feedPermissionSnapshots[request],
      );

  /// The body of [questionIdentity].
  PendingRequestIdentity _questionIdentity(PendingQuestion request) =>
      PendingRequestIdentity._(
        this,
        locationRevision,
        false,
        request.id,
        _questionContents(request),
        feed: _feedQuestionSnapshots[request],
      );

  /// The body of [isRequestPending].
  bool _isRequestPending(PendingRequestIdentity request) {
    if (request._retired ||
        _disposed ||
        request._owner != this ||
        request._location != locationRevision) {
      request._retired = true;
      return false;
    }
    bool pending;
    if (request._feedPermission case final route?) {
      pending = _isFeedPermissionPending(route);
    } else if (request._feed case final route?) {
      pending = _isFeedQuestionPending(route);
    } else if (request._permission) {
      final current = permissions[request._id];
      pending =
          current != null && _permissionContents(current) == request._contents;
    } else {
      final current = questions[request._id];
      pending =
          current != null && _questionContents(current) == request._contents;
    }
    if (!pending) request._retired = true;
    return pending;
  }

  /// A notification, sheet and inline card share one slot. Claim it before
  /// waking the transport; a second decision waits for the first result.
  Future<void> _withPendingReply(
    PendingRequestIdentity request,
    Future<void> Function() send,
  ) {
    if (!isRequestPending(request)) return Future.value();
    final key = (
      request._location,
      request._permission,
      request._id,
      request._contents,
      request._feedPermission?.item.identity ?? request._feed?.item.identity,
    );
    final existing = _pendingReplies[key];
    if (existing != null && isRequestPending(existing.request)) {
      return existing.future;
    }
    final completion = Completer<void>();
    _pendingReplies[key] = (request: request, future: completion.future);
    // Remember a removal even if a server reuses the ID before wake finishes.
    void checkPending() => isRequestPending(request);
    addListener(checkPending);
    () async {
      try {
        await send();
        completion.complete();
      } catch (error, stack) {
        // Resolution or replacement makes the old failure irrelevant.
        if (!isRequestPending(request)) {
          completion.complete();
        } else {
          completion.completeError(error, stack);
        }
      } finally {
        removeListener(checkPending);
        if (identical(_pendingReplies[key]?.future, completion.future)) {
          _pendingReplies.remove(key);
        }
      }
    }();
    return completion.future;
  }

  /// Sends one permission reply on an already-resolved transport. Notification
  /// actions use this directly with the live background transport because the
  /// foreground path's wake reconciliation doubles as an app resume, which
  /// would clear every posted alert.
  Future<void> _sendPermissionReply(
    ServerGateway? currentApi,
    String requestID,
    String response, {
    String? message,
    PendingRequestIdentity? expectedRequest,
    bool prepareTransport = false,
    void Function()? onConfirmed,
  }) async {
    if (expectedRequest != null &&
        (!expectedRequest._permission || expectedRequest._id != requestID)) {
      throw ArgumentError('Permission request identity does not match');
    }
    if (expectedRequest?._feedPermission case final route?) {
      return _replyToFeedPermission(
        route.item,
        response,
        expectedRequest!,
        message: message,
      );
    }
    if (expectedRequest != null && !isRequestPending(expectedRequest)) return;
    final permission = permissions[requestID];
    if (permission == null) {
      if (_resolvedPermissionIDs.contains(requestID)) return;
      throw StateError('Permission request $requestID is no longer pending');
    }
    final request = expectedRequest ?? permissionIdentity(permission);
    return _withPendingReply(request, () async {
      final transport = prepareTransport
          ? await _requireActionTransport()
          : currentApi;
      if (!isRequestPending(request)) return;
      if (transport == null) throw StateError('Not connected to OpenCode');
      await _writePermissionReply(
        transport,
        request,
        response,
        message: message,
        onConfirmed: onConfirmed,
      );
    });
  }

  Future<void> _writePermissionReply(
    ServerGateway currentApi,
    PendingRequestIdentity request,
    String response, {
    String? message,
    void Function()? onConfirmed,
  }) async {
    final requestID = request._id;
    final permission = permissions[requestID]!;
    final generation = _generation;
    try {
      final legacyIdentity = _legacyPermissionIdentities[requestID];
      final v2SessionID = _v2PermissionSessions[requestID];
      if (v2SessionID != null) {
        await currentApi.respondPermissionV2(
          v2SessionID,
          permission.id,
          response,
          message: message,
        );
      } else {
        await currentApi.respondPermission(
          permission.id,
          response,
          legacySessionID: legacyIdentity?.sessionID,
          legacyPermissionID: legacyIdentity?.permissionID,
          message: message,
        );
      }
      if (!_isCurrent(generation, currentApi)) return;
      onConfirmed?.call();
      if (!isRequestPending(request)) return;
      _resolvePermission(requestID);
    } catch (error) {
      if (!_isCurrent(generation, currentApi) || !isRequestPending(request)) {
        return;
      }
      if (_resolvedPermissionIDs.contains(requestID)) return;
      if (error is ApiException && error.isPermissionNotFound(permission.id)) {
        _resolvePermission(requestID);
        return;
      }
      lastError = error.toString();
      _notifyListeners();
      rethrow;
    }
  }
}
