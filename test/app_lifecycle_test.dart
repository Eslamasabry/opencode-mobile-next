import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _LifecycleConnection extends ConnectionController {
  _LifecycleConnection(super.store);

  int suspends = 0;
  int resumes = 0;

  @override
  void suspendForLifecycle() {
    suspends++;
  }

  @override
  Future<void> resumeFromLifecycle() async {
    resumes++;
  }
}

class _HomeRepository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('app lifecycle suspends and resumes the shared transport', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final connection = _LifecycleConnection(store);
    addTearDown(connection.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
        ],
        child: const OcApp(),
      ),
    );

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    await tester.pump();
    expect(connection.suspends, 0);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    expect(connection.suspends, 1);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(connection.resumes, 1);
  });

  testWidgets('background suspension keeps Home on its current route', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final connection = ConnectionController(store)
      ..api = OpenCodeApi(baseUrl: 'http://localhost')
      ..repository = _HomeRepository()
      ..version = 'test'
      ..status = StreamStatus.connected;
    addTearDown(connection.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(connection)],
        child: MaterialApp(
          home: const HomeScreen(),
          routes: {'/servers': (_) => const ServersScreen()},
        ),
      ),
    );
    await tester.pump();

    connection.suspendForLifecycle();
    await tester.pumpAndSettle();

    expect(connection.lifecycleSuspended, isTrue);
    expect(find.byType(HomeScreen), findsOneWidget);
    expect(find.byType(ServersScreen), findsNothing);
  });
}
