import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/profile_monitor_screen.dart';
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
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
    expect(tester.takeException(), isNull);
  });
}
