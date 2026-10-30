// AUDIT TOOLING ONLY (slice S6 of the style cleanup, 2026-10-21): the real
// ChatScreen with what no other capture shows: a finished reply holding a
// code block, a Markdown table and a quote, and one prompt queued under it
// (the queued bubble). Style, mode and the rest come from AUDIT_* (see
// audit_env.dart):
//
//   TZ=UTC AUDIT_STYLE=paper AUDIT_MODE=light AUDIT_OUT=<root> \
//     flutter test tool/capture/audit/style_s6_transcript_test.dart
//
// Output: <root>/s6-transcript/transcript-dark.png (the audit's own names
// keep "-dark"; AUDIT_MODE decides the mode).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import '../fixtures.dart';

const _dir = 'build/s6-transcript';

const _reply = '''
The cache now sits behind one interface. The store opens once:

```dart
final store = SqliteStore(path);
await store.open();
return CachedGateway(store, gateway);
```

| File | Added | Removed |
| --- | ---: | ---: |
| `lib/state/cache.dart` | 42 | 0 |
| `lib/main.dart` | 3 | 1 |

> Keep the old key readable for one release.
''';

class _Api extends CaptureApi {
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(permissionRequests: true, promptSteering: true);

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

/// One prompt waiting to send in the checkout session.
class _Controller extends CaptureController {
  _Controller(super.store);

  @override
  List<QueuedPrompt> queuedPromptsFor(String sessionID) => [
    if (sessionID == checkoutSessionID)
      QueuedPrompt(
        id: 'queued-1',
        profileID: 'laptop',
        sessionID: sessionID,
        text: 'Then run the cache tests on the phone.',
        createdAt: DateTime.now().millisecondsSinceEpoch,
      ),
  ];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  testWidgets('s6 transcript dark', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    SharedPreferencesStorePlatform.instance =
        InMemorySharedPreferencesStore.empty();
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    final now = DateTime.now().millisecondsSinceEpoch;
    final api = _Api()
      ..messagesHandler = (_) async => [
        MessageWithParts(
          info: messageInfo('msg_user', 'user', created: now - 20000),
          parts: [
            Part(
              id: 'part_prompt',
              messageID: 'msg_user',
              type: 'text',
              text: 'Put the cache behind an interface.',
            ),
          ],
        ),
        MessageWithParts(
          info: messageInfo('msg_reply', 'assistant', created: now - 15000),
          parts: [
            Part(
              id: 'part_words',
              messageID: 'msg_reply',
              type: 'text',
              text: _reply,
            ),
          ],
        ),
      ];
    final controller = _Controller(
      SeededProfileStore(
        prefs: prefs,
        seeded: [
          ServerProfile(
            id: 'laptop',
            name: 'Laptop',
            baseUrl: 'http://192.168.1.20:4096',
          ),
        ],
      ),
    );
    controller
      ..api = api
      ..repository = CaptureRepository()
      ..status = StreamStatus.connected
      ..directory = projectDirectory
      ..sessionsById = Map.of(api.sessionsById);
    addTearDown(controller.dispose);
    final key = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: const ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: key,
        controller: controller,
      ),
    );
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 120));
    }
    expect(tester.takeException(), isNull);
    await writePng(
      '$_dir/transcript-dark.png',
      await capturePng(tester, key, pixelRatio: 2),
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
  });
}
