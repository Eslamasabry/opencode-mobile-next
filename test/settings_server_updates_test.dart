import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
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
    name: 'OpenCode server',
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

void main() {
  testWidgets('managed local profile opens the in-app updater', (tester) async {
    final controller = await _controllerFor('http://127.0.0.1:4096');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: SettingsScreen(controller: controller),
        routes: {
          '/termux-setup': (_) => const Scaffold(body: Text('Managed updater')),
        },
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Update managed OpenCode'), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('server-updates-tile')));
    await tester.tap(find.byKey(const Key('server-updates-tile')));
    await tester.pumpAndSettle();
    expect(find.text('Managed updater'), findsOneWidget);
  });

  testWidgets('remote profile clearly remains externally managed', (
    tester,
  ) async {
    final controller = await _controllerFor('http://100.64.0.10:4747');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Server updates are managed externally'), findsOneWidget);
    expect(
      find.textContaining('official upgrade and model-refresh commands'),
      findsOneWidget,
    );
  });
}
