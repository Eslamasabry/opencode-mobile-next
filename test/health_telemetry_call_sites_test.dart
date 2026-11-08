// The health events at the places in the app that fire them, with fakes:
// the offline queue flush (queue_flush) and a connect (conn_attempt, conn_drop).
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/telemetry/telemetry_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_event_channel.dart';
import 'support/fake_server_gateway.dart';
import 'support/fake_server_operations.dart';
import 'support/offline_queue_fixtures.dart';
import 'support/telemetry_recorder.dart';

class _Gateway extends FakeServerGateway {
  _Gateway(this.onHealth) : super(baseUrl: 'http://example.test:4096');
  final Future<Health> Function() onHealth;
  @override
  Future<Health> health() => onHealth();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (_) async => null,
    );
    messenger.setMockMethodCallHandler(
      const MethodChannel('oc/background'),
      (_) async => null,
    );
  });
  tearDown(() {
    AppTelemetry.reset();
    AppTelemetry.health = null;
  });

  Future<RecordingAdapter> consenting() async {
    final adapter = RecordingAdapter();
    AppTelemetry.install(
      await startedService(adapter, usage: true, crashes: true),
    );
    return adapter;
  }

  group('queue_flush', () {
    test('counts what was sent and what failed', () async {
      final adapter = await consenting();
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('a', text: 'one'));
      await controller.queuePrompt(queuedEntry('b', text: 'two'));
      await controller.flushOfflineQueue();
      await settleTelemetry();
      final flush = adapter.events.where((e) => e.$1 == 'queue_flush');
      expect(flush.single.$2, {'sent': '2-5', 'failed': '0'});
      expect(api.prompts, hasLength(2));
    });

    test('a refused prompt is counted as failed', () async {
      final adapter = await consenting();
      final api = FakeApi()
        ..promptPlan.add(ApiException('refused', statusCode: 400));
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('a', text: 'one'));
      await controller.flushOfflineQueue();
      await settleTelemetry();
      final flush = adapter.events.where((e) => e.$1 == 'queue_flush');
      expect(flush.single.$2, {'sent': '0', 'failed': '1'});
    });

    test('with usage off nothing is sent', () async {
      final adapter = RecordingAdapter();
      AppTelemetry.install(await startedService(adapter, crashes: true));
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('a'));
      await controller.flushOfflineQueue();
      await settleTelemetry();
      expect(adapter.events, isEmpty);
    });
  });

  group('conn_attempt', () {
    Future<ConnectionController> controller(_Gateway gateway) async {
      SharedPreferences.setMockInitialValues({});
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final c = ConnectionController(
        store,
        apiFactory: (_) => gateway,
        repositoryFactory: (_) => FakeServerOperations(),
        eventStreamFactory:
            ({required api, required onEvent, required onStatus, onError}) =>
                FakeEventChannel(
                  api: api,
                  onEvent: onEvent,
                  onStatus: onStatus,
                  onError: onError,
                ),
      );
      addTearDown(c.dispose);
      return c;
    }

    ServerProfile profile() =>
        ServerProfile(id: 's', name: 's', baseUrl: 'https://example.test:4096');

    test(
      'a server that answers is a connection ok, with how long it took',
      () async {
        final adapter = await consenting();
        final c = await controller(
          _Gateway(() async => Health(healthy: true, version: '2.0.28')),
        );
        unawaited(c.connect(profile()));
        for (var i = 0; i < 30; i++) {
          await settleTelemetry();
        }
        final attempts = adapter.events.where((e) => e.$1 == 'conn_attempt');
        expect(attempts, hasLength(1));
        expect(attempts.single.$2, {
          'server_kind': 'computer',
          'result': 'ok',
          'ms_range': '<500',
        });
        expect(adapter.crumbs, contains('conn.connect'));
      },
    );

    test('a host that cannot be found is a failed attempt of kind dns, with '
        'no host name in it', () async {
      final adapter = await consenting();
      final c = await controller(
        _Gateway(
          () async =>
              throw const SocketException('Failed host lookup: example.test'),
        ),
      );
      await c.connect(profile());
      await settleTelemetry();
      final attempts = adapter.events.where((e) => e.$1 == 'conn_attempt');
      expect(attempts.single.$2, {
        'server_kind': 'computer',
        'result': 'fail',
        'fail_kind': 'dns',
        'ms_range': '<500',
      });
      expect(attempts.map((e) => '${e.$2}').join(), isNot(contains('example')));
    });

    test('a wrong password is a failed attempt of kind auth', () async {
      final adapter = await consenting();
      final c = await controller(
        _Gateway(() async => throw ApiException('no', statusCode: 401)),
      );
      await c.connect(profile());
      await settleTelemetry();
      final attempts = adapter.events.where((e) => e.$1 == 'conn_attempt');
      expect(attempts.single.$2['fail_kind'], 'auth');
    });

    test('nothing is sent with usage off', () async {
      final adapter = RecordingAdapter();
      AppTelemetry.install(await startedService(adapter, crashes: true));
      final c = await controller(
        _Gateway(() async => throw ApiException('no', statusCode: 503)),
      );
      await c.connect(profile());
      await settleTelemetry();
      expect(adapter.events, isEmpty);
    });
  });

  group('send_result', () {
    Future<void> type(WidgetTester tester, String text, String tooltip) async {
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        text,
      );
      await tester.pump();
      await tester.tap(find.byTooltip(tooltip));
      await tester.pump();
      await tester.pump();
    }

    testWidgets('a send while disconnected is queued', (tester) async {
      final adapter = await consenting();
      final controller = await queueController(
        FakeApi(),
        status: StreamStatus.disconnected,
      );
      addTearDown(controller.dispose);
      await pumpChat(tester, controller);
      await type(tester, 'offline draft', 'Send when back online');
      final sent = adapter.events.where((e) => e.$1 == 'send_result');
      expect(sent.single.$2, {'result': 'queued', 'first_reply_range': 'none'});
    });

    testWidgets('an accepted send with no reply is ok, first reply none, '
        'after a minute', (tester) async {
      final adapter = await consenting();
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await pumpChat(tester, controller);
      await type(tester, 'hello there', 'Send');
      expect(adapter.events.where((e) => e.$1 == 'send_result'), isEmpty);
      await tester.pump(const Duration(seconds: 30));
      expect(adapter.events.where((e) => e.$1 == 'send_result'), isEmpty);
      await tester.pump(const Duration(seconds: 40));
      await tester.pump();
      final sent = adapter.events.where((e) => e.$1 == 'send_result');
      expect(sent.single.$2, {'result': 'ok', 'first_reply_range': 'none'});
      expect(adapter.crumbs, contains('chat.send'));
    });

    testWidgets('a refused send is a failure of kind rejected', (tester) async {
      final adapter = await consenting();
      final api = FakeApi()
        ..promptPlan.add(ApiException('no thanks', statusCode: 422));
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await pumpChat(tester, controller);
      await type(tester, 'hello there', 'Send');
      await tester.pump();
      final sent = adapter.events.where((e) => e.$1 == 'send_result');
      expect(sent.single.$2, {
        'result': 'fail',
        'fail_kind': 'rejected',
        'first_reply_range': 'none',
      });
      expect(sent.map((e) => '${e.$2}').join(), isNot(contains('no thanks')));
    });

    testWidgets('with usage off nothing is sent and no timer runs', (
      tester,
    ) async {
      final adapter = RecordingAdapter();
      AppTelemetry.install(await startedService(adapter, crashes: true));
      final controller = await queueController(FakeApi());
      addTearDown(controller.dispose);
      await pumpChat(tester, controller);
      await type(tester, 'hello there', 'Send');
      await tester.pump(const Duration(minutes: 2));
      expect(adapter.events, isEmpty);
    });
  });
}
