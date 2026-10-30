// The opened step (owner 2026-10-14): a command is one box (the whole command
// once after `$`, the first lines of output, "Show all N lines", a copy icon);
// an inline edit diff has no file header and no fill of its own, and its
// "Wrap lines" choice is on the step's long-press menu. Reduced motion.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_step_timeline.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/kit/kit_diff_view.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';

Future<void> _pump(WidgetTester tester, Widget child) async {
  tester.view.physicalSize = const Size(412, 915) * 2;
  tester.view.devicePixelRatio = 2;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      locale: const Locale('en'),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: true),
        child: widget!,
      ),
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: child,
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

KitStepTimeline _timeline(List<Widget> steps) => KitStepTimeline(
  label: 'Ran a command',
  icon: AppIconography.terminal,
  expanded: true,
  onPressed: () {},
  steps: steps,
);

const _command = 'find / -xdev ( -iname "*ui-ux*" ) -type f 2>/dev/null | head';

KitToolRow _open({String output = '', ValueChanged<String>? onOpenFull}) =>
    KitToolRow(
      kind: KitToolKind.shell,
      title: 'Shell',
      path: _command,
      pathCut: KitMonoCut.end,
      status: KitToolStatus.done,
      expanded: true,
      onExpansionChanged: (_) {},
      command: KitStepCommand(
        command: _command,
        output: output,
        onOpenFull: onOpenFull,
      ),
    );

void main() {
  testWidgets('an open command step shows the command once, after the prompt', (
    tester,
  ) async {
    await _pump(tester, _timeline([_open(output: '/a\n/b')]));
    expect(find.byKey(const ValueKey('kit-step-command')), findsOneWidget);
    // The box holds the whole command once; the line above is the command too,
    // without the prompt, and there is no third copy.
    expect(find.text('\$ $_command'), findsOneWidget);
    expect(find.text(_command), findsOneWidget);
    expect(find.text('Shell'), findsNothing);
    expect(find.text('/a'), findsOneWidget);
    expect(find.text('/b'), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-step-show-all')), findsNothing);
  });

  testWidgets('the box wraps a long command instead of cutting it', (
    tester,
  ) async {
    final long = List.filled(12, 'a-long-argument').join(' ');
    await _pump(
      tester,
      _timeline([
        KitToolRow(
          kind: KitToolKind.shell,
          title: 'Shell',
          path: long,
          pathCut: KitMonoCut.end,
          status: KitToolStatus.done,
          expanded: true,
          onExpansionChanged: (_) {},
          command: KitStepCommand(command: long),
        ),
      ]),
    );
    final box = find.byKey(const ValueKey('kit-step-command-text'));
    expect(find.text('\$ $long'), findsOneWidget);
    // Three lines or more: it wrapped.
    expect(tester.getSize(box).height, greaterThan(40));
  });

  testWidgets('output past 6 lines says Show all N lines and opens in place', (
    tester,
  ) async {
    final output = List.generate(20, (i) => 'row ${i + 1}').join('\n');
    await _pump(tester, _timeline([_open(output: output)]));
    expect(find.text('row 6'), findsOneWidget);
    expect(find.text('row 7'), findsNothing);
    expect(find.text('Show all 20 lines'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('kit-step-show-all')));
    await tester.pumpAndSettle();
    expect(find.text('row 20'), findsOneWidget);
    expect(find.text('Show all 20 lines'), findsNothing);
  });

  testWidgets('output past the in-place cap opens elsewhere', (tester) async {
    final output = List.generate(300, (i) => 'row ${i + 1}').join('\n');
    String? full;
    await _pump(
      tester,
      _timeline([_open(output: output, onOpenFull: (text) => full = text)]),
    );
    await tester.tap(find.byKey(const ValueKey('kit-step-show-all')));
    await tester.pumpAndSettle();
    expect(full, output);
    expect(find.text('row 7'), findsNothing);
  });

  testWidgets('the box has a copy icon in its top-right corner', (
    tester,
  ) async {
    await _pump(tester, _timeline([_open(output: '/a')]));
    final box = tester.getRect(find.byKey(const ValueKey('kit-step-command')));
    final copy = tester.getRect(
      find.byKey(const ValueKey('kit-step-command-copy')),
    );
    expect(copy.right, closeTo(box.right, 1));
    expect(copy.top, closeTo(box.top, 1));
  });

  testWidgets('the box sits at the text x, under the line', (tester) async {
    await _pump(tester, _timeline([_open(output: '/a')]));
    final line = tester.getTopLeft(find.text(_command)).dx;
    final box = tester.getTopLeft(
      find.byKey(const ValueKey('kit-step-command')),
    );
    expect(box.dx, closeTo(line, .01));
  });

  // A band of the diff paints its fill as a ColoredBox or a decorated box.
  bool paintsFill(Widget w) =>
      w is ColoredBox || (w is DecoratedBox && w.decoration is BoxDecoration);
  Color? fillOf(Widget w) => w is ColoredBox
      ? w.color
      : ((w as DecoratedBox).decoration as BoxDecoration).color;

  group('an inline diff', () {
    KitDiffFile file([String path = 'test/checkout_test.dart']) {
      final before = [for (var i = 1; i <= 60; i++) 'line $i'];
      final after = [...before]..[30] = 'changed line 31';
      return KitDiffFile.fromTexts(
        path,
        before: before.join('\n'),
        after: after.join('\n'),
      );
    }

    testWidgets('has no file header and no fill on the fold row or the lines', (
      tester,
    ) async {
      final a = file();
      await _pump(tester, KitDiffView(files: [a], inline: true, maxLines: 12));
      final roles = AppTheme.rolesOf(
        Theme.of(tester.element(find.byType(KitDiffView))),
      );
      expect(
        find.byKey(ValueKey('kit-diff-file-header-${a.path}')),
        findsNothing,
      );
      expect(find.text('c_test.dart'), findsNothing);
      final fold = find.byKey(const ValueKey('kit-diff-gap-0'));
      expect(fold, findsOneWidget);
      final bar = fillOf(
        tester.widget(
          find
              .descendant(
                of: fold,
                matching: find.byWidgetPredicate(paintsFill),
              )
              .first,
        ),
      );
      expect(bar, Colors.transparent);
      expect(bar, isNot(roles.surface1));
      expect(bar, isNot(roles.surface2));
      // Nothing in the view paints the card fill.
      final fills = tester
          .widgetList(
            find.descendant(
              of: find.byType(KitDiffView),
              matching: find.byWidgetPredicate(paintsFill),
            ),
          )
          .map(fillOf);
      expect(fills, isNot(contains(roles.surface1)));
      expect(fills, isNot(contains(roles.surface2)));
    });

    testWidgets('several files keep a header so each one is named', (
      tester,
    ) async {
      final a = file('lib/a.dart');
      final b = file('lib/b.dart');
      await _pump(tester, KitDiffView(files: [a, b], inline: true));
      expect(
        find.byKey(ValueKey('kit-diff-file-header-${a.path}')),
        findsOneWidget,
      );
    });

    testWidgets('the same diff not inline keeps its header and its fills', (
      tester,
    ) async {
      final a = file();
      await _pump(tester, KitDiffView(files: [a], maxLines: 12));
      expect(
        find.byKey(ValueKey('kit-diff-file-header-${a.path}')),
        findsOneWidget,
      );
      final roles = AppTheme.rolesOf(
        Theme.of(tester.element(find.byType(KitDiffView))),
      );
      final bar = tester.widget(
        find
            .descendant(
              of: find.byKey(const ValueKey('kit-diff-gap-0')),
              matching: find.byWidgetPredicate(paintsFill),
            )
            .first,
      );
      expect(fillOf(bar), roles.surface2);
    });
  });

  group('an edit in the conversation', () {
    ToolState edit() => ToolState.fromJson(const {
      'status': 'completed',
      'input': {
        'filePath': '/workspace/test/c_test.dart',
        'oldString': 'await tester.pump();',
        'newString': 'await tester.pumpUntil(done);',
      },
      'output': 'Edit applied successfully.',
      'metadata': {
        'filediff': {
          'patch':
              '@@ -40,3 +40,3 @@\n context\n-await tester.pump();\n'
              '+await tester.pumpUntil(done);\n context',
          'additions': 1,
          'deletions': 1,
        },
      },
    }, toolName: 'edit');

    testWidgets('opens to the diff with no file header, one line above it', (
      tester,
    ) async {
      await _pump(
        tester,
        _timeline([ToolCard(toolName: 'edit', state: edit(), embedded: true)]),
      );
      await tester.tap(find.byKey(const Key('embedded-tool-row')));
      await tester.pumpAndSettle();
      // The step line names the file and the counts, once.
      expect(find.text('c_test.dart'), findsOneWidget);
      expect(
        find.byKey(
          const ValueKey('tool-diff-file-header-/workspace/test/c_test.dart'),
        ),
        findsNothing,
      );
      expect(find.textContaining('await tester.pumpUntil'), findsWidgets);
    });

    testWidgets('Wrap lines is on the step line\'s long-press menu', (
      tester,
    ) async {
      await _pump(
        tester,
        _timeline([ToolCard(toolName: 'edit', state: edit(), embedded: true)]),
      );
      await tester.tap(find.byKey(const Key('embedded-tool-row')));
      await tester.pumpAndSettle();
      // No wrap control on the line or in the diff.
      expect(find.byTooltip('Wrap lines'), findsNothing);
      await tester.longPress(find.byKey(const Key('embedded-tool-row')));
      await tester.pumpAndSettle();
      expect(find.text('Wrap lines'), findsOneWidget);
    });
  });
}
