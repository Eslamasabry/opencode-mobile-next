// "Undo all" on the changes screen (vision slice, 2026-10-20): the chat wires
// ReviewWorkspace.onUndoAll to a staged undo from the conversation's first
// prompt, files included, gated on the server's revert capability.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';

class _Api extends CaptureApi {
  _Api({this.revert = true});
  final bool revert;

  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(sessionRevert: revert);

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

class _Repo extends CaptureRepository implements StagedRevertGateway {
  final stages = <String>[];

  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) async => [
    FileDiff.fromJson({
      'file': 'lib/main.dart',
      'patch': '@@ -1 +1 @@\n-old\n+new',
      'additions': 1,
      'deletions': 1,
      'status': 'modified',
    }),
  ];

  @override
  Future<String?> sessionRevertPrompt(String id, String messageID) async => '';

  @override
  Future<SessionRevert> stageSessionRevert(
    String sessionID,
    String messageID, {
    required bool applyFiles,
  }) async {
    stages.add('$sessionID:$messageID:$applyFiles');
    return SessionRevert(messageID: messageID, snapshot: 's', files: const []);
  }

  @override
  Future<void> clearSessionRevert(String sessionID) async {}

  @override
  Future<void> commitSessionRevert(String sessionID) async {}
}

Future<_Repo> _open(WidgetTester tester, {bool revert = true}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final now = DateTime.now().millisecondsSinceEpoch;
  final api = _Api(revert: revert)
    ..busy = {}
    ..messagesHandler = (_) async => [
      for (final (id, role, text) in [
        ('msg_first', 'user', 'Build the page.'),
        ('msg_a1', 'assistant', 'Done.'),
        ('msg_second', 'user', 'Now tweak it.'),
        ('msg_a2', 'assistant', 'Tweaked.'),
      ])
        MessageWithParts(
          info: messageInfo(
            id,
            role,
            created: now - 60000,
            completed: role == 'assistant' ? now - 1000 : now - 60000,
          ),
          parts: [Part(id: 'p_$id', messageID: id, type: 'text', text: text)],
        ),
    ];
  final repo = _Repo();
  final controller = CaptureController(
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
    ..repository = repo
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..sessionsById = Map.of(api.sessionsById)
    ..busySessions = <String>{};
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: checkoutSessionID),
      boundaryKey: GlobalKey(),
      controller: controller,
    ),
  );
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
  await tester.tap(find.byTooltip('Conversation menu'));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const ValueKey('session-menu-changes')));
  await tester.pumpAndSettle();
  return repo;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  testWidgets('Undo all stages an undo from the first prompt, files included', (
    tester,
  ) async {
    final repo = await _open(tester);
    expect(find.byKey(const Key('review-undo-all')), findsOneWidget);
    await tester.tap(find.byKey(const Key('review-undo-all')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-undo-confirm')), findsOneWidget);
    await tester.tap(
      find.descendant(
        of: find.byKey(const Key('review-undo-confirm')),
        matching: find.text('Undo all'),
      ),
    );
    await tester.pumpAndSettle();
    expect(repo.stages, ['$checkoutSessionID:msg_first:true']);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a server that cannot revert offers no Undo all', (tester) async {
    await _open(tester, revert: false);
    expect(find.byKey(const Key('review-undo-all')), findsNothing);
  });
}
