import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/retained_tab_view.dart';

class _TabProbe extends StatefulWidget {
  const _TabProbe({super.key, required this.label, required this.focus});

  final String label;
  final FocusNode focus;

  @override
  State<_TabProbe> createState() => _TabProbeState();
}

class _TabProbeState extends State<_TabProbe>
    with SingleTickerProviderStateMixin {
  final text = TextEditingController();
  late final AnimationController activity = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 1),
  )..addListener(() => ticks++);
  var ticks = 0;

  @override
  void initState() {
    super.initState();
    activity.repeat();
  }

  @override
  void dispose() {
    text.dispose();
    activity.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(widget.label),
      TextField(controller: text, focusNode: widget.focus),
    ],
  );
}

void main() {
  testWidgets(
    'retains drafts and state while excluding inactive focus and tickers',
    (tester) async {
      final selected = ValueNotifier(0);
      final firstFocus = FocusNode();
      final secondFocus = FocusNode();
      final first = GlobalKey<_TabProbeState>();
      final second = GlobalKey<_TabProbeState>();
      addTearDown(selected.dispose);
      addTearDown(firstFocus.dispose);
      addTearDown(secondFocus.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ValueListenableBuilder<int>(
              valueListenable: selected,
              builder: (context, index, _) => RetainedTabView(
                index: index,
                children: [
                  _TabProbe(
                    key: first,
                    label: 'First tab content',
                    focus: firstFocus,
                  ),
                  _TabProbe(
                    key: second,
                    label: 'Second tab content',
                    focus: secondFocus,
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      final original = first.currentState!;
      await tester.enterText(find.byType(TextField).first, 'Keep this draft');
      await tester.pump(const Duration(milliseconds: 30));
      expect(firstFocus.hasFocus, isTrue);
      final hiddenTicks = second.currentState!.ticks;
      await tester.pump(const Duration(milliseconds: 30));
      expect(second.currentState!.ticks, hiddenTicks);

      selected.value = 1;
      await tester.pump();
      expect(firstFocus.hasFocus, isFalse);
      firstFocus.requestFocus();
      await tester.pump();
      expect(firstFocus.hasFocus, isFalse);
      final stoppedTicks = original.ticks;
      await tester.pump(const Duration(milliseconds: 200));
      expect(original.ticks, stoppedTicks);
      expect(find.text('First tab content'), findsNothing);
      expect(find.text('Second tab content'), findsOneWidget);
      secondFocus.requestFocus();
      await tester.pump();
      expect(secondFocus.hasFocus, isTrue);

      selected.value = 0;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(first.currentState, same(original));
      expect(original.text.text, 'Keep this draft');
      expect(secondFocus.hasFocus, isFalse);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'rapid selection retargets in place and exposes only selected semantics',
    (tester) async {
      final semantics = tester.ensureSemantics();
      final selected = ValueNotifier(0);
      addTearDown(selected.dispose);
      var taps = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ValueListenableBuilder<int>(
              valueListenable: selected,
              builder: (context, index, _) => RetainedTabView(
                index: index,
                children: [
                  TextButton(
                    onPressed: () => taps++,
                    child: const Text('First action'),
                  ),
                  TextButton(
                    onPressed: () => taps += 10,
                    child: const Text('Second action'),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      selected.value = 1;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      expect(
        tester.semantics.simulatedAccessibilityTraversal().map(
          (node) => node.label,
        ),
        isNot(contains('First action')),
      );
      expect(
        tester.semantics.simulatedAccessibilityTraversal().map(
          (node) => node.label,
        ),
        contains('Second action'),
      );
      await tester.tap(find.text('Second action'));
      expect(taps, 10);
      final beforeReverse = tester
          .widget<Opacity>(
            find
                .ancestor(
                  of: find.text('First action'),
                  matching: find.byType(Opacity),
                )
                .first,
          )
          .opacity;
      selected.value = 0;
      await tester.pump();
      final afterReverse = tester
          .widget<Opacity>(
            find
                .ancestor(
                  of: find.text('First action'),
                  matching: find.byType(Opacity),
                )
                .first,
          )
          .opacity;
      expect(afterReverse, closeTo(beforeReverse, .001));
      expect(
        tester.semantics.simulatedAccessibilityTraversal().map(
          (node) => node.label,
        ),
        isNot(contains('Second action')),
      );
      expect(
        tester.semantics.simulatedAccessibilityTraversal().map(
          (node) => node.label,
        ),
        contains('First action'),
      );
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.text('Second action'), findsNothing);
      expect(tester.takeException(), isNull);
      semantics.dispose();
    },
  );

  testWidgets('reduced effects show only the requested tab in the next frame', (
    tester,
  ) async {
    final selected = ValueNotifier(0);
    addTearDown(selected.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ValueListenableBuilder<int>(
            valueListenable: selected,
            builder: (context, index, _) => RetainedTabView(
              index: index,
              reduceMotion: true,
              children: const [Text('First'), Text('Second')],
            ),
          ),
        ),
      ),
    );
    selected.value = 1;
    await tester.pump();
    expect(find.text('First'), findsNothing);
    expect(find.text('Second'), findsOneWidget);
    await tester.pump();
    expect(tester.binding.transientCallbackCount, 0);
  });
}
