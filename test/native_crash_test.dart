import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/app_exit_recovery.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/diagnostics/report_problem.dart';
import 'package:opencode_mobile/diagnostics/report_problem_startup.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:opencode_mobile/platform/native_crash.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _frame =
    'io.github.eslamasabry.opencode_mobile.PhoneAgentHost.start(PhoneAgentHost.kt:42)';
const _unsafeMessage =
    'https://login.example/?token=fake-secret-code /home/oc/.claude/credentials';
final _at = DateTime(2026, 10, 4, 10, 3);

Map<String, Object?> _rawCrash() => {
  'timestamp': _at.millisecondsSinceEpoch,
  'exceptionClass': 'java.lang.IllegalStateException',
  'message': 'Invalid state',
  'frames': [_frame, 'java.lang.Thread.run(Thread.java:101)'],
};

class _Lifecycle extends AppLifecycleBridge {
  int reads = 0;

  @override
  Future<AppLaunchReport> launchReport() async {
    reads++;
    return AppLaunchReport.fromMap({'lastCrash': _rawCrash()});
  }
}

class _Store extends ProfileStore {
  _Store({required super.prefs});

  @override
  List<ServerProfile> get profiles => const [];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    PerfTrace.resetForTesting();
    PerfTrace.logSink = null;
    SharedPreferences.setMockInitialValues({});
  });
  tearDown(() async {
    await ReportProblemStartup.resetForTesting();
    PerfTrace.resetForTesting();
  });

  test('reads the latest JVM crash independently of exit history', () {
    final report = AppLaunchReport.fromMap({'lastCrash': _rawCrash()});
    expect(report.exit, isNull);
    expect(report.lastCrash!.exceptionClass, 'java.lang.IllegalStateException');
    expect(report.lastCrash!.timestamp, _at);
    expect(report.lastCrash!.message, 'Invalid state');
    expect(report.lastCrash!.frames, hasLength(2));
    expect(
      report.lastCrash!.traceAttrs['frame0'],
      'PhoneAgentHost.start (PhoneAgentHost.kt:42)',
    );
    expect(AppLaunchReport.empty.lastCrash, isNull);
  });

  test('invalid native input fails closed without paths or sign-in data', () {
    final crash = NativeCrashRecord.fromMap({
      ..._rawCrash(),
      'message': _unsafeMessage,
      'frames': [
        _frame,
        _unsafeMessage,
        'java.lang.Thread.run(/home/oc/secret.java:42)',
        'java.lang.Thread.run(Thread.java:42)\n$_unsafeMessage',
        null,
        12,
      ],
    })!;
    expect(crash.message, '[redacted]');
    expect(crash.frames, [_frame]);
    expect(crash.diagnosticMessage, isNot(contains('fake-secret-code')));
    expect(crash.diagnosticStack, isNot(contains('/home/oc')));
    expect(crash.traceAttrs.toString(), isNot(contains('login.example')));
    for (final raw in [
      null,
      false,
      {..._rawCrash(), 'timestamp': 0},
      {..._rawCrash(), 'timestamp': double.infinity},
      {..._rawCrash(), 'timestamp': 253402300800000},
      {..._rawCrash(), 'exceptionClass': _unsafeMessage},
      {..._rawCrash(), 'exceptionClass': 'untrusted.user.Text'},
    ]) {
      expect(NativeCrashRecord.fromMap(raw), isNull);
    }
  });

  test('bounded metadata never converts unknown objects to text', () {
    final crash = NativeCrashRecord.fromMap({
      ..._rawCrash(),
      'message': {'token': 'fake-secret-code'},
      'frames': List.filled(100, _frame),
    })!;
    expect(crash.message, '[redacted]');
    expect(crash.frames, hasLength(24));
    expect(
      crash.traceAttrs.keys.where((key) => key.startsWith('frame')),
      hasLength(6),
    );
    expect(() => crash.frames.add(_frame), throwsUnsupportedError);
  });

  test('wrapped service crash retains the bounded safe permission cause', () {
    const serviceFrame =
        'android.app.ContextImpl.startForegroundService(ContextImpl.java:1824)';
    final crash = NativeCrashRecord.fromMap({
      ..._rawCrash(),
      'exceptionClass': 'java.lang.RuntimeException',
      'message': _unsafeMessage,
      'causes': [
        {
          'exceptionClass': 'java.lang.SecurityException',
          'message': 'Permission denied',
          'frames': List.filled(20, serviceFrame),
        },
        {
          'exceptionClass': 'java.lang.IllegalStateException',
          'message': _unsafeMessage,
          'frames': [_unsafeMessage, _frame],
        },
        {'exceptionClass': _unsafeMessage},
        {
          'exceptionClass': 'java.lang.NullPointerException',
          'message': 'Missing value',
          'frames': [_frame],
        },
      ],
    })!;
    expect(crash.causes, hasLength(2));
    expect(crash.causes.first.frames, hasLength(8));
    expect(crash.causes.last.message, '[redacted]');
    expect(crash.causes.last.frames, [_frame]);
    expect(() => crash.causes.clear(), throwsUnsupportedError);
    final diagnostics = AppDiagnosticsController();
    addTearDown(diagnostics.dispose);
    diagnostics.record(
      crash.diagnosticMessage,
      StackTrace.fromString(crash.diagnosticStack),
      source: 'crash.last',
    );
    PerfTrace.mark('crash.last', attrs: crash.traceAttrs);
    final entry = diagnostics.entries.single;
    expect(entry.stack, contains('Caused by java > lang > SecurityException'));
    expect(entry.stack, contains('ContextImpl. startForegroundService'));
    expect(entry.stack, contains('Permission denied'));
    expect(entry.stack, isNot(contains('fake-secret-code')));
    final mark = PerfTrace.spans.single;
    expect(mark.attrs['cause0'], 'SecurityException');
    expect(mark.attrs['causeFrame0'], contains('startForegroundService'));
    expect(mark.attrs['causeFrame0'], contains('ContextImpl.java:1824'));
    expect(PerfTrace.reportText(), isNot(contains('/home/oc')));
  });

  test(
    'oc/lifecycle launch report accepts the native crash contract',
    () async {
      const channel = MethodChannel(AppLifecycleBridge.channelName);
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'launchReport');
        return {'lastCrash': _rawCrash()};
      });
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      final crash = (await AppLifecycleBridge().launchReport()).lastCrash!;
      expect(crash.frames.first, _frame);
    },
  );

  test(
    'startup emits one crash.last and persists its full bounded stack',
    () async {
      final directory = Directory.systemTemp.createTempSync(
        'native-crash-test-',
      );
      addTearDown(() {
        if (directory.existsSync()) directory.deleteSync(recursive: true);
      });
      final diagnostics = AppDiagnosticsController();
      addTearDown(diagnostics.dispose);
      final lifecycle = _Lifecycle();
      final recovery = AppExitRecovery(bridge: lifecycle);
      addTearDown(recovery.dispose);
      final starter = BuiltinServerStarter(linux: BuiltinLinux());
      addTearDown(starter.dispose);
      final store = _Store(prefs: await SharedPreferences.getInstance());
      Future<void> launch() => recovery.runOnce(
        store: store,
        active: null,
        starter: starter,
        diagnostics: diagnostics,
      );
      await launch();
      await launch();
      expect(lifecycle.reads, 1);
      expect(recovery.notice, isNull);
      final entry = diagnostics.entries.single;
      expect(entry.source, 'crash.last');
      expect(entry.timestamp, _at);
      expect(
        entry.stack,
        contains('PhoneAgentHost.start (PhoneAgentHost.kt:42)'),
      );
      expect(entry.stack, contains('Thread.run (Thread.java:101)'));
      final mark = PerfTrace.spans
          .where((span) => span.name == 'crash.last')
          .single;
      expect(mark.attrs['class'], 'IllegalStateException');
      expect(mark.attrs['frame0'], contains('PhoneAgentHost.start'));
      expect(PerfTrace.reportText(), contains('PhoneAgentHost.kt:42'));

      final opened = await ReportProblemStartup.start(
        diagnostics,
        directory: directory,
      );
      expect(
        opened!.report.entries.where((entry) => entry.source == 'crash.last'),
        hasLength(1),
      );
      await ReportProblemStartup.resetForTesting();
      final reopened = await ReportProblem.open(directory: directory);
      addTearDown(reopened.dispose);
      final kept = reopened.entries.where(
        (entry) => entry.source == 'crash.last',
      );
      expect(kept, hasLength(1));
      expect(kept.single.stack, contains('PhoneAgentHost.kt:42'));
      expect(kept.single.stack, isNot(contains('[REDACTED]')));
    },
  );
}
