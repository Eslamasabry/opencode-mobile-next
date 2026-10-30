// The changes screen starts with one plain sentence (vision decision 6,
// gaps 2026-10-21): the agent's own words for its last turn, or the
// conversation's title, with "3 files · +42 −8" under it as the counts
// line. With no sentence the counts line stands alone.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:shared_preferences/shared_preferences.dart';

FileDiff _diff(String file) => FileDiff(
  file: file,
  patch: '@@ -1 +1 @@\n-old\n+$file',
  additions: 1,
  deletions: 1,
);

Future<void> _pump(WidgetTester tester, {String? summary}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      home: ReviewWorkspace(
        loadDiffs: () async => [_diff('a.dart'), _diff('b.dart')],
        summary: summary,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

KitTextRole _roleOf(WidgetTester tester, String text) => tester
    .widget<KitText>(
      find.ancestor(of: find.text(text), matching: find.byType(KitText)).first,
    )
    .role;

void main() {
  setUp(() {
    ReviewWorkspace.clearCache();
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('the sentence comes first, the counts line under it', (
    tester,
  ) async {
    await _pump(tester, summary: '  Added a combo counter.  ');
    final sentence = find.byKey(const Key('review-summary-sentence'));
    expect(sentence, findsOneWidget);
    expect(find.textContaining('Added a combo counter.'), findsOneWidget);
    expect(find.text('2 files changed · +2 −2'), findsOneWidget);
    expect(
      tester.getTopLeft(sentence).dy,
      lessThan(tester.getTopLeft(find.text('2 files changed · +2 −2')).dy),
    );
    expect(_roleOf(tester, '2 files changed · +2 −2'), KitTextRole.secondary);
  });

  testWidgets('with no sentence the counts line stands alone', (tester) async {
    await _pump(tester, summary: '   ');
    expect(find.byKey(const Key('review-summary-sentence')), findsNothing);
    expect(find.text('2 files changed · +2 −2'), findsOneWidget);
    expect(_roleOf(tester, '2 files changed · +2 −2'), KitTextRole.headline);
  });
}
