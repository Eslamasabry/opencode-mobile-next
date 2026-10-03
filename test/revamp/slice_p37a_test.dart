// slice-P3.7a "One diff component": every diff opens on KitDiffView with its
// one "Change 1 of N" navigator. Run results open a changed file's recorded
// diff directly (no record sheet in between) and walk the whole run's
// changes; the read-only diff page keeps "1 of 2" whole right to left.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';

const _cartPatch =
    '@@ -1,3 +1,3 @@\n'
    ' class Cart {\n'
    '-  int total = 0;\n'
    '+  int total = 1;\n'
    ' }\n';

const _apiPatch =
    '@@ -4,2 +4,2 @@\n'
    ' import "http";\n'
    '-const base = "v1";\n'
    '+const base = "v2";\n';

Widget _app(Widget home, {TextDirection direction = TextDirection.ltr}) =>
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) =>
          Directionality(textDirection: direction, child: child!),
      home: home,
    );

void main() {
  testWidgets('the diff page keeps "1 of 2" whole right to left', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _app(
        DiffPage(
          diffs: [
            FileDiff(file: 'lib/a.dart', patch: _cartPatch),
            FileDiff(file: 'lib/b.dart', patch: _apiPatch),
          ],
        ),
        direction: TextDirection.rtl,
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.textContaining(' · ${KitBidi.auto('1 of 2')}', findRichText: true),
      findsOneWidget,
    );
    // Only directional insets on the page's own parts: the header's start
    // is the right edge.
    final header = tester.getRect(
      find.byKey(const ValueKey('diff-file-header-lib/a.dart')),
    );
    final name = tester.getRect(find.textContaining('a.dart').first);
    expect(name.right, greaterThan(header.center.dx));
    expect(tester.takeException(), isNull);
  });
}
