import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/api/sse.dart' show LiveEventChannel, StreamStatus;
import 'package:opencode_mobile/api2/transport.dart' show Api2AuthRequired;
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeChannel implements LiveEventChannel {
  bool started = false;
  bool disposed = false;

  @override
  void start() => started = true;

  @override
  Future<void> dispose() async => disposed = true;
}

/// Minimal live v2 gateway: health + event channels succeed, everything else
/// throws and is absorbed by the controller's per-surface error handling.
class _FakeV2Gateway implements ServerGateway {
  int healthCalls = 0;
  bool closed = false;
  String? _directory;
  String? _workspace;
  _FakeChannel? channel;
  _FakeChannel? globalChannel;
  void Function(StreamStatus status)? streamStatus;
  void Function(Object error)? streamError;

  @override
  Future<Health> health() async {
    healthCalls += 1;
    return Health(healthy: true, version: '0.0.0-beta-18600');
  }

  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) {
    streamStatus = onStatus;
    streamError = onError;
    return channel = _FakeChannel();
  }

  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => globalChannel = _FakeChannel();

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
  }

  @override
  void close() => closed = true;

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

class _FakeV2Operations implements ServerOperationsGateway {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

class _FakeV1Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

/// A v1 client whose health mimics meeting v2's Basic-auth gate (401).
class _WrongFlavorV1Api extends OpenCodeApi {
  _WrongFlavorV1Api() : super(baseUrl: 'http://127.0.0.1:1');

  int healthCalls = 0;

  @override
  Future<Health> health() {
    healthCalls += 1;
    throw ApiException('GET /global/health failed (HTTP 401)', statusCode: 401);
  }
}

/// A v1 client that answers but reports itself unhealthy — a truthful v1
/// failure that must never be mistaken for a flavor problem.
class _UnhealthyV1Api extends OpenCodeApi {
  _UnhealthyV1Api() : super(baseUrl: 'http://127.0.0.1:1');

  @override
  Future<Health> health() async => Health(healthy: false, version: '1.18.23');
}

/// Records saves without touching the real secure-storage channel, which is
/// unmocked in widget tests and would hang a real upsert.
class _RecordingStore extends ProfileStore {
  _RecordingStore({required super.prefs});

  final saved = <ServerProfile>[];

  @override
  Future<void> upsert(ServerProfile profile) async => saved.add(profile);
}

Future<_RecordingStore> _store() async {
  SharedPreferences.setMockInitialValues({});
  return _RecordingStore(prefs: await SharedPreferences.getInstance());
}

ServerProfile _v2Profile() => ServerProfile(
  id: 'v2-box',
  name: 'V2 box',
  baseUrl: 'http://127.0.0.1:1',
  password: 'serve-password',
  flavor: ServerFlavor.v2,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() => serverProbe = probeServerConnection);

  testWidgets('a v2 profile builds the v2 pair and gateway event channels', (
    tester,
  ) async {
    final gateway = _FakeV2Gateway();
    final operations = _FakeV2Operations();
    var v1ApiBuilds = 0;
    var v2Builds = 0;
    final controller = ConnectionController(
      await _store(),
      apiFactory: (_) {
        v1ApiBuilds += 1;
        return OpenCodeApi(baseUrl: 'http://127.0.0.1:1');
      },
      repositoryFactory: (_) => _FakeV1Repository(),
      v2GatewayFactory: (profile) {
        v2Builds += 1;
        return (gateway: gateway, operations: operations);
      },
    );
    // Disposed at the end of the body, not in tearDown: the polling
    // fallback timer must be cancelled before the tester's invariant check.
    addTearDown(controller.dispose);

    await controller.connect(_v2Profile());
    await tester.pump();

    expect(v2Builds, 1);
    expect(v1ApiBuilds, 0, reason: 'a v2 profile must never build the v1 api');
    expect(controller.api, same(gateway));
    expect(controller.repository, same(operations));
    expect(gateway.healthCalls, 1);
    expect(controller.version, '0.0.0-beta-18600');
    // The event channels come from the gateway's EventGateway seam, not the
    // v1 EventStream factory.
    expect(gateway.channel?.started, isTrue);
    expect(gateway.globalChannel?.started, isTrue);
    controller.dispose();
  });

  testWidgets('a mid-session 401 sets passwordRejected until recovery', (
    tester,
  ) async {
    final gateway = _FakeV2Gateway();
    final controller = ConnectionController(
      await _store(),
      repositoryFactory: (_) => _FakeV1Repository(),
      v2GatewayFactory: (_) => (
        gateway: gateway,
        operations: _FakeV2Operations(),
      ),
    );
    // Disposed at the end of the body, not in tearDown: the polling
    // fallback timer must be cancelled before the tester's invariant check.
    addTearDown(controller.dispose);
    await controller.connect(_v2Profile());
    await tester.pump();
    expect(controller.passwordRejected, isFalse);

    gateway.streamError!(
      const Api2AuthRequired('GET /event: authentication required'),
    );
    expect(controller.passwordRejected, isTrue);

    // Other stream failures never claim a rotated password.
    controller.passwordRejected = false;
    gateway.streamError!(StateError('socket closed'));
    expect(controller.passwordRejected, isFalse);

    // A recovered stream clears the flag (a fresh password worked).
    gateway.streamError!(
      const Api2AuthRequired('GET /event: authentication required'),
    );
    expect(controller.passwordRejected, isTrue);
    gateway.streamStatus!(StreamStatus.connected);
    expect(controller.passwordRejected, isFalse);
    await tester.pump();
    controller.dispose();
  });

  testWidgets('a wrong cached flavor re-probes once and retries as v2', (
    tester,
  ) async {
    final gateway = _FakeV2Gateway();
    final v1Api = _WrongFlavorV1Api();
    var probeCalls = 0;
    serverProbe = ({required baseUrl, username, password}) async {
      probeCalls += 1;
      return const ServerProbeResult.success(
        '0.0.0-beta-18600',
        flavor: ServerFlavor.v2,
      );
    };
    final store = await _store();
    final controller = ConnectionController(
      store,
      apiFactory: (_) => v1Api,
      repositoryFactory: (_) => _FakeV1Repository(),
      v2GatewayFactory: (_) => (
        gateway: gateway,
        operations: _FakeV2Operations(),
      ),
    );
    // Disposed at the end of the body, not in tearDown: the polling
    // fallback timer must be cancelled before the tester's invariant check.
    addTearDown(controller.dispose);

    final profile = ServerProfile(
      id: 'was-v1',
      name: 'Upgraded box',
      baseUrl: 'http://127.0.0.1:1',
      password: 'serve-password',
    );
    expect(profile.flavor, ServerFlavor.v1);
    await controller.connect(profile);
    await tester.pump();

    expect(probeCalls, 1);
    expect(profile.flavor, ServerFlavor.v2);
    expect(profile.serverVersion, '0.0.0-beta-18600');
    // The corrected flavor is persisted so the next connect skips detection.
    expect(store.saved.single.flavor, ServerFlavor.v2);
    expect(controller.api, same(gateway));
    expect(gateway.healthCalls, 1);
    expect(v1Api.healthCalls, 1);
    controller.dispose();
  });

  testWidgets('an unhealthy v1 server fails closed without a re-probe', (
    tester,
  ) async {
    var probeCalls = 0;
    serverProbe = ({required baseUrl, username, password}) async {
      probeCalls += 1;
      return const ServerProbeResult.success(
        '0.0.0-beta-18600',
        flavor: ServerFlavor.v2,
      );
    };
    final controller = ConnectionController(
      await _store(),
      apiFactory: (_) => _UnhealthyV1Api(),
      repositoryFactory: (_) => _FakeV1Repository(),
    );
    // Disposed at the end of the body, not in tearDown: the polling
    // fallback timer must be cancelled before the tester's invariant check.
    addTearDown(controller.dispose);

    await controller.connect(
      ServerProfile(
        id: 'sick',
        name: 'Sick box',
        baseUrl: 'http://127.0.0.1:1',
      ),
    );
    await tester.pump();

    expect(probeCalls, 0, reason: 'unhealthy is not a flavor problem');
    expect(controller.api, isNull);
    expect(controller.lastError, contains('unhealthy'));
    controller.dispose();
  });
}
