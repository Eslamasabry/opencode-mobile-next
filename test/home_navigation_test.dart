import 'package:flutter/material.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;

import 'package:flutter/rendering.dart';
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
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/kit/kit_nav.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/navigation/last_project.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/widgets/remote_folder_picker.dart';
import 'package:opencode_mobile/ui/kit/glass/kit_glass.dart';
import 'package:opencode_mobile/ui/kit/kit_bottom_inset.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ShellApi extends OpenCodeApi {
  _ShellApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [];
}

class _LongFilesApi extends _ShellApi {
  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [
    for (var i = 0; i < 30; i++)
      FileNode(
        name: 'file-${i.toString().padLeft(2, '0')}.dart',
        path: 'file-${i.toString().padLeft(2, '0')}.dart',
        isDir: false,
      ),
  ];
}

class _NestedFilesApi extends _ShellApi {
  final paths = <String>[];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async {
    paths.add(path);
    return switch (path) {
      '' => [FileNode(name: 'lib', path: 'lib', isDir: true)],
      'lib' => [FileNode(name: 'src', path: 'lib/src', isDir: true)],
      _ => [],
    };
  }

  @override
  Future<List<String>> findFile(String query) async => [];
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

class _ShellProfileStore extends ProfileStore {
  final ServerProfile? profile;

  _ShellProfileStore({required super.prefs, this.profile});

  @override
  List<ServerProfile> get profiles => [?profile];

  @override
  String? get activeId => profile?.id;
}

Future<ConnectionController> _controller({
  String? profileName,
  String? directory,
  Map<String, Object> seed = const {},
}) async {
  SharedPreferences.setMockInitialValues({...seed});
  final prefs = await SharedPreferences.getInstance();
  final store = _ShellProfileStore(
    prefs: prefs,
    profile: profileName == null
        ? null
        : ServerProfile(
            id: 'local',
            name: profileName,
            baseUrl: 'http://localhost:4096',
          ),
  );
  return ConnectionController(store)
    ..api = _ShellApi()
    ..repository = _ShellRepository()
    ..status = StreamStatus.connected
    // The Project hub lists its tools only once a project is open.
    ..directory = directory;
}

Widget _shellHome(ConnectionController controller, {int? initialTab}) =>
    Builder(
      builder: (context) => ListenableBuilder(
        listenable: controller,
        builder: (context, _) => AppConditionsScope(
          conditions: [
            connectionKitStatus(
              context,
              controller,
              actionContext: () => context,
            ),
          ],
          child: HomeScreen(initialTab: initialTab),
        ),
      ),
    );

Future<void> _pumpShell(
  WidgetTester tester,
  ConnectionController controller, {
  bool disableAnimations = false,
}) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(disableAnimations: disableAnimations),
          child: child!,
        ),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: _shellHome(controller),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

// The shell revamp names the current destination in its selected navigation
// item; compact/medium headers now identify the server instead of repeating it.
String _selectedDestination(WidgetTester tester) {
  final nav = tester.widget<KitNav>(find.byType(KitNav));
  final destination = nav.destinations[nav.selected];
  expect(
    tester.getSemantics(find.byKey(destination.key!)),
    isSemantics(
      isButton: true,
      isSelected: true,
      hasSelectedState: true,
      hasTapAction: true,
      label: destination.needsYou > 0
          ? '${destination.label}, ${destination.needsYou} need you'
          : destination.label,
    ),
  );
  return destination.label;
}

Finder _dockGlass() => find.descendant(
  of: find.byType(KitNavBar),
  matching: find.byType(KitGlass),
);

Finder _chatsDestination() =>
    find.byKey(const ValueKey('home-shell-tab-chats'));

/// The dock's selection lens. Since kit-fluid-glass (71cb03cd) it rides
/// KitMotion springs on one ticker instead of an AnimatedPositioned.
Rect _dockLens(WidgetTester tester) => tester.getRect(
  find.descendant(
    of: find.byType(KitNavBar),
    matching: find.byWidgetPredicate(
      (widget) => widget.runtimeType.toString() == '_KitNavLens',
    ),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final (width, locale) in [
    (320.0, const Locale('en')),
    (390.0, const Locale('en')),
    (320.0, const Locale('ar')),
  ]) {
    testWidgets('navigation stays inside its glass surface at $width and 2.5x '
        '(${locale.languageCode})', (tester) async {
      tester.view.physicalSize = Size(width, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: MaterialApp(
            theme: AppTheme.dark(),
            locale: locale,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(2.5)),
              child: child!,
            ),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: _shellHome(controller),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final dock = tester.getRect(_dockGlass());
      final icon = tester.getRect(
        find.descendant(
          of: find.byType(KitNavBar),
          matching: find.byIcon(AppIconography.chat),
        ),
      );
      expect(icon.top, greaterThanOrEqualTo(dock.top + 4));
      final copy = lookupAppLocalizations(locale);
      final labels = [
        copy.shellTabChats,
        copy.shellTabFiles,
        copy.librarySettingsTitle,
      ];
      if (locale.languageCode == 'en') {
        expect(labels, ['Conversations', 'Files', 'Settings']);
      } else {
        expect(labels, ['المحادثات', 'الملفات', 'الإعدادات']);
        expect(
          Directionality.of(tester.element(find.byType(KitNavBar))),
          TextDirection.rtl,
        );
      }
      for (final label in labels) {
        final rect = tester.getRect(
          find.descendant(
            of: find.byType(KitNavBar),
            matching: find.text(label),
          ),
        );
        final paragraph = tester.renderObject<RenderParagraph>(
          find.descendant(
            of: find.descendant(
              of: find.byType(KitNavBar),
              matching: find.text(label),
            ),
            matching: find.byType(RichText),
          ),
        );
        final lines = paragraph
            .getBoxesForSelection(
              TextSelection(baseOffset: 0, extentOffset: label.length),
            )
            .map((box) => box.top)
            .toSet();
        expect(lines, hasLength(1), reason: '$label stays on one line');
        expect(rect.bottom, lessThanOrEqualTo(dock.bottom - 4));
        expect(rect.left, greaterThanOrEqualTo(dock.left));
        expect(rect.right, lessThanOrEqualTo(dock.right));
      }
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets(
    'Files Back clears search, ascends folders, returns home, then guards exit',
    (tester) async {
      final api = _NestedFilesApi();
      final controller = await _controller(directory: '/srv/app')
        ..api = api;
      addTearDown(controller.dispose);
      var exits = 0;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'SystemNavigator.pop') exits++;
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await _pumpShell(tester, controller);
      await tester.tap(find.byIcon(AppIconography.files));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('project-hub-files')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('lib'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('src'));
      await tester.pumpAndSettle();
      expect(
        tester.getSemantics(find.bySemanticsLabel('Open Project root')).label,
        contains('Project root'),
      );
      final search = find.byKey(const ValueKey('files-search-field'));
      await tester.enterText(search, 'needle');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(tester.widget<TextField>(search).controller!.text, isEmpty);
      expect(api.paths.last, 'lib/src');
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(api.paths.last, 'lib');
      expect(exits, 0);
      expect(find.text('Press back again to exit'), findsNothing);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(api.paths.last, '');
      await tester.tap(search);
      tester.view.viewInsets = const FakeViewPadding(bottom: 260);
      addTearDown(tester.view.resetViewInsets);
      await tester.pump();
      await tester.binding.handlePopRoute();
      await tester.pump();
      expect(find.text('Press back again to exit'), findsNothing);
      expect(exits, 0);
      expect(tester.widget<TextField>(search).focusNode?.hasFocus, isFalse);
      tester.view.resetViewInsets();
      await tester.pump();
      // At the root of Files, Back returns to the Files hub first.
      await tester.binding.handlePopRoute();
      await tester.pump();
      expect(find.text('Press back again to exit'), findsNothing);
      expect(_selectedDestination(tester), 'Files');
      expect(
        find.byKey(const ValueKey('project-hub-files')).hitTestable(),
        findsOneWidget,
      );
      expect(search.hitTestable(), findsNothing);
      await tester.binding.handlePopRoute();
      await tester.pump();
      expect(find.text('Press back again to exit'), findsNothing);
      expect(_selectedDestination(tester), 'Conversations');
      await tester.binding.handlePopRoute();
      await tester.pump();
      expect(find.text('Press back again to exit'), findsOneWidget);
      expect(exits, 0);
      await tester.binding.handlePopRoute();
      await tester.pump();
      expect(exits, 1);
    },
  );

  testWidgets('inactive Files tab does not consume Back', (tester) async {
    final api = _NestedFilesApi();
    final controller = await _controller(directory: '/srv/app')
      ..api = api;
    addTearDown(controller.dispose);
    await _pumpShell(tester, controller);
    await tester.tap(find.byIcon(AppIconography.files));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('project-hub-files')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('lib'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(AppIconography.settings));
    await tester.pumpAndSettle();
    final loads = api.paths.length;
    await tester.binding.handlePopRoute();
    await tester.pump();
    expect(find.text('Press back again to exit'), findsNothing);
    expect(_selectedDestination(tester), 'Conversations');
    expect(api.paths.length, loads);
  });

  testWidgets('switching destinations preserves Files search and folder', (
    tester,
  ) async {
    final api = _NestedFilesApi();
    final controller = await _controller(directory: '/srv/app')
      ..api = api;
    addTearDown(controller.dispose);
    await _pumpShell(tester, controller);
    await tester.tap(find.byIcon(AppIconography.files));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('project-hub-files')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('lib'));
    await tester.pumpAndSettle();
    final search = find.byKey(const ValueKey('files-search-field'));
    await tester.enterText(search, 'needle');
    await tester.pump(const Duration(milliseconds: 400));
    final loads = api.paths.length;
    await tester.tap(find.byIcon(AppIconography.settings));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(AppIconography.files));
    await tester.pumpAndSettle();
    expect(tester.widget<TextField>(search).controller!.text, 'needle');
    expect(api.paths.last, 'lib');
    expect(api.paths.length, loads);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Settings Back returns Chats before offering exit', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pumpShell(tester, controller);
    await tester.tap(find.byIcon(AppIconography.settings));
    await tester.pumpAndSettle();
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(_selectedDestination(tester), 'Conversations');
    expect(find.text('Press back again to exit'), findsNothing);
  });

  testWidgets('reduced motion switches destinations without animation', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pumpShell(tester, controller, disableAnimations: true);
    final before = _dockLens(tester);
    await tester.tap(find.byIcon(AppIconography.settings));
    await tester.pump();
    expect(_selectedDestination(tester), 'Settings');
    // One pump: the lens is already on Settings and Settings is fully shown
    // (no fade). Settings builds on first visit (lazy tabs, 5253e12c), so
    // its own loading bar may still run; the switch itself does not.
    final after = _dockLens(tester);
    expect(after.left, greaterThan(before.left));
    expect(
      find.ancestor(
        of: find.byType(SettingsScreen),
        matching: find.byWidgetPredicate(
          (widget) => widget is Opacity && widget.opacity < 1,
        ),
      ),
      findsNothing,
    );
    await tester.pumpAndSettle();
    expect(_dockLens(tester), after);
    expect(tester.takeException(), isNull);
  });

  testWidgets('switching destinations fades through: the tab being left '
      'clears before the chosen one shows, never both half-visible', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pumpShell(tester, controller);
    final lensAtRest = _dockLens(tester);

    double opacityOf(Type screen) => tester
        .widget<Opacity>(
          find
              .ancestor(of: find.byType(screen), matching: find.byType(Opacity))
              .first,
        )
        .opacity;

    await tester.tap(find.byIcon(AppIconography.settings));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 40));
    // The dock lens is on its way to Settings, not there yet.
    final lensMoving = _dockLens(tester);
    expect(opacityOf(SettingsScreen), 0);
    expect(opacityOf(ChatsHomeScreen), inExclusiveRange(0, 1));
    await tester.pump(const Duration(milliseconds: 100));
    expect(opacityOf(SettingsScreen), inExclusiveRange(0, 1));
    await tester.pumpAndSettle();
    expect(opacityOf(SettingsScreen), 1);
    final lensOnSettings = _dockLens(tester);
    expect(lensOnSettings.center.dx, greaterThan(lensAtRest.center.dx));
    expect(lensMoving, isNot(lensOnSettings));
    // The status dot is still while connected: nothing runs at rest.
    expect(tester.binding.transientCallbackCount, 0);
  });

  testWidgets(
    'frosted dock preserves last file reachability and yields to keyboard',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetViewInsets);
      final controller = await _controller(directory: '/srv/app')
        ..api = _LongFilesApi();
      addTearDown(controller.dispose);
      await _pumpShell(tester, controller);
      await tester.tap(find.byIcon(AppIconography.files));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('project-hub-files')));
      await tester.pumpAndSettle();
      expect(
        KitBottomInset.of(
          tester.element(find.byKey(const ValueKey('files-search-field'))),
        ).bottom,
        greaterThanOrEqualTo(tester.getSize(_dockGlass()).height),
      );
      final list = find.byType(ListView).first;
      await tester.drag(list, const Offset(0, -2200));
      await tester.pumpAndSettle();
      final last = find.byKey(const ValueKey('project-file-file-29.dart'));
      await tester.ensureVisible(last);
      await tester.pumpAndSettle();
      // End-of-list scrolling includes the dock inset, not merely the viewport.
      final scrollable = tester.state<ScrollableState>(
        find.descendant(of: list, matching: find.byType(Scrollable)).first,
      );
      scrollable.position.jumpTo(scrollable.position.maxScrollExtent);
      await tester.pump();
      expect(
        tester.getRect(last).bottom,
        lessThanOrEqualTo(tester.getRect(_dockGlass()).top),
      );
      tester.view.viewInsets = const FakeViewPadding(bottom: 300);
      await tester.pumpAndSettle();
      expect(_dockGlass(), findsNothing);
      expect(
        KitBottomInset.of(
          tester.element(find.byKey(const ValueKey('files-search-field'))),
        ).bottom,
        300,
      );
      final search = find.byKey(const ValueKey('files-search-field'));
      expect(tester.getRect(search).bottom, lessThanOrEqualTo(544));
      tester.view.resetViewInsets();
      await tester.pumpAndSettle();
      expect(_dockGlass(), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('phone shell uses product bottom navigation', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await _pumpShell(tester, controller);

    expect(find.byType(KitNavBar), findsOneWidget);
    expect(_dockGlass(), findsOneWidget);
    final dock = tester.getRect(_dockGlass());
    expect(dock.left, 16);
    expect(dock.right, 374);
    expect(dock.height, 60);
    final navigation = tester.widget<KitNavBar>(find.byType(KitNavBar));
    final roles = ThemeRoles.resolve(
      Theme.of(tester.element(find.byType(KitNavBar))),
    );
    for (final (label, glyph, color) in [
      ('Conversations', AppIconography.chat, roles.text1),
      ('Files', AppIconography.files, roles.text2),
      ('Settings', AppIconography.settings, roles.text2),
    ]) {
      final labelFinder = find.descendant(
        of: find.byType(KitNavBar),
        matching: find.text(label),
      );
      expect(tester.widget<Text>(labelFinder).style!.color, color);
      expect(
        tester
            .widget<Icon>(
              find.descendant(
                of: find.byType(KitNavBar),
                matching: find.byIcon(glyph),
              ),
            )
            .color,
        color,
      );
    }
    final icon = tester.getRect(
      find.descendant(
        of: find.byType(KitNavBar),
        matching: find.byIcon(AppIconography.chat),
      ),
    );
    expect(icon.top - dock.top, greaterThanOrEqualTo(8));
    final label = tester.getRect(
      find.descendant(
        of: find.byType(KitNavBar),
        matching: find.text('Conversations'),
      ),
    );
    expect(dock.bottom - label.bottom, greaterThanOrEqualTo(4));

    expect(find.byType(KitNavRail), findsNothing);
    // UX plan 5.1: one tab per noun, in this order.
    final navigationLabels = navigation.destinations
        .map((destination) => destination.label)
        .toList();
    expect(navigationLabels, ['Conversations', 'Files', 'Settings']);
    expect(find.text('Conversations'), findsWidgets);
    expect(find.text('Files'), findsWidgets);
    expect(find.text('Work'), findsNothing);
    expect(find.text('Inbox'), findsNothing);
    expect(find.text('Project'), findsNothing);
    expect(find.text('Workspace'), findsNothing);
    expect(find.text('Activity'), findsNothing);
    expect(find.text('Terminal'), findsNothing);
    expect(find.text('Settings'), findsOneWidget);
    expect(find.text('More'), findsNothing);
    expect(find.text('API'), findsNothing);
    expect(find.text('Guide'), findsNothing);
  });

  testWidgets('one pending badge, on the Chats destination', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller(profileName: 'Test server');
    addTearDown(controller.dispose);

    await _pumpShell(tester, controller);
    expect(
      find.descendant(of: _chatsDestination(), matching: find.text('1')),
      findsNothing,
    );

    controller.permissions = {
      'perm-1': PermissionRequest(
        id: 'perm-1',
        sessionID: 'session-1',
        permission: 'edit',
        patterns: const ['lib/main.dart'],
      ),
    };
    controller.notifyListeners();
    await tester.pump();

    // UX-P0-01: exactly one global badge, and the duplicate app-bar entry
    // points are gone.
    final badge = _chatsDestination();
    expect(badge, findsOneWidget);
    expect(
      find.descendant(of: find.byType(KitNav), matching: find.text('1')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: find.byType(KitNavBar), matching: badge),
      findsOneWidget,
    );
    expect(
      find.descendant(of: badge, matching: find.text('1')),
      findsOneWidget,
    );
    expect(tester.getSemantics(badge).label, 'Conversations, 1 need you');

    // The badge counts everything waiting on the person, not just "some".
    controller.permissions = {
      for (final id in ['perm-1', 'perm-2', 'perm-3'])
        id: PermissionRequest(
          id: id,
          sessionID: 'session-1',
          permission: 'edit',
          patterns: const ['lib/main.dart'],
        ),
    };
    controller.notifyListeners();
    await tester.pumpAndSettle();
    expect(
      find.descendant(of: badge, matching: find.text('3')),
      findsOneWidget,
    );
    controller.permissions = {'perm-1': controller.permissions['perm-1']!};
    controller.notifyListeners();
    await tester.pumpAndSettle();
    expect(
      find.descendant(of: badge, matching: find.text('1')),
      findsOneWidget,
    );
    expect(find.byTooltip('Mission Control'), findsNothing);
    expect(find.byTooltip('Pending requests'), findsNothing);
    // The model lives on the composer; the shell has no overflow menu left.
    expect(find.byTooltip('Model / agent'), findsNothing);
    expect(
      find.descendant(
        of: find.byType(AppBar),
        matching: find.byWidgetPredicate((widget) => widget is PopupMenuButton),
      ),
      findsNothing,
    );
  });

  group('cold start opens on Chats', () {
    PermissionRequest permission(String id) => PermissionRequest(
      id: id,
      sessionID: 'session-1',
      permission: 'edit',
      patterns: const ['lib/main.dart'],
    );

    String title(WidgetTester tester) => _selectedDestination(tester);

    Future<void> pump(
      WidgetTester tester,
      ConnectionController controller, {
      int? initialTab,
      ChatFeedFilter? initialChatFilter,
    }) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Builder(
              builder: (context) => ListenableBuilder(
                listenable: controller,
                builder: (context, _) => HomeScreen(
                  initialTab: initialTab,
                  initialChatFilter: initialChatFilter,
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
    }

    ChatFeedFilter? chatsFilter(WidgetTester tester) => tester
        .widget<ChatsHomeScreen>(find.byType(ChatsHomeScreen))
        .initialFilter;

    testWidgets('nothing waiting: Chats, no badge', (tester) async {
      final controller = await _controller(profileName: 'Test server');
      addTearDown(controller.dispose);
      await pump(tester, controller);
      expect(title(tester), 'Conversations');
      expect(
        find.descendant(of: _chatsDestination(), matching: find.text('1')),
        findsNothing,
      );
    });

    testWidgets('something waiting: still Chats, and the badge says so', (
      tester,
    ) async {
      final controller = await _controller(profileName: 'Test server');
      addTearDown(controller.dispose);
      controller.permissions = {'perm-1': permission('perm-1')};
      await pump(tester, controller);
      expect(title(tester), 'Conversations');
      expect(
        find.descendant(of: _chatsDestination(), matching: find.text('1')),
        findsOneWidget,
      );
      expect(chatsFilter(tester), isNull);
    });

    testWidgets('a request that arrives later only moves the badge', (
      tester,
    ) async {
      final controller = await _controller(profileName: 'Test server');
      addTearDown(controller.dispose);
      await pump(tester, controller);
      await tester.tap(find.byIcon(AppIconography.settings));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      controller.permissions = {'perm-1': permission('perm-1')};
      controller.notifyListeners();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      expect(title(tester), 'Settings');
      expect(
        find.descendant(of: _chatsDestination(), matching: find.text('1')),
        findsOneWidget,
      );
    });

    testWidgets('an explicit destination is respected', (tester) async {
      final controller = await _controller(profileName: 'Test server');
      addTearDown(controller.dispose);
      controller.permissions = {'perm-1': permission('perm-1')};
      await pump(tester, controller, initialTab: 2);
      expect(title(tester), 'Settings');
    });

    testWidgets('a filter asked for at launch reaches the Chats list', (
      tester,
    ) async {
      final controller = await _controller(profileName: 'Test server');
      addTearDown(controller.dispose);
      await pump(
        tester,
        controller,
        initialChatFilter: const ChatFeedFilter(needsYou: true),
      );
      expect(title(tester), 'Conversations');
      expect(chatsFilter(tester), const ChatFeedFilter(needsYou: true));
    });
  });

  group('everything that opened the Inbox opens Chats with its filter', () {
    Future<AppShortcutSignals> pumpWithSignals(
      WidgetTester tester,
      ConnectionController controller,
    ) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final signals = AppShortcutSignals();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: AppShortcutScope(
              signals: signals,
              child: _shellHome(controller),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return signals;
    }

    ChatFeedFilter? chatsFilter(WidgetTester tester) => tester
        .widget<ChatsHomeScreen>(find.byType(ChatsHomeScreen))
        .initialFilter;

    testWidgets('Needs you (notification, Quick Settings tile, widget)', (
      tester,
    ) async {
      final controller = await _controller(profileName: 'Test server');
      addTearDown(controller.dispose);
      final signals = await pumpWithSignals(tester, controller);
      await tester.tap(find.byIcon(AppIconography.settings));
      await tester.pumpAndSettle();
      expect(_selectedDestination(tester), 'Settings');

      expect(signals.dispatch(const OpenChatsIntent(needsYou: true)), isTrue);
      await tester.pumpAndSettle();
      expect(_selectedDestination(tester), 'Conversations');
      expect(chatsFilter(tester), const ChatFeedFilter(needsYou: true));
    });

    testWidgets('Running, then asking again re-opens on the new filter', (
      tester,
    ) async {
      final controller = await _controller(profileName: 'Test server');
      addTearDown(controller.dispose);
      final signals = await pumpWithSignals(tester, controller);
      expect(signals.dispatch(const OpenChatsIntent(running: true)), isTrue);
      await tester.pumpAndSettle();
      expect(chatsFilter(tester), const ChatFeedFilter(running: true));

      expect(signals.dispatch(const OpenChatsIntent(needsYou: true)), isTrue);
      await tester.pumpAndSettle();
      expect(chatsFilter(tester), const ChatFeedFilter(needsYou: true));

      expect(signals.dispatch(const OpenChatsIntent()), isTrue);
      await tester.pumpAndSettle();
      expect(chatsFilter(tester), isNull);
    });

    testWidgets('Ctrl+1..3 are Chats, Files, Settings; there is no 4', (
      tester,
    ) async {
      final controller = await _controller(directory: '/srv/app');
      addTearDown(controller.dispose);
      final signals = await pumpWithSignals(tester, controller);
      expect(signals.dispatch(const SelectDestinationIntent(1)), isTrue);
      await tester.pumpAndSettle();
      expect(_selectedDestination(tester), 'Files');
      expect(signals.dispatch(const SelectDestinationIntent(2)), isTrue);
      await tester.pumpAndSettle();
      expect(_selectedDestination(tester), 'Settings');
      expect(signals.dispatch(const SelectDestinationIntent(0)), isTrue);
      await tester.pumpAndSettle();
      expect(_selectedDestination(tester), 'Conversations');
      expect(signals.dispatch(const SelectDestinationIntent(3)), isFalse);
      expect(
        appShortcutBindings.values.whereType<SelectDestinationIntent>().map(
          (intent) => intent.index,
        ),
        everyElement(inInclusiveRange(0, 2)),
      );
    });
  });

  group('the Files tab is the project tools of one project', () {
    Future<void> openFiles(WidgetTester tester, ConnectionController c) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await _pumpShell(tester, c);
      await tester.tap(find.byIcon(AppIconography.files));
      await tester.pumpAndSettle();
    }

    testWidgets('titled Files, with a chip naming the project', (tester) async {
      final controller = await _controller(directory: '/srv/app');
      addTearDown(controller.dispose);
      await openFiles(tester, controller);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('project-hub-title')),
          matching: find.text('Files'),
        ),
        findsOneWidget,
      );
      final chip = find.byKey(const ValueKey('project-hub-context'));
      expect(chip, findsOneWidget);
      expect(find.bySemanticsLabel(RegExp('app')), findsWidgets);
      expect(find.byKey(const ValueKey('project-hub-files')), findsOneWidget);
    });

    testWidgets('the chip opens the project chooser to switch', (tester) async {
      final controller = await _controller(directory: '/srv/app');
      addTearDown(controller.dispose);
      await openFiles(tester, controller);
      await tester.tap(find.byKey(const ValueKey('project-hub-context')));
      await tester.pumpAndSettle();
      expect(find.byType(RemoteFolderSheet), findsOneWidget);
    });

    testWidgets('the project is the one last used for a chat', (tester) async {
      final controller = await _controller(
        profileName: 'Test server',
        directory: '/srv/app',
        seed: {'oc.lastProject.local': '/srv/app'},
      );
      addTearDown(controller.dispose);
      expect(lastUsedProjectOf(controller), '/srv/app');
      await openFiles(tester, controller);
      expect(find.bySemanticsLabel(RegExp('app')), findsWidgets);
      expect(controller.directory, '/srv/app');
    });

    testWidgets('a last-used temporary folder is not a project', (
      tester,
    ) async {
      final controller = await _controller(
        profileName: 'Test server',
        directory: '/srv/app',
        seed: {'oc.lastProject.local': '/tmp/scratch'},
      );
      addTearDown(controller.dispose);
      expect(lastUsedProjectOf(controller), isNull);
    });

    testWidgets('a temporary folder is never shown as the project', (
      tester,
    ) async {
      final controller = await _controller(directory: '/tmp/scratch');
      addTearDown(controller.dispose);
      await openFiles(tester, controller);
      expect(find.text('scratch'), findsNothing);
      expect(find.byKey(const ValueKey('project-hub-files')), findsNothing);
      expect(
        find.byKey(const ValueKey('project-hub-choose-project')),
        findsOneWidget,
      );
    });

    test('which folders are temporary', () {
      expect(isTemporaryProjectDirectory('/tmp/scratch'), isTrue);
      expect(isTemporaryProjectDirectory('/var/folders/ab/cd'), isTrue);
      expect(isTemporaryProjectDirectory('/root/projects/app'), isFalse);
    });
  });

  testWidgets('failed reconnect keeps the product shell and location visible', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller(profileName: 'This device (Termux)')
      ..api = null
      ..repository = null
      ..status = StreamStatus.disconnected
      ..lastError = 'Endpoint is unavailable';
    addTearDown(controller.dispose);

    await _pumpShell(tester, controller);

    expect(find.byType(HomeScreen), findsOneWidget);
    expect(find.byType(KitNav), findsOneWidget);
    // A completed failure occupies the one shared status slot immediately.
    final line = find.byKey(const ValueKey('connection-status-banner'));
    expect(line, findsOneWidget);
    expect(
      find.descendant(
        of: line,
        matching: find.text("This device (Termux) isn't answering"),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: line,
        matching: find.text('Reconnect to This device (Termux)'),
      ),
      findsOneWidget,
    );
    // The raw error and the secondary action live behind Details.
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.tap(find.byKey(const ValueKey('connection-banner-details')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    // The raw error waits folded under the sheet's own Details.
    expect(find.textContaining('Endpoint is unavailable'), findsNothing);
    final fold = find.descendant(
      of: find.byKey(const ValueKey('connection-banner-details-sheet')),
      matching: find.byKey(const ValueKey('kit-details-toggle')),
    );
    await tester.ensureVisible(fold);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.tap(fold);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.textContaining('Endpoint is unavailable'), findsOneWidget);
    expect(find.text('Switch server'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    // Stop the controller-owned fallback poll before widget-test invariants.
    controller.dispose();
  });

  testWidgets('recovery banner fits a 320dp phone with 2x text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller(profileName: 'This device (Termux)')
      ..api = null
      ..repository = null
      ..status = StreamStatus.disconnected
      ..lastError = 'Endpoint is unavailable';
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(2)),
              child: _shellHome(controller),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final line = find.byKey(const ValueKey('connection-status-banner'));
    expect(
      find.descendant(
        of: line,
        matching: find.text('Reconnect to This device (Termux)'),
      ),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('kit-status-more')), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    // Stop the controller-owned fallback poll before widget-test invariants.
    controller.dispose();
  });

  testWidgets('automatic SSE reconnect still offers a manual retry', (
    tester,
  ) async {
    final controller = await _controller(profileName: 'This device (Termux)')
      ..status = StreamStatus.reconnecting;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: _shellHome(controller),
        ),
      ),
    );
    await tester.pump();
    // The server pill reports reconnection at once, and alone: no second
    // line saying the same thing under it (one indicator). After the
    // controller's 15-second quiet window the shared line appears with
    // its way forward.
    final line = find.byKey(const ValueKey('connection-status-banner'));
    expect(line, findsNothing);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('server-switcher-button')),
        matching: find.textContaining('Reconnecting'),
      ),
      findsOneWidget,
    );
    expect(find.text('Reconnecting to This device (Termux)…'), findsNothing);
    expect(find.text("This device (Termux) isn't answering"), findsNothing);
    await tester.pump(const Duration(seconds: 16));
    await tester.pump(const Duration(milliseconds: 300));
    expect(line, findsOneWidget);
    expect(find.text("This device (Termux) isn't answering"), findsOneWidget);

    final retry = tester.widget<TextButton>(
      find.widgetWithText(TextButton, 'Reconnect to This device (Termux)'),
    );
    expect(retry.onPressed, isNotNull);
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.tap(find.byKey(const ValueKey('connection-banner-details')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Switch server'), findsOneWidget);
  });

  testWidgets(
    'phone header names the server and keeps the current tab in the dock',
    (tester) async {
      tester.view.physicalSize = const Size(411, 891);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller(profileName: 'This device (Termux)');
      addTearDown(controller.dispose);

      await _pumpShell(tester, controller);

      final profile = find.byKey(const ValueKey('server-switcher-button'));
      final tab = find.byKey(const ValueKey('home-shell-tab-chats'));
      expect(profile, findsOneWidget);
      expect(tab, findsOneWidget);
      expect(tester.getRect(profile).bottom, lessThan(tester.getRect(tab).top));
      expect(
        tester.getSemantics(profile).label,
        contains('This device (Termux)'),
      );
      expect(
        tester.getSemantics(profile).label,
        contains('Connected, Switch server'),
      );
      expect(_selectedDestination(tester), 'Conversations');
      expect(find.byKey(const ValueKey('current-tab-title')), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'expanded tablet shell puts navigation and server controls in the sidebar',
    (tester) async {
      tester.view.physicalSize = const Size(1024, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller();
      addTearDown(controller.dispose);

      await _pumpShell(tester, controller);

      expect(find.byType(KitNavRail), findsOneWidget);
      expect(
        tester.widget<KitNavRail>(find.byType(KitNavRail)).extended,
        isTrue,
      );
      expect(find.byType(KitNavBar), findsNothing);
      final profile = find.byKey(const ValueKey('server-switcher-button'));
      expect(
        find.descendant(of: find.byType(KitNavRail), matching: profile),
        findsOneWidget,
      );
      final tab = find.byKey(const ValueKey('home-shell-tab-chats'));
      expect(tester.getRect(profile).bottom, lessThan(tester.getRect(tab).top));
      expect(_selectedDestination(tester), 'Conversations');
      expect(find.byKey(const ValueKey('current-tab-title')), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );
}
