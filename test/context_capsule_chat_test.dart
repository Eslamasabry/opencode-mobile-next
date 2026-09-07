import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/draft_attachments.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_drafts.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/context_capsule_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');
  @override
  Future<List<Session>> sessions() async => [];
  @override
  Future<Map<String, String>> sessionStatuses() async => {};
  @override
  Future<List<MessageWithParts>> messages(String id) async => [];
  @override
  Future<Session> session(String id) async =>
      Session(id: id, title: 'Repair checkout');
}

class _Controller extends ConnectionController {
  _Controller(super.store, {super.draftAttachmentVault});
  void changeLocation() {
    locationRevision++;
    notifyListeners();
  }
}

Future<_Controller> _controller({DraftAttachmentVault? vault}) async {
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  return _Controller(store, draftAttachmentVault: vault)
    ..api = _Api()
    ..status = StreamStatus.disconnected;
}

Future<void> _chat(WidgetTester tester, ConnectionController conn) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _open(WidgetTester tester) async {
  await tester.tap(find.byKey(const Key('composer-tools-button')));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Context capsule'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Error'));
  await tester.pumpAndSettle();
  await tester.enterText(find.byType(TextField).last, 'Checkout fails offline');
}

String _draft(WidgetTester tester) => tester
    .widget<TextField>(find.byKey(const Key('chat-composer-field')))
    .controller!
    .text;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'oc.profiles': jsonEncode([
        {
          'id': 'profile-1',
          'name': 'Server',
          'baseUrl': 'http://localhost',
          'username': '',
        },
      ]),
      'oc.activeProfile': 'profile-1',
    });
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets(
    'Apply preserves composer, persists offline and restores after controller restart',
    (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      await _chat(tester, conn);
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'Keep this instruction.',
      );
      await _open(tester);
      tester.testTextInput.hide();
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Apply to draft'),
        200,
        scrollable: find
            .descendant(
              of: find.byType(ContextCapsuleScreen),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.tap(find.text('Apply to draft'));
      await tester.pumpAndSettle();
      final expected =
          'Keep this instruction.\n\nContext capsule\n\nError\nCheckout fails offline';
      expect(_draft(tester), expected);
      expect(conn.queuedPromptsFor('session-1'), isEmpty);
      expect(
        SessionDraftStore(prefs: conn.store.prefs).load().values.single.text,
        expected,
      );
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
      final restarted = await _controller();
      addTearDown(restarted.dispose);
      await _chat(tester, restarted);
      expect(_draft(tester), expected);
    },
  );

  testWidgets('Cancel leaves existing composer unchanged', (tester) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    await _chat(tester, conn);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Original',
    );
    await _open(tester);
    tester.testTextInput.hide();
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Cancel'),
      200,
      scrollable: find
          .descendant(
            of: find.byType(ContextCapsuleScreen),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(_draft(tester), 'Original');
  });

  testWidgets(
    'location change while capsule is open refuses apply even after returning',
    (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      await _chat(tester, conn);
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'Original',
      );
      await _open(tester);
      conn.changeLocation();
      await tester.pumpAndSettle();
      conn.changeLocation();
      await tester.pumpAndSettle();
      expect(find.text('Apply to draft'), findsNothing);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(_draft(tester), 'Original');
    },
  );

  test(
    'applied bundle shares attachment restoration and profile deletion',
    () async {
      final dir = await Directory.systemTemp.createTemp('context-capsule-');
      addTearDown(() => dir.delete(recursive: true));
      final vault = DraftAttachmentVault(directory: () async => dir);
      final conn = await _controller(vault: vault);
      addTearDown(conn.dispose);
      const old = PromptAttachment(
        filename: 'original.txt',
        mime: 'text/plain',
        url: 'data:text/plain;base64,b2xk',
      );
      const image = PromptAttachment(
        filename: 'screen.png',
        mime: 'image/png',
        url: 'data:image/png;base64,aW1hZ2U=',
      );
      await conn.saveSessionDraft(
        'session-1',
        'Original\n\nContext capsule\n\nError\nFailed',
        attachments: [old, image],
      );
      final restarted = await _controller(vault: vault);
      addTearDown(restarted.dispose);
      final restored = await restarted.restoreDraftAttachments(
        'session-1',
        profileID: 'profile-1',
        directory: null,
        workspace: null,
      );
      expect(restored.attachments.map((a) => a.filename), [
        'original.txt',
        'screen.png',
      ]);
      expect(restored.unavailable, isEmpty);
      await restarted.deleteProfileAndLocalData('profile-1');
      expect(SessionDraftStore(prefs: restarted.store.prefs).load(), isEmpty);
      expect(dir.listSync(recursive: true).whereType<File>(), isEmpty);
    },
  );
}
