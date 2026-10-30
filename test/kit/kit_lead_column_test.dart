// KitLeadColumn: a notice's words start on a list's 56 dp text rail, and
// under KitLeadColumn.compact (the conversation) on the steps' 48 dp rail
// (impeccable audit 2026-10-28, #8).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_lead_column.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart';

Future<void> _pump(WidgetTester tester, Widget child) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Column(children: [child]),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets("by default a notice's words start on the list's text rail", (
    tester,
  ) async {
    await _pump(tester, const KitNotice(message: 'Signed out'));
    expect(tester.getTopLeft(find.text('Signed out')).dx, 56);
  });

  testWidgets("compact: on the conversation's 48 dp rail", (tester) async {
    await _pump(
      tester,
      const KitLeadColumn.compact(child: KitNotice(message: 'No reply')),
    );
    expect(tester.getTopLeft(find.text('No reply')).dx, 48);
  });
}
