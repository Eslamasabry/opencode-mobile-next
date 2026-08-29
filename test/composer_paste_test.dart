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
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeApi extends OpenCodeApi {
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

Future<ConnectionController> _controller(_FakeApi api) async {
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
    ..status = StreamStatus.connected;
  return controller;
}

Future<TextField> _pumpChatField(
  WidgetTester tester,
  ConnectionController conn,
) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pump();
  await tester.pump();
  return tester.widget<TextField>(
    find.byKey(const Key('chat-composer-field')),
  );
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

  testWidgets('an image committed by the keyboard becomes an attachment', (
    tester,
  ) async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    final field = await _pumpChatField(tester, controller);
    final config = field.contentInsertionConfiguration;
    expect(config, isNotNull);

    config!.onContentInserted(
      KeyboardInsertedContent(
        mimeType: 'image/png',
        uri: 'content://media/pasted.png',
        data: Uint8List.fromList(List<int>.filled(64, 7)),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(find.textContaining('pasted-image-'), findsOneWidget);
    expect(find.textContaining('.png'), findsOneWidget);
  });

  testWidgets('inserted content without bytes is ignored', (tester) async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    final field = await _pumpChatField(tester, controller);

    field.contentInsertionConfiguration!.onContentInserted(
      const KeyboardInsertedContent(
        mimeType: 'image/gif',
        uri: 'content://media/remote.gif',
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(find.textContaining('pasted-image-'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('an oversized inserted image is rejected with the size message', (
    tester,
  ) async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    final field = await _pumpChatField(tester, controller);

    field.contentInsertionConfiguration!.onContentInserted(
      KeyboardInsertedContent(
        mimeType: 'image/png',
        uri: 'content://media/huge.png',
        data: Uint8List(10 * 1024 * 1024 + 1),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(find.textContaining('pasted-image-'), findsNothing);
    expect(
      find.textContaining('Each attachment must be 10 MB or smaller.'),
      findsOneWidget,
    );
  });
}
