import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
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
  });

  group('review workspace handoff', () {
    testWidgets('a hunk header selects its lines and stages as a hunk', (
      tester,
    ) async {
      final store = await _pumpReview(tester, [
        FileDiff(
          file: 'lib/client.dart',
          patch: '@@ -8,2 +8,2 @@\n-old request\n+new request',
          additions: 1,
          deletions: 1,
        ),
      ]);

      await tester.tap(find.byKey(const Key('review-line-0')));
      await tester.pump();
      expect(find.byKey(const Key('review-selection-bar')), findsOneWidget);
      expect(find.textContaining('Hunk selected'), findsOneWidget);

      await tester.tap(find.byKey(const Key('review-selection-add')));
      await tester.pumpAndSettle();

      final staged = store.referencesFor('session-1').single;
      expect(staged.kind, ReviewReferenceKind.hunk);
      expect(staged.path, 'lib/client.dart');
      expect(staged.snippet, contains('+new request'));
      expect(staged.scope, ReviewReferenceScope.session);
      expect(find.byKey(const Key('review-staged-count')), findsOneWidget);
      expect(find.text('1 on prompt'), findsOneWidget);
    });

    testWidgets('the whole changed file stages from the toolbar', (
      tester,
    ) async {
      final store = await _pumpReview(tester, [
        FileDiff(
          file: 'lib/client.dart',
          patch: '@@ -8,2 +8,2 @@\n-old request\n+new request',
          additions: 1,
          deletions: 1,
          status: 'modified',
        ),
      ]);

      await tester.tap(find.byKey(const Key('review-add-file')));
      await tester.pumpAndSettle();

      final staged = store.referencesFor('session-1').single;
      expect(staged.kind, ReviewReferenceKind.changedFile);
      expect(staged.status, 'modified');
      expect(staged.lineLabel, isNull);

      // Staging the same file twice says so instead of duplicating it. The
      // first snack bar has to clear before the second can show.
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('review-add-file')));
      await tester.pumpAndSettle();
      expect(store.referencesFor('session-1'), hasLength(1));
      expect(find.textContaining('already on the prompt'), findsOneWidget);
    });

    testWidgets('a review comment stages and keeps review open', (
      tester,
    ) async {
      final store = await _pumpReview(tester, [
        FileDiff(
          file: 'lib/client.dart',
          patch: '@@ -8,2 +8,2 @@\n-old request\n+new request',
          additions: 1,
          deletions: 1,
        ),
      ]);

      await tester.tap(find.text('Ask about file'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('review-comment-field')),
        'Explain the retry change.',
      );
      await tester.pump();
      await tester.tap(find.byKey(const Key('review-add-to-prompt')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('review-workspace')), findsOneWidget);
      final staged = store.referencesFor('session-1').single;
      expect(staged.kind, ReviewReferenceKind.comment);
      expect(staged.comment, 'Explain the retry change.');
      expect(staged.snippet, isNull);
    });

    testWidgets('the phone selection bar stays inside a 360dp screen', (
      tester,
    ) async {
      await _pumpReview(tester, [
        FileDiff(
          file: 'lib/client.dart',
          patch: '@@ -8,2 +8,2 @@\n-old request\n+new request',
          additions: 1,
          deletions: 1,
        ),
      ], size: const Size(360, 800));

      final lineRect = tester.getRect(find.byKey(const Key('review-line-1')));
      await tester.tapAt(Offset(lineRect.left + 120, lineRect.center.dy));
      await tester.pump();

      expect(find.byKey(const Key('review-selection-bar')), findsOneWidget);
      for (final key in const [
        Key('review-selection-clear'),
        Key('review-selection-copy'),
        Key('review-selection-add'),
      ]) {
        final rect = tester.getRect(find.byKey(key));
        expect(rect.left, greaterThanOrEqualTo(0), reason: '$key left');
        expect(rect.right, lessThanOrEqualTo(360), reason: '$key right');
        expect(rect.height, greaterThanOrEqualTo(44), reason: '$key height');
      }
      expect(tester.takeException(), isNull);
    });
  });
}
