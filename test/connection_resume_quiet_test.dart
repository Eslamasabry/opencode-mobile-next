// The owner, 2026-10-27: "the Claude Code refresh or reconnecting shows every
// time the app was in background and I return". The app retires its link on
// purpose in the background: no line says anything is down while it is out of
// sight, and the return rebuilds the link without a line for a short grace.
// Only a reconnect still going after that grace shows, and it folds away once
// the link is back.
import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';

import 'support/connection_v2_fixtures.dart';

final _profile = ServerProfile(
  id: 'server',
  name: 'Server',
  baseUrl: 'http://127.0.0.1:1',
);

Future<(ConnectionController, List<V2Api>)> _connected(
  WidgetTester tester, {
  Completer<Health>? resumeHealth,
}) async {
  final apis = <V2Api>[];
  final controller = ConnectionController(
    await memoryProfileStore(),
    apiFactory: (_) {
      final api = V2Api();
      if (apis.isNotEmpty && resumeHealth != null) {
        api.healthCompleter = resumeHealth;
      }
      apis.add(api);
      return api;
    },
    repositoryFactory: (_) => QuestionRepository(legacyUnavailable: false),
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) =>
            FakeEventStream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            ),
  );
  addTearDown(controller.dispose);
  await controller.connect(_profile);
  await tester.pump();
  expect(controller.status, StreamStatus.connected);
  return (controller, apis);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('in the background the retired link says nothing is down', (
    tester,
  ) async {
    final (controller, _) = await _connected(tester);
    controller.suspendForLifecycle();
    expect(controller.status, isNot(StreamStatus.connected));
    expect(controller.connectionStatus.visible, isFalse);
    await tester.pump(const Duration(seconds: 30));
    expect(controller.connectionStatus.visible, isFalse);
    expect(
      controller.connectionStatus.phase,
      isNot(ConnectionStatusPhase.notAnswering),
    );
    controller.dispose();
  });

  testWidgets('the return reconnects quietly; a quick link never shows', (
    tester,
  ) async {
    final health = Completer<Health>();
    final (controller, _) = await _connected(tester, resumeHealth: health);
    controller.suspendForLifecycle();
    final resume = controller.resumeFromLifecycle();
    expect(controller.status, StreamStatus.connecting);
    expect(controller.connectionStatus.visible, isFalse);
    expect(controller.connectionStatus.waiting, isFalse);
    await tester.pump(const Duration(seconds: 2));
    expect(controller.connectionStatus.visible, isFalse);
    health.complete(Health(healthy: true, version: 'v2'));
    await resume;
    await tester.pump();
    expect(controller.status, StreamStatus.connected);
    expect(controller.connectionStatus.visible, isFalse);
    await tester.pump(const Duration(seconds: 5));
    expect(controller.connectionStatus.visible, isFalse);
    controller.dispose();
  });

  testWidgets('a reconnect still going after the grace shows, then folds '
      'away once the link is back', (tester) async {
    final health = Completer<Health>();
    final (controller, _) = await _connected(tester, resumeHealth: health);
    var changes = 0;
    controller.addListener(() => changes++);
    controller.suspendForLifecycle();
    final resume = controller.resumeFromLifecycle();
    await tester.pump(const Duration(milliseconds: 3900));
    expect(controller.connectionStatus.visible, isFalse);
    final before = changes;
    await tester.pump(const Duration(milliseconds: 200));
    // The grace ran out by itself: the line is told to show.
    expect(changes, greaterThan(before));
    expect(controller.connectionStatus.visible, isTrue);
    expect(controller.connectionStatus.waiting, isTrue);
    health.complete(Health(healthy: true, version: 'v2'));
    await resume;
    await tester.pump();
    expect(controller.connectionStatus.visible, isFalse);
    controller.dispose();
  });

  testWidgets('Try again during the grace says it is reconnecting at once', (
    tester,
  ) async {
    final health = Completer<Health>();
    final (controller, _) = await _connected(tester, resumeHealth: health);
    controller.suspendForLifecycle();
    final resume = controller.resumeFromLifecycle();
    expect(controller.connectionStatus.visible, isFalse);
    // A resume in flight answers Try again with itself; the person asked,
    // so the grace ends.
    unawaited(controller.retryConnection());
    expect(controller.connectionStatus.visible, isTrue);
    health.complete(Health(healthy: true, version: 'v2'));
    await resume;
    await tester.pump(const Duration(seconds: 5));
    controller.dispose();
  });
}
