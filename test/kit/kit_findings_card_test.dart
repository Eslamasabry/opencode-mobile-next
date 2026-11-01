import 'dart:ui' show CheckedState;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'kit_motion_still.dart';
import 'team_kit_test_support.dart';

void main() {
  kitMotionStillTests(
    'KitFindingsCard',
    builds: {
      for (final state in KitTeamState.values)
        state.name: () => teamSample('kit_findings_card', state: state),
    },
    changes: {
      'finding selection updates the action': KitMotionChange(
        build: () => KitFindingsCard(
          title: 'Review',
          status: '1 finding',
          findings: [
            KitTeamFinding(
              id: 'draft',
              title: 'Preserve the draft',
              severityLabel: 'Major',
              onChanged: (_) {},
            ),
          ],
        ),
        act: (tester, stage) => stage.rebuild(
          KitFindingsCard(
            title: 'Review',
            status: '1 finding',
            findings: [
              KitTeamFinding(
                id: 'draft',
                title: 'Preserve the draft',
                severityLabel: 'Major',
                selected: true,
                onChanged: (_) {},
              ),
            ],
            primary: KitAction(label: 'Fix 1 selected', onPressed: () {}),
          ),
        ),
        shows: 'Fix 1 selected',
      ),
      'work finishes': KitMotionChange(
        build: () =>
            teamSample('kit_findings_card', state: KitTeamState.running),
        act: (tester, stage) => stage.rebuild(
          teamSample('kit_findings_card', state: KitTeamState.done),
        ),
        shows: 'Accepted · 5 min ago',
      ),
    },
  );

  testWidgets('selection has checked semantics and retains keyboard focus', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    addTearDown(semantics.dispose);
    var selected = false;
    await pumpTeam(
      tester,
      StatefulBuilder(
        builder: (context, setState) => KitFindingsCard(
          title: 'Review',
          status: '1 finding',
          findings: [
            KitTeamFinding(
              id: 'draft',
              title: 'Preserve the draft',
              severityLabel: 'Major',
              selected: selected,
              onChanged: (value) => setState(() => selected = value),
            ),
          ],
        ),
      ),
    );
    final control = find.bySemanticsLabel('Major: Preserve the draft');
    expect(
      tester.getSemantics(control).flagsCollection.isChecked,
      CheckedState.isFalse,
    );
    await tester.tap(control);
    await tester.pumpAndSettle();
    expect(selected, isTrue);
    expect(
      tester.getSemantics(control).flagsCollection.isChecked,
      CheckedState.isTrue,
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    final focused = FocusManager.instance.primaryFocus;
    await tester.sendKeyEvent(LogicalKeyboardKey.space);
    await tester.pumpAndSettle();
    expect(selected, isFalse);
    expect(FocusManager.instance.primaryFocus, same(focused));
    expect(
      tester.getSemantics(control).flagsCollection.isChecked,
      CheckedState.isFalse,
    );
  });

  testWidgets('wraps at 360 dp with 2x text', (tester) async {
    await pumpTeam(tester, teamSample('kit_findings_card'), scale: 2);
    expect(tester.takeException(), isNull);
    expect(find.text('Milestone 2'), findsOneWidget);
  });
  testWidgets('action is controlled by its caller', (tester) async {
    var calls = 0;
    await pumpTeam(
      tester,
      teamSample('kit_findings_card', action: () => calls++),
    );
    await tester.tap(find.text('Approve and start'));
    await tester.pump();
    expect(calls, 1);
  });
  testWidgets('last-known state suppresses mutations', (tester) async {
    await pumpTeam(
      tester,
      teamSample('kit_findings_card', state: KitTeamState.stale),
    );
    expect(find.text('Approve and start'), findsNothing);
  });
}
