// Agent cards on the phone: every ask kind answers with the right
// GenUiAnswer, required fields hold Send, a danger confirm goes through the
// kit's sheet, held answers undo, receipts and passed-over cards read, an
// unreadable card says one line, the list slot answers with secondary
// buttons, and Settings toggles the feature with a plain status.
import 'dart:ui' show CheckedState, Tristate;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart' show ChatStatus;
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/agents/cards_from_agents_row.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/widgets/agent_card_view.dart';

import '../tool/capture/fixtures.dart' show captureTheme;
import 'support/agent_card_fakes.dart';
import 'support/chats_fakes.dart';

final _en = lookupAppLocalizations(const Locale('en'));

String _b(String s) => KitBidi.auto(s);

Widget _host(
  Widget child, {
  double textScale = 1,
  Locale locale = const Locale('en'),
  bool light = false,
  List<Override> overrides = const [],
}) => ProviderScope(
  overrides: overrides,
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: captureTheme(light: light),
    locale: locale,
    supportedLocales: AppLocalizations.supportedLocales,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    builder: (context, app) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: app!,
    ),
    home: Material(child: SingleChildScrollView(child: child)),
  ),
);

AgentCardView _view(
  FakeGenUi gen,
  GenUiCard card, {
  bool busy = false,
  bool inList = false,
  FakeCardPhotos? photos,
}) => AgentCardView(
  controller: gen,
  parse: GenUiParsed(card),
  agentLabel: 'Claude Code',
  busy: busy,
  inList: inList,
  photos: photos,
);

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double textScale = 1,
  Locale locale = const Locale('en'),
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(_host(child, textScale: textScale, locale: locale));
  await tester.pumpAndSettle();
}

final _send = find.byKey(const Key('agent-card-send'));

KitButton _button(WidgetTester tester, Finder finder) =>
    tester.widget<KitButton>(finder);

/// Whether a choice row reads as selected to a screen reader.
bool _chosen(WidgetTester tester, String label) {
  final data = tester
      .getSemantics(
        find
            .ancestor(
              of: find.text(_b(label)),
              matching: find.byType(KitTappable),
            )
            .first,
      )
      .getSemanticsData();
  return data.flagsCollection.isSelected == Tristate.isTrue ||
      data.flagsCollection.isChecked == CheckedState.isTrue;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('choice', () {
    testWidgets('single: one tap answers with its id, no Send', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      await _pump(tester, _view(gen, card));

      expect(find.text(_en.agentCardAsks(_b('Claude Code'))), findsOneWidget);
      expect(find.text(_b('Pick a database')), findsOneWidget);
      expect(_send, findsNothing);
      await tester.tap(find.text(_b('SQLite')));
      await tester.pumpAndSettle();

      expect(gen.answers, hasLength(1));
      expect(
        (gen.answers.single.answer as GenUiChoiceAnswer).ids,
        equals(['sqlite']),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('multi: several picks answer in the agent\'s order', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk(multi: true));
      await _pump(tester, _view(gen, card));

      expect(find.text(_en.agentCardChooseOneOrMore), findsOneWidget);
      // In a conversation Send is the card's one primary.
      expect(_button(tester, _send).role, KitButtonRole.primary);
      await tester.tap(find.text(_b('MongoDB')));
      await tester.pumpAndSettle();
      await tester.tap(find.text(_b('Postgres')));
      await tester.pumpAndSettle();
      await tester.tap(_send);
      await tester.pumpAndSettle();
      expect(
        (gen.answers.single.answer as GenUiChoiceAnswer).ids,
        equals(['pg', 'mongo']),
      );
    });

    testWidgets('while the agent is working a tap says why and sends nothing', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      await _pump(tester, _view(gen, card, busy: true));
      await tester.tap(find.text(_b('Postgres')));
      await tester.pumpAndSettle();
      expect(find.text(_en.agentCardBusy), findsOneWidget);
      expect(gen.answers, isEmpty);
    });

    testWidgets('a failed answer says so in plain words and can be retried', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      gen.deliveries[card.callID] = GenUiDeliveryState.failed;
      await _pump(tester, _view(gen, card));
      expect(find.text(_en.agentCardFailed), findsOneWidget);
      await tester.tap(find.text(_b('Postgres')));
      await tester.pumpAndSettle();
      expect(gen.answers, hasLength(1));
    });

    testWidgets('an uncertain delivery never offers a second send', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      gen.deliveries[card.callID] = GenUiDeliveryState.deliveryUnknown;
      await _pump(tester, _view(gen, card));
      await tester.tap(find.text(_b('Postgres')));
      await tester.pumpAndSettle();
      expect(find.text(_en.agentCardDeliveryUnknown), findsWidgets);
      expect(gen.answers, isEmpty);
    });

    testWidgets('a thrown answer shows plain words, never the error text', (
      tester,
    ) async {
      final gen = FakeGenUi()..answerError = StateError('secret-token-xyz');
      final card = agentCard(ask: choiceAsk());
      await _pump(tester, _view(gen, card));
      await tester.tap(find.text(_b('Postgres')));
      await tester.pumpAndSettle();
      expect(find.textContaining('secret-token-xyz'), findsNothing);
      expect(find.byKey(const Key('agent-card-error')), findsOneWidget);
    });
  });

  group('form', () {
    GenUiFormAsk ask() => GenUiFormAsk(
      submitLabel: 'Save setup',
      fields: [
        GenUiField(
          id: 'name',
          label: 'Project name',
          type: GenUiFieldType.text,
          required: true,
        ),
        GenUiField(
          id: 'replicas',
          label: 'Replicas',
          type: GenUiFieldType.number,
          min: 1,
          max: 5,
          defaultValue: 2,
        ),
        GenUiField(id: 'public', label: 'Public', type: GenUiFieldType.toggle),
        GenUiField(
          id: 'region',
          label: 'Region',
          type: GenUiFieldType.select,
          options: const [
            GenUiOption(id: 'eu', label: 'Europe'),
            GenUiOption(id: 'us', label: 'United States'),
          ],
        ),
        GenUiField(
          id: 'due',
          label: 'Due date',
          type: GenUiFieldType.date,
          defaultValue: '2026-10-07',
        ),
      ],
    );

    testWidgets('a required field holds Send with a reason, then answers', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(title: 'Set up the deploy', ask: ask());
      await _pump(tester, _view(gen, card));

      expect(find.text(_en.agentCardFormFix), findsOneWidget);
      expect(find.text(_en.agentCardFieldRequiredHint), findsWidgets);
      // The agent sees typed text: said once.
      expect(find.text(_en.agentCardTextNote), findsOneWidget);
      await tester.tap(find.text('Save setup'), warnIfMissed: false);
      await tester.pumpAndSettle();
      expect(gen.answers, isEmpty);

      // Touched and still empty: the field says what is wrong.
      await tester.enterText(find.byType(EditableText).first, 'x');
      await tester.pump();
      await tester.enterText(find.byType(EditableText).first, '');
      await tester.pumpAndSettle();
      expect(find.text(_en.agentCardFieldRequired), findsOneWidget);

      await tester.enterText(find.byType(EditableText).first, 'Atlas');
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('agent-card-field-public')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text(_b('Europe')));
      await tester.tap(find.text(_b('Europe')));
      await tester.pumpAndSettle();
      expect(find.text(_en.agentCardFormFix), findsNothing);
      await tester.ensureVisible(find.text('Save setup'));
      await tester.tap(find.text('Save setup'));
      await tester.pumpAndSettle();

      final values = (gen.answers.single.answer as GenUiFormAnswer).values;
      expect(values, {
        'name': 'Atlas',
        'replicas': 2,
        'public': true,
        'region': 'eu',
        'due': '2026-10-07',
      });
    });

    testWidgets('a number outside its range blocks Send with its reason', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: ask());
      await _pump(tester, _view(gen, card));
      await tester.enterText(find.byType(EditableText).first, 'Atlas');
      await tester.enterText(find.byType(EditableText).at(1), '9');
      await tester.pumpAndSettle();
      expect(find.text(_en.agentCardFieldMax('5')), findsOneWidget);
      expect(find.text(_en.agentCardFormFix), findsOneWidget);
    });
  });

  group('undo gives back what the person had', () {
    testWidgets('a chosen option stays chosen', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      await _pump(tester, _view(gen, card));
      await tester.tap(find.text(_b('SQLite')));
      await tester.pumpAndSettle();
      gen.set(
        card,
        delivery: GenUiDeliveryState.held,
        summary: 'You chose SQLite',
      );
      await tester.pumpAndSettle();
      expect(find.text(_b('Postgres')), findsNothing);
      gen.set(card, delivery: GenUiDeliveryState.idle);
      await tester.pumpAndSettle();
      expect(find.text(_b('Postgres')), findsOneWidget);
      expect(_chosen(tester, 'SQLite'), isTrue);
      expect(_chosen(tester, 'Postgres'), isFalse);
    });

    testWidgets('form values come back filled in', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(
        ask: GenUiFormAsk(
          fields: [
            GenUiField(
              id: 'name',
              label: 'Project name',
              type: GenUiFieldType.text,
              required: true,
            ),
            GenUiField(
              id: 'public',
              label: 'Public',
              type: GenUiFieldType.toggle,
            ),
          ],
        ),
      );
      await _pump(tester, _view(gen, card));
      await tester.enterText(find.byType(EditableText).first, 'Atlas');
      await tester.tap(find.byKey(const Key('agent-card-field-public')));
      await tester.pumpAndSettle();
      await tester.tap(_send);
      await tester.pumpAndSettle();
      gen.set(card, delivery: GenUiDeliveryState.held, summary: 'Sent');
      await tester.pumpAndSettle();
      expect(find.byType(EditableText), findsNothing);
      gen.set(card, delivery: GenUiDeliveryState.idle);
      await tester.pumpAndSettle();
      expect(find.text('Atlas'), findsOneWidget);
      await tester.tap(_send);
      await tester.pumpAndSettle();
      expect((gen.answers.last.answer as GenUiFormAnswer).values, {
        'name': 'Atlas',
        'public': true,
      });
    });

    testWidgets('picked photos come back', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(
        ask: const GenUiPhotoAsk(purpose: 'Show me', max: 2),
      );
      await _pump(tester, _view(gen, card, photos: FakeCardPhotos()));
      await tester.tap(find.byKey(const Key('agent-card-photo-take')));
      await tester.pumpAndSettle();
      await tester.tap(_send);
      await tester.pumpAndSettle();
      gen.set(
        card,
        delivery: GenUiDeliveryState.held,
        summary: 'Sent: 1 photo',
      );
      await tester.pumpAndSettle();
      expect(find.text(_en.agentCardPhotoName(1)), findsNothing);
      gen.set(card, delivery: GenUiDeliveryState.idle);
      await tester.pumpAndSettle();
      expect(find.text(_en.agentCardPhotoName(1)), findsOneWidget);
      await tester.tap(_send);
      await tester.pumpAndSettle();
      expect(gen.answers.last.attachments, 1);
    });

    testWidgets('answered drops the draft', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      await _pump(tester, _view(gen, card));
      await tester.tap(find.text(_b('SQLite')));
      await tester.pumpAndSettle();
      gen.set(
        card,
        state: GenUiCardState.answered,
        summary: 'You chose SQLite',
      );
      await tester.pumpAndSettle();
      gen.set(card, state: GenUiCardState.waiting);
      await tester.pumpAndSettle();
      expect(_chosen(tester, 'SQLite'), isFalse);
    });
  });

  group('held answer words', () {
    testWidgets('a single choice reads what was chosen, then Undo', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      await _pump(tester, _view(gen, card));
      await tester.tap(find.text(_b('SQLite')));
      await tester.pumpAndSettle();
      gen.set(card, delivery: GenUiDeliveryState.held);
      await tester.pumpAndSettle();
      expect(find.text(_b('SQLite')), findsOneWidget);
      expect(find.text(_en.agentCardSent), findsNothing);
      expect(find.text(_en.kitUndoAction), findsOneWidget);
    });

    testWidgets('a confirm reads its own label', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(
        ask: const GenUiConfirmAsk(confirmLabel: 'Run it'),
      );
      await _pump(tester, _view(gen, card));
      await tester.tap(find.text('Run it'));
      await tester.pumpAndSettle();
      gen.set(card, delivery: GenUiDeliveryState.held);
      await tester.pumpAndSettle();
      expect(find.text(_b('Run it')), findsOneWidget);
    });
  });

  group('confirm', () {
    testWidgets('Confirm and Cancel answer true and false', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(
        title: 'Run the migration?',
        ask: const GenUiConfirmAsk(confirmLabel: 'Run it'),
      );
      await _pump(tester, _view(gen, card));
      await tester.tap(find.text('Run it'));
      await tester.pumpAndSettle();
      expect((gen.answers.single.answer as GenUiConfirmAnswer).value, isTrue);
      await tester.tap(find.text(_en.agentCardCancelDefault));
      await tester.pumpAndSettle();
      expect((gen.answers.last.answer as GenUiConfirmAnswer).value, isFalse);
    });

    testWidgets('a danger confirm goes through the confirm sheet', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(
        title: 'Delete 14 files?',
        ask: const GenUiConfirmAsk(
          confirmLabel: 'Delete',
          tone: GenUiConfirmTone.danger,
        ),
      );
      await _pump(tester, _view(gen, card));
      await tester.tap(find.byKey(const Key('agent-card-confirm')));
      await tester.pumpAndSettle();
      // The sheet asks first; nothing is sent yet.
      expect(
        find.byKey(const Key('agent-card-danger-confirm')),
        findsOneWidget,
      );
      expect(gen.answers, isEmpty);
      await tester.tap(find.byKey(const Key('agent-card-danger-confirm')));
      await tester.pumpAndSettle();
      expect((gen.answers.single.answer as GenUiConfirmAnswer).value, isTrue);
    });

    testWidgets('closing the danger sheet sends nothing', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(
        ask: const GenUiConfirmAsk(
          confirmLabel: 'Delete',
          tone: GenUiConfirmTone.danger,
        ),
      );
      await _pump(tester, _view(gen, card));
      await tester.tap(find.byKey(const Key('agent-card-confirm')));
      await tester.pumpAndSettle();
      await tester.tap(find.text(_en.agentCardCancelDefault).last);
      await tester.pumpAndSettle();
      expect(gen.answers, isEmpty);
    });
  });

  group('photo', () {
    testWidgets('picks within the ask\'s max, then Send carries the photos', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final photos = FakeCardPhotos();
      final card = agentCard(
        ask: const GenUiPhotoAsk(purpose: 'Show me the error', max: 2),
      );
      await _pump(tester, _view(gen, card, photos: photos));

      expect(find.text(_b('Show me the error')), findsOneWidget);
      expect(find.text(_en.agentCardPhotoNone), findsOneWidget);
      await tester.tap(find.byKey(const Key('agent-card-photo-take')));
      await tester.pumpAndSettle();
      expect(photos.takes, 1);
      expect(find.text(_en.agentCardPhotoName(1)), findsOneWidget);
      await tester.tap(find.byKey(const Key('agent-card-photo-choose')));
      await tester.pumpAndSettle();
      // Only the room that was left (one) was asked of the gallery.
      expect(find.text(_en.agentCardPhotoName(2)), findsOneWidget);
      expect(find.text(_en.agentCardPhotoMax), findsOneWidget);

      await tester.tap(_send);
      await tester.pumpAndSettle();
      expect((gen.answers.single.answer as GenUiPhotoAnswer).count, 2);
      expect(gen.answers.single.attachments, 2);
    });

    testWidgets('a picked photo can be taken back', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: const GenUiPhotoAsk(purpose: 'Show me'));
      await _pump(tester, _view(gen, card, photos: FakeCardPhotos()));
      await tester.tap(find.byKey(const Key('agent-card-photo-take')));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip(_en.agentCardPhotoRemove(1)));
      await tester.pumpAndSettle();
      expect(find.text(_en.agentCardPhotoName(1)), findsNothing);
      expect(find.text(_en.agentCardPhotoNone), findsOneWidget);
    });

    testWidgets('a picker failure is plain words', (tester) async {
      final gen = FakeGenUi();
      final photos = FakeCardPhotos()..error = StateError('boom /secret/path');
      final card = agentCard(ask: const GenUiPhotoAsk(purpose: 'Show me'));
      await _pump(tester, _view(gen, card, photos: photos));
      await tester.tap(find.byKey(const Key('agent-card-photo-choose')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('agent-card-photo-error')), findsOneWidget);
      expect(find.textContaining('/secret/path'), findsNothing);
    });

    testWidgets('no photo source here: it says to type the answer', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: const GenUiPhotoAsk(purpose: 'Show me'));
      await _pump(tester, _view(gen, card));
      expect(
        find.byKey(const Key('agent-card-photo-unavailable')),
        findsOneWidget,
      );
      expect(_send, findsNothing);
    });
  });

  group('states', () {
    testWidgets('held: a receipt with Undo that calls undoGenUiAnswer', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      gen.deliveries[card.callID] = GenUiDeliveryState.held;
      gen.summaries[card.callID] = 'You chose Postgres';
      await _pump(tester, _view(gen, card));
      expect(find.text(_b('You chose Postgres')), findsOneWidget);
      expect(_send, findsNothing);
      await tester.tap(find.text(_en.kitUndoAction));
      await tester.pumpAndSettle();
      expect(gen.undone, [card]);
    });

    testWidgets('answered: the receipt shows the summary and opens read-only', (
      tester,
    ) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      gen.states[card.callID] = GenUiCardState.answered;
      gen.summaries[card.callID] = 'You chose Postgres';
      await _pump(tester, _view(gen, card));
      expect(find.text(_b('You chose Postgres')), findsOneWidget);
      expect(find.text(_b('Two fit this project.')), findsNothing);
      await tester.tap(find.text(_b('Pick a database')));
      await tester.pumpAndSettle();
      expect(find.text(_b('Two fit this project.')), findsOneWidget);
      expect(_send, findsNothing);
      expect(find.text(_en.kitUndoAction), findsNothing);
    });

    testWidgets('passed over: one quiet line, no controls', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      gen.states[card.callID] = GenUiCardState.passedOver;
      await _pump(tester, _view(gen, card));
      expect(find.text(_en.agentCardNotAnswered), findsOneWidget);
      expect(_send, findsNothing);
    });

    testWidgets('report: the body, no controls', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(
        title: 'Build finished',
        body: [
          GenUiKeyValue(
            rows: const [GenUiKeyValueRow(key: 'Duration', value: '4 min')],
          ),
          GenUiList(
            style: GenUiListStyle.check,
            items: const [
              GenUiListItem(text: 'Tests pass', done: true),
              GenUiListItem(text: 'Docs'),
            ],
          ),
          GenUiTable(
            columns: const ['File', 'Lines'],
            rows: const [
              ['a.dart', '3'],
            ],
          ),
          GenUiChart(
            kind: GenUiChartKind.bar,
            labels: const ['Mon', 'Tue'],
            series: [
              GenUiChartSeries(name: 'ms', values: const [4, 9]),
            ],
          ),
          const GenUiCode(language: 'sh', text: 'make test'),
          GenUiDiffStat(
            files: const [
              GenUiDiffFile(path: 'lib/a.dart', added: 3, removed: 1),
            ],
          ),
          const GenUiProgress(label: 'Coverage', value: 0.62),
          const GenUiCallout(
            tone: GenUiCalloutTone.warning,
            text: 'Flaky test',
          ),
          const GenUiLink(
            label: 'Open the run',
            url: 'https://example.com/run',
          ),
        ],
      );
      await _pump(tester, _view(gen, card));
      expect(
        find.text(_en.agentCardReports(_b('Claude Code'))),
        findsOneWidget,
      );
      expect(find.text(_b('Duration')), findsOneWidget);
      expect(find.text(_b('Tests pass')), findsOneWidget);
      expect(find.text(_b('Flaky test')), findsOneWidget);
      expect(find.byType(KitChart), findsOneWidget);
      expect(find.byType(KitMiniTable), findsNWidgets(2));
      expect(_send, findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('unknown: read-only with a quiet line', (tester) async {
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk());
      gen.states[card.callID] = GenUiCardState.unknown;
      await _pump(tester, _view(gen, card));
      expect(find.byKey(const Key('agent-card-unknown')), findsOneWidget);
      expect(_send, findsNothing);
    });

    testWidgets('unreadable: one line and only the problem name in Details', (
      tester,
    ) async {
      final gen = FakeGenUi();
      await _pump(
        tester,
        AgentCardView(
          controller: gen,
          parse: const GenUiUnreadable(reason: GenUiProblem.unknownKey),
          agentLabel: 'Claude Code',
        ),
      );
      expect(find.text(_en.agentCardUnreadable), findsOneWidget);
      await tester.tap(find.text(_en.agentCardDetails));
      await tester.pumpAndSettle();
      expect(find.textContaining('unknownKey'), findsOneWidget);
    });
  });

  group('list slot', () {
    testWidgets('a waiting card under its row answers with secondary buttons', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final gen = FakeGenUi();
      final card = agentCard(ask: choiceAsk(multi: true));
      final host = FakeChatsHost(
        FakeChatFeedSource(
          items: [
            chat(
              'a',
              'Pick a database',
              at: DateTime(2026, 10, 7, 12),
              status: ChatStatus.needsYou,
              agentLabel: 'Claude Code',
            ),
          ],
        ),
      )..requestCards['a'] = _view(gen, card, inList: true);
      await tester.pumpWidget(chatsApp(host, const ChatsHomeScreen()));
      await tester.pumpAndSettle();

      await tester.tap(find.text(_b('Postgres')));
      await tester.pumpAndSettle();
      // The list keeps its one primary: the card's Send is secondary.
      final send = _button(tester, _send);
      expect(send.role, KitButtonRole.secondary);
      await tester.tap(_send);
      await tester.pumpAndSettle();
      expect((gen.answers.single.answer as GenUiChoiceAnswer).ids, ['pg']);
    });
  });

  group('settings row', () {
    Future<void> mount(WidgetTester tester, FakeGenUi gen) async {
      await tester.pumpWidget(
        _host(
          const CardsFromAgentsRow(),
          overrides: [genUiControllerProvider.overrideWithValue(gen)],
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('toggles and says what is true in plain words', (tester) async {
      final gen = FakeGenUi()
        ..enabled = false
        ..status = const GenUiSetupOff();
      await mount(tester, gen);
      expect(find.text(_en.cardsFromAgentsTitle), findsOneWidget);
      expect(find.text(_en.cardsStatusOff), findsOneWidget);

      gen.status = GenUiSetupOn(agents: const [GenUiAgent.claude]);
      await tester.tap(find.byKey(const Key('cards-from-agents-switch')));
      await tester.pumpAndSettle();
      expect(gen.enableCalls, [true]);
      expect(find.text(_en.cardsStatusOn('Claude Code')), findsOneWidget);
    });

    testWidgets('each status has its words, reasons never raw', (tester) async {
      final gen = FakeGenUi();
      await mount(tester, gen);
      final cases = <GenUiSetupStatus, String>{
        const GenUiSetupInstalling(): _en.cardsStatusChecking,
        GenUiSetupPartial(
          agents: const [GenUiAgent.openCode1, GenUiAgent.openCode2],
          reason: GenUiSetupProblem.notQualified,
        ): _en.cardsStatusPartial(
          'OpenCode',
          _en.cardsProblemNotQualified,
        ),
        GenUiSetupRestartRequired(agents: const [GenUiAgent.claude]): _en
            .cardsStatusRestart('Claude Code'),
        const GenUiSetupUnavailable(reason: GenUiSetupProblem.unsupportedHost):
            _en.cardsStatusUnavailable(_en.cardsProblemUnsupportedHost),
        const GenUiSetupFailed(reason: GenUiSetupProblem.installationFailed):
            _en.cardsStatusFailed(_en.cardsProblemInstallationFailed),
      };
      for (final entry in cases.entries) {
        gen.status = entry.key;
        gen.notifyListeners();
        await tester.pumpAndSettle();
        expect(find.text(entry.value), findsOneWidget, reason: entry.value);
      }
    });

    testWidgets('each status line names the agent it is about', (tester) async {
      final gen = FakeGenUi();
      await mount(tester, gen);
      final cases = <GenUiSetupStatus, String>{
        GenUiSetupPartial(
          agents: const [GenUiAgent.claude],
          reason: GenUiSetupProblem.notQualified,
          affected: const [GenUiAgent.openCode1, GenUiAgent.openCode2],
        ): 'On for Claude Code. OpenCode hasn\'t been checked to work with '
            'cards yet.',
        const GenUiSetupUnavailable(
          reason: GenUiSetupProblem.notQualified,
          affected: [GenUiAgent.openCode2],
        ): 'Not available. OpenCode hasn\'t been checked to work with cards '
            'yet.',
        const GenUiSetupFailed(
          reason: GenUiSetupProblem.registrationFailed,
          affected: [GenUiAgent.claude],
        ): "Couldn't turn this on. Claude Code couldn't be told about cards.",
        // Nothing names an agent (the app has not asked any yet): the line
        // says so without "This agent".
        const GenUiSetupUnavailable(reason: GenUiSetupProblem.notQualified):
            'Not available. Cards haven\'t been checked to work with the '
            'agents here yet.',
      };
      for (final entry in cases.entries) {
        gen.status = entry.key;
        gen.notifyListeners();
        await tester.pumpAndSettle();
        expect(find.text(entry.value), findsOneWidget, reason: entry.value);
        expect(find.textContaining('This agent'), findsNothing);
      }
    });

    testWidgets('a failed change says so without the error text', (
      tester,
    ) async {
      final gen = FakeGenUi()
        ..enabled = false
        ..enableError = StateError('/etc/secret');
      await mount(tester, gen);
      await tester.tap(find.byKey(const Key('cards-from-agents-switch')));
      await tester.pumpAndSettle();
      expect(find.text(_en.cardsSettingFailed), findsOneWidget);
      expect(find.textContaining('/etc/secret'), findsNothing);
    });

    testWidgets('draws nothing without a controller', (tester) async {
      await tester.pumpWidget(
        _host(
          const CardsFromAgentsRow(),
          overrides: [genUiControllerProvider.overrideWithValue(null)],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text(_en.cardsFromAgentsTitle), findsNothing);
    });
  });

  group('layout', () {
    GenUiCard everything() => agentCard(
      title:
          'A long title that keeps going so it has to wrap at large text sizes',
      body: [
        const GenUiText(
          text: 'Body words that are long enough to wrap around.',
        ),
        GenUiKeyValue(
          rows: const [
            GenUiKeyValueRow(
              key: 'Duration of the whole run',
              value: '4 min 12 s',
            ),
          ],
        ),
        GenUiTable(
          columns: const ['File', 'Added', 'Removed'],
          rows: const [
            ['lib/some/very/long/path/to/a_file.dart', '12', '3'],
          ],
        ),
      ],
      ask: choiceAsk(),
    );

    testWidgets('text 2.0 does not overflow', (tester) async {
      final gen = FakeGenUi();
      await _pump(tester, _view(gen, everything()), textScale: 2);
      expect(tester.takeException(), isNull);
      await _pump(
        tester,
        _view(
          gen,
          agentCard(
            ask: GenUiFormAsk(
              fields: [
                GenUiField(
                  id: 'a',
                  label: 'A fairly long field label',
                  type: GenUiFieldType.text,
                  required: true,
                ),
                GenUiField(
                  id: 'b',
                  label: 'Toggle me',
                  type: GenUiFieldType.toggle,
                ),
              ],
            ),
          ),
        ),
        textScale: 2,
      );
      expect(tester.takeException(), isNull);
      await _pump(
        tester,
        _view(
          gen,
          agentCard(ask: const GenUiConfirmAsk(confirmLabel: 'Go ahead now')),
        ),
        textScale: 2,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('Arabic, right to left, draws the same card', (tester) async {
      final gen = FakeGenUi();
      await _pump(tester, _view(gen, everything()), locale: const Locale('ar'));
      expect(tester.takeException(), isNull);
      expect(
        find.text(
          lookupAppLocalizations(
            const Locale('ar'),
          ).agentCardAsks(_b('Claude Code')),
        ),
        findsOneWidget,
      );
    });
  });
}
