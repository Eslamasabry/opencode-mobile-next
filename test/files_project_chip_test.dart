import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/navigation/last_project.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/project_folder_actions.dart';
import 'package:opencode_mobile/ui/widgets/folder_browser.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The Files tab's project chip opens the same "Open a project" sheet the
/// New conversation chip uses, and a project chosen there that differs from
/// the current location moves the location (`selectLocation`) and becomes
/// the last used project.

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [];
}

class _Repository implements ProductRepository {
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

class _Store extends ProfileStore {
  _Store({required super.prefs});

  final profile = ServerProfile(
    id: 'builtin',
    name: 'This phone',
    baseUrl: BuiltinLinux.serverUrl,
    username: BuiltinLinux.serverUsername,
  );

  @override
  List<ServerProfile> get profiles => [profile];

  @override
  String? get activeId => profile.id;
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  final selected = <String?>[];

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    selected.add(directory);
    this.directory = directory;
    locationError = null;
    notifyListeners();
  }
}

class _Linux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async =>
      const BuiltinLinuxStatus(installed: true, phase: BuiltinLinuxPhase.ready);
}

const _tree = <String, List<(String, bool)>>{
  '/': [('root', true)],
  '/root': [('projects', false)],
  '/root/projects': [('demo', true), ('notes', false)],
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late _Controller controller;

  setUp(() async {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    SharedPreferences.setMockInitialValues({});
    controller =
        _Controller(_Store(prefs: await SharedPreferences.getInstance()))
          ..api = _Api()
          ..repository = _Repository()
          ..status = StreamStatus.connected
          ..directory = '/root/projects/notes';
    ProjectFolderActions.builtinLinuxOverride = _Linux();
    ProjectFolderActions.folderListerOverride = (path) async {
      final entries = _tree[path];
      if (entries == null) {
        throw FolderListException(FolderListProblem.missing, path);
      }
      return [
        for (final (name, git) in entries)
          FolderEntry(
            name: name,
            path: path == '/' ? '/$name' : '$path/$name',
            isGit: git,
          ),
      ];
    };
  });

  tearDown(() {
    ProjectFolderActions.builtinLinuxOverride = null;
    ProjectFolderActions.folderListerOverride = null;
    debugPlatformCapabilities = null;
    controller.dispose();
  });

  Future<void> openFiles(WidgetTester tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: HomeScreen(initialTab: 1),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('the chip opens the Open a project sheet, not a page', (
    tester,
  ) async {
    await openFiles(tester);
    await tester.tap(find.byKey(const ValueKey('project-hub-context')));
    await tester.pumpAndSettle();
    expect(find.byType(FolderBrowserSheet), findsOneWidget);
    expect(find.text('Open a project'), findsOneWidget);
    expect(find.text('New project'), findsOneWidget);
    expect(find.text('Search this phone'), findsOneWidget);
  });

  testWidgets('a project that differs from the location moves it, and the '
      'chip and last used follow', (tester) async {
    await openFiles(tester);
    expect(controller.directory, '/root/projects/notes');
    await tester.tap(find.byKey(const ValueKey('project-hub-context')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('open-project-browse')));
    await tester.pumpAndSettle();
    final demo = find.byKey(const ValueKey('in-app-project-demo'));
    await tester.ensureVisible(demo);
    await tester.pumpAndSettle();
    await tester.tap(demo);
    await tester.pumpAndSettle();

    expect(controller.selected, ['/root/projects/demo']);
    expect(controller.directory, '/root/projects/demo');
    expect(lastUsedProjectOf(controller), '/root/projects/demo');
    expect(find.byType(FolderBrowserSheet), findsNothing);
    expect(find.bySemanticsLabel(RegExp('demo')), findsWidgets);
  });

  testWidgets('closing the sheet changes nothing', (tester) async {
    await openFiles(tester);
    await tester.tap(find.byKey(const ValueKey('project-hub-context')));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(controller.selected, isEmpty);
    expect(controller.directory, '/root/projects/notes');
  });
}
