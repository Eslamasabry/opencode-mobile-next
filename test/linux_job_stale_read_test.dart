// A job read that began before a job started must not end that job: a backup
// waits for "not running", so a stale read made it start nothing.

@TestOn('linux')
library;

import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_manager.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_packages.dart';

class _Manager extends LinuxManager {
  _Manager() : super(BuiltinLinux());

  final reads = <Completer<LinuxJobState>>[];

  @override
  Future<void> startScriptJob(String script, {required String notice}) async {}

  @override
  Future<LinuxJobState> readJob() {
    final c = Completer<LinuxJobState>();
    reads.add(c);
    return c.future;
  }
}

void main() {
  test('a read begun before the job started does not end it', () async {
    final manager = _Manager();
    final jobs = LinuxJobController(manager);
    addTearDown(jobs.dispose);

    unawaited(jobs.refresh()); // e.g. the read made when the page opened
    expect(manager.reads, hasLength(1));

    await jobs.startScript(LinuxJobKind.backup, 'x', notice: 'n');
    expect(jobs.state.running, isTrue);

    // The early read ends: it saw no job.
    manager.reads.first.complete(LinuxJobState.none);
    await Future<void>.delayed(Duration.zero);

    expect(jobs.state.running, isTrue);
  });
}
