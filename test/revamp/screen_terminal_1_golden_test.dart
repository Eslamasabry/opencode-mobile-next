// Golden renders of the Terminal page (vision 2026-10-20, terminal first):
// the server's terminal open with its tabs (and wide), the one the page
// starts when none runs, a tab's menu, the stop question, the rename dialog,
// the explained gate on a server without terminals, this phone's terminal
// before Linux is set up; the standalone terminal surface (connected,
// closed, its menu); and the default shell sheet.
// Phone 412x915 and one wide window (1280x800), dark and light (owner
// decision 2026-09-27: no Arabic), with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_terminal_1_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/fake_local_terminal.dart';
import 'screen_terminal_1_fixtures.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

const _greeting =
    'dev@shopfront:/srv/shopfront\$ flutter test\r\n'
    '00:04 +41: All tests passed!\r\n'
    'dev@shopfront:/srv/shopfront\$ git status --short\r\n'
    ' M lib/checkout/checkout_page.dart\r\n'
    '?? test/checkout_test.dart\r\n'
    'dev@shopfront:/srv/shopfront\$ ';

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

class _Linux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: false,
    phase: BuiltinLinuxPhase.idle,
    services: [],
  );
}

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Widget Function(ConnectionController controller, TerminalServer s)
  home,
  TerminalServer? server,
  bool terminals = true,
  Size size = _phone,
  Future<void> Function()? then,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final repository = server ?? TerminalServer();
  repository.greeting = _greeting;
  final controller = await terminalController(repository, terminals: terminals);
  final backend = FakeLocalTerminalBackend();
  final sessions = LocalTerminalSessions(backend: backend);
  final linux = _Linux();
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
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: child!,
            ),
            home: home(controller, repository),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pumpAndSettle();
    if (then != null) await then();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
    disposeTerminalController(controller);
    sessions.dispose();
    await backend.close();
  }
}

/// A terminal's tab on the page (its name carries bidi marks).
Finder _tab(String title) => find.descendant(
  of: find.byKey(const ValueKey('terminal-tabs')),
  matching: find.textContaining(title),
);

Widget _list(ConnectionController controller, TerminalServer _) =>
    TerminalPage(controller: controller, localSupported: false);

Widget _surface(ConnectionController _, TerminalServer server) =>
    TerminalSurface(repository: server, process: runningShell);

const _bash = TerminalShellOption(
  path: '/bin/bash',
  name: 'bash',
  acceptable: true,
);
const _zsh = TerminalShellOption(
  path: '/usr/bin/zsh',
  name: 'zsh',
  acceptable: true,
);
const _fish = TerminalShellOption(
  path: '/usr/bin/fish',
  name: 'fish',
  acceptable: false,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('list · $mode', (tester) async {
      await _shot(tester, 'terminal_page_tabs', light: light, home: _list);
    });

    testWidgets('list wide · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_page_tabs',
        light: light,
        size: _wide,
        home: _list,
      );
    });

    testWidgets('list empty · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_page_started',
        light: light,
        server: TerminalServer(processes: []),
        home: _list,
      );
    });

    testWidgets('list row menu · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_tab_menu',
        light: light,
        home: _list,
        then: () => tester.longPress(_tab('build-server')),
      );
    });

    testWidgets('stop question · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_remove_sheet',
        light: light,
        home: _list,
        then: () async {
          await tester.longPress(_tab('build-server'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Stop build-server'));
        },
      );
    });

    testWidgets('rename dialog · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_rename_dialog',
        light: light,
        home: _list,
        then: () async {
          await tester.longPress(_tab('tests'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Rename tests'));
        },
      );
    });

    testWidgets('server without terminals · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_list_unavailable',
        light: light,
        terminals: false,
        home: (controller, _) => TerminalPage(
          controller: controller,
          localSupported: true,
          initialSource: TerminalSource.server,
        ),
      );
    });

    testWidgets('this phone without Linux · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_phone_not_set_up',
        light: light,
        home: (controller, _) => TerminalPage(
          controller: controller,
          localSupported: true,
          initialSource: TerminalSource.phone,
        ),
      );
    });

    testWidgets('surface connected · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_surface_open',
        light: light,
        home: _surface,
      );
    });

    testWidgets('surface connected wide · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_surface_open',
        light: light,
        size: _wide,
        home: _surface,
      );
    });

    testWidgets('surface closed · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_surface_closed',
        light: light,
        home: _surface,
        then: () async {
          final server = tester
              .widget<TerminalSurface>(find.byType(TerminalSurface))
              .repository;
          await (server as TerminalServer).channels.single.outputController
              .close();
          await tester.pump();
        },
      );
    });

    testWidgets('surface menu · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_surface_menu',
        light: light,
        home: _surface,
        then: () =>
            tester.tap(find.byKey(const ValueKey('terminal-surface-menu'))),
      );
    });

    testWidgets('default shell sheet · $mode', (tester) async {
      await _shot(
        tester,
        'terminal_shell_sheet',
        light: light,
        server: TerminalServer(
          shells: const TerminalShellSettings(
            selected: 'zsh',
            options: [_bash, _zsh, _fish],
          ),
        ),
        home: (controller, _) => Scaffold(
          body: SafeArea(
            child: ListView(
              children: [DefaultShellRow(controller: controller)],
            ),
          ),
        ),
        then: () => tester.tap(
          find.byKey(const ValueKey('default-shell-settings-entry')),
        ),
      );
    });
  }
}
