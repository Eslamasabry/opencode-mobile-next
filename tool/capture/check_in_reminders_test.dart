import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/profile_monitor_screen.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show StreamStatus;

import '../../test/support/profile_monitor_fixture.dart';
import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final dark in [false, true]) {
    for (final scenario in [
      'controls',
      'narrow',
      'inbox',
      'unavailable',
      'off',
    ]) {
      testWidgets('check-in $scenario ${dark ? 'dark' : 'light'}', (
        tester,
      ) async {
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        const secure = MethodChannel(
          'plugins.it_nomads.com/flutter_secure_storage',
        );
        messenger.setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
        addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
        tester.view.physicalSize = Size(scenario == 'narrow' ? 320 : 390, 844);
        tester.view.devicePixelRatio = 1;
        tester.platformDispatcher.textScaleFactorTestValue =
            scenario == 'narrow' ? 2 : 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        final store = await monitorStore(count: 1);
        await store.setActiveId('profile-1');
        final now = DateTime.now();
        if (scenario != 'off') {
          await store.prefs.setString(
            ProfileMonitor.rulesKey('profile-1'),
            jsonEncode(
              const ProfileNotifyRules(
                enabled: true,
                checkInAfterMinutes: 30,
              ).toJson(),
            ),
          );
          await store.prefs.setString(
            ProfileMonitor.busyIntervalsKey('profile-1'),
            jsonEncode([
              ObservedBusyInterval(
                sessionID: 'same-session',
                firstObservedBusyAt: now.subtract(const Duration(minutes: 36)),
                lastObservedBusyAt: now.subtract(const Duration(minutes: 1)),
              ).toJson(),
            ]),
          );
        }
        final controller = ConnectionController(
          store,
          monitorGatewayFactory: (_) => (
            gateway: MonitorTestGateway(failure: scenario == 'unavailable'),
            operations: MonitorTestOperations(),
          ),
        );
        controller
          ..api = (CaptureApi()..busy = {})
          ..repository = CaptureRepository()
          ..status = StreamStatus.connected;
        try {
          await controller.profileMonitor.refresh();
          final boundary = GlobalKey();
          await tester.pumpWidget(
            captureApp(
              boundaryKey: boundary,
              controller: controller,
              store: store,
              light: !dark,
              home: scenario == 'inbox'
                  ? const HomeScreen(initialTab: 2)
                  : ProfileMonitorScreen(controller: controller),
            ),
          );
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 200));
          if (scenario == 'controls' || scenario == 'narrow') {
            final target = find.byKey(
              const ValueKey('monitor-check-in-after-profile-1'),
            );
            await tester.scrollUntilVisible(
              target,
              250,
              scrollable: find.byType(Scrollable).first,
            );
            await tester.pumpAndSettle();
            expect(target.hitTestable(), findsOneWidget);
          }
          if (scenario == 'inbox') {
            expect(find.textContaining('Time to check in'), findsOneWidget);
            expect(
              find.byKey(const ValueKey('activity-all-clear')),
              findsNothing,
            );
          }
          if (scenario == 'unavailable') {
            expect(
              find.byKey(const ValueKey('monitor-busy-same-session')),
              findsNothing,
            );
          }
          expect(tester.takeException(), isNull);
          await writePng(
            'docs/qa/check-in-reminders/$scenario-${dark ? 'dark' : 'light'}.png',
            await capturePng(tester, boundary, pixelRatio: 1),
          );
        } finally {
          await tester.pumpWidget(const SizedBox.shrink());
          controller.dispose();
          await tester.pump();
        }
        expect(tester.takeException(), isNull);
      });
    }
  }
}
