import 'support/complete_message_history.dart';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_drafts.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeApi extends OpenCodeApi with CompleteMessageHistory {
  _FakeApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<Session> session(String id) async => Session(id: id);
}

Future<ConnectionController> _controller(
  _FakeApi api, {
  StreamStatus status = StreamStatus.connected,
}) async {
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
  final controller = ConnectionController(store)
    ..api = api
    ..status = status;
  return controller;
}

Future<void> _pumpChat(WidgetTester tester, ConnectionController conn) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pump();
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // See offline_queue_test.dart: unmocked flutter_secure_storage reads
    // hang inside testWidgets; answer them with null.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  test('session drafts persist and reload', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = SessionDraftStore(prefs: prefs);
    expect(
      await store.save({
        'session-1': const SessionDraft(
          sessionID: 'session-1',
          text: 'half-written prompt',
          updatedAt: 5,
        ),
      }),
      isTrue,
    );

    final reloaded = SessionDraftStore(prefs: prefs).load();
    expect(reloaded, hasLength(1));
    expect(reloaded['session-1']?.text, 'half-written prompt');
    expect(reloaded['session-1']?.updatedAt, 5);
  });

  test('the newest drafts win when the cap is exceeded', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = SessionDraftStore(prefs: prefs);
    final drafts = {
      for (var i = 0; i < SessionDraftStore.maxDrafts + 5; i++)
        'session-$i': SessionDraft(
          sessionID: 'session-$i',
          text: 'draft $i',
          updatedAt: i,
        ),
    };
    expect(await store.save(drafts), isTrue);

    final reloaded = SessionDraftStore(prefs: prefs).load();
    expect(reloaded, hasLength(SessionDraftStore.maxDrafts));
    // The five oldest were evicted; the newest survive.
    for (var i = 0; i < 5; i++) {
      expect(reloaded.containsKey('session-$i'), isFalse);
    }
    expect(
      reloaded['session-${SessionDraftStore.maxDrafts + 4}']?.text,
      'draft ${SessionDraftStore.maxDrafts + 4}',
    );
  });

  test('saving an empty draft clears the stored entry', () async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);

    await controller.saveSessionDraft('session-1', 'keep me');
    expect(controller.sessionDraft('session-1'), 'keep me');

    await controller.saveSessionDraft('session-1', '   ');
    expect(controller.sessionDraft('session-1'), isNull);
    final prefs = await SharedPreferences.getInstance();
    expect(SessionDraftStore(prefs: prefs).load(), isEmpty);
  });

  testWidgets('composer text survives leaving and reopening a chat', (
    tester,
  ) async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'unsent thought',
    );
    await tester.pump();

    // Navigate away: the route disposes and the draft persists.
    await tester.pumpWidget(const SizedBox());
    expect(controller.sessionDraft('session-1'), 'unsent thought');

    await _pumpChat(tester, controller);
    expect(
      tester
          .widget<TextField>(find.byKey(const Key('chat-composer-field')))
          .controller
          ?.text,
      'unsent thought',
    );
  });

  testWidgets('sending clears the persisted draft', (tester) async {
    final api = _FakeApi();
    final controller = await _controller(
      api,
      status: StreamStatus.disconnected,
    );
    addTearDown(controller.dispose);
    await controller.saveSessionDraft('session-1', 'stale copy');
    await _pumpChat(tester, controller);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'send me',
    );
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await tester.pump();
    await tester.pump();

    // Disconnected send queues the prompt and the draft is gone.
    expect(controller.queuedPromptCount, 1);
    expect(controller.sessionDraft('session-1'), isNull);
  });
}
