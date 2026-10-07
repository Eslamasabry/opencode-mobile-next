import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('test.builtin_performance_diagnostics');

  tearDown(() {
    debugPlatformCapabilities = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('performance reads each service lifetime through the channel', () async {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    MethodCall? received;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          received = call;
          return {
            'serverRunning': true,
            'prootMode': 'seccomp',
            'prootFilters': 1,
            'serverFilters': 2,
            'workHeld': true,
            'services': {
              'server': {
                'running': true,
                'lastExitCode': 137,
                'lastUptimeMs': 4000,
                'uptimeMs': 250,
                'restartCount': 3,
                'exitReason': 'memory_or_phantom_kill',
              },
              'team': {
                'running': false,
                'lastExitCode': 0,
                'lastUptimeMs': 120000,
                'uptimeMs': null,
                'restartCount': 0,
                'exitReason': 'exited',
              },
            },
          };
        });

    final performance = await BuiltinLinux(channel: channel).performance();
    expect(received?.method, 'performance');
    expect(received?.arguments, isNull);
    expect(performance.serverRunning, isTrue);
    expect(performance.prootMode, BuiltinProotMode.seccomp);
    expect(performance.prootFilters, 1);
    expect(performance.serverFilters, 2);
    expect(performance.workHeld, isTrue);
    expect(performance.services.keys, ['server', 'team']);
    final server = performance.services['server']!;
    expect(server.running, isTrue);
    expect(server.lastExitCode, 137);
    expect(server.lastUptimeMs, 4000);
    expect(server.uptimeMs, 250);
    expect(server.restartCount, 3);
    expect(server.exitReason, BuiltinServiceExitReason.memoryOrPhantomKill);
    final team = performance.services['team']!;
    expect(team.running, isFalse);
    expect(team.lastExitCode, 0);
    expect(team.lastUptimeMs, 120000);
    expect(team.uptimeMs, isNull);
    expect(team.restartCount, 0);
    expect(team.exitReason, BuiltinServiceExitReason.exited);
    expect(() => performance.services.clear(), throwsUnsupportedError);
  });

  test(
    'older APKs and absent channel results have no service records',
    () async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (_) async => null);
      expect(
        (await BuiltinLinux(channel: channel).performance()).services,
        isEmpty,
      );
      expect(BuiltinPerformance.fromMap({}).services, isEmpty);
      expect(
        BuiltinPerformance.fromMap({'serverRunning': true}).services,
        isEmpty,
      );
    },
  );

  test('malformed service containers and entries are ignored', () {
    for (final value in [null, 'bad', false, 7, <Object?>[]]) {
      expect(BuiltinPerformance.fromMap({'services': value}).services, isEmpty);
    }
    final performance = BuiltinPerformance.fromMap({
      'services': {
        null: {},
        7: {},
        '': {},
        'not-a-map': 'bad',
        'not-a-map-either': [],
        'server': <Object?, Object?>{},
      },
    });
    expect(performance.services.keys, ['server']);
    final server = performance.services['server']!;
    expect(server.running, isFalse);
    expect(server.lastExitCode, isNull);
    expect(server.lastUptimeMs, isNull);
    expect(server.uptimeMs, isNull);
    expect(server.restartCount, 0);
    expect(server.exitReason, BuiltinServiceExitReason.unknown);
  });

  test('malformed numeric fields never fabricate an exit or uptime', () {
    for (final invalid in [
      null,
      '137',
      true,
      1.5,
      double.nan,
      double.infinity,
    ]) {
      final record = BuiltinServiceDiagnostics.fromMap({
        'running': 'true',
        'lastExitCode': invalid,
        'lastUptimeMs': invalid,
        'uptimeMs': invalid,
        'restartCount': invalid,
        'exitReason': 'new-native-reason',
      });
      expect(record.running, isFalse);
      expect(record.lastExitCode, isNull);
      expect(record.lastUptimeMs, isNull);
      expect(record.uptimeMs, isNull);
      expect(record.restartCount, 0);
      expect(record.exitReason, BuiltinServiceExitReason.unknown);
    }
    final record = BuiltinServiceDiagnostics.fromMap({
      'lastExitCode': -1,
      'lastUptimeMs': -1,
      'uptimeMs': -1,
      'restartCount': -1,
    });
    expect(record.lastExitCode, -1);
    expect(record.lastUptimeMs, isNull);
    expect(record.uptimeMs, isNull);
    expect(record.restartCount, 0);
    final filters = BuiltinPerformance.fromMap({
      'prootFilters': double.nan,
      'serverFilters': double.infinity,
    });
    expect(filters.prootFilters, isNull);
    expect(filters.serverFilters, isNull);
  });

  test('an unknown reason stays unknown even with exit 137', () {
    final record = BuiltinServiceDiagnostics.fromMap({'lastExitCode': 137});
    expect(record.lastExitCode, 137);
    expect(record.exitReason, BuiltinServiceExitReason.unknown);
    for (final invalid in [null, 137, false, 'oom', 'sigkill']) {
      expect(
        BuiltinServiceExitReason.parse(invalid),
        BuiltinServiceExitReason.unknown,
      );
    }
  });

  test('zero durations are observations rather than missing values', () {
    final record = BuiltinServiceDiagnostics.fromMap({
      'lastExitCode': 0,
      'lastUptimeMs': 0,
      'uptimeMs': 0,
      'restartCount': 0,
    });
    expect(record.lastExitCode, 0);
    expect(record.lastUptimeMs, 0);
    expect(record.uptimeMs, 0);
    expect(record.restartCount, 0);
  });

  test(
    'unsupported platforms return an empty snapshot without a call',
    () async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      var called = false;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (_) async {
            called = true;
            return {};
          });
      final performance = await BuiltinLinux(channel: channel).performance();
      expect(called, isFalse);
      expect(performance.serverRunning, isFalse);
      expect(performance.services, isEmpty);
    },
  );
}
