import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

const _dir = '/work/app';

Map<String, dynamic> _entry(
  String handle, {
  String? title,
  String? first,
  String at = '2026-10-09T10:00:00.000Z',
  String cwd = _dir,
  String provider = 'claude',
}) => {
  'providerId': provider,
  'providerLabel': 'Claude Code',
  'providerHandleId': handle,
  'cwd': cwd,
  'title': title,
  'firstPromptPreview': first,
  'lastPromptPreview': null,
  'lastActivityAt': at,
};

/// Conversations begun in Claude Code's own app, found by the daemon and
/// brought into this project's list.
void main() {
  late FakeDaemon daemon;
  late PaseoGateway gateway;

  setUp(() {
    daemon = FakeDaemon();
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: _dir,
    );
    addTearDown(gateway.close);
  });

  test(
    'lists this project\'s Claude Code conversations, newest first',
    () async {
      daemon.handlers['fetch_recent_provider_sessions_request'] = (_) => (
        'fetch_recent_provider_sessions_response',
        {
          'entries': [
            _entry(
              'old',
              first: 'Fix the login bug',
              at: '2026-10-01T10:00:00Z',
            ),
            _entry('new', title: 'Release notes', at: '2026-10-09T10:00:00Z'),
            _entry('bad id\u0000', first: 'x'),
            {'providerId': 'claude'},
          ],
          'filteredAlreadyImportedCount': 2,
        },
      );
      final found = await gateway.importableConversations();
      final sent = daemon.of('fetch_recent_provider_sessions_request').single;
      expect(sent['cwd'], _dir);
      expect(sent['providers'], ['claude']);
      expect(found.items.map((c) => c.handle), ['new', 'old']);
      expect(found.items.first.displayTitle, 'Release notes');
      expect(found.items[1].displayTitle, 'Fix the login bug');
      expect(found.alreadyImported, 2);
      expect(gateway.providerImportDirectory, _dir);
    },
  );

  test(
    'importing sends the agent, its handle and folder, then reads it',
    () async {
      daemon.handlers['import_agent_request'] = (_) => (
        'status',
        {
          'status': 'agent_resumed',
          'agentId': 'imp-1',
          'agent': agentJson('imp-1'),
        },
      );
      daemon.handlers['fetch_agent_request'] = (_) =>
          ('fetch_agent_response', {'agent': agentJson('imp-1')});
      final session = await gateway.importConversation(
        ImportableConversationFixture.one('native-1'),
      );
      final sent = daemon.of('import_agent_request').single;
      expect(sent['providerId'], 'claude');
      expect(sent['providerHandleId'], 'native-1');
      expect(sent['cwd'], _dir);
      expect(session.id, 'imp-1');
      expect(session.directory, _dir);
    },
  );

  test('a refusal is a failure, not a conversation', () async {
    daemon.handlers['import_agent_request'] = (_) => (
      'status',
      {'status': 'agent_create_failed', 'error': 'session file is gone'},
    );
    await expectLater(
      gateway.importConversation(ImportableConversationFixture.one('x')),
      throwsA(isA<PaseoFailure>()),
    );
  });
}

class ImportableConversationFixture {
  static ImportableConversation one(String handle) => ImportableConversation(
    agentId: 'claude',
    handle: handle,
    directory: _dir,
    lastUsed: DateTime.utc(2026, 10, 9),
  );
}
