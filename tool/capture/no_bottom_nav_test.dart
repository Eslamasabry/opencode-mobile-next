// Captures for the no-bottom-bar shell (2026-10-20,
// docs/qa/no-bottom-nav-2026-10-20/README.md): the real HomeScreen and
// ChatScreen at phone and PC sizes, real fonts, no server.
//
//   flutter test --concurrency=1 tool/capture/no_bottom_nav_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/no-bottom-nav/<phase>-<name>.png
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:clock/clock.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart'
    show chatsHostProvider;
import 'package:opencode_mobile/ui/screens/home_screen.dart';

import '../../test/support/setup_capture_preferences.dart';
import '../../test/support/chats_fakes.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/search/go_to_commands.dart';
import 'fixtures.dart';

const _phase = String.fromEnvironment('NAV_PHASE', defaultValue: 'after');
const _dir = '/home/eslam/Storage2/launch-footage/no-bottom-nav';

void _mockSecureStorage(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      null,
    ),
  );
}

/// A phone frame: the status bar and the gesture area are real insets.
void _phone(WidgetTester tester, Size size, {double ratio = 1}) {
  tester.view.physicalSize = size * ratio;
  tester.view.devicePixelRatio = ratio;
  tester.view.padding = FakeViewPadding(top: 32 * ratio, bottom: 24 * ratio);
  addTearDown(tester.view.reset);
}

/// The shell's search button needs the shortcut bus the app installs.
Widget _withBus(Widget child) => AppShortcutScope(
  signals: AppShortcutSignals(),
  perform: (_) {},
  child: child,
);

Future<void> _settle(WidgetTester tester, {int frames = 8}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Part _tool(
  String id,
  String name,
  Map<String, dynamic> input,
  Map<String, dynamic> metadata,
) => Part(
  id: id,
  messageID: 'msg_reply',
  callID: id,
  type: 'tool',
  toolName: name,
  toolState: ToolState.fromJson({
    'status': 'completed',
    'title': name,
    'input': input,
    'metadata': metadata,
    'output': '',
  }, toolName: name),
);

List<MessageWithParts> _turnWithFiles(int now) => [
  MessageWithParts(
    info: messageInfo('msg_user', 'user', created: now - 60000),
    parts: [
      Part(
        id: 'part_prompt',
        messageID: 'msg_user',
        type: 'text',
        text: 'Add a combo counter and screen shake.',
      ),
    ],
  ),
  MessageWithParts(
    info: messageInfo(
      'msg_reply',
      'assistant',
      created: now - 50000,
      completed: now - 20000,
    ),
    parts: [
      _tool(
        'edit_game',
        'edit',
        {'filePath': '$projectDirectory/src/game.js'},
        {
          'filediff': {'additions': 30, 'deletions': 5},
        },
      ),
      _tool(
        'edit_style',
        'edit',
        {'filePath': '$projectDirectory/src/style.css'},
        {
          'filediff': {'additions': 4, 'deletions': 0},
        },
      ),
      Part(
        id: 'part_words',
        messageID: 'msg_reply',
        type: 'text',
        text: 'Added a combo counter and screen shake.',
      ),
    ],
  ),
];

Future<CaptureController> _controller({bool busy = true}) async {
  final prefs = await setupCapturePreferences();
  final api = CaptureApi();
  if (!busy) api.busy = {};
  final controller = await captureController(prefs: prefs, api: api);
  return controller;
}

/// Names connections the way the app does (the fake says "OpenCode").
class _NamedHost extends FakeChatsHost {
  _NamedHost(super.source);

  @override
  String connectionName(BuildContext context, String id, String fallback) =>
      listSources?.chatListSources
          .where((source) => source.id == id)
          .firstOrNull
          ?.name ??
      fallback;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadCaptureFonts();
    // Arabic falls back to Noto, as on a device.
    final arabic = FontLoader('Noto Sans Arabic');
    for (final face in ['Regular', 'Bold']) {
      final bytes = File(
        'test/fixtures/fonts/NotoSansArabic-$face.ttf',
      ).readAsBytesSync();
      arabic.addFont(Future.value(ByteData.sublistView(bytes)));
    }
    await arabic.load();
    // AppTheme.forLocale names the platform sans for Arabic (Roboto).
    final sans = FontLoader('sans-serif');
    for (final face in ['Regular', 'Medium', 'Bold']) {
      final bytes = File(
        'tool/capture/fonts/Roboto-$face.ttf',
      ).readAsBytesSync();
      sans.addFont(Future.value(ByteData.sublistView(bytes)));
    }
    await sans.load();
  });

  for (final light in [false, true]) {
    final tone = light ? 'light' : 'dark';
    testWidgets('home $tone, then Settings from the gear', (tester) async {
      _mockSecureStorage(tester);
      _phone(tester, const Size(412, 915));
      final controller = await _controller();
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: _withBus(const HomeScreen()),
          boundaryKey: key,
          controller: controller,
          light: light,
        ),
      );
      await _settle(tester);
      expect(tester.takeException(), isNull);
      await writePng(
        '$_dir/$_phase-home-$tone.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      final gear = find.byKey(const ValueKey('home-shell-settings'));
      if (gear.evaluate().isEmpty) return;
      await tester.tap(gear);
      await _settle(tester);
      expect(tester.takeException(), isNull);
      await writePng(
        '$_dir/$_phase-settings-from-gear-$tone.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
    });
  }

  testWidgets('chat with changed files, then Files from the chip', (
    tester,
  ) async {
    _mockSecureStorage(tester);
    _phone(tester, const Size(412, 915));
    final controller = await _controller(busy: false);
    addTearDown(controller.dispose);
    final now = DateTime.now().millisecondsSinceEpoch;
    (controller.api! as CaptureApi).messagesHandler = (_) async =>
        _turnWithFiles(now);
    controller.busySessions = {};
    // The model list is read, as on a phone: the footer names the model.
    controller.catalog = sampleCatalog();
    final key = GlobalKey();
    final navigator = GlobalKey<NavigatorState>();
    // Opened from home, as on the phone: the bar has its Back.
    chatScreenFor = (id) => ChatScreen(sessionID: id);
    await tester.pumpWidget(
      captureApp(
        home: _withBus(const HomeScreen()),
        boundaryKey: key,
        controller: controller,
        navigatorKey: navigator,
      ),
    );
    await _settle(tester, frames: 2);
    unawaited(navigator.currentState!.pushNamed('/chat/$checkoutSessionID'));
    await _settle(tester, frames: 10);
    expect(tester.takeException(), isNull);
    await writePng(
      '$_dir/$_phase-chat-files-dark.png',
      await capturePng(tester, key, pixelRatio: 2),
    );
    final chip = find.byKey(const ValueKey('chat-project-chip'));
    if (chip.evaluate().isNotEmpty) {
      await tester.tap(chip);
      await _settle(tester);
      expect(tester.takeException(), isNull);
      await writePng(
        '$_dir/$_phase-files-from-chip-dark.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
    }
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
  });

  for (final scenario in [
    (name: 'pc-1280x800-dark', size: const Size(1280, 800), scale: 1.0),
    (name: 'phone-320-text2-dark', size: const Size(320, 760), scale: 2.0),
  ]) {
    testWidgets('home ${scenario.name}', (tester) async {
      _mockSecureStorage(tester);
      _phone(tester, scenario.size);
      final controller = await _controller();
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: Builder(
            builder: (context) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: TextScaler.linear(scenario.scale)),
              child: _withBus(const HomeScreen()),
            ),
          ),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _settle(tester);
      expect(tester.takeException(), isNull);
      await writePng(
        '$_dir/$_phase-home-${scenario.name}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
    });
  }

  testWidgets('home Arabic RTL', (tester) async {
    _mockSecureStorage(tester);
    _phone(tester, const Size(412, 915));
    tester.platformDispatcher.localesTestValue = const [Locale('ar')];
    addTearDown(tester.platformDispatcher.clearLocalesTestValue);
    final controller = await _controller();
    addTearDown(controller.dispose);
    final key = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: Builder(
          builder: (context) => Theme(
            data: AppTheme.forLocale(Theme.of(context), const Locale('ar')),
            child: _withBus(const HomeScreen()),
          ),
        ),
        boundaryKey: key,
        controller: controller,
      ),
    );
    await _settle(tester);
    expect(tester.takeException(), isNull);
    await writePng(
      '$_dir/$_phase-home-ar-dark.png',
      await capturePng(tester, key, pixelRatio: 2),
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
  });

  // Owner's phone, 2026-10-20: a connection not answering is the quiet
  // status line with Try again and ×, and the chips fit.
  for (final scene in [
    (name: 'home-unreachable-dark', size: const Size(412, 915), scale: 1.0),
    (
      name: 'home-phone-320-text2-states-dark',
      size: const Size(320, 760),
      scale: 2.0,
    ),
  ]) {
    testWidgets('conversations: ${scene.name}', (tester) async {
      _mockSecureStorage(tester);
      _phone(tester, scene.size);
      final now = DateTime(2026, 10, 20, 17);
      final source = FakeChatFeedSource(
        items: [
          chat(
            'a',
            'Fix the login bug',
            status: ChatStatus.needsYou,
            at: now.subtract(const Duration(minutes: 2)),
          ),
          chat(
            'b',
            'Add dark mode',
            status: ChatStatus.running,
            at: now.subtract(const Duration(minutes: 9)),
          ),
          chat(
            's',
            'Rename the API',
            at: now.subtract(const Duration(minutes: 20)),
            finishedUnseen: true,
            stoppedByPerson: true,
          ),
          chat(
            'c',
            'Explain the build',
            at: now.subtract(const Duration(hours: 30)),
          ),
        ],
        projects: [project('alpha', chats: 3, needs: 1, kind: 'dart')],
      )..unreachableServers = ['termux'];
      final host = _NamedHost(source)
        ..listSources = FakeListSources(const [
          ChatListSource(
            id: 'ubuntu',
            name: 'In-app Ubuntu',
            kind: ChatListSourceKind.openCode,
            shown: true,
            main: true,
          ),
          ChatListSource(
            id: 'termux',
            name: 'Termux',
            kind: ChatListSourceKind.openCode,
            shown: true,
            unreachable: true,
          ),
        ]);
      final controller = await _controller();
      addTearDown(controller.dispose);
      final key = GlobalKey();
      // The real shell (bar, pill, Search, gear) over this feed: the note
      // takes the status slot under the bar, above the title.
      await withClock(Clock.fixed(now), () async {
        await tester.pumpWidget(
          captureApp(
            home: Builder(
              builder: (context) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(scene.scale)),
                child: ProviderScope(
                  overrides: [chatsHostProvider.overrideWithValue(host)],
                  child: _withBus(const HomeScreen()),
                ),
              ),
            ),
            boundaryKey: key,
            controller: controller,
          ),
        );
        await _settle(tester);
      });
      expect(tester.takeException(), isNull);
      expect(find.byKey(const ValueKey('home-shell-settings')), findsOneWidget);
      await writePng(
        '$_dir/$_phase-${scene.name}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
    });
  }

  // Search's own empty state (mockup 6): Go to, then Recent.
  for (final scene in [
    (
      name: 'search-open-dark',
      size: const Size(412, 915),
      scale: 1.0,
      light: false,
      ar: false,
    ),
    (
      name: 'search-open-light',
      size: const Size(412, 915),
      scale: 1.0,
      light: true,
      ar: false,
    ),
    (
      name: 'search-open-320-text2-dark',
      size: const Size(320, 760),
      scale: 2.0,
      light: false,
      ar: false,
    ),
    (
      name: 'search-open-ar-dark',
      size: const Size(412, 915),
      scale: 1.0,
      light: false,
      ar: true,
    ),
  ]) {
    testWidgets('search open ${scene.name}', (tester) async {
      _mockSecureStorage(tester);
      _phone(tester, scene.size);
      if (scene.ar) {
        tester.platformDispatcher.localesTestValue = const [Locale('ar')];
        addTearDown(tester.platformDispatcher.clearLocalesTestValue);
      }
      final controller = await _controller();
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          // Above the navigator, so the Search sheet (a route of its own)
          // gets the same text size and Arabic theme as the screen under it.
          aboveNavigator: (child) => Builder(
            builder: (context) => Theme(
              data: scene.ar
                  ? AppTheme.forLocale(Theme.of(context), const Locale('ar'))
                  : Theme.of(context),
              child: MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(scene.scale)),
                child: child,
              ),
            ),
          ),
          home: _withBus(const HomeScreen()),
          boundaryKey: key,
          controller: controller,
          light: scene.light,
        ),
      );
      await _settle(tester, frames: 3);
      final context = tester.element(find.byType(HomeScreen));
      final l10n = AppLocalizations.of(context);
      unawaited(
        showCommandPalette(context, [
          DesktopCommand(
            label: l10n.e7LocaleUiNewSession,
            icon: Icons.add_rounded,
            onInvoke: () {},
          ),
          ...goToCommands(
            l10n,
            controller: controller,
            target: () => context,
            atShell: (_) {},
          ),
        ]),
      );
      await _settle(tester);
      expect(tester.takeException(), isNull);
      await writePng(
        '$_dir/$_phase-${scene.name}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
    });
  }
}
