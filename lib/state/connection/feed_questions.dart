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
    this.directoryQuestion,
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
  final _DirectoryQuestion? directoryQuestion;
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
        !_feedContainsQuestionRow(
          owner._ocChatFeed(const ChatFeedFilter(includeSubagents: true)),
          item,
        )) {
      return null;
    }
    final scoped = owner.directory == item.directory
        ? null
        : owner._feedDirectoryQuestions[item.directory]?.values
              .where(
                (entry) =>
                    entry.question.sessionID == item.sessionID &&
                    owner._directoryQuestionCurrent(entry),
              )
              .firstOrNull;
    final question = owner.directory == item.directory
        ? owner.questionForSession(item.sessionID)
        : scoped?.question;
    final profileID = (owner._connectedProfile ?? owner.profile)?.id;
    if (question == null ||
        profileID == null ||
        !isProfileReadable(profileID)) {
      return null;
    }
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
      delegate: scoped == null ? owner.questionIdentity(question) : null,
      directoryQuestion: scoped,
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
    if (!isProfileReadable(route.profileID) ||
        _questionContents(route.question) != route.contents) {
      return false;
    }
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
        (route.directoryQuestion != null
            ? owner._directoryQuestionCurrent(route.directoryQuestion!) &&
                  identical(
                    owner._feedDirectoryQuestions[route.item.directory]?[route
                        .question
                        .id],
                    route.directoryQuestion,
                  )
            : owner.directory == route.item.directory) &&
        _feedContainsQuestionRow(
          owner._ocChatFeed(const ChatFeedFilter(includeSubagents: true)),
          route.item,
        ) &&
        (route.directoryQuestion != null ||
            owner.isRequestPending(route.delegate!));
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
        if (route.directoryQuestion case final entry?) {
          if (entry.attempted) {
            throw const ProductException(
              'Delivery is unconfirmed. Refresh the conversation before answering again.',
            );
          }
          final pair = owner._buildTransportPair(entry.profile);
          try {
            pair.gateway.setLocation(directory: item.directory);
            pair.operations.setLocation(directory: item.directory);
            if (!isRequestPending(expectedRequest)) return;
            entry.attempted = true;
            if (entry.modern) {
              if (captured == null) {
                await pair.gateway.rejectQuestionV2(
                  item.sessionID,
                  route.question.id,
                );
              } else {
                await pair.gateway.answerQuestionV2(
                  item.sessionID,
                  route.question.id,
                  captured,
                );
              }
            } else if (captured == null) {
              await pair.operations.rejectQuestion(route.question.id);
            } else {
              await pair.operations.answerQuestion(route.question.id, captured);
            }
            expectedRequest._retired = true;
            owner._feedQuestionEpoch++;
            if (identical(
              owner._feedDirectoryQuestions[item.directory]?[route.question.id],
              entry,
            )) {
              owner._feedDirectoryQuestions[item.directory]?.remove(
                route.question.id,
              );
            }
            if (!owner._disposed) owner._notifyListeners();
            if (!_disposed) _notifyListeners();
            owner._feedScheduleRefresh();
          } finally {
            pair.gateway.close();
          }
          return;
        }
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
