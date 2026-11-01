// What the app keeps running on the phone when nothing is being done: the
// fewer programs, the further from Android's limit of 32.
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/quota_collector_host.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The phone's Linux: every service start is written down.
class _Linux extends BuiltinLinux {
  bool serverRunning = false;
  final services = <String>[];

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: serverRunning,
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
  }) async => serverRunning = true;

  @override
  Future<void> startService(
    String name,
    String script, {
    int? port,
    String? notice,
  }) async => services.add(name);
}

ServerProfile _inApp() =>
    ServerProfile(
        id: 'builtin',
        name: 'This phone, built-in',
        baseUrl: BuiltinLinux.serverUrl,
      )
      ..username = BuiltinLinux.serverUsername
      ..password = 'secret';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory support;

  setUp(() {
    support = Directory.systemTemp.createTempSync('oc-baseline');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    // Usage was set up on this phone: its token is kept.
    messenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (call) async => call.method == 'read' ? 'x' * 64 : null,
    );
    messenger.setMockMethodCallHandler(
      const MethodChannel('plugins.flutter.io/path_provider'),
      (_) async => support.path,
    );
    SharedPreferences.setMockInitialValues({
      InAppQuotaCollector.enabledKey('builtin'): true,
    });
  });

  tearDown(() {
    serverProbe = probeServerConnection;
    support.deleteSync(recursive: true);
  });

  test('starting the phone\'s OpenCode starts no usage collector', () async {
    // As on a phone: the in-app Linux is there.
    debugPlatformCapabilities = const PlatformCapabilities(
      platform: TargetPlatform.android,
      isWeb: false,
    );
    addTearDown(() => debugPlatformCapabilities = null);
    final linux = _Linux();
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success('2.2.5');
    final starter = BuiltinServerStarter(
      linux: linux,
      pollInterval: Duration.zero,
    );
    expect(await starter.autoStartIfStopped(_inApp()), isTrue);
    // Whatever the start set going in the background has had its time.
    await Future<void>.delayed(const Duration(milliseconds: 500));
    // The collector (a Linux and Node: two of Android's 32) waits until
    // something reads usage.
    expect(linux.services, isNot(contains(InAppQuotaCollector.serviceName)));
    starter.dispose();
  });

  group('the usage collector on demand', () {
    test('a read wakes it, and it stops once nothing reads', () async {
      var running = false;
      var starts = 0;
      var stops = 0;
      final demand = QuotaCollectorDemand(
        enabled: true,
        ensure: (_) async {
          starts++;
          running = true;
          return true;
        },
        answers: (_) async => running,
        stop: () async {
          stops++;
          running = false;
        },
        idleFor: const Duration(milliseconds: 200),
        answerEvery: const Duration(milliseconds: 10),
      );
      await demand.use(_inApp(), 'token');
      expect(starts, 1);
      expect(running, isTrue);
      // Reads close together keep it: no second start, no stop between.
      await Future<void>.delayed(const Duration(milliseconds: 120));
      await demand.use(_inApp(), 'token');
      await Future<void>.delayed(const Duration(milliseconds: 120));
      expect(starts, 1);
      expect(stops, 0);
      // Quiet for longer than idleFor: stopped.
      await Future<void>.delayed(const Duration(milliseconds: 200));
      expect(stops, 1);
      expect(running, isFalse);
      // The next read starts it again.
      await demand.use(_inApp(), 'token');
      expect(starts, 2);
      demand.cancel();
    });

    test('where there is no in-app Linux it does nothing', () async {
      var asked = 0;
      final demand = QuotaCollectorDemand(
        enabled: false,
        ensure: (_) async {
          asked++;
          return true;
        },
        answers: (_) async {
          asked++;
          return false;
        },
        stop: () async => asked++,
      );
      await demand.use(_inApp(), 'token');
      demand.touch();
      expect(asked, 0);
      expect(demand.running, isFalse);
    });
  });
}
