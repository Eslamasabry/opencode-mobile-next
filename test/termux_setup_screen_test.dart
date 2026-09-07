import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/screens/termux_setup_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs});

  final savedProfiles = <ServerProfile>[];
  String? selectedID;
  Completer<void>? pendingSave;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(savedProfiles);

  @override
  String? get activeId => selectedID;

  @override
  Future<void> upsert(ServerProfile profile) async {
    await pendingSave?.future;
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

  int retryCalls = 0;
  int retriesToFail = 0;

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
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

  @override
  Future<void> retryConnection() async {
    retryCalls++;
    if (retryCalls <= retriesToFail) {
      api = null;
      status = StreamStatus.disconnected;
      lastError = 'Server unavailable during lifecycle resume.';
      notifyListeners();
      return;
    }
    api = OpenCodeApi(baseUrl: TermuxBridge.managedServerUrl);
    version = '1.18.21';
    status = StreamStatus.connected;
    notifyListeners();
  }
}

Map<String, Object> _commandResult({String stdout = ''}) => {
  'stdout': stdout,
  'stderr': '',
  'exitCode': 0,
  'err': -1,
  'errorMessage': '',
};

// Gates represent native command acknowledgements, independent of widget time.
class _SetupProgressFixture {
  _SetupProgressFixture(this.store)
    : connection = _LocalConnectionController(store);

  final _MemoryProfileStore store;
  final _LocalConnectionController connection;
  Completer<Map<String, Object>>? pendingBridge;
  Completer<Map<String, Object>>? pendingLaunch;
  Completer<Map<String, Object>>? pendingStatus;
  int launchCalls = 0;
  int restartCalls = 0;
  String? restartOperation;
  String inventoryOutput = 'ubuntu=absent\nversion=\n';
  Completer<Map<String, Object>>? pendingInventory;
  int statusReads = 0;
  bool launched = false;

  Future<Object?> handle(MethodCall call) async {
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
    final script = (call.arguments as Map)['script'] as String;
    if (script.contains('ubuntu=absent')) {
      return pendingInventory?.future ??
          _commandResult(stdout: inventoryOutput);
    }
    if (script.contains("printf 'opencode-bridge-ok'")) {
      return pendingBridge?.future ??
          Future.value(_commandResult(stdout: 'opencode-bridge-ok'));
    }
    if (script.contains('"\$MANAGER" restart')) {
      restartCalls++;
      restartOperation = RegExp(
        r"restart '4096' '([^']+)'",
      ).firstMatch(script)!.group(1);
      return _commandResult();
    }
    if (script.contains('manager_tmp=')) {
      launchCalls++;
      final result =
          await (pendingLaunch?.future ??
              Future.value(_commandResult(stdout: 'manager-started:123')));
      launched = true;
      return result;
    }
    if (script.contains('__OC_SETUP_OUTPUT__') ||
        (script.contains('exec "') && script.contains(' status'))) {
      statusReads++;
      if (launched && pendingStatus != null) return pendingStatus!.future;
      return statusResult();
    }
    return _commandResult();
  }

  Map<String, Object> statusResult() => _commandResult(
    stdout: restartOperation != null
        ? '''phase=ready
message=OpenCode is ready
port=4096
runner=proot
version=1.18.29
pid=123
operation=$restartOperation
operation_result=completed
__OC_SETUP_OUTPUT__
[oc] authenticated server ready
'''
        : launched
        ? '''phase=installing_dependencies
message=Installing Termux dependencies
port=4096
runner=proot
version=
pid=123
__OC_SETUP_OUTPUT__
[oc] Installing packages
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

  Future<void> mount(WidgetTester tester, {double textScale = 1}) async {
    const channel = MethodChannel('oc/termux');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, handle);
    addTearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
      connection.dispose();
    });
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(textScale)),
            child: child!,
          ),
          home: const TermuxSetupScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    statusReads = 0;
  }
}

Future<_SetupProgressFixture> _setupFixture() async {
  SharedPreferences.setMockInitialValues({});
  return _SetupProgressFixture(
    _MemoryProfileStore(prefs: await SharedPreferences.getInstance()),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/termux');

  testWidgets(
    'install shows immediate progress throughout slow native startup',
    (tester) async {
      final fixture = await _setupFixture();
      await fixture.mount(tester);
      fixture.pendingBridge = Completer<Map<String, Object>>();
      fixture.store.pendingSave = Completer<void>();
      fixture.pendingLaunch = Completer<Map<String, Object>>();
      fixture.pendingStatus = Completer<Map<String, Object>>();

      await tester.scrollUntilVisible(find.text('Install & start'), 250);
      await tester.pump();
      await tester.tap(find.text('Install & start'));
      await tester.pump();

      expect(find.text('Preparing setup'), findsOneWidget);
      expect(find.text('Checking Termux connection'), findsOneWidget);
      expect(find.text('0s elapsed'), findsOneWidget);
      expect(find.text('LIVE OUTPUT'), findsOneWidget);
      expect(find.text('Install & start'), findsNothing);
      expect(fixture.launchCalls, 0);
      expect(fixture.statusReads, 0);

      await tester.pump(const Duration(seconds: 21));
      expect(find.text('21s elapsed'), findsOneWidget);
      expect(find.text('Checking Termux connection'), findsOneWidget);
      expect(fixture.statusReads, 0);

      fixture.pendingBridge!.complete(
        _commandResult(stdout: 'opencode-bridge-ok'),
      );
      await tester.pump();
      expect(find.text('Saving local server settings'), findsOneWidget);
      expect(fixture.launchCalls, 0);

      fixture.store.pendingSave!.complete();
      await tester.pump();
      expect(find.text('Starting setup in Termux'), findsOneWidget);
      expect(fixture.launchCalls, 1);
      await tester.pump(const Duration(seconds: 3));
      expect(fixture.launchCalls, 1);
      expect(fixture.statusReads, 0);

      fixture.pendingLaunch!.complete(
        _commandResult(stdout: 'manager-started:123'),
      );
      await tester.pump();
      expect(find.text('Reading setup progress'), findsOneWidget);
      fixture.pendingStatus!.complete(fixture.statusResult());
      await tester.pump();
      expect(find.text('Installing Termux dependencies'), findsOneWidget);
      expect(fixture.launchCalls, 1);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'prelaunch bridge failure stops waiting and offers a working retry',
    (tester) async {
      final fixture = await _setupFixture();
      await fixture.mount(tester);
      fixture.pendingBridge = Completer<Map<String, Object>>();
      await tester.scrollUntilVisible(find.text('Install & start'), 250);
      await tester.pump();
      await tester.tap(find.text('Install & start'));
      await tester.pump();
      fixture.pendingBridge!.completeError(
        PlatformException(
          code: 'bridge_failed',
          message: 'Termux could not answer',
        ),
      );
      await tester.pump();
      expect(find.textContaining('Termux could not answer'), findsOneWidget);
      expect(fixture.launchCalls, 0);
      expect(fixture.store.savedProfiles, isEmpty);

      fixture.pendingBridge = null;
      await tester.ensureVisible(
        find.text('Retry — resumes where setup left off'),
      );
      await tester.pump();
      await tester.tap(find.text('Retry — resumes where setup left off'));
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      expect(fixture.launchCalls, 1);
      expect(find.text('Installing Termux dependencies'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'resuming during launch preserves progress and avoids stale polling',
    (tester) async {
      final fixture = await _setupFixture();
      await fixture.mount(tester);
      fixture.pendingLaunch = Completer<Map<String, Object>>();
      await tester.scrollUntilVisible(find.text('Install & start'), 250);
      await tester.pump();
      await tester.tap(find.text('Install & start'));
      await tester.pump();
      expect(fixture.launchCalls, 1);
      expect(find.text('Starting setup in Termux'), findsOneWidget);

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();
      await tester.pump(const Duration(seconds: 2));
      expect(find.text('Starting setup in Termux'), findsOneWidget);
      expect(find.text('Install & start'), findsNothing);
      expect(fixture.statusReads, 0);
      expect(fixture.launchCalls, 1);

      fixture.pendingLaunch!.complete(
        _commandResult(stdout: 'manager-started:123'),
      );
      await tester.pump();
      expect(find.text('Installing Termux dependencies'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  for (final textScale in [1.0, 2.0]) {
    testWidgets(
      'setup uses phone space without overflow at text scale $textScale',
      (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final fixture = await _setupFixture();
        await fixture.mount(tester, textScale: textScale);
        await tester.scrollUntilVisible(find.text('Install & start'), 250);
        await tester.pump();
        await tester.tap(find.text('Install & start'));
        await tester.pump();
        await tester.pump(const Duration(seconds: 1));
        expect(find.text('LIVE OUTPUT'), findsOneWidget);
        expect(tester.takeException(), isNull);
        if (textScale == 1) {
          final terminal = find.byKey(const Key('setup-live-output'));
          expect(tester.getSize(terminal).height, greaterThanOrEqualTo(300));
          expect(tester.getRect(terminal).bottom, lessThanOrEqualTo(844));
        }
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }

  testWidgets('checks the environment before enabling installation', (
    tester,
  ) async {
    final fixture = await _setupFixture();
    fixture.pendingInventory = Completer<Map<String, Object>>();
    await fixture.mount(tester);
    expect(find.text('Checking installed environment...'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Install & start'), 200);
    await tester.pump();
    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Install & start'),
    );
    expect(button.onPressed, isNull);
    expect(fixture.launchCalls, 0);
    fixture.pendingInventory!.complete(
      _commandResult(stdout: 'ubuntu=absent\nversion=\n'),
    );
    await tester.pump();
    expect(find.text('No managed Ubuntu installation found.'), findsOneWidget);
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Install & start'),
          )
          .onPressed,
      isNotNull,
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('starts the detected installation without downloading packages', (
    tester,
  ) async {
    final fixture = await _setupFixture();
    fixture.inventoryOutput = 'ubuntu=installed\nversion=1.18.29\n';
    fixture.store.savedProfiles.add(
      ServerProfile(
        id: 'local',
        name: 'This device',
        baseUrl: TermuxBridge.managedServerUrl,
        username: 'opencode',
        password: 'synthetic-test-secret',
      ),
    );
    await fixture.mount(tester);
    expect(find.text('Found OpenCode 1.18.29 in Ubuntu'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Start installed OpenCode'), 200);
    await tester.pump();
    await tester.tap(find.text('Start installed OpenCode'));
    await tester.pumpAndSettle();
    expect(fixture.restartCalls, 0);
    await tester.tap(find.text('Start & connect'));
    await tester.pumpAndSettle();
    expect(fixture.restartCalls, 1);
    expect(fixture.launchCalls, 0);
    expect(find.text('Continue to app'), findsOneWidget);
    expect(fixture.store.selectedID, 'local');
    await tester.pumpWidget(const SizedBox.shrink());
  });

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
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: TermuxSetupScreen(),
        ),
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
    var restartCalls = 0;
    var restartShouldFail = false;
    var restartOperation = '';
    var restartResult = '';
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
          if (script.contains('ubuntu=absent')) {
            return _commandResult(stdout: 'ubuntu=absent\nversion=\n');
          }
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
operation=$restartOperation
operation_result=$restartResult
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
          if (script.contains("\"\$MANAGER\" restart '4096' ")) {
            restartCalls++;
            restartOperation = RegExp(
              r"restart '4096' '([^']+)'",
            ).firstMatch(script)!.group(1)!;
            if (restartShouldFail) {
              restartResult = 'not_performed';
              return {
                ..._commandResult(),
                'stderr': 'The installed OpenCode command is unavailable',
                'exitCode': 1,
              };
            }
            restartResult = 'completed';
            launched = true;
            return _commandResult(stdout: '[oc] authenticated server ready');
          }
          if (script.contains('manager_tmp=')) {
            restartOperation = '';
            restartResult = '';
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
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const TermuxSetupScreen(),
          routes: {'/home': (_) => const Scaffold(body: Text('Home'))},
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Install & start'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Install & start'), 250);
    await tester.pump();
    await tester.tap(find.text('Install & start'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(find.text('OpenCode is running on this phone.'), findsOneWidget);
    expect(find.text('Continue to app'), findsOneWidget);
    expect(find.text('Stop local server'), findsOneWidget);
    expect(find.text('Restart local server'), findsOneWidget);
    expect(find.textContaining('Version 1.18.21'), findsOneWidget);

    await tester.ensureVisible(
      find.byKey(const Key('restart-managed-opencode')),
    );
    connection.busySessions.add('busy-session');
    await tester.tap(find.byKey(const Key('restart-managed-opencode')));
    await tester.pumpAndSettle();
    expect(find.text('Restart the local server?'), findsOneWidget);
    expect(
      find.textContaining(
        '1 session is generating. Restarting will interrupt it.',
      ),
      findsOneWidget,
    );
    await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
    await tester.pumpAndSettle();

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

    await tester.ensureVisible(
      find.byKey(const Key('restart-managed-opencode')),
    );
    connection.retriesToFail = 1;
    await tester.tap(find.byKey(const Key('restart-managed-opencode')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Restart'));
    await tester.pumpAndSettle();
    expect(restartCalls, 1);
    expect(connection.retryCalls, 2);
    expect(launchCalls, 1);
    expect(find.text('OpenCode is running on this phone.'), findsOneWidget);

    restartShouldFail = true;
    await tester.tap(find.byKey(const Key('restart-managed-opencode')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Restart'));
    await tester.pumpAndSettle();
    expect(restartCalls, 2);
    expect(connection.retryCalls, 2);
    expect(find.text('OpenCode is running on this phone.'), findsOneWidget);
    restartShouldFail = false;

    await tester.tap(find.byKey(const Key('update-managed-opencode')));
    await tester.pumpAndSettle();
    expect(find.text('Update managed OpenCode?'), findsOneWidget);
    // The dialog names the exact pinned release, not "latest": the user is
    // agreeing to install a specific server version.
    expect(
      find.textContaining(
        'OpenCode ${TermuxBridge.defaultOpenCodeVersion} — the release this '
        'app version is tested against',
      ),
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

  for (final staleReady in [true, false]) {
    testWidgets(
      staleReady
          ? 'restart timeout does not accept a previous ready operation'
          : 'restart timeout follows matching preflight until completion',
      (tester) async {
        SharedPreferences.setMockInitialValues({});
        final store = _MemoryProfileStore(
          prefs: await SharedPreferences.getInstance(),
        );
        final profile = ServerProfile(
          id: 'local',
          name: 'Local',
          baseUrl: TermuxBridge.managedServerUrl,
          username: 'opencode',
          password: 'test-password',
        );
        await store.upsert(profile);
        final connection = _LocalConnectionController(store);
        await connection.connect(profile);
        addTearDown(connection.dispose);
        String? operation;
        var completed = false;
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
              final script = (call.arguments as Map)['script'] as String;
              if (script.contains('ubuntu=absent')) {
                return _commandResult(stdout: 'ubuntu=absent\nversion=\n');
              }
              if (script.contains("printf 'opencode-bridge-ok'")) {
                return _commandResult(stdout: 'opencode-bridge-ok');
              }
              if (script.contains("\"\$MANAGER\" restart '4096' ")) {
                operation = RegExp(
                  r"restart '4096' '([^']+)'",
                ).firstMatch(script)!.group(1)!;
                throw PlatformException(code: 'command_timeout');
              }
              if (script.contains('__OC_SETUP_OUTPUT__')) {
                final preflight =
                    operation != null && !staleReady && !completed;
                return _commandResult(
                  stdout:
                      '''phase=${preflight ? 'restarting' : 'ready'}
message=${preflight ? 'Checking the local server before restart' : 'OpenCode is ready'}
port=4096
runner=proot
version=1.18.21
pid=321
operation=${staleReady ? 'prior-restart' : operation ?? ''}
operation_result=${preflight ? '' : 'completed'}
__OC_SETUP_OUTPUT__
''',
                );
              }
              return _commandResult();
            });
        addTearDown(
          () => TestDefaultBinaryMessengerBinding
              .instance
              .defaultBinaryMessenger
              .setMockMethodCallHandler(channel, null),
        );
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              bootstrapProvider.overrideWithValue(AppBootstrap(store)),
              connProvider.overrideWithValue(connection),
            ],
            child: const MaterialApp(
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: TermuxSetupScreen(),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.ensureVisible(
          find.byKey(const Key('restart-managed-opencode')),
        );
        await tester.tap(find.byKey(const Key('restart-managed-opencode')));
        await tester.pumpAndSettle();
        await tester.tap(find.widgetWithText(FilledButton, 'Restart'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 100));
        expect(operation, isNotNull);
        expect(connection.retryCalls, 0);
        expect(
          find.text('Local server restarted and reconnected.'),
          findsNothing,
        );
        if (staleReady) {
          expect(
            find.textContaining('Could not restart the local server'),
            findsOneWidget,
          );
        } else {
          expect(
            find.textContaining('Checking the local server before restart'),
            findsOneWidget,
          );
          completed = true;
          await tester.pump(const Duration(seconds: 1));
          await tester.pumpAndSettle();
          expect(connection.retryCalls, 1);
          expect(
            find.text('OpenCode is running on this phone.'),
            findsOneWidget,
          );
        }
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }

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
          if (script.contains('ubuntu=absent')) {
            return _commandResult(stdout: 'ubuntu=absent\nversion=\n');
          }
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
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: TermuxSetupScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    await tester.scrollUntilVisible(find.text('Install & start'), 250);
    await tester.pump();
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
    expect(find.text('Retry — resumes where setup left off'), findsOneWidget);
  });
}
