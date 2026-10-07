part of '../connection.dart';

Object _feedPermissionProfileIdentity(ServerProfile profile) => (
  profile.baseUrl,
  profile.username,
  profile.password,
  profile.flavor,
  profile.backend,
);

/// A UI identity is distinct from the wire request ID: native request IDs may
/// collide across feeds, and Undo/receipt ledgers are shared by the list.
class _FeedPermissionRoute {
  _FeedPermissionRoute({
    required this.item,
    required this.profileID,
    required this.profileIdentity,
    required this.permission,
    required this.wireID,
    required this.gateway,
    required this.source,
    required this.revision,
  }) : contents = _permissionContents(permission);

  final ChatFeedItem item;
  final String profileID;
  final Object profileIdentity;
  final PermissionRequest permission;
  final String contents, wireID;
  final PaseoGateway gateway;
  final PaseoChatFeedSource source;
  final Object revision;
}

extension _FeedPermissions on ConnectionController {
  PermissionRequest? _permissionForFeedItem(ChatFeedItem item) {
    if (_disposed || item.sourceId != _paseoSourceId(item.directory)) {
      return null;
    }
    final profile = _paProfile;
    final pair = _paSources[item.directory];
    if (profile == null ||
        pair == null ||
        !isProfileReadable(profile.id) ||
        _paHostProfile != profile.id ||
        !phoneAgentsAvailable ||
        !_feedContainsQuestionRow(
          pair.source.chatFeed(const ChatFeedFilter(includeSubagents: true)),
          item,
        )) {
      return null;
    }
    final snapshot = pair.gateway.nativePermissionSnapshot(item.sessionID);
    if (snapshot == null) return null;
    final wire = snapshot.permission;
    final key = _feedPermissionKeys[snapshot.revision] ??= jsonEncode([
      'feed-permission',
      profile.id,
      item.identity,
      wire.id,
      ++_feedPermissionSequence,
    ]);
    final permission = PermissionRequest(
      id: key,
      sessionID: wire.sessionID,
      permission: wire.permission,
      patterns: List.unmodifiable(wire.patterns),
      metadata: wire.metadata,
      always: List.unmodifiable(wire.always),
      tool: wire.tool,
      message: wire.message,
    );
    _feedPermissionSnapshots[permission] = _FeedPermissionRoute(
      item: item,
      profileID: profile.id,
      profileIdentity: _feedPermissionProfileIdentity(profile),
      permission: permission,
      wireID: wire.id,
      gateway: pair.gateway,
      source: pair.source,
      revision: snapshot.revision,
    );
    return permission;
  }

  PendingRequestIdentity _permissionIdentityForFeedItem(
    ChatFeedItem item,
    PermissionRequest permission,
  ) {
    final route = _feedPermissionSnapshots[permission];
    if (route == null ||
        route.item.identity != item.identity ||
        route.item.agentId != item.agentId) {
      throw ArgumentError('Permission does not belong to this feed row');
    }
    return permissionIdentity(permission);
  }

  bool _isFeedPermissionPending(_FeedPermissionRoute route) {
    final pair = _paSources[route.item.directory];
    final profile = _paProfile;
    return !_disposed &&
        isProfileReadable(route.profileID) &&
        phoneAgentsAvailable &&
        profile?.id == route.profileID &&
        _paHostProfile == route.profileID &&
        _feedPermissionProfileIdentity(profile!) == route.profileIdentity &&
        store.profiles.any(
          (p) =>
              p.id == route.profileID &&
              _feedPermissionProfileIdentity(p) == route.profileIdentity,
        ) &&
        identical(pair?.gateway, route.gateway) &&
        identical(pair?.source, route.source) &&
        _permissionContents(route.permission) == route.contents &&
        _feedContainsQuestionRow(
          route.source.chatFeed(const ChatFeedFilter(includeSubagents: true)),
          route.item,
        ) &&
        route.gateway.isNativePermissionCurrent(
          route.item.sessionID,
          route.wireID,
          route.revision,
        );
  }

  Future<void> _replyToFeedPermission(
    ChatFeedItem item,
    String response,
    PendingRequestIdentity expectedRequest, {
    String? message,
  }) {
    final route = expectedRequest._feedPermission;
    if (route == null ||
        route.item.identity != item.identity ||
        route.item.agentId != item.agentId ||
        expectedRequest._owner != this) {
      throw ArgumentError(
        'Permission request identity does not match feed row',
      );
    }
    return _withPendingReply(expectedRequest, () async {
      if (!isRequestPending(expectedRequest)) return;
      await route.gateway.respondPermission(
        route.wireID,
        response,
        legacySessionID: route.item.sessionID,
        message: message,
      );
      expectedRequest._retired = true;
      if (!_disposed) _notifyListeners();
      // Refresh inventory only; never prepare/resume a chat or read its timeline.
      unawaited(route.source.refreshAfterActivity());
    });
  }
}
