import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_start_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_setup_engine.dart';
import 'support/voice_device_channel.dart';

class _OneProfileStore extends ProfileStore {
  _OneProfileStore({required super.prefs, required this.profile});

  final ServerProfile profile;

  @override
  List<ServerProfile> get profiles => [profile];

  @override
  String? get activeId => profile.id;

  @override
  Future<void> upsert(ServerProfile value) async {}
}

/// Every connect fails as a stopped server would, so the app stays on its
/// opening card and each connect attempt can be counted.
class _RefusedConnection extends ConnectionController {
  _RefusedConnection(super.store);

  int connectCalls = 0;
  final log = <String>[];

  /// Runs as a connect begins (a server that dies right after it answered).
  void Function()? beforeConnect;

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    beforeConnect?.call();
    connectCalls++;
    log.add('connect');
    lastError = 'Health check failed: connection refused';
    notifyListeners();
  }
}

class _FakeLinux extends BuiltinLinux {
  _FakeLinux(this.events);

  // BB5 reports native idle work; this launch fake must not open a real channel.
  @override
  Future<void> observePhoneAgentWork({
    required String profileId,
    required bool? busy,
  }) async {}

  final List<String> events;
  bool serverRunning = false;
  bool serverDies = false;

  /// The running process holds the port but rejects our password (it was
  /// started with another one): only a fresh start answers again.
  bool rejectsPassword = false;
  int starts = 0;
  int generation = 0;
  bool wanted = true;

  /// Holds every status read until completed: the launch is still deciding.
  Completer<void>? statusGate;

  @override
  Future<BuiltinLinuxStatus> status() async {
    await statusGate?.future;
    return _status();
  }

  BuiltinLinuxStatus _status() => BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: serverRunning,
    serverRestartWanted: wanted,
    serverRecoveryGeneration: generation,
  );

  @override
  Future<void> cancelServerRecovery() async {
    generation++;
  }

  @override
  Future<void> confirmServerRecovery({required int expectedGeneration}) async {}

  @override
  Future<void> restartServer(
    String script, {
    int port = 4097,
    required int expectedGeneration,
  }) async {
    if (!wanted || expectedGeneration != generation) {
      throw const BuiltinLinuxException('The phone server could not restart.');
    }
    await startServer(script, port: port);
  }

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: '');

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    starts++;
    events.add('start');
    serverRunning = !serverDies;
    rejectsPassword = false;
  }
}

void main() {
  late _RefusedConnection connection;
  late _FakeLinux linux;
  late _OneProfileStore store;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    final profile = ServerProfile(
      id: 'builtin',
      name: 'This phone, built-in (OpenCode)',
      baseUrl: BuiltinLinux.serverUrl,
      username: BuiltinLinux.serverUsername,
    )..password = 'secret';
    store = _OneProfileStore(
      prefs: await SharedPreferences.getInstance(),
      profile: profile,
    );
    connection = _RefusedConnection(store);
    linux = _FakeLinux(connection.log);
    serverProbe = ({required baseUrl, username, password}) async =>
        !linux.serverRunning
        ? const ServerProbeResult.failure('refused')
        : linux.rejectsPassword
        ? const ServerProbeResult.failure('rejected', needsPassword: true)
        : const ServerProbeResult.success('1.18.29');
  });

  tearDown(() => serverProbe = probeServerConnection);

  Future<void> mount(WidgetTester tester) async {
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    tester.view
      ..physicalSize = const Size(900, 1800)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(connection.store)),
          connProvider.overrideWithValue(connection),
          builtinLinuxProvider.overrideWithValue(linux),
          builtinServerStarterProvider.overrideWith(
            (ref) => BuiltinServerStarter(
              linux: linux,
              pollInterval: const Duration(milliseconds: 10),
            ),
          ),
        ],
        child: const OcApp(),
      ),
    );
  }

  Future<void> settle(WidgetTester tester) async {
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
  }

  Future<void> unmount(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    connection.dispose();
    await tester.pump();
  }

  testWidgets('opening after a confirmed in-app crash starts it once, then '
      'connects; Start and connect on the card starts it again', (
    tester,
  ) async {
    // It answered the start, then stopped before the connect.
    connection.beforeConnect = () => linux.serverRunning = false;
    await mount(tester);
    await settle(tester);
    // The next status read (the healing owner's 5 s poll) sees it stopped.
    await tester.pump(const Duration(seconds: 6));
    await settle(tester);

    expect(connection.log, ['start', 'connect']);
    expect(find.text('OpenCode inside the app is stopped'), findsOneWidget);
    expect(find.textContaining('Termux'), findsNothing);

    // The failed connect does not start the server again on its own.
    await settle(tester);
    expect(linux.starts, 1);
    expect(connection.connectCalls, 1);

    connection.beforeConnect = null;
    await tester.tap(find.byKey(const ValueKey('saved-server-start-phone')));
    await settle(tester);
    expect(connection.log, ['start', 'connect', 'start', 'connect']);

    await unmount(tester);
  });

  testWidgets('explicit Stop intent stays stopped on launch and resume', (
    tester,
  ) async {
    linux.wanted = false;
    await mount(tester);
    await settle(tester);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump(const Duration(seconds: 20));
    await settle(tester);
    expect(linux.starts, 0);
    await unmount(tester);
  });

  testWidgets('a server that is already running is only connected to', (
    tester,
  ) async {
    linux.serverRunning = true;
    await mount(tester);
    await settle(tester);
    expect(connection.log, ['connect']);
    await unmount(tester);
  });

  testWidgets('a start that fails is tried once more, then the card says '
      'why in plain words (QA B1)', (tester) async {
    linux.serverDies = true;
    await mount(tester);
    await settle(tester);
    // The healing restart failed first; the launch start was the one more
    // try, right away.
    await tester.pump(const Duration(seconds: 3));
    await settle(tester);
    expect(linux.starts, 2);
    expect(connection.connectCalls, 0);
    expect(find.text('OpenCode inside the app did not start'), findsOneWidget);
    expect(
      find.text(
        'OpenCode closed by itself while it was starting. Open setup to see '
        'its log, or start it again.',
      ),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('saved-server-open-in-app-setup')),
      findsOneWidget,
    );
    // Bounded: no third start on its own inside the healing back-off.
    await tester.pump(const Duration(seconds: 5));
    await settle(tester);
    expect(linux.starts, 2);

    await unmount(tester);
  });

  testWidgets('its Open setup lands on phone setup, not the old in-app page '
      '(builtin-server-setup merged into phone-setup-start, P1.3)', (
    tester,
  ) async {
    final previous = PhoneSetup.engine;
    final previousTermux = PhoneSetup.termux;
    PhoneSetup.engine = FakeSetupEngine();
    // Phone setup now observes both hosts. Neither should poll a native
    // channel in this route test.
    PhoneSetup.termux = FakeSetupEngine();
    answerVoiceDeviceProbe();
    addTearDown(() {
      PhoneSetup.engine = previous;
      PhoneSetup.termux = previousTermux;
    });
    linux.serverDies = true;
    await mount(tester);
    await settle(tester);
    await tester.pump(const Duration(seconds: 3));
    await settle(tester);
    await tester.tap(
      find.byKey(const ValueKey('saved-server-open-in-app-setup')),
    );
    await settle(tester);
    expect(find.byType(PhoneSetupStartScreen), findsOneWidget);
    expect(
      ModalRoute.of(
        tester.element(find.byType(PhoneSetupStartScreen)),
      )?.settings.name,
      'phone-setup-start',
    );
    // The native device-info probe has a bounded five-second timeout.
    await tester.pump(const Duration(seconds: 6));
    await unmount(tester);
  });

  testWidgets('resume preserves backoff before healing a later crash', (
    tester,
  ) async {
    await mount(tester);
    await settle(tester);
    expect(connection.log, ['start', 'connect']);

    // Android stopped the server while the app was away.
    linux.serverRunning = false;
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await settle(tester);
    expect(connection.log, ['start', 'connect']);

    await unmount(tester);
  });

  testWidgets('every cold launch starts a stopped in-app server, also past '
      'the crash-restart budget (QA B1)', (tester) async {
    // Android ended the app with the server each time; the person opens it
    // again. The fourth launch used to open on "stopped" and wait for a tap.
    for (var launch = 1; launch <= 4; launch++) {
      linux.serverRunning = false;
      connection = _RefusedConnection(store);
      await mount(tester);
      await settle(tester);
      expect(linux.starts, launch, reason: 'launch $launch');
      expect(connection.connectCalls, 1, reason: 'launch $launch');
      await unmount(tester);
    }
  });

  testWidgets('no stopped or failure page flashes while the launch start is '
      'still deciding (QA B7)', (tester) async {
    linux.statusGate = Completer<void>();
    // A refused connect from before the start had its turn.
    connection.lastError = 'Health check failed: connection refused';
    await mount(tester);
    await settle(tester);
    expect(find.byKey(const ValueKey('saved-server-failed')), findsNothing);
    expect(find.text('OpenCode inside the app is stopped'), findsNothing);

    linux.statusGate!.complete();
    await settle(tester);
    expect(linux.starts, 1);
    await unmount(tester);
  });

  testWidgets('a running in-app server whose first connect failed is not '
      'called stopped, and is connected again while it answers (QA-03)', (
    tester,
  ) async {
    // After a relaunch on a busy phone: the server runs and answers its
    // health check, but the first connect timed out.
    linux.serverRunning = true;
    await mount(tester);
    await settle(tester);
    expect(connection.connectCalls, 1);
    expect(find.text('OpenCode inside the app is stopped'), findsNothing);
    expect(find.text("OpenCode on this phone isn't answering"), findsWidgets);

    // The healing owner's foreground health poll reconnects after a
    // short back-off instead of leaving the page for good.
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(seconds: 1));
    }
    await settle(tester);
    expect(connection.connectCalls, greaterThanOrEqualTo(2));
    expect(linux.starts, 0, reason: 'a server that answers is not restarted');
    await unmount(tester);
  });

  testWidgets('a running server that rejects our password is replaced at '
      'launch, by the app\'s own start (QA-03)', (tester) async {
    linux.serverRunning = true;
    linux.rejectsPassword = true;
    await mount(tester);
    await settle(tester);
    expect(linux.starts, 1);
    expect(connection.log, ['start', 'connect']);
    await unmount(tester);
  });
}
