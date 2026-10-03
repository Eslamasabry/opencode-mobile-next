// KitFilterChip (one on/off filter chip): its behaviour, and the still-motion registration G8
// requires of every kit part.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

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
    'KitFilterChip',
    builds: {
      'default': () => Wrap(
        spacing: 8,
        children: [
          KitFilterChip(label: 'Running', selected: false, onPressed: () {}),
          KitFilterChip(label: 'Running', selected: true, onPressed: () {}),
          KitFilterChip(
            label: 'Needs you · 1',
            needsYou: true,
            selected: true,
            onPressed: () {},
          ),
        ],
      ),
    },
  );

  testWidgets('toggles by its tap and says its state', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      _host(
        KitFilterChip(
          label: 'Running',
          selected: true,
          onPressed: () => taps++,
        ),
      ),
    );
    expect(find.text('Running'), findsOneWidget);
    await tester.tap(find.text('Running'));
    expect(taps, 1);
  });
}
