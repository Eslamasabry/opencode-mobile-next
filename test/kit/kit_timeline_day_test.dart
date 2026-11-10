import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'kit_motion_still.dart';
import 'team_kit_test_support.dart';

void main() {
  kitMotionStillTests(
    'KitTimelineDay',
    builds: {
      for (final state in KitTeamState.values)
        state.name: () => teamSample('kit_timeline_day', state: state),
    },
  );
  testWidgets('long day folds after ten events', (tester) async {
    await pumpTeam(
      tester,
      KitTimelineDay(
        title: 'Today',
        status: '12 events',
        moreLabel: 'Show remaining events',
        items: [for (var i = 0; i < 12; i++) KitTeamItem(title: 'Event $i')],
      ),
    );
    expect(find.text('Event 10'), findsNothing);
    await tester.ensureVisible(find.text('Show remaining events'));
    await tester.tap(find.text('Show remaining events'));
    await tester.pumpAndSettle();
    expect(find.text('Event 10'), findsOneWidget);
  });
  testWidgets('wraps at 360 dp with 2x text', (tester) async {
    await pumpTeam(tester, teamSample('kit_timeline_day'), scale: 2);
    expect(tester.takeException(), isNull);
    expect(find.text('Milestone 2'), findsOneWidget);
  });
  testWidgets('action is controlled by its caller', (tester) async {
    var calls = 0;
    await pumpTeam(
      tester,
      teamSample('kit_timeline_day', action: () => calls++),
    );
    await tester.tap(find.text('Approve and start'));
    await tester.pump();
    expect(calls, 1);
  });
  testWidgets('last-known state suppresses mutations', (tester) async {
    await pumpTeam(
      tester,
      teamSample('kit_timeline_day', state: KitTeamState.stale),
    );
    expect(find.text('Approve and start'), findsNothing);
  });
}
