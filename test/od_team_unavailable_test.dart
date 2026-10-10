// OD1 team lane, feature 1: an agent the host says it cannot run shows why in
// plain words on its row and page, with the host's own text under Details.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitDetailsFold;
import 'package:opencode_mobile/ui/widgets/team_agent_row.dart';
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';

import 'coverage/lists_support.dart' show withRealHttp;
import 'coverage/team_support.dart';
import 'od_team_support.dart';
import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late TeamHost wire;
  late OrchestrationController team;
  late GasCityGateway gateway;

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    wire = await TeamHost.start();
    final samples =
        jsonDecode(
              File(
                'test/fixtures/coverage/gascity_team_samples.json',
              ).readAsStringSync(),
            )
            as Map;
    final city = ((samples['cases'] as List).first as Map)['payload'] as Map;
    await withRealHttp(() async {
      (team, gateway) = await bootTeam(wire, city.cast<String, Object?>());
    });
  });
  tearDownAll(() async {
    await team.stop();
    await gateway.close();
    await wire.close();
  });

  testWidgets('the row says why in plain words, not the host text', (
    tester,
  ) async {
    final agent = team.snapshot.agents.firstWhere(
      (a) => a.id == 'gastown.witness',
    );
    expect(agent.unavailableReason, 'No model is signed in for this agent');
    await tester.pumpWidget(
      teamApp(
        Scaffold(
          body: TeamAgentRow(
            agent: agent,
            work: null,
            now: DateTime.utc(2026, 10, 9, 10),
            onTap: () {},
          ),
        ),
      ),
    );
    await teamSettle(tester);
    expect(find.textContaining("Can't start"), findsOneWidget);
    expect(find.textContaining("model isn't set up"), findsOneWidget);
    expect(find.textContaining('No model is signed in'), findsNothing);
  });

  testWidgets('the page names the problem and keeps the host text in Details', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      teamApp(AgentScreen(controller: team, agentId: 'gastown.witness')),
    );
    await teamSettle(tester);
    expect(
      find.byKey(const ValueKey('team-agent-unavailable')),
      findsOneWidget,
    );
    // Said once, in the notice, with the agent's page name, not its id.
    expect(
      find.textContaining("can't start", findRichText: true),
      findsOneWidget,
    );
    expect(find.text("Supervisor · witness can't start"), findsOneWidget);
    expect(find.textContaining('gastown.witness'), findsNothing);
    expect(find.textContaining('No model is signed in'), findsNothing);
    final fold = find.byType(KitDetailsFold);
    await tester.ensureVisible(fold);
    await tester.tap(
      find.descendant(of: fold, matching: find.byType(InkWell)).first,
    );
    await teamSettle(tester);
    expect(find.text('What the computer says'), findsOneWidget);
    expect(find.text('No model is signed in for this agent'), findsOneWidget);
    await odShot(
      tester,
      AgentScreen(controller: team, agentId: 'gastown.witness'),
      'unavailable-agent',
    );
  });
}
