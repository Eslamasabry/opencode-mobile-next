part of '../connection.dart';

// Questions the agent asks and their answers.

/// [ConnectionController]'s agent questions.
mixin _ConnectionControllerQuestions on ChangeNotifier {
  ConnectionController get _self;

  final _feedQuestionSnapshots = Expando<_FeedQuestionRoute>();

  /// Pure read of the row's live question inventory. Does not open a chat.
  /// Capture an identity from this exact snapshot when presenting a decision.
  PendingQuestion? questionForFeedItem(ChatFeedItem item) =>
      _self._questionForFeedItem(item);

  PendingRequestIdentity questionIdentityForFeedItem(
    ChatFeedItem item,
    PendingQuestion question,
  ) => _self._questionIdentityForFeedItem(item, question);

  Future<void> answerQuestionForFeedItem(
    ChatFeedItem item,
    List<List<String>> answers, {
    required PendingRequestIdentity expectedRequest,
  }) => _self._replyToFeedQuestion(item, answers, expectedRequest);

  Future<void> rejectQuestionForFeedItem(
    ChatFeedItem item, {
    required PendingRequestIdentity expectedRequest,
  }) => _self._replyToFeedQuestion(item, null, expectedRequest);

  bool questionsLoading = false;
  String? questionsError;
  Map<String, PendingQuestion> questions = {};
  final Map<String, String> _v2QuestionSessions = {};
  final Set<String> _resolvedQuestionIDs = {};

  final _questionReads = _PendingReadGate();

  /// Reads the waiting questions; a read already running for this stream
  /// connect is shared rather than repeated.
  Future<void> refreshPendingQuestions() => _questionReads.run(
    _self._pendingReadEpoch,
    () => PerfTrace.span('questions.refresh', _self._refreshPendingQuestions),
  );

  Future<void> answerQuestion(
    String requestID,
    List<List<String>> answers, {
    PendingRequestIdentity? expectedRequest,
  }) => _self._answerQuestion(
    requestID,
    answers,
    expectedRequest: expectedRequest,
  );

  Future<void> rejectQuestion(
    String requestID, {
    PendingRequestIdentity? expectedRequest,
  }) => _self._rejectQuestion(requestID, expectedRequest: expectedRequest);
}

extension _ConnectionControllerQuestionsImpl on ConnectionController {
  Future<void> _refreshPendingQuestions() async {
    final current = repository;
    final currentApi = api;
    final generation = _generation;
    if (current == null) return;
    final refreshGeneration = ++_questionsRefreshGeneration;
    final revision = _questionRevision;
    questionsLoading = true;
    questionsError = null;
    _notifyListeners();
    try {
      final results = await _loadPendingQuestions(currentApi, current);
      if (!_isCurrentQuestionsRefresh(
        generation,
        currentApi,
        current,
        refreshGeneration,
      )) {
        return;
      }
      final hydrated = {
        for (final question in results.pending)
          if (!_resolvedQuestionIDs.contains(question.id))
            question.id: question,
      };
      if (!results.v2Succeeded) {
        for (final entry in questions.entries) {
          if (_v2QuestionSessions.containsKey(entry.key) &&
              !_resolvedQuestionIDs.contains(entry.key)) {
            hydrated.putIfAbsent(entry.key, () => entry.value);
          }
        }
      }
      final questionIDs = {...questions.keys, ...hydrated.keys};
      for (final id in questionIDs) {
        if ((_questionRevisions[id] ?? 0) > revision) continue;
        final question = hydrated[id];
        if (question == null) {
          questions.remove(id);
          _v2QuestionSessions.remove(id);
        } else {
          questions[id] = question;
          if (results.v2Succeeded) {
            if (results.v2IDs.contains(id)) {
              _v2QuestionSessions[id] = question.sessionID;
            } else {
              _v2QuestionSessions.remove(id);
            }
          }
        }
      }
      questionsLoading = false;
      _observeAttentionRead(AttentionKind.question);
      _syncInputAlerts();
      _notifyListeners();
    } catch (error) {
      if (!_isCurrentQuestionsRefresh(
        generation,
        currentApi,
        current,
        refreshGeneration,
      )) {
        return;
      }
      questionsLoading = false;
      questionsError = error.toString();
      _recordLocationError(questionsError!);
      _notifyListeners();
    }
  }

  Future<({List<PendingQuestion> pending, Set<String> v2IDs, bool v2Succeeded})>
  _loadPendingQuestions(
    ServerGateway? currentApi,
    ServerOperationsGateway currentRepository,
  ) async {
    List<PendingQuestion>? legacy;
    List<PendingQuestion>? v2;
    Object? legacyError;
    Object? v2Error;
    await Future.wait<void>([
      () async {
        try {
          legacy = await currentRepository.listQuestions();
        } catch (error) {
          legacyError = error;
        }
      }(),
      () async {
        if (currentApi == null || !_v2Probe('question')) return;
        try {
          final raw = await currentApi.pendingQuestionsV2();
          v2 = raw
              .map(PendingQuestion.fromJson)
              .where(
                (question) =>
                    question.id.isNotEmpty && question.sessionID.isNotEmpty,
              )
              .toList();
        } catch (error) {
          v2Error = error;
          _v2Failed('question', error);
        }
      }(),
    ]);
    if (legacy == null && v2 == null) {
      throw StateError(
        'Could not hydrate pending questions: '
        '${legacyError ?? v2Error ?? 'no endpoint available'}',
      );
    }
    final merged = <String, PendingQuestion>{
      for (final question in legacy ?? const <PendingQuestion>[])
        question.id: question,
      for (final question in v2 ?? const <PendingQuestion>[])
        question.id: question,
    };
    return (
      pending: merged.values.toList(),
      v2IDs: {
        for (final question in v2 ?? const <PendingQuestion>[]) question.id,
      },
      v2Succeeded: v2 != null,
    );
  }

  /// The body of [answerQuestion].
  Future<void> _answerQuestion(
    String requestID,
    List<List<String>> answers, {
    PendingRequestIdentity? expectedRequest,
  }) => _sendQuestionReply(
    api,
    repository,
    requestID,
    answers,
    expectedRequest: expectedRequest,
    prepareTransport: true,
  );

  /// The body of [rejectQuestion].
  Future<void> _rejectQuestion(
    String requestID, {
    PendingRequestIdentity? expectedRequest,
  }) => _sendQuestionReply(
    api,
    repository,
    requestID,
    null,
    expectedRequest: expectedRequest,
    prepareTransport: true,
  );

  /// Sends one question answer on already-resolved transport objects; the
  /// notification-action path passes the live background transport directly
  /// to avoid resume semantics (see [_sendPermissionReply]).
  Future<void> _sendQuestionAnswer(
    ServerGateway? currentApi,
    ServerOperationsGateway? current,
    String requestID,
    List<List<String>> answers,
  ) => _sendQuestionReply(currentApi, current, requestID, answers);

  Future<void> _sendQuestionReply(
    ServerGateway? currentApi,
    ServerOperationsGateway? current,
    String requestID,
    List<List<String>>? answers, {
    PendingRequestIdentity? expectedRequest,
    bool prepareTransport = false,
  }) async {
    if (expectedRequest != null &&
        (expectedRequest._permission || expectedRequest._id != requestID)) {
      throw ArgumentError('Question request identity does not match');
    }
    if (expectedRequest?._feed case final route?) {
      return _replyToFeedQuestion(route.item, answers, expectedRequest!);
    }
    if (expectedRequest != null && !isRequestPending(expectedRequest)) return;
    final question = questions[requestID];
    if (question == null) {
      if (_resolvedQuestionIDs.contains(requestID)) return;
      throw StateError('Question request $requestID is no longer pending');
    }
    final request = expectedRequest ?? questionIdentity(question);
    final capturedAnswers = answers
        ?.map((values) => List<String>.of(values))
        .toList();
    return _withPendingReply(request, () async {
      if (prepareTransport) {
        await prepareActionTransport();
        currentApi = api;
        current = repository;
      }
      if (!isRequestPending(request)) return;
      await _writeQuestionReply(currentApi, current, request, capturedAnswers);
    });
  }

  Future<void> _writeQuestionReply(
    ServerGateway? currentApi,
    ServerOperationsGateway? current,
    PendingRequestIdentity request,
    List<List<String>>? answers,
  ) async {
    final requestID = request._id;
    final generation = _generation;
    if (current == null) throw StateError('Not connected to OpenCode');
    final v2SessionID = _v2QuestionSessions[requestID];
    try {
      if (v2SessionID != null) {
        if (currentApi == null) throw StateError('Not connected to OpenCode');
        if (answers == null) {
          await currentApi.rejectQuestionV2(v2SessionID, requestID);
        } else {
          await currentApi.answerQuestionV2(v2SessionID, requestID, answers);
        }
      } else if (answers == null) {
        await current.rejectQuestion(requestID);
      } else {
        await current.answerQuestion(requestID, answers);
      }
    } catch (error) {
      if (!_isCurrent(generation, currentApi) ||
          repository != current ||
          !isRequestPending(request)) {
        return;
      }
      if (_isQuestionNotFound(error, requestID)) {
        _resolveQuestion(requestID);
        return;
      }
      rethrow;
    }
    if (!_isCurrent(generation, currentApi) ||
        repository != current ||
        !isRequestPending(request)) {
      return;
    }
    _resolveQuestion(requestID);
  }

  void _resolveQuestion(String requestID) {
    _markQuestionChanged(requestID);
    questionsLoading = false;
    _v2QuestionSessions.remove(requestID);
    _resolvedQuestionIDs.add(requestID);
    questions.remove(requestID);
    _syncInputAlerts();
    _notifyListeners();
  }

  bool _isQuestionNotFound(Object error, String requestID) {
    if (error is ApiException) return error.isQuestionNotFound(requestID);
    if (error is ProductException && error.cause != null) {
      return _isQuestionNotFound(error.cause!, requestID);
    }
    if (error is sdk.OpenCodeApiException && error.statusCode == 404) {
      final decoded = error.payloadAs<sdk.QuestionNotFoundError>();
      if (decoded != null) return decoded.requestID == requestID;
      final raw = error.rawPayload;
      return raw is Map &&
          raw['_tag']?.toString() == 'QuestionNotFoundError' &&
          raw['requestID']?.toString() == requestID;
    }
    return false;
  }
}
