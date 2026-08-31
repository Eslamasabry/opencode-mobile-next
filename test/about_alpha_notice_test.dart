import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/about_screen.dart' show AboutScreen, alphaNoticeBody;
import 'package:flutter_test/flutter_test.dart';

/// The alpha/vibecoded notice and the bug-report affordances on About. Runs
/// in its own file because the document `ListView` builds lazily against a
/// process-wide asset cache: reusing a file that already loaded the privacy
/// documents leaves the 320dp fixture showing an empty first viewport.
void main() {
  testWidgets('About shows the alpha/vibecoded notice and both report paths',
      (tester) async {
    await tester.pumpWidget(const MaterialApp(home: AboutScreen()));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('about-alpha-report-bug')),
        findsOneWidget);
    expect(find.byKey(const ValueKey('about-report-bug')), findsOneWidget);
    expect(find.text('Alpha · vibecoded'), findsOneWidget);
    expect(
        find.textContaining('built heavily with AI assistance'), findsOneWidget);
    expect(find.textContaining('not been hardware-tested'), findsOneWidget);
  });

  test('the notice copy keeps all three public claims', () {
    // Alpha, AI-assisted construction, untested desktop — none may quietly
    // disappear from the reader-facing copy.
    expect(alphaNoticeBody, contains('AI assistance'));
    expect(alphaNoticeBody, contains('public alpha'));
    expect(alphaNoticeBody, contains('not been hardware-tested'));
    expect(find.text('Alpha · vibecoded'), findsNothing,
        reason: 'the title is a widget concern, asserted in the test above');
  });
}
