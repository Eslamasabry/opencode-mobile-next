import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/team_project_controller.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_digest.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_project_conversation.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_projects_screen.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_refusal.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit/kit_harness.dart';

class _Gateway implements OrchestrationProjectGateway {
  _Gateway(this.project);
  final TeamProject project;
  final sent = <TeamProjectAction>[];
  @override
  Future<TeamWorkspace> teamWorkspace() async =>
      TeamWorkspace(projects: [project]);
  @override
  Stream<TeamWorkspace> watchTeamWorkspace() => const Stream.empty();
  @override
  Future<TeamCommandResult> executeProject(TeamProjectCommand command) async {
    sent.add(command.action);
    return const TeamCommandResult(accepted: true);
  }

  @override
  Future<void> close() async {}
  @override
  Future<void> deleteLocalData() async {}
}

TeamTimelineEvent _row(
  String id,
  String kind,
  String text,
  String at, {
  String taskId = '',
}) => TeamTimelineEvent(
  id: id,
  kind: kind,
  text: text,
  at: at,
  taskId: taskId,
  actor: 'engine',
);

final _l = lookupAppLocalizations(const Locale('en'));

Future<_Gateway> _openOverview(WidgetTester tester, TeamProject project) async {
  final gateway = _Gateway(project);
  final c = TeamProjectController(gateway);
  await c.load();
  addTearDown(c.dispose);
  final context = await pumpKitHost(tester);
  pushKitPage<void>(
    context,
    (_) => TeamProjectOverview(controller: c, projectId: project.id),
  );
  await tester.pumpAndSettle();
  return gateway;
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  group('plain words for engine codes', () {
    test('sessionFailed and modelNotConfigured read as sentences', () {
      expect(
        teamTimelineWords(_l, 'Planning needs review (sessionFailed).'),
        isNot(contains('sessionFailed')),
      );
      final model = teamReasonFor(_l, 'modelNotConfigured')!;
      expect(model.message, 'The team needs a model.');
      expect(model.next, 'Pick one in Team settings › Model.');
      expect(teamReasonNeedsModel('modelNotConfigured'), isTrue);
    });

    test('an unknown code never reaches the person', () {
      expect(
        teamTimelineWords(_l, 'Planning failed (weirdNewCode).'),
        'Planning failed.',
      );
      expect(
        teamTimelineWords(_l, 'Planning is stopped.'),
        'Planning is stopped.',
      );
    });
  });

  group('digest keeps only what is still true', () {
    test('a later success replaces the earlier stop for the same item', () {
      final rows = [
        _row('1', 'planner', 'Planning is stopped.', '2026-10-01T10:00:00Z'),
        _row(
          '2',
          'planner',
          'Planning was interrupted and needs review before resuming.',
          '2026-10-01T10:05:00Z',
        ),
        _row(
          '3',
          'planner',
          'Planning finished. The plan is ready to review.',
          '2026-10-01T10:10:00Z',
        ),
      ];
      final shown = teamDigestEvents(rows, null);
      expect(
        [for (final e in shown) e.text],
        ['Planning finished. The plan is ready to review.'],
      );
    });

    test('items stay separate, and what was read is dropped', () {
      final rows = [
        _row(
          '1',
          'job',
          'Work is running.',
          '2026-10-01T10:00:00Z',
          taskId: 'a',
        ),
        _row('2', 'job', 'Work finished.', '2026-10-01T10:02:00Z', taskId: 'a'),
        _row(
          '3',
          'job',
          'Work is running.',
          '2026-10-01T10:03:00Z',
          taskId: 'b',
        ),
      ];
      expect([for (final e in teamDigestEvents(rows, null)) e.id], ['3', '2']);
      expect(
        [
          for (final e in teamDigestEvents(
            rows,
            DateTime.utc(2026, 10, 1, 10, 2, 30),
          ))
            e.id,
        ],
        ['3'],
      );
    });
  });

  testWidgets('the digest shows plain words, no code, after a failed plan', (
    tester,
  ) async {
    await _openOverview(
      tester,
      TeamProject(
        id: 'p',
        name: 'Site',
        status: 'failed',
        planningState: const TeamPlanningState(
          stage: 'failed',
          reason: 'sessionFailed',
        ),
        timeline: [
          _row('1', 'planner', 'Planning is stopped.', '2026-10-01T10:00:00Z'),
          _row(
            'x',
            'planningCheckpoint',
            'Planning failed (sessionFailed).',
            '2026-10-01T10:06:00Z',
          ),
        ],
      ),
    );
    expect(find.text('Since you were away'), findsOneWidget);
    expect(find.textContaining('(sessionFailed)'), findsNothing);
    expect(find.text('Planning is stopped.'), findsNothing);
    expect(find.text("The planner stopped before it answered."), findsWidgets);
  });

  testWidgets('a failed plan offers a way forward, code only in Details', (
    tester,
  ) async {
    await _openOverview(
      tester,
      const TeamProject(
        id: 'p',
        name: 'Site',
        status: 'failed',
        planningState: TeamPlanningState(
          stage: 'failed',
          reason: 'modelNotConfigured',
        ),
      ),
    );
    expect(find.byKey(const ValueKey('team-plan-failed')), findsOneWidget);
    expect(find.text('The team needs a model.'), findsOneWidget);
    expect(find.text('Pick a model'), findsOneWidget);
    expect(find.text('Approve the spec again'), findsOneWidget);
    expect(find.text('modelNotConfigured'), findsNothing);
  });

  testWidgets('a failed plan the engine can retry asks again, not approve', (
    tester,
  ) async {
    final gateway = await _openOverview(
      tester,
      const TeamProject(
        id: 'p',
        name: 'Site',
        status: 'planFailed',
        planningState: TeamPlanningState(
          stage: 'failed',
          reason: 'sessionFailed',
        ),
      ),
    );
    expect(find.byKey(const ValueKey('team-plan-failed')), findsOneWidget);
    expect(find.text('Approve the spec again'), findsNothing);
    expect(find.text('Approve the spec again to retry.'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('team-plan-failed-retry')));
    await tester.pumpAndSettle();
    expect(gateway.sent, [TeamProjectAction.retryPlan]);
  });

  testWidgets('a retryable failed plan with no reason has no approve note', (
    tester,
  ) async {
    await _openOverview(
      tester,
      const TeamProject(id: 'p', name: 'Site', status: 'planFailed'),
    );
    expect(find.text('Approve the spec again to retry.'), findsNothing);
    expect(
      find.byKey(const ValueKey('team-plan-failed-approve')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('team-plan-failed-retry')),
      findsOneWidget,
    );
  });

  testWidgets('a failed plan with no reason says to approve the spec again', (
    tester,
  ) async {
    await _openOverview(
      tester,
      const TeamProject(id: 'p', name: 'Site', status: 'failed'),
    );
    expect(find.text('Approve the spec again to retry.'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('team-plan-failed-approve')),
      findsOneWidget,
    );
  });

  testWidgets('the task page header follows the live state', (tester) async {
    Future<void> open(String status) async {
      final c = TeamProjectController(
        _Gateway(
          TeamProject(
            id: 'p',
            name: 'P',
            status: 'running',
            tasks: [TeamTask(id: 't', title: 'Task', status: status)],
            timeline: [
              _row(
                'e',
                'job',
                'Work finished.',
                '2026-10-01T10:00:00Z',
                taskId: 't',
              ),
            ],
          ),
        ),
      );
      await c.load();
      addTearDown(c.dispose);
      final context = await pumpKitHost(tester);
      pushKitPage<void>(
        context,
        (_) =>
            TeamProjectConversation(controller: c, projectId: 'p', taskId: 't'),
      );
      for (var i = 0; i < 8; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
    }

    await open('running');
    expect(find.text('Work so far'), findsOneWidget);
    expect(find.text('Work completed'), findsNothing);
  });
}
