import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/aiteam_scripts.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';

class _Linux extends BuiltinLinux {
  bool disabled = false;
  bool running = true;
  bool removed = false;
  bool failStop = false;
  String stopError = 'Could not stop';
  bool failRead = false;
  bool throwRead = false;
  String? failingScript;
  Completer<void>? preparing;
  final scripts = <String>[];

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
    if (throwRead && script == BuiltinTeam.disabledCheckScript) {
      throw const BuiltinLinuxException('password=fake-native-read-secret');
    }
    if (script == failingScript ||
        (failRead && script == BuiltinTeam.disabledCheckScript)) {
      return const BuiltinLinuxRunResult(exitCode: 1, output: '');
    }
    if (script == BuiltinTeam.disableScript) disabled = true;
    if (script == BuiltinTeam.enableScript) disabled = false;
    if (script == BuiltinTeam.cityScript) await preparing?.future;
    if (script == AiTeamScripts.removeScript) {
      if (running) throw StateError('Removing a running team');
      removed = true;
    }
    return BuiltinLinuxRunResult(
      exitCode: script == BuiltinTeam.disabledCheckScript && disabled ? 42 : 0,
      output: '',
    );
  }

  @override
  Future<void> stopService(String name) async {
    if (failStop) throw BuiltinLinuxException(stopError);
    running = false;
  }

  @override
  Future<void> startService(
    String name,
    String script, {
    int? port,
    String? notice,
  }) async {
    running = true;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'turn off survives a new team instance and keeps installed tools',
    () async {
      final linux = _Linux();
      await BuiltinTeam(linux: linux).turnOff();
      final reopened = BuiltinTeam(linux: linux);
      expect(await reopened.isTurnedOff(), isTrue);
      await reopened.ensureRunning(notice: 'AI Team');
      expect(linux.running, isFalse);
      expect(linux.removed, isFalse);
      expect(linux.scripts, isNot(contains(AiTeamScripts.removeScript)));
    },
  );

  test('temporary stop still permits automatic recovery', () async {
    final linux = _Linux();
    final team = BuiltinTeam(linux: linux);
    await team.stop();
    await team.ensureRunning(notice: 'AI Team');
    expect(linux.running, isTrue);
    expect(linux.disabled, isFalse);
  });

  test('remove stops and invokes the existing setup remove script', () async {
    final linux = _Linux();
    await BuiltinTeam(linux: linux).remove();
    expect(linux.disabled, isTrue);
    expect(linux.running, isFalse);
    expect(linux.removed, isTrue);
    expect(linux.scripts, contains(AiTeamScripts.removeScript));
    expect(AiTeamScripts.removeScript, isNot(contains('/root/projects')));
  });

  test('stop failure leaves files installed and recovery disabled', () async {
    final linux = _Linux()..failStop = true;
    await expectLater(
      BuiltinTeam(linux: linux).remove(),
      throwsA(isA<BuiltinLinuxException>()),
    );
    expect(linux.removed, isFalse);
    expect(linux.disabled, isTrue);
  });

  test('failed persistence prevents stop and removal', () async {
    final linux = _Linux()..failingScript = BuiltinTeam.disableScript;
    await expectLater(
      BuiltinTeam(linux: linux).remove(),
      throwsA(isA<BuiltinLinuxException>()),
    );
    expect(linux.running, isTrue);
    expect(linux.removed, isFalse);
  });

  test('failed marker read never restarts a stopped team', () async {
    final linux = _Linux()
      ..running = false
      ..failRead = true;
    await BuiltinTeam(linux: linux).ensureRunning(notice: 'AI Team');
    expect(linux.running, isFalse);
  });

  test('failed remove is reported and does not re-enable recovery', () async {
    final linux = _Linux()..failingScript = AiTeamScripts.removeScript;
    final team = BuiltinTeam(linux: linux);
    await expectLater(team.remove(), throwsA(isA<BuiltinLinuxException>()));
    expect(linux.disabled, isTrue);
    expect(linux.removed, isFalse);
    await team.ensureRunning(notice: 'AI Team');
    expect(linux.running, isFalse);
  });

  test('an explicit start enables a previously turned-off team', () async {
    final linux = _Linux();
    final team = BuiltinTeam(
      linux: linux,
      httpGet: (_) async => '{"status":"ok"}',
    );
    await team.turnOff();
    await team.start(notice: 'AI Team');
    expect(linux.disabled, isFalse);
    expect(linux.running, isTrue);
  });

  test('remove waits for preparation from another instance', () async {
    final linux = _Linux()..preparing = Completer<void>();
    final preparing = BuiltinTeam(linux: linux).prepare();
    await Future<void>.delayed(Duration.zero);
    final removing = BuiltinTeam(linux: linux).remove();
    await Future<void>.delayed(Duration.zero);
    expect(linux.removed, isFalse);
    linux.preparing!.complete();
    await preparing;
    await removing;
    expect(linux.removed, isTrue);
    expect(linux.running, isFalse);
    expect(linux.scripts.last, AiTeamScripts.removeScript);
  });

  test('queued recovery after removal stays off', () async {
    final linux = _Linux();
    await Future.wait([
      BuiltinTeam(linux: linux).remove(),
      BuiltinTeam(linux: linux).ensureRunning(notice: 'AI Team'),
      BuiltinTeam(linux: linux).prepare(),
    ]);
    expect(linux.running, isFalse);
    expect(linux.scripts, isNot(contains(BuiltinTeam.cityScript)));
  });

  test('native stop diagnostics redact fake credentials', () async {
    final linux = _Linux()
      ..failStop = true
      ..stopError = 'password=fake-removal-secret';
    await expectLater(
      BuiltinTeam(linux: linux).turnOff(),
      throwsA(
        isA<BuiltinLinuxException>().having(
          (error) => error.message,
          'redacted detail',
          allOf(contains('•••'), isNot(contains('fake-removal-secret'))),
        ),
      ),
    );
  });

  test('reading unknown enabled state reports an error', () async {
    final linux = _Linux()..failRead = true;
    await expectLater(
      BuiltinTeam(linux: linux).isTurnedOff(),
      throwsA(
        isA<BuiltinLinuxException>().having(
          (error) => error.code,
          'code',
          'team_state_unavailable',
        ),
      ),
    );
  });

  test('failed enable does not start the service', () async {
    final linux = _Linux()
      ..running = false
      ..disabled = true
      ..failingScript = BuiltinTeam.enableScript;
    await expectLater(
      BuiltinTeam(linux: linux).start(notice: 'AI Team'),
      throwsA(isA<BuiltinLinuxException>()),
    );
    expect(linux.running, isFalse);
    expect(linux.disabled, isTrue);
  });

  test('management scripts parse with the system shell', () async {
    for (final script in [
      BuiltinTeam.disableScript,
      BuiltinTeam.enableScript,
      BuiltinTeam.disabledCheckScript,
      BuiltinTeam.serviceScript,
    ]) {
      final result = await Process.run('sh', ['-n', '-c', script]);
      expect(result.exitCode, 0, reason: result.stderr.toString());
    }
  });

  test('thrown marker read error exposes no native details', () async {
    final linux = _Linux()..throwRead = true;
    await expectLater(
      BuiltinTeam(linux: linux).isTurnedOff(),
      throwsA(
        isA<BuiltinLinuxException>().having(
          (error) => error.message,
          'safe message',
          'Could not read AI Team enabled state.',
        ),
      ),
    );
  });
}
