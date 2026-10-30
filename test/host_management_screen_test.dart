import 'package:opencode_mobile/domain/server_gateway.dart';
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'support/fake_event_channel.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/host_management_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/setup_commands.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _HealthyApi extends FakeServerGateway {
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

/// Servers › the server › Status and version (and the Linux service row
/// beside it on that page).
Future<void> _openServerCategory(WidgetTester tester) async {
  final hub = tester.widget<SettingsScreen>(find.byType(SettingsScreen));
  unawaited(
    openServerPage(
      tester.element(find.byType(SettingsScreen)),
      hub.controller,
      hub.controller.profile!.id,
    ),
  );
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
    final controller = await _controllerFor('http://192.0.2.20:4747');
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
    final entry = find.byKey(const Key('host-management-entry'));
    await tester.ensureVisible(entry);
    await tester.pump();
    await tester.ensureVisible(entry);
    await tester.tap(entry);
    await tester.pumpAndSettle();

    expect(find.byType(HostManagementScreen), findsOneWidget);
    // Titled with the server; one line says where the commands run, and
    // no "This server" group repeats the server's address and version.
    expect(find.text('OpenCode on Dev workstation'), findsOneWidget);
    expect(
      find.text(
        "These commands run on Dev workstation's computer; copy each into "
        'a terminal there.',
      ),
      findsOneWidget,
    );
    expect(find.text('This server'), findsNothing);
    expect(find.text('http://192.0.2.20:4747'), findsNothing);
    // The setup command carries the profile's exact port.
    expect(find.textContaining('--port 4747'), findsOneWidget);
    await tester.drag(find.byType(ListView).last, const Offset(0, -800));
    await tester.pumpAndSettle();
    expect(find.textContaining('ubuntu-opencode.sh'), findsNothing);
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

  testWidgets(
    'install copies the pinned OC2 command without a retired helper',
    (tester) async {
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
      final controller = await _controllerFor('http://192.0.2.20:4747');
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(home: HostManagementScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('ubuntu-opencode.sh'), findsNothing);
      final copy = find.byKey(
        const ValueKey('copy-host-command-Install and start OpenCode 2'),
      );
      await tester.ensureVisible(copy);
      await tester.pump();
      await tester.ensureVisible(copy);
      await tester.tap(copy);
      await tester.pumpAndSettle();
      expect(copiedText, HostScripts.install(4747));
      expect(copiedText, contains('@opencode/cli@2.1.1'));
      expect(
        copiedText,
        contains('opencode2 serve --hostname 127.0.0.1 --port 4747'),
      );
    },
  );

  testWidgets('host management fits a 320dp phone at 2x text', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controllerFor('http://192.0.2.20:4747');
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
