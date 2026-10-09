// Behaviour of unit chat-3 (the composer and its prompt pages): P4.3's
// withdraw-to-draft with Undo, P7.7's "Sign in to a model" chip, P7.5's
// never "Choose model" while a model answers, the offline Send words, the
// "+" sheet's unavailable rows, the history sheet's no-match state and the
// prompt editor's discard question.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/kit_search_field.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import 'chat_3_support.dart';

Finder _inner(Finder field) =>
    find.descendant(of: field, matching: find.byType(TextField));

String _composerText(WidgetTester tester) => tester
    .widget<TextField>(_inner(find.byKey(const Key('chat-composer-field'))))
    .controller!
    .text;

Future<void> _openPromptTool(WidgetTester tester, String key) async {
  await tester.tap(find.byKey(const Key('composer-tools-button')));
  await tester.pumpAndSettle();
  await Scrollable.ensureVisible(
    tester.element(find.byKey(const Key('composer-tools-prompts'))),
    alignment: .5,
  );
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tools-prompts')));
  await tester.pumpAndSettle();
  await Scrollable.ensureVisible(
    tester.element(find.byKey(Key(key))),
    alignment: .5,
  );
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(Key(key)));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(chat3MockSecureStorage);

  testWidgets('a withdrawn message returns to the draft, and Undo takes it '
      'back out and queues it again', (tester) async {
    final composer = TextEditingController(text: 'typed since');
    addTearDown(composer.dispose);
    var requeued = 0;
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: Builder(
            builder: (context) => Center(
              child: TextButton(
                onPressed: () => returnWithdrawnToDraft(
                  context,
                  composer: composer,
                  text: 'waiting message',
                  onUndo: () => requeued++,
                ),
                child: const Text('withdraw'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('withdraw'));
    await tester.pump();
    expect(composer.text, 'waiting message\ntyped since');
    expect(find.text('Returned to your draft'), findsOneWidget);

    await tester.tap(find.text('Undo'));
    await tester.pumpAndSettle();
    expect(composer.text, 'typed since');
    expect(requeued, 1);
  });

  testWidgets('KitComposer: an attachment alone makes Send live, and a note '
      'or attachment appearing keeps the field', (tester) async {
    final controller = TextEditingController();
    final focus = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focus.dispose);
    var sends = 0;
    Widget composer({required bool attached}) => MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: Align(
          alignment: Alignment.bottomCenter,
          child: KitComposer(
            controller: controller,
            focusNode: focus,
            hint: 'Ask OpenCode…',
            onSend: () => sends++,
            note: attached ? 'Attachments save with this draft.' : null,
            hasAttachments: attached,
            sendKey: const Key('send'),
            fieldKey: const Key('field'),
          ),
        ),
      ),
    );
    await tester.pumpWidget(composer(attached: false));
    expect(find.byKey(const Key('send')), findsOneWidget);
    await tester.tap(find.byKey(const Key('send')));
    expect(sends, 0);

    focus.requestFocus();
    await tester.pump();
    final editable = tester.state(find.byType(EditableText));
    await tester.pumpWidget(composer(attached: true));
    // The same field, still focused: the keyboard stays up.
    expect(tester.state(find.byType(EditableText)), same(editable));
    expect(focus.hasFocus, isTrue);
    await tester.tap(find.byKey(const Key('send')));
    expect(sends, 1);
  });

  testWidgets('with no model signed in the chip says so before the first '
      'send, and never asks to choose while a model answers', (tester) async {
    final conn = await chat3Controller();
    addTearDown(conn.dispose);
    conn.catalog = const CatalogSnapshot(providers: [], models: [], agents: []);
    await pumpChat3(tester, conn);
    expect(find.text('Sign in to a model'), findsOneWidget);
    expect(find.textContaining('Choose'), findsNothing);

    conn.busySessions.add('session-1');
    conn.notifyListeners();
    await tester.pumpAndSettle();
    expect(find.text('Sign in to a model'), findsNothing);
    expect(find.text('Server default'), findsOneWidget);
    expect(find.textContaining('Choose'), findsNothing);
  });

  testWidgets('offline, Send says it will send when back online', (
    tester,
  ) async {
    final conn = await chat3Controller();
    addTearDown(conn.dispose);
    conn.status = StreamStatus.disconnected;
    await pumpChat3(tester, conn);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'queue me',
    );
    await tester.pump();
    expect(find.byTooltip('Send when back online'), findsOneWidget);
    expect(find.textContaining("sends when you're back online"), findsWidgets);
    await tester.pumpWidget(const SizedBox.shrink());
    conn.dispose();
  });

  testWidgets('the history sheet says when nothing matches and clears the '
      'search', (tester) async {
    final api = Chat3Api()
      ..transcript = [chat3Prompt('m1', 'Review the parser')];
    final conn = await chat3Controller(api: api);
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    await _openPromptTool(tester, 'composer-tool-history');
    expect(find.text('Tap a prompt to add it to your draft.'), findsOneWidget);
    expect(find.byKey(const ValueKey('reuse-prompt-0')), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('prompt-history-search')),
      'zzz',
    );
    // The search settles after typing stops.
    await tester.pump(const Duration(seconds: 1));
    await tester.pumpAndSettle();
    expect(find.byType(KitSearchNoMatch), findsOneWidget);
    expect(find.byKey(const ValueKey('reuse-prompt-0')), findsNothing);

    await tester.tap(
      find.descendant(
        of: find.byType(KitSearchNoMatch),
        matching: find.textContaining('Clear'),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('reuse-prompt-0')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('reuse-prompt-0')));
    await tester.pumpAndSettle();
    expect(_composerText(tester), 'Review the parser');
  });

  testWidgets('the prompt editor asks before discarding and hands its text '
      'back with Use in draft', (tester) async {
    final conn = await chat3Controller();
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'first',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('prompt-editor-button')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('prompt-editor-screen')), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('prompt-editor-field')),
      'rewritten',
    );
    await tester.pump();
    // The system back gesture.
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.text('Discard prompt changes?'), findsOneWidget);
    expect(find.text('Discard changes'), findsOneWidget);
    await tester.tap(find.text('Keep editing'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('prompt-editor-screen')), findsOneWidget);

    await tester.tap(find.byKey(const Key('prompt-editor-done')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('prompt-editor-screen')), findsNothing);
    expect(_composerText(tester), 'rewritten');
  });

  testWidgets('on a server that takes files the attach row is live', (
    tester,
  ) async {
    final conn = await chat3Controller();
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    await tester.tap(find.byKey(const Key('composer-tools-button')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tool-attach')), findsOneWidget);
    expect(find.text('This server takes text only'), findsNothing);
  });
}
