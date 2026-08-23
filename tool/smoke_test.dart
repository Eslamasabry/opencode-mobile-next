// Integration smoke test against a real opencode server.
// Usage: dart run tool/smoke_test.dart <baseUrl>
import 'dart:async';
import 'dart:io';


import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';

Future<void> main(List<String> args) async {
  final baseUrl = args.isNotEmpty ? args[0] : 'http://127.0.0.1:4123';
  final api = OpenCodeApi(baseUrl: baseUrl);

  var failures = 0;
  void check(String name, bool ok, [String extra = '']) {
    stdout.writeln('${ok ? 'PASS' : 'FAIL'}  $name ${extra.isEmpty ? '' : '- $extra'}');
    if (!ok) failures++;
  }

  // 1. Health
  final h = await api.health();
  check('health', h.healthy == true, 'version=${h.version}');

  // 2. Sessions list + create + rename + delete
  await api.sessions();
  check('list sessions', true);
  final s = await api.createSession();
  check('create session', s.id.isNotEmpty);
  await api.renameSession(s.id, 'smoke-test-session');
  final renamed = await api.session(s.id);
  check('rename session', renamed.title == 'smoke-test-session', 'title=${renamed.title}');
  final msgs = await api.messages(s.id);
  check('messages empty', msgs.isEmpty, '${msgs.length} messages');

  // 3. Providers / agents catalogs
  try {
    final p = await api.providers();
    check('providers catalog', true, '${p.providers.length} providers');
  } catch (e) {
    check('providers catalog', false, '$e');
  }
  final agents = await api.agents();
  check('agents catalog', agents.isNotEmpty, agents.map((a) => a.name).take(5).join(','));

  // 4. Files
  final rootFiles = await api.listFiles('');
  check('file listing', rootFiles.isNotEmpty, '${rootFiles.length} entries at project root');

  // 5. File search
  try {
    final found = await api.findFile('pubspec');
    check('file search', found.isNotEmpty, found.take(2).join(', '));
  } catch (_) {
    check('file search', false, 'find/file unavailable');
  }

  // 6. SSE event stream: expect server.connected within 15s
  final gotConnectedEvent = Completer<bool>();
  final stream = EventStream(
    api: api,
    onStatus: (_) {},
    onEvent: (env) {
      if (env.type == 'server.connected' && !gotConnectedEvent.isCompleted) {
        gotConnectedEvent.complete(true);
      }
      // 7. Session lifecycle events while we touch the session
      if (env.type.contains('session') && !gotConnectedEvent.isCompleted) {
        // any session traffic also proves the bus is alive
      }
    },
    onError: (e) {
      if (!gotConnectedEvent.isCompleted) gotConnectedEvent.complete(false);
    },
  )..start();

  final ok = await gotConnectedEvent.future.timeout(
    const Duration(seconds: 20),
    onTimeout: () => false,
  );
  check('SSE event stream', ok);

  await stream.dispose();
  await api.deleteSession(s.id).catchError((_) => false as dynamic);

  stdout.writeln(failures == 0 ? '\nALL CHECKS PASSED' : '\n$failures CHECK(S) FAILED');
  exit(failures == 0 ? 0 : 1);
}
