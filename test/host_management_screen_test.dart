import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/host_management_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _HealthyApi extends OpenCodeApi {
  _HealthyApi() : super(baseUrl: 'http://127.0.0.1:4096');

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.23');
}

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs, required this.savedProfile});

  final ServerProfile savedProfile;
  String? selectedID;

  @override
  List<ServerProfile> get profiles => [savedProfile];

  @override
  String? get activeId => selectedID;

  @override
  Future<void> setActiveId(String? id) async => selectedID = id;
}

Future<ConnectionController> _controllerFor(String baseUrl) async {
  SharedPreferences.setMockInitialValues({});
  final profile = ServerProfile(
    id: 'server',
    name: 'Dev workstation',
    baseUrl: baseUrl,
  );
  final store = _MemoryProfileStore(
    prefs: await SharedPreferences.getInstance(),
    savedProfile: profile,
  );
  await store.setActiveId(profile.id);
  return ConnectionController(store)
    ..api = _HealthyApi()
    ..version = '1.18.23'
    ..status = StreamStatus.connected;
}

Future<void> _openServerCategory(WidgetTester tester) async {
  final row = find.byKey(const ValueKey('settings-category-server'));
  await tester.ensureVisible(row);
  await tester.tap(row);
  await tester.pumpAndSettle();
}

void main() {
  test('the ubuntu host script passes bash syntax validation', () {
    final result = Process.runSync('bash', [
      '-n',
      'scripts/host/ubuntu-opencode.sh',
    ]);
    expect(result.exitCode, 0, reason: '${result.stderr}');
  });

  testWidgets('remote profiles expose the host management destination', (
    tester,
  ) async {
    final controller = await _controllerFor('http://192.168.1.20:4747');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await _openServerCategory(tester);

    await tester.scrollUntilVisible(
      find.byKey(const Key('host-management-entry')),
      240,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.byKey(const Key('host-management-entry')));
    await tester.pumpAndSettle();

    expect(find.byType(HostManagementScreen), findsOneWidget);
    expect(
      find.textContaining('the app cannot run them for you'),
      findsOneWidget,
    );
    // The setup command carries the profile's exact port.
    expect(find.textContaining('OPENCODE_PORT=4747'), findsOneWidget);
    await tester.drag(find.byType(ListView).last, const Offset(0, -800));
    await tester.pumpAndSettle();
    expect(find.textContaining('ubuntu-opencode.sh status'), findsOneWidget);
  });

  testWidgets('the managed loopback profile hides host management', (
    tester,
  ) async {
    final controller = await _controllerFor('http://127.0.0.1:4096');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await _openServerCategory(tester);

    expect(find.byKey(const Key('host-management-entry')), findsNothing);
  });

  testWidgets('copy buttons place the exact command on the clipboard', (
    tester,
  ) async {
    String? copiedText;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copiedText = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    final controller = await _controllerFor('http://192.168.1.20:4747');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(home: HostManagementScreen(controller: controller)),
    );
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('copy-host-command-Restart the server')),
      240,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(
      find.byKey(const ValueKey('copy-host-command-Restart the server')),
    );
    await tester.pumpAndSettle();

    expect(copiedText, 'bash ubuntu-opencode.sh restart');
    expect(
      find.text("Copied. Run it on the server's computer."),
      findsOneWidget,
    );
  });

  testWidgets('host management fits a 320dp phone at 2x text', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controllerFor('http://192.168.1.20:4747');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
          child: child!,
        ),
        home: HostManagementScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();

    await tester.drag(find.byType(ListView), const Offset(0, -600));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
