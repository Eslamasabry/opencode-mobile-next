// Idle stored agents stay asleep and keep their list time.
//
// Incident 2026-10-27 (build 3760): after a reinstall two Kilo chats idle
// since 2026-10-25 jumped to "Today / Just now" and two `kilo acp` processes
// started. The daemon loads a stored agent for `fetch_agent_timeline` and
// re-stamps its `updatedAt`; background gen-UI recovery read the history of
// the 20 most recent chats after every reconnect.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/mappers.dart' show paseoSessionStatus;
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;

const _directory = '/work/idle';
const _created = '2026-10-25T17:04:30.000Z';
const _prompt = '2026-10-25T17:04:30.000Z';
const _asleep = '2026-10-25T17:04:31.547Z';
const _woken = '2026-10-27T01:17:22.000Z';

void main() {
  late FakePaseoSocket socket;
  late PaseoGateway gateway;
  late Map<String, dynamic> agent;

  setUp(() {
    agent = {
      'id': 'kilo',
      'provider': 'kilo',
      'cwd': _directory,
      'title': 'Idle Kilo chat',
      'status': 'idle',
      'createdAt': _created,
      'updatedAt': _asleep,
      'lastUserMessageAt': _prompt,
      'activeTurn': null,
      'pendingPermissions': <Object>[],
    };
    socket = FakePaseoSocket();
    socket.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': [
          {'agent': agent},
        ],
        'pageInfo': {'hasMore': false},
      },
    );
    socket.handlers['fetch_agent_timeline_request'] = (request) => (
      'fetch_agent_timeline_response',
      {
        'agentId': request['agentId'],
        'agent': agent,
        'entries': <Object>[],
        'hasOlder': false,
        'hasNewer': false,
      },
    );
    socket.handlers['fetch_agent_request'] = (_) =>
        ('fetch_agent_response', {'agent': agent});
    gateway = PaseoGateway(
      directory: _directory,
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:4099',
        socketFactory: (_, _) async => socket,
      ),
    );
  });

  tearDown(() => gateway.close());

  Future<int?> listed() async {
    final page = await gateway.sessionPage();
    return page.items.single.time?.updated;
  }

  group('waking', () {
    test(
      'a listed idle agent may not be loaded by a background read',
      () async {
        await gateway.sessionPage();
        expect(gateway.genUiMayLoad('kilo'), isFalse);
        expect(gateway.genUiMayLoad('unknown'), isFalse);
        expect(socket.of('fetch_agent_timeline_request'), isEmpty);
      },
    );

    test('opening the chat, a running turn or a send wakes it', () async {
      await gateway.sessionPage();
      await gateway.messages('kilo');
      expect(gateway.genUiMayLoad('kilo'), isTrue);
    });

    test('a running agent and a watched turn count as awake', () async {
      agent['status'] = 'running';
      await gateway.sessionPage();
      expect(gateway.genUiMayLoad('kilo'), isTrue);
      agent['status'] = 'idle';
      await gateway.sessionPage();
      // The turn was seen: its end is still this app's to reconcile.
      expect(gateway.genUiMayLoad('kilo'), isTrue);
    });
  });

  group('list time', () {
    test('a rise with no prompt and no turn keeps the earlier time', () async {
      final before = await listed();
      expect(before, DateTime.parse(_asleep).millisecondsSinceEpoch);
      agent['updatedAt'] = _woken;
      expect(await listed(), before);
    });

    test('a new prompt moves the time', () async {
      await listed();
      agent['updatedAt'] = _woken;
      agent['lastUserMessageAt'] = _woken;
      expect(await listed(), DateTime.parse(_woken).millisecondsSinceEpoch);
    });

    test('a long turn that ends hours later still counts as recent', () async {
      await listed();
      agent['status'] = 'running';
      agent['updatedAt'] = '2026-10-25T18:00:00.000Z';
      await listed();
      agent['status'] = 'idle';
      agent['updatedAt'] = '2026-10-25T21:30:00.000Z';
      expect(
        await listed(),
        DateTime.parse('2026-10-25T21:30:00.000Z').millisecondsSinceEpoch,
      );
      // Once that turn is over, an unexplained rise is held back again.
      agent['updatedAt'] = _woken;
      expect(
        await listed(),
        DateTime.parse('2026-10-25T21:30:00.000Z').millisecondsSinceEpoch,
      );
    });

    // Jitter J10 (2026-10-28): opening an idle Kilo chat and going back
    // brought it to the top as "Done · Just now" though nothing was sent.
    // Reading its history loads it: the daemon says "initializing" with no
    // turn, then idle, and re-stamps its time.
    test(
      'a chat the daemon only loaded keeps its time and is not run',
      () async {
        final before = await listed();
        agent['status'] = 'initializing';
        agent['updatedAt'] = _woken;
        expect(await listed(), before);
        expect(paseoSessionStatus(agent), 'idle');
        agent['status'] = 'idle';
        agent['updatedAt'] = '2026-10-27T01:17:40.000Z';
        expect(await listed(), before);
      },
    );

    test('a turn after a load still moves the time', () async {
      await listed();
      agent['status'] = 'initializing';
      agent['updatedAt'] = _woken;
      await listed();
      agent['status'] = 'running';
      agent['activeTurn'] = {'turnId': 't1', 'startedAt': _woken};
      expect(paseoSessionStatus(agent), 'busy');
      expect(await listed(), DateTime.parse(_woken).millisecondsSinceEpoch);
    });

    test('a first sight keeps the time the list showed last run', () async {
      gateway.savedListTime = (id) =>
          id == 'kilo' ? DateTime.parse(_asleep).millisecondsSinceEpoch : null;
      agent['updatedAt'] = _woken;
      expect(await listed(), DateTime.parse(_asleep).millisecondsSinceEpoch);
    });

    test('a prompt sent after the saved time moves it', () async {
      gateway.savedListTime = (id) =>
          id == 'kilo' ? DateTime.parse(_asleep).millisecondsSinceEpoch : null;
      agent['updatedAt'] = _woken;
      agent['lastUserMessageAt'] = _woken;
      expect(await listed(), DateTime.parse(_woken).millisecondsSinceEpoch);
    });

    test(
      'a first sight is trusted and an earlier time never rewinds',
      () async {
        agent['updatedAt'] = _woken;
        final first = await listed();
        expect(first, DateTime.parse(_woken).millisecondsSinceEpoch);
        agent['updatedAt'] = _asleep;
        expect(await listed(), DateTime.parse(_asleep).millisecondsSinceEpoch);
      },
    );
  });
}
