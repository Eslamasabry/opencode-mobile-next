// KitAgentChoice (an agent as a card in the start grid): its words and mark
// come from the persona, its states, and the still-motion registration.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_persona.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Widget _host(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(
    body: Center(child: SizedBox(width: 180, child: child)),
  ),
);

KitAgentChoice _card({
  AgentPersona persona = AgentPersona.claudeCode,
  KitAgentChoiceState state = KitAgentChoiceState.available,
  VoidCallback? onPressed,
  String? otherName,
}) => KitAgentChoice(
  tappableKey: const ValueKey('card'),
  persona: persona,
  state: state,
  otherName: otherName,
  onPressed: onPressed ?? () {},
);

void main() {
  kitMotionStillTests(
    'KitAgentChoice',
    builds: {
      'available': () => _card(),
      'selected': () => _card(state: KitAgentChoiceState.selected),
      'needs setup': () => _card(state: KitAgentChoiceState.needsSetup),
    },
    changes: {
      'gets selected': KitMotionChange(
        build: () => _card(),
        act: (tester, stage) =>
            stage.rebuild(_card(state: KitAgentChoiceState.selected)),
        shows: 'Claude Code',
      ),
    },
  );

  testWidgets('says the persona name and its one line, and taps once', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(_host(_card(onPressed: () => taps++)));
    expect(find.text('Claude Code'), findsOneWidget);
    expect(find.text('Careful with big changes'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('card')));
    expect(taps, 1);
  });

  testWidgets('an agent with no persona shows its own name and no line', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(_card(persona: AgentPersona.other, otherName: 'Zed')),
    );
    expect(find.text('Zed'), findsOneWidget);
    expect(find.text('Another agent'), findsNothing);
  });

  testWidgets('one not set up says Set up in place of its line', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(_card(state: KitAgentChoiceState.needsSetup)),
    );
    expect(find.text('Set up'), findsOneWidget);
    expect(find.text('Careful with big changes'), findsNothing);
  });

  testWidgets('is one 48 dp target with name and line as its label', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_host(_card(state: KitAgentChoiceState.selected)));
    expect(
      tester.getSize(find.byKey(const ValueKey('card'))).height,
      greaterThanOrEqualTo(48),
    );
    expect(
      find.bySemanticsLabel('Claude Code. Careful with big changes'),
      findsOneWidget,
    );
    handle.dispose();
  });

  double badge(WidgetTester tester) => tester
      .widget<AnimatedOpacity>(
        find
            .ancestor(
              of: find.byIcon(AppIconography.check),
              matching: find.byType(AnimatedOpacity),
            )
            .first,
      )
      .opacity;

  testWidgets('only the chosen one shows its check badge', (tester) async {
    await tester.pumpWidget(_host(_card()));
    expect(badge(tester), 0);
    await tester.pumpWidget(_host(_card(state: KitAgentChoiceState.selected)));
    await tester.pumpAndSettle();
    expect(badge(tester), 1);
  });

  testWidgets('Solid marks the chosen card one way: its check, no tint and '
      'no ring', (tester) async {
    await tester.pumpWidget(_host(_card(state: KitAgentChoiceState.selected)));
    await tester.pumpAndSettle();
    final roles = KitTokens.of(
      tester.element(find.byType(KitAgentChoice)),
    ).roles;
    final faces = [
      for (final box in tester.widgetList<DecoratedBox>(
        find.descendant(
          of: find.byType(KitAgentChoice),
          matching: find.byType(DecoratedBox),
        ),
      ))
        if (box.decoration case final BoxDecoration d when d.border != null) d,
    ];
    expect(faces, isNotEmpty);
    final face = faces.first;
    // The same surface as an unchosen card: no accent breathed into it.
    expect(face.color, roles.surface1);
    // The same hairline: no accent ring.
    expect((face.border! as Border).top.color, roles.hairline);
    // The check still says which one is chosen, so never colour alone.
    expect(badge(tester), 1);
  });

  testWidgets('spanning a row it lays out sideways: mark beside the name', (
    tester,
  ) async {
    await tester.pumpWidget(_host(KitAgentGridSpan(child: _card())));
    final mark = tester.getRect(find.byType(KitIcon).first);
    final name = tester.getRect(find.text('Claude Code'));
    expect(mark.right, lessThan(name.left));
    await tester.pumpWidget(_host(_card()));
    final stackedMark = tester.getRect(find.byType(KitIcon).first);
    final stackedName = tester.getRect(find.text('Claude Code'));
    expect(stackedMark.bottom, lessThan(stackedName.top));
  });
}
