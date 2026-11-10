// Captures of the Terminal screen (vision slice, 2026-10-20): the real
// TerminalPage at 412x915 dp (and 320 dp at 2x text, and Arabic), real fonts,
// a fake phone shell and a fake server, no device.
//
//   TZ=UTC flutter test --concurrency=1 tool/capture/vision_terminal_test.dart
//
// Output: /home/eslam/vision-assets/out/vision-terminal/<phase>-<scene>.png
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/domain/dev_preview.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/project_dev_server.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';

import '../../test/revamp/screen_terminal_1_fixtures.dart';
import '../../test/support/fake_local_terminal.dart';
import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'fixtures.dart';
// AUDIT TOOLING (audit/style-coherence): no-op unless AUDIT_STYLE is set.
import 'audit/audit_env.dart';

const _phase = String.fromEnvironment('VISION_PHASE', defaultValue: 'after');
const _dir = '/home/eslam/vision-assets/out/vision-terminal';

const _phoneOutput =
    '\x1b[1;32mroot@localhost\x1b[0m:\x1b[1;34m~/projects/neon-breaker\x1b[0m# git status\r\n'
    'On branch main\r\n'
    'Your branch is up to date with \'origin/main\'.\r\n'
    '\r\n'
    'nothing to commit, working tree clean\r\n'
    '\x1b[1;32mroot@localhost\x1b[0m:\x1b[1;34m~/projects/neon-breaker\x1b[0m# npm run dev &\r\n'
    '[1] 4182\r\n'
    '\r\n'
    '> neon-breaker@1.0.0 dev\r\n'
    '> vite --host\r\n'
    '\r\n'
    '  \x1b[32mVITE v5.4.2\x1b[0m  ready in \x1b[1m412\x1b[0m ms\r\n'
    '\r\n'
    '  \x1b[32m>\x1b[0m Local:   \x1b[36mhttp://localhost:5173/\x1b[0m\r\n'
    '\x1b[1;32mroot@localhost\x1b[0m:\x1b[1;34m~/projects/neon-breaker\x1b[0m# ';

const _serverOutput =
    'dev@laptop:~/shopfront\$ flutter test\r\n'
    '00:04 +41: All tests passed!\r\n'
    'dev@laptop:~/shopfront\$ git status --short\r\n'
    ' M lib/checkout/checkout_page.dart\r\n'
    '?? test/checkout_test.dart\r\n'
    'dev@laptop:~/shopfront\$ ';

class _Linux extends BuiltinLinux {
  _Linux(this.installed);
  final bool installed;
  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: installed,
    phase: BuiltinLinuxPhase.idle,
    services: const [],
  );
}

enum _Scene {
  main('main'),
  tabs('tabs'),
  server('server'),
  serverTabs('server-tabs'),
  ended('ended'),
  notSetUp('not-set-up');

  const _Scene(this.file);
  final String file;
}

Future<void> _capture(
  WidgetTester tester, {
  required _Scene scene,
  required bool light,
  Size size = const Size(412, 915),
  double textScale = 1,
  Locale? locale,
  String? suffix,
}) async {
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  debugDisableShadows = false;
  tester.view.physicalSize = size * 2;
  tester.view.devicePixelRatio = 2;
  addTearDown(tester.view.reset);
  final server = TerminalServer(
    processes: scene == _Scene.serverTabs
        ? [runningShell, endedTests, endedLint]
        : [runningShell],
  );
  server.greeting = _serverOutput;
  final controller = await terminalController(server);
  final backend = FakeLocalTerminalBackend();
  final sessions = LocalTerminalSessions(backend: backend);
  final linux = _Linux(scene != _Scene.notSetUp);
  if (scene == _Scene.tabs) {
    sessions.startShell();
    sessions.startShell();
  }
  final shellId = scene == _Scene.tabs ? 2 : 1;
  final dev = ProjectDevServer(
    look: () async =>
        scene == _Scene.main ? const DevPreviewLink(port: 5173) : null,
  );
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: ProviderScope(
          overrides: [
            builtinLinuxProvider.overrideWithValue(linux),
            localTerminalProvider.overrideWithValue(sessions),
            connProvider.overrideWithValue(controller),
          ],
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: light),
            locale: locale,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                disableAnimations: true,
                textScaler: TextScaler.linear(textScale),
              ),
              child: locale == null
                  ? auditWrap(context, child!)
                  : Theme(
                      data: AppTheme.forLocale(Theme.of(context), locale),
                      child: child!,
                    ),
            ),
            initialRoute: '/terminal',
            routes: {
              '/': (_) => const SizedBox.shrink(),
              '/terminal': (_) => TerminalPage(
                controller: controller,
                localSupported: true,
                initialSource:
                    scene == _Scene.server || scene == _Scene.serverTabs
                    ? TerminalSource.server
                    : TerminalSource.phone,
                linux: linux,
                sessions: sessions,
                devServer: _phase == 'before' ? null : dev,
              ),
            },
          ),
        ),
      ),
    );
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    if (scene == _Scene.main || scene == _Scene.ended || scene == _Scene.tabs) {
      backend.output(shellId, _phoneOutput);
      await tester.pump(const Duration(milliseconds: 100));
      if (scene == _Scene.ended) {
        backend.exit(shellId, 0);
      }
    }
    for (var i = 0; i < 8; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(tester.takeException(), isNull);
    await writePng(
      '$_dir/$_phase-${scene.file}${suffix ?? ''}-${light ? 'light' : 'dark'}.png',
      await capturePng(tester, boundary, pixelRatio: 2),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
    debugDisableShadows = true;
    disposeTerminalController(controller);
    dev.dispose();
    sessions.dispose();
    await backend.close();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  for (final light in [false, true]) {
    for (final scene in _Scene.values) {
      testWidgets('${scene.file} ${light ? 'light' : 'dark'}', (tester) async {
        await _capture(tester, scene: scene, light: light);
      });
    }
  }
  testWidgets('main 320 at 2x text', (tester) async {
    await _capture(
      tester,
      scene: _Scene.main,
      light: false,
      size: const Size(320, 915),
      textScale: 2,
      suffix: '-320-2x',
    );
  });
  testWidgets('main Arabic', (tester) async {
    await _capture(
      tester,
      scene: _Scene.main,
      light: false,
      locale: const Locale('ar'),
      suffix: '-ar',
    );
  });
}
