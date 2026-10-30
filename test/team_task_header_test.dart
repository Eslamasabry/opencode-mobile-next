// The AI Team task's header line (vision 2026-10-20, "B's gate + A's Now
// line in the header", docs/qa/vision-team-task-2026-10-20/README.md): the
// Now line's words for the conversation's header, and the Now line that
// keeps only the problems under it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/team_now_line_view.dart';

final _now = DateTime.utc(2026, 10, 20, 12);
final AppLocalizations _l10n = AppLocalizationsEn();

TeamNowInput _input(
  TeamNowActivity activity, {
  Duration ago = const Duration(minutes: 5),
  TeamNowNext next = TeamNowNext.review,
}) => TeamNowInput(
  activityKey: 'p:run',
  activity: activity,
  next: next,
  reason: null,
  since: _now.subtract(ago),
);

void main() {
  group('teamTaskHeaderLine', () {
    test('working: who on which step, since when, and what next', () {
      final line = teamTaskHeaderLine(
        _l10n,
        _input(TeamNowActivity.working),
        now: _now,
        who: 'Worker 1',
        step: 'Sync engine',
      )!;
      expect(line.line, 'Now: Worker 1 on Sync engine · 5 min');
      expect(line.next, 'Next: review');
    });

    test('the step that is the task itself is not said; under a minute no '
        'time', () {
      final line = teamTaskHeaderLine(
        _l10n,
        _input(TeamNowActivity.working, ago: const Duration(seconds: 20)),
        now: _now,
        who: 'Worker',
      )!;
      expect(line.line, 'Now: Worker is working');
    });

    test('reviewing says "Next: merge" only where a merge gate follows', () {
      final front = teamTaskHeaderLine(
        _l10n,
        _input(TeamNowActivity.reviewing, next: TeamNowNext.finish),
        now: _now,
        mergeNext: true,
      )!;
      expect(front.line, 'Now: reviewing the changes · 5 min');
      expect(front.next, 'Next: merge');
      final phone = teamTaskHeaderLine(
        _l10n,
        _input(TeamNowActivity.reviewing, next: TeamNowNext.finish),
        now: _now,
      )!;
      expect(phone.next, isNull);
    });

    test('the merge gate waits: where the task is, no time (the card says '
        'how long)', () {
      final line = teamTaskHeaderLine(
        _l10n,
        _input(TeamNowActivity.completed),
        now: _now,
        mergeWaits: true,
      )!;
      expect(line.line, 'All steps done');
      expect(line.next, isNull);
    });

    test('finished says how long ago', () {
      final line = teamTaskHeaderLine(
        _l10n,
        _input(TeamNowActivity.completed, ago: const Duration(hours: 5)),
        now: _now,
      )!;
      expect(line.line, 'Finished 5 h ago');
    });

    test(
      'a question, or a problem with its own line, keeps the host words',
      () {
        for (final activity in [
          TeamNowActivity.needsYou,
          TeamNowActivity.delayed,
          TeamNowActivity.unavailable,
          TeamNowActivity.unconfirmed,
          TeamNowActivity.refused,
        ]) {
          expect(
            teamTaskHeaderLine(_l10n, _input(activity), now: _now),
            isNull,
            reason: '$activity',
          );
        }
      },
    );
  });

  test('a wait past its usual time leaves the header to the line under it', () {
    final early = _input(
      TeamNowActivity.waitingForWorker,
      ago: const Duration(minutes: 2),
      next: TeamNowNext.worker,
    );
    final late = _input(
      TeamNowActivity.waitingForWorker,
      ago: const Duration(minutes: 15),
      next: TeamNowNext.worker,
    );
    expect(
      teamTaskHeaderLine(_l10n, early, now: _now)!.line,
      'Now: waiting for a worker · 2 min',
    );
    expect(teamNowShowsUnderHeader(early, const Duration(minutes: 2)), isFalse);
    expect(teamTaskHeaderLine(_l10n, late, now: _now), isNull);
    expect(teamNowShowsUnderHeader(late, const Duration(minutes: 15)), isTrue);
  });

  group('TeamNowLineView.calmInHeader', () {
    Future<void> pump(WidgetTester tester, TeamNowActivity activity) =>
        tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: TeamNowLineView(
                keyPrefix: 'p',
                input: _input(activity),
                wayOut: (_) => null,
                clock: () => _now,
                calmInHeader: true,
              ),
            ),
          ),
        );

    testWidgets('a calm stage draws nothing: the header has it', (
      tester,
    ) async {
      await pump(tester, TeamNowActivity.working);
      expect(find.byKey(const ValueKey('p-now-calm')), findsOneWidget);
      expect(find.byType(KitStatusLine), findsNothing);
    });

    testWidgets('a problem keeps its line under the header', (tester) async {
      await pump(tester, TeamNowActivity.delayed);
      expect(find.byType(KitStatusLine), findsOneWidget);
      await pump(tester, TeamNowActivity.unavailable);
      expect(find.byType(KitStatusLine), findsOneWidget);
    });
  });
}
