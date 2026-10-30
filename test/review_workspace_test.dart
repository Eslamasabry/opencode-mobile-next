import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/product_repository.dart'
    show ProductException;
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_diff_view.dart';
import 'package:opencode_mobile/ui/kit/kit_row_parts.dart' show KitExpandRow;
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<void> _pumpReview(
  WidgetTester tester,
  Future<List<FileDiff>> Function() loader, {
  Future<List<FileDiff>> Function()? workingTreeLoader,
  Future<List<FileDiff>> Function()? branchLoader,
  Size size = const Size(800, 700),
  double textScale = 1,
  ReviewHandoffSession? handoff,
  String? profileId,
  bool settle = true,
  Future<void> Function()? onUndoAll,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: child!,
      ),
      home: ReviewWorkspace(
        loadDiffs: loader,
        loadWorkingTreeDiffs: workingTreeLoader,
        loadBranchDiffs: branchLoader,
        handoff: handoff,
        profileId: profileId,
        onUndoAll: onUndoAll,
      ),
    ),
  );
  if (settle) await tester.pumpAndSettle();
}

/// The file's folded row, while it is the open one.
Finder _header(String path) => find.byWidgetPredicate(
  (widget) =>
      widget is KitExpandRow &&
      widget.key == ValueKey('review-fold-$path') &&
      widget.expanded == true,
);

/// Unfolds [path]'s row (the open one folds).
Future<void> _openFile(WidgetTester tester, String path) async {
  await tester.tap(find.byKey(ValueKey('review-fold-header-$path')));
  await tester.pumpAndSettle();
}

/// A file's menu opens on a long press of its row.
Future<void> _openFileMenu(WidgetTester tester, String path) async {
  await tester.longPress(find.byKey(ValueKey('review-fold-header-$path')));
  await tester.pumpAndSettle();
}

void main() {
  setUp(() {
    ReviewWorkspace.clearCache();
    SharedPreferences.setMockInitialValues({});
  });

  FileDiff diff(String file, [String? text]) => FileDiff(
    file: file,
    patch: '@@ -1 +1 @@\n-old\n+${text ?? file}',
    additions: 1,
    deletions: 1,
  );

  testWidgets('renders one KitDiffView: each file a folded row, the first '
      'open, no hunk navigator and no viewed ticks', (tester) async {
    await _pumpReview(
      tester,
      () async => [diff('a.dart', 'first'), diff('b.dart', 'second')],
    );

    expect(find.byType(KitDiffView), findsOneWidget);
    expect(find.byType(KitExpandRow), findsNWidgets(2));
    expect(_header('a.dart'), findsOneWidget);
    expect(find.text('first'), findsOneWidget);
    expect(find.text('second'), findsNothing);
    expect(find.byKey(const ValueKey('review-nav-label')), findsNothing);
    expect(find.byKey(const ValueKey('review-nav-next')), findsNothing);
    expect(find.byKey(const ValueKey('review-file-viewed-0')), findsNothing);

    // Opening the next row folds the first.
    await _openFile(tester, 'b.dart');
    expect(_header('b.dart'), findsOneWidget);
    expect(find.text('second'), findsOneWidget);
    expect(find.text('first'), findsNothing);
    // Its row folds again.
    await _openFile(tester, 'b.dart');
    expect(find.text('second'), findsNothing);
    // The old per-file toolbar, strip and bottom hunk bar are gone.
    expect(find.byKey(const Key('review-hunk-bar')), findsNothing);
    expect(find.byKey(const Key('review-file-strip')), findsNothing);
  });

  testWidgets('says what changed in one line, then asks Looks good', (
    tester,
  ) async {
    await _pumpReview(tester, () async => [diff('a.dart'), diff('b.dart')]);
    expect(find.text('2 files changed · +2 −2'), findsOneWidget);
    expect(
      find.text('The agent already made these changes in your project.'),
      findsOneWidget,
    );
    expect(find.byKey(const Key('review-looks-good')), findsOneWidget);
    expect(find.text('Send back with a note'), findsOneWidget);
    // No revert from the caller: Undo all is not offered.
    expect(find.byKey(const Key('review-undo-all')), findsNothing);
  });

  testWidgets('Undo all confirms by naming the files, then undoes', (
    tester,
  ) async {
    var undone = 0;
    await _pumpReview(
      tester,
      () async => [diff('a.dart'), diff('b.dart'), diff('c.dart')],
      onUndoAll: () async => undone++,
    );
    await tester.tap(find.byKey(const Key('review-undo-all')));
    await tester.pumpAndSettle();
    expect(find.text('Undo 3 changed files?'), findsOneWidget);
    expect(undone, 0);
    await tester.tap(
      find.descendant(
        of: find.byKey(const Key('review-undo-confirm')),
        matching: find.text('Undo all'),
      ),
    );
    await tester.pumpAndSettle();
    expect(undone, 1);
  });

  testWidgets('Send back with a note puts the note on the chat prompt', (
    tester,
  ) async {
    final store = ReviewHandoffStore();
    await _pumpReview(
      tester,
      () async => [diff('a.dart')],
      handoff: ReviewHandoffSession(store: store, sessionID: 'chat-1'),
    );
    await tester.tap(find.byKey(const Key('review-send-back')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-comment-sheet')), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('review-comment-field')),
      'Keep the old name',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('review-add-to-prompt')));
    await tester.pumpAndSettle();
    expect(store.referencesFor('chat-1'), hasLength(1));
    expect(store.referencesFor('chat-1').single.comment, 'Keep the old name');
    await tester.pump(const Duration(seconds: 10));
  });

  testWidgets('refresh keeps the file being read after the files reorder', (
    tester,
  ) async {
    var diffs = [diff('a.dart'), diff('b.dart')];
    await _pumpReview(tester, () async => diffs);
    await _openFile(tester, 'b.dart');
    expect(_header('b.dart'), findsOneWidget);

    diffs = [diff('inserted.dart'), diff('b.dart'), diff('a.dart')];
    await tester.tap(find.byKey(const Key('review-refresh')));
    await tester.pumpAndSettle();
    expect(_header('b.dart'), findsOneWidget);
  });

  testWidgets('refresh honours a file opened while the request was pending', (
    tester,
  ) async {
    Completer<List<FileDiff>>? pending;
    await _pumpReview(
      tester,
      () async =>
          pending == null ? [diff('a.dart'), diff('b.dart')] : pending.future,
    );
    pending = Completer<List<FileDiff>>();
    await tester.tap(find.byKey(const Key('review-refresh')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('review-fold-header-b.dart')));
    await tester.pump();
    pending.complete([diff('b.dart'), diff('a.dart')]);
    await tester.pumpAndSettle();
    expect(_header('b.dart'), findsOneWidget);
  });

  testWidgets('a failed refresh keeps the diff and says why, with Try again', (
    tester,
  ) async {
    var fail = false;
    await _pumpReview(tester, () async {
      if (fail) throw const ProductException('Review refresh failed');
      return [diff('a.dart')];
    });
    fail = true;
    await tester.tap(find.byKey(const Key('review-refresh')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-refresh-failed')), findsOneWidget);
    expect(find.text('Review refresh failed'), findsOneWidget);
    expect(_header('a.dart'), findsOneWidget);

    fail = false;
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-refresh-failed')), findsNothing);
    expect(_header('a.dart'), findsOneWidget);
  });

  testWidgets('scope change clears the old view and rejects an older read', (
    tester,
  ) async {
    Completer<List<FileDiff>>? pending;
    await _pumpReview(
      tester,
      () async => pending == null ? [diff('session.dart')] : pending.future,
      workingTreeLoader: () async =>
          throw const ProductException('Working tree failed'),
    );
    pending = Completer<List<FileDiff>>();
    await tester.tap(find.byKey(const Key('review-refresh')));
    await tester.pump();
    await tester.tap(find.text('Uncommitted'));
    await tester.pump();
    pending.complete([diff('stale.dart')]);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-error')), findsOneWidget);
    expect(find.text('Working tree failed'), findsOneWidget);
    expect(_header('session.dart'), findsNothing);
    expect(_header('stale.dart'), findsNothing);
  });

  testWidgets('an error says why and Try again leads to the empty state', (
    tester,
  ) async {
    var attempts = 0;
    await _pumpReview(tester, () async {
      attempts++;
      if (attempts == 1) throw const ProductException('server unavailable');
      return const [];
    });

    expect(find.byKey(const Key('review-error')), findsOneWidget);
    expect(find.text("Couldn't load the changes"), findsOneWidget);
    expect(find.text('server unavailable'), findsOneWidget);
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-empty')), findsOneWidget);
    expect(find.text('No changes yet'), findsOneWidget);
    expect(
      find.text('Edits the agent makes show up here to review.'),
      findsOneWidget,
    );
  });

  testWidgets('a read slower than 8 s says so and offers Try again', (
    tester,
  ) async {
    final slow = Completer<List<FileDiff>>();
    var loads = 0;
    await _pumpReview(tester, () {
      loads++;
      return loads == 1 ? slow.future : Future.value([diff('a.dart')]);
    }, settle: false);
    await tester.pump();
    expect(find.byKey(const Key('review-loading')), findsOneWidget);
    expect(find.byKey(const Key('review-slow')), findsNothing);

    await tester.pump(const Duration(seconds: 9));
    expect(find.byKey(const Key('review-slow')), findsOneWidget);
    expect(find.text('Still reading the changes'), findsOneWidget);

    await tester.tap(find.byKey(const Key('review-slow-retry')));
    await tester.pumpAndSettle();
    expect(_header('a.dart'), findsOneWidget);
    slow.complete(const []);
    await tester.pumpAndSettle();
    expect(_header('a.dart'), findsOneWidget);
  });

  // slice-R18: one reload on screen at a time.
  testWidgets('R18: no top-bar Refresh while the error or the slow wait '
      'shows; Try again is the one reload', (tester) async {
    final slow = Completer<List<FileDiff>>();
    var loads = 0;
    await _pumpReview(tester, () {
      loads++;
      if (loads == 1) return slow.future;
      if (loads == 2) throw const ProductException('server unavailable');
      return Future.value([diff('a.dart')]);
    }, settle: false);
    await tester.pump(const Duration(seconds: 9));
    expect(find.byKey(const Key('review-slow')), findsOneWidget);
    expect(find.byKey(const Key('review-refresh')), findsNothing);

    await tester.tap(find.byKey(const Key('review-slow-retry')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-error')), findsOneWidget);
    expect(find.byKey(const Key('review-refresh')), findsNothing);
    expect(find.text('Try again'), findsOneWidget);

    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(_header('a.dart'), findsOneWidget);
    expect(find.byKey(const Key('review-refresh')), findsOneWidget);
    slow.complete(const []);
    await tester.pumpAndSettle();
  });

  testWidgets('R18: what a view covers is said only when it is empty', (
    tester,
  ) async {
    var session = [diff('a.dart')];
    await _pumpReview(
      tester,
      () async => session,
      workingTreeLoader: () async => [diff('b.dart')],
    );
    expect(find.byKey(const Key('review-scope-picker')), findsOneWidget);
    expect(find.byKey(const Key('review-scope-hint')), findsNothing);
    expect(find.text('Files this conversation changed.'), findsNothing);

    session = const [];
    await tester.tap(find.byKey(const Key('review-refresh')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-empty')), findsOneWidget);
    expect(find.text('Files this conversation changed.'), findsOneWidget);
  });

  testWidgets('on a phone the views are one compact row that opens a picker', (
    tester,
  ) async {
    FileDiff added(String file, String line) => FileDiff(
      file: file,
      patch: '@@ -0,0 +1 @@\n+$line',
      additions: 1,
      deletions: 0,
    );
    await _pumpReview(
      tester,
      () async => [added('session.txt', 'session change')],
      workingTreeLoader: () async => [added('working.txt', 'working change')],
      branchLoader: () async => [added('branch.txt', 'branch change')],
      size: const Size(412, 915),
    );
    // One row, not three: the diff starts near the top.
    final picker = tester.getRect(find.byKey(const Key('review-scope-picker')));
    expect(picker.height, lessThan(100));
    expect(tester.getTopLeft(find.text('session change')).dy, lessThan(320));
    // Every view is still reachable.
    await tester.tap(find.byKey(const Key('review-scope-picker')));
    await tester.pumpAndSettle();
    expect(find.text('Uncommitted'), findsOneWidget);
    expect(find.text('Whole branch'), findsOneWidget);
    await tester.tap(find.text('Uncommitted'));
    await tester.pumpAndSettle();
    expect(find.text('working change'), findsOneWidget);
  });

  testWidgets('switches among session, working tree and branch views', (
    tester,
  ) async {
    var sessionLoads = 0;
    var workingTreeLoads = 0;
    var branchLoads = 0;
    FileDiff added(String file, String line) => FileDiff(
      file: file,
      patch: '@@ -0,0 +1 @@\n+$line',
      additions: 1,
      deletions: 0,
    );
    await _pumpReview(
      tester,
      () async {
        sessionLoads++;
        return [added('session.txt', 'session change')];
      },
      workingTreeLoader: () async {
        workingTreeLoads++;
        return [added('working.txt', 'working change')];
      },
      branchLoader: () async {
        branchLoads++;
        return [added('branch.txt', 'branch change')];
      },
    );

    expect(find.byKey(const Key('review-scope-picker')), findsOneWidget);
    expect(find.text('session change'), findsOneWidget);
    expect(sessionLoads, 1);

    await tester.tap(find.text('Uncommitted'));
    await tester.pumpAndSettle();
    expect(find.text('working change'), findsOneWidget);
    expect(workingTreeLoads, 1);

    await tester.tap(find.text('Whole branch'));
    await tester.pumpAndSettle();
    expect(find.text('branch change'), findsOneWidget);
    expect(branchLoads, 1);

    await tester.tap(find.text('This conversation'));
    await tester.pumpAndSettle();
    expect(find.text('session change'), findsOneWidget);
    expect(sessionLoads, 2);
  });

  testWidgets('each view says what it covers, in words on screen', (
    tester,
  ) async {
    await _pumpReview(
      tester,
      () async => const [],
      workingTreeLoader: () async => const [],
      branchLoader: () async => const [],
    );
    expect(find.text('Files this conversation changed.'), findsOneWidget);
    await tester.tap(find.text('Whole branch'));
    await tester.pumpAndSettle();
    expect(
      find.text('Everything on this branch, compared with the main branch.'),
      findsOneWidget,
    );
  });

  testWidgets('reopening shows the last result at once and the other views '
      'are ready before they are asked for', (tester) async {
    final slow = Completer<List<FileDiff>>();
    var workingLoads = 0;
    FileDiff change(String file, String line) => FileDiff(
      file: file,
      patch: '@@ -0,0 +1 @@\n+$line',
      additions: 1,
      deletions: 0,
    );
    Widget review() => MaterialApp(
      theme: AppTheme.dark(),
      home: ReviewWorkspace(
        cacheKey: 'server|/work|chat',
        initialScope: ReviewDiffScope.workingTree,
        loadWorkingTreeDiffs: () {
          workingLoads++;
          return workingLoads == 1
              ? Future.value([change('a.txt', 'first read')])
              : slow.future;
        },
        loadBranchDiffs: () async => [change('b.txt', 'branch read')],
      ),
    );

    await tester.pumpWidget(review());
    await tester.pumpAndSettle();
    expect(find.text('first read'), findsOneWidget);

    // Closed and opened again: the fresh read is slow, the last one shows.
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(review());
    await tester.pump();
    expect(find.text('first read'), findsOneWidget);

    // The branch view was loaded behind the first one.
    await tester.tap(find.text('Whole branch'));
    await tester.pump();
    expect(find.text('branch read'), findsOneWidget);
    slow.complete(const []);
    await tester.pumpAndSettle();
  });

  testWidgets('opens one working-tree file directly', (tester) async {
    var loads = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        home: ReviewWorkspace(
          initialScope: ReviewDiffScope.workingTree,
          initialFile: '/README.md',
          loadWorkingTreeDiffs: () async {
            loads++;
            return [
              FileDiff(
                file: 'lib/first.dart',
                patch: '@@ -0,0 +1 @@\n+first file',
                additions: 1,
                deletions: 0,
              ),
              FileDiff(
                file: 'README.md',
                patch: '@@ -1 +1 @@\n-old\n+selected readme',
                additions: 1,
                deletions: 1,
              ),
            ];
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(loads, 1);
    expect(find.byKey(const Key('review-scope-picker')), findsNothing);
    expect(find.text('selected readme'), findsOneWidget);
    expect(find.text('first file'), findsNothing);
  });

  testWidgets('viewed progress counts files that were on screen', (
    tester,
  ) async {
    await _pumpReview(
      tester,
      () async => [diff('lib/a.dart'), diff('lib/b.dart'), diff('lib/c.dart')],
    );

    // The file on screen first already counts as viewed.
    await _openFile(tester, 'lib/b.dart');

    // Going back to a viewed file does not count it twice.
    await _openFile(tester, 'lib/a.dart');
  });

  testWidgets('a large diff builds only the lines on screen', (tester) async {
    final patch = StringBuffer('@@ -1,1200 +1,1200 @@');
    for (var index = 1; index <= 1200; index++) {
      patch.write('\n+line $index');
    }
    await _pumpReview(
      tester,
      () async => [
        FileDiff(
          file: 'lib/large.dart',
          patch: patch.toString(),
          additions: 1200,
          deletions: 0,
        ),
      ],
    );

    expect(find.text('line 1'), findsOneWidget);
    expect(find.text('line 1000'), findsNothing);
  });

  group('the comment sheet keeps what was typed', () {
    Future<ReviewHandoffStore> pumpWithHandoff(
      WidgetTester tester, {
      String? profileId,
    }) async {
      final store = ReviewHandoffStore();
      await _pumpReview(
        tester,
        () async => [diff('lib/a.dart')],
        handoff: ReviewHandoffSession(store: store, sessionID: 'chat-1'),
        profileId: profileId,
      );
      return store;
    }

    Future<void> openComment(WidgetTester tester) async {
      await _openFileMenu(tester, 'lib/a.dart');
      await tester.tap(find.byKey(const Key('review-file-comment')));
      await tester.pumpAndSettle();
    }

    String fieldText(WidgetTester tester) => tester
        .widget<EditableText>(
          find.descendant(
            of: find.byKey(const Key('review-comment-field')),
            matching: find.byType(EditableText),
          ),
        )
        .controller
        .text;

    testWidgets('through typing, a swipe away and opening it again', (
      tester,
    ) async {
      final store = await pumpWithHandoff(tester);
      await openComment(tester);
      expect(find.byKey(const Key('review-comment-sheet')), findsOneWidget);
      // Nothing typed: the one action says why it waits.
      expect(find.text('Type a comment first.'), findsOneWidget);

      await tester.enterText(
        find.byKey(const Key('review-comment-field')),
        'Check the retry loop.',
      );
      await tester.pump();

      // Swiped down by its handle: nothing is asked, nothing is lost.
      final sheet = tester.getRect(
        find.byKey(const Key('review-comment-sheet')),
      );
      await tester.flingFrom(
        sheet.topCenter + const Offset(0, 8),
        const Offset(0, 700),
        2500,
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('review-comment-field')), findsNothing);
      expect(store.referencesFor('chat-1'), isEmpty);

      await openComment(tester);
      expect(fieldText(tester), 'Check the retry loop.');

      await tester.tap(find.byKey(const Key('review-add-to-prompt')));
      await tester.pumpAndSettle();
      expect(
        store.referencesFor('chat-1').single.comment,
        'Check the retry loop.',
      );

      // Used: the next comment starts empty.
      await openComment(tester);
      expect(fieldText(tester), isEmpty);
      KitUndo.commitPending();
      await tester.pumpAndSettle();
    });

    testWidgets('across closing and opening review again', (tester) async {
      await pumpWithHandoff(tester);
      await openComment(tester);
      await tester.enterText(
        find.byKey(const Key('review-comment-field')),
        'Keep this one.',
      );
      await tester.pump();
      await tester.pumpWidget(const SizedBox());

      await pumpWithHandoff(tester);
      await openComment(tester);
      expect(fieldText(tester), 'Keep this one.');
    });

    testWidgets('on disk under the profile when the profile is known', (
      tester,
    ) async {
      await pumpWithHandoff(tester, profileId: 'p1');
      await openComment(tester);
      await tester.enterText(
        find.byKey(const Key('review-comment-field')),
        'Saved for later.',
      );
      await tester.pumpAndSettle();
      final prefs = await SharedPreferences.getInstance();
      expect(
        prefs.getString('oc.draft.review.chat-1.lib/a.dart.p1'),
        'Saved for later.',
      );
    });
  });

  testWidgets('fits a 320 dp phone at 2x text without overflow', (
    tester,
  ) async {
    await _pumpReview(
      tester,
      () async => [
        FileDiff(
          file:
              '.github/workflows/a-very-long-android-quality-workflow-name.yml',
          patch: '@@ -0,0 +1,2 @@\n+name: Android quality\n+on: push',
          additions: 2,
          deletions: 0,
          status: 'added',
        ),
        diff('lib/b.dart'),
      ],
      size: const Size(320, 640),
      textScale: 2,
    );
    expect(tester.takeException(), isNull);
    expect(
      _header(
        '.github/workflows/a-very-long-android-quality-workflow-name.yml',
      ),
      findsOneWidget,
    );
  });
}
