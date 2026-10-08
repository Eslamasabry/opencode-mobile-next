// F16 (docs/qa/emulator-qa-claude-2026-09-28): "Notify you when the agent
// needs you?" is asked once, after the first reply of a new person's first
// conversation. It lives in the chat page's status slot (over the top of the
// transcript, below every real status line), so the reply's actions at the
// bottom never move when it appears or goes. "Notify me" is the action; the
// line's close is "Not now". Either answer is remembered.
//
// Evidence renders only (CAPTURE_EVIDENCE). Regenerate deliberately:
//   flutter test --update-goldens --dart-define=CAPTURE_EVIDENCE=true \
//     [--dart-define=EVIDENCE_TAG=before] test/chat_notify_offer_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/first_run.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import 'support/complete_message_history.dart';

const _evidence = bool.fromEnvironment('CAPTURE_EVIDENCE');
const _evidenceTag = String.fromEnvironment(
  'EVIDENCE_TAG',
  defaultValue: 'after',
);
final _en = lookupAppLocalizations(const Locale('en'));
final _question = find.text(_en.firstRunNotifyTitle);

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store, {super.backgroundLive});

  final _laptop = ServerProfile(
    id: 'laptop',
    name: 'Laptop',
    baseUrl: 'http://192.168.1.20:4096',
  );

  @override
  ServerProfile? get profile => _laptop;
}

class _Api extends OpenCodeApi with CompleteMessageHistory {
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
      parts: [
        Part(id: 'p1', messageID: 'm1', type: 'text', text: 'Explain this'),
      ],
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
      parts: [
        Part(
          id: 'p2',
          messageID: 'm2',
          type: 'text',
          text: 'It is a phone client.',
        ),
      ],
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

/// The native side of "Stay connected in the background".
class _Native {
  final calls = <String>[];
  bool deny = false;

  Future<Map<String, dynamic>> call(
    String method, [
    Map<String, dynamic>? arguments,
  ]) async {
    if (method == 'getBackgroundPause') {
      return const {
        'supported': true,
        'active': false,
        'paused': false,
        'reason': 'none',
        'at': null,
        'canResume': false,
      };
    }
    calls.add(method);
    if (method == 'enable' && deny) {
      throw PlatformException(code: 'notification_denied');
    }
    return {
      'enabled': method == 'enable',
      'active': method == 'enable',
      'notificationGranted': method == 'enable',
    };
  }
}

Future<(_Controller, _Native)> _controller({bool pending = false}) async {
  SharedPreferences.setMockInitialValues({
    FirstRun.stateKey: 'done',
    if (pending) FirstRun.notifyAskKey: 'pending',
  });
  final prefs = await SharedPreferences.getInstance();
  final native = _Native();
  final c = _Controller(
    ProfileStore(prefs: prefs),
    backgroundLive: BackgroundLiveController(
      preferences: prefs,
      invoke: native.call,
    ),
  );
  c
    ..api = _Api()
    ..repository = _Repository()
    ..status = StreamStatus.connected
    // A provider is signed in, so the free-model note has nothing to say.
    ..providers = ProvidersResponse(
      providers: [
        ProviderInfo(
          id: 'anthropic',
          name: 'Anthropic',
          modelIDs: const [],
          modelData: const {},
        ),
      ],
    );
  c.sessionsById['s1'] = Session(id: 's1');
  addTearDown(c.dispose);
  return (c, native);
}

Widget _app(_Controller c, {ThemeData? theme}) => ProviderScope(
  overrides: [connProvider.overrideWithValue(c)],
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: theme ?? AppTheme.dark(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: const ChatScreen(sessionID: 's1'),
  ),
);

/// The question falls due once the first reply is in: the one-time ask
/// turns pending while the conversation is on screen.
Future<void> _becomeDue(WidgetTester tester, _Controller c) async {
  await c.store.prefs.setString(FirstRun.notifyAskKey, 'pending');
  c.notifyListeners();
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() => debugPlatformCapabilities = null);

  void phone(WidgetTester tester) {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }

  testWidgets('appearing and going never moves the reply or its actions', (
    tester,
  ) async {
    phone(tester);
    final (c, native) = await _controller();
    await tester.pumpWidget(_app(c));
    await tester.pumpAndSettle();
    expect(_question, findsNothing);

    final reply = find.text('It is a phone client.', findRichText: true);
    final composer = find.byType(TextField).last;
    final replyAt = tester.getTopLeft(reply);
    final composerAt = tester.getTopLeft(composer);

    await _becomeDue(tester, c);
    expect(_question, findsOneWidget);
    expect(tester.getTopLeft(reply), replyAt);
    expect(tester.getTopLeft(composer), composerAt);
    // A line in the page's status slot, over the top of the transcript.
    final line = find.byKey(const ValueKey('chat-status-notify-offer'));
    expect(line, findsOneWidget);
    expect(tester.getRect(line).bottom, lessThan(replyAt.dy));
    // Showing the question asks Android for nothing.
    expect(native.calls, isEmpty);

    await tester.tap(find.byTooltip(_en.firstRunNotifyDecline));
    await tester.pumpAndSettle();
    expect(_question, findsNothing);
    expect(tester.getTopLeft(reply), replyAt);
    expect(tester.getTopLeft(composer), composerAt);
    expect(tester.takeException(), isNull);
  });

  testWidgets('"Not now" is remembered: not asked again, nothing turned on', (
    tester,
  ) async {
    phone(tester);
    final (c, native) = await _controller(pending: true);
    await tester.pumpWidget(_app(c));
    await tester.pumpAndSettle();
    expect(_question, findsOneWidget);

    await tester.tap(find.byTooltip(_en.firstRunNotifyDecline));
    await tester.pumpAndSettle();
    expect(_question, findsNothing);
    expect(native.calls, isEmpty);
    expect(c.keepLiveInBackground, isFalse);
    expect(FirstRun(c.store.prefs).notifyAskPending, isFalse);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(_app(c));
    await tester.pumpAndSettle();
    expect(_question, findsNothing);
  });

  testWidgets('"Notify me" turns on requests and the background connection, '
      'and is remembered', (tester) async {
    phone(tester);
    final (c, native) = await _controller(pending: true);
    await c.setNotifyFinishedRuns(false);
    await tester.pumpWidget(_app(c));
    await tester.pumpAndSettle();

    await tester.tap(find.text(_en.firstRunNotifyAccept));
    await tester.pumpAndSettle();
    expect(native.calls.where((call) => call == 'enable'), hasLength(1));
    expect(c.keepLiveInBackground, isTrue);
    expect(c.notificationPreferences.requests, isTrue);
    expect(c.notificationPreferences.finishedRuns, isFalse);
    expect(_question, findsNothing);
    expect(FirstRun(c.store.prefs).notifyAskPending, isFalse);
  });

  testWidgets('a refusal at Android\'s prompt says so in plain words in the '
      'same slot, and is an answer too', (tester) async {
    phone(tester);
    final (c, native) = await _controller(pending: true);
    native.deny = true;
    await tester.pumpWidget(_app(c));
    await tester.pumpAndSettle();

    await tester.tap(find.text(_en.firstRunNotifyAccept));
    await tester.pumpAndSettle();
    expect(_question, findsNothing);
    final failed = find.byKey(const ValueKey('chat-status-notify-failed'));
    expect(failed, findsOneWidget);
    // No raw platform code as copy.
    expect(find.textContaining('notification_denied'), findsNothing);
    expect(FirstRun(c.store.prefs).notifyAskPending, isFalse);

    await tester.tap(find.byKey(const ValueKey('kit-status-dismiss')).first);
    await tester.pumpAndSettle();
    expect(failed, findsNothing);
  });

  testWidgets('a reply still streaming does not ask', (tester) async {
    phone(tester);
    final (c, _) = await _controller(pending: true);
    c.busySessions.add('s1');
    await tester.pumpWidget(_app(c));
    await tester.pump(const Duration(seconds: 1));
    expect(_question, findsNothing);
  });

  // Evidence only (docs/qa/slice-fix-stop-offer-2026-09-29):
  //   flutter test --update-goldens --dart-define=CAPTURE_EVIDENCE=true \
  //     [--dart-define=EVIDENCE_TAG=before] test/chat_notify_offer_test.dart
  for (final (size, light) in const [
    (Size(412, 915), false),
    (Size(1280, 800), true),
  ]) {
    testWidgets('evidence · ${size.width.toInt()}', skip: !_evidence, (
      tester,
    ) async {
      await loadCaptureFonts();
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final (c, _) = await _controller(pending: true);
      await tester.pumpWidget(_app(c, theme: captureTheme(light: light)));
      await tester.pumpAndSettle();
      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile(
          'goldens/evidence/chat_notify_offer_${_evidenceTag}_'
          '${size.width.toInt()}_${light ? 'light' : 'dark'}.png',
        ),
      );
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
