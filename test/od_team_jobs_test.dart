// OD1 team lane, feature 5: a project's page lists the host's scheduled jobs
// (Gas City orders) in plain wording, each a switch that turns it on or off.
// The schedule is said in words ("Every day at 09:00"), never as a cron
// expression; loading, failed and empty are each said in words.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/team/team_rig_screen.dart';
import 'package:opencode_mobile/ui/widgets/team_job_words.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'coverage/lists_support.dart' show withRealHttp;
import 'coverage/team_support.dart' show teamSettle;
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'od_team_support.dart';

ScheduledJob _cron(String expression, {bool enabled = true, String? rig}) =>
    ScheduledJob(
      id: '${rig ?? 'team'}/job',
      name: 'job',
      enabled: enabled,
      trigger: ScheduledJobTrigger.cron,
      schedule: expression,
      projectId: rig,
    );

ScheduledJob _every(String interval) => ScheduledJob(
  id: 'job',
  name: 'job',
  enabled: true,
  trigger: ScheduledJobTrigger.interval,
  interval: interval,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final en = lookupAppLocalizations(const Locale('en'));
  final ar = lookupAppLocalizations(const Locale('ar'));

  setUpAll(() async {
    // The app's localization delegates do this for the app's language.
    await initializeDateFormatting('en');
    await initializeDateFormatting('ar');
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });

  group('plain schedule wording', () {
    test('cron, in English', () {
      String say(String e) => teamJobWhen(en, _cron(e));
      expect(say('0 9 * * *'), 'Every day at 09:00');
      expect(say('30 17 * * 1-5'), 'Every weekday at 17:30');
      expect(say('0 8 * * 1'), 'Every Monday at 08:00');
      expect(say('0 8 * * 0'), 'Every Sunday at 08:00');
      expect(say('*/15 * * * *'), 'Every 15 minutes');
      expect(say('* * * * *'), 'Every minute');
      expect(say('0 * * * *'), 'Every hour');
      expect(say('0 */6 * * *'), 'Every 6 hours');
      // Too unusual to say simply: never the expression itself.
      expect(say('0 9 1 * *'), 'On a schedule');
      expect(say('nonsense'), 'On a schedule');
    });

    test('intervals and the other triggers', () {
      expect(teamJobWhen(en, _every('5m')), 'Every 5 minutes');
      expect(teamJobWhen(en, _every('1h')), 'Every hour');
      expect(teamJobWhen(en, _every('30s')), 'Every 30 seconds');
      expect(teamJobWhen(en, _every('2h30m')), 'Repeats on its own');
      ScheduledJob of(ScheduledJobTrigger t) =>
          ScheduledJob(id: 'j', name: 'j', enabled: true, trigger: t);
      expect(
        teamJobWhen(en, of(ScheduledJobTrigger.event)),
        'When something happens',
      );
      expect(
        teamJobWhen(en, of(ScheduledJobTrigger.manual)),
        'Only when started by hand',
      );
    });

    test('Arabic says it too, with no cron text', () {
      final words = teamJobWhen(ar, _cron('0 9 * * *'));
      expect(words, contains('09:00'));
      expect(words, isNot(contains('*')));
    });
  });

  group('the project page', () {
    late OdFixture host;
    setUp(() async => host = await OdFixture.boot());
    tearDown(() async => host.close());
    Future<void> open(
      WidgetTester tester, {
      List<ScheduledJob> jobs = const [],
      Object? error,
    }) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      host.gateway
        ..jobs = jobs
        ..jobsError = error;
      await tester.pumpWidget(
        odApp(TeamRigScreen(controller: host.team, projectId: 'ocproof')),
      );
      await teamSettle(tester);
    }

    testWidgets('lists the jobs in words, and a switch turns one off', (
      tester,
    ) async {
      await open(
        tester,
        jobs: [
          ScheduledJob(
            id: 'ocproof/nightly',
            name: 'Nightly checks',
            enabled: true,
            trigger: ScheduledJobTrigger.cron,
            schedule: '0 9 * * *',
            projectId: 'ocproof',
          ),
          ScheduledJob(
            id: 'ocproof/sweep',
            name: 'Branch sweep',
            enabled: false,
            trigger: ScheduledJobTrigger.interval,
            interval: '6h',
            projectId: 'ocproof',
          ),
          ScheduledJob(
            id: 'digest',
            name: 'Daily digest',
            enabled: true,
            trigger: ScheduledJobTrigger.cron,
            schedule: '0 7 * * 1-5',
          ),
          ScheduledJob(
            id: 'other/elsewhere',
            name: 'Not this project',
            enabled: true,
            trigger: ScheduledJobTrigger.manual,
            projectId: 'other',
          ),
        ],
      );
      expect(find.text('Scheduled jobs'), findsOneWidget);
      expect(find.text('Nightly checks'), findsOneWidget);
      expect(find.text('Every day at 09:00'), findsOneWidget);
      expect(find.text('Every 6 hours'), findsOneWidget);
      expect(find.text('For the whole team'), findsOneWidget);
      expect(find.text('Every weekday at 07:00'), findsOneWidget);
      expect(find.text('Not this project'), findsNothing);
      expect(find.textContaining('* *'), findsNothing);
      await odCapture(tester, 'jobs');

      await tester.tap(
        find.byKey(const ValueKey('team-job-switch-ocproof/nightly')),
      );
      await teamSettle(tester);
      final call = host.gateway.controlCalls.single;
      expect(call.verb, 'controlScheduledJob');
      expect(call.target, 'ocproof/nightly');
      expect(call.arg, false);

      await tester.tap(
        find.byKey(const ValueKey('team-job-switch-ocproof/sweep')),
      );
      await teamSettle(tester);
      expect(host.gateway.controlCalls.last.arg, true);
    });

    testWidgets('no jobs says so, in words', (tester) async {
      await open(tester);
      expect(find.byKey(const ValueKey('team-jobs-empty')), findsOneWidget);
      expect(find.text('No scheduled jobs'), findsOneWidget);
      await odCapture(tester, 'jobs-empty');
    });

    testWidgets('a failed read says so and can be tried again', (tester) async {
      await open(tester, error: StateError('boom'));
      expect(find.text("Couldn't read scheduled jobs"), findsOneWidget);
      expect(find.textContaining('boom'), findsNothing);
      await odCapture(tester, 'jobs-failed');
      host.gateway
        ..jobsError = null
        ..jobs = [_cron('0 9 * * *', rig: 'ocproof')];
      await tester.tap(find.byKey(const ValueKey('team-jobs-retry')));
      await teamSettle(tester);
      expect(find.byKey(const ValueKey('team-jobs-failed')), findsNothing);
      expect(find.text('Every day at 09:00'), findsOneWidget);
    });

    testWidgets('a refused switch shows why and keeps the job as it was', (
      tester,
    ) async {
      await open(tester, jobs: [_cron('0 9 * * *', rig: 'ocproof')]);
      host.gateway.refuseControlsWith = 'the job is running';
      await tester.tap(
        find.byKey(const ValueKey('team-job-switch-ocproof/job')),
      );
      await teamSettle(tester);
      expect(find.textContaining('the job is running'), findsOneWidget);
      await odCapture(tester, 'jobs-refused');
    });
  });

  group('what the gateway sends', () {
    test('reads /orders and switches by the scoped name', () async {
      final host = await OdTeam.boot();
      addTearDown(host.close);
      host.city['orders'] = [
        {
          'name': 'nightly',
          'scoped_name': 'shopfront/nightly',
          'enabled': true,
          'trigger': 'cron',
          'schedule': '0 9 * * *',
          'rig': 'shopfront',
          'type': 'exec',
          'timeout_ms': 1000,
          'capture_output': false,
        },
      ];
      await withRealHttp(() async {
        final jobs = await host.gateway.scheduledJobs();
        expect(jobs.single.id, 'shopfront/nightly');
        expect(jobs.single.projectId, 'shopfront');
        expect(jobs.single.trigger, ScheduledJobTrigger.cron);
        final off = await host.gateway.controlScheduledJob(
          'shopfront/nightly',
          enabled: false,
          requestId: 'r1',
        );
        final on = await host.gateway.controlScheduledJob(
          'shopfront/nightly',
          enabled: true,
          requestId: 'r2',
        );
        expect(off.isAccepted && on.isAccepted, isTrue);
      });
      expect(
        [for (final w in host.writes) '${w.method} ${w.path}'],
        [
          'POST /v0/city/phone/order/shopfront%2Fnightly/disable',
          'POST /v0/city/phone/order/shopfront%2Fnightly/enable',
        ],
      );
    });
  });
}
