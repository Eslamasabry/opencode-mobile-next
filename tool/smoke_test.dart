// Integration smoke test against a real OpenCode server.
// Usage: dart run tool/smoke_test.dart <baseUrl> [directory] [workspace]
import 'dart:async';
import 'dart:io';

import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';

Future<void> main(List<String> args) async {
  final baseUrl = args.isNotEmpty ? args[0] : 'http://127.0.0.1:4123';
  final api = OpenCodeApi(baseUrl: baseUrl);
  final directory = args.length > 1 && args[1].trim().isNotEmpty
      ? args[1].trim()
      : null;
  final workspace = args.length > 2 && args[2].trim().isNotEmpty
      ? args[2].trim()
      : null;
  api.setLocation(directory: directory, workspace: workspace);

  var failures = 0;
  Session? smokeSession;
  EventStream? stream;

  void check(String name, bool ok, [String extra = '']) {
    stdout.writeln(
      '${ok ? 'PASS' : 'FAIL'}  $name ${extra.isEmpty ? '' : '- $extra'}',
    );
    if (!ok) failures++;
  }

  try {
    final health = await api.health();
    check('health', health.healthy, 'version=${health.version}');
    if (directory != null) {
      check(
        'location scope',
        api.directory == directory && api.workspace == workspace,
        [directory, ?workspace].join(' · '),
      );
    }

    final gotConnectedEvent = Completer<bool>();
    stream = EventStream(
      api: api,
      onStatus: (_) {},
      onEvent: (event) {
        if (event.type == 'server.connected' &&
            !gotConnectedEvent.isCompleted) {
          gotConnectedEvent.complete(true);
        }
      },
      onError: (_) {
        if (!gotConnectedEvent.isCompleted) {
          gotConnectedEvent.complete(false);
        }
      },
    )..start();

    await api.sessions();
    check('list sessions', true);
    final session = await api.createSession();
    smokeSession = session;
    check('create session', session.id.isNotEmpty);

    await api.renameSession(session.id, 'opencode-mobile-smoke');
    final renamed = await api.session(session.id);
    check(
      'rename session',
      renamed.title == 'opencode-mobile-smoke',
      'title=${renamed.title}',
    );

    var messages = await api.messages(session.id);
    check('messages empty', messages.isEmpty, '${messages.length} messages');

    final providers = await api.providers();
    check(
      'providers catalog',
      providers.providers.isNotEmpty,
      '${providers.providers.length} providers',
    );
    final agents = await api.agents();
    check(
      'agents catalog',
      agents.isNotEmpty,
      agents.map((agent) => agent.name).take(5).join(','),
    );

    String? agent;
    for (final candidate in agents) {
      if (candidate.name == 'build') {
        agent = candidate.name;
        break;
      }
    }
    if (agent == null) {
      check('model-free shell', false, 'build agent unavailable');
    } else {
      const expectedOutput = 'opencode-mobile-smoke-output';
      await api.shell(
        session.id,
        command: 'printf $expectedOutput',
        agent: agent,
      );
      messages = await api.messages(session.id);
      final outputs = messages
          .expand((message) => message.parts)
          .where((part) => part.type == 'tool')
          .map((part) => part.toolState.output?.trim())
          .whereType<String>();
      check(
        'model-free shell and hydration',
        outputs.contains(expectedOutput),
        outputs.isEmpty ? 'no completed tool output' : outputs.join(' | '),
      );
    }

    final rootFiles = await api.listFiles('');
    check(
      'file listing',
      rootFiles.isNotEmpty,
      '${rootFiles.length} entries at project root',
    );

    try {
      final found = await api.findFile('pubspec');
      check('file search', found.isNotEmpty, found.take(2).join(', '));
    } catch (error) {
      check('file search', false, '$error');
    }

    final streamConnected = await gotConnectedEvent.future.timeout(
      const Duration(seconds: 20),
      onTimeout: () => false,
    );
    check('SSE event stream', streamConnected);
  } catch (error, stackTrace) {
    check('unexpected smoke failure', false, '$error');
    stderr.writeln(stackTrace);
  } finally {
    await stream?.dispose();
    final session = smokeSession;
    if (session != null) {
      try {
        await api.deleteSession(session.id);
        check('cleanup session', true);
      } catch (error) {
        check('cleanup session', false, '$error');
      }
    }
    api.close();
  }

  stdout.writeln(
    failures == 0 ? '\nALL CHECKS PASSED' : '\n$failures CHECK(S) FAILED',
  );
  exit(failures == 0 ? 0 : 1);
}
