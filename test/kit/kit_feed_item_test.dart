// KitFeedItem (one conversation in a feed: project, title, state or time, last line): its behaviour, and the still-motion registration G8
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
    'KitFeedItem',
    builds: {
      'default': () => KitRowGroup(
        leadingIcons: false,
        children: [
          KitFeedItem(
            project: 'alpha',
            gitLabel: 'Git',
            title: 'Fix the login bug',
            preview: 'Allow running the tests?',
            tag: const KitStatusTag(
              label: 'Needs you',
              tone: KitStatusTagTone.needsYou,
            ),
            onTap: () {},
          ),
          KitFeedItem(
            project: 'beta',
            title: 'Add dark mode',
            preview: 'Editing lib/ui/theme.dart',
            tag: const KitStatusTag(
              label: 'Running',
              tone: KitStatusTagTone.running,
            ),
            onTap: () {},
          ),
          KitFeedItem(
            project: 'alpha',
            gitLabel: 'Git',
            title: 'Rename the settings page and move its tests next to it',
            preview: 'Done. Four files changed.',
            time: '3d ago',
            onTap: () {},
          ),
          KitFeedItem(
            project: 'gamma',
            title: 'Plan the release notes',
            time: 'Sep 24',
            onTap: () {},
          ),
        ],
      ),
    },
  );

  testWidgets('says project, title, tag and last line, and taps once', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(
      _host(
        KitFeedItem(
          project: 'alpha',
          gitLabel: 'Git',
          title: 'Fix the login bug',
          preview: 'Allow running the tests?',
          tag: const KitStatusTag(
            label: 'Needs you',
            tone: KitStatusTagTone.needsYou,
          ),
          time: 'ignored',
          onTap: () => taps++,
        ),
      ),
    );
    expect(find.text('alpha'.isolated), findsOneWidget);
    expect(find.text('Git'), findsOneWidget);
    expect(find.text('Fix the login bug'.isolated), findsOneWidget);
    expect(find.text('Needs you'), findsOneWidget);
    expect(find.text('ignored'), findsNothing);
    expect(find.text('Allow running the tests?'.isolated), findsOneWidget);
    await tester.tap(find.text('Fix the login bug'.isolated));
    expect(taps, 1);
  });

  testWidgets('without a tag it shows the time; empty preview shows none', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        KitFeedItem(
          project: 'beta',
          title: 'Plan',
          time: '3d ago',
          onTap: () {},
        ),
      ),
    );
    expect(find.text('3d ago'), findsOneWidget);
    expect(find.text('Git'), findsNothing);
  });

  testWidgets('reads as one sentence to a screen reader', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(
      _host(
        KitFeedItem(
          project: 'alpha',
          gitLabel: 'Git',
          title: 'Fix',
          preview: 'Done',
          time: '5 min ago',
          onTap: () {},
        ),
      ),
    );
    expect(
      find.bySemanticsLabel('alpha, Git, Fix, 5 min ago, Done'),
      findsOneWidget,
    );
    handle.dispose();
  });
}
