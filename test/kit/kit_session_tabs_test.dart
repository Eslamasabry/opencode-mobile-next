// KitSessionTabs (a screen's open sessions as pills): behaviour, and the
// still-motion registration G8 requires of every kit part.
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

KitSessionTabs _tabs({
  int selected = 0,
  ValueChanged<int>? onTap,
  List<KitMenuItem> menu = const [],
}) => KitSessionTabs(
  semanticsLabel: 'Open terminals',
  endedLabel: 'ended',
  tabs: [
    for (final (i, name) in ['Shell 1', 'Shell 2', 'Shell 3'].indexed)
      KitSessionTab(
        tabKey: ValueKey('tab-$i'),
        label: name,
        selected: i == selected,
        ended: i == 2,
        onTap: () => onTap?.call(i),
        menu: menu,
      ),
  ],
);

void main() {
  kitMotionStillTests('KitSessionTabs', builds: {'default': () => _tabs()});

  testWidgets('a tap chooses that session', (tester) async {
    int? chosen;
    await tester.pumpWidget(_host(_tabs(onTap: (i) => chosen = i)));
    await tester.tap(find.text('Shell 2'));
    expect(chosen, 1);
  });

  testWidgets('each pill is a 48 dp target; the shown one is selected and an '
      'ended one says so', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_host(_tabs()));
    expect(
      tester.getSize(find.byKey(const ValueKey('tab-1'))).height,
      greaterThanOrEqualTo(48),
    );
    expect(
      tester.getSize(find.byKey(const ValueKey('tab-1'))).height,
      lessThan(64),
      reason: 'the target hugs its pill',
    );
    expect(
      tester.getSemantics(find.byKey(const ValueKey('tab-0'))),
      isSemantics(label: 'Shell 1', isSelected: true, isButton: true),
    );
    expect(find.bySemanticsLabel('Shell 3, ended'), findsOneWidget);
    handle.dispose();
  });

  testWidgets('long-press opens the session\'s own acts', (tester) async {
    var stopped = false;
    await tester.pumpWidget(
      _host(
        _tabs(
          menu: [
            KitMenuItem(
              label: 'Stop Shell 2',
              onSelected: () => stopped = true,
            ),
          ],
        ),
      ),
    );
    await tester.longPress(find.text('Shell 2'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Stop Shell 2'));
    await tester.pumpAndSettle();
    expect(stopped, isTrue);
  });

  testWidgets('many sessions slide sideways instead of wrapping', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        SizedBox(
          width: 200,
          child: KitSessionTabs(
            semanticsLabel: 'Open terminals',
            tabs: [
              for (var i = 0; i < 8; i++)
                KitSessionTab(
                  label: 'Terminal ${i + 1}',
                  selected: i == 0,
                  onTap: () {},
                ),
            ],
          ),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
    expect(find.byType(SingleChildScrollView), findsOneWidget);
  });
}
