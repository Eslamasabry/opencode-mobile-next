// The G6 kit overflow matrix engine (docs/ux-system/revamp/STANDARDS.md §18,
// A11Y-2, LAY-4, KIT-24), shared by test/text_scale_overflow_test.dart (the
// manifest, the ceiling and KIT-24 self-tests) and the shard files
// test/text_scale_overflow_matrix_<n>_test.dart that pump the scenes. The
// matrix is split across shards only so `flutter test` can run them side by
// side; every scene is pumped by exactly one shard (round robin over
// kitOverflowScenes) and the main file checks that the shards cover them all.
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderShiftedBox;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_choice_list.dart' show KitChoiceRow;
import 'package:opencode_mobile/ui/kit/kit_segmented.dart' show KitSegmented;

import '../../tool/capture/fixtures.dart' show captureTheme;
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'kit_overflow_scenes.dart';

/// LAY-4's overflow widths (320, 360, 412, 600, 800, 840, 1280, 1600) at a
/// plausible height for each, plus the 915x412 phone in landscape.
const kitOverflowSizes = <Size>[
  Size(320, 640),
  Size(360, 740),
  Size(412, 915),
  Size(600, 960),
  Size(800, 1280),
  Size(840, 1180),
  Size(1280, 800),
  Size(1600, 1000),
  Size(915, 412),
];

const kitOverflowScales = <double>[1.0, 1.3, 2.0];

/// The ceiling of the ratchet: every combination that overflowed when the
/// gate was built (code head 4cc835fd), with its first error line. The
/// committed baseline may hold only these combinations, each with the same
/// error and an overflow no larger. Nothing is ever added here (kit units
/// never edit this file, PROC-13); a new scene or combination that overflows
/// fails.
///
/// The KitActionBlock and KitSheet entries the gate was built with left
/// when the visual language merge (ddcb6bc7) made them fit, so they are
/// gone from here too. The one addition is that merge's own code, added
/// once by the integrator when the merge met the gate
/// (docs/qa/integrate-vl-gates-2026-09-26/README.md): KitRowValue as a
/// KitRow's trailing value takes its full width at text 2.0 on a narrow
/// phone. kit-KitRow-v2 removes it (docs/ux-system/kit-api/KitRow.md,
/// test 11: no overflow at 2.0 and 320 dp).
const kitOverflowCeiling = <String, Map<String, String>>{
  'KitRowGroup/default': {
    '320x640 text 2.0 ltr':
        'A RenderFlex overflowed by 55 pixels on the right.',
    '320x640 text 2.0 rtl':
        'A RenderFlex overflowed by 55 pixels on the right.',
    '360x740 text 2.0 ltr':
        'A RenderFlex overflowed by 15 pixels on the right.',
    '360x740 text 2.0 rtl':
        'A RenderFlex overflowed by 15 pixels on the right.',
  },
};

/// The committed baseline: the entries of [kitOverflowCeiling] that still
/// overflow. It must exist, must equal what the matrix observes, and may
/// only shrink.
const kitOverflowBaselinePath = 'test/text_scale_overflow_baseline.json';

typedef KitOverflows = Map<String, Map<String, String>>;

/// The baseline, or null when the file is missing (which fails the gate;
/// it is never recreated from observations).
KitOverflows? loadKitOverflowBaseline() {
  final file = File(kitOverflowBaselinePath);
  if (!file.existsSync()) return null;
  final json = jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
  return {
    for (final MapEntry(:key, :value) in json.entries)
      key: {
        for (final MapEntry(:key, :value)
            in (value as Map<String, dynamic>).entries)
          key: value as String,
      },
  };
}

String encodeKitOverflowBaseline(KitOverflows baseline) {
  final ids = baseline.keys.where((id) => baseline[id]!.isNotEmpty).toList()
    ..sort();
  return '${const JsonEncoder.withIndent('  ').convert({
    for (final id in ids) id: {for (final combo in baseline[id]!.keys.toList()..sort()) combo: baseline[id]![combo]},
  })}\n';
}

final _pixels = RegExp(r'([\d.]+) pixels');

/// The error with its pixel amount taken out, and the amount.
(String, double?) _errorShape(String line) {
  final m = _pixels.firstMatch(line);
  return (
    line.replaceAll(_pixels, '# pixels'),
    m == null ? null : double.tryParse(m[1]!),
  );
}

/// Compares [observed] failures with the [allowed] ones for one scene:
/// `worse` is every failure not allowed (a new combination, a different
/// error, a larger overflow); `better` is every allowed failure that is gone
/// or smaller, which the same change must remove from the baseline.
({List<String> worse, List<String> better}) compareKitOverflows(
  Map<String, String> observed,
  Map<String, String> allowed,
) {
  final worse = <String>[];
  final better = <String>[];
  for (final MapEntry(:key, :value) in observed.entries) {
    final known = allowed[key];
    if (known == null) {
      worse.add('$key: $value');
      continue;
    }
    final (shape, amount) = _errorShape(value);
    final (knownShape, knownAmount) = _errorShape(known);
    if (shape != knownShape || (amount ?? 0) > (knownAmount ?? 0)) {
      worse.add('$key: $value (baselined: $known)');
    } else if ((amount ?? 0) < (knownAmount ?? 0)) {
      better.add('$key: now $value (baselined: $known)');
    }
  }
  for (final MapEntry(:key, :value) in allowed.entries) {
    if (!observed.containsKey(key)) {
      better.add('$key: fixed (baselined: $value)');
    }
  }
  return (worse: worse, better: better);
}

String kitSizeName(Size size) => '${size.width.toInt()}x${size.height.toInt()}';

/// Built once: the theme is identical for every combination, and building it
/// (text themes, component themes) was a visible share of each pump.
final ThemeData _kitTheme = captureTheme();

Widget kitOverflowApp({
  required Key key,
  required double scale,
  required bool rtl,
  required Widget Function(BuildContext context) home,
}) => KeyedSubtree(
  key: key,
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: _kitTheme,
    locale: Locale(rtl ? 'ar' : 'en'),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(disableAnimations: true, textScaler: TextScaler.linear(scale)),
      child: child!,
    ),
    home: Scaffold(body: Builder(builder: home)),
  ),
);

// ---------------------------------------------------------------------------
// KIT-24.

bool isKitSegmented(Widget widget) => widget is KitSegmented<Object?>;

bool isKitChoiceRow(Widget widget) => widget is KitChoiceRow<Object?>;

/// KIT-24 on the pumped tree, for a scene whose labels do not fit: every
/// KitSegmented is full width and is a vertical stack of at least two
/// full-width KitChoiceRows, and draws no text outside them (the horizontal
/// segmented track is gone). Returns why it fails, or null.
String? kit24Problem({
  bool Function(Widget) isSegmented = isKitSegmented,
  bool Function(Widget) isChoiceRow = isKitChoiceRow,
}) {
  final segmented = find.byWidgetPredicate(isSegmented).evaluate().toList();
  if (segmented.isEmpty) {
    return 'labels do not fit, but no KitSegmented is shown (KIT-24)';
  }
  for (final part in segmented) {
    var partBox = part.renderObject! as RenderBox;
    // The part builds inside a KitMaxWidth (kit-v2 §8.6): its rows fill the
    // box the cap leaves it, not the wider box the caller gave.
    if (partBox.runtimeType.toString() == '_RenderKitMaxWidth') {
      partBox = (partBox as RenderShiftedBox).child!;
    }
    final available = partBox.constraints.maxWidth;
    if (available.isFinite && partBox.size.width < available - 0.5) {
      return 'KitSegmented is ${partBox.size.width} of $available dp '
          '(KIT-24: full width)';
    }
    final of = find.byElementPredicate((e) => identical(e, part));
    final rows =
        find
            .descendant(of: of, matching: find.byWidgetPredicate(isChoiceRow))
            .evaluate()
            .map((e) => e.renderObject! as RenderBox)
            .map((b) => b.localToGlobal(Offset.zero) & b.size)
            .toList()
          ..sort((a, b) => a.top.compareTo(b.top));
    if (rows.length < 2) {
      return 'labels do not fit, but KitSegmented shows ${rows.length} '
          'KitChoiceRow(s), not a stack (KIT-24)';
    }
    for (var i = 0; i < rows.length; i++) {
      if (rows[i].width < partBox.size.width - 0.5) {
        return 'a KitChoiceRow is ${rows[i].width} of the part\'s '
            '${partBox.size.width} dp (KIT-24: full-width rows)';
      }
      if (i > 0 && rows[i].top < rows[i - 1].bottom - 0.5) {
        return 'KitChoiceRows overlap or sit side by side, not stacked '
            '(KIT-24)';
      }
    }
    final texts = find
        .descendant(of: of, matching: find.byType(RichText))
        .evaluate();
    for (final text in texts) {
      var inRow = false;
      text.visitAncestorElements((ancestor) {
        if (isChoiceRow(ancestor.widget)) {
          inRow = true;
          return false;
        }
        return !identical(ancestor, part);
      });
      if (!inRow) {
        return 'KitSegmented still draws '
            '"${(text.widget as RichText).text.toPlainText()}" outside its '
            'KitChoiceRows (KIT-24: the track gives way to the stack)';
      }
    }
  }
  return null;
}

/// A phone, portrait or landscape: where KIT-24's stack is checked at 2.0.
bool kitIsPhone(Size size) => size.shortestSide < 600;

/// One combination the harness shows: the app shell stays mounted between
/// combinations and only these values (and a fresh key on the scene, so no
/// widget state carries over) change.
class _Combo {
  const _Combo(this.key, this.scale, this.rtl);

  final Key key;
  final double scale;
  final bool rtl;
}

/// Pumps [scene] at every size, scale and direction and returns, for each
/// combination that threw (an overflow included) or broke KIT-24, its first
/// line, keyed by the combination ("800x1280 text 2.0 ltr").
///
/// The MaterialApp is built once per scene and updated in place for each
/// combination (rebuilding the whole shell 54 times per scene was most of the
/// matrix's time). The scene itself is rebuilt from scratch every time, and
/// routes a modal scene opened are popped first.
Future<Map<String, String>> pumpKitMatrix(
  WidgetTester tester,
  KitOverflowScene scene,
) async {
  final failures = <String, String>{};
  addTearDown(tester.view.reset);
  final combo = ValueNotifier<_Combo>(
    const _Combo(ValueKey('start'), 1.0, false),
  );
  final navigator = GlobalKey<NavigatorState>();
  BuildContext? opener;

  Widget content(BuildContext context, _Combo c) {
    final copy = KitSceneCopy(rtl: c.rtl);
    if (scene.open != null) {
      opener = context;
      return const SizedBox.expand();
    }
    final part = scene.build!(context, copy);
    return switch (scene.host) {
      KitOverflowHost.list => ListView(
        padding: const EdgeInsets.all(16),
        children: [part],
      ),
      _ => part,
    };
  }

  final home = Scaffold(
    body: ValueListenableBuilder<_Combo>(
      valueListenable: combo,
      builder: (context, c, _) => KeyedSubtree(
        key: c.key,
        child: Builder(builder: (b) => content(b, c)),
      ),
    ),
  );
  Widget app() => ValueListenableBuilder<_Combo>(
    valueListenable: combo,
    builder: (context, c, _) => MaterialApp(
      navigatorKey: navigator,
      debugShowCheckedModeBanner: false,
      theme: _kitTheme,
      locale: Locale(c.rtl ? 'ar' : 'en'),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          disableAnimations: true,
          textScaler: TextScaler.linear(c.scale),
        ),
        child: child!,
      ),
      home: home,
    ),
  );

  var mounted = false;
  for (final size in kitOverflowSizes) {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = size;
    for (final scale in kitOverflowScales) {
      for (final rtl in [false, true]) {
        final where = '${kitSizeName(size)} text $scale ${rtl ? 'rtl' : 'ltr'}';
        final copy = KitSceneCopy(rtl: rtl);
        if (mounted) {
          navigator.currentState?.popUntil((r) => r.isFirst);
          await tester.pump(const Duration(milliseconds: 400));
          tester.takeException();
        }
        combo.value = _Combo(ValueKey(where), scale, rtl);
        if (!mounted) {
          await tester.pumpWidget(app());
          mounted = true;
        } else {
          await tester.pump();
        }
        if (scene.open != null) {
          unawaited(Future.sync(() => scene.open!(opener!, copy)));
          await tester.pump();
        }
        await tester.pump(const Duration(milliseconds: 400));
        await tester.pump(const Duration(milliseconds: 400));
        final error = tester.takeException();
        if (error != null) {
          failures[where] = '$error'.split('\n').first;
          continue;
        }
        if (scene.labelsOverflow && scale >= 2.0 && kitIsPhone(size)) {
          final problem = kit24Problem();
          if (problem != null) failures[where] = problem;
        }
      }
    }
  }
  // Leave no route or ticker behind for the next scene.
  await tester.pumpWidget(const SizedBox());
  combo.dispose();
  return failures;
}

/// How many files pump the scenes (`test/text_scale_overflow_matrix_N_test`).
const kitOverflowShards = 6;

/// Registers the matrix tests for the scenes of shard [shard]: scene index
/// modulo [kitOverflowShards]. Each shard checks its scenes against the
/// committed baseline and prints the tighter baseline it would commit.
void registerKitOverflowShard(int shard) {
  TestWidgetsFlutterBinding.ensureInitialized();
  group('G6 kit overflow matrix shard $shard', () {
    setUpAll(loadKitGalleryFonts);
    final baseline = loadKitOverflowBaseline();
    final observed = <String, Map<String, String>>{};
    final scenes = [
      for (var i = shard; i < kitOverflowScenes.length; i += kitOverflowShards)
        kitOverflowScenes[i],
    ];

    for (final scene in scenes) {
      testWidgets(
        '${scene.id} fits every overflow size, text scale and direction',
        (tester) async {
          final failures = await pumpKitMatrix(tester, scene);
          observed[scene.id] = failures;
          final (:worse, :better) = compareKitOverflows(
            failures,
            baseline?[scene.id] ?? const {},
          );
          expect(
            worse,
            isEmpty,
            reason:
                '${scene.id} overflowed or threw in ${worse.length} of '
                '${kitOverflowSizes.length * kitOverflowScales.length * 2} '
                'combinations beyond $kitOverflowBaselinePath:\n${worse.join('\n')}',
          );
          expect(
            better,
            isEmpty,
            reason:
                '${scene.id} improved. Tighten $kitOverflowBaselinePath in the same '
                'change (G6_OVERFLOW_WRITE=1 writes it), so a later '
                'regression at these combinations fails:\n${better.join('\n')}',
          );
        },
      );
    }

    // The ratchet: once every scene ran, print the tighter baseline to commit
    // (or write it with G6_OVERFLOW_WRITE=1). It keeps only baselined
    // entries that still fail no worse, so it never grows.
    tearDownAll(() {
      final current = baseline;
      if (current == null || observed.length != scenes.length) {
        return;
      }
      final next = <String, Map<String, String>>{
        for (final MapEntry(:key, :value) in observed.entries)
          key: {
            for (final MapEntry(key: combo, value: line) in value.entries)
              if (current[key]?[combo] != null &&
                  !compareKitOverflows(
                    {combo: line},
                    {combo: current[key]![combo]!},
                  ).worse.isNotEmpty)
                combo: line,
          },
      };
      // Other shards own the other scenes: only this shard's entries change.
      final text = encodeKitOverflowBaseline({...current, ...next});
      if (text == encodeKitOverflowBaseline(current)) return;
      if (Platform.environment['G6_OVERFLOW_WRITE'] == '1') {
        File(kitOverflowBaselinePath).writeAsStringSync(text);
        stdout.writeln('G6_OVERFLOW_WRITE=1: wrote $kitOverflowBaselinePath');
      } else {
        stdout.writeln(
          '--- G6 overflow baseline tightened (commit as $kitOverflowBaselinePath) ---\n'
          '$text--- end baseline ---',
        );
      }
    });
  });
}
