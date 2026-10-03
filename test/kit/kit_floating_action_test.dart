// KitFloatingAction (a floating primary over a list): its behaviour, and the still-motion registration G8
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
    'KitFloatingAction',
    builds: {
      'default': () => SizedBox(
        height: 320,
        width: 360,
        child: KitFloatingAction(
          label: 'New conversation',
          icon: AppIconography.add,
          onPressed: () {},
          child: const SizedBox.expand(),
        ),
      ),
    },
  );

  testWidgets('floats its words over the child and taps once', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      _host(
        KitFloatingAction(
          label: 'New conversation',
          onPressed: () => taps++,
          child: const SizedBox.expand(),
        ),
        scroll: false,
      ),
    );
    expect(find.text('New conversation'), findsOneWidget);
    await tester.tap(find.text('New conversation'));
    expect(taps, 1);
  });

  testWidgets('clears what is published at the bottom', (tester) async {
    await tester.pumpWidget(
      _host(
        KitBottomInset(
          insets: const KitClearance(bottom: 100),
          child: KitFloatingAction(
            label: 'New conversation',
            onPressed: () {},
            child: const SizedBox.expand(),
          ),
        ),
        scroll: false,
      ),
    );
    final bottom = tester.getBottomLeft(find.byType(KitButton)).dy;
    expect(bottom, lessThanOrEqualTo(600 - 100));
  });
}
