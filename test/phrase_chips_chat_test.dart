import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'support/fake_event_channel.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show PromptDelivery;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phrase_chips.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart'
    show KitButton, KitButtonRole, KitPhraseChips;
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show capturePng, loadCaptureFonts;
import 'support/complete_message_history.dart';

/// The phrase chips in a real chat: which row shows in which moment, what a
/// tap and a long-press do, and what a send teaches.
class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> messagesResult = const [];
  final List<String> prompts = [];

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async =>
      List.of(messagesResult);

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileDiff>> diff(String id) async => const [];

  @override
  Future<List<Todo>> todos(String id) async => const [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {
    prompts.add(text);
  }
}

MessageWithParts _assistant(
  String id, {
  String? errorText,
  MessageErrorKind? errorKind,
  List<Part> parts = const [],
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'assistant',
    errorText: errorText,
    errorKind: errorKind,
    time: MsgTime(created: 10, completed: 11),
  ),
  parts: [
    Part(id: '$id-text', messageID: id, type: 'text', text: 'Done.'),
    ...parts,
  ],
);

MessageWithParts _user(String id, {int created = 1}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'user',
    time: MsgTime(created: created, completed: created + 1),
  ),
  parts: [Part(id: '$id-text', messageID: id, type: 'text', text: 'Hi')],
);

Part _edit() => Part(
  id: 'edit-1',
  messageID: 'a1',
  type: 'tool',
  toolName: 'edit',
  callID: 'c1',
  toolState: ToolState(status: 'completed'),
);

Future<ConnectionController> _pump(
  WidgetTester tester,
  _Api api, {
  Map<String, Object> prefsValues = const {},
  Locale? locale,
  GlobalKey? boundary,
}) async {
  SharedPreferences.setMockInitialValues(prefsValues);
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.upsert(
    ServerProfile(id: 'p1', name: 'Test', baseUrl: 'http://localhost:4096'),
  );
  await store.setActiveId('p1');
  final controller = ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: boundary == null ? null : AppTheme.light(),
          locale: locale,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ChatScreen(
            sessionID: 'session-1',
            handoffStore: ReviewHandoffStore(),
          ),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump();
  await tester.pumpAndSettle();
  return controller;
}

/// Two sends of [text] in [moment], as if the person had typed it twice.
Future<void> _learn(
  ConnectionController controller,
  String text,
  PhraseMoment moment,
) async {
  final store = PhraseChipStore.forProfile(controller.store.prefs, 'p1');
  await store.record(text, moment);
  await store.record(text, moment);
}

Finder _chip(String text) => find.byKey(ValueKey('phrase-chip-$text'));

void _busy(ConnectionController controller) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'session.status',
      properties: {
        'sessionID': 'session-1',
        'status': {'type': 'busy'},
      },
    ),
  );
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

  testWidgets('an empty new chat shows no chips', (tester) async {
    final api = _Api();
    final controller = await _pump(tester, api);
    await _learn(controller, 'Run the tests', PhraseMoment.idle);
    await tester.pumpAndSettle();
    expect(find.byType(KitPhraseChips), findsNothing);
    expect(_chip('Run the tests'), findsNothing);
    expect(_chip('Continue'), findsNothing);
  });

  // Concept C (chat fidelity, 20 Oct 2026): while the agent works the
  // screen is the conversation, the live line and the glowing composer.
  // The live line already answers "What's the update?".
  testWidgets('working: no phrase row over the composer', (tester) async {
    final api = _Api()..messagesResult = [_user('u1')];
    final controller = await _pump(tester, api);
    _busy(controller);
    await tester.pumpAndSettle();
    expect(_chip("What's the update?"), findsNothing);
    expect(find.byType(KitPhraseChips), findsNothing);
  });

  testWidgets('a turn stopped partway offers Continue', (tester) async {
    final api = _Api()
      ..messagesResult = [
        _user('u1'),
        _assistant(
          'a1',
          errorText: 'aborted',
          errorKind: MessageErrorKind.aborted,
        ),
      ];
    await _pump(tester, api);
    expect(_chip('Continue'), findsOneWidget);
    expect(find.bySemanticsLabel('Send: Continue'), findsOneWidget);
    await tester.tap(_chip('Continue'));
    await tester.pumpAndSettle();
    expect(api.prompts, ['Continue']);
  });

  // Owner, 2026-10-22: a bare "Try" chip read as unclear. After a stop the
  // row says "Try again" in full, next to Continue.
  testWidgets('a turn stopped partway offers Try again in full', (
    tester,
  ) async {
    final api = _Api()
      ..messagesResult = [
        _user('u1'),
        _assistant(
          'a1',
          errorText: 'aborted',
          errorKind: MessageErrorKind.aborted,
        ),
      ];
    await _pump(tester, api);
    expect(_chip('Try again'), findsOneWidget);
    expect(find.text('Try again'), findsOneWidget);
    expect(find.bySemanticsLabel('Send: Try again'), findsOneWidget);
    // Both chips whole on the screen, nowhere near the row's fading end.
    final width = tester.view.physicalSize.width / tester.view.devicePixelRatio;
    expect(tester.getRect(_chip('Try again')).right, lessThan(width / 2));
    await tester.tap(_chip('Try again'));
    await tester.pumpAndSettle();
    expect(api.prompts, ['Try again']);
  });

  // Unslop 2026-10-30: a model's usage limit offers another model as its
  // one fix, Details as a quiet link, and no row of retry chips under it.
  testWidgets('a usage-limit error: Choose another model, a Details link, '
      'no phrase row', (tester) async {
    final api = _Api()
      ..messagesResult = [
        _user('u1'),
        _assistant(
          'a1',
          errorText: 'Free usage limit reached for this model.',
          errorKind: MessageErrorKind.unknown,
        ),
      ];
    await _pump(tester, api);
    expect(find.byKey(const Key('error-action-choose-model')), findsOneWidget);
    expect(find.text('Choose another model'), findsOneWidget);
    expect(find.byKey(const Key('error-action-resend')), findsNothing);
    final details = find.byKey(const Key('error-action-details'));
    expect(details, findsOneWidget);
    expect(find.text('Details'), findsOneWidget);
    expect(find.text('Error details'), findsNothing);
    expect(
      tester.widget<KitButton>(details).role,
      KitButtonRole.tertiary,
      reason: 'Details is a link, not a second button beside the fix',
    );
    expect(
      tester
          .widget<KitButton>(find.byKey(const Key('error-action-choose-model')))
          .role,
      KitButtonRole.secondary,
    );
    expect(find.byType(KitPhraseChips), findsNothing);
    expect(_chip('Continue'), findsNothing);
    expect(_chip('Try again'), findsNothing);
  });

  testWidgets('no second Continue beside the transcript\'s own button', (
    tester,
  ) async {
    final api = _Api()
      ..messagesResult = [
        _user('u1'),
        _assistant(
          'a1',
          errorText: 'Output too long',
          errorKind: MessageErrorKind.outputLength,
        ),
      ];
    await _pump(tester, api);
    expect(find.byKey(const Key('error-action-continue')), findsOneWidget);
    expect(_chip('Continue'), findsNothing);
  });

  testWidgets('finished with changes shows what was learned for it', (
    tester,
  ) async {
    final api = _Api()
      ..messagesResult = [
        _user('u1'),
        _assistant('a1', parts: [_edit()]),
      ];
    final controller = await _pump(tester, api);
    expect(_chip('Commit it'), findsNothing);
    await _learn(controller, 'Commit it', PhraseMoment.finishedWithChanges);
    await _learn(controller, 'Run the tests', PhraseMoment.idle);
    await tester.pumpAndSettle();
    expect(_chip('Commit it'), findsOneWidget);
    // Learned for another moment: not offered here.
    expect(_chip('Run the tests'), findsNothing);
  });

  testWidgets('hides while the person is typing and comes back empty', (
    tester,
  ) async {
    final api = _Api()..messagesResult = [_user('u1'), _assistant('a1')];
    final controller = await _pump(tester, api);
    await _learn(controller, 'Run the tests', PhraseMoment.idle);
    await tester.pumpAndSettle();
    expect(_chip('Run the tests'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('chat-composer-field')), 'Fix');
    await tester.pumpAndSettle();
    expect(_chip('Run the tests'), findsNothing);
    await tester.enterText(find.byKey(const Key('chat-composer-field')), '');
    await tester.pumpAndSettle();
    expect(_chip('Run the tests'), findsOneWidget);
  });

  testWidgets('long-press Edit puts the words in the box without sending', (
    tester,
  ) async {
    final api = _Api()..messagesResult = [_user('u1'), _assistant('a1')];
    final controller = await _pump(tester, api);
    await _learn(controller, 'Run the tests', PhraseMoment.idle);
    await tester.pumpAndSettle();
    await tester.longPress(_chip('Run the tests'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Edit'));
    await tester.pumpAndSettle();
    final field = tester.widget<EditableText>(
      find.descendant(
        of: find.byKey(const Key('chat-composer-field')),
        matching: find.byType(EditableText),
      ),
    );
    expect(field.controller.text, 'Run the tests');
    expect(api.prompts, isEmpty);
  });

  testWidgets('long-press Hide stops suggesting it, and Settings can undo', (
    tester,
  ) async {
    final api = _Api()..messagesResult = [_user('u1'), _assistant('a1')];
    final controller = await _pump(tester, api);
    await _learn(controller, 'Run the tests', PhraseMoment.idle);
    await tester.pumpAndSettle();
    await tester.longPress(_chip('Run the tests'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Hide'));
    await tester.pumpAndSettle();
    expect(_chip('Run the tests'), findsNothing);
    final store = PhraseChipStore.forProfile(controller.store.prefs, 'p1');
    expect(store.hiddenCount, 1);
    await store.showHidden();
    await tester.pumpAndSettle();
    expect(_chip('Run the tests'), findsOneWidget);
  });

  testWidgets('the switch turns the whole row off', (tester) async {
    final api = _Api()..messagesResult = [_user('u1'), _assistant('a1')];
    final controller = await _pump(tester, api);
    await _learn(controller, 'Run the tests', PhraseMoment.idle);
    await tester.pumpAndSettle();
    expect(_chip('Run the tests'), findsOneWidget);
    await PhraseChipStore.setEnabled(controller.store.prefs, false);
    await tester.pumpAndSettle();
    expect(_chip('Run the tests'), findsNothing);
    await PhraseChipStore.setEnabled(controller.store.prefs, true);
  });

  testWidgets('a short sent phrase is learned and becomes a chip', (
    tester,
  ) async {
    final api = _Api()..messagesResult = [_user('u1'), _assistant('a1')];
    final controller = await _pump(tester, api);
    Future<void> sendTyped(String text) async {
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        text,
      );
      await tester.pump();
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pumpAndSettle();
    }

    await sendTyped('Run the tests');
    expect(_chip('Run the tests'), findsNothing);
    await sendTyped('run the tests.');
    expect(api.prompts, ['Run the tests', 'run the tests.']);
    final store = PhraseChipStore.forProfile(controller.store.prefs, 'p1');
    expect(store.learnedCount, 1);
    // A long message is never learned.
    await sendTyped('Please refactor the whole settings screen today');
    expect(store.learnedCount, 1);
  });

  testWidgets('Arabic: the row lays out right to left with its label', (
    tester,
  ) async {
    final api = _Api()..messagesResult = [_user('u1'), _assistant('a1')];
    final controller = await _pump(tester, api, locale: const Locale('ar'));
    await _learn(controller, 'شغّل الاختبارات', PhraseMoment.idle);
    await tester.pumpAndSettle();
    final chip = _chip('شغّل الاختبارات');
    expect(chip, findsOneWidget);
    expect(Directionality.of(tester.element(chip)), TextDirection.rtl);
    expect(find.bySemanticsLabel('إرسال: شغّل الاختبارات'), findsOneWidget);
  });

  // Evidence for docs/qa/phrase-chips-2026-10-12: the real chat screen at
  // phone size, in two moments. Runs only with OC_PHRASE_CAPTURE_DIR set.
  final captureDirectory = Platform.environment['OC_PHRASE_CAPTURE_DIR'];
  for (final moment in ['stopped-partway', 'working']) {
    testWidgets('captures the $moment moment', (tester) async {
      tester.view
        ..physicalSize = const Size(390, 844)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.runAsync(loadCaptureFonts);
      final boundary = GlobalKey();
      final api = _Api()
        ..messagesResult = moment == 'working'
            ? [
                _user(
                  'u1',
                  created: DateTime.now().millisecondsSinceEpoch - 4000,
                ),
              ]
            : [
                _user('u1'),
                _assistant(
                  'a1',
                  errorText: 'aborted',
                  errorKind: MessageErrorKind.aborted,
                ),
              ];
      final controller = await _pump(tester, api, boundary: boundary);
      await _learn(controller, 'Run the tests', PhraseMoment.stoppedPartway);
      await _learn(controller, 'Commit it', PhraseMoment.stoppedPartway);
      await _learn(controller, 'Run the tests', PhraseMoment.working);
      if (moment == 'working') _busy(controller);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      final png = await capturePng(tester, boundary, pixelRatio: 2);
      File('${captureDirectory!}/$moment.png').writeAsBytesSync(png);
    }, skip: captureDirectory == null);
  }
}
