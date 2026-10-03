// KitFilterChips (one scrolling line of filter chips): its behaviour, and the still-motion registration G8
// requires of every kit part.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

extension on String {
  String get isolated => KitBidi.auto(this);
}

Widget _host(Widget child, {double textScale = 1, bool scroll = true}) =>
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, app) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: app!,
      ),
      home: Scaffold(body: scroll ? ListView(children: [child]) : child),
    );

void main() {
  kitMotionStillTests(
    'KitFilterChips',
    builds: {
      'default': () => KitFilterChips(
        chips: [
          KitChip.summary(
            label: 'All projects',
            expanded: false,
            onPressed: () {},
          ),
          KitFilterChip(
            label: 'Needs you · 2',
            needsYou: true,
            selected: true,
            onPressed: () {},
          ),
          KitFilterChip(label: 'Running', selected: false, onPressed: () {}),
        ],
      ),
    },
  );

  testWidgets('lays chips on one line that scrolls when they do not fit', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        KitFilterChips(
          chips: [
            for (var i = 0; i < 12; i++)
              KitFilterChip(
                label: 'Filter number $i',
                selected: false,
                onPressed: () {},
              ),
          ],
        ),
      ),
    );
    expect(find.text('Filter number 0'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.drag(find.byType(KitFilterChips), const Offset(-600, 0));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });
}
