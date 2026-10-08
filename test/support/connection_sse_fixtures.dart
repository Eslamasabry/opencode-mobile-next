import 'dart:async';

import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ControlledApi extends OpenCodeApi {
  ControlledApi(this.label) : super(baseUrl: 'http://127.0.0.1:1');

  final String label;
  final healthResult = Completer<Health>();
  Completer<List<Session>>? sessionsResult;
  Object? sessionsFailure;
  int sessionsCalls = 0;
  int healthCalls = 0;
  Object? healthFailure;
  bool closed = false;

  // The folder check must answer without a real network call.
  @override
  Future<List<FileNode>> listFiles(String path) =>
      Future.error(ApiException('Connection failed'));

  @override
  Future<Health> health() {
    healthCalls += 1;
    final failure = healthFailure;
    if (failure != null) return Future.error(failure);
    return healthResult.future;
  }

  @override
  Future<List<Session>> sessions() {
    sessionsCalls += 1;
    final failure = sessionsFailure;
    if (failure != null) return Future.error(failure);
    return sessionsResult?.future ?? Future.value(const []);
  }

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  Completer<ProvidersResponse>? providersResult;
  int providersCalls = 0;

  @override
  Future<ProvidersResponse> providers() {
    providersCalls += 1;
    return providersResult?.future ??
        Future.value(ProvidersResponse(providers: const []));
  }

  // The v1 catalog load also reads the runtime view; answer it locally so
  // the test never reaches the network.
  @override
  Future<ProvidersResponse> configuredProviders() async =>
      ProvidersResponse(providers: const []);

  @override
  Future<List<AgentInfo>> agents() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));

  @override
  void close() {
    closed = true;
    super.close();
  }
}

class FakeEventStream extends EventStream {
  FakeEventStream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });

  bool started = false;
  bool disposed = false;

  @override
  void start() {
    started = true;
    onStatus(StreamStatus.connecting);
  }

  @override
  Future<void> dispose() async {
    disposed = true;
  }

  void emitStatus(StreamStatus value) => onStatus(value);
  void emit(EventEnvelope value) => onEvent(value);
}

class TestRepository extends SdkProductRepository {
  TestRepository(OpenCodeApi api) : super(api.sdkClient);

  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();

  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];
}

Future<ProfileStore> memoryProfileStore() async {
  SharedPreferences.setMockInitialValues({});
  return ProfileStore(prefs: await SharedPreferences.getInstance());
}

ServerProfile testProfile(String id) =>
    ServerProfile(id: id, name: id, baseUrl: 'http://127.0.0.1:1');

EventStreamFactory streamFactory(List<FakeEventStream> streams) {
  return ({required api, required onEvent, required onStatus, onError}) {
    final stream = FakeEventStream(
      api: api,
      onEvent: onEvent,
      onStatus: onStatus,
      onError: onError,
    );
    streams.add(stream);
    return stream;
  };
}

ProductRepositoryFactory get testRepositoryFactory =>
    (api) => TestRepository(api);
