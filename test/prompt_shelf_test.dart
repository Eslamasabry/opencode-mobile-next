import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'support/complete_message_history.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/prompt_shelf.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/prompt_history_navigation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';
import '../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

class _RefusingStore extends InMemorySharedPreferencesStore {
  _RefusingStore() : super.withData({});
  bool refuse = true;
  @override
  Future<bool> setValue(String valueType, String key, Object value) async =>
      refuse ? false : super.setValue(valueType, key, value);
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
        time: MsgTime(created: 1),
      ),
      parts: [Part(type: 'text', text: 'prior prompt')],
    ),
  ];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  @override
  ServerProfile get profile =>
      ServerProfile(id: 'a', name: 'A', baseUrl: 'http://localhost');
}

class _HistoryProfiles extends ProfileStore {
  _HistoryProfiles(SharedPreferences prefs) : super(prefs: prefs);
  bool present = true;
  @override
  List<ServerProfile> get profiles => present
      ? [ServerProfile(id: 'a', name: 'A', baseUrl: 'http://localhost')]
      : [];
}

const _attachment = PromptAttachment(
  mime: 'text/plain',
  filename: 'note.txt',
  url: 'data:text/plain;base64,aGVsbG8=',
);
const _reference = ReviewReference(
  id: 'ref1',
  kind: ReviewReferenceKind.hunk,
  path: 'lib/a.dart',
  scope: ReviewReferenceScope.workingTree,
  lineLabel: '1–2',
  snippet: '+hello',
  comment: 'Check this',
  added: 1,
  removed: 0,
  status: 'modified',
);
StashedPrompt _prompt(String id) => StashedPrompt(
  id: id,
  text: 'مرحبا $id',
  createdAt: int.tryParse(id) ?? 1,
  directory: '/project',
  attachments: [_attachment],
  references: [_reference],
);

Future<(_Controller, ReviewHandoffStore)> _pump(
  WidgetTester tester, {
  double? width,
}) async {
  if (width != null) {
    tester.view.physicalSize = Size(width, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }
  if (Platform.environment['OC_PROMPT_PREVIEW'] != null) {
    await tester.runAsync(loadCaptureFonts);
  }
  final prefs = await SharedPreferences.getInstance();
  final c = _Controller(_HistoryProfiles(prefs))
    ..api = _Api()
    ..status = StreamStatus.connected;
  c.sessionsById['s'] = Session(id: 's', title: 'Chat');
  final handoff = ReviewHandoffStore();
  addTearDown(c.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(c)],
      child: RepaintBoundary(
        key: const ValueKey('prompt-preview'),
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(width == 320 ? 1.7 : 1)),
            child: child!,
          ),
          home: ChatScreen(sessionID: 's', handoffStore: handoff),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (c, handoff);
}

Future<void> _tool(WidgetTester tester, String key) async {
  await tester.tap(find.byKey(const Key('composer-tools-button')));
  await tester.pumpAndSettle();
  await tester.ensureVisible(find.byKey(Key(key)));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(Key(key)));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'late successful sends do not recreate a deleted profile history',
    () async {
      final prefs = await SharedPreferences.getInstance();
      final profiles = _HistoryProfiles(prefs);
      final c = _Controller(profiles);
      addTearDown(c.dispose);
      expect(c.canUsePromptShelf, isTrue);
      await c.rememberSentPrompt('a', 'delivered');
      expect(PromptShelfStore(prefs).history('a'), ['delivered']);
      await prefs.remove('oc.promptHistory.a');
      profiles.present = false;
      expect(c.canUsePromptShelf, isFalse);
      await c.rememberSentPrompt('a', 'late completion');
      expect(prefs.containsKey('oc.promptHistory.a'), isFalse);
    },
  );

  test(
    'stash survives reload with full attachments/references, separate from drafts and other profiles',
    () async {
      final prefs = await SharedPreferences.getInstance();
      final shelf = PromptShelfStore(prefs);
      await Future.wait([
        shelf.stash('a', _prompt('1')),
        shelf.stash('a', _prompt('2')),
        shelf.stash('b', _prompt('3')),
      ]);
      final reloaded = PromptShelfStore(prefs);
      expect(reloaded.stashes('a').map((p) => p.id), ['2', '1']);
      expect(reloaded.stashes('a').last.toJson(), _prompt('1').toJson());
      expect(prefs.containsKey('oc.sessionDrafts'), isFalse);
      await shelf.remove('a', '1');
      expect(shelf.stashes('a').map((p) => p.id), ['2']);
      expect(shelf.stashes('b').single.id, '3');
      final profileStore = ProfileStore(prefs: prefs);
      for (final key in profileStore.profileScopedPreferenceKeys('a')) {
        await prefs.remove(key);
      }
      shelf.forget('a');
      expect(shelf.stashes('a'), isEmpty);
      expect(shelf.stashes('b').length, 1);
    },
  );

  test(
    '50 stash entries never evict a saved prompt; history is bounded separately',
    () async {
      final shelf = PromptShelfStore(await SharedPreferences.getInstance());
      for (var i = 0; i < 50; i++) {
        await shelf.stash('a', _prompt('$i'));
      }
      await expectLater(
        shelf.stash('a', _prompt('overflow')),
        throwsStateError,
      );
      expect(shelf.stashes('a').length, 50);
      for (var i = 0; i < 52; i++) {
        await shelf.recordSent('a', 'sent $i');
      }
      expect(shelf.history('a').length, 50);
      expect(shelf.history('a').first, 'sent 51');
      expect(shelf.stashes('a').length, 50);
    },
  );

  test(
    'failed save retains acknowledged state and later writes recover',
    () async {
      final old = SharedPreferencesStorePlatform.instance;
      final fake = _RefusingStore();
      SharedPreferencesStorePlatform.instance = fake;
      SharedPreferences.resetStatic();
      addTearDown(() {
        SharedPreferencesStorePlatform.instance = old;
        SharedPreferences.resetStatic();
      });
      final shelf = PromptShelfStore(await SharedPreferences.getInstance());
      await expectLater(shelf.stash('a', _prompt('1')), throwsStateError);
      expect(shelf.stashes('a'), isEmpty);
      fake.refuse = false;
      await shelf.stash('a', _prompt('2'));
      expect(shelf.stashes('a').single.id, '2');
    },
  );

  test(
    'history boundary filtering preserves selection, multiline movement and composing text',
    () {
      final up = KeyDownEvent(
        physicalKey: PhysicalKeyboardKey.arrowUp,
        logicalKey: LogicalKeyboardKey.arrowUp,
        timeStamp: Duration.zero,
      );
      const start = TextEditingValue(
        text: 'first\nsecond',
        selection: TextSelection.collapsed(offset: 0),
      );
      expect(isPromptHistoryKey(up, start, suggestionsOpen: false), isTrue);
      expect(
        isPromptHistoryKey(
          up,
          start.copyWith(selection: const TextSelection.collapsed(offset: 7)),
          suggestionsOpen: false,
        ),
        isFalse,
      );
      expect(
        isPromptHistoryKey(
          up,
          start.copyWith(
            selection: const TextSelection(baseOffset: 0, extentOffset: 3),
          ),
          suggestionsOpen: false,
        ),
        isFalse,
      );
      expect(
        isPromptHistoryKey(
          up,
          start.copyWith(composing: const TextRange(start: 0, end: 2)),
          suggestionsOpen: false,
        ),
        isFalse,
      );
      expect(isPromptHistoryKey(up, start, suggestionsOpen: true), isFalse);
      final navigation = PromptHistoryNavigation();
      expect(navigation.move(true, start, ['recent', 'older'])?.text, 'recent');
      expect(navigation.move(true, start, [])?.text, 'older');
      expect(navigation.restore(), start);
    },
  );

  testWidgets(
    'real composer arrows restore the exact draft and leave modifier/suggestion keys alone',
    (tester) async {
      final (c, _) = await _pump(tester);
      final fieldFinder = find.byKey(const Key('chat-composer-field'));
      await tester.enterText(fieldFinder, 'my draft');
      final field = tester.widget<TextField>(fieldFinder).controller!;
      field.selection = const TextSelection.collapsed(offset: 0);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
      expect(field.text, 'my draft');
      field.selection = const TextSelection.collapsed(offset: 0);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
      await tester.pump();
      expect(field.text, 'prior prompt');
      await tester.pump(const Duration(milliseconds: 700));
      expect(c.sessionDraft('s'), 'my draft');
      field.selection = TextSelection.collapsed(offset: field.text.length);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pump();
      expect(field.text, 'my draft');
      expect(field.selection.extentOffset, 0);
      await tester.enterText(fieldFinder, '/help');
      await tester.pump();
      field.selection = const TextSelection.collapsed(offset: 0);
      await tester.pump();
      expect(field.text, '/help');
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
      expect(field.text, '/help');
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets(
    'temporary attachments are named and partial restore keeps the source entry',
    (tester) async {
      final (c, _) = await _pump(tester);
      await c.savePromptStash(
        const StashedPrompt(
          id: 'temporary',
          text: 'restore available text',
          createdAt: 1,
          attachments: [
            PromptAttachment(
              mime: 'image/png',
              filename: 'temporary.png',
              url: 'content://keyboard/temporary',
            ),
          ],
        ),
        locationRevision: c.locationRevision,
      );
      await _tool(tester, 'composer-tool-saved');
      await tester.tap(find.byKey(const ValueKey('restore-stash-temporary')));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Temporary attachments: temporary.png'),
        findsOneWidget,
      );
      await tester.tap(find.text('Restore available content'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<TextField>(find.byKey(const Key('chat-composer-field')))
            .controller!
            .text,
        'restore available text',
      );
      expect(
        c.promptStash.single.attachments.single.url,
        'content://keyboard/temporary',
      );
      await tester.pumpWidget(const SizedBox());
    },
  );

  for (final width in [411.0, 320.0]) {
    testWidgets(
      'stash and restore preserve a current draft and structured reference at $width',
      (tester) async {
        final (c, handoff) = await _pump(tester, width: width);
        final fieldFinder = find.byKey(const Key('chat-composer-field'));
        handoff.stage('s', _reference);
        await tester.enterText(fieldFinder, 'save this');
        await _tool(tester, 'composer-tool-stash');
        expect(
          c.promptStash.single.references.single.toPromptText(),
          _reference.toPromptText(),
        );
        expect(handoff.referencesFor('s'), isEmpty);
        expect(tester.widget<TextField>(fieldFinder).controller!.text, isEmpty);
        final id = c.promptStash.single.id;
        await tester.enterText(fieldFinder, 'keep current');
        await _tool(tester, 'composer-tool-saved');
        final preview = Platform.environment['OC_PROMPT_PREVIEW'];
        if (preview != null && width == 411) {
          await tester.runAsync(() async {
            final boundary = tester.renderObject<RenderRepaintBoundary>(
              find.byKey(const ValueKey('prompt-preview')),
            );
            final image = await boundary.toImage(pixelRatio: 1);
            final data = await image.toByteData(format: ui.ImageByteFormat.png);
            await File(preview).writeAsBytes(data!.buffer.asUint8List());
            image.dispose();
          });
        }
        await tester.ensureVisible(find.byKey(ValueKey('restore-stash-$id')));
        await tester.pumpAndSettle();
        expect(
          find.byKey(ValueKey('restore-stash-$id')).hitTestable(),
          findsOneWidget,
        );
        await tester.tap(find.byKey(ValueKey('restore-stash-$id')));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Restore').last);
        await tester.pumpAndSettle();
        expect(
          tester.widget<TextField>(fieldFinder).controller!.text,
          'save this',
        );
        expect(c.promptStash.single.text, 'keep current');
        expect(
          handoff.referencesFor('s').single.toPromptText(),
          _reference.toPromptText(),
        );
        await tester.pumpWidget(const SizedBox());
      },
    );
  }
}
