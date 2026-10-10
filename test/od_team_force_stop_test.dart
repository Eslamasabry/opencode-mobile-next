// OD1 team lane, feature 4: Force stop, beside the normal Stop. It is a
// secondary entry in the agent page's menu, and a plain notice with the same
// action appears once a normal Stop did not end the session. Either way it
// asks first, naming the agent and what may be lost, and only then sends
// the hard kill (AgentControlAction.kill).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'od_team_support.dart' show odBoundary, odCapture;
import 'support/team_controls_fixtures.dart';

Future<void> _pump(WidgetTester tester, dynamic controller) async {
  await tester.pumpWidget(
    RepaintBoundary(
      key: odBoundary,
      child: app(
        AgentScreen(controller: controller, agentId: 'fox', now: () => clock),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = OrchestrationStore(await SharedPreferences.getInstance());
    clock = DateTime.utc(2026, 9, 11, 9, 41);
    nextKey = 0;
  });

  testWidgets('the menu holds Force stop, under Stop, and asks first', (
    tester,
  ) async {
    await size(tester, const Size(400, 900));
    final (controller, gateway) = await boot();
    await _pump(tester, controller);
    expect(key('team-agent-stop-stuck'), findsNothing);
    await openMore(tester);
    expect(key('team-agent-control-stop'), findsOneWidget);
    expect(find.text('Force stop fox'), findsOneWidget);
    await odCapture(tester, 'force-stop-menu');
    await tester.tap(key('team-agent-control-force-stop'));
    await tester.pumpAndSettle();
    expect(find.text('Force stop fox?'), findsOneWidget);
    expect(find.textContaining('may be lost'), findsOneWidget);
    expect(gateway.calls, isEmpty, reason: 'nothing before the yes');
    await odCapture(tester, 'force-stop-confirm');
    // Backing out sends nothing.
    await tester.tap(find.text('Keep running'));
    await tester.pumpAndSettle();
    expect(gateway.calls, isEmpty);

    await openMore(tester);
    await tester.tap(key('team-agent-control-force-stop'));
    await tester.pumpAndSettle();
    await tester.tap(key('team-agent-force-stop-confirm-action'));
    await tester.pumpAndSettle();
    expect(gateway.calls.single.verb, 'controlAgent');
    expect(gateway.calls.single.arg, AgentControlAction.kill);
    expect(find.textContaining('Force stop · Sending'), findsOneWidget);
    await drain(tester);
  });

  testWidgets('a Stop that did not end the session offers Force stop', (
    tester,
  ) async {
    await size(tester, const Size(400, 900));
    final (controller, gateway) = await boot(
      timeout: const Duration(seconds: 2),
    );
    await _pump(tester, controller);
    await openMore(tester);
    await tester.tap(key('team-agent-control-stop'));
    await tester.pumpAndSettle();
    await tester.tap(key('team-agent-stop-confirm-action'));
    await tester.pumpAndSettle();
    expect(gateway.calls.single.arg, AgentControlAction.stop);
    // The host never says the session stopped.
    expect(key('team-agent-stop-stuck'), findsNothing);
    await tester.pump(const Duration(seconds: 3));
    await tester.pump();
    expect(key('team-agent-stop-stuck'), findsOneWidget);
    expect(find.text("fox didn't stop"), findsOneWidget);
    await odCapture(tester, 'force-stop-notice');
    await tester.tap(key('team-agent-stop-stuck-force'));
    await tester.pumpAndSettle();
    await tester.tap(key('team-agent-force-stop-confirm-action'));
    await tester.pumpAndSettle();
    expect(gateway.calls.last.arg, AgentControlAction.kill);
    await drain(tester);
  });

  testWidgets('a host that cannot force an end has no Force stop', (
    tester,
  ) async {
    await size(tester, const Size(400, 900));
    final (controller, _) = await boot(
      capabilities: const OrchestrationCapabilities(
        runs: true,
        agents: true,
        agentOutput: true,
        controlAgent: true,
      ),
    );
    await _pump(tester, controller);
    await openMore(tester);
    expect(key('team-agent-control-stop'), findsOneWidget);
    expect(key('team-agent-control-force-stop'), findsNothing);
  });
}
