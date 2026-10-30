// KitSegmented (lib/ui/kit/kit_segmented.dart), docs/ux-system/kit-api/
// KitSegmented.md "Tests required", including item 5: the stacked form of
// KitChoiceRows when the labels do not fit (KIT-24).
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_choice_list.dart';
import 'package:opencode_mobile/ui/kit/kit_segmented.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

final _dark = AppTheme.dark();

Widget _host(
  Widget child, {
  TextDirection direction = TextDirection.ltr,
  double textScale = 1,
  bool reduced = false,
  // A platform without the Android ripple, so a press shows the flat
  // pressed fill (owner rule 2026-10-26: Android draws the ripple).
  bool flat = false,
}) => MaterialApp(
  theme: flat ? _dark.copyWith(platform: TargetPlatform.fuchsia) : _dark,
  builder: (context, app) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      textScaler: TextScaler.linear(textScale),
      disableAnimations: reduced,
    ),
    child: app!,
  ),
  home: Scaffold(
    body: Directionality(
      textDirection: direction,
      child: Padding(padding: const EdgeInsets.all(16), child: child),
    ),
  ),
);

List<KitSegment<String>> _segments({
  bool cDisabled = false,
  bool bDisabled = false,
  int? countB,
}) => [
  const KitSegment(value: 'a', label: 'Alpha'),
  KitSegment(
    value: 'b',
    label: 'Bravo',
    count: countB,
    enabled: !bDisabled,
    disabledReason: bDisabled
        ? 'Chosen at install · reinstall to change'
        : null,
  ),
  KitSegment(
    value: 'c',
    label: 'Charlie',
    enabled: !cDisabled,
    disabledReason: cDisabled
        ? 'Chosen at install · reinstall to change'
        : null,
  ),
];

KitSegmented<String> _part({
  String selected = 'a',
  ValueChanged<String>? onChanged,
  bool cDisabled = false,
  int? countB,
}) => KitSegmented<String>(
  segments: _segments(cDisabled: cDisabled, countB: countB),
  selected: selected,
  onChanged: onChanged ?? (_) {},
  semanticsLabel: 'Choice',
);

/// A host that keeps the selection, as a screen would: the part keeps none.
class _Chooser extends StatefulWidget {
  const _Chooser({this.calls});

  final List<String>? calls;

  @override
  State<_Chooser> createState() => _ChooserState();
}

class _ChooserState extends State<_Chooser> {
  String _selected = 'a';

  @override
  Widget build(BuildContext context) => KitSegmented<String>(
    segments: _segments(),
    selected: _selected,
    onChanged: (value) {
      widget.calls?.add(value);
      setState(() => _selected = value);
    },
    semanticsLabel: 'Choice',
  );
}

/// The check glyphs a person can see: painted with an opacity above zero.
List<Element> _visibleChecks(WidgetTester tester) => [
  for (final element in find.byIcon(AppIconography.check).evaluate())
    if (_painted(element.renderObject!)) element,
];

bool _painted(RenderObject object) {
  for (RenderObject? node = object; node != null; node = node.parent) {
    if (node is RenderOpacity && node.opacity == 0) return false;
    if (node is RenderAnimatedOpacity && node.opacity.value == 0) return false;
  }
  return true;
}

/// The label of the segment that holds keyboard focus: the one label inside
/// the focused node. Null when focus is outside the group, or on something
/// that holds the whole group (the app's scope).
String? _focusedLabel(List<String> labels) {
  final context = FocusManager.instance.primaryFocus?.context;
  if (context == null) return null;
  final inside = [
    for (final label in labels)
      if (find
          .descendant(
            of: find.byElementPredicate((e) => e == context),
            matching: find.text(label),
          )
          .evaluate()
          .isNotEmpty)
        label,
  ];
  return inside.length == 1 ? inside.single : null;
}

const _labels = ['Alpha', 'Bravo', 'Charlie'];

/// Every colour the control paints with a [Paint] (fills, outlines, the
/// ring), as 32-bit ARGB so a role colour and a painted colour compare
/// equal. Text and glyphs are painted as paragraphs and are not included.
Set<int> _paintColors(
  WidgetTester tester, {
  Finder? around,
  bool strokesOnly = false,
}) {
  final colors = <int>{};
  expect(
    around ?? find.byType(KitSegmented<String>),
    paints..everything((method, args) {
      for (final arg in args) {
        if (arg is Paint &&
            (!strokesOnly || arg.style == PaintingStyle.stroke)) {
          colors.add(arg.color.toARGB32());
        }
      }
      return true;
    }),
  );
  return colors;
}

_Palette _rolesOf(WidgetTester tester) =>
    _Palette(KitTokens.of(tester.element(find.byType(KitSegmented<String>))));

/// The token colours this part may paint with (KitSegmented.md "Tokens"),
/// as 32-bit ARGB.
class _Palette {
  _Palette(KitTokens tokens)
    : accent = tokens.roles.accent.toARGB32(),
      surface2 = tokens.roles.surface2.toARGB32(),
      surface3 = tokens.roles.surface3.toARGB32(),
      allowed = {
        for (final role in [
          tokens.roles.surface1,
          tokens.roles.surface2,
          tokens.roles.surface3,
          tokens.roles.hairline,
          tokens.roles.accent,
        ])
          role.toARGB32(),
      };

  final int accent;
  final int surface2;
  final int surface3;
  final Set<int> allowed;
}

Future<void> _tab(WidgetTester tester, {bool shift = false}) async {
  if (shift) await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
  await tester.sendKeyEvent(LogicalKeyboardKey.tab);
  if (shift) await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
  await tester.pump();
}

Widget _between(Widget part, FocusNode before, FocusNode after) => Column(
  mainAxisSize: MainAxisSize.min,
  children: [
    TextButton(
      focusNode: before,
      autofocus: true,
      onPressed: () {},
      child: const Text('Before'),
    ),
    part,
    TextButton(focusNode: after, onPressed: () {}, child: const Text('After')),
  ],
);

void main() {
  kitMotionStillTests(
    'KitSegmented',
    builds: {
      'default': () => _part(),
      'with counts': () => _part(selected: 'b', countB: 2),
      'segment disabled': () => _part(cDisabled: true),
      'disabled': () => KitSegmented<String>(
        segments: _segments(),
        selected: 'a',
        onChanged: null,
        semanticsLabel: 'Choice',
        disabledReason: 'Set by your admin',
      ),
    },
    changes: {
      'a tap moves the selection': KitMotionChange(
        build: () => const _Chooser(),
        act: (tester, stage) => tester.tap(find.text('Bravo')),
        shows: 'Bravo',
      ),
      'a segment becomes disabled': KitMotionChange(
        build: () => _part(),
        act: (tester, stage) => stage.rebuild(_part(cDisabled: true)),
        shows: 'Chosen at install · reinstall to change',
      ),
      // The host changes the value (KIT-24's LayoutBuilder must not leave a
      // frame callback behind when the Tab stop moves with it).
      'the host moves the selection': KitMotionChange(
        build: () => _part(),
        act: (tester, stage) => stage.rebuild(_part(selected: 'c')),
        shows: 'Charlie',
      ),
    },
  );

  group('construction (G37)', () {
    test('fewer than 2 segments asserts', () {
      expect(
        () => KitSegmented<String>(
          segments: const [KitSegment(value: 'a', label: 'Alpha')],
          selected: 'a',
          onChanged: (_) {},
          semanticsLabel: 'Choice',
        ),
        throwsAssertionError,
      );
    });

    test('more than 4 segments asserts', () {
      expect(
        () => KitSegmented<int>(
          segments: [
            for (var i = 0; i < 5; i++) KitSegment(value: i, label: '$i'),
          ],
          selected: 0,
          onChanged: (_) {},
          semanticsLabel: 'Choice',
        ),
        throwsAssertionError,
      );
    });

    testWidgets('a selected value not among the segments asserts', (
      tester,
    ) async {
      await tester.pumpWidget(_host(_part(selected: 'z')));
      expect(tester.takeException(), isAssertionError);
    });

    test('a disabled segment without disabledReason asserts', () {
      expect(
        () => KitSegment<String>(value: 'c', label: 'Charlie', enabled: false),
        throwsAssertionError,
      );
    });

    test('onChanged null without a group disabledReason asserts', () {
      expect(
        () => KitSegmented<String>(
          segments: _segments(),
          selected: 'a',
          onChanged: null,
          semanticsLabel: 'Choice',
        ),
        throwsAssertionError,
      );
    });
  });

  group('tapping (KIT-24, STATE-9)', () {
    testWidgets('tapping another segment calls onChanged exactly once', (
      tester,
    ) async {
      final calls = <String>[];
      await tester.pumpWidget(_host(_part(onChanged: calls.add)));
      await tester.tap(find.text('Bravo'));
      await tester.pump();
      expect(calls, ['b']);
    });

    testWidgets('tapping the selected segment does not call onChanged', (
      tester,
    ) async {
      final calls = <String>[];
      await tester.pumpWidget(_host(_part(onChanged: calls.add)));
      await tester.tap(find.text('Alpha'));
      await tester.pump();
      expect(calls, isEmpty);
    });

    testWidgets('tapping a disabled segment does not call onChanged', (
      tester,
    ) async {
      final calls = <String>[];
      await tester.pumpWidget(
        _host(_part(onChanged: calls.add, cDisabled: true)),
      );
      await tester.tap(find.text('Charlie'), warnIfMissed: false);
      await tester.pump();
      expect(calls, isEmpty);
    });

    testWidgets('a tap leaves no keyboard focus or ring behind', (
      tester,
    ) async {
      await tester.pumpWidget(_host(const _Chooser()));
      await tester.tap(find.text('Bravo'));
      await tester.pumpAndSettle();
      expect(
        _focusedLabel(_labels),
        isNull,
        reason: 'a touch does not move keyboard focus into the group',
      );
      expect(
        _paintColors(tester, strokesOnly: true),
        isNot(contains(_rolesOf(tester).accent)),
        reason: 'no accent focus ring after a touch',
      );
    });
  });

  group('the check and semantics (A11Y-1)', () {
    testWidgets('only the selected segment shows the check glyph', (
      tester,
    ) async {
      await tester.pumpWidget(_host(_part(selected: 'b')));
      final checks = _visibleChecks(tester);
      expect(checks, hasLength(1));
      // It sits in the selected (middle) third of the control.
      final control = tester.getRect(find.byType(KitSegmented<String>));
      final check = tester.getCenter(find.byElementPredicate(checks.contains));
      final third = control.width / 3;
      expect(check.dx, greaterThan(control.left + third));
      expect(check.dx, lessThan(control.left + 2 * third));
    });

    testWidgets('the check moves with the selection', (tester) async {
      await tester.pumpWidget(_host(const _Chooser()));
      await tester.tap(find.text('Charlie'));
      await tester.pumpAndSettle();
      final checks = _visibleChecks(tester);
      expect(checks, hasLength(1));
      final control = tester.getRect(find.byType(KitSegmented<String>));
      final check = tester.getCenter(find.byElementPredicate(checks.contains));
      expect(check.dx, greaterThan(control.left + 2 * control.width / 3));
    });

    testWidgets('each segment carries button, selected and group semantics', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(_host(_part(selected: 'b')));
      expect(
        tester.getSemantics(find.text('Alpha')),
        matchesSemantics(
          label: 'Alpha',
          isButton: true,
          isInMutuallyExclusiveGroup: true,
          isEnabled: true,
          hasTapAction: true,
          hasEnabledState: true,
          hasSelectedState: true,
        ),
      );
      expect(
        tester.getSemantics(find.text('Bravo')),
        matchesSemantics(
          label: 'Bravo',
          isButton: true,
          isSelected: true,
          isInMutuallyExclusiveGroup: true,
          isEnabled: true,
          hasTapAction: true,
          hasEnabledState: true,
          hasSelectedState: true,
        ),
      );
      expect(
        find.bySemanticsLabel('Choice'),
        findsOneWidget,
        reason: 'the group carries its semanticsLabel',
      );
      semantics.dispose();
    });

    testWidgets('a disabled segment is announced as not enabled', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(_host(_part(cDisabled: true)));
      expect(
        tester.getSemantics(find.text('Charlie')),
        matchesSemantics(
          label: 'Charlie',
          isButton: true,
          isInMutuallyExclusiveGroup: true,
          hasEnabledState: true,
          hasSelectedState: true,
        ),
      );
      semantics.dispose();
    });

    testWidgets('the count is included in the segment semantic label', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(_host(_part(countB: 2)));
      expect(
        tester.getSemantics(find.text('Bravo')),
        matchesSemantics(
          label: 'Bravo, 2',
          isButton: true,
          isInMutuallyExclusiveGroup: true,
          isEnabled: true,
          hasTapAction: true,
          hasEnabledState: true,
          hasSelectedState: true,
        ),
      );
      semantics.dispose();
    });
  });

  group('disabled reasons (STATE-8)', () {
    testWidgets(
      "a disabled segment's reason is visible text under the control",
      (tester) async {
        await tester.pumpWidget(_host(_part(cDisabled: true)));
        final reason = find.text('Chosen at install · reinstall to change');
        expect(reason, findsOneWidget);
        expect(
          tester.getTopLeft(reason).dy,
          greaterThan(tester.getBottomLeft(find.text('Charlie')).dy),
          reason: 'the reason sits under the control',
        );
      },
    );

    testWidgets('a reason shared by several segments is shown once', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          KitSegmented<String>(
            segments: _segments(bDisabled: true, cDisabled: true),
            selected: 'a',
            onChanged: (_) {},
            semanticsLabel: 'Choice',
          ),
        ),
      );
      expect(
        find.text('Chosen at install · reinstall to change'),
        findsOneWidget,
      );
    });

    testWidgets('a disabled whole control shows its own reason', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          KitSegmented<String>(
            segments: _segments(),
            selected: 'a',
            onChanged: null,
            semanticsLabel: 'Choice',
            disabledReason: 'Set by your admin',
          ),
        ),
      );
      expect(find.text('Set by your admin'), findsOneWidget);
    });
  });

  group('keyboard (LAY-10, LAY-11, G14)', () {
    late FocusNode before;
    late FocusNode after;
    setUp(() {
      before = FocusNode(debugLabel: 'before');
      after = FocusNode(debugLabel: 'after');
    });
    tearDown(() {
      before.dispose();
      after.dispose();
    });

    testWidgets('Tab lands on the selected segment', (tester) async {
      await tester.pumpWidget(
        _host(_between(_part(selected: 'b'), before, after)),
      );
      await tester.pump();
      expect(FocusManager.instance.primaryFocus, before);
      await _tab(tester);
      expect(_focusedLabel(_labels), 'Bravo');
    });

    testWidgets('the group is one Tab stop', (tester) async {
      await tester.pumpWidget(_host(_between(_part(), before, after)));
      await tester.pump();
      await _tab(tester);
      expect(_focusedLabel(_labels), 'Alpha');
      await _tab(tester);
      expect(FocusManager.instance.primaryFocus, after);
    });

    testWidgets('Arrow Right moves focus without calling onChanged', (
      tester,
    ) async {
      final calls = <String>[];
      await tester.pumpWidget(
        _host(_between(_part(onChanged: calls.add), before, after)),
      );
      await tester.pump();
      await _tab(tester);
      expect(_focusedLabel(_labels), 'Alpha');
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump();
      expect(_focusedLabel(_labels), 'Bravo');
      expect(calls, isEmpty, reason: 'arrows move focus only');
    });

    testWidgets('arrows skip a disabled segment', (tester) async {
      await tester.pumpWidget(
        _host(
          _between(
            KitSegmented<String>(
              segments: _segments(bDisabled: true),
              selected: 'a',
              onChanged: (_) {},
              semanticsLabel: 'Choice',
            ),
            before,
            after,
          ),
        ),
      );
      await tester.pump();
      await _tab(tester);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump();
      expect(_focusedLabel(_labels), 'Charlie');
    });

    testWidgets('Space calls onChanged for the focused segment', (
      tester,
    ) async {
      final calls = <String>[];
      await tester.pumpWidget(
        _host(_between(_part(onChanged: calls.add), before, after)),
      );
      await tester.pump();
      await _tab(tester);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pump();
      expect(calls, ['b']);
    });

    testWidgets('Enter calls onChanged for the focused segment', (
      tester,
    ) async {
      final calls = <String>[];
      await tester.pumpWidget(
        _host(_between(_part(onChanged: calls.add), before, after)),
      );
      await tester.pump();
      await _tab(tester);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(calls, ['b']);
    });

    testWidgets('in RTL, Arrow Left moves to the next segment', (tester) async {
      final calls = <String>[];
      await tester.pumpWidget(
        _host(
          _between(_part(onChanged: calls.add), before, after),
          direction: TextDirection.rtl,
        ),
      );
      await tester.pump();
      await _tab(tester);
      expect(_focusedLabel(_labels), 'Alpha');
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
      await tester.pump();
      expect(_focusedLabel(_labels), 'Bravo');
      expect(calls, isEmpty);
    });

    testWidgets('leaving the group and coming back lands on the selected '
        'segment again', (tester) async {
      await tester.pumpWidget(
        _host(_between(_part(selected: 'b'), before, after)),
      );
      await tester.pump();
      await _tab(tester);
      expect(_focusedLabel(_labels), 'Bravo');
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump();
      expect(_focusedLabel(_labels), 'Charlie');
      await _tab(tester);
      expect(FocusManager.instance.primaryFocus, after);
      await _tab(tester, shift: true);
      expect(
        _focusedLabel(_labels),
        'Bravo',
        reason: 'Shift+Tab back in lands on the selected segment',
      );
      await _tab(tester, shift: true);
      expect(FocusManager.instance.primaryFocus, before);
      await _tab(tester);
      expect(
        _focusedLabel(_labels),
        'Bravo',
        reason: 'Tab back in lands on the selected segment',
      );
    });

    testWidgets('keyboard focus shows the accent ring', (tester) async {
      await tester.pumpWidget(_host(_between(_part(), before, after)));
      await tester.pump();

      expect(
        _paintColors(tester, strokesOnly: true),
        isNot(contains(_rolesOf(tester).accent)),
        reason: 'no ring before focus arrives',
      );
      await _tab(tester);
      expect(_paintColors(tester), contains(_rolesOf(tester).accent));
    });

    testWidgets('the ring does not move the content', (tester) async {
      await tester.pumpWidget(_host(_between(_part(), before, after)));
      await tester.pump();
      final at = tester.getTopLeft(find.text('Alpha'));
      await _tab(tester);
      expect(_paintColors(tester), contains(_rolesOf(tester).accent));
      expect(tester.getTopLeft(find.text('Alpha')), at);
    });
  });

  group('fine pointer (D11)', () {
    testWidgets('hover and press fill with surface steps, never overlays', (
      tester,
    ) async {
      await tester.pumpWidget(_host(_part(), flat: true));
      final roles = _rolesOf(tester);
      // Framework ink paints on the Material behind the control, so the
      // colours are read from there: the page's own ground plus the tokens.
      final behind = find
          .ancestor(
            of: find.byType(KitSegmented<String>),
            matching: find.byType(Material),
          )
          .first;
      final allowed = {
        ...roles.allowed,
        tester.widget<Material>(behind).color!.toARGB32(),
      };
      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      addTearDown(mouse.removePointer);
      await mouse.addPointer(location: Offset.zero);
      await mouse.moveTo(tester.getCenter(find.text('Bravo')));
      await tester.pump();
      final hovered = _paintColors(tester, around: behind);
      expect(hovered.difference(allowed), isEmpty);
      await mouse.down(tester.getCenter(find.text('Bravo')));
      await tester.pump();
      final pressed = _paintColors(tester, around: behind);
      expect(pressed.difference(allowed), isEmpty);
      expect(pressed, contains(roles.surface3));
      await mouse.up();
      await tester.pump();
    });

    testWidgets('a hovered unselected segment fills surface2 in dark', (
      tester,
    ) async {
      await tester.pumpWidget(_host(_part()));
      final roles = _rolesOf(tester);
      expect(_paintColors(tester), isNot(contains(roles.surface2)));
      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      addTearDown(mouse.removePointer);
      await mouse.addPointer(location: Offset.zero);
      await mouse.moveTo(tester.getCenter(find.text('Bravo')));
      await tester.pump();
      expect(_paintColors(tester), contains(roles.surface2));
    });
  });

  group('reduced motion (G8)', () {
    testWidgets('after a real tap one pump() settles and no ticker runs', (
      tester,
    ) async {
      final calls = <String>[];
      await tester.pumpWidget(_host(_Chooser(calls: calls), reduced: true));
      await tester.tap(find.text('Bravo'));
      await tester.pump();
      expect(calls, ['b']);
      expect(tester.hasRunningAnimations, isFalse);
      final checks = _visibleChecks(tester);
      expect(checks, hasLength(1));
      final control = tester.getRect(find.byType(KitSegmented<String>));
      final check = tester.getCenter(find.byElementPredicate(checks.contains));
      expect(check.dx, greaterThan(control.left + control.width / 3));
      expect(check.dx, lessThan(control.left + 2 * control.width / 3));
    });
  });

  group('stacking (KIT-24)', () {
    const longLabels = [
      'Only this conversation',
      'Every conversation on this computer',
    ];
    KitSegmented<String> long({
      String selected = 'a',
      ValueChanged<String>? onChanged,
    }) => KitSegmented<String>(
      segments: [
        KitSegment(value: 'a', label: longLabels[0]),
        KitSegment(value: 'b', label: longLabels[1]),
      ],
      selected: selected,
      onChanged: onChanged ?? (_) {},
      semanticsLabel: 'Permission scope',
    );

    Future<void> at(WidgetTester tester, Size size) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = size;
      addTearDown(tester.view.reset);
    }

    Finder rows() => find.byWidgetPredicate((w) => w is KitChoiceRow);

    testWidgets('at text 2.0 it renders full-width KitChoiceRows with the '
        'same values, and no track', (tester) async {
      await at(tester, const Size(412, 915));
      await tester.pumpWidget(_host(_part(selected: 'b'), textScale: 2));
      final found = tester.widgetList<KitChoiceRow<String>>(rows()).toList();
      expect([for (final r in found) r.choice.value], ['a', 'b', 'c']);
      expect([for (final r in found) r.choice.title], _labels);
      expect([for (final r in found) r.selected], [false, true, false]);
      final part = tester.getRect(find.byType(KitSegmented<String>));
      final rects = [
        for (final e in rows().evaluate())
          tester.getRect(find.byElementPredicate((x) => x == e)),
      ];
      for (var i = 0; i < rects.length; i++) {
        expect(rects[i].width, moreOrLessEquals(part.width));
        if (i > 0) {
          expect(rects[i].top, greaterThanOrEqualTo(rects[i - 1].bottom));
        }
      }
      expect(_visibleChecks(tester), isEmpty, reason: 'the track is gone');
      expect(tester.takeException(), isNull);
    });

    testWidgets('at 320 dp, text 1.0, long labels stack', (tester) async {
      await at(tester, const Size(320, 800));
      await tester.pumpWidget(_host(long()));
      expect(rows(), findsNWidgets(2));
      for (final label in longLabels) {
        expect(find.text(label), findsOneWidget, reason: 'never cut');
      }
      expect(tester.takeException(), isNull);
    });

    testWidgets('at 412 dp, text 1.0, short labels stay one row', (
      tester,
    ) async {
      await at(tester, const Size(412, 915));
      // The test font draws every glyph one em wide, so "short" here is
      // shorter than on a phone's font.
      const short = ['Day', 'Week', 'Year'];
      await tester.pumpWidget(
        _host(
          KitSegmented<String>(
            segments: [for (final l in short) KitSegment(value: l, label: l)],
            selected: 'Day',
            onChanged: (_) {},
            semanticsLabel: 'Range',
          ),
        ),
      );
      expect(rows(), findsNothing);
      expect(_visibleChecks(tester), hasLength(1));
      final rects = [for (final l in short) tester.getRect(find.text(l))];
      expect(rects[1].top, moreOrLessEquals(rects[0].top));
      expect(rects[2].top, moreOrLessEquals(rects[0].top));
    });

    testWidgets('it stacks the moment a label would not fit, and returns to '
        'one row when it fits again', (tester) async {
      await at(tester, const Size(1280, 800));
      // Labels that fit the 720 dp reading cap on one row (the test font is
      // one em a glyph), but not 320 dp.
      Widget mid() => KitSegmented<String>(
        segments: const [
          KitSegment(value: 'a', label: 'Only this one'),
          KitSegment(value: 'b', label: 'Everything here'),
        ],
        selected: 'a',
        onChanged: (_) {},
        semanticsLabel: 'Permission scope',
      );
      await tester.pumpWidget(_host(mid()));
      expect(rows(), findsNothing, reason: 'room for both labels');
      tester.view.physicalSize = const Size(320, 800);
      await tester.pump();
      expect(rows(), findsNWidgets(2));
      tester.view.physicalSize = const Size(1280, 800);
      await tester.pump();
      expect(rows(), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('tapping a row calls onChanged once; the selected row and a '
        'disabled row do not', (tester) async {
      final calls = <String>[];
      await tester.pumpWidget(
        _host(
          _part(selected: 'a', cDisabled: true, onChanged: calls.add),
          textScale: 2,
        ),
      );
      await tester.tap(find.text('Bravo'));
      await tester.tap(find.text('Alpha'));
      await tester.tap(find.text('Charlie'));
      await tester.pump();
      expect(calls, ['b']);
    });

    testWidgets('rows carry selected and radio-group semantics, the group '
        'keeps its label', (tester) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(_host(_part(selected: 'b'), textScale: 2));
      expect(
        tester.getSemantics(find.text('Bravo')),
        isSemantics(
          label: 'Bravo',
          isSelected: true,
          isInMutuallyExclusiveGroup: true,
          isEnabled: true,
        ),
      );
      expect(
        tester.getSemantics(find.text('Alpha')),
        isSemantics(
          label: 'Alpha',
          isSelected: false,
          isInMutuallyExclusiveGroup: true,
          isEnabled: true,
          hasTapAction: true,
        ),
      );
      expect(find.bySemanticsLabel('Choice'), findsOneWidget);
      semantics.dispose();
    });

    testWidgets("a disabled segment's reason is on its own row, once", (
      tester,
    ) async {
      await tester.pumpWidget(_host(_part(cDisabled: true), textScale: 2));
      final reason = find.text('Chosen at install · reinstall to change');
      expect(reason, findsOneWidget);
      expect(
        find.descendant(
          of: find.byWidgetPredicate(
            (w) => w is KitChoiceRow && w.choice.value == 'c',
          ),
          matching: reason,
        ),
        findsOneWidget,
      );
    });

    testWidgets('a disabled whole control keeps its one reason under the '
        'stack', (tester) async {
      await tester.pumpWidget(
        _host(
          KitSegmented<String>(
            segments: _segments(),
            selected: 'a',
            onChanged: null,
            semanticsLabel: 'Choice',
            disabledReason: 'Set by your admin',
          ),
          textScale: 2,
        ),
      );
      expect(find.text('Set by your admin'), findsOneWidget);
      expect(
        tester.getTopLeft(find.text('Set by your admin')).dy,
        greaterThan(tester.getBottomLeft(find.text('Charlie')).dy),
      );
    });

    testWidgets('the count joins the row title', (tester) async {
      await tester.pumpWidget(_host(_part(countB: 2), textScale: 2));
      expect(find.text('Bravo · 2'), findsOneWidget);
    });

    group('keyboard', () {
      late FocusNode before;
      late FocusNode after;
      setUp(() {
        before = FocusNode(debugLabel: 'before');
        after = FocusNode(debugLabel: 'after');
      });
      tearDown(() {
        before.dispose();
        after.dispose();
      });

      testWidgets('Tab lands on the selected row, and the stack is one Tab '
          'stop', (tester) async {
        await tester.pumpWidget(
          _host(_between(_part(selected: 'b'), before, after), textScale: 2),
        );
        await tester.pump();
        await _tab(tester);
        expect(_focusedLabel(_labels), 'Bravo');
        await _tab(tester);
        expect(FocusManager.instance.primaryFocus, after);
        await _tab(tester, shift: true);
        expect(_focusedLabel(_labels), 'Bravo');
      });

      testWidgets('Arrow Down and Up move focus without selecting and skip a '
          'disabled row; Space selects', (tester) async {
        final calls = <String>[];
        await tester.pumpWidget(
          _host(
            _between(
              _part(selected: 'a', cDisabled: true, onChanged: calls.add),
              before,
              after,
            ),
            textScale: 2,
          ),
        );
        await tester.pump();
        await _tab(tester);
        expect(_focusedLabel(_labels), 'Alpha');
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
        await tester.pump();
        expect(_focusedLabel(_labels), 'Bravo');
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
        await tester.pump();
        expect(_focusedLabel(_labels), 'Alpha', reason: 'skips Charlie');
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
        await tester.pump();
        expect(_focusedLabel(_labels), 'Bravo');
        expect(calls, isEmpty, reason: 'arrows never select');
        await tester.sendKeyEvent(LogicalKeyboardKey.space);
        await tester.pump();
        expect(calls, ['b']);
      });

      testWidgets('in RTL, Arrow Down still moves to the next row', (
        tester,
      ) async {
        await tester.pumpWidget(
          _host(
            _between(_part(), before, after),
            textScale: 2,
            direction: TextDirection.rtl,
          ),
        );
        await tester.pump();
        await _tab(tester);
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
        await tester.pump();
        expect(_focusedLabel(_labels), 'Bravo');
        // Left and Right still follow the reading direction when stacked.
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
        await tester.pump();
        expect(_focusedLabel(_labels), 'Charlie');
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
        await tester.pump();
        expect(_focusedLabel(_labels), 'Bravo');
      });
    });

    testWidgets('under reduced motion one pump() settles after a row tap', (
      tester,
    ) async {
      final calls = <String>[];
      await tester.pumpWidget(
        _host(_Chooser(calls: calls), reduced: true, textScale: 2),
      );
      await tester.tap(find.text('Bravo'));
      await tester.pump();
      expect(calls, ['b']);
      expect(tester.hasRunningAnimations, isFalse);
      expect(
        tester
            .widget<KitChoiceRow<String>>(
              find.byWidgetPredicate(
                (w) => w is KitChoiceRow && w.choice.value == 'b',
              ),
            )
            .selected,
        isTrue,
      );
    });
  });

  group('sizing and overflow (LAY-9, G6)', () {
    // LAY-4's overflow sizes: widths 320–1600 dp, plus 915×412 landscape.
    const sizes = [
      Size(320, 900),
      Size(360, 900),
      Size(412, 915),
      Size(600, 900),
      Size(800, 900),
      Size(840, 900),
      Size(1280, 800),
      Size(1600, 1000),
      Size(915, 412),
    ];
    final scenes = <String, KitSegmented<String> Function()>{
      'default': () => _part(),
      'with counts': () => _part(selected: 'b', countB: 2),
      'segment disabled': () => _part(cDisabled: true),
      'disabled': () => KitSegmented<String>(
        segments: _segments(),
        selected: 'a',
        onChanged: null,
        semanticsLabel: 'Choice',
        disabledReason: 'Set by your admin',
      ),
    };

    for (final MapEntry(key: scene, value: build) in scenes.entries) {
      for (final scale in [1.0, 1.3, 2.0]) {
        for (final direction in TextDirection.values) {
          testWidgets('$scene · text $scale · ${direction.name}: no overflow, '
              '48 dp targets', (tester) async {
            final semantics = tester.ensureSemantics();
            addTearDown(tester.view.reset);
            tester.view.devicePixelRatio = 1;
            for (final size in sizes) {
              tester.view.physicalSize = size;
              await tester.pumpWidget(
                _host(build(), direction: direction, textScale: scale),
              );
              expect(tester.takeException(), isNull, reason: '$size');
              expect(
                tester.getSize(find.byType(KitSegmented<String>)).height,
                greaterThanOrEqualTo(48),
                reason: '$size',
              );
              for (final label in _labels) {
                // In the stacked form a count joins the row's title.
                final target = tester
                    .getSemantics(find.textContaining(label))
                    .rect;
                expect(target.height, greaterThanOrEqualTo(48));
                expect(target.width, greaterThanOrEqualTo(48));
              }
            }
            semantics.dispose();
          });
        }
      }
    }
  });

  group('tall', () {
    Widget tall(String selected, ValueChanged<String> onChanged) =>
        KitSegmented<String>(
          semanticsLabel: 'Light or dark',
          tall: true,
          selected: selected,
          segments: const [
            KitSegment(
              value: 'auto',
              label: 'Auto',
              icon: Icons.brightness_auto,
            ),
            KitSegment(value: 'light', label: 'Light', icon: Icons.light_mode),
            KitSegment(value: 'dark', label: 'Dark', icon: Icons.dark_mode),
            KitSegment(value: 'saver', label: 'Saver', icon: Icons.eco),
          ],
          onChanged: onChanged,
        );

    testWidgets('each segment is its icon over its word; a tap chooses', (
      tester,
    ) async {
      final chosen = <String>[];
      await tester.pumpWidget(_host(tall('dark', chosen.add)));
      for (final word in ['Auto', 'Light', 'Dark', 'Saver']) {
        expect(find.text(word), findsOneWidget);
      }
      expect(
        tester.getTopLeft(find.byIcon(Icons.eco)).dy,
        lessThan(tester.getTopLeft(find.text('Saver')).dy),
      );
      // No check: the filled block marks the choice.
      expect(find.byIcon(AppIconography.check), findsNothing);
      await tester.tap(find.text('Saver'));
      expect(chosen, ['saver']);
    });

    testWidgets('stacks at 2x text with nothing cut', (tester) async {
      await tester.pumpWidget(_host(tall('dark', (_) {}), textScale: 2));
      expect(tester.takeException(), isNull);
      expect(find.byType(KitChoiceRow<String>), findsNWidgets(4));
    });
  });
}
