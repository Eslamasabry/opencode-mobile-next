// KitJumpPill (docs/ux-system/kit-api/KitJumpPill.md): the frozen "Tests
// required" contract, items 1-9.
import 'kit_motion_still.dart';

import 'dart:ui' as ui;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

/// Pumps [child] as a screen's body, with the real test window sized to
/// [size] (not just a MediaQuery override), so tap and hit-test geometry
/// line up with what a person would actually see.
Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double textScale = 1,
  bool light = false,
  bool disableAnimations = false,
  KitEffects effects = KitEffects.defaults,
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    KitEffectsScope(
      effects: effects,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: light ? AppTheme.light() : AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, widget) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: TextScaler.linear(textScale),
            disableAnimations: disableAnimations,
          ),
          child: widget!,
        ),
        home: Scaffold(body: Center(child: child)),
      ),
    ),
  );
}

/// Whether keyboard focus is on [target] itself or a descendant of it
/// (test/kit/kit_keyboard_test.dart's own helper, kept local so this
/// file's write set stays just its own tests).
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

/// The pill's own filled stadium (the background [DecoratedBox]).
final _fill = find.byWidgetPredicate(
  (w) =>
      w is DecoratedBox &&
      w.position == DecorationPosition.background &&
      w.decoration is ShapeDecoration &&
      (w.decoration as ShapeDecoration).color != null,
);

/// Reads back what [boundary] actually painted, one RGBA pixel per logical
/// pixel at the test window's device pixel ratio of 1.
Future<({ByteData bytes, int width})> _paintedPixels(
  WidgetTester tester,
  GlobalKey boundary,
) async {
  final render =
      boundary.currentContext!.findRenderObject()! as RenderRepaintBoundary;
  final data = await tester.binding.runAsync(() async {
    final image = await render.toImage();
    final bytes = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
    final width = image.width;
    image.dispose();
    return (bytes: bytes!, width: width);
  });
  return data!;
}

Color _pixel(({ByteData bytes, int width}) px, int x, int y) {
  final i = (y * px.width + x) * 4;
  return Color.fromARGB(
    px.bytes.getUint8(i + 3),
    px.bytes.getUint8(i),
    px.bytes.getUint8(i + 1),
    px.bytes.getUint8(i + 2),
  );
}

/// Every channel within [tolerance] of [expected] (anti-aliasing).
bool _near(Color a, Color expected, {int tolerance = 24}) {
  int c(double v) => (v * 255).round();
  return (c(a.r) - c(expected.r)).abs() <= tolerance &&
      (c(a.g) - c(expected.g)).abs() <= tolerance &&
      (c(a.b) - c(expected.b)).abs() <= tolerance;
}

/// The label's laid-out paragraph inside the pill (the icon is a
/// [RichText] too, so match on the words).
RenderParagraph _labelParagraph(WidgetTester tester, String label) =>
    tester.renderObject<RenderParagraph>(
      find.descendant(
        of: find.byType(KitJumpPill),
        matching: find.byWidgetPredicate(
          (w) => w is RichText && w.text.toPlainText() == label,
        ),
      ),
    );

extension on RenderParagraph {
  /// How many lines the paragraph actually laid out (distinct line tops of
  /// its whole text's selection boxes).
  int get _renderedLines => getBoxesForSelection(
    TextSelection(baseOffset: 0, extentOffset: text.toPlainText().length),
  ).map((b) => b.top.round()).toSet().length;
}

/// The fill the pill shows: KitTappable's hover or press fill when it has
/// one, the resting stadium under it otherwise.
Color _fillColor(WidgetTester tester) {
  final over =
      (tester
                  .widget<AnimatedContainer>(
                    find.descendant(
                      of: find.byType(KitTappable),
                      matching: find.byType(AnimatedContainer),
                    ),
                  )
                  .decoration!
              as ShapeDecoration)
          .color!;
  if (over.a != 0) return over;
  return (tester.widget<DecoratedBox>(_fill.first).decoration
          as ShapeDecoration)
      .color!;
}

void main() {
  KitJumpPill motionPill(bool visible) =>
      KitJumpPill(label: 'Jump to latest', onPressed: () {}, visible: visible);
  Widget motionLayer(bool visible) => KitJumpPillLayer(
    pill: motionPill(visible),
    child: const SizedBox.expand(child: Text('Log output')),
  );
  kitMotionStillTests(
    'KitJumpPill',
    builds: {'visible': () => motionPill(true)},
    changes: {
      'appears': KitMotionChange(
        build: () => motionPill(false),
        act: (tester, stage) => stage.rebuild(motionPill(true)),
        shows: 'Jump to latest',
      ),
      'disappears': KitMotionChange(
        build: () => motionPill(true),
        act: (tester, stage) => stage.rebuild(motionPill(false)),
        hides: 'Jump to latest',
      ),
    },
  );
  kitMotionStillTests(
    'KitJumpPillLayer',
    builds: {'over log': () => motionLayer(true)},
    changes: {
      'new output shows jump': KitMotionChange(
        build: () => motionLayer(false),
        act: (tester, stage) => stage.rebuild(motionLayer(true)),
        shows: 'Jump to latest',
      ),
    },
  );

  group('1. visible', () {
    testWidgets('shows the label and icon; tap calls onPressed once', (
      tester,
    ) async {
      var taps = 0;
      await _pump(
        tester,
        KitJumpPill(
          label: '3 new · Jump to latest',
          onPressed: () => taps++,
          visible: true,
        ),
      );
      expect(find.text('3 new · Jump to latest'), findsOneWidget);
      expect(find.byIcon(AppIconography.down), findsOneWidget);
      await tester.tap(find.text('3 new · Jump to latest'));
      await tester.pump();
      expect(taps, 1);
    });
  });

  group('2. hidden', () {
    /// True when [finder] sits under an offstage [RenderOffstage] (Flutter
    /// keeps the element mounted and laid out, but excludes it from
    /// painting, hit testing and semantics — proxy_box.dart `RenderOffstage`
    /// `hitTest`/`visitChildrenForSemantics`).
    bool underOffstage(WidgetTester tester, Finder finder) {
      for (
        RenderObject? r = tester.renderObject(finder);
        r != null;
        r = r.parent
      ) {
        if (r is RenderOffstage && r.offstage) return true;
      }
      return false;
    }

    /// The offstage subtree is still built (`Visibility(maintainState:
    /// true)`), so a finder must be told not to skip it (finders default to
    /// `skipOffstage: true`, which is exactly the opposite of what "kept
    /// mounted" needs to prove here).
    Finder onstageOrNot(String text) => find.text(text, skipOffstage: false);

    testWidgets('not hittable, not in semantics, not focusable; nothing paints '
        'once settled hidden', (tester) async {
      final handle = tester.ensureSemantics();
      var taps = 0;
      await _pump(
        tester,
        KitJumpPill(
          label: 'Jump to latest',
          onPressed: () => taps++,
          visible: false,
        ),
      );
      // Kept mounted (found by the element tree)...
      expect(onstageOrNot('Jump to latest'), findsOneWidget);
      // ...but offstage: not painted, not hit-testable, not in semantics.
      expect(underOffstage(tester, onstageOrNot('Jump to latest')), isTrue);
      expect(find.bySemanticsLabel('Jump to latest'), findsNothing);

      await tester.tap(onstageOrNot('Jump to latest'), warnIfMissed: false);
      await tester.pump();
      expect(taps, 0);

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(
        _focusIn(tester, find.byType(KitJumpPill, skipOffstage: false)),
        isFalse,
        reason: 'ExcludeFocus keeps a hidden pill out of Tab order',
      );

      expect(tester.hasRunningAnimations, isFalse);
      handle.dispose();
    });

    testWidgets(
      'toggling to hidden ends offstage, not hittable, after settling',
      (tester) async {
        var taps = 0;
        Widget host(bool visible) => KitEffectsScope(
          effects: KitEffects.defaults,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: Center(
                child: KitJumpPill(
                  label: 'Jump to latest',
                  onPressed: () => taps++,
                  visible: visible,
                ),
              ),
            ),
          ),
        );
        await tester.pumpWidget(host(true));
        await tester.pump(const Duration(seconds: 1));
        await tester.pumpWidget(host(false));
        // Settle the exit animation (KitMotion.quick, 150ms).
        await tester.pump(const Duration(milliseconds: 300));

        expect(underOffstage(tester, onstageOrNot('Jump to latest')), isTrue);
        await tester.tap(onstageOrNot('Jump to latest'), warnIfMissed: false);
        await tester.pump();
        expect(taps, 0);
        expect(tester.hasRunningAnimations, isFalse);
      },
    );

    testWidgets('hidden takes effect at once: 50 ms into the exit fade the '
        'pill still paints but cannot be tapped, read or focused', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      var taps = 0;
      final theme = AppTheme.dark();
      Widget host(bool visible) => KitEffectsScope(
        effects: KitEffects.defaults,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: theme,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: Center(
              child: KitJumpPill(
                label: 'Jump to latest',
                onPressed: () => taps++,
                visible: visible,
              ),
            ),
          ),
        ),
      );
      await tester.pumpWidget(host(true));
      await tester.pump(const Duration(seconds: 1));
      // Focus it first, so hiding has to take focus away, not just refuse it.
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_focusIn(tester, find.byType(KitTappable)), isTrue);
      expect(find.semantics.byLabel('Jump to latest'), findsOne);
      final rect = tester.getRect(find.byType(KitTappable));

      await tester.pumpWidget(host(false));
      await tester.pump(const Duration(milliseconds: 50));

      // Still mid-fade: painted, partly opaque, animation running.
      expect(tester.hasRunningAnimations, isTrue);
      expect(underOffstage(tester, onstageOrNot('Jump to latest')), isFalse);
      final opacity = tester.widget<Opacity>(
        find.descendant(
          of: find.byType(KitJumpPill),
          matching: find.byType(Opacity),
        ),
      );
      expect(opacity.opacity, greaterThan(0));
      expect(opacity.opacity, lessThan(1));

      // ...yet already out of the semantics tree and focus...
      expect(find.semantics.byLabel('Jump to latest'), findsNothing);
      expect(
        _focusIn(tester, find.byType(KitJumpPill, skipOffstage: false)),
        isFalse,
        reason: 'hiding takes focus off the pill at once',
      );

      // ...and not hittable: a tap where it is drawn does nothing.
      await tester.tapAt(rect.center);
      await tester.pump();
      expect(taps, 0);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(taps, 0);

      // Tab does not land on it either.
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(
        _focusIn(tester, find.byType(KitJumpPill, skipOffstage: false)),
        isFalse,
      );

      await tester.pump(const Duration(seconds: 1));
      expect(underOffstage(tester, onstageOrNot('Jump to latest')), isTrue);
      handle.dispose();
    });
  });

  group('3. motion', () {
    testWidgets('toggling visible animates over KitMotion.standard, no scale', (
      tester,
    ) async {
      final key = GlobalKey();
      // One theme instance for every pump: a fresh AppTheme.dark() per call
      // would not compare equal and MaterialApp's AnimatedTheme would
      // animate between the two (the harness moving, not the part) — see
      // test/kit/kit_motion_still.dart's own `_theme`.
      final theme = AppTheme.dark();
      Widget host(bool visible) => KitEffectsScope(
        effects: KitEffects.defaults,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: theme,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: Center(
              child: KitJumpPill(
                key: key,
                label: 'Jump to latest',
                onPressed: () {},
                visible: visible,
              ),
            ),
          ),
        ),
      );
      await tester.pumpWidget(host(false));
      // Let the app's own initial-route transition settle before toggling,
      // so only KitJumpPill's own animation is under test below.
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpWidget(host(true));
      await tester.pump();
      expect(tester.hasRunningAnimations, isTrue);
      final pillScope = find.byType(KitJumpPill);
      expect(
        find.descendant(of: pillScope, matching: find.byType(ScaleTransition)),
        findsNothing,
      );
      expect(
        tester
            .widgetList(
              find.descendant(of: pillScope, matching: find.byType(Transform)),
            )
            .whereType<Transform>()
            .any((t) => t.transform.getMaxScaleOnAxis() != 1.0),
        isFalse,
        reason: 'MOT-2: KitJumpPill never scales',
      );
      // KitMotion.standard is 250ms; well short of it nothing has settled.
      await tester.pump(const Duration(milliseconds: 100));
      expect(tester.hasRunningAnimations, isTrue);
      await tester.pump(const Duration(milliseconds: 200));
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('under reduced motion (system), one pump() settles', (
      tester,
    ) async {
      final key = GlobalKey();
      final theme = AppTheme.dark();
      Widget host(bool visible) => MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: theme,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: Scaffold(
          body: Center(
            child: KitJumpPill(
              key: key,
              label: 'Jump to latest',
              onPressed: () {},
              visible: visible,
            ),
          ),
        ),
      );
      await tester.pumpWidget(host(false));
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpWidget(host(true));
      await tester.pump();
      expect(tester.hasRunningAnimations, isFalse);
      expect(find.text('Jump to latest'), findsOneWidget);

      await tester.pumpWidget(host(false));
      await tester.pump();
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('under reduced motion (Animations: Off), one pump() settles', (
      tester,
    ) async {
      final key = GlobalKey();
      final theme = AppTheme.dark();
      Widget host(bool visible) => KitEffectsScope(
        effects: const KitEffects(motion: KitMotionLevel.off),
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: theme,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: Center(
              child: KitJumpPill(
                key: key,
                label: 'Jump to latest',
                onPressed: () {},
                visible: visible,
              ),
            ),
          ),
        ),
      );
      await tester.pumpWidget(host(false));
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpWidget(host(true));
      await tester.pump();
      expect(tester.hasRunningAnimations, isFalse);
      expect(find.text('Jump to latest'), findsOneWidget);
    });
  });

  group('4. KitJumpPillLayer', () {
    testWidgets('clears the published bottom inset by default', (tester) async {
      const inset = 140.0;
      await tester.pumpWidget(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: KitBottomInset(
              insets: const KitClearance(bottom: inset),
              child: KitJumpPillLayer(
                pill: KitJumpPill(
                  label: 'Jump to latest',
                  onPressed: () {},
                  visible: true,
                ),
                child: const SizedBox.expand(),
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(seconds: 1));
      final areaBottom = tester.getRect(find.byType(Scaffold)).bottom;
      final pillBottom = tester.getRect(find.byType(KitJumpPill)).bottom;
      // >= 152 dp above the area's bottom (140 inset + 12 space3).
      expect(areaBottom - pillBottom, greaterThanOrEqualTo(152));
    });

    testWidgets('clearBottomInset: false sits space3 from the area bottom', (
      tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: KitBottomInset(
              insets: const KitClearance(bottom: 140),
              child: KitJumpPillLayer(
                clearBottomInset: false,
                pill: KitJumpPill(
                  label: 'Jump to latest',
                  onPressed: () {},
                  visible: true,
                ),
                child: const SizedBox.expand(),
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(seconds: 1));
      final context = tester.element(find.byType(Scaffold));
      final tokens = KitTokens.of(context);
      final areaBottom = tester.getRect(find.byType(Scaffold)).bottom;
      final pillBottom = tester.getRect(find.byType(KitJumpPill)).bottom;
      expect(areaBottom - pillBottom, moreOrLessEquals(tokens.space3));
    });
  });

  group('5. KitJumpPill.older', () {
    testWidgets('sits at the top edge with the up glyph', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: KitJumpPillLayer(
              pill: KitJumpPill.older(
                label: 'Earlier messages',
                onPressed: () {},
                visible: true,
              ),
              child: const SizedBox.expand(),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(seconds: 1));
      expect(find.byIcon(AppIconography.chevronUp), findsOneWidget);
      final context = tester.element(find.byType(Scaffold));
      final tokens = KitTokens.of(context);
      final areaTop = tester.getRect(find.byType(Scaffold)).top;
      final pillTop = tester.getRect(find.byType(KitJumpPill)).top;
      expect(pillTop - areaTop, moreOrLessEquals(tokens.space3, epsilon: 2));
    });
  });

  group('6. latestLabel', () {
    testWidgets('newCount: 3 says "3 new · Jump to latest"; 0 says '
        '"Jump to latest"', (tester) async {
      late BuildContext ctx;
      await tester.pumpWidget(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) {
              ctx = context;
              return const SizedBox();
            },
          ),
        ),
      );
      expect(
        KitJumpPill.latestLabel(ctx, newCount: 3),
        '3 new · Jump to latest',
      );
      expect(
        KitJumpPill.latestLabel(ctx, newCount: 1),
        '1 new · Jump to latest',
      );
      expect(KitJumpPill.latestLabel(ctx), 'Jump to latest');
      expect(KitJumpPill.latestLabel(ctx, newCount: 0), 'Jump to latest');
    });
  });

  group('7. semantics', () {
    testWidgets('a button whose label is the words, not a live region, '
        '>= 48x48', (tester) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        KitJumpPill(
          label: '3 new · Jump to latest',
          onPressed: () {},
          visible: true,
        ),
      );
      // Settle the entrance animation: at opacity 0 mid-fade, Opacity
      // excludes its subtree from semantics by design, which is not what
      // this test means to exercise.
      await tester.pump(const Duration(seconds: 1));
      final semantics = tester.getSemantics(
        find.text('3 new · Jump to latest'),
      );
      // Focusability itself is proven by real Tab traversal in "8. desktop
      // capabilities" below; `excludeSemantics` merges only this node's own
      // flags, not the tappable's contributed `isFocusable`.
      expect(
        semantics,
        isSemantics(
          label: '3 new · Jump to latest',
          isButton: true,
          hasTapAction: true,
        ),
      );
      expect(
        semantics.getSemanticsData().flagsCollection.isLiveRegion,
        isFalse,
        reason: 'K2 §1.19: not a live region',
      );
      final box = tester.getSize(find.byType(KitTappable));
      expect(box.width, greaterThanOrEqualTo(48));
      expect(box.height, greaterThanOrEqualTo(48));
      handle.dispose();
    });
  });

  group('8. desktop capabilities', () {
    testWidgets('focus ring painted inside the pill in accent when '
        'focused; Enter activates', (tester) async {
      var taps = 0;
      final boundary = GlobalKey();
      await _pump(
        tester,
        RepaintBoundary(
          key: boundary,
          child: Padding(
            padding: const EdgeInsets.all(8),
            child: KitJumpPill(
              label: 'Jump to latest',
              onPressed: () => taps++,
              visible: true,
            ),
          ),
        ),
      );
      await tester.pump(const Duration(seconds: 1));
      final roles = ThemeRoles.resolve(AppTheme.dark());
      final origin = tester.getTopLeft(find.byKey(boundary));
      final pill = tester.getRect(find.byType(KitTappable)).shift(-origin);
      // The ring is focusRingWidth (2 logical px at a ratio of 1), drawn
      // inside the stadium like every KitTappable's: sample the columns
      // just inside the pill's leftmost point, and the rows just inside its
      // top.
      final y = pill.center.dy.floor();
      final x = pill.center.dx.floor();
      final left = pill.left.floor();
      final top = pill.top.floor();
      List<Color> ringSamples(({ByteData bytes, int width}) px) => [
        _pixel(px, left + 1, y),
        _pixel(px, left + 2, y),
        _pixel(px, x, top + 1),
        _pixel(px, x, top + 2),
      ];

      final before = await _paintedPixels(tester, boundary);
      expect(
        ringSamples(before).any((c) => _near(c, roles.accent)),
        isFalse,
        reason: 'no ring while unfocused',
      );

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_focusIn(tester, find.byType(KitTappable)), isTrue);
      final after = await _paintedPixels(tester, boundary);
      // At the pill's left and top edge the painted pixel is accent — the
      // ring is on screen, inside the stadium.
      expect(
        _near(_pixel(after, left + 1, y), roles.accent) ||
            _near(_pixel(after, left + 2, y), roles.accent),
        isTrue,
        reason: 'accent ring at the pill\'s left edge',
      );
      expect(
        _near(_pixel(after, x, top + 1), roles.accent) ||
            _near(_pixel(after, x, top + 2), roles.accent),
        isTrue,
        reason: 'accent ring at the pill\'s top edge',
      );
      // The pill's own fill in the middle is untouched.
      expect(_near(_pixel(after, x, y), roles.accent), isFalse);

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(taps, 1);
    });

    testWidgets('fine-pointer hover steps the fill to surface2', (
      tester,
    ) async {
      await _pump(
        tester,
        KitJumpPill(label: 'Jump to latest', onPressed: () {}, visible: true),
      );
      final roles = ThemeRoles.resolve(AppTheme.dark());
      expect(_fillColor(tester), roles.surface3);
      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await mouse.addPointer(location: Offset.zero);
      addTearDown(mouse.removePointer);
      await mouse.moveTo(tester.getCenter(find.byType(KitTappable)));
      await tester.pump();
      expect(_fillColor(tester), roles.surface2);
    });
  });

  group('9. text scale and line count', () {
    // Real glyph widths (Roboto), not the test font's square boxes, so the
    // line counts below are what a phone lays out.
    setUpAll(loadKitGalleryFonts);

    Widget layer(String label) => KitJumpPillLayer(
      clearBottomInset: false,
      pill: KitJumpPill(label: label, onPressed: () {}, visible: true),
      child: const SizedBox.expand(),
    );

    const real = '3 new · Jump to latest';
    const long = '123456 new messages · Jump to latest in this conversation';

    testWidgets('200% text at 320 dp: the real label, no overflow, <= 2 '
        'rendered lines, not truncated', (tester) async {
      await _pump(
        tester,
        layer(real),
        size: const Size(320, 700),
        textScale: 2.0,
      );
      await tester.pump(const Duration(seconds: 1));
      expect(tester.takeException(), isNull);
      final paragraph = _labelParagraph(tester, real);
      expect(paragraph._renderedLines, lessThanOrEqualTo(2));
      expect(paragraph.didExceedMaxLines, isFalse, reason: 'never truncated');
      expect(
        tester.getRect(find.byType(KitTappable)).width,
        lessThanOrEqualTo(320 - 2 * 16),
        reason: 'max width = area width - 2 x gutter',
      );
    });

    testWidgets('200% text at 320 dp: a long count is bounded to 2 lines', (
      tester,
    ) async {
      await _pump(
        tester,
        layer(long),
        size: const Size(320, 700),
        textScale: 2.0,
      );
      await tester.pump(const Duration(seconds: 1));
      expect(tester.takeException(), isNull);
      expect(
        _labelParagraph(tester, long)._renderedLines,
        lessThanOrEqualTo(2),
      );
    });

    testWidgets('100% text: one line, even for a long label in a narrow '
        'area', (tester) async {
      await _pump(tester, layer(real), size: const Size(320, 700));
      await tester.pump(const Duration(seconds: 1));
      final paragraph = _labelParagraph(tester, real);
      expect(paragraph._renderedLines, 1);
      expect(paragraph.didExceedMaxLines, isFalse);

      await _pump(tester, layer(long), size: const Size(240, 700));
      await tester.pump(const Duration(seconds: 1));
      expect(tester.takeException(), isNull);
      expect(_labelParagraph(tester, long)._renderedLines, 1);
      // A label longer than the area: the pill takes exactly the area's
      // width less two gutters (KitJumpPill.md "Adaptive"), no more.
      expect(
        tester.getRect(find.byType(KitTappable)).width,
        moreOrLessEquals(240 - 2 * 16),
      );
    });
  });
}
