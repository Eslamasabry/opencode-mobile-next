part of '../connection.dart';

mixin _ConnectionControllerGenUi on ChangeNotifier implements GenUiController {
  ConnectionController get _self;
  ConnectionController? _genUiParent;
  GenUiStateController? _genUiLocal;
  GenUiInstaller? _genUiInstaller;
  final _genUiSetup = <String, GenUiSetupStatus>{};
  final _genUiChanges = <String, Future<void>>{};
  final _genUiAttempted = <String>{};
  final _genUiDesired = <String, bool>{};
  final _genUiSettingGeneration = <String, int>{};
  final _genUiTargets = <String, GenUiTarget>{};
  final _genUiPhoneChannels = <String, LiveEventChannel>{};
  final _genUiPhoneSources =
      <
        GenUiScope,
        ({PaseoGateway gateway, Iterable<String> Function()? sessions})
      >{};
  final _genUiLocationGateways = <GenUiScope, ServerGateway>{};
  final _genUiFeedRefreshers = <GenUiScope, _GenUiFeedRefresh>{};
  Timer? _genUiTimer;
  GenUiScope? _genUiLastScope;

  GenUiStateController get _genUiState =>
      _genUiParent?._genUiState ??
      (_genUiLocal ??= GenUiStateController(
        _self.store.prefs,
        delayedAnswers: _self.delayedAnswers,
      )..addListener(_self._genUiChanged));

  String? get _genUiOwnerID =>
      _genUiParent?._genUiOwnerID ??
      _self._agentOwnerProfileId ??
      (_self._connectedProfile ?? _self.profile)?.id;

  bool get _genUiManaged {
    if (_genUiParent != null) return _genUiParent!._genUiManaged;
    final owner = _self._connectedProfile ?? _self.profile;
    return owner != null &&
        BuiltinLinux.managesServerUrl(owner.baseUrl) &&
        (BuiltinLinux.supported || _genUiInstaller != null);
  }

  @override
  bool get genUiEnabled {
    if (_genUiParent != null) return _genUiParent!.genUiEnabled;
    final id = _genUiOwnerID;
    return id != null &&
        _genUiManaged &&
        (_genUiDesired[id] ??
            _self.store.prefs.getBool('oc.genui.enabled.$id') ??
            true);
  }

  @override
  GenUiSetupStatus get genUiStatus {
    if (_genUiParent != null) return _genUiParent!.genUiStatus;
    if (!_genUiManaged) {
      return const GenUiSetupUnavailable(
        reason: GenUiSetupProblem.unsupportedHost,
      );
    }
    final id = _genUiOwnerID;
    if (id == null) {
      return const GenUiSetupUnavailable(
        reason: GenUiSetupProblem.unsupportedHost,
      );
    }
    return _genUiSetup[id] ??
        (genUiEnabled
            ? const GenUiSetupUnavailable(
                reason: GenUiSetupProblem.notQualified,
              )
            : const GenUiSetupOff());
  }

  @override
  Future<void> setGenUiEnabled(bool on) =>
      _genUiParent?.setGenUiEnabled(on) ?? _self._setGenUiEnabled(on);

  GenUiScope? get _genUiScope {
    final id = _genUiOwnerID, folder = _self.directory;
    if (id == null || folder == null) return null;
    return GenUiScope(
      profileID: id,
      sourceId: _self.isAgentBackend
          ? _paseoSourceId(folder)
          : _self.isSideBackend
          ? _sideSourceId(id)
          : _openCodeSourceId,
      directory: folder,
      workspace: _self.workspace,
    );
  }

  @override
  List<GenUiCard> waitingCardsForSession(String sessionID) {
    final scope = _genUiScope;
    return scope == null
        ? const []
        : _genUiState.waiting(scope, _self._genUiSessionID(scope, sessionID));
  }

  @override
  List<GenUiCard> waitingCardsForFeedItem(ChatFeedItem item) {
    if (item.sourceId?.startsWith('paseo:') == true) {
      final owner = _self._genUiPhoneController;
      if (!identical(owner, _self)) return owner.waitingCardsForFeedItem(item);
    }
    final side = _self.isSideBackend
        ? null
        : _self._sideForSource(item.sourceId);
    if (side != null) return side.waitingCardsForFeedItem(item);
    final owner = item.sourceId?.startsWith('paseo:') == true
        ? _self._paProfile?.id
        : _genUiOwnerID;
    if (owner == null) return const [];
    final source =
        item.sourceId ??
        (_self.isSideBackend ? _sideSourceId(owner) : _openCodeSourceId);
    final scope = GenUiScope(
      profileID: owner,
      sourceId: source,
      directory: item.directory,
      workspace: source.startsWith('paseo:') ? null : _self.workspace,
    );
    return _genUiState.waiting(
      scope,
      _self._genUiSessionID(scope, item.sessionID),
    );
  }

  @override
  GenUiParse? genUiCardForPart(String sessionID, String messageID, Part part) {
    final scope = _genUiScope;
    return scope == null
        ? null
        : _genUiState.cardForPart(
            scope,
            _self._genUiSessionID(scope, sessionID),
            messageID,
            part,
          );
  }

  @override
  GenUiCardState genUiStateForCard(GenUiCard card) =>
      _self._genUiStateForScope(card.scope).state(card);
  @override
  String? genUiAnswerSummary(GenUiCard card) =>
      _self._genUiStateForScope(card.scope).summary(card);
  @override
  GenUiDeliveryState genUiDeliveryFor(GenUiCard card) =>
      _self._genUiHasAliasDispatch(card)
      ? GenUiDeliveryState.deliveryUnknown
      : _self._genUiStateForScope(card.scope).delivery(card);
  @override
  void undoGenUiAnswer(GenUiCard card) =>
      _self._genUiStateForScope(card.scope).undo(card);
  @override
  Future<void> answerGenUi(
    GenUiCard card,
    GenUiAnswer answer, {
    List<PromptAttachment> attachments = const [],
  }) {
    final side = _self.isSideBackend
        ? null
        : _self._sideForSource(card.scope.sourceId);
    if (side != null) {
      return side.answerGenUi(card, answer, attachments: attachments);
    }
    if (_self._genUiHasAliasDispatch(card)) {
      return Future.error(
        const ProductException(
          'Check the conversation before sending this answer again.',
        ),
      );
    }
    final state = _self._genUiStateForScope(card.scope);
    final owner = _genUiParent ?? _self._genUiPhoneController;
    return state.answer(card, answer, attachments).whenComplete(() {
      // Undo and pre-send failures do not change the conversation. A sent
      // answer needs a fresh timestamp even if transcript confirmation lags.
      if (state.state(card) == GenUiCardState.answered ||
          state.delivery(card) == GenUiDeliveryState.deliveryUnknown) {
        owner._genUiFeedRefreshers[card.scope]?.queue();
      }
    });
  }
}

extension _ConnectionGenUiImpl on ConnectionController {
  // A new phone conversation has a local draft ID in its feed gateway and a
  // daemon ID in its chat gateway. Card state and its durable send fence must
  // use the same identity in both views, scoped to this exact phone source.
  String _genUiSessionID(GenUiScope scope, String id) {
    final owner = _genUiParent ?? _genUiPhoneController;
    return owner._genUiPhoneSources[scope]?.gateway.daemonSessionId(id) ?? id;
  }

  bool _genUiHasAliasDispatch(GenUiCard card) {
    if (!card.scope.sourceId.startsWith('paseo:')) return false;
    // Old local-ID dispatch records remain untouched. After restart their
    // alias may no longer be known; a matching scoped call stays fail closed.
    try {
      return _genUiStateForScope(card.scope).journal
          .read(card.scope.profileID)
          .any(
            (entry) =>
                entry.scope == card.scope &&
                entry.sessionID != card.sessionID &&
                entry.messageID == card.messageID &&
                entry.callID == card.callID &&
                entry.dispatchID != null,
          );
    } catch (_) {
      return true;
    }
  }

  ConnectionController get _genUiPhoneController {
    final id = _paProfile?.id;
    if (id != null && id != (_connectedProfile ?? profile)?.id) {
      for (final side in _sides.values) {
        if ((side._connectedProfile ?? side.profile)?.id == id) return side;
      }
    }
    return this;
  }

  GenUiStateController _genUiStateForScope(GenUiScope scope) {
    if (scope.profileID != _genUiOwnerID) {
      for (final side in _sides.values) {
        if (side._genUiOwnerID == scope.profileID) return side._genUiState;
      }
    }
    return _genUiState;
  }

  void _genUiChanged() {
    if (_disposed) return;
    _notifyListeners();
    final backend = _paBackend;
    if (backend != null && !backend._disposed) backend._notifyListeners();
  }

  Future<void> _setGenUiEnabled(bool on) {
    final id = _genUiOwnerID;
    if (id == null ||
        !_genUiManaged ||
        _disposed ||
        _deletingReadProfiles.contains(id)) {
      return Future.error(
        const ProductException('Agent cards are unavailable on this server.'),
      );
    }
    _genUiAttempted.add(id);
    _genUiDesired[id] = on;
    final settingGeneration = (_genUiSettingGeneration[id] ?? 0) + 1;
    _genUiSettingGeneration[id] = settingGeneration;
    final prior = _genUiChanges[id] ?? Future.value();
    // Disable admission immediately, even while an earlier install drains.
    if (!on) {
      _genUiSetup[id] = const GenUiSetupOff();
      final scope = _genUiScope;
      if (scope != null) _genUiState.forgetSource(scope);
      for (final entry in _genUiLocationGateways.entries) {
        _genUiState.forgetSource(entry.key);
      }
      for (final folder in _paSources.keys) {
        _genUiState.forgetSource(
          GenUiScope(
            profileID: id,
            sourceId: _paseoSourceId(folder),
            directory: folder,
          ),
        );
      }
    }
    late final Future<void> work;
    work = prior
        .catchError((Object _) {})
        .then((_) async {
          if (_disposed ||
              _deletingReadProfiles.contains(id) ||
              _genUiSettingGeneration[id] != settingGeneration) {
            return;
          }
          if (!await store.prefs.setBool('oc.genui.enabled.$id', on)) {
            _genUiSetup[id] = const GenUiSetupFailed(
              reason: GenUiSetupProblem.storageFailed,
            );
            throw const ProductException(
              'The card setting could not be saved.',
            );
          }
          _genUiState.invalidateProfile(id);
          _genUiSetup[id] = const GenUiSetupInstalling();
          _genUiChanged();
          final installer = _genUiInstaller ??= ManagedGenUiInstaller.builtin();
          // Stage managed registrations; the installer independently gates
          // effective readiness on end-to-end runtime qualification.
          final agents = GenUiAgent.values.toSet();
          final result = await installer.setEnabled(
            profileId: id,
            agents: agents,
            enabled: on,
          );
          if (_disposed ||
              _deletingReadProfiles.contains(id) ||
              _genUiOwnerID != id ||
              _genUiSettingGeneration[id] != settingGeneration) {
            return;
          }
          _genUiSetup[id] = result;
          _genUiSync();
          for (final entry in _genUiPhoneSources.entries.toList()) {
            if (entry.key.profileID != id) continue;
            _genUiAttachPhone(entry.key, entry.value.gateway);
            for (final sid
                in entry.value.sessions?.call().take(20) ?? const <String>[]) {
              _genUiQueueTarget(GenUiTarget(entry.key, sid));
            }
          }
          unawaited(_genUiState.recover(const []));
          _genUiRefreshFeed();
          _genUiChanged();
        })
        .whenComplete(() {
          if (identical(_genUiChanges[id], work)) _genUiChanges.remove(id);
        });
    _genUiChanges[id] = work;
    return work;
  }

  void _genUiSync() {
    // The phone feed owns one stable gateway per directory. The active chat
    // contributes authoritative transcripts but must not replace that source
    // with its synthetic profile/temporary gateway.
    if (isAgentBackend && _genUiParent != null) {
      final gateway = api, scope = _genUiScope;
      final generation = _generation;
      if (gateway is PaseoGateway && scope != null) {
        gateway.genUiShowAllowed = () =>
            !_disposed &&
            _generation == generation &&
            identical(api, gateway) &&
            status == StreamStatus.connected &&
            _genUiScope == scope &&
            genUiEnabled &&
            genUiStatus.agents.contains(GenUiAgent.claude) &&
            _genUiState.available(scope);
      }
      return;
    }
    final scope = _genUiScope,
        gateway = api,
        owner = _connectedProfile ?? profile;
    if (scope == null || gateway == null || owner == null) return;
    final last = _genUiLastScope;
    if (last != null && last != scope) _genUiState.forgetSource(last);
    _genUiLastScope = scope;
    final generation = _generation, endpoint = owner.baseUrl;
    final agent = isAgentBackend
        ? GenUiAgent.claude
        : owner.flavor == ServerFlavor.v2
        ? GenUiAgent.openCode2
        : GenUiAgent.openCode1;
    _genUiState.register(
      scope,
      gateway,
      endpoint: receipt_crypto.sha256.convert(utf8.encode(endpoint)).toString(),
      current: () =>
          !_disposed &&
          _generation == generation &&
          identical(api, gateway) &&
          status == StreamStatus.connected &&
          (gateway is! PaseoGateway ||
              (gateway.transport.connected &&
                  gateway.transport.serverVersion == '0.9.2')) &&
          (_connectedProfile ?? profile)?.baseUrl == endpoint &&
          genUiEnabled &&
          !_deletingReadProfiles.contains(scope.profileID),
      ready: genUiStatus.agents.contains(agent),
    );
    if (_genUiParent == null &&
        genUiEnabled &&
        _genUiAttempted.add(scope.profileID)) {
      unawaited(setGenUiEnabled(true).catchError((Object _) {}));
    }
  }

  bool get _genUiEffective {
    final scope = _genUiScope;
    return scope != null && _genUiState.available(scope);
  }

  void _genUiObserve(
    String sessionID,
    List<MessageWithParts> messages, {
    required bool complete,
  }) {
    _genUiSync();
    final scope = _genUiScope;
    if (scope != null) {
      final id = _genUiSessionID(scope, sessionID);
      if (id != sessionID) {
        _genUiQueueTarget(GenUiTarget(scope, id));
        return;
      }
      _genUiState.observe(scope, id, messages, tailComplete: complete);
    }
  }

  void _genUiOnEvent(EventEnvelope event, {GenUiScope? scope}) {
    // Tokens do not change card identity or settlement. Durable message/tool
    // boundaries and idle/session events perform bounded reconciliation.
    if (event.type == 'message.part.delta' ||
        event.type == 'message.delta' ||
        event.type == 'tool.progress') {
      return;
    }
    _genUiSync();
    final currentScope = scope ?? _genUiScope;
    if (currentScope == null || !_genUiState.available(currentScope)) return;
    final props = event.properties;
    final part = props['part'];
    final info = props['info'];
    final sid =
        props['sessionID'] ??
        (part is Map ? part['sessionID'] : null) ??
        (info is Map
            ? (info['sessionID'] ??
                  (event.type.startsWith('session.') ? info['id'] : null))
            : null);
    if (sid is! String || sid.isEmpty) return;
    if (event.type == 'session.idle' ||
        (event.type == 'session.status' &&
            props['status'] is Map &&
            (props['status'] as Map)['type'] == 'idle')) {
      final owner = _genUiParent ?? _genUiPhoneController;
      owner._genUiFeedRefreshers[currentScope]?.queue();
    }
    if (part is Map && part['sessionID'] != null && part['sessionID'] != sid) {
      return;
    }
    final id = _genUiSessionID(currentScope, sid);
    if (event.type == 'session.deleted') {
      // The feed gateway drops its alias before broadcasting deletion.
      final captured = props['daemonSessionID'];
      final deletedID =
          currentScope.sourceId.startsWith('paseo:') &&
              captured is String &&
              captured.isNotEmpty
          ? captured
          : id;
      for (final removedID in {sid, deletedID}) {
        unawaited(
          _genUiState
              .removeSession(currentScope, removedID)
              .catchError((Object _) {}),
        );
      }
      return;
    }
    if (!event.type.startsWith('message.') &&
        !event.type.startsWith('session.')) {
      return;
    }
    _genUiState.stale(currentScope, id);
    _genUiQueueTarget(GenUiTarget(currentScope, id));
  }

  void _genUiQueueTarget(GenUiTarget target) {
    if (_disposed || !_genUiState.available(target.scope)) return;
    target = GenUiTarget(
      target.scope,
      _genUiSessionID(target.scope, target.sessionID),
    );
    // Bounded coalescing; a later feed/reconnect pass covers evicted hints.
    if (_genUiTargets.length >= 100) {
      _genUiTargets.remove(_genUiTargets.keys.first);
    }
    _genUiTargets[jsonEncode([genUiScopeKey(target.scope), target.sessionID])] =
        target;
    _genUiTimer ??= Timer(const Duration(milliseconds: 350), () {
      _genUiTimer = null;
      final targets = _genUiTargets.values.toList();
      _genUiTargets.clear();
      if (!_disposed) unawaited(_genUiState.recover(targets));
    });
  }

  GenUiScope? _genUiLocation(String folder, String? ws) {
    final owner = _connectedProfile ?? profile;
    final here = _genUiScope;
    if (owner == null || here == null || !genUiEnabled) return null;
    if (folder == here.directory && ws == here.workspace) return here;
    final scope = GenUiScope(
      profileID: here.profileID,
      sourceId: here.sourceId,
      directory: folder,
      workspace: ws,
    );
    var gateway = _genUiLocationGateways[scope];
    if (gateway == null) {
      if (_genUiLocationGateways.length >= 8) {
        final evicted = _genUiLocationGateways.keys.first;
        _genUiState.forgetSource(evicted);
        _genUiLocationGateways.remove(evicted)?.close();
      }
      if (owner.backend != ServerBackend.openCode) return null;
      gateway = owner.flavor == ServerFlavor.v2
          ? _v2GatewayFactory(owner).gateway
          : _apiFactory(owner);
      gateway.setLocation(directory: folder, workspace: ws);
      _genUiLocationGateways[scope] = gateway;
    }
    final current = gateway, generation = _generation, endpoint = owner.baseUrl;
    _genUiState.register(
      scope,
      current,
      endpoint: receipt_crypto.sha256.convert(utf8.encode(endpoint)).toString(),
      current: () =>
          !_disposed &&
          _generation == generation &&
          !current.isClosed &&
          (_connectedProfile ?? profile)?.baseUrl == endpoint &&
          genUiEnabled &&
          !_deletingReadProfiles.contains(scope.profileID),
      ready: genUiStatus.agents.contains(
        owner.flavor == ServerFlavor.v2
            ? GenUiAgent.openCode2
            : GenUiAgent.openCode1,
      ),
    );
    return scope;
  }

  void _genUiGlobalEvent(EventEnvelope event) {
    if (event.directory == null || !genUiEnabled || isAgentBackend) return;
    if (event.directory == directory &&
        event.workspace == workspace &&
        status == StreamStatus.connected) {
      return;
    }
    final scope = _genUiLocation(event.directory!, event.workspace);
    if (scope != null) _genUiOnEvent(event, scope: scope);
  }

  void _genUiRefreshFeed() {
    _genUiSync();
    final scope = _genUiScope;
    if (scope == null || !_genUiState.available(scope)) return;
    for (final session in sessionsById.values.take(20)) {
      _genUiQueueTarget(GenUiTarget(scope, session.id));
    }
    for (final row in _feedGlobal.take(20)) {
      final folder = row.projectDirectory ?? row.session.directory;
      if (folder == null) continue;
      final location = _genUiLocation(folder, null);
      if (location != null) {
        _genUiQueueTarget(GenUiTarget(location, row.session.id));
      }
    }
  }

  PaseoChatFeedSource _genUiPhoneFeed(
    PaseoGateway gateway,
    String folder,
    String name,
  ) {
    final owner = _paProfile?.id;
    final scope = owner == null
        ? null
        : GenUiScope(
            profileID: owner,
            sourceId: _paseoSourceId(folder),
            directory: folder,
          );
    ConnectionController controller() => _genUiPhoneController;
    late final PaseoChatFeedSource source;
    source = PaseoChatFeedSource(
      gateway,
      projectName: name,
      persistLastUsedProject: _ocRemember,
      initialLastUsedProjectDirectory: _ocLastUsed,
      hasWaitingCard: (id) =>
          scope != null &&
          controller()._genUiState
              .waiting(scope, controller()._genUiSessionID(scope, id))
              .isNotEmpty,
      cardsIncomplete: () => controller()._genUiState.recoveryIncomplete,
      refreshCards: (items) async {
        if (scope == null) return;
        controller()._genUiAttachPhone(scope, gateway);
        for (final item in items.take(20)) {
          controller()._genUiQueueTarget(GenUiTarget(scope, item.sessionID));
        }
      },
    );
    if (scope != null) {
      controller()._genUiAttachPhone(
        scope,
        gateway,
        sessions: () => source.chatFeed().items.map((row) => row.sessionID),
      );
      final owner = controller();
      owner._genUiFeedRefreshers.remove(scope)?.dispose();
      owner._genUiFeedRefreshers[scope] = _GenUiFeedRefresh(
        refresh: source.refreshAfterActivity,
        current: () =>
            !owner._disposed &&
            identical(owner._genUiPhoneSources[scope]?.gateway, gateway) &&
            owner._genUiState.available(scope),
      );
    }
    return source;
  }

  void _genUiAttachPhone(
    GenUiScope scope,
    PaseoGateway gateway, {
    Iterable<String> Function()? sessions,
  }) {
    final owner = _paProfile;
    if (owner == null) return;
    _genUiPhoneSources[scope] = (
      gateway: gateway,
      sessions: sessions ?? _genUiPhoneSources[scope]?.sessions,
    );
    final endpoint = owner.baseUrl;
    _genUiState.register(
      scope,
      gateway,
      endpoint: receipt_crypto.sha256.convert(utf8.encode(endpoint)).toString(),
      current: () =>
          !_disposed &&
          !gateway.isClosed &&
          gateway.transport.connected &&
          gateway.transport.serverVersion == '0.9.2' &&
          genUiStatus.agents.contains(GenUiAgent.claude) &&
          _paProfile?.id == scope.profileID &&
          _paProfile?.baseUrl == endpoint &&
          genUiEnabled &&
          !_deletingReadProfiles.contains(scope.profileID),
      ready: true,
    );
    // The gateway checks the raw tool name/provider and replies only once.
    // This closure is the profile-owned authority, rechecked for every call.
    gateway.genUiShowAllowed = () =>
        !_disposed &&
        identical(_genUiPhoneSources[scope]?.gateway, gateway) &&
        _genUiState.available(scope);
    final key = genUiScopeKey(scope);
    if (_genUiPhoneChannels.containsKey(key)) return;
    final channel = gateway.openEventChannel(
      onEvent: (event) => _genUiOnEvent(event, scope: scope),
      onStatus: (status) {
        if (status != StreamStatus.connected) {
          _genUiState.invalidateScope(scope);
        }
        if (status == StreamStatus.connected) {
          final ids =
              _genUiPhoneSources[scope]?.sessions?.call() ?? const <String>[];
          unawaited(
            _genUiState.recover([
              for (final id in ids.take(20))
                GenUiTarget(scope, _genUiSessionID(scope, id)),
            ]),
          );
        }
      },
      onError: (_) {},
    );
    _genUiPhoneChannels[key] = channel;
    channel.start();
  }

  void _genUiDropPhone(String folder) {
    final controller = _genUiPhoneController;
    if (!identical(controller, this)) {
      controller._genUiDropPhone(folder);
      return;
    }
    final id = _paProfile?.id;
    if (id == null) return;
    final scope = GenUiScope(
      profileID: id,
      sourceId: _paseoSourceId(folder),
      directory: folder,
    );
    _genUiFeedRefreshers.remove(scope)?.dispose();
    _genUiPhoneSources.remove(scope);
    unawaited(_genUiPhoneChannels.remove(genUiScopeKey(scope))?.dispose());
    _genUiState.forgetSource(scope);
  }

  Future<void> _genUiCloseProfile(String id) => Future.wait<void>([
    _genUiState.closeProfile(id),
    for (final side in _sides.values) side._genUiState.closeProfile(id),
  ]).then((_) {});

  void _genUiReopenProfile(String id) {
    _genUiState.reopenProfile(id);
    for (final side in _sides.values) {
      side._genUiState.reopenProfile(id);
    }
  }

  Future<void> _genUiDeleteProfile(String id) async {
    for (final side in _sides.values) {
      if (side._genUiOwnerID == id) {
        await side._genUiDeleteProfile(id);
        return;
      }
    }
    await _genUiState.closeProfile(id);
    await _genUiChanges[id]?.catchError((Object _) {});
    if (_genUiAttempted.contains(id) ||
        store.prefs.containsKey('oc.genui.enabled.$id')) {
      try {
        await (_genUiInstaller ??= ManagedGenUiInstaller.builtin()).setEnabled(
          profileId: id,
          agents: GenUiAgent.values.toSet(),
          enabled: false,
        );
      } catch (_) {
        // Registration removal is best effort. A damaged/unowned config must
        // never retain a profile's credentials or local data. Native profile
        // cleanup still removes the private Claude home after this attempt.
      }
    }
    _genUiSetup.remove(id);
    _genUiAttempted.remove(id);
    _genUiDesired.remove(id);
    _genUiSettingGeneration.remove(id);
  }

  void _genUiReset() {
    _genUiTimer?.cancel();
    _genUiTimer = null;
    _genUiTargets.clear();
    final scope = _genUiLastScope;
    if (scope != null) _genUiState.forgetSource(scope);
    _genUiLastScope = null;
    for (final entry in _genUiLocationGateways.entries) {
      _genUiState.forgetSource(entry.key);
      entry.value.close();
    }
    _genUiLocationGateways.clear();
  }

  void _genUiDispose() {
    _genUiReset();
    for (final channel in _genUiPhoneChannels.values) {
      unawaited(channel.dispose());
    }
    _genUiPhoneChannels.clear();
    for (final refresh in _genUiFeedRefreshers.values) {
      refresh.dispose();
    }
    _genUiFeedRefreshers.clear();
    _genUiPhoneSources.clear();
    _genUiLocal?.dispose();
    _genUiLocal = null;
  }
}

// Like the open-chat list refresh, coalesce changes over two seconds. A
// completion arriving during a read gets a trailing read, never lost by the
// feed's in-flight future coalescing. Only this source's bounded list is read.
class _GenUiFeedRefresh {
  _GenUiFeedRefresh({required this.refresh, required this.current});
  final Future<void> Function() refresh;
  final bool Function() current;
  Timer? _timer;
  bool _reading = false, _again = false, _disposed = false;

  void queue() {
    if (_disposed || !current()) return;
    if (_reading) {
      _again = true;
      return;
    }
    _timer ??= Timer(const Duration(seconds: 2), _run);
  }

  Future<void> _run() async {
    _timer = null;
    if (_disposed || !current()) return;
    _reading = true;
    try {
      await refresh();
    } catch (_) {
      // Feed refresh cannot turn an accepted answer into a failed delivery.
    } finally {
      _reading = false;
      if (_again) {
        _again = false;
        queue();
      }
    }
  }

  void dispose() {
    _disposed = true;
    _timer?.cancel();
    _timer = null;
    _again = false;
  }
}
