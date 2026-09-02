// Marketing capture pipeline: renders the real widgets with sample data
// through flutter_test and writes PNG screenshots plus the demo video frames.
//
//   flutter test tool/capture/capture_test.dart
//
// Screenshots land in video/public/shots/. The demo frames are written only
// when CAPTURE_FRAMES_DIR names a directory:
//
//   CAPTURE_FRAMES_DIR=/tmp/frames flutter test tool/capture/capture_test.dart
//   python3 tool/capture/feed_jpeg.py /tmp/frames \
//     | ffmpeg -f image2pipe -c:v mjpeg -framerate 30 -i pipe:0 \
//         -c:v libvpx -b:v 3M -pix_fmt yuv420p -vf scale=585:1266 \
//         video/public/demo.webm
//   python3 tool/capture/make_gif.py /tmp/frames video/public/demo.gif 390 2
//
// Lives under tool/ on purpose so `flutter test` on test/ never runs it.
// It is still a test binary, so the test-only seams it drives (mock
// preferences, the controller's event injection) are used deliberately.
// ignore_for_file: invalid_use_of_visible_for_testing_member

import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/widgets/diff_view.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'fixtures.dart';

const shotsDir = 'video/public/shots';

Future<SharedPreferences> _prefs() async {
  SharedPreferences.setMockInitialValues({});
  return SharedPreferences.getInstance();
}

/// Runs [body] on a 390x844 phone at 3x on the Android platform path.
///
/// The platform override is a foundation debug variable the binding checks
/// before tear-downs run, so it is reset here rather than in a tear-down.
Future<void> _onPhone(WidgetTester tester, Future<void> Function() body) async {
  tester.view.physicalSize = const Size(1170, 2532);
  tester.view.devicePixelRatio = captureDevicePixelRatio;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  try {
    await body();
  } finally {
    debugDefaultTargetPlatformOverride = null;
  }
}

/// Advances [seconds] of animation in 33 ms steps so entrance transitions
/// and tints reach a steady frame before a capture.
Future<void> _settle(WidgetTester tester, double seconds) async {
  final frames = (seconds * 30).round();
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 33));
  }
}

Future<void> _shot(WidgetTester tester, GlobalKey key, String name) async {
  final bytes = await capturePng(tester, key);
  await writePng('$shotsDir/$name', bytes);
}

Widget _chatWithReview(String sessionID) => ChatScreen(sessionID: sessionID);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    await loadCaptureFonts();
    ProviderLogo.imageProviderOverride = (_) => null;
    chatScreenFor = _chatWithReview;
    Directory(shotsDir).createSync(recursive: true);
  });
  tearDownAll(() => ProviderLogo.imageProviderOverride = null);

  testWidgets(
    '01 welcome',
    (tester) => _onPhone(tester, () async {
      final prefs = await _prefs();
      final store = ProfileStore(prefs: prefs);
      await store.load();
      final controller = CaptureController(store);
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const ServersScreen(),
          boundaryKey: key,
          controller: controller,
          store: store,
        ),
      );
      await _settle(tester, 2);
      expect(find.byKey(const ValueKey('first-run-welcome')), findsOneWidget);
      await _shot(tester, key, '01-welcome.png');
    }),
  );

  testWidgets(
    '02 workspace',
    (tester) => _onPhone(tester, () async {
      final controller = await captureController(prefs: await _prefs());
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const HomeScreen(),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _settle(tester, 2);
      await _shot(tester, key, '02-workspace.png');
      expect(
        find.byKey(const ValueKey('current-project-entry')),
        findsOneWidget,
      );
      expect(find.text('Fix flaky checkout test'), findsOneWidget);
      expect(find.byKey(const ValueKey('workspace-quick-ask')), findsOneWidget);
    }),
  );

  Future<void> chatShot(
    WidgetTester tester, {
    required String name,
    bool light = false,
    bool awaitingPermission = false,
  }) => _onPhone(tester, () async {
    final api = CaptureApi()..diffs = sampleDiffs();
    api.messagesHandler = (_) async => sampleTranscript(
      streaming: !awaitingPermission,
      awaitingPermission: awaitingPermission,
    );
    final controller = await captureController(prefs: await _prefs(), api: api);
    if (awaitingPermission) {
      controller.permissions = {samplePermission().id: samplePermission()};
    }
    addTearDown(controller.dispose);
    final key = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: const ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: key,
        controller: controller,
        light: light,
      ),
    );
    await _settle(tester, 2);
    if (awaitingPermission) {
      expect(
        find.byKey(const ValueKey('permission-card-perm_checkout')),
        findsOneWidget,
      );
      // The running group is open; open the Shell card inside it too.
      await tester.tap(find.text('Shell').first);
      await _settle(tester, 1);
    }
    await _shot(tester, key, name);
    if (!awaitingPermission) {
      expect(find.byKey(const ValueKey('composer-activity')), findsOneWidget);
      expect(
        find.text('Read 3 files, edited 1, ran 1 command'),
        findsOneWidget,
      );
    }
  });

  testWidgets('03 chat streaming', (tester) async {
    await chatShot(tester, name: '03-chat-streaming.png');
  });

  testWidgets('04 permission card', (tester) async {
    await chatShot(
      tester,
      name: '04-permission-card.png',
      awaitingPermission: true,
    );
  });

  testWidgets(
    '05 diff',
    (tester) => _onPhone(tester, () async {
      final controller = await captureController(prefs: await _prefs());
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: DiffView(diffs: sampleDiffs(), title: 'Changes · 2 files'),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _settle(tester, 1);
      await _shot(tester, key, '05-diff.png');
      expect(find.byKey(const Key('diff-view')), findsOneWidget);
      expect(find.textContaining('checkout_test.dart'), findsWidgets);
    }),
  );

  testWidgets(
    '06 providers',
    (tester) => _onPhone(tester, () async {
      final repository = CaptureRepository()
        ..integrations = sampleIntegrations();
      final controller = await captureController(
        prefs: await _prefs(),
        repository: repository,
      );
      controller.catalog = sampleCatalog();
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: IntegrationsScreen(
            controller: controller,
            mode: IntegrationsMode.providers,
          ),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _settle(tester, 2);
      expect(find.text('Anthropic'), findsOneWidget);
      expect(find.text('Ollama'), findsOneWidget);
      await _shot(tester, key, '06-providers.png');
    }),
  );

  testWidgets(
    '07 activity inbox',
    (tester) => _onPhone(tester, () async {
      final controller = await captureController(prefs: await _prefs());
      controller
        ..permissions = {samplePermission().id: samplePermission()}
        ..questions = {sampleQuestion().id: sampleQuestion()};
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const HomeScreen(),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _settle(tester, 1);
      await tester.tap(find.byIcon(Icons.notifications_outlined));
      await _settle(tester, 2);
      expect(find.text('Run a shell command'), findsOneWidget);
      expect(find.text('Theme source'), findsOneWidget);
      await _shot(tester, key, '07-activity-inbox.png');
    }),
  );

  testWidgets('08 light chat', (tester) async {
    await chatShot(tester, name: '08-light-chat.png', light: true);
  });

  testWidgets(
    'demo video frames',
    (tester) async {
      final framesDir = Platform.environment['CAPTURE_FRAMES_DIR']!;
      await _captureDemo(tester, framesDir);
    },
    skip: Platform.environment['CAPTURE_FRAMES_DIR'] == null,
    timeout: const Timeout(Duration(minutes: 20)),
  );
}

// ---------------------------------------------------------------------------
// The scripted demo
// ---------------------------------------------------------------------------

const _fps = 30;
const _framePixelRatio = 1.5; // 585x1266, the size the webm ships at.

Future<void> _captureDemo(WidgetTester tester, String framesDir) =>
    _onPhone(tester, () => _demoBody(tester, framesDir));

Future<void> _demoBody(WidgetTester tester, String framesDir) async {
  final dir = Directory(framesDir);
  if (dir.existsSync()) dir.deleteSync(recursive: true);
  dir.createSync(recursive: true);

  final boundary = GlobalKey();
  final navigator = GlobalKey<NavigatorState>();
  final caption = ValueNotifier<String>('');
  addTearDown(caption.dispose);
  var frame = 0;

  Future<void> snap() async {
    final bytes = await capturePng(
      tester,
      boundary,
      pixelRatio: _framePixelRatio,
    );
    await writePng('$framesDir/${frame.toString().padLeft(4, '0')}.png', bytes);
    frame += 1;
  }

  /// Pumps [seconds] of frames, calling [onFrame] before each pump.
  Future<void> run(
    double seconds, {
    Future<void> Function(int index, int total)? onFrame,
  }) async {
    final total = (seconds * _fps).round();
    for (var i = 0; i < total; i++) {
      await onFrame?.call(i, total);
      await tester.pump(const Duration(milliseconds: 33));
      await snap();
    }
  }

  // --- 1. Welcome ---------------------------------------------------------
  final prefs = await _prefs();
  final emptyStore = ProfileStore(prefs: prefs);
  await emptyStore.load();
  final welcomeController = CaptureController(emptyStore);
  addTearDown(welcomeController.dispose);
  caption.value = 'Pair in one command';
  await tester.pumpWidget(
    captureApp(
      home: const ServersScreen(),
      boundaryKey: boundary,
      controller: welcomeController,
      store: emptyStore,
      caption: caption,
    ),
  );
  await run(1.5);
  // Press the Connect card (ripple), then cut to the connected workspace.
  final press = await tester.startGesture(
    tester.getCenter(find.byKey(const ValueKey('welcome-connect-card'))),
  );
  await run(0.35);
  await press.cancel();

  // --- 2. Workspace -------------------------------------------------------
  // The checkout session is created live by the quick-ask, so the workspace
  // shows a different busy session and three recent ones.
  final now = DateTime.now().millisecondsSinceEpoch;
  final api = CaptureApi()..diffs = sampleDiffs();
  api.sessionsById.remove(checkoutSessionID);
  api.sessionsById['ses_orders'] = Session(
    id: 'ses_orders',
    title: 'Migrate orders to Postgres',
    directory: projectDirectory,
    time: SessionTime(created: now - 30 * 60000, updated: now - 60000),
    cost: 0.88,
    summary: const SessionDiffSummary(additions: 340, deletions: 96, files: 11),
  );
  api.busy = {'ses_orders'};
  final controller = await captureController(prefs: prefs, api: api);
  addTearDown(controller.dispose);
  api.messagesHandler = (_) async => [];
  // Quick-ask creates the checkout session; keep its id stable so the event
  // fixtures below address it.
  api.sessionsById[checkoutSessionID] = Session(
    id: checkoutSessionID,
    directory: projectDirectory,
    time: SessionTime(created: now, updated: now),
  );
  caption.value = 'Your sessions, with what they cost';
  await tester.pumpWidget(
    captureApp(
      home: const HomeScreen(),
      boundaryKey: boundary,
      controller: controller,
      caption: caption,
      navigatorKey: navigator,
    ),
  );
  await run(2);

  // --- 3. Quick ask → empty chat -----------------------------------------
  // Route through the same path the pill takes, with the fixed session id.
  navigator.currentState!.pushNamed('/chat/$checkoutSessionID');
  caption.value = 'Start from a suggestion, or just ask';
  await run(1.9);
  expect(find.byKey(const Key('chat-composer-field')), findsOneWidget);

  // --- 4. Type the prompt -------------------------------------------------
  caption.value = 'Type it, say it, or share it in';
  final field = tester.widget<TextField>(
    find.byKey(const Key('chat-composer-field')),
  );
  final composer = field.controller!;
  await run(
    2,
    onFrame: (i, total) async {
      final chars = ((i + 1) / total * userPrompt.length).ceil().clamp(
        0,
        userPrompt.length,
      );
      composer.text = userPrompt.substring(0, chars);
      composer.selection = TextSelection.collapsed(offset: chars);
    },
  );

  // --- 5. Send, then stream the answer -----------------------------------
  await tester.tap(find.byKey(const Key('chat-send-button')));
  await run(0.3);
  final events = _EventScript(controller, api);
  events.userMessage();
  events.sessionBusy(title: 'Fix flaky checkout test');
  events.assistantStarted();
  caption.value = 'Answers stream in as they are written';
  await run(0.3);
  // Intro paragraph, a few characters per frame.
  await run(
    1.4,
    onFrame: (i, total) async =>
        events.streamText('part_intro', answerIntro, (i + 1) / total),
  );
  // Tools, one after another, with the ticker naming the running one.
  caption.value = 'Watch every step as it happens';
  final tools = toolParts(shellStatus: 'running');
  for (var index = 0; index < tools.length; index++) {
    final part = tools[index];
    final isShell = part.toolName == 'bash';
    events.toolPart(part, status: 'running');
    await run(isShell ? 0.4 : 0.35);
    if (!isShell) {
      events.toolPart(part, status: 'completed');
      await run(0.15);
    }
  }

  // --- 6. Permission card -------------------------------------------------
  controller.permissions = {samplePermission().id: samplePermission()};
  controller.notifyListeners();
  caption.value = 'Approve without leaving the keyboard';
  await run(2);
  await tester.tap(find.byKey(const Key('permission-card-allow-once')));
  await run(0.4);

  // --- 7. Tool completes, answer finishes, choices appear ----------------
  events.toolPart(shellPart(status: 'completed'), status: 'completed');
  caption.value = 'Choices come as buttons';
  await run(0.4);
  await run(
    1.2,
    onFrame: (i, total) async =>
        events.streamText('part_outro', answerOutro, (i + 1) / total),
  );
  events.assistantFinished();
  events.sessionIdle();
  await run(1.6);

  // --- 8. Tap a choice → composer fills ----------------------------------
  await tester.ensureVisible(find.text('Run the full test suite'));
  await tester.tap(find.text('Run the full test suite'));
  caption.value = 'One tap to answer';
  await run(1.5);

  // --- 9. Diff full-screen -----------------------------------------------
  caption.value = 'Diffs that fit a phone';
  navigator.currentState!.push(
    MaterialPageRoute<void>(
      builder: (_) =>
          DiffView(diffs: sampleDiffs(), title: 'Changes · 2 files'),
    ),
  );
  await run(3);
  navigator.currentState!.pop();
  await run(0.8);

  // --- 10. End card -------------------------------------------------------
  final icon = await decodeImageFile(
    tester,
    'assets/branding/app-icon-256.png',
  );
  await tester.pumpWidget(
    RepaintBoundary(
      key: boundary,
      child: Directionality(
        textDirection: TextDirection.ltr,
        child: _EndCard(icon: icon),
      ),
    ),
  );
  await run(2);
  icon.dispose();
  await tester.pumpWidget(const SizedBox());
}

/// Emits the server events that make the chat screen show the scripted turn.
class _EventScript {
  _EventScript(this.controller, this.api);

  final ConnectionController controller;
  final CaptureApi api;
  final int startedAt = DateTime.now().millisecondsSinceEpoch;
  final Map<String, int> _streamed = {};

  void _emit(String type, Map<String, dynamic> properties) =>
      controller.handleEventForTesting(captureEvent(type, properties));

  void userMessage() {
    _emit('message.part.updated', {
      'sessionID': checkoutSessionID,
      'part': partJson(
        id: 'part_user',
        messageID: 'msg_user',
        type: 'text',
        text: userPrompt,
      ),
    });
    _emit('message.updated', {
      'info': {
        'id': 'msg_user',
        'sessionID': checkoutSessionID,
        'role': 'user',
        'time': {'created': startedAt, 'completed': startedAt},
      },
    });
  }

  void sessionBusy({required String title}) {
    // The idle edge refreshes sessions from the API, so the title must
    // survive there too.
    api.sessionsById[checkoutSessionID] = Session(
      id: checkoutSessionID,
      title: title,
      directory: projectDirectory,
      time: SessionTime(created: startedAt, updated: startedAt),
    );
    _emit('session.updated', {
      'info': {
        'id': checkoutSessionID,
        'title': title,
        'directory': projectDirectory,
        'time': {'created': startedAt, 'updated': startedAt},
      },
    });
    _emit('session.status', {
      'sessionID': checkoutSessionID,
      'status': {'type': 'busy'},
    });
  }

  void sessionIdle() {
    _emit('session.status', {
      'sessionID': checkoutSessionID,
      'status': {'type': 'idle'},
    });
  }

  void assistantStarted() {
    _emit('message.updated', {
      'info': {
        'id': 'msg_assistant',
        'sessionID': checkoutSessionID,
        'role': 'assistant',
        'providerID': 'anthropic',
        'modelID': 'claude-sonnet-4',
        'time': {'created': startedAt + 400},
      },
    });
  }

  void assistantFinished() {
    _emit('message.updated', {
      'info': {
        'id': 'msg_assistant',
        'sessionID': checkoutSessionID,
        'role': 'assistant',
        'providerID': 'anthropic',
        'modelID': 'claude-sonnet-4',
        'cost': 0.07,
        'tokens': {'input': 6420, 'output': 812},
        'time': {
          'created': startedAt + 400,
          'completed': DateTime.now().millisecondsSinceEpoch,
        },
      },
    });
  }

  /// Streams [text] into [partID] so that [fraction] of it is visible.
  void streamText(String partID, String text, double fraction) {
    final target = (text.length * fraction).round().clamp(0, text.length);
    final sent = _streamed[partID];
    if (sent == null) {
      _emit('message.part.updated', {
        'sessionID': checkoutSessionID,
        'part': partJson(
          id: partID,
          messageID: 'msg_assistant',
          type: 'text',
          text: text.substring(0, target),
        ),
      });
    } else if (target > sent) {
      _emit('message.part.delta', {
        'sessionID': checkoutSessionID,
        'messageID': 'msg_assistant',
        'partID': partID,
        'field': 'text',
        'delta': text.substring(sent, target),
      });
    }
    _streamed[partID] = target;
  }

  void toolPart(Part part, {required String status}) {
    final state = ToolState(
      status: status,
      input: part.toolState.input,
      output: status == 'completed' ? part.toolState.output : null,
      metadata: part.toolState.metadata,
      title: part.toolState.title,
    );
    _emit('message.part.updated', {
      'sessionID': checkoutSessionID,
      'part': partJson(
        id: part.id!,
        messageID: 'msg_assistant',
        type: 'tool',
        tool: part.toolName,
        state: toolStateJson(state),
      ),
    });
  }
}

/// The closing frame: app icon and tagline on the dark background.
class _EndCard extends StatelessWidget {
  const _EndCard({required this.icon});

  final ui.Image icon;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: AppTheme.background,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(28),
            child: RawImage(image: icon, width: 128, height: 128),
          ),
          const SizedBox(height: 28),
          const Text(
            'OpenCode Mobile',
            style: TextStyle(
              fontFamily: AppTheme.displayFamily,
              fontSize: 30,
              fontWeight: FontWeight.w700,
              color: Colors.white,
              letterSpacing: -.4,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            'your coding agent, in your pocket',
            style: TextStyle(
              fontFamily: AppTheme.displayFamily,
              fontSize: 17,
              fontWeight: FontWeight.w500,
              color: Colors.white.withValues(alpha: .72),
            ),
          ),
        ],
      ),
    );
  }
}
