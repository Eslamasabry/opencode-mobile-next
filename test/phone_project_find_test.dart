// The Open a project sheet's start page (New project, Search this phone,
// Opened before, Choose a folder) and the search step it opens
// (lib/ui/widgets/folder_browser.dart).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/phone_project_scan.dart';
import 'package:opencode_mobile/platform/phone_storage_folders.dart';
import 'package:opencode_mobile/ui/widgets/folder_browser.dart';

const _root = PhoneStorageFolders.root;

PhoneProject _project(String name, {bool git = false}) => PhoneProject(
  name: name,
  path: '$_root/Documents/$name',
  kind: PhoneProjectKind.node,
  hasGit: git,
);

void main() {
  late List<PhoneProjectScan> scans;
  late List<Duration> limits;
  late int asked;
  late bool grant;
  FolderBrowserChoice? choice;

  Future<void> open(
    WidgetTester tester, {
    bool search = true,
    List<String> recent = const [],
    bool projectSpace = true,
  }) async {
    tester.view
      ..devicePixelRatio = 1
      ..physicalSize = const Size(412, 915);
    addTearDown(tester.view.reset);
    scans = [];
    limits = [];
    asked = 0;
    choice = null;
    await tester.pumpWidget(
      MaterialApp(
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () async =>
                  choice = await showModalBottomSheet<FolderBrowserChoice>(
                    context: context,
                    isScrollControlled: true,
                    builder: (_) => FolderBrowserSheet(
                      projectSpace: projectSpace,
                      list: (path) async => [
                        const FolderEntry(
                          name: 'demo',
                          path: '/root/projects/demo',
                          isGit: true,
                        ),
                        const FolderEntry(
                          name: 'notes',
                          path: '/root/projects/notes',
                        ),
                      ],
                      recent: recent.isEmpty ? null : () async => recent,
                      phone: search
                          ? PhoneStoragePlace(
                              list: (_) async => [
                                const FolderEntry(
                                  name: 'Documents',
                                  path: '$_root/Documents',
                                ),
                              ],
                              ensureAccess: (_) async {
                                asked++;
                                return grant;
                              },
                              scan: (limit) {
                                limits.add(limit);
                                final scan = PhoneProjectScan.manual();
                                scans.add(scan);
                                return scan;
                              },
                            )
                          : null,
                    ),
                  ),
              child: const Text('go'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('go'));
    await tester.pumpAndSettle();
  }

  setUp(() => grant = true);

  Future<void> tapKey(WidgetTester tester, String key) async {
    await tester.tap(find.byKey(ValueKey(key)));
    await tester.pumpAndSettle();
  }

  // The quiet progress line keeps animating while it looks.
  Future<void> startSearch(WidgetTester tester) async {
    await tester.tap(find.byKey(const ValueKey('open-project-search')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
  }

  // Leaves the sheet so the search's timer is cancelled.
  Future<void> leave(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
  }

  testWidgets('the start page shows New project, Search, then Opened before', (
    tester,
  ) async {
    await open(tester, recent: ['/root/projects/demo']);
    expect(find.text('Open a project'), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-sheet-menu')), findsNothing);
    final newProject = tester.getTopLeft(find.text('New project')).dy;
    final search = tester.getTopLeft(find.text('Search this phone')).dy;
    final before = tester.getTopLeft(find.text('Opened before')).dy;
    final recent = tester.getTopLeft(find.text('demo')).dy;
    final choose = tester.getTopLeft(find.text('Choose a folder')).dy;
    expect(newProject, lessThan(search));
    expect(search, lessThan(before));
    expect(before, lessThan(recent));
    expect(recent, lessThan(choose));
    // The folder list is not on this page.
    expect(find.text('notes'), findsNothing);
  });

  testWidgets('no recent folders, no section', (tester) async {
    await open(tester);
    expect(find.text('Opened before'), findsNothing);
    expect(find.text('Choose a folder'), findsOneWidget);
  });

  testWidgets('the Git badge shows only on git folders', (tester) async {
    await open(tester, recent: ['/root/projects/demo', '/root/projects/notes']);
    expect(find.text('demo'), findsOneWidget);
    expect(find.text('notes'), findsOneWidget);
    expect(find.text('Git'), findsOneWidget);
    // It sits on demo's row, not notes'.
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('open-project-recent-0')),
        matching: find.text('Git'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('open-project-recent-1')),
        matching: find.text('Git'),
      ),
      findsNothing,
    );
  });

  testWidgets('a tap on a recent folder opens it', (tester) async {
    await open(tester, recent: ['/root/projects/demo']);
    await tester.tap(find.text('demo'));
    await tester.pumpAndSettle();
    expect(choice, isA<FolderBrowserOpen>());
    expect((choice! as FolderBrowserOpen).path, '/root/projects/demo');
  });

  testWidgets('a server that cannot list folders has no Search row', (
    tester,
  ) async {
    await open(tester, search: false);
    expect(find.text('New project'), findsOneWidget);
    expect(find.text('Search this phone'), findsNothing);
  });

  testWidgets('without a project space a new project goes in Projects on the '
      'phone', (tester) async {
    await open(tester, projectSpace: false);
    await tapKey(tester, 'open-project-new');
    expect(
      find.text('Creates \u2066$_root/Projects/\u2026\u2069'),
      findsOneWidget,
    );
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'fresh',
    );
    await tapKey(tester, 'phone-new-folder-create');
    expect(choice, isA<FolderBrowserCreate>());
    expect((choice! as FolderBrowserCreate).path, '$_root/Projects/fresh');
  });

  testWidgets('search: consent first, and a refusal says so', (tester) async {
    grant = false;
    await open(tester);
    await tapKey(tester, 'open-project-search');
    expect(asked, 1);
    expect(scans, isEmpty);
    expect(find.text('Searching this phone'), findsNothing);
    expect(
      find.byKey(const ValueKey('folder-browser-phone-refused')),
      findsOneWidget,
    );
  });

  testWidgets('search: timer, counts, results as found, Stop, finished line', (
    tester,
  ) async {
    await open(tester);
    await startSearch(tester);
    expect(find.text('Searching this phone'), findsOneWidget);
    expect(find.text('0:00'), findsOneWidget);
    expect(find.text('0 folders checked · 0 found'), findsOneWidget);
    expect(find.byKey(const ValueKey('phone-scan-progress')), findsOneWidget);
    expect(find.text('Stop'), findsOneWidget);
    expect(limits, [const Duration(seconds: 10)]);
    // Nothing from the start page is left.
    expect(find.text('New project'), findsNothing);

    scans.single.visited = 41;
    scans.single.emit(_project('api', git: true));
    await tester.pump();
    scans.single.emit(_project('site'));
    await tester.pump(const Duration(seconds: 7));
    expect(find.text('0:07'), findsOneWidget);
    expect(find.text('41 folders checked · 2 found'), findsOneWidget);
    expect(find.text('api'), findsOneWidget);
    expect(find.text('site'), findsOneWidget);
    expect(find.text('Git'), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    scans.single.finish(PhoneScanEnd.completed);
    await tester.pumpAndSettle();
    expect(find.text('2 found in 0:09'), findsOneWidget);
    expect(find.text('Stop'), findsNothing);
    expect(find.text('Projects on this phone'), findsOneWidget);

    await tester.tap(find.text('api'));
    await tester.pumpAndSettle();
    expect((choice! as FolderBrowserOpen).path, '$_root/Documents/api');
  });

  testWidgets('Stop ends the search and keeps what was found', (tester) async {
    await open(tester);
    await startSearch(tester);
    scans.single.emit(_project('api'));
    await tester.pump(const Duration(seconds: 3));
    await tester.tap(find.text('Stop'));
    await tester.pumpAndSettle();
    expect(scans.single.cancelled, isTrue);
    expect(find.text('1 found in 0:03'), findsOneWidget);
    expect(find.text('api'), findsOneWidget);
    expect(find.text('Stop'), findsNothing);
  });

  testWidgets('nothing found says so and what to do next', (tester) async {
    await open(tester);
    await startSearch(tester);
    scans.single.finish(PhoneScanEnd.completed);
    await tester.pumpAndSettle();
    expect(find.text('No projects found'), findsOneWidget);
    expect(find.text('Browse folders or start a New project.'), findsOneWidget);
  });

  testWidgets('the time cap says so and Look deeper scans for 30 seconds', (
    tester,
  ) async {
    await open(tester);
    await startSearch(tester);
    scans.single.emit(_project('api'));
    scans.single.finish(PhoneScanEnd.timedOut);
    await tester.pumpAndSettle();
    expect(find.text('Stopped after 10 seconds · 1 found'), findsOneWidget);
    await tester.tap(find.text('Look deeper'));
    await tester.pump();
    expect(limits, [const Duration(seconds: 10), const Duration(seconds: 30)]);
    expect(find.text('Searching this phone'), findsOneWidget);
    expect(find.text('api'), findsNothing);
    scans.last.emit(_project('late'));
    scans.last.finish(PhoneScanEnd.timedOut);
    await tester.pumpAndSettle();
    expect(find.text('Stopped after 30 seconds · 1 found'), findsOneWidget);
    expect(find.text('Look deeper'), findsNothing);
  });

  testWidgets('back cancels the search and returns to the start page', (
    tester,
  ) async {
    await open(tester);
    await startSearch(tester);
    scans.single.emit(_project('api'));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('phone-scan-back')));
    await tester.pumpAndSettle();
    expect(scans.single.cancelled, isTrue);
    expect(find.text('Searching this phone'), findsNothing);
    expect(find.text('Open a project'), findsOneWidget);
    expect(find.text('New project'), findsOneWidget);
    await leave(tester);
  });

  testWidgets('a finished search is kept for the sheet and shown again', (
    tester,
  ) async {
    await open(tester);
    await startSearch(tester);
    scans.single.emit(_project('api'));
    scans.single.finish(PhoneScanEnd.completed);
    await tester.pumpAndSettle();
    await tapKey(tester, 'phone-scan-back');
    await tapKey(tester, 'open-project-search');
    expect(scans.length, 1);
    expect(find.text('api'), findsOneWidget);
    expect(find.text('Projects on this phone'), findsOneWidget);
  });

  testWidgets('Choose a folder opens the folder browser', (tester) async {
    await open(tester);
    await tapKey(tester, 'open-project-browse');
    expect(find.text('projects'), findsOneWidget);
    expect(find.text('demo'), findsOneWidget);
    expect(find.text('Find projects'), findsNothing);
  });
}
