import 'package:opencode_mobile/domain/server_gateway.dart';
import 'support/complete_message_history.dart';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/composer_tools.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeApi extends FakeServerGateway with CompleteMessageHistory {
  _FakeApi({this.attachments = true}) : super(baseUrl: 'http://localhost');

  final bool attachments;

  @override
  ServerCapabilities get capabilities => attachments
      ? super.capabilities
      : const ServerCapabilities(promptAttachments: false);

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
        'flavor': 'v2',
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
  // The composer's field is a KitField (a TextFormField); its TextField
  // carries the IME content-insertion hook.
  return tester.widget<TextField>(
    find.descendant(
      of: find.byKey(const Key('chat-composer-field')),
      matching: find.byType(TextField),
    ),
  );
}

const _channel = MethodChannel('oc/clipboard');

/// Answers `oc/clipboard` like the Kotlin side; [calls] records the order.
void _clipboard(
  List<String> calls, {
  required bool hasImage,
  bool big = false,
}) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(_channel, (call) async {
        calls.add(call.method);
        if (call.method == 'hasImage') return hasImage;
        if (!hasImage) return null;
        if (big) throw PlatformException(code: 'too_large');
        return {
          'mime': 'image/png',
          'bytes': Uint8List.fromList(List<int>.filled(64, 7)),
        };
      });
  addTearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, null),
  );
}

Future<void> _pump(WidgetTester tester, _FakeApi api) async {
  final controller = await _controller(api);
  addTearDown(controller.dispose);
  await _pumpChatField(tester, controller);
}

/// Types a word and long-presses it, as a person opens the field's menu.
Future<void> _openMenu(WidgetTester tester) async {
  final field = find.byKey(const Key('chat-composer-field'));
  await tester.enterText(field, 'hello');
  await tester.pump();
  await tester.longPress(field);
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
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

  testWidgets('the menu offers Paste image only while the clipboard has one', (
    tester,
  ) async {
    final calls = <String>[];
    _clipboard(calls, hasImage: false);
    await _pump(tester, _FakeApi());
    await _openMenu(tester);
    expect(find.text('Paste image'), findsNothing);
    expect(calls, isNot(contains('clipboardImage')));
  });

  testWidgets('Paste image attaches the clipboard picture', (tester) async {
    final calls = <String>[];
    _clipboard(calls, hasImage: true);
    await _pump(tester, _FakeApi());
    await _openMenu(tester);
    // Looking is cheap; the picture is read only on the tap.
    expect(calls, ['hasImage']);
    await tester.tap(find.text('Paste image'));
    await tester.pump();
    await tester.pump();
    await tester.pump();
    expect(calls, ['hasImage', 'clipboardImage']);
    expect(find.textContaining('pasted-image-'), findsOneWidget);
    expect(find.textContaining('.png'), findsOneWidget);
  });

  testWidgets('a text-only agent refuses with its usual note', (tester) async {
    final calls = <String>[];
    _clipboard(calls, hasImage: true);
    await _pump(tester, _FakeApi(attachments: false));
    await _openMenu(tester);
    await tester.tap(find.text('Paste image'));
    await tester.pump();
    await tester.pump();
    expect(
      find.text(
        'This server supports text only. Remove attachments before sending.',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('pasted-image-'), findsNothing);
    expect(calls, isNot(contains('clipboardImage')));
  });

  testWidgets('a too-large clipboard picture says the size limit', (
    tester,
  ) async {
    _clipboard(<String>[], hasImage: true, big: true);
    await _pump(tester, _FakeApi());
    await _openMenu(tester);
    await tester.tap(find.text('Paste image'));
    await tester.pump();
    await tester.pump();
    expect(
      find.textContaining('Each attachment must be 10 MB or smaller.'),
      findsOneWidget,
    );
    expect(find.textContaining('pasted-image-'), findsNothing);
  });

  testWidgets(
    'the + sheet lists Paste image from clipboard when there is one',
    (tester) async {
      _clipboard(<String>[], hasImage: true);
      await _pump(tester, _FakeApi());
      await openComposerTools(tester);
      await tester.pumpAndSettle();
      expect(find.text('Paste image from clipboard'), findsOneWidget);
      await tester.tap(find.byKey(const Key('composer-tool-paste-image')));
      await tester.pumpAndSettle();
      expect(find.textContaining('pasted-image-'), findsOneWidget);
    },
  );

  testWidgets(
    'the + sheet leaves the row out with no picture on the clipboard',
    (tester) async {
      _clipboard(<String>[], hasImage: false);
      await _pump(tester, _FakeApi());
      await openComposerTools(tester);
      await tester.pumpAndSettle();
      expect(find.text('Paste image from clipboard'), findsNothing);
    },
  );
}
