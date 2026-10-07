part of '../connection.dart';

/// A snapshot is bound to a live inventory, never just a reusable request ID.
class _FeedQuestionRoute {
  _FeedQuestionRoute({
    required this.item,
    required this.profileID,
    required this.question,
    this.gateway,
    this.source,
    this.revision,
    this.controller,
    this.delegate,
  }) : contents = _questionContents(question);

  final ChatFeedItem item;
  final String profileID;
  final PendingQuestion question;
  final String contents;
  final PaseoGateway? gateway;
  final PaseoChatFeedSource? source;
  final Object? revision;
  final ConnectionController? controller;
  final PendingRequestIdentity? delegate;
}

extension _FeedQuestions on ConnectionController {
  PendingQuestion? _questionForFeedItem(ChatFeedItem item) {
    if (_disposed) return null;
    final sourceID = item.sourceId ?? _openCodeSourceId;
    if (sourceID.startsWith('paseo:')) {
      final pair = _paSources[item.directory];
      final profileID = _paProfile?.id;
      if (sourceID != _paseoSourceId(item.directory) ||
          pair == null ||
          profileID == null ||
          _deletingReadProfiles.contains(profileID) ||
          _paHostProfile != profileID ||
          !phoneAgentsAvailable ||
          !_feedContainsQuestionRow(
            pair.source.chatFeed(const ChatFeedFilter(includeSubagents: true)),
            item,
          )) {
        return null;
      }
      final snapshot = pair.gateway.nativeQuestionSnapshot(item.sessionID);
      if (snapshot == null) return null;
      final route = _FeedQuestionRoute(
        item: item,
        profileID: profileID,
        question: snapshot.question,
        gateway: pair.gateway,
        source: pair.source,
        revision: snapshot.revision,
      );
      _feedQuestionSnapshots[route.question] = route;
      return route.question;
    }
    final owner = sourceID == _openCodeSourceId
        ? this
        : _sideForSource(sourceID);
    if (owner == null ||
        owner._disposed ||
        owner.directory != item.directory ||
        !_feedContainsQuestionRow(
          owner._ocChatFeed(const ChatFeedFilter(includeSubagents: true)),
          item,
        )) {
      return null;
    }
    final question = owner.questionForSession(item.sessionID);
    final profileID = (owner._connectedProfile ?? owner.profile)?.id;
    if (question == null || profileID == null) return null;
    // Give the list its own snapshot; never annotate the chat's model object.
    final copy = PendingQuestion(
      id: question.id,
      sessionID: question.sessionID,
      prompts: List.unmodifiable(question.prompts),
    );
    _feedQuestionSnapshots[copy] = _FeedQuestionRoute(
      item: item,
      profileID: profileID,
      question: copy,
      controller: owner,
      delegate: owner.questionIdentity(question),
    );
    return copy;
  }

  bool _feedContainsQuestionRow(ChatFeedSnapshot snapshot, ChatFeedItem item) =>
      snapshot.items.any(
        (row) =>
            row.sessionID == item.sessionID &&
            row.directory == item.directory &&
            row.agentId == item.agentId,
      );

  PendingRequestIdentity _questionIdentityForFeedItem(
    ChatFeedItem item,
    PendingQuestion question,
  ) {
    final route = _feedQuestionSnapshots[question];
    if (route == null ||
        route.item.identity != item.identity ||
        route.item.agentId != item.agentId) {
      throw ArgumentError('Question does not belong to this feed row');
    }
    return _questionIdentity(question);
  }

  bool _isFeedQuestionPending(_FeedQuestionRoute route) {
    if (_questionContents(route.question) != route.contents) return false;
    final gateway = route.gateway;
    if (gateway != null) {
      final pair = _paSources[route.item.directory];
      return !_deletingReadProfiles.contains(route.profileID) &&
          phoneAgentsAvailable &&
          _paProfile?.id == route.profileID &&
          _paHostProfile == route.profileID &&
          identical(pair?.gateway, gateway) &&
          identical(pair?.source, route.source) &&
          _feedContainsQuestionRow(
            route.source!.chatFeed(
              const ChatFeedFilter(includeSubagents: true),
            ),
            route.item,
          ) &&
          gateway.isNativeQuestionCurrent(
            route.item.sessionID,
            route.question.id,
            route.revision!,
          );
    }
    final owner = route.controller!;
    final sourceID = route.item.sourceId ?? _openCodeSourceId;
    return identical(
          sourceID == _openCodeSourceId ? this : _sideForSource(sourceID),
          owner,
        ) &&
        (owner._connectedProfile ?? owner.profile)?.id == route.profileID &&
        owner.directory == route.item.directory &&
        _feedContainsQuestionRow(
          owner._ocChatFeed(const ChatFeedFilter(includeSubagents: true)),
          route.item,
        ) &&
        owner.isRequestPending(route.delegate!);
  }

  Future<void> _replyToFeedQuestion(
    ChatFeedItem item,
    List<List<String>>? answers,
    PendingRequestIdentity expectedRequest,
  ) {
    final route = expectedRequest._feed;
    if (route == null ||
        route.item.identity != item.identity ||
        route.item.agentId != item.agentId ||
        expectedRequest._owner != this) {
      throw ArgumentError('Question request identity does not match feed row');
    }
    final captured = answers?.map((a) => List<String>.of(a)).toList();
    return _withPendingReply(expectedRequest, () async {
      if (!isRequestPending(expectedRequest)) return;
      final gateway = route.gateway;
      if (gateway != null) {
        if (captured == null) {
          await gateway.rejectQuestionV2(item.sessionID, route.question.id);
        } else {
          await gateway.answerQuestionV2(
            item.sessionID,
            route.question.id,
            captured,
          );
        }
        expectedRequest._retired = true;
        if (!_disposed) _notifyListeners();
        // This is an inventory read only. No chat backend, resume, location
        // change or timeline subscription is created to deliver the reply.
        unawaited(route.source!.refreshAfterActivity());
      } else {
        final owner = route.controller!;
        await owner._sendQuestionReply(
          owner.api,
          owner.repository,
          route.question.id,
          captured,
          expectedRequest: route.delegate,
        );
      }
    });
  }
}
