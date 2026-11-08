// Firebase Performance Monitoring (docs/telemetry/health-and-diagnostics.md,
// section C2): nothing is collected without `usage`, only fixed trace names
// and listed attribute values leave, the flow call sites produce the traces,
// and the build never applies the Performance Gradle plugin (which would
// trace HTTP and record request URLs).
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/telemetry/perf_traces.dart';
import 'package:opencode_mobile/telemetry/telemetry.dart';
import 'package:opencode_mobile/telemetry/telemetry_provider.dart';

import 'support/telemetry_recorder.dart';

class _PerfVendor extends RecordingAdapter implements PerformanceAdapter {
  bool? collecting;
  final traces = <(String, Map<String, String>, int)>[];

  @override
  Future<void> setCollection({
    required bool usage,
    required bool crashes,
  }) async {
    await super.setCollection(usage: usage, crashes: crashes);
    await setPerformanceCollection(usage);
  }

  @override
  Future<void> setPerformanceCollection(bool enabled) async {
    collecting = enabled;
  }

  @override
  Future<void> perfTrace(
    String name,
    Map<String, String> attributes,
    int durationMs,
  ) async {
    traces.add((name, attributes, durationMs));
  }
}

Future<TelemetryService> _service(
  _PerfVendor vendor, {
  bool usage = false,
  bool crashes = false,
}) async {
  final service = TelemetryService(
    adapter: vendor,
    store: MemoryTelemetryConsentStore.split(usage: usage, crashes: crashes),
  );
  await service.start();
  return service;
}

void main() {
  tearDown(AppTelemetry.reset);

  group('consent', () {
    test('nothing is collected or sent without usage', () async {
      final vendor = _PerfVendor();
      final service = await _service(vendor, crashes: true);
      expect(vendor.collecting, isFalse);
      service.performanceTrace(
        PerfSchema.connConnect,
        const Duration(seconds: 1),
        {'server_kind': 'phone', 'result': 'ok'},
      );
      await settleTelemetry();
      expect(vendor.traces, isEmpty);
    });

    test('usage turns collection on, and off again with it', () async {
      final vendor = _PerfVendor();
      final service = await _service(vendor);
      await service.setUsageConsent(true);
      expect(vendor.collecting, isTrue);
      service.performanceTrace(
        PerfSchema.connConnect,
        const Duration(milliseconds: 1234),
        {'server_kind': 'phone', 'result': 'ok'},
      );
      await settleTelemetry();
      expect(vendor.traces, hasLength(1));
      expect(vendor.traces.single.$1, 'conn_connect');
      expect(vendor.traces.single.$2, {'server_kind': 'phone', 'result': 'ok'});
      expect(vendor.traces.single.$3, 1234);
      await service.setUsageConsent(false);
      expect(vendor.collecting, isFalse);
      service.performanceTrace(PerfSchema.appColdStart, Duration.zero);
      await settleTelemetry();
      expect(vendor.traces, hasLength(1));
    });

    test('a vendor without Performance is skipped without error', () async {
      final service = await startedService(RecordingAdapter(), usage: true);
      service.performanceTrace(PerfSchema.appColdStart, Duration.zero);
      await settleTelemetry();
    });

    test('combined adapter reaches only the Performance vendors', () async {
      final perf = _PerfVendor();
      final other = RecordingAdapter();
      final service = TelemetryService(
        adapter: CombinedTelemetryAdapter([other, perf]),
        store: MemoryTelemetryConsentStore.split(usage: true),
      );
      await service.start();
      service.performanceTrace(PerfSchema.appColdStart, Duration.zero);
      await settleTelemetry();
      expect(perf.traces.single.$1, 'app_cold_start');
      expect(other.calls, isNot(contains('trace')));
    });
  });

  group('fixed values only', () {
    test('unknown trace names are dropped', () async {
      final vendor = _PerfVendor();
      final service = await _service(vendor, usage: true);
      for (final name in [
        'GET https://my-server.example/session',
        'conn_connect ',
        'linux_setup_secret',
        '_app_start',
        '',
      ]) {
        service.performanceTrace(name, Duration.zero);
      }
      await settleTelemetry();
      expect(vendor.traces, isEmpty);
    });

    test('unknown attribute names and values are dropped', () async {
      final vendor = _PerfVendor();
      final service = await _service(vendor, usage: true);
      service.performanceTrace(PerfSchema.agentStart, Duration.zero, {
        'agent': 'claude-3-opus-secret-model',
        'result': 'Error: connect to 10.0.0.5 failed',
        'host': '10.0.0.5',
        'url': 'https://example.com/x',
      });
      service.performanceTrace(PerfSchema.connConnect, Duration.zero, {
        'server_kind': 'home.example.org',
        'result': 'ok',
        'fail_kind': 'dns',
      });
      service.performanceTrace(PerfSchema.appColdStart, Duration.zero, {
        'server_kind': 'phone',
      });
      await settleTelemetry();
      expect(vendor.traces.map((t) => t.$1), [
        'agent_start',
        'conn_connect',
        'app_cold_start',
      ]);
      expect(vendor.traces[0].$2, isEmpty);
      expect(vendor.traces[1].$2, {'result': 'ok'});
      expect(vendor.traces[2].$2, isEmpty);
    });

    test('every listed name and value is a plain word', () {
      final word = RegExp(r'^[a-z][a-z0-9_]{0,39}$');
      for (final name in PerfSchema.names) {
        expect(word.hasMatch(name), isTrue, reason: name);
      }
      expect(
        PerfSchema.names,
        containsAll([
          'conn_connect',
          'send_first_reply',
          'agent_start',
          'app_cold_start',
          'linux_setup_node',
          'linux_setup_done',
        ]),
      );
    });
  });

  group('flow call sites', () {
    test('each flow produces its trace with fixed attributes', () async {
      final vendor = _PerfVendor();
      AppTelemetry.install(await _service(vendor, usage: true));
      HealthFlow.connAttempt(
        serverKind: 'computer',
        ok: false,
        elapsed: const Duration(seconds: 2),
        failure: Exception('connect to secret-host.example failed'),
      );
      HealthFlow.sendResult(
        result: 'ok',
        firstReply: const Duration(seconds: 3),
        serverKind: 'phone',
      );
      HealthFlow.sendResult(result: 'ok', serverKind: 'phone');
      HealthFlow.linuxSetupStep(
        step: 'node',
        ok: true,
        elapsed: const Duration(seconds: 9),
      );
      HealthFlow.agentStart(
        agent: 'codex',
        ok: true,
        elapsed: const Duration(milliseconds: 700),
      );
      HealthFlow.agentStart(agent: 'codex', ok: true);
      await settleTelemetry();
      final seen = [for (final t in vendor.traces) '${t.$1}|${t.$2}|${t.$3}'];
      expect(seen, [
        'conn_connect|{server_kind: computer, result: fail}|2000',
        'send_first_reply|{server_kind: phone}|3000',
        'linux_setup_node|{step: node, result: ok}|9000',
        'agent_start|{agent: codex, result: ok}|700',
      ]);
      expect(vendor.traces.toString(), isNot(contains('secret-host')));
    });
  });

  group('build', () {
    String read(String path) => File(path).readAsStringSync();

    test('the Performance Gradle plugin is never applied', () {
      final gradleFiles = [
        'android/build.gradle.kts',
        'android/settings.gradle.kts',
        'android/app/build.gradle.kts',
      ];
      for (final path in gradleFiles) {
        final text = read(path);
        expect(text, isNot(contains('firebase-perf')), reason: path);
        expect(text, isNot(contains('firebase.perf')), reason: path);
        expect(text, isNot(contains('perf-plugin')), reason: path);
        expect(
          text,
          isNot(contains('com.google.firebase.firebase-perf')),
          reason: path,
        );
      }
    });

    test('no network trace is ever created by hand', () {
      // The only network API in the SDK is the manual HttpMetric, which takes
      // a URL. Nothing in lib/ may use it.
      final offenders = [
        for (final f in Directory('lib').listSync(recursive: true))
          if (f is File &&
              f.path.endsWith('.dart') &&
              RegExp(
                r'newHttpMetric|HttpMetric',
              ).hasMatch(f.readAsStringSync()))
            f.path,
      ];
      expect(offenders, isEmpty);
    });

    test('collection is off in the manifest until usage is agreed', () {
      final manifest = read('android/app/src/main/AndroidManifest.xml');
      expect(
        manifest,
        matches(
          RegExp(
            r'firebase_performance_collection_deactivated"\s+android:value="true"',
          ),
        ),
      );
    });

    test('the SDK is wired like Analytics and Crashlytics', () {
      final main = read('lib/main.dart');
      expect(main, contains('FirebasePerformanceAdapter()'));
      // The gradle wiring is conditional on the private config for all
      // Firebase plugins; none is applied outside that block.
      final gradle = read('android/app/build.gradle.kts');
      expect(gradle, contains('if (ocTelemetryConfig)'));
    });
  });
}
