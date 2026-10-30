// Behaviour tests for KitNav (docs/ux-system/kit-api/KitNav.md, "Tests
// required"). Owner decision 2026-09-27: Arabic/RTL review dropped, so the
// spec's RTL case (9) is not run here.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/glass/kit_glass.dart';
import 'package:opencode_mobile/ui/kit/kit_bottom_inset.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_effects.dart';
import 'package:opencode_mobile/ui/kit/kit_layout.dart';
import 'package:opencode_mobile/ui/kit/kit_nav.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

List<KitNavDestination> _destinations({int inbox = 0, bool panes = false}) => [
  KitNavDestination(
    label: 'Work',
    icon: AppIconography.workspace,
    selectedIcon: AppIconography.workspaceSelected,
    key: const ValueKey('nav-work'),
    pane: panes ? (_) => const KitText('Work pane') : null,
  ),
  KitNavDestination(
    label: 'Inbox',
    icon: AppIconography.activity,
    selectedIcon: AppIconography.activitySelected,
    needsYou: inbox,
    key: const ValueKey('nav-inbox'),
  ),
  const KitNavDestination(
    label: 'Project',
    icon: AppIconography.files,
    selectedIcon: AppIconography.filesSelected,
    key: ValueKey('nav-project'),
  ),
  const KitNavDestination(
    label: 'Settings',
    icon: AppIconography.settings,
    key: ValueKey('nav-settings'),
  ),
];

class _Probe {
  KitClearance? clearance;
  double? paddingBottom;
  bool? hostsPane;
  final selections = <int>[];
}

/// A medium window by default: the rail. A phone (compact) draws no bar
/// at all (owner, 2026-10-20).
Future<_Probe> _pump(
  WidgetTester tester, {
  Size size = const Size(700, 1000),
  int inbox = 0,
  bool panes = false,
  double textScale = 1,
  double keyboard = 0,
  bool disableAnimations = false,
  KitEffects effects = KitEffects.defaults,
  int initial = 0,
  Widget? header,
  KitAction? primary,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final probe = _Probe();
  var selected = initial;
  await tester.pumpWidget(
    KitEffectsScope(
      effects: effects,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: TextScaler.linear(textScale),
            viewInsets: EdgeInsets.only(bottom: keyboard),
            padding: const EdgeInsets.only(bottom: 24),
            disableAnimations: disableAnimations,
          ),
          child: child!,
        ),
        home: StatefulBuilder(
          builder: (context, setState) => KitNav(
            destinations: _destinations(inbox: inbox, panes: panes),
            selected: selected,
            onSelected: (i) {
              probe.selections.add(i);
              setState(() => selected = i);
            },
            sidebarHeader: header,
            sidebarPrimary: primary,
            child: Builder(
              builder: (context) {
                probe.clearance = KitBottomInset.of(context);
                probe.paddingBottom = MediaQuery.paddingOf(context).bottom;
                probe.hostsPane = KitNav.hostsPane(context);
                return const ColoredBox(
                  color: Color(0xFF336699),
                  child: SizedBox.expand(),
                );
              },
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return probe;
}

KitNavRail? _rail(WidgetTester tester) {
  final found = find.byType(KitNavRail);
  return found.evaluate().isEmpty ? null : tester.widget<KitNavRail>(found);
}

void main() {
  Widget motionNav(int selected) => KitNav(
    destinations: _destinations(inbox: 2),
    selected: selected,
    onSelected: (_) {},
    child: Text(selected == 0 ? 'Work content' : 'Inbox content'),
  );
  Widget motionRail(int selected) => KitNavRail(
    destinations: _destinations(inbox: 2),
    selected: selected,
    onSelected: (_) {},
  );
  kitMotionStillTests(
    'KitNav',
    builds: {'needs you': () => motionNav(0)},
    changes: {
      'destination changes': KitMotionChange(
        build: () => motionNav(0),
        act: (tester, stage) => stage.rebuild(motionNav(1)),
        shows: 'Inbox content',
      ),
    },
  );
  kitMotionStillTests(
    'KitNavRail',
    builds: {'selected work': () => motionRail(0)},
    changes: {
      'inbox selected': KitMotionChange(
        build: () => motionRail(0),
        act: (tester, stage) => stage.rebuild(motionRail(1)),
        shows: 'Inbox',
      ),
    },
  );

  // Real faces, so label widths (and the A11Y-8 clamp) are the device's.
  setUpAll(loadKitGalleryFonts);

  testWidgets('layout follows the window class, short windows keep the rail', (
    tester,
  ) async {
    // A phone: no bar, no destinations; the child is the whole window.
    await _pump(tester, size: const Size(412, 915));
    expect(find.byType(KitNavRail), findsNothing);
    expect(find.byKey(const ValueKey('nav-inbox')), findsNothing);
    expect(
      tester.getSize(
        find.byWidgetPredicate(
          (widget) =>
              widget is ColoredBox && widget.color == const Color(0xFF336699),
        ),
      ),
      const Size(412, 915),
    );

    await _pump(tester, size: const Size(700, 1000));
    expect(_rail(tester)!.extended, isFalse);

    await _pump(tester, size: const Size(1280, 800));
    expect(_rail(tester)!.extended, isTrue);
    expect(tester.getSize(find.byType(KitNavRail)).width, 296);

    // Behaviour: "< 480 dp tall keeps the rail (medium-or-wider)"; 915
    // wide is expanded, so the rail.
    await _pump(tester, size: const Size(915, 412));
    expect(_rail(tester)!.extended, isFalse);

    await _pump(tester, size: const Size(412, 300));
    expect(find.byType(KitNavRail), findsNothing);
  });

  testWidgets('a tap selects once and the selected one says so', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final probe = await _pump(tester);
    await tester.tap(find.byKey(const ValueKey('nav-inbox')));
    await tester.pumpAndSettle();
    expect(probe.selections, [1]);
    expect(
      tester.getSemantics(find.byKey(const ValueKey('nav-inbox'))),
      isSemantics(
        label: 'Inbox',
        isButton: true,
        isSelected: true,
        hasTapAction: true,
      ),
    );
    semantics.dispose();
  });

  testWidgets('needs-you count is read once with the label', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pump(tester, inbox: 3);
    expect(find.bySemanticsLabel('Inbox, 3 need you'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
    await _pump(tester, inbox: 1);
    expect(find.bySemanticsLabel('Inbox, 1 need you'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('a phone adds nothing above the system inset', (tester) async {
    final probe = await _pump(tester, size: const Size(412, 915));
    expect(probe.paddingBottom, 24);
    expect(probe.hostsPane, isFalse);
  });

  testWidgets('clearance published per layout', (tester) async {
    var probe = await _pump(tester, size: const Size(700, 1000));
    expect(probe.clearance!.start, KitLayout.railWidth + 8);
    expect(tester.getSize(find.byType(KitNavRail)).width, 80);
    expect(tester.getTopLeft(find.byType(KitNavRail)).dx, 8);

    probe = await _pump(tester, size: const Size(1280, 800));
    expect(probe.clearance!.start, 296);
  });

  testWidgets('the sidebar widens with larger text so its primary keeps one '
      'line, capped at 400 dp and a third of the window', (tester) async {
    final primary = KitAction(label: 'New conversation', onPressed: () {});
    var probe = await _pump(
      tester,
      size: const Size(1280, 800),
      textScale: 2,
      panes: true,
      primary: primary,
    );
    expect(tester.getSize(find.byType(KitNavRail)).width, 400);
    expect(probe.clearance!.start, 400);
    final label = find.descendant(
      of: find.byType(KitButton),
      matching: find.text('New conversation'),
    );
    // One line: the label is as tall as one line of its own style.
    final paragraph = tester.renderObject<RenderParagraph>(label);
    final tops = {
      for (final box in paragraph.getBoxesForSelection(
        const TextSelection(baseOffset: 0, extentOffset: 16),
      ))
        box.top,
    };
    expect(tops, hasLength(1));
    expect(tester.takeException(), isNull);

    probe = await _pump(tester, size: const Size(1280, 800), textScale: 1.1);
    expect(tester.getSize(find.byType(KitNavRail)).width, closeTo(325.6, .01));

    // An expanded window keeps a third for the sidebar at most.
    probe = await _pump(tester, size: const Size(900, 800), textScale: 2);
    expect(tester.getSize(find.byType(KitNavRail)).width, 300);
    expect(probe.clearance!.start, 300);
  });

  testWidgets('sidebar holds header, destinations, pane and primary', (
    tester,
  ) async {
    var pressed = 0;
    var probe = await _pump(
      tester,
      size: const Size(1280, 800),
      panes: true,
      header: const KitText('Server header'),
      primary: KitAction(label: 'New conversation', onPressed: () => pressed++),
    );
    expect(find.text('Server header'), findsOneWidget);
    for (final label in ['Work', 'Inbox', 'Project', 'Settings']) {
      expect(find.text(label), findsOneWidget);
    }
    expect(find.text('Work pane'), findsOneWidget);
    expect(probe.hostsPane, isTrue);
    await tester.tap(find.text('New conversation'));
    expect(pressed, 1);
    for (final label in ['Work', 'Inbox']) {
      expect(
        tester
            .getSize(find.byKey(ValueKey('nav-${label.toLowerCase()}')))
            .height,
        greaterThanOrEqualTo(48),
      );
    }

    // Inbox has no pane.
    await tester.tap(find.text('Inbox'));
    await tester.pumpAndSettle();
    expect(find.text('Work pane'), findsNothing);
    expect(probe.hostsPane, isFalse);

    probe = await _pump(tester, size: const Size(412, 915), panes: true);
    expect(probe.hostsPane, isFalse);
    probe = await _pump(tester, size: const Size(700, 1000), panes: true);
    expect(probe.hostsPane, isFalse);
  });

  testWidgets('the rail is a solid surface at navRadius; sidebar is not', (
    tester,
  ) async {
    await _pump(tester, size: const Size(700, 1000));
    final glass = tester.widget<KitGlass>(
      find.descendant(
        of: find.byType(KitNavRail),
        matching: find.byType(KitGlass),
      ),
    );
    expect(glass.borderRadius, BorderRadius.circular(22));

    await _pump(tester, size: const Size(1280, 800));
    expect(
      find.descendant(
        of: find.byType(KitNavRail),
        matching: find.byType(KitGlass),
      ),
      findsNothing,
    );
  });

  for (final height in [1000.0, 600.0]) {
    testWidgets('200% text, rail $height tall: no overflow, labels shown', (
      tester,
    ) async {
      await _pump(tester, size: Size(700, height), textScale: 2);
      expect(tester.takeException(), isNull);
      for (final label in ['Work', 'Inbox', 'Project', 'Settings']) {
        expect(find.text(label), findsOneWidget);
      }
      final scale = MediaQuery.textScalerOf(
        tester.element(find.text('Work')),
      ).scale(10);
      expect(scale, lessThanOrEqualTo(10 * KitTokens.navLabelMaxScale));
    });
  }

  testWidgets('keyboard: Tab lands on selected, arrows move, Enter selects', (
    tester,
  ) async {
    FocusManager.instance.highlightStrategy =
        FocusHighlightStrategy.alwaysTraditional;
    addTearDown(
      () => FocusManager.instance.highlightStrategy =
          FocusHighlightStrategy.automatic,
    );
    final probe = await _pump(tester, size: const Size(700, 1000), initial: 1);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pumpAndSettle();
    String? focused() => FocusManager.instance.primaryFocus?.debugLabel;
    expect(focused(), 'KitNav 1');
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.pumpAndSettle();
    expect(focused(), 'KitNav 2');
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
    await tester.pumpAndSettle();
    expect(focused(), 'KitNav 0');
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.pumpAndSettle();
    expect(focused(), 'KitNav 2');
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();
    expect(probe.selections, [2]);
  });

  testWidgets('reduced motion: switching settles in one pump', (tester) async {
    await _pump(tester, disableAnimations: true);
    final lens = find.byWidgetPredicate(
      (widget) => widget.runtimeType.toString() == '_KitNavLens',
    );
    final before = tester.getRect(lens);
    await tester.tap(find.byKey(const ValueKey('nav-settings')));
    await tester.pump();
    final after = tester.getRect(lens);
    expect(after.top, greaterThan(before.top));
    await tester.pumpAndSettle();
    expect(tester.getRect(lens), after);
  });
}
