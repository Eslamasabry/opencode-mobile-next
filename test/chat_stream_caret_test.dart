// The typing caret in the real chat: a thin accent mark after the words of
// the reply still being written (KitStreamCaret, the Appearance preview's
// caret). It shows only on the block the agent is writing right now, goes
// the moment that block, the turn or the reply ends, and holds still under
// reduced motion.
//
// OpenCode 2 events run through the real v2 adapter: `session.text.delta`
// grows the block, `session.text.ended` finishes it (v1's `time.end`).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_stream_caret.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';
import 'support/fake_server_gateway.dart';

const _session = 'session-1';

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api(this.transcript) : super(baseUrl: 'http://localhost');

  final List<MessageWithParts> transcript;

  @override
  ServerCapabilities get capabilities => api2ServerCapabilities;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {
    _session: 'busy',
  };

  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => transcript;

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileDiff>> diff(String id) async => const [];

  @override
  Future<List<Todo>> todos(String id) async => const [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];
}

MessageWithParts _prompt(String id, String text, int created) =>
    MessageWithParts(
      info: MessageInfo(
        id: id,
        sessionID: _session,
        role: 'user',
        time: MsgTime(created: created, completed: created),
      ),
      parts: [Part(id: '$id-text', messageID: id, type: 'text', text: text)],
    );

MessageWithParts _reply(
  String id,
  String text, {
  required int created,
  bool done = true,
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: _session,
    role: 'assistant',
    finish: done ? 'stop' : null,
    time: MsgTime(created: created, completed: done ? created + 1 : null),
  ),
  parts: [Part(id: '$id-text', messageID: id, type: 'text', text: text)],
);

/// The caret inside the reply block whose words contain [text].
Finder _caretAfter(String text) => find.descendant(
  of: find.ancestor(
    of: find.textContaining(text, findRichText: true),
    matching: find.byKey(const Key('assistant-text-block')),
  ),
  matching: find.byType(KitStreamCaret),
);

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  List<MessageWithParts> transcript, {
  bool reducedMotion = false,
}) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 2.6;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.upsert(
    ServerProfile(id: 'p1', name: 'Test', baseUrl: 'http://localhost:4096'),
  );
  await store.setActiveId('p1');
  final controller = ConnectionController(store)
    ..api = _Api(transcript)
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, inner) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(disableAnimations: reducedMotion),
          child: inner!,
        ),
        home: ChatScreen(
          sessionID: _session,
          handoffStore: ReviewHandoffStore(),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump();
  await tester.pump(const Duration(seconds: 1));
  return controller;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('the caret follows a streaming reply and goes once that block '
      'ends', (tester) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final controller = await _pumpChat(tester, [
      _prompt('msg_u1', 'Why does checkout hang?', now - 2000),
    ]);
    final adapter = Api2EventAdapter();
    void v2(String type, Map<String, dynamic> data) =>
        adaptApi2EventJson(adapter, {
          'type': type,
          'data': {'sessionID': _session, ...data},
        }).forEach(controller.handleEventForTesting);
    const step = 'msg_a1';

    v2('session.execution.started', const {});
    v2('session.step.started', {'assistantMessageID': step, 'agent': 'build'});
    v2('session.text.started', {'assistantMessageID': step, 'ordinal': 0});
    v2('session.text.delta', {
      'assistantMessageID': step,
      'ordinal': 0,
      'delta': 'The fake clock never ',
    });
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(_caretAfter('The fake clock never'), findsOneWidget);

    // Still the block being written: the caret stays at its end.
    v2('session.text.delta', {
      'assistantMessageID': step,
      'ordinal': 0,
      'delta': 'advances the timer.',
    });
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(_caretAfter('advances the timer.'), findsOneWidget);

    // The block is done although the step still runs: no caret.
    v2('session.text.ended', {
      'assistantMessageID': step,
      'ordinal': 0,
      'text': 'The fake clock never advances the timer.',
    });
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(
      find.textContaining('advances the timer.', findRichText: true),
      findsOneWidget,
    );
    expect(find.byType(KitStreamCaret), findsNothing);
  });

  testWidgets('the caret goes when the turn ends mid-block', (tester) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final controller = await _pumpChat(tester, [
      _prompt('msg_u1', 'Why does checkout hang?', now - 2000),
    ]);
    final adapter = Api2EventAdapter();
    void v2(String type, Map<String, dynamic> data) =>
        adaptApi2EventJson(adapter, {
          'type': type,
          'data': {'sessionID': _session, ...data},
        }).forEach(controller.handleEventForTesting);
    const step = 'msg_a1';

    v2('session.execution.started', const {});
    v2('session.step.started', {'assistantMessageID': step, 'agent': 'build'});
    v2('session.text.started', {'assistantMessageID': step, 'ordinal': 0});
    v2('session.text.delta', {
      'assistantMessageID': step,
      'ordinal': 0,
      'delta': 'Looking at the test',
    });
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(_caretAfter('Looking at the test'), findsOneWidget);

    // The step ends with no `text.ended` of its own (a lost event).
    v2('session.step.ended', {
      'assistantMessageID': step,
      'finish': 'stop',
      'cost': 0,
      'tokens': {
        'input': 10,
        'output': 5,
        'reasoning': 0,
        'cache': {'read': 0, 'write': 0},
      },
    });
    v2('session.execution.succeeded', const {});
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(
      find.textContaining('Looking at the test', findRichText: true),
      findsOneWidget,
    );
    expect(find.byType(KitStreamCaret), findsNothing);
  });

  testWidgets('a finished reply has no caret; only the streaming one does', (
    tester,
  ) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await _pumpChat(tester, [
      _prompt('msg_u1', 'First question', now - 9000),
      _reply('msg_a1', 'An older, finished answer.', created: now - 8000),
      _prompt('msg_u2', 'Second question', now - 3000),
      _reply('msg_a2', 'A reply still being', created: now - 2000, done: false),
    ]);
    expect(_caretAfter('A reply still being'), findsOneWidget);
    expect(_caretAfter('An older, finished answer.'), findsNothing);
    expect(find.byType(KitStreamCaret), findsOneWidget);
  });

  testWidgets('a conversation of finished replies shows no caret', (
    tester,
  ) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await _pumpChat(tester, [
      _prompt('msg_u1', 'First question', now - 9000),
      _reply('msg_a1', 'All done here.', created: now - 8000),
    ]);
    expect(
      find.textContaining('All done here.', findRichText: true),
      findsOneWidget,
    );
    expect(find.byType(KitStreamCaret), findsNothing);
  });

  testWidgets('under reduced motion the caret holds still', (tester) async {
    KitMotion.loops = true;
    addTearDown(() => KitMotion.loops = false);
    final now = DateTime.now().millisecondsSinceEpoch;
    await _pumpChat(tester, [
      _prompt('msg_u1', 'Why does checkout hang?', now - 3000),
      _reply('msg_a1', 'Reading the test', created: now - 2000, done: false),
    ], reducedMotion: true);
    double opacity() => tester
        .widget<FadeTransition>(
          find.descendant(
            of: _caretAfter('Reading the test'),
            matching: find.byType(FadeTransition),
          ),
        )
        .opacity
        .value;
    expect(opacity(), 1);
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 200));
      expect(opacity(), 1);
    }
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
