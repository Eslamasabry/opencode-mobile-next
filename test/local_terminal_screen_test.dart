import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_state_view.dart';
import 'package:opencode_mobile/ui/kit/terminal_key_bar.dart';
import 'package:opencode_mobile/ui/screens/local_terminal_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:xterm/xterm.dart' as xterm;

import 'support/fake_local_terminal.dart';

// The local terminal's screen (docs/design/local-terminal-2026-09-24.md §4):
// the source choice, the states, the key bar, and shells that outlive it.

class _Linux extends BuiltinLinux {
  bool installed = true;
  List<String> services = const [];
  bool fail = false;

  @override
  Future<BuiltinLinuxStatus> status() async {
    if (fail) throw const BuiltinLinuxException('The status read failed');
    return BuiltinLinuxStatus(
      installed: installed,
      phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
      services: services,
    );
  }
}

void main() {
  late FakeLocalTerminalBackend backend;
  late LocalTerminalSessions sessions;
  late _Linux linux;
  late ConnectionController connection;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    backend = FakeLocalTerminalBackend();
    sessions = LocalTerminalSessions(backend: backend);
    linux = _Linux();
    connection = ConnectionController(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
    );
  });

  tearDown(() async {
    localTerminalOpenSetupOverride = null;
    connection.dispose();
    sessions.dispose();
    await backend.close();
  });

  Widget app(Widget home) => ProviderScope(
    overrides: [
      builtinLinuxProvider.overrideWithValue(linux),
      localTerminalProvider.overrideWithValue(sessions),
      connProvider.overrideWithValue(connection),
    ],
    child: MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: home,
    ),
  );

  Future<void> mountPhone(WidgetTester tester) async {
    tester.view
      ..physicalSize = const Size(412, 915)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(app(const LocalTerminalView()));
    await tester.pumpAndSettle();
  }

  group('states', () {
    testWidgets('Linux not installed: one state with Set up', (tester) async {
      linux.installed = false;
      var opened = 0;
      localTerminalOpenSetupOverride = (_) async => opened++;
      await mountPhone(tester);
      expect(
        find.byKey(const ValueKey('local-terminal-not-set-up')),
        findsOneWidget,
      );
      expect(find.text("Linux isn't set up on this phone"), findsOneWidget);
      expect(backend.calls, isNot(contains(startsWith('start'))));
      await tester.tap(find.byKey(const ValueKey('local-terminal-set-up')));
      await tester.pumpAndSettle();
      expect(opened, 1);
    });

    testWidgets('installed: a shell opens at once, with the key bar', (
      tester,
    ) async {
      backend.startGate = Completer<void>();
      await tester.pumpWidget(app(const LocalTerminalView()));
      await tester.pump();
      await tester.pump();
      // One loading bar while the shell starts.
      expect(find.byKey(const ValueKey('kit-loading-bar')), findsOneWidget);
      backend.startGate!.complete();
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('kit-loading-bar')), findsNothing);
      expect(find.byType(xterm.TerminalView), findsOneWidget);
      expect(find.byType(TerminalKeyBar), findsOneWidget);
      expect(sessions.shells.single.running, isTrue);
    });

    testWidgets('the shell ended: its output stays, with Restart', (
      tester,
    ) async {
      await mountPhone(tester);
      backend.output(1, 'bye\r\n');
      backend.exit(1, 0);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('local-terminal-ended')),
        findsOneWidget,
      );
      expect(find.text('It exited with code 0.'), findsOneWidget);
      expect(find.byType(TerminalKeyBar), findsNothing);
      expect(find.byType(xterm.TerminalView), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('local-terminal-restart')));
      await tester.pumpAndSettle();
      expect(sessions.shells.single.number, 2);
      expect(sessions.shells.single.running, isTrue);
      expect(backend.calls, contains('remove 1'));
      // The key bar comes back inside the one terminal surface (6b2903df),
      // so the new shell's screen changes size once more: that size reaches
      // the new shell after the resize debounce.
      await tester.pump(const Duration(milliseconds: 100));
      expect(backend.calls, contains(startsWith('resize 2 ')));
    });

    testWidgets('a shell that cannot start says so, with Try again', (
      tester,
    ) async {
      backend.startFailure = 'proot: permission denied';
      await mountPhone(tester);
      final state = tester.widget<KitStateView>(
        find.byKey(const ValueKey('local-terminal-failed')),
      );
      expect(state.title, "The shell didn't start");
      expect(state.details, contains('permission denied'));
      backend.startFailure = null;
      await tester.tap(find.byKey(const ValueKey('local-terminal-try-again')));
      await tester.pumpAndSettle();
      expect(sessions.shells.last.running, isTrue);
    });

    testWidgets('AI Team on: the line says what a shell costs', (tester) async {
      linux.services = ['server', 'aiteam'];
      backend.processes = 9;
      await mountPhone(tester);
      expect(
        find.byKey(const ValueKey('kit-status-local-terminal-cost')),
        findsOneWidget,
      );
      expect(find.textContaining('Each shell runs 2 programs'), findsOneWidget);
      expect(find.textContaining('the app runs 8'), findsOneWidget);
    });

    testWidgets('AI Team off: no cost line', (tester) async {
      await mountPhone(tester);
      expect(
        find.byKey(const ValueKey('kit-status-local-terminal-cost')),
        findsNothing,
      );
    });
  });

  group('keys', () {
    testWidgets('sticky Ctrl, then c from the phone keyboard, sends 0x03', (
      tester,
    ) async {
      await mountPhone(tester);
      final shell = sessions.shells.single;
      await tester.tap(find.byKey(const ValueKey('terminal-key-ctrl')));
      await tester.pump();
      shell.terminal.textInput('c');
      await tester.pump();
      shell.terminal.textInput('c');
      await tester.pumpAndSettle();
      expect(backend.written[1], [0x03, 0x63]);
    });

    testWidgets('the arrow keys and Esc reach the shell', (tester) async {
      await mountPhone(tester);
      await tester.tap(find.byKey(const ValueKey('terminal-key-up')));
      await tester.tap(find.byKey(const ValueKey('terminal-key-esc')));
      await tester.pumpAndSettle();
      expect(backend.writtenText(1), '\x1b[A\x1b');
    });
  });

  group('shells', () {
    testWidgets('the menu lists shells and opens a new one', (tester) async {
      await mountPhone(tester);
      await tester.tap(find.byKey(const ValueKey('local-terminal-menu')));
      await tester.pumpAndSettle();
      expect(find.text('Shell 1'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('local-terminal-new')));
      await tester.pumpAndSettle();
      expect(sessions.shells.map((shell) => shell.number), [1, 2]);
      expect(sessions.current, sessions.shells.last);
      await tester.tap(find.byKey(const ValueKey('local-terminal-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Shell 1'));
      await tester.pumpAndSettle();
      expect(sessions.current, sessions.shells.first);
    });

    testWidgets('Stop asks first, then stops the shell', (tester) async {
      await mountPhone(tester);
      await tester.tap(find.byKey(const ValueKey('local-terminal-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('local-terminal-stop')));
      await tester.pumpAndSettle();
      expect(backend.calls, isNot(contains('stop 1')));
      await tester.tap(
        find.byKey(const ValueKey('local-terminal-stop-confirm')),
      );
      await tester.pumpAndSettle();
      expect(backend.calls, contains('stop 1'));
      expect(
        find.byKey(const ValueKey('local-terminal-ended')),
        findsOneWidget,
      );
    });

    testWidgets('leaving and coming back finds the shell and its output', (
      tester,
    ) async {
      await mountPhone(tester);
      backend.output(1, 'kept line\r\n');
      await tester.pumpAndSettle();
      await tester.pumpWidget(app(const SizedBox()));
      await tester.pumpAndSettle();
      await tester.pumpWidget(app(const LocalTerminalView()));
      await tester.pumpAndSettle();
      expect(sessions.shells, hasLength(1));
      expect(backend.calls.where((call) => call.startsWith('start')), [
        isNotNull,
      ]);
      final text = sessions.shells.single.terminal.buffer.lines[0].getText();
      expect(text, contains('kept line'));
    });
  });

  group('source choice', () {
    Future<void> mountPage(
      WidgetTester tester, {
      TerminalSource? initial,
    }) async {
      tester.view
        ..physicalSize = const Size(412, 915)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        app(
          TerminalPage(
            controller: connection,
            initialSource: initial,
            localSupported: true,
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('This phone is the default once Linux is installed', (
      tester,
    ) async {
      await mountPage(tester);
      expect(find.byType(LocalTerminalView), findsOneWidget);
      expect(find.text('Built-in Linux'), findsOneWidget);
      expect(find.text('OpenCode server'), findsOneWidget);
    });

    testWidgets('without Linux the server is the default', (tester) async {
      linux.installed = false;
      await mountPage(tester);
      expect(find.byType(LocalTerminalView), findsNothing);
      expect(find.byType(TerminalScreen), findsOneWidget);
    });

    testWidgets('the choice switches between the two', (tester) async {
      await mountPage(tester);
      await tester.tap(find.byKey(const ValueKey('terminal-source-server')));
      await tester.pumpAndSettle();
      expect(find.byType(TerminalScreen), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('terminal-source-phone')));
      await tester.pumpAndSettle();
      expect(find.byType(LocalTerminalView), findsOneWidget);
      // The shell kept running while the server's list showed.
      expect(sessions.shells, hasLength(1));
    });

    testWidgets('landscape with the keyboard up: no choice, one row of keys', (
      tester,
    ) async {
      tester.view
        ..physicalSize = const Size(915, 412)
        ..devicePixelRatio = 1
        ..viewInsets = const FakeViewPadding(bottom: 250);
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        app(TerminalPage(controller: connection, localSupported: true)),
      );
      await tester.pumpAndSettle();
      expect(find.byType(LocalTerminalView), findsOneWidget);
      expect(find.byKey(const ValueKey('terminal-source')), findsNothing);
      final esc = tester.getRect(
        find.byKey(const ValueKey('terminal-key-esc')),
      );
      final ctrl = tester.getRect(
        find.byKey(const ValueKey('terminal-key-ctrl')),
      );
      expect(ctrl.top, esc.top, reason: 'one row');
      // The shell still has lines to show.
      expect(
        tester.getSize(find.byType(xterm.TerminalView)).height,
        greaterThan(40),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('off Android there is no choice, only the server', (
      tester,
    ) async {
      tester.view
        ..physicalSize = const Size(412, 915)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        app(TerminalPage(controller: connection, localSupported: false)),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('terminal-source')), findsNothing);
      expect(find.byType(TerminalScreen), findsOneWidget);
    });
  });
}
