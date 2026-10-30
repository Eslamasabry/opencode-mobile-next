// The chat page's rebuild budget while a reply streams (fix/perf-chat
// 2026-10-27): a connection notification that changes nothing the page
// draws does not rebuild it, and a stream flush works out the newest turn
// only, not the whole conversation again.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';
import 'support/fake_server_gateway.dart';

const _session = 'budget';

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api(this.history) : super(baseUrl: 'http://localhost');
  final List<MessageWithParts> history;

  @override
  Future<List<MessageWithParts>> messages(String id) async => history;
  @override
  Future<Session> session(String id) async => Session(id: id);
  @override
  Future<List<Session>> sessions() async => const [];
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<List<Todo>> todos(String id) async => const [];
  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];
}

/// [turns] finished turns, a prompt and its reply each; the newest reply
/// is the one the test streams into.
List<MessageWithParts> _history(int turns) => [
  for (var i = 0; i < turns * 2; i++)
    MessageWithParts(
      info: MessageInfo(
        id: i == turns * 2 - 1 ? 'tail' : 'm${i.toString().padLeft(4, '0')}',
        sessionID: _session,
        role: i.isEven ? 'user' : 'assistant',
        time: MsgTime(created: i + 1, completed: i + 2),
      ),
      parts: [
        Part(
          id: 'p$i',
          messageID: i == turns * 2 - 1 ? 'tail' : 'm$i',
          type: 'text',
          text: 'Words $i.',
        ),
      ],
    ),
];

Future<ConnectionController> _open(WidgetTester tester, int turns) async {
  SharedPreferences.setMockInitialValues({});
  final conn =
      ConnectionController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
        )
        ..api = _Api(_history(turns))
        ..status = StreamStatus.connected;
  addTearDown(conn.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: const ChatScreen(sessionID: _session),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return conn;
}

/// Counts the chat page's own rebuilds while [body] runs.
Future<int> _pageRebuilds(Future<void> Function() body) async {
  var rebuilds = 0;
  final previous = debugOnRebuildDirtyWidget;
  debugOnRebuildDirtyWidget = (element, _) {
    if (element.widget is ChatScreen) rebuilds++;
  };
  try {
    await body();
  } finally {
    debugOnRebuildDirtyWidget = previous;
  }
  return rebuilds;
}

void main() {
  testWidgets('streaming into the newest turn works out only that turn', (
    tester,
  ) async {
    const turns = 100;
    final conn = await _open(tester, turns);
    final before = debugChatDisplayPassMessages;
    for (var i = 0; i < 10; i++) {
      conn.handleEventForTesting(
        EventEnvelope(
          type: 'message.part.delta',
          properties: {
            'sessionID': _session,
            'messageID': 'tail',
            'partID': 'p${turns * 2 - 1}',
            'field': 'text',
            'delta': ' more',
          },
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
    }
    expect(find.textContaining('more more more'), findsOneWidget);
    // Ten flushes of a two-message turn, not ten passes over 200 messages.
    expect(debugChatDisplayPassMessages - before, lessThanOrEqualTo(10 * 2));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a notification about another conversation does not rebuild '
      'this one; one about this conversation does', (tester) async {
    final conn = await _open(tester, 3);
    final elsewhere = await _pageRebuilds(() async {
      for (var i = 0; i < 5; i++) {
        // Another conversation's reply moving on: the connection notifies.
        conn.handleEventForTesting(
          EventEnvelope(
            type: 'message.updated',
            properties: {
              'info': {
                'id': 'other-reply-$i',
                'sessionID': 'other',
                'role': 'assistant',
                'time': {'created': 100 + i},
              },
            },
          ),
        );
        await tester.pump();
      }
    });
    expect(elsewhere, 0);
    final here = await _pageRebuilds(() async {
      conn.handleEventForTesting(
        EventEnvelope(
          type: 'session.status',
          properties: {
            'sessionID': _session,
            'status': {'type': 'busy'},
          },
        ),
      );
      await tester.pump();
    });
    expect(here, greaterThan(0));
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
