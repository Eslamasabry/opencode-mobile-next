// Coverage pilot (Gate 1 + Gate 2 input) for Paseo tool steps.
//
// test/fixtures/coverage/paseo_tool_samples.json is generated from Paseo's own
// protocol schema (tool/coverage/paseo_tool_samples.mjs): one completed
// tool_call per step type, every field holding a unique marker. Each sample
// goes through the app's real mapper and tool card, the step is opened, and
// every marker is looked for in the text on screen. Writes the field report
// and one screenshot per opened step to build/coverage/.
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

/// The name Claude Code gives the tool that produces each step type.
const _toolNames = {
  'worktree_setup': 'worktree_setup',
  'shell': 'Bash',
  'read': 'Read',
  'edit': 'Edit',
  'write': 'Write',
  'search': 'WebSearch',
  'fetch': 'WebFetch',
  'sub_agent': 'Agent',
  'plain_text': 'Skill',
  'plan': 'ExitPlanMode',
  'unknown': 'mcp__drive__search',
};

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

void main() {
  setUpAll(loadKitGalleryFonts);
  final samples =
      jsonDecode(
            File(
              'test/fixtures/coverage/paseo_tool_samples.json',
            ).readAsStringSync(),
          )
          as Map<String, dynamic>;
  final out = Directory('build/coverage')..createSync(recursive: true);
  final report = <Map<String, Object?>>[];
  tearDownAll(() {
    File(
      '${out.path}/paseo_tool_report.json',
    ).writeAsStringSync(const JsonEncoder.withIndent('  ').convert(report));
  });

  for (final variant in (samples['variants'] as List).cast<Map>()) {
    final type = variant['type'] as String;
    testWidgets('paseo tool step · $type', (tester) async {
      tester.view.physicalSize = const Size(412, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final message = paseoItemMessage(
        'agent-1',
        {
          'type': 'tool_call',
          'callId': 'call-$type',
          'name': _toolNames[type] ?? type,
          'status': 'completed',
          'error': null,
          'detail': variant['detail'],
        },
        id: 'call-$type',
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
                  state: part.toolState!,
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final collapsed = _screenText(tester).join('\n');
      final row = find.byType(KitToolRow);
      if (row.evaluate().isNotEmpty) {
        await tester.tap(row.first, warnIfMissed: false);
        await tester.pumpAndSettle();
      }
      final expanded = _screenText(tester).join('\n');
      for (final field in (variant['fields'] as List).cast<Map>()) {
        final marker = field['marker'] as String?;
        report.add({
          'type': type,
          'tool': _toolNames[type],
          'field': field['path'],
          'kind': field['kind'],
          'marker': marker,
          'onLine': marker != null && collapsed.contains(marker),
          'whenOpened': marker != null && expanded.contains(marker),
        });
      }
      final render =
          boundary.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      await tester.runAsync(() async {
        final image = await render.toImage();
        final png = await image.toByteData(format: ui.ImageByteFormat.png);
        File(
          '${out.path}/paseo_$type.png',
        ).writeAsBytesSync(png!.buffer.asUint8List());
      });
    });
  }
}
