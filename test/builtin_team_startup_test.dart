import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';

class _Linux extends BuiltinLinux {
  bool running = true;
  final scripts = <String>[];
  int starts = 0;

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    services: running ? [BuiltinTeam.serviceName] : [],
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async {
    scripts.add(script);
    return const BuiltinLinuxRunResult(exitCode: 0, output: '');
  }

  @override
  Future<void> startService(
    String name,
    String script, {
    int? port,
    String? notice,
  }) async {
    starts++;
    running = true;
  }

  @override
  Future<void> stopService(String name) async => running = false;
}

Future<void> _settled(BuiltinTeamStartProgress progress) async {
  if (!progress.running) return;
  final done = Completer<void>();
  void changed() {
    if (!progress.running && !done.isCompleted) done.complete();
  }

  progress.addListener(changed);
  try {
    changed();
    await done.future.timeout(const Duration(seconds: 2));
  } finally {
    progress.removeListener(changed);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('healthy running city reuses its registration and service', () async {
    final linux = _Linux();
    final progress = BuiltinTeamStartProgress();
    addTearDown(progress.dispose);
    final team = BuiltinTeam(
      linux: linux,
      progress: progress,
      httpGet: (_) async => '{"status":"ok"}',
    );
    await team.start(notice: 'AI Team');
    expect(linux.starts, 0);
    expect(linux.scripts, isNot(contains(BuiltinTeam.registerScript)));
    expect(linux.scripts, isNot(contains(BuiltinTeam.cityScript)));
    expect(progress.error, isNull);
    expect(progress.isDone(BuiltinTeamStartStep.store), isTrue);
  });

  test('healthy background recovery does not reinstall or register', () async {
    final linux = _Linux();
    final progress = BuiltinTeamStartProgress();
    addTearDown(progress.dispose);
    final team = BuiltinTeam(
      linux: linux,
      progress: progress,
      httpGet: (_) async => '{"status":"ok"}',
    );
    await team.ensureRunning(notice: 'AI Team', observe: true);
    await _settled(progress);
    expect(linux.starts, 0);
    expect(linux.scripts, [BuiltinTeam.disabledCheckScript]);
    expect(progress.error, isNull);
    expect(progress.isDone(BuiltinTeamStartStep.store), isTrue);
  });

  test(
    'unhealthy city is registered and only live health marks ready',
    () async {
      final linux = _Linux();
      final progress = BuiltinTeamStartProgress();
      addTearDown(progress.dispose);
      final team = BuiltinTeam(
        linux: linux,
        progress: progress,
        httpGet: (uri) async {
          if (uri.path == '/health') return '{"status":"ok"}';
          return linux.scripts.contains(BuiltinTeam.registerScript)
              ? '{"status":"ok"}'
              : '{"status":"starting"}';
        },
      );
      await team.start(notice: 'AI Team');
      expect(linux.scripts, contains(BuiltinTeam.registerScript));
      expect(progress.error, isNull);
      expect(progress.isDone(BuiltinTeamStartStep.store), isTrue);
    },
  );

  test('hanging store health expires background recovery honestly', () async {
    final linux = _Linux();
    final progress = BuiltinTeamStartProgress();
    addTearDown(progress.dispose);
    final hang = Completer<String?>();
    final team = BuiltinTeam(
      linux: linux,
      progress: progress,
      recoveryStoreTimeout: const Duration(milliseconds: 50),
      httpGet: (uri) =>
          uri.path == '/health' ? Future.value('{"status":"ok"}') : hang.future,
    );
    await team.ensureRunning(notice: 'AI Team', observe: true);
    await _settled(progress);
    expect(progress.failedStep, BuiltinTeamStartStep.store);
    expect(
      progress.error,
      isA<BuiltinTeamException>().having((e) => e.timedOut, 'timedOut', true),
    );
    expect(progress.isDone(BuiltinTeamStartStep.store), isFalse);
    expect(linux.running, isTrue);
    hang.complete('{"status":"ok"}');
    await Future<void>.delayed(Duration.zero);
    expect(progress.failedStep, BuiltinTeamStartStep.store);
    expect(progress.isDone(BuiltinTeamStartStep.store), isFalse);
  });

  for (final operation in ['stop', 'turnOff', 'remove']) {
    test(
      '$operation cancels pending observation and ignores late health',
      () async {
        final linux = _Linux();
        final progress = BuiltinTeamStartProgress();
        addTearDown(progress.dispose);
        final hang = Completer<String?>();
        final observing = Completer<void>();
        final team = BuiltinTeam(
          linux: linux,
          progress: progress,
          httpGet: (uri) {
            if (uri.path == '/health') return Future.value('{"status":"ok"}');
            if (!observing.isCompleted) observing.complete();
            return hang.future;
          },
        );
        await team.ensureRunning(notice: 'AI Team', observe: true);
        await observing.future;
        switch (operation) {
          case 'stop':
            await team.stop();
          case 'turnOff':
            await team.turnOff();
          case 'remove':
            await team.remove();
        }
        expect(progress.running, isFalse);
        expect(progress.isDone(BuiltinTeamStartStep.store), isFalse);
        expect(linux.running, isFalse);
        hang.complete('{"status":"ok"}');
        await Future<void>.delayed(Duration.zero);
        expect(progress.running, isFalse);
        expect(progress.isDone(BuiltinTeamStartStep.store), isFalse);
        expect(linux.starts, 0);
      },
    );
  }
}
