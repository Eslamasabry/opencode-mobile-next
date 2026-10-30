// KitBackgroundWorkLine (docs/ux-system/kit-api/KitBackgroundWorkLine.md):
// the quiet line under a finished turn while background work runs. One task
// names itself and how long it has run; several say how many. Either opens
// in place to one row per task with its name, time and command, and a tap on
// a row goes to its step; none fades out and takes no room. Plus the
// reduced-motion samples (G8x), opened included.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_background_work_line.dart';

import 'kit_motion_still.dart';

/// The line as a screen reader hears it: its words in full, never cut.
Finder _says(String words) =>
    find.bySemanticsLabel(RegExp(RegExp.escape(words)));

const _lineKey = ValueKey('background-work');

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  bool reduceMotion = true,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: reduceMotion),
        child: widget!,
      ),
      home: Scaffold(
        body: Padding(
          padding: const EdgeInsets.all(16),
          child: Align(alignment: AlignmentDirectional.topStart, child: child),
        ),
      ),
    ),
  );
}

KitBackgroundTask _task(String name, Duration ago, {String command = ''}) =>
    KitBackgroundTask(
      name: name,
      since: clock.now().subtract(ago),
      command: command,
    );

const _loop =
    'until grep -q "BUILD SUCCESSFUL" /tmp/claude-1000/tasks/b1x2y3.output; '
    'do sleep 30; done';

void main() {
  kitMotionStillTests(
    'KitBackgroundWorkLine',
    builds: {
      'one task': () => KitBackgroundWorkLine(
        tasks: [
          KitBackgroundTask(name: 'Build the owner APK', since: DateTime(2026)),
        ],
        onOpenTask: (_) {},
      ),
      'after the turn stopped': () => KitBackgroundWorkLine(
        afterStop: true,
        tasks: [
          KitBackgroundTask(name: 'Waiting for grep', since: DateTime(2026)),
        ],
        onOpenTask: (_) {},
      ),
      'two tasks': () => KitBackgroundWorkLine(
        tasks: [
          KitBackgroundTask(name: 'Build the owner APK', since: DateTime(2026)),
          KitBackgroundTask(name: 'flutter test', since: DateTime(2026)),
        ],
        onOpenTask: (_) {},
      ),
    },
    changes: {
      'one task opens': KitMotionChange(
        build: () => KitBackgroundWorkLine(
          lineKey: _lineKey,
          tasks: [
            KitBackgroundTask(
              name: 'Waiting for grep',
              since: DateTime(2026),
              command: _loop,
            ),
          ],
          onOpenTask: (_) {},
        ),
        act: (tester, stage) => tester.tap(find.byKey(_lineKey)),
        shows: _loop,
      ),
    },
  );

  // Owner, 2026-10-29: the chevron under "Still running in the background ·
  // grep · 8 min" only scrolled the chat a little, and nothing said what
  // ran. The line now opens in place, as with several tasks.
  testWidgets('one task opens in place: name, time and the command; the row '
      'goes to its step', (tester) async {
    final opened = <int>[];
    await _pump(
      tester,
      KitBackgroundWorkLine(
        lineKey: _lineKey,
        tasks: [
          _task('Waiting for grep', const Duration(minutes: 8), command: _loop),
        ],
        onOpenTask: opened.add,
      ),
    );
    expect(
      _says('Still running in the background · Waiting for grep · 8 min'),
      findsOneWidget,
    );
    expect(find.text(_loop), findsNothing);
    await tester.tap(find.byKey(_lineKey));
    await tester.pump();
    // The tap opened the line; it went nowhere.
    expect(opened, isEmpty);
    expect(find.text('Waiting for grep · 8 min'), findsOneWidget);
    final command = tester.widget<Text>(find.text(_loop));
    expect(command.maxLines, 3);
    expect(command.overflow, TextOverflow.ellipsis);
    expect(_says('Shows the step that started it'), findsOneWidget);
    await tester.tap(find.text('Waiting for grep · 8 min'));
    await tester.pump();
    expect(opened, [0]);
    // A second tap on the line folds it again.
    await tester.tap(find.byKey(_lineKey));
    await tester.pump();
    expect(find.text(_loop), findsNothing);
  });

  // Owner, 2026-10-29: under a turn that ended on an API error the line
  // read as if the agent were still working.
  testWidgets('after the turn stopped the line says so', (tester) async {
    Widget line(int count) => KitBackgroundWorkLine(
      afterStop: true,
      tasks: [
        for (var i = 0; i < count; i++)
          _task('Waiting for grep', const Duration(minutes: 7)),
      ],
      onOpenTask: (_) {},
    );
    await _pump(tester, line(1));
    expect(
      _says('Still running after the turn stopped · Waiting for grep · 7 min'),
      findsOneWidget,
    );
    await _pump(tester, line(2));
    expect(
      find.text('2 tasks still running after the turn stopped'),
      findsOneWidget,
    );
  });

  testWidgets('a command with a secret in it is redacted on the row', (
    tester,
  ) async {
    await _pump(
      tester,
      KitBackgroundWorkLine(
        lineKey: _lineKey,
        tasks: [
          _task(
            'curl',
            const Duration(minutes: 1),
            command: 'curl -H "Authorization: Bearer sk-live-1234567890abcdef"',
          ),
        ],
        onOpenTask: (_) {},
      ),
    );
    await tester.tap(find.byKey(_lineKey));
    await tester.pump();
    expect(find.textContaining('sk-live-1234567890abcdef'), findsNothing);
  });

  testWidgets('the time counts on by the minute', (tester) async {
    await _pump(
      tester,
      KitBackgroundWorkLine(
        tasks: [_task('Build the owner APK', const Duration(seconds: 10))],
        onOpenTask: (_) {},
      ),
    );
    expect(_says('under a minute'), findsOneWidget);
    await tester.pump(const Duration(minutes: 1));
    expect(_says('· 1 min'), findsOneWidget);
    await tester.pump(const Duration(hours: 1, minutes: 4));
    expect(_says('· 1 h 5 min'), findsOneWidget);
  });

  testWidgets('several tasks: a count that opens to one row each', (
    tester,
  ) async {
    final opened = <int>[];
    await _pump(
      tester,
      KitBackgroundWorkLine(
        lineKey: _lineKey,
        tasks: [
          _task('Build the owner APK', const Duration(minutes: 7)),
          _task('flutter test', const Duration(minutes: 3)),
        ],
        onOpenTask: opened.add,
      ),
    );
    expect(
      find.text('2 tasks still running in the background'),
      findsOneWidget,
    );
    expect(find.text('flutter test · 3 min'), findsNothing);
    await tester.tap(find.byKey(_lineKey));
    await tester.pump();
    expect(find.text('Build the owner APK · 7 min'), findsOneWidget);
    await tester.tap(find.text('flutter test · 3 min'));
    await tester.pump();
    expect(opened, [1]);
  });

  testWidgets('with no task left the line fades out and takes no room', (
    tester,
  ) async {
    Widget line(List<KitBackgroundTask> tasks) => KitBackgroundWorkLine(
      lineKey: _lineKey,
      tasks: tasks,
      onOpenTask: (_) {},
    );
    await _pump(
      tester,
      line([_task('Build the owner APK', const Duration(minutes: 2))]),
      reduceMotion: false,
    );
    final height = tester.getSize(find.byType(KitBackgroundWorkLine)).height;
    expect(height, greaterThan(0));
    await _pump(tester, line(const []), reduceMotion: false);
    // Mid-fade the old line is still there, fading.
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.byKey(_lineKey), findsOneWidget);
    final fade = tester.widget<FadeTransition>(
      find
          .ancestor(
            of: find.byKey(_lineKey),
            matching: find.byType(FadeTransition),
          )
          .first,
    );
    expect(fade.opacity.value, inExclusiveRange(0, 1));
    await tester.pump(const Duration(milliseconds: 150));
    expect(find.byKey(_lineKey), findsNothing);
    expect(tester.getSize(find.byType(KitBackgroundWorkLine)).height, 0);
  });
}
