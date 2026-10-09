// Coverage ratchet for what a Gas City supervisor sends the AI Team screens:
// the wire bodies are served to the app's real gateway and the team's pages are
// drawn from what it parsed (see paseo_coverage_support.dart).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitDetailsFold;
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';
import 'package:opencode_mobile/ui/screens/team/gate_sheet.dart';
import 'package:opencode_mobile/ui/screens/team/task_details_sheet.dart';
import 'package:opencode_mobile/ui/screens/team/team_agents_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_settings_screen.dart';
import 'package:opencode_mobile/ui/screens/team/work_sheet.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'lists_support.dart' show withRealHttp;
import 'paseo_coverage_support.dart';
import 'team_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late TeamHost wire;
  final family = CoverageFamily('gascity_team', prefix: '');

  final teams = <String, (OrchestrationController, GasCityGateway)>{};

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    wire = await TeamHost.start();
    // The supervisor is read over real sockets, outside the widget zone.
    await withRealHttp(() async {
      for (final variant in family.cases) {
        final city = (variant['payload'] as Map).cast<String, Object?>();
        teams[variant['id'] as String] = await bootTeam(wire, city);
      }
    });
  });
  tearDownAll(() async {
    for (final (team, gateway) in teams.values) {
      await team.stop();
      await gateway.close();
    }
    await wire.close();
  });

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('gas city team · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      {
        final (team, _) = teams[id]!;
        final text = StringBuffer();
        Future<void> openFolds() async {
          final folds = find.byType(KitDetailsFold).evaluate().length;
          for (var i = 0; i < folds; i++) {
            final toggle = find
                .descendant(
                  of: find.byType(KitDetailsFold).at(i),
                  matching: find.byType(InkWell),
                )
                .first;
            await tester.ensureVisible(toggle);
            await tester.tap(toggle, warnIfMissed: false);
            await teamSettle(tester);
          }
          // Folds that are not the kit's own (a settings row).
          final labels = find.text('Technical details').evaluate().length;
          for (var i = 0; i < labels; i++) {
            final label = find.text('Technical details').at(i);
            if (find
                .ancestor(of: label, matching: find.byType(KitDetailsFold))
                .evaluate()
                .isNotEmpty) {
              continue;
            }
            await tester.ensureVisible(label);
            await tester.tap(label, warnIfMissed: false);
            await teamSettle(tester);
          }
        }

        final boundary = GlobalKey();
        var shots = 0;
        Future<void> shoot(String name) async {
          if (id != 'city_at_work' || shots >= 12) return;
          shots++;
          final safe = name.replaceAll(RegExp(r'[^A-Za-z0-9]+'), '_');
          await writeCasePng(tester, boundary, 'team-gascity-$id-$safe');
        }

        Future<void> page(String name, Widget home) async {
          await tester.pumpWidget(
            RepaintBoundary(key: boundary, child: teamApp(home)),
          );
          await teamSettle(tester);
          await shoot(name);
          await openFolds();
          text.writeln('## $name');
          text.writeln(screenText(tester).join('\n'));
        }

        Future<void> sheet(
          String name,
          Future<void> Function(BuildContext) open,
        ) async {
          await tester.pumpWidget(
            RepaintBoundary(
              key: boundary,
              child: teamApp(
                Builder(
                  builder: (context) => TextButton(
                    onPressed: () => open(context),
                    child: const Text('open'),
                  ),
                ),
              ),
            ),
          );
          await tester.pump();
          expect(find.text('open'), findsOneWidget, reason: name);
          await tester.tap(find.text('open'));
          await teamSettle(tester);
          await shoot(name);
          await openFolds();
          text.writeln('## $name');
          text.writeln(screenText(tester).join('\n'));
          await tester.pumpWidget(const SizedBox.shrink());
        }

        await page('home', TeamHomeScreen(controller: team));
        await page('agents', TeamAgentsScreen(controller: team));
        for (final agent in team.snapshot.agents) {
          await page(
            'agent ${agent.id}',
            AgentScreen(controller: team, agentId: agent.id),
          );
        }
        for (final item in team.snapshot.work) {
          await sheet(
            'work ${item.id}',
            (c) => showWorkSheet(c, team, item.id),
          );
        }
        for (final run in team.snapshot.runs) {
          await sheet(
            'run ${run.id}',
            (c) => showTeamTaskDetails(c, team, run.id),
          );
        }
        for (final gate in team.snapshot.gates) {
          await sheet(
            'gate ${gate.id}',
            (c) => showGateSheet(c, team, gate.id),
          );
        }
        await page('settings', TeamSettingsScreen(controller: team));
        File(
          'build/coverage/gascity_team_$id.txt',
        ).writeAsStringSync(text.toString());
        final problems = checkCase(family, variant, text.toString());
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump(const Duration(seconds: 2));
        expect(problems, isEmpty, reason: problems.join('\n'));
      }
    });
  }
}
