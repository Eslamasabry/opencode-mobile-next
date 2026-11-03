// LinuxManager reads (Running, Ports, Packages, Storage) ride out a moment
// when the phone cannot prove who owns every program in Linux (a program
// that just ended, or one the ownership check met mid-start): N1-5 on build
// 2400 showed "Could not read Linux" twice on Running and once on Packages.
// A read is tried again before the page says anything, and a failure that
// stays is said as "busy", never as "Linux is not set up".

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_manager.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_processes.dart';

const _ownership = BuiltinLinuxException(
  'Open app settings, force stop OpenCode, then open it again.',
  code: 'runtime_ownership_unknown',
);

class _Linux extends BuiltinLinux {
  _Linux(this.failures);

  /// Errors the next runs throw, in order; then the scan answers.
  final List<BuiltinLinuxException> failures;
  int runs = 0;

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async {
    runs++;
    if (failures.isNotEmpty) throw failures.removeAt(0);
    return const BuiltinLinuxRunResult(
      exitCode: 0,
      output:
          '::self 900\n::uptime 50.00\n'
          '::p 41\t41 (python3) S 1 41 41 0 -1 0 0 0 0 0 0 0 0 0 20 0 1 0 '
          '3000 1000 900 0\tpython3 -m http.server 8765\n',
    );
  }
}

void main() {
  test('a passing ownership failure is retried and the list comes', () async {
    final linux = _Linux([_ownership, _ownership]);
    final manager = LinuxManager(linux, retryDelay: Duration.zero);
    final scan = await manager.scanProcesses();
    expect(scan.processes.single.programName, 'http.server');
    expect(linux.runs, 3);
  });

  test('one that stays is "busy", not "not set up"', () async {
    final linux = _Linux([_ownership, _ownership, _ownership, _ownership]);
    final manager = LinuxManager(linux, retryDelay: Duration.zero);
    await expectLater(
      manager.scanProcesses(),
      throwsA(
        isA<LinuxManagerException>().having(
          (e) => e.problem,
          'problem',
          LinuxProblem.busy,
        ),
      ),
    );
    expect(linux.runs, 3);
  });

  test('Linux that is not there is said at once, without retries', () async {
    final linux = _Linux([
      const BuiltinLinuxException('Ubuntu is not installed in the app yet'),
    ]);
    final manager = LinuxManager(linux, retryDelay: Duration.zero);
    await expectLater(
      manager.scanProcesses(),
      throwsA(
        isA<LinuxManagerException>().having(
          (e) => e.problem,
          'problem',
          LinuxProblem.unavailable,
        ),
      ),
    );
    expect(linux.runs, 1);
    expect(LinuxProcessScan.parse('').processes, isEmpty);
  });
}
