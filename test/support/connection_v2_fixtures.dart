import 'dart:async';

import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class V2Api extends OpenCodeApi {
  V2Api({
    this.healthy = true,
    this.permissions = const [],
    this.questions = const [],
    this.providersResult,
    this.configuredProvidersResult,
    this.agentsResult = const [],
  }) : super(baseUrl: 'http://127.0.0.1:1');

  final bool healthy;
  List<PermissionRequest> permissions;
  List<Map<String, dynamic>> questions;
  List<PermissionRequest>? legacyPermissions;
  Object? permissionV2Error;
  Object? questionV2Error;
  Object? answerQuestionError;
  Object? rejectQuestionError;
  Completer<void>? questionWrite;
  List<Session> sessionsResult = const [];
  final ProvidersResponse? providersResult;
  final ProvidersResponse? configuredProvidersResult;
  final List<AgentInfo> agentsResult;
  bool catalogUnavailable = false;
  Completer<List<Session>>? sessionsCompleter;
  Completer<Map<String, String>>? statusesCompleter;
  Completer<Health>? healthCompleter;
  int healthCalls = 0;
  bool closed = false;
  final List<(String, String, String)> permissionReplies = [];
  final List<(String, String, List<List<String>>)> questionReplies = [];
  final List<(String, String)> questionRejects = [];

  @override
  Future<Health> health() {
    healthCalls += 1;
    return healthCompleter?.future ??
        Future.value(Health(healthy: healthy, version: 'v2'));
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() =>
      legacyPermissions == null
      ? Future.error(ApiException('legacy unavailable', statusCode: 404))
      : Future.value(legacyPermissions);

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async {
    if (permissionV2Error case final error?) throw error;
    return permissions;
  }

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async {
    if (questionV2Error case final error?) throw error;
    return questions;
  }

  @override
  Future<List<Session>> sessions() =>
      sessionsCompleter?.future ?? Future.value(sessionsResult);

  @override
  Future<Map<String, String>> sessionStatuses() =>
      statusesCompleter?.future ?? Future.value(const {});

  @override
  Future<ProvidersResponse> providers() async {
    if (catalogUnavailable) throw ApiException('Catalog unavailable');
    return providersResult ?? ProvidersResponse(providers: const []);
  }

  @override
  Future<ProvidersResponse> configuredProviders() async =>
      configuredProvidersResult ??
      (throw ApiException('configured providers unavailable'));

  @override
  Future<List<AgentInfo>> agents() async => agentsResult;

  @override
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply, {
    String? message,
  }) async {
    permissionReplies.add((sessionID, requestID, reply));
  }

  @override
  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) async {
    await questionWrite?.future;
    if (answerQuestionError case final error?) throw error;
    questionReplies.add((sessionID, requestID, answers));
  }

  @override
  Future<void> rejectQuestionV2(String sessionID, String requestID) async {
    if (rejectQuestionError case final error?) throw error;
    questionRejects.add((sessionID, requestID));
  }

  @override
  void close() {
    closed = true;
    super.close();
  }
}

class QuestionRepository implements ProductRepository {
  QuestionRepository({
    this.legacyUnavailable = true,
    this.catalog,
    this.integrations = const [],
    this.defaults = const ChatDefaults(),
  });

  final bool legacyUnavailable;
  final CatalogSnapshot? catalog;
  final List<IntegrationInfo> integrations;
  final ChatDefaults defaults;
  List<PendingQuestion> questions = const [];
  Completer<List<PendingQuestion>>? questionsCompleter;
  Object? answerError;
  Object? rejectError;
  int listCalls = 0;
  int runtimeRefreshCalls = 0;
  final List<String> answers = [];
  final List<String> rejects = [];

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<PendingQuestion>> listQuestions() {
    listCalls += 1;
    if (questionsCompleter != null) return questionsCompleter!.future;
    return legacyUnavailable
        ? Future.error(StateError('legacy unavailable'))
        : Future.value(questions);
  }

  @override
  Future<void> answerQuestion(String id, List<List<String>> values) async {
    if (answerError case final error?) throw error;
    answers.add(id);
  }

  @override
  Future<void> rejectQuestion(String id) async {
    if (rejectError case final error?) throw error;
    rejects.add(id);
  }

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      catalog ?? (throw StateError('detailed catalog unavailable'));

  @override
  Future<ChatDefaults> loadChatDefaults() async => defaults;

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => integrations;

  @override
  Future<void> refreshProviderRuntime() async {
    runtimeRefreshCalls += 1;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class FakeEventStream extends EventStream {
  FakeEventStream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });

  bool disposed = false;

  @override
  void start() => onStatus(StreamStatus.connected);

  @override
  Future<void> dispose() async {
    disposed = true;
  }

  void emitStatus(StreamStatus status) => onStatus(status);
}

Future<ProfileStore> memoryProfileStore([
  Map<String, Object> values = const {},
]) async {
  SharedPreferences.setMockInitialValues(values);
  return ProfileStore(prefs: await SharedPreferences.getInstance());
}
