import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart'
    show KitIconButton, KitMenuPanel, KitTopBar;
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';

/// Stable chat, 2026-09-13. The 390dp captures showed the session title
/// losing most of its width to an always-labelled Tasks shortcut, and a
/// bare "…" under the newest reply that read as the answer trailing off.
/// These tests pin the phone app bar, the message-actions control, and the
/// compose/Send/Stop controls on a 320dp phone with the keyboard open at
/// 1x, 2x and 2.5x text, without touching drafts or navigation.
const _sessionID = 'session-1';
const _longTitle = 'Fix the flaky checkout test before the release build';
const _draft = 'Keep the basket after reopening.';

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api({String? title}) : super(baseUrl: 'http://localhost') {
    current = Session(id: _sessionID, title: title);
  }

  late Session current;
  List<MessageWithParts> transcript = const [];

  @override
  Future<List<Session>> sessions() async => [current];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => transcript;

  @override
  Future<Session> session(String id) async => current;
}

MessageWithParts _reply(String id, String text) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: _sessionID,
    role: 'assistant',
    time: MsgTime(created: 1, completed: 2),
  ),
  parts: [Part(id: '$id-text', messageID: id, type: 'text', text: text)],
);

Future<ConnectionController> _controller(_Api api) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  return ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected
    ..sessionsById = {api.current.id: api.current};
}

/// Pumps the chat on a phone of [size] at [textScale]. The view itself is
/// resized so MediaQuery, the Scaffold and the keyboard inset all agree.
Future<void> _pumpChat(
  WidgetTester tester,
  ConnectionController conn, {
  required Size size,
  double textScale = 1,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: const ChatScreen(sessionID: _sessionID),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// The busy composer animates its activity ring forever, so a busy chat
/// pumps explicit frames instead of settling.
Future<void> _pumpFrames(WidgetTester tester) async {
  for (var i = 0; i < 4; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Finder get _title => find.byKey(const Key('chat-title'));
Finder get _tasks => find.byKey(const Key('running-work-indicator'));
Finder get _field => find.byKey(const Key('chat-composer-field'));
Finder get _send => find.byKey(const Key('chat-send-button'));
Finder get _stop => find.byKey(const Key('chat-stop-button'));

String _draftText(WidgetTester tester) =>
    tester.widget<TextFormField>(_field).controller!.text;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // ProfileStore reads passwords through flutter_secure_storage, whose
    // unmocked channel never answers inside testWidgets.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  for (final scale in [1.0, 2.0, 2.5]) {
    testWidgets(
      'a 320dp app bar at ${scale}x keeps a compact title and task details reachable',
      (tester) async {
        final conn = await _controller(_Api(title: _longTitle));
        addTearDown(conn.dispose);
        await _pumpChat(
          tester,
          conn,
          size: const Size(320, 640),
          textScale: scale,
        );
        expect(tester.takeException(), isNull);

        expect(_tasks, findsNothing);
        expect(find.byTooltip('Tasks · 0 running'), findsNothing);
        final titleRect = tester.getRect(_title);
        final appBar = tester.getRect(find.byType(KitTopBar));
        expect(titleRect.top, greaterThanOrEqualTo(appBar.top));
        expect(titleRect.bottom, lessThanOrEqualTo(appBar.bottom + .5));

        await tester.tap(find.byKey(const ValueKey('session-actions-button')));
        await tester.pumpAndSettle();
        expect(find.text(_longTitle), findsWidgets);
        // Task details are the conversation menu's Subagents (P10.2).
        final results = find.byKey(const ValueKey('session-menu-subagents'));
        await tester.ensureVisible(results);
        await tester.pumpAndSettle();
        expect(results.hitTestable(), findsOneWidget);
        await tester.tap(results);
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 400));
        expect(find.byKey(const Key('running-work-sheet')), findsOneWidget);
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }

  testWidgets('a wide idle app bar also omits empty task chrome', (
    tester,
  ) async {
    final conn = await _controller(_Api(title: _longTitle));
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn, size: const Size(800, 600));
    expect(tester.takeException(), isNull);
    expect(_tasks, findsNothing);
    expect(find.text('Tasks'), findsNothing);
    expect(
      find.descendant(of: find.byType(KitTopBar), matching: _title),
      findsOneWidget,
    );
  });

  testWidgets('a short title stays on one line in a one-row bar at 1x', (
    tester,
  ) async {
    final conn = await _controller(_Api(title: 'Fix CI'));
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn, size: const Size(390, 844));
    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(find.byType(KitTopBar)).height,
      lessThanOrEqualTo(64),
    );
    expect(tester.getSize(_title).height, lessThanOrEqualTo(31));
  });

  testWidgets(
    'the message-actions control is a labelled button, not a bare ellipsis',
    (tester) async {
      final api = _Api(title: 'Review the basket')
        ..transcript = [_reply('a1', 'The checkout is ready to review.')];
      final conn = await _controller(api);
      addTearDown(conn.dispose);
      final semantics = tester.ensureSemantics();
      await _pumpChat(tester, conn, size: const Size(390, 844));
      expect(tester.takeException(), isNull);

      final actions = find.byKey(const ValueKey('message-actions-a1'));
      expect(actions, findsOneWidget);
      expect(find.text('…'), findsNothing);
      final size = tester.getSize(actions);
      expect(size.width, greaterThanOrEqualTo(44));
      expect(size.height, greaterThanOrEqualTo(44));
      expect(find.bySemanticsLabel('More for this reply'), findsOneWidget);

      await tester.tap(actions);
      await tester.pumpAndSettle();
      // More opens the kit's one menu; Copy stays beside it, not in it.
      expect(find.byType(KitMenuPanel), findsOneWidget);
      expect(find.byKey(const ValueKey('message-menu-copy')), findsNothing);
      semantics.dispose();
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  for (final scale in [1.0, 2.0, 2.5]) {
    testWidgets(
      'at 320dp and ${scale}x the keyboard leaves the field and Send on '
      'screen while a run is active, and the draft survives',
      (tester) async {
        final conn = await _controller(_Api(title: _longTitle));
        addTearDown(conn.dispose);
        await _pumpChat(
          tester,
          conn,
          size: const Size(320, 640),
          textScale: scale,
        );
        await tester.tap(_field);
        await tester.enterText(_field, _draft);
        await tester.pump();

        // A 300dp keyboard on a 640dp phone.
        tester.view.viewInsets = const FakeViewPadding(bottom: 300);
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 300));
        expect(tester.takeException(), isNull);
        const visibleBottom = 640.0 - 300;
        final appBarBottom = tester.getRect(find.byType(KitTopBar)).bottom;
        expect(tester.getRect(_field).top, greaterThanOrEqualTo(appBarBottom));
        expect(tester.getRect(_field).bottom, lessThanOrEqualTo(visibleBottom));
        expect(tester.getRect(_send).bottom, lessThanOrEqualTo(visibleBottom));
        expect(
          tester.getSemantics(_send),
          isSemantics(isEnabled: true, hasTapAction: true),
        );

        // A run starts: Send stays above the keyboard, and the delivery
        // words do not push it off screen. Typed words keep Send; Stop leads
        // the row, never beside it.
        conn.busySessions.add(_sessionID);
        conn.notifyListeners();
        await _pumpFrames(tester);
        expect(tester.takeException(), isNull);
        expect(find.byKey(const ValueKey('kit-composer-stop')), findsOneWidget);
        expect(find.text('Sends after this reply'), findsOneWidget);
        expect(tester.getRect(_send).bottom, lessThanOrEqualTo(visibleBottom));
        expect(tester.getRect(_field).bottom, lessThanOrEqualTo(visibleBottom));
        expect(tester.getRect(_field).top, greaterThanOrEqualTo(appBarBottom));
        expect(_draftText(tester), _draft);
        final editor = tester
            .state<EditableTextState>(
              find.descendant(of: _field, matching: find.byType(EditableText)),
            )
            .renderEditable;
        final fieldRect = tester.getRect(_field);
        expect(
          fieldRect.height,
          greaterThanOrEqualTo(editor.preferredLineHeight),
        );
        // KitField gives the editor a 48 dp target; its composer variant
        // has no inner padding (the glass supplies the surrounding inset).
        expect(fieldRect.height, greaterThanOrEqualTo(48));
        final editableTop = editor.localToGlobal(Offset.zero).dy;
        expect(
          editableTop,
          greaterThanOrEqualTo(fieldRect.top),
          reason: 'Draft text must not paint above its field into the hint',
        );
        expect(
          editableTop + editor.size.height,
          lessThanOrEqualTo(fieldRect.bottom),
        );
        expect(
          tester
              .widget<EditableText>(
                find.descendant(
                  of: _field,
                  matching: find.byType(EditableText),
                ),
              )
              .focusNode
              .hasFocus,
          isTrue,
        );

        // Compact rendering must not turn the editor into a single-line
        // input, which would silently remove newlines from user edits.
        const multilineDraft = 'Keep the basket.\nCheck reopening.';
        await tester.enterText(_field, multilineDraft);
        await _pumpFrames(tester);
        expect(_draftText(tester), multilineDraft);

        // The keyboard closes and the run ends: the draft is still there.
        tester.view.viewInsets = FakeViewPadding.zero;
        conn.busySessions.remove(_sessionID);
        conn.notifyListeners();
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        expect(_draftText(tester), multilineDraft);
        expect(_stop, findsNothing);
        await tester.pump(const Duration(milliseconds: 700));
        expect(conn.sessionDraft(_sessionID), multilineDraft);
      },
    );
  }

  testWidgets('rotating a phone keeps the Tasks shortcut in the bar without '
      'remounting the editor or losing the draft', (tester) async {
    final conn = await _controller(_Api(title: _longTitle));
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn, size: const Size(360, 740));
    // The shortcut is contextual now: rotate while a related task is active.
    conn.sessionsById['child-task'] = Session(
      id: 'child-task',
      parentID: _sessionID,
      title: 'Run checkout tests',
    );
    conn.busySessions.add('child-task');
    conn.notifyListeners();
    await tester.pumpAndSettle();
    await tester.enterText(_field, 'Still writing');
    await tester.pump();
    final editable = find.descendant(
      of: _field,
      matching: find.byType(EditableText),
    );
    final editor = tester.state<EditableTextState>(editable);
    expect(tester.widget(_tasks), isA<KitIconButton>());

    // A phone on its side is short: the bar keeps its phone arrangement
    // (KitTopBar, kit-v2.md §8.1).
    tester.view.physicalSize = const Size(740, 360);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(tester.widget(_tasks), isA<KitIconButton>());
    expect(tester.state<EditableTextState>(editable), same(editor));
    expect(_draftText(tester), 'Still writing');
    await tester.pump(const Duration(milliseconds: 700));
    expect(conn.sessionDraft(_sessionID), 'Still writing');
  });
}
