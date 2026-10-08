// Behaviour tests for KitChecklist and the SetupProgressView adapter
// (docs/ux-system/kit-api/KitChecklist.md "Tests required").
import 'kit_motion_still.dart';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/setup_progress_view.dart';

import 'kit_harness.dart';

Future<void> _show(
  WidgetTester tester,
  Widget child, {
  bool reduceMotion = false,
  KitEffects effects = KitEffects.defaults,
  bool scroll = true,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    KitEffectsScope(
      effects: effects,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, app) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(disableAnimations: reduceMotion),
          child: app!,
        ),
        home: Scaffold(
          body: scroll
              ? SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: child,
                )
              : child,
        ),
      ),
    ),
  );
}

KitStep _step(
  String title,
  KitMarkState state, {
  String? supporting,
  KitAction? personAction,
  KitAction? retry,
}) => KitStep(
  title: title,
  state: state,
  supporting: supporting,
  personAction: personAction,
  retry: retry,
);

String _label(WidgetTester tester, Finder finder) =>
    tester.getSemantics(finder).label;

void main() {
  Widget motionChecklist(KitMarkState state) => KitChecklist(
    steps: [KitStep(title: 'Install tools', state: state)],
  );
  kitMotionStillTests(
    'KitChecklist',
    builds: {
      'working': () => motionChecklist(KitMarkState.working),
      'waiting': () => motionChecklist(KitMarkState.waiting),
    },
    changes: {
      'step completes': KitMotionChange(
        build: () => motionChecklist(KitMarkState.working),
        act: (tester, stage) =>
            stage.rebuild(motionChecklist(KitMarkState.done)),
        shows: 'Install tools',
      ),
    },
  );

  testWidgets('1. each row reads "Step n of N, title, state, supporting"', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await _show(
      tester,
      KitChecklist(
        steps: [
          const KitStep(
            key: ValueKey('a'),
            title: 'Linux base',
            state: KitMarkState.done,
            supporting: 'Already installed',
          ),
          const KitStep(
            key: ValueKey('b'),
            title: 'Download',
            state: KitMarkState.working,
            supporting: '29 of 30 MB',
          ),
          KitStep(
            key: const ValueKey('c'),
            title: 'Notifications',
            state: KitMarkState.waiting,
            personAction: KitAction(label: 'Allow', onPressed: () {}),
          ),
        ],
      ),
    );
    expect(
      _label(tester, find.byKey(const ValueKey('a'))),
      'Step 1 of 3, Linux base, Done, Already installed',
    );
    expect(
      _label(tester, find.byKey(const ValueKey('b'))),
      'Step 2 of 3, Download, Working, 29 of 30 MB',
    );
    expect(
      _label(tester, find.byKey(const ValueKey('c'))),
      'Step 3 of 3, Notifications, Waiting, needs you, Allow',
    );
    semantics.dispose();
  });

  testWidgets('2. a person step shows the needs-you mark and its button', (
    tester,
  ) async {
    var taps = 0;
    await _show(
      tester,
      KitChecklist(
        steps: [
          _step('Termux', KitMarkState.done),
          _step(
            'Get Termux',
            KitMarkState.waiting,
            supporting: 'From F-Droid',
            personAction: KitAction(
              key: const ValueKey('allow'),
              label: 'Get Termux',
              onPressed: () => taps++,
            ),
          ),
        ],
      ),
    );
    expect(find.byType(KitTaskMark), findsOneWidget);
    expect(find.textContaining('Needs you · From F-Droid'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('allow')));
    await tester.pump();
    expect(taps, 1);
  });

  testWidgets('3. Try again sits on the failed row only', (tester) async {
    await _show(
      tester,
      KitChecklist(
        steps: [
          _step('Linux base', KitMarkState.done),
          _step(
            'Node.js',
            KitMarkState.failed,
            supporting: 'The download stopped',
            retry: KitAction(
              key: const ValueKey('retry'),
              label: 'Try again',
              onPressed: () {},
            ),
          ),
          _step('OpenCode', KitMarkState.waiting),
        ],
      ),
    );
    expect(find.byKey(const ValueKey('retry')), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('kit-checklist-step-1')),
        matching: find.byKey(const ValueKey('retry')),
      ),
      findsOneWidget,
    );
    expect(
      () => KitStep(
        title: 'x',
        state: KitMarkState.working,
        retry: KitAction(label: 'Try again', onPressed: () {}),
      ),
      throwsAssertionError,
    );
  });

  testWidgets('4. a working step escalates at 8 s, once', (tester) async {
    final semantics = tester.ensureSemantics();
    final since = clock.now();
    await _show(
      tester,
      KitChecklist(
        since: since,
        onSlow: [
          KitAction(
            key: const ValueKey('way-out'),
            label: 'Run in background',
            onPressed: () {},
          ),
        ],
        steps: [
          _step('Download', KitMarkState.working, supporting: '12 of 30 MB'),
          _step('Unpack', KitMarkState.waiting),
        ],
      ),
    );
    const summary = ValueKey('kit-checklist-summary');
    final before = _label(tester, find.byKey(summary));
    await tester.pump(const Duration(seconds: 7));
    expect(find.text('12 of 30 MB'), findsOneWidget);
    expect(find.byKey(const ValueKey('way-out')), findsNothing);
    expect(_label(tester, find.byKey(summary)), before);

    await tester.pump(const Duration(seconds: 1));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Still waiting after 8 s'), findsOneWidget);
    expect(find.byKey(const ValueKey('way-out')), findsOneWidget);
    final slow = _label(tester, find.byKey(summary));
    expect(slow, '$before, Still waiting after 8 s');
    expect(
      tester.getSemantics(find.byKey(summary)),
      isSemantics(isLiveRegion: true),
    );
    // The escalation is one change of the live region, not a tick.
    await tester.pump(const Duration(seconds: 30));
    expect(_label(tester, find.byKey(summary)), slow);
    semantics.dispose();
  });

  testWidgets('4b. a slow step counts the wait it shows, the live region '
      'stays one change', (tester) async {
    final semantics = tester.ensureSemantics();
    await _show(
      tester,
      KitChecklist(
        since: clock.now(),
        steps: [
          _step('Start OpenCode', KitMarkState.working),
          _step('Connect', KitMarkState.waiting),
        ],
      ),
    );
    await tester.pump(const Duration(seconds: 8));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Still waiting after 8 s'), findsOneWidget);
    const summary = ValueKey('kit-checklist-summary');
    final slow = _label(tester, find.byKey(summary));
    // A counter that stood at "8 s" for twenty seconds read as stuck.
    await tester.pump(const Duration(seconds: 12));
    expect(find.text('Still waiting after 20 s'), findsOneWidget);
    expect(find.text('Still waiting after 8 s'), findsNothing);
    // Past a minute it says minutes, not a count of seconds.
    await tester.pump(const Duration(seconds: 45));
    expect(find.text('Waiting 1 min'), findsOneWidget);
    // Screen readers heard the escalation once; the count is not announced.
    expect(_label(tester, find.byKey(summary)), slow);
    semantics.dispose();
  });

  group('5. done haptic', () {
    List<KitStep> steps(KitMarkState last) => [
      _step('One', KitMarkState.done),
      _step('Two', last),
    ];

    testWidgets('fires once on the turn to done', (tester) async {
      final calls = recordHaptics(tester);
      await _show(tester, KitChecklist(steps: steps(KitMarkState.working)));
      expect(calls, isEmpty);
      await _show(tester, KitChecklist(steps: steps(KitMarkState.done)));
      await tester.pump();
      expect(calls, ['HapticFeedbackType.successNotification']);
      await _show(tester, KitChecklist(steps: steps(KitMarkState.done)));
      expect(calls, hasLength(1));
    });

    testWidgets('not on a first build that is already done', (tester) async {
      final calls = recordHaptics(tester);
      await _show(tester, KitChecklist(steps: steps(KitMarkState.done)));
      await tester.pump();
      expect(calls, isEmpty);
    });

    testWidgets('not with Vibration off', (tester) async {
      final calls = recordHaptics(tester);
      const off = KitEffects(haptics: false);
      await _show(
        tester,
        KitChecklist(steps: steps(KitMarkState.working)),
        effects: off,
      );
      await _show(
        tester,
        KitChecklist(steps: steps(KitMarkState.done)),
        effects: off,
      );
      expect(calls, isEmpty);
    });
  });

  testWidgets('6. estimate and cost show before the start only', (
    tester,
  ) async {
    KitChecklist checklist(KitMarkState first) => KitChecklist(
      estimate: 'About 8 minutes the first time',
      cost: const ['About 208 MB', 'uses battery while it installs'],
      steps: [_step('Download', first), _step('Install', KitMarkState.waiting)],
    );
    await _show(tester, checklist(KitMarkState.waiting));
    expect(find.text('About 8 minutes the first time'), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-checklist-cost')), findsOneWidget);
    await _show(tester, checklist(KitMarkState.working));
    expect(find.text('About 8 minutes the first time'), findsNothing);
    expect(find.byKey(const ValueKey('kit-checklist-cost')), findsNothing);
  });

  testWidgets('7. compact is one line that unfolds and folds back', (
    tester,
  ) async {
    await _show(
      tester,
      KitChecklist(
        compact: true,
        next: 'merge',
        steps: [
          _step('Plan', KitMarkState.done),
          _step('Build', KitMarkState.done),
          _step('Reviewing', KitMarkState.working),
          _step('Merge', KitMarkState.waiting),
          _step('Check', KitMarkState.waiting),
          _step('Deploy', KitMarkState.waiting),
          _step('Report', KitMarkState.waiting),
        ],
      ),
    );
    expect(find.text('Step 3 of 7 · Reviewing · next: merge'), findsOneWidget);
    expect(find.text('Deploy'), findsNothing);

    const line = ValueKey('kit-checklist-compact');
    await tester.tap(find.byKey(line));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Deploy'), findsOneWidget);
    await tester.tap(find.byKey(line));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Deploy'), findsNothing);

    // Enter on the focused line unfolds it too.
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Deploy'), findsOneWidget);
  });

  testWidgets('8. the log is folded until Details opens it', (tester) async {
    final buffer = KitLogBuffer()..appendText('Get:1 noble InRelease\n');
    addTearDown(buffer.dispose);
    await _show(
      tester,
      KitChecklist(
        detailsKey: const ValueKey('details'),
        log: KitLogPanel(lines: buffer),
        steps: [_step('Download', KitMarkState.working)],
      ),
    );
    expect(find.byKey(const ValueKey('kit-log-panel')), findsNothing);
    await tester.tap(find.byKey(const ValueKey('details')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byKey(const ValueKey('kit-log-panel')), findsOneWidget);
  });

  group('9. SetupProgressView adapter keeps its keys and words', () {
    const components = [
      ComponentProgress(id: 'linux', state: ComponentState.done),
      ComponentProgress(
        id: 'node',
        state: ComponentState.running,
        stage: 'Downloading',
        bytesDone: 18000000,
        bytesTotal: 30000000,
      ),
      ComponentProgress(id: 'opencode', state: ComponentState.pending),
    ];

    testWidgets('while running', (tester) async {
      await _show(
        tester,
        SetupProgressView(
          progress: const SetupProgress(
            state: SetupState.running,
            overall: .4,
            components: components,
          ),
          components: const [],
          onCancel: () {},
          onContinue: () {},
        ),
        scroll: false,
      );
      for (final key in [
        'setup-progress-checklist',
        'setup-progress-row-linux',
        'setup-progress-row-node',
        'setup-progress-cancel',
        'setup-progress-details',
        'setup-progress-overall',
      ]) {
        expect(find.byKey(ValueKey(key)), findsOneWidget, reason: key);
      }
      expect(find.byKey(const Key('setup-progress-continue')), findsNothing);
      expect(find.text('Downloading · 18 of 30 MB'), findsOneWidget);
    });

    testWidgets('a network failure asks to reconnect', (tester) async {
      await _show(
        tester,
        SetupProgressView(
          progress: const SetupProgress(
            state: SetupState.failed,
            overall: .4,
            logTail: 'curl: (6) Could not resolve host: nodejs.org\n',
            components: [
              ComponentProgress(id: 'linux', state: ComponentState.done),
              ComponentProgress(
                id: 'node',
                state: ComponentState.failed,
                error: 'Exit code 6',
              ),
            ],
          ),
          components: const [],
          onContinue: () {},
          onCancel: () {},
        ),
        scroll: false,
      );
      expect(
        find.text("No internet connection — Continue when you're back online"),
        findsOneWidget,
      );
      expect(find.byKey(const Key('setup-progress-continue')), findsOneWidget);
      expect(find.byKey(const Key('setup-progress-cancel')), findsNothing);
    });

    testWidgets('a job error with no failed row', (tester) async {
      await _show(
        tester,
        SetupProgressView(
          progress: const SetupProgress(
            state: SetupState.failed,
            overall: 1,
            error: 'OpenCode did not start',
            components: [
              ComponentProgress(id: 'linux', state: ComponentState.done),
            ],
          ),
          components: const [],
          onContinue: () {},
        ),
        scroll: false,
      );
      expect(find.byKey(const Key('setup-progress-job-error')), findsOneWidget);
      // The job's own text is under Details, never the words (no raw
      // errors; slice-close-servers).
      expect(find.text('OpenCode did not start'), findsNothing);
      expect(
        find.text(
          lookupAppLocalizations(
            const Locale('en'),
          ).setupProgressViewFailedUnknown,
        ),
        findsOneWidget,
      );
    });
  });

  testWidgets('10. reduced motion settles after one pump', (tester) async {
    await _show(
      tester,
      reduceMotion: true,
      KitChecklist(
        progress: const KitProgress.staged(step: 2, of: 3, label: 'Download'),
        steps: [
          _step('Linux base', KitMarkState.done),
          const KitStep(
            title: 'Download',
            state: KitMarkState.working,
            value: .4,
          ),
          _step('Install', KitMarkState.waiting),
        ],
      ),
    );
    await tester.pump();
    expect(tester.binding.hasScheduledFrame, isFalse);
  });

  testWidgets('11. Tab visits person, retry, resume, stop, then Details', (
    tester,
  ) async {
    final buffer = KitLogBuffer();
    addTearDown(buffer.dispose);
    KitAction action(String key, String label, {bool destructive = false}) =>
        KitAction(
          key: ValueKey(key),
          label: label,
          destructive: destructive,
          onPressed: () {},
        );
    await _show(
      tester,
      KitChecklist(
        detailsKey: const ValueKey('details'),
        log: KitLogPanel(lines: buffer),
        resume: action('resume', 'Continue setup'),
        stop: action('stop', 'Stop', destructive: true),
        steps: [
          _step('Linux base', KitMarkState.done),
          _step(
            'Notifications',
            KitMarkState.waiting,
            personAction: action('person', 'Allow'),
          ),
          _step(
            'Node.js',
            KitMarkState.failed,
            retry: action('retry', 'Try again'),
          ),
        ],
      ),
    );
    const order = ['person', 'retry', 'resume', 'stop', 'details'];
    String? focused() {
      final context = FocusManager.instance.primaryFocus?.context;
      if (context == null) return null;
      String? hit;
      bool match(Element element) {
        final key = element.widget.key;
        if (key is ValueKey<String> && order.contains(key.value)) {
          hit = key.value;
          return false;
        }
        return true;
      }

      if (match(context as Element)) context.visitAncestorElements(match);
      return hit;
    }

    final seen = <String>[];
    for (var i = 0; i < 12 && seen.length < order.length; i++) {
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      final key = focused();
      if (key != null && (seen.isEmpty || seen.last != key)) seen.add(key);
    }
    expect(seen, order);
  });

  testWidgets('12. Report this failure sits on the failed row only (P8.4)', (
    tester,
  ) async {
    var reported = 0;
    await _show(
      tester,
      KitChecklist(
        steps: [
          _step('Linux base', KitMarkState.done),
          KitStep(
            key: const ValueKey('kit-checklist-step-1'),
            title: 'Node.js',
            state: KitMarkState.failed,
            supporting: 'The download stopped',
            retry: KitAction(
              key: const ValueKey('retry'),
              label: 'Try again',
              onPressed: () {},
            ),
            report: KitAction(
              key: const ValueKey('report'),
              label: 'Report this failure',
              onPressed: () => reported++,
            ),
          ),
        ],
      ),
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('kit-checklist-step-1')),
        matching: find.byKey(const ValueKey('report')),
      ),
      findsOneWidget,
    );
    // Try again stays the row's one button; Report follows under it.
    expect(
      tester.getTopLeft(find.byKey(const ValueKey('report'))).dy,
      greaterThan(tester.getTopLeft(find.byKey(const ValueKey('retry'))).dy),
    );
    await tester.tap(find.byKey(const ValueKey('report')));
    expect(reported, 1);
    expect(
      () => KitStep(
        title: 'x',
        state: KitMarkState.done,
        report: KitAction(label: 'Report this failure', onPressed: () {}),
      ),
      throwsAssertionError,
    );
  });
}
