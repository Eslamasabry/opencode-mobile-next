// Behaviour tests for KitTabSwitcher v2 and KitTabStrip
// (docs/ux-system/kit-api/KitTabSwitcher.md "Tests required").
import 'kit_motion_still.dart';

import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/kit/motion/kit_tab_switcher.dart';

Widget _host(
  Widget child, {
  TextDirection direction = TextDirection.ltr,
  double textScale = 1,
  bool reduced = false,
}) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, app) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      textScaler: TextScaler.linear(textScale),
      disableAnimations: reduced,
    ),
    child: app!,
  ),
  home: Scaffold(
    body: Directionality(textDirection: direction, child: child),
  ),
);

const _words = [
  'Backlog',
  'Ready',
  'Working',
  'Review',
  'Done',
  'Archive',
  'Blocked',
  'Later',
];

List<KitTab> _tabs(int n, {List<int?>? counts, int needsYouAt = -1}) => [
  for (var i = 0; i < n; i++)
    KitTab(
      label: _words[i],
      count: counts?[i],
      needsYou: i == needsYouAt ? 1 : 0,
      key: ValueKey('tab-$i'),
      countKey: ValueKey('count-$i'),
      needsYouKey: ValueKey('needs-you-$i'),
    ),
];

Widget _strip({
  int n = 5,
  int selected = 2,
  List<int?>? counts,
  int needsYouAt = -1,
  ValueChanged<int>? onSelected,
}) => Align(
  alignment: AlignmentDirectional.topStart,
  child: KitTabStrip(
    tabs: _tabs(n, counts: counts, needsYouAt: needsYouAt),
    selected: selected,
    onSelected: onSelected ?? (_) {},
    semanticsLabel: 'Columns',
    stripKey: const ValueKey('strip'),
  ),
);

class _Page extends StatefulWidget {
  const _Page(this.name);

  final String name;

  @override
  State<_Page> createState() => _PageState();
}

class _PageState extends State<_Page> {
  int taps = 0;

  @override
  Widget build(BuildContext context) => Center(
    child: TextButton(
      onPressed: () => setState(() => taps++),
      child: Text('${widget.name} $taps'),
    ),
  );
}

void main() {
  kitMotionStillTests(
    'KitTabStrip',
    builds: {'selected work': () => _strip()},
    changes: {
      'selection moves': KitMotionChange(
        build: () => _strip(selected: 0),
        act: (tester, stage) => stage.rebuild(_strip(selected: 3)),
        shows: 'Review',
      ),
    },
  );

  group('strip', () {
    testWidgets('another tab calls onSelected once; the selected one nothing', (
      tester,
    ) async {
      final calls = <int>[];
      await tester.pumpWidget(_host(_strip(onSelected: calls.add)));
      await tester.tap(find.text('Review'));
      await tester.pumpAndSettle();
      expect(calls, [3]);
      await tester.tap(find.text('Working'));
      await tester.pumpAndSettle();
      expect(calls, [3]);
    });

    testWidgets('each tab is at least 48 dp tall and wide', (tester) async {
      await tester.pumpWidget(_host(_strip()));
      for (var i = 0; i < 5; i++) {
        final size = tester.getSize(find.byKey(ValueKey('tab-$i')));
        expect(size.height, greaterThanOrEqualTo(48));
        expect(size.width, greaterThanOrEqualTo(48));
      }
    });
  });

  group('studio additions', () {
    testWidgets('accent fills the selected tab with the accent', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          Align(
            alignment: AlignmentDirectional.topStart,
            child: KitTabStrip(
              tabs: _tabs(3),
              selected: 1,
              accent: true,
              onSelected: (_) {},
            ),
          ),
        ),
      );
      final roles = KitTokens.of(
        tester.element(find.byKey(const ValueKey('tab-1'))),
      ).roles;
      final fill = tester.widget<AnimatedContainer>(
        find
            .descendant(
              of: find.byKey(const ValueKey('tab-1')),
              matching: find.byType(AnimatedContainer),
            )
            .first,
      );
      expect((fill.decoration! as ShapeDecoration).color, roles.accent);
    });

    testWidgets('a swipe asks for the next or previous destination, mirrored '
        'in right to left', (tester) async {
      final steps = <int>[];
      Widget body(TextDirection d) => _host(
        direction: d,
        SizedBox(
          height: 300,
          child: KitTabSwitcher(
            index: 1,
            onSwipe: steps.add,
            children: const [_Page('A'), _Page('B'), _Page('C')],
          ),
        ),
      );
      await tester.pumpWidget(body(TextDirection.ltr));
      await tester.fling(find.text('B 0'), const Offset(-300, 0), 1200);
      await tester.pumpAndSettle();
      expect(steps, [1]);
      await tester.pumpWidget(body(TextDirection.rtl));
      await tester.fling(find.text('B 0'), const Offset(-300, 0), 1200);
      await tester.pumpAndSettle();
      expect(steps, [1, -1]);
    });

    testWidgets('shrinkWrap takes the chosen destination height', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          ListView(
            children: [
              KitTabSwitcher(
                index: 0,
                shrinkWrap: true,
                children: const [SizedBox(height: 120), SizedBox(height: 300)],
              ),
            ],
          ),
        ),
      );
      expect(tester.getSize(find.byType(KitTabSwitcher)).height, 120);
    });
  });

  group('counts', () {
    testWidgets('null shows no number; 3 shows "3" and "Working, 3"', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        _host(_strip(counts: const [null, null, 3, null, null])),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('count-0')), findsNothing);
      expect(find.text('0'), findsNothing);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('count-2')),
          matching: find.text('3'),
        ),
        findsOneWidget,
      );
      final paragraph = tester.renderObject<RenderParagraph>(find.text('3'));
      expect(
        paragraph.text.style?.fontFeatures,
        contains(const FontFeature.tabularFigures()),
      );
      final data = tester
          .getSemantics(find.byKey(const ValueKey('tab-2')))
          .getSemanticsData();
      expect(data.label, 'Working, 3');
      expect(data.flagsCollection.isButton, isTrue);
      expect(data.flagsCollection.isSelected, Tristate.isTrue);
      final other = tester
          .getSemantics(find.byKey(const ValueKey('tab-0')))
          .getSemanticsData();
      expect(other.label, 'Backlog');
      expect(other.flagsCollection.isSelected, Tristate.isFalse);
      semantics.dispose();
    });
  });

  group('needs you', () {
    testWidgets('a badge by needsYouKey, the merged label, one attention '
        'colour', (tester) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        _host(_strip(counts: const [1, 2, 3, 4, 5], needsYouAt: 2)),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('needs-you-2')), findsOneWidget);
      expect(find.byKey(const ValueKey('needs-you-1')), findsNothing);
      final label = tester
          .getSemantics(find.byKey(const ValueKey('tab-2')))
          .getSemanticsData()
          .label;
      expect(label, startsWith('Working, 3'));
      expect(label, endsWith(', 1 need you'));

      // The only attention colour painted anywhere is the badge's fill.
      final roles = KitTokens.of(
        tester.element(find.byKey(const ValueKey('strip'))),
      ).roles;
      final attention = {
        roles.attention,
        roles.attentionFill,
        roles.attentionSurface,
        roles.attentionLine,
      };
      var painted = 0;
      for (final element
          in find
              .descendant(
                of: find.byKey(const ValueKey('strip')),
                matching: find.byWidgetPredicate((_) => true),
              )
              .evaluate()) {
        final render = element.renderObject;
        Color? color;
        if (render is RenderDecoratedBox) {
          final d = render.decoration;
          color = d is BoxDecoration
              ? d.color
              : d is ShapeDecoration
              ? d.color
              : null;
        } else if (render is RenderParagraph && element.widget is RichText) {
          color = render.text.style?.color;
        }
        if (color != null && attention.contains(color)) painted++;
      }
      expect(painted, 1);
      semantics.dispose();
    });
  });

  group('overflow', () {
    testWidgets('eight tabs at 320 dp scroll inside the strip; the last '
        'selected scrolls into view', (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      var selected = 0;
      late StateSetter set;
      await tester.pumpWidget(
        _host(
          StatefulBuilder(
            builder: (context, setState) {
              set = setState;
              return _strip(
                n: 8,
                selected: selected,
                counts: const [12, 3, 4, 5, 6, 7, 8, 120],
              );
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      final strip = tester.widget<SingleChildScrollView>(
        find.byKey(const ValueKey('strip')),
      );
      expect(strip.controller!.position.maxScrollExtent, greaterThan(0));
      expect(strip.controller!.offset, 0);
      set(() => selected = 7);
      await tester.pumpAndSettle();
      expect(strip.controller!.offset, greaterThan(0));
      final last = tester.getRect(find.byKey(const ValueKey('tab-7')));
      expect(last.right, lessThanOrEqualTo(320));
      expect(last.left, greaterThanOrEqualTo(0));
    });

    for (final width in const [320.0, 412.0, 915.0, 1280.0, 1600.0]) {
      for (final scale in const [1.0, 1.3, 2.0]) {
        for (final direction in TextDirection.values) {
          testWidgets(
            'no exception at $width dp, text $scale, ${direction.name}',
            (tester) async {
              tester.view.physicalSize = Size(width, width == 915 ? 412 : 800);
              tester.view.devicePixelRatio = 1;
              addTearDown(tester.view.reset);
              await tester.pumpWidget(
                _host(
                  KitTabSwitcher.tabs(
                    tabs: _tabs(
                      8,
                      counts: const [1, 22, 333, 4, 5, 6, 7, 8],
                      needsYouAt: 3,
                    ),
                    index: 7,
                    onSelected: (_) {},
                    children: [for (final w in _words) _Page(w)],
                  ),
                  direction: direction,
                  textScale: scale,
                ),
              );
              await tester.pumpAndSettle();
              expect(tester.takeException(), isNull);
            },
          );
        }
      }
    }
  });

  group('keyboard', () {
    Future<List<int>> pumpKeyboard(
      WidgetTester tester, {
      TextDirection direction = TextDirection.ltr,
    }) async {
      final calls = <int>[];
      await tester.pumpWidget(
        _host(
          Column(
            children: [
              TextButton(onPressed: () {}, child: const Text('Before')),
              _strip(onSelected: calls.add),
            ],
          ),
          direction: direction,
        ),
      );
      FocusManager.instance.highlightStrategy =
          FocusHighlightStrategy.alwaysTraditional;
      addTearDown(
        () => FocusManager.instance.highlightStrategy =
            FocusHighlightStrategy.automatic,
      );
      await tester.pumpAndSettle();
      return calls;
    }

    bool focused(WidgetTester tester, int i) {
      final focus = FocusManager.instance.primaryFocus;
      if (focus?.context == null) return false;
      return find
          .ancestor(
            of: find.byElementPredicate((e) => e == focus!.context),
            matching: find.byKey(ValueKey('tab-$i')),
          )
          .evaluate()
          .isNotEmpty;
    }

    testWidgets('Tab enters on the selected tab; Right moves focus; Enter '
        'selects; Tab leaves the strip', (tester) async {
      final calls = await pumpKeyboard(tester);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(focused(tester, 2), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump();
      expect(focused(tester, 3), isTrue);
      expect(calls, isEmpty);
      await tester.sendKeyEvent(LogicalKeyboardKey.end);
      await tester.pump();
      expect(focused(tester, 4), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.home);
      await tester.pump();
      expect(focused(tester, 0), isTrue);
      expect(calls, isEmpty);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(calls, [0]);
    });

    testWidgets('in RTL, Left moves forward', (tester) async {
      final calls = await pumpKeyboard(tester, direction: TextDirection.rtl);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(focused(tester, 2), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
      await tester.pump();
      expect(focused(tester, 3), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pump();
      expect(calls, [3]);
    });
  });

  group('body', () {
    testWidgets('switching shows no scale and keeps each destination\'s '
        'state', (tester) async {
      var index = 0;
      late StateSetter set;
      await tester.pumpWidget(
        _host(
          StatefulBuilder(
            builder: (context, setState) {
              set = setState;
              return KitTabSwitcher(
                index: index,
                children: const [_Page('One'), _Page('Two')],
              );
            },
          ),
        ),
      );
      await tester.tap(find.text('One 0'));
      await tester.pump();
      expect(find.text('One 1'), findsOneWidget);
      set(() => index = 1);
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 40));
        final inBody = find.byType(KitTabSwitcher);
        expect(
          find.descendant(of: inBody, matching: find.byType(ScaleTransition)),
          findsNothing,
        );
        for (final t in tester.widgetList<Transform>(
          find.descendant(of: inBody, matching: find.byType(Transform)),
        )) {
          final m = t.transform;
          expect(m.getMaxScaleOnAxis(), 1, reason: 'no scale (MOT-2)');
        }
      }
      await tester.pumpAndSettle();
      set(() => index = 0);
      await tester.pumpAndSettle();
      expect(find.text('One 1'), findsOneWidget);
    });

    testWidgets('.tabs keeps strip and body in step', (tester) async {
      var index = 0;
      await tester.pumpWidget(
        _host(
          StatefulBuilder(
            builder: (context, setState) => KitTabSwitcher.tabs(
              tabs: _tabs(3),
              index: index,
              onSelected: (i) => setState(() => index = i),
              semanticsLabel: 'Views',
              children: const [
                _Page('Page A'),
                _Page('Page B'),
                _Page('Page C'),
              ],
            ),
          ),
        ),
      );
      expect(find.text('Page A 0'), findsOneWidget);
      await tester.tap(find.text('Working'));
      await tester.pumpAndSettle();
      expect(index, 2);
      // Only the chosen destination takes touches.
      await tester.tap(find.text('Page C 0'));
      await tester.pump();
      expect(find.text('Page C 1'), findsOneWidget);
    });

    testWidgets('lazy builds only the chosen and preloaded destinations, '
        'then keeps each one it built', (tester) async {
      var index = 0;
      late StateSetter set;
      final built = <String>[];
      Widget page(String name) => Builder(
        builder: (context) {
          built.add(name);
          return _Page(name);
        },
      );
      await tester.pumpWidget(
        _host(
          StatefulBuilder(
            builder: (context, setState) {
              set = setState;
              return KitTabSwitcher(
                index: index,
                lazy: true,
                preload: const {0},
                children: [page('Work'), page('Inbox'), page('Settings')],
              );
            },
          ),
        ),
      );
      // Startup: the home destination only; the others are not built.
      expect(built.toSet(), {'Work'});
      expect(find.text('Inbox 0', skipOffstage: false), findsNothing);
      expect(find.text('Settings 0', skipOffstage: false), findsNothing);
      // The first visit builds it, visible from that frame on.
      set(() => index = 2);
      await tester.pump();
      expect(built, contains('Settings'));
      expect(find.text('Inbox 0', skipOffstage: false), findsNothing);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Settings 0'));
      await tester.pump();
      expect(find.text('Settings 1'), findsOneWidget);
      // Leaving and coming back keeps its state (no rebuild from scratch).
      set(() => index = 0);
      await tester.pumpAndSettle();
      expect(find.text('Settings 1', skipOffstage: false), findsOneWidget);
      set(() => index = 2);
      await tester.pumpAndSettle();
      expect(find.text('Settings 1'), findsOneWidget);
      expect(find.text('Inbox 0', skipOffstage: false), findsNothing);
    });

    testWidgets('lazy starts on a destination other than the preloaded one', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          KitTabSwitcher(
            index: 1,
            lazy: true,
            preload: const {0},
            children: const [_Page('Work'), _Page('Inbox'), _Page('Settings')],
          ),
        ),
      );
      expect(find.text('Inbox 0'), findsOneWidget);
      expect(find.text('Work 0', skipOffstage: false), findsOneWidget);
      expect(find.text('Settings 0', skipOffstage: false), findsNothing);
    });

    testWidgets('without lazy every destination is built at once', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          KitTabSwitcher(
            index: 0,
            children: const [_Page('Work'), _Page('Inbox')],
          ),
        ),
      );
      expect(find.text('Inbox 0', skipOffstage: false), findsOneWidget);
    });

    test('.tabs with mismatched lengths asserts', () {
      expect(
        () => KitTabSwitcher.tabs(
          tabs: _tabs(3),
          index: 0,
          onSelected: (_) {},
          children: const [SizedBox(), SizedBox()],
        ),
        throwsAssertionError,
      );
    });
  });

  group('reduced motion', () {
    testWidgets('a switch and a count change settle after one pump', (
      tester,
    ) async {
      var index = 0;
      var count = 1;
      late StateSetter set;
      await tester.pumpWidget(
        _host(
          StatefulBuilder(
            builder: (context, setState) {
              set = setState;
              return KitTabSwitcher.tabs(
                tabs: _tabs(2, counts: [count, 2]),
                index: index,
                onSelected: (_) {},
                children: const [_Page('One'), _Page('Two')],
              );
            },
          ),
          reduced: true,
        ),
      );
      await tester.pump();
      set(() {
        index = 1;
        count = 7;
      });
      await tester.pump();
      expect(tester.binding.transientCallbackCount, 0);
      expect(find.text('One 0'), findsNothing);
      expect(find.text('Two 0'), findsOneWidget);
      expect(find.text('7'), findsOneWidget);
      expect(find.text('1'), findsNothing);
    });
  });
}
