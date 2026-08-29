import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/desktop/desktop_interaction.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ShellApi extends OpenCodeApi {
  _ShellApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [];
}

class _ShellRepository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = _ShellApi()
    ..repository = _ShellRepository()
    ..status = StreamStatus.connected;
}

/// A widget test that runs with the platform reported as Linux desktop.
///
/// The override has to be cleared inside the test body — flutter_test asserts
/// that no foundation debug variable outlives it, so tearDown runs too late.
void desktopTest(
  String description,
  Future<void> Function(WidgetTester tester) body,
) {
  testWidgets(description, (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.linux;
    try {
      await body(tester);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}

/// Presses an accelerator the way a desktop keyboard would.
Future<void> _press(WidgetTester tester, LogicalKeyboardKey key) async {
  await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
  await tester.sendKeyEvent(key);
  await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
  await tester.pumpAndSettle();
}

class _Harness {
  _Harness({this.onNewSession, this.onOpenSettings});

  final navigatorKey = GlobalKey<NavigatorState>();
  final signals = AppShortcutSignals();
  final VoidCallback? onNewSession;
  final VoidCallback? onOpenSettings;
  int newSessions = 0;
  int settingsOpens = 0;

  Widget app(Widget home) => MaterialApp(
    navigatorKey: navigatorKey,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    scrollBehavior: const AppScrollBehavior(),
    builder: (context, child) => AppShortcuts(
      navigatorKey: navigatorKey,
      signals: signals,
      handlers: AppShortcutHandlers(
        onNewSession: () {
          newSessions++;
          onNewSession?.call();
        },
        onOpenSettings: () {
          settingsOpens++;
          onOpenSettings?.call();
        },
        paletteCommands: (context) => [
          DesktopCommand(
            label: 'New session',
            icon: Icons.add_rounded,
            onInvoke: () => newSessions++,
          ),
          DesktopCommand(
            label: 'Open the diagnostics screen',
            icon: Icons.bug_report_outlined,
            onInvoke: () {},
          ),
        ],
      ),
      child: child ?? const SizedBox.shrink(),
    ),
    home: home,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('desktop', () {
    desktopTest('the binding table carries no plain-letter accelerator', (
      tester,
    ) async {
      // A bare letter would fire while the user typed into the composer.
      for (final activator in appShortcutBindings.keys) {
        expect(
          activator,
          isA<SingleActivator>().having(
            (a) => a.control || a.meta || a.alt,
            'carries a modifier',
            isTrue,
          ),
        );
      }
      expect(appShortcutBindings.length, 20);
    });

    desktopTest('Ctrl+K opens the command launcher and Enter runs a command', (
      tester,
    ) async {
      final harness = _Harness();
      await tester.pumpWidget(
        harness.app(const Scaffold(body: Text('surface'))),
      );
      await tester.pumpAndSettle();

      await _press(tester, LogicalKeyboardKey.keyK);
      expect(
        find.byKey(const ValueKey('desktop-command-palette')),
        findsOneWidget,
      );

      await tester.enterText(
        find.byKey(const ValueKey('command-palette-query')),
        'diagnostics',
      );
      await tester.pumpAndSettle();
      expect(find.text('New session'), findsNothing);
      expect(find.text('Open the diagnostics screen'), findsOneWidget);

      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('desktop-command-palette')),
        findsNothing,
      );
    });

    desktopTest('Ctrl+N asks the shell for a new session', (tester) async {
      final harness = _Harness();
      await tester.pumpWidget(
        harness.app(const Scaffold(body: Text('surface'))),
      );
      await tester.pumpAndSettle();

      await _press(tester, LogicalKeyboardKey.keyN);
      expect(harness.newSessions, 1);
    });

    desktopTest('Ctrl+N inside the composer still reaches the shell', (
      tester,
    ) async {
      final harness = _Harness();
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        harness.app(
          Scaffold(
            body: TextField(controller: controller, autofocus: true),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await _press(tester, LogicalKeyboardKey.keyN);
      expect(harness.newSessions, 1);
      // The accelerator must not type into the field it fired over.
      expect(controller.text, isEmpty);
    });

    desktopTest('Ctrl+, opens settings', (tester) async {
      final harness = _Harness();
      await tester.pumpWidget(
        harness.app(const Scaffold(body: Text('surface'))),
      );
      await tester.pumpAndSettle();

      await _press(tester, LogicalKeyboardKey.comma);
      expect(harness.settingsOpens, 1);
    });

    desktopTest('Ctrl+/ lists every shortcut', (tester) async {
      final harness = _Harness();
      await tester.pumpWidget(
        harness.app(const Scaffold(body: Text('surface'))),
      );
      await tester.pumpAndSettle();

      await _press(tester, LogicalKeyboardKey.slash);
      expect(
        find.byKey(const ValueKey('keyboard-shortcuts-sheet')),
        findsOneWidget,
      );
      expect(find.text('Command launcher'), findsOneWidget);
      expect(find.text('Send the prompt'), findsOneWidget);
      expect(find.text('Ctrl + W'), findsOneWidget);
    });

    desktopTest('Ctrl+W closes the current route', (tester) async {
      final harness = _Harness();
      await tester.pumpWidget(
        harness.app(
          Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: TextButton(
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const Scaffold(body: Text('pushed')),
                    ),
                  ),
                  child: const Text('push'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('push'));
      await tester.pumpAndSettle();
      expect(find.text('pushed'), findsOneWidget);

      await _press(tester, LogicalKeyboardKey.keyW);
      expect(find.text('pushed'), findsNothing);
      expect(find.text('push'), findsOneWidget);
    });

    desktopTest('Ctrl+W honours a PopScope veto', (tester) async {
      final harness = _Harness();
      var vetoed = 0;
      await tester.pumpWidget(
        harness.app(
          Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: TextButton(
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => PopScope(
                        canPop: false,
                        onPopInvokedWithResult: (_, _) => vetoed++,
                        child: const Scaffold(body: Text('guarded')),
                      ),
                    ),
                  ),
                  child: const Text('push'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('push'));
      await tester.pumpAndSettle();

      await _press(tester, LogicalKeyboardKey.keyW);
      expect(find.text('guarded'), findsOneWidget);
      expect(vetoed, 1);
    });

    desktopTest('Ctrl+1..4 switch the shell destinations', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final connection = await _controller();
      addTearDown(connection.dispose);
      final harness = _Harness();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(connection)],
          child: harness.app(const HomeScreen()),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('current-tab-title')))
            .data,
        'Workspace',
      );

      await _press(tester, LogicalKeyboardKey.digit2);
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('current-tab-title')))
            .data,
        'Files',
      );

      await _press(tester, LogicalKeyboardKey.digit4);
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('current-tab-title')))
            .data,
        'More',
      );

      await _press(tester, LogicalKeyboardKey.digit1);
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('current-tab-title')))
            .data,
        'Workspace',
      );
    });

    desktopTest('Ctrl+F focuses the Files find field, and only there', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final connection = await _controller();
      addTearDown(connection.dispose);
      final harness = _Harness();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(connection)],
          child: harness.app(const HomeScreen()),
        ),
      );
      await tester.pumpAndSettle();

      // Workspace has no find field: nothing takes focus.
      await _press(tester, LogicalKeyboardKey.keyF);
      expect(
        tester.binding.focusManager.primaryFocus?.debugLabel,
        isNot('files-search'),
      );

      await _press(tester, LogicalKeyboardKey.digit2);
      await _press(tester, LogicalKeyboardKey.keyF);
      expect(
        tester.binding.focusManager.primaryFocus?.debugLabel,
        'files-search',
      );
    });

    desktopTest('Escape already closes a modal sheet', (tester) async {
      // Flutter's own modal dismiss action covers this; the shortcut layer
      // must not rebind Escape and break it.
      final harness = _Harness();
      await tester.pumpWidget(
        harness.app(
          Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: TextButton(
                  onPressed: () => showModalBottomSheet<void>(
                    context: context,
                    builder: (_) => const SizedBox(
                      height: 160,
                      child: Center(child: Text('sheet body')),
                    ),
                  ),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('sheet body'), findsOneWidget);

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.text('sheet body'), findsNothing);
    });
  });

  group('android', () {
    testWidgets('no shortcut layer is installed', (tester) async {
      final harness = _Harness();
      await tester.pumpWidget(
        harness.app(const Scaffold(body: Text('surface'))),
      );
      await tester.pumpAndSettle();

      await _press(tester, LogicalKeyboardKey.keyK);
      expect(
        find.byKey(const ValueKey('desktop-command-palette')),
        findsNothing,
      );

      await _press(tester, LogicalKeyboardKey.keyN);
      expect(harness.newSessions, 0);
    });

    testWidgets('the More menu hides the shortcuts entry', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final connection = await _controller();
      addTearDown(connection.dispose);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(connection)],
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: HomeScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.more_vert));
      await tester.pumpAndSettle();

      expect(find.text('Refresh'), findsWidgets);
      expect(find.text('Keyboard shortcuts'), findsNothing);
    });
  });
}
