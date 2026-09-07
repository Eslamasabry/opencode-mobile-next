import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/codex/gateway.dart'
    show codexServerCapabilities;
import 'package:opencode_mobile/codex/transport.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeChannel implements LiveEventChannel {
  _FakeChannel(this._onEvent, this._onStatus);

  final void Function(EventEnvelope) _onEvent;
  final void Function(StreamStatus) _onStatus;
  bool started = false;
  bool disposed = false;

  void emitEvent(EventEnvelope event) => _onEvent(event);

  @override
  void start() {
    started = true;
    _onStatus(StreamStatus.connected);
  }

  @override
  Future<void> dispose() async {
    disposed = true;
  }
}

class _FakeCodexGateway implements ServerGateway {
  _FakeCodexGateway({this.healthError});

  final Object? healthError;
  final scopes = <(String?, String?)>[];
  int sessionPageReads = 0;
  int providerReads = 0;
  int nullScopedReads = 0;
  bool closed = false;
  _FakeChannel? eventChannel;
  _FakeChannel? globalEventChannel;
  String? _directory;
  String? _workspace;

  @override
  ServerCapabilities get capabilities => codexServerCapabilities;

  @override
  String? get directory => _directory;

  @override
  String? get workspace => _workspace;

  @override
  bool get isClosed => closed;

  @override
  void setLocation({String? directory, String? workspace}) {
    _directory = directory;
    _workspace = workspace;
    scopes.add((directory, workspace));
  }

  @override
  Future<Health> health() async {
    if (healthError case final error?) throw error;
    return Health(healthy: true, version: 'Codex app-server (test)');
  }

  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async {
    sessionPageReads += 1;
    if (_directory == null) nullScopedReads += 1;
    return const ServerPage(items: []);
  }

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<ProvidersResponse> providers() async {
    providerReads += 1;
    if (_directory == null) nullScopedReads += 1;
    return ProvidersResponse(providers: const []);
  }

  @override
  Future<List<AgentInfo>> agents() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => const [];

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => const [];

  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => eventChannel = _FakeChannel(onEvent, onStatus);

  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => globalEventChannel = _FakeChannel(onEvent, onStatus);

  @override
  void close() => closed = true;

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

class _FakeCodexOperations implements ServerOperationsGateway {
  final scopes = <(String?, String?)>[];

  @override
  void setLocation({String? directory, String? workspace}) {
    scopes.add((directory, workspace));
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

class _RecordingStore extends ProfileStore {
  _RecordingStore({required super.prefs});

  @override
  Future<void> upsert(ServerProfile profile) async {}
}

Future<_RecordingStore> _store() async {
  SharedPreferences.setMockInitialValues({});
  return _RecordingStore(prefs: await SharedPreferences.getInstance());
}

ServerProfile _codexProfile({
  String directory = '/workspace/project',
  String token = 'codex-token',
  bool requiresReentry = false,
}) => ServerProfile(
  id: 'codex-profile',
  name: 'Codex',
  baseUrl: 'wss://codex.example.test',
  backend: ServerBackend.codex,
  codexToken: token,
  codexDirectory: directory,
  requiresCodexTokenReentry: requiresReentry,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() => serverProbe = probeServerConnection);

  testWidgets(
    'Codex startup uses a non-null profile scope for sessions and catalog',
    (tester) async {
      final gateway = _FakeCodexGateway();
      final operations = _FakeCodexOperations();
      final profiles = <ServerProfile>[];
      final controller = ConnectionController(
        await _store(),
        codexGatewayFactory: (profile) {
          profiles.add(profile);
          return (gateway: gateway, operations: operations);
        },
      );
      addTearDown(controller.dispose);

      await controller.connect(_codexProfile());
      await tester.pump();

      expect(profiles.single.backend, ServerBackend.codex);
      expect(gateway.scopes, isNotEmpty);
      expect(gateway.scopes.first.$1, '/workspace/project');
      expect(operations.scopes.first.$1, '/workspace/project');
      expect(gateway.sessionPageReads, greaterThan(0));
      expect(gateway.providerReads, greaterThan(0));
      expect(gateway.nullScopedReads, 0);
      expect(controller.directory, '/workspace/project');
      expect(gateway.eventChannel?.started, isTrue);
      controller.dispose();
      await tester.pump();
    },
  );

  testWidgets(
    'Codex token rejection reports reentry and never invokes HTTP redetection',
    (tester) async {
      var probeCalls = 0;
      serverProbe = ({required baseUrl, username, password}) async {
        probeCalls += 1;
        return const ServerProbeResult.success('unexpected');
      };
      final gateway = _FakeCodexGateway(
        healthError: CodexFailure(CodexFailureKind.authentication),
      );
      final controller = ConnectionController(
        await _store(),
        codexGatewayFactory: (_) =>
            (gateway: gateway, operations: _FakeCodexOperations()),
      );
      addTearDown(controller.dispose);

      final profile = _codexProfile();
      await controller.connect(profile);
      await tester.pump();

      expect(probeCalls, 0);
      expect(controller.passwordRejected, isTrue);
      expect(profile.requiresCodexTokenReentry, isTrue);
      expect(profile.backend, ServerBackend.codex);
      expect(controller.api, isNull);
      controller.dispose();
      await tester.pump();
    },
  );

  testWidgets(
    'an explicit Codex folder wins over a stale persisted location on connect',
    (tester) async {
      final store = await _store();
      final profile = _codexProfile(directory: '/workspace/new');
      await store.setLocation(profile.id, directory: '/workspace/old');
      final gateway = _FakeCodexGateway();
      final operations = _FakeCodexOperations();
      final controller = ConnectionController(
        store,
        codexGatewayFactory: (_) => (gateway: gateway, operations: operations),
      );
      addTearDown(controller.dispose);

      await controller.connect(profile);
      await tester.pump();

      expect(gateway.scopes.first.$1, '/workspace/new');
      expect(operations.scopes.first.$1, '/workspace/new');
      expect(controller.directory, '/workspace/new');
      controller.dispose();
      await tester.pump();
    },
  );

  testWidgets(
    'Codex select-location and lifecycle reconnect preserve backend scope and dispose pairs',
    (tester) async {
      final pairs = [
        (_FakeCodexGateway(), _FakeCodexOperations()),
        (_FakeCodexGateway(), _FakeCodexOperations()),
        (_FakeCodexGateway(), _FakeCodexOperations()),
      ];
      var factoryCalls = 0;
      final controller = ConnectionController(
        await _store(),
        codexGatewayFactory: (_) {
          final pair = pairs[factoryCalls++];
          return (gateway: pair.$1, operations: pair.$2);
        },
      );
      addTearDown(controller.dispose);

      await controller.connect(_codexProfile());
      await tester.pump();
      await controller.selectLocation(directory: '/workspace/other');
      await tester.pump();
      await controller.retryConnection();
      await tester.pump();

      expect(factoryCalls, 3);
      expect(pairs[0].$1.closed, isTrue);
      expect(pairs[1].$1.closed, isTrue);
      expect(pairs[2].$1.closed, isFalse);
      expect(pairs[1].$1.scopes.last.$1, '/workspace/other');
      expect(pairs[2].$1.scopes.last.$1, '/workspace/other');
      expect(controller.directory, '/workspace/other');
      controller.dispose();
      await tester.pump();
    },
  );

  testWidgets('an unreadable Codex token refuses the factory and network', (
    tester,
  ) async {
    var factoryCalls = 0;
    final controller = ConnectionController(
      await _store(),
      codexGatewayFactory: (_) {
        factoryCalls += 1;
        return (
          gateway: _FakeCodexGateway(),
          operations: _FakeCodexOperations(),
        );
      },
    );
    addTearDown(controller.dispose);

    await controller.connect(_codexProfile(token: '', requiresReentry: true));
    await tester.pump();

    expect(factoryCalls, 0);
    expect(controller.api, isNull);
    expect(controller.status, StreamStatus.disconnected);
    expect(controller.lastError, contains('token'));
    controller.dispose();
    await tester.pump();
  });

  testWidgets('Codex item completion stays busy until explicit session idle', (
    tester,
  ) async {
    final gateway = _FakeCodexGateway();
    final controller = ConnectionController(
      await _store(),
      codexGatewayFactory: (_) =>
          (gateway: gateway, operations: _FakeCodexOperations()),
    );
    addTearDown(controller.dispose);

    await controller.connect(_codexProfile());
    await tester.pump();
    final channel = gateway.eventChannel!;
    const sessionID = 'codex-session';

    channel.emitEvent(
      EventEnvelope(
        type: 'session.status',
        properties: {
          'sessionID': sessionID,
          'status': {'type': 'busy'},
        },
      ),
    );
    expect(controller.busySessions, contains(sessionID));

    channel.emitEvent(
      EventEnvelope(
        type: 'message.updated',
        properties: {
          'info': {
            'id': 'codex-message',
            'sessionID': sessionID,
            'role': 'assistant',
            'time': {'created': 1, 'completed': 2},
          },
        },
      ),
    );
    expect(controller.busySessions, contains(sessionID));

    channel.emitEvent(
      EventEnvelope(
        type: 'session.status',
        properties: {
          'sessionID': sessionID,
          'status': {'type': 'idle'},
        },
      ),
    );
    expect(controller.busySessions, isNot(contains(sessionID)));

    channel.emitEvent(
      EventEnvelope(
        type: 'message.updated',
        properties: {
          'info': {
            'id': 'late-codex-message',
            'sessionID': sessionID,
            'role': 'assistant',
            'time': {'created': 3, 'completed': 4},
          },
        },
      ),
    );
    expect(controller.busySessions, isNot(contains(sessionID)));

    controller.dispose();
    await tester.pump();
  });
}
