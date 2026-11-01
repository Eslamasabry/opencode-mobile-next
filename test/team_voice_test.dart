import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/team_project.dart';
import 'package:opencode_mobile/state/team_voice.dart';

TeamTimelineEvent _work(String suffix, {String kind = 'job'}) =>
    TeamTimelineEvent(
      kind: kind,
      text: '${kind == 'planner' ? 'Planning' : 'Work'} $suffix',
      actor: 'engine',
      taskId: 'task',
    );

void main() {
  // Every canned line the phone engine writes (engine/phone/src/store.rs,
  // append_job_timeline) has a speaker; none falls through to "other".
  test('each engine line is said by its role', () {
    final expected = <String, (TeamVoice, TeamVoiceSays)>{
      'is queued.': (TeamVoice.manager, TeamVoiceSays.queued),
      'is starting.': (TeamVoice.developer, TeamVoiceSays.starting),
      'is running.': (TeamVoice.developer, TeamVoiceSays.working),
      'is checking acceptance criteria.': (
        TeamVoice.reviewer,
        TeamVoiceSays.checking,
      ),
      'has checker findings that need review.': (
        TeamVoice.reviewer,
        TeamVoiceSays.foundProblems,
      ),
      'is preparing the dev merge.': (TeamVoice.manager, TeamVoiceSays.merging),
      'finished.': (TeamVoice.manager, TeamVoiceSays.merged),
      'is waiting for a free lane.': (
        TeamVoice.manager,
        TeamVoiceSays.waitingForLane,
      ),
      'is waiting for another task to finish.': (
        TeamVoice.manager,
        TeamVoiceSays.waitingForTask,
      ),
      'is waiting for your chat reply to finish.': (
        TeamVoice.manager,
        TeamVoiceSays.waitingForChat,
      ),
      'is waiting for chat status to be checked.': (
        TeamVoice.manager,
        TeamVoiceSays.waitingForChat,
      ),
      'is waiting for protected execution to be verified.': (
        TeamVoice.manager,
        TeamVoiceSays.waitingForCheck,
      ),
      'is waiting for the phone server to be checked.': (
        TeamVoice.manager,
        TeamVoiceSays.waitingForCheck,
      ),
      'is waiting for an execution check.': (
        TeamVoice.manager,
        TeamVoiceSays.waitingForCheck,
      ),
      'is paused.': (TeamVoice.manager, TeamVoiceSays.paused),
      'is paused. Resume to check its existing session.': (
        TeamVoice.manager,
        TeamVoiceSays.paused,
      ),
      'is stopped.': (TeamVoice.manager, TeamVoiceSays.stopped),
      'was interrupted and needs review before resuming.': (
        TeamVoice.manager,
        TeamVoiceSays.interrupted,
      ),
      'was interrupted when the app stopped. Resume to check its existing '
          'session.': (
        TeamVoice.manager,
        TeamVoiceSays.interrupted,
      ),
      'is checking its existing session before resuming.': (
        TeamVoice.manager,
        TeamVoiceSays.resuming,
      ),
    };
    for (final MapEntry(key: suffix, value: (voice, says))
        in expected.entries) {
      final line = teamVoiceOf(_work(suffix));
      expect(line.voice, voice, reason: suffix);
      expect(line.says, says, reason: suffix);
      expect(line.planner, isFalse);
    }
  });

  test('the planner is the architect while it plans and when it is done', () {
    final running = teamVoiceOf(_work('is running.', kind: 'planner'));
    expect(running.voice, TeamVoice.architect);
    expect(running.says, TeamVoiceSays.planning);
    expect(running.planner, isTrue);
    final done = teamVoiceOf(
      _work('finished. The plan is ready to review.', kind: 'planner'),
    );
    expect(done.voice, TeamVoice.architect);
    expect(done.says, TeamVoiceSays.planReady);
    final queued = teamVoiceOf(_work('is queued.', kind: 'planner'));
    expect(queued.voice, TeamVoice.manager);
    expect(queued.planner, isTrue);
  });

  test('an unknown line stays the manager’s, with the engine’s words', () {
    final line = teamVoiceOf(
      const TeamTimelineEvent(kind: 'job', text: 'Work status changed.'),
    );
    expect(line.voice, TeamVoice.manager);
    expect(line.says, TeamVoiceSays.other);
    expect(line.text, 'Work status changed.');
  });

  test('now is the task’s newest line; a persona names the developer', () {
    const task = TeamTask(id: 'task', roleId: 'frontend');
    final project = TeamProject(
      id: 'p',
      tasks: const [task],
      timeline: [
        _work('is running.'),
        _work('is checking acceptance criteria.'),
        const TeamTimelineEvent(
          kind: 'job',
          text: 'Work is running.',
          taskId: 'other',
        ),
      ],
    );
    expect(teamVoiceNow(project, task)!.voice, TeamVoice.reviewer);
    // The task's status wins over a stale newest line.
    final restarted = TeamProject(id: 'p', timeline: [_work('finished.')]);
    final running = teamVoiceNow(
      restarted,
      const TeamTask(id: 'task', status: 'running'),
    )!;
    expect(running.voice, TeamVoice.developer);
    expect(running.says, TeamVoiceSays.working);
    expect(
      teamVoiceNow(
        restarted,
        const TeamTask(id: 'task', status: 'merged'),
      )!.says,
      TeamVoiceSays.merged,
    );
    expect(teamVoiceNow(project, const TeamTask(id: 'quiet')), isNull);
    const roles = [
      TeamProjectRole(id: 'frontend', name: 'Frontend'),
      TeamProjectRole(id: 'worker', name: 'Worker'),
    ];
    expect(teamDeveloperPersona(task, roles), 'Frontend');
    expect(
      teamDeveloperPersona(const TeamTask(roleId: 'worker'), roles),
      isNull,
    );
    expect(teamDeveloperPersona(const TeamTask(roleId: 'gone'), roles), isNull);
  });
}
