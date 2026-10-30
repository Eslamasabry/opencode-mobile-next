import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/models.dart' show EventEnvelope;
import 'package:opencode_mobile/paseo/chat_feed_source.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/mappers.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson, stream;

/// A phone agent's conversation says Working only on live evidence.
///
/// The Paseo daemon answers for an agent it has not loaded (all of them,
/// after its helper restarts) from the agent's saved record, whose `status`
/// is the last one saved. A helper stopped mid-turn (Android stopping it with
/// the app, a phone restart) leaves `running` there for good; the list showed
/// those conversations as Working days later (owner's phone, 2026-10-20).
void main() {
  late FakeDaemon daemon;
  late PaseoGateway gateway;
  late PaseoChatFeedSource source;
  var entries = <Map<String, dynamic>>[];

  String ago(Duration age) =>
      DateTime.now().toUtc().subtract(age).toIso8601String();

  setUp(() {
    daemon = FakeDaemon();
    entries = [];
    daemon.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': entries,
        'pageInfo': {'hasMore': false, 'nextCursor': null},
      },
    );
    // A fresh gateway: the app just started (or the phone restarted), so
    // nothing of this conversation is known but what the daemon answers.
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: '/work/app',
    );
    source = PaseoChatFeedSource(gateway);
  });

  tearDown(() async {
    await source.dispose();
    gateway.close();
    await Future<void>.delayed(Duration.zero);
  });

  ChatStatus statusOf(String id) =>
      source.chatFeed().items.singleWhere((row) => row.sessionID == id).status;

  test('a saved record still saying running two days later is not Working '
      'after a restart', () async {
    entries = [
      {
        'agent': agentJson(
          'stale',
          status: 'running',
          loaded: false,
          updatedAt: ago(const Duration(days: 2)),
        ),
      },
      {'agent': agentJson('quiet', updatedAt: ago(const Duration(days: 2)))},
    ];
    await source.refreshChatFeed();
    expect(statusOf('stale'), ChatStatus.idle);
    expect(statusOf('quiet'), ChatStatus.idle);
    expect((await gateway.sessionStatuses())['stale'], 'idle');
  });

  test('a loaded agent running a long turn stays Working', () async {
    // Its record last changed two days ago, but the daemon holds the agent
    // and names the turn in progress: that is live.
    entries = [
      {
        'agent': agentJson(
          'live',
          status: 'running',
          updatedAt: ago(const Duration(days: 2)),
        ),
      },
    ];
    await source.refreshChatFeed();
    expect(statusOf('live'), ChatStatus.running);
  });

  test('a turn this gateway watched start stays Working over a stale '
      'record', () async {
    entries = [
      {
        'agent': agentJson(
          'streamed',
          status: 'running',
          loaded: false,
          updatedAt: ago(const Duration(days: 2)),
        ),
      },
    ];
    await source.refreshChatFeed();
    expect(statusOf('streamed'), ChatStatus.idle);
    daemon.push(
      'agent_stream',
      stream('streamed', {
        'type': 'turn_started',
        'provider': 'claude',
      }, seq: 1),
    );
    await Future<void>.delayed(Duration.zero);
    await source.refreshChatFeed();
    expect(statusOf('streamed'), ChatStatus.running);
  });

  test('a recent record says Working only until its bound, then reads '
      'idle by itself', () async {
    // Saved just under the bound ago: believed for a few hundred
    // milliseconds more, then the session reports a turn end.
    entries = [
      {
        'agent': agentJson(
          'recent',
          status: 'running',
          loaded: false,
          updatedAt: ago(
            paseoRecordBusyFreshFor - const Duration(milliseconds: 300),
          ),
        ),
      },
    ];
    final events = <EventEnvelope>[];
    final channel = gateway.openEventChannel(
      onEvent: events.add,
      onStatus: (_) {},
    );
    await source.refreshChatFeed();
    expect(statusOf('recent'), ChatStatus.running);
    await Future<void>.delayed(const Duration(milliseconds: 600));
    expect((await gateway.sessionStatuses())['recent'], 'idle');
    expect(
      events.where(
        (event) =>
            event.type == 'session.idle' &&
            event.properties['sessionID'] == 'recent',
      ),
      isNotEmpty,
    );
    await source.refreshChatFeed();
    expect(statusOf('recent'), ChatStatus.idle);
    await channel.dispose();
  });

  group('paseoSessionStatus', () {
    final now = DateTime.utc(2026, 10, 20, 12);
    Map<String, dynamic> record(String status, Duration age) => agentJson(
      'a',
      status: status,
      loaded: false,
      updatedAt: now.subtract(age).toIso8601String(),
    );

    test('a record is believed running only within the bound', () {
      expect(
        paseoSessionStatus(
          record('running', const Duration(days: 2)),
          now: now,
        ),
        'idle',
      );
      expect(
        paseoSessionStatus(
          record('initializing', const Duration(hours: 1)),
          now: now,
        ),
        'idle',
      );
      expect(
        paseoSessionStatus(
          record('running', const Duration(minutes: 1)),
          now: now,
        ),
        'busy',
      );
    });

    test('a loaded agent is live whatever its age', () {
      final live = agentJson(
        'a',
        status: 'running',
        updatedAt: now.subtract(const Duration(days: 2)).toIso8601String(),
      );
      expect(paseoSessionStatus(live, now: now), 'busy');
      expect(paseoRecordBusyUntil(live), isNull);
      expect(
        paseoSessionStatus({...live, 'activeTurn': null}, now: now),
        'busy',
      );
    });

    test('a record without a readable time is not running', () {
      final unknown = {...record('running', Duration.zero)}
        ..remove('updatedAt');
      expect(paseoSessionStatus(unknown, now: now), 'idle');
    });
  });
}
