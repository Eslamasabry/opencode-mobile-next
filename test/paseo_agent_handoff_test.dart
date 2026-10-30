import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

const _directory = '/work/app';
const _record = 'handoff_0123456789abcdef';

void main() {
  late FakeDaemon daemon;
  late PaseoGateway gateway;
  late Map<String, String> files;
  late Set<String> folders;
  var writeConflict = false;
  var readbackMismatch = false;
  var parentIsSymlink = false;
  var hostAllowed = true;
  var acquisitions = 0;
  var releases = 0;

  setUp(() async {
    daemon = FakeDaemon();
    files = {};
    folders = {'.'};
    writeConflict = false;
    readbackMismatch = false;
    parentIsSymlink = false;
    hostAllowed = true;
    acquisitions = 0;
    releases = 0;
    daemon.handlers['fs.entry.create.request'] = (request) {
      final parent = request['parentPath'] as String;
      final path = parent == '.'
          ? request['name'] as String
          : '$parent/${request['name']}';
      final success = !files.containsKey(path) && !folders.contains(path);
      if (success) {
        if (request['kind'] == 'directory') {
          folders.add(path);
        } else {
          files[path] = '';
        }
      }
      return (
        'fs.entry.create.response',
        {
          'cwd': _directory,
          'parentPath': parent,
          'path': success ? path : null,
          'success': success,
          'error': success ? null : 'already exists',
        },
      );
    };
    daemon.handlers['file_explorer_request'] = (request) {
      final path = request['path'] as String;
      if (request['mode'] == 'list') {
        return (
          'file_explorer_response',
          {
            'cwd': _directory,
            'path': path,
            'directory': {
              'entries': [
                {
                  'name': '.handoff',
                  'kind': parentIsSymlink ? 'file' : 'directory',
                },
              ],
            },
            'error': null,
          },
        );
      }
      final text = files[path];
      return (
        'file_explorer_response',
        {
          'cwd': _directory,
          'path': path,
          'error': text == null ? 'missing' : null,
          'file': {
            'path': path,
            'kind': 'text',
            'encoding': 'utf-8',
            'content': readbackMismatch && text != '' ? 'different' : text,
            'modifiedAt': '2026-10-14T00:00:00.000Z',
            'revision': 'revision-1',
          },
        },
      );
    };
    daemon.handlers['fs.file.write.request'] = (request) {
      expect(request['expectedRevision'], 'revision-1');
      expect(request['expectedModifiedAt'], '2026-10-14T00:00:00.000Z');
      if (!writeConflict) {
        files[request['path'] as String] = request['content'] as String;
      }
      return (
        'fs.file.write.response',
        {
          'result': {'status': writeConflict ? 'conflict' : 'written'},
        },
      );
    };
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: _directory,
      beforePayloadUse: () async {
        acquisitions++;
        if (!hostAllowed) throw StateError('Host maintenance');
      },
      afterPayloadUse: () => releases++,
    );
    await gateway.transport.connect();
    daemon.push('status', {
      'status': 'server_info',
      'version': '0.11.2',
      'features': {'workspaceFileEditing': true},
    });
    await Future<void>.delayed(Duration.zero);
  });
  tearDown(() => gateway.close());

  void prepareAgentCreation() {
    daemon.handlers['get_providers_snapshot_request'] = (_) => (
      'get_providers_snapshot_response',
      {
        'entries': [
          {
            'provider': 'claude',
            'status': 'ready',
            'models': [
              {'provider': 'claude', 'id': 'opus', 'label': 'Opus'},
            ],
            'modes': [
              {'id': 'default', 'label': 'Always Ask'},
            ],
          },
        ],
      },
    );
    daemon.handlers['create_agent_request'] = (_) => (
      'status',
      {
        'status': 'agent_created',
        'agent': agentJson('durable-agent', status: 'initializing'),
      },
    );
  }

  test(
    'handoff first prompt preserves live and durable navigation identities',
    () async {
      prepareAgentCreation();
      final draft = await gateway.createSession();
      var fenced = false;
      await gateway.promptAgentHandoff(
        draft.id,
        text: 'Read the handoff file and continue.',
        model: ModelRef(providerID: 'claude', modelID: 'opus'),
        beforeSend: () {
          expect(daemon.of('create_agent_request'), isEmpty);
          fenced = true;
        },
      );
      expect(fenced, isTrue);
      final request = daemon.of('create_agent_request').single;
      expect(request['initialPrompt'], 'Read the handoff file and continue.');
      expect((request['config'] as Map)['model'], 'opus');
      expect((request['config'] as Map)['cwd'], _directory);
      expect(gateway.handoffSessionId(draft.id), 'durable-agent');
      expect(gateway.handoffSessionId('durable-agent'), 'durable-agent');
      // The conversation opens on the agent backend's own gateway, which
      // knows only the daemon's id (owner's phone 2026-10-26: the draft id
      // there was "Couldn't open this conversation").
      expect(
        gateway.handoffNavigationSessionId('durable-agent'),
        'durable-agent',
      );
      expect(gateway.handoffNavigationSessionId(draft.id), 'durable-agent');
      expect(
        gateway.handoffNavigationSessionId('unseen-durable-id'),
        'unseen-durable-id',
      );
    },
  );

  test(
    'the continued conversation opens on another gateway of the same daemon',
    () async {
      prepareAgentCreation();
      final draft = await gateway.createSession();
      await gateway.promptAgentHandoff(
        draft.id,
        text: 'Read the handoff file and continue.',
        model: ModelRef(providerID: 'claude', modelID: 'opus'),
        beforeSend: () {},
      );
      // What AgentHandoffController.send returns for the sheet to open.
      final open = gateway.handoffNavigationSessionId(
        gateway.handoffSessionId(draft.id),
      );
      // The chat's own gateway (the agent backend) is a fresh one: the
      // daemon knows only its own id and answers others with `agent: null`.
      daemon.handlers['fetch_agent_request'] = (request) => (
        'fetch_agent_response',
        {
          'agent': request['agentId'] == 'durable-agent'
              ? agentJson('durable-agent')
              : null,
          'project': null,
          'error': request['agentId'] == 'durable-agent'
              ? null
              : 'Agent not found: ${request['agentId']}',
        },
      );
      daemon.handlers['fetch_agent_timeline_request'] = (request) => (
        'fetch_agent_timeline_response',
        {
          'agentId': request['agentId'],
          'epoch': 'epoch-1',
          'endCursor': {'epoch': 'epoch-1', 'seq': 1},
          'entries': [
            {
              'provider': 'claude',
              'item': {
                'type': 'user_message',
                'text': 'Read the handoff file and continue.',
                'messageId': 'u1',
              },
              'timestamp': '2026-10-26T03:35:27.922Z',
              'seqStart': 1,
            },
          ],
        },
      );
      // Its own link to the same daemon.
      final link = FakeDaemon()..handlers.addAll(daemon.handlers);
      final chat = PaseoGateway(
        transport: PaseoTransport(
          endpoint: 'ws://127.0.0.1:6767',
          socketFactory: (_, _) async => link,
        ),
        directory: _directory,
      );
      addTearDown(chat.close);
      final messages = await chat.messages(open);
      expect(
        messages.map((m) => m.parts.map((p) => p.text).join()),
        contains('Read the handoff file and continue.'),
      );
    },
  );

  test(
    'handoff dispatch fence can refuse creation after provider preflight',
    () async {
      prepareAgentCreation();
      final draft = await gateway.createSession();
      await expectLater(
        gateway.promptAgentHandoff(
          draft.id,
          text: 'Do not send after scope deletion.',
          model: ModelRef(providerID: 'claude', modelID: 'opus'),
          beforeSend: () => throw StateError('Scope deleted'),
        ),
        throwsA(isA<StateError>()),
      );
      expect(daemon.of('get_providers_snapshot_request'), hasLength(1));
      expect(daemon.of('create_agent_request'), isEmpty);
      expect(gateway.handoffSessionId(draft.id), draft.id);
    },
  );

  Future<String> write({
    String directory = _directory,
    String id = _record,
    String markdown = 'User: Continue the work.\n',
  }) => gateway.writeAgentHandoff(
    directory: directory,
    recordId: id,
    markdown: markdown,
  );

  test(
    'writes and verifies ignore before record in exclusively created folder',
    () async {
      final path = await write();
      expect(path, '.handoff/$_record/record.md');
      expect(files[path], 'User: Continue the work.\n');
      expect(files['.handoff/$_record/.gitignore'], contains('\n*\n'));
      final writes = daemon.sent
          .where((r) => r['type'] == 'fs.file.write.request')
          .toList();
      expect(writes.map((r) => r['path']), [
        '.handoff/$_record/.gitignore',
        '.handoff/$_record/record.md',
      ]);
      expect(gateway.capabilities.agentHandoffFiles, isTrue);
      expect(gateway.capabilities.withGenUi(true).agentHandoffFiles, isTrue);
      expect(const ServerCapabilities().agentHandoffFiles, isFalse);
    },
  );

  test(
    'file mutation holds the host lease and denied host cannot write',
    () async {
      await write();
      expect(acquisitions, 1);
      expect(releases, 1);
      final sent = daemon.sent.length;
      hostAllowed = false;
      await expectLater(write(id: 'denied'), throwsA(isA<PaseoFailure>()));
      expect(daemon.sent.length, sent);
      expect(acquisitions, 2);
      expect(releases, 1);
    },
  );

  test('existing transfer folder is never overwritten or reused', () async {
    await write();
    final before = Map<String, String>.of(files);
    await expectLater(
      write(markdown: 'different'),
      throwsA(isA<PaseoFailure>()),
    );
    expect(files, before);
  });

  test('existing shared ignore file remains untouched', () async {
    folders.add('.handoff');
    files['.handoff/.gitignore'] = 'owner rules';
    await write();
    expect(files['.handoff/.gitignore'], 'owner rules');
  });

  test('parent symlink is refused before record or ignore creation', () async {
    folders.add('.handoff');
    parentIsSymlink = true;
    await expectLater(write(), throwsA(isA<PaseoFailure>()));
    expect(files, isEmpty);
    expect(folders, isNot(contains('.handoff/$_record')));
  });

  test('missing negotiated feature and disconnect disable writes', () async {
    daemon.push('status', {'status': 'server_info', 'version': '0.11.2'});
    await Future<void>.delayed(Duration.zero);
    expect(gateway.capabilities.agentHandoffFiles, isFalse);
    await expectLater(write(), throwsA(isA<PaseoFailure>()));
    expect(daemon.sent, isEmpty);
    await gateway.transport.close();
    expect(gateway.transport.workspaceFileEditing, isFalse);
  });

  test(
    'invalid scope, path identifiers and byte limits do not dispatch',
    () async {
      for (final id in ['../escape', 'id/name', '', 'x' * 129]) {
        await expectLater(write(id: id), throwsA(isA<PaseoFailure>()));
      }
      await expectLater(
        write(directory: '/different'),
        throwsA(isA<PaseoFailure>()),
      );
      await expectLater(
        write(markdown: 'é' * 50000),
        throwsA(isA<PaseoFailure>()),
      );
      await expectLater(
        write(
          markdown: 'Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456',
        ),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.sent, isEmpty);
    },
  );

  test(
    'ignore conflict or readback mismatch never writes conversation content',
    () async {
      writeConflict = true;
      await expectLater(write(), throwsA(isA<PaseoFailure>()));
      expect(files, isNot(contains('.handoff/$_record/record.md')));
      writeConflict = false;
      readbackMismatch = true;
      await expectLater(
        write(id: 'another_record'),
        throwsA(isA<PaseoFailure>()),
      );
      expect(files, isNot(contains('.handoff/another_record/record.md')));
    },
  );
}
