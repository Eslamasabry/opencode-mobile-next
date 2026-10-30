// KitTappable (docs/ux-system/kit-api/KitTappable.md): the frozen "Tests
// required" contract (12 items), the debug asserts (STATE-8, onLongPress vs
// menu), the reduced-motion sample (G8x, MOT-7) via kitMotionStillTests, and
// no HapticFeedback (MOT-11).
import 'dart:ui' as ui;
import 'dart:ui' show Tristate;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_tappable.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_harness.dart';
import 'kit_motion_still.dart';

const _shotKey = ValueKey('shot');

/// Pumps [child] as a screen's body at [size] (DPR 1, so logical and
/// physical pixels line up for [WidgetTester.tapAt] and the pixel probe),
/// wrapped in a [RepaintBoundary] so [_Shot.take] can read back what was
/// actually painted (the same technique as `kit_chip_test.dart`).
Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  bool light = false,
  Size size = const Size(412, 915),
  // The pixel probe (test 7) needs a real surface behind the tappable: it
  // paints no fill itself when idle (the host is assumed to already paint
  // the surface it sits on), so idle pixels are otherwise the Scaffold's
  // plain ground colour, not `surface`.
  Color? background,
  // These tests probe the flat pressed fill, which a platform without the
  // Android ripple shows (owner rule 2026-10-26: on Android a press draws
  // the native ripple instead; test/kit/kit_ink_layer_test.dart covers it).
  bool flat = true,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  // The background must sit INSIDE the RepaintBoundary: `toImage()` only
  // captures what paints within its own bounds, so a background painted by
  // an ancestor of the boundary would never show up in the probe.
  final content = background == null
      ? child
      : ColoredBox(color: background, child: child);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: flat
          ? (light ? AppTheme.light() : AppTheme.dark()).copyWith(
              platform: TargetPlatform.fuchsia,
            )
          : (light ? AppTheme.light() : AppTheme.dark()),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: Center(
          child: RepaintBoundary(key: _shotKey, child: content),
        ),
      ),
    ),
  );
}

/// The role colour [surface] resolves to in [light]'s theme, for the pixel
/// probe's background (see [_pump]'s `background`).
Color _surfaceColor(bool light, KitSurfaceLevel surface) => KitTokens.fallback(
  light ? AppTheme.light() : AppTheme.dark(),
).fillOf(surface);

/// What the window actually shows, read back as pixels (kit_chip_test.dart's
/// `_Shot`, copied here since tests only reuse shared, not sibling, files).
class _Shot {
  _Shot(this.origin, this.width, this.bytes);

  final Offset origin;
  final int width;
  final ByteData bytes;

  static Future<_Shot> take(WidgetTester tester) async {
    final boundary = tester.renderObject<RenderRepaintBoundary>(
      find.byKey(_shotKey),
    );
    final origin = tester.getTopLeft(find.byKey(_shotKey));
    final data = await tester.runAsync(() async {
      final image = await boundary.toImage();
      final bytes = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
      final width = image.width;
      image.dispose();
      return (bytes!, width);
    });
    return _Shot(origin, data!.$2, data.$1);
  }

  Color at(Offset global) {
    final p = global - origin;
    final i = (p.dy.floor() * width + p.dx.floor()) * 4;
    return Color.fromARGB(
      bytes.getUint8(i + 3),
      bytes.getUint8(i),
      bytes.getUint8(i + 1),
      bytes.getUint8(i + 2),
    );
  }
}

bool _near(Color a, Color b) =>
    ((a.r - b.r) * 255).abs() <= 2 &&
    ((a.g - b.g) * 255).abs() <= 2 &&
    ((a.b - b.b) * 255).abs() <= 2;

/// KitTappable's node in the semantics tree (what a screen reader sees).
SemanticsNode _node(WidgetTester tester) =>
    tester.getSemantics(find.byType(KitTappable));

/// The custom actions published on [node], by label, as action ids (hint
/// overrides, which travel in the same list, left out).
Map<String, int> _customActionIds(SemanticsNode node) => {
  for (final id in node.getSemanticsData().customSemanticsActionIds ?? [])
    if (CustomSemanticsAction.getAction(id)!.action == null)
      CustomSemanticsAction.getAction(id)!.label ?? '': id,
};

/// The hint a screen reader speaks for [node]'s long-press action, if it is
/// overridden (e.g. "Double-tap and hold to Show actions").
String? _longPressHint(SemanticsNode node) {
  for (final id in node.getSemanticsData().customSemanticsActionIds ?? []) {
    final action = CustomSemanticsAction.getAction(id)!;
    if (action.action == SemanticsAction.longPress) return action.hint;
  }
  return null;
}

/// Performs [action] on [node] the way the platform's screen reader does:
/// through the semantics owner, not through a widget's callback.
Future<void> _perform(
  WidgetTester tester,
  SemanticsNode node,
  SemanticsAction action, [
  Object? arguments,
]) async {
  node.owner!.performAction(node.id, action, arguments);
  await tester.pumpAndSettle();
}

/// The cursor the mouse at device 1 (the first mouse [TestGesture]) shows.
MouseCursor _cursor() =>
    RendererBinding.instance.mouseTracker.debugDeviceActiveCursor(1)!;

/// The focus ring is the only [CustomPaint] KitTappable ever builds.
Finder get _ring => find.descendant(
  of: find.byType(KitTappable),
  matching: find.byType(CustomPaint),
);

/// A child with state of its own, to prove KitTappable never remounts it.
class _Counted extends StatefulWidget {
  const _Counted({required this.onInit});

  final VoidCallback onInit;

  @override
  State<_Counted> createState() => _CountedState();
}

class _CountedState extends State<_Counted> {
  @override
  void initState() {
    super.initState();
    widget.onInit();
  }

  @override
  Widget build(BuildContext context) => const Text('Row');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('disabled requires disabledReason (STATE-8, debug assert)', () {
    expect(
      () => KitTappable(onTap: null, child: const Text('Archive')),
      throwsAssertionError,
    );
    expect(
      () => KitTappable(
        onTap: null,
        disabledReason: 'Needs a connection',
        child: const Text('Archive'),
      ),
      returnsNormally,
    );
  });

  test('onLongPress cannot combine with a non-empty menu', () {
    expect(
      () => KitTappable(
        onTap: () {},
        onLongPress: () {},
        menu: [KitMenuItem(label: 'Archive', onSelected: () {})],
        child: const Text('Row'),
      ),
      throwsAssertionError,
    );
    expect(
      () => KitTappable(
        onTap: () {},
        onLongPress: () {},
        child: const Text('Row'),
      ),
      returnsNormally,
    );
  });

  testWidgets('1. a 20×20 child gets a 48×48 hit area', (tester) async {
    var taps = 0;
    await _pump(
      tester,
      KitTappable(
        onTap: () => taps++,
        child: const SizedBox(width: 20, height: 20),
      ),
    );
    final box = tester.getRect(find.byType(KitTappable));
    expect(box.width, 48);
    expect(box.height, 48);
    // Inside the 48 box (0–48), outside the centred 20×20 child (14–34).
    await tester.tapAt(box.topLeft + const Offset(2, 2));
    await tester.pump();
    expect(taps, 1);
  });

  testWidgets('2. Enter and Space fire once; key repeat does not refire', (
    tester,
  ) async {
    var taps = 0;
    final focusNode = FocusNode(debugLabel: 'under test');
    addTearDown(focusNode.dispose);
    await _pump(
      tester,
      KitTappable(
        onTap: () => taps++,
        focusNode: focusNode,
        child: const Text('Go'),
      ),
    );
    focusNode.requestFocus();
    await tester.pump();

    await tester.sendKeyDownEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.enter);
    expect(taps, 1, reason: 'Enter fires once');

    await tester.sendKeyDownEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyRepeatEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyRepeatEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.enter);
    expect(taps, 2, reason: 'a held Enter (key repeat) does not fire again');

    await tester.sendKeyDownEvent(LogicalKeyboardKey.space);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.space);
    expect(taps, 3, reason: 'Space fires once');
  });

  testWidgets('3. disabled: no Tab stop, no hover fill, basic cursor', (
    tester,
  ) async {
    // Disposed inline, not via addTearDown: the end-of-test leak check runs
    // before addTearDown callbacks.
    final handle = tester.ensureSemantics();
    final focusNode = FocusNode(debugLabel: 'under test');
    addTearDown(focusNode.dispose);
    await _pump(
      tester,
      KitTappable(
        onTap: null,
        disabledReason: 'Needs a connection',
        focusNode: focusNode,
        child: const Text('Delete'),
      ),
      background: _surfaceColor(false, KitSurfaceLevel.surface1),
    );

    final semantics = tester.getSemantics(find.byType(KitTappable));
    expect(semantics.flagsCollection.isEnabled, Tristate.isFalse);
    expect(semantics.hint, 'Needs a connection');

    expect(focusNode.canRequestFocus, isFalse);
    focusNode.requestFocus();
    await tester.pump();
    expect(focusNode.hasFocus, isFalse);

    // A fine pointer over it: the basic cursor and no hover fill.
    final tokens = KitTokens.of(tester.element(find.byType(KitTappable)));
    final rect = tester.getRect(find.byType(KitTappable));
    final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await mouse.addPointer(location: Offset.zero);
    addTearDown(mouse.removePointer);
    await mouse.moveTo(rect.center);
    await tester.pumpAndSettle();
    expect(_cursor(), SystemMouseCursors.basic);
    final shot = await _Shot.take(tester);
    expect(
      _near(
        shot.at(rect.topLeft + const Offset(4, 4)),
        tokens.fillOf(KitSurfaceLevel.surface1),
      ),
      isTrue,
      reason: 'no hover fill: the surface it sits on shows through',
    );
    handle.dispose();
  });

  group('4. menu: right-click, long-press, Shift+F10, onLongPress hook', () {
    testWidgets('right-click opens the menu at the click point', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      addTearDown(() => debugPlatformCapabilities = null);
      var selected = 0;
      await _pump(
        tester,
        KitTappable(
          onTap: () {},
          menu: [KitMenuItem(label: 'Archive', onSelected: () => selected++)],
          child: const Text('Row'),
        ),
        size: const Size(1280, 800),
      );
      final point = tester.getCenter(find.byType(KitTappable));
      await tester.tap(
        find.byType(KitTappable),
        buttons: kSecondaryMouseButton,
        kind: PointerDeviceKind.mouse,
      );
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      expect(tester.getTopLeft(find.byType(KitMenuPanel)), point);

      await tester.tap(find.text('Archive'));
      await tester.pumpAndSettle();
      expect(selected, 1);
    });

    testWidgets('long-press opens the same items at the press point', (
      tester,
    ) async {
      var selected = 0;
      await _pump(
        tester,
        KitTappable(
          onTap: () {},
          menu: [KitMenuItem(label: 'Archive', onSelected: () => selected++)],
          child: const Text('Row'),
        ),
        size: const Size(1280, 800),
      );
      final point = tester.getCenter(find.byType(KitTappable));
      await tester.longPressAt(point);
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      expect(tester.getTopLeft(find.byType(KitMenuPanel)), point);
      await tester.tap(find.text('Archive'));
      await tester.pumpAndSettle();
      expect(selected, 1);
    });

    testWidgets('Shift+F10 opens it anchored (position: null)', (tester) async {
      final focusNode = FocusNode(debugLabel: 'under test');
      addTearDown(focusNode.dispose);
      await _pump(
        tester,
        KitTappable(
          onTap: () {},
          focusNode: focusNode,
          menu: [KitMenuItem(label: 'Archive', onSelected: () {})],
          child: const Text('Row'),
        ),
      );
      focusNode.requestFocus();
      await tester.pump();
      await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.f10);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      _expectAnchored(tester);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
    });

    testWidgets('the Menu key also opens it anchored', (tester) async {
      final focusNode = FocusNode(debugLabel: 'under test');
      addTearDown(focusNode.dispose);
      await _pump(
        tester,
        KitTappable(
          onTap: () {},
          focusNode: focusNode,
          menu: [KitMenuItem(label: 'Archive', onSelected: () {})],
          child: const Text('Row'),
        ),
      );
      focusNode.requestFocus();
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.contextMenu);
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      _expectAnchored(tester);
    });

    testWidgets('keys from a focused child belong to the child, not the row', (
      tester,
    ) async {
      var rowTaps = 0;
      var childPresses = 0;
      final childFocus = FocusNode(debugLabel: 'nested button');
      addTearDown(childFocus.dispose);
      await _pump(
        tester,
        KitTappable(
          onTap: () => rowTaps++,
          menu: [KitMenuItem(label: 'Archive', onSelected: () {})],
          child: Padding(
            padding: const EdgeInsets.all(8),
            child: TextButton(
              focusNode: childFocus,
              onPressed: () => childPresses++,
              child: const Text('Retry'),
            ),
          ),
        ),
      );
      childFocus.requestFocus();
      await tester.pump();

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(childPresses, 1, reason: "Enter reaches the child's action");
      expect(rowTaps, 0, reason: "the row's onTap does not fire");

      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pumpAndSettle();
      expect(childPresses, 2, reason: "Space reaches the child's action");
      expect(rowTaps, 0);

      await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.f10);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
      await tester.pumpAndSettle();
      expect(
        find.byType(KitMenuPanel),
        findsNothing,
        reason: 'Shift+F10 inside a child does not open the row menu',
      );
    });

    testWidgets('onLongPress: a long-press calls it and opens no menu', (
      tester,
    ) async {
      var hookCalls = 0;
      await _pump(
        tester,
        KitTappable(
          onTap: () {},
          onLongPress: () => hookCalls++,
          child: const Text('Row'),
        ),
      );
      await tester.longPress(find.byType(KitTappable));
      await tester.pumpAndSettle();
      expect(hookCalls, 1);
      expect(find.byType(KitMenuPanel), findsNothing);
    });
  });

  group('5. screen reader: the menu as semantic actions', () {
    testWidgets('each enabled item is a custom action that runs it', (
      tester,
    ) async {
      // Disposed inline, not via addTearDown: the end-of-test leak check
      // runs before addTearDown callbacks.
      final handle = tester.ensureSemantics();
      var archived = 0;
      await _pump(
        tester,
        KitTappable(
          onTap: () {},
          menu: [
            KitMenuItem(label: 'Rename', onSelected: () {}),
            KitMenuItem(label: 'Archive', onSelected: () => archived++),
          ],
          child: const Text('Row'),
        ),
      );
      final node = _node(tester);
      final ids = _customActionIds(node);
      expect(ids.keys, unorderedEquals(['Rename', 'Archive']));
      await _perform(
        tester,
        node,
        SemanticsAction.customAction,
        ids['Archive'],
      );
      expect(archived, 1);
      expect(find.byType(KitMenuPanel), findsNothing);
      handle.dispose();
    });

    testWidgets('the long-press action is "Show actions" and opens the menu', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        KitTappable(
          onTap: () {},
          menu: [KitMenuItem(label: 'Archive', onSelected: () {})],
          child: const Text('Row'),
        ),
      );
      final node = _node(tester);
      expect(
        node.getSemanticsData().hasAction(SemanticsAction.longPress),
        isTrue,
      );
      expect(_longPressHint(node), 'Show actions');
      expect(_customActionIds(node).keys, ['Archive']);
      await _perform(tester, node, SemanticsAction.longPress);
      expect(find.byType(KitMenuPanel), findsOneWidget);
      _expectAnchored(tester);
      handle.dispose();
    });

    testWidgets('disabled with a menu: no custom actions, no long-press', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        KitTappable(
          onTap: null,
          disabledReason: 'Needs a connection',
          menu: [KitMenuItem(label: 'Archive', onSelected: () {})],
          child: const Text('Row'),
        ),
      );
      final node = _node(tester);
      final data = node.getSemanticsData();
      expect(
        data.customSemanticsActionIds ?? const <int>[],
        isEmpty,
        reason: 'no menu items and no "Show actions" hint',
      );
      expect(data.hasAction(SemanticsAction.longPress), isFalse);
      expect(_longPressHint(node), isNull);
      handle.dispose();
    });
  });

  testWidgets('6. empty menu: long-press shows the tooltip, opens nothing', (
    tester,
  ) async {
    await _pump(
      tester,
      KitTappable(onTap: () {}, tooltip: 'More info', child: const Text('Row')),
    );
    await tester.longPress(find.byType(KitTappable));
    await tester.pump();
    expect(find.byType(KitMenuPanel), findsNothing);
    expect(find.text('More info'), findsOneWidget);
  });

  group('7. hover and pressed fills come from surface steps', () {
    Future<void> probe(
      WidgetTester tester, {
      required bool light,
      required KitSurfaceLevel surface,
      required KitSurfaceLevel expectHover,
      required KitSurfaceLevel expectPressed,
    }) async {
      await _pump(
        tester,
        KitTappable(onTap: () {}, surface: surface, child: const Text('Row')),
        light: light,
        background: _surfaceColor(light, surface),
      );
      final tokens = KitTokens.of(tester.element(find.byType(KitTappable)));
      final rect = tester.getRect(find.byType(KitTappable));
      final probePoint = rect.topLeft + const Offset(4, 4);

      var shot = await _Shot.take(tester);
      expect(
        _near(shot.at(probePoint), tokens.fillOf(surface)),
        isTrue,
        reason: 'no hover: the fill matches the surface it sits on',
      );

      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await mouse.addPointer(location: Offset.zero);
      addTearDown(() => mouse.removePointer());
      await mouse.moveTo(rect.center);
      await tester.pumpAndSettle();
      expect(_cursor(), SystemMouseCursors.click, reason: 'fine pointer');
      shot = await _Shot.take(tester);
      expect(_near(shot.at(probePoint), tokens.fillOf(expectHover)), isTrue);

      await mouse.down(rect.center);
      await tester.pumpAndSettle();
      shot = await _Shot.take(tester);
      expect(_near(shot.at(probePoint), tokens.fillOf(expectPressed)), isTrue);
      await mouse.up();
    }

    testWidgets('surface1: hover surface2 in dark, pressed surface3', (
      tester,
    ) async {
      await probe(
        tester,
        light: false,
        surface: KitSurfaceLevel.surface1,
        expectHover: KitSurfaceLevel.surface2,
        expectPressed: KitSurfaceLevel.surface3,
      );
    });

    testWidgets('surface1: hover surface2 and pressed surface3 in light too', (
      tester,
    ) async {
      await probe(
        tester,
        light: true,
        surface: KitSurfaceLevel.surface1,
        expectHover: KitSurfaceLevel.surface2,
        expectPressed: KitSurfaceLevel.surface3,
      );
    });

    testWidgets('surface3: hover and pressed both step down to surface2', (
      tester,
    ) async {
      await probe(
        tester,
        light: false,
        surface: KitSurfaceLevel.surface3,
        expectHover: KitSurfaceLevel.surface2,
        expectPressed: KitSurfaceLevel.surface2,
      );
    });

    testWidgets('touch (no mouse connected): no hover paint', (tester) async {
      await _pump(
        tester,
        const KitTappable(onTap: _noop, child: Text('Row')),
        background: _surfaceColor(false, KitSurfaceLevel.surface1),
      );
      final tokens = KitTokens.of(tester.element(find.byType(KitTappable)));
      final rect = tester.getRect(find.byType(KitTappable));
      await tester.tapAt(rect.center);
      await tester.pumpAndSettle();
      final shot = await _Shot.take(tester);
      expect(
        _near(
          shot.at(rect.topLeft + const Offset(4, 4)),
          tokens.fillOf(KitSurfaceLevel.surface1),
        ),
        isTrue,
      );
    });
  });

  testWidgets('8. keyboard focus shows the ring; a tap does not', (
    tester,
  ) async {
    final focusNode = FocusNode(debugLabel: 'under test');
    addTearDown(focusNode.dispose);
    await _pump(
      tester,
      KitTappable(
        onTap: () {},
        focusNode: focusNode,
        child: const SizedBox(width: 20, height: 20),
      ),
      background: _surfaceColor(false, KitSurfaceLevel.surface1),
    );
    expect(_ring, findsNothing);

    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(focusNode.hasFocus, isTrue, reason: 'Tab moved focus onto it');
    // DPR 1 here, so 2 physical px is 2 logical px: the two outermost
    // columns on each side are accent, the third is the surface again.
    final tokens = KitTokens.of(tester.element(find.byType(KitTappable)));
    final accent = tokens.roles.accent;
    final surface = tokens.fillOf(KitSurfaceLevel.surface1);
    final rect = tester.getRect(find.byType(KitTappable));
    final shot = await _Shot.take(tester);
    Color column(double dx) => shot.at(Offset(rect.left + dx, rect.center.dy));
    expect(_near(column(0), accent), isTrue, reason: 'ring, start edge');
    expect(_near(column(1), accent), isTrue, reason: 'ring, 2nd px');
    expect(_near(column(2), surface), isTrue, reason: 'exactly 2 px wide');
    expect(_near(column(rect.width - 1), accent), isTrue, reason: 'end edge');
    expect(_near(column(rect.width - 2), accent), isTrue);
    expect(_near(column(rect.width - 3), surface), isTrue);

    focusNode.unfocus();
    // FocusNode's own change notification can lag a frame behind unfocus()
    // (FocusNode.unfocus doc: "may take a frame to update").
    await tester.pump();
    await tester.pump();
    expect(_ring, findsNothing);

    await tester.tap(find.byType(KitTappable));
    await tester.pump();
    expect(focusNode.hasFocus, isFalse, reason: 'a tap never requests focus');
    expect(_ring, findsNothing);
  });

  testWidgets('9. the tooltip shows on hover and on keyboard focus', (
    tester,
  ) async {
    final focusNode = FocusNode(debugLabel: 'under test');
    addTearDown(focusNode.dispose);
    await _pump(
      tester,
      KitTappable(
        onTap: () {},
        tooltip: 'Retry',
        shortcut: 'Ctrl+R',
        focusNode: focusNode,
        child: const Text('Row'),
      ),
    );

    final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await mouse.addPointer(location: Offset.zero);
    addTearDown(() => mouse.removePointer());
    await mouse.moveTo(tester.getCenter(find.byType(KitTappable)));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Retry  Ctrl+R'), findsOneWidget);
    await mouse.moveTo(Offset.zero);
    await tester.pump(const Duration(seconds: 1));

    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(focusNode.hasFocus, isTrue);
    await tester.pump();
    expect(find.text('Retry  Ctrl+R'), findsOneWidget);
  });

  testWidgets('10. a tap is silent; a long-press ticks once (MOT-11)', (
    tester,
  ) async {
    final calls = recordHaptics(tester);
    var taps = 0;
    await _pump(
      tester,
      KitTappable(
        onTap: () => taps++,
        onLongPress: () {},
        child: const Text('Row'),
      ),
    );
    await tester.tap(find.byType(KitTappable));
    await tester.pump();
    expect(calls, isEmpty, reason: 'taps are silent');
    await tester.longPress(find.byType(KitTappable));
    await tester.pumpAndSettle();
    expect(taps, 1);
    // The platform's long-press tick (owner rule 2026-10-26, native feel).
    expect(calls, ['null']);
  });

  testWidgets('the child keeps its state across Effects Off and tooltip', (
    tester,
  ) async {
    var inits = 0;
    final config = ValueNotifier<(bool, String?)>((false, null));
    addTearDown(config.dispose);
    await _pump(
      tester,
      ValueListenableBuilder<(bool, String?)>(
        valueListenable: config,
        builder: (context, value, _) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: value.$1),
          child: KitTappable(
            onTap: () {},
            tooltip: value.$2,
            child: _Counted(onInit: () => inits++),
          ),
        ),
      ),
    );
    expect(inits, 1);
    config.value = (true, null); // Effects Off
    await tester.pumpAndSettle();
    config.value = (true, 'More info'); // a tooltip appears
    await tester.pumpAndSettle();
    config.value = (false, 'More info'); // Effects back on
    await tester.pumpAndSettle();
    config.value = (false, null); // the tooltip goes away
    await tester.pumpAndSettle();
    expect(inits, 1, reason: 'the child subtree was never remounted');
  });

  testWidgets('12. selected exposes isSelected in semantics', (tester) async {
    // Disposed inline, not via addTearDown: the end-of-test leak check runs
    // before addTearDown callbacks.
    final handle = tester.ensureSemantics();
    await _pump(
      tester,
      KitTappable(onTap: () {}, selected: true, child: const Text('Row')),
    );
    final semantics = tester.getSemantics(find.byType(KitTappable));
    expect(semantics.flagsCollection.isSelected, Tristate.isTrue);
    handle.dispose();
  });

  group('13. a tap shows on the very next frame (KitPressTracker)', () {
    const idle = KitSurfaceLevel.surface1;
    const pressed = KitSurfaceLevel.surface3; // dark: hover surface2, then 3

    /// The fill at the tappable's top-start corner, as painted.
    Future<Color> fillAt(WidgetTester tester, Finder target) async {
      final shot = await _Shot.take(tester);
      return shot.at(tester.getRect(target).topLeft + const Offset(4, 4));
    }

    Future<bool> showsPressed(WidgetTester tester, [Finder? target]) async {
      final tokens = KitTokens.of(tester.element(find.byType(KitTappable)));
      final at = target ?? find.byType(KitTappable);
      return _near(await fillAt(tester, at), tokens.fillOf(pressed));
    }

    Future<bool> showsIdle(WidgetTester tester) async {
      final tokens = KitTokens.of(tester.element(find.byType(KitTappable)));
      return _near(
        await fillAt(tester, find.byType(KitTappable)),
        tokens.fillOf(idle),
      );
    }

    /// A row at the top of a list that scrolls, so a touch on it could
    /// still become a scroll.
    Widget inScroll(Widget row) => SizedBox(
      width: 300,
      height: 300,
      child: ListView(children: [row, const SizedBox(height: 1000)]),
    );

    testWidgets('pointer-down paints the pressed fill on the first frame', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitTappable(onTap: _noop, child: Text('Row')),
        background: _surfaceColor(false, idle),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(KitTappable)),
      );
      await tester.pump();
      expect(await showsPressed(tester), isTrue, reason: 'no 100 ms wait');
      await gesture.up();
      await tester.pumpAndSettle();
      expect(await showsIdle(tester), isTrue);
    });

    testWidgets('a quick tap (down and up in one frame) still shows it', (
      tester,
    ) async {
      var taps = 0;
      await _pump(
        tester,
        KitTappable(onTap: () => taps++, child: const Text('Row')),
        background: _surfaceColor(false, idle),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(KitTappable)),
      );
      await gesture.up();
      await tester.pump();
      expect(taps, 1);
      expect(await showsPressed(tester), isTrue, reason: 'held after up');
      expect(
        tester.hasRunningAnimations,
        isFalse,
        reason: 'the press appears at once, no ticker (one pump settles)',
      );
      await tester.pump(KitMotion.pressHold);
      await tester.pumpAndSettle();
      expect(await showsIdle(tester), isTrue, reason: 'clears after the hold');
    });

    testWidgets('in a scroll view a quick tap shows it on release', (
      tester,
    ) async {
      var taps = 0;
      await _pump(
        tester,
        inScroll(KitTappable(onTap: () => taps++, child: const Text('Row'))),
        background: _surfaceColor(false, idle),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(KitTappable)),
      );
      await tester.pump();
      expect(
        await showsIdle(tester),
        isTrue,
        reason: 'a touch that may become a scroll waits kPressTimeout',
      );
      await gesture.up();
      await tester.pump();
      expect(taps, 1);
      expect(await showsPressed(tester), isTrue);
      await tester.pump(KitMotion.pressHold);
      await tester.pumpAndSettle();
      expect(await showsIdle(tester), isTrue);
    });

    testWidgets('in a scroll view a held touch shows it after kPressTimeout', (
      tester,
    ) async {
      await _pump(
        tester,
        inScroll(const KitTappable(onTap: _noop, child: Text('Row'))),
        background: _surfaceColor(false, idle),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(KitTappable)),
      );
      await tester.pump(kPressTimeout);
      expect(await showsPressed(tester), isTrue);
      await gesture.up();
      await tester.pumpAndSettle();
      expect(await showsIdle(tester), isTrue);
    });

    testWidgets('a drag that starts a scroll cancels it at once', (
      tester,
    ) async {
      var taps = 0;
      await _pump(
        tester,
        inScroll(KitTappable(onTap: () => taps++, child: const Text('Row'))),
        background: _surfaceColor(false, idle),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(KitTappable)),
      );
      await tester.pump(kPressTimeout);
      expect(await showsPressed(tester), isTrue);
      await gesture.moveBy(const Offset(0, -40));
      await tester.pump();
      await gesture.up();
      await tester.pumpAndSettle();
      expect(taps, 0);
      final tokens = KitTokens.of(tester.element(find.byType(KitTappable)));
      expect(
        _near(
          await fillAt(tester, find.byType(KitTappable)),
          tokens.fillOf(pressed),
        ),
        isFalse,
        reason: 'the scroll took the gesture; no press is left behind',
      );
    });

    testWidgets('a drag off a still row cancels it with no hold', (
      tester,
    ) async {
      var taps = 0;
      await _pump(
        tester,
        KitTappable(onTap: () => taps++, child: const Text('Row')),
        background: _surfaceColor(false, idle),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(KitTappable)),
      );
      await tester.pump();
      expect(await showsPressed(tester), isTrue);
      await gesture.moveBy(const Offset(40, 0));
      await tester.pump();
      expect(tester.hasRunningAnimations, isTrue, reason: 'eases out');
      await tester.pumpAndSettle();
      expect(await showsIdle(tester), isTrue);
      await gesture.up();
      await tester.pumpAndSettle();
      expect(taps, 0);
    });

    testWidgets('reduced motion: in and out are both instant', (tester) async {
      await _pump(
        tester,
        const MediaQuery(
          data: MediaQueryData(disableAnimations: true),
          child: KitTappable(onTap: _noop, child: Text('Row')),
        ),
        background: _surfaceColor(false, idle),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(KitTappable)),
      );
      await gesture.up();
      await tester.pump();
      expect(await showsPressed(tester), isTrue);
      expect(tester.hasRunningAnimations, isFalse);
      await tester.pump(KitMotion.pressHold);
      expect(tester.hasRunningAnimations, isFalse);
      expect(await showsIdle(tester), isTrue);
    });

    testWidgets('a control inside the row that wins the tap: row stays idle', (
      tester,
    ) async {
      var rowTaps = 0;
      var buttonTaps = 0;
      await _pump(
        tester,
        KitTappable(
          onTap: () => rowTaps++,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Row'),
              const SizedBox(width: 80),
              KitIconButton(
                icon: Icons.close,
                tooltip: 'Remove',
                onPressed: () => buttonTaps++,
              ),
            ],
          ),
        ),
        background: _surfaceColor(false, idle),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(KitIconButton)),
      );
      await gesture.up();
      await tester.pump();
      expect((rowTaps, buttonTaps), (0, 1));
      expect(await showsIdle(tester), isTrue, reason: 'the row lost the tap');
    });

    testWidgets('Enter activates with no pointer press painted', (
      tester,
    ) async {
      var taps = 0;
      await _pump(
        tester,
        KitTappable(
          onTap: () => taps++,
          autofocus: true,
          child: const Text('Row'),
        ),
        background: _surfaceColor(false, idle),
      );
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(taps, 1);
      final tokens = KitTokens.of(tester.element(find.byType(KitTappable)));
      expect(
        _near(
          await fillAt(tester, find.byType(KitTappable)),
          tokens.fillOf(pressed),
        ),
        isFalse,
      );
    });

    testWidgets('KitIconButton: a quick tap shows its pressed fill', (
      tester,
    ) async {
      await _pump(
        tester,
        KitIconButton(icon: Icons.refresh, tooltip: 'Reload', onPressed: () {}),
        background: _surfaceColor(false, idle),
      );
      final tokens = KitTokens.of(tester.element(find.byType(KitIconButton)));
      final center = tester.getCenter(find.byType(KitIconButton));
      // Beside the glyph, inside the 48 dp circle.
      final probe = center + const Offset(-16, 0);
      Future<Color> fill() async => (await _Shot.take(tester)).at(probe);
      expect(_near(await fill(), tokens.roles.surface1), isTrue);
      final gesture = await tester.startGesture(center);
      await gesture.up();
      await tester.pump();
      expect(_near(await fill(), tokens.roles.surface3), isTrue);
      await tester.pump(KitMotion.pressHold);
      await tester.pumpAndSettle();
      expect(_near(await fill(), tokens.roles.surface1), isTrue);
    });

    testWidgets('KitButton: pointer-down shows its pressed layer at once', (
      tester,
    ) async {
      await _pump(
        tester,
        KitButton.primary(label: 'Connect', onPressed: () {}, expand: false),
        background: _surfaceColor(false, idle),
      );
      final tokens = KitTokens.of(tester.element(find.byType(KitButton)));
      final roles = tokens.roles;
      final rect = tester.getRect(find.byType(FilledButton));
      // Clear of the label and the rounded corner.
      final probe = Offset(rect.left + 12, rect.center.dy);
      Future<Color> fill() async => (await _Shot.take(tester)).at(probe);
      expect(_near(await fill(), roles.accent), isTrue);
      final pressedFill = Color.alphaBlend(
        roles.onAccent.withValues(alpha: 0.16),
        roles.accent,
      );
      final gesture = await tester.startGesture(rect.center);
      await tester.pump();
      expect(_near(await fill(), pressedFill), isTrue, reason: 'first frame');
      await gesture.up();
      await tester.pump(KitMotion.pressHold);
      await tester.pumpAndSettle();
      expect(_near(await fill(), roles.accent), isTrue);

      // A quick tap also shows it.
      final quick = await tester.startGesture(rect.center);
      await quick.up();
      await tester.pump();
      expect(_near(await fill(), pressedFill), isTrue, reason: 'quick tap');
      await tester.pump(KitMotion.pressHold);
      await tester.pumpAndSettle();
    });
  });

  // 11 (reduced motion settles after one pump with no ticker) is the
  // kitMotionStillTests registration below (G8x, MOT-7).
  kitMotionStillTests(
    'KitTappable',
    builds: {
      'enabled': () => KitTappable(onTap: () {}, child: const Text('Row')),
      'disabled': () => KitTappable(
        onTap: null,
        disabledReason: 'Needs a connection',
        child: const Text('Row'),
      ),
    },
    changes: {
      'pressed then released': KitMotionChange(
        build: () => KitTappable(onTap: () {}, child: const Text('Row')),
        act: (tester, stage) async {
          await tester.tap(find.byType(KitTappable));
        },
        shows: 'Row',
      ),
    },
  );
}

void _noop() {}

/// A keyboard- or screen-reader-opened menu is anchored to the tappable
/// (`position: null`), not to a pointer: KitMenu's anchoring rule puts it
/// below the widget, aligned to its end edge (the right, in LTR).
void _expectAnchored(WidgetTester tester) {
  final own = tester.getRect(find.byType(KitTappable));
  final panel = tester.getRect(find.byType(KitMenuPanel));
  // KitMenu snaps its origin to a device pixel (DPR 1 here), so allow half
  // a pixel either way.
  expect(
    panel.top,
    closeTo(own.bottom, 0.5),
    reason: 'directly below the widget',
  );
  expect(
    panel.right,
    closeTo(own.right, 0.5),
    reason: "aligned to the widget's end edge",
  );
}
