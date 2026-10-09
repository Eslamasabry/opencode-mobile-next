// Coverage ratchet for Paseo tool steps (Gate 1 + Gate 2).
//
// test/fixtures/coverage/paseo_tool_samples.json is generated from Paseo's own
// protocol schema (tool/coverage/paseo_tool_samples.mjs): every field of every
// step type, and realistic cases (grep with lines, grep with files, glob, web
// search, edit by diff, ...) that fill those fields the way Paseo does. The
// generator fails when a schema field is in no case.
//
// test/fixtures/coverage/paseo_tool_ledger.json says, for every
// `type.field`, "shown" or "ignored: <reason a person would accept>". Each
// case goes through the app's real mapper and tool card, the step is opened,
// and the text on screen is checked against the ledger:
//   - a field with no ledger entry fails (a new Paseo field needs a decision),
//   - a "shown" field whose value is not on screen when opened fails,
//   - an "ignored" field whose value IS on screen fails (stale ledger).
// Writes build/coverage/paseo_tool_report.json and one PNG per case; run
// tool/coverage/contact_sheet.py to put them on one sheet for review.
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/mappers.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';

import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

/// Every string a person can read in the current tree.
List<String> _screenText(WidgetTester tester) => [
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
String _flat(String text) => text
    .replaceAll(RegExp('[\u200b-\u200f\u2066-\u2069\u202a-\u202e]'), '')
    .replaceAll(RegExp(r'\s+'), ' ');

void main() {
  setUpAll(loadKitGalleryFonts);
  final samples =
      jsonDecode(
            File(
              'test/fixtures/coverage/paseo_tool_samples.json',
            ).readAsStringSync(),
          )
          as Map<String, dynamic>;
  final ledger =
      (jsonDecode(
                File(
                  'test/fixtures/coverage/paseo_tool_ledger.json',
                ).readAsStringSync(),
              )
              as Map)
          .cast<String, String>();
  final out = Directory('build/coverage')..createSync(recursive: true);
  final report = <Map<String, Object?>>[];
  tearDownAll(() {
    File(
      '${out.path}/paseo_tool_report.json',
    ).writeAsStringSync(const JsonEncoder.withIndent('  ').convert(report));
  });

  test('every Paseo field has a ledger decision', () {
    final schema = (samples['schema'] as Map).cast<String, List<dynamic>>();
    final keys = {
      for (final type in schema.entries)
        for (final field in type.value.cast<Map>())
          '${type.key}.${field['path']}',
    };
    expect(
      keys.difference(ledger.keys.toSet()),
      isEmpty,
      reason: 'Paseo fields with no entry in paseo_tool_ledger.json',
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
  });

  final cases = (samples['cases'] as List).cast<Map>();
  for (final variant in cases) {
    final id = variant['id'] as String;
    final type = variant['type'] as String;
    testWidgets('paseo tool step · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final message = paseoItemMessage(
        'agent-1',
        {
          'type': 'tool_call',
          'callId': 'call-$id',
          'name': variant['tool'],
          'status': variant['status'],
          'error': variant['error'],
          'detail': variant['detail'],
        },
        id: 'call-$id',
        provider: 'claude',
      );
      final part = message!.parts.single;
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: child!,
            ),
            home: Scaffold(
              body: SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: ToolCard(
                  toolName: part.toolName ?? 'tool',
                  state: part.toolState,
                  onRerunCommand: (_) {},
                  onRetry: () {},
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final row = find.byType(KitToolRow);
      if (row.evaluate().isNotEmpty && part.toolState.status != 'error') {
        await tester.tap(row.first, warnIfMissed: false);
        await tester.pumpAndSettle();
      }
      final screen = _flat(_screenText(tester).join('\n'));
      final problems = <String>[];
      for (final field in (variant['fields'] as List).cast<Map>()) {
        final path = field['path'] as String;
        final key = '$type.$path';
        final decision = ledger[key];
        final probes = (field['probes'] as List).cast<String>();
        final visible = [
          for (final probe in probes) screen.contains(_flat(probe)),
        ];
        report.add({
          'case': id,
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
            }
          }
        } else {
          for (var i = 0; i < probes.length; i++) {
            if (visible[i]) {
              problems.add(
                '$key: "${probes[i]}" is on screen but the ledger says '
                '"$decision"',
              );
            }
          }
        }
      }
      final render =
          boundary.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      await tester.runAsync(() async {
        final image = await render.toImage();
        final png = await image.toByteData(format: ui.ImageByteFormat.png);
        File(
          '${out.path}/paseo_$id.png',
        ).writeAsBytesSync(png!.buffer.asUint8List());
      });
      expect(problems, isEmpty, reason: 'screen text:\n$screen');
    });
  }

  test('every "shown" field was checked by at least one real value', () {
    final checked = <String>{
      for (final variant in cases)
        for (final field in (variant['fields'] as List).cast<Map>())
          if ((field['probes'] as List).isNotEmpty)
            '${variant['type']}.${field['path']}',
    };
    final unchecked = [
      for (final entry in ledger.entries)
        if (entry.value == 'shown' && !checked.contains(entry.key)) entry.key,
    ];
    expect(
      unchecked,
      isEmpty,
      reason: '"shown" fields no case gives a value to look for',
    );
  });
}
