// KitAgentGrid (agent cards in two columns): layout, and the still-motion
// registration G8 requires.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_persona.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Widget _host(Widget child, {TextDirection dir = TextDirection.ltr}) =>
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Directionality(
        textDirection: dir,
        child: Scaffold(body: SingleChildScrollView(child: child)),
      ),
    );

KitAgentGrid _grid(int count) => KitAgentGrid(
  children: [
    for (final p in AgentPersona.all.take(count))
      KitAgentChoice(
        tappableKey: ValueKey(p.id.name),
        persona: p,
        state: KitAgentChoiceState.available,
        onPressed: () {},
      ),
  ],
);

void main() {
  kitMotionStillTests('KitAgentGrid', builds: {'three': () => _grid(3)});

  testWidgets('two columns, equal heights, an odd last one spans both', (
    tester,
  ) async {
    await tester.pumpWidget(_host(_grid(3)));
    final a = tester.getRect(find.byKey(const ValueKey('claudeCode')));
    final b = tester.getRect(find.byKey(const ValueKey('codex')));
    final c = tester.getRect(find.byKey(const ValueKey('gemini')));
    expect(a.top, b.top);
    expect(a.height, b.height);
    expect(a.width, closeTo(b.width, .5));
    expect(c.top, greaterThan(a.bottom));
    expect(c.left, a.left);
    expect(c.right, b.right);
  });

  testWidgets('right to left puts the first card at the right', (tester) async {
    await tester.pumpWidget(_host(_grid(2), dir: TextDirection.rtl));
    final a = tester.getRect(find.byKey(const ValueKey('claudeCode')));
    final b = tester.getRect(find.byKey(const ValueKey('codex')));
    expect(a.left, greaterThan(b.left));
  });
}
