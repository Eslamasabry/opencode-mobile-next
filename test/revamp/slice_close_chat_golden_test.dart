// Gallery of slice-close-chat, the chat lane's review-board closure: a
// prompt that got no answer (Not answered, "Use <model> and resend"), a
// finished turn that keeps its explanation in view, a queued message the
// server refused (Try again), the working composer (one trailing control,
// the editor in the field's corner), the leave-unsaved-draft sheet, the
// wrapping shell command and an ended worker session. Phone 412x915 and a
// wide window (1280x800), dark, with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_close_chat_golden_test.dart
// and look at every changed image before committing it.
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import 'chat_3_support.dart';
import '../support/composer_tools.dart';

/// Refuses the session-draft write, so leaving asks about the draft.
class _RefusingDrafts extends InMemorySharedPreferencesStore {
  _RefusingDrafts(super.data) : super.withData();

  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (key == 'flutter.oc.sessionDrafts') return false;
    return super.setValue(type, key, value);
  }

  @override
  Future<bool> remove(String key) async {
    if (key == 'flutter.oc.sessionDrafts') return false;
    return super.remove(key);
  }
}

/// A catalog that stays what the scene set.
class _CatalogConnection extends ConnectionController {
  _CatalogConnection(super.store);

  @override
  Future<void> refreshCatalog() async {}

  @override
  Future<void> ensureCatalog() async {}
}

MessageWithParts _step(String id, List<Part> parts, {String? error}) =>
    MessageWithParts(
      info: MessageInfo(
        id: id,
        sessionID: 'session-1',
        role: 'assistant',
        providerID: 'openai',
        modelID: 'gpt-5.6-sol',
        errorText: error,
        time: MsgTime(created: 2, completed: 3),
      ),
      parts: parts,
    );

Part _text(String id, String text) =>
    Part(id: id, messageID: id, type: 'text', text: text);

Part _tool(String id, String name, Map<String, Object?> input) => Part(
  id: id,
  callID: id,
  type: 'tool',
  toolName: name,
  toolState: ToolState.fromJson({
    'status': 'completed',
    'input': input,
    'output': 'done',
  }, toolName: name),
);

const _modelMissing =
    'ProviderModelNotFoundError: Model not found: openai/gpt-5.6-sol. '
    'Did you mean: gpt-5.6-sol-pro?';

const _explanation =
    'The flakiness comes from `CheckoutBloc`: it reads the balance before '
    'the save has finished, so one run in five sees the old value.\n\n'
    'The fix awaits the save before the read.';

Future<ConnectionController> _connection({
  required List<MessageWithParts> transcript,
  bool refuseDrafts = false,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Laptop',
        'flavor': 'v2',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  if (refuseDrafts) {
    SharedPreferencesStorePlatform.instance = _RefusingDrafts(
      await SharedPreferencesStorePlatform.instance.getAll(),
    );
    SharedPreferences.resetStatic();
  }
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final conn = _CatalogConnection(store)
    ..api = (Chat3Api()..transcript = transcript)
    ..status = StreamStatus.connected;
  conn.catalog = const CatalogSnapshot(
    providers: [],
    models: [
      CatalogModel(
        id: 'gpt-5.6-sol-pro',
        providerID: 'openai',
        name: 'GPT-5.6 Sol Pro',
        enabled: true,
        status: 'active',
        contextLimit: 400000,
        outputLimit: 64000,
        reasoning: true,
        attachments: true,
        tools: true,
        variants: [],
      ),
    ],
    agents: [],
  );
  return conn;
}

typedef _Scene = ({
  List<MessageWithParts> transcript,
  bool pushed,
  bool refuseDrafts,
  Future<void> Function(WidgetTester tester, ConnectionController conn) act,
});

Future<void> _none(WidgetTester tester, ConnectionController conn) async {}

Future<void> _type(WidgetTester tester, String text) async {
  await tester.enterText(find.byKey(const Key('chat-composer-field')), text);
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
}

final _prompt = chat3Prompt('u1', 'Why is the checkout test flaky?');

final Map<String, _Scene> _scenes = {
  'close_chat_no_answer': (
    transcript: [
      _prompt,
      _step('a1', const [], error: _modelMissing),
    ],
    pushed: false,
    refuseDrafts: false,
    act: _none,
  ),
  'close_chat_finished_turn': (
    transcript: [
      _prompt,
      _step('a1', [
        _text('a1-t', 'Looking into it.'),
        _tool('t1', 'read', {'filePath': '/app/lib/checkout_bloc.dart'}),
        _tool('t2', 'read', {'filePath': '/app/test/checkout_test.dart'}),
      ]),
      _step('a2', [
        _text('a2-t', _explanation),
        _tool('t3', 'edit', {
          'filePath': '/app/lib/checkout_bloc.dart',
          'oldString': 'a',
          'newString': 'b',
        }),
      ]),
      _step('a3', [
        _text('a3-t', 'Fixed. The test now passes 50 runs in a row.'),
      ]),
    ],
    pushed: false,
    refuseDrafts: false,
    act: _none,
  ),
  'close_chat_queued_refused': (
    transcript: [_prompt],
    pushed: false,
    refuseDrafts: false,
    act: (tester, conn) async {
      await conn.queuePrompt(
        QueuedPrompt(
          id: 'queued-1',
          profileID: 'profile-1',
          sessionID: 'session-1',
          text: 'Then open a pull request with the fix.',
          createdAt: 1,
          error: 'APIError: 529 {"type":"overloaded_error"}',
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
    },
  ),
  'close_chat_working_composer': (
    transcript: [_prompt],
    pushed: false,
    refuseDrafts: false,
    act: (tester, conn) async {
      conn.busySessions.add('session-1');
      conn.notifyListeners();
      await tester.pump();
      await _type(tester, 'Also check the refund flow while you are there.');
    },
  ),
  'close_chat_leave_sheet': (
    transcript: [_prompt],
    pushed: true,
    refuseDrafts: true,
    act: (tester, conn) async {
      await _type(tester, 'Keep this half-written thought');
      await tester.tap(find.byTooltip('Back'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
    },
  ),
  'close_chat_shell_dialog': (
    transcript: [_prompt],
    pushed: false,
    refuseDrafts: false,
    act: (tester, conn) async {
      await openComposerTools(tester);
      await tester.pump(const Duration(milliseconds: 600));
      await tester.tap(find.byKey(const Key('composer-tool-commands')));
      await tester.pump(const Duration(milliseconds: 600));
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        'shell',
      );
      await tester.pump();
      final row = find.byKey(const Key('command-mobile-shell'));
      await tester.ensureVisible(row);
      await tester.tap(row);
      await tester.pump(const Duration(milliseconds: 600));
      await tester.enterText(
        find.byKey(const ValueKey('run-shell-command')),
        'flutter test --concurrency=1 test/checkout_test.dart '
        '--plain-name "checkout keeps the balance"',
      );
      await tester.pump(const Duration(milliseconds: 600));
    },
  ),
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUp(chat3MockSecureStorage);

  for (final MapEntry(key: name, value: scene) in _scenes.entries) {
    for (final size in const [Size(412, 915), Size(1280, 800)]) {
      final sized = size.width == 412
          ? ''
          : '_${size.width.toInt()}x${size.height.toInt()}';
      final file = '$name${sized}_dark';
      testWidgets(file, (tester) async {
        debugPlatformCapabilities = const PlatformCapabilities.android();
        debugDefaultTargetPlatformOverride = TargetPlatform.android;
        tester.view.physicalSize = size;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final store = SharedPreferencesStorePlatform.instance;
        addTearDown(() {
          SharedPreferencesStorePlatform.instance = store;
          SharedPreferences.resetStatic();
        });
        final conn = await _connection(
          transcript: scene.transcript,
          refuseDrafts: scene.refuseDrafts,
        );
        addTearDown(conn.dispose);
        final boundary = GlobalKey();
        const chat = ChatScreen(sessionID: 'session-1');
        try {
          await tester.pumpWidget(
            RepaintBoundary(
              key: boundary,
              child: ProviderScope(
                overrides: [connProvider.overrideWithValue(conn)],
                child: MaterialApp(
                  debugShowCheckedModeBanner: false,
                  theme: captureTheme(light: false),
                  localizationsDelegates:
                      AppLocalizations.localizationsDelegates,
                  supportedLocales: AppLocalizations.supportedLocales,
                  builder: (context, child) => MediaQuery(
                    data: MediaQuery.of(
                      context,
                    ).copyWith(disableAnimations: true),
                    child: child!,
                  ),
                  home: scene.pushed
                      ? Builder(
                          builder: (context) => Scaffold(
                            body: Center(
                              child: TextButton(
                                onPressed: () => Navigator.of(context).push(
                                  MaterialPageRoute<void>(builder: (_) => chat),
                                ),
                                child: const Text('Open chat'),
                              ),
                            ),
                          ),
                        )
                      : chat,
                ),
              ),
            ),
          );
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 600));
          if (scene.pushed) {
            await tester.tap(find.text('Open chat'));
            await tester.pump();
            await tester.pump(const Duration(milliseconds: 600));
          }
          await scene.act(tester, conn);
          await tester.pump(const Duration(milliseconds: 600));
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile('goldens/$file.png'),
          );
        } finally {
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pump();
          debugPlatformCapabilities = null;
          debugDefaultTargetPlatformOverride = null;
        }
      });
    }
  }
}
