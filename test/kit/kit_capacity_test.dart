// The kit capacity gate (docs/ux-system/kit-v2.md §8.6; owner, 2026-10-28:
// "tomorrow no agent can by mistake add more than the component can carry").
//
// This file holds the contract to the kit: every part lib/ui/kit/kit.dart
// exports (the G6 manifest, so the same set the overflow matrix pumps) has
// an entry in test/kit/kit_capacity.json with a known role and width cap,
// and every List/Iterable/Set field such a part declares has a `lists` rule
// and, when it draws items, a many-item run in kit_capacity_lists.dart. A
// new part or a new list slot fails until it declares its capacity. It also
// proves the probes the shards rely on, on small stand-ins.
//
// The shards test/kit/kit_capacity_shard_<n>_test.dart pump every scene
// with extreme content (kit_capacity_engine.dart). What breaks the contract
// today is in test/kit/kit_capacity_allowlist.json, which only shrinks;
// KIT_CAPACITY_WRITE=1 tightens it and records text slots.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/stress_content.dart';
import '../text_scale_overflow_test.dart' show readKitManifest;
import 'kit_capacity_contract.dart';
import 'kit_capacity_engine.dart';
import 'kit_capacity_lists.dart';
import 'kit_capacity_probe.dart';

/// Every List/Iterable/Set field declared in the body of each class in
/// [names], read from the kit's source: `Part` -> field (leading `_`
/// dropped) -> its type.
Map<String, Map<String, String>> kitListFields(Set<String> names) {
  final out = <String, Map<String, String>>{};
  final classPattern = RegExp(
    r'^(?:(?:abstract|final|base|sealed|interface)\s+)*class\s+(Kit\w+)[^{]*\{',
    multiLine: true,
  );
  final fieldPattern = RegExp(
    r'^\s*final\s+((?:List|Iterable|Set)<[^;=]*?>\??)\s+(\w+)\s*;',
    multiLine: true,
  );
  final files = Directory('lib/ui/kit')
      .listSync(recursive: true)
      .whereType<File>()
      .where((f) => f.path.endsWith('.dart'));
  for (final file in files) {
    final source = file.readAsStringSync();
    for (final m in classPattern.allMatches(source)) {
      final name = m[1]!;
      if (!names.contains(name)) continue;
      var depth = 1;
      var i = m.end;
      while (depth > 0 && i < source.length) {
        final ch = source[i];
        if (ch == '{') depth++;
        if (ch == '}') depth--;
        i++;
      }
      final body = source.substring(m.end, i);
      for (final f in fieldPattern.allMatches(body)) {
        final field = f[2]!.replaceFirst(RegExp('^_+'), '');
        (out[name] ??= {})[field] = f[1]!;
      }
    }
  }
  return out;
}

/// The static violations: `list` (a slot declared `unknown`) and `run` (a
/// slot that draws items with no many-item run), keyed `Part.field`.
Map<String, KitCapacityEntries> kitStaticViolations(
  KitCapacityContract contract,
) {
  final runs = {for (final r in kitListStress) r.slot};
  final out = <String, KitCapacityEntries>{'list': {}, 'run': {}};
  for (final MapEntry(key: part, value: entry) in contract.parts.entries) {
    for (final MapEntry(key: field, value: rule) in entry.lists.entries) {
      final slot = '$part.$field';
      if (rule.beyond == 'unknown') {
        out['list']![slot] = (
          value: 1,
          note: 'no rule for more items than fit has been declared',
        );
      } else if (kitListDrawsItems.contains(rule.beyond) &&
          rule.via == null &&
          !runs.contains(slot)) {
        out['run']![slot] = (
          value: 1,
          note: '${rule.beyond} is declared but no run proves it',
        );
      }
    }
  }
  return out;
}

class _Probe extends StatelessWidget {
  const _Probe(this.child);

  final Widget child;

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    home: Scaffold(body: child),
  );
}

void main() {
  final contract = KitCapacityContract.load();
  final manifest = readKitManifest();

  test('the G6 manifest reads the kit', () {
    expect(manifest.problems, isEmpty);
    expect(manifest.parts, containsAll(['KitRow', 'KitSheet', 'showKitSheet']));
  });

  test('every exported kit part declares its capacity, and only those do', () {
    final missing = manifest.parts.difference(contract.parts.keys.toSet());
    final stale = contract.parts.keys.toSet().difference(manifest.parts);
    expect(
      missing.toList()..sort(),
      isEmpty,
      reason:
          'Add these parts to $kitCapacityContractPath with a role (its width '
          'cap, docs/ux-system/kit-v2.md §8.6) and a lists rule per list '
          'field:',
    );
    expect(
      stale.toList()..sort(),
      isEmpty,
      reason: '$kitCapacityContractPath names parts kit.dart no longer exports',
    );
  });

  test('every role and cap names a known width', () {
    for (final MapEntry(:key, :value) in contract.roles.entries) {
      expect(kitWidthTokens.containsKey(value), isTrue, reason: 'role $key');
    }
    for (final MapEntry(:key, :value) in contract.parts.entries) {
      expect(contract.roles, contains(value.role), reason: key);
      if (value.maxWidth != null) {
        expect(kitWidthTokens, contains(value.maxWidth), reason: key);
      }
      // A role with a cap needs a real width, and a part that fills cannot
      // pretend to be capped.
      expect(contract.tokenOf(key), isNotNull, reason: key);
    }
    expect(contract.minTarget, 48, reason: 'kit-v2 §8.3: 48 dp everywhere');
  });

  test('every text rule in the contract is one the probe can report', () {
    final known = RegExp(r'^(wrap|scroll|(ellipsis|fade|clip|visible):\d+)$');
    for (final MapEntry(key: part, value: entry) in contract.parts.entries) {
      for (final MapEntry(key: slot, value: rules) in entry.text.entries) {
        for (final rule in kitSlotRules(rules)) {
          expect(
            known.hasMatch(rule),
            isTrue,
            reason: '$part · "$slot": $rule',
          );
        }
        expect(kitSlotRulesText(kitSlotRules(rules)), rules, reason: slot);
      }
    }
  });

  test('every list-like field of a part declares what happens beyond what '
      'fits', () {
    final fields = kitListFields(manifest.parts);
    final undeclared = <String>[];
    final stale = <String>[];
    for (final MapEntry(key: part, value: byField) in fields.entries) {
      final declared = contract.parts[part]?.lists ?? const {};
      for (final field in byField.keys) {
        if (!declared.containsKey(field)) {
          undeclared.add('$part.$field (${byField[field]})');
        }
      }
    }
    for (final MapEntry(key: part, value: entry) in contract.parts.entries) {
      for (final field in entry.lists.keys) {
        if (!(fields[part]?.containsKey(field) ?? false)) {
          stale.add('$part.$field');
        }
      }
    }
    expect(
      undeclared,
      isEmpty,
      reason:
          'Declare each in $kitCapacityContractPath under the part\'s '
          '"lists": {"beyond": grows|wraps|scroll|more|page|fixed|not-items, '
          '"shows": N} and add a 50-item run to kit_capacity_lists.dart',
    );
    expect(stale, isEmpty, reason: 'lists rules for fields that are gone');
    for (final MapEntry(key: part, value: entry) in contract.parts.entries) {
      for (final MapEntry(key: field, value: rule) in entry.lists.entries) {
        expect(kitListBeyond, contains(rule.beyond), reason: '$part.$field');
        if (rule.beyond == 'fixed' || rule.beyond == 'page') {
          expect(rule.shows, isNotNull, reason: '$part.$field needs shows');
        }
      }
    }
  });

  test('every many-item run fills a declared slot to its limit', () {
    final runs = <String>{};
    for (final run in kitListStress) {
      expect(runs.add(run.slot), isTrue, reason: 'two runs for ${run.slot}');
      final rule = contract.parts[run.part]?.lists[run.field];
      expect(rule, isNotNull, reason: '${run.slot} has no lists rule');
      expect(
        run.count,
        rule!.beyond == 'fixed' ? rule.shows : 50,
        reason:
            '${run.slot}: 50 items, or exactly `shows` for a slot the part '
            'caps with an assert',
      );
    }
    for (final entry in contract.parts.values) {
      for (final rule in entry.lists.values) {
        if (rule.via != null) {
          expect(runs, contains(rule.via), reason: 'via ${rule.via}');
        }
      }
    }
  });

  test('list slots with no rule or no run are allowlisted, and the '
      'allowlist only shrinks', () {
    final allowlist = loadKitCapacityAllowlist();
    final found = kitStaticViolations(contract);
    final fresh = <String>[];
    final fixed = <String>[];
    for (final check in ['list', 'run']) {
      for (final key in found[check]!.keys) {
        if (!allowlist[check]!.containsKey(key)) {
          fresh.add('$check · $key: ${found[check]![key]!.note}');
        }
      }
      for (final key in allowlist[check]!.keys) {
        if (!found[check]!.containsKey(key)) fixed.add('$check · $key');
      }
    }
    expect(
      fresh,
      isEmpty,
      reason:
          'A new list slot must declare its rule and get a run in '
          'kit_capacity_lists.dart:\n${fresh.join('\n')}',
    );
    expect(
      fixed,
      isEmpty,
      reason:
          'Remove these from $kitCapacityAllowlistPath, they now pass:\n'
          '${fixed.join('\n')}',
    );
  });

  test('the shards pump every case exactly once', () {
    for (var n = 0; n < kitCapacityShards; n++) {
      final file = File('test/kit/kit_capacity_shard_${n}_test.dart');
      expect(file.existsSync(), isTrue, reason: file.path);
      expect(file.readAsStringSync(), contains('registerKitCapacityShard($n)'));
    }
    expect(
      File(
        'test/kit/kit_capacity_shard_${kitCapacityShards}_test.dart',
      ).existsSync(),
      isFalse,
    );
    final cases = kitCapacityCases();
    expect(cases.map((c) => c.id).toSet(), hasLength(cases.length));
    final covered = {for (final c in cases) ...c.scene.parts};
    expect(manifest.parts.difference(covered), isEmpty);
  });

  test('the stress fixtures are as extreme as they claim', () {
    expect(StressContent.prose.length, greaterThanOrEqualTo(1000));
    expect(StressContent.proseArabic.length, greaterThanOrEqualTo(900));
    expect(StressContent.token, hasLength(300));
    expect(StressContent.token, isNot(contains(' ')));
    expect(StressContent.cjk, isNot(contains(' ')));
    expect(StressContent.emoji, contains('‍'));
    const original = 'Connect to this server';
    final pseudo = StressContent.pseudo(original);
    expect(
      pseudo.length,
      greaterThanOrEqualTo((original.length * 1.4).floor()),
    );
    expect(pseudo, startsWith(StressContent.pseudoOpen));
    expect(pseudo, endsWith(StressContent.pseudoClose));
    expect(StressContent.pseudo(pseudo), pseudo);
    expect(StressContent.pseudo(''), '');
    expect(StressContent.items(50), hasLength(50));
  });

  group('probes', () {
    Future<void> pump(WidgetTester tester, Widget child) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(1600, 1000);
      addTearDown(tester.view.reset);
      await tester.pumpWidget(_Probe(child));
    }

    RenderParagraph paragraph(WidgetTester tester, String start) =>
        tester.renderObject<RenderParagraph>(
          find.byWidgetPredicate(
            (w) => w is RichText && w.text.toPlainText().startsWith(start),
          ),
        );

    testWidgets('paintedWidth counts ink, not layout', (tester) async {
      await pump(
        tester,
        Center(
          child: ConstrainedBox(
            key: const Key('outer'),
            constraints: const BoxConstraints(maxWidth: 1500),
            child: const SizedBox(
              width: 1500,
              height: 40,
              child: Align(
                alignment: Alignment.centerLeft,
                child: SizedBox(
                  width: 300,
                  height: 40,
                  child: DecoratedBox(
                    decoration: BoxDecoration(color: Color(0xFF000000)),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      final outer = tester.renderObject(find.byKey(const Key('outer')));
      const window = Rect.fromLTWH(0, 0, 1600, 1000);
      expect(paintedWidth(outer, window: window), 300);

      await pump(
        tester,
        const SizedBox(
          key: Key('clear'),
          width: 1500,
          height: 40,
          child: DecoratedBox(decoration: BoxDecoration()),
        ),
      );
      expect(
        paintedWidth(
          tester.renderObject(find.byKey(const Key('clear'))),
          window: window,
        ),
        0,
      );

      await pump(
        tester,
        const Row(
          key: Key('row'),
          children: [Text('start'), Spacer(), Text('end')],
        ),
      );
      expect(
        paintedWidth(
          tester.renderObject(find.byKey(const Key('row'))),
          window: window,
        ),
        1600,
      );
    });

    testWidgets('textRule reads the slot\'s overflow rule', (tester) async {
      await pump(
        tester,
        Column(
          children: [
            const Text('wraps freely'),
            const Text(
              'one line',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            const Text('two faded', maxLines: 2, overflow: TextOverflow.fade),
            const Text('clipped', maxLines: 1),
            const Text(
              'single',
              softWrap: false,
              overflow: TextOverflow.ellipsis,
            ),
            const SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Text('sideways', softWrap: false),
            ),
            SizedBox(
              height: 40,
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: const [Text('scrolls', maxLines: 1)],
              ),
            ),
          ],
        ),
      );
      expect(textRule(paragraph(tester, 'wraps')), 'wrap');
      expect(textRule(paragraph(tester, 'one line')), 'ellipsis:1');
      expect(textRule(paragraph(tester, 'two faded')), 'fade:2');
      expect(textRule(paragraph(tester, 'clipped')), 'clip:1');
      expect(textRule(paragraph(tester, 'single')), 'ellipsis:1');
      expect(textRule(paragraph(tester, 'scrolls')), 'scroll');
      expect(textRule(paragraph(tester, 'sideways')), 'scroll');
      expect(isValidTextRule('clip:1'), isFalse);
      expect(isValidTextRule('ellipsis:3'), isTrue);
    });

    testWidgets('silentCut finds words lost without an ellipsis', (
      tester,
    ) async {
      final long = StressContent.prose;
      await pump(
        tester,
        SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(width: 200, child: Text('A$long', maxLines: 1)),
              SizedBox(
                width: 200,
                child: Text(
                  'B$long',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              SizedBox(width: 200, height: 20, child: Text('C$long')),
              SizedBox(
                width: 200,
                height: 30,
                child: ClipRect(
                  child: OverflowBox(
                    maxHeight: double.infinity,
                    alignment: Alignment.topLeft,
                    child: Text('D$long'),
                  ),
                ),
              ),
              SizedBox(width: 200, child: Text('E$long')),
              const Icon(Icons.add),
            ],
          ),
        ),
      );
      expect(silentCut(paragraph(tester, 'A')), contains('no ellipsis'));
      expect(silentCut(paragraph(tester, 'B')), isNull);
      expect(silentCut(paragraph(tester, 'C')), contains('shorter'));
      expect(silentCut(paragraph(tester, 'D')), contains('RenderClipRect'));
      expect(silentCut(paragraph(tester, 'E')), isNull);
      final icon = tester.renderObject<RenderParagraph>(
        find.descendant(of: find.byType(Icon), matching: find.byType(RichText)),
      );
      expect(isIconText(icon), isTrue);
      expect(silentCut(icon), isNull);
    });

    testWidgets('pseudoLocalizeTree rewrites a part\'s own words in place', (
      tester,
    ) async {
      await pump(tester, const Text('Settings'));
      final root = tester.renderObject(find.byType(Scaffold));
      pseudoLocalizeTree(root);
      await tester.pump();
      final p = tester.renderObject<RenderParagraph>(find.byType(RichText));
      expect(p.text.toPlainText(), startsWith(StressContent.pseudoOpen));
      expect(p.text.toPlainText(), contains('Šéţţîñĝš'));
    });

    test('slotKey folds numbers and long words', () {
      expect(slotKey('Last reply 12 min ago'), 'Last reply ## min ago');
      expect(slotKey('a' * 60), hasLength(48));
    });
  });
}
