import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/api/models.dart' show Health;
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/stash_memory_vault.dart';

class _Store extends ProfileStore {
  _Store(SharedPreferences prefs) : super(prefs: prefs);

  final servers = [
    ServerProfile(id: 'laptop', name: 'Laptop', baseUrl: 'http://laptop'),
    ServerProfile(id: 'desk', name: 'Desk', baseUrl: 'http://desk'),
  ];
  String? selected = 'laptop';

  @override
  List<ServerProfile> get profiles => servers;
  @override
  String? get activeId => selected;
}

class _PendingApi extends OpenCodeApi {
  _PendingApi() : super(baseUrl: 'http://example.invalid');
  final result = Completer<Health>();
  @override
  Future<Health> health() => result.future;
}

class _Operations implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late _Store store;
  late ConnectionController controller;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = _Store(await SharedPreferences.getInstance());
    controller = ConnectionController(
      store,
      draftAttachmentVault: StashMemoryVault(),
      stashAttachmentVault: StashMemoryVault(),
    );
  });
  tearDown(() => controller.dispose());

  testWidgets(
    'established link gets a bounded quiet window without reachability',
    (tester) async {
      controller.api = OpenCodeApi(baseUrl: 'http://example.invalid');
      controller.status = StreamStatus.connected;
      controller.busySessions.add('working');
      controller.status = StreamStatus.reconnecting;
      expect(controller.connectionStatus.reachable, isFalse);
      expect(controller.connectionStatus.visible, isFalse);
      expect(controller.connectionStatus.waiting, isFalse);
      await tester.pump(const Duration(seconds: 8));
      controller.status = StreamStatus.disconnected;
      controller.status = StreamStatus.reconnecting;
      await tester.pump(const Duration(milliseconds: 6999));
      expect(controller.connectionStatus.visible, isFalse);
      expect(controller.busySessions, contains('working'));
      await tester.pump(const Duration(milliseconds: 1));
      expect(controller.connectionStatus.visible, isTrue);
      controller.dispose();
    },
  );

  testWidgets('a fresh connect stays visible after an established connection', (
    tester,
  ) async {
    controller.api = OpenCodeApi(baseUrl: 'http://example.invalid');
    controller.status = StreamStatus.connected;
    controller.status = StreamStatus.connecting;
    expect(controller.connectionStatus.visible, isTrue);
    expect(controller.connectionStatus.waiting, isTrue);
    controller.dispose();
  });

  testWidgets('credentials bypass the quiet reconnect window', (tester) async {
    controller.api = OpenCodeApi(baseUrl: 'http://example.invalid');
    controller.status = StreamStatus.connected;
    controller.status = StreamStatus.reconnecting;
    controller.passwordRejected = true;
    expect(
      controller.connectionStatus.phase,
      ConnectionStatusPhase.credentialsRequired,
    );
    expect(controller.connectionStatus.visible, isTrue);
    controller.dispose();
  });

  testWidgets('one grace period survives reads and transport reconnect churn', (
    tester,
  ) async {
    var changes = 0;
    controller.addListener(() => changes++);
    controller.status = StreamStatus.connecting;
    final started = controller.connectionStatus.since;
    expect(controller.connectionStatus.phase, ConnectionStatusPhase.connecting);
    expect(controller.connectionStatus.waiting, isTrue);
    await tester.pump(const Duration(seconds: 4));
    controller.status = StreamStatus.reconnecting;
    expect(controller.connectionStatus.since, started);
    await tester.pump(const Duration(milliseconds: 3999));
    expect(
      controller.connectionStatus.phase,
      ConnectionStatusPhase.reconnecting,
    );
    expect(changes, 0);
    await tester.pump(const Duration(milliseconds: 1));
    expect(
      controller.connectionStatus.phase,
      ConnectionStatusPhase.notAnswering,
    );
    expect(controller.connectionStatus.waiting, isFalse);
    expect(changes, 1);
    controller.status = StreamStatus.connecting;
    expect(
      controller.connectionStatus.phase,
      ConnectionStatusPhase.notAnswering,
    );
  });

  testWidgets('a new attempt resets grace; success cancels the old deadline', (
    tester,
  ) async {
    controller.status = StreamStatus.reconnecting;
    await tester.pump(const Duration(seconds: 7));
    controller.connectionAttemptRevision++;
    controller.status = StreamStatus.connecting;
    await tester.pump(const Duration(seconds: 2));
    expect(controller.connectionStatus.waiting, isTrue);
    controller.status = StreamStatus.connected;
    expect(controller.connectionStatus.reachable, isTrue);
    expect(controller.connectionStatus.visible, isFalse);
    await tester.pump(const Duration(seconds: 8));
    controller.status = StreamStatus.reconnecting;
    await tester.pump(const Duration(seconds: 7));
    expect(controller.connectionStatus.waiting, isTrue);
    await tester.pump(const Duration(seconds: 1));
    expect(
      controller.connectionStatus.phase,
      ConnectionStatusPhase.notAnswering,
    );
  });

  testWidgets(
    'real retry publishes pending state and resets an expired attempt',
    (tester) async {
      controller.dispose();
      final api = _PendingApi();
      controller = ConnectionController(
        store,
        apiFactory: (_) => api,
        repositoryFactory: (_) => _Operations(),
        draftAttachmentVault: StashMemoryVault(),
        stashAttachmentVault: StashMemoryVault(),
      );
      controller.status = StreamStatus.reconnecting;
      await tester.pump(const Duration(seconds: 8));
      expect(
        controller.connectionStatus.phase,
        ConnectionStatusPhase.notAnswering,
      );
      controller.passwordRejected = true;
      final attempt = controller.connectionStatus.attemptRevision;
      final retry = controller.retryConnection();
      expect(controller.retryConnection(), same(retry));
      expect(controller.connectionStatus.attemptRevision, greaterThan(attempt));
      expect(controller.connectionStatus.waiting, isTrue);
      expect(controller.connectionStatus.retrying, isTrue);
      await tester.pump(const Duration(seconds: 7));
      expect(controller.connectionStatus.waiting, isTrue);
      api.result.complete(Health(healthy: false));
      await retry;
      expect(
        controller.connectionStatus.phase,
        ConnectionStatusPhase.notAnswering,
      );
      expect(controller.connectionStatus.retrying, isFalse);
      expect(controller.connectionStatus.reachable, isFalse);
      controller.dispose();
    },
  );

  testWidgets('failure and credentials cannot look reachable', (tester) async {
    controller.status = StreamStatus.disconnected;
    expect(
      controller.connectionStatus.phase,
      ConnectionStatusPhase.notAnswering,
    );
    expect(controller.connectionStatus.reachable, isFalse);
    controller.status = StreamStatus.reconnecting;
    controller.passwordRejected = true;
    await tester.pump(const Duration(seconds: 8));
    expect(
      controller.connectionStatus.phase,
      ConnectionStatusPhase.credentialsRequired,
    );
    expect(controller.connectionStatus.reachable, isFalse);
    expect(controller.connectionStatus.serverName, 'Laptop');
    expect(controller.connectionStatus.profileId, 'laptop');
  });

  testWidgets('switching profiles gives the new owner a fresh grace period', (
    tester,
  ) async {
    controller.status = StreamStatus.connecting;
    await tester.pump(const Duration(seconds: 7));
    store.selected = 'desk';
    controller.notifyListeners();
    await tester.pump(const Duration(seconds: 2));
    expect(controller.connectionStatus.profileId, 'desk');
    expect(controller.connectionStatus.waiting, isTrue);
    store.selected = null;
    controller.notifyListeners();
    await tester.pump(const Duration(seconds: 8));
    expect(controller.connectionStatus.phase, ConnectionStatusPhase.hidden);
    expect(controller.connectionStatus.visible, isFalse);
  });

  testWidgets('isolated work has no shared server status or deadline', (
    tester,
  ) async {
    controller.dispose();
    controller = ConnectionController(
      store,
      isIsolated: true,
      draftAttachmentVault: StashMemoryVault(),
      stashAttachmentVault: StashMemoryVault(),
    );
    controller.status = StreamStatus.connecting;
    await tester.pump(const Duration(seconds: 10));
    expect(controller.connectionStatus.phase, ConnectionStatusPhase.hidden);
    expect(controller.connectionStatus.since, isNull);
  });

  testWidgets('disposal cancels the deadline without a late notification', (
    tester,
  ) async {
    var changes = 0;
    controller.addListener(() => changes++);
    controller.status = StreamStatus.connecting;
    controller.dispose();
    await tester.pump(const Duration(seconds: 10));
    expect(changes, 0);
  });
}
