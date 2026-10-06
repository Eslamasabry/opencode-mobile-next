part of '../connection.dart';

// The other connections in the one Conversations list (see
// docs/design/all-connections-one-list-2026-10-06.md): every saved OpenCode
// server on this phone (in-app Ubuntu, Termux) beside the main connection,
// each on its own ConnectionController, so its conversations show and open
// without switching servers. Servers elsewhere join only when turned on.

/// Source id of another saved server's feed in the merged feed.
String _sideSourceId(String profileId) => 'profile:$profileId';

/// [ConnectionController]'s [ChatListSources].
mixin _ConnectionControllerSideConnections on ChangeNotifier
    implements ChatListSources {
  ConnectionController get _self;

  /// Each other shown server's own connection, by profile id.
  final _sides = <String, ConnectionController>{};

  /// What each side was connected with: an edited profile reconnects.
  final _sideSigs = <String, String>{};

  /// In-app Ubuntu sides whose server was started once already.
  final _sideStarts = <String>{};
  Timer? _sideNotify;

  /// Runs on this phone: the in-app Ubuntu or a server on the phone's own
  /// address (Termux).
  static bool _onThisPhone(ServerProfile profile) =>
      BuiltinLinux.managesServerUrl(profile.baseUrl) ||
      _isLoopbackUrl(profile.baseUrl);

  static String _shownKey(String id) => 'oc.chatListShown.$id';

  String? get _mainProfileId => (_self._connectedProfile ?? _self.profile)?.id;

  bool _sideEligible(ServerProfile profile) =>
      profile.backend == ServerBackend.openCode &&
      _self.isProfileReadable(profile.id);

  bool _shownInList(ServerProfile profile) =>
      _self.store.prefs.getBool(_shownKey(profile.id)) ?? _onThisPhone(profile);

  /// The agents' id in [chatListSources]: scoped to the phone profile they
  /// run under, so its deletion sweep finds the choice.
  String? get _agentsSourceId {
    final owner = _self._paProfile;
    return owner == null ? null : '${owner.id}$agentBackendProfileSuffix';
  }

  /// Whether the agents' conversations show in the list.
  bool get _agentsShown {
    final id = _agentsSourceId;
    return id == null || (_self.store.prefs.getBool(_shownKey(id)) ?? true);
  }

  static String _sideSignature(ServerProfile p) =>
      '${p.baseUrl}\u0000${p.flavor.name}\u0000${p.username}'
      '\u0000${p.password}\u0000${p.name}';

  @override
  List<ChatListSource> get chatListSources {
    final mainId = _mainProfileId;
    return [
      for (final profile in _self.store.profiles)
        if (profile.id == mainId || _sideEligible(profile))
          ChatListSource(
            id: profile.id,
            name: profile.name,
            kind: ChatListSourceKind.openCode,
            shown: profile.id == mainId || _shownInList(profile),
            onThisPhone: _onThisPhone(profile),
            main: profile.id == mainId,
          ),
      if (_self.phoneAgentsAvailable && _agentsSourceId != null)
        ChatListSource(
          id: _agentsSourceId!,
          name: _self._paCatalog.byId('claude')?.name ?? 'Claude Code',
          kind: ChatListSourceKind.agents,
          shown: _agentsShown,
        ),
    ];
  }

  @override
  Future<void> setChatListSourceShown(String id, bool shown) async {
    if (id == _mainProfileId) return;
    await _self.store.prefs.setBool(_shownKey(id), shown);
    if (_self._disposed) return;
    _syncSideConnections();
    _self._notifyListeners();
  }

  /// Opens a connection to every other shown server and closes the ones no
  /// longer wanted (hidden, deleted, now the main connection).
  void _syncSideConnections() {
    if (!_self._ownsProfileServices || _self._disposed) return;
    final mainId = _mainProfileId;
    final wanted = <String, ServerProfile>{
      if (mainId != null)
        for (final profile in _self.store.profiles)
          if (profile.id != mainId &&
              _sideEligible(profile) &&
              _shownInList(profile))
            profile.id: profile,
    };
    var changed = false;
    for (final id in _sides.keys.toList()) {
      final profile = wanted[id];
      if (profile != null && _sideSigs[id] == _sideSignature(profile)) {
        continue;
      }
      _dropSide(id);
      changed = true;
    }
    for (final profile in wanted.values) {
      if (_sides.containsKey(profile.id)) continue;
      final side = ConnectionController.sideBackend(_self)
        ..addListener(_sideChanged);
      _sides[profile.id] = side;
      _sideSigs[profile.id] = _sideSignature(profile);
      unawaited(_connectSide(side, profile));
      changed = true;
    }
    if (changed) _self._paRebuildMerged();
  }

  Future<void> _connectSide(
    ConnectionController side,
    ServerProfile profile,
  ) async {
    try {
      await side.connect(profile);
    } catch (_) {
      // The list says this server's conversations couldn't load.
    }
    if (side._disposed || side.isConnected) return;
    // The in-app Ubuntu's server runs only once something starts it: the
    // first failed connect starts it, then connects again.
    if (!BuiltinLinux.supported ||
        !BuiltinLinux.managesServerUrl(profile.baseUrl) ||
        !_sideStarts.add(profile.id)) {
      return;
    }
    final failure = await startBuiltinServer(
      linux: BuiltinLinux(),
      profile: profile,
      stillWanted: () => !side._disposed,
    );
    if (failure == null && !side._disposed) await side.retryConnection();
  }

  /// A side's conversations changed: the list follows, a few times a second
  /// at most (a streaming reply notifies on every word).
  void _sideChanged() {
    if (_sideNotify?.isActive ?? false) return;
    _sideNotify = Timer(const Duration(milliseconds: 250), () {
      if (!_self._disposed) _self._notifyListeners();
    });
  }

  void _dropSide(String id) {
    final side = _sides.remove(id);
    _sideSigs.remove(id);
    _sidesReached.remove(id);
    if (side == null) return;
    side.removeListener(_sideChanged);
    _self._paOwners.removeWhere((_, owner) => identical(owner, side));
    if (!side._disposed) side.dispose();
  }

  /// The merged feed's sources for the other shown servers.
  List<NamedChatFeedSource> get _sideFeedSources => [
    for (final entry in _sides.entries)
      NamedChatFeedSource(
        id: _sideSourceId(entry.key),
        label: entry.value.profile?.name ?? 'OpenCode',
        source: _OpenCodeFeed(entry.value),
      ),
  ];

  /// The side a merged row's source names, if it is one.
  ConnectionController? _sideForSource(String? sourceId) {
    if (sourceId == null || !sourceId.startsWith('profile:')) return null;
    final side = _sides[sourceId.substring('profile:'.length)];
    return side == null || side._disposed ? null : side;
  }

  /// The shown servers still connecting (profile ids), for the list's
  /// "Loading … conversations" line.
  /// Only a first connect counts: a later reconnect keeps the rows it had.
  Iterable<String> get _sidesLoading sync* {
    for (final entry in _sides.entries) {
      final side = entry.value;
      if (side._disposed) continue;
      if (side.isConnected) _sidesReached.add(entry.key);
      if (_sidesReached.contains(entry.key)
          ? side._ocChatFeed().loading
          : side.status == StreamStatus.connecting) {
        yield entry.key;
      }
    }
  }

  /// Sides connected at least once since they joined.
  final _sidesReached = <String>{};

  /// A shown server that could not be reached (its rows are missing).
  bool get _sidesFailed => _sides.values.any(
    (side) =>
        !side._disposed &&
        !side.isConnected &&
        side.status != StreamStatus.connecting &&
        side.lastError != null,
  );

  void _sideShutdown() {
    _sideNotify?.cancel();
    _sideNotify = null;
    for (final id in _sides.keys.toList()) {
      _dropSide(id);
    }
  }
}
