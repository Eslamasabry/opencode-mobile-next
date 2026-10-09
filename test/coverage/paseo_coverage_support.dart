// Shared by the Paseo coverage ratchets (tool steps, timeline items,
// permissions): the ledger rules, the screen-text reader and the report.
//
// Per family, test/fixtures/coverage holds
//   paseo_<family>_samples.json  generated from Paseo's protocol schema by
//                                tool/coverage/paseo_samples.mjs
//   paseo_<family>_ledger.json   "<group>.<field>": "shown" or
//                                "ignored: <reason a person would accept>"
// and each case is run through the app's real code and widgets, opened, and
// the text on screen is checked against the ledger:
//   - a field with no ledger entry fails (a new Paseo field needs a decision),
//   - a "shown" field whose value is not on screen when opened fails,
//   - an "ignored" field whose value IS on screen fails (stale ledger).
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

class CoverageFamily {
  CoverageFamily(this.name)
    : samples =
          jsonDecode(
                File(
                  'test/fixtures/coverage/paseo_${name}_samples.json',
                ).readAsStringSync(),
              )
              as Map<String, dynamic>,
      ledger =
          (jsonDecode(
                    File(
                      'test/fixtures/coverage/paseo_${name}_ledger.json',
                    ).readAsStringSync(),
                  )
                  as Map)
              .cast<String, String>();

  final String name;
  final Map<String, dynamic> samples;
  final Map<String, String> ledger;

  List<Map> get cases => (samples['cases'] as List).cast<Map>();
  final report = <Map<String, Object?>>[];

  Set<String> get schemaKeys => {
    for (final group in (samples['schema'] as Map).entries)
      for (final field in (group.value as List).cast<Map>())
        '${group.key}.${field['path']}',
  };

  Directory get out => Directory('build/coverage')..createSync(recursive: true);
}

/// Every string a person can read in the current tree.
List<String> screenText(WidgetTester tester) => [
  for (final widget in tester.allWidgets)
    if (widget is Text)
      widget.data ?? widget.textSpan?.toPlainText() ?? ''
    else if (widget is RichText)
      widget.text.toPlainText()
    else if (widget is EditableText)
      widget.controller.text,
];

/// Whitespace-insensitive and blind to the invisible break and direction
/// marks the kit puts in paths, so a wrapped line still matches.
String flat(String text) => text
    .replaceAll(RegExp('[\u200b-\u200f\u2066-\u2069\u202a-\u202e]'), '')
    .replaceAll(RegExp(r'\s+'), ' ');

/// The ledger rules every family shares.
void registerLedgerTests(CoverageFamily family) {
  tearDownAll(() {
    File(
      '${family.out.path}/paseo_${family.name}_report.json',
    ).writeAsStringSync(
      const JsonEncoder.withIndent('  ').convert(family.report),
    );
  });

  test('every Paseo field has a ledger decision', () {
    final keys = family.schemaKeys;
    final ledger = family.ledger;
    expect(
      keys.difference(ledger.keys.toSet()),
      isEmpty,
      reason: 'Paseo fields with no entry in paseo_${family.name}_ledger.json',
    );
    expect(
      ledger.keys.toSet().difference(keys),
      isEmpty,
      reason: 'ledger entries for fields Paseo no longer has',
    );
    for (final entry in ledger.entries) {
      expect(
        entry.value == 'shown' ||
            (entry.value.startsWith('ignored: ') && entry.value.length > 20),
        isTrue,
        reason: '${entry.key}: "shown" or "ignored: <reason>"',
      );
    }
    final excluded = (family.samples['excluded'] as Map? ?? {}).keys;
    for (final key in excluded) {
      expect(
        family.ledger[key],
        startsWith('ignored: '),
        reason: '$key cannot appear in a case, so it can only be ignored',
      );
    }
  });

  test('every "shown" field was checked by at least one real value', () {
    final checked = <String>{
      for (final variant in family.cases)
        for (final field in (variant['fields'] as List).cast<Map>())
          if ((field['probes'] as List).isNotEmpty) field['key'] as String,
    };
    final unchecked = [
      for (final entry in family.ledger.entries)
        if (entry.value == 'shown' && !checked.contains(entry.key)) entry.key,
    ];
    expect(
      unchecked,
      isEmpty,
      reason: '"shown" fields no case gives a value to look for',
    );
  });
}

/// The ledger check for one opened case: the problems found on [screenText].
/// [primaryText] is what was readable before any fold was opened: a field the
/// case marks `primary` (what is being asked for) must be there already.
List<String> checkCase(
  CoverageFamily family,
  Map variant,
  String screenText, {
  String? primaryText,
}) {
  final screen = flat(screenText);
  final primaryScreen = flat(primaryText ?? screenText);
  final problems = <String>[];
  for (final field in (variant['fields'] as List).cast<Map>()) {
    final key = field['key'] as String;
    final decision = family.ledger[key];
    final probes = (field['probes'] as List).cast<String>();
    final visible = [for (final probe in probes) screen.contains(flat(probe))];
    family.report.add({
      'case': variant['id'],
      'field': key,
      'decision': decision,
      'probes': probes,
      'visible': visible,
    });
    if (decision == null) {
      problems.add('$key: no ledger entry');
    } else if (decision == 'shown') {
      for (var i = 0; i < probes.length; i++) {
        if (!visible[i]) {
          problems.add('$key: "${probes[i]}" is not on screen');
        } else if (field['primary'] == true &&
            !primaryScreen.contains(flat(probes[i]))) {
          problems.add('$key: "${probes[i]}" is behind a fold');
        }
      }
    } else {
      for (var i = 0; i < probes.length; i++) {
        if (visible[i]) {
          problems.add(
            '$key: "${probes[i]}" is on screen but the ledger says "$decision"',
          );
        }
      }
    }
  }
  return problems;
}

/// Writes the boundary's picture as build/coverage/<name.png.
Future<void> writeCasePng(
  WidgetTester tester,
  GlobalKey boundary,
  String name,
) async {
  final render =
      boundary.currentContext!.findRenderObject()! as RenderRepaintBoundary;
  await tester.runAsync(() async {
    final image = await render.toImage();
    final png = await image.toByteData(format: ui.ImageByteFormat.png);
    final dir = Directory('build/coverage')..createSync(recursive: true);
    File('${dir.path}/$name.png').writeAsBytesSync(png!.buffer.asUint8List());
  });
}
