import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/crash_diagnostics');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late AppDiagnosticsController diagnostics;
  late Directory directory;

  setUp(() {
    CrashDiagnosticsStartup.resetForTesting();
    diagnostics = AppDiagnosticsController();
    directory = Directory.systemTemp.createTempSync('crash-startup-test-');
  });

  tearDown(() {
    CrashDiagnosticsStartup.resetForTesting();
    messenger.setMockMethodCallHandler(channel, null);
    diagnostics.dispose();
    directory.deleteSync(recursive: true);
  });

  test('blocking reply beyond the launch budget remains unavailable', () async {
    messenger.setMockMethodCallHandler(channel, (_) async {
      sleep(
        CrashDiagnosticsStartup.launchBudget + const Duration(milliseconds: 50),
      );
      return {'directory': directory.path};
    });
    expect(
      await CrashDiagnosticsStartup.start(diagnostics, nativeChannel: channel),
      isNull,
    );
    expect(CrashDiagnosticsStartup.current, isNull);
    expect(directory.listSync(), isEmpty);
  });

  test(
    'never-answering native channel has bounded unavailable readiness',
    () async {
      final readyBeforeFirstFrame = CrashDiagnosticsStartup.ready;
      final reply = Completer<Object?>();
      messenger.setMockMethodCallHandler(channel, (_) => reply.future);
      final opened = CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
      );
      expect(await opened.timeout(const Duration(seconds: 2)), isNull);
      expect(await readyBeforeFirstFrame, isNull);
      expect(await CrashDiagnosticsStartup.ready, isNull);
      expect(CrashDiagnosticsStartup.current, isNull);
      expect(directory.listSync(), isEmpty);
      CrashDiagnosticsStartup.capture(
        diagnostics,
        StateError('synthetic-provider-secret'),
        StackTrace.fromString('synthetic-provider-secret'),
        'flutter',
      );
      expect(diagnostics.entries.single.message, 'Invalid state');
      expect(diagnostics.entries.single.stack, isEmpty);
      expect(directory.listSync(), isEmpty);
    },
  );

  test('late reply cannot enable capture or alter persisted consent', () async {
    final consent = File(
      '${directory.path}/${CrashDiagnosticsController.consentFileName}',
    );
    final epoch = DateTime.now().millisecondsSinceEpoch - 1000;
    consent.writeAsStringSync('$epoch');
    final reply = Completer<Object?>();
    messenger.setMockMethodCallHandler(channel, (_) => reply.future);
    expect(
      await CrashDiagnosticsStartup.start(diagnostics, nativeChannel: channel),
      isNull,
    );
    reply.complete({'directory': directory.path});
    await Future<void>.delayed(Duration.zero);
    expect(CrashDiagnosticsStartup.current, isNull);
    expect(await CrashDiagnosticsStartup.ready, isNull);
    expect(consent.readAsStringSync(), '$epoch');
    expect(directory.listSync().length, 1);
  });

  test(
    'timely native reply makes explicitly opted-in capture available',
    () async {
      File(
        '${directory.path}/${CrashDiagnosticsController.consentFileName}',
      ).writeAsStringSync('${DateTime.now().millisecondsSinceEpoch - 1000}');
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => {'directory': directory.path},
      );
      final controller = await CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
      );
      expect(controller, isNotNull);
      expect(controller!.enabled, isTrue);
      expect(CrashDiagnosticsStartup.current, same(controller));
    },
  );
  test(
    'background startup imports only private categories into diagnostics',
    () async {
      var ready = false;
      diagnostics.addListener(() => expect(ready, isTrue));
      final now = DateTime.now().millisecondsSinceEpoch;
      File(
        '${directory.path}/${CrashDiagnosticsController.consentFileName}',
      ).writeAsStringSync('${now - 1000}');
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => {
          'directory': directory.path,
          'nativeCrash': {
            'timestamp': now,
            'message': 'synthetic-provider-secret',
          },
          'anrTimestamp': now,
        },
      );
      final controller = await CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
      );
      expect(controller, isNotNull);
      expect(controller!.savedCount, 2);
      ready = true;
      CrashDiagnosticsStartup.capture(
        diagnostics,
        StateError('synthetic-provider-secret'),
        null,
        'flutter',
      );
      expect(controller.savedCount, 3);
      await Future<void>.delayed(Duration.zero);
      expect(diagnostics.entries.map((entry) => entry.source), [
        'crash.flutter',
        'crash.native',
        'crash.anr',
      ]);
      expect(
        File('${directory.path}/crash-diagnostics.json').readAsStringSync(),
        isNot(contains('synthetic-provider-secret')),
      );
    },
  );
  test(
    'clear before import notification cannot restore erased entries',
    () async {
      final now = DateTime.now().millisecondsSinceEpoch;
      File(
        '${directory.path}/${CrashDiagnosticsController.consentFileName}',
      ).writeAsStringSync('${now - 1000}');
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => {
          'directory': directory.path,
          'nativeCrash': {'timestamp': now},
        },
      );
      final controller = await CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
      );
      expect(controller, isNotNull);
      expect(controller!.clear(), isTrue);
      await Future<void>.delayed(Duration.zero);
      expect(controller.savedCount, 0);
      expect(diagnostics.isEmpty, isTrue);
    },
  );
}
