// Coverage ratchet for Paseo tool steps (Gate 1 + Gate 2).
//
// test/fixtures/coverage/paseo_tool_samples.json is generated from Paseo's own
// protocol schema (tool/coverage/paseo_samples.mjs): every field of every
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
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/mappers.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';

import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';

void main() {
  setUpAll(loadKitGalleryFonts);
  final family = CoverageFamily('tool');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('paseo tool step · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final item = (variant['payload'] as Map).cast<String, dynamic>();
      final message = paseoItemMessage(
        'agent-1',
        item,
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
      final screen = screenText(tester).join('\n');
      final problems = checkCase(family, variant, screen);
      await writeCasePng(tester, boundary, 'paseo_$id');
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
