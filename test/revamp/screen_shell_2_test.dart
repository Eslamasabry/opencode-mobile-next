// Unit screen-shell-2: the shell (home_screen.dart) and the shortcut layer's
// visible parts (shortcuts.dart) rebuilt from kit parts. Behaviour only:
// what the person sees and what runs. Goldens live in
// test/goldens/work_parts_golden_test.dart.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/kit/kit_nav.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_search_field.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart'
    show OpenProjectToolIntent, ProjectTool;

import '../../tool/capture/fixtures.dart' show CaptureApi;
import '../support/work_tab_fixture.dart';

/// A server without project tools (Codex, Paseo today).
class _NoProjectApi extends CaptureApi {
  @override
  ServerCapabilities get capabilities => const ServerCapabilities(
    fileBrowsing: false,
    terminal: false,
    projectManagement: false,
    globalSessionSearch: false,
    sessionImportExport: false,
    serverCatalog: false,
  );
}

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

void _size(WidgetTester tester, Size size) {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

class _Shell {
  final navigatorKey = GlobalKey<NavigatorState>();
  final signals = AppShortcutSignals();
  int palettes = 0;
  int newSessions = 0;

  Widget app(ConnectionController controller, {int initialTab = 0}) =>
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          navigatorKey: navigatorKey,
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => AppShortcuts(
            navigatorKey: navigatorKey,
            signals: signals,
            handlers: AppShortcutHandlers(
              onNewSession: () => newSessions++,
              onOpenSettings: () {},
              paletteCommands: (_) {
                palettes++;
                return [
                  DesktopCommand(
                    label: 'New conversation',
                    icon: Icons.add,
                    onInvoke: () => newSessions++,
                  ),
                ];
              },
            ),
            child: child!,
          ),
          home: HomeScreen(initialTab: initialTab),
        ),
      );
}

Future<void> _settle(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 600));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('shell frame', () {
    testWidgets('a phone gets the dock and the glass top controls', (
      tester,
    ) async {
      _mockSecureStorage(tester);
      _size(tester, const Size(412, 915));
      final controller = await workController(sessions: workLoadedSessions());
      addTearDown(controller.dispose);
      await tester.pumpWidget(_Shell().app(controller));
      await _settle(tester);

      expect(find.byType(KitNavBar), findsOneWidget);
      expect(find.byType(KitNavRail), findsNothing);
      for (final label in ['Chats', 'Files', 'Settings']) {
        expect(
          find.descendant(
            of: find.byType(KitNavBar),
            matching: find.text(label),
          ),
          findsOneWidget,
        );
      }
      // The server pill says the state in words, not only by colour.
      expect(
        find.byKey(const ValueKey('server-switcher-button')),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('server-switcher-button')),
          matching: find.textContaining('Connected'),
        ),
        findsOneWidget,
      );
      // Search opens the same launcher as Ctrl/Cmd+K.
      await tester.tap(find.byKey(const ValueKey('home-shell-search')));
      await _settle(tester);
      expect(
        find.byKey(const ValueKey('desktop-command-palette')),
        findsOneWidget,
      );
    });

    testWidgets('a PC window gets the sidebar, which alone names the tab', (
      tester,
    ) async {
      _mockSecureStorage(tester);
      _size(tester, const Size(1280, 800));
      final controller = await workController(sessions: workLoadedSessions());
      addTearDown(controller.dispose);
      await tester.pumpWidget(_Shell().app(controller, initialTab: 1));
      await _settle(tester);

      expect(find.byType(KitNavBar), findsNothing);
      final rail = tester.widget<KitNavRail>(find.byType(KitNavRail));
      expect(rail.extended, isTrue);
      expect(rail.destinations[rail.selected].label, 'Files');
      // slice-R14: the pane has no bar repeating the highlighted
      // destination; it starts with the destination's own content.
      expect(find.byType(KitTopBar), findsNothing);
      expect(find.byKey(const ValueKey('current-tab-title')), findsNothing);
      // The sidebar holds the server pill.
      expect(
        find.descendant(
          of: find.byType(KitNavRail),
          matching: find.byKey(const ValueKey('server-switcher-button')),
        ),
        findsOneWidget,
      );
    });

    testWidgets('a medium window gets the rail; 760 is no longer a break', (
      tester,
    ) async {
      _mockSecureStorage(tester);
      // 700 dp was a dock under the old 760 literal; medium is a rail.
      _size(tester, const Size(700, 1000));
      final controller = await workController(sessions: workLoadedSessions());
      addTearDown(controller.dispose);
      await tester.pumpWidget(_Shell().app(controller));
      await _settle(tester);

      expect(find.byType(KitNavBar), findsNothing);
      expect(
        tester.widget<KitNavRail>(find.byType(KitNavRail)).extended,
        isFalse,
      );
    });

    testWidgets('the first back on Chats says a second one exits', (
      tester,
    ) async {
      _mockSecureStorage(tester);
      _size(tester, const Size(412, 915));
      final controller = await workController(sessions: workLoadedSessions());
      addTearDown(controller.dispose);
      await tester.pumpWidget(_Shell().app(controller));
      await _settle(tester);

      await tester.binding.handlePopRoute();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.text('Press back again to exit'), findsOneWidget);

      await tester.pump(const Duration(seconds: 3));
      await tester.pump(const Duration(milliseconds: 600));
      expect(find.text('Press back again to exit'), findsNothing);
    });
  });

  group('Files on a server without project tools', () {
    testWidgets('a search result for Files explains and offers the switch', (
      tester,
    ) async {
      _mockSecureStorage(tester);
      _size(tester, const Size(412, 915));
      final controller = await workController(sessions: workLoadedSessions());
      controller.api = _NoProjectApi();
      addTearDown(controller.dispose);
      final shell = _Shell();
      await tester.pumpWidget(shell.app(controller));
      await _settle(tester);

      expect(find.text('Files'), findsNothing);
      expect(
        shell.signals.dispatch(const OpenProjectToolIntent(ProjectTool.files)),
        isTrue,
      );
      await _settle(tester);
      expect(
        find.byKey(const ValueKey('home-shell-project-unavailable-sheet')),
        findsOneWidget,
      );
      expect(find.text("Files isn't available"), findsOneWidget);
      expect(
        find.textContaining("doesn't offer files, changes or code search"),
        findsOneWidget,
      );
      expect(find.text('Switch server'), findsOneWidget);
    });

    testWidgets('the terminal shortcut opens the Terminal page, which says '
        'why there is none, instead of doing nothing (slice-R14)', (
      tester,
    ) async {
      _mockSecureStorage(tester);
      _size(tester, const Size(1280, 800));
      final controller = await workController(sessions: workLoadedSessions());
      controller.api = _NoProjectApi();
      addTearDown(controller.dispose);
      final shell = _Shell();
      // A PC: the server's terminal is the only source.
      debugDefaultTargetPlatformOverride = TargetPlatform.linux;
      try {
        await tester.pumpWidget(shell.app(controller));
        await _settle(tester);

        expect(shell.signals.dispatch(const OpenTerminalIntent()), isTrue);
        await _settle(tester);
        expect(find.byKey(const ValueKey('terminal-page')), findsOneWidget);
        expect(
          find.byKey(const ValueKey('terminal-unavailable')),
          findsOneWidget,
        );
        await tester.pumpWidget(const SizedBox());
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
    });

    testWidgets('Ctrl+2 explains instead of silently landing on Chats', (
      tester,
    ) async {
      _mockSecureStorage(tester);
      _size(tester, const Size(412, 915));
      final controller = await workController(sessions: workLoadedSessions());
      controller.api = _NoProjectApi();
      addTearDown(controller.dispose);
      final shell = _Shell();
      await tester.pumpWidget(shell.app(controller, initialTab: 1));
      await _settle(tester);

      shell.signals.dispatch(const SelectDestinationIntent(1));
      await _settle(tester);
      expect(find.text("Files isn't available"), findsOneWidget);
    });

    testWidgets('the tab going away after a server switch is explained', (
      tester,
    ) async {
      _mockSecureStorage(tester);
      _size(tester, const Size(412, 915));
      final controller = await workController(sessions: workLoadedSessions());
      addTearDown(controller.dispose);
      await tester.pumpWidget(_Shell().app(controller, initialTab: 1));
      await _settle(tester);
      expect(
        find.byKey(const ValueKey('home-shell-project-unavailable')),
        findsNothing,
      );

      controller.api = _NoProjectApi();
      controller.notifyListeners();
      await _settle(tester);

      final row = find.byKey(const ValueKey('home-shell-project-unavailable'));
      expect(row, findsOneWidget);
      expect(
        find.descendant(of: row, matching: find.byType(KitRow)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: row, matching: find.text('Switch server')),
        findsOneWidget,
      );

      // Picking a tab is the person moving on.
      await tester.tap(
        find.descendant(
          of: find.byType(KitNavBar),
          matching: find.text('Chats'),
        ),
      );
      await _settle(tester);
      expect(row, findsNothing);
    });
  });

  group('command launcher', () {
    test('matches label, hint and keywords in any case', () {
      final commands = [
        DesktopCommand(
          label: 'Notifications',
          icon: Icons.add,
          onInvoke: () {},
        ),
        DesktopCommand(
          label: 'Appearance',
          icon: Icons.add,
          hint: 'Theme and text size',
          onInvoke: () {},
        ),
        DesktopCommand(
          label: 'Diagnostics',
          icon: Icons.add,
          keywords: 'logs errors',
          onInvoke: () {},
        ),
      ];
      expect(matchCommands(commands, '  '), commands);
      expect(matchCommands(commands, 'NOTIF').single.label, 'Notifications');
      expect(matchCommands(commands, 'theme').single.label, 'Appearance');
      expect(matchCommands(commands, 'logs').single.label, 'Diagnostics');
      expect(matchCommands(commands, 'zzz'), isEmpty);
      // The shared settings matcher (P9.4): one typo still finds it.
      expect(matchCommands(commands, 'diagnotics').single.label, 'Diagnostics');
    });

    Future<List<String>> openPalette(WidgetTester tester) async {
      final ran = <String>[];
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Center(
              child: GestureDetector(
                onTap: () => showCommandPalette(context, [
                  DesktopCommand(
                    label: 'New conversation',
                    icon: Icons.add,
                    keys: 'Ctrl + N',
                    onInvoke: () => ran.add('new'),
                  ),
                  DesktopCommand(
                    label: 'Diagnostics',
                    icon: Icons.add,
                    hint: 'Recent errors and connection detail',
                    onInvoke: () => ran.add('diagnostics'),
                  ),
                ]),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      return ran;
    }

    testWidgets('filters as you type; Enter runs the filled first match', (
      tester,
    ) async {
      final ran = await openPalette(tester);
      expect(find.byType(KitSearchField), findsOneWidget);
      expect(find.text('New conversation'), findsOneWidget);
      // No keyboard hints on a phone.
      expect(find.text('Ctrl + N'), findsNothing);

      await tester.enterText(
        find.byKey(const ValueKey('command-palette-query')),
        'errors',
      );
      await tester.pump();
      expect(find.text('New conversation'), findsNothing);
      final row = tester.widget<KitRow>(
        find.byKey(const ValueKey('command-Diagnostics')),
      );
      expect(row.selected, isTrue);

      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();
      expect(ran, ['diagnostics']);
      expect(
        find.byKey(const ValueKey('desktop-command-palette')),
        findsNothing,
      );
    });

    testWidgets('no match says so and clears', (tester) async {
      await openPalette(tester);
      await tester.enterText(
        find.byKey(const ValueKey('command-palette-query')),
        'zzz',
      );
      await tester.pumpAndSettle();
      expect(find.byType(KitSearchNoMatch), findsOneWidget);
      await tester.tap(find.text('Clear search'));
      await tester.pumpAndSettle();
      expect(find.text('Diagnostics'), findsOneWidget);
    });

    testWidgets('a desktop shows the keys on each row', (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.linux;
      try {
        await openPalette(tester);
        expect(find.text('Ctrl + N'), findsOneWidget);
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
    });
  });

  group('shortcuts help', () {
    testWidgets('groups rows by where they work', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Center(
              child: GestureDetector(
                onTap: () => showShortcutsHelp(context),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();

      final anywhere = find.byKey(const ValueKey('shortcuts-help-anywhere'));
      final conversation = find.byKey(
        const ValueKey('shortcuts-help-conversation'),
      );
      expect(find.text('Anywhere'), findsOneWidget);
      expect(find.text('In a conversation'), findsOneWidget);
      expect(
        find.descendant(of: anywhere, matching: find.text('Command launcher')),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: anywhere,
          matching: find.text('Find on this screen'),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: conversation,
          matching: find.text('Send the prompt'),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(of: conversation, matching: find.text('Ctrl + B')),
        findsOneWidget,
      );
      // Every entry is listed once.
      final l10n = lookupAppLocalizations(const Locale('en'));
      for (final entry in shortcutHelp(l10n)) {
        expect(find.text(entry.keys), findsOneWidget, reason: entry.keys);
      }
    });
  });
}
