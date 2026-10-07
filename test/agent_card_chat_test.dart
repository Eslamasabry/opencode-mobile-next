// An agent card in the conversation: a completed card call is drawn in place
// of its tool row (other tool calls keep theirs), it answers through the
// controller, and the composer says the answer can also be typed while a card
// waits.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/agent_card_view.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/agent_card_fakes.dart';
import 'support/complete_message_history.dart';

final _en = lookupAppLocalizations(const Locale('en'));

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// A connection that also draws agent cards, over a [FakeGenUi].
class _Controller extends ConnectionController implements GenUiController {
  _Controller(super.store, this.gen) {
    gen.addListener(notifyListeners);
  }

  final FakeGenUi gen;

  final _laptop = ServerProfile(
    id: 'laptop',
    name: 'Laptop',
    baseUrl: 'http://192.168.1.20:4096',
  );

  @override
  ServerProfile? get profile => _laptop;

  @override
  GenUiParse? genUiCardForPart(String sessionID, String messageID, Part part) =>
      gen.genUiCardForPart(sessionID, messageID, part);
  @override
  GenUiCardState genUiStateForCard(GenUiCard card) =>
      gen.genUiStateForCard(card);
  @override
  String? genUiAnswerSummary(GenUiCard card) => gen.genUiAnswerSummary(card);
  @override
  void undoGenUiAnswer(GenUiCard card) => gen.undoGenUiAnswer(card);
  @override
  List<GenUiCard> waitingCardsForSession(String sessionID) =>
      gen.waitingCardsForSession(sessionID);
  @override
  List<GenUiCard> waitingCardsForFeedItem(ChatFeedItem item) =>
      gen.waitingCardsForFeedItem(item);
  @override
  Future<void> answerGenUi(
    GenUiCard card,
    GenUiAnswer answer, {
    List<PromptAttachment> attachments = const [],
  }) => gen.answerGenUi(card, answer, attachments: attachments);
  @override
  bool get genUiEnabled => gen.genUiEnabled;
  @override
  Future<void> setGenUiEnabled(bool on) => gen.setGenUiEnabled(on);
  @override
  GenUiSetupStatus get genUiStatus => gen.genUiStatus;
  @override
  GenUiDeliveryState genUiDeliveryFor(GenUiCard card) =>
      gen.genUiDeliveryFor(card);
}

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<MessageWithParts>> messages(String id) async => [
    MessageWithParts(
      info: MessageInfo(
        id: 'm1',
        sessionID: id,
        role: 'user',
        time: MsgTime(created: 1, completed: 1),
      ),
      parts: [Part(id: 'p1', messageID: 'm1', type: 'text', text: 'Set it up')],
    ),
    MessageWithParts(
      info: MessageInfo(
        id: 'm2',
        sessionID: id,
        role: 'assistant',
        parentID: 'm1',
        finish: 'stop',
        time: MsgTime(created: 2, completed: 3),
      ),
      parts: [
        Part(
          id: 'p2',
          messageID: 'm2',
          type: 'tool',
          callID: 'c-bash',
          toolName: 'bash',
          toolState: ToolState(
            status: 'completed',
            title: 'ls -la',
            input: const {'command': 'ls -la'},
            output: 'total 0',
          ),
        ),
        Part(
          id: 'p3',
          messageID: 'm2',
          type: 'tool',
          callID: 'c1',
          toolName: 'oc-ui_show',
          toolState: ToolState(status: 'completed', input: const {}),
        ),
      ],
    ),
  ];
  @override
  Future<Session> session(String id) async => Session(id: id);
  @override
  Future<List<Session>> sessions() async => [Session(id: 's1')];
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<List<Todo>> todos(String id) async => const [];
  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];
}

Future<_Controller> _controller(FakeGenUi gen) async {
  SharedPreferences.setMockInitialValues({});
  final c = _Controller(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
    gen,
  );
  c
    ..api = _Api()
    ..repository = _Repository()
    ..status = StreamStatus.connected;
  c.sessionsById['s1'] = Session(id: 's1');
  addTearDown(c.dispose);
  return c;
}

Future<void> _open(WidgetTester tester, _Controller c) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(c)],
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const ChatScreen(sessionID: 's1'),
      ),
    ),
  );
  await tester.pumpAndSettle();
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

  testWidgets('a card call is drawn as the card and answers; the composer '
      'invites a typed answer while it waits', (tester) async {
    final gen = FakeGenUi();
    final card = agentCard(callID: 'c1', ask: choiceAsk());
    gen.cards['c1'] = GenUiParsed(card);
    gen.waiting = [card];
    final c = await _controller(gen);
    await _open(tester, c);

    expect(find.byType(AgentCardView), findsOneWidget);
    expect(find.text(KitBidi.auto('Pick a database')), findsOneWidget);
    // The call the domain does not read as a card keeps its tool row.
    expect(find.text('ls -la', findRichText: true), findsWidgets);
    // Typing stays allowed, and the hint says so.
    expect(find.text(_en.agentCardComposerHint), findsOneWidget);

    await tester.tap(find.text(KitBidi.auto('SQLite')));
    await tester.pumpAndSettle();
    expect((gen.answers.single.answer as GenUiChoiceAnswer).ids, ['sqlite']);
    expect(tester.takeException(), isNull);
  });

  testWidgets('with no card waiting the hint is the usual one', (tester) async {
    final gen = FakeGenUi();
    final card = agentCard(callID: 'c1', ask: choiceAsk());
    gen.cards['c1'] = GenUiParsed(card);
    gen.states['c1'] = GenUiCardState.answered;
    gen.summaries['c1'] = 'You chose Postgres';
    final c = await _controller(gen);
    await _open(tester, c);

    expect(find.text(_en.agentCardComposerHint), findsNothing);
    expect(find.text(KitBidi.auto('You chose Postgres')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a connection without cards draws the ordinary tool row', (
    tester,
  ) async {
    final gen = FakeGenUi(); // knows no card
    final c = await _controller(gen);
    await _open(tester, c);
    expect(find.byType(AgentCardView), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
