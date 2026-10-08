// Regenerate deliberately, and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/project_fixture_gateway.dart';
import 'package:opencode_mobile/state/team_project_controller.dart';
import '../../team_project_fixture_test.dart' show MemoryPersistence;
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_projects_screen.dart';
import '../kit/kit_gallery.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_project_editors.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_project_conversation.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import '../../../tool/capture/census/support/i1_team_core_world.dart';

const _sceneTask = {
  'plan': 't-data',
  'findings': 't-table',
  'promote': 't-layout',
};

const _diff =
    'diff --git a/lib/a.dart b/lib/a.dart\n--- a/lib/a.dart\n+++ b/lib/a.dart\n'
    '@@ -1 +1 @@\n-old\n+new\n'
    'diff --git a/lib/b.dart b/lib/b.dart\n--- a/lib/b.dart\n+++ b/lib/b.dart\n'
    '@@ -1 +1 @@\n-old\n+new\n';

/// Reshapes the seeded demo project into the state each scene shows.
TeamWorkspace _scene(TeamWorkspace w, String scene) {
  final p = w.projects.first;
  switch (scene) {
    case 'plan':
      return w.copyWith(
        projects: [
          p.copyWith(
            status: 'plan',
            planApproved: false,
            requests: const [],
            tasks: [
              for (final t in p.tasks)
                p.phases.any((f) => f.id == t.phaseId && f.milestoneId == 'm2')
                    ? t.copyWith(status: 'queued', changedAt: '')
                    : t,
            ],
          ),
        ],
      );
    case 'findings':
      return w.copyWith(
        projects: [
          p.copyWith(
            tasks: [
              for (final t in p.tasks)
                t.id == 't-table'
                    ? t.copyWith(
                        status: 'findings',
                        findings: const [
                          TeamFinding(
                            id: 'f1',
                            severity: 'critical',
                            text: 'Price rounds down at checkout',
                            criterion: 'Criterion 2 · totals match the invoice',
                          ),
                          TeamFinding(
                            id: 'f2',
                            severity: 'major',
                            text: 'Choice lost on restart',
                            criterion: 'Criterion 2 · survives restart',
                          ),
                          TeamFinding(
                            id: 'f3',
                            severity: 'minor',
                            text: 'No Arabic label on the switch',
                            criterion: 'Criterion 4 · both languages',
                          ),
                        ],
                      )
                    : t,
            ],
          ),
        ],
      );
    case 'promote':
      final merged = [
        for (final t in p.tasks) t.copyWith(status: 'merged', diff: _diff),
      ];
      return w.copyWith(
        projects: [
          p.copyWith(
            requests: const [],
            tasks: merged,
            phases: [for (final f in p.phases) f.copyWith(accepted: true)],
            mergeQueue: [
              for (final t in merged.where((t) => t.repoId == 'site'))
                TeamMergeItem(
                  id: 'merge-${t.id}',
                  taskId: t.id,
                  repoId: 'site',
                  status: 'merged',
                  checksPassed: true,
                ),
            ],
          ),
        ],
      );
    default:
      return w;
  }
}

void main() {
  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    await SharedPreferences.getInstance();
    await loadKitGalleryFonts();
  });
  for (final size in [const Size(412, 915), const Size(1280, 800)]) {
    for (final light in [false, true]) {
      for (final scene in [
        'overview',
        'new',
        'plan',
        'findings',
        'promote',
        'before',
      ]) {
        testWidgets('project $scene ${kitGallerySize(size)} $light', (
          tester,
        ) async {
          final c = TeamProjectController(
            ProjectFixtureGateway(persistence: MemoryPersistence()),
          );
          await c.load();
          c.snapshot = _scene(c.snapshot!, scene);
          final legacy = scene == 'before' ? (await teamController()).$1 : null;
          await kitGalleryShot(
            tester,
            name: kitGalleryName('kit_teamprojects_$scene', size, light: light),
            size: size,
            light: light,
            open: (context) {
              if (scene == 'new') {
                openTeamNewProject(context, c);
              } else {
                pushKitPage<void>(
                  context,
                  (_) => switch (scene) {
                    'before' => TeamHomeScreen(
                      controller: legacy!,
                      now: teamNow,
                    ),
                    'overview' =>
                      size == const Size(412, 915)
                          ? TeamProjectOverview(
                              controller: c,
                              projectId: 'demo-project',
                            )
                          : TeamProjectsScreen(controller: c),
                    _ => TeamProjectConversation(
                      controller: c,
                      projectId: 'demo-project',
                      taskId: _sceneTask[scene]!,
                    ),
                  },
                );
              }
            },
            then: scene == 'new'
                ? (tester) async {
                    await tester.enterText(
                      find.descendant(
                        of: find.byKey(const ValueKey('goal')),
                        matching: find.byType(EditableText),
                      ),
                      'Launch the marketing site and its docs in Arabic and English.',
                    );
                    for (final label in ['Parallel agents', 'Set limits']) {
                      await tester.ensureVisible(find.text(label));
                      await tester.pumpAndSettle();
                      await tester.tap(find.text(label).first);
                      await tester.pumpAndSettle();
                    }
                    await tester.drag(
                      find.byType(Scrollable).first,
                      const Offset(0, 4000),
                    );
                    await tester.pumpAndSettle();
                  }
                : null,
          );
          legacy?.dispose();
          c.dispose();
        }, variant: TargetPlatformVariant.only(TargetPlatform.android));
      }
    }
  }
  for (final size in [
    const Size(360, 800),
    const Size(412, 915),
    const Size(1280, 800),
  ]) {
    testWidgets('project large text ${kitGallerySize(size)}', (tester) async {
      final c = TeamProjectController(
        ProjectFixtureGateway(persistence: MemoryPersistence()),
      );
      await c.load();
      await kitGalleryShot(
        tester,
        name: kitGalleryName(
          'kit_teamprojects_scaled',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        open: (context) => pushKitPage<void>(
          context,
          (_) => TeamProjectsScreen(controller: c),
        ),
      );
      c.dispose();
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));
  }
}
