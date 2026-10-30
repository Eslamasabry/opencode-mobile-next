// screen-terminal-1 (wave 2b): the terminal pages rebuilt from kit parts.
// What the person sees, what is sent and what is kept: one list ordered by
// urgency with the state in words, acts that name their terminal, failures
// that stay where they happened, the explained gate with its way out, the
// terminal page's menu and details, and the default shell row.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/kit/kit_session_tabs.dart';
import 'package:opencode_mobile/ui/kit/kit_state_view.dart';
import 'package:opencode_mobile/ui/screens/local_terminal_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';

import '../support/fake_local_terminal.dart';
import 'screen_terminal_1_fixtures.dart';

class _Linux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: false,
    phase: BuiltinLinuxPhase.idle,
    services: [],
  );
}

Widget _app(Widget home, {List<Override> overrides = const []}) =>
    ProviderScope(
      overrides: overrides,
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: home,
      ),
    );

Future<void> _phone(WidgetTester tester) async {
  tester.view
    ..physicalSize = const Size(412, 915)
    ..devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Future<(TerminalServer, ConnectionController)> _list(
  WidgetTester tester, {
  TerminalServer? server,
}) async {
  await _phone(tester);
  final repository = server ?? TerminalServer();
  final controller = await terminalController(repository);
  addTearDown(() => disposeTerminalController(controller));
  await tester.pumpWidget(
    _app(TerminalPage(controller: controller, localSupported: false)),
  );
  await tester.pumpAndSettle();
  return (repository, controller);
}

Future<void> _openTabMenu(WidgetTester tester, String title) async {
  await tester.longPress(
    find.descendant(
      of: find.byType(KitSessionTabs),
      matching: find.textContaining(title),
    ),
  );
  await tester.pumpAndSettle();
}

/// A server whose new terminals fail to start.
class _NoStartServer extends TerminalServer {
  _NoStartServer() : super(processes: []);

  @override
  Future<TerminalProcess> createTerminal({String? title}) async =>
      throw const ProductException('The server is busy.');
}

void main() {
  group('terminal page on a server', () {
    testWidgets('the running terminal opens at once; the others are tabs, '
        'running first', (tester) async {
      final (server, _) = await _list(tester);
      expect(
        find.byKey(const ValueKey('terminal-surface-pty-1')),
        findsOneWidget,
      );
      expect(server.channels, hasLength(1), reason: 'connected to it');
      final tabs = find.byKey(const ValueKey('terminal-tabs'));
      expect(tabs, findsOneWidget);
      double x(String title) => tester
          .getTopLeft(
            find.descendant(of: tabs, matching: find.textContaining(title)),
          )
          .dx;
      expect(x('build-server'), lessThan(x('tests')));
      expect(x('tests'), lessThan(x('lint')));
      // No list and no pinned New terminal: it waits in the bar's menu.
      expect(find.byKey(const ValueKey('terminal-session-rows')), findsNothing);
      expect(find.byKey(const ValueKey('kit-screen-bottom')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('terminal-surface-menu')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('terminal-new')), findsOneWidget);
    });

    testWidgets('one terminal shows no tabs', (tester) async {
      await _list(tester, server: TerminalServer(processes: [runningShell]));
      expect(find.byKey(const ValueKey('terminal-tabs')), findsNothing);
      expect(find.text('Terminal'), findsOneWidget);
    });

    testWidgets('with none running it starts one, once', (tester) async {
      final (server, _) = await _list(
        tester,
        server: TerminalServer(processes: [endedTests]),
      );
      expect(server.processes, hasLength(2));
      final started = server.processes.last;
      expect(started.running, isTrue);
      expect(
        find.byKey(ValueKey('terminal-surface-${started.id}')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('terminal-none')), findsNothing);
    });

    testWidgets('R18: a start that fails says No terminals yet, names the '
        'project, and keeps New terminal', (tester) async {
      await _phone(tester);
      final controller = await terminalController(_NoStartServer());
      addTearDown(() => disposeTerminalController(controller));
      controller.directory = '/srv/shopfront';
      await tester.pumpWidget(
        _app(TerminalPage(controller: controller, localSupported: false)),
      );
      await tester.pumpAndSettle();
      expect(find.text('No terminals yet'), findsOneWidget);
      expect(find.textContaining('Start one in'), findsOneWidget);
      expect(find.textContaining('shopfront'), findsWidgets);
      expect(
        find.byKey(const ValueKey('terminal-list-notice')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('terminal-new')), findsOneWidget);
    });

    testWidgets("a tab's menu names the terminal it acts on", (tester) async {
      await _list(tester);
      await _openTabMenu(tester, 'build-server');
      expect(find.text('Rename build-server'), findsOneWidget);
      expect(find.text('Stop build-server'), findsOneWidget);
    });

    testWidgets('removing the ended terminals asks once, keeps running ones', (
      tester,
    ) async {
      final (server, _) = await _list(tester);
      await tester.tap(find.byKey(const ValueKey('terminal-surface-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Remove 2 ended terminals'));
      await tester.pumpAndSettle();
      expect(find.text('Remove 2 ended terminals?'), findsOneWidget);
      expect(server.removed, isEmpty, reason: 'nothing before the yes');
      await tester.tap(
        find.byKey(const ValueKey('terminal-remove-ended-confirm')),
      );
      await tester.pumpAndSettle();
      expect(server.removed, ['pty-2', 'pty-3']);
      // One terminal left: no tabs, and nothing ended to clear up.
      expect(find.byKey(const ValueKey('terminal-tabs')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('terminal-surface-menu')));
      await tester.pumpAndSettle();
      expect(find.text('Remove 2 ended terminals'), findsNothing);
    });

    testWidgets('a stop that fails keeps the question open with the reason', (
      tester,
    ) async {
      final (server, _) = await _list(tester);
      server.failure = const ProductException('The terminal is busy.');
      await _openTabMenu(tester, 'build-server');
      await tester.tap(find.text('Stop build-server'));
      await tester.pumpAndSettle();
      expect(find.text('Stop build-server?'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('terminal-remove-confirm')));
      await tester.pumpAndSettle();
      expect(server.removed, isEmpty);
      expect(find.text('Stop build-server?'), findsOneWidget);
      // The kit says it did not finish (never the server's raw words) and
      // offers Try again in place.
      expect(find.byKey(const ValueKey('kit-confirm-failed')), findsOneWidget);
    });

    testWidgets('a rename that fails stays in the dialog with the name kept', (
      tester,
    ) async {
      final (server, _) = await _list(tester);
      server.failure = const ProductException('Names must be unique.');
      await _openTabMenu(tester, 'tests');
      await tester.tap(find.text('Rename tests'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.descendant(
          of: find.byKey(const ValueKey('terminal-rename-field')),
          matching: find.byType(EditableText),
        ),
        'unit tests',
      );
      await tester.tap(find.byKey(const ValueKey('terminal-rename-confirm')));
      await tester.pumpAndSettle();
      expect(find.text('Names must be unique.'), findsOneWidget);
      expect(find.text('unit tests'), findsOneWidget);
      server.failure = null;
      await tester.tap(find.byKey(const ValueKey('terminal-rename-confirm')));
      await tester.pumpAndSettle();
      expect(server.renamed, {'pty-2': 'unit tests'});
    });
  });

  group('the gate explains itself', () {
    testWidgets('a server with no terminals says so about the terminal only, '
        'and the source choice is the one way to this phone', (tester) async {
      await _phone(tester);
      final backend = FakeLocalTerminalBackend();
      final sessions = LocalTerminalSessions(backend: backend);
      addTearDown(() async {
        sessions.dispose();
        await backend.close();
      });
      final linux = _Linux();
      final controller = await terminalController(
        TerminalServer(),
        terminals: false,
      );
      addTearDown(() => disposeTerminalController(controller));
      await tester.pumpWidget(
        _app(
          TerminalPage(
            controller: controller,
            localSupported: true,
            initialSource: TerminalSource.server,
            linux: linux,
            sessions: sessions,
          ),
          overrides: [
            builtinLinuxProvider.overrideWithValue(linux),
            localTerminalProvider.overrideWithValue(sessions),
          ],
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('terminal-unavailable')),
        findsOneWidget,
      );
      // The Terminal page talks about the terminal, not Files, and is
      // gated on the terminal-only registry entry (slice-R18).
      expect(find.text("This server doesn't share a terminal"), findsOneWidget);
      expect(
        tester
            .widget<KitStateView>(
              find.byKey(const ValueKey('terminal-unavailable')),
            )
            .capability,
        'flag:terminal',
      );
      expect(find.text('Files and terminal'), findsNothing);
      expect(find.byKey(const ValueKey('terminal-session-rows')), findsNothing);
      // One control for the switch: the place line, no second button.
      expect(find.byKey(const ValueKey('terminal-use-phone')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('terminal-source')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('terminal-source-phone')));
      await tester.pumpAndSettle();
      // This phone without Linux: the gate explains and offers setup.
      expect(find.byType(LocalTerminalView), findsOneWidget);
      expect(
        find.byKey(const ValueKey('local-terminal-not-set-up')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('local-terminal-set-up')),
        findsOneWidget,
      );
    });
  });

  group('terminal page', () {
    Future<TerminalServer> surface(WidgetTester tester) async {
      await _phone(tester);
      final server = TerminalServer();
      await tester.pumpWidget(
        _app(TerminalSurface(repository: server, process: runningShell)),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      return server;
    }

    Future<void> openMenu(WidgetTester tester) async {
      await tester.tap(find.byKey(const ValueKey('terminal-surface-menu')));
      await tester.pumpAndSettle();
    }

    testWidgets('connected: no status line; the menu names the terminal', (
      tester,
    ) async {
      await surface(tester);
      expect(
        find.byKey(const ValueKey('kit-status-terminal-connection')),
        findsNothing,
      );
      await openMenu(tester);
      expect(find.text('Rename build-server'), findsOneWidget);
      expect(find.text('Paste into build-server'), findsOneWidget);
      expect(find.text('Stop build-server'), findsOneWidget);
    });

    testWidgets('details hold the command, folder and process id', (
      tester,
    ) async {
      await surface(tester);
      await openMenu(tester);
      await tester.tap(find.byKey(const ValueKey('terminal-details')));
      await tester.pumpAndSettle();
      expect(find.text('build-server details'), findsOneWidget);
      expect(find.text('/srv/shopfront'), findsOneWidget);
      expect(find.text('4821'), findsOneWidget);
    });

    testWidgets('a closed connection says so with Reconnect', (tester) async {
      final server = await surface(tester);
      await server.channels.single.outputController.close();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.text('Connection closed'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('terminal-status-reconnect')));
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 20)),
      );
      await tester.pump();
      expect(server.channels, hasLength(2));
    });

    testWidgets('a rename here renames the page at once', (tester) async {
      final server = await surface(tester);
      await openMenu(tester);
      await tester.tap(find.byKey(const ValueKey('terminal-rename')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.descendant(
          of: find.byKey(const ValueKey('terminal-rename-field')),
          matching: find.byType(EditableText),
        ),
        'deploy',
      );
      await tester.tap(find.byKey(const ValueKey('terminal-rename-confirm')));
      await tester.pumpAndSettle();
      expect(server.renamed, {'pty-1': 'deploy'});
      expect(find.text('deploy'), findsOneWidget);
    });
  });

  group('default shell', () {
    Future<TerminalServer> row(
      WidgetTester tester,
      TerminalShellSettings shells,
    ) async {
      await _phone(tester);
      final server = TerminalServer(shells: shells);
      final controller = await terminalController(server);
      addTearDown(() => disposeTerminalController(controller));
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: ListView(children: [DefaultShellRow(controller: controller)]),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return server;
    }

    const bash = TerminalShellOption(
      path: '/bin/bash',
      name: 'bash',
      acceptable: true,
    );
    const fish = TerminalShellOption(
      path: '/usr/bin/fish',
      name: 'fish',
      acceptable: false,
    );

    testWidgets('one shell: the row says so and opens nothing', (tester) async {
      await row(
        tester,
        const TerminalShellSettings(selected: '', options: [bash]),
      );
      expect(
        find.text('bash · the only shell this server offers'),
        findsOneWidget,
      );
      await tester.tap(
        find.byKey(const ValueKey('default-shell-settings-entry')),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('server-shell-/bin/bash')),
        findsNothing,
      );
    });

    testWidgets('choosing a shell saves it; a failed save shows in the row', (
      tester,
    ) async {
      final server = await row(
        tester,
        const TerminalShellSettings(selected: '', options: [bash, fish]),
      );
      server.failure = const ProductException('The server said no.');
      await tester.tap(
        find.byKey(const ValueKey('default-shell-settings-entry')),
      );
      await tester.pumpAndSettle();
      expect(find.text('Shell for commands'), findsWidgets);
      await tester.tap(
        find.byKey(const ValueKey('server-shell-/usr/bin/fish')),
      );
      await tester.pumpAndSettle();
      expect(
        find.textContaining("Couldn't change the shell. The server said no."),
        findsOneWidget,
      );
      server.failure = null;
      await tester.tap(
        find.byKey(const ValueKey('default-shell-settings-entry')),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('server-shell-/usr/bin/fish')),
      );
      await tester.pumpAndSettle();
      expect(server.selectedShells, ['fish']);
    });
  });
}
