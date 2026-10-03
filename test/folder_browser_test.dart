// Open a project for OpenCode inside the app: browse Ubuntu's folders from
// the projects folder (lib/ui/widgets/folder_browser.dart), through
// ProjectFolderActions.openFolder, whose result the callers keep using.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/workspace_paths.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_sheet.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/screens/project_folder_actions.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

class _Projects implements ProductRepository {
  List<WorkspaceProject> projects = const [];

  @override
  Future<List<WorkspaceProject>> listProjects() async => projects;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store, _Projects projects) {
    repository = projects;
  }

  final opened = <String?>[];

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    opened.add(directory);
    locationError = null;
  }
}

/// Ubuntu installed; the server is stopped. Only making a folder runs
/// anything in it.
class _Linux extends BuiltinLinux {
  final created = <String>[];

  @override
  Future<BuiltinLinuxStatus> status() async =>
      const BuiltinLinuxStatus(installed: true, phase: BuiltinLinuxPhase.ready);

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    final path = RegExp(r"dir='([^']*)'").firstMatch(script)?[1];
    if (path == null) {
      return const BuiltinLinuxRunResult(exitCode: 127, output: 'unexpected');
    }
    created.add(path);
    return BuiltinLinuxRunResult(exitCode: 0, output: 'created $path\n');
  }
}

/// Ubuntu's folders: path -> (name, git) inside it.
const _tree = <String, List<(String, bool)>>{
  '/': [('opt', false), ('root', true)],
  '/root': [('projects', false)],
  '/root/projects': [('demo', true), ('notes', false), ('work', false)],
  '/root/projects/work': [('api', true), ('docs', false)],
  '/root/projects/work/docs': [],
};

void main() {
  late _Linux linux;
  late _Projects projects;
  late _Controller controller;
  late List<String> listed;
  Object? failNext;
  String? result;

  setUp(() async {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    SharedPreferences.setMockInitialValues({});
    linux = _Linux();
    projects = _Projects();
    controller = _Controller(
      _Store(prefs: await SharedPreferences.getInstance()),
      projects,
    );
    listed = [];
    failNext = null;
    result = null;
    ProjectFolderActions.builtinLinuxOverride = linux;
    ProjectFolderActions.folderListerOverride = (path) async {
      listed.add(path);
      if (failNext case final error?) {
        failNext = null;
        throw error;
      }
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

  Future<void> openSheet(
    WidgetTester tester, {
    Locale locale = const Locale('en'),
    double textScale = 1,
    bool browse = true,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        locale: locale,
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () async => result =
                  await ProjectFolderActions.openFolder(context, controller),
              child: const Text('go'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('go'));
    await tester.pumpAndSettle();
    // The sheet opens on its start page; most of these tests browse.
    if (browse) {
      await tester.tap(find.byKey(const ValueKey('open-project-browse')));
      await tester.pumpAndSettle();
    }
  }

  // The header's title is the folder shown (the browser is kit-only).
  String shownTitle(WidgetTester tester) => tester
      .widget<KitText>(
        find.byWidgetPredicate(
          (widget) => widget is KitText && widget.role == KitTextRole.title,
        ),
      )
      .text;

  Future<void> openMenu(WidgetTester tester) async {
    await tester.tap(find.byKey(const ValueKey('kit-sheet-menu')));
    await tester.pumpAndSettle();
  }

  Future<void> tapKey(WidgetTester tester, String key) async {
    // Controls below the folders sit in the sheet's scrolling body.
    await tester.ensureVisible(find.byKey(ValueKey(key)));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(ValueKey(key)));
    await tester.pumpAndSettle();
  }

  testWidgets('starts at the projects folder, goes into a folder and up', (
    tester,
  ) async {
    await openSheet(tester);
    expect(shownTitle(tester), 'projects');
    // At the first folder the header offers Back to the start page, not a
    // way up.
    expect(find.byTooltip('Back'), findsOneWidget);
    expect(find.byKey(const ValueKey('folder-browser-up')), findsNothing);
    expect(find.text('demo'), findsOneWidget);
    expect(find.text('work'), findsOneWidget);

    // A project folder opens on a tap; its chevron shows what is inside.
    await tapKey(tester, 'folder-browse-work');
    expect(shownTitle(tester), 'work');
    expect(find.byTooltip('Up one folder'), findsOneWidget);
    expect(find.byTooltip('Back'), findsNothing);
    expect(find.text('api'), findsOneWidget);
    expect(find.text('demo'), findsNothing);

    // A plain folder deeper down is gone into with a tap.
    await tapKey(tester, 'in-app-project-docs');
    expect(shownTitle(tester), 'docs');
    expect(find.text('No folders in here'), findsOneWidget);

    await tapKey(tester, 'folder-browser-up');
    expect(shownTitle(tester), 'work');
    await tapKey(tester, 'folder-browser-up');
    expect(shownTitle(tester), 'projects');
    expect(find.text('demo'), findsOneWidget);
    expect(controller.opened, isEmpty);
    expect(linux.created, isEmpty);
  });

  testWidgets('git repositories and known OpenCode projects are marked', (
    tester,
  ) async {
    projects.projects = const [
      WorkspaceProject(
        id: 'p',
        name: 'notes',
        directory: '/root/projects/notes/',
        worktrees: [],
        updatedAt: 1,
      ),
    ];
    await openSheet(tester);
    Finder supporting(String key) => find.descendant(
      of: find.byKey(ValueKey('in-app-project-$key')),
      matching: find.byType(RichText),
    );
    String texts(String key) => tester
        .widgetList<RichText>(supporting(key))
        .map((text) => text.text.toPlainText())
        .join(' | ');
    expect(texts('demo'), contains('Git repository'));
    expect(texts('notes'), contains('OpenCode project'));
    expect(texts('work'), isNot(contains('Git repository')));
    expect(texts('work'), isNot(contains('OpenCode project')));
  });

  testWidgets('a project opens with a tap and returns its path', (
    tester,
  ) async {
    await openSheet(tester);
    await tapKey(tester, 'in-app-project-demo');
    expect(result, '/root/projects/demo');
    expect(controller.opened, ['/root/projects/demo']);
  });

  testWidgets('Open opens the folder shown, returning its path', (
    tester,
  ) async {
    await openSheet(tester);
    // The projects folder itself is not a project.
    expect(find.byKey(const ValueKey('folder-browser-open')), findsNothing);
    expect(find.textContaining('Your projects live here'), findsOneWidget);
    await tapKey(tester, 'folder-browse-work');
    expect(find.text('Open work'), findsOneWidget);
    await tapKey(tester, 'folder-browser-open');
    expect(result, '/root/projects/work');
    expect(controller.opened, ['/root/projects/work']);
  });

  testWidgets('the sheet opens on three plain options', (tester) async {
    await openSheet(tester, browse: false);
    expect(shownTitle(tester), 'Open a project');
    // No folder list, place line or menu on this page.
    expect(find.text('demo'), findsNothing);
    expect(find.text('Project space'), findsNothing);
    expect(find.byKey(const ValueKey('kit-sheet-menu')), findsNothing);
    expect(find.byTooltip('Close'), findsOneWidget);
    expect(find.text('New project'), findsOneWidget);
    expect(find.text('Choose a folder'), findsOneWidget);
    expect(find.text('Search this phone'), findsOneWidget);
    // Nothing opened before: no section.
    expect(find.text('Opened before'), findsNothing);
  });

  testWidgets('a new project is named in place and made in the project '
      'space', (tester) async {
    await openSheet(tester, browse: false);
    await tapKey(tester, 'open-project-new');
    // No second sheet or dialog: the one sheet's content became the step.
    expect(find.byKey(const ValueKey('in-app-projects')), findsOneWidget);
    expect(find.byType(KitSheet), findsOneWidget);
    expect(find.byType(BottomSheet), findsOneWidget);
    expect(find.byType(AlertDialog), findsNothing);
    expect(shownTitle(tester), 'New project');
    expect(find.text('Change'), findsOneWidget);
    expect(find.text('Create and open'), findsOneWidget);
    expect(find.text('In \u2066/root/projects\u2069'), findsOneWidget);
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'cli',
    );
    await tester.pump();
    expect(find.text('Creates \u2066/root/projects/cli\u2069'), findsOneWidget);
    await tapKey(tester, 'phone-new-folder-create');
    expect(linux.created, ['/root/projects/cli']);
    expect(result, '/root/projects/cli');
    expect(controller.opened, ['/root/projects/cli']);
  });

  testWidgets('Change folder picks another parent and comes back', (
    tester,
  ) async {
    await openSheet(tester, browse: false);
    await tapKey(tester, 'open-project-new');
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'cli',
    );
    await tapKey(tester, 'phone-new-folder-change');
    // The browser starts at the folder the project would go in; it does
    // not offer to open anything, only to use a folder.
    expect(shownTitle(tester), 'projects');
    expect(find.byKey(const ValueKey('folder-browser-open')), findsNothing);
    await tapKey(tester, 'in-app-project-work');
    expect(find.byKey(const ValueKey('folder-browser-open')), findsNothing);
    expect(find.text('Use work'), findsOneWidget);
    await tapKey(tester, 'folder-browser-use');
    expect(shownTitle(tester), 'New project');
    expect(
      find.text('Creates \u2066/root/projects/work/cli\u2069'),
      findsOneWidget,
    );
    await tapKey(tester, 'phone-new-folder-create');
    expect(linux.created, ['/root/projects/work/cli']);
    expect(result, '/root/projects/work/cli');
  });

  testWidgets('Back from the folder picker returns to the name', (
    tester,
  ) async {
    await openSheet(tester, browse: false);
    await tapKey(tester, 'open-project-new');
    await tapKey(tester, 'phone-new-folder-change');
    await tapKey(tester, 'folder-browser-start');
    expect(shownTitle(tester), 'New project');
    expect(find.byKey(const ValueKey('phone-new-folder-name')), findsOneWidget);
  });

  testWidgets('Back returns from New project to the start page', (
    tester,
  ) async {
    await openSheet(tester, browse: false);
    await tapKey(tester, 'open-project-new');
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'x',
    );
    expect(find.byTooltip('Back'), findsOneWidget);
    await tapKey(tester, 'phone-new-folder-cancel');
    expect(find.byKey(const ValueKey('phone-new-folder-name')), findsNothing);
    expect(shownTitle(tester), 'Open a project');
    expect(find.text('New project'), findsOneWidget);
    expect(linux.created, isEmpty);
    expect(result, isNull);
  });

  testWidgets('a bad name is explained under the field and clears when '
      'fixed', (tester) async {
    await openSheet(tester, browse: false);
    await tapKey(tester, 'open-project-new');
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'a/b',
    );
    await tapKey(tester, 'phone-new-folder-create');
    expect(linux.created, isEmpty);
    expect(find.byKey(const ValueKey('phone-new-folder-name')), findsOneWidget);
    final problem = projectFolderNameProblem('a/b')!;
    expect(find.text(problem), findsOneWidget);
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'ab',
    );
    await tester.pumpAndSettle();
    expect(find.text(problem), findsNothing);
  });

  testWidgets('the name field stays above the keyboard', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await openSheet(tester, browse: false);
    await tapKey(tester, 'open-project-new');
    tester.view.viewInsets = FakeViewPadding(
      bottom: 300 * tester.view.devicePixelRatio,
    );
    addTearDown(tester.view.resetViewInsets);
    await tester.pumpAndSettle();
    final field = tester.getRect(
      find.byKey(const ValueKey('phone-new-folder-name')),
    );
    final create = tester.getRect(
      find.byKey(const ValueKey('phone-new-folder-create')),
    );
    final top = tester.view.physicalSize.height / tester.view.devicePixelRatio;
    expect(create.bottom, lessThanOrEqualTo(top - 300));
    expect(field.bottom, lessThanOrEqualTo(create.top));
  });

  testWidgets('the header is one row: Back, the folder, a menu', (
    tester,
  ) async {
    await openSheet(tester);
    // The old blocks and rows are gone.
    expect(find.text('Place'), findsNothing);
    expect(find.text('Folders'), findsNothing);
    expect(find.text('Up one folder'), findsNothing);
    expect(find.text('Show hidden folders'), findsNothing);
    // New project lives on the start page, not here.
    expect(find.text('New project'), findsNothing);
    // The place is one quiet line under the title; hidden folders are a
    // phone-storage setting, so the project space's menu leaves it out.
    expect(find.text('Project space'), findsOneWidget);
    await openMenu(tester);
    expect(find.text('Enter a path'), findsOneWidget);
    expect(find.text('Show hidden folders'), findsNothing);
  });

  testWidgets('a folder that cannot be listed says why and can be retried', (
    tester,
  ) async {
    failNext = const FolderListException(
      FolderListProblem.denied,
      'Permission denied',
    );
    await openSheet(tester);
    expect(find.text('This folder can’t be shown'), findsOneWidget);
    expect(find.text('The app isn’t allowed to read it.'), findsOneWidget);
    expect(find.byKey(const ValueKey('folder-browser-open')), findsNothing);
    await tapKey(tester, 'folder-browser-retry');
    expect(find.text('demo'), findsOneWidget);
    expect(listed, ['/root/projects', '/root/projects']);
  });

  testWidgets('Enter a path starts from the folder shown', (tester) async {
    await openSheet(tester);
    await tapKey(tester, 'folder-browse-work');
    await openMenu(tester);
    await tester.tap(find.text('Enter a path'));
    await tester.pumpAndSettle();
    final field = tester.widget<EditableText>(
      find.descendant(
        of: find.byKey(const ValueKey('open-folder-path')),
        matching: find.byType(EditableText),
      ),
    );
    expect(field.controller.text, '/root/projects/work/');
  });

  testWidgets('fits 320 dp at twice the text size, in Arabic, paths left to '
      'right', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await openSheet(tester, locale: const Locale('ar'), textScale: 2);
    expect(tester.takeException(), isNull);
    expect(shownTitle(tester), 'projects');
    // The folders sit in the sheet's scrolling body; its actions stay
    // pinned below it.
    await tester.ensureVisible(
      find.byKey(const ValueKey('folder-browse-work')),
    );
    await tester.pumpAndSettle();
    await tapKey(tester, 'folder-browse-work');
    expect(tester.takeException(), isNull);
    expect(shownTitle(tester), 'work');
    expect(find.text('فتح work'), findsOneWidget);
  });
}
