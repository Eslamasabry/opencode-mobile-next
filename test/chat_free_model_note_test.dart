// The chat's free-model note (coordinator's request on
// slice-fix-inbox-status): a conversation whose replies come from
// OpenCode's free model because no provider is signed in says so in one
// quiet line in the page's status slot, with "Add your key". It is
// never a blocking banner, it can be dismissed once per conversation, and
// showing or dismissing it never moves the reply (F16's concern too).
//
// Regenerate deliberately, and look at every changed image before committing it:
//   flutter test --update-goldens --dart-define=CAPTURE_EVIDENCE=true \
//     test/chat_free_model_note_test.dart
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/free_model_notice.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart'
    show IntegrationsScreen;
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import 'support/complete_message_history.dart';

const _evidence = bool.fromEnvironment('CAPTURE_EVIDENCE');
final _en = lookupAppLocalizations(const Locale('en'));
final _note = find.byKey(const ValueKey('chat-status-free-model'));

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  final _laptop = ServerProfile(
    id: 'laptop',
    name: 'Laptop',
    baseUrl: 'http://192.168.1.20:4096',
  );

  @override
  ServerProfile? get profile => _laptop;
}

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<MessageWithParts>> messages(String id) async => [
    MessageWithParts(
      info: MessageInfo(
        id: 'm1',
        sessionID: id,
        role: 'user',
        time: MsgTime(created: 1, completed: 1),
      ),
      parts: [Part(id: 'p1', messageID: 'm1', type: 'text', text: 'Hello')],
    ),
    MessageWithParts(
      info: MessageInfo(
        id: 'm2',
        sessionID: id,
        role: 'assistant',
        parentID: 'm1',
        finish: 'stop',
        time: MsgTime(created: 2, completed: 3),
      ),
      parts: [Part(id: 'p2', messageID: 'm2', type: 'text', text: 'Hi there.')],
    ),
  ];
  @override
  Future<Session> session(String id) async => Session(id: id);
  @override
  Future<List<Session>> sessions() async => [Session(id: 's1')];
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<List<Todo>> todos(String id) async => const [];
  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];
}

CatalogModel _free(String id) => CatalogModel(
  id: id,
  providerID: 'opencode',
  name: id,
  enabled: true,
  status: 'active',
  contextLimit: 200000,
  outputLimit: 32000,
  reasoning: false,
  attachments: false,
  tools: true,
  variants: const [],
  cost: const ModelCost(inputPerMillion: 0, outputPerMillion: 0),
);

Future<_Controller> _controller({List<String> signedIn = const []}) async {
  SharedPreferences.setMockInitialValues({});
  final c = _Controller(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
  );
  c
    ..api = _Api()
    ..repository = _Repository()
    ..status = StreamStatus.connected
    ..providers = ProvidersResponse(
      providers: [
        for (final id in signedIn)
          ProviderInfo(
            id: id,
            name: id,
            modelIDs: const [],
            modelData: const {},
          ),
      ],
    )
    ..catalog = CatalogSnapshot(
      providers: const [],
      models: [_free('big-pickle')],
      agents: const [],
    )
    ..selectedModel = ModelRef(providerID: 'opencode', modelID: 'big-pickle');
  c.sessionsById['s1'] = Session(id: 's1');
  c.sessionsById['s2'] = Session(id: 's2');
  addTearDown(c.dispose);
  return c;
}

Widget _app(_Controller c, String sessionID, {ThemeData? theme}) =>
    ProviderScope(
      overrides: [connProvider.overrideWithValue(c)],
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: theme ?? AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ChatScreen(key: ValueKey(sessionID), sessionID: sessionID),
      ),
    );

Future<void> _open(WidgetTester tester, _Controller c, String id) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pumpWidget(_app(c, id));
  await tester.pumpAndSettle();
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

  void phone(WidgetTester tester) {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }

  testWidgets('free model and nobody signed in: one quiet line with the way '
      'out; dismissing it never moves the reply and keeps it gone for this '
      'conversation only', (tester) async {
    phone(tester);
    final c = await _controller();
    await _open(tester, c, 's1');

    expect(_note, findsOneWidget);
    expect(find.text(_en.chatFreeModelNote), findsOneWidget);
    expect(find.text(_en.chatFreeModelAddKey), findsOneWidget);
    // A line in the status slot, not a banner over the conversation.
    expect(tester.widget(_note), isA<KitStatusLine>());

    final reply = find.text('Hi there.', findRichText: true);
    expect(reply, findsOneWidget);
    final before = tester.getTopLeft(reply);

    await tester.tap(find.byKey(const ValueKey('kit-status-dismiss')).first);
    await tester.pumpAndSettle();
    expect(_note, findsNothing);
    // The reply stays where it was.
    expect(tester.getTopLeft(reply), before);
    expect(
      FreeModelNoteDismissals.dismissed(c.store.prefs, 'laptop', 's1'),
      isTrue,
    );

    // Opened again: still dismissed. Another conversation: said once there.
    await _open(tester, c, 's1');
    expect(_note, findsNothing);
    await _open(tester, c, 's2');
    expect(_note, findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Sign in to a provider opens the provider sign-ins', (
    tester,
  ) async {
    phone(tester);
    final c = await _controller();
    await _open(tester, c, 's1');
    await tester.tap(find.byKey(const ValueKey('chat-free-model-sign-in')));
    await tester.pumpAndSettle();
    expect(find.byType(IntegrationsScreen), findsOneWidget);
  });

  testWidgets('a signed-in provider means the free model was a choice: no '
      'note', (tester) async {
    phone(tester);
    final c = await _controller(signedIn: const ['anthropic']);
    await _open(tester, c, 's1');
    expect(_note, findsNothing);
    expect(tester.takeException(), isNull);
  });

  // Evidence only (docs/qa/slice-fix-inbox-status-2026-09-28):
  //   flutter test --update-goldens --dart-define=CAPTURE_EVIDENCE=true \
  //     test/chat_free_model_note_test.dart
  for (final (size, light) in const [
    (Size(412, 915), false),
    (Size(1280, 800), true),
  ]) {
    testWidgets('evidence · ${size.width.toInt()}', skip: !_evidence, (
      tester,
    ) async {
      await loadCaptureFonts();
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final c = await _controller();
      debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
      try {
        await tester.pumpWidget(
          _app(c, 's1', theme: captureTheme(light: light)),
        );
        await tester.pumpAndSettle();
        await expectLater(
          find.byType(MaterialApp),
          matchesGoldenFile(
            'goldens/evidence/chat_free_model_note_'
            '${size.width.toInt()}_${light ? 'light' : 'dark'}.png',
          ),
        );
        await tester.pumpWidget(const SizedBox.shrink());
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
    });
  }

  test(
    'dismissals are per server, bounded, and swept with the server',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      for (var i = 0; i < FreeModelNoteDismissals.maxSessions + 5; i++) {
        await FreeModelNoteDismissals.dismiss(prefs, 'laptop', 's$i');
      }
      expect(
        prefs.getStringList(FreeModelNoteDismissals.keyFor('laptop')),
        hasLength(FreeModelNoteDismissals.maxSessions),
      );
      expect(FreeModelNoteDismissals.dismissed(prefs, 'laptop', 's0'), isFalse);
      expect(
        FreeModelNoteDismissals.dismissed(
          prefs,
          'laptop',
          's${FreeModelNoteDismissals.maxSessions + 4}',
        ),
        isTrue,
      );
      expect(
        FreeModelNoteDismissals.dismissed(prefs, 'studio', 's204'),
        isFalse,
      );
      final store = ProfileStore(prefs: prefs);
      expect(
        store.profileScopedPreferenceKeys('laptop'),
        contains(FreeModelNoteDismissals.keyFor('laptop')),
      );
    },
  );
}
