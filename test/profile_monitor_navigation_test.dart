import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'support/profile_monitor_fixture.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        ),
  );
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        ),
  );

  test(
    'a source edit during request preflight cannot switch active transport',
    () async {
      final store = await monitorStore(count: 1);
      final pending = Completer<List<PermissionRequest>>();
      final gateway = MonitorTestGateway(pending: pending);
      final controller = ConnectionController(
        store,
        monitorGatewayFactory: (_) =>
            (gateway: gateway, operations: MonitorTestOperations()),
      );
      addTearDown(controller.dispose);
      // Persist opt-in directly so only the deliberate navigation opens transport.
      await store.prefs.setString(
        ProfileMonitor.rulesKey('profile-1'),
        '{"enabled":true}',
      );
      final route = MonitoredRoute(
        profileID: 'profile-1',
        requestID: 'request-1',
        sessionID: 'same-session',
        kind: MonitoredRequestKind.permission,
        createdAt: DateTime.now(),
        serverUrl: store.profiles.single.baseUrl,
        sourceIdentity: ProfileMonitor.routeSourceIdentity(
          store.profiles.single,
        ),
      );
      final preparing = controller.prepareMonitoredRequest(route);
      store.profiles.single.baseUrl = 'https://different.example';
      pending.complete([request(1)]);
      expect(await preparing, isFalse);
      expect(controller.isConnected, isFalse);
      expect(gateway.isClosed, isTrue);
    },
  );

  for (final kind in [CodingAlertKind.permission, CodingAlertKind.checkIn]) {
    test(
      'monitored ${kind.name} alert carries an opaque profile route with no reply actions',
      () async {
        final store = await monitorStore(count: 1);
        Map<String, dynamic>? posted;
        final background = BackgroundLiveController(
          preferences: store.prefs,
          invoke: (method, [arguments]) async {
            if (method == 'showCodingAlert') posted = arguments;
            return {
              'enabled': true,
              'active': true,
              'notificationGranted': true,
              'shown': true,
            };
          },
        );
        addTearDown(background.dispose);
        await background.setEnabled(true);
        await background.showCodingAlert(
          kind: kind,
          sessionID: 'same-session',
          key: 'monitor:profile-1:request-1',
          profileID: 'profile-1',
          monitorToken: 'opaque-token',
          allowActions: false,
        );
        expect(posted, containsPair('profileID', 'profile-1'));
        expect(posted, containsPair('monitorToken', 'opaque-token'));
        expect(posted, containsPair('allowActions', false));
        expect(posted!.keys, isNot(contains('title')));
        expect(posted!.keys, isNot(contains('password')));
        expect(posted!.keys, isNot(contains('directory')));
        expect(posted!.keys, isNot(contains('prompt')));
        if (kind == CodingAlertKind.checkIn) {
          expect(posted, containsPair('kind', 'checkin'));
        }
        final opened = CodingAlertOpen.fromPlatform(posted!);
        expect(opened?.profileID, 'profile-1');
        expect(opened?.monitorToken, 'opaque-token');
      },
    );
  }
}
