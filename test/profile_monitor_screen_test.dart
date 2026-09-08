import 'package:flutter/material.dart';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/ui/screens/profile_monitor_screen.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'support/profile_monitor_fixture.dart';

Future<void> _reveal(WidgetTester tester, Finder target) async {
  for (
    var attempt = 0;
    attempt < 30 && target.hitTestable().evaluate().isEmpty;
    attempt++
  ) {
    await tester.drag(find.byType(ListView), const Offset(0, -180));
    await tester.pump();
  }
  expect(target.hitTestable(), findsOneWidget);
}

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
  testWidgets(
    'monitoring is explicit and selected-location rows show truthful current and unknown states',
    (tester) async {
      final store = await monitorStore();
      var reads = 0;
      final controller = ConnectionController(
        store,
        monitorGatewayFactory: (_) {
          reads++;
          return (
            gateway: MonitorTestGateway(requests: [request(1)]),
            operations: MonitorTestOperations(),
          );
        },
      );
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ProfileMonitorScreen(controller: controller),
        ),
      );
      await tester.pump();
      expect(reads, 0);
      expect(find.text('Not monitored · attention unknown'), findsNWidgets(2));
      final enable = find
          .widgetWithText(SwitchListTile, 'Monitor this server')
          .first;
      await tester.ensureVisible(enable);
      await tester.tap(enable);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(reads, 1);
      expect(find.text('Current observation'), findsOneWidget);
      expect(find.text('Private title'), findsOneWidget);
      await _reveal(tester, find.text('Not monitored · attention unknown'));
      expect(find.text('Not monitored · attention unknown'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets('monitor settings remain reachable at 320px and 2.5x text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final store = await monitorStore(count: 1);
    final controller = ConnectionController(
      store,
      monitorGatewayFactory: (_) =>
          (gateway: MonitorTestGateway(), operations: MonitorTestOperations()),
    );
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2.5)),
          child: child!,
        ),
        home: ProfileMonitorScreen(controller: controller),
      ),
    );
    final enable = find.descendant(
      of: find.widgetWithText(SwitchListTile, 'Monitor this server'),
      matching: find.byType(Switch),
    );
    await _reveal(tester, enable);
    await tester.tap(enable.hitTestable());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    final quiet = find.descendant(
      of: find.widgetWithText(SwitchListTile, 'Quiet hours'),
      matching: find.byType(Switch),
    );
    await _reveal(tester, quiet);
    await tester.tap(quiet.hitTestable());
    await tester.pump();
    expect(controller.profileMonitor.rulesFor('profile-1').quietStart, 22 * 60);
    final checkIn = find.byKey(const ValueKey('monitor-check-in-profile-1'));
    await _reveal(tester, checkIn);
    expect(
      controller.profileMonitor.rulesFor('profile-1').checkInAfterMinutes,
      isNull,
    );
    await tester.tap(
      find.descendant(of: checkIn, matching: find.byType(Switch)).hitTestable(),
    );
    await tester.pump();
    expect(
      controller.profileMonitor.rulesFor('profile-1').checkInAfterMinutes,
      30,
    );
    final duration = find.byKey(
      const ValueKey('monitor-check-in-after-profile-1'),
    );
    await _reveal(tester, duration);
    await tester.tap(duration.hitTestable());
    await tester.pumpAndSettle();
    await tester.tap(find.text('15 minutes').last);
    await tester.pumpAndSettle();
    expect(
      controller.profileMonitor.rulesFor('profile-1').checkInAfterMinutes,
      15,
    );
    expect(
      jsonDecode(
        store.prefs.getString(ProfileMonitor.rulesKey('profile-1'))!,
      )['checkInAfterMinutes'],
      15,
    );
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'active server due interval appears in the Inbox without becoming pending attention',
    (tester) async {
      final store = await monitorStore(count: 1);
      await store.setActiveId('profile-1');
      final now = DateTime.now();
      await store.prefs.setString(
        ProfileMonitor.busyIntervalsKey('profile-1'),
        jsonEncode([
          ObservedBusyInterval(
            sessionID: 'same-session',
            firstObservedBusyAt: now.subtract(const Duration(minutes: 31)),
            lastObservedBusyAt: now.subtract(const Duration(minutes: 1)),
          ).toJson(),
        ]),
      );
      await store.prefs.setString(
        ProfileMonitor.rulesKey('profile-1'),
        jsonEncode(
          const ProfileNotifyRules(
            enabled: true,
            checkInAfterMinutes: 30,
          ).toJson(),
        ),
      );
      final controller = ConnectionController(
        store,
        monitorGatewayFactory: (_) => (
          gateway: MonitorTestGateway(),
          operations: MonitorTestOperations(),
        ),
      );
      await controller.profileMonitor.refresh();
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: ProfileMonitorInbox(controller: controller)),
        ),
      );
      await tester.pump();
      expect(find.text('Private title'), findsOneWidget);
      expect(find.textContaining('Time to check in'), findsOneWidget);
      expect(
        controller.profileMonitor.snapshotFor('profile-1').pendingCount,
        0,
      );
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: ActivityScreen(controller: controller, embedded: true),
          ),
        ),
      );
      await tester.pump();
      expect(find.textContaining('Time to check in'), findsOneWidget);
      expect(find.byKey(const ValueKey('activity-all-clear')), findsNothing);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ProfileMonitorScreen(controller: controller),
        ),
      );
      await tester.pump();
      final busyRow = find.byKey(const ValueKey('monitor-busy-same-session'));
      await _reveal(tester, busyRow);
      final span = controller.profileMonitor
          .snapshotFor('profile-1')
          .busyIntervals
          .single
          .observedFor
          .inMinutes;
      // Busy samples can straddle idle work that the monitor never observed.
      expect(
        find.textContaining('Busy at checks spanning $span min'),
        findsOneWidget,
      );
      expect(find.textContaining('Seen busy for at least'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
      expect(tester.takeException(), isNull);
    },
  );
}
