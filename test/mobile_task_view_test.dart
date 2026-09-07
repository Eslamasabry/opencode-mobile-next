import 'package:flutter/material.dart';
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
  test('fallback preserves unknown status as plain bounded text', () {
    expect(
      MobileTaskView.fallback([
        {'content': 'Unknown outcome', 'status': 'success'},
      ]),
      '[success] Unknown outcome',
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
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: SingleChildScrollView(child: MobileTaskList(view: view)),
        ),
      ),
    );
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
  testWidgets('plain text remains inert with RTL and large type', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(2)),
          child: Directionality(
            textDirection: TextDirection.rtl,
            child: child!,
          ),
        ),
        home: Scaffold(
          body: SingleChildScrollView(
            child: MobileTaskList(
              view: MobileTaskView.fromTodos([
                {
                  'content':
                      '[Open](https://example.test) <script>no()</script>',
                  'status': 'pending',
                },
              ])!,
            ),
          ),
        ),
      ),
    );
    expect(
      find.text('[Open](https://example.test) <script>no()</script>'),
      findsOneWidget,
    );
    expect(find.byType(TextButton), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
