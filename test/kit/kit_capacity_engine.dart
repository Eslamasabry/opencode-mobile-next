// The kit capacity gate's engine (docs/ux-system/kit-v2.md §8.6), shared by
// test/kit/kit_capacity_test.dart (the contract, coverage and probe
// self-tests) and the shard files test/kit/kit_capacity_shard_<n>_test.dart
// that pump the scenes.
//
// Every scene of the G6 overflow matrix (test/kit/kit_overflow_scenes.dart,
// one or more per exported part) and every 50-item list run
// (test/kit/kit_capacity_lists.dart) is pumped at 280, 412 and 1,600 dp, at
// text 1.0 and 2.0, as written and with the stress fixtures
// (test/support/stress_content.dart) that [kitCapacityFixtures] picks for
// each size and direction; a fixture replaces every string the scene passes
// through KitSceneCopy.t. The pseudo fixture
// also pseudo-localizes, in place, the words the part writes itself. Each
// pump is held to the contract in test/kit/kit_capacity.json; what breaks
// it today is in test/kit/kit_capacity_allowlist.json, which only shrinks.
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';

import '../../tool/capture/fixtures.dart' show captureTheme;
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/stress_content.dart';
import 'kit_capacity_contract.dart';
import 'kit_capacity_lists.dart';
import 'kit_capacity_probe.dart';
import 'kit_overflow_scenes.dart';

/// The narrowest phone the kit must hold (a small phone with large display
/// size), the census phone, and a large PC window.
const kitCapacitySizes = <Size>[
  Size(280, 640),
  Size(412, 915),
  Size(1600, 1000),
];

const kitCapacityScales = <double>[1.0, 2.0];

/// The fixtures each size is pumped with. The narrowest phone takes every
/// fixture in both directions (that is where content breaks a part); the
/// census phone the three that grow text most; the PC window, left to right
/// only, the ones that make a part as wide as it gets, for the width caps.
List<StressFixture?> kitCapacityFixtures(Size size, {required bool rtl}) =>
    switch (size.width) {
      280 => [null, ...StressFixture.values],
      412 => [null, StressFixture.pseudo, StressFixture.prose],
      _ =>
        rtl
            ? const []
            : [
                null,
                StressFixture.prose,
                StressFixture.token,
                StressFixture.numbers,
                StressFixture.pseudo,
              ],
    };

/// Where widths are measured against the caps.
const kitCapacityWideWidth = 1600.0;

/// Where text slots are read and tap targets checked: the census phone at
/// text 1.0, left to right, as written.
const kitCapacityCensus = Size(412, 915);

/// How many shard files pump the scenes.
const kitCapacityShards = 8;

/// One pump of the gate: a scene with a unique id, and the list slot it
/// stresses with 50 items, if it is a list run.
class KitCapacityCase {
  const KitCapacityCase(this.id, this.scene, {this.list});

  final String id;
  final KitOverflowScene scene;
  final KitListStress? list;

  /// The part the scene's text slots are declared under.
  String get part => scene.parts.first;
}

/// Every case, in a fixed order: the overflow scenes, then the list runs.
/// Ids repeat in the overflow registry (two `KitMessage/reply`), so a
/// repeat gets `#2`, `#3`.
List<KitCapacityCase> kitCapacityCases() {
  final seen = <String, int>{};
  String unique(String id) {
    final n = (seen[id] ?? 0) + 1;
    seen[id] = n;
    return n == 1 ? id : '$id#$n';
  }

  return [
    for (final scene in kitOverflowScenes)
      KitCapacityCase(unique(scene.id), scene),
    for (final list in kitListStress)
      KitCapacityCase(unique(list.scene.id), list.scene, list: list),
  ];
}

/// What one case did across every combination.
class KitCapacityObservation {
  KitCapacityObservation(this.subject);

  final KitCapacityCase subject;

  /// Combination -> first line of the error.
  final errors = <String, String>{};

  /// Combination -> the first silent cut and how many there were.
  final cuts = <String, String>{};

  /// Part -> widest painted width at 1,600 dp and where.
  final widths = <String, ({double width, String where})>{};

  /// Parts the scene names that were never found in its tree.
  final missing = <String>{};

  /// Text slot key -> its rule, at the census combination.
  final slots = <String, String>{};

  /// androidTapTargetGuideline's complaints at the census combination.
  final targets = <String>[];

  /// The most list items drawn on screen at once (list runs only).
  int itemsDrawn = 0;

  Map<String, Object?> toJson() => {
    'id': subject.id,
    'errors': errors.length,
    if (errors.isNotEmpty) 'firstError': _first(errors),
    'cuts': cuts.length,
    if (cuts.isNotEmpty) 'firstCut': _first(cuts),
    'widths': {
      for (final MapEntry(:key, :value) in widths.entries)
        key: value.width.roundToDouble(),
    },
    if (missing.isNotEmpty) 'missing': missing.toList(),
    'targets': targets.length,
    if (subject.list != null) 'itemsDrawn': itemsDrawn,
  };
}

String _first(Map<String, String> byWhere) {
  final first = byWhere.entries.first;
  return '${first.key}: ${first.value}';
}

final ThemeData _theme = captureTheme();

class _Combo {
  const _Combo(this.key, this.scale, this.rtl, this.fixture);

  final Key key;
  final double scale;
  final bool rtl;
  final StressFixture? fixture;
}

String _typeName(Widget widget) {
  final name = widget.runtimeType.toString();
  final generic = name.indexOf('<');
  return generic < 0 ? name : name.substring(0, generic);
}

RenderObject? _overlay() {
  final overlays = find.byType(Overlay, skipOffstage: false).evaluate();
  return overlays.isEmpty ? null : overlays.first.renderObject;
}

/// The page route under a modal and every modal barrier: what an opener's
/// width leaves out.
Set<RenderObject> _underModal() {
  final scopes = find
      .byWidgetPredicate(
        (w) => w.runtimeType.toString().startsWith('_ModalScope'),
        skipOffstage: false,
      )
      .evaluate()
      .toList();
  return {
    if (scopes.isNotEmpty) ?scopes.first.renderObject,
    for (final barrier
        in find.byType(ModalBarrier, skipOffstage: false).evaluate())
      ?barrier.renderObject,
  };
}

/// The widest any instance of [part] paints, or null when none is in the
/// tree. An opener (`showKit…`) is measured as everything it put above the
/// page, scrims and barriers left out.
double? _partWidth(String part, Rect window) {
  final overlay = _overlay();
  if (overlay == null) return null;
  if (part.startsWith('showKit')) {
    return paintedWidth(
      overlay,
      window: window,
      exclude: _underModal(),
      skipFullWindow: true,
    );
  }
  final instances = find
      .byWidgetPredicate((w) => _typeName(w) == part, skipOffstage: false)
      .evaluate();
  double? widest;
  for (final element in instances) {
    final render = element.renderObject;
    if (render == null || !render.attached) continue;
    // A scrim that dims the whole window is the page's, not the part's
    // (a floating panel draws its own).
    final width = paintedWidth(render, window: window, skipFullWindow: true);
    widest = widest == null ? width : math.max(widest, width);
  }
  return widest;
}

int _itemsOnScreen(String item, Rect window) {
  var count = 0;
  for (final element
      in find
          .byWidgetPredicate((w) => _typeName(w) == item, skipOffstage: false)
          .evaluate()) {
    final render = element.renderObject;
    if (render is! RenderBox) continue;
    var hidden = false;
    for (RenderObject? a = render; a != null; a = a.parent) {
      if ((a is RenderOffstage && a.offstage) ||
          (a is RenderOpacity && a.opacity == 0) ||
          (a is RenderAnimatedOpacity && a.opacity.value == 0)) {
        hidden = true;
        break;
      }
    }
    final rect = globalRect(render)?.intersect(window);
    if (!hidden && rect != null && rect.width > 0 && rect.height > 0) {
      count++;
    }
  }
  return count;
}

/// Pumps [subject] at every size, scale, direction and fixture and records
/// what it did. The app shell stays mounted; the scene is rebuilt from
/// scratch for each combination and any route it opened is popped first,
/// as in the G6 matrix.
Future<KitCapacityObservation> pumpKitCapacity(
  WidgetTester tester,
  KitCapacityCase subject,
) async {
  final scene = subject.scene;
  final observation = KitCapacityObservation(subject);
  addTearDown(tester.view.reset);
  final combo = ValueNotifier<_Combo>(
    const _Combo(ValueKey('start'), 1.0, false, null),
  );
  final navigator = GlobalKey<NavigatorState>();
  BuildContext? opener;

  KitSceneCopy copyFor(_Combo c) => KitSceneCopy(
    rtl: c.rtl,
    stress: c.fixture == null
        ? null
        : (text, {required rtl}) =>
              StressContent.forSlot(c.fixture!, text, rtl: rtl),
  );

  Widget content(BuildContext context, _Combo c) {
    if (scene.open != null) {
      opener = context;
      return const SizedBox.expand();
    }
    final part = scene.build!(context, copyFor(c));
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
      theme: _theme,
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
  for (final size in kitCapacitySizes) {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = size;
    final window = Offset.zero & size;
    for (final scale in kitCapacityScales) {
      for (final rtl in [false, true]) {
        for (final fixture in kitCapacityFixtures(size, rtl: rtl)) {
          final where =
              '${size.width.toInt()}x${size.height.toInt()} text $scale '
              '${rtl ? 'rtl' : 'ltr'} ${fixture?.name ?? 'as written'}';
          final census =
              size == kitCapacityCensus &&
              scale == 1.0 &&
              !rtl &&
              fixture == null;
          if (mounted) {
            navigator.currentState?.popUntil((r) => r.isFirst);
            await tester.pump(const Duration(milliseconds: 400));
            tester.takeException();
          }
          final semantics = census ? tester.ensureSemantics() : null;
          try {
            combo.value = _Combo(ValueKey(where), scale, rtl, fixture);
            if (!mounted) {
              await tester.pumpWidget(app());
              mounted = true;
            } else {
              await tester.pump();
            }
            if (scene.open != null) {
              unawaited(
                Future.sync(() => scene.open!(opener!, copyFor(combo.value))),
              );
              await tester.pump();
            }
            await tester.pump(const Duration(milliseconds: 400));
            await tester.pump(const Duration(milliseconds: 400));
            final overlay = _overlay();
            if (fixture == StressFixture.pseudo && overlay != null) {
              pseudoLocalizeTree(overlay);
              await tester.pump();
            }
            final error = tester.takeException();
            if (error != null) {
              observation.errors[where] = '$error'.split('\n').first;
              continue;
            }
            final root = _overlay();
            if (root == null) continue;

            final cuts = <String>[];
            visitShown(root, (node) {
              if (node is RenderParagraph) {
                final why = silentCut(node);
                if (why != null) cuts.add(why);
              }
            });
            if (cuts.isNotEmpty) {
              observation.cuts[where] =
                  '${cuts.first}${cuts.length > 1 ? ' (+${cuts.length - 1} more)' : ''}';
            }

            if (size.width == kitCapacityWideWidth) {
              for (final part in scene.parts) {
                final width = _partWidth(part, window);
                if (width == null) {
                  observation.missing.add(part);
                  continue;
                }
                final known = observation.widths[part];
                if (known == null || width > known.width) {
                  observation.widths[part] = (width: width, where: where);
                }
              }
            }

            if (subject.list != null && fixture == null) {
              observation.itemsDrawn = math.max(
                observation.itemsDrawn,
                _itemsOnScreen(subject.list!.item, window),
              );
            }

            if (census) {
              visitShown(root, (node) {
                if (node is! RenderParagraph || isIconText(node)) return;
                final text = node.text.toPlainText();
                final key = slotKey(text);
                if (key.isEmpty) return;
                final rule = textRule(node);
                var unique = key;
                for (
                  var n = 2;
                  observation.slots.containsKey(unique) &&
                      observation.slots[unique] != rule;
                  n++
                ) {
                  unique = '$key #$n';
                }
                observation.slots[unique] = rule;
              });
              final evaluation = await androidTapTargetGuideline.evaluate(
                tester,
              );
              if (!evaluation.passed) {
                observation.targets.addAll(
                  evaluation.reason!
                      .split('\n')
                      .where((l) => l.trim().isNotEmpty),
                );
              }
            }
          } finally {
            semantics?.dispose();
          }
        }
      }
    }
  }
  // Every part found at some width counts as found.
  observation.missing.removeAll(observation.widths.keys);
  await tester.pumpWidget(const SizedBox());
  combo.dispose();
  return observation;
}

/// The contract violations in [o]: check -> subject -> value and note.
/// Every subject starts with the case id, so a shard owns its entries.
Map<String, KitCapacityEntries> kitCapacityViolations(
  KitCapacityObservation o,
  KitCapacityContract contract,
) {
  final id = o.subject.id;
  final out = <String, KitCapacityEntries>{
    for (final c in kitCapacityChecks) c: {},
  };
  if (o.errors.isNotEmpty) {
    out['error']![id] = (value: o.errors.length, note: _first(o.errors));
  }
  if (o.cuts.isNotEmpty) {
    out['cut']![id] = (value: o.cuts.length, note: _first(o.cuts));
  }
  for (final MapEntry(key: part, value: w) in o.widths.entries) {
    final cap = contract.capOf(part);
    if (cap != null && w.width > cap + .5) {
      out['width']!['$id · $part'] = (
        value: w.width.roundToDouble(),
        note:
            'cap ${contract.tokenOf(part)} = ${cap.toInt()} dp; widest at '
            '${w.where}',
      );
    }
  }
  if (o.targets.isNotEmpty) {
    out['target']![id] = (value: o.targets.length, note: o.targets.first);
  }
  for (final MapEntry(key: slot, value: rule) in o.slots.entries) {
    if (!isValidTextRule(rule)) {
      out['rule']!['$id · $slot'] = (
        value: 1,
        note: '$rule: set up to cut its words without an ellipsis',
      );
    }
  }
  final list = o.subject.list;
  if (list != null) {
    final rule = contract.parts[list.part]?.lists[list.field];
    final shows = rule?.shows;
    if (shows != null && rule!.beyond != 'fixed' && o.itemsDrawn > shows) {
      out['items']![id] = (
        value: o.itemsDrawn,
        note: '${list.slot} shows at most $shows (${rule.beyond})',
      );
    }
  }
  return out;
}

bool _owns(String id, String subject) =>
    subject == id || subject.startsWith('$id · ');

/// Compares [found] with the [allowed] entries [id] owns: `worse` is every
/// violation not allowed or larger than allowed, `better` every allowed one
/// that is gone or smaller.
({List<String> worse, List<String> better}) compareKitCapacity(
  String id,
  Map<String, KitCapacityEntries> found,
  Map<String, KitCapacityEntries> allowed,
) {
  final worse = <String>[];
  final better = <String>[];
  for (final check in kitCapacityChecks) {
    final now = found[check] ?? const {};
    final then = {
      for (final MapEntry(:key, :value) in (allowed[check] ?? const {}).entries)
        if (_owns(id, key)) key: value,
    };
    for (final MapEntry(:key, :value) in now.entries) {
      final known = then[key];
      if (known == null) {
        worse.add('$check · $key = ${value.value} (${value.note})');
      } else if (value.value > known.value + (check == 'width' ? 1 : 0)) {
        worse.add(
          '$check · $key = ${value.value}, allowlisted ${known.value} '
          '(${value.note})',
        );
      } else if (value.value < known.value - (check == 'width' ? 1 : 0)) {
        better.add('$check · $key: now ${value.value}, was ${known.value}');
      }
    }
    for (final key in then.keys) {
      if (!now.containsKey(key)) better.add('$check · $key: fixed');
    }
  }
  return (worse: worse, better: better);
}

/// The rules a declared slot allows: one rule, or several joined by `, `
/// when the part's states set the same words up differently (a server name
/// that wraps while connected and keeps to one line while reconnecting).
Set<String> kitSlotRules(String declared) => declared.split(', ').toSet();

/// [rules] as the contract writes them: sorted, joined by `, `.
String kitSlotRulesText(Iterable<String> rules) =>
    (rules.toSet().toList()..sort()).join(', ');

/// The text slots of [o] that its part's contract does not declare, or
/// declares with other rules.
List<String> kitCapacitySlotDrift(
  KitCapacityObservation o,
  KitCapacityContract contract,
) {
  final declared = contract.parts[o.subject.part]?.text ?? const {};
  return [
    for (final MapEntry(key: slot, value: rule) in o.slots.entries)
      if (declared[slot] == null)
        '${o.subject.part} · "$slot" is not declared (it is $rule)'
      else if (!kitSlotRules(declared[slot]!).contains(rule))
        '${o.subject.part} · "$slot" is declared ${declared[slot]} but is $rule',
  ];
}

/// Set in the environment or with `--dart-define` (tool/qa/vps_test.sh
/// passes flutter test arguments after `--`, not the environment).
bool _flag(String name) =>
    Platform.environment[name] == '1' ||
    switch (name) {
          'KIT_CAPACITY_WRITE' => const String.fromEnvironment(
            'KIT_CAPACITY_WRITE',
          ),
          _ => const String.fromEnvironment('KIT_CAPACITY_BOOTSTRAP'),
        } ==
        '1';

bool get _writing => _flag('KIT_CAPACITY_WRITE');

/// Adds entries only while the gate is first made (or a ratchet-tighten
/// commit resets it); never in normal use.
bool get _bootstrapping => _flag('KIT_CAPACITY_BOOTSTRAP');

/// Registers the capacity tests for the cases of shard [shard]: case index
/// modulo [kitCapacityShards].
void registerKitCapacityShard(int shard) {
  TestWidgetsFlutterBinding.ensureInitialized();
  group('kit capacity shard $shard', () {
    setUpAll(loadKitGalleryFonts);
    final contract = KitCapacityContract.load();
    final allowlist = loadKitCapacityAllowlist();
    final all = kitCapacityCases();
    final cases = [
      for (var i = shard; i < all.length; i += kitCapacityShards) all[i],
    ];
    final observed = <String, KitCapacityObservation>{};

    for (final subject in cases) {
      testWidgets('${subject.id} carries extreme content within its contract', (
        tester,
      ) async {
        final o = await pumpKitCapacity(tester, subject);
        observed[subject.id] = o;
        if (_writing) return;
        expect(
          o.missing,
          isEmpty,
          reason:
              '${subject.id} names parts its tree never showed, so their width '
              'was not measured',
        );
        final drift = kitCapacitySlotDrift(o, contract);
        expect(
          drift,
          isEmpty,
          reason:
              'Declare each text slot\'s overflow rule in '
              '$kitCapacityContractPath (KIT_CAPACITY_WRITE=1 records what '
              'the part does now; review the diff):\n${drift.join('\n')}',
        );
        final (:worse, :better) = compareKitCapacity(
          subject.id,
          kitCapacityViolations(o, contract),
          allowlist,
        );
        expect(
          worse,
          isEmpty,
          reason:
              '${subject.id} breaks the capacity contract '
              '(docs/ux-system/kit-v2.md §8.6) beyond '
              '$kitCapacityAllowlistPath. Fix the part; the allowlist only '
              'shrinks:\n${worse.join('\n')}',
        );
        expect(
          better,
          isEmpty,
          reason:
              '${subject.id} improved. Tighten $kitCapacityAllowlistPath in '
              'the same change (KIT_CAPACITY_WRITE=1 writes it):\n'
              '${better.join('\n')}',
        );
      });
    }

    tearDownAll(() {
      if (observed.isEmpty) return;
      final report = File('build/kit_capacity/report_$shard.json')
        ..createSync(recursive: true);
      report.writeAsStringSync(
        const JsonEncoder.withIndent(
          '  ',
        ).convert([for (final o in observed.values) o.toJson()]),
      );
      if (!_writing || observed.length != cases.length) return;
      withKitCapacityLock(() {
        final now = KitCapacityContract.load();
        final parts = now.raw['parts']! as Map<String, Object?>;
        for (final o in observed.values) {
          final entry = parts[o.subject.part] as Map<String, Object?>?;
          if (entry == null || o.slots.isEmpty) continue;
          final text = {
            ...((entry['text'] as Map<String, Object?>?) ?? const {}),
          };
          for (final MapEntry(key: slot, value: rule) in o.slots.entries) {
            text[slot] = kitSlotRulesText([
              ...kitSlotRules((text[slot] as String?) ?? rule),
              rule,
            ]);
          }
          entry['text'] = text;
        }
        File(
          kitCapacityContractPath,
        ).writeAsStringSync(encodeKitCapacityContract(now.raw));

        final current = loadKitCapacityAllowlist();
        for (final o in observed.values) {
          final found = kitCapacityViolations(o, now);
          for (final check in kitCapacityChecks) {
            final entries = current[check]!;
            entries.removeWhere(
              (key, _) =>
                  _owns(o.subject.id, key) && !found[check]!.containsKey(key),
            );
            for (final MapEntry(:key, :value) in found[check]!.entries) {
              final known = entries[key];
              if (_bootstrapping ||
                  (known != null && value.value <= known.value)) {
                entries[key] = value;
              }
            }
          }
        }
        File(
          kitCapacityAllowlistPath,
        ).writeAsStringSync(encodeKitCapacityAllowlist(current));
      });
      stdout.writeln(
        'KIT_CAPACITY_WRITE=1: shard $shard wrote $kitCapacityContractPath '
        'and $kitCapacityAllowlistPath',
      );
    });
  });
}
