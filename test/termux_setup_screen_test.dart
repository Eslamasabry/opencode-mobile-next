import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/termux_setup_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs});

  final savedProfiles = <ServerProfile>[];
  String? selectedID;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(savedProfiles);

  @override
  String? get activeId => selectedID;

  @override
  Future<void> upsert(ServerProfile profile) async {
    final index = savedProfiles.indexWhere((item) => item.id == profile.id);
    if (index == -1) {
      savedProfiles.add(profile);
    } else {
      savedProfiles[index] = profile;
    }
  }

  @override
  Future<void> setActiveId(String? id) async {
    selectedID = id;
  }
}

class _LocalConnectionController extends ConnectionController {
  _LocalConnectionController(super.store);

  @override
  Future<void> connect(ServerProfile profile) async {
    await store.setActiveId(profile.id);
    api = OpenCodeApi(baseUrl: profile.baseUrl);
    version = '1.18.21';
    status = StreamStatus.connected;
    notifyListeners();
  }

  @override
  Future<void> disconnect({
    bool keepActive = false,
    bool silent = false,
  }) async {
    api = null;
    version = null;
    status = StreamStatus.disconnected;
    if (!keepActive) await store.setActiveId(null);
    if (!silent) notifyListeners();
  }
}

Map<String, Object> _commandResult({String stdout = ''}) => {
  'stdout': stdout,
  'stderr': '',
  'exitCode': 0,
  'err': -1,
  'errorMessage': '',
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/termux');

  testWidgets('checking Termux does not mark the download step complete', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = _MemoryProfileStore(
      prefs: await SharedPreferences.getInstance(),
    );
    final connection = _LocalConnectionController(store);
    addTearDown(connection.dispose);
    final capabilities = Completer<Map<String, Object?>>();

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'getCapabilities') return capabilities.future;
          return _commandResult();
        });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
        ],
        child: const MaterialApp(home: TermuxSetupScreen()),
      ),
    );
    await tester.pump();

    expect(find.text('Checking Termux...'), findsOneWidget);
    expect(find.byIcon(Icons.check_rounded), findsNothing);

    capabilities.complete({
      'installed': false,
      'version': null,
      'serviceAvailable': false,
      'protocolSupported': false,
      'permissionGranted': false,
    });
    await tester.pump();
  });

  testWidgets('successful setup exposes continue and normal stop controls', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = _MemoryProfileStore(
      prefs: await SharedPreferences.getInstance(),
    );
    final connection = _LocalConnectionController(store);
    addTearDown(connection.dispose);
    var launched = false;
    var launchCalls = 0;
    var stopCalls = 0;

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'getCapabilities') {
            return <String, Object>{
              'installed': true,
              'version': '0.118',
              'serviceAvailable': true,
              'protocolSupported': true,
              'permissionGranted': true,
            };
          }
          if (call.method != 'runInTermux') return true;
          final arguments = call.arguments as Map<Object?, Object?>;
          final script = arguments['script']! as String;
          if (script.contains("printf 'opencode-bridge-ok'")) {
            return _commandResult(stdout: 'opencode-bridge-ok');
          }
          if (script.contains('__OC_SETUP_OUTPUT__')) {
            return _commandResult(
              stdout: launched
                  ? '''phase=ready
message=OpenCode is ready
port=4096
runner=proot
version=1.18.21
pid=321
__OC_SETUP_OUTPUT__
[oc] authenticated server ready
'''
                  : '''phase=idle
message=No setup has been started
port=4096
runner=
version=
pid=
__OC_SETUP_OUTPUT__
''',
            );
          }
          if (script.contains("manager.sh\" stop '4096'")) {
            launched = false;
            stopCalls++;
            return _commandResult(stdout: '[oc] server stopped');
          }
          if (script.contains('manager_tmp=')) {
            launched = true;
            launchCalls++;
            return _commandResult(stdout: 'manager-started:123');
          }
          if (script.contains('exec "') && script.contains(' status')) {
            return _commandResult(
              stdout: launched
                  ? '''phase=ready
message=OpenCode is ready
port=4096
runner=proot
version=1.18.21
pid=321
'''
                  : '''phase=idle
message=No setup has been started
port=4096
runner=
version=
pid=
''',
            );
          }
          return _commandResult();
        });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
        ],
        child: MaterialApp(
          home: const TermuxSetupScreen(),
          routes: {'/home': (_) => const Scaffold(body: Text('Home'))},
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Install & start'), findsOneWidget);
    await tester.ensureVisible(find.text('Install & start'));
    await tester.tap(find.text('Install & start'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(find.text('OpenCode is running on this phone.'), findsOneWidget);
    expect(find.text('Continue to app'), findsOneWidget);
    expect(find.text('Stop local server'), findsOneWidget);
    expect(find.textContaining('Version 1.18.21'), findsOneWidget);

    await tester.ensureVisible(
      find.byKey(const Key('update-managed-opencode')),
    );
    connection.busySessions.add('busy-session');
    await tester.tap(find.byKey(const Key('update-managed-opencode')));
    await tester.pump();
    expect(
      find.text('Stop active generation before updating OpenCode.'),
      findsOneWidget,
    );
    expect(launchCalls, 1);
    connection.busySessions.clear();

    await tester.tap(find.byKey(const Key('update-managed-opencode')));
    await tester.pumpAndSettle();
    expect(find.text('Update managed OpenCode?'), findsOneWidget);
    expect(
      find.textContaining('latest stable OpenCode release'),
      findsOneWidget,
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Update'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(launchCalls, 2);
    expect(find.text('OpenCode is running on this phone.'), findsOneWidget);

    await tester.ensureVisible(find.text('Stop local server'));
    await tester.tap(find.text('Stop local server'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(stopCalls, 1);
    expect(connection.api, isNull);
    expect(find.textContaining('local server is stopped'), findsOneWidget);
    expect(find.text('Install & start'), findsOneWidget);
  });

  testWidgets('launch timeout recovers the persisted phase and root cause', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = _MemoryProfileStore(
      prefs: await SharedPreferences.getInstance(),
    );
    final connection = _LocalConnectionController(store);
    addTearDown(connection.dispose);
    var launchTimedOut = false;

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'getCapabilities') {
            return <String, Object>{
              'installed': true,
              'version': '0.118',
              'serviceAvailable': true,
              'protocolSupported': true,
              'permissionGranted': true,
            };
          }
          if (call.method != 'runInTermux') return true;
          final arguments = call.arguments as Map<Object?, Object?>;
          final script = arguments['script']! as String;
          if (script.contains("printf 'opencode-bridge-ok'")) {
            return _commandResult(stdout: 'opencode-bridge-ok');
          }
          if (script.contains('manager_tmp=')) {
            launchTimedOut = true;
            throw PlatformException(
              code: 'command_timeout',
              message:
                  'Termux did not return a command result within 30 seconds.',
            );
          }
          if (script.contains('__OC_SETUP_OUTPUT__')) {
            return _commandResult(
              stdout: launchTimedOut
                  ? '''phase=failed
message=Repairing the Termux package set failed (exit 127; setup line 812)
port=4096
runner=proot
version=
pid=
__OC_SETUP_OUTPUT__
CANNOT LINK EXECUTABLE "curl": cannot locate symbol "SSL_set_quic_tls_transport_params"
'''
                  : '''phase=idle
message=No setup has been started
port=4096
runner=
version=
pid=
__OC_SETUP_OUTPUT__
''',
            );
          }
          return _commandResult();
        });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
        ],
        child: const MaterialApp(home: TermuxSetupScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    await tester.ensureVisible(find.text('Install & start'));
    await tester.tap(find.text('Install & start'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(
      find.textContaining('Repairing the Termux package set failed'),
      findsOneWidget,
    );
    expect(
      find.textContaining('SSL_set_quic_tls_transport_params'),
      findsWidgets,
    );
    expect(find.text('Stop & retry'), findsOneWidget);
  });
}
