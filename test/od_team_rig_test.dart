// OD1 team lane, features 2 and 3: a project (Gas City rig) has its own page
// on the team home. Pause names the project and says what stops; resume is
// one tap; delete names the project and says what goes and what stays. Each
// tap reaches the gateway with the right arguments (the wire routes are
// proven in od_team_gateway_test.dart).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'coverage/team_support.dart';
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'od_team_support.dart';

Future<void> _openRig(WidgetTester tester, OdFixture host) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(odApp(TeamHomeScreen(controller: host.team)));
  await teamSettle(tester);
  final row = find.byKey(const ValueKey('team-home-project-ocproof'));
  await tester.ensureVisible(row);
  await odCapture(tester, 'home-projects');
  await tester.tap(row);
  await teamSettle(tester);
  expect(find.byKey(const ValueKey('team-rig')), findsOneWidget);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late OdFixture host;

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() async => host = await OdFixture.boot());
  tearDown(() async => host.close());

  testWidgets('pause asks first, names the project and says what stops', (
    tester,
  ) async {
    await _openRig(tester, host);
    expect(find.text('Active'), findsWidgets);
    await odCapture(tester, 'rig-page');
    await tester.tap(find.byKey(const ValueKey('team-rig-pause')));
    await teamSettle(tester);
    expect(find.text('Pause ocproof?'), findsOneWidget);
    expect(
      find.textContaining('No new work starts in “ocproof”'),
      findsOneWidget,
    );
    expect(
      host.gateway.controlCalls,
      isEmpty,
      reason: 'nothing is sent before the yes',
    );
    await odCapture(tester, 'rig-pause-confirm');
    await tester.tap(
      find.byKey(const ValueKey('team-rig-pause-confirm-action')),
    );
    await teamSettle(tester);
    final call = host.gateway.controlCalls.single;
    expect(call.verb, 'controlProject');
    expect(call.target, 'ocproof');
    expect(call.arg, ProjectControlAction.suspend);
    expect(find.byKey(const ValueKey('team-rig-paused')), findsOneWidget);
    expect(find.text('Resume project ocproof'), findsOneWidget);
    await odCapture(tester, 'rig-paused');

    // Resume is harmless: one tap, no question.
    await tester.tap(find.byKey(const ValueKey('team-rig-resume')));
    await teamSettle(tester);
    expect(host.gateway.controlCalls.last.arg, ProjectControlAction.resume);
    expect(find.byKey(const ValueKey('team-rig-paused')), findsNothing);
    expect(find.text('Pause project ocproof'), findsOneWidget);
  });

  testWidgets('delete names the project, says what goes and what stays', (
    tester,
  ) async {
    await _openRig(tester, host);
    await tester.ensureVisible(find.byKey(const ValueKey('team-rig-delete')));
    expect(find.text('Delete project ocproof'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('team-rig-delete')));
    await teamSettle(tester);
    expect(find.text('Delete ocproof?'), findsOneWidget);
    expect(find.textContaining('removed from this team'), findsOneWidget);
    expect(find.text('Its folder and files on the computer'), findsOneWidget);
    expect(find.text('Its past tasks and history'), findsOneWidget);
    await odCapture(tester, 'rig-delete-confirm');
    // Backing out sends nothing.
    await tester.tap(find.text('Cancel'));
    await teamSettle(tester);
    expect(host.gateway.controlCalls, isEmpty);
    await tester.tap(find.byKey(const ValueKey('team-rig-delete')));
    await teamSettle(tester);
    await tester.tap(
      find.byKey(const ValueKey('team-rig-delete-confirm-action')),
    );
    await teamSettle(tester);
    final call = host.gateway.controlCalls.single;
    expect(call.verb, 'controlProject');
    expect(call.arg, ProjectControlAction.remove);
    // The page leaves with the project.
    expect(find.byKey(const ValueKey('team-rig')), findsNothing);
  });

  testWidgets('a refusal stays on the page, with the way to try again', (
    tester,
  ) async {
    host.gateway.refuseControlsWith = 'the project is busy';
    await _openRig(tester, host);
    await tester.tap(find.byKey(const ValueKey('team-rig-pause')));
    await teamSettle(tester);
    await tester.tap(
      find.byKey(const ValueKey('team-rig-pause-confirm-action')),
    );
    await teamSettle(tester);
    expect(find.byKey(const ValueKey('team-rig-receipt')), findsOneWidget);
    expect(find.textContaining('the project is busy'), findsOneWidget);
    expect(find.byKey(const ValueKey('team-rig-paused')), findsNothing);
    await odCapture(tester, 'rig-refused');
  });
}
