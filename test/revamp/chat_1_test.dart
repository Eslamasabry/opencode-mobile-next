import 'package:opencode_mobile/domain/server_gateway.dart';
// chat-1 (wave 2c): the transcript's messages, markdown and work line from
// kit parts only. Behaviour a person sees: a turn blocked on a request says
// "Waiting for you" (P7.5), a chat error's server words can be copied
// (P8.3), a reply the connection dropped says so, and KitTurn's additive
// segment / onMore / copyLabel seams.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import '../support/fake_server_gateway.dart';
import '../support/fake_event_channel.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/complete_message_history.dart';

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api(this.transcript) : super(baseUrl: 'http://localhost');

  final List<MessageWithParts> transcript;

  @override
  Future<List<MessageWithParts>> messages(String id) async => transcript;

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

MessageWithParts _message(
  String id,
  String role,
  List<Part> parts, {
  required int created,
  int? completed,
  String? error,
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: role,
    errorText: error,
    time: MsgTime(created: created, completed: completed),
  ),
  parts: parts,
);

Part _tool(String id, String name, String status) => Part(
  id: id,
  type: 'tool',
  callID: 'call-$id',
  toolName: name,
  toolState: ToolState.fromJson({
    'status': status,
    'input': {'command': 'flutter test'},
  }, toolName: name),
);

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  List<MessageWithParts> transcript, {
  bool busy = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(ProfileStore(prefs: prefs))
    ..api = _Api(transcript)
    ..status = StreamStatus.connected;
  if (busy) controller.busySessions.add('session-1');
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await _frames(tester);
  return controller;
}

/// Bounded pumps: a working chat animates forever.
Future<void> _frames(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Widget _host(Widget child) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(
    body: SingleChildScrollView(
      child: Padding(padding: const EdgeInsets.all(16), child: child),
    ),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('P7.5: work blocked on a permission says "Waiting for you", '
      'never that tools are running', (tester) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final controller = await _pumpChat(tester, [
      _message(
        'u1',
        'user',
        [Part(id: 'u1-t', type: 'text', text: 'Run the tests')],
        created: now - 5000,
        completed: now - 5000,
      ),
      _message('a1', 'assistant', [
        _tool('t1', 'read', 'completed'),
        _tool('t2', 'bash', 'running'),
      ], created: now - 4000),
    ], busy: true);

    // Before the request: the turn's one live line names the step in hand;
    // the folded work line waits until the turn is done.
    expect(find.text('Waiting for you'), findsNothing);
    expect(find.byKey(const ValueKey('kit-turn-live-line')), findsOneWidget);
    expect(find.byKey(const Key('work-group')), findsNothing);

    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.asked',
        properties: {
          'id': 'request-1',
          'sessionID': 'session-1',
          'permission': 'bash',
          'patterns': ['flutter test'],
          'metadata': <String, Object?>{},
          'always': <String>[],
        },
      ),
    );
    await _frames(tester);

    // The request card says what is asked; the running step is not repeated
    // above it, and nothing claims that tools are running meanwhile.
    expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
    expect(find.text('Running tools'), findsNothing);
    // Only the composer says it waits (vision 2026-10-20), no step line.
    expect(find.text('Waiting for you'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const Key('chat-composer-surface')),
        matching: find.text('Waiting for you'),
      ),
      findsOneWidget,
    );
    expect(find.byType(ToolCard), findsOneWidget, reason: 'only the read');
    // The command shows only on the card: its question and its block.
    expect(find.textContaining('flutter test'), findsNWidgets(2));
    expect(
      find.descendant(
        of: find.byType(KitRequestCard),
        matching: find.textContaining('flutter test'),
      ),
      findsNWidgets(2),
    );
  });

  testWidgets('P8.3: a chat error opens its server words, and they copy', (
    tester,
  ) async {
    final copied = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copied.add((call.arguments as Map)['text'] as String);
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    await _pumpChat(tester, [
      _message(
        'u1',
        'user',
        [Part(id: 'u1-t', type: 'text', text: 'Hello')],
        created: 1,
        completed: 1,
      ),
      _message(
        'a1',
        'assistant',
        const [],
        created: 2,
        completed: 3,
        error: 'upstream exploded at frame 42',
      ),
    ]);

    expect(find.byKey(const Key('error-card-generic')), findsOneWidget);
    await tester.tap(find.byKey(const Key('error-action-details')));
    await _frames(tester);
    expect(find.text('Error details'), findsWidgets);
    await tester.tap(find.byKey(const ValueKey('kit-details-copy-all')));
    await _frames(tester);
    expect(copied.single, contains('upstream exploded at frame 42'));
  });

  testWidgets('a reply the connection dropped says so at its end', (
    tester,
  ) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await _pumpChat(tester, [
      _message(
        'u1',
        'user',
        [Part(id: 'u1-t', type: 'text', text: 'Explain')],
        created: now - 60000,
        completed: now - 60000,
      ),
      // Never completed, and the conversation is no longer working on it.
      _message('a1', 'assistant', [
        Part(id: 'a1-t', type: 'text', text: 'The first half of'),
      ], created: now - 50000),
    ]);
    expect(
      find.text('The connection dropped before this reply finished.'),
      findsOneWidget,
    );
  });

  group('KitTurn segments (additive, chat-1)', () {
    testWidgets('a middle segment draws neither the phase line nor the '
        'footer; the last one draws both', (tester) async {
      KitTurn turn(KitTurnSegment segment) => KitTurn(
        segment: segment,
        phase: KitTurnPhase.stopped,
        blocks: const [KitText('Some words')],
        footer: KitTurnFooter(copyText: () => 'Some words'),
        copyKey: ValueKey('copy-$segment'),
      );
      await tester.pumpWidget(
        _host(
          Column(
            children: [turn(KitTurnSegment.middle), turn(KitTurnSegment.last)],
          ),
        ),
      );
      expect(find.text('You stopped this reply.'), findsOneWidget);
      expect(
        find.byKey(ValueKey('copy-${KitTurnSegment.middle}')),
        findsNothing,
      );
      expect(
        find.byKey(ValueKey('copy-${KitTurnSegment.last}')),
        findsOneWidget,
      );
    });

    testWidgets('onMore replaces the More menu; copyLabel names Copy', (
      tester,
    ) async {
      var more = 0;
      await tester.pumpWidget(
        _host(
          KitTurn(
            phase: KitTurnPhase.finished,
            blocks: const [KitText('Reply')],
            footer: KitTurnFooter(
              copyText: () => 'Reply',
              copyLabel: 'Copy reply so far',
              onMore: () => more++,
            ),
            moreKey: const ValueKey('more'),
            copyKey: const ValueKey('copy'),
          ),
        ),
      );
      expect(find.byTooltip('Copy reply so far'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('more')));
      await tester.pump();
      expect(more, 1);
    });
  });
}
