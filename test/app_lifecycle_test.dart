import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/builtin_server_recovery.dart';
import 'package:opencode_mobile/builtin/phone_server_healing.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
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

class _PhoneConnection extends _LifecycleConnection {
  _PhoneConnection(super.store, this.phone);
  final ServerProfile phone;

  @override
  ServerProfile? get profile => phone;
}

class _SlowHealing extends PhoneServerHealing {
  _SlowHealing({required super.connection, required super.starter})
    : super(
        createRecovery: (record) => BuiltinServerRecovery(
          store: connection.store,
          linux: BuiltinLinux(),
          starter: starter,
          onRestart: record,
        ),
      );

  final gate = Completer<void>();

  @override
  Future<void> check(ServerProfile profile) => gate.future;
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

  testWidgets('resume reconnects a live phone server while healing still '
      'checks', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final phone = ServerProfile(
      id: 'phone',
      name: 'This phone',
      baseUrl: BuiltinLinux.serverUrl,
      username: BuiltinLinux.serverUsername,
    );
    final connection = _PhoneConnection(store, phone);
    final starter = BuiltinServerStarter(linux: BuiltinLinux());
    final healing = _SlowHealing(connection: connection, starter: starter);
    addTearDown(() {
      starter.dispose();
      connection.dispose();
    });

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
          phoneServerHealingProvider.overrideWithValue(healing),
        ],
        child: const OcApp(),
      ),
    );
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    // The healing check is still pending, yet the transport already resumes.
    expect(connection.resumes, 1);
    healing.dispose();
  });

  testWidgets('app restores and applies appearance without a restart', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({'oc.appearance': 'light'});
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final connection = ConnectionController(store);
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
    await tester.pump();

    expect(
      tester.widget<MaterialApp>(find.byType(MaterialApp)).themeMode,
      ThemeMode.light,
    );

    await connection.setAppearance(AppAppearance.dark);
    await tester.pump();

    expect(
      tester.widget<MaterialApp>(find.byType(MaterialApp)).themeMode,
      ThemeMode.dark,
    );
  });

  testWidgets('background suspension keeps Home on its current route', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final connection = ConnectionController(store)
      ..api = FakeServerGateway(baseUrl: 'http://localhost')
      ..repository = _HomeRepository()
      ..version = 'test'
      ..status = StreamStatus.connected;
    addTearDown(connection.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(connection)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
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

  test('opted-in background mode keeps the shared transport active', () async {
    SharedPreferences.setMockInitialValues({
      BackgroundLiveController.preferenceKey: true,
    });
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final backgroundLive = BackgroundLiveController(
      preferences: store.prefs,
      invoke: (method, [arguments]) async {
        if (method == 'getBackgroundPause') {
          return const {
            'supported': true,
            'active': false,
            'paused': false,
            'reason': 'none',
            'at': null,
            'canResume': false,
          };
        }
        return const {
          'enabled': true,
          'active': true,
          'notificationGranted': true,
          'batteryOptimizationIgnored': false,
        };
      },
    );
    final connection = ConnectionController(
      store,
      backgroundLive: backgroundLive,
    )..status = StreamStatus.connected;
    addTearDown(connection.dispose);

    connection.suspendForLifecycle();

    expect(connection.lifecycleSuspended, isFalse);
    expect(connection.status, StreamStatus.connected);
  });
}
