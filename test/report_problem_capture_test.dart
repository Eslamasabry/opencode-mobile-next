import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/diagnostics/report_problem.dart';
import 'package:opencode_mobile/diagnostics/report_problem_capture.dart';
import 'package:opencode_mobile/platform/thermal.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory directory;
  late ReportProblem report;
  late AppDiagnosticsController diagnostics;
  ReportProblemCapture? capture;

  setUp(() async {
    KitRedact.clearKnownSecrets();
    PerfTrace.resetForTesting();
    PerfTrace.logSink = null;
    directory = Directory.systemTemp.createTempSync('report-capture-test-');
    report = await ReportProblem.open(directory: directory);
    diagnostics = AppDiagnosticsController();
  });

  tearDown(() async {
    await capture?.close();
    capture = null;
    diagnostics.dispose();
    report.dispose();
    if (directory.existsSync()) directory.deleteSync(recursive: true);
    PerfTrace.resetForTesting();
    PerfTrace.logSink = null;
    KitRedact.clearKnownSecrets();
  });

  test('captures buffered errors and timings once plus repeated errors', () {
    final at = DateTime.utc(2026, 9, 27);
    diagnostics.record('failed', null, source: 'test', at: at);
    PerfTrace.mark('before.attach');
    capture = ReportProblemCapture(report: report, diagnostics: diagnostics);
    expect(report.entries, hasLength(2));

    PerfTrace.mark('after.attach');
    expect(report.entries, hasLength(3));
    diagnostics.record('failed', null, source: 'test', at: at);
    expect(report.entries, hasLength(4));
    diagnostics.record('different', null, source: 'test', at: at);
    expect(report.entries, hasLength(5));
    expect(
      report.entries.where((entry) => entry.message == 'failed'),
      hasLength(2),
    );
  });

  test('captures long spans that finish after newer span IDs', () {
    capture = ReportProblemCapture(report: report, diagnostics: diagnostics);
    final slow = PerfTrace.begin('slow');
    PerfTrace.mark('fast');
    slow.finish();
    expect(report.entries, hasLength(2));
    expect(report.reportText(), contains('slow'));
    expect(report.reportText(), contains('fast'));
  });

  test('completed timing is flushed to disk before returning to caller', () {
    capture = ReportProblemCapture(report: report, diagnostics: diagnostics);
    PerfTrace.mark('same.turn.crash.evidence');
    expect(report.entries, hasLength(1));
    final persisted = File(
      '${directory.path}/report_problem.json',
    ).readAsStringSync();
    expect(persisted, contains('same.turn.crash.evidence'));
  });

  test('only the report capacity of historical timings is imported', () async {
    report.dispose();
    report = await ReportProblem.open(directory: directory, maxEntries: 2);
    for (var index = 0; index < 5; index++) {
      PerfTrace.mark('historical.$index');
    }
    var persistedChanges = 0;
    report.addListener(() => persistedChanges++);
    capture = ReportProblemCapture(report: report, diagnostics: diagnostics);
    expect(persistedChanges, 1);
    expect(report.entries, hasLength(2));
    expect(report.reportText(), contains('historical.3'));
    expect(report.reportText(), contains('historical.4'));
    expect(report.reportText(), isNot(contains('historical.2')));
  });

  test(
    'trace sink and captured report redact provider and registered keys',
    () {
      const known = 'fake-registered-trace-credential';
      const provider = 'sk-ant-fakeProviderCredential12345';
      KitRedact.registerKnownSecret(known);
      final logs = <String>[];
      PerfTrace.logSink = logs.add;
      capture = ReportProblemCapture(report: report, diagnostics: diagnostics);
      final parent = PerfTrace.begin('parent $provider');
      final child = PerfTrace.begin(
        'child $known',
        parent: parent,
        attrs: {'detail': '$known $provider'},
      );
      child.finish();
      parent.finish();
      final persisted = File(
        '${directory.path}/report_problem.json',
      ).readAsStringSync();
      expect(logs, isNotEmpty);
      for (final value in [logs.join('\n'), report.reportText(), persisted]) {
        expect(value, isNot(contains(known)));
        expect(value, isNot(contains(provider)));
      }
    },
  );

  test(
    'empty-memory clear erases restored evidence without trace resurrection',
    () async {
      report.recordError('previous process', null);
      report.dispose();
      report = await ReportProblem.open(directory: directory);
      PerfTrace.mark('old.timing');
      capture = ReportProblemCapture(report: report, diagnostics: diagnostics);
      expect(report.reportText(), contains('previous process'));

      // This clear must notify even though this process has no buffered errors.
      diagnostics.clear();
      expect(report.entries, isEmpty);
      PerfTrace.mark('new.timing');
      await Future<void>.delayed(Duration.zero);
      expect(report.entries, hasLength(1));
      expect(report.reportText(), contains('new.timing'));
      expect(report.reportText(), isNot(contains('old.timing')));
      final reopened = await ReportProblem.open(directory: directory);
      addTearDown(reopened.dispose);
      expect(reopened.reportText(), isNot(contains('previous process')));
    },
  );

  test(
    'thermal readings and stream errors are captured and redacted',
    () async {
      final readings = StreamController<ThermalReading>(sync: true);
      addTearDown(readings.close);
      capture = ReportProblemCapture(
        report: report,
        diagnostics: diagnostics,
        thermalReadings: readings.stream,
      );
      readings.add(
        const ThermalReading(status: ThermalStatus.severe, headroom: 0.8),
      );
      readings.addError(
        StateError('token=fake-thermal-token'),
        StackTrace.fromString('password=fake-stack-password'),
      );
      expect(report.entries, hasLength(2));
      expect(report.reportText(), contains('severe'));
      expect(report.reportText(), contains('thermal.stream'));
      expect(report.reportText(), isNot(contains('fake-thermal-token')));
      expect(report.reportText(), isNot(contains('fake-stack-password')));
    },
  );

  test('close detaches every source and is idempotent', () async {
    final readings = StreamController<ThermalReading>(sync: true);
    addTearDown(readings.close);
    capture = ReportProblemCapture(
      report: report,
      diagnostics: diagnostics,
      thermalReadings: readings.stream,
    );
    await capture!.close();
    await capture!.close();
    diagnostics.record('after.close', null, source: 'test');
    PerfTrace.mark('after.close');
    readings.add(const ThermalReading(status: ThermalStatus.critical));
    await Future<void>.delayed(Duration.zero);
    expect(readings.hasListener, isFalse);
    expect(report.entries, isEmpty);
  });

  test('storage failures never escape diagnostics listeners', () {
    capture = ReportProblemCapture(report: report, diagnostics: diagnostics);
    directory.deleteSync(recursive: true);
    File(directory.path).writeAsStringSync('blocked directory');
    addTearDown(() => File(directory.path).deleteSync());
    expect(
      () => diagnostics.record('safe failure', null, source: 'test'),
      returnsNormally,
    );
    expect(report.storageFailed, isTrue);
    expect(diagnostics.clear, returnsNormally);
  });

  test('registered keys are sanitized before diagnostic truncation', () {
    const key = 'fake-registered-key-123';
    KitRedact.registerKnownSecret(key);
    expect(diagnostics.sanitize(key, limit: 10), isNot(contains('fake-regis')));
    diagnostics.record(key, StackTrace.fromString(key), source: key);
    expect(diagnostics.reportText(), isNot(contains(key)));
  });

  test(
    'Flutter error callbacks receive categories without caller metadata',
    () {
      const secret = 'fake-forwarded-credential';
      KitRedact.registerKnownSecret(secret);
      final previous = FlutterError.onError;
      FlutterErrorDetails? forwarded;
      var collected = 0;
      var filtered = 0;
      FlutterError.onError = (details) => forwarded = details;
      final errors = installAppErrorCapture(diagnostics);
      addTearDown(() {
        errors.restore();
        FlutterError.onError = previous;
      });
      FlutterError.onError!(
        FlutterErrorDetails(
          exception: StateError(secret),
          stack: StackTrace.fromString(secret),
          library: secret,
          context: ErrorDescription(secret),
          informationCollector: () {
            collected++;
            return [ErrorDescription(secret)];
          },
          stackFilter: (_) {
            filtered++;
            return [secret];
          },
          silent: true,
        ),
      );
      expect(forwarded, isNotNull);
      final details = forwarded!;
      expect(details.silent, isTrue);
      // Pattern redaction cannot prove arbitrary throwable-controlled metadata
      // safe. Global callbacks now deliberately forward fixed categories only.
      expect(details.exception, 'Invalid state');
      expect(details.library, 'Flutter framework');
      expect(details.stack, isNull);
      expect(details.context, isNull);
      expect(details.informationCollector, isNull);
      expect(details.stackFilter, isNull);
      expect(collected, 0);
      expect(filtered, 0);
      expect(diagnostics.entries.single.message, 'Invalid state');
      expect(diagnostics.entries.single.source, 'flutter');
      expect(diagnostics.entries.single.stack, isEmpty);
    },
  );

  test('platform callback and device logs receive only redacted errors', () {
    const secret = 'fake-platform-credential';
    KitRedact.registerKnownSecret(secret);
    final previous = PlatformDispatcher.instance.onError;
    Object? forwarded;
    StackTrace? forwardedStack;
    PlatformDispatcher.instance.onError = (error, stack) {
      forwarded = error;
      forwardedStack = stack;
      return false;
    };
    final errors = installAppErrorCapture(diagnostics);
    addTearDown(() {
      errors.restore();
      PlatformDispatcher.instance.onError = previous;
    });
    final logs = <String>[];
    runZoned(
      () {
        expect(
          PlatformDispatcher.instance.onError!(
            StateError(secret),
            StackTrace.fromString(secret),
          ),
          isTrue,
        );
      },
      zoneSpecification: ZoneSpecification(
        print: (_, _, _, line) => logs.add(line),
      ),
    );
    expect(forwarded, isNotNull);
    expect(forwarded.toString(), isNot(contains(secret)));
    expect(forwardedStack.toString(), isNot(contains(secret)));
    expect(logs, isNotEmpty);
    expect(logs.join('\n'), isNot(contains(secret)));
  });
}
