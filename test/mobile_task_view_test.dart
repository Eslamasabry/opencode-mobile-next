import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/mobile_tool_view.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/widgets/mobile_task_view.dart';

void main() {
  const rows = [
    {'content': 'Review changes', 'status': 'completed'},
    {'content': 'Run focused checks', 'status': 'in_progress'},
    {'content': 'Publish', 'status': 'cancelled'},
  ];
  const prioritised = [
    {'content': 'Review changes', 'status': 'completed', 'priority': 'low'},
    {
      'content': 'Run focused checks',
      'status': 'in_progress',
      'priority': 'high',
    },
    {'content': 'Publish', 'status': 'cancelled', 'priority': 'medium'},
    {'content': 'Announce', 'status': 'pending'},
  ];

  Widget host(Widget child, {double? width, double textScale = 1}) {
    return MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, inner) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: inner!,
      ),
      home: Scaffold(
        body: SingleChildScrollView(
          child: width == null
              ? child
              : Align(
                  alignment: Alignment.topLeft,
                  child: SizedBox(width: width, child: child),
                ),
        ),
      ),
    );
  }

  test('only bundled schema and bounded known statuses render', () {
    final view = MobileTaskView.fromTodos(rows)!;
    expect(view.tasks.length, 3);
    expect(view.tasks.last.status, MobileTaskStatus.cancelled);
    expect(
      MobileTaskView.fromDeclaration({
        'renderer': MobileTaskView.rendererID,
        'version': 2,
        'tasks': rows,
      }),
      isNull,
    );
    expect(
      MobileTaskView.fromDeclaration({
        'renderer': MobileTaskView.rendererID,
        'version': 1,
        'tasks': rows,
        'action': {'url': 'https://example.test'},
      }),
      isNull,
    );
    expect(
      MobileTaskView.fromTodos([
        {'content': 'Unknown outcome', 'status': 'success'},
      ]),
      isNull,
    );
    expect(MobileTaskView.fromTodos(List.filled(65, rows.first)), isNull);
    expect(
      MobileTaskView.fromTodos([
        {'content': 'x' * 1025, 'status': 'completed'},
      ]),
      isNull,
    );
    expect(
      MobileTaskView.fromTodos(
        List.filled(40, {'content': 'x' * 1024, 'status': 'pending'}),
      ),
      isNull,
    );
  });

  test('priority is optional, reviewed values only, never coerced', () {
    final view = MobileTaskView.fromTodos(prioritised)!;
    expect(view.tasks[0].priority, MobileTaskPriority.low);
    expect(view.tasks[1].priority, MobileTaskPriority.high);
    expect(view.tasks[2].priority, MobileTaskPriority.medium);
    expect(view.tasks[3].priority, isNull);
    // Rows without the key still parse (older servers omit it).
    expect(
      MobileTaskView.fromTodos(rows)!.tasks.every((t) => t.priority == null),
      isTrue,
    );
    // Anything else rejects the structured view, like an unknown status.
    expect(
      MobileTaskView.fromTodos([
        {'content': 'Ship', 'status': 'pending', 'priority': 'urgent'},
      ]),
      isNull,
    );
    expect(
      MobileTaskView.fromTodos([
        {'content': 'Ship', 'status': 'pending', 'priority': 1},
      ]),
      isNull,
    );
    expect(
      MobileTaskView.fromTodos([
        {'content': 'Ship', 'status': 'pending', 'priority': 'HIGH'},
      ]),
      isNull,
    );
  });

  test('progress counts completed out of tracked, ignoring cancelled', () {
    final view = MobileTaskView.fromTodos(prioritised)!;
    expect(view.completedCount, 1);
    expect(view.trackedCount, 3);
    final allCancelled = MobileTaskView.fromTodos([
      {'content': 'A', 'status': 'cancelled'},
      {'content': 'B', 'status': 'cancelled'},
    ])!;
    expect(allCancelled.completedCount, 0);
    expect(allCancelled.trackedCount, 0);
  });

  test('toPlainText is deterministic, ordered and preserves task text', () {
    final view = MobileTaskView.fromTodos(prioritised)!;
    const expected =
        '[completed · low] Review changes\n'
        '[in_progress · high] Run focused checks\n'
        '[cancelled · medium] Publish\n'
        '[pending] Announce';
    expect(view.toPlainText(), expected);
    expect(view.toPlainText(), view.toPlainText());
    const tricky = '[Open](https://example.test) <script>no()</script>  \n x';
    final inert = MobileTaskView.fromTodos([
      {'content': tricky, 'status': 'pending', 'priority': 'high'},
    ])!;
    expect(inert.toPlainText(), '[pending · high] $tricky');
    final bounded = MobileTaskView.fromTodos(
      List.filled(30, {'content': 'x' * 1024, 'status': 'pending'}),
    )!;
    expect(bounded.toPlainText().length, lessThan(32768 + 30 * 16));
  });

  test('fallback preserves unknown status and priority as bounded text', () {
    expect(
      MobileTaskView.fallback([
        {'content': 'Unknown outcome', 'status': 'success'},
      ]),
      '[success] Unknown outcome',
    );
    expect(
      MobileTaskView.fallback([
        {'content': 'Ship', 'status': 'pending', 'priority': 'urgent'},
      ]),
      '[pending · urgent] Ship',
    );
    expect(
      MobileTaskView.fallback([
        {'content': 'Ship', 'status': 'pending', 'priority': 'p' * 41},
      ]),
      '[pending] Ship',
    );
    final fallback = MobileTaskView.fallback(
      List.filled(100, {'content': 'x' * 10000, 'status': 'pending'}),
    );
    expect(fallback.length, lessThan(26000));
  });

  testWidgets('filter changes presentation without mutating reported tasks', (
    tester,
  ) async {
    final view = MobileTaskView.fromTodos(rows)!;
    await tester.pumpWidget(host(MobileTaskList(view: view)));
    expect(find.text('Review changes'), findsOneWidget);
    expect(find.text('Cancelled'), findsOneWidget);
    await tester.tap(find.text('Show unfinished only'));
    await tester.pump();
    expect(find.text('Review changes'), findsNothing);
    expect(find.text('Publish'), findsNothing);
    expect(find.text('Run focused checks'), findsOneWidget);
    expect(view.tasks.length, 3);
    await tester.tap(find.text('Show unfinished only'));
    await tester.pump();
    expect(find.text('Review changes'), findsOneWidget);
  });

  testWidgets('filter label wraps in full instead of fading at 320 px / 2x', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    final view = MobileTaskView.fromTodos(rows)!;
    await tester.pumpWidget(
      host(MobileTaskList(view: view), width: 320, textScale: 2),
    );
    final label = find.text('Show unfinished only');
    final paragraph = tester.renderObject<RenderParagraph>(label);
    // The whole wording is laid out inside its box: no horizontal clipping
    // and no fade shader (which is what a chip label produced here)...
    expect(paragraph.debugHasOverflowShader, isFalse);
    expect(
      paragraph.textSize.width,
      lessThanOrEqualTo(paragraph.size.width + 0.01),
    );
    // ...because it wrapped onto more than one line...
    expect(
      paragraph.textSize.height,
      greaterThan(paragraph.preferredLineHeight * 1.5),
    );
    // ...and the control itself stays inside the 320 px column.
    final control = find.byKey(const Key('mobile-tasks-filter'));
    expect(tester.getRect(control).right, lessThanOrEqualTo(320));
    expect(
      tester.getSemantics(control),
      isSemantics(
        isButton: true,
        isSelected: false,
        label: 'Show unfinished only',
      ),
    );
    // Toggling from the wrapped label still filters and is announced.
    await tester.tap(label);
    await tester.pump();
    expect(find.text('Review changes'), findsNothing);
    expect(
      tester.getSemantics(control),
      isSemantics(isButton: true, isSelected: true),
    );
    expect(tester.takeException(), isNull);
    handle.dispose();
  });

  testWidgets('priority caption and accessible progress render', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    final view = MobileTaskView.fromTodos(prioritised)!;
    await tester.pumpWidget(host(MobileTaskList(view: view)));
    expect(find.text('In progress · High priority'), findsOneWidget);
    expect(find.text('Completed · Low priority'), findsOneWidget);
    expect(find.text('Cancelled · Medium priority'), findsOneWidget);
    expect(find.text('Pending'), findsOneWidget);
    expect(find.text('1 of 3 done'), findsOneWidget);
    final bar = tester.widget<LinearProgressIndicator>(
      find.byKey(const Key('mobile-tasks-progress-bar')),
    );
    expect(bar.value, closeTo(1 / 3, 0.001));
    expect(
      tester.getSemantics(find.byKey(const Key('mobile-tasks-progress-bar'))),
      isSemantics(label: '1 of 3 done', value: '33%'),
    );
    // The filter never changes the reported progress.
    await tester.tap(find.text('Show unfinished only'));
    await tester.pump();
    expect(find.text('1 of 3 done'), findsOneWidget);
    handle.dispose();
  });

  testWidgets('progress is hidden when nothing is tracked', (tester) async {
    final view = MobileTaskView.fromTodos([
      {'content': 'A', 'status': 'cancelled'},
      {'content': 'B', 'status': 'cancelled'},
    ])!;
    await tester.pumpWidget(host(MobileTaskList(view: view)));
    expect(find.byKey(const Key('mobile-tasks-progress')), findsNothing);
    expect(find.byKey(const Key('mobile-tasks-progress-bar')), findsNothing);
    expect(find.text('Cancelled'), findsNWidgets(2));
    expect(tester.takeException(), isNull);
  });

  testWidgets('copy sends the full parsed list even while filtered', (
    tester,
  ) async {
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copied = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    final view = MobileTaskView.fromTodos(prioritised)!;
    await tester.pumpWidget(host(MobileTaskList(view: view)));
    await tester.tap(find.text('Show unfinished only'));
    await tester.pump();
    expect(find.text('Review changes'), findsNothing);
    await tester.tap(find.byKey(const Key('mobile-tasks-copy-all')));
    await tester.pump();
    await tester.pump();
    expect(copied, view.toPlainText());
    expect(copied, contains('Review changes'));
    expect(find.text('All tasks copied'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('clipboard failure is reported in place', (tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          throw PlatformException(code: 'denied');
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    final view = MobileTaskView.fromTodos(rows)!;
    await tester.pumpWidget(host(MobileTaskList(view: view)));
    await tester.tap(find.byKey(const Key('mobile-tasks-copy-all')));
    await tester.pump();
    await tester.pump();
    expect(find.text('Could not copy the task list.'), findsOneWidget);
    expect(find.text('All tasks copied'), findsNothing);
    expect(tester.takeException(), isNull);
    // The button is usable again after the failure.
    final button = tester.widget<TextButton>(
      find.byKey(const Key('mobile-tasks-copy-all')),
    );
    expect(button.onPressed, isNotNull);
  });

  testWidgets(
    'plain text remains inert with RTL, large type and narrow width',
    (tester) async {
      await tester.pumpWidget(
        host(
          Directionality(
            textDirection: TextDirection.rtl,
            child: MobileTaskList(
              view: MobileTaskView.fromTodos([
                {
                  'content':
                      '[Open](https://example.test) <script>no()</script>',
                  'status': 'pending',
                  'priority': 'high',
                },
                {'content': 'Done', 'status': 'completed'},
              ])!,
            ),
          ),
          width: 320,
          textScale: 2,
        ),
      );
      expect(
        find.text('[Open](https://example.test) <script>no()</script>'),
        findsOneWidget,
      );
      expect(find.text('Pending · High priority'), findsOneWidget);
      expect(find.text('1 of 2 done'), findsOneWidget);
      // Only the local copy button is tappable; task text never becomes a link.
      expect(find.bySubtype<TextButton>(), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
