// Captures of This phone (vision slice, 2026-10-20): the real ThisPhoneScreen
// over the in-app host, the live aurora painted (still), real fonts, blurred
// shadows, no server. Phone 412x915 dp at 2x pixels, one full screen each.
//
//   flutter test --concurrency=1 tool/capture/vision_this_phone_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     tool/capture/vision_this_phone_test.dart
//
// The data is made up but the shape is the app's own: the memory line sums
// a /proc scan the fake Linux answers; the countdowns are the crash recovery
// and heat guard states the page reads.
//
// Output: /home/eslam/Storage2/launch-footage/vision-this-phone/<scene>.png
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server_recovery.dart';
import 'package:opencode_mobile/builtin/deliberate_stop.dart';
import 'package:opencode_mobile/builtin/thermal_guard.dart';
import 'package:opencode_mobile/builtin/thermal_guard_teams.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show StreamStatus;
import 'package:opencode_mobile/platform/thermal.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/revamp/screen_phone_1_fixtures.dart';
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-this-phone',
);

enum _State { running, crashed, heat, stopped, gaveUp, needsYou }

enum _Scene {
  runningDark('running-dark'),
  runningLight('running-light', light: true),
  crashed('crashed-restarts-by-itself-dark', state: _State.crashed),
  crashedLight(
    'crashed-restarts-by-itself-light',
    state: _State.crashed,
    light: true,
  ),
  heat('heat-paused-dark', state: _State.heat),
  stopped('stopped-by-you-dark', state: _State.stopped),
  gaveUp('kept-stopping-dark', state: _State.gaveUp),
  needsYou('start-failed-dark', state: _State.needsYou),
  rows('running-rows-dark', scroll: true),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  smallCrashed(
    'small-320-text2-crashed-dark',
    state: _State.crashed,
    size: Size(320, 693),
    scale: 2,
  ),
  arabic('arabic-rtl-dark', locale: Locale('ar')),
  arabicCrashed(
    'arabic-rtl-crashed-dark',
    state: _State.crashed,
    locale: Locale('ar'),
  );

  const _Scene(
    this.file, {
    this.state = _State.running,
    this.light = false,
    this.scroll = false,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale,
  });
  final String file;
  final _State state;
  final bool light;
  final bool scroll;
  final Size size;
  final double scale;
  final Locale? locale;
}

/// The in-app Linux with a server, its processes and its last exit.
class _Linux extends PhoneLinux {
  _Linux({required super.running, this.killed = false, this.failStart = false});

  final bool killed;
  final bool failStart;

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    if (failStart) {
      throw const BuiltinLinuxException(
        'The phone server could not start.',
        code: 'server_start_unavailable',
      );
    }
    return super.startServer(script, port: port, restoreRecipe: restoreRecipe);
  }

  static String _stat(int pid, String comm, int ppid, int rssPages) =>
      '$pid ($comm) S $ppid 0 0 0 0 0 0 0 0 0 1200 300 0 0 20 0 1 0 '
      '5000 0 $rssPages';

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    if (!script.contains('oc-linux-scan')) return super.run(script);
    // OpenCode, Paseo and Claude Code: about 410 MB together.
    final output = [
      '::self 900',
      '::uptime 4000.0',
      '::p 900\t${_stat(900, 'sh', 1, 200)}\tsh',
      '::p 120\t${_stat(120, 'node', 1, 46080)}\tnode /opt/opencode2/bin/opencode serve --port 4097',
      '::p 140\t${_stat(140, 'node', 1, 30720)}\tnode /opt/paseo/daemon.js',
      '::p 160\t${_stat(160, 'node', 140, 28160)}\tnode /opt/oc-claude/cli.js',
    ].join('\n');
    return BuiltinLinuxRunResult(exitCode: 0, output: output);
  }

  @override
  Future<BuiltinPerformance> performance() async => BuiltinPerformance(
    serverRunning: running,
    services: {
      BuiltinLinux.serverServiceName: BuiltinServiceDiagnostics(
        running: running,
        lastExitCode: killed ? 137 : null,
        lastStopRequested: killed ? false : null,
        exitReason: killed
            ? BuiltinServiceExitReason.memoryOrPhantomKill
            : BuiltinServiceExitReason.unknown,
      ),
    },
  );
}

class _Bridge extends ThermalBridge {
  @override
  Future<ThermalReading> current() async =>
      const ThermalReading(status: ThermalStatus.none);

  @override
  Stream<ThermalReading> readings() => const Stream.empty();

  @override
  Future<bool> notify({required String title, String text = ''}) async => true;
}

class _Port implements ThermalTeamPort {
  static const team = ThermalTeam(
    id: 'phone',
    url: 'http://127.0.0.1:8472',
    builtin: true,
  );

  @override
  Future<List<ThermalTeam>> runningHere() async => [team];

  @override
  Future<ThermalTeamHold?> pause(
    ThermalTeam team, {
    required DateTime now,
  }) async => ThermalTeamHold(team: team, since: now);

  @override
  Future<ThermalTeamHold> stop(ThermalTeamHold hold) async => hold;

  @override
  Future<bool> resume(ThermalTeamHold hold) async => true;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  setUp(() {
    useNoTermuxJob();
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  for (final scene in _Scene.values) {
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // Blurred shadows as a phone draws them (the test binding flattens
      // them); put back before the test ends.
      debugDisableShadows = false;
      KitLiveBackground.frozenSeconds = 21;
      final running =
          scene.state == _State.running || scene.state == _State.heat;
      final linux = _Linux(
        running: running,
        killed: scene.state == _State.crashed || scene.state == _State.gaveUp,
        failStart: scene.state == _State.needsYou,
      );
      final recovery = ValueNotifier(switch (scene.state) {
        _State.crashed => BuiltinRecoveryState(
          phase: BuiltinRecoveryPhase.waiting,
          profileId: inAppProfile.id,
          attempts: 1,
          nextAttemptAt: DateTime.now().add(const Duration(seconds: 38)),
        ),
        _State.gaveUp => BuiltinRecoveryState(
          phase: BuiltinRecoveryPhase.exhausted,
          profileId: inAppProfile.id,
          attempts: 3,
        ),
        _ => BuiltinRecoveryState(
          phase: BuiltinRecoveryPhase.ready,
          profileId: inAppProfile.id,
        ),
      });
      ThermalGuard? guard;
      final boundary = GlobalKey();
      await pumpPhone(
        tester,
        // Opened the way the app opens it (Settings › Server, Search): a
        // pushed page with Back.
        home: const SizedBox.expand(key: ValueKey('opener')),
        routes: {
          thisPhoneRoute: (_) =>
              const ThisPhoneScreen(kind: PhoneHostKind.inApp),
        },
        linux: linux,
        profiles: [inAppProfile],
        light: scene.light,
        size: scene.size,
        pixelRatio: 2,
        textScale: scene.scale,
        locale: scene.locale,
        boundary: boundary,
        readyTimeout: const Duration(milliseconds: 300),
        overrides: [thisPhoneRecoveryProvider.overrideWithValue(recovery)],
        configure: (controller) {
          if (running) {
            controller
              ..api = CaptureApi()
              ..repository = CaptureRepository()
              ..status = StreamStatus.connected;
          }
          if (scene.state == _State.stopped) {
            unawaited(
              DeliberateServerStop.mark(
                controller.store.prefs,
                inAppProfile.id,
              ),
            );
          }
        },
      );
      unawaited(
        openThisPhone(
          tester.element(find.byKey(const ValueKey('opener'))),
          kind: PhoneHostKind.inApp,
        ),
      );
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      if (scene.state == _State.heat) {
        // The real guard: hot (paused the team), then cool for 30 s, so
        // its own resume is scheduled in about 1.5 min.
        final container = ProviderScope.containerOf(
          tester.element(find.byType(MaterialApp)),
          listen: false,
        );
        guard = ThermalGuard(
          bridge: _Bridge(),
          port: _Port(),
          prefs: await SharedPreferences.getInstance(),
          clock: () => DateTime.now().subtract(const Duration(seconds: 30)),
        );
        await guard.observe(const ThermalReading(status: ThermalStatus.severe));
        guard.policy.coolSince = DateTime.now().subtract(
          const Duration(seconds: 25),
        );
        container.read(thermalGuardSlotProvider).value = guard;
      }
      for (var i = 0; i < 20; i++) {
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 10)),
        );
        await tester.pump(const Duration(milliseconds: 100));
      }
      if (scene.state == _State.needsYou) {
        // The person tapped Restart now and the start failed.
        await tester.tap(find.byKey(const ValueKey('this-phone-start')));
        for (var i = 0; i < 20; i++) {
          await tester.runAsync(
            () => Future<void>.delayed(const Duration(milliseconds: 20)),
          );
          await tester.pump(const Duration(milliseconds: 100));
        }
      }
      if (scene.scroll) {
        await tester.drag(
          find.byKey(const ValueKey('this-phone')),
          const Offset(0, -560),
        );
        for (var i = 0; i < 10; i++) {
          await tester.pump(const Duration(milliseconds: 100));
        }
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, boundary, pixelRatio: 2),
      );
      await unmountPhone(tester);

      recovery.dispose();
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
