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

/// Audit UX-P0-03: the composer used to ring the prompt field with five
/// equal-weight controls, so the field was the least stable element on a
/// narrow phone at a large text scale. These tests pin the new anatomy —
/// one leading tools button, a secondary model chip, and Send — and the
/// share of the composer the field must keep.
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

Future<ConnectionController> _controller() async {
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
    ..api = _FakeApi()
    ..status = StreamStatus.connected;
}

Future<void> _pumpChat(
  WidgetTester tester,
  ConnectionController conn, {
  required Size size,
  double textScale = 2.5,
}) async {
  await tester.binding.setSurfaceSize(size);
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: MaterialApp(
        // setSurfaceSize resizes the render surface but leaves the view's
        // reported size at the 800x600 default, and the composer picks its
        // layout from MediaQuery, so both have to say the same thing.
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(size: size, textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: const ChatScreen(sessionID: 'session-1'),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// The prompt field's width as a fraction of the composer surface it sits in.
double _fieldShare(WidgetTester tester) {
  final field = tester.getSize(find.byKey(const Key('chat-composer-field')));
  final surface = tester.getSize(
    find.byKey(const Key('chat-composer-surface')),
  );
  return field.width / surface.width;
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

  testWidgets('the prompt field stays dominant on a 360dp phone at 2.5x text', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller, size: const Size(360, 760));

    // Only the leading tools button, the model context chip, and Send
    // surround the field; Attach and Voice moved into the tools sheet.
    expect(find.byKey(const Key('composer-tools-button')), findsOneWidget);
    expect(find.byKey(const Key('composer-model-context')), findsOneWidget);
    expect(find.byKey(const Key('chat-send-button')), findsOneWidget);
    expect(find.byIcon(Icons.attach_file_rounded), findsNothing);
    expect(find.byIcon(Icons.mic_none_rounded), findsNothing);

    expect(_fieldShare(tester), greaterThanOrEqualTo(0.9));
    expect(tester.takeException(), isNull);
  });

  testWidgets('the compact composer keeps most of its width for the field', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    // Height under 520 selects the compact single-row composer, where the
    // field used to share its row with four other controls.
    await _pumpChat(tester, controller, size: const Size(360, 420));

    expect(find.byKey(const Key('composer-tools-button')), findsOneWidget);
    expect(find.byIcon(Icons.attach_file_rounded), findsNothing);
    expect(find.byIcon(Icons.mic_none_rounded), findsNothing);
    // The model/agent is a context chip above the field, not a fifth icon
    // competing for the field's row.
    expect(find.byKey(const Key('composer-model-context')), findsOneWidget);

    // Before UX-P0-03 the four leading icons plus Send left the field 82 of
    // the composer's 340 dp here — 24% — at this text scale. It now keeps
    // 222 dp, and this floor stops another control from creeping back in.
    expect(_fieldShare(tester), greaterThanOrEqualTo(0.55));
    expect(tester.takeException(), isNull);
  });

  testWidgets('every collapsed tool stays reachable from the tools sheet', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pumpChat(
      tester,
      controller,
      size: const Size(360, 760),
      textScale: 1,
    );

    await tester.tap(find.byKey(const Key('composer-tools-button')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tools-sheet')), findsOneWidget);
    final tools = [
      const Key('composer-tool-commands'),
      const Key('composer-tool-attach'),
      const Key('composer-tool-voice'),
    ];
    for (final tool in tools) {
      final finder = find.byKey(tool);
      expect(finder, findsOneWidget);
      // Every entry keeps the Android minimum target it had as a button.
      expect(tester.getSize(finder).height, greaterThanOrEqualTo(48));
      final node = tester.getSemantics(finder);
      expect(node.label, isNotEmpty);
    }

    await tester.tap(find.byKey(const Key('composer-tool-commands')));
    await tester.pumpAndSettle();
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('command-launcher-search')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the tools sheet closes Attach and Voice while a run is active', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pumpChat(
      tester,
      controller,
      size: const Size(360, 760),
      textScale: 1,
    );
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    // The busy chat animates a typing indicator forever, so this pumps
    // explicit frames instead of settling.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    await tester.tap(find.byKey(const Key('composer-tools-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(
      tester
          .widget<ListTile>(find.byKey(const Key('composer-tool-attach')))
          .enabled,
      isFalse,
    );
    expect(
      tester
          .widget<ListTile>(find.byKey(const Key('composer-tool-voice')))
          .enabled,
      isFalse,
    );
    // Commands stay available: they do not depend on the run finishing.
    expect(
      tester
          .widget<ListTile>(find.byKey(const Key('composer-tool-commands')))
          .enabled,
      isTrue,
    );
  });
}
