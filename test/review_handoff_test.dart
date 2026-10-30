import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';

ReviewReference _reference({
  required String id,
  ReviewReferenceKind kind = ReviewReferenceKind.selection,
  String path = 'lib/client.dart',
  String? lineLabel,
  String? snippet,
  String? comment,
  ReviewReferenceScope scope = ReviewReferenceScope.none,
}) => ReviewReference(
  id: id,
  kind: kind,
  path: path,
  lineLabel: lineLabel,
  snippet: snippet,
  comment: comment,
  scope: scope,
);

Future<ReviewHandoffStore> _pumpReview(
  WidgetTester tester,
  List<FileDiff> diffs, {
  Size size = const Size(800, 700),
}) async {
  final store = ReviewHandoffStore();
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      home: ReviewWorkspace(
        loadDiffs: () async => diffs,
        handoff: ReviewHandoffSession(store: store, sessionID: 'session-1'),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return store;
}

void main() {
  group('ReviewHandoffStore', () {
    test('stages references per session and reports duplicates', () {
      final store = ReviewHandoffStore();
      expect(
        store.stage('a', _reference(id: '1', lineLabel: 'new line 8')),
        ReviewStageOutcome.staged,
      );
      expect(
        store.stage('a', _reference(id: '2', lineLabel: 'new line 8')),
        ReviewStageOutcome.duplicate,
      );
      expect(store.referencesFor('a'), hasLength(1));
      expect(store.referencesFor('b'), isEmpty);
      expect(store.hasReferences('b'), isFalse);
    });

    test('two comments on the same range stay distinct', () {
      final store = ReviewHandoffStore();
      for (final comment in ['first note', 'second note']) {
        store.stage(
          'a',
          _reference(
            id: store.nextID('c'),
            kind: ReviewReferenceKind.comment,
            lineLabel: 'new line 8',
            comment: comment,
          ),
        );
      }
      expect(store.referencesFor('a'), hasLength(2));
    });

    test('caps a session and notifies listeners on change', () {
      final store = ReviewHandoffStore();
      var notifications = 0;
      store.addListener(() => notifications++);
      for (var i = 0; i < ReviewHandoffStore.maxPerSession; i++) {
        expect(
          store.stage('a', _reference(id: '$i', lineLabel: 'line $i')),
          ReviewStageOutcome.staged,
        );
      }
      expect(
        store.stage('a', _reference(id: 'extra', lineLabel: 'line extra')),
        ReviewStageOutcome.full,
      );
      expect(notifications, ReviewHandoffStore.maxPerSession);

      store.remove('a', '0');
      expect(store.referencesFor('a'), hasLength(9));
      expect(notifications, ReviewHandoffStore.maxPerSession + 1);

      expect(store.take('a'), hasLength(9));
      expect(store.referencesFor('a'), isEmpty);
    });

    test('removing an unknown id does not notify', () {
      final store = ReviewHandoffStore();
      var notifications = 0;
      store.stage('a', _reference(id: '1'));
      store.addListener(() => notifications++);
      store.remove('a', 'missing');
      store.remove('other-session', '1');
      expect(notifications, 0);
    });
  });

  group('ReviewReference', () {
    test('labels a reference by file and line range', () {
      final reference = _reference(
        id: '1',
        lineLabel: 'new lines 8–12',
        scope: ReviewReferenceScope.workingTree,
      );
      expect(reference.label, 'client.dart · new lines 8–12');
      expect(
        reference.description,
        'Selected lines · lib/client.dart · new lines 8–12 · working tree',
      );
    });

    test('formats structured markdown rather than pre-rendered prose', () {
      final block = ReviewReference.format([
        _reference(
          id: '1',
          kind: ReviewReferenceKind.comment,
          lineLabel: 'new line 8',
          snippet: '+new request',
          comment: 'Keep the retry behavior explicit.',
          scope: ReviewReferenceScope.session,
        ),
        _reference(
          id: '2',
          kind: ReviewReferenceKind.file,
          path: 'lib/main.dart',
        ),
      ]);
      expect(block, startsWith('References:'));
      expect(block, contains('`lib/client.dart` (new line 8 · session'));
      expect(block, contains('Keep the retry behavior explicit.'));
      expect(block, contains('```diff\n+new request\n```'));
      expect(block, contains('`lib/main.dart`'));
    });

    test('a single reference reads in the singular and empty stays empty', () {
      expect(
        ReviewReference.format([_reference(id: '1')]),
        startsWith('Reference:'),
      );
      expect(ReviewReference.format(const []), isEmpty);
    });

    test('a snippet containing a fence gets a longer fence', () {
      final block = ReviewReference.format([
        _reference(id: '1', snippet: '+```dart\n+final x = 1;\n+```'),
      ]);
      // A three-backtick fence would be closed by the snippet's own fence,
      // spilling the tail of the diff into the prompt as prose.
      expect(block, contains('````diff\n+```dart'));
      expect(block, endsWith('+```\n````'));
    });

    test('an even longer run pushes the fence out further', () {
      final block = ReviewReference.format([
        _reference(id: '1', snippet: '+`````\n+edge'),
      ]);
      expect(block, contains('``````diff\n'));
    });

    test('the send-wide snippet budget truncates and says it did', () {
      final huge = List.generate(400, (line) => '+line $line').join('\n');
      final block = ReviewReference.format([
        _reference(id: '1', snippet: huge),
      ], budget: 200);

      expect(block.length, lessThan(600));
      expect(block, contains('+line 0'));
      expect(block, isNot(contains('+line 399')));
      expect(block, contains('Snippet truncated to fit the prompt'));
      expect(block, contains('of 400 lines'));
      expect(block, contains('characters omitted'));
      // The truncation notice names the file, so the agent can go read it.
      expect(block, contains('Read `lib/client.dart` for the rest.'));
      // Whole lines only: a half-line of diff is a lie about the diff.
      expect(block, isNot(contains('+line 1\n+li\n')));
    });

    test('a small reference is never starved by a large one', () {
      final huge = List.generate(400, (line) => '+huge $line').join('\n');
      final block = ReviewReference.format([
        _reference(id: '1', path: 'lib/small.dart', snippet: '+tiny change'),
        _reference(id: '2', path: 'lib/huge.dart', snippet: huge),
      ], budget: 400);

      // The small snippet fits its share whole; the large one absorbs the
      // truncation and discloses it.
      expect(block, contains('+tiny change'));
      expect(
        'Snippet truncated'.allMatches(block).length,
        1,
        reason: 'only the oversized reference is cut',
      );
      expect(block, contains('Read `lib/huge.dart` for the rest.'));
    });

    test('a zero budget keeps the pointer and drops the body', () {
      final block = ReviewReference.format([
        _reference(id: '1', snippet: '+one\n+two'),
      ], budget: 0);

      expect(block, contains('`lib/client.dart`'));
      expect(block, isNot(contains('+one')));
      expect(block, contains('Snippet truncated to fit the prompt'));
      expect(block, contains('showing 0 of 2 lines'));
    });

    test('the default budget bounds ten maximum references', () {
      final page = List.generate(4000, (line) => '+line $line').join('\n');
      final block = ReviewReference.format([
        for (var i = 0; i < ReviewHandoffStore.maxPerSession; i++)
          _reference(id: '$i', path: 'lib/file$i.dart', snippet: page),
      ]);

      expect(
        block.length,
        lessThan(ReviewReference.maxSnippetChars + 4000),
        reason: 'the cap has to hold for a full session of large diffs',
      );
    });
  });

  group('review workspace handoff', () {
    FileDiff client({String? status}) => FileDiff(
      file: 'lib/client.dart',
      patch: '@@ -8,2 +8,2 @@\n-old request\n+new request\n keep',
      additions: 1,
      deletions: 1,
      status: status,
    );

    // The Undo bar's window is a timer; close it before the test ends.
    Future<void> settleUndo(WidgetTester tester) async {
      KitUndo.commitPending();
      await tester.pumpAndSettle();
    }

    // The file's menu is a long press on its row.
    Future<void> openFileMenu(WidgetTester tester) async {
      await tester.longPress(
        find.byKey(const ValueKey('review-fold-header-lib/client.dart')),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('selected lines stage on the prompt, with Undo', (
      tester,
    ) async {
      final store = await _pumpReview(tester, [client()]);

      await tester.tap(find.byKey(const ValueKey('review-line-0-current-8')));
      await tester.pump();
      expect(
        find.byKey(const ValueKey('review-selection-bar')),
        findsOneWidget,
      );

      await tester.tap(find.text('Add to prompt'));
      await tester.pump();

      final staged = store.referencesFor('session-1').single;
      expect(staged.kind, ReviewReferenceKind.selection);
      expect(staged.path, 'lib/client.dart');
      expect(staged.lineLabel, 'new line 8');
      expect(staged.snippet, 'new request');
      expect(staged.scope, ReviewReferenceScope.session);
      // Said in the bar's subtitle; no snack bar, the kit's Undo bar.
      expect(find.textContaining('1 on prompt'), findsOneWidget);
      expect(find.byType(SnackBar), findsNothing);
      expect(find.byKey(const Key('review-staged-undo')), findsOneWidget);

      await tester.tap(find.text('Undo'));
      await tester.pumpAndSettle();
      expect(store.referencesFor('session-1'), isEmpty);
    });

    testWidgets('the whole changed file stages from its menu', (tester) async {
      final store = await _pumpReview(tester, [client(status: 'modified')]);

      await openFileMenu(tester);
      await tester.tap(find.byKey(const Key('review-add-file')));
      await tester.pumpAndSettle();

      final staged = store.referencesFor('session-1').single;
      expect(staged.kind, ReviewReferenceKind.changedFile);
      expect(staged.status, 'modified');
      expect(staged.lineLabel, isNull);

      // Staging the same file twice says so instead of duplicating it.
      await openFileMenu(tester);
      await tester.tap(find.byKey(const Key('review-add-file')));
      await tester.pumpAndSettle();
      expect(store.referencesFor('session-1'), hasLength(1));
      expect(find.byKey(const Key('review-staged-notice')), findsOneWidget);
      expect(find.textContaining('already on the prompt'), findsOneWidget);
      await settleUndo(tester);
    });

    testWidgets('a review comment stages and keeps review open', (
      tester,
    ) async {
      final store = await _pumpReview(tester, [client()]);

      await openFileMenu(tester);
      await tester.tap(find.byKey(const Key('review-file-comment')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('review-comment-field')),
        'Explain the retry change.',
      );
      await tester.pump();
      await tester.tap(find.byKey(const Key('review-add-to-prompt')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('review-workspace')), findsOneWidget);
      expect(find.byKey(const Key('review-comment-field')), findsNothing);
      final staged = store.referencesFor('session-1').single;
      expect(staged.kind, ReviewReferenceKind.comment);
      expect(staged.comment, 'Explain the retry change.');
      expect(staged.snippet, isNull);
      await settleUndo(tester);
    });

    testWidgets('a comment on selected lines quotes them', (tester) async {
      final store = await _pumpReview(tester, [client()]);

      await tester.tap(find.byKey(const ValueKey('review-line-0-current-8')));
      await tester.pump();
      await tester.tap(find.text('Comment'));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('review-comment-quote')), findsOneWidget);
      expect(find.text('new line 8'), findsOneWidget);
      await tester.enterText(
        find.byKey(const Key('review-comment-field')),
        'Why rename it?',
      );
      await tester.pump();
      await tester.tap(find.byKey(const Key('review-add-to-prompt')));
      await tester.pumpAndSettle();

      final staged = store.referencesFor('session-1').single;
      expect(staged.kind, ReviewReferenceKind.comment);
      expect(staged.lineLabel, 'new line 8');
      expect(staged.snippet, 'new request');
      await settleUndo(tester);
    });

    testWidgets('every file seen with notes waiting offers the way back', (
      tester,
    ) async {
      final store = await _pumpReview(tester, [client()]);
      expect(find.byKey(const Key('review-all-viewed')), findsNothing);

      await openFileMenu(tester);
      await tester.tap(find.byKey(const Key('review-add-file')));
      await tester.pumpAndSettle();

      expect(store.referencesFor('session-1'), hasLength(1));
      expect(find.byKey(const Key('review-all-viewed')), findsOneWidget);
      expect(find.text("You've seen every file"), findsOneWidget);
      expect(find.byKey(const Key('review-back-to-chat')), findsOneWidget);
      await settleUndo(tester);
    });

    testWidgets('the phone selection bar stays inside a 360dp screen', (
      tester,
    ) async {
      await _pumpReview(tester, [client()], size: const Size(360, 800));

      await tester.tap(find.byKey(const ValueKey('review-line-0-current-8')));
      await tester.pump();

      expect(
        find.byKey(const ValueKey('review-selection-bar')),
        findsOneWidget,
      );
      for (final label in const ['Comment', 'Add to prompt', 'Copy lines']) {
        final rect = tester.getRect(find.text(label));
        expect(rect.left, greaterThanOrEqualTo(0), reason: '$label left');
        expect(rect.right, lessThanOrEqualTo(360), reason: '$label right');
      }
      expect(tester.takeException(), isNull);
    });
  });
}
