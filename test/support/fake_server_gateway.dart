import 'package:opencode_mobile/domain/genui/gen_ui_history.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

import 'fake_event_channel.dart';
import 'fake_server_capabilities.dart';

export 'package:opencode_mobile/domain/models.dart' show ApiException;

/// In-memory protocol-neutral fixture. It never constructs an HTTP client.
/// Tests override the reads and actions needed by the behavior under test.
class FakeServerGateway
    implements
        ServerGateway,
        SessionRetryGateway,
        CorrelatedPromptGateway,
        GenUiHistoryGateway {
  FakeServerGateway({required this.baseUrl, this.username, this.password});
  final String baseUrl;
  final String? username;
  final String? password;
  @override
  String? directory;
  @override
  String? workspace;
  @override
  bool isClosed = false;
  @override
  ServerCapabilities get capabilities => testServerCapabilities;
  @override
  void setLocation({String? directory, String? workspace}) {
    this.directory = directory;
    this.workspace = workspace;
  }

  @override
  void close() => isClosed = true;
  @override
  Future<Health> health() async => Health(healthy: true, version: '2.1.1');
  @override
  Future<List<Session>> sessions() async => const [];
  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await sessions() : const []);
  @override
  Future<Session> session(String id) async => Session(id: id);
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<List<MessageWithParts>> messages(String id) async => const [];
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
  @override
  Future<List<Todo>> todos(String id) async => const [];
  @override
  Future<List<FileDiff>> diff(String id) async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      pendingPermissions();
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => const [];
  @override
  Future<List<Api2FormInfo>> pendingForms() async => const [];
  @override
  Future<List<Api2FormInfo>> sessionForms(String sessionID) async => const [];
  @override
  Future<List<Api2InboxItem>> inboxItems(String sessionID) async => const [];
  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: const []);
  @override
  Future<ProvidersResponse> configuredProviders() => providers();
  @override
  Future<List<AgentInfo>> agents() async => const [];
  @override
  Future<List<FileNode>> listFiles(String path) async => const [];
  @override
  Future<List<String>> findFile(String query) async => const [];
  @override
  Future<List<FindMatch>> findText(String pattern) async => const [];
  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope) onEvent,
    required void Function(StreamStatus) onStatus,
    void Function(Object)? onError,
  }) => FakeEventChannel(
    api: this,
    onEvent: onEvent,
    onStatus: onStatus,
    onError: onError,
  );
  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope) onEvent,
    required void Function(StreamStatus) onStatus,
    void Function(Object)? onError,
  }) => FakeEventChannel(
    api: this,
    onEvent: onEvent,
    onStatus: onStatus,
    onError: onError,
    global: true,
  );
  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) =>
      Future.error(UnimplementedError('This fixture does not accept prompts'));

  @override
  Future<void> promptWithMessageID(
    String sessionID, {
    required String messageID,
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
    void Function()? beforeSend,
  }) async {
    beforeSend?.call();
    await promptAsync(
      sessionID,
      text: text,
      model: model,
      agent: agent,
      variant: variant,
      attachments: attachments,
      agentMentions: agentMentions,
      delivery: delivery,
    );
  }

  int _messageSequence = 0;
  @override
  String createPromptMessageID() => 'msg_fixture_${++_messageSequence}';
  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async => const {};
  @override
  Future<bool> genUiSessionIdle(String sessionID) async => true;
  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) async => GenUiHistoryPage(
    items: cursor == null ? await messages(sessionID) : const [],
    hasMore: false,
  );
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
