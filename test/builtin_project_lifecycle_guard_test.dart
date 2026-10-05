import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

// Native behavior is tested by the project-storage harness. These guards keep
// a later merge from disconnecting the safe storage helper from real launches,
// installation, removal, or the two native lifecycle admission locks.
void main() {
  const native =
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile';
  final linux = File('$native/BuiltinLinux.kt').readAsStringSync();
  final activity = File('$native/MainActivity.kt').readAsStringSync();
  final setup = File('$native/SetupRunner.kt').readAsStringSync();
  final terminal = File('$native/LocalTerminal.kt').readAsStringSync();
  final service = File('$native/BuiltinServerService.kt').readAsStringSync();

  String method(String source, String signature) {
    final start = source.indexOf(signature);
    expect(start, isNonNegative, reason: '$signature must remain integrated');
    final end = source.indexOf('\n    }', start);
    expect(end, greaterThan(start));
    return source.substring(start, end);
  }

  test('every proot command prepares and binds persistent projects', () {
    final body = method(linux, 'fun prootCommand(');
    expect(body, contains('check(!installingRuntime)'));
    expect(body, contains('projectStorage.prepare()'));
    expect(
      body,
      contains(
        r'--bind=${projectStorage.projects.absolutePath}:/root/projects',
      ),
    );
    expect(
      body.indexOf('projectStorage.prepare()'),
      lessThan(body.indexOf('return')),
    );
  });

  test('installation preserves projects before replacing a rootfs', () {
    final body = method(
      linux,
      'fun install(image: Image = imageForDevice(), progress: InstallProgress)',
    );
    expect(body, contains('projectStorage.prepare()'));
    expect(body, contains('projectStorage.resetRootfs()'));
    expect(body, isNot(contains('rootfs.deleteRecursively()')));
    expect(
      body.indexOf('projectStorage.prepare()'),
      lessThan(body.indexOf('projectStorage.resetRootfs()')),
    );
    expect(body, contains('installingRuntime = true'));
    expect(body, contains('finally'));
    expect(body, contains('synchronized(this) { installingRuntime = false }'));
    expect(
      linux,
      isNot(contains('@Synchronized\n    fun install(image:')),
      reason: 'Long downloads must not hold the runtime monitor.',
    );
  });

  test(
    'native removal independently enforces preservation and confirmation',
    () {
      final body = method(linux, 'fun uninstall(');
      expect(body, contains('alsoDeleteProjects: Boolean = false'));
      expect(
        body,
        contains(
          'require(!alsoDeleteProjects || confirmationName == "OpenCode")',
        ),
      );
      expect(body, contains('!installingRuntime'));
      expect(body, contains('!SetupRunner.get(context).running'));
      expect(
        body,
        contains('projectStorage.removeRuntime(alsoDeleteProjects)'),
      );
      expect(body, contains('stopAllServices()'));
      expect(body, contains('terminals.remove(it.id)'));
      expect(body, contains('processes.filter { it.isAlive }'));
      expect(
        body.indexOf('stopAllServices()'),
        lessThan(body.indexOf('projectStorage.removeRuntime')),
      );
    },
  );

  test('setup and terminal admission use the runtime lock first', () {
    for (final body in [
      method(setup, 'fun start(jobId:'),
      method(terminal, 'fun start(rows:'),
    ]) {
      expect(body, contains('synchronized(linux)'));
      final runtimeLock = body.indexOf('synchronized(linux)');
      final nestedLock = body.indexOf('synchronized(', runtimeLock + 1);
      expect(nestedLock, greaterThan(runtimeLock));
    }
    expect(
      terminal,
      isNot(contains('@Synchronized\n    fun start(rows:')),
      reason: 'Terminal-first locking deadlocks against runtime removal.',
    );
  });

  test(
    'reinstall recreates proc bindings instead of reusing deleted files',
    () {
      expect(linux, contains('private val fakeProcBinds: List<String> get()'));
      expect(
        linux,
        isNot(contains('private val fakeProcBinds: List<String> by lazy')),
      );
    },
  );

  test(
    'runtime status and installation dispatch never wait on the UI thread',
    () {
      expect(activity, contains('"status" -> inBackground {'));
      expect(activity, contains('"installUbuntu" -> inBackground {'));
      expect(activity, contains('"projectStorage" -> inBackground {'));
      expect(
        activity,
        contains('"uninstall", "removeRuntime" -> inBackground {'),
      );
    },
  );

  test('notification stop waits for runtime storage off the UI thread', () {
    final start = service.indexOf('if (intent?.action == ACTION_STOP)');
    final end = service.indexOf('return START_NOT_STICKY', start);
    // The stop action hands over to stopRuntime, which does the work.
    expect(service.substring(start, end), contains('stopRuntime(startId)'));
    final body = service.indexOf('private fun stopRuntime(');
    final stop = service.substring(body, service.indexOf('override fun', body));
    expect(stop, contains('Thread({'));
    expect(stop, contains('.start()'));
    expect(
      stop.indexOf('Thread({'),
      lessThan(stop.indexOf('.stopAllServices()')),
    );
  });
}
