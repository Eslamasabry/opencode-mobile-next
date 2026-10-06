import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/local_server_controls.dart';
import 'package:opencode_mobile/state/termux_running_server.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/termux_reach.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/widgets/termux_running_server_entry.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Connection extends ConnectionController {
  _Connection(super.store);
  final attempted = <ServerProfile>[];
  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    attempted.add(profile);
    api = OpenCodeApi(baseUrl: profile.baseUrl);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/termux');
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final calls = <MethodCall>[];
  final probes = <String>[];
  final secrets = <String, String>{};
  final oldProbe = termuxRunningServerProbe;
  late Map<String, Object> capabilities;
  late String status;
  late ServerProbeResult health;
  final local = ServerProfile(
    id: 'local',
    name: 'Phone',
    baseUrl: TermuxBridge.managedServerUrl,
    password: 'synthetic-local',
  );
  final remote = ServerProfile(
    id: 'remote',
    name: 'Work server',
    baseUrl: 'https://work.example.test',
    password: 'synthetic-remote',
  );

  setUp(() {
    calls.clear();
    probes.clear();
    secrets.clear();
    debugPlatformCapabilities = const PlatformCapabilities.android();
    capabilities = {
      'installed': true,
      'serviceAvailable': true,
      'protocolSupported': true,
      'permissionGranted': true,
    };
    status =
        'phase=ready\nport=4096\nruntime=opencode1\nversion=1.18.29\npid=12\n';
    health = const ServerProbeResult.success('1.18.29');
    termuxRunningServerProbe =
        ({required baseUrl, username, password, cancellation}) async {
          probes.add(baseUrl);
          expect(baseUrl, TermuxBridge.managedServerUrl);
          expect(password, isNot('synthetic-remote'));
          return health;
        };
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      if (call.method == 'getCapabilities') return capabilities;
      expect(call.method, 'runInTermux');
      expect((call.arguments as Map)['script'], TermuxBridge.statusScript());
      return {'exitCode': 0, 'stdout': status, 'stderr': ''};
    });
    messenger.setMockMethodCallHandler(secure, (call) async {
      final args = call.arguments as Map;
      final key = args['key'] as String?;
      switch (call.method) {
        case 'write':
          secrets[key!] = args['value'] as String;
          return null;
        case 'read':
          return secrets[key];
        case 'delete':
          secrets.remove(key);
          return null;
        case 'readAll':
          return secrets;
        case 'containsKey':
          return secrets.containsKey(key);
        case 'deleteAll':
          secrets.clear();
          return null;
      }
      return null;
    });
  });
  tearDown(() {
    debugPlatformCapabilities = null;
    termuxRunningServerProbe = oldProbe;
    messenger.setMockMethodCallHandler(channel, null);
    messenger.setMockMethodCallHandler(secure, null);
  });

  test(
    'ready status requires live loopback response, not just saved manager state',
    () async {
      final observed = await detectTermuxRunningServer(
        profiles: [remote, local],
      );
      expect(observed.isRunning, isTrue);
      expect(
        savedProfileForTermuxServer([remote, local], observed),
        same(local),
      );
      expect(probes, [TermuxBridge.managedServerUrl]);
      health = const ServerProbeResult.failure('unreachable');
      expect(
        (await detectTermuxRunningServer()).state,
        TermuxRunningServerState.unavailable,
      );
    },
  );

  test(
    'permission denied sends no command; one look at the app-authored loopback says whether OpenCode answers',
    () async {
      capabilities['permissionGranted'] = false;
      final observed = await detectTermuxRunningServer();
      expect(observed.state, TermuxRunningServerState.denied);
      expect(observed.problem, TermuxProblem.accessNeeded);
      expect(observed.heardOnPhone, isTrue);
      expect(calls.map((call) => call.method), ['getCapabilities']);
      expect(probes, [TermuxBridge.managedServerUrl]);
      health = const ServerProbeResult.failure('refused');
      expect((await detectTermuxRunningServer()).heardOnPhone, isFalse);
    },
  );

  test(
    'missing Termux and a stopped or switching runtime are not running',
    () async {
      capabilities['installed'] = false;
      expect((await detectTermuxRunningServer()).isRunning, isFalse);
      capabilities['installed'] = true;
      for (final phase in [
        'idle',
        'stopped',
        'failed',
        'installing_opencode',
      ]) {
        status = 'phase=$phase\n';
        expect((await detectTermuxRunningServer()).isRunning, isFalse);
      }
      status =
          'phase=ready\nswitch_previous=opencode1\nswitch_target=opencode2\n';
      expect((await detectTermuxRunningServer()).isRunning, isFalse);
      expect(probes, isEmpty);
    },
  );

  test('unknown port is never scanned', () async {
    status = 'phase=ready\nport=9876\n';
    expect((await detectTermuxRunningServer()).isRunning, isFalse);
    expect(probes, isEmpty);
  });

  test(
    'authentication challenge is observed without claiming authenticated health',
    () async {
      health = const ServerProbeResult.failure(
        'password required',
        needsPassword: true,
      );
      final observed = await detectTermuxRunningServer(profiles: [remote]);
      expect(observed.isRunning, isTrue);
      expect(observed.needsCredentials, isTrue);
      expect(savedProfileForTermuxServer([remote], observed), isNull);
    },
  );

  test('web, iOS and desktop never touch the native bridge', () async {
    for (final platform in [
      const PlatformCapabilities(platform: TargetPlatform.android, isWeb: true),
      const PlatformCapabilities(platform: TargetPlatform.iOS),
      const PlatformCapabilities.linuxDesktop(),
    ]) {
      debugPlatformCapabilities = platform;
      expect(
        (await detectTermuxRunningServer()).state,
        TermuxRunningServerState.unsupported,
      );
    }
    expect(calls, isEmpty);
    expect(probes, isEmpty);
  });

  Future<void> entry(
    WidgetTester tester, {
    List<ServerProfile>? profiles,
    ValueChanged<ServerProfile>? onConnect,
    void Function(TermuxRunningServer, ServerProfile?)? onCredentials,
    double textScale = 1,
    LocalServerCardActions? actions,
    String? connectedProfileID,
    Future<void> Function()? onDisconnect,
    ValueChanged<ServerProfile>? onForget,
    VoidCallback? onManage,
    int busyConversations = 0,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: RepaintBoundary(
            key: const ValueKey('capture'),
            child: Builder(
              builder: (context) => MediaQuery(
                // Preserve the simulated viewport for KitRow's trailing bound.
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(textScale)),
                child: SingleChildScrollView(
                  child: TermuxRunningServerEntry(
                    profiles: profiles ?? [remote, local],
                    busy: false,
                    revision: 0,
                    onConnect: onConnect ?? (_) {},
                    onEnterCredentials: onCredentials ?? (_, _) {},
                    actions: actions,
                    connectedProfileID: connectedProfileID,
                    onDisconnect: onDisconnect,
                    onForget: onForget,
                    onManage: onManage,
                    busyConversations: busyConversations,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  for (final stage in ['capabilities', 'status', 'probe']) {
    for (final replyAfterDispose in [false, true]) {
      testWidgets(
        'dispose during $stage cancels deadlines (late reply: $replyAfterDispose)',
        (tester) async {
          final pendingNative = Completer<Map<String, Object>>();
          final pendingProbe = Completer<ServerProbeResult>();
          messenger.setMockMethodCallHandler(channel, (call) async {
            calls.add(call);
            if (call.method == 'getCapabilities') {
              return stage == 'capabilities'
                  ? pendingNative.future
                  : capabilities;
            }
            expect(call.method, 'runInTermux');
            expect(
              (call.arguments as Map)['script'],
              TermuxBridge.statusScript(),
            );
            if (stage == 'status') return pendingNative.future;
            return {'exitCode': 0, 'stdout': status, 'stderr': ''};
          });
          termuxRunningServerProbe =
              ({required baseUrl, username, password, cancellation}) async {
                probes.add(baseUrl);
                return pendingProbe.future;
              };
          await entry(tester);
          expect(calls.length, stage == 'capabilities' ? 1 : 2);
          expect(probes.length, stage == 'probe' ? 1 : 0);
          await tester.pumpWidget(const SizedBox());
          await tester.pump();
          if (replyAfterDispose) {
            if (stage == 'capabilities') {
              pendingNative.complete(capabilities);
            } else if (stage == 'status') {
              pendingNative.complete({
                'exitCode': 0,
                'stdout': status,
                'stderr': '',
              });
            } else {
              pendingProbe.complete(health);
            }
            await tester.pump();
            await tester.pump();
            expect(calls.length, stage == 'capabilities' ? 1 : 2);
            expect(probes.length, stage == 'probe' ? 1 : 0);
          }
          expect(tester.takeException(), isNull);
          // When replyAfterDispose is false, leave the platform/probe future
          // unresolved. The widget-test pending-timer invariant proves disposal
          // cancels deadlines without waiting for the native reply or timeout.
        },
      );
    }
  }

  testWidgets(
    'visible connect is explicit, rechecks, and never starts or switches runtime',
    (tester) async {
      final connected = <ServerProfile>[];
      await entry(tester, onConnect: connected.add);
      expect(
        find.byKey(const ValueKey('termux-running-server-connect')),
        findsOneWidget,
      );
      expect(connected, isEmpty);
      expect(find.textContaining('1.18.29'), findsNothing);
      await tester.tap(
        find.byKey(const ValueKey('termux-running-server-connect')),
      );
      await tester.pumpAndSettle();
      expect(connected, [local]);
      expect(probes, hasLength(2));
    },
  );

  testWidgets(
    'missing or rejected credentials go straight to credential flow',
    (tester) async {
      health = const ServerProbeResult.failure(
        'password required',
        needsPassword: true,
      );
      ServerProfile? requested;
      var opened = 0;
      await entry(
        tester,
        onCredentials: (_, profile) {
          opened++;
          requested = profile;
        },
      );
      await tester.tap(
        find.byKey(const ValueKey('termux-running-server-connect')),
      );
      await tester.pumpAndSettle();
      expect(opened, 1);
      expect(requested, same(local));
      await tester.pumpWidget(const SizedBox());
      await entry(
        tester,
        profiles: [remote],
        onCredentials: (_, profile) {
          opened++;
          requested = profile;
        },
      );
      await tester.tap(
        find.byKey(const ValueKey('termux-running-server-connect')),
      );
      await tester.pumpAndSettle();
      expect(opened, 2);
      expect(requested, isNull);
    },
  );

  testWidgets(
    'stale observation cannot connect a server that stopped before tap',
    (tester) async {
      var connected = 0;
      await entry(tester, onConnect: (_) => connected++);
      status = 'phase=stopped\n';
      await tester.tap(
        find.byKey(const ValueKey('termux-running-server-connect')),
      );
      await tester.pumpAndSettle();
      expect(connected, 0);
      expect(
        find.byKey(const ValueKey('termux-running-server-connect')),
        findsNothing,
      );
    },
  );

  testWidgets('resume removes stale running entry', (tester) async {
    await entry(tester);
    status = 'phase=stopped\n';
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('termux-running-server-connect')),
      findsNothing,
    );
  });

  testWidgets(
    'denied and unavailable explain uncertainty without a connect claim',
    (tester) async {
      capabilities['permissionGranted'] = false;
      await entry(tester);
      expect(
        find.textContaining(
          'OpenCode is running in Termux, but this app can\'t reach Termux yet.',
        ),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('termux-running-server-fix')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('termux-running-server-connect')),
        findsNothing,
      );
      capabilities['permissionGranted'] = true;
      health = const ServerProbeResult.failure('unreachable');
      // No expect inside the handler: it would run inside pumpAndSettle.
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'getCapabilities') return capabilities;
        return {'exitCode': 0, 'stdout': status, 'stderr': ''};
      });
      // Coming back to the app reads the phone again.
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await tester.pumpAndSettle();
      expect(
        find.textContaining(
          'OpenCode 1 is set up in Termux but isn\'t answering.',
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'server list retains remote profiles and connects local only on tap',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      await store.upsert(remote);
      await store.upsert(local);
      final connection = _Connection(store);
      addTearDown(connection.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(connection),
          ],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            routes: {
              '/home': (_) => const Scaffold(body: Text('Connected home')),
            },
            home: const ServersScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('termux-running-server-connect')),
        findsOneWidget,
      );
      expect(connection.attempted, isEmpty);
      final before = jsonEncode(remote.toJson());
      await tester.tap(
        find.byKey(const ValueKey('termux-running-server-connect')),
      );
      await tester.pumpAndSettle();
      expect(connection.attempted, [local]);
      expect(store.profiles, hasLength(2));
      expect(jsonEncode(remote.toJson()), before);
      expect(find.text('Connected home'), findsOneWidget);
    },
  );

  testWidgets('a running phone server leads the first-run question', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final connection = _Connection(store);
    addTearDown(connection.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const ServersScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final card = find.byKey(const ValueKey('termux-lead'));
    expect(card, findsOneWidget);
    // A live server the app found leads the page: no welcome hero and no
    // question; the other ways follow it, compact.
    expect(find.byKey(const ValueKey('servers-welcome-hero')), findsNothing);
    expect(find.byKey(const ValueKey('welcome-question')), findsNothing);
    final cardBottom = tester.getRect(card).bottom;
    expect(
      tester.getRect(find.byKey(const ValueKey('welcome-choice-in-app'))).top,
      greaterThan(cardBottom),
    );
    expect(
      tester.getRect(find.byKey(const ValueKey('welcome-choice-computer'))).top,
      greaterThan(cardBottom),
    );
  });

  testWidgets(
    'a running phone server whose password the app lost is restored from the phone',
    (tester) async {
      // A reinstall or cleared app data: the server still runs, the app has no
      // saved server for it, and the person was never shown the password.
      SharedPreferences.setMockInitialValues({});
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final connection = _Connection(store);
      addTearDown(connection.dispose);
      TermuxBridge.managedServerPasswordOverride = () async =>
          'the-password-setup-wrote';
      addTearDown(() => TermuxBridge.managedServerPasswordOverride = null);
      health = const ServerProbeResult.failure(
        'password required',
        needsPassword: true,
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
            routes: {'/home': (_) => const Scaffold(body: Text('home'))},
            home: const ServersScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final connect = find.byKey(const ValueKey('termux-lead-connect'));
      await tester.ensureVisible(connect);
      await tester.tap(connect);
      await tester.pumpAndSettle();

      // No password is asked for: the saved server comes back and connects.
      expect(find.byKey(const ValueKey('server-password-field')), findsNothing);
      final restored = store.profiles.single;
      expect(restored.baseUrl, TermuxBridge.managedServerUrl);
      expect(restored.username, 'opencode');
      expect(restored.password, 'the-password-setup-wrote');
      expect(connection.attempted.single.id, restored.id);
    },
  );

  testWidgets(
    'welcome running entry opens password editor with authored local URL',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final connection = _Connection(store);
      addTearDown(connection.dispose);
      health = const ServerProbeResult.failure(
        'password required',
        needsPassword: true,
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
            home: const ServersScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final connect = find.byKey(const ValueKey('termux-lead-connect'));
      await tester.ensureVisible(connect);
      await tester.tap(connect);
      await tester.pumpAndSettle();
      final url = tester.widget<TextFormField>(
        find.byKey(const ValueKey('server-url-field')),
      );
      expect(url.controller!.text, TermuxBridge.managedServerUrl);
      expect(
        find.byKey(const ValueKey('server-password-field')),
        findsOneWidget,
      );
      expect(store.profiles, isEmpty);
      expect(connection.attempted, isEmpty);
    },
  );

  for (final scale in [1.0, 2.0]) {
    testWidgets('running entry fits a narrow phone at text scale $scale', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await entry(tester, textScale: scale);
      expect(tester.takeException(), isNull);
      final connect = find.byKey(
        const ValueKey('termux-running-server-connect'),
      );
      await tester.ensureVisible(connect);
      expect(tester.getSize(connect).height, greaterThanOrEqualTo(48));
      const captureDirectory = String.fromEnvironment(
        'TERMUX_ENTRY_CAPTURE_DIR',
      );
      if (captureDirectory.isNotEmpty) {
        final boundary = tester.renderObject<RenderRepaintBoundary>(
          find.byKey(const ValueKey('capture')),
        );
        await tester.runAsync(() async {
          final image = await boundary.toImage(pixelRatio: 1);
          final data = await image.toByteData(format: ui.ImageByteFormat.png);
          await Directory(captureDirectory).create(recursive: true);
          await File(
            '$captureDirectory/running-${scale}x.png',
          ).writeAsBytes(data!.buffer.asUint8List());
          image.dispose();
        });
      }
    });
  }

  group('in-place controls', () {
    const stoppedStatus = 'phase=stopped\nport=4096\nruntime=opencode1\n';
    Finder key(String name) =>
        find.byKey(ValueKey('termux-running-server-$name'));
    // Restart, Stop and the rest are in the row's menu (one row per server,
    // no buttons in rows).
    Future<void> menu(WidgetTester tester, String name) async {
      await tester.tap(key('menu'));
      await tester.pumpAndSettle();
      await tester.tap(key(name));
      await tester.pumpAndSettle();
    }

    test(
      'a stopped, set-up server is observed as stopped, not absent',
      () async {
        status = stoppedStatus;
        final observed = await detectTermuxRunningServer(profiles: [local]);
        expect(observed.state, TermuxRunningServerState.stopped);
        expect(savedProfileForTermuxServer([remote, local], observed), local);
        // A phone that was never set up reports no runtime and stays hidden.
        status = 'phase=stopped\n';
        expect(
          (await detectTermuxRunningServer()).state,
          TermuxRunningServerState.absent,
        );
        // Nothing is probed for a server that is not running.
        expect(probes, isEmpty);
      },
    );

    testWidgets('a stopped server offers Start in place, and only Start', (
      tester,
    ) async {
      status = stoppedStatus;
      var restarts = 0;
      await entry(
        tester,
        actions: LocalServerCardActions(
          restart: () async {
            restarts++;
            status =
                'phase=ready\nport=4096\nruntime=opencode1\nversion=1.18.29\npid=12\n';
          },
          stop: () async {},
        ),
      );
      expect(find.text('Termux'), findsOneWidget);
      expect(find.text('OpenCode 1 · Stopped'), findsOneWidget);
      expect(key('connect'), findsNothing);
      // Nothing to restart or stop: no menu offers them.
      expect(key('menu'), findsNothing);
      expect(key('restart'), findsNothing);
      expect(key('stop'), findsNothing);
      await tester.tap(key('start'));
      await tester.pumpAndSettle();
      // Starting loses nothing, so it does not ask first.
      expect(restarts, 1);
      expect(find.text('OpenCode 1 · Running'), findsOneWidget);
      expect(key('connect'), findsOneWidget);
    });

    testWidgets('Restart and Stop ask first and do nothing when declined', (
      tester,
    ) async {
      var restarts = 0;
      var stops = 0;
      await entry(
        tester,
        busyConversations: 2,
        actions: LocalServerCardActions(
          restart: () async => restarts++,
          stop: () async {
            stops++;
            status = stoppedStatus;
          },
        ),
      );
      await menu(tester, 'restart');
      expect(
        find.byKey(const ValueKey('restart-local-server-sheet')),
        findsOneWidget,
      );
      // The sheet says what a restart interrupts.
      expect(find.textContaining('2'), findsWidgets);
      await tester.tapAt(const Offset(5, 5));
      await tester.pumpAndSettle();
      expect(restarts, 0);

      await menu(tester, 'restart');
      await tester.tap(
        find.byKey(const ValueKey('confirm-restart-local-server')),
      );
      await tester.pumpAndSettle();
      expect(restarts, 1);

      await menu(tester, 'stop');
      await tester.tapAt(const Offset(5, 5));
      await tester.pumpAndSettle();
      expect(stops, 0);

      await menu(tester, 'stop');
      await tester.tap(find.text('Stop local server').last);
      await tester.pumpAndSettle();
      expect(stops, 1);
      // The row stays, now offering Start: nothing was taken away.
      expect(key('start'), findsOneWidget);
    });

    testWidgets('a failed control says so in the row and keeps its controls', (
      tester,
    ) async {
      await entry(
        tester,
        actions: LocalServerCardActions(
          restart: () async =>
              throw const LocalServerControlFailure('proot is missing'),
          stop: () async => throw const LocalServerControlFailure(''),
        ),
      );
      await menu(tester, 'restart');
      await tester.tap(
        find.byKey(const ValueKey('confirm-restart-local-server')),
      );
      await tester.pumpAndSettle();
      // Native diagnostics are classified into authored recovery copy.
      expect(find.text('proot is missing'), findsNothing);
      expect(
        find.text(
          lookupAppLocalizations(const Locale('en')).productErrorTermux,
        ),
        findsOneWidget,
      );
      await tester.tap(key('menu'));
      await tester.pumpAndSettle();
      expect(key('restart'), findsOneWidget);
      await tester.tap(key('stop'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Stop local server').last);
      await tester.pumpAndSettle();
      expect(
        find.text('The server could not be stopped. Try again.'),
        findsOneWidget,
      );
    });

    testWidgets(
      'the connected server is marked, with Disconnect, and no Open',
      (tester) async {
        final opened = <ServerProfile>[];
        var disconnects = 0;
        final forgotten = <ServerProfile>[];
        var managed = 0;
        await entry(
          tester,
          connectedProfileID: local.id,
          onConnect: opened.add,
          onDisconnect: () async => disconnects++,
          onForget: forgotten.add,
          onManage: () => managed++,
        );
        // The current mark: the word leads the row's line.
        expect(find.text('Connected · OpenCode 1 · Running'), findsOneWidget);
        expect(
          find.byKey(const ValueKey('kit-row-current-mark')),
          findsOneWidget,
        );
        // Already connected: no button leading to where the person is.
        expect(find.text('Open'), findsNothing);
        expect(key('connect'), findsNothing);
        expect(opened, isEmpty);

        await tester.tap(key('menu'));
        await tester.pumpAndSettle();
        await tester.tap(key('disconnect'));
        await tester.pumpAndSettle();
        expect(disconnects, 1);

        await tester.tap(key('menu'));
        await tester.pumpAndSettle();
        await tester.tap(key('forget'));
        await tester.pumpAndSettle();
        expect(forgotten, [local]);

        await tester.tap(key('menu'));
        await tester.pumpAndSettle();
        await tester.tap(key('manage'));
        await tester.pumpAndSettle();
        expect(managed, 1);
      },
    );

    testWidgets(
      'Disconnect needs a connection and Forget needs a saved server',
      (tester) async {
        await entry(
          tester,
          profiles: [remote],
          onDisconnect: () async {},
          onForget: (_) {},
        );
        // Nothing to disconnect from and nothing saved to forget: no menu
        // at all, rather than an empty one.
        expect(key('menu'), findsNothing);
        expect(key('disconnect'), findsNothing);
        expect(key('forget'), findsNothing);
      },
    );

    for (final scale in [1.0, 2.5]) {
      testWidgets('all controls fit a 320 dp phone at text scale $scale', (
        tester,
      ) async {
        await tester.binding.setSurfaceSize(const Size(320, 900));
        addTearDown(() => tester.binding.setSurfaceSize(null));
        await entry(
          tester,
          textScale: scale,
          actions: LocalServerCardActions(
            restart: () async {},
            stop: () async {},
          ),
        );
        expect(tester.takeException(), isNull);
        for (final name in ['connect', 'menu']) {
          await tester.ensureVisible(key(name));
          expect(tester.getSize(key(name)).height, greaterThanOrEqualTo(48));
        }
        await tester.tap(key('menu'));
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        for (final name in ['restart', 'stop']) {
          expect(tester.getSize(key(name)).height, greaterThanOrEqualTo(48));
        }
      });
    }
  });
}
