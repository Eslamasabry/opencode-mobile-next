// KitWorkLine (docs/ux-system/kit-api/KitWorkLine.md): the frozen "Tests
// required" contract, the reduced-motion sample (G8x) and the keyboard
// behaviour (G14). Arabic plural checks are not run: owner decision
// 2026-09-27 dropped Arabic (no Arabic copy for new keys).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_work_line.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_chip.dart';
import 'package:opencode_mobile/ui/kit/kit_status_mark.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

const _lineKey = ValueKey('work-group-header');
const _stepsKey = ValueKey('work-group-steps');

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double textScale = 1,
  bool reduceMotion = true,
  Size size = const Size(412, 915),
  TextDirection? direction,
  double devicePixelRatio = 1,
}) async {
  tester.view.physicalSize = size * devicePixelRatio;
  tester.view.devicePixelRatio = devicePixelRatio;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, widget) {
        Widget body = MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: TextScaler.linear(textScale),
            disableAnimations: reduceMotion,
          ),
          child: widget!,
        );
        if (direction != null) {
          body = Directionality(textDirection: direction, child: body);
        }
        return body;
      },
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Align(alignment: AlignmentDirectional.topStart, child: child),
        ),
      ),
    ),
  );
}

List<Widget> _steps(int n) => [
  for (var i = 0; i < n; i++)
    SizedBox(
      key: ValueKey('step-$i'),
      height: 24,
      child: Text('Step number $i'),
    ),
];

Widget _line({
  KitWorkState state = KitWorkState.done,
  KitWorkCounts counts = const KitWorkCounts(read: 3, edited: 1),
  List<Widget>? steps,
  String? now,
  bool? expanded,
  ValueChanged<bool>? onExpansionChanged,
}) => KitWorkLine(
  counts: counts,
  state: state,
  steps: steps ?? _steps(3),
  now: now,
  expanded: expanded,
  onExpansionChanged: onExpansionChanged,
  lineKey: _lineKey,
  stepsKey: _stepsKey,
);

/// The summary for [counts] as the part says it, in English.
Future<String> _summary(WidgetTester tester, KitWorkCounts counts) async {
  late String out;
  await _pump(
    tester,
    Builder(
      builder: (context) {
        out = KitWorkLine.summaryOf(context, counts);
        return const SizedBox();
      },
    ),
  );
  return out;
}

/// The chip's one button node: the Semantics KitWorkLine wraps its chip in.
final _chipNode = find
    .ancestor(of: find.byKey(_lineKey), matching: find.byType(Semantics))
    .first;

/// Whether the kit part [owner] paints its keyboard focus ring: a stroke in
/// the theme's `accent` role (LOOK-21). Nothing else in a chip or a
/// tertiary button paints in accent, so this reads the drawn ring, not the
/// widget shape that draws it.
bool _ringShows(WidgetTester tester, Finder owner) {
  final accent = KitTokens.of(tester.element(owner)).roles.accent;
  return _paintCalls(tester, owner).any(
    (args) =>
        args.any((a) => a is Paint && a.color.toARGB32() == accent.toARGB32()),
  );
}

/// The positional arguments of every canvas call [owner]'s subtree makes.
List<List<Object?>> _paintCalls(WidgetTester tester, Finder owner) {
  final canvas = TestRecordingCanvas();
  final context = TestRecordingPaintingContext(canvas);
  tester.renderObject(owner).paint(context, Offset.zero);
  context.dispose();
  return [
    for (final call in canvas.invocations) call.invocation.positionalArguments,
  ];
}

/// Long enough for a Material-backed button's focus side to ease in.
const _ringSettles = Duration(milliseconds: 300);

/// The kit part (a chip or a button) that holds primary focus, if any.
Finder? _focusOwner(WidgetTester tester) {
  final focused = FocusManager.instance.primaryFocus?.context;
  if (focused == null) return null;
  for (final type in [KitChip, KitButton]) {
    final owner = find.ancestor(
      of: find.byElementPredicate((e) => e == focused),
      matching: find.byType(type),
    );
    if (owner.evaluate().isNotEmpty) return owner.first;
  }
  return null;
}

void main() {
  kitMotionStillTests(
    'KitWorkLine',
    builds: {
      'running': () =>
          _line(state: KitWorkState.running, now: 'Reading main.dart'),
      'done': () => _line(),
    },
    changes: {
      'steps open': KitMotionChange(
        build: () => _line(expanded: false),
        act: (tester, stage) => stage.rebuild(_line(expanded: true)),
        shows: 'Step number 2',
      ),
      'steps close': KitMotionChange(
        build: () => _line(expanded: true),
        act: (tester, stage) => stage.rebuild(_line(expanded: false)),
        hides: 'Step number 2',
      ),
    },
  );

  group('summaryOf (1)', () {
    testWidgets('read 3 + edited 1', (tester) async {
      expect(
        await _summary(tester, const KitWorkCounts(read: 3, edited: 1)),
        'Read 3 files · edited 1 file',
      );
    });
    testWidgets('one segment only', (tester) async {
      expect(
        await _summary(tester, const KitWorkCounts(ran: 2)),
        'Ran 2 commands',
      );
    });
    testWidgets('no counted call falls back to steps', (tester) async {
      expect(await _summary(tester, const KitWorkCounts(steps: 4)), '4 steps');
    });
    testWidgets('every segment, singular, in order', (tester) async {
      expect(
        await _summary(
          tester,
          const KitWorkCounts(
            read: 1,
            searched: 1,
            listed: 1,
            edited: 1,
            ran: 1,
            fetched: 1,
            delegated: 1,
            other: 1,
            notRun: 1,
            steps: 9,
          ),
        ),
        'Read 1 file · searched once · listed 1 folder · edited 1 file · '
        'ran 1 command · fetched 1 page · delegated 1 task · 1 other step · '
        '1 not run',
      );
    });
    test('isEmpty', () {
      expect(const KitWorkCounts().isEmpty, isTrue);
      expect(const KitWorkCounts(steps: 1).isEmpty, isFalse);
      expect(const KitWorkCounts(notRun: 1).isEmpty, isFalse);
    });
  });

  group('states (2, 3)', () {
    testWidgets('running shows the live words and a working mark', (
      tester,
    ) async {
      await _pump(
        tester,
        _line(state: KitWorkState.running, now: 'Editing main.dart'),
      );
      expect(find.text('Editing main.dart'), findsOneWidget);
      final marks = tester.widgetList<KitStatusMark>(
        find.byType(KitStatusMark),
      );
      expect(marks.map((m) => m.state), [KitMarkState.working]);
    });

    // Owner, 2026-10-08: opened while running, the line showed the live
    // step with a spinner, and the same step ran again in the list with its
    // own. Opened, the line is a plain collapse control; the running step in
    // the list keeps the only spinner.
    testWidgets('running and opened: "Hide steps", no mark, no live words', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        _line(
          state: KitWorkState.running,
          now: 'Editing main.dart',
          expanded: true,
        ),
      );
      expect(find.byKey(_stepsKey), findsOneWidget);
      expect(find.text('Hide steps'), findsOneWidget);
      expect(find.text('Editing main.dart'), findsNothing);
      expect(find.byType(KitStatusMark), findsNothing);
      expect(find.bySemanticsLabel('Working, Hide steps'), findsOneWidget);
      handle.dispose();
      // Folded again: the live line is back, with its one mark.
      await _pump(
        tester,
        _line(
          state: KitWorkState.running,
          now: 'Editing main.dart',
          expanded: false,
        ),
      );
      expect(find.text('Editing main.dart'), findsOneWidget);
      expect(find.byType(KitStatusMark), findsOneWidget);
    });

    testWidgets('running without now falls back to the summary', (
      tester,
    ) async {
      await _pump(tester, _line(state: KitWorkState.running));
      expect(find.text('Read 3 files · edited 1 file'), findsOneWidget);
    });

    testWidgets('waitingForYou: the words, no mark, no progress anywhere '
        '(AUTO-15)', (tester) async {
      await _pump(
        tester,
        _line(
          state: KitWorkState.waitingForYou,
          now: 'Running tools',
          expanded: true,
        ),
        reduceMotion: false,
      );
      expect(find.text('Waiting for you'), findsOneWidget);
      expect(find.text('Running tools'), findsNothing);
      expect(find.byType(KitStatusMark), findsNothing);
      expect(find.byType(ProgressIndicator), findsNothing);
    });

    testWidgets('done: the summary and no mark', (tester) async {
      await _pump(tester, _line());
      expect(find.text('Read 3 files · edited 1 file'), findsOneWidget);
      expect(find.byType(KitStatusMark), findsNothing);
      expect(find.byKey(_stepsKey), findsNothing);
    });

    testWidgets('stopped says so', (tester) async {
      await _pump(tester, _line(state: KitWorkState.stopped));
      expect(
        find.text('Read 3 files · edited 1 file · Stopped'),
        findsOneWidget,
      );
      expect(find.byType(KitStatusMark), findsNothing);
    });

    testWidgets('endedFailed: failed mark with its word, open on first '
        'build, no danger colour (LOOK-5 interim)', (tester) async {
      await _pump(tester, _line(state: KitWorkState.endedFailed));
      final marks = tester.widgetList<KitStatusMark>(
        find.byType(KitStatusMark),
      );
      expect(marks.map((m) => m.state), [KitMarkState.failed]);
      expect(find.text("Didn't finish"), findsOneWidget);
      expect(find.byKey(_stepsKey), findsOneWidget);

      final theme = AppTheme.dark();
      final roles = ThemeRoles.resolve(theme);
      final forbidden = {
        roles.danger,
        roles.dangerFill,
        theme.colorScheme.error,
      };
      final line = find.byType(KitWorkLine);
      final colours = <Color?>[
        for (final t in tester.widgetList<RichText>(
          find.descendant(of: line, matching: find.byType(RichText)),
        ))
          t.text.style?.color,
        for (final i in tester.widgetList<Icon>(
          find.descendant(of: line, matching: find.byType(Icon)),
        ))
          i.color,
      ];
      expect(colours.where(forbidden.contains), isEmpty);
      expect(
        tester
            .widget<RichText>(
              find
                  .descendant(
                    of: find.text("Didn't finish"),
                    matching: find.byType(RichText),
                  )
                  .first,
            )
            .text
            .style
            ?.color,
        roles.text1,
      );
    });
  });

  group('toggling (4)', () {
    testWidgets('tap toggles an uncontrolled line', (tester) async {
      final changes = <bool>[];
      await _pump(tester, _line(onExpansionChanged: changes.add));
      expect(find.byKey(_stepsKey), findsNothing);
      await tester.tap(find.byKey(_lineKey));
      await tester.pump();
      expect(find.byKey(_stepsKey), findsOneWidget);
      await tester.tap(find.byKey(_lineKey));
      await tester.pump();
      expect(find.byKey(_stepsKey), findsNothing);
      expect(changes, [true, false]);
    });

    testWidgets('controlled never toggles on its own', (tester) async {
      final changes = <bool>[];
      await _pump(
        tester,
        _line(expanded: false, onExpansionChanged: changes.add),
      );
      await tester.tap(find.byKey(_lineKey));
      await tester.pump();
      expect(changes, [true]);
      expect(find.byKey(_stepsKey), findsNothing);

      await _pump(
        tester,
        _line(expanded: true, onExpansionChanged: changes.add),
      );
      await tester.tap(find.byKey(_lineKey));
      await tester.pump();
      expect(changes, [true, false]);
      expect(find.byKey(_stepsKey), findsOneWidget);
    });

    testWidgets('uncontrolled starts from opensByDefault', (tester) async {
      for (final state in KitWorkState.values) {
        await _pump(tester, _line(state: state));
        expect(
          find.byKey(_stepsKey).evaluate().isNotEmpty,
          KitWorkLine.opensByDefault(state),
          reason: '$state',
        );
      }
      expect(KitWorkLine.opensByDefault(KitWorkState.endedFailed), isTrue);
      expect(KitWorkLine.opensByDefault(KitWorkState.done), isFalse);
    });
  });

  testWidgets('opened: steps in order under stepsKey, summary not repeated '
      '(5)', (tester) async {
    await _pump(tester, _line(expanded: true));
    final steps = find.descendant(
      of: find.byKey(_stepsKey),
      matching: find.textContaining('Step number'),
    );
    expect(tester.widgetList<Text>(steps).map((t) => t.data), [
      'Step number 0',
      'Step number 1',
      'Step number 2',
    ]);
    expect(
      find.descendant(
        of: find.byKey(_stepsKey),
        matching: find.textContaining('Read 3 files'),
      ),
      findsNothing,
    );
    expect(find.textContaining('Read 3 files'), findsOneWidget);
  });

  testWidgets('30 steps: 25 built, "Show 5 earlier steps" reveals the rest '
      'in place and focus moves to the first (6)', (tester) async {
    await _pump(tester, _line(expanded: true, steps: _steps(30)));
    expect(find.byKey(const ValueKey('step-4')), findsNothing);
    expect(find.byKey(const ValueKey('step-5')), findsOneWidget);
    expect(find.byKey(const ValueKey('step-29')), findsOneWidget);
    final earlier = find.text('Show 5 earlier steps');
    expect(earlier, findsOneWidget);
    // The earlier-steps row sits above the newest steps.
    expect(
      tester.getTopLeft(earlier).dy,
      lessThan(tester.getTopLeft(find.byKey(const ValueKey('step-5'))).dy),
    );

    await tester.tap(earlier);
    await tester.pump();
    await tester.pump();
    expect(find.text('Show 5 earlier steps'), findsNothing);
    for (var i = 0; i < 30; i++) {
      expect(find.byKey(ValueKey('step-$i')), findsOneWidget);
    }
    final first = tester.getTopLeft(find.byKey(const ValueKey('step-0'))).dy;
    expect(
      first,
      lessThan(tester.getTopLeft(find.byKey(const ValueKey('step-5'))).dy),
    );
    final focused = FocusManager.instance.primaryFocus?.context;
    expect(focused, isNotNull);
    var holdsStep = false;
    (focused! as Element).visitChildElements((child) {
      void visit(Element e) {
        if (e.widget.key == const ValueKey('step-0')) holdsStep = true;
        e.visitChildElements(visit);
      }

      visit(child);
    });
    expect(holdsStep, isTrue);
  });

  testWidgets('keyboard reveal: focus lands on the first revealed step\'s '
      'own control, and no later Tab stop lacks a visible ring (G14)', (
    tester,
  ) async {
    final buttons = [
      for (var i = 0; i < 30; i++)
        KitButton.tertiary(
          key: ValueKey('step-$i'),
          label: 'Step $i',
          onPressed: () {},
        ),
    ];
    await _pump(tester, _line(expanded: true, steps: buttons));
    // Tab to "Show 5 earlier steps" (after the chip) and press it.
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(
      tester.widget(_focusOwner(tester)!),
      tester.widget(
        find.ancestor(
          of: find.text('Show 5 earlier steps'),
          matching: find.byType(KitButton),
        ),
      ),
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    // Reveal, then the post-frame focus request, then the button's ring
    // (its Material eases the side in over the theme-change duration).
    await tester.pump();
    await tester.pump();
    await tester.pump(_ringSettles);

    final step0 = find.byKey(const ValueKey('step-0'));
    expect(tester.widget(_focusOwner(tester)!), tester.widget(step0));
    expect(_ringShows(tester, step0), isTrue);

    // Two full passes: chip + 30 steps, each stop a part with its ring.
    for (var n = 0; n < 62; n++) {
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.pump(_ringSettles);
      final owner = _focusOwner(tester);
      expect(owner, isNotNull, reason: 'Tab stop $n has no kit part');
      expect(_ringShows(tester, owner!), isTrue, reason: 'Tab stop $n');
    }
  });

  testWidgets('prose steps: focus holds the first revealed step once, then '
      'it is never a Tab stop again (G14)', (tester) async {
    await _pump(tester, _line(expanded: true, steps: _steps(30)));
    await tester.tap(find.text('Show 5 earlier steps'));
    await tester.pump();
    await tester.pump();
    final revealed = FocusManager.instance.primaryFocus;
    expect(revealed, isNotNull);
    expect(_focusOwner(tester), isNull);
    for (var n = 0; n < 4; n++) {
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(FocusManager.instance.primaryFocus, isNot(same(revealed)));
      expect(
        tester.widget(_focusOwner(tester)!),
        tester.widget(find.byKey(_lineKey)),
      );
    }
  });

  group('motion (7, G8x)', () {
    testWidgets('no size or layout animation in the subtree', (tester) async {
      await _pump(
        tester,
        _line(expanded: true, state: KitWorkState.running),
        reduceMotion: false,
      );
      final line = find.byType(KitWorkLine);
      for (final type in [AnimatedSize, AnimatedContainer, SizeTransition]) {
        expect(
          find.descendant(of: line, matching: find.byType(type)),
          findsNothing,
          reason: '$type',
        );
      }
    });

    testWidgets('reduced motion: one pump settles the opening', (tester) async {
      await _pump(tester, _line());
      await tester.tap(find.byKey(_lineKey));
      await tester.pump();
      expect(tester.hasRunningAnimations, isFalse);
      expect(find.byKey(_stepsKey), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(KitWorkLine),
          matching: find.byType(FadeTransition),
        ),
        findsNothing,
      );
    });

    testWidgets('full motion: steps fade in over quick (paint only)', (
      tester,
    ) async {
      await _pump(tester, _line(), reduceMotion: false);
      await tester.tap(find.byKey(_lineKey));
      await tester.pump();
      final fade = tester.widget<FadeTransition>(
        find
            .ancestor(
              of: find.byKey(const ValueKey('step-0')),
              matching: find.byType(FadeTransition),
            )
            .first,
      );
      expect(fade.opacity.value, lessThan(1));
      await tester.pumpAndSettle();
      expect(fade.opacity.value, 1);
    });
  });

  group('semantics (8)', () {
    Future<void> expectLabel(
      WidgetTester tester,
      KitWorkState state,
      String label, {
      String? now,
    }) async {
      final handle = tester.ensureSemantics();
      await _pump(tester, _line(state: state, now: now));
      final node = tester.getSemantics(_chipNode);
      expect(
        node,
        isSemantics(
          label: label,
          isButton: true,
          hasTapAction: true,
          hasExpandedState: true,
          isExpanded: KitWorkLine.opensByDefault(state),
          isLiveRegion: false,
        ),
        reason: '$state',
      );
      final size = tester.getSize(_chipNode);
      expect(size.width, greaterThanOrEqualTo(48));
      expect(size.height, greaterThanOrEqualTo(48));
      handle.dispose();
    }

    testWidgets('labels per state', (tester) async {
      const summary = 'Read 3 files · edited 1 file';
      await expectLabel(
        tester,
        KitWorkState.running,
        'Working, Editing main.dart',
        now: 'Editing main.dart',
      );
      await expectLabel(
        tester,
        KitWorkState.waitingForYou,
        'Waiting for you, $summary',
      );
      await expectLabel(tester, KitWorkState.done, summary);
      await expectLabel(
        tester,
        KitWorkState.endedFailed,
        "Didn't finish, $summary",
      );
      await expectLabel(tester, KitWorkState.stopped, 'Stopped, $summary');
    });

    testWidgets('the leading mark says nothing of its own', (tester) async {
      final handle = tester.ensureSemantics();
      await _pump(tester, _line(state: KitWorkState.endedFailed, steps: []));
      expect(find.bySemanticsLabel("Didn't finish"), findsNothing);
      expect(find.bySemanticsLabel(RegExp('^Failed')), findsNothing);
      handle.dispose();
    });
  });

  testWidgets('desktop: Tab reaches the chip, Enter toggles, the focus ring '
      'shows (9)', (tester) async {
    await _pump(
      tester,
      _line(
        steps: [TextButton(onPressed: () {}, child: const Text('Step button'))],
      ),
    );
    final chip = find.byKey(_lineKey);
    expect(_ringShows(tester, chip), isFalse);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    // Primary focus sits within the chip, and the chip draws its ring.
    expect(_focusOwner(tester), isNotNull);
    expect(tester.widget(_focusOwner(tester)!), tester.widget(chip));
    expect(_ringShows(tester, chip), isTrue);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pump();
    expect(find.byKey(_stepsKey), findsOneWidget);
    // Focus stays on the chip; Tab continues into the steps.
    expect(tester.widget(_focusOwner(tester)!), tester.widget(chip));
    expect(_ringShows(tester, chip), isTrue);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    final focused = FocusManager.instance.primaryFocus?.context;
    expect(
      find.ancestor(
        of: find.byWidget(focused!.widget),
        matching: find.byKey(_stepsKey),
      ),
      findsOneWidget,
    );
  });

  group('200 % text at 320 dp (10, G6)', () {
    for (final direction in TextDirection.values) {
      for (final state in KitWorkState.values) {
        testWidgets('$state · ${direction.name}', (tester) async {
          await _pump(
            tester,
            _line(
              state: state,
              expanded: true,
              now: 'Editing lib/ui/screens/chat/message_view.dart',
              counts: const KitWorkCounts(
                read: 12,
                searched: 3,
                edited: 4,
                ran: 2,
                notRun: 1,
              ),
              steps: _steps(30),
            ),
            textScale: 2,
            size: const Size(320, 800),
            direction: direction,
          );
          expect(tester.takeException(), isNull);
        });
      }
    }
  });

  testWidgets('a running line that ends on a failure opens by itself', (
    tester,
  ) async {
    await _pump(tester, _line(state: KitWorkState.running));
    expect(find.byKey(_stepsKey), findsNothing);
    await _pump(tester, _line(state: KitWorkState.endedFailed));
    expect(find.byKey(_stepsKey), findsOneWidget);
  });

  testWidgets('the opened steps draw no grouping stroke (DPR 3)', (
    tester,
  ) async {
    await _pump(tester, _line(expanded: true), devicePixelRatio: 3);
    final steps = find.byKey(_stepsKey);
    final hairline = KitTokens.of(tester.element(steps)).roles.hairline;
    // The steps sit on the transcript's one gutter (crit-chat-insets): no
    // shape of the steps is painted in the hairline role.
    final strokes = [
      for (final args in _paintCalls(tester, steps))
        if (args.any(
          (a) => a is Paint && a.color.toARGB32() == hairline.toARGB32(),
        ))
          args,
    ];
    expect(strokes, isEmpty);
  });
}
