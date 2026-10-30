// Behaviour tests for KitRowParts v2 (docs/ux-system/kit-api/KitRowParts.md,
// "Tests required"): KitRowMenu on showKitMenu, KitSwitchRow risk and
// locked, and a controlled KitExpandRow.
import 'package:flutter/material.dart';
import 'dart:ui' show Tristate;

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/effects.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_row_parts.dart';
import 'package:opencode_mobile/ui/kit/kit_status_line.dart';
import 'package:opencode_mobile/ui/kit/kit_status_slot.dart';
import '../support/part_semantics.dart';

Widget _app(Widget child, {double textScale = 1, double width = 412}) =>
    MaterialApp(
      theme: AppTheme.light(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, inner) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          disableAnimations: true,
          textScaler: TextScaler.linear(textScale),
        ),
        child: inner!,
      ),
      home: Scaffold(
        body: Center(
          child: SizedBox(
            width: width,
            child: SingleChildScrollView(child: child),
          ),
        ),
      ),
    );

List<KitMenuItem> _items(List<String> log) => [
  KitMenuItem(
    label: 'Delete',
    destructive: true,
    onSelected: () => log.add('delete'),
  ),
  KitMenuItem(label: 'Rename', onSelected: () => log.add('rename')),
];

KitStatus? _contributed(WidgetTester tester) => tester
    .widget<KitStatusContribution>(find.byType(KitStatusContribution))
    .status;

void main() {
  group('KitRowMenu', () {
    testWidgets('a tap opens the menu, destructive items last', (tester) async {
      final log = <String>[];
      await tester.pumpWidget(
        _app(KitRowMenu(items: _items(log), menuLabel: 'Row actions')),
      );
      expect(find.byTooltip('More'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('kit-row-menu-button')));
      await tester.pumpAndSettle();
      final rename = tester.getTopLeft(find.text('Rename'));
      final delete = tester.getTopLeft(find.text('Delete'));
      expect(rename.dy, lessThan(delete.dy));
      await tester.tap(find.text('Rename'));
      await tester.pumpAndSettle();
      expect(log, ['rename']);
    });

    testWidgets('disabled or empty renders no button', (tester) async {
      await tester.pumpWidget(
        _app(
          Column(
            children: [
              KitRowMenu(items: _items([]), enabled: false),
              const KitRowMenu(items: []),
            ],
          ),
        ),
      );
      expect(find.byKey(const ValueKey('kit-row-menu-button')), findsNothing);
    });

    testWidgets('Enter on the focused button opens the menu', (tester) async {
      await tester.pumpWidget(_app(KitRowMenu(items: _items([]))));
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(find.text('Rename'), findsOneWidget);
    });

    testWidgets('show opens the same list at a position', (tester) async {
      late BuildContext context;
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (c) {
              context = c;
              return const SizedBox(height: 10);
            },
          ),
        ),
      );
      KitRowMenu.show(context, _items([]), position: const Offset(40, 40));
      await tester.pumpAndSettle();
      expect(find.text('Delete'), findsOneWidget);
      expect(find.text('Rename'), findsOneWidget);
    });
  });

  group('KitSwitchRow', () {
    testWidgets('a tap toggles; Space toggles when focused', (tester) async {
      final values = <bool>[];
      await tester.pumpWidget(
        _app(
          KitSwitchRow(title: 'Sounds', value: false, onChanged: values.add),
        ),
      );
      await tester.tap(find.text('Sounds'));
      expect(values, [true]);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pumpAndSettle();
      expect(values.length, greaterThan(1));
    });

    // Slice S3 of the style cleanup: away from Solid's defaults the switch
    // is drawn from the style resolver, and works the same.
    group('under a style', () {
      Widget styled(Widget child) => KitEffectsScope(
        effects: const KitEffects(surfaceStyle: KitSurfaceStyle.brutal),
        child: child,
      );

      testWidgets('Solid keeps the platform switch', (tester) async {
        await tester.pumpWidget(
          _app(KitSwitchRow(title: 'Sounds', value: false, onChanged: (_) {})),
        );
        expect(find.byType(Switch), findsOneWidget);
      });

      // Style recheck F8: no Fine-tune knob names the switch, so Solid
      // keeps the platform switch under every one.
      for (final (name, fineTune) in const [
        ('Corners › Sharp', KitFineTune(corners: KitCorners.sharp)),
        ('Density › Compact', KitFineTune(density: KitDensity.compact)),
        ('Depth › Raised', KitFineTune(depth: KitDepth.raised)),
        ('Edges › Off', KitFineTune(edges: KitEdges.none)),
      ]) {
        testWidgets('Solid with $name keeps the platform switch', (
          tester,
        ) async {
          await tester.pumpWidget(
            _app(
              KitEffectsScope(
                effects: KitEffects(fineTune: fineTune),
                child: KitSwitchRow(
                  title: 'Sounds',
                  value: true,
                  onChanged: (_) {},
                ),
              ),
            ),
          );
          expect(find.byType(Switch), findsOneWidget);
        });
      }

      testWidgets('drawn by the style; a tap and Space toggle; toggled '
          'semantics', (tester) async {
        final semantics = tester.ensureSemantics();
        final values = <bool>[];
        await tester.pumpWidget(
          _app(
            styled(
              KitSwitchRow(
                title: 'Sounds',
                value: false,
                switchKey: const ValueKey('sounds'),
                onChanged: values.add,
              ),
            ),
          ),
        );
        expect(find.byType(Switch), findsNothing);
        expect(find.byKey(const ValueKey('sounds')), findsOneWidget);
        final data = partSemantics(
          tester,
          find.byType(KitSwitchRow),
        ).getSemanticsData();
        expect(data.flagsCollection.isToggled, Tristate.isFalse);
        await tester.tap(find.byKey(const ValueKey('sounds')));
        expect(values, [true]);
        await tester.tap(find.text('Sounds'));
        expect(values, [true, true]);
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pumpAndSettle();
        await tester.sendKeyEvent(LogicalKeyboardKey.space);
        await tester.pumpAndSettle();
        expect(values.length, greaterThan(2));
        semantics.dispose();
      });

      testWidgets('a row that opens: only the switch toggles', (tester) async {
        final log = <String>[];
        await tester.pumpWidget(
          _app(
            styled(
              KitSwitchRow(
                title: 'Morning summary',
                value: true,
                switchKey: const ValueKey('summary'),
                onChanged: (v) => log.add('changed:$v'),
                onOpen: () => log.add('open'),
                openLabel: 'Edit schedule',
              ),
            ),
          ),
        );
        await tester.tap(find.text('Morning summary'));
        await tester.tap(find.byKey(const ValueKey('summary')));
        expect(log, ['open', 'changed:false']);
      });

      testWidgets('disabled: no toggle, not enabled', (tester) async {
        final semantics = tester.ensureSemantics();
        await tester.pumpWidget(
          _app(
            styled(
              const KitSwitchRow(
                title: 'Sounds',
                value: true,
                switchKey: ValueKey('sounds'),
                onChanged: null,
                disabledReason: 'Turn on notifications first',
              ),
            ),
          ),
        );
        await tester.tap(find.byKey(const ValueKey('sounds')));
        final data = partSemantics(
          tester,
          find.byType(KitSwitchRow),
        ).getSemanticsData();
        expect(data.flagsCollection.isEnabled, Tristate.isFalse);
        expect(data.flagsCollection.isToggled, Tristate.isTrue);
        semantics.dispose();
      });
    });

    testWidgets('disabled shows its reason as text and hint, undimmed', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        _app(
          const KitSwitchRow(
            title: 'Sounds',
            value: false,
            onChanged: null,
            disabledReason: 'Turn on notifications first',
          ),
        ),
      );
      expect(find.text('Turn on notifications first'), findsOneWidget);
      final data = partSemantics(
        tester,
        find.byType(KitSwitchRow),
      ).getSemanticsData();
      expect(data.hint, 'Turn on notifications first');
      expect(data.flagsCollection.isEnabled, Tristate.isFalse);
      final dimmed = tester
          .widgetList<Opacity>(
            find.descendant(
              of: find.byType(KitSwitchRow),
              matching: find.byType(Opacity),
            ),
          )
          .where((o) => o.opacity < 1);
      expect(dimmed, isEmpty);
      semantics.dispose();
    });

    group('risk', () {
      Widget risky({
        required bool value,
        required List<String> log,
        List<KitUntil> until = const [
          KitUntil.off,
          KitUntil.conversation,
          KitUntil.hour,
        ],
        KitUntil? current,
      }) => KitSwitchRow(
        title: 'Approve everything',
        value: value,
        onChanged: (v) => log.add('changed:$v'),
        risk: KitRisk(
          scope: 'Every conversation on this server',
          onLabel: 'Auto-approve is on',
          icon: AppIconography.more,
          until: until,
          current: current,
          onUntil: (u) => log.add('until:${u.name}'),
        ),
      );

      testWidgets('off to on unfolds the step without calling onChanged', (
        tester,
      ) async {
        final log = <String>[];
        await tester.pumpWidget(_app(risky(value: false, log: log)));
        await tester.tap(find.text('Approve everything'));
        await tester.pump();
        expect(
          find.byKey(const ValueKey('kit-switch-risk-step')),
          findsOneWidget,
        );
        expect(find.text('Every conversation on this server'), findsOneWidget);
        expect(log, isEmpty);
        await tester.tap(find.text('For an hour'));
        await tester.pump();
        expect(log, ['until:hour', 'changed:true']);
        expect(
          find.byKey(const ValueKey('kit-switch-risk-step')),
          findsNothing,
        );
      });

      testWidgets('Not now folds back and calls neither', (tester) async {
        final log = <String>[];
        await tester.pumpWidget(_app(risky(value: false, log: log)));
        await tester.tap(find.byType(Switch));
        await tester.pump();
        await tester.tap(find.text('Not now'));
        await tester.pump();
        expect(
          find.byKey(const ValueKey('kit-switch-risk-step')),
          findsNothing,
        );
        expect(log, isEmpty);
      });

      testWidgets('one option offers Turn on', (tester) async {
        final log = <String>[];
        await tester.pumpWidget(
          _app(risky(value: false, log: log, until: const [KitUntil.off])),
        );
        await tester.tap(find.text('Approve everything'));
        await tester.pump();
        await tester.tap(find.text('Turn on'));
        await tester.pump();
        expect(log, ['until:off', 'changed:true']);
      });

      testWidgets('while on: status condition with Turn off; on to off acts '
          'at once', (tester) async {
        final log = <String>[];
        await tester.pumpWidget(
          _app(risky(value: true, log: log, current: KitUntil.hour)),
        );
        expect(
          find.text('Every conversation on this server · For an hour'),
          findsOneWidget,
        );
        final status = _contributed(tester)!;
        expect(status.kind, KitStatusKind.riskySwitch);
        expect(status.message, 'Auto-approve is on');
        expect(status.onDismiss, isNull);
        expect(status.action!.label, 'Turn off');
        status.action!.onPressed!();
        expect(log, ['changed:false']);

        log.clear();
        await tester.tap(find.text('Approve everything'));
        await tester.pump();
        expect(log, ['changed:false']);
        expect(
          find.byKey(const ValueKey('kit-switch-risk-step')),
          findsNothing,
        );

        await tester.pumpWidget(_app(risky(value: false, log: log)));
        expect(_contributed(tester), isNull);
      });

      testWidgets('the step lays out at text 2.0 and 320 dp', (tester) async {
        await tester.pumpWidget(
          _app(risky(value: false, log: []), textScale: 2, width: 320),
        );
        await tester.tap(find.text('Approve everything'));
        await tester.pump();
        expect(tester.takeException(), isNull);
        expect(find.text('For this conversation'), findsOneWidget);
      });
    });

    testWidgets('locked: no switch, the word, no toggle', (tester) async {
      final semantics = tester.ensureSemantics();
      var calls = 0;
      await tester.pumpWidget(
        _app(
          KitSwitchRow(
            title: 'Core tools',
            value: true,
            onChanged: (_) => calls++,
            locked: 'Always included',
          ),
        ),
      );
      expect(find.byType(Switch), findsNothing);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('kit-switch-locked')),
          matching: find.text('Always included'),
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('Core tools'));
      expect(calls, 0);
      final data = partSemantics(
        tester,
        find.byType(KitSwitchRow),
      ).getSemanticsData();
      expect(data.flagsCollection.isToggled, Tristate.isTrue);
      expect(data.flagsCollection.isEnabled, Tristate.isFalse);
      // Said once, in the label: not again as a hint.
      expect(data.hint, isEmpty);
      expect('Always included'.allMatches(data.label).length, 1);
      semantics.dispose();
    });

    test('locked with value false asserts', () {
      expect(
        () => KitSwitchRow(
          title: 't',
          value: false,
          onChanged: null,
          locked: 'Always',
        ),
        throwsAssertionError,
      );
    });
  });

  group('KitExpandRow', () {
    testWidgets('controlled: a tap reports without unfolding', (tester) async {
      final reported = <bool>[];
      Widget row(bool expanded) => KitExpandRow(
        title: 'Built-in',
        expanded: expanded,
        onExpansionChanged: reported.add,
        children: const [Text('child')],
      );
      await tester.pumpWidget(_app(row(false)));
      await tester.tap(find.text('Built-in'));
      await tester.pump();
      expect(reported, [true]);
      expect(find.text('child'), findsNothing);
      await tester.pumpWidget(_app(row(true)));
      await tester.pump();
      expect(find.text('child'), findsOneWidget);
    });

    testWidgets('uncontrolled toggles and reports; Enter and Space fold', (
      tester,
    ) async {
      final reported = <bool>[];
      await tester.pumpWidget(
        _app(
          KitExpandRow(
            title: 'Built-in',
            onExpansionChanged: reported.add,
            children: const [Text('child')],
          ),
        ),
      );
      await tester.tap(find.text('Built-in'));
      await tester.pump();
      expect(find.text('child'), findsOneWidget);
      expect(reported, [true]);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(find.text('child'), findsNothing);
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pump();
      expect(find.text('child'), findsOneWidget);
      expect(reported, [true, false, true]);
    });

    test('expanded with initiallyExpanded asserts', () {
      expect(
        () => KitExpandRow(
          title: 't',
          expanded: true,
          initiallyExpanded: true,
          children: const [],
        ),
        throwsAssertionError,
      );
    });

    testWidgets('maintainState keeps a folded child\'s State', (tester) async {
      final field = GlobalKey<_CounterState>();
      await tester.pumpWidget(
        _app(
          KitExpandRow(
            title: 'Form',
            initiallyExpanded: true,
            maintainState: true,
            children: [_Counter(key: field)],
          ),
        ),
      );
      field.currentState!.count = 7;
      await tester.tap(find.text('Form'));
      await tester.pump();
      expect(find.text('count'), findsNothing);
      expect(field.currentState?.count, 7);
    });
  });
}

class _Counter extends StatefulWidget {
  const _Counter({super.key});

  @override
  State<_Counter> createState() => _CounterState();
}

class _CounterState extends State<_Counter> {
  int count = 0;

  @override
  Widget build(BuildContext context) => const Text('count');
}
