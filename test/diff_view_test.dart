import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/ui/widgets/diff_view.dart';

Future<void> _pump(WidgetTester tester, List<FileDiff> diffs) async {
  await tester.pumpWidget(MaterialApp(home: DiffView(diffs: diffs)));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('patch view shows a sticky file header, gutter numbers and '
      'a count-only gap between hunks', (tester) async {
    await _pump(tester, [
      FileDiff(
        file: 'lib/ui/widgets/markdown.dart',
        additions: 2,
        deletions: 1,
        patch:
            '--- a/lib/ui/widgets/markdown.dart\n'
            '+++ b/lib/ui/widgets/markdown.dart\n'
            '@@ -10,3 +10,4 @@\n'
            ' context ten\n'
            '-old eleven\n'
            '+new eleven\n'
            '+new twelve\n'
            ' context twelve\n'
            '@@ -60,2 +61,2 @@\n'
            ' context sixty\n'
            ' context sixty-one\n',
      ),
    ]);

    expect(find.byKey(const Key('diff-view')), findsOneWidget);
    expect(find.text('markdown.dart'), findsOneWidget);
    expect(find.textContaining('lib/ui/widgets/'), findsOneWidget);
    expect(find.text('+2'), findsOneWidget);
    expect(find.text('−1'), findsOneWidget);
    // Removed lines number by the old file, added by the new file.
    expect(find.text('11'), findsNWidgets(2));
    expect(find.text('12'), findsOneWidget);
    expect(find.text('old eleven'), findsOneWidget);
    expect(find.text('new twelve'), findsOneWidget);
    // The patch does not carry the skipped lines, so the gap only reports.
    expect(find.text('+47 lines'), findsOneWidget);
    expect(find.text('Expand'), findsNothing);
  });

  testWidgets('before/after pairs collapse context and expand 20 lines per '
      'tap', (tester) async {
    final before = List.generate(60, (i) => 'line ${i + 1}');
    final after = [...before]..[30] = 'changed line 31';
    await _pump(tester, [
      FileDiff(
        file: 'notes.txt',
        before: before.join('\n'),
        after: after.join('\n'),
      ),
    ]);

    expect(find.text('changed line 31'), findsOneWidget);
    expect(find.text('line 31'), findsOneWidget);
    // Three lines of context stay on each side; the rest folds.
    expect(find.text('line 30'), findsOneWidget);
    expect(find.text('line 27'), findsNothing);
    expect(find.text('+27 lines'), findsOneWidget);

    await tester.tap(find.byKey(const Key('diff-gap-0')));
    await tester.pumpAndSettle();
    // Chevron-up reveals the 20 lines just above the hunk.
    expect(find.text('+7 lines'), findsOneWidget);
    expect(find.text('line 8'), findsOneWidget);
    expect(find.text('line 7'), findsNothing);
  });

  testWidgets('code wraps below 600dp and scrolls sideways above it', (
    tester,
  ) async {
    final diff = FileDiff(
      file: 'a.dart',
      before: 'x',
      after: 'y ${'long ' * 80}',
    );
    await tester.binding.setSurfaceSize(const Size(400, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await _pump(tester, [diff]);
    expect(find.byKey(const Key('diff-view-horizontal')), findsNothing);

    await tester.binding.setSurfaceSize(const Size(900, 800));
    await _pump(tester, [diff]);
    expect(find.byKey(const Key('diff-view-horizontal')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
