// screen-files-1 behaviour: the Project tab (map project-hub, proposal fix)
// offers the chooser when no project is open, lists Changes first, leaves
// Search to Files' own field, and opens Files inside the tab under one top
// bar whose title is the open folder.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:opencode_mobile/ui/widgets/remote_folder_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => path.isEmpty
      ? [FileNode(name: 'lib', path: 'lib', isDir: true)]
      : [FileNode(name: 'main.dart', path: 'lib/main.dart', isDir: false)];

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

  testWidgets('Files is the title, the project is a chip; Changes first; no '
      'Search row', (tester) async {
    await _pump(tester, directory: '/srv/shopfront');
    expect(find.text('Files'), findsWidgets);
    expect(find.bySemanticsLabel(RegExp('shopfront')), findsWidgets);
    // The path is no longer repeated in the header.
    expect(find.text('/srv/shopfront'), findsNothing);
    expect(find.byKey(const ValueKey('project-hub-search')), findsNothing);
    final changes = tester.getTopLeft(
      find.byKey(const ValueKey('project-hub-changes')),
    );
    final files = tester.getTopLeft(
      find.byKey(const ValueKey('project-hub-files')),
    );
    expect(changes.dy, lessThan(files.dy));
    // Title-only rows: no static descriptions under the tool names.
    expect(find.text('Browse and preview project files'), findsNothing);
    expect(find.text('Open persistent project terminals'), findsNothing);
  });

  testWidgets('Files opens in the tab under one bar titled by the folder', (
    tester,
  ) async {
    await _pump(tester, directory: '/srv/shopfront');
    await tester.tap(find.byKey(const ValueKey('project-hub-files')));
    await tester.pumpAndSettle();
    final header = find.byKey(const ValueKey('project-hub-files-header'));
    expect(
      find.descendant(of: header, matching: find.text('Files')),
      findsOneWidget,
    );

    await tester.tap(find.text('lib'));
    await tester.pumpAndSettle();
    expect(
      find.descendant(of: header, matching: find.text('lib')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('files-breadcrumb')), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('project-hub-files-back')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('project-hub-changes')), findsOneWidget);
  });

  // slice-P3.7a (d4f01730) removed the changes sheet: the changed-files row
  // says the count and totals once and opens the diff itself (Review, one
  // "Change 1 of N" navigator). What this test guarded still holds: no
  // per-status groups anywhere, one set of changed files.
  testWidgets('the changes row says the set once, never grouped by status, '
      'and opens the diff itself', (tester) async {
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
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    // One row for the whole set: its count and totals, said once.
    final row = find.byKey(const ValueKey('files-changes-card'));
    expect(row, findsOneWidget);
    expect(
      find.descendant(of: row, matching: find.text('3 changed files')),
      findsOneWidget,
    );
    expect(find.text(KitBidi.ltr('+31 −3')), findsOneWidget);
    // No per-status group labels with counts.
    expect(find.textContaining('Modified · 2'), findsNothing);
    expect(find.textContaining('Added · 1'), findsNothing);

    await tester.tap(row);
    await tester.pumpAndSettle();

    // Straight into the diff with its one navigator; no list in between.
    expect(find.byKey(const ValueKey('files-changes-sheet')), findsNothing);
    expect(find.byKey(const Key('review-workspace')), findsOneWidget);
    expect(find.text('Change 1 of 3'), findsOneWidget);
    expect(find.textContaining('Modified · 2'), findsNothing);
    expect(find.textContaining('Added · 1'), findsNothing);
  });
}
