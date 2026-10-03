// KitChip (docs/ux-system/kit-api/KitChip.md): the frozen "Tests required"
// contract, K2 §7 G9, the reduced-motion sample (G8x, MOT-7) and the
// keyboard behaviour (G14).
import 'dart:ui' as ui;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart' hide TextDirection;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_chip.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

/// Pumps [child] as a screen's body, with the real test window sized to
/// [size] (not just a MediaQuery override), so a widget's own render
/// position — and therefore [WidgetTester.tap] and [WidgetTester.getSize] —
/// line up with what a person would actually see.
Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Locale locale = const Locale('en'),
  double textScale = 1,
  bool light = false,
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: light ? AppTheme.light() : AppTheme.dark(),
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: widget!,
      ),
      home: Scaffold(
        body: Center(
          child: RepaintBoundary(key: _shotKey, child: child),
        ),
      ),
    ),
  );
}

/// Whether keyboard focus is on [target] itself or a descendant of it
/// (test/kit/kit_keyboard_test.dart's own helper, kept local so this file's
/// write set stays just its own tests).
bool _focusIn(WidgetTester tester, Finder target) {
  final focused = FocusManager.instance.primaryFocus?.context;
  if (focused == null) return false;
  final element = tester.element(target);
  if (focused == element) return true;
  var found = false;
  (focused as Element).visitAncestorElements((ancestor) {
    if (ancestor == element) {
      found = true;
      return false;
    }
    return true;
  });
  return found;
}

const _bodyKey = ValueKey('kit-chip-body');
const _checkKey = ValueKey('kit-chip-check');
const _removeKey = ValueKey('kit-chip-remove');
const _shotKey = ValueKey('shot');

/// The pill a chip draws: its filled stadium (the one [DecoratedBox] under
/// [chip] with a filled [ShapeDecoration]).
Finder _pillOf(Finder chip) => find.descendant(
  of: chip,
  matching: find.byWidgetPredicate(
    (w) =>
        w is DecoratedBox &&
        w.position == DecorationPosition.background &&
        w.decoration is ShapeDecoration &&
        (w.decoration as ShapeDecoration).color != null,
  ),
);

/// The keyboard focus ring (a foreground stadium outline), if one is drawn.
final _ring = find.byWidgetPredicate(
  (w) =>
      w is DecoratedBox &&
      w.position == DecorationPosition.foreground &&
      w.decoration is ShapeDecoration,
);

/// What the window actually shows, read back as pixels (logical = physical:
/// [_pump] runs at DPR 1).
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

  /// How many pixels inside [rect] are [color] (within rounding).
  int count(Rect rect, Color color) {
    var n = 0;
    for (var y = rect.top.ceil(); y < rect.bottom.floor(); y++) {
      for (var x = rect.left.ceil(); x < rect.right.floor(); x++) {
        if (_near(at(Offset(x + .5, y + .5)), color)) n++;
      }
    }
    return n;
  }
}

bool _near(Color a, Color b) =>
    ((a.r - b.r) * 255).abs() <= 2 &&
    ((a.g - b.g) * 255).abs() <= 2 &&
    ((a.b - b.b) * 255).abs() <= 2;

void main() {
  testWidgets('severity tones remain distinct and readable in both themes', (
    tester,
  ) async {
    for (final light in [false, true]) {
      final colors = <Color>[];
      for (final tone in KitChipTone.values.where(
        (tone) => tone != KitChipTone.active,
      )) {
        await _pump(
          tester,
          KitChip(label: 'Severity', tone: tone),
          light: light,
        );
        final text = tester.widget<Text>(find.text('Severity'));
        final ink = text.textSpan!.style!.color!;
        final context = tester.element(find.byType(KitChip));
        final fill = KitTokens.of(context).roles.surface3;
        expect(contrastRatio(ink, fill), greaterThanOrEqualTo(4.5));
        colors.add(ink);
      }
      expect(colors.toSet(), hasLength(3));
    }
  });

  testWidgets('the active tone is readable on its own accent tint', (
    tester,
  ) async {
    for (final light in [false, true]) {
      await _pump(
        tester,
        const KitChip(label: 'On', tone: KitChipTone.active),
        light: light,
      );
      final text = tester.widget<Text>(find.text('On'));
      final ink = text.textSpan!.style!.color!;
      final roles = KitTokens.of(tester.element(find.byType(KitChip))).roles;
      final tint = Color.alphaBlend(
        roles.accent.withValues(alpha: .22),
        roles.surface3,
      );
      expect(contrastRatio(ink, tint), greaterThanOrEqualTo(4.5));
    }
  });

  kitMotionStillTests(
    'KitChipWrap',
    builds: {
      'default': () => const KitChipWrap(
        children: [
          KitChip(label: 'main'),
          KitChip(label: '3 agents'),
        ],
      ),
    },
  );

  kitMotionStillTests(
    'KitChip',
    builds: {
      'plain': () => const KitChip(label: 'main'),
      'count': () => KitChip.count(label: 'Tasks', count: 3),
      'removable': () => KitChip.removable(label: 'file.txt', onRemove: () {}),
    },
    changes: {
      'action selects': KitMotionChange(
        build: () => KitChip.action(
          label: 'Open in terminal',
          onPressed: () {},
          selected: false,
        ),
        act: (tester, stage) => stage.rebuild(
          KitChip.action(
            label: 'Open in terminal',
            onPressed: () {},
            selected: true,
          ),
        ),
        shows: 'Open in terminal',
      ),
      'action deselects': KitMotionChange(
        build: () => KitChip.action(
          label: 'Open in terminal',
          onPressed: () {},
          selected: true,
        ),
        act: (tester, stage) => stage.rebuild(
          KitChip.action(
            label: 'Open in terminal',
            onPressed: () {},
            selected: false,
          ),
        ),
        shows: 'Open in terminal',
      ),
      'summary expands': KitMotionChange(
        build: () => KitChip.summary(
          label: 'Read 3 files · edited 1',
          onPressed: () {},
          expanded: false,
        ),
        act: (tester, stage) => stage.rebuild(
          KitChip.summary(
            label: 'Read 3 files · edited 1',
            onPressed: () {},
            expanded: true,
          ),
        ),
        shows: 'Read 3 files · edited 1',
      ),
    },
  );

  group('plain', () {
    testWidgets('is not a button, has no tap action, is not focusable', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(tester, const KitChip(label: 'main'));
      expect(
        tester.getSemantics(find.text('main')),
        isSemantics(
          label: 'main',
          isButton: false,
          hasTapAction: false,
          isFocusable: false,
        ),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_focusIn(tester, find.text('main')), isFalse);
      handle.dispose();
    });
  });

  group('action', () {
    testWidgets('tapping calls onPressed once', (tester) async {
      var taps = 0;
      await _pump(
        tester,
        KitChip.action(label: 'Open in terminal', onPressed: () => taps++),
      );
      // The overlay tap zone (Stack, on top of the visible label) is what
      // actually receives the tap; warnIfMissed would flag that
      // correct, deliberate layering as a mismatch.
      await tester.tap(find.text('Open in terminal'), warnIfMissed: false);
      await tester.pump();
      expect(taps, 1);
    });

    testWidgets('selected: true shows the check and toggled: true', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        KitChip.action(label: 'Ask first', onPressed: () {}, selected: true),
      );
      expect(find.byKey(_checkKey), findsOneWidget);
      expect(
        tester.getSemantics(find.text('Ask first')),
        isSemantics(
          label: 'Ask first',
          isButton: true,
          hasToggledState: true,
          isToggled: true,
        ),
      );
      handle.dispose();
    });

    testWidgets('selected: null has no toggled flag', (tester) async {
      final handle = tester.ensureSemantics();
      await _pump(tester, KitChip.action(label: 'Retry', onPressed: () {}));
      expect(find.byKey(_checkKey), findsNothing);
      expect(
        tester.getSemantics(find.text('Retry')),
        isSemantics(label: 'Retry', isButton: true, hasToggledState: false),
      );
      handle.dispose();
    });
  });

  group('removable', () {
    testWidgets('the x is a separate 48x48 target labelled "Remove {label}"', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        KitChip.removable(label: 'file.txt', onRemove: () {}),
      );
      final x = find.byKey(_removeKey);
      expect(x, findsOneWidget);
      final size = tester.getSize(x);
      expect(size.width, greaterThanOrEqualTo(48));
      expect(size.height, greaterThanOrEqualTo(48));
      expect(
        tester.getSemantics(x),
        isSemantics(label: 'Remove file.txt', isButton: true),
      );
      handle.dispose();
    });

    testWidgets('tapping the x calls onRemove only, not onPressed', (
      tester,
    ) async {
      var removed = 0;
      var pressed = 0;
      await _pump(
        tester,
        KitChip.removable(
          label: 'file.txt',
          onRemove: () => removed++,
          onPressed: () => pressed++,
        ),
      );
      await tester.tap(find.byKey(_removeKey));
      await tester.pump();
      expect(removed, 1);
      expect(pressed, 0);
    });

    testWidgets('tapping the body calls onPressed only, not onRemove', (
      tester,
    ) async {
      var removed = 0;
      var pressed = 0;
      await _pump(
        tester,
        KitChip.removable(
          label: 'file.txt',
          onRemove: () => removed++,
          onPressed: () => pressed++,
        ),
      );
      await tester.tap(find.byKey(_bodyKey));
      await tester.pump();
      expect(pressed, 1);
      expect(removed, 0);
    });

    testWidgets('Delete on the focused chip calls onRemove', (tester) async {
      var removed = 0;
      await _pump(
        tester,
        KitChip.removable(label: 'file.txt', onRemove: () => removed++),
      );
      // Only the x is focusable here (no onPressed), so one Tab reaches it.
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_focusIn(tester, find.byKey(_removeKey)), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.delete);
      await tester.pump();
      expect(removed, 1);
    });

    testWidgets('Backspace on the focused x also calls onRemove', (
      tester,
    ) async {
      var removed = 0;
      await _pump(
        tester,
        KitChip.removable(label: 'file.txt', onRemove: () => removed++),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.backspace);
      await tester.pump();
      expect(removed, 1);
    });
  });

  group('count', () {
    testWidgets('semantics label is "{label}, {count}"', (tester) async {
      final handle = tester.ensureSemantics();
      await _pump(tester, KitChip.count(label: 'Tasks', count: 3));
      expect(
        tester.getSemantics(find.textContaining('Tasks')),
        isSemantics(label: 'Tasks, 3'),
      );
      handle.dispose();
    });

    testWidgets('the number is formatted with intl for en and ar', (
      tester,
    ) async {
      const count = 1234;
      final en = NumberFormat.decimalPattern('en').format(count);
      final ar = NumberFormat.decimalPattern('ar').format(count);
      await _pump(tester, KitChip.count(label: 'Tasks', count: count));
      expect(find.textContaining(en), findsOneWidget);

      await _pump(
        tester,
        KitChip.count(label: 'المهام', count: count),
        locale: const Locale('ar'),
      );
      expect(find.textContaining(ar), findsOneWidget);
    });

    testWidgets('with onPressed it is a button that fires once', (
      tester,
    ) async {
      var taps = 0;
      await _pump(
        tester,
        KitChip.count(label: 'Tasks', count: 3, onPressed: () => taps++),
      );
      await tester.tap(find.textContaining('Tasks'), warnIfMissed: false);
      await tester.pump();
      expect(taps, 1);
    });
  });

  group('summary', () {
    testWidgets('a tap calls onPressed', (tester) async {
      var taps = 0;
      await _pump(
        tester,
        KitChip.summary(
          label: 'Read 3 files · edited 1',
          onPressed: () => taps++,
        ),
      );
      await tester.tap(
        find.text('Read 3 files · edited 1'),
        warnIfMissed: false,
      );
      await tester.pump();
      expect(taps, 1);
    });

    testWidgets('expanded true/false exposes expanded semantics', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        KitChip.summary(
          label: 'Read 3 files',
          onPressed: () {},
          expanded: false,
        ),
      );
      expect(
        tester.getSemantics(find.text('Read 3 files')),
        isSemantics(hasExpandedState: true, isExpanded: false),
      );
      await _pump(
        tester,
        KitChip.summary(
          label: 'Read 3 files',
          onPressed: () {},
          expanded: true,
        ),
      );
      expect(
        tester.getSemantics(find.text('Read 3 files')),
        isSemantics(hasExpandedState: true, isExpanded: true),
      );
      handle.dispose();
    });

    testWidgets('the chevron points down folded and up open', (tester) async {
      // Read from what is painted: the chevron glyph's transform to the
      // screen. Folded it is drawn as is (down); open it is drawn upside
      // down, its vertical axis flipped (up).
      double verticalAxis() => tester
          .renderObject<RenderBox>(find.byIcon(AppIconography.chevronDown))
          .getTransformTo(null)
          .entry(1, 1);

      await _pump(
        tester,
        KitChip.summary(
          label: 'Read 3 files',
          onPressed: () {},
          expanded: false,
        ),
      );
      expect(verticalAxis(), closeTo(1, 1e-6));
      await _pump(
        tester,
        KitChip.summary(
          label: 'Read 3 files',
          onPressed: () {},
          expanded: true,
        ),
      );
      await tester.pumpAndSettle();
      expect(verticalAxis(), closeTo(-1, 1e-6));
    });
  });

  group('hit area (LAY-9)', () {
    const key = ValueKey('under-test');

    Future<void> expectFortyEight(WidgetTester tester, double textScale) async {
      await _pump(
        tester,
        KitChip.action(key: key, label: 'Open in terminal', onPressed: () {}),
        textScale: textScale,
      );
      final size = tester.getSize(find.byKey(key));
      expect(size.height, greaterThanOrEqualTo(48));
    }

    testWidgets('at least 48 dp high at text 1.0', (tester) async {
      await expectFortyEight(tester, 1);
    });

    testWidgets('at least 48 dp high at text 2.0', (tester) async {
      await expectFortyEight(tester, 2);
    });

    testWidgets('every kind in a KitChipWrap at 320 dp: hit areas never '
        'overlap and pills line up in each run', (tester) async {
      // All five kinds, static ones included, twice over so the wrap has
      // several mixed runs.
      final keys = <GlobalKey>[];
      Widget keyed(Widget Function(Key key) chip) {
        final key = GlobalKey();
        keys.add(key);
        return chip(key);
      }

      await _pump(
        tester,
        SizedBox(
          width: 320,
          child: KitChipWrap(
            children: [
              for (var i = 0; i < 2; i++) ...[
                keyed((k) => KitChip(key: k, label: 'main')),
                keyed(
                  (k) => KitChip.action(
                    key: k,
                    label: 'Open in terminal',
                    onPressed: () {},
                  ),
                ),
                keyed(
                  (k) => KitChip.removable(
                    key: k,
                    label: 'file.txt',
                    onRemove: () {},
                  ),
                ),
                keyed((k) => KitChip.count(key: k, label: 'Tasks', count: 3)),
                keyed(
                  (k) => KitChip.summary(
                    key: k,
                    label: 'Read 3 files · edited 1',
                    onPressed: () {},
                    expanded: false,
                  ),
                ),
                keyed(
                  (k) => KitChip.count(
                    key: k,
                    label: 'Agents',
                    count: 2,
                    onPressed: () {},
                  ),
                ),
              ],
            ],
          ),
        ),
        size: const Size(320, 915),
      );
      final rects = [for (final key in keys) tester.getRect(find.byKey(key))];
      for (var i = 0; i < rects.length; i++) {
        expect(rects[i].height, greaterThanOrEqualTo(48), reason: 'chip $i');
        for (var j = i + 1; j < rects.length; j++) {
          expect(
            rects[i].overlaps(rects[j]),
            isFalse,
            reason: 'chip $i and chip $j overlap: ${rects[i]} / ${rects[j]}',
          );
        }
      }
      // A run is the chips that share a top edge in the wrap.
      final runs = <double, List<double>>{};
      for (final key in keys) {
        final top = tester.getRect(find.byKey(key)).top;
        final pill = tester.getRect(_pillOf(find.byKey(key)));
        expect(pill.height, KitTokens.chipHeight);
        runs.putIfAbsent(top, () => []).add(pill.center.dy);
      }
      expect(runs.length, greaterThan(2), reason: 'several runs: $runs');
      for (final run in runs.entries) {
        for (final centre in run.value) {
          expect(
            centre,
            closeTo(run.value.first, .5),
            reason: 'pill centres in the run at ${run.key}: ${run.value}',
          );
        }
      }
    });
  });

  group('truncation (A11Y-8, G6)', () {
    const long =
        'A genuinely very long chip label that will not fit on one line at '
        'any of the overflow widths this test pumps it at';

    testWidgets(
      'at text 2.0 a long label ellipsises with the full text in semantics',
      (tester) async {
        final handle = tester.ensureSemantics();
        await _pump(
          tester,
          const KitChip(label: long),
          textScale: 2,
          size: const Size(320, 800),
        );
        final text = tester.widget<Text>(find.text(long));
        expect(text.maxLines, 1);
        expect(text.overflow, TextOverflow.ellipsis);
        expect(tester.getSemantics(find.text(long)), isSemantics(label: long));
        handle.dispose();
      },
    );

    for (final width in [320.0, 360.0, 412.0]) {
      for (final ltr in [true, false]) {
        testWidgets('no overflow at $width dp, ${ltr ? 'LTR' : 'RTL'}', (
          tester,
        ) async {
          await _pump(
            tester,
            Directionality(
              textDirection: ltr ? TextDirection.ltr : TextDirection.rtl,
              child: KitChip.removable(label: long, onRemove: () {}),
            ),
            size: Size(width, 800),
          );
          expect(tester.takeException(), isNull);
        });
      }
    }
  });

  group('honesty (LOOK-14, LOOK-4)', () {
    /// Every colour the chips actually paint, read from the render tree:
    /// each text run's effective colour (a glyph is a text run too, so the
    /// check, × and chevron are included) and each fill and outline.
    ({List<(String, Color)> texts, List<(String, Color)> paints}) painted(
      WidgetTester tester,
    ) {
      final texts = <(String, Color)>[];
      final paints = <(String, Color)>[];
      void span(InlineSpan s, Color? inherited) {
        if (s is! TextSpan) return;
        final color = s.style?.color ?? inherited;
        final text = s.text ?? '';
        if (text.isNotEmpty && color != null) texts.add((text, color));
        for (final child in s.children ?? const <InlineSpan>[]) {
          span(child, color);
        }
      }

      void visit(RenderObject o) {
        if (o is RenderParagraph) span(o.text, null);
        if (o is RenderDecoratedBox) {
          final d = o.decoration;
          if (d is ShapeDecoration) {
            if (d.color != null) paints.add(('fill', d.color!));
            final shape = d.shape;
            if (shape is OutlinedBorder &&
                shape.side.style != BorderStyle.none) {
              paints.add(('outline', shape.side.color));
            }
          } else if (d is BoxDecoration && d.color != null) {
            paints.add(('box', d.color!));
          }
        }
        o.visitChildren(visit);
      }

      visit(tester.renderObject(find.byType(KitChipWrap)));
      return (texts: texts, paints: paints);
    }

    for (final light in [false, true]) {
      testWidgets('no text is painted below full alpha, no attention role '
          '(${light ? 'light' : 'dark'})', (tester) async {
        final roles = ThemeRoles.resolve(
          light ? AppTheme.light() : AppTheme.dark(),
        );
        final attention = {
          roles.attention,
          roles.attentionFill,
          roles.onAttentionFill,
          roles.attentionSurface,
          roles.attentionLine,
        };
        // The gallery scene: one chip of each kind, the selected and
        // expanded states, and the focused and hovered looks.
        await _pump(
          tester,
          KitChipWrap(
            children: [
              const KitChip(label: 'main', icon: Icons.circle),
              KitChip.action(
                label: 'Open in terminal',
                onPressed: () {},
                selected: true,
              ),
              KitChip.action(
                label: 'Ask first',
                onPressed: () {},
                icon: Icons.bolt,
                selected: false,
              ),
              KitChip.removable(label: 'file.txt', onRemove: () {}),
              KitChip.count(label: 'Tasks', count: 3),
              KitChip.summary(
                label: 'Read 3 files · edited 1',
                onPressed: () {},
                expanded: true,
              ),
            ],
          ),
          light: light,
        );
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
        await mouse.addPointer(location: Offset.zero);
        addTearDown(mouse.removePointer);
        await mouse.moveTo(tester.getCenter(find.text('Ask first')));
        await tester.pumpAndSettle();

        final (:texts, :paints) = painted(tester);
        for (final (text, color) in texts) {
          expect(color.a, 1, reason: '"$text" painted at ${color.a} alpha');
          expect(
            attention.contains(color),
            isFalse,
            reason: '"$text" painted in an attention role',
          );
        }
        for (final (what, color) in paints) {
          expect(
            attention.contains(color),
            isFalse,
            reason: 'a $what painted in an attention role',
          );
        }
        // The scan sees what it should: the quiet and the loud words, the
        // accent check, the hover fill and the focus ring.
        expect(texts, contains(('main', roles.text2)));
        expect(texts, contains(('Open in terminal', roles.text1)));
        expect(texts.map((t) => t.$2), contains(roles.accent));
        expect(paints, contains(('fill', roles.surface2)));
        expect(paints, contains(('outline', roles.accent)));
      });
    }
  });

  group('selected check (KitMotion.quick cross-fade)', () {
    testWidgets('turning selection on and off cross-fades check and icon', (
      tester,
    ) async {
      final selected = ValueNotifier(false);
      addTearDown(selected.dispose);
      await _pump(
        tester,
        ValueListenableBuilder<bool>(
          valueListenable: selected,
          builder: (context, on, _) => KitChip.action(
            label: 'Ask first',
            onPressed: () {},
            icon: Icons.bolt,
            selected: on,
          ),
        ),
      );
      final check = find.byKey(_checkKey);
      final icon = find.byIcon(Icons.bolt);
      double opacity(Finder glyph) => tester
          .renderObject<RenderAnimatedOpacity>(
            find.ancestor(of: glyph, matching: find.byType(FadeTransition)),
          )
          .opacity
          .value;

      expect(check, findsNothing);
      expect(icon, findsOneWidget);

      selected.value = true;
      await tester.pump();
      await tester.pump(KitMotion.quick ~/ 2);
      expect(opacity(check), inExclusiveRange(0, 1), reason: 'fading in');
      expect(opacity(icon), inExclusiveRange(0, 1), reason: 'fading out');
      await tester.pumpAndSettle();
      expect(opacity(check), 1);
      expect(icon, findsNothing);

      selected.value = false;
      await tester.pump();
      await tester.pump(KitMotion.quick ~/ 2);
      expect(opacity(check), inExclusiveRange(0, 1), reason: 'fading out');
      expect(opacity(icon), inExclusiveRange(0, 1), reason: 'fading in');
      await tester.pumpAndSettle();
      expect(check, findsNothing);
      expect(opacity(icon), 1);
    });
  });

  group('hover and press (the pill, under the words)', () {
    const k = ValueKey('chip');
    // Each interactive kind and zone: the chip, its label, the label's
    // colour, and where the pointer goes.
    final cases =
        <
          String,
          (
            Widget chip,
            String label,
            Color Function(ThemeRoles) ink,
            Offset Function(Rect chip, Rect label) at,
          )
        >{
          'action': (
            KitChip.action(key: k, label: 'Open', onPressed: () {}),
            'Open',
            (r) => r.text1,
            (chip, label) => chip.center,
          ),
          'action, selected': (
            KitChip.action(
              key: k,
              label: 'Open',
              onPressed: () {},
              selected: true,
            ),
            'Open',
            (r) => r.text1,
            (chip, label) => chip.center,
          ),
          'count': (
            KitChip.count(key: k, label: 'Tasks', count: 3, onPressed: () {}),
            'Tasks · 3',
            (r) => r.text2,
            (chip, label) => chip.center,
          ),
          'summary': (
            KitChip.summary(
              key: k,
              label: 'Read 3 files',
              onPressed: () {},
              expanded: false,
            ),
            'Read 3 files',
            (r) => r.text1,
            (chip, label) => chip.center,
          ),
          'removable body': (
            KitChip.removable(
              key: k,
              label: 'file.txt',
              onRemove: () {},
              onPressed: () {},
            ),
            'file.txt',
            (r) => r.text1,
            (chip, label) => Offset(label.left + 4, chip.center.dy),
          ),
          'removable x': (
            KitChip.removable(
              key: k,
              label: 'file.txt',
              onRemove: () {},
              onPressed: () {},
            ),
            'file.txt',
            (r) => r.text1,
            (chip, label) => Offset(chip.right - 24, chip.center.dy),
          ),
        };

    for (final entry in cases.entries) {
      for (final hover in [true, false]) {
        testWidgets('${entry.key}: ${hover ? 'hover' : 'press'} fills the '
            'pill surface2 and the words stay on top', (tester) async {
          final (chip, text, ink, at) = entry.value;
          final roles = ThemeRoles.resolve(AppTheme.dark());
          await _pump(tester, chip);
          final box = tester.getRect(find.byKey(k));
          final label = tester.getRect(find.text(text));
          final pill = tester.getRect(_pillOf(find.byKey(k)));
          final inPill = Offset(pill.left + 3, pill.center.dy);
          final outsidePill = Offset(label.center.dx, box.top + 2);

          final rest = await _Shot.take(tester);
          final words = rest.count(label, ink(roles));
          expect(words, greaterThan(0), reason: 'the label is drawn');
          expect(_near(rest.at(inPill), roles.surface3), isTrue);

          TestGesture? touch;
          if (hover) {
            final mouse = await tester.createGesture(
              kind: PointerDeviceKind.mouse,
            );
            await mouse.addPointer(location: Offset.zero);
            addTearDown(mouse.removePointer);
            await mouse.moveTo(at(box, label));
          } else {
            touch = await tester.startGesture(at(box, label));
            await tester.pump(kPressTimeout);
          }
          // Long enough for any highlight fade to finish, short of a long
          // press.
          await tester.pump(const Duration(milliseconds: 300));

          final active = await _Shot.take(tester);
          expect(
            active.count(label, ink(roles)),
            words,
            reason: 'every pixel of the words is still painted',
          );
          expect(
            _near(active.at(inPill), roles.surface2),
            isTrue,
            reason: 'the pill takes the surface2 fill: ${active.at(inPill)}',
          );
          expect(
            active.at(outsidePill),
            rest.at(outsidePill),
            reason: 'no fill spreads past the 32 dp pill',
          );
          await touch?.up();
          await tester.pumpAndSettle();
        });
      }
    }
  });

  group('truncation tooltip (KitChip.md "Adaptive")', () {
    const long = 'A long label that can never fit inside so narrow a chip';
    const k = ValueKey('chip');

    Future<void> settle(WidgetTester tester) async {
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
    }

    final kinds = <String, (Widget Function(VoidCallback tap), bool)>{
      'action': (
        (tap) => KitChip.action(key: k, label: long, onPressed: tap),
        false,
      ),
      'summary': (
        (tap) => KitChip.summary(
          key: k,
          label: long,
          onPressed: tap,
          expanded: false,
        ),
        false,
      ),
      'removable with a body tap': (
        (tap) => KitChip.removable(
          key: k,
          label: long,
          onRemove: () {},
          onPressed: tap,
        ),
        true,
      ),
      'removable': (
        (tap) => KitChip.removable(key: k, label: long, onRemove: () {}),
        true,
      ),
      'plain': ((tap) => const KitChip(key: k, label: long), false),
    };

    for (final entry in kinds.entries) {
      testWidgets('long-press on a truncated ${entry.key} chip shows the full '
          'label and does not tap', (tester) async {
        var taps = 0;
        final (build, removable) = entry.value;
        await _pump(tester, SizedBox(width: 160, child: build(() => taps++)));
        expect(find.text(long), findsOneWidget);
        final box = tester.getRect(find.byKey(k));
        await tester.longPressAt(
          removable ? Offset(box.left + 24, box.center.dy) : box.center,
        );
        await tester.pump();
        expect(find.text(long), findsNWidgets(2), reason: 'the tooltip');
        expect(taps, 0);
        await settle(tester);
      });
    }

    testWidgets('a truncated count chip with a tap shows "label · n" in full', (
      tester,
    ) async {
      await _pump(
        tester,
        SizedBox(
          width: 160,
          child: KitChip.count(key: k, label: long, count: 3, onPressed: () {}),
        ),
      );
      await tester.longPress(find.byKey(k));
      await tester.pump();
      expect(find.text('$long · 3'), findsNWidgets(2));
      await settle(tester);
    });

    testWidgets('a fine pointer hovering a truncated action chip shows it', (
      tester,
    ) async {
      await _pump(
        tester,
        SizedBox(
          width: 160,
          child: KitChip.action(key: k, label: long, onPressed: () {}),
        ),
      );
      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await mouse.addPointer(location: Offset.zero);
      addTearDown(mouse.removePointer);
      await mouse.moveTo(tester.getCenter(find.byKey(k)));
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.text(long), findsNWidgets(2));
      await mouse.moveTo(Offset.zero);
      await settle(tester);
    });

    testWidgets('a label that fits shows no tooltip', (tester) async {
      await _pump(
        tester,
        KitChip.action(key: k, label: 'Open', onPressed: () {}),
      );
      await tester.longPress(find.byKey(k));
      await tester.pump();
      expect(find.text('Open'), findsOneWidget);
      await settle(tester);
    });
  });

  group('focus ring (around the pill)', () {
    const k = ValueKey('chip');
    final kinds = <String, Widget>{
      'action': KitChip.action(key: k, label: 'Open', onPressed: () {}),
      'count': KitChip.count(
        key: k,
        label: 'Tasks',
        count: 3,
        onPressed: () {},
      ),
      'summary': KitChip.summary(
        key: k,
        label: 'Read 3 files',
        onPressed: () {},
        expanded: false,
      ),
      'removable x': KitChip.removable(
        key: k,
        label: 'file.txt',
        onRemove: () {},
      ),
    };
    for (final entry in kinds.entries) {
      testWidgets('${entry.key}: keyboard focus draws an accent ring on the '
          '32 dp pill, not the 48 dp box', (tester) async {
        final roles = ThemeRoles.resolve(AppTheme.dark());
        await _pump(tester, entry.value);
        expect(_ring, findsNothing);
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        expect(_ring, findsOneWidget);
        final ring = tester.getRect(_ring);
        final pill = tester.getRect(_pillOf(find.byKey(k)));
        expect(ring, pill);
        expect(ring.height, KitTokens.chipHeight);
        expect(ring.center.dy, tester.getRect(find.byKey(k)).center.dy);
        final shape =
            (tester.widget<DecoratedBox>(_ring).decoration as ShapeDecoration)
                    .shape
                as OutlinedBorder;
        expect(shape.side.color, roles.accent);
      });
    }
  });

  group('keyboard (G14)', () {
    testWidgets('Tab order is the chip, then its x; Enter and Space activate', (
      tester,
    ) async {
      var pressed = 0;
      var removed = 0;
      await _pump(
        tester,
        KitChip.removable(
          label: 'file.txt',
          onPressed: () => pressed++,
          onRemove: () => removed++,
        ),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_focusIn(tester, find.byKey(_bodyKey)), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(pressed, 1);

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_focusIn(tester, find.byKey(_removeKey)), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pump();
      expect(removed, 1);
    });
  });
}
