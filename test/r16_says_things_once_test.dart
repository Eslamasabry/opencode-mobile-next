// slice-R16: the Codex account, offline demo, Available on this server,
// Note for the agent and Active context pages say each thing once.
//
// Behaviour first (one test per acceptance line), then the gallery: phone
// 412x915 and one wide window (1280x800), dark and light (owner decision
// 2026-09-27: no Arabic), with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/r16_says_things_once_test.dart
// and look at every changed image before committing it.
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/agent_account.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/active_context_screen.dart';
import 'package:opencode_mobile/ui/screens/agent_account_screen.dart';
import 'package:opencode_mobile/ui/screens/demo_screen.dart';
import 'package:opencode_mobile/ui/screens/server_capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/session_note_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import 'revamp/screen_system_1_fixtures.dart' show systemController;
import 'support/account_fakes.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

// ---------------------------------------------------------------- fixtures

class _Notes extends ProductRepository implements SessionNoteGateway {
  String? value = 'Keep changes focused and explain each step.';
  @override
  bool get sessionNotesSupported => true;
  @override
  Future<String?> loadSessionNote(String id) async => value;
  @override
  Future<void> saveSessionNote(String id, String note) async => value = note;
  @override
  Future<void> removeSessionNote(String id) async => value = null;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Context extends ProductRepository implements ActiveContextGateway {
  @override
  bool get activeContextSupported => true;
  @override
  Future<List<ActiveContextMessage>> loadActiveContext(String id) async =>
      _rows;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

const _rows = [
  ActiveContextMessage(
    id: 'msg_01',
    type: 'system',
    content: [
      ContextContent(
        ContextContentKind.text,
        'Follow the project instructions.',
      ),
    ],
  ),
  ActiveContextMessage(
    id: 'msg_02',
    type: 'user',
    content: [
      ContextContent(ContextContentKind.text, 'Make the welcome friendlier.'),
    ],
  ),
  ActiveContextMessage(
    id: 'msg_03',
    type: 'assistant',
    content: [
      ContextContent(
        ContextContentKind.text,
        'I changed “Hello” to “Welcome aboard!”.',
      ),
      ContextContent(
        ContextContentKind.toolOutput,
        'lib/welcome.dart: 1 line changed',
        name: 'edit',
      ),
    ],
  ),
];

class _Controller extends ConnectionController {
  _Controller(super.store);
  @override
  int sessionHistoryRevision(String id) => 0;
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

Future<_Controller> _controller(ProductRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final controller = _Controller(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
  )..repository = repository;
  addTearDown(controller.dispose);
  return controller;
}

Future<AgentAccountController> _signedOut() async {
  final controller = AgentAccountController(FakeAccountSession());
  await controller.refresh();
  // A fixed "Last checked" so the gallery does not move with the clock.
  controller.updatedAt = DateTime(2026, 9, 27, 9, 41);
  addTearDown(controller.dispose);
  return controller;
}

const _channels = [
  'oc/background',
  'oc/termux',
  'oc/voice',
  'oc/read-aloud',
  'oc/camera',
  'oc/share',
  'plugins.it_nomads.com/flutter_secure_storage',
  'dev.shorebird/code_push',
];

/// The demo's chat never touches a real channel; these answer quietly.
void _quietChannels() {
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  for (final channel in _channels) {
    messenger.setMockMethodCallHandler(
      MethodChannel(channel),
      (call) async => null,
    );
  }
  messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
    if (call.method == 'Clipboard.hasStrings') return {'value': false};
    if (call.method == 'LiveText.isLiveTextInputAvailable') return false;
    return null;
  });
  addTearDown(() {
    for (final channel in _channels) {
      messenger.setMockMethodCallHandler(MethodChannel(channel), null);
    }
    messenger.setMockMethodCallHandler(SystemChannels.platform, null);
  });
}

Future<void> _demoPump(WidgetTester tester) async {
  // The production chat keeps a running-turn indicator animated.
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Widget _app(Widget home, {bool light = false}) => ProviderScope(
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: captureTheme(light: light),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: true),
      child: child!,
    ),
    home: home,
  ),
);

void _size(WidgetTester tester, Size size) {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Finder _key(String key) => find.byKey(ValueKey(key));

Finder _rich(String text) => find.textContaining(text, findRichText: true);

// ---------------------------------------------------------------- behaviour

void main() {
  setUp(() => debugPlatformCapabilities = const PlatformCapabilities.android());
  tearDown(() => debugPlatformCapabilities = null);

  group('behaviour', () {
    testWidgets('Codex account: the sign-in note is said once, not again '
        'under Details', (tester) async {
      _size(tester, _phone);
      final controller = await _signedOut();
      await tester.pumpWidget(
        _app(
          AgentAccountPanel(
            controller: controller,
            profileName: 'My Codex host',
          ),
        ),
      );
      await tester.pumpAndSettle();
      final l10n = lookupAppLocalizations(const Locale('en'));
      expect(find.text(l10n.agentAccountSignInNote), findsOneWidget);
      // The sign-in note in the person's words, not the runtime's.
      expect(
        find.text(
          'Sign in with your ChatGPT account. You finish in the browser; '
          'this app never sees your password.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('device-code'), findsNothing);
      await tester.tap(find.text('Details'));
      await tester.pumpAndSettle();
      expect(find.text(l10n.agentAccountSignInNote), findsOneWidget);
      expect(find.text(l10n.agentAccountHostNote), findsOneWidget);
      expect(find.textContaining('official Codex runtime'), findsNothing);
    });

    testWidgets('demo: the X leaves the demo, set-up waits for the '
        'finished notice, and nothing is said twice', (tester) async {
      _size(tester, _phone);
      _quietChannels();
      SharedPreferences.setMockInitialValues({});
      await HttpOverrides.runZoned(() async {
        await tester.pumpWidget(
          _app(
            Builder(
              builder: (context) => Center(
                child: GestureDetector(
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(builder: (_) => const DemoScreen()),
                  ),
                  child: const Text('Open demo'),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Open demo'));
        await _demoPump(tester);
        expect(_key('demo-set-up-server-menu'), findsNothing);
        expect(find.text('Set up your own server'), findsNothing);
        expect(find.byTooltip('Close'), findsNothing);
        expect(
          find.text(
            'Everything here is simulated on this device. No server, '
            'provider, or files are accessed.',
          ),
          findsOneWidget,
        );
        expect(find.textContaining('Nothing is saved.'), findsNothing);
        expect(find.text('Reset demo'), findsOneWidget);
        await tester.tap(find.byTooltip('Leave demo'));
        await _demoPump(tester);
        expect(find.text('Open demo'), findsOneWidget);
        await tester.pumpWidget(const SizedBox.shrink());
        await _demoPump(tester);
      }, createHttpClient: (_) => throw StateError('no network in the demo'));
    });

    testWidgets('capabilities: the intro says once where missing features '
        'work; rows repeat it only when they differ', (tester) async {
      _size(tester, const Size(412, 4000));
      final controller = await systemController(codexServerCapabilities);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(ServerCapabilitiesScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      expect(
        _rich('Missing features work on other OpenCode servers.'),
        findsOneWidget,
      );
      // Files has the most common answer: the intro covers it.
      expect(
        find.descendant(
          of: _key('capability-unavailable-files'),
          matching: _rich('Works on'),
        ),
        findsNothing,
      );
      // Worktrees work somewhere else: that row says where.
      expect(
        find.descendant(
          of: _key('capability-unavailable-worktrees'),
          matching: _rich('Works on'),
        ),
        findsOneWidget,
      );
    });

    testWidgets('capabilities: the focused feature names its servers', (
      tester,
    ) async {
      _size(tester, const Size(412, 4000));
      final controller = await systemController(codexServerCapabilities);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          ServerCapabilitiesScreen(
            controller: controller,
            focusFeature: 'files',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: _key('capability-unavailable-files'),
          matching: _rich('Works on'),
        ),
        findsOneWidget,
      );
    });

    testWidgets('capabilities: a server with every feature does not claim '
        'missing ones work elsewhere', (tester) async {
      _size(tester, const Size(412, 2000));
      final controller = await systemController(
        const ServerCapabilities(
          pluginInventory: true,
          developmentServices: true,
          webSearch: true,
          messageDelete: true,
          savedPermissionList: true,
          permissionRequests: true,
          inbox: true,
          mcpConfigWrites: true,
        ),
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(ServerCapabilitiesScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      expect(_key('server-capabilities-all'), findsOneWidget);
      expect(_rich('Missing features work on'), findsNothing);
    });

    testWidgets('note: Save note appears only once the words change; Delete '
        'saved note stays', (tester) async {
      _size(tester, _phone);
      final controller = await _controller(_Notes());
      await tester.pumpWidget(
        _app(SessionNoteScreen(controller: controller, sessionID: 'ses_1')),
      );
      await tester.pumpAndSettle();
      expect(_key('save-session-note'), findsNothing);
      expect(find.text('Change the note to save it.'), findsNothing);
      expect(_key('remove-session-note'), findsOneWidget);
      await tester.enterText(_key('session-note-editor'), 'Keep it short.');
      await tester.pumpAndSettle();
      expect(_key('save-session-note'), findsOneWidget);
      expect(find.text('Save note'), findsOneWidget);
      // Typing the saved words back hides it again.
      await tester.enterText(
        _key('session-note-editor'),
        'Keep changes focused and explain each step.',
      );
      await tester.pumpAndSettle();
      expect(_key('save-session-note'), findsNothing);
    });

    testWidgets('active context: plain intro, no count line, "{Role} '
        'message" pages with the disclaimer under Details', (tester) async {
      _size(tester, _phone);
      final controller = await _controller(_Context());
      await tester.pumpWidget(
        _app(ActiveContextScreen(controller: controller, sessionID: 'ses_1')),
      );
      await tester.pumpAndSettle();
      expect(
        find.text(
          'What the model reads on its next turn, after the latest summary.',
        ),
        findsOneWidget,
      );
      expect(find.text('3 messages'), findsNothing);
      expect(find.textContaining('not tokens'), findsNothing);
      await tester.tap(_key('active-context-msg_03'));
      await tester.pumpAndSettle();
      expect(find.text('Assistant message'), findsOneWidget);
      final l10n = lookupAppLocalizations(const Locale('en'));
      expect(find.text(l10n.activeContextContentHelp), findsNothing);
      await tester.ensureVisible(find.text('Details'));
      await tester.tap(find.text('Details'));
      await tester.pumpAndSettle();
      expect(find.text(l10n.activeContextContentHelp), findsOneWidget);
    });
  });

  // ---------------------------------------------------------------- gallery

  group('gallery', () {
    setUpAll(loadCaptureFonts);

    String name(String shot, Size size, bool light) => [
      'r16_$shot',
      if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
      light ? 'light' : 'dark',
    ].join('_');

    Future<void> shot(
      WidgetTester tester,
      String id, {
      required bool light,
      required Size size,
      required Widget home,
      Future<void> Function()? then,
    }) async {
      _size(tester, size);
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      final boundary = GlobalKey();
      try {
        await tester.pumpWidget(
          RepaintBoundary(
            key: boundary,
            child: _app(home, light: light),
          ),
        );
        await tester.pumpAndSettle();
        if (then != null) await then();
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('goldens/${name(id, size, light)}.png'),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        debugDefaultTargetPlatformOverride = null;
      }
    }

    for (final light in [false, true]) {
      for (final size in [_phone, _wide]) {
        final label = '${light ? 'light' : 'dark'} ${size.width.toInt()}';

        testWidgets('account signed out ($label)', (tester) async {
          final controller = await _signedOut();
          await shot(
            tester,
            'account_signed_out',
            light: light,
            size: size,
            home: AgentAccountPanel(
              controller: controller,
              profileName: 'My Codex host',
            ),
            then: () async {
              await tester.ensureVisible(find.text('Details'));
              await tester.tap(find.text('Details'));
            },
          );
        });

        testWidgets('capabilities on Codex ($label)', (tester) async {
          final controller = await systemController(codexServerCapabilities);
          addTearDown(controller.dispose);
          await shot(
            tester,
            'capabilities_codex',
            light: light,
            size: size,
            home: ServerCapabilitiesScreen(controller: controller),
          );
        });

        testWidgets('note unchanged ($label)', (tester) async {
          final controller = await _controller(_Notes());
          await shot(
            tester,
            'note_unchanged',
            light: light,
            size: size,
            home: SessionNoteScreen(controller: controller, sessionID: 'ses_1'),
          );
        });

        testWidgets('context message ($label)', (tester) async {
          final controller = await _controller(_Context());
          await shot(
            tester,
            'context_message',
            light: light,
            size: size,
            home: ActiveContextScreen(
              controller: controller,
              sessionID: 'ses_1',
            ),
            then: () async {
              await tester.tap(_key('active-context-msg_03'));
              await tester.pumpAndSettle();
              await tester.ensureVisible(find.text('Details'));
              await tester.tap(find.text('Details'));
            },
          );
        });
      }
    }
  });
}
