// Captures of the project's Files page and its reader (vision slice,
// 2026-10-20, docs/qa/vision-files-2026-10-20/README.md): the real page
// opened the way a conversation opens it, the live background painted still,
// real fonts, no server. Phone 412x915 dp at 2x pixels, one screen per image.
//
//   flutter test --concurrency=1 tool/capture/vision_files_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_PASS=pass1 \
//     tool/capture/vision_files_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-files/<pass>/<scene>.png
// (without a pass, straight into the folder).
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'dart:io';

import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

const _pass = String.fromEnvironment('VISION_PASS', defaultValue: '');
const _root = '/home/eslam/Storage2/launch-footage/vision-files';
String get _dir => _pass.isEmpty ? _root : '$_root/$_pass';

const _project = '/home/dev/neon-breaker';

const _gameJs = '''const paddle = { x: 0, y: 0, w: 100, h: 14 };
const balls = [];
let score = 0;

function launchBall() {
  balls.push({
    x: paddle.x,
    y: paddle.y - 12,
    vx: 0,
    vy: -400,
    r: 7,
  });
}

function movePaddle(pointerX) {
  paddle.x = Math.max(50, Math.min(width - 50, pointerX));
}

function update(dt) {
  for (const ball of balls) {
    ball.x += ball.vx * dt;
    ball.y += ball.vy * dt;
    if (ball.y > height) lose(ball);
  }
  score += hits.length * combo;
}
''';

/// What this project folder holds; `src/game.js` and `index.html` are the
/// uncommitted changes Git reports.
class _Api extends CaptureApi {
  _Api({this.empty = false});

  final bool empty;

  // What OpenCode 2 serves: list, read and find; no text or symbol search.
  @override
  ServerCapabilities get capabilities => api2ServerCapabilities;

  @override
  Future<List<String>> findFile(String query) async => [
    for (final path in ['src/game.js', 'src/levels.js'])
      if (path.contains(query)) path,
  ];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async {
    if (empty) return [];
    if (path == 'src') {
      return [
        for (final name in ['game.js', 'input.js', 'levels.js', 'sound.js'])
          FileNode(name: name, path: 'src/$name', isDir: false),
      ];
    }
    return [
      FileNode(name: '.git', path: '.git', isDir: true),
      FileNode(name: 'assets', path: 'assets', isDir: true),
      FileNode(name: 'src', path: 'src', isDir: true),
      for (final name in [
        '.gitignore',
        'index.html',
        'package.json',
        'README.md',
        'style.css',
      ])
        FileNode(name: name, path: name, isDir: false),
    ];
  }

  @override
  Future<FileContent> fileContent(String path) async =>
      FileContent(_gameJs, mimeType: 'text/javascript');
}

class _Repository extends CaptureRepository {
  _Repository({this.changes = true});

  final bool changes;

  @override
  Future<List<VersionControlFile>> listFileStatuses() async => changes
      ? [
          VersionControlFile(
            path: 'src/game.js',
            status: 'modified',
            additions: 30,
            deletions: 5,
          ),
          VersionControlFile(
            path: 'index.html',
            status: 'modified',
            additions: 4,
            deletions: 1,
          ),
        ]
      : [];

  @override
  Future<List<WorkspaceProject>> listProjects() async => [
    const WorkspaceProject(
      id: 'project_neon',
      name: 'neon-breaker',
      directory: _project,
      worktrees: [],
      updatedAt: 1,
    ),
  ];
}

enum _Scene {
  mainDark('main-dark'),
  mainLight('main-light', light: true),
  folder('folder-dark', open: ['project-file-src']),
  reader('reader-dark', open: ['project-file-src', 'project-file-src/game.js']),
  readerLight(
    'reader-light',
    light: true,
    open: ['project-file-src', 'project-file-src/game.js'],
  ),
  readerHome(
    'reader-no-conversation-dark',
    fromChat: false,
    open: ['project-file-src', 'project-file-src/game.js'],
  ),
  searching('search-dark', search: 'game'),
  clean('no-changes-dark', changes: false),
  emptyFolder('empty-folder-dark', empty: true),
  noProject('no-project-dark', project: false),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  smallReader(
    'small-320-text2-reader-dark',
    size: Size(320, 693),
    scale: 2,
    open: ['project-file-src', 'project-file-src/game.js'],
  ),
  arabic('arabic-rtl-dark', locale: Locale('ar')),
  arabicReader(
    'arabic-rtl-reader-dark',
    locale: Locale('ar'),
    open: ['project-file-src', 'project-file-src/game.js'],
  );

  const _Scene(
    this.file, {
    this.light = false,
    this.open = const [],
    this.search,
    this.fromChat = true,
    this.changes = true,
    this.empty = false,
    this.project = true,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final bool light;
  final List<String> open;
  final String? search;
  final bool fromChat;
  final bool changes;
  final bool empty;
  final bool project;
  final Size size;
  final double scale;
  final Locale locale;
}

final _phone = ServerProfile(
  id: 'phone',
  name: 'This phone',
  baseUrl: 'http://127.0.0.1:4097',
);

Future<void> _settle(WidgetTester tester) async {
  await tester.pump();
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    // Arabic falls back to Noto, as on a device; AppTheme.forLocale names
    // the platform sans (Roboto) for it.
    final arabic = FontLoader('Noto Sans Arabic');
    for (final face in ['Regular', 'Bold']) {
      final bytes = File(
        'test/fixtures/fonts/NotoSansArabic-$face.ttf',
      ).readAsBytesSync();
      arabic.addFont(Future.value(ByteData.sublistView(bytes)));
    }
    await arabic.load();
    final sans = FontLoader('sans-serif');
    for (final face in ['Regular', 'Medium', 'Bold']) {
      final bytes = File(
        'tool/capture/fonts/Roboto-$face.ttf',
      ).readAsBytesSync();
      sans.addFont(Future.value(ByteData.sublistView(bytes)));
    }
    await sans.load();
  });
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  for (final scene in _Scene.values) {
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // Blurred shadows as a phone draws them (the test binding flattens
      // them); put back before the test ends.
      debugDisableShadows = false;
      tester.view.physicalSize = scene.size * 2;
      tester.view.devicePixelRatio = 2;
      tester.view.padding = const FakeViewPadding(top: 64, bottom: 48);
      tester.platformDispatcher.textScaleFactorTestValue = scene.scale;
      tester.platformDispatcher.localesTestValue = [scene.locale];
      tester.platformDispatcher.localeTestValue = scene.locale;
      addTearDown(tester.view.reset);
      addTearDown(tester.platformDispatcher.clearAllTestValues);
      KitLiveBackground.frozenSeconds = 21;
      const secure = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
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
      final prefs = await setupCapturePreferences();
      final store = SeededProfileStore(prefs: prefs, seeded: [_phone]);
      final controller = CaptureController(store);
      final api = _Api(empty: scene.empty);
      controller
        ..api = api
        ..repository = _Repository(changes: scene.changes)
        ..status = StreamStatus.connected
        ..directory = scene.project ? _project : null
        ..sessionsById = Map.of(api.sessionsById);
      controller.appearance.value = scene.light
          ? AppAppearance.light
          : AppAppearance.dark;
      final handoff = ReviewHandoffSession(
        store: ReviewHandoffStore(),
        sessionID: checkoutSessionID,
      );
      final boundary = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          // The app's own Arabic type, as main.dart applies it.
          home: const SizedBox.expand(key: ValueKey('opener')),
          // The app's own Arabic type for every route, as main.dart does.
          aboveNavigator: (child) => Builder(
            builder: (context) => Theme(
              data: AppTheme.forLocale(Theme.of(context), scene.locale),
              child: child,
            ),
          ),
          boundaryKey: boundary,
          controller: controller,
          light: scene.light,
        ),
      );
      await tester.pump();
      final opener = tester.element(find.byKey(const ValueKey('opener')));
      unawaited(
        openProjectFiles(
          opener,
          controller,
          directory: scene.project ? _project : null,
          handoff: scene.fromChat ? handoff : null,
          onAttachFile: scene.fromChat ? (_, _) async {} : null,
          onReviewPrompt: scene.fromChat ? (_) {} : null,
        ),
      );
      await _settle(tester);
      if (_pass == 'before' &&
          find
              .byKey(const ValueKey('project-hub-files'))
              .evaluate()
              .isNotEmpty &&
          scene != _Scene.mainDark) {
        // Today's page is a list of tools; the browser is one row in.
        await tester.tap(find.byKey(const ValueKey('project-hub-files')));
        await _settle(tester);
      }
      for (final key in scene.open) {
        await tester.tap(find.byKey(ValueKey(key)));
        await _settle(tester);
      }
      if (scene.search case final query?) {
        final button = find.byKey(const ValueKey('project-files-search'));
        if (button.evaluate().isNotEmpty) {
          await tester.tap(button);
          await _settle(tester);
        }
        await tester.enterText(
          find.byKey(const ValueKey('files-search-field')),
          query,
        );
        await tester.pump(const Duration(milliseconds: 600));
        await _settle(tester);
        FocusManager.instance.primaryFocus?.unfocus();
        await _settle(tester);
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_dir/${scene.file}.png',
        await capturePng(tester, boundary, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump(const Duration(seconds: 10));
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
