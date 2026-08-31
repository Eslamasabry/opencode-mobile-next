import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/desktop/file_drop.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A widget test that runs with the platform reported as Linux desktop. The
/// override must be cleared inside the body: flutter_test asserts no
/// foundation debug variable outlives the test, so tearDown runs too late.
void desktopTest(
  String description,
  Future<void> Function(WidgetTester tester) body,
) {
  testWidgets(description, (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.linux;
    try {
      await body(tester);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}

class _ChatApi extends OpenCodeApi {
  _ChatApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [
    MessageWithParts(
      info: MessageInfo(
        id: 'm1',
        sessionID: 'session-1',
        role: 'user',
        time: MsgTime(created: 1, completed: 2),
      ),
      parts: [Part(id: 'p1', type: 'text', text: 'hello')],
    ),
  ];

  @override
  Future<Session> session(String id) async => Session(id: id);
}

DroppedFile _file(String name, {required int bytes, String? mimeType}) {
  final payload = Uint8List.fromList(List<int>.filled(bytes, 0x61));
  return DroppedFile(
    name: name,
    mimeType: mimeType,
    length: () async => bytes,
    readBytes: () async => payload,
  );
}

Future<void> _pumpChat(WidgetTester tester) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final connection = ConnectionController(ProfileStore(prefs: prefs))
    ..api = _ChatApi()
    ..status = StreamStatus.connected;
  addTearDown(connection.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(connection)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _drop(WidgetTester tester, List<DroppedFile> files) async {
  final state = tester.state<DesktopFileDropTargetState>(
    find.byType(DesktopFileDropTarget),
  );
  await state.debugHandleDrop(files);
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  desktopTest('a dropped file becomes a composer attachment', (tester) async {
    await _pumpChat(tester);

    await _drop(tester, [
      _file('notes.txt', bytes: 12, mimeType: 'text/plain'),
    ]);

    expect(find.text('notes.txt'), findsOneWidget);
  });

  desktopTest('two dropped files both attach', (tester) async {
    await _pumpChat(tester);

    await _drop(tester, [
      _file('one.txt', bytes: 4, mimeType: 'text/plain'),
      _file('two.txt', bytes: 4, mimeType: 'text/plain'),
    ]);

    expect(find.text('one.txt'), findsOneWidget);
    expect(find.text('two.txt'), findsOneWidget);
  });

  desktopTest('an oversized drop is refused by the existing cap', (
    tester,
  ) async {
    await _pumpChat(tester);

    await _drop(tester, [
      _file('huge.bin', bytes: 10 * 1024 * 1024 + 1),
    ]);

    expect(find.text('huge.bin'), findsNothing);
    expect(
      find.textContaining('10 MB or smaller'),
      findsOneWidget,
      reason: 'the drop path reuses the picker\'s per-file cap',
    );
  });

  desktopTest('the drop stops at the five-attachment cap', (tester) async {
    await _pumpChat(tester);

    await _drop(tester, [
      for (var i = 0; i < 6; i++)
        _file('file$i.txt', bytes: 4, mimeType: 'text/plain'),
    ]);

    // The chip strip scrolls, so only the leading chips are laid out; the
    // load-bearing assertion is that the sixth file was refused by the same
    // cap the picker enforces.
    expect(find.text('file0.txt'), findsOneWidget);
    expect(find.text('file5.txt', skipOffstage: false), findsNothing);
    expect(find.textContaining('up to 5 files'), findsOneWidget);
  });

  desktopTest('a dropped file keeps its bytes in the attachment payload', (
    tester,
  ) async {
    await _pumpChat(tester);

    await _drop(tester, [
      _file('payload.txt', bytes: 3, mimeType: 'text/plain'),
    ]);

    // Open the chip's preview: it decodes the data URL the drop produced, so
    // seeing the bytes back proves the payload survived the pipeline.
    await tester.tap(find.text('payload.txt'));
    await tester.pumpAndSettle();
    expect(find.textContaining(utf8.decode(Uint8List.fromList([97, 97, 97]))),
        findsWidgets);
  });

  testWidgets('android installs no drop target', (tester) async {
    await _pumpChat(tester);
    expect(find.byType(DesktopFileDropTarget), findsOneWidget);
    // The widget is in the tree but is a pass-through: no highlight layer and
    // no plugin listener exist off desktop.
    expect(find.byKey(const ValueKey('composer-drop-highlight')), findsNothing);
  });
}
