// KitStatusTag (a worded state tag): its behaviour, and the still-motion registration G8
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
    'KitStatusTag',
    builds: {
      'default': () => Row(
        mainAxisSize: MainAxisSize.min,
        spacing: 12,
        children: const [
          KitStatusTag(label: 'Needs you', tone: KitStatusTagTone.needsYou),
          KitStatusTag(label: 'Running', tone: KitStatusTagTone.running),
          KitStatusTag(label: 'Done', tone: KitStatusTagTone.done),
        ],
      ),
    },
  );

  testWidgets('shows its word in both tones', (tester) async {
    await tester.pumpWidget(
      _host(
        const Column(
          children: [
            KitStatusTag(label: 'Needs you', tone: KitStatusTagTone.needsYou),
            KitStatusTag(label: 'Running', tone: KitStatusTagTone.running),
            KitStatusTag(label: 'Done', tone: KitStatusTagTone.done),
          ],
        ),
      ),
    );
    expect(find.text('Needs you'), findsOneWidget);
    expect(find.text('Running'), findsOneWidget);
    expect(find.text('Done'), findsOneWidget);
  });
}
