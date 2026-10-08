import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';

class _ManualStartupTiming implements CrashDiagnosticsStartupTiming {
  @override
  Duration elapsed = Duration.zero;
  Duration? budget;
  final _deadline = Completer<void>();

  void advance(Duration duration) {
    elapsed += duration;
    final limit = budget;
    if (limit != null && elapsed >= limit && !_deadline.isCompleted) {
      _deadline.complete();
    }
  }

  @override
  Future<CrashDiagnosticsController?> timeout(
    Future<CrashDiagnosticsController?> pending,
    Duration budget, {
    required CrashDiagnosticsController? Function() onTimeout,
  }) {
    this.budget = budget;
    advance(Duration.zero);
    final result = Completer<CrashDiagnosticsController?>();
    pending.then(
      (value) {
        if (!result.isCompleted) result.complete(value);
      },
      onError: (Object error, StackTrace stack) {
        if (!result.isCompleted) result.completeError(error, stack);
      },
    );
    _deadline.future.then((_) {
      if (!result.isCompleted) result.complete(onTimeout());
    });
    return result.future;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/crash_diagnostics');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late AppDiagnosticsController diagnostics;
  late Directory directory;
  late _ManualStartupTiming timing;

  setUp(() {
    CrashDiagnosticsStartup.resetForTesting();
    diagnostics = AppDiagnosticsController();
    timing = _ManualStartupTiming();
    directory = Directory.systemTemp.createTempSync('crash-startup-test-');
  });

  tearDown(() {
    CrashDiagnosticsStartup.resetForTesting();
    messenger.setMockMethodCallHandler(channel, null);
    diagnostics.dispose();
    directory.deleteSync(recursive: true);
  });

  test(
    'slow opening outlives the launch budget and resolves readiness later',
    () async {
      final reply = Completer<Object?>();
      var nativeOpens = 0;
      messenger.setMockMethodCallHandler(channel, (_) {
        nativeOpens++;
        return reply.future;
      });
      final readiness = CrashDiagnosticsStartup.ready;
      var readyCompleted = false;
      unawaited(readiness.then((_) => readyCompleted = true));
      final launch = CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
        timing: timing,
      );
      timing.advance(CrashDiagnosticsStartup.launchBudget);
      expect(await launch, isNull);
      expect(readyCompleted, isFalse);
      expect(CrashDiagnosticsStartup.current, isNull);
      expect(CrashDiagnosticsStartup.start(diagnostics), same(launch));
      timing.advance(const Duration(milliseconds: 50));
      reply.complete({'directory': directory.path});
      final controller = await readiness;
      expect(controller, isNotNull);
      expect(controller!.enabled, isFalse);
      expect(CrashDiagnosticsStartup.current, same(controller));
      expect(nativeOpens, 1);
    },
  );

  testWidgets('production launch wait still returns at 300 ms', (tester) async {
    final reply = Completer<Object?>();
    messenger.setMockMethodCallHandler(channel, (_) => reply.future);
    var completed = false;
    final opened = CrashDiagnosticsStartup.start(
      diagnostics,
      nativeChannel: channel,
    );
    unawaited(opened.then((_) => completed = true));
    await tester.pump(const Duration(milliseconds: 299));
    expect(completed, isFalse);
    await tester.pump(const Duration(milliseconds: 1));
    expect(completed, isTrue);
    expect(await opened, isNull);
    expect(CrashDiagnosticsStartup.current, isNull);
    expect(directory.listSync(), isEmpty);
  });

  test(
    'reply beyond the elapsed launch budget becomes available later',
    () async {
      messenger.setMockMethodCallHandler(channel, (_) async {
        timing.advance(
          CrashDiagnosticsStartup.launchBudget +
              const Duration(milliseconds: 50),
        );
        return {'directory': directory.path};
      });
      expect(
        await CrashDiagnosticsStartup.start(
          diagnostics,
          nativeChannel: channel,
          timing: timing,
        ),
        isNull,
      );
      final controller = await CrashDiagnosticsStartup.ready;
      expect(controller, isNotNull);
      expect(CrashDiagnosticsStartup.current, same(controller));
      expect(controller!.enabled, isFalse);
    },
  );

  test(
    'never-answering channel bounds launch while readiness stays pending',
    () async {
      final readyBeforeFirstFrame = CrashDiagnosticsStartup.ready;
      var readyCompleted = false;
      unawaited(readyBeforeFirstFrame.then((_) => readyCompleted = true));
      final reply = Completer<Object?>();
      messenger.setMockMethodCallHandler(channel, (_) => reply.future);
      final opened = CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
        timing: timing,
      );
      var completed = false;
      unawaited(opened.then((_) => completed = true));
      expect(timing.budget, const Duration(milliseconds: 300));
      timing.advance(const Duration(milliseconds: 299));
      await Future<void>.delayed(Duration.zero);
      expect(completed, isFalse);
      timing.advance(const Duration(milliseconds: 1));
      expect(await opened, isNull);
      expect(readyCompleted, isFalse);
      expect(CrashDiagnosticsStartup.ready, same(readyBeforeFirstFrame));
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

  test('actual late opening failure resolves readiness to null', () async {
    final reply = Completer<Object?>();
    messenger.setMockMethodCallHandler(channel, (_) => reply.future);
    final readiness = CrashDiagnosticsStartup.ready;
    final launch = CrashDiagnosticsStartup.start(
      diagnostics,
      nativeChannel: channel,
      timing: timing,
    );
    timing.advance(CrashDiagnosticsStartup.launchBudget);
    expect(await launch, isNull);
    reply.completeError(
      PlatformException(
        code: 'unavailable',
        message: 'synthetic-provider-secret',
      ),
    );
    expect(await readiness, isNull);
    expect(CrashDiagnosticsStartup.current, isNull);
    expect(directory.listSync(), isEmpty);
  });

  test('late opening preserves explicit persisted consent', () async {
    final consent = File(
      '${directory.path}/${CrashDiagnosticsController.consentFileName}',
    );
    final epoch = DateTime.now().millisecondsSinceEpoch - 1000;
    consent.writeAsStringSync('$epoch');
    final reply = Completer<Object?>();
    messenger.setMockMethodCallHandler(channel, (_) => reply.future);
    final opened = CrashDiagnosticsStartup.start(
      diagnostics,
      nativeChannel: channel,
      timing: timing,
    );
    timing.advance(CrashDiagnosticsStartup.launchBudget);
    expect(await opened, isNull);
    reply.complete({'directory': directory.path});
    final controller = await CrashDiagnosticsStartup.ready;
    expect(controller, isNotNull);
    expect(controller!.enabled, isTrue);
    expect(CrashDiagnosticsStartup.current, same(controller));
    expect(consent.readAsStringSync(), '$epoch');
    expect(directory.listSync().length, 1);
  });

  test(
    'reset drops an old reply after the new controller has attached',
    () async {
      final staleDirectory = Directory('${directory.path}/stale');
      final oldReply = Completer<Object?>();
      final oldCall = Completer<void>();
      messenger.setMockMethodCallHandler(channel, (_) {
        oldCall.complete();
        return oldReply.future;
      });
      final oldReady = CrashDiagnosticsStartup.ready;
      final oldLaunch = CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
        timing: timing,
      );
      await oldCall.future;
      CrashDiagnosticsStartup.resetForTesting();
      expect(await oldReady, isNull);
      final newReady = CrashDiagnosticsStartup.ready;
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => {'directory': directory.path},
      );
      final controller = await CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
        timing: _ManualStartupTiming(),
      );
      expect(await newReady, same(controller));
      expect(controller, isNotNull);
      oldReply.complete({'directory': staleDirectory.path});
      expect(await oldLaunch, isNull);
      expect(CrashDiagnosticsStartup.current, same(controller));
      expect(await CrashDiagnosticsStartup.ready, same(controller));
      expect(staleDirectory.existsSync(), isFalse);
    },
  );

  test(
    'late restored records replay exactly once after real readiness',
    () async {
      final now = DateTime.now().millisecondsSinceEpoch;
      File(
        '${directory.path}/${CrashDiagnosticsController.consentFileName}',
      ).writeAsStringSync('${now - 1000}');
      final reply = Completer<Object?>();
      var nativeOpens = 0;
      messenger.setMockMethodCallHandler(channel, (_) {
        nativeOpens++;
        return reply.future;
      });
      var ready = false;
      diagnostics.addListener(() => expect(ready, isTrue));
      final launch = CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
        timing: timing,
      );
      timing.advance(CrashDiagnosticsStartup.launchBudget);
      expect(await launch, isNull);
      reply.complete({
        'directory': directory.path,
        'nativeCrash': {
          'timestamp': now,
          'message': 'synthetic-provider-secret',
        },
        'anrTimestamp': now,
      });
      final controller = await CrashDiagnosticsStartup.ready;
      ready = true;
      expect(controller!.savedCount, 2);
      expect(CrashDiagnosticsStartup.current, same(controller));
      await Future<void>.delayed(Duration.zero);
      expect(diagnostics.entries.map((entry) => entry.source), [
        'crash.native',
        'crash.anr',
      ]);
      expect(CrashDiagnosticsStartup.start(diagnostics), same(launch));
      expect(await launch, isNull);
      await Future<void>.delayed(Duration.zero);
      expect(diagnostics.entries.length, 2);
      expect(nativeOpens, 1);
      expect(
        File('${directory.path}/crash-diagnostics.json').readAsStringSync(),
        isNot(contains('synthetic-provider-secret')),
      );
    },
  );

  test('reset after readiness cancels queued restored notifications', () async {
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
    unawaited(
      CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
        timing: timing,
      ),
    );
    final controller = await CrashDiagnosticsStartup.ready;
    expect(controller!.savedCount, 1);
    CrashDiagnosticsStartup.resetForTesting();
    await Future<void>.delayed(Duration.zero);
    expect(CrashDiagnosticsStartup.current, isNull);
    expect(diagnostics.isEmpty, isTrue);
    expect(controller.snapshot.available, isFalse);
  });

  test('reset during replay prevents further stale notifications', () async {
    final now = DateTime.now().millisecondsSinceEpoch;
    File(
      '${directory.path}/${CrashDiagnosticsController.consentFileName}',
    ).writeAsStringSync('${now - 1000}');
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => {
        'directory': directory.path,
        'nativeCrash': {'timestamp': now},
        'anrTimestamp': now,
      },
    );
    diagnostics.addListener(CrashDiagnosticsStartup.resetForTesting);
    unawaited(
      CrashDiagnosticsStartup.start(
        diagnostics,
        nativeChannel: channel,
        timing: timing,
      ),
    );
    final controller = await CrashDiagnosticsStartup.ready;
    expect(controller!.savedCount, 2);
    await Future<void>.delayed(Duration.zero);
    expect(diagnostics.entries.map((entry) => entry.source), ['crash.native']);
    expect(CrashDiagnosticsStartup.current, isNull);
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
        timing: timing,
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
        timing: timing,
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
        timing: timing,
      );
      expect(controller, isNotNull);
      expect(controller!.clear(), isTrue);
      await Future<void>.delayed(Duration.zero);
      expect(controller.savedCount, 0);
      expect(diagnostics.isEmpty, isTrue);
    },
  );
}
