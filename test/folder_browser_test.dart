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
  }

  // The folder shown is a mono KitText (the browser is kit-only).
  String shownPath(WidgetTester tester) => tester
      .widget<KitText>(find.byKey(const ValueKey('folder-browser-path')))
      .text;

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
    expect(shownPath(tester), '/root/projects');
    expect(find.text('demo'), findsOneWidget);
    expect(find.text('work'), findsOneWidget);

    // A project folder opens on a tap; its chevron shows what is inside.
    await tapKey(tester, 'folder-browse-work');
    expect(shownPath(tester), '/root/projects/work');
    expect(find.text('api'), findsOneWidget);
    expect(find.text('demo'), findsNothing);

    // A plain folder deeper down is gone into with a tap.
    await tapKey(tester, 'in-app-project-docs');
    expect(shownPath(tester), '/root/projects/work/docs');
    expect(find.text('No folders in here'), findsOneWidget);

    await tapKey(tester, 'folder-browser-up');
    await tapKey(tester, 'folder-browser-up');
    expect(shownPath(tester), '/root/projects');
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

  testWidgets('a new project is named in place and made in the folder '
      'shown', (tester) async {
    await openSheet(tester);
    await tapKey(tester, 'folder-browse-work');
    await tapKey(tester, 'phone-new-folder');
    // No second sheet or dialog: the one sheet's actions became the field.
    expect(find.byKey(const ValueKey('in-app-projects')), findsOneWidget);
    expect(find.byType(KitSheet), findsOneWidget);
    expect(find.byKey(const ValueKey('phone-new-folder')), findsNothing);
    expect(find.byKey(const ValueKey('folder-browser-open')), findsNothing);
    expect(find.text('Create and open'), findsOneWidget);
    expect(
      find.text('Creates \u2066/root/projects/work/\u2026\u2069'),
      findsOneWidget,
    );
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'cli',
    );
    await tester.pump();
    expect(
      find.text('Creates \u2066/root/projects/work/cli\u2069'),
      findsOneWidget,
    );
    await tapKey(tester, 'phone-new-folder-create');
    expect(linux.created, ['/root/projects/work/cli']);
    expect(result, '/root/projects/work/cli');
    expect(controller.opened, ['/root/projects/work/cli']);
  });

  testWidgets('Cancel returns to Open and New project here', (tester) async {
    await openSheet(tester);
    await tapKey(tester, 'folder-browse-work');
    await tapKey(tester, 'phone-new-folder');
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'x',
    );
    await tapKey(tester, 'phone-new-folder-cancel');
    expect(find.byKey(const ValueKey('phone-new-folder-name')), findsNothing);
    expect(find.byKey(const ValueKey('phone-new-folder')), findsOneWidget);
    expect(find.byKey(const ValueKey('folder-browser-open')), findsOneWidget);
    expect(linux.created, isEmpty);
    expect(result, isNull);
  });

  testWidgets('a bad name is explained under the field and clears when '
      'fixed', (tester) async {
    await openSheet(tester);
    await tapKey(tester, 'folder-browse-work');
    await tapKey(tester, 'phone-new-folder');
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
    await openSheet(tester);
    await tapKey(tester, 'folder-browse-work');
    await tapKey(tester, 'phone-new-folder');
    tester.view.viewInsets = FakeViewPadding(
      bottom: 300 * tester.view.devicePixelRatio,
    );
    addTearDown(tester.view.resetViewInsets);
    await tester.pumpAndSettle();
    final field = tester.getRect(
      find.byKey(const ValueKey('phone-new-folder-name')),
    );
    final cancel = tester.getRect(
      find.byKey(const ValueKey('phone-new-folder-cancel')),
    );
    final create = tester.getRect(
      find.byKey(const ValueKey('phone-new-folder-create')),
    );
    final top = tester.view.physicalSize.height / tester.view.devicePixelRatio;
    expect(cancel.bottom, lessThanOrEqualTo(top - 300));
    expect(create.bottom, lessThanOrEqualTo(cancel.top));
    expect(field.bottom, lessThanOrEqualTo(create.top));
  });

  testWidgets('the sheet has two blocks: Place and Folders', (tester) async {
    await openSheet(tester);
    expect(find.text('Place'), findsOneWidget);
    expect(find.text('Folders'), findsOneWidget);
    expect(find.text('New project'), findsNothing);
    expect(find.byKey(const ValueKey('in-app-new-project-name')), findsNothing);
  });

  testWidgets('the home folder and / are never offered as a project', (
    tester,
  ) async {
    await openSheet(tester);
    await tapKey(tester, 'folder-browser-up');
    expect(shownPath(tester), '/root');
    expect(find.byKey(const ValueKey('folder-browser-open')), findsNothing);
    expect(find.textContaining('home folder and the root'), findsOneWidget);
    await tapKey(tester, 'folder-browser-up');
    expect(shownPath(tester), '/');
    expect(find.byKey(const ValueKey('folder-browser-up')), findsNothing);
    expect(find.byKey(const ValueKey('folder-browser-open')), findsNothing);
    // /root is a git repository here (a stray one), and still only browses.
    await tapKey(tester, 'in-app-project-root');
    expect(controller.opened, isEmpty, reason: 'the home folder never opens');
    expect(shownPath(tester), '/root');
    // A new project named after a home folder is refused too.
    await tapKey(tester, 'folder-browser-up');
    await tapKey(tester, 'phone-new-folder');
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'root',
    );
    await tapKey(tester, 'phone-new-folder-create');
    expect(find.textContaining('home folder'), findsWidgets);
    expect(controller.opened, isEmpty);
    expect(result, isNull);
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
    await tapKey(tester, 'in-app-enter-path');
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
    // Paths are laid out left to right even in Arabic.
    final path = tester.widget<RichText>(
      find
          .descendant(
            of: find.byKey(const ValueKey('folder-browser-path')),
            matching: find.byType(RichText),
          )
          .first,
    );
    expect(path.textDirection, TextDirection.ltr);
    // The folders sit in the sheet's scrolling body; its actions stay
    // pinned below it.
    await tester.ensureVisible(
      find.byKey(const ValueKey('folder-browse-work')),
    );
    await tester.pumpAndSettle();
    await tapKey(tester, 'folder-browse-work');
    expect(tester.takeException(), isNull);
    expect(shownPath(tester), '/root/projects/work');
    expect(find.text('فتح work'), findsOneWidget);
  });
}
