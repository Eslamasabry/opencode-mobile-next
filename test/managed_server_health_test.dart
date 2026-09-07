import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/widgets/managed_server_health.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/termux');
  final calls = <MethodCall>[];
  Completer<Map<String, Object>>? response;

  setUp(() {
    calls.clear();
    response = null;
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          if ((call.arguments as Map)['script'] ==
              TermuxBridge.storageScript()) {
            return {
              'exitCode': 0,
              'stdout': 'total_kib=10485760\navailable_kib=5242880\n',
              'stderr': '',
            };
          }
          return response?.future ??
              {
                'exitCode': 0,
                'stdout':
                    'phase=ready\nrunner=proot\nversion=1.18.29\npid=12\n',
                'stderr': '',
              };
        });
  });
  tearDown(() {
    debugPlatformCapabilities = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  Future<void> pump(WidgetTester tester, {VoidCallback? onManage}) =>
      tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: SingleChildScrollView(
              child: ManagedServerHealth(onManage: onManage ?? () {}),
            ),
          ),
        ),
      );

  testWidgets(
    'checking is explicit, reads Termux storage, and never starts setup',
    (tester) async {
      await pump(tester);
      expect(calls, isEmpty);
      await tester.tap(find.text('Check status'));
      await tester.pumpAndSettle();
      expect(find.text('Server process running'), findsOneWidget);
      expect(find.text('OpenCode 1.18.29'), findsOneWidget);
      expect(find.textContaining('Last checked at'), findsOneWidget);
      expect(
        find.text('Termux storage: 5.0 GiB free of 10.0 GiB'),
        findsOneWidget,
      );
      expect(calls, hasLength(2));
      expect(calls.first.method, 'runInTermux');
      expect(
        (calls.first.arguments as Map)['script'],
        TermuxBridge.statusScript(),
      );
      expect(
        (calls.last.arguments as Map)['script'],
        TermuxBridge.storageScript(),
      );
    },
  );

  testWidgets(
    'pending check prevents duplicates and permits leaving for setup',
    (tester) async {
      response = Completer();
      var opened = 0;
      await pump(tester, onManage: () => opened++);
      await tester.tap(find.text('Check status'));
      await tester.pump();
      expect(find.text('Checking Termux…'), findsOneWidget);
      await tester.tap(find.text('Check status'));
      await tester.tap(find.text('Open setup controls'));
      expect(opened, 1);
      expect(calls, hasLength(1));
      await tester.pumpWidget(const SizedBox());
      response!.complete({
        'exitCode': 0,
        'stdout': 'phase=stopped\n',
        'stderr': '',
      });
      await tester.pump();
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('failed refresh clears old ready state and hides raw errors', (
    tester,
  ) async {
    await pump(tester);
    await tester.tap(find.text('Check status'));
    await tester.pumpAndSettle();
    response = Completer();
    await tester.tap(find.text('Check status'));
    await tester.pump();
    response!.completeError(
      PlatformException(code: 'broken', message: 'secret fixture'),
    );
    await tester.pumpAndSettle();
    expect(find.text('Server process running'), findsNothing);
    expect(find.textContaining('Could not check Termux'), findsOneWidget);
    expect(find.textContaining('secret fixture'), findsNothing);
  });

  testWidgets('unsupported platform has no controls or bridge calls', (
    tester,
  ) async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    await pump(tester);
    expect(find.text('Check status'), findsNothing);
    expect(calls, isEmpty);
  });
}
