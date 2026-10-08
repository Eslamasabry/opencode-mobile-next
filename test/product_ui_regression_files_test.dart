import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/kit/kit_status_line.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/product_ui_regression_fixtures.dart';

class _FileStatusRepository extends LocationRepository {
  List<VersionControlFile> statuses = const [];
  List<FileDiff> diffs = const [];
  Object? statusError;
  int statusLoads = 0;
  int diffLoads = 0;

  @override
  Future<List<VersionControlFile>> listFileStatuses() async {
    statusLoads++;
    if (statusError case final error?) throw error;
    return statuses;
  }

  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) async {
    diffLoads++;
    return diffs;
  }
}

/// Review is reached from the file row's long-press sheet (the touch twin of
/// the desktop right-click menu); the change badge itself is not a button.
Future<void> _openInReview(WidgetTester tester, String path) async {
  await tester.ensureVisible(find.byKey(ValueKey('project-file-$path')));
  await tester.pumpAndSettle();
  await tester.longPress(find.byKey(ValueKey('project-file-$path')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const ValueKey('file-menu-review')));
  await tester.pumpAndSettle();
}

Future<void> _openReviewComment(WidgetTester tester, String path) async {
  await tester.tap(
    find.descendant(
      of: find.byKey(ValueKey('review-file-header-$path')),
      matching: find.byTooltip('More'),
    ),
  );
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('review-file-comment')));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    ReviewWorkspace.clearCache();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets(
    'newer file request wins and location invalidates retained files',
    (tester) async {
      final first = Completer<List<FileNode>>();
      final second = Completer<List<FileNode>>();
      var call = 0;
      final api = TestApi(
        files: (_) {
          call++;
          if (call == 1) return first.future;
          if (call == 2) return second.future;
          return Future.value(const <FileNode>[]);
        },
      );
      final repository = LocationRepository();
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final controller = SignalController(ProfileStore(prefs: prefs))
        ..api = api
        ..repository = repository
        ..status = StreamStatus.connected;
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: FilesScreen(controller: controller)),
        ),
      );
      await tester.pump();
      repository.setLocation(directory: '/new');
      controller.signalLocation(repository);
      await tester.pump();
      expect(call, 2);
      second.complete([
        FileNode(name: 'new.txt', path: 'new.txt', isDir: false),
      ]);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 20));
      expect(find.text('new.txt'), findsOneWidget);

      first.complete([
        FileNode(name: 'old.txt', path: 'old.txt', isDir: false),
      ]);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 20));
      expect(find.text('new.txt'), findsOneWidget);
      expect(find.text('old.txt'), findsNothing);
    },
  );

  testWidgets('nested files use project-relative API paths and breadcrumbs', (
    tester,
  ) async {
    final requestedPaths = <String>[];
    final api = TestApi(
      files: (path) async {
        requestedPaths.add(path);
        return switch (path) {
          '' => [FileNode(name: 'lib', path: '/lib', isDir: true)],
          'lib' => [FileNode(name: 'ui', path: 'lib/ui', isDir: true)],
          'lib/ui' => [
            FileNode(
              name: 'screen.dart',
              path: 'lib/ui/screen.dart',
              isDir: false,
            ),
          ],
          _ => const <FileNode>[],
        };
      },
    );
    final controller = await regressionController(
      api: api,
      repository: LocationRepository(),
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
    await tester.tap(find.text('lib'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('ui'));
    await tester.pumpAndSettle();

    expect(requestedPaths, ['', 'lib', 'lib/ui']);
    expect(find.bySemanticsLabel('Open Project root'), findsOneWidget);
    expect(find.text('/lib/ui'), findsNothing);

    await tester.tap(find.bySemanticsLabel('Open folder lib'));
    await tester.pumpAndSettle();
    expect(requestedPaths.last, 'lib');
  });

  testWidgets(
    'files show exact changes and aggregate nested changes without crowding',
    (tester) async {
      final api = TestApi(
        files: (path) async => switch (path) {
          '' => [
            FileNode(name: 'lib', path: 'lib', isDir: true),
            FileNode(name: 'README.md', path: 'README.md', isDir: false),
          ],
          'lib' => [
            FileNode(name: 'main.dart', path: 'lib/main.dart', isDir: false),
          ],
          _ => const <FileNode>[],
        },
      );
      final repository = _FileStatusRepository()
        ..statuses = const [
          VersionControlFile(
            path: '/README.md',
            status: 'modified',
            additions: 8,
            deletions: 2,
          ),
          VersionControlFile(
            path: 'lib/main.dart',
            status: 'added',
            additions: 34,
            deletions: 0,
          ),
          VersionControlFile(
            path: 'gone.txt',
            status: 'deleted',
            additions: 0,
            deletions: 12,
          ),
        ]
        ..diffs = [
          FileDiff(
            file: 'lib/main.dart',
            patch: '@@ -0,0 +1 @@\n+library change',
            additions: 1,
            deletions: 0,
          ),
          FileDiff(
            file: 'README.md',
            patch: '@@ -1 +1 @@\n-old readme\n+new readme',
            additions: 1,
            deletions: 1,
          ),
          FileDiff(
            file: 'gone.txt',
            patch: '@@ -1 +0,0 @@\n-deleted text',
            additions: 0,
            deletions: 1,
            status: 'deleted',
          ),
        ];
      final controller = await regressionController(
        api: api,
        repository: repository,
      );
      addTearDown(controller.dispose);
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(2)),
              child: Scaffold(body: FilesScreen(controller: controller)),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('1 changed file'), findsOneWidget);
      expect(find.text('Modified'), findsOneWidget);
      expect(find.text('gone.txt'), findsOneWidget);
      expect(find.text('Deleted'), findsOneWidget);
      expect(tester.takeException(), isNull);

      await tester.ensureVisible(find.text('gone.txt'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('gone.txt'));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('review-workspace')), findsOneWidget);
      expect(find.text('deleted text'), findsOneWidget);
      await tester.pageBack();
      await tester.pumpAndSettle();

      await _openInReview(tester, 'README.md');

      expect(find.byKey(const Key('review-workspace')), findsOneWidget);
      expect(find.byKey(const Key('review-scope-picker')), findsNothing);
      expect(find.text('new readme'), findsOneWidget);
      expect(find.text('library change'), findsNothing);
      expect(repository.diffLoads, 2);
      expect(tester.takeException(), isNull);

      await tester.pageBack();
      await tester.pumpAndSettle();

      await tester.scrollUntilVisible(
        find.text('lib'),
        -200,
        scrollable: find
            .descendant(
              of: find.byType(ListView),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('lib'));
      await tester.pumpAndSettle();

      expect(find.text('Added'), findsOneWidget);
      expect(repository.statusLoads, greaterThanOrEqualTo(2));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('file review comments return to the active chat callback', (
    tester,
  ) async {
    final api = TestApi(
      files: (_) async => [
        FileNode(name: 'README.md', path: 'README.md', isDir: false),
      ],
    );
    final repository = _FileStatusRepository()
      ..statuses = const [
        VersionControlFile(
          path: 'README.md',
          status: 'modified',
          additions: 1,
          deletions: 1,
        ),
      ]
      ..diffs = [
        FileDiff(
          file: 'README.md',
          patch: '@@ -1 +1 @@\n-old copy\n+new copy',
          additions: 1,
          deletions: 1,
        ),
      ];
    final controller = await regressionController(
      api: api,
      repository: repository,
    );
    addTearDown(controller.dispose);
    String? reviewPrompt;

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: FilesScreen(
            controller: controller,
            onReviewPrompt: (value) => reviewPrompt = value,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _openInReview(tester, 'README.md');
    await _openReviewComment(tester, 'README.md');
    await tester.enterText(
      find.byKey(const Key('review-comment-field')),
      'Keep this wording precise.',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('review-add-to-prompt')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('review-workspace')), findsNothing);
    expect(reviewPrompt, contains('Review `README.md`'));
    expect(reviewPrompt, contains('Keep this wording precise.'));
    expect(
      find.text(
        'Review comment added. Return to the conversation to continue.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('top-level file review copies its comment for another chat', (
    tester,
  ) async {
    String? copiedText;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copiedText = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    final api = TestApi(
      files: (_) async => [
        FileNode(name: 'README.md', path: 'README.md', isDir: false),
      ],
    );
    final repository = _FileStatusRepository()
      ..statuses = const [
        VersionControlFile(
          path: 'README.md',
          status: 'modified',
          additions: 1,
          deletions: 1,
        ),
      ]
      ..diffs = [
        FileDiff(
          file: 'README.md',
          patch: '@@ -1 +1 @@\n-old copy\n+new copy',
          additions: 1,
          deletions: 1,
        ),
      ];
    final controller = await regressionController(
      api: api,
      repository: repository,
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
    await _openInReview(tester, 'README.md');
    await _openReviewComment(tester, 'README.md');
    await tester.enterText(
      find.byKey(const Key('review-comment-field')),
      'Use the approved wording.',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('review-add-to-prompt')));
    await tester.pumpAndSettle();

    expect(copiedText, contains('Review `README.md`'));
    expect(copiedText, contains('Use the approved wording.'));
    expect(
      tester.takeAnnouncements().map((announcement) => announcement.message),
      contains('Review comment copied. Paste it into a conversation.'),
    );
  });

  testWidgets('changes row opens the diff itself (slice-P3.7a): totals on '
      'the row, no list of the same files in between', (tester) async {
    final api = TestApi(
      files: (_) async => [
        FileNode(name: 'README.md', path: 'README.md', isDir: false),
        FileNode(name: 'lib', path: 'lib', isDir: true),
      ],
    );
    final repository = _FileStatusRepository()
      ..statuses = const [
        VersionControlFile(
          path: 'README.md',
          status: 'modified',
          additions: 8,
          deletions: 2,
        ),
        VersionControlFile(
          path: 'lib/main.dart',
          status: 'added',
          additions: 34,
          deletions: 0,
        ),
      ]
      ..diffs = [
        FileDiff(
          file: 'lib/main.dart',
          patch: '@@ -0,0 +1 @@\n+library change',
          additions: 1,
          deletions: 0,
        ),
      ];
    final controller = await regressionController(
      api: api,
      repository: repository,
    );
    addTearDown(controller.dispose);
    final store = ReviewHandoffStore();

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: FilesScreen(
            controller: controller,
            handoff: ReviewHandoffSession(store: store, sessionID: 's1'),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    // The row names the set and its totals.
    expect(find.byKey(const ValueKey('files-changes-card')), findsOneWidget);
    expect(find.text('2 changed files'), findsOneWidget);
    expect(find.text(KitBidi.ltr('+42 −2')), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('files-changes-card')));
    await tester.pumpAndSettle();

    // Straight into Review: the diff with its one navigator, no sheet.
    expect(find.byKey(const ValueKey('files-changes-sheet')), findsNothing);
    expect(find.byKey(const Key('review-workspace')), findsOneWidget);
    expect(find.text('library change'), findsOneWidget);
    expect(find.text('Change 1 of 1'), findsOneWidget);

    // A changed file stages as a reference from the diff's own menu.
    await tester.tap(find.byTooltip('More').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('review-add-file')));
    await tester.pumpAndSettle();
    final staged = store.referencesFor('s1').single;
    expect(staged.kind, ReviewReferenceKind.changedFile);
    expect(staged.path, 'lib/main.dart');
    expect(staged.added, 1);
    // Let the staged notice's Undo window run out.
    await tester.pump(const Duration(seconds: 9));
  });

  testWidgets('project files stage as references distinct from attachments', (
    tester,
  ) async {
    final api = TestApi(
      files: (_) async => [
        FileNode(name: 'README.md', path: 'README.md', isDir: false),
      ],
      contents: {
        'README.md': const FileContent('hello', mimeType: 'text/plain'),
      },
    );
    final controller = await regressionController(
      api: api,
      repository: LocationRepository(),
    );
    addTearDown(controller.dispose);
    final store = ReviewHandoffStore();

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: FilesScreen(
            controller: controller,
            handoff: ReviewHandoffSession(store: store, sessionID: 's1'),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('README.md'));
    await tester.pumpAndSettle();

    // Add-to-prompt (a reference) and attach (an upload) are separate
    // affordances; only the reference one exists without an attach handler.
    expect(find.byKey(const Key('project-file-add-reference')), findsOneWidget);
    expect(find.byKey(const Key('project-file-attach')), findsNothing);

    await tester.tap(find.byKey(const Key('project-file-add-reference')));
    await tester.pumpAndSettle();
    final staged = store.referencesFor('s1').single;
    expect(staged.kind, ReviewReferenceKind.file);
    expect(staged.path, 'README.md');
    expect(staged.snippet, isNull);
    // Adding a reference now offers Undo for eight seconds. Let the real
    // undo window commit before the test tears down its host.
    await tester.pump(const Duration(seconds: 8));
    await tester.pumpAndSettle();
  });

  testWidgets(
    'file status failure stays scoped and refresh recovers change marks',
    (tester) async {
      final api = TestApi(
        files: (_) async => [
          FileNode(name: 'README.md', path: 'README.md', isDir: false),
        ],
      );
      final repository = _FileStatusRepository()
        ..statusError = const ProductException('Old server');
      final controller = await regressionController(
        api: api,
        repository: repository,
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

      expect(find.text('README.md'), findsOneWidget);
      expect(find.byType(KitStatusLine), findsOneWidget);
      expect(
        find.text('File change indicators are unavailable on this server.'),
        findsOneWidget,
      );

      repository
        ..statusError = null
        ..statuses = const [
          VersionControlFile(
            path: 'README.md',
            status: 'modified',
            additions: 1,
            deletions: 0,
          ),
        ];
      // Change marks now occupy the informational status slot. Foreground
      // refresh refreshes their read along with the listing.
      controller.signalDataRefreshForTesting();
      await tester.pumpAndSettle();

      expect(find.byType(KitStatusLine), findsNothing);
      expect(find.text('Modified'), findsOneWidget);
      expect(repository.statusLoads, 2);
    },
  );

  testWidgets('foreground refresh reloads the current file directory', (
    tester,
  ) async {
    var refreshed = false;
    final requestedPaths = <String>[];
    final api = TestApi(
      files: (path) async {
        requestedPaths.add(path);
        if (path.isEmpty) {
          return [FileNode(name: 'lib', path: 'lib', isDir: true)];
        }
        return [
          FileNode(
            name: refreshed ? 'after.dart' : 'before.dart',
            path: 'lib/${refreshed ? 'after.dart' : 'before.dart'}',
            isDir: false,
          ),
        ];
      },
    );
    final controller = await regressionController(
      api: api,
      repository: LocationRepository(),
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
    await tester.tap(find.text('lib'));
    await tester.pumpAndSettle();
    expect(find.text('before.dart'), findsOneWidget);

    refreshed = true;
    controller.signalDataRefreshForTesting();
    await tester.pumpAndSettle();

    expect(requestedPaths.last, 'lib');
    expect(find.text('after.dart'), findsOneWidget);
    expect(find.text('before.dart'), findsNothing);
  });

  testWidgets('file search clear control has an accessible tooltip', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final controller = await regressionController(
      repository: LocationRepository(),
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

    await tester.enterText(find.byType(TextField), 'query');
    await tester.pumpAndSettle();

    expect(find.byTooltip('Clear search'), findsOneWidget);
    expect(find.bySemanticsLabel(RegExp('Clear search')), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    semantics.dispose();
  });

  testWidgets('clearing file search restores the directory it started in', (
    tester,
  ) async {
    final requestedPaths = <String>[];
    final api = TestApi(
      files: (path) async {
        requestedPaths.add(path);
        return switch (path) {
          '' => [FileNode(name: 'lib', path: 'lib', isDir: true)],
          'lib' => [
            FileNode(name: 'src', path: 'lib/src', isDir: true),
            FileNode(name: 'local.dart', path: 'lib/local.dart', isDir: false),
          ],
          _ => const <FileNode>[],
        };
      },
      findFiles: (_) async => const ['test/result.dart'],
    );
    final controller = await regressionController(
      api: api,
      repository: LocationRepository(),
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
    await tester.tap(find.text('lib'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'result');
    await tester.pump(const Duration(milliseconds: 350));
    await tester.pumpAndSettle();
    expect(find.text('result.dart'), findsOneWidget);

    await tester
        .widget<RefreshIndicator>(find.byType(RefreshIndicator).first)
        .onRefresh();
    await tester.pumpAndSettle();
    expect(find.text('result.dart'), findsOneWidget);
    expect(find.text('local.dart'), findsNothing);

    await tester.tap(find.byTooltip('Clear search'));
    await tester.pumpAndSettle();

    expect(requestedPaths.last, 'lib');
    expect(find.text('local.dart'), findsOneWidget);
    expect(find.text('result.dart'), findsNothing);
  });
}
