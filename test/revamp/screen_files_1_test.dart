// screen-files-1 behaviour, as the vision slice left it (2026-10-20, "just
// the files"): the Files page offers the chooser when no project is open;
// otherwise it is the project's files under a large title, a changed file
// marked on its own name, the tools in the bar's ⋯, Search behind an icon,
// and a reader whose one main action asks about the file.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import '../support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import '../support/fake_event_channel.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:opencode_mobile/ui/widgets/remote_folder_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends FakeServerGateway {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => path.isEmpty
      ? [
          FileNode(name: 'lib', path: 'lib', isDir: true),
          FileNode(name: 'main.dart', path: 'main.dart', isDir: false),
          FileNode(name: 'README.md', path: 'README.md', isDir: false),
        ]
      : [FileNode(name: 'app.dart', path: 'lib/app.dart', isDir: false)];

  @override
  Future<FileContent> fileContent(String path) async =>
      const FileContent('void main() {}\n');

  @override
  Future<List<String>> findFile(String query) async => const [];
}

class _Repository implements ProductRepository {
  _Repository([this.statuses = const [], this.diffs = const []]);

  final List<VersionControlFile> statuses;
  final List<FileDiff> diffs;

  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) async => diffs;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<VersionControlFile>> listFileStatuses() async => statuses;

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller({
  String? directory,
  List<VersionControlFile> statuses = const [],
  List<FileDiff> diffs = const [],
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = _Api()
    ..repository = _Repository(statuses, diffs)
    ..directory = directory
    ..status = StreamStatus.connected;
}

Future<ConnectionController> _pump(
  WidgetTester tester, {
  String? directory,
}) async {
  final controller = await _controller(directory: directory);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(body: ProjectHub(controller: controller)),
    ),
  );
  await tester.pumpAndSettle();
  return controller;
}

/// The phone's door: the Files page pushed over a conversation, where its
/// own Back walks out of the search and up the folders.
Future<void> _pumpPage(WidgetTester tester, {String? directory}) async {
  final controller = await _controller(directory: directory);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: const Scaffold(body: SizedBox.expand(key: ValueKey('chat'))),
    ),
  );
  unawaited(
    openProjectFiles(
      tester.element(find.byKey(const ValueKey('chat'))),
      controller,
      directory: directory,
    ),
  );
  await tester.pumpAndSettle();
}

Finder _title(String text) => find.descendant(
  of: find.byKey(const ValueKey('project-hub-title')),
  matching: find.text(text),
  matchRoot: true,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('with no project open the hub offers the chooser', (
    tester,
  ) async {
    await _pump(tester);
    expect(find.text('No project selected'), findsOneWidget);
    final choose = find.byKey(const ValueKey('project-hub-choose-project'));
    expect(choose, findsOneWidget);
    // The tools act on a project: none is listed until one is open.
    for (final tool in ['changes', 'files', 'terminal', 'health']) {
      expect(find.byKey(ValueKey('project-hub-$tool')), findsNothing);
    }

    await tester.tap(choose);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.byType(RemoteFolderSheet), findsOneWidget);
  });

  testWidgets('Files is the title, the project under it, the files below; '
      'the tools are in the bar', (tester) async {
    await _pump(tester, directory: '/srv/shopfront');
    expect(_title('Files'), findsOneWidget);
    expect(find.textContaining('shopfront'), findsOneWidget);
    expect(find.text('/srv/shopfront'), findsNothing);
    expect(find.byType(FilesScreen), findsOneWidget);
    expect(
      find.byKey(const ValueKey('project-file-main.dart')),
      findsOneWidget,
    );
    // No Browse files or Search rows, no always-visible field, no
    // breadcrumb: the page is the files.
    expect(find.byKey(const ValueKey('project-hub-files')), findsNothing);
    expect(find.byKey(const ValueKey('project-hub-search')), findsNothing);
    expect(find.byKey(const ValueKey('files-search-field')), findsNothing);
    expect(find.byKey(const ValueKey('files-breadcrumb')), findsNothing);

    await tester.tap(find.byKey(const ValueKey('project-hub-menu')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('project-hub-changes')), findsOneWidget);
    expect(find.byKey(const ValueKey('project-hub-copy-path')), findsOneWidget);
  });

  testWidgets('Search opens the field under the title; Back closes it', (
    tester,
  ) async {
    await _pumpPage(tester, directory: '/srv/shopfront');
    await tester.tap(find.byKey(const ValueKey('project-files-search')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('files-search-field')), findsOneWidget);
    expect(
      tester.binding.focusManager.primaryFocus?.debugLabel,
      'files-search',
    );

    // Back closes the empty field and leaves the list as it was.
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('files-search-field')), findsNothing);
    expect(
      find.byKey(const ValueKey('project-file-main.dart')),
      findsOneWidget,
    );
  });

  testWidgets('a folder becomes the title and Back walks up', (tester) async {
    await _pumpPage(tester, directory: '/srv/shopfront');
    await tester.tap(find.text('lib'));
    await tester.pumpAndSettle();
    expect(_title('lib'), findsOne);
    expect(find.text('app.dart'), findsOneWidget);
    expect(find.byKey(const ValueKey('files-breadcrumb')), findsNothing);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(_title('Files'), findsOne);
    // At the project's root Back leaves the page for the conversation.
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.byType(ProjectHub), findsNothing);
  });

  // slice-P3.7a (d4f01730) removed the changes sheet; the vision slice moved
  // the set's door into the bar's ⋯ and put each change on its file's name.
  // What this test guards still holds: no per-status groups anywhere, one
  // set of changed files, straight into the diff.
  testWidgets('a change shows on its file\'s name in words, and Changes '
      'opens the diff itself', (tester) async {
    final controller = await _controller(
      directory: '/srv/shopfront',
      statuses: const [
        VersionControlFile(
          path: 'test/checkout_test.dart',
          status: 'added',
          additions: 24,
          deletions: 0,
        ),
        VersionControlFile(
          path: 'lib/cart/cart_bloc.dart',
          status: 'modified',
          additions: 1,
          deletions: 1,
        ),
        VersionControlFile(
          path: 'main.dart',
          status: 'modified',
          additions: 6,
          deletions: 2,
        ),
      ],
      diffs: [
        FileDiff(
          file: 'lib/cart/cart_bloc.dart',
          patch: '@@ -1 +1 @@\n-old cart\n+new cart',
          additions: 1,
          deletions: 1,
          status: 'modified',
        ),
        FileDiff(
          file: 'main.dart',
          patch: '@@ -1 +1 @@\n-old main\n+new main',
          additions: 6,
          deletions: 2,
          status: 'modified',
        ),
        FileDiff(
          file: 'test/checkout_test.dart',
          patch: '@@ -0,0 +1 @@\n+checkout test',
          additions: 24,
          deletions: 0,
          status: 'added',
        ),
      ],
    );
    addTearDown(controller.dispose);
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: ProjectHub(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    String label(String key) => tester
        .getSemantics(find.byKey(ValueKey('project-file-$key')))
        .getSemanticsData()
        .label;
    // The state in words after the name, never colour alone; a folder says
    // how many changed inside; an unchanged file says nothing more.
    expect(label('main.dart'), contains('Modified'));
    expect(label('lib'), contains('1 changed file'));
    expect(label('README.md'), isNot(contains('Modified')));
    // No count-and-totals row above the list any more.
    expect(find.byKey(const ValueKey('files-changes-card')), findsNothing);
    expect(find.textContaining('Modified · 2'), findsNothing);
    semantics.dispose();

    await tester.tap(find.byKey(const ValueKey('project-hub-menu')));
    await tester.pumpAndSettle();
    expect(find.text('Changes · 3 files'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('project-hub-changes')));
    await tester.pumpAndSettle();

    // Straight into the diff with its one navigator; no list in between.
    expect(find.byKey(const ValueKey('files-changes-sheet')), findsNothing);
    expect(find.byKey(const Key('review-workspace')), findsOneWidget);
    // The changes screen is folded rows under one counts line; the
    // "Change 1 of N" navigator is gone (b0695f646).
    expect(find.textContaining('3 files changed'), findsOneWidget);
    expect(find.text('Change 1 of 3'), findsNothing);
    expect(find.textContaining('Modified · 2'), findsNothing);
    expect(find.textContaining('Added · 1'), findsNothing);
  });

  testWidgets('Ask about this file puts it in the conversation and goes back '
      'there', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller(directory: '/srv/shopfront');
    addTearDown(controller.dispose);
    final handoff = ReviewHandoffSession(
      store: ReviewHandoffStore(),
      sessionID: 'ses_1',
    );
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const Scaffold(body: SizedBox.expand(key: ValueKey('chat'))),
      ),
    );
    unawaited(
      openProjectFiles(
        tester.element(find.byKey(const ValueKey('chat'))),
        controller,
        directory: '/srv/shopfront',
        handoff: handoff,
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('project-file-main.dart')));
    await tester.pumpAndSettle();

    // The reader: the one main action at the bottom, Copy beside it.
    final ask = find.byKey(const Key('project-file-ask'));
    expect(ask, findsOneWidget);
    expect(find.text('Ask about this file'), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-viewer-copy')), findsOneWidget);
    expect(
      tester.getCenter(ask).dy,
      greaterThan(tester.getCenter(find.text('main.dart').last).dy),
    );

    await tester.tap(ask);
    await tester.pumpAndSettle();
    expect(handoff.references.single.path, 'main.dart');
    // Back in the conversation: the reader and the Files page closed.
    expect(find.byType(ProjectHub), findsNothing);
    expect(find.byKey(const ValueKey('chat')), findsOneWidget);
  });
}
