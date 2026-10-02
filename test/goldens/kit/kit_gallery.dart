// Shared frame for the kit part galleries (gate G4, docs/ux-system/kit-v2.md
// §7 and §8.4): every part at the five window sizes in light and dark, and
// at 412 and 1280 wide with 2.0 text and in Arabic (right to left). The
// app's real fonts come from tool/capture; Arabic falls back to Noto Sans
// Arabic (test/fixtures/fonts, OFL), as it does on an Android device.
//
// Gate G5 (docs/ux-system/revamp/STANDARDS.md §18, absolute): every shot
// also runs androidTapTargetGuideline, labeledTapTargetGuideline and
// textContrastGuideline, and checks that the screen-reader traversal reads
// top to bottom, then start to end (A11Y-1, A11Y-4, A11Y-6, LAY-9), in
// light and in dark whichever theme the gallery asked for. There is no
// opt-out: fix the part, not the shot. The one baseline entry is capped by
// kitGalleryG5Ceiling below; the gate's own tests are
// kit_gallery_g5_test.dart.
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' show FlutterView;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/kit/kit_screen.dart';

import '../../../tool/capture/fixtures.dart'
    show captureTheme, loadCaptureFonts;

const _arabicFallback = 'KitGalleryNotoSansArabic';

/// The capture fonts plus the Arabic fallback family, and the families
/// AppTheme.forLocale names for Arabic ('sans-serif', which is Roboto on
/// Android, and 'Noto Sans Arabic'), so a screen rendered through it reads
/// like the device (TEST-8, gate G23).
Future<void> loadKitGalleryFonts() async {
  await loadCaptureFonts();
  Future<void> load(String family, List<String> paths) async {
    final loader = FontLoader(family);
    for (final path in paths) {
      loader.addFont(File(path).readAsBytes().then(ByteData.sublistView));
    }
    await loader.load();
  }

  const noto = [
    'test/fixtures/fonts/NotoSansArabic-Regular.ttf',
    'test/fixtures/fonts/NotoSansArabic-Bold.ttf',
  ];
  await load(_arabicFallback, noto);
  await load('Noto Sans Arabic', noto);
  await load('sans-serif', const [
    'tool/capture/fonts/Roboto-Regular.ttf',
    'tool/capture/fonts/Roboto-Medium.ttf',
    'tool/capture/fonts/Roboto-Bold.ttf',
  ]);
}

/// The capture theme with Arabic falling back to Noto, like a device.
ThemeData _theme({required bool light}) {
  final theme = captureTheme(light: light);
  const fallback = [_arabicFallback];
  // Buttons carry their own text styles in the app theme.
  ButtonStyle? withFallback(ButtonStyle? style) {
    final text = style?.textStyle;
    if (style == null || text == null) return style;
    return style.copyWith(
      textStyle: WidgetStateProperty.resolveWith(
        (states) =>
            text.resolve(states)?.copyWith(fontFamilyFallback: fallback),
      ),
    );
  }

  final text = theme.textTheme.apply(fontFamilyFallback: fallback);
  return theme.copyWith(
    // The kit's own styles carry the fallback too.
    extensions: [
      ...theme.extensions.values,
      KitTokens.fromRoles(ThemeRoles.resolve(theme), text),
    ],
    textTheme: text,
    primaryTextTheme: theme.primaryTextTheme.apply(
      fontFamilyFallback: fallback,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: withFallback(theme.filledButtonTheme.style),
    ),
    textButtonTheme: TextButtonThemeData(
      style: withFallback(theme.textButtonTheme.style),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: withFallback(theme.outlinedButtonTheme.style),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: withFallback(theme.elevatedButtonTheme.style),
    ),
  );
}

/// The §8.4 sizes: phone, the census phone, tablet portrait, tablet
/// landscape or PC, large PC.
const kitGallerySizes = <Size>[
  Size(360, 800),
  Size(412, 915),
  Size(800, 1280),
  Size(1280, 800),
  Size(1600, 1000),
];

/// Where 2.0 text and Arabic are rendered.
const kitGalleryScaledSizes = <Size>[Size(412, 915), Size(1280, 800)];

String kitGallerySize(Size size) =>
    '${size.width.toInt()}x${size.height.toInt()}';

/// The TEST-20 golden name for a gallery shot:
/// `<shot>[_ar][_text2][_<W>x<H>]_<dark|light>`, where [shot] is
/// `kit_<part>_<state>` and the size is left out for 412x915. Gate G23
/// (test/golden_harness_test.dart) rejects any other kit golden name.
String kitGalleryName(
  String shot,
  Size size, {
  required bool light,
  bool ar = false,
  bool text2 = false,
}) {
  if (!RegExp(r'^kit_[a-z0-9]+(_[a-z0-9]+)+$').hasMatch(shot)) {
    throw ArgumentError.value(shot, 'shot', 'must be kit_<part>_<state>');
  }
  return [
    shot,
    if (ar) 'ar',
    if (text2) 'text2',
    if (size != const Size(412, 915)) kitGallerySize(size),
    light ? 'light' : 'dark',
  ].join('_');
}

/// Pumps an empty screen at [size], runs [open] against a context under the
/// navigator (it opens the modal part), settles, runs the G5 accessibility
/// checks ([expectKitGalleryAccessible]) and compares the whole window with
/// `goldens/kit/<name>.png`.
///
/// G5 checks every shot in both themes (A11Y-6), whatever the gallery
/// renders: the shot is first pumped in the other theme and checked there
/// (no golden), then in its own theme, checked and compared. [name] must end
/// in `_light` or `_dark` to match [light]; the other theme is checked under
/// the partner name, so a baseline entry means the same frame from either
/// gallery loop.
Future<void> kitGalleryShot(
  WidgetTester tester, {
  required String name,
  required Size size,
  required bool light,
  required FutureOr<void> Function(BuildContext context) open,
  Future<void> Function(WidgetTester tester)? then,
  Locale locale = const Locale('en'),
  double textScale = 1,
  bool settleAfterThen = true,
}) async {
  final own = _themeName(light);
  if (!name.endsWith('_$own')) {
    throw ArgumentError.value(
      name,
      'name',
      'G5 (STANDARDS.md §18, A11Y-6): a gallery shot name ends in "_$own" '
          'when light is $light',
    );
  }
  final stem = name.substring(0, name.length - own.length - 1);
  // TEST-9: DPR 3.0, [size] in logical pixels.
  tester.view.physicalSize = size * 3.0;
  tester.view.devicePixelRatio = 3.0;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  // Released before the test ends; a tear-down runs too late for the check.
  final semantics = tester.ensureSemantics();
  // ARCH-11: rendered as Android. Cleared in the finally: flutter_test checks
  // it before tear-downs run, also when a caller catches a failed shot.
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  try {
    // The other theme first (checks only), then the shot's own theme.
    for (final pass in [!light, light]) {
      late BuildContext context;
      // A fresh tree, so nothing the other pass opened carries over.
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: _theme(light: pass),
            locale: locale,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                disableAnimations: true,
                textScaler: TextScaler.linear(textScale),
              ),
              child: child!,
            ),
            home: Scaffold(
              body: Builder(
                builder: (inner) {
                  context = inner;
                  return const SizedBox.expand();
                },
              ),
            ),
          ),
        ),
      );
      unawaited(Future.sync(() => open(context)));
      await tester.pumpAndSettle();
      if (then != null) {
        await then(tester);
        if (settleAfterThen) await tester.pumpAndSettle();
      }
      expect(tester.takeException(), isNull);
      await expectKitGalleryAccessible(
        tester,
        shot: '${stem}_${_themeName(pass)}',
        direction: Directionality.of(context),
      );
    }
  } finally {
    debugDefaultTargetPlatformOverride = null;
    semantics.dispose();
  }
  await expectLater(find.byKey(boundary), matchesGoldenFile('$name.png'));
}

String _themeName(bool light) => light ? 'light' : 'dark';

/// The G5 checks by the name the baseline uses.
const _guidelines = <String, AccessibilityGuideline>{
  'androidTapTarget': androidTapTargetGuideline,
  'labeledTapTarget': labeledTapTargetGuideline,
  'textContrast': textContrastGuideline,
};
const _readingOrder = 'readingOrder';

/// A G5 baseline: shot (golden name, theme included) → check → the labels
/// of the semantics nodes that may still fail that check in that shot.
typedef KitGalleryG5Baseline = Map<String, Map<String, Set<String>>>;

/// The hard ceiling on the G5 baseline. G5 is absolute (STANDARDS.md
/// §18.2); this one entry is today's code, recorded when the gate was built
/// (docs/qa/gate-G5-2026-09-26/README.md). The JSON baseline may hold only
/// entries from this set, so adding a shot, a check or a node there fails
/// every shot. Kit units never edit this harness (§0.5 step 3, PROC-13);
/// nobody raises this set.
const kitGalleryG5Ceiling = <String, Map<String, Set<String>>>{
  'kit_confirm_destructive_text2_1280x800_light': {
    'textContrast': {'Cancel'},
  },
};

/// Where the shrinking G5 baseline lives. An entry leaves it as soon as its
/// node passes: a listed failure that no longer happens fails the shot, so
/// a stale entry cannot hide a later regression.
const kitGalleryG5BaselinePath =
    'test/goldens/kit/kit_gallery_g5_baseline.json';

/// Reads the committed baseline ({"shots": {shot: {check: [label]}}}).
KitGalleryG5Baseline readKitGalleryG5Baseline([
  String path = kitGalleryG5BaselinePath,
]) {
  final file = File(path);
  if (!file.existsSync()) return {};
  final raw = jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
  final shots = raw['shots'] as Map<String, dynamic>? ?? const {};
  return {
    for (final MapEntry(key: shot, value: checks) in shots.entries)
      shot: {
        for (final MapEntry(key: check, value: labels)
            in (checks as Map<String, dynamic>).entries)
          check: {for (final label in labels as List<dynamic>) label as String},
      },
  };
}

/// Every (shot, check, node) in [baseline] that [kitGalleryG5Ceiling] does
/// not hold.
List<String> kitGalleryG5OverCeiling(KitGalleryG5Baseline baseline) => [
  for (final MapEntry(key: shot, value: checks) in baseline.entries)
    for (final MapEntry(key: check, value: labels) in checks.entries)
      for (final label in labels)
        if (!(kitGalleryG5Ceiling[shot]?[check]?.contains(label) ?? false))
          '$shot [$check] "$label"',
];

/// One G5 failure: the check, the failing node's label (its identity in the
/// baseline) and the reason.
typedef _G5Failure = ({String check, String node, String reason});

/// Gate G5 on what is on screen now: the three platform guidelines and the
/// reading order (A11Y-1, A11Y-4, A11Y-6, LAY-9), less the (check, node)
/// pairs that [shot] still has in the baseline. Semantics must be on
/// ([WidgetTester.ensureSemantics]). [baseline] replaces the committed file
/// in the gate's own tests; it is held to the same ceiling.
Future<void> expectKitGalleryAccessible(
  WidgetTester tester, {
  required String shot,
  required TextDirection direction,
  @visibleForTesting KitGalleryG5Baseline? baseline,
}) async {
  final entries = baseline ?? readKitGalleryG5Baseline();
  final over = kitGalleryG5OverCeiling(entries);
  if (over.isNotEmpty) {
    fail(
      'G5 (STANDARDS.md §18) is absolute: $kitGalleryG5BaselinePath may '
      'only shrink, and these entries are outside kitGalleryG5Ceiling in '
      'test/goldens/kit/kit_gallery.dart:\n${over.join('\n')}\n'
      'Fix the part instead.',
    );
  }

  final failures = <_G5Failure>[];
  for (final MapEntry(key: check, value: guideline) in _guidelines.entries) {
    final result = await guideline.evaluate(tester);
    if (!result.passed) {
      failures.addAll(
        _splitGuidelineReason(check, result.reason ?? guideline.description),
      );
    }
  }
  for (final (node, problem) in _readingOrderProblems(
    tester.semantics.simulatedAccessibilityTraversal(),
    direction: direction,
    view: tester.view,
    ignore: _barrierNodeIds(),
  )) {
    failures.add((
      check: _readingOrder,
      node: node,
      reason:
          'The screen reader must read top to bottom, then start to end '
          '(A11Y-4). Fix the part\'s widget order or its semantics sort '
          'keys: $problem',
    ));
  }

  final allowed = entries[shot] ?? const {};
  final seen = <(String, String)>{};
  final fresh = <_G5Failure>[];
  for (final failure in failures) {
    if (allowed[failure.check]?.contains(failure.node) ?? false) {
      seen.add((failure.check, failure.node));
    } else {
      fresh.add(failure);
    }
  }
  final stale = [
    for (final MapEntry(key: check, value: labels) in allowed.entries)
      for (final label in labels)
        if (!seen.contains((check, label))) '[$check] "$label"',
  ];
  final messages = [
    for (final failure in fresh)
      '[${failure.check}] node "${failure.node}": ${failure.reason}',
    if (stale.isNotEmpty)
      'Stale baseline: $shot now passes ${stale.join(', ')}. Delete '
          '${stale.length == 1 ? 'that entry' : 'those entries'} from '
          '$kitGalleryG5BaselinePath (it only shrinks; a stale entry would '
          'hide a later regression).',
  ];
  if (messages.isNotEmpty) {
    fail(
      'G5 (docs/ux-system/revamp/STANDARDS.md §18) failed for $shot:\n'
      '${messages.join('\n\n')}',
    );
  }
}

/// Splits a guideline's reason, which joins one paragraph per failing node
/// (each starting with that node's `SemanticsNode#…` description), into
/// failures keyed by the node's label (or tooltip; empty when it has none).
List<_G5Failure> _splitGuidelineReason(String check, String reason) {
  final starts = [
    for (final match in RegExp(
      r'^SemanticsNode#\d+\(',
      multiLine: true,
    ).allMatches(reason))
      match.start,
  ];
  if (starts.isEmpty) return [(check: check, node: '', reason: reason)];
  if (starts.first != 0) starts.insert(0, 0);
  return [
    for (var i = 0; i < starts.length; i++)
      () {
        final part = reason
            .substring(
              starts[i],
              i + 1 < starts.length ? starts[i + 1] : reason.length,
            )
            .trim();
        final name =
            RegExp(r'\blabel: "((?:[^"\\]|\\.)*)"').firstMatch(part) ??
            RegExp(r'\btooltip: "((?:[^"\\]|\\.)*)"').firstMatch(part);
        return (check: check, node: name?.group(1) ?? '', reason: part);
      }(),
  ];
}

/// Semantics nodes of modal barriers. The framework sorts a dismissible
/// barrier after its route on purpose (ModalRoute gives it
/// `OrdinalSortKey(1.0)`), so "tap to close" is read after the sheet; it is
/// not content and has no place in the reading order.
Set<int> _barrierNodeIds() {
  final ids = <int>{};
  void visit(Element element) {
    if (element is RenderObjectElement) {
      final id = element.renderObject.debugSemantics?.id;
      if (id != null) ids.add(id);
    }
    element.visitChildren(visit);
  }

  for (final barrier
      in find.byType(ModalBarrier, skipOffstage: false).evaluate()) {
    visit(barrier);
  }
  return ids;
}

/// A11Y-4 on a traversal: a visible node must not sit wholly above the node
/// read just before it, nor wholly on its start side while starting on the
/// same line. Nodes that overlap (a row and its trailing button) have no
/// order between them and are skipped, as are the [ignore]d ids.
List<String> kitReadingOrderProblems(
  Iterable<SemanticsNode> traversal, {
  required TextDirection direction,
  required FlutterView view,
  Set<int> ignore = const {},
}) => [
  for (final (_, problem) in _readingOrderProblems(
    traversal,
    direction: direction,
    view: view,
    ignore: ignore,
  ))
    problem,
];

/// [kitReadingOrderProblems] with the label of each node read out of order.
List<(String, String)> _readingOrderProblems(
  Iterable<SemanticsNode> traversal, {
  required TextDirection direction,
  required FlutterView view,
  Set<int> ignore = const {},
}) {
  const slack = 1.0;
  final screen = Offset.zero & (view.physicalSize / view.devicePixelRatio);
  final nodes = <(SemanticsNode, Rect)>[];
  for (final node in traversal) {
    if (node.flagsCollection.isHidden || ignore.contains(node.id)) continue;
    final rect = _globalRect(node, view.devicePixelRatio);
    if (rect.isEmpty || !rect.overlaps(screen)) continue;
    nodes.add((node, rect));
  }

  final problems = <(String, String)>[];
  for (var i = 1; i < nodes.length; i++) {
    final (before, a) = nodes[i - 1];
    final (after, b) = nodes[i];
    final beforePane = _adaptivePane(before);
    final afterPane = _adaptivePane(after);
    if (beforePane != null && afterPane != null && beforePane != afterPane) {
      // Adaptive columns are traversed independently, from start to end.
      // Starting at the top of the next column is not a reversed row.
      final aPane = _globalRect(beforePane, view.devicePixelRatio);
      final bPane = _globalRect(afterPane, view.devicePixelRatio);
      final forward = direction == TextDirection.ltr
          ? bPane.left >= aPane.right - slack
          : bPane.right <= aPane.left + slack;
      if (!forward) {
        problems.add((
          _text(after),
          'adaptive panes go back towards the start: '
              '${_describe(after, b)} after ${_describe(before, a)}',
        ));
      }
      continue;
    }
    final overlap = a.intersect(b);
    if (overlap.width > slack && overlap.height > slack) continue;
    final bool backwards = direction == TextDirection.ltr
        ? b.right <= a.left + slack
        : b.left >= a.right - slack;
    final String? why;
    if (b.bottom <= a.top + slack) {
      why = 'goes back up';
    } else if (backwards &&
        b.top <= a.top + slack &&
        b.center.dy < a.bottom &&
        a.center.dy < b.bottom) {
      why = 'goes back towards the start of the line';
    } else {
      why = null;
    }
    if (why != null) {
      problems.add((
        _text(after),
        '${_describe(after, b)} is read after ${_describe(before, a)}: $why',
      ));
    }
  }
  return problems;
}

SemanticsNode? _adaptivePane(SemanticsNode node) {
  for (
    SemanticsNode? current = node;
    current != null;
    current = current.parent
  ) {
    if (current.identifier.startsWith(KitScreen.paneSemanticsPrefix)) {
      return current;
    }
  }
  return null;
}

String _text(SemanticsNode node) {
  final data = node.getSemanticsData();
  return [
    data.label,
    data.value,
    data.tooltip,
  ].where((part) => part.isNotEmpty).join(' / ').replaceAll('\n', ' ');
}

String _describe(SemanticsNode node, Rect rect) {
  final text = _text(node);
  return '#${node.id} "$text" at (${rect.left.round()}, ${rect.top.round()}, '
      '${rect.right.round()}, ${rect.bottom.round()})';
}

Rect _globalRect(SemanticsNode node, double devicePixelRatio) {
  var rect = node.rect;
  for (SemanticsNode? at = node; at != null; at = at.parent) {
    final transform = at.transform;
    if (transform != null) rect = MatrixUtils.transformRect(transform, rect);
  }
  return Rect.fromLTRB(
    rect.left / devicePixelRatio,
    rect.top / devicePixelRatio,
    rect.right / devicePixelRatio,
    rect.bottom / devicePixelRatio,
  );
}

/// Pumps [child] as a screen's body at [size] (a part that is not a modal:
/// rows, buttons, cards, type), settles, and compares the whole window with
/// `goldens/kit/<name>.png`.
Future<void> kitGalleryPart(
  WidgetTester tester, {
  required String name,
  required Size size,
  required bool light,
  required Widget child,
  Locale locale = const Locale('en'),
  double textScale = 1,
  bool removeAnimations = true,
}) async {
  final own = _themeName(light);
  if (!name.endsWith('_$own')) {
    throw ArgumentError.value(
      name,
      'name',
      'G5 (STANDARDS.md §18, A11Y-6): a gallery shot name ends in "_$own" '
          'when light is $light',
    );
  }
  final stem = name.substring(0, name.length - own.length - 1);
  // TEST-9: DPR 3.0, [size] in logical pixels.
  tester.view.physicalSize = size * 3.0;
  tester.view.devicePixelRatio = 3.0;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  final semantics = tester.ensureSemantics();
  // ARCH-11: rendered as Android.
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  try {
    // G5 checks both themes (A11Y-6); the golden is the shot's own theme.
    for (final pass in [!light, light]) {
      late BuildContext context;
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: _theme(light: pass),
            locale: locale,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                disableAnimations: removeAnimations,
                textScaler: TextScaler.linear(textScale),
              ),
              child: child!,
            ),
            home: Scaffold(
              body: Builder(
                builder: (inner) {
                  context = inner;
                  return SafeArea(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      child: Center(
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 720),
                          child: child,
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectKitGalleryAccessible(
        tester,
        shot: '${stem}_${_themeName(pass)}',
        direction: Directionality.of(context),
      );
    }
  } finally {
    debugDefaultTargetPlatformOverride = null;
    semantics.dispose();
  }
  await expectLater(find.byKey(boundary), matchesGoldenFile('$name.png'));
}
