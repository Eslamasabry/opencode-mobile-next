import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';

Future<void> _pumpReview(
  WidgetTester tester,
  Future<List<FileDiff>> Function() loader, {
  Future<List<FileDiff>> Function()? workingTreeLoader,
  Future<List<FileDiff>> Function()? branchLoader,
  Size size = const Size(800, 700),
  double textScale = 1,
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
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('navigates files and supports unified and split review', (
    tester,
  ) async {
    final diffs = [
      FileDiff.fromJson({
        'file': 'lib/api/client.dart',
        'patch': '@@ -1,2 +1,2 @@\n-old client\n+new client\n same',
        'additions': 1,
        'deletions': 1,
        'status': 'modified',
      }),
      FileDiff.fromJson({
        'file': 'test/client_test.dart',
        'patch': '@@ -4,0 +5,2 @@\n+test one\n+test two',
        'additions': 2,
        'deletions': 0,
        'status': 'added',
      }),
    ];

    await _pumpReview(tester, () async => diffs);

    expect(find.text('2 changed files'), findsOneWidget);
    expect(find.byKey(const Key('review-file-strip')), findsOneWidget);
    expect(find.text('+new client'), findsOneWidget);

    await tester.tap(find.byKey(const Key('review-file-1')));
    await tester.pumpAndSettle();
    expect(find.text('+test one'), findsOneWidget);
    expect(find.text('+test two'), findsOneWidget);

    await tester.tap(find.byKey(const Key('review-mode-split')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-split-row-1')), findsOneWidget);
    expect(find.text('Split'), findsOneWidget);
  });

  testWidgets('disambiguates duplicate basenames in the phone file strip', (
    tester,
  ) async {
    await _pumpReview(
      tester,
      () async => [
        FileDiff(
          file: 'android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
          patch: '@@ -1 +1 @@\n-old v26\n+new v26',
          additions: 1,
          deletions: 1,
        ),
        FileDiff(
          file: 'android/app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml',
          patch: '@@ -1 +1 @@\n-old v33\n+new v33',
          additions: 1,
          deletions: 1,
        ),
      ],
      size: const Size(360, 700),
    );

    expect(find.text('ic_launcher.xml · mipmap-anydpi-v26'), findsOneWidget);
    expect(find.text('ic_launcher.xml · mipmap-anydpi-v33'), findsOneWidget);
    expect(
      find.bySemanticsLabel(
        RegExp(r'mipmap-anydpi-v26/ic_launcher\.xml, modified'),
      ),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const Key('review-file-1')));
    await tester.pumpAndSettle();
    expect(find.text('+new v33'), findsOneWidget);
  });

  testWidgets('shows a retryable error and a composed empty state', (
    tester,
  ) async {
    var attempts = 0;
    await _pumpReview(tester, () async {
      attempts++;
      if (attempts == 1) throw StateError('server unavailable');
      return const [];
    });

    expect(find.byKey(const Key('review-error')), findsOneWidget);
    expect(find.text('Bad state: server unavailable'), findsOneWidget);
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-empty')), findsOneWidget);
    expect(find.text('No changes to review'), findsOneWidget);
  });

  testWidgets('virtualizes a large diff instead of building every line', (
    tester,
  ) async {
    final patch = StringBuffer('@@ -1,1200 +1,1200 @@');
    for (var index = 1; index <= 1200; index++) {
      patch.write('\n line $index');
    }
    await _pumpReview(
      tester,
      () async => [
        FileDiff(
          file: 'lib/large.dart',
          patch: patch.toString(),
          additions: 0,
          deletions: 0,
        ),
      ],
    );

    expect(find.byKey(const Key('review-line-1')), findsOneWidget);
    expect(find.byKey(const Key('review-line-1000')), findsNothing);
  });

  testWidgets('switches among VCS working tree, branch, and session scopes', (
    tester,
  ) async {
    var sessionLoads = 0;
    var workingTreeLoads = 0;
    var branchLoads = 0;
    await _pumpReview(
      tester,
      () async {
        sessionLoads++;
        return [
          FileDiff(
            file: 'session.txt',
            patch: '@@ -0,0 +1 @@\n+session change',
            additions: 1,
            deletions: 0,
          ),
        ];
      },
      workingTreeLoader: () async {
        workingTreeLoads++;
        return [
          FileDiff(
            file: 'working.txt',
            patch: '@@ -0,0 +1 @@\n+working change',
            additions: 1,
            deletions: 0,
          ),
        ];
      },
      branchLoader: () async {
        branchLoads++;
        return [
          FileDiff(
            file: 'branch.txt',
            patch: '@@ -0,0 +1 @@\n+branch change',
            additions: 1,
            deletions: 0,
          ),
        ];
      },
    );

    expect(find.byKey(const Key('review-scope-picker')), findsOneWidget);
    expect(find.text('+session change'), findsOneWidget);
    expect(sessionLoads, 1);

    await tester.tap(find.text('Working tree'));
    await tester.pumpAndSettle();
    expect(find.text('+working change'), findsOneWidget);
    expect(workingTreeLoads, 1);

    await tester.tap(find.text('Branch'));
    await tester.pumpAndSettle();
    expect(find.text('+branch change'), findsOneWidget);
    expect(branchLoads, 1);

    await tester.tap(find.text('Session'));
    await tester.pumpAndSettle();
    expect(find.text('+session change'), findsOneWidget);
    expect(sessionLoads, 2);
  });

  testWidgets('opens one working-tree file directly without a fake session', (
    tester,
  ) async {
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
    expect(find.text('+selected readme'), findsOneWidget);
    expect(find.text('+first file'), findsNothing);
  });

  testWidgets('keeps review controls reachable at compact high text scale', (
    tester,
  ) async {
    await _pumpReview(
      tester,
      () async => [
        FileDiff(
          file: 'lib/compact.dart',
          patch: '@@ -1 +1 @@\n-before\n+after',
          additions: 1,
          deletions: 1,
        ),
      ],
      size: const Size(640, 320),
      textScale: 2,
    );

    expect(tester.takeException(), isNull);
    expect(find.byKey(const Key('review-file-strip')), findsOneWidget);
    expect(find.byKey(const Key('review-mode-unified')), findsOneWidget);
    expect(find.text('+after'), findsOneWidget);
  });

  testWidgets('fits primary review actions on a phone without a clipped strip', (
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
      ],
      size: const Size(360, 800),
    );

    expect(tester.takeException(), isNull);
    expect(find.byKey(const Key('review-phone-toolbar')), findsOneWidget);
    expect(find.byKey(const Key('review-current-file-name')), findsOneWidget);

    for (final key in const [
      Key('review-mode-unified'),
      Key('review-mode-split'),
      Key('review-previous-hunk'),
      Key('review-next-hunk'),
      Key('review-file-actions'),
    ]) {
      final rect = tester.getRect(find.byKey(key));
      expect(rect.left, greaterThanOrEqualTo(0), reason: '$key left edge');
      expect(rect.right, lessThanOrEqualTo(360), reason: '$key right edge');
    }

    await tester.tap(find.byKey(const Key('review-file-actions')));
    await tester.pumpAndSettle();
    expect(find.text('Ask about file'), findsOneWidget);
    expect(find.text('Copy patch'), findsOneWidget);
  });

  testWidgets('comment composer stays usable at 320dp height with 2x text', (
    tester,
  ) async {
    await _pumpReview(
      tester,
      () async => [
        FileDiff(
          file: 'lib/short.dart',
          patch: '@@ -1 +1 @@\n-before\n+after',
          additions: 1,
          deletions: 1,
        ),
      ],
      size: const Size(640, 320),
      textScale: 2,
    );

    // Compact-height toolbar exposes the whole-file Ask entry point.
    await tester.ensureVisible(find.text('Ask'));
    await tester.pump();
    final askRect = tester.getRect(find.text('Ask'));
    await tester.tapAt(askRect.center);
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byKey(const Key('review-comment-field')), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('review-comment-field')),
      'Tighten this diff',
    );
    await tester.pump();

    // The action row stays reachable by scrolling the capped sheet.
    await tester.ensureVisible(find.byKey(const Key('review-add-to-prompt')));
    await tester.pump();
    expect(tester.takeException(), isNull);
    await tester.tap(
      find.byKey(const Key('review-add-to-prompt')),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-comment-field')), findsNothing);
  });

  testWidgets('phone toolbar mode buttons meet 44dp targets', (tester) async {
    await _pumpReview(
      tester,
      () async => [
        FileDiff(
          file: 'lib/targets.dart',
          patch: '@@ -1 +1 @@\n-old\n+new',
          additions: 1,
          deletions: 1,
        ),
      ],
      size: const Size(360, 800),
    );

    expect(find.byKey(const Key('review-phone-toolbar')), findsOneWidget);
    for (final key in const [
      Key('review-mode-unified'),
      Key('review-mode-split'),
    ]) {
      final size = tester.getSize(find.byKey(key));
      expect(size.height, greaterThanOrEqualTo(44), reason: '$key height');
    }
  });

  testWidgets('phone layout mirrors hunk navigation into the bottom bar', (
    tester,
  ) async {
    final patch = StringBuffer();
    for (var hunk = 0; hunk < 6; hunk++) {
      patch.write('@@ -${hunk * 40 + 1},20 +${hunk * 40 + 1},20 @@\n');
      for (var line = 0; line < 20; line++) {
        patch.write(' context $hunk-$line\n');
      }
    }
    await _pumpReview(
      tester,
      () async => [
        FileDiff(
          file: 'lib/hunks.dart',
          patch: patch.toString().trimRight(),
          additions: 0,
          deletions: 0,
        ),
      ],
      size: const Size(360, 800),
    );

    expect(find.byKey(const Key('review-hunk-bar')), findsOneWidget);
    final next = find.byKey(const Key('review-hunk-bar-next'));
    final previous = find.byKey(const Key('review-hunk-bar-previous'));
    expect(next, findsOneWidget);
    expect(previous, findsOneWidget);

    expect(find.byKey(const Key('review-line-0')), findsOneWidget);
    await tester.tap(next);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('review-line-0')), findsNothing);
    expect(tester.takeException(), isNull);

    // Selecting lines swaps the bottom slot to the selection bar. Diff rows
    // are wider than the phone viewport, so tap near their left edge.
    final lineRect = tester.getRect(find.byKey(const Key('review-line-22')));
    await tester.tapAt(Offset(lineRect.left + 120, lineRect.center.dy));
    await tester.pump();
    expect(find.byKey(const Key('review-selection-bar')), findsOneWidget);
    expect(find.byKey(const Key('review-hunk-bar')), findsNothing);
  });

  testWidgets('diff pane refreshes with pull-to-refresh', (tester) async {
    var loads = 0;
    await _pumpReview(tester, () async {
      loads++;
      return [
        FileDiff(
          file: 'lib/refresh.dart',
          patch: '@@ -1 +1 @@\n-old load\n+load $loads',
          additions: 1,
          deletions: 1,
        ),
      ];
    }, size: const Size(360, 800));

    expect(loads, 1);
    expect(find.text('+load 1'), findsOneWidget);

    final rowRect = tester.getRect(find.byKey(const Key('review-line-0')));
    await tester.flingFrom(
      Offset(rowRect.left + 120, rowRect.top + 10),
      const Offset(0, 320),
      1000,
    );
    await tester.pumpAndSettle();

    expect(loads, 2);
    expect(find.text('+load 2'), findsOneWidget);
  });

  testWidgets('viewed progress counts files whose diff was opened', (
    tester,
  ) async {
    final diffs = [
      FileDiff.fromJson({
        'file': 'lib/a.dart',
        'patch': '@@ -1 +1 @@\n-a\n+A',
        'additions': 1,
        'deletions': 1,
        'status': 'modified',
      }),
      FileDiff.fromJson({
        'file': 'lib/b.dart',
        'patch': '@@ -1 +1 @@\n-b\n+B',
        'additions': 1,
        'deletions': 1,
        'status': 'modified',
      }),
      FileDiff.fromJson({
        'file': 'lib/c.dart',
        'patch': '@@ -1 +1 @@\n-c\n+C',
        'additions': 1,
        'deletions': 1,
        'status': 'modified',
      }),
    ];

    await _pumpReview(tester, () async => diffs);

    // The initially opened file already counts as viewed.
    expect(
      find.byKey(const ValueKey('review-viewed-progress')),
      findsOneWidget,
    );
    expect(find.text('1 of 3 viewed'), findsOneWidget);

    await tester.tap(find.byKey(const Key('review-file-1')));
    await tester.pumpAndSettle();
    expect(find.text('2 of 3 viewed'), findsOneWidget);

    // Re-opening an already viewed file does not double count.
    await tester.tap(find.byKey(const Key('review-file-0')));
    await tester.pumpAndSettle();
    expect(find.text('2 of 3 viewed'), findsOneWidget);
  });
}
