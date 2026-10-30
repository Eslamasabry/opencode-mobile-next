// KitAgentLine (the quiet "Agent · place" line that opens the choices): its
// behaviour, and the still-motion registration G8 requires of every kit part.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Widget _host(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: child),
);

KitAgentLine _line({VoidCallback? onPressed, String place = 'on this phone'}) =>
    KitAgentLine(
      tappableKey: const ValueKey('line'),
      agent: 'Claude Code',
      place: place,
      semanticsLabel: 'Claude Code on this phone. Change the agent.',
      onPressed: onPressed ?? () {},
    );

void main() {
  kitMotionStillTests('KitAgentLine', builds: {'default': () => _line()});

  testWidgets('says agent and place on one muted line and opens on tap', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(_host(_line(onPressed: () => taps++)));
    expect(find.text('Claude Code · on this phone'), findsOneWidget);
    await tester.tap(find.text('Claude Code · on this phone'));
    expect(taps, 1);
  });

  testWidgets('is at least 48 dp tall and has one semantics label', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_host(_line()));
    expect(
      tester.getSize(find.byKey(const ValueKey('line'))).height,
      greaterThanOrEqualTo(48),
    );
    expect(
      find.bySemanticsLabel('Claude Code on this phone. Change the agent.'),
      findsOneWidget,
    );
    handle.dispose();
  });

  testWidgets('a long place is cut to one line, never wrapped', (tester) async {
    await tester.pumpWidget(
      _host(SizedBox(width: 200, child: _line(place: 'a' * 80))),
    );
    expect(tester.takeException(), isNull);
  });
}
