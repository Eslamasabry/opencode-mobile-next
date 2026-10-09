// The last setup step (Start OpenCode) failed on a phone that had been used
// for a while (build 2065, emulator 2026-09-29): the finisher the app shell
// hands the setup engine read Riverpod through the shell's own widget ref,
// and that widget had been replaced by then ("Using ref when a widget is
// unmounted"). The finisher must depend on objects, not on a widget's ref.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart' show TermuxRuntime;
import 'package:shared_preferences/shared_preferences.dart';

import '../setup_engine_test.dart' show FakeLinux;

class _Store extends ProfileStore {
  _Store({required super.prefs});

  final saved = <ServerProfile>[];

  @override
  List<ServerProfile> get profiles => saved;

  @override
  Future<void> upsert(ServerProfile value) async {
    saved
      ..removeWhere((p) => p.id == value.id)
      ..add(value);
  }
}

class _Connection extends ConnectionController {
  _Connection(super.store);

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {}
}

class _Linux extends BuiltinLinux {
  int starts = 0;

  // BB5 reports native idle work; this lifetime fake must not open a real channel.
  @override
  Future<void> observePhoneAgentWork({
    required String profileId,
    required bool? busy,
  }) async {}

  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: true,
  );

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
  }) async => starts++;

  @override
  Future<void> restartServer(
    String script, {
    int port = 4097,
    required int expectedGeneration,
  }) async => starts++;
}

void main() {
  testWidgets('the setup finisher still works after the shell that attached '
      'it was replaced', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final store = _Store(prefs: await SharedPreferences.getInstance());
    final connection = _Connection(store);
    final linux = _Linux();
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success('1.18.29');
    addTearDown(() => serverProbe = probeServerConnection);
    final engine = ChannelSetupEngine(linux: FakeLinux());
    addTearDown(engine.dispose);
    final previous = PhoneSetup.engine;
    PhoneSetup.engine = engine;
    addTearDown(() => PhoneSetup.engine = previous);

    final overrides = [
      bootstrapProvider.overrideWithValue(AppBootstrap(store)),
      connProvider.overrideWithValue(connection),
      builtinLinuxProvider.overrideWithValue(linux),
    ];
    tester.view
      ..physicalSize = const Size(900, 1800)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(overrides: overrides, child: const OcApp()),
    );
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    final finish = engine.finisher!;

    // The shell goes away (a route replaced it) while the scope lives on.
    await tester.pumpWidget(
      ProviderScope(overrides: overrides, child: const SizedBox.shrink()),
    );
    await tester.pump();

    final error = await finish(
      const SetupFinishRequest(
        host: SetupHostKind.builtin,
        runtime: TermuxRuntime.openCode1,
        openCodeChanged: false,
        version: null,
      ),
    );
    // It got as far as connecting (the fake connection does not), which is
    // after reading the store and starting OpenCode.
    expect(error, startsWith('Could not connect'));
    expect(linux.starts, 1, reason: 'OpenCode was started');
    expect(store.saved, isNotEmpty);
  });
}
